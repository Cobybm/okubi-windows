package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Offline PSP acceptance tests: no actual PPSSPP process or player data. */
public final class DesktopPspLauncherTest {
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("okubi-psp-test-");
        try {
            Path rom=Files.write(folder.resolve("my-game.iso"),new byte[2048]);
            Path other=Files.write(folder.resolve("disc.cso"),new byte[1024]);
            Path pbp=Files.write(folder.resolve("EBOOT.PBP"),new byte[1024]);
            Path unknown=Files.write(folder.resolve("exe.bat"),new byte[1024]);
            Path exe=Files.write(folder.resolve("PPSSPPWindows64.exe"),new byte[1024]);
            Path fakeExe=Files.write(folder.resolve("hacker.exe"),new byte[1024]);
            check(DesktopPspLauncher.isPspImage(rom),"ISO accepted.");
            check(DesktopPspLauncher.isPspImage(other),"CSO accepted.");
            check(DesktopPspLauncher.isPspImage(pbp),"EBOOT accepted.");
            check(!DesktopPspLauncher.isPspImage(unknown),"BAT never PSP.");
            check(!DesktopPspLauncher.isPspImage(folder.resolve("EBOOT.BIN")),"PS3 BIN not PSP.");
            check(DesktopPspLauncher.command(exe,rom).equals(List.of(
                    exe.toAbsolutePath().normalize().toString(),"--windowed",
                    rom.toAbsolutePath().normalize().toString())),
                    "Official PPSSPP CLI path kept separate from game arguments.");
            try {
                DesktopPspLauncher.command(fakeExe,rom);
                throw new AssertionError("Unknown executable accepted.");
            } catch(IOException expected) { }
            try {
                DesktopPspLauncher.validImage(unknown);
                throw new AssertionError("Script accepted as PSP game.");
            } catch(IOException expected) { }
            Path prefs=folder.resolve("okubi-psp.properties");
            DesktopPspLauncher.Library library=new DesktopPspLauncher.Library(prefs);
            check(library.add(rom),"ROM added.");
            check(!library.add(rom),"Duplicate ignored.");
            check(library.add(other),"Second PSP image added.");
            library.emulator(exe);
            DesktopPspLauncher.Library reloaded=new DesktopPspLauncher.Library(prefs);
            check(reloaded.games().size()==2,"PSP library survives reload.");
            check(exe.toAbsolutePath().normalize().equals(reloaded.emulator()),
                    "Emulator persists.");
            check(reloaded.remove(rom),"Only library reference removed.");
            check(Files.exists(rom),"Original user ROM must not be deleted.");
            check(Files.exists(other),"Other ROM not deleted.");
            check(new DesktopPspLauncher.Library(prefs).games().size()==1,
                    "Removal saved without deleting original files.");
            System.out.println("DesktopPspLauncherTest PASS");
        } finally {
            try(var files=Files.walk(folder)) {
                files.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}catch(IOException ignored){}
                });
            }
        }
    }
}
