"""Phase 11: fake-ADB tests for a device preflight that cannot mutate a phone."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/okubi_a05s_preflight.py"
spec = importlib.util.spec_from_file_location("okubi_a05s_preflight", SOURCE)
preflight = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = preflight
spec.loader.exec_module(preflight)


class FakeAdb:
    def __init__(self, state="device", multi=False):
        self.calls = []
        self.state = state
        self.multi = multi

    def __call__(self, arguments):
        self.calls.append(tuple(arguments))
        if arguments == ["devices"]:
            suffix = "R58EXAMPLE2\tdevice\n" if self.multi else ""
            return 0, "List of devices attached\nR58EXAMPLE\t" + self.state + "\n" + suffix
        cmd = arguments[2:]
        if cmd == ["shell", "getprop", "ro.product.model"]:
            return 0, "SM-A057M\n"
        if cmd == ["shell", "getprop", "ro.build.version.sdk"]:
            return 0, "34\n"
        if cmd == ["shell", "getprop", "ro.build.version.release"]:
            return 0, "14\n"
        if cmd[:3] == ["shell", "pm", "path"]:
            return (0, "package:/data/app/sandbox/base.apk\n") if cmd[3] in (
                preflight.CANONICAL, preflight.LEGACY[2]
            ) else (1, "")
        if cmd[:3] == ["shell", "dumpsys", "package"]:
            code = 36 if cmd[3] == preflight.CANONICAL else 18
            return 0, "versionCode=" + str(code) + " minSdk=26\n" + \
                      "versionName=1.0.0-unified-beta17\n"
        if cmd[:3] == ["shell", "run-as", preflight.LEGACY[2]]:
            return 0, "uid=12345\n"
        raise AssertionError("Unexpected ADB request: " + repr(arguments))


class PreflightDeviceTests(unittest.TestCase):
    def test_read_only_scan_and_report_no_identifiers(self):
        adb = FakeAdb()
        result = preflight.collect(adb)
        report = preflight.make_report(result)
        self.assertIn("SM-A057M", report)
        self.assertIn("bo.nexoplay.app.unified", report)
        self.assertIn("versionCode 36", report)
        self.assertIn("Rescate ADB run-as: posible", report)
        self.assertNotIn("R58EXAMPLE", report)
        self.assertNotIn("/data/app", report)
        self.assertNotIn("uid=12345", report)
        self.assertEqual(len(preflight.PACKAGES), 5)
        for args in adb.calls:
            joined = " ".join(args)
            for forbidden in (" uninstall ", " install ", " clear ",
                              " push ", " pull ", " rm ", " cat ",
                              " am start ", " su "):
                self.assertNotIn(forbidden, " " + joined + " ")

    def test_unauthorized_or_multiple_devices_rejected(self):
        for fake in (FakeAdb(state="unauthorized"), FakeAdb(multi=True)):
            with self.assertRaises(preflight.PreflightError):
                preflight.collect(fake)
            self.assertEqual(len(fake.calls), 1)

    def test_package_version_parsing_and_sanitization(self):
        self.assertEqual(
            ("36", "1.0.0-unified-beta17"),
            preflight.package_version("versionCode=36 targetSdk=34\nversionName=1.0.0-unified-beta17\n"),
        )
        self.assertEqual(
            ("no disponible", "no disponible"),
            preflight.package_version("unknown"),
        )
        self.assertNotIn("\x1b", preflight.safe_label("\x1b[31mA05s"))

    def test_absent_unified_does_not_claim_success(self):
        sample = {
            "model": "SM-A057M", "sdk": "34", "android": "14",
            "packages": [{"package": preflight.CANONICAL, "installed": False,
                          "version_code": "no disponible",
                          "version_name": "no disponible",
                          "rescue_debug": "no comprobado"}],
        }
        self.assertIn("UNIFIED no instalada", preflight.make_report(sample))
        self.assertNotIn("Beta 16 detectada", preflight.make_report(sample))


if __name__ == "__main__":
    unittest.main()
