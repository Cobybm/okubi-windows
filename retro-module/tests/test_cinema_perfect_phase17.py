"""Phase 17: CINEMA PERFECT geometry, comfort, and save-preserving wiring.

The actual numerical invariants are compiled and executed in Java by CI
(RetroCinemaGeometryTest). These source checks catch dropped touch wiring.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT / "retro-module/java/bo/nexoplay/app/retro"
PLAYER = (RETRO / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
TOUCH = (RETRO / "RetroTouchPad.java").read_text(encoding="utf-8")
STAGE = (RETRO / "RetroCinemaStage.java").read_text(encoding="utf-8")
GEOMETRY = (RETRO / "RetroCinemaGeometry.java").read_text(encoding="utf-8")
PROFILE = (RETRO / "RetroControlProfile.java").read_text(encoding="utf-8")
INTEGRATE = (ROOT / "retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")


class CinemaPerfectPhase17(unittest.TestCase):
    def test_video_and_pad_share_exact_same_geometry(self):
        self.assertIn("RetroCinemaGeometry.fit(", STAGE)
        self.assertIn("pad.setCinemaGeometry(geometry);", STAGE)
        self.assertIn("gameWidth = geometry.gameWidth;", STAGE)
        self.assertIn("gameHeight = geometry.gameHeight;", STAGE)
        self.assertIn("gameLeft = geometry.gameLeft;", STAGE)
        self.assertIn("gameTop = geometry.gameTop;", STAGE)
        self.assertIn("private RetroCinemaGeometry.Layout cinemaGeometry;", TOUCH)
        self.assertIn("public void setCinemaGeometry(RetroCinemaGeometry.Layout next)", TOUCH)
        self.assertIn("calculateTargets(next.stageWidth, next.stageHeight);", TOUCH)
        self.assertIn("releaseAll(); // Never retain a pointer pressed across a resized view.", TOUCH)
        self.assertIn("RetroCinemaGeometry.fit(width, height, 1.5f, density)", TOUCH)

    def test_ergonomic_safe_touch_lanes_and_no_phantom_press(self):
        for token in (
            "g.gameRight()",
            "g.gameLeft",
            "g.leftThumbX",
            "g.rightThumbX",
            "g.actionAY",
            "g.actionBY",
            "g.utilityY",
            "g.utilityHalfWidth",
            "Math.max(laneStart, x - touchX)",
            "Math.min(laneEnd, x + touchX)",
            "if (cinemaLayout && !editing && cinemaGeometry != null",
            "x >= cinemaGeometry.gameLeft",
            "x <= cinemaGeometry.gameRight()",
            "y >= cinemaGeometry.gameTop",
            "y <= cinemaGeometry.gameBottom()) return 0;",
        ):
            self.assertIn(token, TOUCH)
        self.assertIn("RetroDpadHitMap.hit(", TOUCH)
        self.assertIn("if (i != leaving) combined |= maskAt(", TOUCH)
        self.assertIn("applyMask(combined);", TOUCH)
        self.assertIn("float maxByRail", GEOMETRY)
        self.assertIn("Math.round(width * .205f)", GEOMETRY)
        self.assertIn("height * .120f", GEOMETRY)

    def test_cinema_remains_opt_in_by_orientation_and_saves_untouched(self):
        self.assertIn("if (landscape) {\n            buildCinemaLayout();\n            return;", PLAYER)
        self.assertIn("createRetroControls(false)", PLAYER)
        self.assertIn("createRetroControls(true)", PLAYER)
        self.assertIn("RetroControlProfile.loadCinema(this)", PLAYER)
        self.assertIn('snes ? "snes_" : ""',
                      PROFILE)
        self.assertIn('private static final String PREFS = "okubi_retro_controls_v1";',
                      PROFILE)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("loadSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("persistAtomic(destination,checkpoint)", PLAYER)
        stage_body = STAGE.split("public final class RetroCinemaStage", 1)[1]
        for forbidden in ("getFilesDir()", "getSharedPreferences(", "nativeLoadState(",
                          "nativeSaveState(", "file.delete(", "openFileOutput(", "run-as"):
            self.assertNotIn(forbidden, stage_body)

    def test_java_and_android_ci_checks_actual_sources(self):
        self.assertIn('"RetroCinemaGeometry.java"', INTEGRATE)
        self.assertIn("RetroCinemaGeometryTest.java", WORKFLOW)
        self.assertIn("java -cp build/cinema-geometry-tests", WORKFLOW)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("--minimum-version-code 36", WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK", WORKFLOW)
        self.assertIn("Compilar APK único OKUBI UNIFIED", WORKFLOW)
        self.assertIn("libsameboy_libretro_android.so", WORKFLOW)
        self.assertIn("libmgba_libretro_android.so", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
