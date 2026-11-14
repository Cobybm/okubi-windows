"""Phase 10 - export destination and restored save integrity gates.

Contract tests inspect actual Android source branches; pure-Java disk tests and
a full APK/native build also run within the same CI workflow.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"android-app/app/src/main/java/bo/nexoplay/app"
MAIN=(APP/"MainActivity.java").read_text(encoding="utf-8")
BACKUP=(APP/"OkubiUnifiedBackup.java").read_text(encoding="utf-8")
CHECK=(APP/"OkubiArchiveVerifier.java").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")

class VerifiedMigrationPhase10(unittest.TestCase):
    def test_saf_export_is_reopened_and_validated_before_success(self):
        section=MAIN.split("private void transferUnified(Uri uri,boolean importing)",1)[1].split(
            "private String paletteName()",1)[0]
        self.assertIn('getContentResolver().openOutputStream(uri,"wt")',section)
        self.assertIn("getContentResolver().openInputStream(uri)",section)
        self.assertIn("OkubiUnifiedBackup.inspect(this,check)",section)
        self.assertIn("verified.newSaves+verified.existingSaves!=outcome.saves",section)
        self.assertIn("Respaldo creado y verificado:",section)
        self.assertIn("Respaldo no verificado: no lo uses para migrar.",section)
        self.assertLess(section.index("getContentResolver().openOutputStream(uri,"),
                        section.index("try(InputStream check=getContentResolver().openInputStream(uri))"))

    def test_actual_import_bytes_verified_after_atomic_finish(self):
        importing=BACKUP.split("public static Outcome importFrom(",1)[1].split(
            "private static String readSmall(",1)[0]
        self.assertIn("OkubiArchiveVerifier.matches(target,",importing)
        self.assertIn("verified.lengths.get(entry),verified.hashes.get(entry)",importing)
        self.assertLess(importing.index("atomic.finishWrite(output)"),
                        importing.index("committed.add(target);"))
        self.assertLess(importing.index("committed.add(target);"),
                        importing.index("OkubiArchiveVerifier.matches(target,"))
        self.assertLess(importing.index("OkubiArchiveVerifier.matches(target,"),
                        importing.index("saved++;"))
        self.assertIn("for(File created:committed)",importing)
        self.assertIn("new AtomicFile(created).delete()",importing)
        self.assertIn("if(target.exists()){skipped++;continue;}",importing)
        self.assertIn("if(OkubiMigrationRules.isSave(name) && size==0)",BACKUP)

    def test_integrity_uses_disk_bytes_and_bounded_sha256(self):
        for token in ("new FileInputStream(file)", "file.length()!=expectedBytes",
                      "read>expectedBytes", "sha.update(buffer,0,n)",
                      "expectedSha256.contentEquals(hex)", "return false;"):
            self.assertIn(token,CHECK)
        self.assertNotIn("delete(",CHECK)

    def test_workflow_build_and_identity_are_preserved(self):
        self.assertIn("OkubiArchiveVerifierTest",WORKFLOW)
        self.assertIn("RetroSaveSlotsTest",WORKFLOW)
        self.assertIn("OkubiMigrationRulesTest",WORKFLOW)
        self.assertIn("libsameboy_libretro_android.so",WORKFLOW)
        self.assertIn("libmgba_libretro_android.so",WORKFLOW)
        self.assertIn("--minimum-version-code 36",WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("1.0.0-unified-beta17",GRADLE)

if __name__=="__main__":
    unittest.main()
