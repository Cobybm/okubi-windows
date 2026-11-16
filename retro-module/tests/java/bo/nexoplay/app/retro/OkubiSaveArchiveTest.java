package bo.nexoplay.app.retro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Pure-Java regression tests; run on Java 17 before the Android build. */
public final class OkubiSaveArchiveTest {
    private static final String ROM = "ab".repeat(32);
    private static final String OTHER_ROM = "cd".repeat(32);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Throwing command, String description) throws Exception {
        try {
            command.run();
            throw new AssertionError("Se esperaba rechazo: " + description);
        } catch (IOException expected) { /* invalid archives must be rejected */ }
    }

    private interface Throwing { void run() throws Exception; }

    private static byte[] encode(OkubiSaveArchive.Snapshot snapshot) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OkubiSaveArchive.write(out, snapshot);
        return out.toByteArray();
    }

    private static OkubiSaveArchive.Snapshot decode(byte[] data) throws Exception {
        return OkubiSaveArchive.read(new ByteArrayInputStream(data), ROM, "GBA");
    }

    private static void roundTrip() throws Exception {
        byte[] sram = new byte[8192];
        byte[] rtc = new byte[16];
        byte[] state = new byte[12000];
        for (int i = 0; i < sram.length; i++) sram[i] = (byte) i;
        for (int i = 0; i < rtc.length; i++) rtc[i] = (byte) (i + 10);
        for (int i = 0; i < state.length; i++) state[i] = (byte) (i * 17);
        OkubiSaveArchive.Snapshot original =
                new OkubiSaveArchive.Snapshot("GBA", ROM, sram, rtc, state);
        byte[] serialized = encode(original);
        check(OkubiSaveArchive.samePayload(original, decode(serialized)),
                "No coincide el respaldo restaurado");
        sram[0] = (byte) 42;
        check(original.ram[0] != sram[0], "No se copió defensivamente la memoria");
    }

    private static void identityAndCorruption() throws Exception {
        byte[] bytes = encode(new OkubiSaveArchive.Snapshot("GBA", ROM,
                new byte[]{2, 3, 4}, null, new byte[]{5, 6, 7}));
        expectFailure(() -> OkubiSaveArchive.read(new ByteArrayInputStream(bytes),
                OTHER_ROM, "GBA"), "identificador de ROM distinto");
        expectFailure(() -> OkubiSaveArchive.read(new ByteArrayInputStream(bytes),
                ROM, "GB"), "consola equivocada");
        byte[] tampered = bytes.clone();
        tampered[tampered.length - 1] ^= 1;
        expectFailure(() -> decode(tampered), "checksum alterado");
        expectFailure(() -> decode(Arrays.copyOf(bytes, bytes.length - 1)), "truncado");
        byte[] extra = Arrays.copyOf(bytes, bytes.length + 1);
        expectFailure(() -> decode(extra), "bytes extra");
        byte[] corruptMagic = bytes.clone();
        corruptMagic[0] ^= 1;
        expectFailure(() -> decode(corruptMagic), "magic incorrecto");
    }

    private static void limitsAndNoData() throws Exception {
        expectFailure(() -> new OkubiSaveArchive.Snapshot("GBA", ROM, null, null, null),
                "sin datos");
        expectFailure(() -> new OkubiSaveArchive.Snapshot("GBA", ROM,
                new byte[OkubiSaveArchive.MAX_RAM + 1], null, null), "SRAM enorme");
        expectFailure(() -> new OkubiSaveArchive.Snapshot("GBA", ROM,
                null, new byte[OkubiSaveArchive.MAX_RTC + 1], null), "RTC enorme");
        expectFailure(() -> new OkubiSaveArchive.Snapshot("GBA", ROM,
                null, null, new byte[OkubiSaveArchive.MAX_STATE + 1]), "estado enorme");
        // SNES is now a real supported system, with its own system marker.
        OkubiSaveArchive.Snapshot snes = new OkubiSaveArchive.Snapshot(
                "SNES", ROM, new byte[]{1, 2}, null, new byte[]{3, 4});
        byte[] snesBytes = encode(snes);
        check(OkubiSaveArchive.samePayload(snes, OkubiSaveArchive.read(
                new ByteArrayInputStream(snesBytes), ROM, "SNES")),
                "SNES backup round trip");
        expectFailure(() -> OkubiSaveArchive.read(
                new ByteArrayInputStream(snesBytes), ROM, "GBA"),
                "SNES must never restore into Pokémon GBA");
        expectFailure(() -> new OkubiSaveArchive.Snapshot("NES", ROM,
                new byte[]{1}, null, null), "sistema desconocido");
        expectFailure(() -> new OkubiSaveArchive.Snapshot("GBA", "wrong",
                new byte[]{1}, null, null), "ROM inválida");

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.writeInt(0x4F4B4253);
        out.writeInt(1);
        out.writeUTF("GBA");
        out.writeUTF(ROM);
        out.writeInt(OkubiSaveArchive.MAX_RAM + 1); // reject before allocating data
        out.flush();
        expectFailure(() -> decode(raw.toByteArray()), "tamaño de archivo malicioso");
    }


    /**
     * Regression matrix: a valid archive may only restore to its exact console
     * and ROM. This must stay true even when two consoles use the same ROM hash.
     * No Android storage or existing user save file is touched.
     */
    private static void consoleIsolationMatrix() throws Exception {
        String[] systems = {"GB", "GBC", "GBA", "SNES"};
        String[] hashes = {ROM, OTHER_ROM};
        for (String sourceSystem : systems) {
            for (String sourceHash : hashes) {
                OkubiSaveArchive.Snapshot original = new OkubiSaveArchive.Snapshot(
                        sourceSystem, sourceHash, new byte[]{1, 9, 7}, null, new byte[]{4, 2});
                byte[] archive = encode(original);
                for (String targetSystem : systems) {
                    for (String targetHash : hashes) {
                        if (sourceSystem.equals(targetSystem) && sourceHash.equals(targetHash)) {
                            OkubiSaveArchive.Snapshot restored = OkubiSaveArchive.read(
                                    new ByteArrayInputStream(archive), targetHash, targetSystem);
                            check(OkubiSaveArchive.samePayload(original, restored),
                                    "No coincide la restauración del mismo juego y consola");
                        } else {
                            final String expectedSystem = targetSystem;
                            final String expectedHash = targetHash;
                            expectFailure(() -> OkubiSaveArchive.read(
                                    new ByteArrayInputStream(archive), expectedHash, expectedSystem),
                                    "No se debe cruzar " + sourceSystem + " a " + expectedSystem);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        roundTrip();
        identityAndCorruption();
        limitsAndNoData();
        consoleIsolationMatrix();
        System.out.println("OKUBI Save Archive: pruebas Java completas");
    }
}
