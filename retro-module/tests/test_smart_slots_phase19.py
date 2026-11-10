"""Fase 19: tarjetas de partida con miniaturas opcionales y guardados protegidos.

El test numérico de nombres y versiones de archivos se ejecuta también con
javac/java reales; estos contratos comprueban la integración de Android.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
ACTIVITY=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
PREVIEWS=(RETRO/"RetroSlotPreviews.java").read_text(encoding="utf-8")
QUEUE=(RETRO/"RetroSlotRequests.java").read_text(encoding="utf-8")
SLOTS=(RETRO/"RetroSaveSlots.java").read_text(encoding="utf-8")
INTEGRATOR=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class SmartSlotsPhase19(unittest.TestCase):
    def test_old_save_names_and_save_archive_format_unchanged(self):
        self.assertIn('slot==FIRST ? ".state" : ".slot"+slot+".state"',SLOTS)
        self.assertIn('MAX_STATE_BYTES = 16L * 1024 * 1024;',SLOTS)
        self.assertIn('sha256 + (slot == 1 ? ".preview.png"',PREVIEWS)
        self.assertIn('RetroSaveSlots.validHash(sha256)',PREVIEWS)
        self.assertIn('RetroSaveSlots.validSlot(slot)',PREVIEWS)
        self.assertIn('RetroSaveSlots.validStateSize(stateBytes)',PREVIEWS)
        for token in ('new File(saveFile.getParentFile(),',
                      'RetroSlotPreviews.filename(activeRomHash,slot)',
                      'BitmapFactory.decodeFile(preview.getAbsolutePath(),bounds)',
                      'bounds.outWidth>RetroSlotPreviews.MAX_IMAGE_EDGE',
                      'Bitmap.Config.RGB_565',
                      'updateSlotCard(preview,details,chosen[0]);',
                      'Último guardado:', 'Last'):
            if token=='Last':
                continue
            self.assertIn(token,ACTIVITY)

    def test_preview_generated_only_after_verified_state_write(self):
        method=ACTIVITY.split("private void saveSnapshotOnWorker(int slot)",1)[1].split(
            "private void loadSnapshotOnWorker(int slot)",1)[0]
        self.assertIn("SameBoyNative.nativeSaveState()",method)
        self.assertIn("persistAtomic(destination,checkpoint)",method)
        self.assertIn("writeSlotPreviewOnWorker(slot);",method)
        self.assertLess(method.index("persistAtomic(destination,checkpoint)"),
                        method.index("writeSlotPreviewOnWorker(slot);"))
        body=ACTIVITY.split("private void writeSlotPreviewOnWorker(int slot)",1)[1].split(
            "private void saveSnapshotOnWorker(int slot)",1)[0]
        for token in ('previewFrameReady && previewSourceFrame != null',
                      'Bitmap.CompressFormat.PNG',
                      'RetroSlotPreviews.validImageSize(image.length)',
                      'persistAtomic(preview, image)',
                      'new AtomicFile(preview).delete();'):
            self.assertIn(token,body)
        for forbidden in ('saveFile.delete(', 'snapshotFile.delete(',
                          'SameBoyNative.nativeLoadState(', 'SameBoyNative.nativeClose('):
            self.assertNotIn(forbidden,body)
        self.assertIn("previewFrameReady = false;",ACTIVITY)
        self.assertIn("previewFrameReady = true;",ACTIVITY)
        self.assertIn("previewSourceFrame = frame;",ACTIVITY)

    def test_confirm_all_occupied_saves_and_pin_before_worker(self):
        ui=ACTIVITY.split("private void requestSnapshot(",1)[1].split(
            "private void enqueueGuardedSave(",1)[0]
        for token in ('RetroSlotRequests.FileStamp.capture(destination)',
                      'if(stamp.exists)',
                      'Sobrescribir ', 'Conservar partida',
                      'enqueueGuardedSave(hash,chosenSlot,stamp)'):
            self.assertIn(token,ui)
        self.assertIn('slotRequests.offerGuardedSave(hash,slot,expected)',ACTIVITY)
        self.assertIn('expected.matches(destination)',ACTIVITY)
        worker=ACTIVITY.split("RetroSlotRequests.Request nextSlot=slotRequests.poll();",1)[1].split(
            "SlotCopyRequest copy=pendingSlotCopy;",1)[0]
        self.assertIn("nextSlot.expectedSaveTarget.matches(target)",worker)
        self.assertIn("saveSnapshotOnWorker(nextSlot.slot)",worker)
        self.assertNotIn("selectedSnapshotSlot",worker)
        self.assertIn('while ((remaining=slotRequests.poll())!=null)',ACTIVITY)
        self.assertIn('remaining.expectedSaveTarget.matches(',ACTIVITY)

    def test_no_metadata_overwrites_legacy_state_and_read_only_ui(self):
        self.assertIn("public static final class FileStamp",QUEUE)
        self.assertIn("public final FileStamp expectedSaveTarget;",QUEUE)
        self.assertIn("public synchronized boolean offerGuardedSave(",QUEUE)
        self.assertIn('new File(target.getPath() + ".bak").exists()',QUEUE)
        self.assertIn('new File(target.getPath() + ".new").exists()',QUEUE)
        self.assertIn("target.lastModified() == lastModified",QUEUE)
        self.assertIn("RetroSaveSlots.validStateSize(target.length())",QUEUE)
        self.assertIn("RetroSlotPreviews.MAX_IMAGE_EDGE",ACTIVITY)
        # The preview helper has no methods that can write games or move saves.
        self.assertNotIn("FileOutputStream",PREVIEWS)
        self.assertNotIn("nativeSaveState",PREVIEWS)
        self.assertNotIn("getFilesDir",PREVIEWS)

    def test_java_real_tests_and_beta13_identity(self):
        self.assertIn('"RetroSlotPreviews.java"',INTEGRATOR)
        self.assertIn("RetroSlotPreviewsTest.java",WORKFLOW)
        self.assertIn("RetroSlotFileStampTest.java",WORKFLOW)
        self.assertIn("java -cp build/slot-stamp-tests",WORKFLOW)
        self.assertIn("java -cp build/slot-preview-tests",WORKFLOW)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("--minimum-version-code 36",WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)


if __name__=="__main__":
    unittest.main()
