"""Contracts for the playable Pokémon personal repository in OKUBI.
These tests do not distribute ROMs, replace Android instrumentation or run a
copyrighted game without user-provided files.
"""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]
RETRO = ROOT / "retro-module/java/bo/nexoplay/app/retro"
CATALOG = ROOT / "catalogs/okubi-pokemon-v1.json"
ACTIVITY = RETRO / "OkubiPokemonActivity.java"
LIBRARY = RETRO / "OkubiPokemonRepository.java"

class PokemonRepositoryTests(unittest.TestCase):
    def test_catalog_metadata_only_and_consistent(self):
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        self.assertEqual("OKUBI", catalog["project"])
        self.assertEqual(43, len(catalog["games"]))
        self.assertEqual(6, len(catalog["patches"]))
        self.assertEqual(5, len(catalog["free_demos"]))
        self.assertEqual(6, len(catalog["android_official"]))
        self.assertEqual(7, len(catalog["patch_sources"]))
        self.assertTrue(all(item["downloadKind"] != "commercial-ROM" for item in catalog["patch_sources"]))
        self.assertFalse(any("romDownload" in item for item in catalog["games"]))
        self.assertTrue(all(not item["romIncluded"] for item in catalog["games"]))
        self.assertTrue(all(item["console"] in ("GB", "GBC") for item in catalog["free_demos"]))
        for item in catalog["free_demos"]:
            self.assertTrue(item["officialDownloadPage"].startswith("https://"))
        for item in catalog["android_official"]:
            self.assertTrue(item["playStore"].startswith(
                "https://play.google.com/store/apps/details?id="))

    def test_bulk_import_and_recent_quickplay(self):
        src = ACTIVITY.read_text(encoding="utf-8")
        for expected in (
                "Intent.EXTRA_ALLOW_MULTIPLE",
                "Intent.ACTION_OPEN_DOCUMENT",
                "REQUEST_MULTI",
                "fileIo.execute(",
                "RetroFileInspector.inspect(",
                "takePersistableUriPermission(",
                "OkubiPokemonRepository.add(",
                "OkubiPokemonRepository.markPlayed(",
                "last_pokemon_uri",
                "OkubiGameBoyActivity.EXTRA_ROM",
                "RetroExternalLauncher.open("):
            self.assertIn(expected, src)
        self.assertIn('new String[]{"Míos", "Clásicos", "ROM hacks", "Gratis", "Android"}', src)

    def test_dedupe_private_storage_and_favorites(self):
        src = LIBRARY.read_text(encoding="utf-8")
        for expected in (
                "MAX_ENTRIES = 250",
                '"okubi_pokemon_repository"',
                "JSONArray",
                "uri.startsWith(\"content://\")",
                "public static void setFavorite",
                "public static void markPlayed",
                "public static void remove",
                "values.size() >= MAX_ENTRIES",
                "public static String systemFromName"):
            self.assertIn(expected, src)
        formats = (RETRO / "OkubiRomFormats.java").read_text(encoding="utf-8")
        for suffix in (".gb", ".gbc", ".gba", ".nds", ".srl",
                       ".z64", ".n64", ".v64", ".rvz", ".gcm",
                       ".3ds", ".cci", ".cia", ".nsp", ".xci", ".zip"):
            self.assertIn(suffix, formats)
        self.assertIn("OkubiRomFormats.systemFromName(title)", src)
        self.assertNotIn("HttpURLConnection", src)

    def test_legacy_imports_and_patch_caution(self):
        source = ACTIVITY.read_text(encoding="utf-8")
        self.assertIn('"entry_official_"', source)
        self.assertIn('"entry_" + (hack ? "hack_" : "official_")', source)
        self.assertIn("Aplica parches IPS o BPS", source)
        self.assertIn("Monpact (demo)", source)
        self.assertIn("PachiPals (demo)", source)
        self.assertIn("D-MONS", source)
        self.assertIn("CritterNet", source)
        self.assertIn("HACK_PATCH_PAGES", source)
        self.assertIn("getLaunchIntentForPackage", source)
        self.assertIn("OkubiRomFormats.detect(", source)
        self.assertIn("OkubiPatchEngine.apply(", source)
        self.assertIn("Intent.ACTION_CREATE_DOCUMENT", source)
        self.assertIn("REQUEST_PATCH_BASE", source)
        self.assertIn("REQUEST_PATCH_FILE", source)
        self.assertIn("REQUEST_PATCH_OUTPUT", source)
        self.assertIn("OkubiRomFormats.isZip(", source)
        self.assertNotIn("raw.githubusercontent.com/Pokemon", source)

class ArchiveSafetyContracts(unittest.TestCase):
    def test_bounded_streaming_no_zip_path_extraction(self):
        formats = (RETRO / "OkubiRomFormats.java").read_text(encoding="utf-8")
        player = (RETRO / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
        checker = (RETRO / "RetroFileInspector.java").read_text(encoding="utf-8")
        reader = (RETRO / "RetroRomReader.java").read_text(encoding="utf-8")
        for marker in ("MAX_ENTRIES = 64", "MAX_GAME_BOY = 8 * 1024 * 1024",
                       "ZipInputStream", "readBounded", "inspectZip",
                       "needsExternalExtractor"):
            self.assertIn(marker, formats)
        self.assertIn("RetroRomReader.readBounded(input,limit,minimum)", formats)
        self.assertIn("out.size() > maximum - n", reader)
        self.assertNotIn("ZipFile.extract", formats)
        self.assertIn("OkubiRomFormats.readGameBoy(", player)
        self.assertIn("OkubiRomFormats.inspectZip(", checker)

if __name__ == "__main__":
    unittest.main()
