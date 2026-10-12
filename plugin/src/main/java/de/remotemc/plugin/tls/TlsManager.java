package de.remotemc.plugin.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * Loads or creates the server certificate in tls.p12. The certificate never changes unless the
 * file is deleted, so paired apps keep trusting it.
 */
public final class TlsManager {

    private static final String ALIAS = "remotemc";
    // The keystore is protected by file permissions; the password only satisfies the PKCS#12 format.
    private static final char[] PASSWORD = "remotemc".toCharArray();

    private final File file;
    private final Logger logger;
    private SSLContext context;
    private byte[] fingerprint;

    public TlsManager(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                ks.load(in, PASSWORD);
            }
        } else {
            ks.load(null, null);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keys = gen.generateKeyPair();
            X509Certificate cert = SelfSignedCertificate.create(keys, "RemoteMC", 30);
            ks.setKeyEntry(ALIAS, keys.getPrivate(), PASSWORD, new Certificate[]{cert});
            try (OutputStream out = new FileOutputStream(file)) {
                ks.store(out, PASSWORD);
            }
            file.setReadable(false, false);
            file.setReadable(true, true);
            logger.info("Created a new TLS certificate (tls.p12). Devices must be paired again if it is ever deleted.");
        }

        Certificate cert = ks.getCertificate(ALIAS);
        fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
    }

    public SSLContext context() {
        return context;
    }

    /** Fingerprint for links/QR codes (base64url, 43 chars). */
    public String fingerprintToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fingerprint);
    }

    /** Full fingerprint for display, e.g. "AB:CD:...". */
    public String fingerprintHex() {
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(fingerprint);
    }

    /** Short code users compare with the app, e.g. "AB12-CD34". */
    public String shortFingerprint() {
        String hex = HexFormat.of().withUpperCase().formatHex(fingerprint, 0, 4);
        return hex.substring(0, 4) + "-" + hex.substring(4);
    }
}
