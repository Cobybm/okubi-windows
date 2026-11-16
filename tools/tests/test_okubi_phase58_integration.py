"""Offline Phase58 integration gates; all data and Git repos are synthetic."""
from __future__ import annotations

import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parents[1] / "okubi_phase58_integration.py"
spec = importlib.util.spec_from_file_location("phase58", SRC)
gate = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(gate)


class FinalIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="okubi-phase58-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in gate.REQUIRED_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("OKUBI synthetic source\n", encoding="utf-8")
        self.write("android-app/app/build.gradle", """
applicationId 'bo.nexoplay.app.unified'
versionCode 36
project.findProperty('okubiQaSideBySide')
applicationIdSuffix '.qa57'
manifestPlaceholders = [okubiAppLabel: 'OKUBI QA57 遊']
""")
        self.write("android-app/app/src/main/AndroidManifest.xml",
                   'android:allowBackup="false"')
        self.write(".github/workflows/okubi-retro.yml", """
github.event_name == 'workflow_dispatch' && github.ref_name == 'main'
--pinned-sha256
gradle :app:assembleDebug --no-daemon
gradle :app:assembleDebug -PokubiQaSideBySide=true
OKUBI-A05S-QA57-SIDE-BY-SIDE-APK
OKUBI-UNIFIED-1.0.0-beta17-APK
tools/okubi_qa57_verify.py
apksigner
OKUBI-QA57-Android-SHA256SUMS.txt
OkubiSaveArchiveTest
""")
        self.write(".github/workflows/okubi-desktop.yml", """
jpackage --type app-image
OKUBI-Desktop-Windows-x64-SHA256SUMS.txt
Forbidden personal artifact
QA57-LAUNCHER.cmd
DesktopSaveVaultStabilityTest
DesktopSmartSaveGuardTest
DesktopUniversalSavePreflightTest
DesktopQa57IsolationTest
DesktopShutdownPolicyTest
EmbeddedCoreTest
DesktopControllerFocusGateTest
DesktopGameHubInputTest
OkubiSaveArchive.java
""")
        self.write("desktop/QA57-LAUNCHER.cmd",
                   'set "APPDATA=private-qa"\nOKUBI-QA57')
        self.write("desktop/src/bo/okubi/desktop/OkubiDesktop.java",
                   "DesktopShutdownPolicy.mayExit crossSaveBusy cloudBusy saveGuardBusy")
        self.write("retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java",
                   "public final class OkubiSaveArchive { }")
        self.write("docs/OKUBI_FINAL_INTEGRATION_PHASE58.md",
                   "NO FUSIONAR NI PUBLICAR AÚN.")

    def write(self, path, contents):
        (self.root / path).write_text(contents, encoding="utf-8")

    def shell(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args],
                              capture_output=True, text=True, check=True).stdout.strip()

    def init_git(self):
        self.shell("init", "-q")
        self.shell("config", "user.name", "OKUBI QA")
        self.shell("config", "user.email", "qa@example.invalid")
        self.shell("checkout", "-qb", "main")
        self.shell("add", ".")
        self.shell("commit", "-qm", "Synthetic baseline")
        self.shell("checkout", "-qb", "candidate")

    def test_complete_integrated_fixture(self):
        result = gate.check_contracts(self.root, ["tools/okubi_phase58_integration.py"])
        self.assertEqual(result["status"], "PASS", result["issues"])
        self.assertEqual(result["contracts_passed"], result["contracts_total"])
        self.assertEqual(result["manual_checks"]["Samsung A05s physical side-by-side installation"],
                         "PENDING", "Green CI must never fake hardware approval.")

    def test_reject_user_rom_saves_keys_and_vidas(self):
        paths = [
            "VIDAS/Assets/game.unity", "vidas\\Scenes\\npc.cs",
            "games/Pokemon Esmeralda.gba", "secret/android-release.jks",
            "personal\\AppData\\OKUBI\\save.srm",
            "private/release-key.pem", "android/unsigned-qa57.apk",
            ".env.local",
        ]
        issues = gate.protected_path_issues(paths)
        for path in paths:
            self.assertTrue(any(path in issue for issue in issues),
                            f"Must refuse private file: {path}")

    def test_allow_only_okubi_source_and_test_paths(self):
        self.assertEqual(gate.protected_path_issues([
            ".github/workflows/okubi-desktop.yml",
            "tools/okubi_phase58_integration.py",
            "android-app/app/build.gradle",
            "desktop/tests/bo/okubi/desktop/DesktopQa57IsolationTest.java",
        ]), [])

    def test_changed_android_id_blocks_integration(self):
        self.write("android-app/app/build.gradle", """
applicationId 'bo.nexoplay.app.unified.qa57'
versionCode 36
""")
        report = gate.check_contracts(self.root, [])
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("Canon Android ID" in i for i in report["issues"]))

    def test_missing_recovery_test_blocks_integration(self):
        self.write(".github/workflows/okubi-desktop.yml",
                   "jpackage --type app-image")
        report = gate.check_contracts(self.root, [])
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("Windows conserva pruebas" in i
                            for i in report["issues"]))

    def test_audits_real_git_ancestry_with_two_synthetic_commits(self):
        self.init_git()
        self.write("docs/OKUBI_FINAL_INTEGRATION_PHASE58.md",
                   "NO FUSIONAR\nPhase58 synthetic candidate")
        self.shell("add", ".")
        self.shell("commit", "-qm", "Phase58 synthetic candidate")
        report = gate.audit_git(self.root, "main")
        self.assertEqual(report["status"], "PASS", report["issues"])
        self.assertTrue(report["main_is_ancestor"])
        self.assertEqual(report["commits_ahead_main"], 1)
        self.assertEqual(report["merge"], "NOT PERFORMED")
        self.assertEqual(report["release"].split()[0], "BLOCKED")

    def test_reject_diverged_main_without_moving_branch(self):
        self.init_git()
        self.write("docs/OKUBI_FINAL_INTEGRATION_PHASE58.md",
                   "NO FUSIONAR\nCandidate")
        self.shell("add", ".")
        self.shell("commit", "-qm", "Candidate")
        self.shell("checkout", "-q", "main")
        self.write("docs/OKUBI_FINAL_INTEGRATION_PHASE58.md",
                   "NO FUSIONAR\nMain update")
        self.shell("add", ".")
        self.shell("commit", "-qm", "Main advanced")
        self.shell("checkout", "-q", "candidate")
        with self.assertRaisesRegex(ValueError, "ancestro"):
            gate.audit_git(self.root, "main")


if __name__ == "__main__":
    unittest.main()
