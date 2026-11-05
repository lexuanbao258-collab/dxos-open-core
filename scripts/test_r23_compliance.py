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

if __name__ == "__main__":
    unittest.main()
