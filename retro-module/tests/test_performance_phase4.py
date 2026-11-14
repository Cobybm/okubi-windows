"""Phase 4 source-level regression guards for low-memory Android devices.

Runtime measurements on an actual device are required for FPS, battery, heap
pressure and ROM load time; these contracts prevent avoidable regressions.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
RETRO=ROOT / "java/bo/nexoplay/app/retro"
APP=ROOT.parent / "android-app/app/src/main/java/bo/nexoplay/app"
NATIVE=ROOT / "native/sameboy_bridge.c"
CI=ROOT.parent / ".github/workflows/okubi-retro.yml"


class Phase4PerformanceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.player=(RETRO / "OkubiGameBoyActivity.java").read_text(encoding="utf-8")
        cls.native=NATIVE.read_text(encoding="utf-8")
        cls.ui=(APP / "MainActivity.java").read_text(encoding="utf-8")
        cls.rom=(RETRO / "OkubiRomFormats.java").read_text(encoding="utf-8")
        cls.reader=(RETRO / "RetroRomReader.java").read_text(encoding="utf-8")
        cls.workflow=CI.read_text(encoding="utf-8")

    def test_core_always_steps_and_audio_is_not_halved(self):
        player=self.player
        native=self.native
        self.assertIn("boolean present=renderPolicy.shouldPresent(surfaceReady);",player)
        self.assertIn("present ? imagePixels : null, samples",player)
        self.assertIn("if (present) {",player)
        self.assertIn("drawFrame(frame, imagePixels);",player)
        self.assertIn("previewFrameReady = true;",player)
        self.assertIn("renderPolicy.observeFrame(",player)
        self.assertIn("if (image) {",native)
        self.assertIn("core_run();",native)
        self.assertIn("SetShortArrayRegion(env, audio",native)
        self.assertIn("SetIntArrayRegion(env, image",native)
        self.assertIn("(image && (*env)->GetArrayLength",native)
        # The same audio buffer writes regardless of image cadence.
        self.assertIn("audio.write(samples, 0, audioCount",player)

    def test_audio_failures_backoff_without_spin(self):
        p=self.player
        self.assertIn("RetroAudioRetry audioRetry = new RetroAudioRetry()",p)
        self.assertIn("audioRetry.mayAttempt(SystemClock.elapsedRealtime())",p)
        self.assertIn("audioRetry.failed(SystemClock.elapsedRealtime())",p)
        self.assertIn("audioRetry.reset();",p)
        self.assertIn('getSharedPreferences("okubi_audio_v1"',p)
        self.assertIn("safeAudio ? AudioTrack.WRITE_BLOCKING",p)

    def test_performance_menu_and_safe_user_preferences(self):
        p=self.player
        helper=(RETRO/"RetroRenderPolicy.java").read_text(encoding="utf-8")
        self.assertIn("Rendimiento y ahorro de batería",p)
        self.assertIn('getSharedPreferences("okubi_performance_v1"',p)
        self.assertIn("RetroRenderPolicy.parseMode(",p)
        self.assertIn("RetroRenderPolicy.Mode.BATTERY",p)
        self.assertIn("RetroRenderPolicy.Mode.QUALITY",p)
        self.assertIn("RetroRenderPolicy.Mode.AUTOMATIC",p)
        self.assertIn("private static final int WINDOW = 120",helper)
        self.assertIn("private static final int RECOVER_WINDOWS = 3",helper)
        self.assertIn("frameNanos / 2L",helper)
        self.assertIn("return surfaceReady &&",helper)

    def test_lazy_cover_worker_and_memory_budget(self):
        ui=self.ui
        self.assertIn("new ArrayBlockingQueue<>(12)",ui)
        self.assertIn("new LruCache<String, Bitmap>(4 * 1024)",ui)
        self.assertIn("bitmap.getByteCount()",ui)
        self.assertIn("getGlobalVisibleRect(coverViewport)",ui)
        self.assertIn("coverTargets.clear();",ui)
        self.assertIn("setOnScrollChangeListener",ui)
        self.assertIn("coverWorker.execute(",ui)
        self.assertIn("private Bitmap decodeRetroCover(String address)",ui)
        self.assertIn("scaled.inSampleSize*=2",ui)
        self.assertIn("onTrimMemory(int level)",ui)
        self.assertIn("setImageDrawable(null)",ui)
        self.assertNotIn("Executors.newSingleThreadExecutor()",ui)

    def test_large_library_has_bounded_initial_view_count(self):
        ui=self.ui
        self.assertIn("LIBRARY_PAGE_SIZE = 36",ui)
        self.assertIn("int shown=Math.min(matches.size(),libraryShown)",ui)
        self.assertIn("for(int entryIndex=0;entryIndex<shown;entryIndex++)",ui)
        self.assertIn("for(int i=0;i<shown;i+=2)",ui)
        self.assertIn("MOSTRAR MÁS JUEGOS",ui)
        self.assertIn("libraryShown+LIBRARY_PAGE_SIZE",ui)
        self.assertIn("coverTargets.clear(); // Previous page",ui)

    def test_rom_ingest_bounded_and_no_copy_when_capacity_exact(self):
        self.assertIn("return RetroRomReader.readBounded(input,limit,minimum)",self.rom)
        self.assertIn("RomBuffer extends ByteArrayOutputStream",self.reader)
        self.assertIn("return count == buf.length ? buf : Arrays.copyOf(buf, count)",self.reader)
        self.assertIn("out.size() > maximum - n",self.reader)
        self.assertIn("if (n == 0)",self.reader)
        self.assertIn("RetroRomReader.readBounded(source, MAX_ROM_BYTES, 32768)",self.player)
        self.assertIn("MAX_GAME_BOY = 8 * 1024 * 1024",self.rom)

    def test_ci_compiles_all_performance_helpers_as_real_java(self):
        workflow=self.workflow
        for name in ("RetroPerformancePolicyTest.java", "RetroRomReaderTest.java",
                     "RetroAudioRetry.java", "RetroRenderPolicy.java"):
            self.assertIn(name,workflow)
        integrator=(ROOT/"integrate.py").read_text(encoding="utf-8")
        for name in ("RetroRenderPolicy.java", "RetroAudioRetry.java", "RetroRomReader.java"):
            self.assertIn(name,integrator)


if __name__=="__main__":
    unittest.main()
