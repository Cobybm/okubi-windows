"""OKUBI GBA / mGBA contracts. These are regression checks, not device testing."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RETRO = ROOT / "java/bo/nexoplay/app/retro"


class GameAdvanceContracts(unittest.TestCase):
    def test_pinned_mgba_build_and_license(self):
        script = (ROOT / "build_mgba.py").read_text(encoding="utf-8")
        self.assertIn("7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6", script)
        self.assertIn("https://github.com/libretro/mgba.git", script)
        self.assertIn("BUILD_LIBRETRO=ON", script)
        self.assertIn("M_CORE_GBA=ON", script)
        self.assertIn("M_CORE_GB=ON", script)
        self.assertIn("mGBA-LICENSE.txt", script)
        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
            self.assertIn(abi, script)

    def test_native_routes_video_shoulder_buttons_and_save(self):
        bridge = (ROOT / "native/sameboy_bridge.c").read_text(encoding="utf-8")
        header = (ROOT / "native/libretro.h").read_text(encoding="utf-8")
        self.assertIn('strcmp(model, "GBA") == 0', bridge)
        self.assertIn('"libmgba_libretro_android.so"', bridge)
        self.assertIn('"libsameboy_libretro_android.so"', bridge)
        self.assertIn("#define GBA_WIDTH 240", bridge)
        self.assertIn("#define GBA_HEIGHT 160", bridge)
        self.assertIn("expected_width * expected_height", bridge)
        self.assertIn("RETRO_DEVICE_ID_JOYPAD_L", bridge)
        self.assertIn("RETRO_DEVICE_ID_JOYPAD_R", bridge)
        self.assertIn("RETRO_MEMORY_SAVE_RAM", bridge)
        self.assertIn("RETRO_DEVICE_ID_JOYPAD_L 10", header)
        self.assertIn("RETRO_DEVICE_ID_JOYPAD_R 11", header)

    def test_player_preserves_gb_saves_and_uses_gba_layout(self):
        java = (RETRO / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
        self.assertIn('frameWidth = GBA_WIDTH;', java)
        self.assertIn('frameHeight = GBA_HEIGHT;', java)
        self.assertIn('"GBA".equals(system) ? "okubi/gba" : "okubi/gameboy"', java)
        self.assertIn("OkubiRomFormats.readGameAdvance(", java)
        self.assertIn("OkubiRomFormats.readGameBoy(", java)
        self.assertIn("new int[frameWidth * frameHeight]", java)
        self.assertIn('"GBA".equals(system) || "SNES".equals(system)) setKey(key, down);', java)
        self.assertIn('saveName + ".srm"', java)
        self.assertIn('saveName + ".state"', java)

    def test_pokemon_and_console_entrypoints(self):
        pokemon = (RETRO / "OkubiPokemonActivity.java").read_text(encoding="utf-8")
        retro = (RETRO / "OkubiRetroActivity.java").read_text(encoding="utf-8")
        native = (RETRO / "SameBoyNative.java").read_text(encoding="utf-8")
        formats = (RETRO / "OkubiRomFormats.java").read_text(encoding="utf-8")
        self.assertIn("SameBoyNative.isGbaAvailable()", pokemon)
        self.assertIn("SameBoyNative.isGbaAvailable()", retro)
        self.assertIn("System.loadLibrary(\"mgba_libretro_android\")", native)
        self.assertIn("readGameAdvance", formats)
        self.assertIn('MAX_GAME_BOY = 8 * 1024 * 1024', formats)


if __name__ == "__main__":
    unittest.main()
