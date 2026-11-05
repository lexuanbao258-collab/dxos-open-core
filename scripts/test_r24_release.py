# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""R24 fail-closed release evidence and archive regression tests."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("r24_release", Path(__file__).with_name("r24-release.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
SHA = "a" * 40
TAG = "v0.1.0"
ROW = {"project": "backend", "name": "example", "version": "1.0",
       "purl": "pkg:maven/test/example@1.0", "license_policy_status": "APPROVED",
       "release_distribution_status": "PENDING"}


class R24ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        def put(name, value):
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value if isinstance(value, bytes) else value.encode())
        put("backend/build/libs/dxos-backend-0.1.0.jar", b"backend-jar")
        put("backend/build/libs/dxos-backend-0.1.0-plain.jar", b"plain-ignored")
        put("portal-bff/build/libs/dxos-portal-bff-0.1.0.jar", b"bff-jar")
        put("frontend/dist/index.html", "<html>demo</html>")
        for f in ("LICENSE", "NOTICE", "THIRD-PARTY-NOTICES.md"):
            put(f, "example")
        put("CHANGELOG.md", "## [Unreleased]\n\n## [0.1.0] - 2026-11-04\n\n### Added\n- test release\n")
        for f in ("backend/build/reports/cyclonedx/bom.json",
                  "portal-bff/build/reports/cyclonedx/bom.json", "frontend-sbom.json"):
            put(f, '{"bomFormat":"CycloneDX"}')
        put("build/r23/license-inventory.json", json.dumps([ROW]))
        self.output = self.repo / "output"
        release.package(self.repo, TAG, SHA, self.output)
        self.archive = self.output / "dxos-v0.1.0.tar.gz"
        self.sums = self.output / "SHA256SUMS"
        self.acceptance = self.repo / "docs/06-deployment/r24-acceptance.json"

    def clearance(self):
        # Package the example upstream text before testing positive clearance.
        text = self.repo / "docs/06-deployment/r24-upstream/example-LICENSE.txt"
        text.parent.mkdir(parents=True, exist_ok=True)
        text.write_text("reviewed upstream license")
        release.package(self.repo, TAG, SHA, self.output)
        doc = {"tag": TAG, "source_sha": SHA, "reviewed_by": ["phanyen06ym-ien"],
               "java_base_image": "eclipse-temurin:21-jre@sha256:" + "b" * 64,
               "evidence": {k: "https://example.test/evidence" for k in release.GATES},
               "components": [{"project": ROW["project"], "name": ROW["name"],
                               "version": ROW["version"], "purl": ROW["purl"],
                               "status": "CLEARED", "evidence": "https://example.test/component",
                               "license_path": "compliance/upstream/example-LICENSE.txt"}]}
        self.acceptance.parent.mkdir(parents=True, exist_ok=True)
        self.acceptance.write_text(json.dumps(doc))
        return doc

    def test_archive_reproducible_and_verified(self):
        first = release.digest(self.archive.read_bytes())
        release.package(self.repo, TAG, SHA, self.output)
        self.assertEqual(first, release.digest(self.archive.read_bytes()))
        release.verify(self.archive, self.sums)

    def test_malformed_tag_and_checksum_rejected(self):
        with self.assertRaises(ValueError):
            release.package(self.repo, "../../bad", SHA, self.output)
        self.sums.write_text("deadbeef  dxos-v0.1.0.tar.gz\n")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            release.verify(self.archive, self.sums)

    def test_missing_clearance_blocks_publish(self):
        with self.assertRaisesRegex(ValueError, "publication BLOCKED"):
            release.gate(self.archive, self.sums, self.acceptance, TAG, SHA)

    def test_partial_or_wrong_sha_blocks_publish(self):
        doc = self.clearance()
        doc["components"][0]["status"] = "PENDING"
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "PENDING"):
            release.gate(self.archive, self.sums, self.acceptance, TAG, SHA)
        doc["components"][0]["status"] = "CLEARED"
        doc["source_sha"] = "b" * 40
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "exact tag"):
            release.gate(self.archive, self.sums, self.acceptance, TAG, SHA)

    def test_explicit_clearance_passes(self):
        self.clearance()
        release.gate(self.archive, self.sums, self.acceptance, TAG, SHA)


if __name__ == "__main__":
    unittest.main()
