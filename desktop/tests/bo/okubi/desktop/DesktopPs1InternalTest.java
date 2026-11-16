package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Offline safety checks for integrated PS1 Libretro (no commercial ROM/BIOS). */
public final class DesktopPs1InternalTest {
    private static void assertTrue(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("okubi-internal-ps1-");
        try {
            Path disc=Files.write(dir.resolve("Digimon.World.3.chd"),new byte[2048]);
            Path iso=Files.write(dir.resolve("unknown.iso"),new byte[2048]);
            Path cue=Files.writeString(dir.resolve("Digimon.cue"),
                    "FILE \"Digimon.bin\" BINARY\n TRACK 01 MODE2/2352\n");
            Path list=Files.writeString(dir.resolve("discs.m3u"),
                    "Digimon.cue\n");
            Path props=dir.resolve("settings").resolve("library.properties");
            DesktopLibrary old=new DesktopLibrary(props);
            assertTrue(DesktopLibrary.Console.fromFile(iso)==null,
                    "Ambiguous ISO must not be automatically guessed as PS1.");
            assertTrue(DesktopPs1Disc.isSupported(disc),"CHD registered as PS1 candidate.");
            assertTrue(DesktopPs1Disc.requiresCompanions(cue),"CUE requires tracks.");
            assertTrue(!DesktopPs1Disc.requiresCompanions(disc),"CHD is self-contained.");
            assertTrue(BundledCores.nameFor(DesktopLibrary.Console.PS1)
                    .equals("mednafen_psx_libretro.dll"),"Bundled core auto-selection.");
            assertTrue(old.addConfirmedGame(disc,DesktopLibrary.Console.PS1),
                    "User-selected PS1 disc reference imported.");
            assertTrue(!old.addConfirmedGame(disc,DesktopLibrary.Console.PS1),
                    "Duplicate PS1 title not imported twice.");
            try {
                old.addConfirmedGame(cue,DesktopLibrary.Console.PS1);
                throw new AssertionError("Missing CUE companion should reject import.");
            }catch(IOException expected) {
                assertTrue(expected.getMessage().contains("Faltan"),
                        "Provide a meaningful missing track warning.");
            }
            Files.write(dir.resolve("Digimon.bin"),new byte[2048]);
            assertTrue(old.addConfirmedGame(cue,DesktopLibrary.Console.PS1),
                    "CUE imported with its companion BIN.");
            assertTrue(old.addConfirmedGame(list,DesktopLibrary.Console.PS1),
                    "M3U imported with correct descriptor.");
            assertTrue(new DesktopLibrary(props).games().stream().filter(
                    g->g.console==DesktopLibrary.Console.PS1).count()==3,
                    "PS1 console labels persist across restart.");
            assertTrue(Files.size(disc)==2048,"Import never modifies user disc.");
            Path attack=Files.writeString(dir.resolve("attack.cue"),
                    "FILE \"../../outside.bin\" BINARY\n");
            try {
                DesktopPs1Disc.validateCompanions(attack);
                throw new AssertionError("Path traversal accepted");
            } catch(IOException expected) {
                assertTrue(expected.getMessage().contains("inválida"),
                        "Reject traversal in CUE.");
            }
            assertTrue(!DesktopPs1Disc.isSupported(dir.resolve("untrusted.exe")),
                    "Executables not treated as PS1 images.");
            System.out.println("DesktopPs1InternalTest PASS");
        }finally{
            try(var stream=Files.walk(dir)){
                stream.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}catch(IOException ignored){}
                });
            }
        }
    }
}
