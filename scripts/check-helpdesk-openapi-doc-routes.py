#!/usr/bin/env python3
"""Compare registered HelpdeskController Java HTTP routes with the public docs spec.

Static check only. It does NOT launch Spring Boot, verify schema or test JWT/ACL.
The parser intentionally checks the narrow controller annotation format; if the
source syntax changes, this script should be updated and reviewed too.
"""
from pathlib import Path
import argparse
import json
import re
import sys

REPO = Path(__file__).resolve().parents[1]
CONTROLLER = REPO / "backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java"

def routes_in_source(text):
    base = re.search(r'@RequestMapping\("([^"]+)"\)', text)
    if not base:
        raise ValueError("HelpdeskController missing literal class @RequestMapping")
    methods = list(re.finditer(r'@(Get|Post)Mapping(?:\("([^"]*)"\))?', text))
    routes = []
    for m in methods:
        suffix = (m.group(2) or "")
        routes.append((m.group(1).lower(), base.group(1) + suffix))
    if len(routes) != len(set(routes)):
        raise ValueError("Duplicate Java endpoint declarations")
    return set(routes)

def routes_in_spec(document):
    return {(m.lower(), p) for p, methods in document.get("paths", {}).items()
            for m in methods if m.lower() in {"get", "post", "put", "patch", "delete"}}

def check(controller, spec):
    java = routes_in_source(Path(controller).read_text(encoding="utf-8"))
    doc = routes_in_spec(json.loads(Path(spec).read_text(encoding="utf-8")))
    if java != doc or len(java) != 9:
        raise ValueError(f"Route drift / unexpected count.\nJava only: {sorted(java-doc)}\nDocs only: {sorted(doc-java)}\nJava total={len(java)}")
    return java

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--controller", type=Path, default=CONTROLLER)
    parser.add_argument("--spec", type=Path, required=True)
    args = parser.parse_args()
    try:
        routes = check(args.controller, args.spec)
        print(f"JAVA_VS_DOCS_ROUTES=PASS ({len(routes)} registered methods)")
        print("SCHEMA_AND_RUNTIME=NOT_VERIFIED (requires real Springdoc export)")
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("JAVA_VS_DOCS_ROUTES=FAIL: " + str(exc), file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
