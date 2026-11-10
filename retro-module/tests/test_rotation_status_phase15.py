"""Phase 15: orientation redraw must not reset status, ROM, or saves.

Source integration contract, not a substitute for portrait/landscape testing on
the real Galaxy A05s. The Android+native build runs in the same CI pipeline.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT / "retro-module/java/bo/nexoplay/app/retro"
ACTIVITY = (RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
PROFILE = (RETRO/"RetroControlProfile.java").read_text(encoding="utf-8")
TOUCH = (RETRO/"RetroTouchPad.java").read_text(encoding="utf-8")
WORKFLOW = (ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class RotationStatusPhase15(unittest.TestCase):
    def test_layout_preserves_last_status_instead_of_false_startup(self):
        self.assertIn('private String lastStatus = "Inicializando el núcleo...";', ACTIVITY)
        layout = ACTIVITY.split("private void buildLayout()", 1)[1].split(
            "private void syncPlaying()", 1)[0]
        self.assertIn("message.setText(lastStatus);", layout)
        self.assertNotIn('message.setText("Inicializando el núcleo...");', layout)
        status = ACTIVITY.split("private void setStatus(String value)", 1)[1].split(
            "private ", 1)[0]
        self.assertIn("runOnUiThread(() ->", status)
        self.assertIn("lastStatus = value;", status)
        self.assertIn("message.setText(lastStatus);", status)

    def test_rotation_never_reinitializes_core_or_writes_save(self):
        start = ACTIVITY.index("public void onConfigurationChanged(Configuration config)")
        end = ACTIVITY.index("public void onWindowFocusChanged(boolean focused)", start)
        rotate = ACTIVITY[start:end]
        for token in ("playing = false;", "currentHolder = null;",
                      "controls.getProfile().save(this);",
                      "super.onConfigurationChanged(config);",
                      "keys.set(0);", "clearPhysicalController();",
                      "controls.releaseAll();", "audioResetRequested = true;",
                      "buildLayout();", "syncPlaying();"):
            self.assertIn(token, rotate)
        self.assertLess(rotate.index("currentHolder = null;"), rotate.index("buildLayout();"))
        self.assertLess(rotate.index("controls.getProfile().save(this);"),
                        rotate.index("super.onConfigurationChanged(config);"))
        self.assertLess(rotate.index("buildLayout();"), rotate.index("syncPlaying();"))
        for forbidden in ("SameBoyNative.nativeClose(", "SameBoyNative.nativeOpen(",
                          "saveBattery(", "persistAtomic(", "slotRequests.clear();",
                          "snapshotFile.delete(", "deleteRecursively(", "finish();"):
            self.assertNotIn(forbidden, rotate)

    def test_orientation_profiles_and_touch_release_survive(self):
        for token in ('snes ? "snes_" : ""',
                      "Configuration.ORIENTATION_LANDSCAPE",
                      "result.dpadX", "result.dpadY", "e.apply();"):
            self.assertIn(token, PROFILE)
        self.assertIn("profile = RetroControlProfile.load(context)", TOUCH)
        self.assertIn("releaseAll();", TOUCH)
        self.assertIn("calculateTargets(w, h);", TOUCH)
        self.assertIn("MotionEvent.ACTION_CANCEL", TOUCH)
        self.assertIn("if (!focused) releaseAll();", TOUCH)

    def test_beta9_identity_is_explicitly_new_but_same_package(self):
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)
        self.assertIn("--minimum-version-code 36", WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK", WORKFLOW)
        self.assertIn("RetroSlotRequestsTest", WORKFLOW)
        self.assertIn("RetroSaveSlotsTest", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
