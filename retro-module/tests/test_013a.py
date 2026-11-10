"""OKUBI 0.13A regression checks: audio, touch hitboxes, configurable controls,
separate game/selector UI, and persistent legacy cartridge saves.
Static checks must be followed by physical-device testing on Samsung A05s.
"""
from pathlib import Path
import unittest
import re

ROOT = Path(__file__).resolve().parents[1]
RETRO = ROOT / "java/bo/nexoplay/app/retro"
APP = ROOT.parent / "android-app/app"
PLAYER = RETRO / "OkubiGameBoyActivity.java"
PAD = RETRO / "RetroTouchPad.java"
PROFILE = RETRO / "RetroControlProfile.java"
SELECTOR = RETRO / "OkubiRetroActivity.java"

class Phase013ATests(unittest.TestCase):
    def test_safe_audio_and_runtime_settings(self):
        source = PLAYER.read_text(encoding="utf-8")
        for contract in (
            "volumePercent = 35",
            'getSharedPreferences("okubi_audio_v1"',
            "audioMuted",
            "safeAudio = true",
            "AudioTrack.WRITE_BLOCKING",
            "audio.setVolume(wantedGain)",
            "audio.pause(); audio.flush();",
            "audioResetRequested = true",
            "private void showAudioSettings()",
            "Reiniciar sonido",
            "Modo audio seguro",
            "Silenciar audio",
        ):
            self.assertIn(contract, source)

    def test_control_skins_and_drag_editor(self):
        profile = PROFILE.read_text(encoding="utf-8")
        pad = PAD.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        for skin in ("OKUBI_NEON", "CLASSIC_GBA", "MINIMAL_GHOST", "ARCADE_BOLD",
                     "RETRO_POCKET", "PRO_TOUCH"):
            self.assertIn(skin, profile)
        self.assertIn("result.xs[i] = optionalCoordinate", profile)
        self.assertIn("result.ys[i] = optionalCoordinate", profile)
        self.assertIn("land_", profile)
        self.assertIn("port_", profile)
        self.assertIn("profile.size", pad)
        self.assertIn("profile.opacity", pad)
        self.assertIn("radius * 1.35f", pad)
        self.assertIn("profile.move(dragged", pad)
        self.assertIn("profile.save(getContext())", pad)
        self.assertIn("profile.swapAB", pad)
        self.assertIn("private void showControlSettings()", player)
        self.assertIn("Mover botones en pantalla", player)
        self.assertIn("Modo zurdo", player)
        self.assertIn("Invertir botones A/B", player)
        self.assertIn("setEditing(editingControls)", player)

    def test_no_fake_controls_and_real_engine_status(self):
        source = SELECTOR.read_text(encoding="utf-8")
        self.assertNotIn("display.addView(touchPad", source)
        self.assertNotIn("PANTALLA RETRO", source)
        self.assertIn("Motor interno", source)
        self.assertIn('"SNES".equals(spec.id)', source)
        self.assertIn("GBA", source)
        self.assertIn("SameBoyNative.isGbaAvailable()", source)
        self.assertIn('SameBoyNative.isAvailable()', source)

    def test_saf_battery_import_on_worker_thread(self):
        player = PLAYER.read_text(encoding="utf-8")
        for token in (
            "REQUEST_IMPORT_SAV", "Intent.ACTION_OPEN_DOCUMENT",
            'file.endsWith(".sav")', 'file.endsWith(".srm")',
            "pendingBatteryImport", "readBattery(",
            "OkubiSaveArchive.Snapshot rawImport", "installBackup(rawImport, true);",
            "saveBattery()"):
            self.assertIn(token, player)

    def test_legacy_game_saves_and_gamepad_preserved(self):
        source = PLAYER.read_text(encoding="utf-8")
        for name in ('saveName + ".srm"', 'saveName + ".rtc"',
                     'saveName + ".state"', "AtomicFile",
                     "KEYCODE_BUTTON_A", "KEYCODE_BUTTON_B",
                     "InputDevice.SOURCE_JOYSTICK", "keys.get() | padKeys.get() | analogKeys.get()"):
            self.assertIn(name, source)
        version = (APP / "build.gradle").read_text(encoding="utf-8")
        code = re.search(r"versionCode\s+(\d+)", version)
        self.assertIsNotNone(code)
        self.assertGreaterEqual(int(code.group(1)), 12)
        self.assertRegex(version, r"versionName '[0-9]+\.[0-9]+\.[0-9]+-(?:alpha|unified-beta[0-9]+)'")

if __name__ == "__main__":
    unittest.main()
