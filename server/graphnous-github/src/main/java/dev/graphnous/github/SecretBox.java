package dev.graphnous.github;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the secrets the server stores, such as the GitHub App's private
 * key, with AES-256-GCM and the server's secrets key: each with a random
 * nonce, and bound to what it is, so one cannot stand in for another.
 */
final class SecretBox {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    private SecretBox(final SecretKeySpec key) {
        this.key = key;
    }

    /**
     * @param key 32 bytes, in base64, such as {@code openssl rand -base64 32}
     *            gives
     * @throws IllegalArgumentException when it is not
     */
    static SecretBox of(final String key) {
        final byte[] bytes;

        try {
            bytes = Base64.getDecoder().decode(key == null ? "" : key.trim());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("The secrets key (graphnous.secrets.key) is not base64", e);
        }

        if (bytes.length != 32) {
            throw new IllegalArgumentException(
                "The secrets key (graphnous.secrets.key) must be 32 bytes in base64, such as openssl rand -base64 32 gives"
            );
        }

        return new SecretBox(new SecretKeySpec(bytes, "AES"));
    }

    /**
     * @param purpose what the secret is, such as github.private-key; the
     *                same opens it
     */
    String seal(final String purpose, final String secret) {
        final var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);

        try {
            final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            final var sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Encrypting " + purpose + " failed", e);
        }
    }

    /**
     * @throws IllegalStateException when it was sealed with another key or
     *                               for another purpose, or was changed
     */
    String open(final String purpose, final String sealed) {
        final var bytes = Base64.getDecoder().decode(sealed);

        try {
            final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));

            return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(
                "Decrypting " + purpose + " failed: it was stored with another secrets key (graphnous.secrets.key), or changed", e
            );
        }
    }
}
