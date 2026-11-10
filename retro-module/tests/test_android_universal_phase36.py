"""Phase 36: all four Android ABIs and safe layouts.

Real NDK/Gradle builds and JVM layout tests are separately performed by CI.
This source contract prevents accidental gaps on follow-up branches.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT / "retro-module"
PLAYER = (RETRO / "java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java").read_text(encoding="utf-8")
INTEGRATOR = (RETRO / "integrate.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")
NATIVE = (RETRO / "native/CMakeLists.txt").read_text(encoding="utf-8")

class AndroidUniversalPhase36(unittest.TestCase):
    def test_every_supported_android_abi_builds_every_core(self):
        for script in ("build_sameboy.py", "build_mgba.py", "build_supafaust.py"):
            source = (RETRO / script).read_text(encoding="utf-8")
            for abi in ("arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
                self.assertIn('"' + abi + '"', source, (script, abi))
        self.assertIn("i686-linux-android", (RETRO / "build_supafaust.py").read_text(encoding="utf-8"))
        self.assertIn("for arch in arm64-v8a armeabi-v7a x86 x86_64", WORKFLOW)
        self.assertIn("'x86', 'x86_64'", WORKFLOW)
        for library in ("libsameboy_libretro_android.so", "libmgba_libretro_android.so",
                        "libsupafaust_libretro_android.so", "libokubi_sameboy_bridge.so"):
            self.assertIn(library, WORKFLOW)
        self.assertIn("okubi_sameboy_bridge", NATIVE)

    def test_foldable_resize_and_accessible_toolbar(self):
        self.assertIn("RetroAdaptiveLayout.java", INTEGRATOR)
        self.assertIn("smallestScreenSize|screenLayout|density", INTEGRATOR)
        self.assertIn("resizeableActivity", INTEGRATOR)
        self.assertIn("RetroAdaptiveLayout.compactBrand(", PLAYER)
        self.assertIn("RetroAdaptiveLayout.hideHeaderStatus(", PLAYER)
        self.assertIn("android.widget.HorizontalScrollView scrollActions", PLAYER)
        self.assertIn("header.addView(scrollActions,", PLAYER)
        self.assertIn("header.addView(menu,p4);", PLAYER)
        self.assertIn("getSafeInsetLeft()", PLAYER)
        self.assertIn("getSafeInsetRight()", PLAYER)
        self.assertIn("getSafeInsetTop()", PLAYER)
        self.assertIn("getSafeInsetBottom()", PLAYER)
        self.assertIn("onConfigurationChanged(Configuration config)", PLAYER)
        self.assertIn("pad.setCinemaGeometry(geometry);",
                      (RETRO / "java/bo/nexoplay/app/retro/RetroCinemaStage.java").read_text(encoding="utf-8"))
        self.assertIn("RetroAdaptiveLayoutTest.java", WORKFLOW)

    def test_all_user_data_remains_compatible(self):
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)", PLAYER)
        self.assertIn("slotRequests.poll()", PLAYER)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)
        self.assertIn("minSdk 26", GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'", GRADLE)

if __name__ == "__main__":
    unittest.main()
