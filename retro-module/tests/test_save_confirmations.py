"""Safety contracts for OKUBI cartridge save acknowledgements.

These source-level checks prevent regressions in user-facing success messages.
Android compilation and physical device tests are separate CI/QA gates.
"""
from pathlib import Path
import unittest

PLAYER = (
    Path(__file__).resolve().parents[1]
    / "java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java"
)


class SaveConfirmationContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = PLAYER.read_text(encoding="utf-8")

    def test_atomic_write_must_be_read_back_before_confirmation(self):
        source = self.source
        self.assertIn("atomic.finishWrite(output);", source)
        self.assertIn("return java.util.Arrays.equals(data, atomic.readFully());", source)
        self.assertIn("if (output != null) atomic.failWrite(output);", source)
        self.assertIn("if (target == null || data == null || data.length == 0) return false;", source)

    def test_missing_or_failed_battery_memory_is_not_success(self):
        source = self.source
        self.assertIn("private enum BatterySaveResult { SAVED, NO_SAVE_MEMORY, FAILED }", source)
        self.assertIn("if (!hasRam && !hasRtc) return BatterySaveResult.NO_SAVE_MEMORY;", source)
        self.assertIn("if (hasRam && !persistAtomic(saveFile, ram)) return BatterySaveResult.FAILED;", source)
        self.assertIn("if (hasRtc && !persistAtomic(rtcFile, rtc)) return BatterySaveResult.FAILED;", source)
        self.assertIn("if (!coreIsOpen || saveFile == null) return BatterySaveResult.FAILED;", source)
        self.assertIn("} catch (RuntimeException | LinkageError error) {", source)

    def test_manual_save_reports_worker_result_not_assumed_success(self):
        source = self.source
        self.assertIn("reportBatterySave(saveBattery(), false);", source)
        self.assertIn('setStatus(imported ? "Partida importada y guardada correctamente"', source)
        self.assertIn(': "Cartucho guardado y verificado");', source)
        self.assertIn('"No se pudo confirmar el guardado del cartucho"', source)
        self.assertNotIn('setStatus("Cartucho guardado");', source)

    def test_raw_import_uses_guarded_backup_restore(self):
        source = self.source
        self.assertIn('setTitle("Importar .sav / .srm")', source)
        self.assertIn("OkubiSaveArchive.Snapshot rawImport", source)
        self.assertIn("installBackup(rawImport, true);", source)
        self.assertIn("if (!matchesSize(imported.ram, before.ram)", source)
        self.assertIn("if (preservePrevious) writeAutomaticRecovery(before);", source)
        self.assertIn('"No se pudo escribir: se conservó la partida anterior"', source)

    def test_restore_rejects_incompatible_sram(self):
        self.assertIn(
            "if (!SameBoyNative.nativeLoadRam(new AtomicFile(saveFile).readFully())) {",
            self.source,
        )
        self.assertIn('"El archivo de batería no corresponde a este cartucho"', self.source)


if __name__ == "__main__":
    unittest.main()
