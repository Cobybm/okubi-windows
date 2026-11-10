package bo.nexoplay.app.retro;

public final class OkubiDownloadProgressTest {
    private static void check(boolean ok,String msg) {
        if(!ok)throw new AssertionError(msg);
    }
    private static void eq(Object expected,Object actual,String msg) {
        if(!expected.equals(actual))throw new AssertionError(
                msg+" expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        check(OkubiDownloadProgress.active(1),"pending");
        check(OkubiDownloadProgress.active(2),"running");
        check(OkubiDownloadProgress.active(4),"paused");
        for(int status:new int[]{-1,8,16,0}) {
            check(!OkubiDownloadProgress.active(status),"not active "+status);
        }
        eq(-1,OkubiDownloadProgress.percent(1,-1),"missing total");
        eq(-1,OkubiDownloadProgress.percent(-5,10),"invalid bytes");
        eq(0,OkubiDownloadProgress.percent(0,100),"0%");
        eq(25,OkubiDownloadProgress.percent(25,100),"25%");
        eq(99,OkubiDownloadProgress.percent(99,100),"99%");
        eq(100,OkubiDownloadProgress.percent(100,100),"100%");
        eq(100,OkubiDownloadProgress.percent(999999,100),"clamp");
        eq(99,OkubiDownloadProgress.percent(Long.MAX_VALUE-1,Long.MAX_VALUE),
            "overflow safe");
        check(OkubiDownloadProgress.message(2,50,100,false).contains("50%"),
                "progress should reflect bytes");
        check(OkubiDownloadProgress.message(2,50,-1,false).contains("desconocido"),
                "unknown size must not invent progress");
        check(OkubiDownloadProgress.message(8,50,100,false).contains("eliminado"),
                "completed DM row is not proof that file exists");
        check(OkubiDownloadProgress.message(8,50,100,true).contains("DISPONIBLE"),
                "verified success");
        check(OkubiDownloadProgress.message(16,0,0,false).contains("FALLÓ"),
                "failed");
        eq("↻ REINTENTAR DESCARGA",OkubiDownloadProgress.action(16,false,true),
                "retry failed patch");
        eq("↻ REINTENTAR DESCARGA",OkubiDownloadProgress.action(8,false,false),
                "retry vanished file");
        eq("⌛ DESCARGA EN CURSO",OkubiDownloadProgress.action(4,false,true),
                "no duplicate while paused");
        eq("✓ APLICAR PARCHE",OkubiDownloadProgress.action(8,true,true),
                "patch is NOT a playable ROM");
        eq("▶ JUGAR EN OKUBI",OkubiDownloadProgress.action(8,true,false),
                "play available ROM");
        System.out.println("OkubiDownloadProgressTest: OK");
    }
}
