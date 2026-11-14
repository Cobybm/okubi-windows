"""Phase 23 CINEMA ULTRA integration regression contracts.

The Java RetroCinemaGeometryTest executes actual numeric geometry checks in CI.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT / "retro-module/java/bo/nexoplay/app/retro"
GEOMETRY = (RETRO / "RetroCinemaGeometry.java").read_text(encoding="utf-8")
STAGE = (RETRO / "RetroCinemaStage.java").read_text(encoding="utf-8")
PAD = (RETRO / "RetroTouchPad.java").read_text(encoding="utf-8")
TEST = (ROOT / "retro-module/tests/java/bo/nexoplay/app/retro/RetroCinemaGeometryTest.java").read_text(encoding="utf-8")


class CinemaUltraPhase23(unittest.TestCase):
    def test_video_footer_touch_geometry_shared(self):
        self.assertIn('Math.max(28f * density, height * .10f)', GEOMETRY)
        self.assertIn('bottomRail = geometry.bottomRail;', STAGE)
        self.assertIn('bottomRail * .5f', STAGE)
        self.assertIn('pad.setCinemaGeometry(geometry);', STAGE)
        self.assertIn('g.utilityY', PAD)
        self.assertIn('g.gameRight()', PAD)

    def test_existing_profiles_and_native_ratio_preserved(self):
        self.assertIn('Math.round(width * .205f)', GEOMETRY)
        self.assertIn('height * .120f', GEOMETRY)
        self.assertIn('(int)Math.floor(gameHeight * (double)aspect)', GEOMETRY)
        self.assertIn('if (cinemaLayout && !editing && cinemaGeometry != null', PAD)
        self.assertIn('ultraLandscape();', TEST)
        self.assertIn('gba.gameHeight > 502', TEST)


if __name__ == "__main__":
    unittest.main()
