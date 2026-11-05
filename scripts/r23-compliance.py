# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""R23: license gate over three CycloneDX SBOMs; generate a notices inventory."""
import argparse
import json
import re
import sys
from pathlib import Path

ALLOWED = set("""0BSD Apache-2.0 BSD-2-Clause BSD-3-Clause CDDL-1.0 CDDL-1.1
CC0-1.0 EDL-1.0 EPL-1.0 EPL-2.0 ISC MIT MPL-2.0 OFL-1.1
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

def license_status(value):
    val = ALIASES.get(value.lower().strip(), value.strip())
    if val in ALLOWED or val in EXCEPTIONS:
        return True, val
    tokens = [t for t in re.split(r"\s+(?:AND|OR)\s+|[()]", val) if t]
    if len(tokens) > 1 and all(t in ALLOWED for t in tokens):
        return True, val
    return False, val or "UNDECLARED"

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
        rows.append({"project": project, "name": c["name"],
                     "version": c["version"], "purl": c.get("purl") or "",
                     "licenses": sorted(set(y for _, y in result)),
                     "approved": all(ok for ok, _ in result)})
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
              "| Project | Dependency | Version | Declared licenses | PURL |",
              "| --- | --- | --- | --- | --- |"]
    for r in rows:
        values = [r["project"], r["name"], r["version"],
                  ", ".join(r["licenses"]), r["purl"]]
        header.append("| " + " | ".join(str(v).replace("|", "\\|").replace("\n", " ") for v in values) + " |")
    header += ["", "## Open compliance work", "",
               "Validate upstream required notice texts, bundled services,",
               "copyright provenance and release packaging separately under R23.", ""]
    (destination / "THIRD-PARTY-NOTICES.md").write_text(
        "\n".join(header), encoding="utf-8")
    bad = [r for r in rows if not r["approved"]]
    print(f"R23: {len(rows)} components, {len(bad)} unapproved or unknown license declarations")
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
