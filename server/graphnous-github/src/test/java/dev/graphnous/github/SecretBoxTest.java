package dev.graphnous.github;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBoxTest {

    static String key() {
        final var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);

        return Base64.getEncoder().encodeToString(bytes);
    }

    private final SecretBox box = SecretBox.of(key());

    @Test
    void opensWhatItSealed() {
        final var sealed = box.seal("github.private-key", "-----BEGIN RSA PRIVATE KEY-----");

        assertThat(sealed).doesNotContain("PRIVATE KEY");
        assertThat(box.open("github.private-key", sealed)).isEqualTo("-----BEGIN RSA PRIVATE KEY-----");
        // A nonce of its own each time
        assertThat(box.seal("github.private-key", "-----BEGIN RSA PRIVATE KEY-----")).isNotEqualTo(sealed);
    }

    @Test
    void opensNothingOfAnotherPurposeOrKeyOrChanged() {
        final var sealed = box.seal("github.private-key", "secret");
        final var bytes = Base64.getDecoder().decode(sealed);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> box.open("github.webhook-secret", sealed)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecretBox.of(key()).open("github.private-key", sealed))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("another secrets key");
        assertThatThrownBy(() -> box.open("github.private-key", Base64.getEncoder().encodeToString(bytes)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void needsAKeyOf32Bytes() {
        assertThatThrownBy(() -> SecretBox.of(Base64.getEncoder().encodeToString(new byte[16])))
            .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> SecretBox.of("not base64!")).hasMessageContaining("not base64");
        assertThatThrownBy(() -> SecretBox.of(null)).hasMessageContaining("32 bytes");
    }
}
