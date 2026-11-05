# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""R24: reproducible release candidate archives and fail-closed distribution gate."""
import argparse
import gzip
import hashlib
import io
import json
import re
import sys
import subprocess
import tarfile
from pathlib import Path

TAG = re.compile(r"v[0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[1-9][0-9]*)?$")
SHA = re.compile(r"[a-f0-9]{40}$")
REVIEWERS = {"phanyen06ym-ien", "ngang02012007-crypto"}
JAVA_BASE = re.compile(r"eclipse-temurin:21-jre@sha256:[a-f0-9]{64}$")
GATES = ("upstream_license_notices", "source_and_relink", "frontend_assets",
         "service_images", "base_images", "archive_and_checksums", "release_notes")
SOURCE_GATES = ("source_provenance", "source_license", "sbom_source_boundary",
                "archive_and_checksums", "release_notes")
PERMISSIVE = {"Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "ISC", "0BSD"}

def ident(row):
    return tuple(row[k] for k in ("project", "name", "version", "purl"))



def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def release_notes(changelog, tag):
    version = re.escape(tag[1:])
    match = re.search(rf"(?ms)^## \[{version}\][^\n]*\n(.*?)(?=^## \[|\Z)", changelog)
    require(match and match.group(1).strip(), "CHANGELOG is missing this version's release notes")
    return f"# DX-OS {tag}\n\n" + match.group(1).strip() + "\n"


def normalize_cyclonedx(raw):
    """Remove only volatile generator metadata from packaged CycloneDX JSON.

    CycloneDX runtime components, licenses and dependency edges remain intact.
    Raw generator SBOM files are still used by the R23 compliance policy gate.
    """
    sbom = json.loads(raw)
    require(isinstance(sbom, dict) and sbom.get("bomFormat") == "CycloneDX",
            "invalid CycloneDX SBOM for release")
    sbom.pop("serialNumber", None)  # generator UUID, changes on every build
    metadata = sbom.get("metadata")
    if isinstance(metadata, dict):
        metadata.pop("timestamp", None)  # generator wall-clock time
        component = metadata.get("component")
        if isinstance(component, dict) and isinstance(component.get("externalReferences"), list):
            refs = component["externalReferences"]
            refs = [ref for ref in refs if not (
                isinstance(ref, dict) and ref.get("type") == "build-system"
                and isinstance(ref.get("url"), str)
                and re.fullmatch(
                    r"https://github[.]com/[^/]+/[^/]+/actions/runs/[0-9]+",
                    ref["url"]
                )
            )]
            if refs:
                component["externalReferences"] = refs
            else:
                component.pop("externalReferences", None)
    return (json.dumps(sbom, sort_keys=True, ensure_ascii=False,
                       separators=(",", ":")) + "\n").encode("utf-8")


def package(repo, tag, sha, output, mode="binary"):
    require(mode in {"binary", "source-only"}, "unknown R24 release mode")
    require(TAG.fullmatch(tag), "release tag must be vMAJOR.MINOR.PATCH")
    require(SHA.fullmatch(sha), "source SHA must be a full lowercase commit hash")
    source = Path(repo)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    prefix = f"dxos-{tag}/"
    files = {}
    def add(target, path):
        path = source / path
        require(path.is_file() and not path.is_symlink(), f"missing/unsafe release input: {path}")
        contents = path.read_bytes()
        if target.endswith(".cdx.json"):
            contents = normalize_cyclonedx(contents)
        files[prefix + target] = contents

    if mode == "binary":
        for app in ("backend", "portal-bff"):
            jars = [p for p in (source / app / "build/libs").glob("*.jar")
                    if not p.name.endswith("-plain.jar")]
            require(len(jars) == 1, f"expected exactly one bootJar for {app}, got {len(jars)}")
            add(f"{app}/application.jar", jars[0].relative_to(source))
        frontend = source / "frontend/dist"
        require(frontend.is_dir(), "frontend/dist missing")
        assets = sorted(p for p in frontend.rglob("*") if p.is_file())
        require(assets and (frontend / "index.html").is_file(), "frontend build has no index.html")
        for path in assets:
            add("frontend/" + path.relative_to(frontend).as_posix(), path.relative_to(source))
    else:
        # Git tracked sources only: no build outputs, vendored dependencies or runtime jars.
        proc = subprocess.run(["git", "ls-files", "--cached", "-z"], cwd=source,
                              check=True, capture_output=True)
        tracked = sorted(p.decode() for p in proc.stdout.split(b"\0") if p)
        require(tracked, "source-only archive needs a checked-out git worktree")
        disallowed_binary = {".jar", ".war", ".class", ".zip", ".gz", ".tar",
                             ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico",
                             ".woff", ".woff2", ".ttf", ".otf", ".pdf", ".exe",
                             ".dll", ".mp4"}
        for name in tracked:
            require(not (name == ".env" or name.startswith(".env.") and name != ".env.example"),
                    "secret-like tracked .env file: source publication blocked")
            path = source / name
            if path.suffix.lower() in disallowed_binary:
                continue
            raw = path.read_bytes()
            require(b"\0" not in raw, f"binary data in source-only archive: {name}")
            raw.decode("utf-8")
            add("source/" + name, Path(name))

    # Candidate builds may be incomplete; reviewed upstream texts are added only
    # by the independently approved R24 distribution workstream.
    if mode == "binary":
        upstream = source / "build/r24/upstream"
        require((upstream / "upstream-index.json").is_file(),
                "R24 upstream evidence collector was not run")
        add("compliance/upstream-index.json",
            (upstream / "upstream-index.json").relative_to(source))
        for path in sorted(p for p in upstream.rglob("*") if p.is_file()
                           and p.name != "upstream-index.json"):
            add("compliance/upstream/" + path.relative_to(upstream).as_posix(),
                path.relative_to(source))
    for target, path in {
        "LICENSE": "LICENSE", "NOTICE": "NOTICE",
        "THIRD-PARTY-NOTICES.md": "THIRD-PARTY-NOTICES.md",
        "CHANGELOG.md": "CHANGELOG.md",
        "compliance/backend.cdx.json": "backend/build/reports/cyclonedx/bom.json",
        "compliance/portal-bff.cdx.json": "portal-bff/build/reports/cyclonedx/bom.json",
        "compliance/frontend.cdx.json": "frontend-sbom.json",
        "compliance/license-inventory.json": "build/r23/license-inventory.json",
    }.items():
        add(target, path)
    inventory = json.loads(files[prefix + "compliance/license-inventory.json"])
    require(isinstance(inventory, list) and inventory and
            all(row.get("license_policy_status") == "APPROVED" for row in inventory),
            "R23 dependency policy is not approved")
    notes = release_notes((source / "CHANGELOG.md").read_text(encoding="utf-8"), tag)
    files[prefix + "RELEASE-NOTES.md"] = notes.encode()
    manifest = {"tag": tag, "source_sha": sha, "mode": mode,
                "files_sha256": {name[len(prefix):]: digest(data)
                                 for name, data in sorted(files.items())}}
    files[prefix + "MANIFEST.json"] = (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode()
    archive = output / f"dxos-open-core-{tag[1:]}.tar.gz"
    with archive.open("wb") as raw:
        with gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as gz:
            with tarfile.open(fileobj=gz, mode="w") as tf:
                for name, contents in sorted(files.items()):
                    info = tarfile.TarInfo(name)
                    info.size, info.mode = len(contents), 0o644
                    info.uid = info.gid = info.mtime = 0
                    info.uname = info.gname = ""
                    tf.addfile(info, io.BytesIO(contents))
    (output / "SHA256SUMS").write_text(f"{digest(archive.read_bytes())}  {archive.name}\n")
    (output / "RELEASE-NOTES.md").write_text(notes, encoding="utf-8")
    verify(archive, output / "SHA256SUMS")
    print(f"R24_CANDIDATE_BUILT {archive.name}: {len(files)} files, {len(inventory)} distribution PENDING")


def verify(archive, checksums):
    archive, checksums = Path(archive), Path(checksums)
    line = checksums.read_text().strip()
    require(line == f"{digest(archive.read_bytes())}  {archive.name}", "checksum mismatch")
    with tarfile.open(archive, "r:gz") as tf:
        members = tf.getmembers()
        require(members and all(m.isfile() and not m.issym() and not m.islnk()
                                and not Path(m.name).is_absolute()
                                and ".." not in Path(m.name).parts for m in members),
                "unsafe archive member")
        data = {m.name: tf.extractfile(m).read() for m in members}
    require(len(data) == len(members), "duplicate archive entries")
    manifests = [name for name in data if name.endswith("/MANIFEST.json")]
    require(len(manifests) == 1, "missing/duplicate manifest")
    manifest = json.loads(data[manifests[0]])
    prefix = manifests[0][:-len("MANIFEST.json")]
    require(TAG.fullmatch(manifest.get("tag", "")) and SHA.fullmatch(manifest.get("source_sha", "")),
            "invalid release identity")
    require(prefix == f"dxos-{manifest['tag']}/", "invalid archive root")
    require(manifest.get("mode") in {"binary", "source-only"}, "release mode missing")
    expected = {prefix + name: d for name, d in manifest["files_sha256"].items()}
    require(set(expected) == set(data) - {manifests[0]}, "archive manifest coverage mismatch")
    require(all(digest(data[name]) == value for name, value in expected.items()),
            "archive file digest mismatch")
    print(f"R24_ARCHIVE_VERIFY_PASS {archive.name}")
    return manifest, data, prefix


def gate(archive, checksums, acceptance, tag, sha, notes_file=None):
    manifest, data, prefix = verify(archive, checksums)
    if notes_file is not None:
        require(Path(notes_file).read_bytes() == data[prefix + "RELEASE-NOTES.md"],
                "standalone release notes mismatch verified archive")
    require(manifest["tag"] == tag and manifest["source_sha"] == sha,
            "release candidate identity differs from tag/commit")
    path = Path(acceptance)
    require(path.is_file(), "R24 distribution acceptance file is absent: publication BLOCKED")
    accepted = json.loads(path.read_text())
    require(accepted.get("tag") == tag and accepted.get("source_sha") == sha,
            "R24 acceptance is not for exact tag and source SHA")
    require(accepted.get("mode") == manifest["mode"],
            "R24 approval scope differs from published archive mode")
    require(accepted.get("archive_sha256") == digest(Path(archive).read_bytes()),
            "reviewed archive SHA-256 mismatch")
    require(set(accepted.get("reviewed_by", [])) & REVIEWERS,
            "R24 independent compliance reviewer is missing")
    if manifest["mode"] == "source-only":
        require(not any(name.startswith(prefix + "backend/") or
                        name.startswith(prefix + "portal-bff/") or
                        name.startswith(prefix + "frontend/") or
                        name.startswith(prefix + "compliance/upstream/") for name in data),
                "source-only package unexpectedly includes distributable binaries")
        require(any(name.startswith(prefix + "source/") for name in data),
                "source-only package has no tracked source")
        for key in SOURCE_GATES:
            ref = accepted.get("evidence", {}).get(key, "")
            require(isinstance(ref, str) and ref.startswith("https://"),
                    f"source-only release missing evidence: {key}")
        print("R24_SOURCE_ONLY_GATE_PASS: no binary/GHCR authorization")
        return "source-only"
    require(JAVA_BASE.fullmatch(accepted.get("java_base_image", "")),
            "pinned, reviewed JRE base image digest is required")
    for key in GATES:
        ref = accepted.get("evidence", {}).get(key, "")
        require(isinstance(ref, str) and ref.startswith("https://"),
                f"missing independently reviewable evidence: {key}")
    inventory = json.loads(data[prefix + "compliance/license-inventory.json"])
    expected = {ident(row) for row in inventory}
    require(len(expected) == len(inventory), "duplicate dependency inventory identities")
    index = json.loads(data[prefix + "compliance/upstream-index.json"])
    require(isinstance(index, list), "upstream source manifest missing")
    records = {ident(row): row for row in index}
    require(len(records) == len(index) and set(records) == expected,
            "upstream source manifest does not cover resolved components")
    permissive = accepted.get("license_groups", {}).get("permissive", {})
    require(permissive.get("status") == "CLEARED" and
            isinstance(permissive.get("evidence"), str) and
            permissive["evidence"].startswith("https://"),
            "reviewed permissive-license group disposition is missing")
    exceptions = accepted.get("exceptions", [])
    require(isinstance(exceptions, list), "exception disposition list missing")
    exceptions_by_id = {ident(row): row for row in exceptions}
    require(len(exceptions_by_id) == len(exceptions), "duplicate license exception decisions")
    required_exceptions = set()
    for row in inventory:
        key = ident(row)
        record = records[key]
        # POM dependency management metadata is not bundled code.
        if record.get("status") == "METADATA_ONLY":
            require("type=pom" in row.get("purl", ""), "unproven metadata-only exclusion")
            continue
        require(record.get("status") == "COLLECTED_UNREVIEWED",
                f"unresolved upstream license text for {key}: cannot publish binaries")
        docs = record.get("docs", [])
        require(isinstance(docs, list) and docs, "upstream documents not collected")
        has_license = False
        for doc in docs:
            path = doc.get("path", "")
            require(path.startswith("compliance/upstream/") and
                    path in {name[len(prefix):] for name in data if name.startswith(prefix)},
                    "upstream license text absent from reviewed archive")
            require(doc.get("sha256") == digest(data[prefix + path]),
                    "upstream text checksum mismatch")
            has_license |= bool(doc.get("license_text")) and bool(data[prefix + path])
        require(has_license, f"actual upstream LICENSE/COPYING bytes missing: {key}")
        licenses = set(row.get("licenses", []))
        require(licenses, "empty license declaration cannot be grouped")
        if not licenses <= PERMISSIVE:
            required_exceptions.add(key)
            decision = exceptions_by_id.get(key, {})
            require(decision.get("status") == "CLEARED" and
                    isinstance(decision.get("evidence"), str) and
                    decision["evidence"].startswith("https://"),
                    f"special-license exception not accepted: {key}")
            require(decision.get("license_choice") in licenses or
                    (row.get("license_choice") and
                     decision.get("license_choice") == row.get("license_choice")),
                    f"special-license option not recorded: {key}")
    require(set(exceptions_by_id) == required_exceptions,
            "exception decisions are stale, missing or outside shipped scope")
    print(f"R24_BINARY_GATE_PASS {len(inventory)} resolved rows, "
          f"{len(required_exceptions)} individually reviewed exceptions")
    return "binary"


def main():
    p = argparse.ArgumentParser()
    sp = p.add_subparsers(dest="command", required=True)
    a = sp.add_parser("package")
    a.add_argument("--repo", default=".")
    a.add_argument("--mode", choices=("binary", "source-only"), default="binary")
    a.add_argument("--tag", required=True)
    a.add_argument("--sha", required=True)
    a.add_argument("--output", default="dist/r24")
    a = sp.add_parser("verify")
    a.add_argument("--archive", required=True)
    a.add_argument("--checksums", required=True)
    a = sp.add_parser("gate")
    a.add_argument("--archive", required=True)
    a.add_argument("--checksums", required=True)
    a.add_argument("--acceptance", required=True)
    a.add_argument("--tag", required=True)
    a.add_argument("--sha", required=True)
    a.add_argument("--release-notes", default=None)
    args = p.parse_args()
    try:
        if args.command == "package":
            package(args.repo, args.tag, args.sha, args.output, args.mode)
        elif args.command == "verify":
            verify(args.archive, args.checksums)
        else:
            gate(args.archive, args.checksums, args.acceptance, args.tag, args.sha, args.release_notes)
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError,
            tarfile.TarError, subprocess.CalledProcessError) as exc:
        print("R24_BLOCKED:", exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
