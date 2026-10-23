import com.android.apksig.ApkSigner;
import com.android.apksig.KeyConfig;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Signs (v2 + v3; minSdk 24 needs no v1) and 4-byte / page-aligns an APK with apksig, the library
 * behind Android's apksigner, so no Android SDK is needed.
 *
 * Usage: java -cp apksig.jar Sign.java IN.apk OUT.apk KEYSTORE ALIAS
 * Passwords come from KEYSTORE_PASSWORD and KEY_PASSWORD.
 */
public class Sign {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("usage: Sign IN.apk OUT.apk KEYSTORE ALIAS");
        }
        char[] storePassword = System.getenv("KEYSTORE_PASSWORD").toCharArray();
        String keyPasswordEnv = System.getenv("KEY_PASSWORD");
        char[] keyPassword = keyPasswordEnv != null ? keyPasswordEnv.toCharArray() : storePassword;

        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream in = new FileInputStream(args[2])) {
            store.load(in, storePassword);
        }
        PrivateKey key = (PrivateKey) store.getKey(args[3], keyPassword);
        if (key == null) {
            throw new IllegalArgumentException("no key '" + args[3] + "' in " + args[2]);
        }
        X509Certificate cert = (X509Certificate) store.getCertificate(args[3]);

        ApkSigner.SignerConfig signer =
                new ApkSigner.SignerConfig.Builder("navos", new KeyConfig.Jca(key), List.of(cert)).build();
        new ApkSigner.Builder(List.of(signer))
                .setInputApk(new File(args[0]))
                .setOutputApk(new File(args[1]))
                .setMinSdkVersion(24)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();
    }
}
