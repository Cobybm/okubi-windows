package bo.nexoplay.app.retro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Verifies Android <-> Windows portable cartridge contract without devices:
 * SRAM and RTC travel through .okubisave v1; native save-states never do.
 */
public final class OkubiPortableSaveContractTest {
    private static final String ROM = "ab".repeat(32);
    private static final String OTHER_ROM = "cd".repeat(32);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] encode(OkubiSaveArchive.Snapshot snapshot) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OkubiSaveArchive.write(out, snapshot);
        return out.toByteArray();
    }

    private static OkubiSaveArchive.Snapshot decode(byte[] bytes,
            String hash, String system) throws IOException {
        return OkubiSaveArchive.read(new ByteArrayInputStream(bytes), hash, system);
    }

    public static void main(String[] args) throws Exception {
        byte[] sram = new byte[8192], rtc = new byte[48], androidState = new byte[320];
        Arrays.fill(sram, (byte) 19);
        Arrays.fill(rtc, (byte) 26);
        Arrays.fill(androidState, (byte) 44);

        // Android may include state, but Windows can verify and skip it.
        OkubiSaveArchive.Snapshot fromPhone =
                new OkubiSaveArchive.Snapshot("GBA", ROM, sram, rtc, androidState);
        byte[] phoneArchive = encode(fromPhone);
        OkubiSaveArchive.Snapshot importedOnDesktop = decode(phoneArchive, ROM, "GBA");
        check(Arrays.equals(sram, importedOnDesktop.ram)
                && Arrays.equals(rtc, importedOnDesktop.rtc)
                && importedOnDesktop.state != null,
                "Original Android archive validates all sections including nonportable state.");

        // Windows export emits only portable SRAM and RTC.
        OkubiSaveArchive.Snapshot windowsPortable =
                new OkubiSaveArchive.Snapshot("GBA", ROM,
                        importedOnDesktop.ram, importedOnDesktop.rtc, null);
        byte[] windowsArchive = encode(windowsPortable);
        OkubiSaveArchive.Snapshot backOnAndroid =
                decode(windowsArchive, ROM, "GBA");
        check(Arrays.equals(sram, backOnAndroid.ram),
                "Android can read SRAM exported from Windows.");
        check(Arrays.equals(rtc, backOnAndroid.rtc),
                "Android can read RTC exported from Windows.");
        check(backOnAndroid.state == null,
                "Windows never distributes architecture-specific quick states.");
        check(Arrays.equals(phoneArchive, encode(fromPhone)),
                "Creating the portable file cannot mutate the old Android archive.");

        // A different ROM or console must never accept an otherwise valid archive.
        for (String system : new String[]{"GB", "GBC", "SNES"}) {
            try {
                decode(windowsArchive, ROM, system);
                throw new AssertionError("Wrong console accepted: " + system);
            } catch (IOException expected) { }
        }
        try {
            decode(windowsArchive, OTHER_ROM, "GBA");
            throw new AssertionError("Wrong cartridge accepted");
        } catch (IOException expected) { }

        // Integrity verification must detect any changed SRAM byte.
        byte[] tampered = windowsArchive.clone();
        tampered[tampered.length - 5] ^= 1;
        try {
            decode(tampered, ROM, "GBA");
            throw new AssertionError("Tampered .okubisave accepted");
        } catch (IOException expected) { }
        System.out.println("OkubiPortableSaveContractTest: PASS");
    }
}
