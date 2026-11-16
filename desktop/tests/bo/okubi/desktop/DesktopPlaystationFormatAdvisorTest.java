package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopPlaystationFormatAdvisorTest {
    private static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("okubi-playstation-files-");
        try {
            Path iso=Files.write(folder.resolve("Backup.ISO"),new byte[]{1,2,3});
            var isoAdvice=DesktopPlaystationFormatAdvisor.inspect(iso);
            check(isoAdvice.ambiguous(),"ISO must ask for console.");
            check(isoAdvice.systems().contains("PSP"),"PSP ISO recognized.");
            check(isoAdvice.systems().contains("PS2"),"PS2 ISO recognized.");

            Path pkg=Files.write(folder.resolve("Download.pkg"),new byte[]{1});
            var pkgAdvice=DesktopPlaystationFormatAdvisor.inspect(pkg);
            check(!pkgAdvice.directFile(),"PKG requires installation in emulator.");
            check(pkgAdvice.ambiguous(),"PS3 versus Vita package ambiguity.");

            Path vita=Files.write(folder.resolve("dump.VPK"),new byte[]{1});
            check(DesktopPlaystationFormatAdvisor.inspect(vita).systems()
                    .contains("PS Vita"),"Vita package recognized.");

            Path cue=Files.writeString(folder.resolve("My game.cue"),
                    "FILE \"Disc1.bin\" BINARY\n TRACK 01 MODE2/2352\n");
            check(DesktopPlaystationFormatAdvisor.inspect(cue).warnings().stream()
                    .anyMatch(x->x.contains("Falta pista")),"Missing CUE sidecar warned.");
            Files.write(folder.resolve("Disc1.bin"),new byte[]{1});
            check(DesktopPlaystationFormatAdvisor.inspect(cue).warnings().stream()
                    .noneMatch(x->x.contains("Falta pista")),"CUE companion detected.");

            Path malicious=Files.writeString(folder.resolve("malicious.cue"),
                    "FILE \"../../outside.bin\" BINARY\n");
            check(DesktopPlaystationFormatAdvisor.inspect(malicious).warnings().stream()
                    .anyMatch(x->x.contains("ruta no permitida")),"CUE traversal warned.");

            Path m3u=Files.writeString(folder.resolve("collection.m3u"),
                    "disc1.chd\ndisc2.chd\n");
            check(DesktopPlaystationFormatAdvisor.inspect(m3u).warnings().size()==2,
                    "M3U companion disks checked.");
            var unknown=DesktopPlaystationFormatAdvisor.inspect(
                    Files.write(folder.resolve("evil.EXE"),new byte[]{1}));
            check(unknown.systems().isEmpty(),"Arbitrary EXE never becomes game.");
            check(!DesktopSmartPerformance.acceptedFile(
                    DesktopSmartPerformance.Console.PS1,"evil.exe"),"No executable.");
            check(DesktopSmartPerformance.suggestConsole("game.CHD")==null,
                    "Ambiguous compressed disc not auto-routed.");
            check(DesktopSmartPerformance.suggestConsole("PSP.ISO")==null,
                    "Ambiguous PSP ISO not assumed PS2.");
            System.out.println("DesktopPlaystationFormatAdvisorTest PASS");
        } finally {
            try (var paths=Files.walk(folder)) {
                paths.sorted((a,b)->b.compareTo(a)).forEach(p->{
                    try { Files.deleteIfExists(p); } catch(IOException ignored) { }
                });
            }
        }
    }
}
