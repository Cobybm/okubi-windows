"""Regression checks for externally linked Nintendo / Pokemon fan directories.

These user-selected websites are browser-only navigation sources, NEVER files
pulled by an automatic downloader inside OKUBI.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CATALOG = (ROOT / "java/bo/nexoplay/app/retro/OkubiCatalogActivity.java").read_text(encoding="utf-8")
POKEMON = (ROOT / "java/bo/nexoplay/app/retro/OkubiPokemonActivity.java").read_text(encoding="utf-8")
MAIN = (ROOT.parent / "android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text(encoding="utf-8")

SOURCES = (
    "https://whackahack.com/juegos/",
    "https://pokemundo.com/category/hack-roms-pokemon-gba/",
    "https://fullgbaroms.blogspot.com/p/roms-de-pokemon-para-gba-gameboy-avance.html",
)

class NintendoSourcesTests(unittest.TestCase):
    def test_all_user_sources_in_nintendo_cards(self):
        for url in SOURCES:
            self.assertIn(url, CATALOG)
        self.assertIn("NINTENDO  /  FANGAMES Y ROM HACKS", CATALOG)
        self.assertIn("showNintendoSources(root)", CATALOG)
        self.assertIn("EXTERNO", CATALOG)
        self.assertIn("Explorar sitio", CATALOG)

    def test_links_are_browser_only(self):
        self.assertIn("new Intent(Intent.ACTION_VIEW, uri)", CATALOG)
        self.assertIn('addCategory(Intent.CATEGORY_BROWSABLE)', CATALOG)
        self.assertIn('if (!"https".equalsIgnoreCase(uri.getScheme())) return;', CATALOG)
        self.assertIn("OKUBI no aloja ni descarga ROMs comerciales", CATALOG)
        self.assertIn("if (g.direct) {", CATALOG)
        self.assertIn("else if (!directDownloading) download(g);", CATALOG)
        self.assertIn("verifiedDirectGame(g)", CATALOG)
        self.assertIn('!"/wolandark/Ascension/master/build/rom/game.gb".equals(uri.getPath())', CATALOG)
        for url in SOURCES:
            self.assertNotIn(f'download("{url}")', CATALOG)

    def test_accessible_from_pokemon_and_discover(self):
        self.assertIn("https://whackahack.com/juegos/", POKEMON)
        self.assertIn("https://pokemundo.com/category/hack-roms-pokemon-gba/", POKEMON)
        self.assertIn("Ver todas las fuentes de Nintendo", POKEMON)
        self.assertIn("NINTENDO / DESCARGAS Y ROM HACKS", MAIN)
        self.assertIn("bo.nexoplay.app.retro.OkubiCatalogActivity.class", MAIN)

if __name__ == "__main__":
    unittest.main()
