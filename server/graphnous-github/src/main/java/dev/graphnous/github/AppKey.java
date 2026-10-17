package dev.graphnous.github;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * The App's private key, and the JSON Web Tokens it signs to act as the App:
 * to get an installation's tokens.
 */
final class AppKey {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * The DER of PKCS#8's algorithm identifier for RSA, which a PKCS#1 key
     * as GitHub gives it lacks.
     */
    private static final byte[] RSA_ALGORITHM = {
        0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private final String appId;
    private final PrivateKey key;

    private AppKey(final String appId, final PrivateKey key) {
        this.appId = appId;
        this.key = key;
    }

    /**
     * @param pem the key as GitHub gives it, PKCS#1 (BEGIN RSA PRIVATE KEY),
     *            or as PKCS#8 (BEGIN PRIVATE KEY)
     * @throws IllegalArgumentException when it is not an RSA private key
     */
    static AppKey of(final String appId, final String pem) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("The GitHub App's id is required (graphnous.github.app-id)");
        }

        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("The GitHub App's private key is required (graphnous.github.private-key)");
        }

        final var pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        final var der = Base64.getMimeDecoder().decode(
            pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "")
        );

        try {
            final var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs8(der) : der));

            return new AppKey(appId.trim(), (RSAPrivateKey) key);
        } catch (final GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("The GitHub App's private key is not an RSA private key in PEM", e);
        }
    }

    /**
     * A token that is the App for ten minutes, the most GitHub takes, from a
     * minute before now, in case the clocks differ.
     */
    String jwt(final Instant now) {
        final var header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        final var claims = "{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}".formatted(
            now.getEpochSecond() - 60,
            now.getEpochSecond() + 9 * 60,
            appId.replace("\"", "")
        );
        final var unsigned = encode(header) + "." + encode(claims);

        try {
            final var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));

            return unsigned + "." + URL.encodeToString(signature.sign());
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Signing the GitHub App's token failed", e);
        }
    }

    private static String encode(final String json) {
        return URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The PKCS#1 key wrapped as PKCS#8: a sequence of version 0, the
     * algorithm, and the key as an octet string.
     */
    static byte[] pkcs8(final byte[] pkcs1) {
        final var body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] {0x02, 0x01, 0x00});
        body.writeBytes(RSA_ALGORITHM);
        body.write(0x04);
        body.writeBytes(length(pkcs1.length));
        body.writeBytes(pkcs1);

        final var sequence = new ByteArrayOutputStream();
        sequence.write(0x30);
        sequence.writeBytes(length(body.size()));
        sequence.writeBytes(body.toByteArray());

        return sequence.toByteArray();
    }

    private static byte[] length(final int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }

        if (length <= 0xff) {
            return new byte[] {(byte) 0x81, (byte) length};
        }

        if (length <= 0xffff) {
            return new byte[] {(byte) 0x82, (byte) (length >> 8), (byte) length};
        }

        return new byte[] {(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }
}
