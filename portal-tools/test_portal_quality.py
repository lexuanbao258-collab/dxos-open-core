#!/usr/bin/env python3
"""Self-tests for portal contract checks. Dependency-free."""
import copy
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import portal_quality as q

class PortalQualityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = q.load(q.PUBLIC_SPEC)
    def test_current_spec(self):
        self.assertEqual(len(q.check_document(self.spec)), 9)
    def test_current_html_links(self):
        pages, links = q.check_site(q.ROOT / "preview")
        self.assertGreaterEqual(pages, 4)
        self.assertGreaterEqual(links, 10)
    def test_reject_removed_operation(self):
        doc = copy.deepcopy(self.spec)
        del doc["paths"]["/api/v1/mvp/requests/{id}/confirm"]
        with self.assertRaisesRegex(ValueError, "route mismatch"):
            q.check_document(doc)
        self.assertTrue(any("Removed operation" in s for s in q.check_breaking(self.spec, doc)))
    def test_detect_new_required_field(self):
        doc = copy.deepcopy(self.spec)
        doc["components"]["schemas"]["CreateRequest"]["required"].append("newRequired")
        self.assertTrue(any("New required request field" in s for s in q.check_breaking(self.spec, doc)))
    def test_detect_removed_response_field(self):
        doc = copy.deepcopy(self.spec)
        del doc["components"]["schemas"]["RequestView"]["properties"]["state"]
        self.assertTrue(any("Removed response property" in s for s in q.check_breaking(self.spec, doc)))
    def test_detect_missing_security(self):
        doc = copy.deepcopy(self.spec)
        doc["security"] = []
        with self.assertRaisesRegex(ValueError, "Missing bearerAuth"):
            q.check_document(doc)
    def test_reject_public_server(self):
        doc = copy.deepcopy(self.spec)
        doc["servers"] = [{"url":"https://api.example.org"}]
        with self.assertRaisesRegex(ValueError, "callable Backend server"):
            q.check_document(doc)
    def test_reject_missing_ref(self):
        doc = copy.deepcopy(self.spec)
        doc["paths"]["/api/v1/mvp/requests"]["post"]["requestBody"]["content"]["application/json"]["schema"]["$ref"] = "#/components/schemas/NotPresent"
        with self.assertRaisesRegex(ValueError, "Missing reference"):
            q.check_document(doc)
    def test_runtime_same_spec_is_compatible(self):
        self.assertEqual(q.compare_runtime(self.spec, self.spec), [])

    def test_runtime_wildcard_response_is_compared_not_skipped(self):
        runtime = copy.deepcopy(self.spec)
        op = runtime["paths"]["/api/v1/mvp/requests"]["get"]
        body = op["responses"]["200"]["content"].pop("application/json")
        op["responses"]["200"]["content"]["*/*"] = body
        runtime["components"]["schemas"]["RequestView"]["properties"]["state"]["type"] = "integer"
        differences = q.compare_runtime(self.spec, runtime)
        self.assertTrue(any(".response." in d and "state" in d for d in differences),
                        differences)

    def test_runtime_missing_wildcard_schema_fails_closed(self):
        runtime = copy.deepcopy(self.spec)
        op = runtime["paths"]["/api/v1/mvp/requests"]["get"]
        op["responses"]["200"]["content"] = {"*/*": {}}
        differences = q.compare_runtime(self.spec, runtime)
        self.assertTrue(any("could not compare schema" in d for d in differences),
                        differences)

    def test_detect_response_nullability(self):
        runtime = copy.deepcopy(self.spec)
        runtime["components"]["schemas"]["RequestView"]["properties"]["assigneeId"]["nullable"] = True
        self.assertTrue(any("assigneeId.nullable" in d
                            for d in q.compare_runtime(self.spec, runtime)))

    def test_detect_request_unknown_fields_policy(self):
        runtime = copy.deepcopy(self.spec)
        runtime["components"]["schemas"]["CreateRequest"]["additionalProperties"] = False
        self.assertTrue(any("additionalProperties" in d
                            for d in q.compare_runtime(self.spec, runtime)))

    def test_detect_runtime_status_drift(self):
        runtime = copy.deepcopy(self.spec)
        del runtime["paths"]["/api/v1/mvp/requests"]["get"]["responses"]["401"]
        self.assertTrue(any("HTTP-status" in d
                            for d in q.compare_runtime(self.spec, runtime)))

    def test_empty_baseline_breaks_none(self):
        self.assertEqual(q.check_breaking(self.spec, self.spec), [])

if __name__ == "__main__":
    unittest.main()
