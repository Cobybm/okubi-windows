package bo.nexoplay.app.retro;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Applies PATCH/IPS and BPS1 patches to an OWNED, player-selected base ROM.
 * Original source and patch arrays are never modified. Source/target capped
 * at 32 MiB to avoid corrupted patch files exhausting low-memory phones.
 *
 * BPS specification by byuu, public domain (see Flips/bps_spec.md).
 */
public final class OkubiPatchEngine {
    public static final int MAX_OUTPUT = 32 * 1024 * 1024;
    public static final int MAX_PATCH = 16 * 1024 * 1024;
    private OkubiPatchEngine() {}

    public static byte[] apply(byte[] base, byte[] patch) throws IOException {
        if (base == null || patch == null || base.length < 32768
                || base.length > MAX_OUTPUT || patch.length < 8 || patch.length > MAX_PATCH)
            throw new IOException("ROM base o parche demasiado grande, vacío o incompleto");
        if (header(patch, "PATCH")) return ips(base, patch);
        if (header(patch, "BPS1")) return bps(base, patch);
        throw new IOException("Parche no compatible: OKUBI admite IPS y BPS; UPS necesita otro parcheador");
    }
    private static boolean header(byte[] bytes, String magic) {
        if (bytes.length < magic.length()) return false;
        for (int i = 0; i < magic.length(); i++)
            if (bytes[i] != (byte)magic.charAt(i)) return false;
        return true;
    }
    private static int u8(byte[] bytes, int pos) throws IOException {
        if (pos < 0 || pos >= bytes.length) throw new IOException("Parche truncado");
        return bytes[pos] & 255;
    }
    private static int be16(byte[] b, int p) throws IOException {
        return (u8(b,p) << 8) | u8(b,p+1);
    }
    private static int be24(byte[] b, int p) throws IOException {
        return (u8(b,p) << 16) | (u8(b,p+1) << 8) | u8(b,p+2);
    }
    private static byte[] ips(byte[] base, byte[] patch) throws IOException {
        byte[] result = Arrays.copyOf(base, base.length);
        int offset = 5;
        boolean eof = false;
        while (offset < patch.length) {
            int address = be24(patch, offset);
            offset += 3;
            if (address == 0x454f46) { eof = true; break; } // "EOF"
            int size = be16(patch, offset); offset += 2;
            int run = size;
            if (size == 0) { run = be16(patch, offset); offset += 2; }
            if (run == 0 || address > MAX_OUTPUT - run)
                throw new IOException("IPS intenta escribir fuera de la ROM");
            int end = address + run;
            if (end > result.length) result = Arrays.copyOf(result, end);
            if (size == 0) {
                byte value = (byte)u8(patch, offset++);
                Arrays.fill(result, address, end, value);
            } else {
                if (size > patch.length - offset)
                    throw new IOException("Bloque IPS incompleto");
                System.arraycopy(patch, offset, result, address, size);
                offset += size;
            }
        }
        if (!eof) throw new IOException("El parche IPS no tiene terminador EOF");
        int remaining = patch.length - offset;
        if (remaining == 3) {
            int truncate = be24(patch, offset);
            if (truncate < 32768 || truncate > MAX_OUTPUT)
                throw new IOException("Tamaño final IPS inválido");
            result = Arrays.copyOf(result, truncate);
        } else if (remaining != 0) {
            throw new IOException("IPS contiene datos inesperados tras EOF");
        }
        return result;
    }
    private static final class Cursor {
        final byte[] bytes;
        int index;
        final int limit;
        Cursor(byte[] bytes, int index, int limit) {
            this.bytes = bytes; this.index = index; this.limit = limit;
        }
        long readVar() throws IOException {
            long value = 0;
            long shift = 1;
            for (int i = 0; i < 10; i++) {
                if (index >= limit) throw new IOException("Parche BPS truncado");
                int b = bytes[index++] & 255;
                if ((b & 127L) > (Long.MAX_VALUE - value) / shift)
                    throw new IOException("Desbordamiento de entero BPS");
                value += (b & 127L) * shift;
                if ((b & 128) != 0) return value;
                if (shift > (Long.MAX_VALUE >> 7)) break;
                shift <<= 7;
                if (value > Long.MAX_VALUE - shift)
                    throw new IOException("Desbordamiento de entero BPS");
                value += shift;
            }
            throw new IOException("Variable BPS demasiado larga");
        }
        int signedOffset() throws IOException {
            long coded = readVar();
            long amount = coded >>> 1;
            if (amount > Integer.MAX_VALUE) throw new IOException("Desplazamiento BPS inválido");
            return (int)(((coded & 1) == 1) ? -amount : amount);
        }
    }
    private static long le32(byte[] data, int at) {
        return ((long)data[at] & 255)
                | (((long)data[at+1] & 255) << 8)
                | (((long)data[at+2] & 255) << 16)
                | (((long)data[at+3] & 255) << 24);
    }
    private static long crc(byte[] data, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, length);
        return crc.getValue();
    }
    private static byte[] bps(byte[] base, byte[] patch) throws IOException {
        if (patch.length < 19) throw new IOException("Parche BPS incompleto");
        int footer = patch.length - 12;
        if (crc(patch, patch.length - 4) != le32(patch, patch.length - 4))
            throw new IOException("Checksum del parche BPS incorrecto");
        if (crc(base, base.length) != le32(patch, footer))
            throw new IOException("ROM original no coincide con la versión requerida por BPS");

        Cursor reader = new Cursor(patch, 4, footer);
        long sourceLen = reader.readVar();
        long targetLen = reader.readVar();
        long metaLen = reader.readVar();
        if (sourceLen != base.length || targetLen < 32768 || targetLen > MAX_OUTPUT)
            throw new IOException("Tamaño de ROM incorrecto para el parche BPS");
        if (metaLen < 0 || metaLen > footer - reader.index)
            throw new IOException("Metadatos BPS dañados");
        reader.index += (int)metaLen;

        byte[] result = new byte[(int)targetLen];
        int output = 0;
        int sourceRelative = 0, targetRelative = 0;
        while (reader.index < footer) {
            long operation = reader.readVar();
            int action = (int)(operation & 3);
            long lengthLong = (operation >>> 2) + 1;
            if (lengthLong > result.length - output)
                throw new IOException("Parche BPS excede tamaño del destino");
            int length = (int)lengthLong;
            if (action == 0) {
                if (output > base.length - length)
                    throw new IOException("SourceRead BPS fuera de rango");
                System.arraycopy(base, output, result, output, length);
                output += length;
            } else if (action == 1) {
                if (length > footer - reader.index)
                    throw new IOException("TargetRead BPS truncado");
                System.arraycopy(patch, reader.index, result, output, length);
                reader.index += length; output += length;
            } else if (action == 2) {
                long shifted = (long)sourceRelative + reader.signedOffset();
                if (shifted < 0 || shifted > base.length - length)
                    throw new IOException("SourceCopy BPS fuera de rango");
                sourceRelative = (int)shifted;
                System.arraycopy(base, sourceRelative, result, output, length);
                sourceRelative += length; output += length;
            } else {
                long shifted = (long)targetRelative + reader.signedOffset();
                if (shifted < 0 || shifted >= output)
                    throw new IOException("TargetCopy BPS fuera de rango");
                targetRelative = (int)shifted;
                for (int j = 0; j < length; j++) {
                    if (targetRelative >= output)
                        throw new IOException("TargetCopy BPS lee bytes no escritos");
                    result[output++] = result[targetRelative++];
                }
            }
        }
        if (reader.index != footer || output != result.length)
            throw new IOException("Parche BPS incompleto");
        if (crc(result, result.length) != le32(patch, footer + 4))
            throw new IOException("Checksum del resultado BPS incorrecto");
        return result;
    }
}
