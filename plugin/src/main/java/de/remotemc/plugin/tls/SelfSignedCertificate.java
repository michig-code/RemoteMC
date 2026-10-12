package de.remotemc.plugin.tls;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds a minimal self-signed X.509 v3 certificate (ECDSA P-256 / SHA-256) with the JDK only,
 * so no crypto library has to be bundled. Clients trust it by pinning its SHA-256 fingerprint.
 */
final class SelfSignedCertificate {

    private static final String OID_ECDSA_SHA256 = "1.2.840.10045.4.3.2";
    private static final String OID_COMMON_NAME = "2.5.4.3";
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'");

    private SelfSignedCertificate() {}

    static X509Certificate create(KeyPair keys, String commonName, int validYears) throws GeneralSecurityException {
        byte[] algorithm = seq(oid(OID_ECDSA_SHA256));
        byte[] name = seq(set(seq(oid(OID_COMMON_NAME), tlv(0x0C, commonName.getBytes(StandardCharsets.UTF_8)))));
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
        byte[] validity = seq(utcTime(now), utcTime(now.plusYears(validYears)));

        byte[] tbs = seq(
                tlv(0xA0, integer(BigInteger.TWO)), // [0] version v3
                integer(new BigInteger(64, new SecureRandom()).add(BigInteger.ONE)),
                algorithm,
                name,
                validity,
                name,
                keys.getPublic().getEncoded() // already a DER SubjectPublicKeyInfo
        );

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.getPrivate());
        signer.update(tbs);
        byte[] signature = signer.sign();

        byte[] bitString = new byte[signature.length + 1]; // leading 0 = no unused bits
        System.arraycopy(signature, 0, bitString, 1, signature.length);
        byte[] der = seq(tbs, algorithm, tlv(0x03, bitString));

        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    // ── minimal DER encoding ───────────────────────────────────────────

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = value.length;
        if (len < 0x80) {
            out.write(len);
        } else {
            byte[] lenBytes = BigInteger.valueOf(len).toByteArray();
            int start = lenBytes[0] == 0 ? 1 : 0;
            out.write(0x80 | (lenBytes.length - start));
            out.write(lenBytes, start, lenBytes.length - start);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static byte[] seq(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] integer(BigInteger value) {
        return tlv(0x02, value.toByteArray());
    }

    private static byte[] utcTime(ZonedDateTime time) {
        return tlv(0x17, UTC_TIME.format(time).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>= 7;
            } while (v > 0);
            for (int j = n - 1; j >= 0; j--) {
                out.write(tmp[j] | (j > 0 ? 0x80 : 0));
            }
        }
        return tlv(0x06, out.toByteArray());
    }
}
