"""Synthetic Phase 78 gate tests. No account, ROM, save or signing files are read."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from okubi_phase78_integration_gate import MANUAL_BLOCKERS, summarize


class Phase78GateTests(unittest.TestCase):
    def setUp(self):
        self.phase70 = {
            "automated_release_gate": "pass",
            "tracked_files_examined": 351,
            "public_release_authorized": False,
        }
        self.phase72 = {
            "automated_checks": "pass",
            "public_release_authorized": False,
        }
        self.unified = {"status": "PASS"}

    def report(self):
        return summarize(self.phase70, self.phase72, self.unified)

    def test_source_gate_can_pass_without_authorizing_release(self):
        report = self.report()
        self.assertEqual(report["automated_integration_gate"], "pass")
        self.assertFalse(report["public_release_authorized"])
        self.assertFalse(report["merge_performed"])
        self.assertFalse(report["ci_build_artifacts_verified_by_this_script"])
        self.assertEqual(report["source_files_examined"], 351)
        self.assertEqual(tuple(report["manual_release_blockers"]), MANUAL_BLOCKERS)

    def test_bad_private_file_audit_blocks_integration(self):
        self.phase70["automated_release_gate"] = "fail"
        self.assertIn("phase70_contract_not_passed_or_release_flag_unsafe",
                      self.report()["failed_check_codes"])

    def test_license_source_audit_failure_blocks_integration(self):
        self.phase72["automated_checks"] = "fail"
        self.assertEqual(self.report()["automated_integration_gate"], "fail")

    def test_canonical_android_or_cross_save_contract_failure_blocks(self):
        self.unified["status"] = "FAIL"
        self.assertIn("unified_contract_not_passed",
                      self.report()["failed_check_codes"])

    def test_accidental_release_flag_fails_closed(self):
        self.phase70["public_release_authorized"] = True
        self.assertEqual(self.report()["automated_integration_gate"], "fail")
        self.phase70["public_release_authorized"] = False
        self.phase72["public_release_authorized"] = True
        self.assertEqual(self.report()["automated_integration_gate"], "fail")

    def test_missing_audit_results_fail_closed(self):
        report = summarize({}, {}, {})
        self.assertEqual(report["automated_integration_gate"], "fail")
        self.assertEqual(len(report["failed_check_codes"]), 3)
        self.assertFalse(report["public_release_authorized"])


if __name__ == "__main__":
    unittest.main()
