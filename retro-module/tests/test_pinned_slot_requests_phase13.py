"""Phase 13: preserve the exact ROM/slot selected for save or load.

Complement pure-Java FIFO tests by checking the actual Android UI/worker
wiring, non-destructive slot filenames, and native compilation in CI.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
ACTIVITY=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
QUEUE=(RETRO/"RetroSlotRequests.java").read_text(encoding="utf-8")
TEST=(ROOT/"retro-module/tests/java/bo/nexoplay/app/retro/RetroSlotRequestsTest.java").read_text(encoding="utf-8")
INTEGRATOR=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class Phase13PinnedSlotCommands(unittest.TestCase):
    def test_queue_is_immutable_ordered_and_bounded(self):
        for token in (
            "public static final int MAX_PENDING = 8;",
            "public enum Action { SAVE, LOAD }",
            "public final Action action;",
            "public final String romHash;",
            "public final int slot;",
            "private final ArrayDeque<Request> pending",
            "public synchronized boolean offer(",
            "public synchronized Request poll()",
            "return pending.offerLast(new Request(action, romHash, slot, null));",
            "return pending.pollFirst();",
            "pending.size() >= MAX_PENDING",
            "RetroSaveSlots.validHash(romHash)",
            "RetroSaveSlots.validSlot(slot)",
        ):
            self.assertIn(token,QUEUE)
        for token in ("no silent replacement", "first slot immutable",
                      "load slot pinned", "ROM identity pinned",
                      "fixed capacity", "clear pending requests"):
            self.assertIn(token,TEST)

    def test_ui_captures_user_slot_and_rom_at_click_time(self):
        self.assertIn("private final RetroSlotRequests slotRequests",ACTIVITY)
        self.assertIn("String hash=activeRomHash;",ACTIVITY)
        self.assertIn("slotRequests.offer(action,hash,chosenSlot)",ACTIVITY)
        self.assertIn("slotRequests.offerGuardedSave(hash,slot,expected)",ACTIVITY)
        self.assertIn("requestSnapshot(RetroSlotRequests.Action.SAVE,chosen[0]);",ACTIVITY)
        self.assertIn("requestSnapshot(RetroSlotRequests.Action.LOAD,slot);",ACTIVITY)
        self.assertIn("requestSnapshot(key == RetroCore.Key.L",ACTIVITY)
        self.assertIn("Cola de puntos ocupada",ACTIVITY)
        self.assertNotIn("saveStateRequested",ACTIVITY)
        self.assertNotIn("loadStateRequested",ACTIVITY)

    def test_worker_obeys_pinned_request_not_selected_slot(self):
        begin=ACTIVITY.index("RetroSlotRequests.Request nextSlot=slotRequests.poll();")
        end=ACTIVITY.index("SlotCopyRequest copy=pendingSlotCopy;",begin)
        worker=ACTIVITY[begin:end]
        self.assertIn("nextSlot.romHash.equals(activeRomHash)",worker)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)",worker)
        self.assertIn("loadSnapshotOnWorker(nextSlot.slot)",worker)
        self.assertNotIn("selectedSnapshotSlot",worker)
        for method in ("saveSnapshotOnWorker(int slot)","loadSnapshotOnWorker(int slot)"):
            self.assertIn(method,ACTIVITY)
        self.assertIn("RetroSaveSlots.validStateSize(checkpoint.length)",ACTIVITY)
        self.assertIn("persistAtomic(destination,checkpoint)",ACTIVITY)
        self.assertIn("SameBoyNative.nativeLoadState(previous)",ACTIVITY)
        self.assertIn("slotRequests.clear();",ACTIVITY)
        self.assertIn("while ((remaining=slotRequests.poll())!=null)",ACTIVITY)
        self.assertIn("remaining.action==RetroSlotRequests.Action.SAVE",ACTIVITY)
        self.assertIn("saveBattery()==BatterySaveResult.FAILED",ACTIVITY)

    def test_retro_compatibility_and_ci(self):
        self.assertIn('"RetroSaveSlots.java", "RetroSlotRequests.java"',INTEGRATOR)
        self.assertIn("RetroSlotRequestsTest.java",WORKFLOW)
        self.assertIn("java -cp build/slot-request-tests",WORKFLOW)
        self.assertIn("RetroSaveSlotsTest",WORKFLOW)
        self.assertIn("libsameboy_libretro_android.so",WORKFLOW)
        self.assertIn("libmgba_libretro_android.so",WORKFLOW)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)

if __name__=="__main__":
    unittest.main()
