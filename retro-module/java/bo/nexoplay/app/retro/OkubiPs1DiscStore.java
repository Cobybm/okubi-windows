package bo.nexoplay.app.retro;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports only documents explicitly selected via Android SAF, including
 * multi-file CUE/BIN backups. The core needs real local paths: Android's
 * content:// URIs are not usable as CD-ROM paths by an unmodified Libretro core.
 *
 * All imported images live in a NEW app-private folder, never beside saves.
 * Cancellation or bad tracks remove ONLY the new temporary folder. Never
 * delete user-provided documents, previously imported games, BIOS or saves.
 */
public final class OkubiPs1DiscStore {
    private static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 64;
    private static final Pattern CUE_FILE =
            Pattern.compile("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|([^\\s]+))");
    private static final Set<String> EXTENSIONS = Set.of(
            ".cue",".bin",".chd",".pbp",".iso",".img",".ccd",".m3u",".toc",".mdf");
    private OkubiPs1DiscStore() {}

    public interface Progress {
        boolean cancelled();
        void onProgress(int filesDone, int totalFiles, long bytesCopied);
    }
    public static final class ImportResult {
        public final Path disc;
        public final Path folder;
        public final String sha256;
        public ImportResult(Path disc, Path folder, String sha256) {
            this.disc=disc; this.folder=folder; this.sha256=sha256;
        }
    }
    private static final class Selected {
        final Uri uri;
        final String name;
        final long size;
        Selected(Uri uri, String name, long size) {
            this.uri=uri;this.name=name;this.size=size;
        }
    }

    public static Path importsRoot(Context context) {
        return context.getFilesDir().toPath().resolve("ps1-titles");
    }

    private static String extension(String name) {
        int dot=name.lastIndexOf('.');
        return dot>=0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private static String requireName(String name) throws IOException {
        if(name==null || name.isBlank() || name.length()>200
                || name.contains("/") || name.contains("\\") || name.contains(":")
                || name.contains("..") || name.indexOf(0)>=0 || name.startsWith("."))
            throw new IOException("Nombre de archivo PS1 inválido.");
        if(!EXTENSIONS.contains(extension(name)))
            throw new IOException("No es una imagen PS1 compatible: "+name);
        return name;
    }

    private static Selected describe(ContentResolver resolver, Uri uri)
            throws IOException {
        if(uri==null || !"content".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Selecciona archivos mediante el selector de Android.");
        String name=null;
        long size=-1;
        try (Cursor c=resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},
                null,null,null)) {
            if(c!=null && c.moveToFirst()) {
                name=c.getString(0);
                if(!c.isNull(1))size=c.getLong(1);
            }
        }catch(SecurityException error) {
            throw new IOException("Android no autorizó leer este documento.",error);
        }
        return new Selected(uri,requireName(name),size);
    }

    public static ImportResult stage(Context context,List<Uri> requested,
                                     Progress progress) throws IOException {
        if(requested==null || requested.isEmpty() || requested.size()>MAX_FILES)
            throw new IOException("Selecciona entre 1 y 64 archivos del juego PS1.");
        ContentResolver resolver=context.getContentResolver();
        List<Selected> files=new ArrayList<>();
        Set<String> names=new HashSet<>();
        long total=0;
        for(Uri uri:requested) {
            Selected found=describe(resolver,uri);
            if(!names.add(found.name.toLowerCase(Locale.ROOT)))
                throw new IOException("El juego contiene nombres repetidos: "+found.name);
            if(found.size>MAX_BYTES)
                throw new IOException("El disco PS1 supera el límite de 4 GiB.");
            if(found.size>0) {
                total+=found.size;
                if(total>MAX_BYTES)
                    throw new IOException("El conjunto de discos supera 4 GiB.");
            }
            files.add(found);
        }
        StatFs disk=new StatFs(context.getFilesDir().getAbsolutePath());
        if(total>0 && total + (64L*1024*1024)>disk.getAvailableBytes())
            throw new IOException("No hay espacio suficiente para importar PS1.");
        // Prefer a multi-disc descriptor, then CUE+tracks, then CHD/PBP.
        String gameName=null;
        for(String type:new String[]{".m3u",".cue",".chd",".pbp",".ccd",
                ".toc",".iso",".img",".bin",".mdf"}) {
            for(Selected file:files) {
                if(extension(file.name).equals(type)) {
                    gameName=file.name;break;
                }
            }
            if(gameName!=null)break;
        }
        if(gameName==null)
            throw new IOException("No elegiste ningún disco PS1.");
        Path root=importsRoot(context);
        if(Files.isSymbolicLink(root))
            throw new IOException("La carpeta privada de juegos no es segura.");
        Files.createDirectories(root);
        Path temporary=root.resolve(".pending-"+UUID.randomUUID());
        Files.createDirectory(temporary);
        boolean published=false;
        String selectedHash=null;
        long copied=0;
        try {
            int done=0;
            for(Selected file:files) {
                if(progress.cancelled())throw new IOException("Importación cancelada.");
                Path dest=temporary.resolve(file.name);
                MessageDigest checksum=sha256();
                try(InputStream in=resolver.openInputStream(file.uri);
                    OutputStream out=Files.newOutputStream(dest)) {
                    if(in==null)throw new IOException("Documento inaccesible: "+file.name);
                    byte[] buf=new byte[128*1024];
                    int n;
                    while((n=in.read(buf))!=-1) {
                        if(progress.cancelled())throw new IOException("Importación cancelada.");
                        if(copied+n>MAX_BYTES)
                            throw new IOException("Discos PS1 demasiado grandes.");
                        out.write(buf,0,n);
                        checksum.update(buf,0,n);
                        copied+=n;
                        progress.onProgress(done,files.size(),copied);
                    }
                }
                if(Files.size(dest)==0)
                    throw new IOException("Archivo vacío: "+file.name);
                if(file.name.equals(gameName))
                    selectedHash=hex(checksum.digest());
                done++;
                progress.onProgress(done,files.size(),copied);
            }
            validate(temporary.resolve(gameName),new HashSet<>(),0);
            if(selectedHash==null)
                throw new IOException("No se verificó la imagen principal.");
            Path destination=root.resolve(UUID.randomUUID().toString());
            try {
                Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
            }catch(java.nio.file.AtomicMoveNotSupportedException e){
                Files.move(temporary,destination);
            }
            published=true;
            return new ImportResult(destination.resolve(gameName),destination,
                    selectedHash);
        }finally {
            if(!published)deleteOnlyTemporary(temporary);
        }
    }

    private static void validate(Path path,Set<Path> stack,int depth)
            throws IOException {
        if(depth>5 || !stack.add(path))
            throw new IOException("Ciclo o exceso de niveles en lista de discos.");
        try {
            String ext=extension(path.getFileName().toString());
            if(!".cue".equals(ext) && !".m3u".equals(ext))return;
            if(Files.size(path)>256*1024)
                throw new IOException("Descriptor PS1 demasiado grande.");
            String data=Files.readString(path,
                    ".cue".equals(ext)?StandardCharsets.ISO_8859_1:StandardCharsets.UTF_8);
            List<String> refs=new ArrayList<>();
            if(".cue".equals(ext)) {
                Matcher matches=CUE_FILE.matcher(data);
                while(matches.find()) {
                    if(refs.size()>=MAX_FILES)
                        throw new IOException("Demasiadas pistas en CUE.");
                    refs.add(matches.group(1)!=null?matches.group(1):matches.group(2));
                }
            }else {
                for(String line:data.replace("\uFEFF","").split("\\R")){
                    String value=line.trim();
                    if(!value.isEmpty() && !value.startsWith("#")) {
                        if(refs.size()>=MAX_FILES)
                            throw new IOException("Demasiados discos en M3U.");
                        refs.add(value);
                    }
                }
            }
            if(refs.isEmpty())throw new IOException("Descriptor PS1 sin pistas.");
            for(String ref:refs) {
                String name=requireName(ref);
                Path member=path.getParent().resolve(name);
                if(!Files.isRegularFile(member,LinkOption.NOFOLLOW_LINKS)
                        || Files.size(member)==0)
                    throw new IOException("Falta seleccionar junto al CUE/M3U: "+name);
                if(".m3u".equals(ext))
                    validate(member,stack,depth+1);
            }
        }finally{stack.remove(path);}
    }

    private static String hex(byte[] bytes) {
        char[] digits="0123456789abcdef".toCharArray();
        char[] output=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++) {
            output[i*2]=digits[(bytes[i]>>>4)&15];
            output[i*2+1]=digits[bytes[i]&15];
        }
        return new String(output);
    }

    private static MessageDigest sha256() throws IOException {
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }

    private static void deleteOnlyTemporary(Path temporary) {
        try {
            if(!temporary.getFileName().toString().startsWith(".pending-"))
                return;
            if(!Files.isDirectory(temporary,LinkOption.NOFOLLOW_LINKS))
                return;
            try(var all=Files.walk(temporary)){
                all.sorted(java.util.Comparator.reverseOrder()).forEach(file->{
                    try{if(!Files.isSymbolicLink(file))Files.deleteIfExists(file);}
                    catch(IOException ignored){}
                });
            }
        }catch(IOException ignored){}
    }
}
