"""OKUBI Phase 9: safely duplicate a state checkpoint into an empty slot.

These are source-integration contracts, complemented by the pure-Java policy
tests and the real Android compile / native build gate in GitHub Actions.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
ACTIVITY=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
POLICY=(RETRO/"RetroSaveSlots.java").read_text(encoding="utf-8")
TESTS=(ROOT/"retro-module/tests/java/bo/nexoplay/app/retro/RetroSaveSlotsTest.java").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class SafeSlotCopyPhase9(unittest.TestCase):
    def test_reject_same_or_occupied_target(self):
        for condition in (
            "validSlot(source) && validSlot(target)",
            "source != target",
            "validStateSize(bytes)",
            "!destinationExists",
        ):
            self.assertIn(condition,POLICY)
        self.assertIn("RetroSaveSlots.canCopy(1,2,100,true)",TESTS)
        self.assertIn("RetroSaveSlots.canCopy(2,2,100,false)",TESTS)

    def test_user_intent_is_confirmed_and_bound_to_current_rom(self):
        for feature in (
            '"Duplicar punto · solo a ranura vacía"',
            "private void chooseCopySource()",
            "private void chooseCopyDestination(int source)",
            "private void confirmCopySlot(int source,int target)",
            '"Copiar sin sobrescribir"',
            "private volatile SlotCopyRequest pendingSlotCopy",
            "new SlotCopyRequest(activeRomHash,source,target)",
            "copy.romHash.equals(activeRomHash)",
        ):
            self.assertIn(feature,ACTIVITY)
        self.assertIn("case 14: chooseCopySource(); break;",ACTIVITY)

    def test_emulation_worker_owns_io_without_loading_or_deleting_saves(self):
        start=ACTIVITY.index("SlotCopyRequest copy=pendingSlotCopy;")
        end=ACTIVITY.index("if (audioResetRequested)",start)
        block=ACTIVITY[start:end]
        for contract in (
            "pendingSlotCopy=null;",
            "snapshotForSlot(copy.from)",
            "snapshotForSlot(copy.to)",
            "source.isFile()",
            "destination.exists()",
            "new AtomicFile(source).readFully()",
            "persistAtomic(destination,bytes)",
            "new AtomicFile(destination).delete()",
        ):
            self.assertIn(contract,block)
        self.assertNotIn("SameBoyNative.nativeLoadState",block)
        self.assertNotIn("saveFile.delete(",block)
        self.assertNotIn("rtcFile.delete(",block)
        self.assertNotIn("selectedSnapshotSlot=",block)
        self.assertLess(ACTIVITY.index("RetroSlotRequests.Request nextSlot=slotRequests.poll();"),start)
        self.assertLess(end,ACTIVITY.index("if (!playing)",end))

    def test_unified_identity_and_critical_gates_remain(self):
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("1.0.0-unified-beta17",GRADLE)
        for check in (
            "RetroSaveSlotsTest",
            "OkubiSaveArchiveTest",
            "RetroDpadHitMapTest",
            "OkubiMigrationRulesTest",
            "libsameboy_libretro_android.so",
            "libmgba_libretro_android.so",
        ):
            self.assertIn(check,WORKFLOW)


if __name__ == "__main__":
    unittest.main()
