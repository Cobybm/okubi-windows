package bo.nexoplay.app.retro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Strictly bounded ROM ingestion. GB/GBA dumps are conventionally powers of
 * two; when the grown array is exactly full, return it without a redundant
 * ByteArrayOutputStream.toByteArray() copy. Only private buffers are returned.
 */
public final class RetroRomReader {
    private RetroRomReader() {}

    private static final class RomBuffer extends ByteArrayOutputStream {
        RomBuffer() { super(64 * 1024); }
        byte[] takeExact() {
            // This buffer must not be used again after ownership is returned.
            return count == buf.length ? buf : Arrays.copyOf(buf, count);
        }
    }

    public static byte[] readBounded(InputStream source, int maximum, int minimum)
            throws IOException {
        if (source == null) throw new IOException("No se pudo leer la ROM");
        if (maximum < 1 || minimum < 0 || minimum > maximum)
            throw new IllegalArgumentException("Límites de ROM incorrectos");
        RomBuffer out = new RomBuffer();
        byte[] chunk = new byte[16 * 1024];
        int n;
        while ((n = source.read(chunk)) != -1) {
            // Some imperfect Android providers return 0 instead of blocking.
            // Force progress or a real EOF without spinning the CPU.
            if (n == 0) {
                int single = source.read();
                if (single == -1) break;
                if (out.size() >= maximum)
                    throw new IOException("Archivo demasiado grande para el emulador");
                out.write(single);
                continue;
            }
            if (out.size() > maximum - n)
                throw new IOException("Archivo demasiado grande para el emulador");
            out.write(chunk, 0, n);
        }
        if (out.size() < minimum) throw new IOException("Cartucho incompleto");
        return out.takeExact();
    }
}
