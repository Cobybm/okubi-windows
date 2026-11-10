"""Phase 70 release gate regressions: synthetic project only, never personal saves."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from okubi_phase70_release_gate import audit, inspect_paths

SAMPLE = {
    ".gitignore": "*.apk\n*.gba\n*.sav\n*.okubisave\n*.jks\n.env\n",
    "android-app/app/build.gradle": (
        "applicationId 'bo.nexoplay.app.unified'\n"
        "applicationIdSuffix '.qa57'\nminSdk 26\nversionCode 36\n"
        "System.getenv('OKUBI_KEYSTORE_PATH')\n"
        "System.getenv('OKUBI_KEYSTORE_PASSWORD')\n"
        "signingConfig signingConfigs.unifiedOwner\n"
    ),
    "android-app/app/src/main/AndroidManifest.xml": '<application android:allowBackup="false" />',
    ".github/workflows/okubi-android-migrated-qa.yml": (
        "gradle :app:assembleDebug -PokubiQaSideBySide=true\n"
        "bo.nexoplay.app.unified.qa57\napksigner\nSHA256SUMS\n"
    ),
    ".github/workflows/okubi-desktop.yml": (
        "--verify-windows-release\nSHA256SUMS\n"
        "Forbidden personal artifact found in Windows ZIP\n"
    ),
    "desktop/QA57-LAUNCHER.cmd": 'set "APPDATA=OKUBI-QA57"\n',
    "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java": (
        "public class OkubiSaveArchive {}\n"
    ),
}


class Phase70ReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for name, data in SAMPLE.items():
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(data, encoding="utf-8")

    def check(self, names=None):
        return audit(self.root, list(SAMPLE) if names is None else names)

    def test_synthetic_clean_tree_passes_but_release_remains_blocked(self):
        r = self.check()
        self.assertEqual(r["automated_release_gate"], "pass")
        self.assertFalse(r["public_release_authorized"])
        self.assertTrue(all(v == "pending" for v in r["manual_gates"].values()))

    def test_forbidden_game_save_is_rejected(self):
        p = self.root / "roms" / "personal.gba"
        p.parent.mkdir()
        p.write_bytes(b"private")
        r = self.check(list(SAMPLE) + ["roms/personal.gba"])
        self.assertIn("private_or_binary_file_tracked", r["failed_check_codes"])

    def test_keystore_file_is_rejected(self):
        (self.root / "release.jks").write_bytes(b"not-a-real-keystore")
        r = self.check(list(SAMPLE) + ["release.jks"])
        self.assertIn("private_or_binary_file_tracked", r["failed_check_codes"])

    def test_embedded_token_is_rejected_without_echoing_secret(self):
        path = self.root / "tools" / "example.py"
        path.parent.mkdir(exist_ok=True)
        path.write_text("token='" + "ghp_" + "A" * 35 + "'\n", encoding="utf-8")
        r = self.check(list(SAMPLE) + ["tools/example.py"])
        self.assertIn("possible_embedded_secret", r["failed_check_codes"])
        self.assertNotIn("ghp_", str(r))

    def test_canonical_android_identity_cannot_change(self):
        path = self.root / "android-app/app/build.gradle"
        path.write_text(SAMPLE["android-app/app/build.gradle"].replace(
            "bo.nexoplay.app.unified", "bo.nexoplay.app.other"
        ), encoding="utf-8")
        self.assertIn("android_canonical_package_changed", self.check()["failed_check_codes"])

    def test_debug_variant_must_remain_side_by_side(self):
        path = self.root / ".github/workflows/okubi-android-migrated-qa.yml"
        path.write_text("gradle :app:assembleRelease\n", encoding="utf-8")
        self.assertIn("android_qa_verification_missing", self.check()["failed_check_codes"])

    def test_windows_verifier_is_mandatory(self):
        path = self.root / ".github/workflows/okubi-desktop.yml"
        path.write_text("jpackage\n", encoding="utf-8")
        self.assertIn("windows_package_verification_missing", self.check()["failed_check_codes"])

    def test_missing_source_file_is_rejected(self):
        (self.root / "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java").unlink()
        self.assertIn("mandatory_release_source_missing", self.check()["failed_check_codes"])

    def test_symlink_cannot_reference_private_data(self):
        target = self.root / "private.txt"
        target.write_text("secret fixture", encoding="utf-8")
        link = self.root / "tools" / "outside.txt"
        link.parent.mkdir(exist_ok=True)
        link.symlink_to(target)
        self.assertIn("symlink_tracked", inspect_paths(self.root, ["tools/outside.txt"]))

    def test_block_unprivileged_workflow_regression(self):
        path = self.root / ".github/workflows/okubi-android-migrated-qa.yml"
        path.write_text(SAMPLE[".github/workflows/okubi-android-migrated-qa.yml"] +
                        "\npull_request_target:\n", encoding="utf-8")
        self.assertIn("unsafe_privileged_pr_trigger", self.check()["failed_check_codes"])


if __name__ == "__main__":
    unittest.main()
