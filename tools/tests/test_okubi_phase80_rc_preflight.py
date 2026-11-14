"""Phase80 package verification tests use synthetic ZIP/APK bytes only."""
from __future__ import annotations

import hashlib
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import okubi_phase80_rc_preflight as qa


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class Phase80ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.windows = self.root / qa.WINDOWS_ZIP
        self.android = self.root / qa.ANDROID_APK
        self.sums = self.root / "SHA256SUMS.txt"
        self.core_bytes = {
            name: (name.encode("ascii") + b" synthetic" * 20)
            for name in qa.CORES
        }
        self.evidence = {
            "releases_authorized": False,
            "cores": [{"name": n, "dll_sha256": digest(self.core_bytes[n])}
                      for n in qa.CORES],
        }
        self.badging = (
            "package: name='bo.nexoplay.app.unified.qa57' "
            "versionCode='36' versionName='1.0.0-unified-beta17-qa57'\n"
            "sdkVersion:'26'\n"
            "application-label:'OKUBI QA57 遊'\n"
        )
        self.signer = (
            "Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\n"
            "Signer #1 certificate DN: CN=Android Debug, O=Android, C=US\n"
            "Signer #1 certificate SHA-256 digest: " + "a" * 64 + "\n"
        )

    def checksum(self, artifact: Path) -> None:
        self.sums.write_text(digest(artifact.read_bytes()) +
                             " *" + artifact.name + "\n", encoding="utf-8")

    def make_windows(self, extras=None):
        root = "OKUBI Desktop/"
        app = root + "app/"
        files = {
            root + "OKUBI Desktop.exe": b"MZ" + b"\0" * 100,
            app + "okubi-desktop.jar": b"JAR synthetic" * 4,
            app + "okubi_libretro_jni.dll": b"MZ" + b"\0" * 100,
            app + "RUN-QA57-ISOLATED.cmd":
                b'@echo off\nset "APPDATA=OKUBI-QA57"\n',
            app + "QA57-READ-ME-FIRST.md": b"QA synthetic instructions",
            app + "PHASE80-READ-ME-FIRST.md": b"Final QA instructions phase 80",
            app + "cores/CORE-SHA256.properties":
                ("\n".join(f"{name}={digest(self.core_bytes[name])}"
                           for name in qa.CORES) + "\n").encode(),
        }
        for name, raw in self.core_bytes.items():
            files[app + "cores/" + name] = raw
        for name in qa.LICENSES:
            files[app + "cores/licenses/" + name] = b"synthetic license notice" * 2
        files.update(extras or {})
        with zipfile.ZipFile(self.windows, "w", compression=zipfile.ZIP_STORED) as z:
            for name, data in files.items():
                z.writestr(name, data)
            z.writestr(app + "fixture-data.dat", os.urandom(11000))
        self.checksum(self.windows)

    def make_android(self, extras=None):
        files = {
            "AndroidManifest.xml": b"synthetic manifest content",
            "classes.dex": b"dex synthetic" * 4,
        }
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            for name in ("sameboy", "mgba", "supafaust"):
                files[f"lib/{abi}/lib{name}_libretro_android.so"] = b"synthetic" * 8
            files[f"lib/{abi}/libokubi_sameboy_bridge.so"] = b"synthetic" * 8
        for name in ("SameBoy-LICENSE.txt", "mGBA-LICENSE.txt",
                     "Supafaust-GPL-2.0.txt", "Supafaust-SOURCE.txt"):
            files["assets/licenses/" + name] = b"source and license test" * 3
        files.update(extras or {})
        with zipfile.ZipFile(self.android, "w", compression=zipfile.ZIP_STORED) as z:
            for name, data in files.items():
                z.writestr(name, data)
            z.writestr("assets/fixture-data.dat", os.urandom(11000))
        self.checksum(self.android)

    def test_windows_qazip_cores_hashes_and_isolation_pass(self):
        self.make_windows()
        result = qa.inspect("windows", self.windows, self.sums, self.evidence)
        self.assertEqual(result["qa_integrity"], "pass")
        self.assertEqual(result["facts"]["cores_verified"], 3)
        self.assertFalse(result["public_release_authorized"])
        self.assertFalse(result["real_device_qa_verified"])

    def test_android_parallel_debug_apk_passes(self):
        self.make_android()
        report = qa.inspect("android", self.android, self.sums, None,
                            self.badging, self.signer)
        self.assertEqual(report["facts"]["abis_verified"], 4)
        self.assertEqual(report["facts"]["signer"], "debug_only")
        self.assertFalse(report["installed_or_replaced_application"])

    def test_modified_windows_core_is_rejected(self):
        self.make_windows({"OKUBI Desktop/app/cores/sameboy_libretro.dll": b"tampered" * 20})
        with self.assertRaisesRegex(qa.CheckError, "dll_mismatch"):
            qa.inspect("windows", self.windows, self.sums, self.evidence)

    def test_wrong_archive_checksum_is_rejected(self):
        self.make_android()
        self.sums.write_text("a" * 64 + " *" + self.android.name)
        with self.assertRaisesRegex(qa.CheckError, "checksum_mismatch"):
            qa.inspect("android", self.android, self.sums, None,
                       self.badging, self.signer)

    def test_android_canonical_package_cannot_masquerade_as_qa(self):
        self.make_android()
        badging = self.badging.replace("bo.nexoplay.app.unified.qa57",
                                      "bo.nexoplay.app.unified")
        with self.assertRaisesRegex(qa.CheckError, "package_identity"):
            qa.inspect("android", self.android, self.sums, None,
                       badging, self.signer)

    def test_android_release_certificate_rejected_in_qa(self):
        self.make_android()
        signer = self.signer.replace("CN=Android Debug", "CN=OKUBI Release")
        with self.assertRaisesRegex(qa.CheckError, "debug_certificate"):
            qa.inspect("android", self.android, self.sums, None,
                       self.badging, signer)

    def test_private_game_and_rom_are_not_packaged(self):
        self.make_android({"assets/personal-roms/pokemon.gba": b"private cartridge"})
        with self.assertRaisesRegex(qa.CheckError, "private_game"):
            qa.inspect("android", self.android, self.sums, None,
                       self.badging, self.signer)

    def test_path_traversal_cannot_be_inside_zip(self):
        self.make_windows({"../escape.txt": b"malicious file" * 3})
        with self.assertRaisesRegex(qa.CheckError, "unsafe_zip_member"):
            qa.inspect("windows", self.windows, self.sums, self.evidence)

    def test_duplicate_casefolded_entries_are_rejected(self):
        self.make_windows({"OKUBI Desktop/app/cores/SAMEBOY_LIBRETRO.dll": b"altered" * 10})
        with self.assertRaisesRegex(qa.CheckError, "duplicate_archive_member"):
            qa.inspect("windows", self.windows, self.sums, self.evidence)

    def test_lost_qa_launcher_is_rejected(self):
        self.make_windows({"OKUBI Desktop/app/RUN-QA57-ISOLATED.cmd":
                           b"@echo off\nrem no isolated appdata\n"})
        with self.assertRaisesRegex(qa.CheckError, "test_profile_not_isolated"):
            qa.inspect("windows", self.windows, self.sums, self.evidence)

    def test_no_live_install_or_private_save_modification(self):
        source = Path(qa.__file__).read_text(encoding="utf-8")
        for forbidden in ("adb install", "adb uninstall", "pm clear", "shutil.rmtree(",
                          "os.remove(", "delete_user_data(", "runas /user"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
