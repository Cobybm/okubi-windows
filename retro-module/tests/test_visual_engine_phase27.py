"""Phase 27: five real, user-selectable modes with a native Canvas renderer.

Complement JVM numerical tests with Android source/integration contracts.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "retro-module/java/bo/nexoplay/app/retro"
PLAYER = (JAVA / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
FILTERS = (JAVA / "RetroVisualFilters.java").read_text(encoding="utf-8")
TOUCH = (JAVA / "RetroTouchPad.java").read_text(encoding="utf-8")
INTEGRATOR = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")


class VisualEnginePhase27(unittest.TestCase):
    def test_five_real_render_modes(self):
        for marker in ("PIXEL_PERFECT(", "SHARP_HD(", "LCD_RETRO(",
                       "SMOOTH_HD(", "GB_CLASSIC("):
            self.assertIn(marker, FILTERS)
        for marker in ("RetroVisualFilters.integerScale(", "bitmapPaint.setFilterBitmap(",
                       "RetroVisualFilters.classicPalette(", "canvas.drawRect(x, scan,",
                       "canvas.drawBitmap(presented, src, dst, bitmapPaint);"):
            self.assertIn(marker, PLAYER)

    def test_selector_and_independent_prefs(self):
        self.assertIn('cinemaAction("◈","Filtros de imagen",this::showVisualSettings)', PLAYER)
        self.assertIn('button("◈ Imagen", this::showVisualSettings)', PLAYER)
        self.assertIn("case 17: showVisualSettings(); break;", PLAYER)
        self.assertIn(".setSingleChoiceItems(titles, checked,", PLAYER)
        self.assertIn('getSharedPreferences("okubi_visual_v1", MODE_PRIVATE)', PLAYER)
        self.assertIn(".putString(system, visualMode.name()).apply();", PLAYER)
        self.assertIn("private volatile RetroVisualFilters.Mode visualMode", PLAYER)
        self.assertNotIn("getFilesDir()", FILTERS)
        self.assertNotIn("SameBoyNative", FILTERS)

    def test_save_game_controls_and_version_unchanged(self):
        self.assertIn('"RetroVisualFilters.java"', INTEGRATOR)
        self.assertIn("RetroVisualFiltersTest.java", CI)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("persistAtomic(destination,bytes)", PLAYER)
        self.assertIn("RetroInputMask.resolveOpposites(combined,", TOUCH)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)


if __name__ == "__main__":
    unittest.main()
