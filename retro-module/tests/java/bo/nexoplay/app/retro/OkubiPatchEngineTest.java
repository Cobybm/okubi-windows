package bo.nexoplay.app.retro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public final class OkubiPatchEngineTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static byte[] base() {
        byte[] base = new byte[32768];
        for (int i = 0; i < base.length; i++) base[i] = (byte)(i * 7);
        return base;
    }
    private static void writeVar(ByteArrayOutputStream out, long value) {
        for (;;) {
            int b = (int)(value & 127);
            value >>>= 7;
            if (value == 0) { out.write(b | 128); return; }
            out.write(b);
            value--;
        }
    }
    private static void le32(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 4; i++) out.write((int)((value >>> (i * 8)) & 255));
    }
    private static long checksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
    private static byte[] bpsPatch(byte[] source, byte[] target, boolean corruptCrc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('B'); out.write('P'); out.write('S'); out.write('1');
        writeVar(out, source.length);
        writeVar(out, target.length);
        writeVar(out, 0);
        writeVar(out, ((long)(10 - 1) << 2)); // sourceRead first ten
        writeVar(out, ((long)(2 - 1) << 2) | 1); // literal two
        out.write(target[10]); out.write(target[11]);
        writeVar(out, ((long)(target.length - 12 - 1) << 2)); // rest sourceRead
        le32(out, checksum(source));
        le32(out, corruptCrc ? 0 : checksum(target));
        le32(out, checksum(out.toByteArray()));
        return out.toByteArray();
    }
    private static void ips() throws Exception {
        byte[] base = base();
        ByteArrayOutputStream patch = new ByteArrayOutputStream();
        patch.write("PATCH".getBytes(StandardCharsets.US_ASCII));
        patch.write(new byte[]{0, 0, 10, 0, 2, 101, 102});
        patch.write("EOF".getBytes(StandardCharsets.US_ASCII));
        byte[] result = OkubiPatchEngine.apply(base, patch.toByteArray());
        check(result[10] == 101 && result[11] == 102, "IPS literal patch failed");
        check(base[10] != 101, "IPS modified source in place");

        ByteArrayOutputStream rle = new ByteArrayOutputStream();
        rle.write("PATCH".getBytes(StandardCharsets.US_ASCII));
        rle.write(new byte[]{0, 0, 10, 0, 0, 0, 4, 77});
        rle.write("EOF".getBytes(StandardCharsets.US_ASCII));
        byte[] repeated = OkubiPatchEngine.apply(base, rle.toByteArray());
        for (int i = 10; i < 14; i++) check(repeated[i] == 77, "IPS RLE failed");
    }
    private static void bps() throws Exception {
        byte[] source = base();
        byte[] target = source.clone();
        target[10] = 99;
        target[11] = 34;
        byte[] patch = bpsPatch(source, target, false);
        check(Arrays.equals(target, OkubiPatchEngine.apply(source, patch)),
                "BPS read/literal mismatch");
        byte[] otherSource = source.clone(); otherSource[100]++;
        try {
            OkubiPatchEngine.apply(otherSource, patch);
            throw new AssertionError("BPS accepted wrong source checksum");
        } catch (IOException expected) {}
        byte[] broken = patch.clone();
        broken[broken.length - 13] ^= 1;
        try {
            OkubiPatchEngine.apply(source, broken);
            throw new AssertionError("BPS accepted corrupt patch checksum");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        ips();
        bps();
        System.out.println("OKUBI: IPS literal/RLE and BPS CRC/roundtrip tests passed");
    }
}
