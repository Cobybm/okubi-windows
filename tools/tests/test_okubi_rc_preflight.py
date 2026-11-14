"""RC1 policy tests: no builds, credentials, account or user data."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "okubi_rc_preflight.py"
spec = importlib.util.spec_from_file_location("okubi_rc_preflight", SCRIPT)
rc = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(rc)


class ReleaseCandidatePolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="okubi-rc1-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for relative in rc.REQUIRED.values():
            p = self.root / relative
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("OKUBI fake CI\n", encoding="utf-8")
        self.replace("windows", """
            --app-version "1.0.6" --type app-image
            SHA256SUMS Get-FileHash ZipFile
            Forbidden personal artifact
            SameBoy-MIT.txt mGBA-MPL-2.0.txt Supafaust-GPL-2.0-plus.txt
            DesktopShutdownPolicyTest DesktopCrossSaveTest DesktopSaveVaultTest
            DesktopSmartSaveGuardTest DesktopUniversalSavePreflightTest
            """)
        self.replace("android", """
            github.event_name == 'workflow_dispatch' && github.ref_name == 'main'
            --kind release --pinned-sha256 OKUBI_RELEASE_CERT_SHA256
            apksigner --kind debug mGBA-LICENSE.txt
            Supafaust-GPL-2.0.txt Supafaust-SOURCE.txt
            """)
        self.replace("gradle", """
            applicationId 'bo.nexoplay.app.unified'
            versionCode 36
            versionName '1.0.0-unified-beta17'
            """)
        self.replace("manifest", 'android:allowBackup="false"')
        self.replace("desktop",
                     "DesktopShutdownPolicy.mayExit cloudBusy crossSaveBusy saveGuardBusy")
        self.replace("release_notes",
                     "NO ESTABLE. Para actualizar necesitas misma huella SHA-256.")

    def replace(self, key, value):
        (self.root / rc.REQUIRED[key]).write_text(value, encoding="utf-8")

    def test_fixture_passes(self):
        result = rc.inspect(self.root)
        self.assertEqual(result["status"], "PASS", result["issues"])
        self.assertEqual(result["checks_passed"], result["checks_total"])

    def test_release_cannot_use_unpinned_debug_identity(self):
        self.replace("android", "apksigner --kind debug")
        result = rc.inspect(self.root)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("Firma del propietario" in issue
                            for issue in result["issues"]))

    def test_version_or_package_drift_fails(self):
        self.replace("gradle", "applicationId 'bo.another.app'\nversionCode 35")
        result = rc.inspect(self.root)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("Identidad Android" in issue
                            for issue in result["issues"]))
        self.assertTrue(any("VersionCode" in issue
                            for issue in result["issues"]))

    def test_windows_cannot_omit_save_guard_or_source_licenses(self):
        self.replace("windows", '--app-version "1.0.6" --type app-image')
        result = rc.inspect(self.root)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("Windows verifica las tres" in issue
                            for issue in result["issues"]))
        self.assertTrue(any("Bloqueo de cierre" in issue
                            for issue in result["issues"]))

    def test_no_backup_promise_on_android(self):
        self.replace("manifest", 'android:allowBackup="true"')
        result = rc.inspect(self.root)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("Datos Android" in issue
                            for issue in result["issues"]))

    def test_real_repo_is_ready_for_automated_checks(self):
        repo = Path(__file__).resolve().parents[2]
        result = rc.inspect(repo)
        self.assertEqual(result["status"], "PASS", result["issues"])


if __name__ == "__main__":
    unittest.main()
