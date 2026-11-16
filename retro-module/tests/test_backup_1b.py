"""Post-audit 1B integration contracts (static; not device-level proof)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / "java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java"
FORMAT = ROOT / "java/bo/nexoplay/app/retro/OkubiSaveArchive.java"
INTEGRATOR = ROOT / "integrate.py"
WORKFLOW = ROOT.parent / ".github/workflows/okubi-retro.yml"


class BackupPhaseContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.player = PLAYER.read_text(encoding="utf-8")
        cls.format = FORMAT.read_text(encoding="utf-8")

    def test_both_saf_flows_are_user_selected(self):
        source = self.player
        for expected in (
            "Intent.ACTION_CREATE_DOCUMENT",
            "Intent.ACTION_OPEN_DOCUMENT",
            "REQUEST_EXPORT_BACKUP",
            "REQUEST_IMPORT_BACKUP",
            "pendingBackupExport = selected;",
            "pendingBackupImport = selected;",
            "getContentResolver().openOutputStream(destination, \"wt\")",
            "getContentResolver().openInputStream(destination)",
            "OkubiSaveArchive.samePayload(exported, verified)",
        ):
            self.assertIn(expected, source)

    def test_worker_only_and_transactional_restore(self):
        source = self.player
        loop = source.index("while (!destroyed) {")
        self.assertGreater(source.index("exportBackup(saveDestination);"), loop)
        self.assertGreater(source.index("importBackup(restoreSource);"), loop)
        self.assertIn("writeAutomaticRecovery(before);", source)
        self.assertIn("restoreStoredFile(saveFile, originalRam)", source)
        self.assertIn("restoreStoredFile(rtcFile, originalRtc)", source)
        self.assertIn("restoreStoredFile(snapshotFile, originalState)", source)
        self.assertIn("restoreLiveSnapshot(before)", source)
        self.assertIn("samePayload(before, restored)", source)
        self.assertIn("if (!matchesSize(imported.ram, before.ram)", source)

    def test_never_packages_roms_or_trusts_archive_paths(self):
        source = self.format
        self.assertIn("romSha256.matches(\"[0-9a-f]{64}\")", source)
        self.assertIn("MessageDigest.isEqual", source)
        self.assertIn("in.read() != -1", source)
        self.assertIn("readPart(in, MAX_RAM", source)
        self.assertIn("readPart(in, MAX_RTC", source)
        self.assertIn("readPart(in, MAX_STATE", source)
        self.assertNotIn("ZipInputStream", source)
        self.assertNotIn("java.nio.file.Paths", source)
        self.assertNotIn("personal-roms", source)

    def test_android_ci_compiles_and_tests_backup(self):
        self.assertIn('"OkubiSaveArchive.java"', INTEGRATOR.read_text(encoding="utf-8"))
        pipeline = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("OkubiSaveArchiveTest.java", pipeline)
        self.assertIn("OkubiSaveArchiveTest", pipeline)
        self.assertIn("Compilar APK único OKUBI UNIFIED", pipeline)


if __name__ == "__main__":
    unittest.main()
