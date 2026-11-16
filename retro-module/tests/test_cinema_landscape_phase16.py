"""OKUBI Phase 16: approved cinema landscape interface is actually wired.

Static integration contracts complement the real Android+NDK build; UI touch
and visual QA must still be tested on the owner's Galaxy A05s.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
PLAYER=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
PAD=(RETRO/"RetroTouchPad.java").read_text(encoding="utf-8")
STAGE=(RETRO/"RetroCinemaStage.java").read_text(encoding="utf-8")
GEOMETRY=(RETRO/"RetroCinemaGeometry.java").read_text(encoding="utf-8")
PROFILE=(RETRO/"RetroControlProfile.java").read_text(encoding="utf-8")
INTEGRATE=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class CinemaLandscapePhase16(unittest.TestCase):
    def test_landscape_replaces_old_half_screen_split_only(self):
        self.assertIn("if (landscape) {\n            buildCinemaLayout();\n            return;",PLAYER)
        self.assertIn("private void buildCinemaLayout()",PLAYER)
        self.assertIn("new RetroCinemaStage(this,frameWidth,frameHeight,",PLAYER)
        self.assertIn("gameArea.setOrientation(LinearLayout.VERTICAL);",PLAYER)
        self.assertIn("createRetroControls(false)",PLAYER)
        self.assertIn("createRetroControls(true)",PLAYER)
        self.assertIn("RetroControlProfile.loadCinema(this)",PLAYER)
        self.assertNotIn("new LinearLayout.LayoutParams(0, -1, 1.30f)",PLAYER)
        self.assertIn("View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY",PLAYER)
        self.assertIn("View.SYSTEM_UI_FLAG_HIDE_NAVIGATION",PLAYER)
        self.assertIn("getWindow().getDecorView().setSystemUiVisibility(0);",PLAYER)

    def test_large_centered_native_aspect_viewport_and_rails(self):
        for part in (
            "nativeWidth / (float) nativeHeight",
            "RetroCinemaGeometry.fit(",
            "pad.setCinemaGeometry(geometry);",
            "game.measure(MeasureSpec.makeMeasureSpec(gameWidth, MeasureSpec.EXACTLY)",
            "pad.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)",
            "game.layout(gameLeft, gameTop",
            "pad.layout(0, 0, r - l, b - t);",
            "canvas.drawRoundRect(outline",
        ):
            self.assertIn(part,STAGE)
        self.assertIn("int left = (width - gameWidth) / 2;",GEOMETRY)
        self.assertIn("public static Layout fit(",GEOMETRY)
        self.assertNotIn("new File(",STAGE)
        self.assertNotIn("SameBoyNative",STAGE)
        self.assertIn("setNativeSize(frameWidth,frameHeight);",PLAYER)
        self.assertIn("currentHolder = screen.getHolder();",PLAYER)

    def test_approved_neon_controls_are_real_interactive_buttons(self):
        for part in (
            "private boolean cinemaLayout;",
            "public void setCinemaLayout(boolean enabled)",
            "calculateCinemaTargets(width, height);",
            "drawCinemaControls(canvas);",
            "RetroCore.Key.UP", "RetroCore.Key.DOWN",
            "RetroCore.Key.LEFT", "RetroCore.Key.RIGHT",
            "RetroCore.Key.A", "RetroCore.Key.B",
            "RetroCore.Key.START", "RetroCore.Key.SELECT",
            "RetroCore.Key.L", "RetroCore.Key.R",
            'cinemaLayout ? "SELECT" : "SEL"',
            'cinemaLayout ? "START" : "STA"',
            "RetroDpadHitMap.hit(", "applyMask(combined);",
            "postInvalidateOnAnimation();",
            "if (editing)",
        ):
            self.assertIn(part,PAD)
        # Existing low-allocation multitouch dispatch must not be rewritten.
        body=PAD.split("@Override public boolean onTouchEvent(MotionEvent event)",1)[1]
        body=body.split("@Override public boolean performClick()",1)[0]
        self.assertNotIn("new ",body)
        self.assertIn("requestSnapshot(RetroSlotRequests.Action.SAVE,selectedSnapshotSlot)",PLAYER)
        self.assertIn("this::showSaveSlots",PLAYER)
        self.assertIn("this::showPerformanceMode",PLAYER)
        self.assertIn("this::showQuickMenu",PLAYER)
        self.assertIn("setOnClickListener(v->action.run())",PLAYER)

    def test_cinema_profiles_do_not_overwrite_legacy_portrait_or_land(self):
        self.assertIn('snes ? "snes_" : ""',PROFILE)
        self.assertIn("public static RetroControlProfile loadCinema(Context context)",PROFILE)
        self.assertIn("dpadX = cinema ? .115f : landscape ? .21f : .22f;",PROFILE)
        self.assertIn("dpadY = cinema ? .73f : .50f;",PROFILE)
        for marker in ("getSharedPreferences(PREFS, Context.MODE_PRIVATE)",
                       "e.apply();", "profile.move(dragged,",
                       "if (editing && dragged != null) profile.save(getContext());"):
            self.assertTrue(marker in PROFILE or marker in PAD)
        self.assertIn('private static final String PREFS = "okubi_retro_controls_v1";',PROFILE)

    def test_no_rom_or_save_regressions_and_build_gate(self):
        self.assertIn('"RetroCinemaStage.java"',INTEGRATE)
        self.assertIn('"RetroCinemaGeometry.java"',INTEGRATE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)
        self.assertIn("RetroSlotRequestsTest",WORKFLOW)
        self.assertIn("RetroSaveSlotsTest",WORKFLOW)
        self.assertIn("libsameboy_libretro_android.so",WORKFLOW)
        self.assertIn("libmgba_libretro_android.so",WORKFLOW)
        landscape=PLAYER.split("private void buildCinemaLayout()",1)[1].split(
            "private void syncPlaying()",1)[0]
        for forbidden in (
            "SameBoyNative.nativeOpen(", "SameBoyNative.nativeClose(",
            "saveBattery(", "persistAtomic(", "slotRequests.clear();",
            "snapshotFile.delete(", "getFilesDir()", "new File(",
        ):
            self.assertNotIn(forbidden,landscape)

if __name__=="__main__":
    unittest.main()
