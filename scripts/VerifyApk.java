import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

public final class VerifyApk {
    private VerifyApk() {}

    public static void main(String[] args) throws Exception {
        boolean allVerified = true;
        for (String path : args) {
            ApkVerifier.Result result = new ApkVerifier.Builder(new File(path)).build().verify();
            allVerified &= result.isVerified();
            System.out.printf(
                    "APK=%s verified=%s v1=%s v2=%s v3=%s v4=%s errors=%d warnings=%d%n",
                    new File(path).getName(), result.isVerified(), result.isVerifiedUsingV1Scheme(),
                    result.isVerifiedUsingV2Scheme(), result.isVerifiedUsingV3Scheme(),
                    result.isVerifiedUsingV4Scheme(), result.getErrors().size(), result.getWarnings().size());
            for (X509Certificate certificate : result.getSignerCertificates()) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
                System.out.println("CERT_SHA256=" + HexFormat.of().withUpperCase().formatHex(digest));
            }
            result.getErrors().forEach(error -> System.out.println("ERROR=" + error));
            result.getWarnings().forEach(warning -> System.out.println("WARNING=" + warning));
        }
        if (!allVerified) System.exit(1);
    }
}
