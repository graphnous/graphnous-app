package dev.graphnous.github;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Whether a webhook delivery is GitHub's: its X-Hub-Signature-256 header is
 * the HMAC-SHA256 of its body with the App's webhook secret.
 */
final class WebhookSignature {

    static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    static boolean isValid(final String secret, final byte[] body, final String header) {
        if (secret == null || secret.isBlank() || header == null || !header.startsWith(PREFIX)) {
            return false;
        }

        final byte[] signature;

        try {
            signature = HexFormat.of().parseHex(header.substring(PREFIX.length()));
        } catch (final IllegalArgumentException e) {
            return false;
        }

        // In constant time, so the time taken does not tell how much matched
        return MessageDigest.isEqual(sign(secret, body), signature);
    }

    static byte[] sign(final String secret, final byte[] body) {
        try {
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            return mac.doFinal(body);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
