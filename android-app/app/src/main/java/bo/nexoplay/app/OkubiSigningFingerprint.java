package bo.nexoplay.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * Public certificate fingerprint of THIS installed OKUBI package.
 *
 * Read-only: no APK downloads, no installer invocation, no private key access,
 * no saved-game access. This does not by itself guarantee compatibility:
 * the prospective APK must have the SAME signer and a suitable versionCode.
 */
public final class OkubiSigningFingerprint {
    private OkubiSigningFingerprint() { }

    public static String currentSha256(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo installed = manager.getPackageInfo(
                    context.getPackageName(), flags);
            Signature[] current;
            if (Build.VERSION.SDK_INT >= 28) {
                if (installed.signingInfo == null)
                    return "Firma de instalación no disponible";
                current = installed.signingInfo.getApkContentsSigners();
            } else {
                current = installed.signatures;
            }
            if (current == null || current.length != 1)
                return "Identidad de firma no verificable";
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    current[0].toByteArray());
            StringBuilder value = new StringBuilder(64);
            for (byte b : digest) {
                value.append(Character.forDigit((b >> 4) & 15, 16));
                value.append(Character.forDigit(b & 15, 16));
            }
            return value.toString();
        } catch (Exception ignored) {
            return "No se pudo consultar la firma de Android";
        }
    }

    public static boolean isFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
