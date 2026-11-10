"""Screenshot regression: no clipped Pokémon dialogue and independent alpha setting."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"retro-module/java/bo/nexoplay/app/retro"
PLAYER=(SRC/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
FIT=(SRC/"RetroScreenFit.java").read_text(encoding="utf-8")
STAGE=(SRC/"RetroCinemaStage.java").read_text(encoding="utf-8")
CONTROLS=(SRC/"RetroTouchPad.java").read_text(encoding="utf-8")
CI=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")

class ScreenOpacityPhase29(unittest.TestCase):
    def test_dialogue_not_cropped_in_fullscreen(self):
        self.assertIn("if (chosen != Mode.STRETCH)", FIT)
        self.assertNotIn("int clippedHeight", FIT)
        self.assertNotIn("int clippedWidth", FIT)
        self.assertIn("canvas.drawBitmap(presented, backgroundSource,", PLAYER)
        self.assertIn("canvas.drawBitmap(presented, src, dst, bitmapPaint)", PLAYER)
        self.assertIn("RetroScreenFit.Mode.COVER", PLAYER)
        self.assertIn("pad.setCinemaGeometry(geometry);", STAGE)

    def test_slider_affects_visuals_not_hitboxes(self):
        self.assertIn("private void showOverlayOpacitySettings()", PLAYER)
        self.assertIn("slider.setMax(80)", PLAYER)
        self.assertIn("controls.setAlpha(", PLAYER)
        self.assertIn('"okubi_overlay_opacity_v1"', PLAYER)
        self.assertIn(".putFloat(system, overlayOpacity).apply()", PLAYER)
        self.assertIn("case 19: showOverlayOpacitySettings(); break;", PLAYER)
        self.assertIn("RetroScreenFit.clampOverlayOpacity", PLAYER)
        self.assertIn("RetroDpadHitMap.hit(", CONTROLS)
        self.assertIn("RetroScreenFitTest.java", CI)

    def test_saves_and_version_remain_unmodified(self):
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        gradle=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", gradle)

if __name__=="__main__":
    unittest.main()
