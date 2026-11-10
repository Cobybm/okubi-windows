"""Regression contracts for OKUBI rebrand and installed-game discovery.

These check source and integration. Actual package visibility and performance
still require testing on a Samsung Galaxy A05s.
"""
from pathlib import Path
import unittest

REPO = Path(__file__).resolve().parents[2]
APP = REPO / "android-app/app/src/main"
JAVA = APP / "java/bo/nexoplay/app"
SCANNER = REPO / "retro-module/java/bo/nexoplay/app/OkubiGameScanner.java"

class OkubiBrandAndDiscoveryTest(unittest.TestCase):
    def test_single_brand_and_new_logo(self):
        activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        prefs = (JAVA / "AppearancePrefs.java").read_text(encoding="utf-8")
        ui = (JAVA / "CinemaUi.java").read_text(encoding="utf-8")
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        drawable = (APP / "res/drawable/ic_okubi.xml").read_text(encoding="utf-8")
        self.assertIn('android:label="${okubiAppLabel}"', manifest)
        self.assertIn('android:icon="@drawable/ic_okubi"', manifest)
        self.assertIn('R.drawable.ic_okubi', activity)
        self.assertIn('TextView logo=text("OKUBI"', activity)
        self.assertIn('TextView kanji=text(" 遊"', activity)
        self.assertNotIn('NEXO / CINEMA', activity)
        self.assertNotIn('mark=text("遊"', activity)
        self.assertIn('String stored=prefs(context).getString("palette",ROSE)', prefs)
        self.assertIn('0xffff3e70', prefs)
        self.assertIn('0xFFFF3E70', ui)
        self.assertIn('strokeColor="#F34783"', drawable)
        self.assertNotIn('遊', drawable)

    def test_games_auto_discover_and_manual_exclusion(self):
        activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        scanner = SCANNER.read_text(encoding="utf-8")
        store = (JAVA / "GameStore.java").read_text(encoding="utf-8")
        catalog = (JAVA / "GameCatalog.java").read_text(encoding="utf-8")
        self.assertIn('OkubiGameScanner.sync(this, catalog, store)', activity)
        self.assertIn('OkubiGameScanner.sync(this,catalog,store)', activity)
        self.assertIn('AÑADIR JUEGO MANUALMENTE', activity)
        self.assertIn('REESCANEAR', activity)
        self.assertIn('ApplicationInfo.CATEGORY_GAME', scanner)
        self.assertIn('ApplicationInfo.FLAG_IS_GAME', scanner)
        self.assertIn('name.equals("worldbox")', scanner)
        self.assertIn('name.startsWith("beholder:")', scanner)
        self.assertIn('name.equals("sophia")', scanner)
        self.assertIn('selectedPackages', scanner)
        self.assertIn('excludedPackages', scanner)
        self.assertIn('okubi_hidden_components', store)
        self.assertIn('Intent.CATEGORY_LAUNCHER', catalog)

    def test_catalog_scan_cost_is_bounded(self):
        catalog = (JAVA / "GameCatalog.java").read_text(encoding="utf-8")
        activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("RELOAD_INTERVAL_MS = 15_000L", catalog)
        self.assertIn("new LruCache<>(96)", catalog)
        self.assertIn("public void forceReload()", catalog)
        self.assertIn("if (catalog.reload()) OkubiGameScanner.sync", activity)
        self.assertIn("catalog.forceReload()", activity)

    def test_integrated_navigation(self):
        activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        for expected in (
                'Screen.RETRO', 'Screen.DISCOVER',
                'showRetroLanding()', 'showDiscover()',
                'OkubiPokemonActivity.class', 'OkubiCatalogActivity.class',
                'ABRIR CONSOLAS'):
            self.assertIn(expected, activity)
        self.assertNotIn('okubiRetroShortcut', activity)

if __name__ == '__main__':
    unittest.main()
