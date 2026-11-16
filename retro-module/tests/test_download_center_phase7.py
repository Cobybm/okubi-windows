"""Phase 7 download dashboard: regressions without an Android phone.

UI and DownloadManager behavior are checked at source level here and validated
by Java status tests plus Android Gradle compilation in the same GitHub run.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
CATALOG=(RETRO/"OkubiCatalogActivity.java").read_text(encoding="utf-8")
PROGRESS=(RETRO/"OkubiDownloadProgress.java").read_text(encoding="utf-8")
OVERLAY=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")

class DownloadDashboardContracts(unittest.TestCase):
    def test_one_central_dashboard_for_game_and_patch_and_user_links(self):
        for part in (
            "private void showDownloadCenter(LinearLayout root)",
            "MIS DESCARGAS  /  OKUBI",
            "showDownloadCenter(root);",
            "centerDirectStatus",
            "centerPatchStatuses",
            "centerExternalStatus",
            "VERIFIED_PATCHES",
            "OkubiIndieLibrary.saved(this,game.url,game.name)",
            "↗ ABRIR DESCARGAS DE ANDROID",
            "DownloadManager.ACTION_VIEW_DOWNLOADS",
            "refreshDownloadCenter();",
            "startProgressTicker();"
        ):
            self.assertIn(part,CATALOG)

    def test_real_download_manager_progress_not_fake_percentage(self):
        for part in (
            "DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR",
            "DownloadManager.COLUMN_TOTAL_SIZE_BYTES",
            "DownloadManager.COLUMN_STATUS",
            "manager.openDownloadedFile(id)",
            "snapshot.readable",
            "OkubiDownloadProgress.message(",
            "OkubiDownloadProgress.action(",
            "DownloadManager.STATUS_FAILED",
            "isPatchPending(patch)",
        ):
            self.assertIn(part,CATALOG)
        for part in (
            "public static int percent(long downloaded, long total)",
            "if (downloaded < 0 || total <= 0) return -1;",
            "return p >= 0",
            "tamaño desconocido",
            "Archivo eliminado o sin permiso",
            "EN COLA DE DESCARGAS",
            "EN PAUSA",
            "FALLÓ LA DESCARGA",
            "REINTENTAR DESCARGA",
            "APLICAR PARCHE",
        ):
            self.assertIn(part,PROGRESS)

    def test_streamed_homebrew_progress_is_bounded_and_atomic(self):
        for part in (
            "directDownloadedBytes=0;",
            "directTotalBytes=connection.getContentLengthLong();",
            "if(directTotalBytes>8L*1024*1024)",
            "directDownloadedBytes=bytes;",
            "if(bytes-lastReport>=131072)",
            "if(directTotalBytes>0&&bytes!=directTotalBytes)",
            "AtomicFile atomic = new AtomicFile(target)",
            "atomic.failWrite(out)",
            "if(Thread.currentThread().isInterrupted())",
            '"/wolandark/Ascension/master/build/rom/game.gb"',
        ):
            self.assertIn(part,CATALOG)
        self.assertNotIn("android.permission.MANAGE_EXTERNAL_STORAGE", CATALOG)

    def test_external_creator_shared_pages_select_correct_game(self):
        for part in (
            "pendingCreatorTitle = game.name;",
            'bundle.putString("pending_indie_title", pendingCreatorTitle)',
            'pendingCreatorTitle = state.getString("pending_indie_title")',
            "available.name.equals(title)",
            "OkubiIndieLibrary.link(this, game.url, game.name",
        ):
            self.assertIn(part,CATALOG)

    def test_polling_only_while_active_and_workflow_kept_compatible(self):
        self.assertIn("progressHandler.postDelayed(progressTicker,1500)",CATALOG)
        self.assertIn("progressHandler.removeCallbacks(progressTicker)",CATALOG)
        self.assertIn("protected void onPause()",CATALOG)
        self.assertIn("OkubiDownloadProgress.java",OVERLAY)
        self.assertIn("OkubiDownloadProgressTest.java",WORKFLOW)
        self.assertIn("RetroDpadHitMapTest",WORKFLOW)
        self.assertIn("OkubiSaveArchiveTest",WORKFLOW)
        self.assertIn("Okubi-UNIFIED",WORKFLOW.replace("OKUBI-UNIFIED","Okubi-UNIFIED"))
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("1.0.0-unified-beta17",GRADLE)

if __name__=="__main__":
    unittest.main()
