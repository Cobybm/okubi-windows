"""Fase82: use synthetic existing QA fixtures; no real games or private data."""
from __future__ import annotations

import copy
import json
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from okubi_phase80_rc_preflight import CheckError
import okubi_phase82_final_audit as gate
from test_okubi_phase80_rc_preflight import Phase80ArtifactTests
from test_okubi_phase81b_source_bundle import SourceBundleTests


class Phase82AcceptanceTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2]
        self.evidence = json.loads((root / "tools/okubi_phase82_release_evidence.json")
                                   .read_text("utf-8"))
        self.win = SourceBundleTests(
            "test_all_source_built_cores_and_corresponding_archives_are_packaged")
        self.win.setUp()
        self.addCleanup(self.win.doCleanups)
        self.android = Phase80ArtifactTests(
            "test_android_parallel_debug_apk_passes")
        self.android.setUp()
        self.addCleanup(self.android.doCleanups)

    def assemble(self):
        self.win.build()
        self.android.make_android()

    def check(self):
        return gate.audit(
            self.win.new_zip, self.win.new_zip.parent / "SHA256SUMS.txt",
            self.android.android, self.android.sums,
            self.android.badging, self.android.signer,
            self.evidence,
        )

    def test_realistic_windows_and_android_synthetic_artifacts_are_verified(self):
        self.assemble()
        report = self.check()
        self.assertEqual(report["automated_artifact_integrity"], "pass")
        self.assertEqual(report["windows"]["source_built_cores"], 3)
        self.assertEqual(report["android"]["retro_cores_verified"], 12)
        self.assertEqual(report["manual_gates_pending"], 6)
        self.assertFalse(report["public_release_authorized"])
        self.assertFalse(report["physical_cross_save_verified"])

    def test_failed_android_checksum_is_rejected(self):
        self.assemble()
        self.android.sums.write_text("0" * 64 + " *" + self.android.android.name)
        with self.assertRaisesRegex(CheckError, "checksum_mismatch"):
            self.check()

    def test_wrong_android_package_never_validates_as_parallel_qa(self):
        self.assemble()
        self.android.badging = self.android.badging.replace(
            "bo.nexoplay.app.unified.qa57", "bo.nexoplay.app.unified")
        with self.assertRaisesRegex(CheckError, "package_identity"):
            self.check()

    def test_canonical_release_cert_never_accepted_as_debug_qa(self):
        self.assemble()
        self.android.signer = self.android.signer.replace(
            "CN=Android Debug", "CN=OKUBI Release")
        with self.assertRaisesRegex(CheckError, "debug_certificate"):
            self.check()

    def test_forged_approval_is_rejected(self):
        self.assemble()
        self.evidence["manual_gates"]["explicit_owner_public_release_approval"] = {
            "status": "approved", "evidence": "not-reviewed"}
        with self.assertRaisesRegex(CheckError, "unreviewed_manual"):
            self.check()

    def test_missing_manual_gate_is_rejected(self):
        self.assemble()
        self.evidence["manual_gates"].pop("physical_cross_save_backup_restore")
        with self.assertRaisesRegex(CheckError, "manual_evidence_gates_missing"):
            self.check()

    def test_unknown_ci_run_is_rejected(self):
        self.assemble()
        self.evidence["automated_ci"]["windows_source_built_ci"] = 123
        with self.assertRaisesRegex(CheckError, "unrecognized_release"):
            self.check()

    def test_tampered_windows_core_rejected_even_if_zip_checksum_is_refreshed(self):
        self.assemble()
        changed = self.win.new_zip.parent / "modified.zip"
        with zipfile.ZipFile(self.win.new_zip) as zin, zipfile.ZipFile(
                changed, "w") as zout:
            for entry in zin.infolist():
                data = zin.read(entry)
                if entry.filename.endswith("/cores/mgba_libretro.dll"):
                    data = b"MZ" + b"tampered" * 50
                zout.writestr(entry, data)
        changed.replace(self.win.new_zip)
        (self.win.new_zip.parent / "SHA256SUMS.txt").write_text(
            gate.digest_bytes(self.win.new_zip.read_bytes())
            + " *" + self.win.new_zip.name + "\n", encoding="ascii")
        with self.assertRaisesRegex(CheckError, "core_or_source_sha256_mismatch"):
            self.check()

    def test_public_release_mode_returns_2_but_writes_a_safe_report(self):
        self.assemble()
        folder = self.android.root
        badgefile = folder / "badging.txt"
        signerfile = folder / "signer.txt"
        evidencefile = folder / "evidence.json"
        reportfile = folder / "report.json"
        badgefile.write_text(self.android.badging, encoding="utf-8")
        signerfile.write_text(self.android.signer, encoding="utf-8")
        evidencefile.write_text(json.dumps(self.evidence), encoding="utf-8")
        argv = [
            "phase82", "--windows", str(self.win.new_zip),
            "--windows-checksums", str(self.win.new_zip.parent / "SHA256SUMS.txt"),
            "--android", str(self.android.android),
            "--android-checksums", str(self.android.sums),
            "--android-badging", str(badgefile),
            "--android-signer", str(signerfile),
            "--evidence", str(evidencefile),
            "--report", str(reportfile), "--require-release",
        ]
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 2)
        saved = json.loads(reportfile.read_text("utf-8"))
        self.assertFalse(saved["public_release_authorized"])
        self.assertEqual(saved["manual_gates_pending"], 6)


if __name__ == "__main__":
    unittest.main()
