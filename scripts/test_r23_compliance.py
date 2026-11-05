# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
"""Run the R23 policy CLI against synthetic positive/negative CycloneDX fixtures."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("r23-compliance.py")

class R23Tests(unittest.TestCase):
    def test_license_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = []
            for project in ("backend", "portal-bff", "frontend"):
                path = root / (project + ".json")
                paths.append(path)
            def fill(value):
                for path in paths:
                    component = {"name": "fixture", "version": "1.0", "type": "library"}
                    if value:
                        component["licenses"] = [{"license": {"id": value}}]
                    path.write_text(json.dumps({
                        "bomFormat": "CycloneDX", "specVersion": "1.6",
                        "components": [component]}))
            def invoke():
                command = [sys.executable, str(SCRIPT)]
                for flag, path in zip(("backend", "portal-bff", "frontend"), paths):
                    command.extend(("--" + flag, str(path)))
                command.extend(("--output", str(root / "out")))
                return subprocess.run(command, capture_output=True, text=True)
            fill("Apache-2.0")
            self.assertEqual(invoke().returncode, 0)
            self.assertTrue((root / "out" / "THIRD-PARTY-NOTICES.md").exists())
            fill("AGPL-3.0-only")
            self.assertEqual(invoke().returncode, 1)
            fill(None)
            self.assertEqual(invoke().returncode, 1)

    def test_strict_spdx_expression_grammar(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("r23_policy", SCRIPT)
        policy = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(policy)
        valid = (
            "MIT", "MIT OR Apache-2.0",
            "(MIT OR Apache-2.0) AND BSD-3-Clause",
            "MIT OR (Apache-2.0 AND ISC)",
            "GPL-2.0-only WITH Classpath-exception-2.0",
        )
        invalid = (
            "MIT OR Apache-2.0 OR", "MIT AND", "OR MIT",
            "MIT (Apache-2.0)", "MIT(Apache-2.0)",
            "(MIT)Apache-2.0", "MIT Apache-2.0",
            "(MIT OR Apache-2.0", "MIT OR Apache-2.0)",
            "MIT OR OR Apache-2.0", "MIT + Apache-2.0",
            "MIT AND (Apache-2.0 OR)",
            "MIT WITH", "MIT WITH RandomException",
            "MIT OR GPL-3.0-only", "MIT/Apache-2.0",
            "MIT @ Apache-2.0", "MIT OR ( )",
            "", None,
        )
        for expression in valid:
            with self.subTest(valid=expression):
                self.assertTrue(policy.license_status(expression)[0])
        for expression in invalid:
            with self.subTest(invalid=expression):
                self.assertFalse(policy.license_status(expression)[0])

    def test_version_pinned_choices_do_not_apply_to_future_releases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def inspect(project, name, version, ids):
                file = root / "bom.json"
                component = {"type": "library", "name": name, "version": version,
                             "licenses": [{"license": {"id": item}} for item in ids]}
                file.write_text(json.dumps({"bomFormat": "CycloneDX",
                                            "specVersion": "1.6",
                                            "components": [component]}))
                cmd = [sys.executable, str(SCRIPT)]
                for label in ("backend", "portal-bff", "frontend"):
                    cmd.extend(("--" + label, str(file)))
                cmd.extend(("--output", str(root / "report")))
                status = subprocess.run(cmd, capture_output=True, text=True).returncode
                return status
            # Every input gets the same synthetic component; only the
            # backend Hibernate exception should match, so this remains red.
            self.assertEqual(inspect("backend", "hibernate-core", "6.6.54.Final",
                                     ["LGPL-2.1-only"]), 1)
            self.assertEqual(inspect("portal-bff", "logback-core", "1.5.35",
                                     ["EPL-2.0", "LGPL-2.1-only"]), 1)

if __name__ == "__main__":
    unittest.main()
