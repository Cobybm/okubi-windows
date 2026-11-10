"""Source-level consistency checks; native emulator accuracy still needs hardware tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1] / "java/bo/nexoplay/app/retro"

class SystemProfilesTest(unittest.TestCase):
    def test_console_identifiers_align_with_core_adapter(self):
        registry=(ROOT/"RetroSystems.java").read_text(encoding="utf-8")
        adapter=(ROOT/"RetroCore.java").read_text(encoding="utf-8")
        ids=re.findall(r'new SystemSpec\("([A-Z0-9]+)"',registry)
        self.assertEqual(16,len(ids))
        self.assertEqual(len(ids),len(set(ids)))
        core=re.search(r'enum Console\s*\{([^}]+)\}',adapter)
        self.assertIsNotNone(core)
        registered={x.strip() for x in core.group(1).split(",")}
        self.assertEqual(set(ids),registered)

    def test_tiers_and_controller_basics(self):
        src=(ROOT/"RetroSystems.java").read_text(encoding="utf-8")
        profiles=re.findall(
            r'new SystemSpec\("([A-Z0-9]+)",.*?Tier\.(RECOMMENDED|EXPERIMENTAL),\s*Controller\.([A-Z_]+)',
            src,re.S)
        self.assertEqual(16,len(profiles))
        self.assertEqual(12,sum(1 for x in profiles if x[1]=="RECOMMENDED"))
        self.assertEqual(4,sum(1 for x in profiles if x[1]=="EXPERIMENTAL"))
        mapping={system:controller for system,tier,controller in profiles}
        self.assertEqual("TWO_WITH_SHOULDERS",mapping["GBA"])
        self.assertEqual("FOUR_BUTTON",mapping["SNES"])
        self.assertEqual("SIX_BUTTON",mapping["MD"])
        self.assertEqual("DUAL_SCREEN",mapping["NDS"])
        self.assertEqual("ONE_BUTTON",mapping["A2600"])

    def test_documented_psp_nds_launcher(self):
        src=(ROOT/"RetroExternalLauncher.java").read_text(encoding="utf-8")
        self.assertIn("org.ppsspp.ppsspp.PpssppActivity",src)
        self.assertIn("me.magnum.melonds.ui.emulator.EmulatorActivity",src)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION",src)

if __name__=="__main__":
    unittest.main()
