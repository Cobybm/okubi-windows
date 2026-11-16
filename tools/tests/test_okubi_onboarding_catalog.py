"""OKUBI guided Android onboarding and Windows legal catalogue safety gates."""
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MAIN=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text(encoding="utf-8")
TOUR=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/OkubiGuidedTour.java").read_text(encoding="utf-8")
PC=(ROOT/"desktop/src/bo/okubi/desktop/DesktopHomebrewDownloads.java").read_text(encoding="utf-8")
DIALOG=(ROOT/"desktop/src/bo/okubi/desktop/DesktopHomebrewDialog.java").read_text(encoding="utf-8")

class GuidedOnboardingAndGamesTests(unittest.TestCase):
    def test_real_control_highlight_not_static_image(self):
        for expression in ("Path.FillType.EVEN_ODD","path.addRoundRect(hole",
                           "focusTarget.getGlobalVisibleRect","focusTarget",
                           "canvas.drawPath(path,paint)","getLocationOnScreen",
                           "target.requestRectangleOnScreen",
                           "overlay.setClickable(true)",
                           "new OkubiGuidedTour(this,root)"):
            self.assertIn(expression, TOUR+"\n"+MAIN)
        self.assertNotIn("BitmapFactory",TOUR)
        self.assertNotIn("R.drawable",TOUR)

    def test_first_run_navigation_and_replay(self):
        for expression in ('TOUR_COMPLETED','startFirstTimeTourIfNeeded',
                           'guidedTourActive', 'getPreferences(MODE_PRIVATE).edit()',
                           '"▶ VOLVER A VER EL TUTORIAL"',
                           '"nav_library"','"library_retro"','"nav_discover"',
                           '"discover_scan"','"discover_homebrew"','"nav_retro"',
                           '"retro_catalog"','"nav_home"','"home_settings"',
                           '"tour_replay"', 'finishGuidedTour()'):
            self.assertIn(expression,MAIN)
        self.assertIn('if(guidedTourActive) { finishGuidedTour();return; }',MAIN)
        self.assertIn('getPreferences(MODE_PRIVATE).edit().putBoolean(TOUR_COMPLETED,true)',MAIN)

    def test_no_god_of_war_in_windows_discovery(self):
        self.assertNotIn("God of War",PC)
        self.assertNotIn("playstation.com/es-bo/god-of-war",PC)
        self.assertIn("PSoXide Demo Disc",PC)
        self.assertIn("Dottie Flowers",PC)
        self.assertIn("Goodboy Galaxy",PC)
        self.assertIn("Böbl",PC)
        self.assertIn("https://www.scummvm.org/es/games/",PC)
        self.assertIn("https://bonnie-studios.itch.io/psoxide-demo-disc",PC)

    def test_downloads_are_allowlisted_no_piracy(self):
        self.assertIn("ENTRIES.contains(entry)",PC)
        self.assertIn("HttpClient.Redirect.NEVER",PC)
        self.assertIn('No se incluyen ROMs comerciales',DIALOG) if False else None
        self.assertIn("DesktopHomebrewDownloads.authorizedDirect(entry)",DIALOG)
        self.assertIn("getSelectedIndex()",DIALOG)
        self.assertIn('new JComboBox<>(FILTERS)',DIALOG)
        self.assertTrue(re.search(r'new Entry\\("2048 GB.*https://hh3\\.gbdev\\.io',PC,re.S))

if __name__=="__main__":
    unittest.main()
