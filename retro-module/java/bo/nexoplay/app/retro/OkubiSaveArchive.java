package bo.nexoplay.app.retro;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Versioned, bounded OKUBI save backup. Pure Java so it can be tested without Android.
 *
 * File layout:
 *   magic(int), version(int), system(UTF), ROM SHA-256(UTF)
 *   [length(int), digest(32 bytes), payload] x 3, ordered SRAM / RTC / save state.
 * A length of -1 means absent data (no digest/payload). There are no filenames,
 * archive paths or ROM bytes, so path traversal and zip bombs are not possible.
 */
public final class OkubiSaveArchive {
    public static final String EXTENSION = ".okubisave";
    private static final int MAGIC = 0x4F4B4253; // "OKBS"
    private static final int VERSION = 1;
    public static final int MAX_RAM = 1024 * 1024;
    public static final int MAX_RTC = 65536;
    public static final int MAX_STATE = 16 * 1024 * 1024;

    private OkubiSaveArchive() {}

    public static final class Snapshot {
        public final String system;
        public final String romSha256;
        public final byte[] ram;
        public final byte[] rtc;
        public final byte[] state;

        public Snapshot(String system, String romSha256, byte[] ram, byte[] rtc,
                        byte[] state) throws IOException {
            if (!"GB".equals(system) && !"GBC".equals(system)
                    && !"GBA".equals(system) && !"SNES".equals(system)) {
                throw new IOException("Sistema retro no válido");
            }
            if (romSha256 == null || !romSha256.matches("[0-9a-f]{64}")) {
                throw new IOException("Identificador del cartucho no válido");
            }
            this.system = system;
            this.romSha256 = romSha256;
            this.ram = checkedCopy(ram, MAX_RAM, "SRAM");
            this.rtc = checkedCopy(rtc, MAX_RTC, "RTC");
            this.state = checkedCopy(state, MAX_STATE, "estado");
            if (this.ram == null && this.rtc == null && this.state == null) {
                throw new IOException("No hay datos de partida para respaldar");
            }
        }
    }

    private static byte[] checkedCopy(byte[] data, int limit, String label)
            throws IOException {
        if (data == null) return null;
        if (data.length == 0 || data.length > limit) {
            throw new IOException("Tamaño de " + label + " no permitido");
        }
        return data.clone();
    }

    private static byte[] checksum(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }

    private static void writePart(DataOutputStream out, byte[] payload)
            throws IOException {
        if (payload == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(payload.length);
        out.write(checksum(payload));
        out.write(payload);
    }

    /** Caller owns the stream, and must close it (especially for SAF providers). */
    public static void write(OutputStream target, Snapshot snapshot)
            throws IOException {
        if (target == null || snapshot == null) throw new IOException("Respaldo inválido");
        DataOutputStream out = new DataOutputStream(target);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeUTF(snapshot.system);
        out.writeUTF(snapshot.romSha256);
        writePart(out, snapshot.ram);
        writePart(out, snapshot.rtc);
        writePart(out, snapshot.state);
        out.flush();
    }

    private static byte[] readPart(DataInputStream in, int limit, String label)
            throws IOException {
        int size = in.readInt();
        if (size == -1) return null;
        if (size <= 0 || size > limit) throw new IOException("Tamaño de " + label + " inválido");
        byte[] expected = new byte[32];
        in.readFully(expected);
        byte[] data = new byte[size];
        in.readFully(data);
        if (!MessageDigest.isEqual(expected, checksum(data))) {
            throw new IOException("El respaldo está corrupto (" + label + ")");
        }
        return data;
    }

    /** Read with strict limits, verify every checksum, and reject trailing bytes. */
    public static Snapshot read(InputStream source, String expectedRomSha256,
                                String expectedSystem) throws IOException {
        if (source == null) throw new IOException("No se pudo abrir el respaldo");
        DataInputStream in = new DataInputStream(new BufferedInputStream(source));
        if (in.readInt() != MAGIC || in.readInt() != VERSION) {
            throw new IOException("Formato o versión de respaldo no compatible");
        }
        String system = in.readUTF();
        String romSha256 = in.readUTF();
        if (!system.equals(expectedSystem) || !romSha256.equals(expectedRomSha256)) {
            throw new IOException("El respaldo pertenece a otro juego o consola");
        }
        byte[] ram = readPart(in, MAX_RAM, "SRAM");
        byte[] rtc = readPart(in, MAX_RTC, "RTC");
        byte[] state = readPart(in, MAX_STATE, "estado");
        if (in.read() != -1) throw new IOException("El respaldo contiene datos extra");
        return new Snapshot(system, romSha256, ram, rtc, state);
    }

    public static boolean samePayload(Snapshot left, Snapshot right) {
        return left != null && right != null
                && left.system.equals(right.system)
                && left.romSha256.equals(right.romSha256)
                && Arrays.equals(left.ram, right.ram)
                && Arrays.equals(left.rtc, right.rtc)
                && Arrays.equals(left.state, right.state);
    }
}
