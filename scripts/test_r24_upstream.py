# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify R24 collector never invents missing upstream LICENSE evidence."""
import importlib.util
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("r24_upstream", Path(__file__).with_name("r24-upstream.py"))
upstream = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(upstream)


class UpstreamTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.output = self.root / "build/r24/upstream"
        self.inventory = self.root / "inventory.json"
        self.gradle_home = self.root / "gradle"
        self.maven = {"project": "backend", "name": "sample", "version": "1.2.3",
                      "purl": "pkg:maven/com.example/sample@1.2.3?type=jar"}
        self.npm = {"project": "frontend", "name": "demo", "version": "4.5.6",
                    "purl": "pkg:npm/demo@4.5.6"}
        self.inventory.write_text(json.dumps([self.maven, self.npm]))

    def collect(self):
        with patch.dict(os.environ, {"GRADLE_USER_HOME": str(self.gradle_home)}):
            return upstream.collect(self.root, self.inventory, self.output)

    def test_extracts_actual_jar_license_and_npm_notice(self):
        jar = self.gradle_home / "caches/modules-2/files-2.1/com.example/sample/1.2.3/hash/sample-1.2.3.jar"
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("META-INF/LICENSE.txt", "upstream java license")
            z.writestr("META-INF/NOTICE", "java notice")
        npm = self.root / "frontend/node_modules/demo"
        npm.mkdir(parents=True)
        (npm / "package.json").write_text('{"name":"demo","version":"4.5.6","license":"MIT"}')
        (npm / "LICENSE").write_text("upstream npm license")
        rows = self.collect()
        self.assertEqual([r["status"] for r in rows], ["COLLECTED_UNREVIEWED", "COLLECTED_UNREVIEWED"])
        self.assertTrue(any(x["license_text"] for x in rows[0]["docs"]))
        self.assertEqual((self.output / "backend/sample-1.2.3/00-LICENSE.txt").read_text(), "upstream java license")
        self.assertTrue(any(x["origin"] == "NOTICE" for x in rows[1]["docs"]) is False)
        self.assertTrue((self.output / "upstream-index.json").exists())

    def test_npm_package_metadata_without_license_text_is_missing(self):
        npm = self.root / "frontend/node_modules/demo"
        npm.mkdir(parents=True)
        (npm / "package.json").write_text('{"name":"demo","version":"4.5.6","license":"MIT"}')
        rows = self.collect()
        self.assertEqual(rows[1]["status"], "MISSING")
        self.assertEqual(rows[1]["docs"], [])

    def test_scoped_npm_purl_with_unscoped_component_name(self):
        component = {"project": "frontend", "name": "be-vietnam-pro", "version": "5.3.0",
                     "purl": "pkg:npm/%40fontsource/be-vietnam-pro@5.3.0"}
        self.inventory.write_text(json.dumps([component]))
        npm = self.root / "frontend/node_modules/@fontsource/be-vietnam-pro"
        npm.mkdir(parents=True)
        (npm / "package.json").write_text('{"name":"@fontsource/be-vietnam-pro","version":"5.3.0"}')
        (npm / "LICENSE").write_text("upstream font license")
        result = self.collect()
        self.assertEqual(result[0]["status"], "COLLECTED_UNREVIEWED")
        self.assertTrue(result[0]["docs"][0]["license_text"])

    def test_pom_is_not_shipped_jar(self):
        rows = [dict(self.maven, purl="pkg:maven/com.example/sample@1.2.3?type=pom")]
        self.inventory.write_text(json.dumps(rows))
        result = self.collect()
        self.assertEqual(result[0]["status"], "METADATA_ONLY")


if __name__ == "__main__":
    unittest.main()
