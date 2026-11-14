"""Fase 21: honest and secure SNES/NES/SEGA/Atari catalog routing.

Assertions are complementary to the real RetroExpansionFormatsTest JVM test.
In phase 21B, SNES has a native core; other systems still route externally.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
MAIN=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text(encoding="utf-8")
FORMATS=(RETRO/"OkubiRomFormats.java").read_text(encoding="utf-8")
POLICY=(RETRO/"RetroExpansionFormats.java").read_text(encoding="utf-8")
SPECS=(RETRO/"RetroSystems.java").read_text(encoding="utf-8")
INSPECTOR=(RETRO/"RetroFileInspector.java").read_text(encoding="utf-8")
REPO=(RETRO/"OkubiPokemonRepository.java").read_text(encoding="utf-8")
RETROUI=(RETRO/"OkubiRetroActivity.java").read_text(encoding="utf-8")
POKEMON=(RETRO/"OkubiPokemonActivity.java").read_text(encoding="utf-8")
LAUNCHER=(RETRO/"RetroExternalLauncher.java").read_text(encoding="utf-8")
INTEGRATE=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
CI=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class RetroExpansionPhase21(unittest.TestCase):
    def test_new_systems_recognized_and_registered_without_migration(self):
        self.assertIn("RetroExpansionFormats.systemFromName(s)",FORMATS)
        self.assertIn("RetroExpansionFormats.isExternalClassic(system)",FORMATS)
        self.assertIn("RetroExpansionFormats.isExternalClassic(system)",REPO)
        self.assertIn('"okubi_pokemon_repository"',REPO)
        self.assertIn("MAX_ENTRIES = 250",REPO)
        for pair in ('"SNES", new String[] {".sfc", ".smc"}',
                     '"NES", new String[] {".nes", ".unf"}',
                     '"MD", new String[] {".md", ".gen"}',
                     '"SMS", new String[] {".sms"}',
                     '"GG", new String[] {".gg"}',
                     '"PCE", new String[] {".pce"}',
                     '"A2600", new String[] {".a26"}'):
            self.assertIn(pair,POLICY)
        self.assertIn('Performance.ECO, "nes,unf",',SPECS)
        self.assertIn('Performance.ECO, "sfc,smc",',SPECS)
        for platform in ("GBA", "GB", "GBC"):
            self.assertIn(f'new SystemSpec("{platform}"',SPECS)
        self.assertIn("RetroExpansionFormats.validKnownSize(system,size)",INSPECTOR)

    def test_archive_and_storage_safety(self):
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT",RETROUI)
        self.assertIn("Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION",RETROUI)
        self.assertIn("takePersistableUriPermission(file",RETROUI)
        self.assertIn("RetroFileInspector.inspect(",RETROUI)
        self.assertIn("RetroExpansionFormats.isExternalClassic(system)",INSPECTOR)
        self.assertIn("Extrae el juego",INSPECTOR)
        self.assertIn("OkubiRomFormats.inspectZip(resolver, uri)",INSPECTOR)
        self.assertIn("if (!patch && size > 0)",INSPECTOR)
        self.assertIn('new Intent(Intent.ACTION_VIEW)',LAUNCHER)
        self.assertIn("intent.setClipData(ClipData.newUri(",LAUNCHER)
        self.assertIn("Intent.FLAG_GRANT_READ_URI_PERMISSION",LAUNCHER)
        self.assertIn("Intent.createChooser(intent",LAUNCHER)
        self.assertIn("RetroExpansionFormats.isExternalClassic(console)",LAUNCHER)
        self.assertIn('!"content".equalsIgnoreCase(file.getScheme())',LAUNCHER)
        self.assertNotIn("FileOutputStream",POLICY)
        self.assertNotIn("HttpURLConnection",POLICY)
        self.assertNotIn("new File(",POLICY)
        self.assertNotIn("getFilesDir(",POLICY)
        self.assertNotIn("delete(",POLICY)

    def test_explicit_external_install_help_not_sideload(self):
        self.assertIn("RetroExpansionFormats.officialEmulatorPage(console)",LAUNCHER)
        self.assertIn('"https".equalsIgnoreCase(uri.getScheme())',LAUNCHER)
        self.assertIn("Intent.CATEGORY_BROWSABLE",LAUNCHER)
        self.assertIn("openOfficialEmulatorPage(",RETROUI)
        self.assertIn("RetroExpansionFormats.isExternalClassic(spec.id)",RETROUI)
        self.assertIn("officialEmulatorButton.setVisibility(",RETROUI)
        self.assertIn("Núcleo no disponible",RETROUI)
        self.assertNotIn("ACTION_INSTALL_PACKAGE",LAUNCHER)
        self.assertNotIn("ACTION_DELETE",LAUNCHER)
        self.assertNotIn("SameBoyNative.nativeOpen(",LAUNCHER)

    def test_ui_quick_console_entrypoints_and_library_game_cards(self):
        self.assertIn('public static final String EXTRA_SYSTEM = "okubi.open.console"',RETROUI)
        self.assertIn("requested.equals(RetroSystems.byPosition(i).id)",RETROUI)
        self.assertIn('"SNES".equals(spec.id)',RETROUI)
        self.assertIn('openClassicConsole("SNES")',MAIN)
        self.assertIn('openClassicConsole("NES")',MAIN)
        self.assertIn('bo.nexoplay.app.retro.OkubiRetroActivity.EXTRA_SYSTEM,system',MAIN)
        self.assertIn("RetroExpansionFormats.isExternalClassic(",MAIN)
        self.assertIn("RetroExpansionFormats.isExternalClassic(game.system)",POKEMON)
        self.assertIn("RetroExpansionFormats.isExternalClassic(system)",POKEMON)
        self.assertIn("RetroExternalLauncher.open(this, uri, system);",POKEMON)
        self.assertIn("if (remember && hasInternal) {",POKEMON)
        self.assertIn("OkubiPokemonRepository.add(this,uri",POKEMON)
        self.assertIn("OkubiPokemonRepository.add(this,file",RETROUI)

    def test_original_cores_and_data_untouched(self):
        self.assertIn("SameBoyNative.isAvailable()",RETROUI)
        self.assertIn("SameBoyNative.isGbaAvailable()",RETROUI)
        self.assertIn("OkubiGameBoyActivity.class",RETROUI)
        self.assertIn('new SystemSpec("GBA", "Game Boy Advance", 240, 160',SPECS)
        self.assertIn("RetroExpansionFormats.java",INTEGRATE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("--minimum-version-code 36",CI)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",CI)
        self.assertIn("RetroExpansionFormatsTest.java",CI)
        self.assertIn("RetroSaveSlotsTest.java",CI)
        self.assertIn("RetroSlotFileStampTest.java",CI)
        self.assertIn("RetroSlotPreviewsTest.java",CI)
        # No new native emulator is claimed in the build pipeline.
        self.assertIn("libsupafaust_libretro_android.so",CI)


if __name__=="__main__":
    unittest.main()
