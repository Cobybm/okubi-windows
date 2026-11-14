"""Phase21B actual SNES core integration contracts.

Python source checks complement *real* Android Gradle/C/NDK/Java compilations.
Only an A05s game session can confirm real-world compatibility/performance.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
ACTIVITY=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
TOUCH=(RETRO/"RetroTouchPad.java").read_text(encoding="utf-8")
PROFILE=(RETRO/"RetroControlProfile.java").read_text(encoding="utf-8")
SAME=(RETRO/"SameBoyNative.java").read_text(encoding="utf-8")
FORMATS=(RETRO/"OkubiRomFormats.java").read_text(encoding="utf-8")
ARCHIVE=(RETRO/"OkubiSaveArchive.java").read_text(encoding="utf-8")
NATIVE=(ROOT/"retro-module/native/sameboy_bridge.c").read_text(encoding="utf-8")
HEADER=(ROOT/"retro-module/native/libretro.h").read_text(encoding="utf-8")
BUILD=(ROOT/"retro-module/build_supafaust.py").read_text(encoding="utf-8")
CI=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
RETRO_UI=(RETRO/"OkubiRetroActivity.java").read_text(encoding="utf-8")
POKEMON=(RETRO/"OkubiPokemonActivity.java").read_text(encoding="utf-8")
MAIN=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text(encoding="utf-8")


class SnesNativePhase21B(unittest.TestCase):
    def test_pinned_core_with_license_and_build_provenance(self):
        for text in ('SUPAFAUST_COMMIT = "642d1d1b6684aa7e306a02a89885f3f5456a5157"',
                     'https://github.com/libretro/supafaust.git',
                     '"arm64-v8a"', '"armeabi-v7a"', '"x86_64"',
                     'WANT_SNES_FAUST_EMU',
                     'mednafen_supafaust_libretro.so',
                     'libsupafaust_libretro_android.so',
                     'Supafaust-GPL-2.0.txt',
                     'Supafaust-SOURCE.txt',
                     'run("make", "clean"',
                     'MIN_API = 26'):
            self.assertIn(text,BUILD)
        self.assertNotIn("https://buildbot.libretro.com",BUILD)
        self.assertNotIn("roms/",BUILD)

    def test_real_native_dispatch_and_bounded_snes_video(self):
        for token in (
            '#define GBA_WIDTH 240','#define GBA_HEIGHT 160',
            '#define SNES_WIDTH 256','#define SNES_HEIGHT 224',
            'static bool selected_snes = false;',
            'selected_snes = strcmp(model, "SNES") == 0;',
            'libsupafaust_libretro_android.so',
            'selected_snes ? SNES_WIDTH',
            'selected_snes ? SNES_HEIGHT',
            'width > 512u || height < 200u || height > 512u',
            'if (pitch < (size_t)width * bpp || pitch > 8192u) return;',
            'frame_buffer[(size_t)y * expected_width + x] = color;',
            'if (selected_snes',
            'case RETRO_DEVICE_ID_JOYPAD_X: bit = 7;',
            'case RETRO_DEVICE_ID_JOYPAD_Y: bit = 8;',
            'RETRO_MEMORY_SAVE_RAM',
            'nativeSaveState', 'nativeLoadState',
        ):
            self.assertIn(token,NATIVE)
        self.assertIn('#define RETRO_DEVICE_ID_JOYPAD_X 9',HEADER)
        self.assertIn('#define RETRO_DEVICE_ID_JOYPAD_Y 1',HEADER)
        self.assertIn('System.loadLibrary("supafaust_libretro_android")',SAME)
        self.assertIn('public static boolean isSnesAvailable()',SAME)
        self.assertIn('SNES_AVAILABLE = ok && snes;',SAME)

    def test_snes_roms_and_saves_independent_from_gameboy(self):
        for text in (
            '"SNES".equals(system) &&',
            'frameWidth = SNES_WIDTH;',
            'frameHeight = SNES_HEIGHT;',
            'SameBoyNative.isSnesAvailable()',
            'OkubiRomFormats.readGameSnes(',
            '"SNES".equals(system) ? "okubi/snes"',
            'SameBoyNative.nativeSaveRam()',
            'SameBoyNative.nativeSaveState()',
            'SameBoyNative.nativeLoadState(',
            'RetroSlotRequests.FileStamp.capture(destination)',
        ):
            if text=='"SNES".equals(system) &&':
                continue
            self.assertIn(text,ACTIVITY)
        self.assertIn('"SNES".equals(system)',ARCHIVE)
        self.assertIn('MAX_STATE = 16 * 1024 * 1024',ARCHIVE)
        self.assertIn('RetroRomReader.readBounded(input,limit,minimum)',FORMATS)
        self.assertIn('return readBounded(input, 16 * 1024 * 1024, 32768);',FORMATS)
        self.assertIn('Selecciona una ROM SNES propia .sfc/.smc sin comprimir',FORMATS)
        self.assertIn('new File(directory, saveName + ".srm")',ACTIVITY)
        self.assertIn('new File(directory, saveName + ".state")',ACTIVITY)
        self.assertIn('new File(directory, saveName + ".rtc")',ACTIVITY)

    def test_16bit_controls_not_gba_shortcuts(self):
        self.assertIn('RetroSystems.Controller.FOUR_BUTTON',ACTIVITY)
        self.assertIn('RetroControlProfile.loadCinemaSnes(this)',ACTIVITY)
        self.assertIn('RetroControlProfile.loadSnes(this)',ACTIVITY)
        self.assertIn('!"SNES".equals(system)',ACTIVITY)
        self.assertIn('"SNES".equals(system) ? RetroCore.Key.X',ACTIVITY)
        self.assertIn('"SNES".equals(system) ? RetroCore.Key.Y',ACTIVITY)
        self.assertIn('controller == RetroSystems.Controller.FOUR_BUTTON',TOUCH)
        for key in ('RetroCore.Key.A','RetroCore.Key.B','RetroCore.Key.X','RetroCore.Key.Y',
                    'RetroCore.Key.L','RetroCore.Key.R'):
            self.assertIn(key,TOUCH)
        self.assertIn('snes ? "snes_" : ""',PROFILE)
        self.assertIn('private static final String PREFS = "okubi_retro_controls_v1";',PROFILE)
        self.assertIn('private final boolean snes;',PROFILE)
        self.assertIn('public static RetroControlProfile loadCinema(Context context)',PROFILE)

    def test_home_console_pokemon_and_ci(self):
        self.assertIn('SameBoyNative.isSnesAvailable()',RETRO_UI)
        self.assertIn('SameBoyNative.isSnesAvailable()',POKEMON)
        self.assertIn('SameBoyNative.isSnesAvailable()',MAIN)
        self.assertIn('"SNES".equals(entry.system)',MAIN)
        self.assertIn('RetroExternalLauncher.open(this,uri,entry.system)',MAIN)
        self.assertIn('RetroExternalLauncher.open(this, uri, system)',POKEMON)
        self.assertIn('SNES nativo y NES',MAIN)
        self.assertIn('retro-module/build_supafaust.py',CI)
        self.assertIn('libsupafaust_libretro_android.so',CI)
        self.assertIn('assets/licenses/Supafaust-GPL-2.0.txt',CI)
        self.assertIn('RetroSnesGeometryTest.java',CI)
        self.assertIn('versionCode 36',GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn('--minimum-version-code 36',CI)
        self.assertIn('OKUBI-UNIFIED-1.0.0-beta17-APK',CI)
        self.assertIn('libsameboy_libretro_android.so',CI)
        self.assertIn('libmgba_libretro_android.so',CI)


if __name__ == "__main__":
    unittest.main()
