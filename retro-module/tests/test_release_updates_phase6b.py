"""Release/upgrade safety checks for OKUBI UNIFIED (pure Python, no APK needed)."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/okubi_release_guard.py"
spec = importlib.util.spec_from_file_location("okubi_release_guard", SCRIPT)
guard = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = guard
spec.loader.exec_module(guard)

GOOD_SHA = "ab" * 32
OTHER_SHA = "cd" * 32


def badging(pkg="bo.nexoplay.app.unified", code=24,
            version="1.0.0-unified-beta5"):
    return f"package: name='{pkg}' versionCode='{code}' versionName='{version}'\n"


def signer(sha=GOOD_SHA, debug=False, scheme=True):
    return (
        "Verifies\n"
        f"Verified using v2 scheme (APK Signature Scheme v2): "
        f"{'true' if scheme else 'false'}\n"
        f"Signer #1 certificate DN: CN={'Android Debug' if debug else 'OKUBI UNIFIED'},O=OKUBI\n"
        f"Signer #1 certificate SHA-256 digest: {sha}\n"
    )


class ApkReleaseContracts(unittest.TestCase):
    def identity(self, **kwargs):
        return guard.parse_apk(badging(), signer(**kwargs))

    def test_parsing_and_fingerprint_format(self):
        apk = self.identity()
        self.assertEqual("bo.nexoplay.app.unified", apk.package)
        self.assertEqual(24, apk.code)
        self.assertEqual("1.0.0-unified-beta5", apk.name)
        self.assertEqual(GOOD_SHA, apk.cert_sha256)
        colon = ":".join(GOOD_SHA[i:i+2] for i in range(0, 64, 2))
        self.assertEqual(GOOD_SHA, guard.normalized_fingerprint(colon))
        with self.assertRaises(guard.ReleaseCheckError):
            guard.normalized_fingerprint("not-a-real-certificate")

    def test_canonical_debug_candidate_checks(self):
        guard.validate(self.identity(debug=True), kind="debug",
                       expected_name="1.0.0-unified-beta5",
                       minimum_code=24, pinned_sha256=None, previous=None)
        for package in ("bo.nexoplay.app", "bo.nexoplay.app.personal",
                        "bo.nexoplay.app.dpadtest", "bo.nexoplay.app.indietest"):
            with self.assertRaises(guard.ReleaseCheckError):
                guard.validate(guard.parse_apk(badging(pkg=package), signer()),
                               kind="debug", expected_name=None, minimum_code=1,
                               pinned_sha256=None, previous=None)
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(self.identity(),kind="debug",expected_name=None,
                           minimum_code=25,pinned_sha256=None,previous=None)

    def test_stable_release_needs_pinned_owner_certificate(self):
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(self.identity(),kind="release",expected_name=None,
                           minimum_code=24,pinned_sha256=None,previous=None)
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(self.identity(debug=True),kind="release",
                           expected_name=None,minimum_code=24,
                           pinned_sha256=GOOD_SHA,previous=None)
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(self.identity(),kind="release",expected_name=None,
                           minimum_code=24,pinned_sha256=OTHER_SHA,previous=None)
        guard.validate(self.identity(),kind="release",expected_name=None,
                       minimum_code=24,pinned_sha256=GOOD_SHA,previous=None)

    def test_existing_installs_can_update_only_with_same_certificate_and_higher_code(self):
        current=self.identity()
        previous=guard.parse_apk(badging(code=23,version="1.0.0-unified-beta4"),
                                 signer(sha=GOOD_SHA))
        guard.validate(current,kind="release",expected_name=None,minimum_code=24,
                       pinned_sha256=GOOD_SHA,previous=previous)
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(current,kind="release",expected_name=None,
                           minimum_code=24,pinned_sha256=GOOD_SHA,
                           previous=guard.parse_apk(badging(code=24),signer()))
        with self.assertRaises(guard.ReleaseCheckError):
            guard.validate(current,kind="release",expected_name=None,
                           minimum_code=24,pinned_sha256=GOOD_SHA,
                           previous=guard.parse_apk(badging(code=23),
                                                    signer(sha=OTHER_SHA)))

    def test_invalid_signer_is_rejected(self):
        for output in (signer(scheme=False), signer(sha=GOOD_SHA) +
                       f"Signer #2 certificate SHA-256 digest: {OTHER_SHA}\n",
                       "Signer #1 certificate SHA-256 digest: " + GOOD_SHA):
            with self.assertRaises(guard.ReleaseCheckError):
                guard.parse_apk(badging(), output)

    def test_workflow_checks_actual_apk_and_no_debug_release_fallback(self):
        workflow = (ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
        gradle = (ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
        self.assertIn("tools/okubi_release_guard.py",workflow)
        self.assertIn("OKUBI_RELEASE_CERT_SHA256",workflow)
        self.assertIn("--kind release",workflow)
        self.assertIn("--kind debug",workflow)
        self.assertIn("OKUBI_KEYSTORE_PATH",gradle)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",gradle)
        self.assertIn("versionCode 36",gradle)
        self.assertIn("versionName '1.0.0-unified-beta17'",gradle)
        self.assertNotIn("storePassword '",gradle)
        self.assertNotIn("keyPassword '",gradle)


if __name__ == "__main__":
    unittest.main()
