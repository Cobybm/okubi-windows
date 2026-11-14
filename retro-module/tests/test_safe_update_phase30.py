"""Phase 30 safety contracts for device migration and APK signer identity.

Checks the actual Android code and release workflow. CI also runs JVM allowlist
tests and builds the Android APK; this is not a replacement for physical QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android-app/app/src/main/java/bo/nexoplay/app"
BACKUP = (APP / "OkubiUnifiedBackup.java").read_text(encoding="utf-8")
RULES = (APP / "OkubiMigrationRules.java").read_text(encoding="utf-8")
MAIN = (APP / "MainActivity.java").read_text(encoding="utf-8")
SIGN = (APP / "OkubiSigningFingerprint.java").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle").read_text(encoding="utf-8")
PREFLIGHT = (ROOT / "tools/okubi_a05s_preflight.py").read_text(encoding="utf-8")


class SafeUpdatePhase30(unittest.TestCase):
    def test_backup_includes_every_portable_console(self):
        self.assertIn('new String[]{"gameboy","gba","snes"}', BACKUP)
        self.assertIn("gameboy|gba|snes", RULES)
        self.assertIn("if(target.exists()){skipped++;continue;}", BACKUP)
        self.assertIn("OkubiArchiveVerifier.matches(target,", BACKUP)
        for namespace in ("okubi_visual_v1", "okubi_screen_fit_v1",
                          "okubi_overlay_opacity_v1", "okubi_retro_controls_v1"):
            self.assertIn('"' + namespace + '"', BACKUP if namespace in BACKUP else RULES)
            self.assertIn('"' + namespace + '"', RULES if namespace in RULES else BACKUP)
        self.assertIn("snes_", RULES)
        self.assertIn("cinema_land_", RULES)

    def test_fingerprint_is_public_and_read_only(self):
        self.assertIn("getPackageInfo(", SIGN)
        self.assertIn("getApkContentsSigners()", SIGN)
        self.assertIn('MessageDigest.getInstance("SHA-256")', SIGN)
        self.assertIn("OkubiSigningFingerprint.currentSha256(this)", MAIN)
        self.assertIn("getSystemService(Context.CLIPBOARD_SERVICE)", MAIN)
        self.assertNotIn("KeyStore.getInstance(", SIGN)
        self.assertNotIn("getFilesDir()", SIGN)
        self.assertNotIn("delete(", SIGN)
        self.assertNotIn("startActivity(", SIGN)

    def test_secret_handling_and_no_destructive_install(self):
        self.assertIn("trap 'rm -f \"$KEY_PATH\"' EXIT", CI)
        self.assertIn('if [ -z "$OKUBI_RELEASE_CERT_SHA256" ]; then', CI)
        self.assertIn("github.event_name == 'workflow_dispatch'", CI)
        self.assertIn("github.ref_name == 'main'", CI)
        self.assertIn("OKUBI_KEYSTORE_BASE64", CI)
        self.assertIn("OKUBI_RELEASE_CERT_SHA256", CI)
        self.assertIn("EXPECTED_VERSION_CODE = 36", PREFLIGHT)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", GRADLE)
        self.assertIn("versionCode 36", GRADLE)
        self.assertNotIn("storePassword '", GRADLE)
        self.assertNotIn("keyPassword '", GRADLE)


if __name__ == "__main__":
    unittest.main()
