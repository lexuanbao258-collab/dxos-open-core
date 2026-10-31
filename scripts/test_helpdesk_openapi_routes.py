#!/usr/bin/env python3
"""Negative tests for source-to-doc route alignment."""
import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).with_name("check-helpdesk-openapi-doc-routes.py")
spec = importlib.util.spec_from_file_location("docs_routes", MODULE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class RouteTests(unittest.TestCase):
    def test_real_controller_has_nine_routes(self):
        source = mod.CONTROLLER.read_text(encoding="utf-8")
        self.assertEqual(len(mod.routes_in_source(source)), 9)
    def test_minimal_controller(self):
        text = '@RequestMapping("/base") class Controller { @GetMapping public void list(){} @PostMapping("/{id}/change") void change(){} }'
        self.assertEqual(mod.routes_in_source(text), {("get", "/base"), ("post", "/base/{id}/change")})
    def test_duplicate_route_fails(self):
        text = '@RequestMapping("/base") class C { @GetMapping void a(){} @GetMapping void b(){} }'
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            mod.routes_in_source(text)
    def test_missing_base_fails(self):
        with self.assertRaisesRegex(ValueError, "missing"):
            mod.routes_in_source('@GetMapping public void a() {}')

if __name__ == "__main__":
    unittest.main()
