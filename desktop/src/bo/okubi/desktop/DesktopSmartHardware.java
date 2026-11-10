package bo.okubi.desktop;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Hardware snapshot for performance recommendations. Read-only; no hardware
 * tuning, overclocking, registry edits or administrative permissions.
 */
public final class DesktopSmartHardware {
    public record Snapshot(String processor, int logicalCores, long memoryBytes,
                           String graphicsAdapter, boolean graphicsKnown,
                           double systemCpuLoad) {
        public double memoryGiB() { return memoryBytes / 1073741824.0; }
        public boolean integratedGraphics() {
            String gpu = graphicsAdapter == null ? "" : graphicsAdapter.toLowerCase(Locale.ROOT);
            return gpu.contains("vega") || gpu.contains("intel") || gpu.contains("uhd")
                    || gpu.contains("radeon(tm) graphics") || gpu.contains("iris")
                    || gpu.contains("integrated") || gpu.contains("ryzen graphics")
                    || gpu.contains("radeon graphics");
        }
        public boolean isBusy() {
            return systemCpuLoad >= .90 && systemCpuLoad <= 1.0;
        }
        public boolean strongForOlderConsoles() {
            return memoryGiB() >= 15 && logicalCores >= 8
                    && graphicsKnown && !integratedGraphics();
        }
    }

    private DesktopSmartHardware() { }

    public static Snapshot detect() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long memory = 0;
        double load = -1;
        try {
            java.lang.management.OperatingSystemMXBean raw =
                    ManagementFactory.getOperatingSystemMXBean();
            if (raw instanceof com.sun.management.OperatingSystemMXBean os) {
                memory = Math.max(0, os.getTotalMemorySize());
                load = os.getCpuLoad();
            }
        } catch (Exception | LinkageError ignored) {
            // Unknown stays unknown; never assume a powerful machine.
        }
        String cpu = readWindowsCim(
                "(Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name)");
        if (cpu.isBlank()) cpu = System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "CPU no detectada");
        String gpu = readWindowsCim(
                "(Get-CimInstance Win32_VideoController | Select-Object -First 1 -ExpandProperty Name)");
        boolean known = !gpu.isBlank();
        return new Snapshot(cpu.trim(), cores, memory,
                known ? gpu.trim() : "GPU no detectada", known, load);
    }

    /** Fixed command only; inputs cannot add shell commands. No admin rights. */
    private static String readWindowsCim(String fixedCommand) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            return "";
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe",
                    "-NoProfile", "-NonInteractive", "-Command", fixedCommand)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(2500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) return "";
            byte[] bytes = process.getInputStream().readNBytes(768);
            return new String(bytes, java.nio.charset.Charset.defaultCharset()).lines()
                    .map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse("");
        } catch (Exception ignored) {
            if (process != null) process.destroyForcibly();
            return "";
        }
    }
}
