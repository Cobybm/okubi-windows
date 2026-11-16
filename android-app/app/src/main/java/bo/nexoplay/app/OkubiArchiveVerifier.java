package bo.nexoplay.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies actual bytes on disk after importing a user backup.
 * Read-only, pure Java, without Android framework dependencies.
 */
public final class OkubiArchiveVerifier {
    private OkubiArchiveVerifier() {}

    public static boolean matches(File file,long expectedBytes,String expectedSha256)
            throws IOException {
        if(file==null || !file.isFile() || expectedBytes<=0
                || file.length()!=expectedBytes || expectedSha256==null
                || !expectedSha256.matches("[0-9a-f]{64}")) return false;
        MessageDigest sha;
        try { sha=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 no disponible",unavailable);
        }
        byte[] buffer=new byte[16384];
        long read=0;
        try(FileInputStream input=new FileInputStream(file)) {
            int n;
            while((n=input.read(buffer))!=-1) {
                read+=n;
                if(read>expectedBytes)return false;
                sha.update(buffer,0,n);
            }
        }
        if(read!=expectedBytes)return false;
        StringBuilder hex=new StringBuilder(64);
        for(byte b:sha.digest()) {
            hex.append(Character.forDigit((b>>>4)&15,16));
            hex.append(Character.forDigit(b&15,16));
        }
        return expectedSha256.contentEquals(hex);
    }
}
