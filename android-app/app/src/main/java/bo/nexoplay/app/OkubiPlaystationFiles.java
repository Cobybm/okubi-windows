package bo.nexoplay.app;

import java.util.Locale;

/**
 * Read-only Android document format advisor. Its result is diagnostic only:
 * there is NO installed PS1/PS2/PSP/Vita core implied and no automatic ROM
 * copying, BIOS download or attempts to execute arbitrary external APKs.
 */
public final class OkubiPlaystationFiles {
    public static final class Advice {
        public final String type, platform, engine, message;
        public final boolean needsManualChoice, needsInstallation;
        Advice(String t, String p, String e, String m,
               boolean choose, boolean install) {
            type=t; platform=p; engine=e; message=m;
            needsManualChoice=choose; needsInstallation=install;
        }
        public String summary() {
            return "Archivo: "+type+"\nConsola: "+platform
                    +"\nEmulador: "+engine+"\n\n"+message
                    +(needsManualChoice?"\n\nLa extensión no indica una consola única.":"")
                    +(needsInstallation?"\n\nEste paquete se instala desde el emulador correspondiente.":"");
        }
    }
    private OkubiPlaystationFiles() { }

    public static Advice describe(String filename) {
        if (filename==null || filename.trim().isEmpty()
                || filename.contains("/") || filename.contains("\\")
                || filename.indexOf(0)>=0) {
            return new Advice("Desconocido","Indeterminado","Ninguno",
                    "No se puede comprobar el nombre de este documento.",false,false);
        }
        String n=filename.toLowerCase(Locale.ROOT);
        if(n.equals("eboot.bin"))
            return new Advice("EBOOT.BIN","PS3 / PSP según contenido",
                    "RPCS3 / PPSSPP",
                    "PS3 suele usar PS3_GAME/USRDIR/EBOOT.BIN; PSP puede usar "
                    +"PSP/GAME/.../EBOOT.PBP. No confundas los dos formatos.",true,false);
        if(n.endsWith(".cue"))
            return new Advice("CUE + pistas","PS1 / PS2 CD",
                    "DuckStation / ARMSX2 o Play!",
                    "Conserva todos los BIN/WAV referenciados por el CUE en la misma carpeta; "
                    +"una URI aislada de Android podría no dar acceso a las pistas.",true,false);
        if(n.endsWith(".bin")||n.endsWith(".img"))
            return new Advice("BIN / IMG","PS1 / PS2 CD",
                    "DuckStation / ARMSX2",
                    "En PS1 es preferible seleccionar el archivo CUE cuando exista.",true,false);
        if(n.endsWith(".ccd")||n.endsWith(".mds")||n.endsWith(".ecm")
                ||n.endsWith(".m3u"))
            return new Advice("Imagen PS1 / lista","PS1","DuckStation",
                    "Las pistas, descriptores y discos adicionales deben estar disponibles.",false,false);
        if(n.endsWith(".chd"))
            return new Advice("CHD comprimido","PS1 / PS2 / PSP",
                    "DuckStation / ARMSX2 / PPSSPP",
                    "El archivo puede ejecutarse directamente sin extraerlo "
                    +"cuando el motor compatible está instalado.",true,false);
        if(n.endsWith(".iso"))
            return new Advice("Imagen ISO","PS1 / PS2 / PSP / PS3",
                    "DuckStation / ARMSX2 / PPSSPP / RPCS3",
                    "No se puede identificar el sistema solo con el sufijo. "
                    +"Las imágenes PS3 podrían necesitar descifrado/importación.",true,false);
        if(n.endsWith(".cso"))
            return new Advice("CSO comprimido","PS2 / PSP",
                    "ARMSX2 / PPSSPP",
                    "La selección depende de cuál consola se copió.",true,false);
        if(n.endsWith(".zso")||n.endsWith(".isz"))
            return new Advice("Imagen PS2 comprimida","PS2","ARMSX2 / Play!",
                    "Verifica el formato exacto aceptado por la versión del motor.",false,false);
        if(n.endsWith(".pbp"))
            return new Advice("PBP / EBOOT.PBP","PS1 / PSP",
                    "DuckStation / PPSSPP",
                    "Puede ser una versión PS1 en PBP o un EBOOT de PSP.",true,false);
        if(n.endsWith(".pkg"))
            return new Advice("Paquete PKG","PS3 / PS Vita","RPCS3 / Vita3K",
                    "Puede requerir la licencia legítima asociada al juego y "
                    +"software de sistema oficial.",true,true);
        if(n.endsWith(".vpk")||n.endsWith(".vci"))
            return new Advice("Paquete Vita","PS Vita","Vita3K",
                    "Se instala desde Vita3K; no es una ISO que pueda abrirse directamente.",
                    false,true);
        if(n.endsWith(".zip")||n.endsWith(".7z")||n.endsWith(".rar"))
            return new Advice("Archivo comprimido","Indeterminado",
                    "Según los archivos interiores",
                    "OKUBI no extrae automáticamente ZIP/7Z/RAR por seguridad. "
                    +"Vita3K admite ZIP compatibles desde su instalador.",true,true);
        if(n.endsWith(".sbi"))
            return new Advice("Datos SBI","PS1","DuckStation",
                    "No es un juego: debe acompañar a una imagen PS1 con el mismo nombre.",false,false);
        return new Advice("No reconocido","Indeterminado","Ninguno",
                "Puedes revisar el archivo original y su extensión sin cambiarle "
                +"el nombre para intentar forzar otro emulador.",false,false);
    }
}
