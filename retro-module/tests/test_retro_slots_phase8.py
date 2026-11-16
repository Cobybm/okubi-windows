"""OKUBI Phase 8: three native save-state slots per ROM, and ZIP/ADB continuity."""
import hashlib
import importlib.util
import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[2]
EMULATOR=(ROOT/"retro-module/java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java").read_text(encoding="utf-8")
SLOTS=(ROOT/"retro-module/java/bo/nexoplay/app/retro/RetroSaveSlots.java").read_text(encoding="utf-8")
RULES=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/OkubiMigrationRules.java").read_text(encoding="utf-8")
BACKUP=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/OkubiUnifiedBackup.java").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
INTEGRATE=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
spec=importlib.util.spec_from_file_location("okubi_rescue_adb",ROOT/"tools/okubi_rescue_adb.py")
rescue=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=rescue
spec.loader.exec_module(rescue)


class RetroSlotsPhase8(unittest.TestCase):
    def test_legacy_slot_kept_and_new_slots_bounded(self):
        for token in (
            'slot==FIRST ? ".state" : ".slot"+slot+".state"',
            "public static final int LAST = 3;",
            "return bytes > 0 && bytes <= MAX_STATE_BYTES;",
            'hash.matches("[0-9a-f]{64}")',
        ):
            self.assertIn(token,SLOTS)
        self.assertIn('snapshotFile = new File(directory, saveName + ".state");',EMULATOR)
        self.assertIn("RetroSaveSlots.filename(activeRomHash,slot)",EMULATOR)

    def test_save_load_on_native_worker_with_confirmation_and_rollback(self):
        for marker in (
            "private void showSaveSlots()",
            "private void confirmLoadSlot(int slot)",
            "setSingleChoiceItems(labels,chosen[0]-1",
            '"Cargar punto"',
            '"Guardar"',
            "requestSnapshot(RetroSlotRequests.Action.SAVE,chosen[0]);",
            "requestSnapshot(RetroSlotRequests.Action.LOAD,slot);",
            "private volatile int selectedSnapshotSlot",
            "RetroSaveSlots.validStateSize(checkpoint.length)",
            "persistAtomic(destination,checkpoint)",
            "new AtomicFile(source).readFully()",
            "SameBoyNative.nativeSaveState();",
            "SameBoyNative.nativeLoadState(previous)",
            "RetroSaveSlots.validStateSize(source.length())",
        ):
            self.assertIn(marker,EMULATOR)
        self.assertIn('case 3: showSaveSlots(); break;',EMULATOR)
        self.assertIn("getSharedPreferences(\"okubi_retro_slots_v1\"",EMULATOR)
        self.assertIn("catch(ClassCastException ignored)",EMULATOR)

    def test_zip_export_and_rescue_preserve_extra_state_slots(self):
        sha="cd"*32
        saves={
            f"saves/gameboy/{sha}.state":b"LEGACY-STATE",
            f"saves/gameboy/{sha}.slot2.state":b"SLOT2-STATE",
            f"saves/gba/{sha}.slot3.state":b"SLOT3-STATE",
        }
        prefs={"okubi_retro_slots_v1":{
            f"slot_{sha}":{"type":"integer","value":3}
        }}
        self.assertTrue(rescue.allowed_pref("okubi_retro_slots_v1",f"slot_{sha}"))
        self.assertFalse(rescue.allowed_pref("okubi_retro_slots_v1",
                                             "slot_../../bad"))
        for path in saves:
            filename=Path(path).name
            self.assertTrue(rescue.SAVE_RE.fullmatch(filename),filename)
        with TemporaryDirectory() as d:
            output=Path(d)/"OKUBI-3-slots.zip"
            total,preferences=rescue.build_archive("bo.nexoplay.app.unified",prefs,
                                                     saves,output)
            self.assertEqual((3,1),(total,preferences))
            with ZipFile(output) as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(set(saves)|{"manifest.json","preferences.json"},
                                 set(archive.namelist()))
                manifest=json.loads(archive.read("manifest.json"))
                for record in manifest["files"]:
                    data=archive.read(record["path"])
                    self.assertEqual(hashlib.sha256(data).hexdigest(),
                                     record["sha256"])
                self.assertEqual(prefs,json.loads(archive.read("preferences.json")))
        self.assertIn('"okubi_retro_slots_v1"',BACKUP)
        self.assertIn('slot[23]',RULES)

    def test_no_game_roms_stored_and_library_unchanged(self):
        self.assertNotIn('personal-roms/',SLOTS)
        self.assertIn("RetroSaveSlots.java",INTEGRATE)
        self.assertIn("RetroSaveSlotsTest",WORKFLOW)
        self.assertIn("OkubiMigrationRulesTest",WORKFLOW)
        self.assertIn("RetroDpadHitMapTest",WORKFLOW)
        self.assertIn("OkubiSaveArchiveTest",WORKFLOW)

if __name__=="__main__":
    unittest.main()
