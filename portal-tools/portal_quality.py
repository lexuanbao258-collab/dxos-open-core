#!/usr/bin/env python3
"""Dependency-free, fail-closed checks for DX-OS static developer docs.

This checks documented routes/schemas, source-draft hygiene, link targets and
potential contract-breaking changes. It does NOT prove runtime/ACL correctness.
"""
import argparse
from html.parser import HTMLParser
import json
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    ("get", "/api/v1/mvp/requests"),
    ("post", "/api/v1/mvp/requests"),
    ("get", "/api/v1/mvp/requests/{id}"),
    ("get", "/api/v1/mvp/requests/{id}/history"),
    ("post", "/api/v1/mvp/requests/{id}/triage"),
    ("post", "/api/v1/mvp/requests/{id}/assign"),
    ("post", "/api/v1/mvp/requests/{id}/start"),
    ("post", "/api/v1/mvp/requests/{id}/resolve"),
    ("post", "/api/v1/mvp/requests/{id}/confirm"),
}
HTTP = {"get", "post", "put", "patch", "delete", "options", "head"}
PUBLIC_SPEC = ROOT / "preview/api/openapi.source-draft.json"

def require(cond, message):
    if not cond:
        raise ValueError(message)

def load(path):
    with open(path, "r", encoding="utf-8") as f:
        value = json.load(f)
    require(isinstance(value, dict), f"JSON root must be object: {path}")
    return value

def operations(document):
    return {
        (method.lower(), path): op
        for path, methods in document.get("paths", {}).items()
        for method, op in methods.items()
        if method.lower() in HTTP
    }

def resolve(document, obj):
    seen = set()
    for _ in range(12):
        if not isinstance(obj, dict) or "$ref" not in obj:
            return obj
        ref = obj["$ref"]
        require(ref.startswith("#/"), "External or unsupported $ref: " + ref)
        require(ref not in seen, "Recursive $ref loop: " + ref)
        seen.add(ref)
        target = document
        for key in ref[2:].split("/"):
            key = key.replace("~1", "/").replace("~0", "~")
            require(isinstance(target, dict) and key in target, f"Missing reference {ref}")
            target = target[key]
        obj = target
    raise ValueError("Maximum $ref depth exceeded")

def media_schema(content, label):
    """Select JSON (or Springdoc's */*) without silently skipping the schema."""
    require(isinstance(content, dict) and content,
            f"Missing response/request content: {label}")
    candidates = ["application/json", "application/problem+json", "*/*"]
    candidates += [k for k in content if k.endswith("+json") and k not in candidates]
    found = next((k for k in candidates if k in content), None)
    require(found is not None,
            f"No JSON-compatible content type: {label}, available={sorted(content)}")
    media = content[found]
    require(isinstance(media, dict) and media.get("schema"),
            f"No schema under {found}: {label}")
    return media["schema"]


def schema_for(document, operation, source):
    if source == "requestBody":
        holder = resolve(document, operation.get("requestBody", {}))
        schema = media_schema(holder.get("content", {}), "requestBody")
    else:
        codes = operation.get("responses", {})
        success = next((c for c in ("200", "201", "202") if c in codes), None)
        require(success is not None, "Missing success response")
        response = resolve(document, codes[success])
        schema = media_schema(response.get("content", {}), f"HTTP {success}")
    result = resolve(document, schema)
    require(isinstance(result, dict) and result,
            "Response/request JSON schema resolves to empty/non-object")
    return result


def normalized_schema(document, obj, depth=0, references=None):
    """Shape-focused deep comparison; intentionally not a full OpenAPI semantic proof."""
    require(depth <= 20, "Schema nesting exceeds 20 levels")
    if references is None:
        references = frozenset()
    if not isinstance(obj, dict):
        return obj
    if "$ref" in obj:
        ref = obj["$ref"]
        require(ref not in references, f"Recursive schema unsupported: {ref}")
        return normalized_schema(document, resolve(document, obj), depth + 1,
                                 references | {ref})
    keys = ("type", "nullable", "format", "enum", "const", "minLength", "maxLength",
            "pattern", "minimum", "maximum", "minItems", "maxItems",
            "additionalProperties", "required", "readOnly", "writeOnly")
    result = {}
    for k in keys:
        if k in obj:
            value = obj[k]
            if k == "type" and isinstance(value, list):
                value = sorted(value)
            elif k in ("enum", "required") and isinstance(value, list):
                value = sorted(value, key=str)
            elif k == "additionalProperties" and isinstance(value, dict):
                value = normalized_schema(document, value, depth + 1, references)
            result[k] = value
    for k in ("properties", "patternProperties"):
        if k in obj:
            require(isinstance(obj[k], dict), f"Invalid {k} in schema")
            result[k] = {name: normalized_schema(document, v, depth + 1, references)
                         for name, v in sorted(obj[k].items())}
    if "items" in obj:
        result["items"] = normalized_schema(document, obj["items"], depth + 1,
                                             references)
    for k in ("oneOf", "anyOf", "allOf"):
        if k in obj:
            result[k] = [normalized_schema(document, v, depth + 1, references)
                         for v in obj[k]]
    return result


def flatten_diff(before, after, prefix):
    """Report every missing or different constraint; never treat {} as proof."""
    if isinstance(before, dict) and isinstance(after, dict):
        results = []
        for key in sorted(set(before) | set(after)):
            path = f"{prefix}.{key}"
            if key not in before:
                results.append(f"{path}: only in runtime ({str(after[key])[:90]})")
            elif key not in after:
                results.append(f"{path}: only in draft ({str(before[key])[:90]})")
            else:
                results += flatten_diff(before[key], after[key], path)
        return results
    if before != after:
        return [f"{prefix}: draft={str(before)[:120]} runtime={str(after)[:120]}"]
    return []


def compare_runtime(draft, runtime):
    """Fail closed on route, status, security, parameter and deep schema drift.

    Comparison is structural and still needs a human for example correctness,
    business authorization, charset validity, and response semantics.
    """
    dops, rops = operations(draft), operations(runtime)
    issues = []
    for key in sorted(set(dops) | set(rops)):
        if key not in dops:
            issues.append(f"{key}: undocumented runtime endpoint")
            continue
        if key not in rops:
            issues.append(f"{key}: draft endpoint absent from runtime")
            continue
        d, r = dops[key], rops[key]
        prefix = f"{key[0].upper()} {key[1]}"
        dstatus, rstatus = set(d.get("responses", {})), set(r.get("responses", {}))
        if dstatus != rstatus:
            issues.append(f"{prefix}.HTTP-status: draft={sorted(dstatus)} runtime={sorted(rstatus)}")
        dsecurity = d.get("security", draft.get("security", []))
        rsecurity = r.get("security", runtime.get("security", []))
        if dsecurity != rsecurity:
            issues.append(f"{prefix}.security: differs")
        def params(op):
            return sorted(op.get("parameters", []),
                          key=lambda p: (p.get("name", ""), p.get("in", "")))
        dparam, rparam = params(d), params(r)
        if dparam != rparam:
            issues.append(f"{prefix}.parameters: differs (inspect required/type/format)")
        for src in (("requestBody", "response") if key[0] == "post" else ("response",)):
            try:
                ds = normalized_schema(draft, schema_for(draft, d, src))
                rs = normalized_schema(runtime, schema_for(runtime, r, src))
                issues.extend(flatten_diff(ds, rs, f"{prefix}.{src}"))
            except (ValueError, KeyError, TypeError) as exc:
                issues.append(f"{prefix}.{src}: could not compare schema: {exc}")
    return issues

def required_schema(document, operation, source):
    root = schema_for(document, operation, source)
    return root.get("required", []) if isinstance(root, dict) else []

def props(document, operation, source):
    root = schema_for(document, operation, source)
    if not isinstance(root, dict):
        return {}
    return root.get("properties", {})

def check_document(document, is_draft=True):
    require(str(document.get("openapi", "")).startswith("3."), "Expected OpenAPI 3.x")
    ops = operations(document)
    require(set(ops) == EXPECTED,
            "9-route mismatch: missing=" + str(sorted(EXPECTED - set(ops))) +
            " unexpected=" + str(sorted(set(ops) - EXPECTED)))
    require("bearerAuth" in document.get("components", {}).get("securitySchemes", {}),
            "Missing HTTP bearerAuth")
    bearer = document["components"]["securitySchemes"]["bearerAuth"]
    require(bearer.get("type") == "http" and bearer.get("scheme") == "bearer",
            "bearerAuth is not an HTTP bearer scheme")
    for (method, path), operation in ops.items():
        effective_security = operation.get("security", document.get("security", []))
        require(any(isinstance(s, dict) and "bearerAuth" in s for s in effective_security),
                f"Missing bearerAuth security: {method} {path}")
        require("summary" in operation and "description" in operation,
                f"Missing summary/description: {method} {path}")
        expected_status = "201" if (method, path) == ("post", "/api/v1/mvp/requests") else "200"
        require(expected_status in operation.get("responses", {}),
                f"Missing expected HTTP {expected_status}: {method} {path}")
        # Check every documented success response, including array schemas.
        # A missing JSON schema is not allowed to pass with empty properties.
        schema_for(document, operation, "response")
        if method == "post":
            require(operation.get("requestBody", {}).get("required", True) is not False,
                    f"Optional request body: {method} {path}")
            require(props(document, operation, "requestBody"),
                    f"Missing request properties: {method} {path}")
        else:
            require("requestBody" not in operation, f"Unexpected GET request body: {path}")
        if "{id}" in path:
            parameters = operation.get("parameters", [])
            require(any(p.get("name") == "id" and p.get("in") == "path" and p.get("required")
                        for p in parameters), f"Missing required id path parameter: {path}")
    require(not document.get("servers"), "Public docs must not define a callable Backend server")
    for ref in extract_refs(document):
        resolve(document, {"$ref": ref})
    if is_draft:
        desc = document.get("info", {}).get("description", "")
        require("BẢN THẢO" in desc and "chưa phải JSON export" in desc,
                "Draft must be clearly identified; never mislabel as verified runtime JSON")
    return ops

def extract_refs(value):
    if isinstance(value, dict):
        for k, v in value.items():
            if k == "$ref":
                yield v
            else:
                yield from extract_refs(v)
    elif isinstance(value, list):
        for v in value:
            yield from extract_refs(v)

def check_breaking(previous, current):
    old, new = operations(previous), operations(current)
    issues = []
    for key, op in old.items():
        if key not in new:
            issues.append("Removed operation: " + str(key))
            continue
        newer = new[key]
        old_responses = op.get("responses", {})
        new_responses = newer.get("responses", {})
        for success in ("200", "201", "202", "204"):
            if success in old_responses and success not in new_responses:
                issues.append(f"Removed {success} success response: {key}")
        for src in ("requestBody",):
            if key[0] == "get":
                continue
            before = set(required_schema(previous, op, src))
            after = set(required_schema(current, newer, src))
            for field in sorted(after - before):
                issues.append(f"New required request field {key}: {field}")
            old_props = props(previous, op, src)
            new_props = props(current, newer, src)
            for field in old_props:
                if field not in new_props:
                    issues.append(f"Removed request field {key}: {field}")
                elif normalize_type(old_props[field]) != normalize_type(new_props[field]):
                    issues.append(f"Request field changed type {key}: {field}")
        before_response = props(previous, op, "response")
        after_response = props(current, newer, "response")
        for field in before_response:
            if field not in after_response:
                issues.append(f"Removed response property {key}: {field}")
            elif normalize_type(before_response[field]) != normalize_type(after_response[field]):
                issues.append(f"Response property changed type {key}: {field}")
    return issues

def normalize_type(schema):
    if not isinstance(schema, dict):
        return None
    if "$ref" in schema:
        return ("ref", schema["$ref"])
    types = schema.get("type")
    return tuple(sorted(types)) if isinstance(types, list) else types

class PageLinks(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.links = []
        self.ids = set()
    def handle_starttag(self, tag, attrs):
        d = dict(attrs)
        if d.get("id"):
            self.ids.add(d["id"])
        for key in ("href", "src"):
            if key in d:
                self.links.append(d[key])

def check_site(root):
    root = root.resolve()
    docs = list(root.rglob("*.html"))
    require(len(docs) >= 4, "Expected four documentation pages")
    checked = 0
    for file in docs:
        parser = PageLinks()
        parser.feed(file.read_text(encoding="utf-8"))
        for url in parser.links:
            if not url or url.startswith(("https://", "http://", "mailto:", "data:")):
                continue
            parsed = urlsplit(url)
            path = unquote(parsed.path)
            candidate = (file.parent / path).resolve() if path else file.resolve()
            require(candidate == root or root in candidate.parents,
                    f"Link leaves site root: {file} -> {url}")
            if candidate.is_dir():
                candidate = candidate / "index.html"
            require(candidate.is_file(), f"Broken internal link: {file} -> {url}")
            # Cross-file anchors generated by JavaScript or Scalar use dynamic routing;
            # static anchors on same file are validated, HTML cross-file anchors optional.
            if not path and parsed.fragment:
                require(parsed.fragment in parser.ids, f"Missing anchor: {file} -> {url}")
            checked += 1
    api = (root / "api/index.html").read_text(encoding="utf-8")
    require("hideTestRequestButton:true" in api and "persistAuth:false" in api,
            "Public Scalar must disable Try Request and auth persistence")
    require("proxyUrl:" not in api and "openapi.source-draft.json" in api,
            "Public Scalar must not use request proxy or unreviewed URL")
    return len(docs), checked

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--spec", type=Path, default=PUBLIC_SPEC)
    p.add_argument("--site", type=Path, default=ROOT / "preview")
    p.add_argument("--baseline", type=Path)
    p.add_argument("--runtime-json", type=Path, help="Optional manually exported runtime OpenAPI; no network access")
    args = p.parse_args()
    spec = load(args.spec)
    check_document(spec)
    print("OPENAPI_SOURCE_DRAFT=PASS (9 endpoints, method/schema refs, bearer, 201, safe servers)")
    pages, links = check_site(args.site)
    print(f"STATIC_SITE=PASS ({pages} HTML pages, {links} internal link/asset checks)")
    if args.baseline:
        issues = check_breaking(load(args.baseline), spec)
        require(not issues, "BREAKING_CHANGES:\\n" + "\\n".join(issues))
        print("BREAKING_CHANGE_CHECK=PASS (no removals/new required fields within checked schema subset)")
    if args.runtime_json:
        runtime = load(args.runtime_json)
        require(set(operations(runtime)) == EXPECTED,
                "Runtime route inventory differs from the nine expected operations")
        issues = compare_runtime(spec, runtime)
        require(not issues,
                "RUNTIME_DRIFT=FAIL (" + str(len(issues)) +
                " differences):\\n" + "\\n".join(issues[:120]))
        print("RUNTIME_COMPARISON=PASS (structural operations, parameters, statuses, schemas)")
        print("WARNING: Does not prove business policy, UTF-8 correctness, E2E or production safety.")
    else:
        print("RUNTIME_COMPARISON=NOT_RUN (requires real exported springdoc JSON)")
    print("HTTP_PLAYGROUND=DISABLED_BY_POLICY (not counted as interactive API test)")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as e:
        print("PORTAL_QUALITY=FAIL: " + str(e), file=sys.stderr)
        sys.exit(1)
