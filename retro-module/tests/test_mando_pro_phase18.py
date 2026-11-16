"""Phase 18 MANDO PRO: user-visible presets/haptic controls with no save changes."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT/"retro-module/java/bo/nexoplay/app/retro"
PLAYER = (RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
PAD = (RETRO/"RetroTouchPad.java").read_text(encoding="utf-8")
PROFILE = (RETRO/"RetroControlProfile.java").read_text(encoding="utf-8")
PRESETS = (RETRO/"RetroControlPresets.java").read_text(encoding="utf-8")
INTEGRATOR = (ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class MandoProPhase18(unittest.TestCase):
    def test_presets_only_change_size_and_opacity(self):
        method = PROFILE.split("public void applyPreset(", 1)[1].split(
            "public void reset()", 1)[0]
        self.assertIn("size = RetroControlPresets.boundedSize(mode.size);", method)
        self.assertIn("opacity = RetroControlPresets.boundedOpacity(mode.opacity);",
                      method)
        for forbidden in ("dpadX =", "dpadY =", "xs[", "ys[",
                          "leftHanded =", "swapAB =", "showShoulders =",
                          "hapticEnabled =", "save(", "getFilesDir(", "delete("):
            self.assertNotIn(forbidden, method)
        self.assertIn("EQUILIBRADO(", PRESETS)
        self.assertIn("COMODO(", PRESETS)
        self.assertIn("COMPACTO(", PRESETS)
        self.assertIn("Float.isFinite(size)", PRESETS)
        self.assertIn("Float.isFinite(opacity)", PRESETS)

    def test_new_haptic_feedback_is_opt_in_and_only_on_down_transition(self):
        self.assertIn("public boolean hapticEnabled = false;", PROFILE)
        self.assertIn('prefs.getBoolean(key + "haptics", false)', PROFILE)
        self.assertIn('.putBoolean(key + "haptics", hapticEnabled)', PROFILE)
        self.assertIn("hapticEnabled = false;", PROFILE)
        self.assertIn("import android.view.HapticFeedbackConstants;", PAD)
        self.assertIn("profile.hapticEnabled && !editing && (changes & next) != 0",
                      PAD)
        self.assertIn("performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);",
                      PAD)
        self.assertNotIn("VibrationEffect", PAD)
        self.assertNotIn("VibratorManager", PAD)
        self.assertNotIn("android.permission.VIBRATE", PAD)
        # Never create allocations in the onTouchEvent hot path.
        event = PAD.split("@Override public boolean onTouchEvent(MotionEvent event)", 1)[1]
        event = event.split("@Override public boolean performClick()", 1)[0]
        self.assertNotIn("new ", event)

    def test_real_ui_has_presets_and_custom_access(self):
        self.assertIn('"MANDO PRO · perfiles rápidos"', PLAYER)
        self.assertIn("case 16: showProPresets(); break;", PLAYER)
        self.assertIn("RetroControlPresets.options()", PLAYER)
        self.assertIn("active.applyPreset(options[which]);", PLAYER)
        self.assertIn("active.save(this);", PLAYER)
        self.assertIn("controls.setProfile(active);", PLAYER)
        self.assertIn("showControlSettings()", PLAYER)
        self.assertIn('vibration.setText("Vibración breve al pulsar', PLAYER)
        self.assertIn("profile.hapticEnabled = vibration.isChecked();", PLAYER)
        self.assertIn("ScrollView scroll = new ScrollView(this);", PLAYER)
        self.assertIn(".setView(scroll)", PLAYER)
        self.assertIn("profile.reset();", PLAYER)

    def test_per_orientation_prefs_and_saved_games_remain_untouched(self):
        self.assertIn('snes ? "snes_" : ""',
                      PROFILE)
        self.assertIn('private static final String PREFS = "okubi_retro_controls_v1";',
                      PROFILE)
        control_method = PLAYER.split("private void showProPresets()", 1)[1].split(
            "private void showControlSettings()", 1)[0]
        for forbidden in ("saveRequested", "snapshotFile", "SameBoyNative",
                          "activeRomHash", "saveBattery(", "getFilesDir(", "delete("):
            self.assertNotIn(forbidden, control_method)
        self.assertIn('"RetroControlPresets.java"', INTEGRATOR)

    def test_beta12_and_real_java_test_in_ci(self):
        self.assertIn("RetroControlPresetsTest.java", WORKFLOW)
        self.assertIn("java -cp build/control-presets-tests", WORKFLOW)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("--minimum-version-code 36", WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK", WORKFLOW)
        self.assertIn("RetroCinemaGeometryTest.java", WORKFLOW)
        self.assertIn("RetroSaveSlotsTest.java", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
