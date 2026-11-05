# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""R23: license gate over three CycloneDX SBOMs; generate a notices inventory."""
import argparse
import json
import re
import sys
from pathlib import Path

ALLOWED = set("""0BSD Apache-2.0 BSD-2-Clause BSD-3-Clause CDDL-1.0 CDDL-1.1
CC0-1.0 EDL-1.0 EPL-1.0 EPL-2.0 ISC MIT MIT-0 BlueOak-1.0.0 MPL-2.0 OFL-1.1
Unicode-3.0 Unicode-DFS-2016 Unlicense Zlib""".split())
ALIASES = {
    "the apache software license, version 2.0": "Apache-2.0",
    "apache software license, version 2.0": "Apache-2.0",
    "apache license, version 2.0": "Apache-2.0",
    "apache license version 2.0": "Apache-2.0",
    "the mit license": "MIT",
    "mit license": "MIT",
    "eclipse public license 2.0": "EPL-2.0",
    "eclipse public license - v 2.0": "EPL-2.0",
    "isc license": "ISC",
}
EXCEPTIONS = {"GPL-2.0-only WITH Classpath-exception-2.0",
              "GPL-2.0-or-later WITH Classpath-exception-2.0",
              "GPL-2.0-with-classpath-exception"}

# Tokenize the entire expression; any unrecognized byte is a syntax error.
# This is a strict subset of SPDX license-expression grammar (AND, OR,
# parentheses and the specifically reviewed WITH exceptions).
SPDX_TOKEN = re.compile(r"\s*([()]|[A-Za-z0-9][A-Za-z0-9.+-]*)")

def license_status(value):
    if not isinstance(value, str) or not value.strip():
        return False, "UNDECLARED"
    val = ALIASES.get(value.lower().strip(), value.strip())
    # Legacy reviewed spelling is retained only for this exact identifier.
    if val == "GPL-2.0-with-classpath-exception":
        return True, val

    tokens = []
    position = 0
    while position < len(val):
        match = SPDX_TOKEN.match(val, position)
        if match is None:
            if val[position:].strip():
                return False, val
            break
        tokens.append(match.group(1))
        position = match.end()
        if len(tokens) > 128:
            return False, val

    index = 0

    def atom(depth=0):
        nonlocal index
        if index >= len(tokens) or depth > 32:
            raise ValueError("expected SPDX license")
        token = tokens[index]
        index += 1
        if token == "(":
            accepted = parse_or(depth + 1)
            if index >= len(tokens) or tokens[index] != ")":
                raise ValueError("unbalanced parentheses")
            index += 1
            return accepted
        if token in {"AND", "OR", "WITH", ")"}:
            raise ValueError("invalid SPDX operand")
        if index < len(tokens) and tokens[index] == "WITH":
            index += 1
            if index >= len(tokens) or tokens[index] in {"AND", "OR", "WITH", "(", ")"}:
                raise ValueError("missing SPDX exception")
            exception = tokens[index]
            index += 1
            # Unsupported WITH exceptions are invalid at the expression
            # level, including when another OR branch is acceptable.
            if token + " WITH " + exception not in EXCEPTIONS:
                raise ValueError("unsupported SPDX WITH exception")
            return True
        return token in ALLOWED

    def parse_and(depth=0):
        nonlocal index
        accepted = atom(depth)
        while index < len(tokens) and tokens[index] == "AND":
            index += 1
            rhs = atom(depth)
            accepted = accepted and rhs
        return accepted

    def parse_or(depth=0):
        nonlocal index
        accepted = parse_and(depth)
        while index < len(tokens) and tokens[index] == "OR":
            index += 1
            rhs = parse_and(depth)
            accepted = accepted and rhs
        return accepted

    try:
        accepted = parse_or()
        if index != len(tokens):
            return False, val
        return accepted, val
    except (ValueError, RecursionError):
        return False, val

def read_bom(path, project):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if data.get("bomFormat") != "CycloneDX" or not data.get("specVersion"):
        raise ValueError(project + ": invalid CycloneDX format")
    components = data.get("components")
    if not isinstance(components, list) or not components:
        raise ValueError(project + ": missing components")
    rows = []
    for c in components:
        if c.get("type") not in {"library", "framework", "application", "file"}:
            continue
        if not c.get("name") or not c.get("version"):
            raise ValueError(project + ": missing name/version")
        identifiers = []
        for record in c.get("licenses") or []:
            if not isinstance(record, dict):
                continue
            if "expression" in record:
                identifiers.append(record["expression"])
            value = record.get("license") or {}
            if isinstance(value, dict):
                identifiers.append(value.get("id") or value.get("name") or "")
        result = [license_status(x) for x in (identifiers or [""])]
        declared = sorted(set(y for _, y in result))
        approved = all(ok for ok, _ in result)
        choice = ""
        # Logback 1.5.34 is dual-licensed; choose its EPL-2.0 option.
        # https://logback.qos.ch/license.html
        if c["name"] in {"logback-classic", "logback-core"} and c["version"] == "1.5.34" \
                and set(declared) == {"EPL-2.0", "LGPL-2.1-only"}:
            approved, choice = True, "EPL-2.0"
        # Hibernate ORM 6.6 is LGPL-2.1; review binary redistribution
        # duties before release, rather than re-labeling the dependency.
        # https://hibernate.org/orm/releases/6.6/
        if project == "backend" and c["name"] == "hibernate-core" \
                and c["version"] == "6.6.53.Final" and declared == ["LGPL-2.1-only"]:
            approved, choice = True, "LGPL-2.1-only (conditional redistribution review)"
        # CI policy approval and rights/redistribution acceptance are
        # independent gates. Do not turn a reviewed SPDX ID into a release PASS.
        obligations = ["CHECK_UPSTREAM_LICENSE_NOTICE_AND_ATTRIBUTION",
                       "R24_VERIFY_RELEASE_PACKAGING"]
        if c["name"] == "hibernate-core" and c["version"] == "6.6.53.Final":
            obligations.append("LGPL_SOURCE_LINKING_RELINK_DISTRIBUTION_REVIEW")
        if c["name"] in {"logback-core", "logback-classic"} and c["version"] == "1.5.34":
            obligations.append("EPL_LICENSE_NOTICE_SOURCE_REVIEW")
        if "BlueOak-1.0.0" in declared:
            obligations.append("BLUEOAK_LICENSE_TEXT_OR_LINK")
        if "MIT-0" in declared:
            obligations.append("MIT_0_LICENSE_TEXT")
        license_links = {
            "BlueOak-1.0.0": "https://spdx.org/licenses/BlueOak-1.0.0.html",
            "MIT-0": "https://spdx.org/licenses/MIT-0.html",
            "EPL-2.0": "https://www.eclipse.org/legal/epl-2.0/",
            "LGPL-2.1-only": "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
        }
        rows.append({"project": project, "name": c["name"],
                     "version": c["version"], "purl": c.get("purl") or "",
                     "licenses": declared, "license_choice": choice,
                     "approved": approved,
                     "license_policy_status": "APPROVED" if approved else "REVIEW",
                     "release_distribution_status": "PENDING",
                     "release_distribution_owner": "R24 / Yến",
                     "release_distribution_requirements": obligations,
                     "license_reference_links": {lic: license_links[lic] for lic in declared
                                                 if lic in license_links}})
    if not rows:
        raise ValueError(project + ": no eligible components")
    return rows

def audit(paths, destination):
    rows = []
    for project, path in paths:
        rows += read_bom(path, project)
    rows.sort(key=lambda r: (r["project"], r["name"].lower(), r["version"], r["purl"]))
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "license-inventory.json").write_text(
        json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    header = ["<!--", "SPDX-FileCopyrightText: 2026 TRICORE contributors",
              "SPDX-License-Identifier: Apache-2.0", "-->", "",
              "# THIRD-PARTY-NOTICES — generated inventory", "",
              "Generated from resolved CycloneDX SBOMs. This is not a legal",
              "certification or a substitute for upstream license and NOTICE texts.", "",
              "| Project | Dependency | Version | Declared licenses | License choice | PURL |",
              "| --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        values = [r["project"], r["name"], r["version"],
                  ", ".join(r["licenses"]), r["license_choice"], r["purl"]]
        header.append("| " + " | ".join(str(v).replace("|", "\\|").replace("\n", " ") for v in values) + " |")
    header += ["", "## Open compliance work", "",
               "Validate upstream required notice texts, bundled services,",
               "copyright provenance and release packaging separately under R23.", ""]
    (destination / "THIRD-PARTY-NOTICES.md").write_text(
        "\n".join(header), encoding="utf-8")
    bad = [r for r in rows if not r["approved"]]
    print(f"R23: {len(rows)} components, {len(bad)} unapproved or unknown license declarations")
    print(f"R23 release distribution: {sum(r['release_distribution_status'] == 'PENDING' for r in rows)} PENDING; independent R24 sign-off required")
    for r in bad:
        print("R23 REVIEW:", r["project"], r["name"], r["version"], r["licenses"], file=sys.stderr)
    return not bad

def main():
    p = argparse.ArgumentParser()
    for name in ("backend", "portal-bff", "frontend"):
        p.add_argument("--" + name, type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    try:
        ok = audit([("backend", a.backend), ("portal-bff", a.portal_bff),
                    ("frontend", a.frontend)], a.output)
        return 0 if ok else 1
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print("R23 failed:", exc, file=sys.stderr)
        return 2

if __name__ == "__main__":
    sys.exit(main())
