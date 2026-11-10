"""Phase 26: inline speed bar must execute actual emulator frames.

JVM numerical tests run in CI; these contracts check Android wiring.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
PLAYER = (JAVA / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
INTEGRATE = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class InlineSpeedPhase26(unittest.TestCase):
    def test_real_core_pacing_not_cosmetic_only(self):
        self.assertIn("RetroSpeedControl.intervalNanos(normalFrameNs, appliedSpeed)", PLAYER)
        self.assertIn("SameBoyNative.nativeStep(inputMask,", PLAYER)
        self.assertIn("RetroFrameTiming.nextDeadline(deadline, now, frameNs)", PLAYER)
        self.assertIn("RetroSpeedControl.presentFrame(appliedSpeed, emulatedFrames++)", PLAYER)
        self.assertIn("RetroSpeedControl.normal(appliedSpeed)", PLAYER)

    def test_inline_ui_and_normal_default(self):
        self.assertIn("private volatile int speedStep = RetroSpeedControl.NORMAL;", PLAYER)
        self.assertIn('cinemaAction("−","Ralentizar emulación",()->changeSpeed(-1))', PLAYER)
        self.assertIn('cinemaAction("+","Acelerar emulación",()->changeSpeed(1))', PLAYER)
        self.assertIn("cinemaSpeedLabel.setText(RetroSpeedControl.label(speedStep))", PLAYER)

    def test_no_save_migration_and_real_build_gate(self):
        self.assertIn('"RetroSpeedControl.java"', INTEGRATE)
        self.assertIn("RetroSpeedControlTest.java", CI)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("persistAtomic(destination,bytes)", PLAYER)


if __name__ == "__main__":
    unittest.main()
