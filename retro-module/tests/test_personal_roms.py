"""Personal APK build tests. No copyrighted ROM data is committed to GitHub.
The locally signed APK may carry two files added AFTER the public CI build.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RETRO = ROOT / "java/bo/nexoplay/app/retro"

class PersonalRomContracts(unittest.TestCase):
    def test_only_allowlisted_cartridges_can_be_opened(self):
        source = (RETRO / "OkubiPersonalLibrary.java").read_text(encoding="utf-8")
        self.assertIn('personal-roms/pokemon-rojo-fuego.gba', source)
        self.assertIn('personal-roms/pokemon-esmeralda.gba', source)
        self.assertIn("public static boolean isAllowed(", source)
        self.assertIn("if (!exists(context, name))", source)
        self.assertIn("context.getAssets().open(", source)
        self.assertNotIn("Base64.getDecoder", source)
        self.assertNotIn("HttpURLConnection", source)

    def test_bundle_option_appears_only_when_assets_exist(self):
        app = (RETRO / "OkubiPokemonActivity.java").read_text(encoding="utf-8")
        self.assertIn("OkubiPersonalLibrary.available(this)", app)
        self.assertIn("if (!personalGames.isEmpty())", app)
        self.assertIn('section(root, "Mis cartuchos incluidos"', app)
        self.assertIn("playIncluded(game)", app)
        self.assertIn('"last_pokemon_asset"', app)
        self.assertIn('remove("last_pokemon_asset")', app)

    def test_emulator_loads_private_asset_only_after_validation(self):
        src = (RETRO / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
        self.assertIn("OkubiPersonalLibrary.exists(this, requestedAsset)", src)
        self.assertIn("OkubiPersonalLibrary.open(this, bundledAsset)", src)
        self.assertIn("OkubiRomFormats.readEmbeddedGba(embedded)", src)
        self.assertIn("SameBoyNative.isGbaAvailable()", src)
        self.assertIn('saveName = sha256(rom)', src)
        self.assertIn('new File(getFilesDir()', src)

if __name__ == "__main__":
    unittest.main()
