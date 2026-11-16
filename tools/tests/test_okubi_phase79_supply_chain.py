"""Phase79 source-only guard regressions. No downloads, ROMs or private files."""
from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import okubi_phase79_supply_chain as guard


class Phase79SupplyChainTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2]
        self.evidence = json.loads((root / guard.EVIDENCE).read_text("utf-8"))
        self.bundler = (root / guard.BUNDLER).read_text("utf-8")

    def result(self):
        return guard.inspect(self.evidence, self.bundler)

    def test_audited_hashes_match_exact_powershell_runtime_checks(self):
        result = self.result()
        self.assertEqual(result["static_integrity_gate"], "pass", result["issues"])
        self.assertEqual(result["audited_windows_cores"], 3)
        self.assertFalse(result["public_release_authorized"])
        self.assertEqual(result["windows_binary_source_provenance"], "unverified")

    def test_wrong_dll_sha_must_fail_even_if_zip_hash_is_allowed(self):
        self.evidence["cores"][0]["dll_sha256"] = "0" * 64
        self.assertIn("core_bundler_sha_pair_mismatch", self.result()["issues"])

    def test_wrong_zip_hash_must_fail(self):
        self.evidence["cores"][1]["zip_sha256"] = "1" * 64
        self.assertEqual(self.result()["static_integrity_gate"], "fail")

    def test_duplicate_or_missing_core_fails(self):
        self.evidence["cores"].append(copy.deepcopy(self.evidence["cores"][0]))
        self.assertIn("required_core_evidence_missing_or_duplicate", self.result()["issues"])

    def test_unreviewed_source_claims_fail(self):
        self.evidence["cores"][2]["corresponding_windows_source_commit"] = "a" * 40
        self.assertIn("windows_source_provenance_asserted_without_independent_review",
                      self.result()["issues"])

    def test_fake_approval_flag_fails(self):
        self.evidence["releases_authorized"] = True
        self.assertIn("audited_evidence_metadata_invalid", self.result()["issues"])
        self.assertFalse(self.result()["public_release_authorized"])

    def test_runtime_guard_cannot_be_removed(self):
        self.bundler = self.bundler.replace(
            "$sha -cne $package.AuditedDllSha256", "$false"
        )
        self.assertIn("core_runtime_fingerprint_guard_missing",
                      self.result()["issues"])

    def test_unknown_binary_or_domain_fails(self):
        self.evidence["cores"][0]["source_repository"] = "https://evil.example/core"
        self.assertIn("core_license_or_source_repository_changed", self.result()["issues"])
        self.evidence["official_buildbot"] = "https://example.com/"
        self.assertIn("unapproved_binary_upstream", self.result()["issues"])

    def test_audit_with_real_repo_is_metadata_only_and_never_releases(self):
        root = Path(__file__).resolve().parents[2]
        report = guard.audit(root)
        self.assertEqual(report["static_integrity_gate"], "pass", report["issues"])
        self.assertEqual(len(report["missing_public_release_evidence"]), 6)
        self.assertFalse(report["merge_performed"])
        self.assertEqual(report["physical_device_qa"], "not_tested_by_this_tool")


if __name__ == "__main__":
    unittest.main()
