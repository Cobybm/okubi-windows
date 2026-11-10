package bo.nexoplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Player-controlled, offline migration of safe settings and hash-keyed
 * emulator saves. Does not export ROMs, covers, SAF URI capabilities or APKs.
 * Import is validated and staged before writing anything to live storage.
 * Existing files / preference keys are never overwritten.
 */
public final class OkubiUnifiedBackup {
    private static final String[] PREF_FILES={
            "nexo_visual_08","okubi_audio_v1","okubi_performance_v1",
            "okubi_visual_v1","okubi_screen_fit_v1","okubi_overlay_opacity_v1",
            "okubi_retro_slots_v1",
            "okubi_retro_controls_v1","nexo_play","okubi_unified_library_v1",
            "bo.nexoplay.app.MainActivity","MainActivity"};
    private static final int BUFFER = 16384;
    private OkubiUnifiedBackup() {}

    public static final class Outcome {
        public final int saves, skippedSaves, preferences;
        Outcome(int a,int b,int c){saves=a;skippedSaves=b;preferences=c;}
        public String describe() {
            return "Recuperados: "+saves+" archivos de partida y "+preferences+
                    " preferencias; "+skippedSaves+" archivos existentes respetados.";
        }
    }
    private static String sha256(byte[] bytes) {
        return hex(digest(bytes));
    }
    private static MessageDigest digest() {
        try {return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static byte[] digest(byte[] bytes) {
        return digest().digest(bytes);
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes)out.append(Character.forDigit((b>>>4)&15,16))
                .append(Character.forDigit(b&15,16));
        return out.toString();
    }
    private static void addToZip(ZipOutputStream zip, String name, InputStream source,
            JSONArray index) throws Exception {
        if (!OkubiMigrationRules.isAllowedEntry(name))
            throw new IOException("Nombre de respaldo no permitido");
        zip.putNextEntry(new ZipEntry(name));
        MessageDigest sha=digest();
        long size=0,limit=OkubiMigrationRules.maximumFor(name);
        byte[] buffer=new byte[BUFFER];
        int amount;
        while((amount=source.read(buffer))!=-1) {
            size+=amount;
            if(size>limit)throw new IOException("Archivo demasiado grande: "+name);
            sha.update(buffer,0,amount);
            zip.write(buffer,0,amount);
        }
        zip.closeEntry();
        if(!"manifest.json".equals(name)){
            JSONObject record=new JSONObject();
            record.put("path",name);
            record.put("size",size);
            record.put("sha256",hex(sha.digest()));
            index.put(record);
        }
    }
    private static boolean allowedString(String value) {
        return value!=null && value.length()<=65536
                && !value.startsWith("content://") && !value.startsWith("file://");
    }
    private static JSONObject encodePreferences(Context context) throws Exception {
        JSONObject result=new JSONObject();
        for(String name:PREF_FILES) {
            Map<String,?> values=context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll();
            JSONObject selected=new JSONObject();
            for(Map.Entry<String,?> row:values.entrySet()){
                String key=row.getKey();
                if(!OkubiMigrationRules.allowPreference(name,key))continue;
                Object raw=row.getValue();
                if(raw==null)continue;
                JSONObject item=new JSONObject();
                if(raw instanceof String){
                    if(!allowedString((String)raw))continue;
                    item.put("type","string");item.put("value",raw);
                } else if(raw instanceof Boolean){
                    item.put("type","boolean");item.put("value",raw);
                } else if(raw instanceof Integer){
                    item.put("type","integer");item.put("value",raw);
                } else if(raw instanceof Long){
                    item.put("type","long");item.put("value",raw);
                } else if(raw instanceof Float){
                    if(!Float.isFinite((Float)raw))continue;
                    item.put("type","float");item.put("value",raw);
                } else if(raw instanceof Set) {
                    JSONArray list=new JSONArray();
                    boolean valid=true;
                    for(Object value:(Set<?>)raw){
                        if(!(value instanceof String)||!allowedString((String)value)
                                ||((String)value).length()>256 ||list.length()>=300){
                            valid=false;break;
                        }
                        list.put(value);
                    }
                    if(!valid)continue;
                    item.put("type","set");item.put("value",list);
                } else continue;
                selected.put(key,item);
            }
            if(selected.length()>0)result.put(name,selected);
        }
        return result;
    }

    /** Uses Android's CREATE_DOCUMENT result stream; never stores a backup in Drive. */
    public static Outcome exportTo(Context context, OutputStream output) throws Exception {
        if(output==null)throw new IOException("No se pudo escribir el archivo");
        JSONArray index=new JSONArray();
        int saves=0;
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            byte[] prefs=encodePreferences(context).toString().getBytes(StandardCharsets.UTF_8);
            try(InputStream input=new java.io.ByteArrayInputStream(prefs)){
                addToZip(zip,"preferences.json",input,index);
            }
            for(String folder:new String[]{"gameboy","gba","snes"}){
                File directory=new File(context.getFilesDir(),"okubi/"+folder);
                File[] files=directory.listFiles();
                if(files==null)continue;
                Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
                for(File file:files){
                    String name="saves/"+folder+"/"+file.getName();
                    if(!file.isFile()||!OkubiMigrationRules.isSave(name))continue;
                    if(file.length()<=0||file.length()>OkubiMigrationRules.maximumFor(name))continue;
                    if(saves>=OkubiMigrationRules.MAX_ENTRIES-2)
                        throw new IOException("Demasiados archivos para un respaldo");
                    try(FileInputStream input=new FileInputStream(file)){
                        addToZip(zip,name,input,index);
                    }
                    saves++;
                }
            }
            JSONObject manifest=new JSONObject();
            manifest.put("format",OkubiMigrationRules.FORMAT);
            manifest.put("version",OkubiMigrationRules.VERSION);
            manifest.put("source",context.getPackageName());
            manifest.put("files",index);
            byte[] metadata=manifest.toString().getBytes(StandardCharsets.UTF_8);
            try(InputStream input=new java.io.ByteArrayInputStream(metadata)){
                addToZip(zip,"manifest.json",input,new JSONArray());
            }
            zip.finish();
        }
        return new Outcome(saves,0,encodePreferences(context).length());
    }

    private static void clean(File target) {
        if(target==null||!target.exists())return;
        if(target.isDirectory()){
            File[] children=target.listFiles();
            if(children!=null)for(File child:children)clean(child);
        }
        if(!target.delete()) { /* temp stays inside private cache */ }
    }

    /**
     * Android Activity.getPreferences uses getLocalClassName(). The original
     * bo.nexoplay.app package stores MainActivity.xml, but the QA/unified APKs
     * use bo.nexoplay.app.MainActivity.xml because the activity class is no
     * longer inside the applicationId package.
     */
    private static String effectivePreferenceFile(String file) {
        return "MainActivity".equals(file)
                ? "bo.nexoplay.app.MainActivity" : file;
    }

    private static Map<String,Map<String,Object>> decodePreferences(JSONObject json)
            throws Exception {
        Map<String,Map<String,Object>> collected=new LinkedHashMap<>();
        for(String file:PREF_FILES) {
            JSONObject prefs=json.optJSONObject(file);
            if(prefs==null)continue;
            if(prefs.length()>350)throw new IOException("Demasiadas preferencias");
            Map<String,Object> items=new LinkedHashMap<>();
            java.util.Iterator<String> keys=prefs.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(!OkubiMigrationRules.allowPreference(file,key))continue;
                JSONObject record=prefs.optJSONObject(key);
                if(record==null)continue;
                String type=record.optString("type","");
                Object value=null;
                switch(type){
                    case "string":
                        String s=record.optString("value","");
                        if(allowedString(s))value=s;
                        break;
                    case "boolean": if(record.opt("value") instanceof Boolean)
                        value=record.getBoolean("value");
                        break;
                    case "integer": if(record.opt("value") instanceof Number)
                        value=record.getInt("value");
                        break;
                    case "long": if(record.opt("value") instanceof Number)
                        value=record.getLong("value");
                        break;
                    case "float": if(record.opt("value") instanceof Number) {
                        float f=(float)record.getDouble("value");
                        if(Float.isFinite(f))value=f;
                    } break;
                    case "set":
                        JSONArray a=record.optJSONArray("value");
                        if(a!=null && a.length()<=300){
                            Set<String> set=new HashSet<>();
                            boolean good=true;
                            for(int i=0;i<a.length();i++){
                                String member=a.optString(i,null);
                                if(member==null||member.length()>256||!allowedString(member)){
                                    good=false;break;
                                }
                                set.add(member);
                            }
                            if(good)value=set;
                        } break;
                    default: break;
                }
                if(value!=null)items.put(key,value);
            }
            if(!items.isEmpty()) {
                String targetFile=effectivePreferenceFile(file);
                Map<String,Object> previous=collected.get(targetFile);
                if(previous==null)collected.put(targetFile,items);
                else for(Map.Entry<String,Object> item:items.entrySet())
                    previous.putIfAbsent(item.getKey(),item.getValue());
            }
        }
        return collected;
    }

    private static void writePreference(SharedPreferences.Editor editor,
            String key,Object value) {
        if(value==null)editor.remove(key);
        else if(value instanceof String)editor.putString(key,(String)value);
        else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
        else if(value instanceof Integer)editor.putInt(key,(Integer)value);
        else if(value instanceof Long)editor.putLong(key,(Long)value);
        else if(value instanceof Float)editor.putFloat(key,(Float)value);
        else if(value instanceof Set) {
            @SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;
            editor.putStringSet(key,new HashSet<>(set));
        }
    }
    private static boolean additiveSet(String file,String key) {
        return "nexo_play".equals(file) &&
                ("selected_components".equals(key)
                || "favorite_components".equals(key)
                || "okubi_hidden_components".equals(key));
    }

    private static int applyPreferences(Context c,Map<String,Map<String,Object>> selected)
            throws IOException {
        int imported=0;
        // Roll back any preferences created/merged earlier in this import if
        // a later SharedPreferences.commit() fails. Existing values survive.
        Map<String,Map<String,Object>> before=new LinkedHashMap<>();
        try {
            for(Map.Entry<String,Map<String,Object>> group:selected.entrySet()){
                String file=group.getKey();
                SharedPreferences prefs=c.getSharedPreferences(file,Context.MODE_PRIVATE);
                SharedPreferences.Editor editor=prefs.edit();
                Map<String,Object> rollback=new LinkedHashMap<>();
                for(Map.Entry<String,Object> item:group.getValue().entrySet()){
                    String key=item.getKey();
                    Object value=item.getValue();
                    if(prefs.contains(key)) {
                        if(!additiveSet(file,key) || !(value instanceof Set))continue;
                        @SuppressWarnings("unchecked") Set<String> incoming=(Set<String>)value;
                        Set<String> existing=prefs.getStringSet(key,java.util.Collections.emptySet());
                        Set<String> merged=new HashSet<>(existing);
                        if(!merged.addAll(incoming))continue;
                        rollback.put(key,new HashSet<>(existing));
                        writePreference(editor,key,merged);
                    } else {
                        rollback.put(key,null);
                        writePreference(editor,key,value);
                    }
                }
                if(!rollback.isEmpty()){
                    // Record before the commit: a reported failure may still
                    // partially reach disk; restoration must be attempted.
                    before.put(file,rollback);
                    if(!editor.commit())
                        throw new IOException("No se pudieron guardar las preferencias");
                    imported+=rollback.size();
                }
            }
            return imported;
        }catch(Exception error){
            for(Map.Entry<String,Map<String,Object>> group:before.entrySet()){
                SharedPreferences.Editor restore=c.getSharedPreferences(
                        group.getKey(),Context.MODE_PRIVATE).edit();
                for(Map.Entry<String,Object> entry:group.getValue().entrySet())
                    writePreference(restore,entry.getKey(),entry.getValue());
                restore.commit();
            }
            if(error instanceof IOException)throw (IOException)error;
            throw new IOException("Error de restauración de preferencias",error);
        }
    }

    private static final class Verified {
        final Map<String,File> files;
        final Map<String,String> hashes;
        final Map<String,Long> lengths;
        final Map<String,Map<String,Object>> settings;
        final String source;
        Verified(Map<String,File> files,Map<String,String> hashes,
                 Map<String,Long> lengths,Map<String,Map<String,Object>> settings,
                 String source) {
            this.files=files;
            this.hashes=hashes;
            this.lengths=lengths;
            this.settings=settings;
            this.source=source;
        }
    }

    /** Description of a checksum-verified backup, without altering live data. */
    public static final class Preview {
        public final String source;
        public final int newSaves, existingSaves, settings;
        Preview(String source,int newSaves,int existingSaves,int settings) {
            this.source=source;
            this.newSaves=newSaves;
            this.existingSaves=existingSaves;
            this.settings=settings;
        }
        public String description() {
            return "Origen: "+source+"\nPartidas nuevas: "+newSaves+
                    "\nPartidas existentes (sin reemplazar): "+existingSaves+
                    "\nPreferencias compatibles por incorporar: "+settings+
                    "\n\nSe verificó la integridad del respaldo. "+
                    "La aplicación volverá a comprobarla antes de importar.";
        }
    }

    private static File newStaging(Context context) throws IOException {
        File staging=new File(context.getCacheDir(),
                "okubi-stage-"+System.currentTimeMillis()+"-"+Thread.currentThread().getId());
        if(!staging.mkdirs())throw new IOException("No se pudo reservar espacio temporal");
        return staging;
    }

    /** Shared validator: ZIP entries, SHA-256, bounds and manifest. */
    private static Verified validateArchive(InputStream stream,File staging)
            throws Exception {
        if(stream==null)throw new IOException("No se pudo leer el respaldo");
            Map<String,File> staged=new LinkedHashMap<>();
            Map<String,String> hashes=new HashMap<>();
            Map<String,Long> lengths=new HashMap<>();
            long sum=0;
            try(ZipInputStream zip=new ZipInputStream(stream)){
                ZipEntry entry;
                byte[] buffer=new byte[BUFFER];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();
                    if(entry.isDirectory() || !OkubiMigrationRules.isAllowedEntry(name)
                            || staged.containsKey(name)
                            || staged.size()>=OkubiMigrationRules.MAX_ENTRIES)
                        throw new IOException("Archivo no permitido o repetido: "+name);
                    File temp=new File(staging,"item-"+staged.size());
                    MessageDigest sha=digest();
                    long size=0,limit=OkubiMigrationRules.maximumFor(name);
                    try(FileOutputStream out=new FileOutputStream(temp)){
                        int count;
                        while((count=zip.read(buffer))!=-1){
                            size+=count;sum+=count;
                            if(size>limit||sum>OkubiMigrationRules.MAX_TOTAL)
                                throw new IOException("Respaldo excede límites seguros");
                            sha.update(buffer,0,count);out.write(buffer,0,count);
                        }
                        out.getFD().sync();
                    }
                    if(OkubiMigrationRules.isSave(name) && size==0)
                        throw new IOException("Partida vacía: "+name);
                    staged.put(name,temp);
                    hashes.put(name,hex(sha.digest()));
                    lengths.put(name,size);
                    zip.closeEntry();
                }
            }
            File manifestFile=staged.get("manifest.json");
            File prefsFile=staged.get("preferences.json");
            if(manifestFile==null||prefsFile==null)
                throw new IOException("Falta el índice de respaldo o preferencias");
            JSONObject manifest=new JSONObject(readSmall(manifestFile,
                    OkubiMigrationRules.MAX_MANIFEST));
            if(!OkubiMigrationRules.FORMAT.equals(manifest.optString("format"))
                    ||manifest.optInt("version")!=OkubiMigrationRules.VERSION)
                throw new IOException("Versión de respaldo no reconocida");
            JSONArray records=manifest.optJSONArray("files");
            if(records==null||records.length()!=staged.size()-1)
                throw new IOException("Índice incompleto o manipulado");
            Set<String> counted=new HashSet<>();
            for(int i=0;i<records.length();i++){
                JSONObject record=records.optJSONObject(i);
                if(record==null)throw new IOException("Índice corrupto");
                String name=record.optString("path","");
                if("manifest.json".equals(name)||!counted.add(name)
                        ||!staged.containsKey(name)
                        ||lengths.get(name)!=record.optLong("size",-1)
                        ||!hashes.get(name).equals(record.optString("sha256")))
                    throw new IOException("Integridad incorrecta: "+name);
            }
            JSONObject settings=new JSONObject(readSmall(prefsFile,
                    OkubiMigrationRules.MAX_PREFS));
            Map<String,Map<String,Object>> imported=decodePreferences(settings);
            String source=manifest.optString("source","");
            if(source.length()>96 || !source.matches("bo\\.nexoplay\\.app(\\.[a-z]+)?"))
                source="origen desconocido";
            return new Verified(staged,hashes,lengths,imported,source);

    }

    private static int pendingPreferences(Context context,
            Map<String,Map<String,Object>> settings) {
        int count=0;
        for(Map.Entry<String,Map<String,Object>> group:settings.entrySet()){
            String file=group.getKey();
            SharedPreferences prefs=context.getSharedPreferences(file,Context.MODE_PRIVATE);
            for(Map.Entry<String,Object> item:group.getValue().entrySet()){
                if(!prefs.contains(item.getKey())) {count++;continue;}
                if(additiveSet(file,item.getKey())&&item.getValue() instanceof Set){
                    @SuppressWarnings("unchecked")
                    Set<String> incoming=(Set<String>)item.getValue();
                    Set<String> existing=prefs.getStringSet(item.getKey(),
                            java.util.Collections.emptySet());
                    if(!existing.containsAll(incoming))count++;
                }
            }
        }
        return count;
    }

    /**
     * Only stages and validates ZIP entries. NO live save writes or preference
     * modifications occur when the player previews a backup.
     */
    public static Preview inspect(Context context,InputStream stream) throws Exception {
        File staging=newStaging(context);
        try {
            Verified verified=validateArchive(stream,staging);
            int newly=0,existing=0;
            for(String name:verified.files.keySet()){
                if(!OkubiMigrationRules.isSave(name))continue;
                File destination=new File(context.getFilesDir(),
                        "okubi/"+name.substring("saves/".length()));
                if(destination.exists())existing++; else newly++;
            }
            return new Preview(verified.source,newly,existing,
                    pendingPreferences(context,verified.settings));
        }finally{clean(staging);}
    }

    /** Validate the entire ZIP + SHA hashes before touching any live file. */
    public static Outcome importFrom(Context context,InputStream stream) throws Exception {
        File staging=newStaging(context);
        List<File> committed=new ArrayList<>();
        try {
            Verified verified=validateArchive(stream,staging);
            Map<String,File> staged=verified.files;
            Map<String,Map<String,Object>> imported=verified.settings;
            int saved=0,skipped=0;
            for(String entry:staged.keySet()){
                if(!OkubiMigrationRules.isSave(entry))continue;
                String relative=entry.substring("saves/".length());
                File target=new File(context.getFilesDir(),"okubi/"+relative);
                if(target.exists()){skipped++;continue;}
                File parent=target.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())
                    throw new IOException("No se pudo crear carpeta de partidas");
                AtomicFile atomic=new AtomicFile(target);
                FileOutputStream output=null;
                try(InputStream input=new FileInputStream(staged.get(entry))){
                    output=atomic.startWrite();
                    byte[] buffer=new byte[BUFFER];
                    int n;
                    while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
                    atomic.finishWrite(output);output=null;
                    // Register before readback so failures roll back this new file.
                    committed.add(target);
                    if(!OkubiArchiveVerifier.matches(target,
                            verified.lengths.get(entry),verified.hashes.get(entry)))
                        throw new IOException("No se pudo verificar partida: "+entry);
                    saved++;
                }catch(Exception error){
                    if(output!=null)atomic.failWrite(output);
                    throw error;
                }
            }
            // Imported preferences never override a setting already chosen in
            // the canonical app. Save transfer is non-destructive as well.
            int preferences=applyPreferences(context,imported);
            return new Outcome(saved,skipped,preferences);
        }catch(Exception error){
            // Roll back only files created by this import. Existing files untouched.
            for(File created:committed) {
                new AtomicFile(created).delete();
            }
            throw error;
        }finally{
            clean(staging);
        }
    }
    private static String readSmall(File file,int maximum) throws IOException {
        if(file.length()<0||file.length()>maximum)
            throw new IOException("Archivo JSON demasiado grande");
        try(FileInputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[(int)file.length()];
            int done=0,n;
            while(done<buffer.length&&(n=in.read(buffer,done,buffer.length-done))>0)
                done+=n;
            if(done!=buffer.length)throw new IOException("Lectura incompleta");
            return new String(buffer,StandardCharsets.UTF_8);
        }
    }
}
