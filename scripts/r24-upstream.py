# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""Collect actual upstream license/NOTICE bytes for R24 review; never invent texts."""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.parse
import zipfile
from pathlib import Path

DOC = re.compile(r"(?i)(?:^|/)(?:LICENSE|LICENCE|COPYING|NOTICE)(?:[._-][^/]*)?$")
LICENSE = re.compile(r"(?i)(?:^|/)(?:LICENSE|LICENCE|COPYING)(?:[._-][^/]*)?$")
MAX_DOC = 1024 * 1024


def safe(value):
    return re.sub(r"[^A-Za-z0-9_.-]", "_", value)[:110]


def identity(row):
    return tuple(row.get(k, "") for k in ("project", "name", "version", "purl"))


def maven_archive(root, row):
    purl = row["purl"]
    if not purl.startswith("pkg:maven/"):
        return None
    base, _, _query = purl.partition("?")
    try:
        group_artifact, version = urllib.parse.unquote(base[len("pkg:maven/"):]).rsplit("@", 1)
        group, artifact = group_artifact.rsplit("/", 1)
    except ValueError:
        return None
    if row["name"] != artifact or row["version"] != version:
        return None
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    base = gradle_home / "caches/modules-2/files-2.1" / group / artifact / version
    jars = sorted(base.glob(f"*/{artifact}-{version}.jar"))
    return jars[0] if len(jars) == 1 else None


def npm_package(root, row):
    purl = row["purl"]
    if not purl.startswith("pkg:npm/"):
        return None
    package = urllib.parse.unquote(purl[len("pkg:npm/"):].split("?", 1)[0].rsplit("@", 1)[0])
    if package != row["name"]:
        return None
    modules = Path(root) / "frontend/node_modules"
    candidates = [modules / package]
    if not candidates[0].exists():
        candidates = sorted(modules.glob("**/node_modules/" + package))
    for candidate in candidates:
        try:
            data = json.loads((candidate / "package.json").read_text(encoding="utf-8"))
            if data.get("name") == package and data.get("version") == row["version"]:
                return candidate
        except (ValueError, OSError):
            continue
    return None


def collect(root, inventory, output):
    root, output = Path(root), Path(output)
    rows = json.loads(Path(inventory).read_text(encoding="utf-8"))
    if not isinstance(rows, list) or not rows:
        raise ValueError("missing resolved runtime license inventory")
    output.mkdir(parents=True, exist_ok=True)
    entries = []
    for row in rows:
        project, name, version, purl = identity(row)
        item = {"project": project, "name": name, "version": version, "purl": purl,
                "status": "MISSING", "docs": [], "source": ""}
        # Metadata BOMs are not binary JARs, and do not imply shipped code.
        if project in ("backend", "portal-bff") and "type=pom" in purl:
            item["status"] = "METADATA_ONLY"
            entries.append(item)
            continue
        jar = maven_archive(root, row) if project in ("backend", "portal-bff") else None
        package = npm_package(root, row) if project == "frontend" else None
        docs = []
        if jar and jar.is_file():
            item["source"] = f"gradle-cache-jar:{jar.name}"
            try:
                with zipfile.ZipFile(jar) as z:
                    for info in sorted(z.infolist(), key=lambda x: x.filename):
                        if (not info.is_dir() and DOC.search(info.filename)
                                and 0 < info.file_size <= MAX_DOC):
                            docs.append((info.filename, z.read(info)))
            except (zipfile.BadZipFile, OSError):
                pass
        if package and package.is_dir():
            item["source"] = "npm:" + package.as_posix().split("node_modules/")[-1]
            for path in sorted(package.iterdir()):
                if path.is_file() and DOC.fullmatch(path.name) and 0 < path.stat().st_size <= MAX_DOC:
                    docs.append((path.name, path.read_bytes()))
        # Explicitly reviewed additional upstream files are retained, never fabricated.
        supplement = root / "docs/06-deployment/r24-upstream" / project / (safe(name) + "-" + safe(version))
        if supplement.is_dir():
            for path in sorted(supplement.iterdir()):
                if path.is_file() and DOC.fullmatch(path.name) and 0 < path.stat().st_size <= MAX_DOC:
                    docs.append(("reviewed-supplement/" + path.name, path.read_bytes()))
        bucket = f"{project}/{safe(name)}-{safe(version)}"
        seen = set()
        for index, (original, data) in enumerate(docs):
            if (original, hashlib.sha256(data).hexdigest()) in seen:
                continue
            seen.add((original, hashlib.sha256(data).hexdigest()))
            name_out = f"{index:02d}-{safe(Path(original).name)}"
            path = output / bucket / name_out
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            item["docs"].append({"path": "compliance/upstream/" + bucket + "/" + name_out,
                                 "sha256": hashlib.sha256(data).hexdigest(),
                                 "origin": original, "license_text": bool(LICENSE.search(original))})
        if any(doc["license_text"] for doc in item["docs"]):
            item["status"] = "COLLECTED_UNREVIEWED"
        entries.append(item)
    index = output / "upstream-index.json"
    index.write_text(json.dumps(entries, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    missing = sum(row["status"] == "MISSING" for row in entries)
    collected = sum(row["status"] == "COLLECTED_UNREVIEWED" for row in entries)
    metadata = sum(row["status"] == "METADATA_ONLY" for row in entries)
    print(f"R24 upstream collection: {collected} collected UNREVIEWED, {missing} MISSING, {metadata} metadata-only")
    for row in entries:
        if row["status"] == "MISSING":
            print("R24_MISSING_LICENSE:", row["project"], row["name"], row["version"], row["purl"])
    return entries


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--root", default=".")
    p.add_argument("--inventory", default="build/r23/license-inventory.json")
    p.add_argument("--output", default="build/r24/upstream")
    args = p.parse_args()
    try:
        collect(args.root, args.inventory, args.output)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print("R24 upstream collection failed:", exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
