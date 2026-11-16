package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Small independent PSP library and vetted PPSSPP command builder.
 * This is a frontend for the user's separately installed official emulator.
 * It neither downloads copyrighted ROMs nor reads/writes PPSSPP save data.
 */
public final class DesktopPspLauncher {
    private DesktopPspLauncher() { }

    public static Path defaultSettings() {
        String appData=System.getenv("APPDATA");
        Path root=appData==null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"),".okubi")
                : Path.of(appData,"OKUBI");
        return root.resolve("psp-library-v1.properties");
    }

    public static boolean isPspImage(Path file) {
        if(file==null || file.getFileName()==null)return false;
        String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".iso") || name.endsWith(".cso")
                || name.endsWith(".chd") || name.equals("eboot.pbp");
    }

    public static Path validImage(Path file) throws IOException {
        if(!isPspImage(file))throw new IOException(
                "PPSSPP acepta ISO, CSO, CHD y EBOOT.PBP compatibles con PSP.");
        Path safe=file.toAbsolutePath().normalize();
        if(!Files.isRegularFile(safe,LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(safe) || Files.size(safe)<512)
            throw new IOException("Archivo PSP no disponible, vacío o vínculo no permitido.");
        return safe;
    }

    public static Path validEmulator(Path executable) throws IOException {
        if(executable==null || executable.getFileName()==null)
            throw new IOException("Selecciona PPSSPPWindows64.exe desde su sitio oficial.");
        Path safe=executable.toAbsolutePath().normalize();
        String name=safe.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.equals("ppssppwindows64.exe") && !name.equals("ppssppwindows.exe")
                && !name.equals("ppssppwindowsarm64.exe"))
            throw new IOException("El ejecutable debe llamarse PPSSPPWindows64.exe "
                    +"o la variante Windows oficial de PPSSPP.");
        if(!Files.isRegularFile(safe,LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(safe))
            throw new IOException("Emulador no encontrado o acceso inseguro.");
        return safe;
    }

    /** PPSSPP's documented CLI accepts a game path as the final argument. */
    public static List<String> command(Path emulator,Path game) throws IOException {
        Path exe=validEmulator(emulator);
        Path rom=validImage(game);
        return List.of(exe.toString(),"--windowed",rom.toString());
    }

    public static Process start(Path emulator,Path game) throws IOException {
        List<String> args=command(emulator,game);
        return new ProcessBuilder(args)
                .directory(Path.of(args.get(0)).getParent().toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    /** Player-owned ROMs and saves are never copied, removed or rewritten. */
    public static final class Library {
        private final Path file;
        private Path emulator;
        private final ArrayList<Path> games=new ArrayList<>();

        public Library(Path file) throws IOException {
            if(file==null)throw new IOException("Ruta de preferencias PSP inválida.");
            this.file=file.toAbsolutePath().normalize();
            read();
        }
        public List<Path> games() { return List.copyOf(games); }
        public Path emulator() { return emulator; }

        public void emulator(Path value) throws IOException {
            emulator=validEmulator(value);
            write();
        }

        public boolean add(Path value) throws IOException {
            Path safe=validImage(value);
            if(games.contains(safe))return false;
            if(games.size()>=500)throw new IOException("Máximo 500 juegos PSP.");
            games.add(safe);
            write();
            return true;
        }

        public boolean remove(Path value) throws IOException {
            if(!games.remove(value))return false;
            write();
            return true;
        }

        private void read() throws IOException {
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return;
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file)>1024*1024)
                throw new IOException("Preferencias PSP inválidas.");
            Properties props=new Properties();
            try(InputStream in=Files.newInputStream(file)){props.load(in);}
            String selected=props.getProperty("ppsspp","");
            if(!selected.isBlank()) {
                try { emulator=validEmulator(Path.of(selected)); }
                catch (Exception ignored) { emulator=null; }
            }
            int count;
            try { count=Math.min(500,Math.max(0,Integer.parseInt(
                    props.getProperty("count","0")))); }
            catch(NumberFormatException error) {count=0;}
            for(int i=0;i<count;i++) {
                try {
                    Path game=validImage(Path.of(props.getProperty("game."+i,"")));
                    if(!games.contains(game))games.add(game);
                } catch(Exception ignored) {
                    // A missing card/file doesn't erase older library metadata.
                }
            }
        }

        private void write() throws IOException {
            Path folder=file.getParent();
            if(folder==null || Files.isSymbolicLink(folder))
                throw new IOException("Carpeta de preferencias PSP inválida.");
            Files.createDirectories(folder);
            if(Files.isSymbolicLink(file))
                throw new IOException("Enlace de preferencias inseguro.");
            Properties props=new Properties();
            props.setProperty("format.version","1");
            props.setProperty("ppsspp",emulator==null?"":emulator.toString());
            props.setProperty("count",Integer.toString(games.size()));
            for(int i=0;i<games.size();i++)
                props.setProperty("game."+i,games.get(i).toString());
            Path temp=Files.createTempFile(folder,"psp-library-",".tmp");
            try {
                try(OutputStream out=Files.newOutputStream(temp)) {
                    props.store(out,"OKUBI PSP library references only - no ROM data");
                }
                try {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException noAtomic) {
                    Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {Files.deleteIfExists(temp);}
        }
    }
}
