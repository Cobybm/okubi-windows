"""QA57 package isolation and tamper rejection without adb/device writes."""
import importlib.util
from pathlib import Path
import unittest
import tempfile
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "okubi_qa57_verify.py"
spec = importlib.util.spec_from_file_location("okubi_qa57_verify", SCRIPT)
qa = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(qa)


class QA57SafetyTests(unittest.TestCase):
    def setUp(self):
        self.original = {"package": qa.ORIGINAL, "versionCode": 36,
                         "versionName": qa.BASE_VERSION, "label": "OKUBI 遊",
                         "minSdk": 26}
        self.sandbox = {"package": qa.QA_PACKAGE, "versionCode": 36,
                        "versionName": qa.QA_VERSION, "label": "OKUBI QA57 遊",
                        "minSdk": 26}

    def test_distinct_installation_identity(self):
        qa.compare(self.original, self.sandbox)
        self.assertNotEqual(self.original["package"], self.sandbox["package"])

    def test_canonical_package_never_becomes_qa(self):
        bad = dict(self.original, package=qa.QA_PACKAGE)
        with self.assertRaisesRegex(ValueError, "normal"):
            qa.compare(bad, self.sandbox)

    def test_debug_build_missing_suffix_must_fail(self):
        with self.assertRaisesRegex(ValueError, "separada"):
            qa.compare(self.original, dict(self.sandbox, package=qa.ORIGINAL))

    def test_distinct_label_required(self):
        with self.assertRaisesRegex(ValueError, "visualmente"):
            qa.compare(self.original, dict(self.sandbox, label="OKUBI 遊"))

    def test_no_android_sdk_regression(self):
        with self.assertRaisesRegex(ValueError, "SDK"):
            qa.compare(self.original, dict(self.sandbox, minSdk=29))

    def test_apk_badging_parser(self):
        text = ("package: name='bo.nexoplay.app.unified.qa57' "
                "versionCode='36' versionName='1.0.0-unified-beta17-qa57'\n"
                "sdkVersion:'26'\n"
                "targetSdkVersion:'34'\n"
                "application-label:'OKUBI QA57 遊'\n")
        self.assertEqual(qa.metadata(text), self.sandbox)
        with self.assertRaises(ValueError):
            qa.metadata("package: name='broken'\n")

    def test_source_does_not_install_uninstall_or_delete(self):
        source = SCRIPT.read_text(encoding="utf-8")
        for forbidden in ("adb install", "adb uninstall", "pm clear",
                          "os.remove(", "shutil.rmtree(", "shell=True",
                          "device_serial", "--replace"):
            self.assertNotIn(forbidden, source)

    def test_workflow_keeps_both_separate_apks(self):
        root = SCRIPT.parents[1]
        gradle = (root / "android-app/app/build.gradle").read_text(encoding="utf-8")
        workflow = (root / ".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
        self.assertIn("applicationId 'bo.nexoplay.app.unified'", gradle)
        self.assertIn("applicationIdSuffix '.qa57'", gradle)
        self.assertIn("project.findProperty('okubiQaSideBySide')", gradle)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK", workflow)
        self.assertIn("OKUBI-A05S-QA57-SIDE-BY-SIDE-APK", workflow)
        self.assertIn("-PokubiQaSideBySide=true", workflow)
        self.assertNotIn("adb uninstall", workflow)
        self.assertNotIn("adb install", workflow)

    def test_zip_does_not_contain_personal_cartridges(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "synthetic.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"fake")
                z.writestr("classes.dex", b"dexfake")
                z.writestr("synthetic-padding.bin", b"test" * 3500)
                for abi in qa.REQUIRED_ABIS:
                    for core in qa.REQUIRED_CORES:
                        z.writestr(f"lib/{abi}/{core}", b"synthetic-core")
                z.writestr("assets/personal-roms/private.gba", b"no")
                z.writestr("assets/licenses/Supafaust-GPL-2.0.txt", b"license")
            with self.assertRaisesRegex(ValueError, "personal"):
                qa.inspect_archive(apk)


if __name__ == "__main__":
    unittest.main()
