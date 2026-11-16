package bo.nexoplay.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Real Java file readback tests, including same-size corruption. */
public final class OkubiArchiveVerifierTest {
    private static void require(boolean value,String reason) {
        if(!value) throw new AssertionError(reason);
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:sha) {
            hex.append(Character.forDigit((b>>>4)&15,16));
            hex.append(Character.forDigit(b&15,16));
        }
        return hex.toString();
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("okubi-restore-");
        Path save=dir.resolve("checkpoint.state");
        try {
            byte[] bytes="OKUBI_CHECKPOINT_10".getBytes(StandardCharsets.UTF_8);
            String good=sha256(bytes);
            Files.write(save,bytes);
            require(OkubiArchiveVerifier.matches(save.toFile(),bytes.length,good),
                    "verified bytes accepted");
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length,
                    sha256(new byte[]{12})),"bad digest rejected");
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length,
                    "not-a-sha"),"bad hash format rejected");
            require(!OkubiArchiveVerifier.matches(save.toFile(),0,good),
                    "zero length rejected");
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length+1,good),
                    "wrong length rejected");
            byte[] corrupt=bytes.clone();
            corrupt[3]^=0x40;
            Files.write(save,corrupt);
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length,good),
                    "same-size corruption detected");
            Files.write(save,new byte[]{1,2,3});
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length,good),
                    "truncation detected");
            Files.delete(save);
            require(!OkubiArchiveVerifier.matches(save.toFile(),bytes.length,good),
                    "missing file rejected");
            require(!OkubiArchiveVerifier.matches(null,bytes.length,good),
                    "null file rejected");
        } finally {
            Files.deleteIfExists(save);
            Files.deleteIfExists(dir);
        }
        System.out.println("OkubiArchiveVerifierTest: OK");
    }
}
