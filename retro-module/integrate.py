#!/usr/bin/env python3
"""Overlay the stand-alone OKUBI retro feature onto the original Android ZIP.
Idempotent and fail-fast: do not silently overwrite existing app sources.
"""
from pathlib import Path
from zipfile import ZipFile
import argparse
import shutil
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)

def integrate(repo: Path, output: Path):
    archive = repo / "source/nexo-android-1.0.zip"
    source_project = repo / "android-app"
    output.mkdir(parents=True, exist_ok=True)
    if (source_project / "app/src/main/AndroidManifest.xml").is_file():
        # The editable application is authoritative. Never revert to the old ZIP.
        shutil.copytree(source_project, output, dirs_exist_ok=True)
    else:
        if not archive.is_file():
            raise FileNotFoundError(archive)
        with ZipFile(archive) as bundle:
            for item in bundle.infolist():
                target = (output / item.filename).resolve()
                if not target.is_relative_to(output.resolve()):
                    raise ValueError("Unsafe archive entry: " + item.filename)
            bundle.extractall(output)
    manifest = output / "app/src/main/AndroidManifest.xml"
    if not manifest.is_file():
        raise FileNotFoundError("Expected AndroidManifest.xml after extraction")
    source = repo / "retro-module/java/bo/nexoplay/app/retro"
    destination = output / "app/src/main/java/bo/nexoplay/app/retro"
    destination.mkdir(parents=True, exist_ok=True)
    expected = ("RetroCore.java", "OkubiRetroActivity.java", "OkubiCatalogActivity.java",
                "OkubiPokemonActivity.java", "RetroTouchPad.java", "RetroDpadHitMap.java", "RetroFileInspector.java", "RetroExpansionFormats.java", "RetroSystems.java", "RetroExternalLauncher.java", "RetroDeviceProfile.java", "RetroAspectFrame.java",
                "SameBoyNative.java", "OkubiGameBoyActivity.java", "RetroControlProfile.java", "RetroControlPresets.java",
                "OkubiPokemonRepository.java", "OkubiRomFormats.java", "OkubiPatchEngine.java",
                "OkubiPersonalLibrary.java", "OkubiSaveArchive.java",
                "RetroControllerAxes.java", "RetroRenderPolicy.java", "RetroFrameTiming.java", "RetroInputMask.java", "RetroSpeedControl.java", "RetroVisualFilters.java", "RetroScreenFit.java",
                "RetroAudioRetry.java", "RetroRomReader.java", "RetroAdaptiveLayout.java", "RetroCinemaStage.java", "RetroCinemaGeometry.java", "OkubiIndieLibrary.java", "OkubiDownloadProgress.java", "RetroSaveSlots.java", "RetroSlotRequests.java", "RetroSlotPreviews.java")
    for filename in expected:
        src = source / filename
        if not src.is_file():
            raise FileNotFoundError(src)
        shutil.copy2(src, destination / filename)

    scanner_src = repo / "retro-module/java/bo/nexoplay/app/OkubiGameScanner.java"
    scanner_dest = output / "app/src/main/java/bo/nexoplay/app/OkubiGameScanner.java"
    if not scanner_src.is_file():
        raise FileNotFoundError(scanner_src)
    shutil.copy2(scanner_src, scanner_dest)

    # Native bridge is built by Android Gradle Plugin + NDK after overlay.
    native_src = repo / "retro-module/native"
    native_dest = output / "app/src/main/cpp"
    native_dest.mkdir(parents=True, exist_ok=True)
    for filename in ("sameboy_bridge.c", "libretro.h", "CMakeLists.txt"):
        shutil.copy2(native_src / filename, native_dest / filename)

    gradle_file = output / "app/build.gradle"
    build_gradle = gradle_file.read_text(encoding="utf-8")
    gradle_anchor = "    compileOptions {"
    if "externalNativeBuild" not in build_gradle and build_gradle.count(gradle_anchor) != 1:
        raise ValueError("Android build.gradle has changed: compileOptions not found")
    native_config = """    ndkVersion '27.2.12479018'
    externalNativeBuild {
        cmake {
            path 'src/main/cpp/CMakeLists.txt'
            version '3.22.1'
        }
    }
"""
    if "externalNativeBuild" not in build_gradle:
        build_gradle = build_gradle.replace(gradle_anchor, native_config + gradle_anchor)
    gradle_file.write_text(build_gradle, encoding="utf-8")

    # Keep a single launcher icon: integrate the retro entry into legacy Home.
    main_activity = output / "app/src/main/java/bo/nexoplay/app/MainActivity.java"
    if not main_activity.is_file():
        raise FileNotFoundError("Missing original MainActivity.java")
    main_source = main_activity.read_text(encoding="utf-8")
    if "OkubiGameScanner.sync(this, catalog, store)" not in main_source:
        # Backward-compatible fallback for the archived v1 project.
        home_anchor = "private void showHome() {"
        if main_source.count(home_anchor) != 1:
            raise ValueError("MainActivity showHome() entry point is missing or ambiguous")
        shortcut = (
            home_anchor + '\n'
            + '        TextView okubiRetroShortcut=CinemaUi.action(this,"OKUBI / RETRO",true);\n'
            + '        body.addView(okubiRetroShortcut,lp(-1,-2));\n'
            + '        body.addView(gap(12));\n'
            + '        okubiRetroShortcut.setOnClickListener(v->startActivity(\n'
            + '            new android.content.Intent(this, bo.nexoplay.app.retro.OkubiRetroActivity.class)));'
        )
        main_source = main_source.replace(home_anchor, shortcut)
    else:
        if "Screen.RETRO" not in main_source or "Screen.DISCOVER" not in main_source:
            raise ValueError("Missing integrated OKUBI Retro/Discover navigation")
    main_source = main_source.replace("Settings.ACTION_ZEN_MODE_SETTINGS",
                                      "Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS")
    main_source = main_source.replace('brand=text("NEXO / CINEMA",16',
                                      'brand=text("OKUBI",20')
    main_source = main_source.replace('mark=text("N",25', 'mark=text("◆",25')
    main_activity.write_text(main_source, encoding="utf-8")

    tree = ET.parse(manifest)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise ValueError("Missing <application> in AndroidManifest.xml")
    app.set("{%s}label" % ANDROID_NS, "${okubiAppLabel}")
    permission = "{%s}name" % ANDROID_NS
    if not any(el.get(permission) == "android.permission.INTERNET" for el in root.findall("uses-permission")):
        ET.Element("noop")  # do not change the app's target API or other permissions
        root.insert(0, ET.Element("uses-permission", {permission: "android.permission.INTERNET"}))
    for short_name in ("OkubiRetroActivity", "OkubiCatalogActivity", "OkubiPokemonActivity", "OkubiGameBoyActivity"):
        full_name = "bo.nexoplay.app.retro." + short_name
        matched = [el for el in app.findall("activity") if el.get(permission) == full_name]
        if matched:
            activity = matched[0]
        else:
            activity = ET.SubElement(app, "activity", {
                permission: full_name,
                "{%s}exported" % ANDROID_NS: "false",
                "{%s}screenOrientation" % ANDROID_NS: "unspecified",
            })
        if short_name == "OkubiRetroActivity":
            # ADB smoke tests may launch this screen explicitly without adding a second app icon.
            activity.set("{%s}exported" % ANDROID_NS, "true")
            activity.set("{%s}label" % ANDROID_NS, "OKUBI Retro")
        if short_name == "OkubiGameBoyActivity":
            # Resize/fold/split-screen/configuration changes must re-measure
            # the game SurfaceView and touch overlay without reopening ROMs.
            activity.set("{%s}configChanges" % ANDROID_NS,
                         "orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|uiMode")
            activity.set("{%s}resizeableActivity" % ANDROID_NS, "true")
            activity.set("{%s}screenOrientation" % ANDROID_NS, "unspecified")
    tree.write(manifest, encoding="utf-8", xml_declaration=True)
    print("OKUBI overlay completed:", output)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", default="build/okubi-android")
    args = parser.parse_args()
    integrate(Path(args.repo).resolve(), Path(args.output).resolve())
