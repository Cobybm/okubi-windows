"""Phase 22 integration contracts; JVM tests check the actual scheduling maths."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
ACTIVITY = (JAVA / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
HELPER = (JAVA / "RetroFrameTiming.java").read_text(encoding="utf-8")
INTEGRATE = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class Phase22FrameTiming(unittest.TestCase):
    def test_helper_copied_into_android_build(self):
        self.assertIn('"RetroFrameTiming.java"', INTEGRATE)
        self.assertIn('RetroFrameTiming.frameIntervalNanos(SameBoyNative.nativeFps())', ACTIVITY)
        self.assertIn('RetroFrameTiming.nextDeadline(deadline, now, frameNs)', ACTIVITY)
        self.assertIn('RetroFrameTiming.parkNanos(deadline - now)', ACTIVITY)
        self.assertIn('LockSupport.parkNanos(park)', ACTIVITY)
        self.assertIn('RetroFrameTimingTest.java', WORKFLOW)

    def test_audio_and_saves_are_not_skipped(self):
        self.assertIn('SameBoyNative.nativeStep(inputMask,', ACTIVITY)
        self.assertIn('audio.write(samples, 0, audioCount,', ACTIVITY)
        self.assertIn('saveSnapshotOnWorker(nextSlot.slot)', ACTIVITY)
        self.assertIn('AudioTrack.WRITE_BLOCKING', ACTIVITY)
        self.assertIn('Math.max(0L, now - scheduled)', ACTIVITY)

    def test_guard_invalid_fps(self):
        self.assertIn('Double.isFinite(fps)', HELPER)
        self.assertIn('FALLBACK_FRAME_NS', HELPER)


if __name__ == "__main__":
    unittest.main()
