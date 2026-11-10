import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile
import shutil
import unittest
import xml.etree.ElementTree as ET

MODULE = Path(__file__).resolve().parents[1] / "integrate.py"
spec = importlib.util.spec_from_file_location("okubi_integrate", MODULE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class IntegrationTests(unittest.TestCase):
    def create_fixture(self, base: Path):
        (base / "source").mkdir()
        java = base / "retro-module/java/bo/nexoplay/app/retro"
        java.mkdir(parents=True)
        for filename in ("RetroCore.java", "OkubiRetroActivity.java", "OkubiCatalogActivity.java",
                         "OkubiPokemonActivity.java", "RetroTouchPad.java", "RetroDpadHitMap.java", "RetroFileInspector.java", "RetroExpansionFormats.java", "RetroSystems.java", "RetroExternalLauncher.java", "RetroDeviceProfile.java", "RetroAspectFrame.java",
                         "SameBoyNative.java", "OkubiGameBoyActivity.java", "RetroControlProfile.java", "RetroControlPresets.java",
                         "OkubiPokemonRepository.java", "OkubiRomFormats.java", "OkubiPatchEngine.java",
                         "OkubiPersonalLibrary.java", "OkubiSaveArchive.java",
                         "RetroControllerAxes.java", "RetroRenderPolicy.java", "RetroFrameTiming.java", "RetroInputMask.java", "RetroSpeedControl.java", "RetroVisualFilters.java", "RetroScreenFit.java", "RetroCinemaStage.java", "RetroCinemaGeometry.java",
                         "RetroAudioRetry.java", "RetroRomReader.java", "RetroAdaptiveLayout.java", "OkubiIndieLibrary.java", "OkubiDownloadProgress.java", "RetroSaveSlots.java", "RetroSlotRequests.java", "RetroSlotPreviews.java"):
            (java / filename).write_text("// test placeholder")
        (java.parent / "OkubiGameScanner.java").write_text("// placeholder scanner")
        native = base / "retro-module/native"
        native.mkdir(parents=True)
        for filename in ("sameboy_bridge.c", "libretro.h", "CMakeLists.txt"):
            (native / filename).write_text("// build placeholder")

        xml = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
package="bo.nexoplay.app">
  <application android:label="NEXO Play">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
"""
        with ZipFile(base / "source/nexo-android-1.0.zip","w") as z:
            z.writestr("app/src/main/AndroidManifest.xml",xml)
            z.writestr("app/build.gradle","plugins {}\nandroid {\n    compileOptions { }\n}")
            z.writestr("app/src/main/java/bo/nexoplay/app/MainActivity.java",
                'class MainActivity { private void showHome() { String zen=Settings.ACTION_ZEN_MODE_SETTINGS; } }')
    def test_overlay_adds_all_sources_and_activities(self):
        with TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_fixture(root)
            output=root/"out"
            mod.integrate(root,output)
            manifest=output/"app/src/main/AndroidManifest.xml"
            tree=ET.parse(manifest)
            ns="{http://schemas.android.com/apk/res/android}name"
            activities=tree.findall("./application/activity")
            self.assertEqual(5,len(activities))
            self.assertEqual(5,len({a.get(ns) for a in activities}))
            sources=list((output/"app/src/main/java/bo/nexoplay/app/retro").glob("*.java"))
            self.assertEqual(38,len(sources))
            self.assertTrue(any(p.get(ns)=="android.permission.INTERNET"
                                for p in tree.findall("uses-permission")))
            app=tree.find("application")
            self.assertEqual("${okubiAppLabel}", app.get("{http://schemas.android.com/apk/res/android}label"))
            self.assertEqual(1,len(tree.findall("./application/activity/intent-filter")))
            player=next(a for a in activities if a.get(ns).endswith("OkubiGameBoyActivity"))
            self.assertEqual("orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|uiMode",
                             player.get("{http://schemas.android.com/apk/res/android}configChanges"))
            self.assertEqual("true", player.get(
                "{http://schemas.android.com/apk/res/android}resizeableActivity"))
            self.assertTrue((output/"app/src/main/cpp/CMakeLists.txt").exists())
            self.assertTrue((output/"app/src/main/java/bo/nexoplay/app/OkubiGameScanner.java").exists())
            build_gradle=(output/"app/build.gradle").read_text()
            self.assertIn("externalNativeBuild",build_gradle)
            self.assertIn("27.2.12479018",build_gradle)

            source=output/"app/src/main/java/bo/nexoplay/app/MainActivity.java"
            self.assertEqual(1,source.read_text(encoding="utf-8").count("okubiRetroShortcut.setOnClickListener"))
            self.assertNotIn("Settings.ACTION_ZEN_MODE_SETTINGS", source.read_text(encoding="utf-8"))
            self.assertIn("Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS", source.read_text(encoding="utf-8"))
            mod.integrate(root,output)
            tree=ET.parse(manifest)
            self.assertEqual(5,len(tree.findall("./application/activity")))
    def test_editable_android_project_has_priority(self):
        with TemporaryDirectory() as temp:
            root=Path(temp)
            self.create_fixture(root)
            project=root/"android-app"
            native=project/"app/src/main"
            (native/"java/bo/nexoplay/app").mkdir(parents=True)
            (native/"AndroidManifest.xml").write_text(
                '<?xml version="1.0"?><manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:label="OKUBI"/></manifest>', encoding="utf-8")
            (project/"app/build.gradle").write_text("plugins {}\\nandroid {\\n    compileOptions { }\\n}")
            main=(native/"java/bo/nexoplay/app/MainActivity.java")
            main.write_text(
                'class MainActivity { void startup(){ OkubiGameScanner.sync(this, catalog, store); '
                'showScreen(Screen.RETRO); showScreen(Screen.DISCOVER); } }',
                encoding="utf-8")
            output=root/"out"
            mod.integrate(root,output)
            actual=(output/"app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text()
            self.assertIn("Screen.DISCOVER",actual)
            self.assertNotIn("okubiRetroShortcut",actual)
            self.assertNotIn("private void showHome()",actual)
            self.assertTrue((output/"app/src/main/java/bo/nexoplay/app/OkubiGameScanner.java").exists())

    def test_zip_slip_is_rejected(self):
        with TemporaryDirectory() as temp:
            root=Path(temp)
            self.create_fixture(root)
            with ZipFile(root/"source/nexo-android-1.0.zip","a") as z:
                z.writestr("../escape.txt","unsafe")
            with self.assertRaises(ValueError):
                mod.integrate(root,root/"out")
            self.assertFalse((root/"escape.txt").exists())

if __name__ == "__main__":
    unittest.main()
