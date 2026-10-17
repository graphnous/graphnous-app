package dev.graphnous.github;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppKeyTest {

    static final KeyPair KEYS = keys();

    @Test
    void readsAKeyAsGitHubGivesItAndSignsTokensOfTheApp() throws Exception {
        final var now = Instant.parse("2026-10-17T12:00:00Z");
        final var jwt = AppKey.of("12345", pkcs1Pem()).jwt(now);
        final var parts = jwt.split("\\.");

        final var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KEYS.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();

        final var claims = JsonMapper.builder().build().readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("iss").asString()).isEqualTo("12345");
        assertThat(claims.path("iat").asLong()).isEqualTo(now.getEpochSecond() - 60);
        assertThat(claims.path("exp").asLong()).isEqualTo(now.getEpochSecond() + 540);
    }

    @Test
    void readsAPkcs8Key() {
        final var pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(KEYS.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";

        assertThat(AppKey.of("12345", pem).jwt(Instant.now())).contains(".");
    }

    @Test
    void wrapsAPkcs1KeyAsJavaReadsIt() {
        assertThat(AppKey.pkcs8(pkcs1())).isEqualTo(KEYS.getPrivate().getEncoded());
    }

    @Test
    void needsAnIdAndAnRsaKey() {
        assertThatThrownBy(() -> AppKey.of(" ", pkcs1Pem())).hasMessageContaining("graphnous.github.app-id");
        assertThatThrownBy(() -> AppKey.of("12345", null)).hasMessageContaining("graphnous.github.private-key");
        assertThatThrownBy(() -> AppKey.of("12345", "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an RSA private key");
    }

    static String pkcs1Pem() {
        return "-----BEGIN RSA PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pkcs1())
            + "\n-----END RSA PRIVATE KEY-----\n";
    }

    /**
     * The key as PKCS#1: what PKCS#8 holds as its last part, an octet
     * string, after the version and the algorithm.
     */
    private static byte[] pkcs1() {
        final var pkcs8 = KEYS.getPrivate().getEncoded();
        // The sequence's tag and length, the version (3 bytes) and the algorithm (15 bytes)
        var at = 1 + lengthSize(pkcs8, 1) + 3 + 15;
        // The octet string's tag, then its length
        at += 1;
        final var size = lengthSize(pkcs8, at);

        return Arrays.copyOfRange(pkcs8, at + size, pkcs8.length);
    }

    private static int lengthSize(final byte[] der, final int at) {
        return (der[at] & 0x80) == 0 ? 1 : 1 + (der[at] & 0x7f);
    }

    private static KeyPair keys() {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
