# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""R24 contract tests: candidate integrity, authorization boundaries, and RC modes."""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("r24_release", Path(__file__).with_name("r24-release.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
SHA = "a" * 40
TAG = "v0.2.0-rc.1"
ROW = {"project": "backend", "name": "example", "version": "1.0",
       "purl": "pkg:maven/test/example@1.0?type=jar",
       "licenses": ["Apache-2.0"], "license_policy_status": "APPROVED",
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
        self.put = put
        put("backend/build/libs/dxos-backend-0.1.0.jar", b"backend-jar")
        put("backend/build/libs/dxos-backend-0.1.0-plain.jar", b"plain-ignored")
        put("portal-bff/build/libs/dxos-portal-bff-0.1.0.jar", b"bff-jar")
        put("frontend/dist/index.html", "<html>demo</html>")
        for name in ("LICENSE", "NOTICE", "THIRD-PARTY-NOTICES.md"):
            put(name, "example")
        put("README.md", "source")
        put("CHANGELOG.md", "## [Unreleased]\n\n## [0.2.0-rc.1] - 2026-11-05\n\n### Added\n- test release\n")
        for name in ("backend/build/reports/cyclonedx/bom.json",
                     "portal-bff/build/reports/cyclonedx/bom.json", "frontend-sbom.json"):
            put(name, '{"bomFormat":"CycloneDX"}')
        put("build/r23/license-inventory.json", json.dumps([ROW]))
        self.upstream_path = "build/r24/upstream/backend/example-1.0/00-LICENSE.txt"
        put(self.upstream_path, "actual upstream license bytes")
        self.index_row = {
            "project": ROW["project"], "name": ROW["name"], "version": ROW["version"],
            "purl": ROW["purl"], "status": "COLLECTED_UNREVIEWED",
            "docs": [{"path": "compliance/upstream/backend/example-1.0/00-LICENSE.txt",
                      "sha256": release.digest(b"actual upstream license bytes"),
                      "origin": "META-INF/LICENSE.txt", "license_text": True}],
            "source": "gradle-cache-jar:example-1.0.jar"}
        put("build/r24/upstream/upstream-index.json", json.dumps([self.index_row]))
        self.output = self.repo / "output"
        self.make()
        self.acceptance = self.repo / "acceptance.json"

    def make(self, mode="binary", tag=TAG):
        release.package(self.repo, tag, SHA, self.output, mode)
        self.archive = self.output / f"dxos-open-core-{tag[1:]}.tar.gz"
        self.sums = self.output / "SHA256SUMS"

    def accept(self, mode="binary"):
        doc = {"tag": TAG, "source_sha": SHA, "mode": mode,
               "archive_sha256": release.digest(self.archive.read_bytes()),
               "reviewed_by": ["phanyen06ym-ien"],
               "evidence": {key: "https://example.test/verified" for key in
                            (release.GATES if mode == "binary" else release.SOURCE_GATES)}}
        if mode == "binary":
            doc.update({"java_base_image": "eclipse-temurin:21-jre@sha256:" + "b" * 64,
                        "license_groups": {"permissive": {"status": "CLEARED",
                                                           "evidence": "https://example.test/group"}},
                        "exceptions": []})
        self.acceptance.write_text(json.dumps(doc))
        return doc

    def gate(self):
        return release.gate(self.archive, self.sums, self.acceptance, TAG, SHA,
                            self.output / "RELEASE-NOTES.md")

    def test_archive_reproducible_and_verified(self):
        first = release.digest(self.archive.read_bytes())
        self.make()
        self.assertEqual(first, release.digest(self.archive.read_bytes()))
        release.verify(self.archive, self.sums)

    def test_invalid_tag_and_checksum_rejected(self):
        with self.assertRaises(ValueError):
            release.package(self.repo, "../../bad", SHA, self.output)
        self.sums.write_text("bad  dxos-open-core-0.2.0-rc.1.tar.gz\n")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            release.verify(self.archive, self.sums)

    def test_missing_clearance_blocks(self):
        with self.assertRaisesRegex(ValueError, "publication BLOCKED"):
            self.gate()

    def test_binary_grouped_clearance_can_pass_with_upstream_bytes(self):
        self.accept()
        self.assertEqual(self.gate(), "binary")

    def test_reviewed_digest_rejects_repackaged_archive_same_tag_sha(self):
        self.accept()
        self.put("NOTICE", "tampered but internally consistent")
        self.make()
        release.verify(self.archive, self.sums)
        with self.assertRaisesRegex(ValueError, "reviewed archive SHA-256 mismatch"):
            self.gate()

    def test_mismatched_standalone_notes_rejected(self):
        self.accept()
        (self.output / "RELEASE-NOTES.md").write_text("wrong notes")
        with self.assertRaisesRegex(ValueError, "release notes mismatch"):
            self.gate()

    def test_stale_sha_or_uncleared_group_rejected(self):
        doc = self.accept()
        doc["source_sha"] = "b" * 40
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "exact tag"):
            self.gate()
        doc["source_sha"] = SHA
        doc["license_groups"]["permissive"]["status"] = "PENDING"
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "permissive-license group"):
            self.gate()

    def test_missing_upstream_license_blocks_binary(self):
        self.accept()
        (self.repo / self.upstream_path).unlink()
        self.make()
        doc = json.loads(self.acceptance.read_text())
        doc["archive_sha256"] = release.digest(self.archive.read_bytes())
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "absent from reviewed archive"):
            self.gate()

    def test_epl_requires_individual_exception(self):
        row = dict(ROW, licenses=["EPL-2.0"])
        self.put("build/r23/license-inventory.json", json.dumps([row]))
        self.make()
        doc = self.accept()
        with self.assertRaisesRegex(ValueError, "special-license exception"):
            self.gate()
        doc["exceptions"] = [{"project": row["project"], "name": row["name"],
                              "version": row["version"], "purl": row["purl"],
                              "status": "CLEARED", "license_choice": "EPL-2.0",
                              "evidence": "https://example.test/epl"}]
        self.acceptance.write_text(json.dumps(doc))
        self.assertEqual(self.gate(), "binary")

    def test_rc_source_only_does_not_ship_compiled_assets_or_require_131_clearances(self):
        self.put("frontend/src/example.ts", "export const example = 1;")
        self.put("backend/gradle/wrapper/gradle-wrapper.jar", b"example binary jar")
        subprocess.run(["git", "init", "-q"], cwd=self.repo, check=True)
        subprocess.run(["git", "add", "README.md", "LICENSE", "NOTICE", "CHANGELOG.md",
                        "frontend/src/example.ts",
                        "backend/gradle/wrapper/gradle-wrapper.jar"], cwd=self.repo, check=True)
        self.make("source-only")
        doc = self.accept(mode="source-only")
        self.assertEqual(self.gate(), "source-only")
        _, data, prefix = release.verify(self.archive, self.sums)
        self.assertIn(prefix + "source/frontend/src/example.ts", data)
        self.assertNotIn(prefix + "backend/application.jar", data)
        self.assertNotIn(prefix + "frontend/index.html", data)
        self.assertNotIn(prefix + "source/backend/gradle/wrapper/gradle-wrapper.jar", data)
        doc["mode"] = "binary"
        self.acceptance.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "scope differs"):
            self.gate()

    def test_rc_tag_validation(self):
        self.assertTrue(release.TAG.fullmatch(TAG))
        self.assertFalse(release.TAG.fullmatch("v0.2.0-rc.0"))
        self.assertFalse(release.TAG.fullmatch("v0.2.0-rc.1/evil"))


if __name__ == "__main__":
    unittest.main()
