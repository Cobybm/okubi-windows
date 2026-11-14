"""Regression guards for the low-end-device code paths.
These are source contract tests; real FPS, crashes and input latency require an APK and Android device.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "java/bo/nexoplay/app/retro"
WORKFLOW = ROOT.parent / ".github/workflows/okubi-retro.yml"

class PerformanceContractsTest(unittest.TestCase):
    def test_touch_has_no_per_event_new_objects(self):
        source = (JAVA / "RetroTouchPad.java").read_text(encoding="utf-8")
        body = source.split("@Override public boolean onTouchEvent(MotionEvent event)", 1)[1]
        body = body.split("@Override public boolean performClick()", 1)[0]
        self.assertNotIn("new ", body)
        self.assertIn("getPointerCount()", body)
        self.assertIn("ACTION_POINTER_UP", body)
        self.assertIn("requestDisallowInterceptTouchEvent(true)", body)
        self.assertIn("postInvalidateOnAnimation()", source)

    def test_switch_does_not_rebuild_screen(self):
        source = (JAVA / "OkubiRetroActivity.java").read_text(encoding="utf-8")
        segment = source.split("private void chooseSystem(int next)", 1)[1].split(
            "private void refreshSelected", 1)[0]
        self.assertNotIn("buildInterface(", segment)
        self.assertIn("refreshSelected(before)", segment)
        self.assertIn("fileIo.execute(", source)
        self.assertIn("runOnUiThread(", source)

    def test_pokemon_import_not_on_main_thread(self):
        source = (JAVA / "OkubiPokemonActivity.java").read_text(encoding="utf-8")
        self.assertIn("fileIo.execute(", source)
        self.assertIn("runOnUiThread(", source)
        self.assertIn("new HorizontalScrollView(this)", source)

    def test_native_aspect_and_android_toolchain(self):
        aspect = (JAVA / "RetroAspectFrame.java").read_text(encoding="utf-8")
        self.assertIn("surfaceWidth", aspect)
        self.assertIn("surfaceHeight", aspect)
        config = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("gradle-version: '8.11.1'", config)
        self.assertIn("platforms;android-35", config)

if __name__ == "__main__":
    unittest.main()
