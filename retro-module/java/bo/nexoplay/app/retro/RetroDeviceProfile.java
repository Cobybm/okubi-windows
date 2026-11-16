package bo.nexoplay.app.retro;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import java.util.Locale;

/**
 * Conservative hardware information, not an FPS benchmark.
 * Actual emulation performance must be measured after cores are connected.
 */
public final class RetroDeviceProfile {
    private RetroDeviceProfile() {}

    public static String summary(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        long ram = 0;
        if (manager != null) {
            manager.getMemoryInfo(memory);
            ram = memory.totalMem;
        }
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String ramText = ram > 0
                ? String.format(Locale.US, " · RAM física aprox. %.1f GB",
                        ram / (1024.0 * 1024.0 * 1024.0))
                : "";
        return "Dispositivo: " + model + ramText
                + " · CPU " + Runtime.getRuntime().availableProcessors() + " núcleos";
    }

    public static String advice(RetroSystems.SystemSpec spec) {
        switch (spec.performance) {
            case ECO:
                return "Perfil sugerido: económico · escala original 1×, píxeles nítidos, sin shaders pesados";
            case BALANCED:
                return "Perfil sugerido: equilibrado · empezar en resolución nativa y comprobar audio/FPS";
            default:
                return "Perfil experimental: resolución nativa, sin mejoras gráficas y pruebas juego por juego";
        }
    }
}
