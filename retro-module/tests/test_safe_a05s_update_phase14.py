"""Phase 14: real APK signing guard logic, mocked ADB (no device writes)."""
import importlib.util
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
spec = importlib.util.spec_from_file_location(
    "okubi_a05s_update_guard", TOOLS/"okubi_a05s_update_guard.py"
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
from okubi_release_guard import ApkIdentity


def identity(cert="ab"*32, code=36, name="1.0.0-unified-beta17",
             package="bo.nexoplay.app.unified"):
    return ApkIdentity(package, code, name, cert, True, True)


class Phase14SafeUpdateTests(unittest.TestCase):
    def test_remote_android_paths_are_strict_and_read_only(self):
        valid = "package:/data/app/~~aB_c1==/bo.nexoplay.app.unified-AaB==/base.apk\n"
        self.assertEqual(
            guard.installed_base_path(valid),
            "/data/app/~~aB_c1==/bo.nexoplay.app.unified-AaB==/base.apk"
        )
        self.assertIsNone(guard.installed_base_path(""))
        with self.assertRaises(guard.PreinstallError):
            guard.installed_base_path("package:/data/app/../base.apk\n")
        with self.assertRaises(guard.PreinstallError):
            guard.installed_base_path("package:/data/app/a/split_config.apk\n")
        with self.assertRaises(guard.PreinstallError):
            guard.installed_base_path("injected-command\n")
        with self.assertRaises(guard.PreinstallError):
            guard.installed_base_path("package:/data/app/a/base.apk\npackage:/b/base.apk\n")

    def test_identity_compatibility_and_rejection(self):
        candidate = identity()
        previous = identity(code=34, name="1.0.0-unified-beta15")
        self.assertIn("ACTUALIZACIÓN",guard.decision(candidate, previous))
        self.assertIn("NUEVA INSTALACIÓN",guard.decision(candidate,None))
        for old in (
            identity(cert="cd"*32,code=34,name="1.0.0-unified-beta15"),
            identity(code=36,name="1.0.0-unified-beta17"),
            identity(code=37,name="1.0.0-unified-beta17"),
            identity(code=26,name="beta7",package="bo.nexoplay.app.personal"),
        ):
            with self.assertRaises(guard.PreinstallError):
                guard.decision(candidate,old)
        for wrong in (
            identity(code=26),
            identity(name="fake"),
            identity(package="bo.nexoplay.app.personal")
        ):
            with self.assertRaises(guard.PreinstallError):
                guard.decision(wrong,None)

    def test_candidate_checked_before_device_and_prevents_install(self):
        with TemporaryDirectory() as d:
            apk=Path(d)/"beta16.apk"
            apk.write_bytes(b"test-file")
            with patch.object(guard,"examine",return_value=identity(
                cert="cd"*32,code=26)),patch.object(
                guard,"authorized_serial"
            ) as phone:
                with self.assertRaises(guard.PreinstallError):
                    guard.perform(apk,adb="adb",aapt="aapt",apksigner="apksigner")
                phone.assert_not_called()

    def test_installed_signature_mismatch_blocks_without_mutation(self):
        with TemporaryDirectory() as d:
            apk=Path(d)/"beta16.apk"
            apk.write_bytes(b"test-file")
            called=[]
            def fake_adb(adb, serial, *args):
                called.append(args)
                if args[:3]==("shell","pm","path"):
                    return "package:/data/app/~~abcd/bo.nexoplay.app.unified-x/base.apk\n"
                if args and args[0]=="pull":
                    Path(args[2]).write_bytes(b"existing-apk")
                    return ""
                raise AssertionError("unexpected adb call")
            with patch.object(guard,"examine",side_effect=[
                identity(),identity("cd"*32,26,"beta7")
            ]),patch.object(guard,"authorized_serial",return_value="hidden-device-id"),patch.object(
                guard,"adb_command",side_effect=fake_adb
            ):
                with self.assertRaisesRegex(guard.PreinstallError,"firma diferente"):
                    guard.perform(apk,adb="adb",aapt="aapt",apksigner="apksigner")
            self.assertEqual("pull",called[1][0])
            self.assertEqual(("shell","pm","path",
                              guard.CANONICAL_PACKAGE),called[0])
            self.assertEqual(2,len(called))
            self.assertNotIn("hidden-device-id",str(called))

    def test_new_installation_never_pulls_or_reads_private_files(self):
        with TemporaryDirectory() as d:
            apk=Path(d)/"beta16.apk"
            apk.write_bytes(b"test-file")
            called=[]
            def fake_adb(adb,serial,*args):
                called.append(args)
                return ""
            with patch.object(guard,"examine",return_value=identity()),patch.object(
                guard,"authorized_serial",return_value="hidden"
            ),patch.object(guard,"adb_command",side_effect=fake_adb):
                output=guard.perform(apk,adb="adb",aapt="aapt",apksigner="apksigner")
            self.assertIn("NUEVA INSTALACIÓN",output)
            self.assertNotIn("hidden",output)
            self.assertEqual(1,len(called))
            self.assertEqual("pm",called[0][1])

    def test_no_destructive_adb_actions_exist_in_tool(self):
        source=(TOOLS/"okubi_a05s_update_guard.py").read_text(encoding="utf-8")
        for forbidden in ('"install"', '"uninstall"', '"clear"', '"push"',
                          '"rm"', '"cat"', '"run-as"', "shell=True"):
            self.assertNotIn(forbidden,source)
        self.assertIn('"pull", remote, str(local)',source)
        self.assertIn("TemporaryDirectory",source)
        self.assertIn("decision(candidate, installed)",source)


if __name__=="__main__":
    unittest.main()
