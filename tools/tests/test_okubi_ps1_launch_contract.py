"""PS1 launch adapter regression guard. No games or external emulators needed."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
AND=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app")
DESK=(ROOT/"desktop/src/bo/okubi/desktop")
ADAPTER=(AND/"OkubiPs1ExternalLauncher.java").read_text(encoding="utf-8")
MAIN=(AND/"MainActivity.java").read_text(encoding="utf-8")
SMART=(DESK/"DesktopSmartPerformanceDialog.java").read_text(encoding="utf-8")
FORMATS=(DESK/"DesktopPlaystationFormatAdvisor.java").read_text(encoding="utf-8")

class Ps1GameLaunchSafety(unittest.TestCase):
    def test_android_real_target_without_private_file_hacks(self):
        for expected in (
            'com.github.stenzek.duckstation',
            'com.github.stenzek.duckstation.EmulationActivity',
            'FLAG_GRANT_READ_URI_PERMISSION',
            'ClipData.newRawUri',
            'Intent.ACTION_MAIN',
            'intent.putExtra("bootPath",selected.toString())',
            'intent.putExtra("resumeState",false)',
            '!"content".equalsIgnoreCase(selected.getScheme())',
        ): self.assertIn(expected, ADAPTER)
        self.assertNotIn('MANAGE_EXTERNAL_STORAGE',ADAPTER)
        self.assertNotIn('file://',ADAPTER)
        self.assertNotIn('new FileOutputStream',ADAPTER)

    def test_ps1_accepts_only_known_images(self):
        for extension in ('.cue','.chd','.iso','.bin','.img','.ccd',
                          '.mds','.m3u','.pbp','.ecm'):
            self.assertIn('"'+extension+'"',ADAPTER)
        self.assertNotIn('name.endsWith(".zip")', ADAPTER)
        self.assertNotIn('name.endsWith(".pkg")', ADAPTER)
        self.assertIn('needsOtherFiles',ADAPTER)
        self.assertIn('consoleAmbiguous',ADAPTER)

    def test_android_picker_and_first_run_guide(self):
        for expected in (
            'REQUEST_PS1_EXTERNAL_LAUNCH', 'selectPs1Game()',
            'handoffPs1Document(data.getData())',
            'OkubiPs1ExternalLauncher.createIntent(document)',
            '"▶ JUGAR PS1 CON DUCKSTATION"',
            'tourTargets.put("discover_ps1",playPs1)',
            'new TourStep(Screen.DISCOVER,"discover_ps1"'
        ):
            self.assertIn(expected,MAIN)
        self.assertIn('conserva juntos todos sus archivos',MAIN)

    def test_windows_existing_safe_launch(self):
        for expected in (
            '"▶ PS1 · JUGAR"', 'quickPlayPs1()',
            'DesktopPlaystationFormatAdvisor.inspect(selected)',
            'DesktopSmartPerformance.Console.PS1',
            'catalog.addGame(selected,DesktopSmartPerformance.Console.PS1)',
            'refreshCatalog(selected.toAbsolutePath().normalize())',
            'launch();',
        ): self.assertIn(expected,SMART)
        self.assertIn('inspectCue(',FORMATS)
        self.assertIn('inspectPlaylist(',FORMATS)

if __name__ == "__main__": unittest.main()
