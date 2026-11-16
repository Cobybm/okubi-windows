"""Phase 25 stability guard for stacked Beta 16 PRs.

This does not replace Android/NDK compilation or on-device QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
INTEGRATE = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")
PLAYER = (JAVA / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
PAD = (JAVA / "RetroTouchPad.java").read_text(encoding="utf-8")
GEOMETRY = (JAVA / "RetroCinemaGeometry.java").read_text(encoding="utf-8")


class StabilityPhase25(unittest.TestCase):
    def test_pull_requests_actually_build(self):
        self.assertIn('  pull_request:\n    paths:', CI)
        for path in ("retro-module/**", "android-app/**",
                     "source/nexo-android-1.0.zip"):
            self.assertIn(path, CI)
        self.assertIn("gradle :app:assembleDebug --no-daemon --stacktrace", CI)
        self.assertIn("apksigner", CI)
        self.assertIn("RetroFrameTimingTest.java", CI)
        self.assertIn("RetroInputMaskTest.java", CI)
        self.assertIn("RetroCinemaGeometryTest.java", CI)
        self.assertIn("OkubiSaveArchiveTest.java", CI)

    def test_new_java_sources_reach_gradle(self):
        for name in ("RetroFrameTiming.java", "RetroInputMask.java",
                     "RetroCinemaStage.java", "RetroCinemaGeometry.java",
                     "OkubiSaveArchive.java", "RetroTouchPad.java"):
            self.assertTrue((JAVA / name).is_file(), name)
            self.assertIn('"' + name + '"', INTEGRATE)
        self.assertIn("RetroFrameTiming.frameIntervalNanos", PLAYER)
        self.assertIn("RetroInputMask.resolveOpposites(combined,", PAD)
        self.assertIn("pad.setCinemaGeometry(geometry);",
                      (JAVA / "RetroCinemaStage.java").read_text(encoding="utf-8"))
        self.assertIn("Math.max(28f * density, height * .10f)", GEOMETRY)

    def test_identity_and_saves_not_migrated_by_qa(self):
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)
        self.assertIn('"SNES".equals(system) ? "okubi/snes"', PLAYER)
        self.assertIn('"GBA".equals(system) ? "okubi/gba"', PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)


if __name__ == "__main__":
    unittest.main()
