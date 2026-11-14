"""Phase 24 regression wiring: Java unit tests verify the actual mask behavior."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
PAD = (JAVA / "RetroTouchPad.java").read_text(encoding="utf-8")
MASK = (JAVA / "RetroInputMask.java").read_text(encoding="utf-8")
INTEGRATE = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class MandoUltraPhase24(unittest.TestCase):
    def test_mask_compiled_in_real_android_overlay(self):
        self.assertIn('"RetroInputMask.java"', INTEGRATE)
        self.assertIn('RetroInputMask.resolveOpposites(combined,', PAD)
        self.assertIn('RetroInputMaskTest.java', WORKFLOW)

    def test_multitouch_and_user_settings_unchanged(self):
        self.assertIn('if (i != leaving) combined |= maskAt(', PAD)
        self.assertIn('applyMask(combined);', PAD)
        self.assertIn('if (editing && dragged != null) profile.save(getContext());', PAD)
        self.assertIn('releaseAll();', PAD)
        self.assertIn('mask &= ~(up | down);', MASK)
        self.assertIn('mask &= ~(left | right);', MASK)


if __name__ == "__main__":
    unittest.main()
