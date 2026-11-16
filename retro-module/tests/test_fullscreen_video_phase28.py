"""Phase 28: screen fills the stage but touch controls retain safe geometry."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
PLAYER = (JAVA / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
STAGE = (JAVA / "RetroCinemaStage.java").read_text(encoding="utf-8")
ASPECT = (JAVA / "RetroAspectFrame.java").read_text(encoding="utf-8")
PAD = (JAVA / "RetroTouchPad.java").read_text(encoding="utf-8")
INTEGRATOR = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")


class FullscreenVideoPhase28(unittest.TestCase):
    def test_stage_is_expanded_without_moving_controls(self):
        self.assertIn("if (expandedVideo) {", STAGE)
        self.assertIn("gameWidth = width;", STAGE)
        self.assertIn("gameWidth = geometry.gameWidth;", STAGE)
        self.assertIn("gameHeight = height;", STAGE)
        self.assertIn("gameHeight = geometry.gameHeight;", STAGE)
        self.assertIn("pad.setCinemaGeometry(geometry);", STAGE)
        self.assertIn("game.setFillAvailable(expanded);", STAGE)
        self.assertIn("if (fillAvailable) {", ASPECT)
        self.assertIn("if (expandedVideo) return;", STAGE)
        self.assertIn("&& x >= cinemaGeometry.gameLeft", PAD)

    def test_selector_is_in_game_and_per_console(self):
        self.assertIn('cinemaAction("⛶","Tamaño de pantalla",this::showScreenFitSettings)', PLAYER)
        self.assertIn("case 18: showScreenFitSettings(); break;", PLAYER)
        self.assertIn('"okubi_screen_fit_v1"', PLAYER)
        self.assertIn(".putString(system, screenFitMode.name()).apply();", PLAYER)
        self.assertIn("activeCinemaStage.setExpandedVideo(", PLAYER)
        self.assertIn("RetroScreenFit.Rects fitted = RetroScreenFit.layout(", PLAYER)
        self.assertIn("canvas.drawBitmap(presented, src, dst, bitmapPaint);", PLAYER)

    def test_no_saves_rom_or_signing_changes(self):
        self.assertIn('"RetroScreenFit.java"', INTEGRATOR)
        self.assertIn("RetroScreenFitTest.java", WORKFLOW)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)


if __name__ == "__main__":
    unittest.main()
