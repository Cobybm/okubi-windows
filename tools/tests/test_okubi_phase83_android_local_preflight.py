"""Phase83 local-only Android signing and Windows profile safety regressions."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from okubi_release_guard import ApkIdentity, ReleaseCheckError
from okubi_phase83_android_local_preflight import assess


class AndroidLocalPreflightTest(unittest.TestCase):
    def setUp(self):
        self.qa = ApkIdentity(
            "bo.nexoplay.app.unified.qa57", 36, "1.0.0-unified-beta17-qa57",
            "a" * 64, True, True)
        self.original = ApkIdentity(
            "bo.nexoplay.app.unified", 36, "1.0.0-unified-beta17",
            "b" * 64, False, True)
        self.candidate = ApkIdentity(
            "bo.nexoplay.app.unified", 37, "1.0.0-release-candidate",
            "b" * 64, False, True)

    def test_qa57_is_read_only_and_does_not_authorize_release(self):
        report = assess(qa=self.qa)
        self.assertTrue(report["android_parallel_qa_verified"])
        self.assertEqual(report["canonical_upgrade_preflight"],
                         "pending_original_canonical_apk")
        self.assertFalse(report["public_release_authorized"])
        self.assertFalse(report["installed_android_app_replaced"])

    def test_original_certificate_reference_is_not_automatic_approval(self):
        report = assess(qa=self.qa, previous=self.original)
        self.assertTrue(report["canonical_previous_apk_checked"])
        self.assertEqual(report["canonical_upgrade_preflight"],
                         "reference_certificate_observed_but_update_not_tested")
        self.assertFalse(report["public_release_authorized"])

    def test_matching_certificate_and_higher_version_are_locally_compatible_only(self):
        report = assess(qa=self.qa, previous=self.original, candidate=self.candidate)
        self.assertEqual(report["canonical_upgrade_preflight"],
                         "certificate_and_version_compatible_on_supplied_apks_only")
        self.assertFalse(report["physical_android_installation_tested"])
        self.assertFalse(report["public_release_authorized"])

    def test_wrong_qa_application_id_is_rejected(self):
        fake = ApkIdentity(
            self.original.package, 36, self.qa.name, self.qa.cert_sha256, True, True)
        with self.assertRaises(ReleaseCheckError):
            assess(qa=fake)

    def test_qa57_unsigned_or_non_debug_is_rejected(self):
        fake = ApkIdentity(
            self.qa.package, 36, self.qa.name, self.qa.cert_sha256, False, True)
        with self.assertRaises(ReleaseCheckError):
            assess(qa=fake)
        fake = ApkIdentity(
            self.qa.package, 36, self.qa.name, self.qa.cert_sha256, True, False)
        with self.assertRaises(ReleaseCheckError):
            assess(qa=fake)

    def test_cannot_skip_previous_canonical_apk(self):
        with self.assertRaises(ReleaseCheckError):
            assess(qa=self.qa, candidate=self.candidate)

    def test_cross_signed_canonical_update_rejected(self):
        wrong = ApkIdentity(
            self.candidate.package, 37, self.candidate.name,
            "c" * 64, False, True)
        with self.assertRaises(ReleaseCheckError):
            assess(previous=self.original, candidate=wrong)

    def test_downgrade_and_debug_release_are_rejected(self):
        downgrade = ApkIdentity(
            self.candidate.package, 36, self.candidate.name,
            self.original.cert_sha256, False, True)
        with self.assertRaises(ReleaseCheckError):
            assess(previous=self.original, candidate=downgrade)
        debug = ApkIdentity(
            self.candidate.package, 37, self.candidate.name,
            self.original.cert_sha256, True, True)
        with self.assertRaises(ReleaseCheckError):
            assess(previous=self.original, candidate=debug)

    def test_reports_never_contain_certificate_hashes_or_paths(self):
        report = assess(qa=self.qa, previous=self.original, candidate=self.candidate)
        data = json.dumps(report)
        self.assertNotIn(self.original.cert_sha256, data)
        self.assertNotIn(self.qa.cert_sha256, data)
        self.assertNotIn("userdata", data)
        self.assertNotIn("keystore", data)
        self.assertFalse(report["private_paths_or_certificate_fingerprints_in_report"])

    def test_windows_qa_script_never_deletes_or_uninstalls_user_data(self):
        path = Path(__file__).resolve().parents[1] / "okubi_phase83_windows_local_qa.ps1"
        content = path.read_text(encoding="utf-8").lower()
        for forbidden in ("remove-item", "uninstall-package", "adb uninstall",
                          "pm clear", "rd /s", "rmdir /s", "appdata\\okubi\\saves"):
            self.assertNotIn(forbidden, content)
        self.assertIn("sha256", content)
        self.assertIn("newguid", content.lower())
        self.assertIn("okubi-qa57", content)


if __name__ == "__main__":
    unittest.main()
