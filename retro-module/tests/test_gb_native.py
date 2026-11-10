"""Static regression checks for first-party SameBoy bridge and Android player.

These are intentionally not a substitute for NDK compilation or gameplay tests.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "native/sameboy_bridge.c"
HEADER = ROOT / "native/libretro.h"
PLAYER = ROOT / "java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java"
JNI = ROOT / "java/bo/nexoplay/app/retro/SameBoyNative.java"

class GameBoyBridgeContracts(unittest.TestCase):
    def test_jni_methods_match_native_exports(self):
        java = JNI.read_text(encoding="utf-8")
        c = NATIVE.read_text(encoding="utf-8")
        methods = re.findall(r"public static native \w+(?:\[\])? (native\w+)\(", java)
        self.assertEqual(len(methods), 11)
        for method in methods:
            self.assertIn("Java_bo_nexoplay_app_retro_SameBoyNative_" + method, c)

    def test_battery_clock_input_and_audio(self):
        native = NATIVE.read_text(encoding="utf-8")
        header = HEADER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        self.assertIn("RETRO_MEMORY_SAVE_RAM", native)
        self.assertIn("RETRO_MEMORY_RTC", native)
        self.assertIn("RETRO_MEMORY_RTC 1", header)
        self.assertIn("RETRO_ENVIRONMENT_GET_VARIABLE 15", header)
        self.assertIn('strcmp(variable->key, "sameboy_model")', native)
        self.assertIn("audio_decimation", native)
        self.assertIn("audio_left_accum", native)
        self.assertIn("av.timing.sample_rate / factor", native)
        self.assertIn('saveName + ".srm"', player)
        self.assertIn('saveName + ".rtc"', player)
        self.assertIn("new AtomicFile", player)
        self.assertIn("new SurfaceView(this)", player)
        self.assertIn("AudioTrack.WRITE_NON_BLOCKING", player)
        self.assertIn("SameBoyNative.nativeStep(inputMask", player)
        self.assertIn("Math.min(width", player)

    def test_optional_hardware_controller_does_not_override_touch(self):
        player = PLAYER.read_text(encoding="utf-8")
        self.assertIn("KeyEvent.KEYCODE_BUTTON_A", player)
        self.assertIn("KeyEvent.KEYCODE_BUTTON_B", player)
        self.assertIn("KeyEvent.KEYCODE_BUTTON_START", player)
        self.assertIn("InputDevice.SOURCE_JOYSTICK", player)
        self.assertIn("keys.get() | padKeys.get() | analogKeys.get()", player)
        self.assertIn("padKeys.set(0)", player)
        self.assertIn("analogKeys.set(0)", player)

    def test_checkpoint_save_and_restore_are_worker_only(self):
        native = NATIVE.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        for symbol in ("retro_serialize_size", "retro_serialize", "retro_unserialize"):
            self.assertIn(symbol, native)
        self.assertIn("16u * 1024u * 1024u", native)
        self.assertIn('saveName + ".state"', player)
        self.assertIn("new AtomicFile(source)", player)
        self.assertIn("RetroSaveSlots.filename(activeRomHash,slot)",player)
        self.assertIn("persistAtomic(destination,checkpoint)",player)
        self.assertIn("SameBoyNative.nativeSaveState()", player)
        self.assertIn("SameBoyNative.nativeLoadState(snapshot)", player)
        self.assertIn("RetroSlotRequests.Request nextSlot=slotRequests.poll();",player)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)",player)
        self.assertIn("loadSnapshotOnWorker(nextSlot.slot)",player)
        self.assertIn("Guardar punto", player)
        self.assertIn("Cargar punto", player)

    def test_reproducible_core_commit_and_abis(self):
        script = (ROOT / "build_sameboy.py").read_text(encoding="utf-8")
        self.assertIn("aa158a889a48b538a0302873704a34577c8eb67d", script)
        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
            self.assertIn(abi, script)
        self.assertIn("NDK_OUT=", script)
        self.assertIn("SameBoy-LICENSE.txt", script)

    def test_internal_launch_from_both_catalogs(self):
        retro = (ROOT / "java/bo/nexoplay/app/retro/OkubiRetroActivity.java").read_text(encoding="utf-8")
        pokemon = (ROOT / "java/bo/nexoplay/app/retro/OkubiPokemonActivity.java").read_text(encoding="utf-8")
        catalog = (ROOT / "java/bo/nexoplay/app/retro/OkubiCatalogActivity.java").read_text(encoding="utf-8")
        self.assertIn("OkubiGameBoyActivity.class", retro)
        self.assertIn("OkubiGameBoyActivity.class", pokemon)
        self.assertIn("EXTRA_HOMEBREW_PATH", catalog)
        self.assertIn("HttpURLConnection", catalog)
        self.assertIn("AtomicFile", catalog)

if __name__ == "__main__":
    unittest.main()
