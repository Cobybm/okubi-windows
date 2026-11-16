"""Unit tests for the release-preparation audit (no GitHub credentials required)."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "okubi_unified_audit.py"
spec = importlib.util.spec_from_file_location("okubi_unified_audit", SCRIPT)
audit = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(audit)


class UnifiedAuditTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for name in audit.REQUIRED_FILES:
            file = self.root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("OKUBI test stub\n", encoding="utf-8")
        self.seed("android-app/app/build.gradle",
                  "applicationId 'bo.nexoplay.app.unified'\n"
                  "minSdk 26\nsigningConfigs { OKUBI_KEYSTORE_PATH }\n")
        self.seed(".github/workflows/okubi-retro.yml",
                  "python3 -m unittest discover -s retro-module/tests -v\n"
                  "python3 retro-module/integrate.py\n"
                  "gradle :app:assembleDebug\napksigner verify\n"
                  "bo.nexoplay.app.unified\n"
                  "OkubiSaveArchiveTest\n")
        self.seed(".github/workflows/okubi-desktop.yml",
                  "runs-on: windows-latest\n"
                  "jpackage --type app-image\n"
                  "OkubiSaveArchive.java\nEmbeddedCoreTest\n"
                  "DesktopCrossSaveTest\nDesktopSaveVaultTest\n"
                  "DesktopSaveVaultStabilityTest\nDesktopSmartSaveGuardTest\n"
                  "DesktopControllerFocusGateTest\n")
        self.seed("desktop/src/bo/okubi/desktop/OkubiDesktop.java",
                  "showGameHub OnlineCoverArtwork smartGuard.beforePlay(game)\n")
        self.seed("desktop/src/bo/okubi/desktop/DesktopSaveVault.java",
                  "backupAutomaticIfChanged\npublic synchronized Entry restore(\n")
        vault_dialog = self.root / "desktop/src/bo/okubi/desktop/DesktopSaveVaultDialog.java"
        vault_dialog.parent.mkdir(parents=True, exist_ok=True)
        vault_dialog.write_text("private void restore()", encoding="utf-8")

    def seed(self, relative: str, text: str):
        (self.root / relative).write_text(text, encoding="utf-8")

    def test_integrated_contract_passes(self):
        report = audit.inspect(self.root, [
            "desktop/src/bo/okubi/desktop/OkubiDesktop.java",
            "android-app/app/build.gradle"])
        self.assertEqual(report["status"], "PASS", report)
        self.assertEqual(report["contracts_passed"], report["contracts_total"])
        self.assertEqual(report["changed_paths_inspected"], 2)

    def test_prohibit_personal_rom_and_save_uploads(self):
        violations = audit.unsafe_changes([
            "source/personal/Pokemon Esmeralda.gba",
            "desktop/private/game.srm",
            "user/.okubi/saves/save.state",
            "VIDAS/Scenes/world.unity",
            "vidas\\Assets\\scene.cs",
        ])
        self.assertGreaterEqual(len(violations), 5)
        self.assertTrue(any("VIDAS" in message for message in violations))

    def test_preserve_unrelated_src(self):
        self.assertFalse(audit.unsafe_changes([
            "desktop/tests/bo/okubi/desktop/DesktopSmartSaveGuardTest.java",
            "android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java",
            "docs/OKUBI_PHASE17_CINEMA_PERFECT.md",
            ".github/workflows/okubi-retro.yml",
        ]))

    def test_missing_contract_fails_closed(self):
        self.seed("android-app/app/build.gradle",
                  "applicationId 'bo.test.clone'\nminSdk 26\n")
        report = audit.inspect(self.root)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("Android app ID unchanged" in msg
                            for msg in report["issues"]))
        self.assertTrue(any("owner signing" in msg
                            for msg in report["issues"]))

    def test_missing_phase_file_blocks_integration(self):
        (self.root / "desktop/src/bo/okubi/desktop/OnlineCoverService.java").unlink()
        report = audit.inspect(self.root)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("OnlineCoverService.java" in msg
                            for msg in report["issues"]))

    def test_save_guard_coverage_not_silent(self):
        self.seed(".github/workflows/okubi-desktop.yml",
                  "runs-on: windows-latest\njpackage --type app-image\n")
        report = audit.inspect(self.root)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("Windows smart guard tests" in msg
                            for msg in report["issues"]))
        self.assertTrue(any("Windows Cross Save tests" in msg
                            for msg in report["issues"]))


if __name__ == "__main__":
    unittest.main()
