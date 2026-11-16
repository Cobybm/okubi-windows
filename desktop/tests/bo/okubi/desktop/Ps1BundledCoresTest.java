package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Package gate: PS1 core must ship alongside existing GB/GBA/SNES cores. */
public final class Ps1BundledCoresTest {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("core-dir required");
        Path root=Path.of(args[0]).toAbsolutePath();
        System.setProperty("okubi.cores.dir",root.toString());
        Path ps1=BundledCores.resolve(DesktopLibrary.Console.PS1);
        if(ps1==null || !Files.isRegularFile(ps1)
                || Files.size(ps1)<100000)
            throw new AssertionError("Real Beetle PSX Windows DLL was not bundled.");
        Properties props=new Properties();
        try(var in=Files.newInputStream(root.resolve("CORE-SHA256.properties"))) {
            props.load(in);
        }
        String pinned=props.getProperty("mednafen_psx_libretro.dll");
        if(pinned==null || !pinned.matches("[0-9a-f]{64}")
                || !pinned.equals(BundledCores.sha256(ps1)))
            throw new AssertionError("PS1 DLL SHA-256 mismatch.");
        for(DesktopLibrary.Console console:DesktopLibrary.Console.values()) {
            if(BundledCores.resolve(console)==null)
                throw new AssertionError("Regression: old core missing: "+console);
        }
        System.out.println("Ps1BundledCoresTest PASS: four real trusted cores.");
    }
}
