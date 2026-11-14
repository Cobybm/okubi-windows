"""Phase 5C: downloaded indie titles must not still show the download action.

These source regression guards complement the full Android Gradle compile.
Actual installed APK and SAF document access are checked on the user's phone.
"""
from pathlib import Path
import unittest

REPO=Path(__file__).resolve().parents[2]
RETRO=REPO/"retro-module/java/bo/nexoplay/app/retro"
CATALOG=(RETRO/"OkubiCatalogActivity.java").read_text(encoding="utf-8")
LINKS=(RETRO/"OkubiIndieLibrary.java").read_text(encoding="utf-8")
POKEMON=(RETRO/"OkubiPokemonActivity.java").read_text(encoding="utf-8")
BUILD=(REPO/"retro-module/integrate.py").read_text(encoding="utf-8")

class IndieDownloadedState(unittest.TestCase):
    def test_direct_download_reflects_on_disk_state_and_reopens_game(self):
        for expected in (
            "private boolean verifiedDirectGame(Game game)",
            "file.isFile() && file.length() >= 32768",
            "file.length() <= 8L * 1024 * 1024",
            'new File(getFilesDir(), "okubi/homebrew/ascension.gb")',
            'action.setText("▶ JUGAR EN OKUBI")' if False else "▶ JUGAR EN OKUBI",
            '"↓ DESCARGAR Y JUGAR"',
            '"⌛ DESCARGANDO..."',
            "private void playDirectGame(Game game)",
            "OkubiGameBoyActivity.EXTRA_HOMEBREW_PATH",
            "if (verifiedDirectGame(game))",
            "directDownloading = true",
            "directDownloading = false",
            "updateIndieAction(game)",
            "for (Game game : GAMES) updateIndieAction(game)",
            "private void refreshDownloadActions()",
            "protected void onResume()",
        ):
            self.assertIn(expected, CATALOG)

    def test_external_games_are_linked_explicitly_and_open_local_file(self):
        for expected in (
            "new Intent(Intent.ACTION_OPEN_DOCUMENT)",
            "FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
            "REQUEST_LINK_INDIE",
            "chooseIndieFile(g)",
            'OkubiIndieLibrary.saved(this, g.url, g.name)',
            "OkubiIndieLibrary.link(this, game.url, game.name",
            "OkubiIndieLibrary.play(this, g.url, g.name)",
            '"↗ VER DESCARGA DEL CREADOR"',
            '"＋ VINCULAR ARCHIVO DESCARGADO"',
            '"↻ CAMBIAR ARCHIVO DESCARGADO"',
            "pendingCreatorUrl",
            'bundle.putString("pending_indie_source"',
        ):
            self.assertIn(expected, CATALOG)

    def test_indie_persistence_is_per_title_not_per_site(self):
        self.assertIn('return "indie:" + title + "@" + creatorUrl;', LINKS)
        self.assertIn("public static Linked saved(Context context, String creatorUrl, String title)",LINKS)
        self.assertIn("public static Linked link(Context context,",LINKS)
        self.assertIn("takePersistableUriPermission",LINKS)
        self.assertIn("OkubiRomFormats.detect(",LINKS)
        self.assertIn("RetroFileInspector.inspect(",LINKS)
        self.assertIn("OkubiPokemonRepository.add(context, uri, title, detected.system)",LINKS)
        self.assertIn("OkubiPokemonRepository.markPlayed(context, file.uri.toString())",LINKS)
        self.assertIn('new Intent(context, OkubiGameBoyActivity.class)',LINKS)
        self.assertIn("EXTRA_ROM, file.uri",LINKS)
        self.assertIn("Intent.FLAG_GRANT_READ_URI_PERMISSION",LINKS)
        self.assertIn("try (InputStream input = context.getContentResolver().openInputStream(uri))",LINKS)
        self.assertNotIn("new FileOutputStream(",LINKS)

    def test_indie_pokemon_tab_uses_same_shared_link_without_fake_install(self):
        for expected in (
            "REQUEST_LINK_FREE_INDIE",
            "pendingFreeIndieSource",
            "renderFree(LinearLayout root)",
            "OkubiIndieLibrary.saved(this, indie[3], indie[0])",
            "OkubiIndieLibrary.play(this, indie[3], indie[0])",
            "OkubiIndieLibrary.link(this, selected[3], selected[0]",
            "draw(true)",
            '"▶ JUGAR EN OKUBI"',
            '"＋ VINCULAR ROM DESCARGADA"',
            '"↗ DESCARGAR DEL CREADOR"',
        ):
            self.assertIn(expected,POKEMON)

    def test_patch_download_states_do_not_duplicate_completed_requests(self):
        for expected in (
            "private int verifiedPatchStatus(DirectPatch patch)",
            "manager.openDownloadedFile(id)",
            'action.setText("✓ DESCARGADO · APLICAR PARCHE")',
            '"⌛ DESCARGANDO PARCHE..."',
            "DownloadManager.STATUS_FAILED",
            "openPatcher()",
        ):
            self.assertIn(expected,CATALOG)

    def test_build_contains_helper_and_rom_and_saves_untouched(self):
        self.assertIn('"OkubiIndieLibrary.java"',BUILD)
        self.assertIn('"RetroDpadHitMap.java"',BUILD)
        self.assertNotIn("SameBoyNative.nativeStep",CATALOG)
        self.assertNotIn("saveFile",LINKS)
        self.assertNotIn("FileOutputStream",LINKS)

if __name__=="__main__":
    unittest.main()
