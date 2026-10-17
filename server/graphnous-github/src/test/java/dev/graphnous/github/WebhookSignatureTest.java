package dev.graphnous.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    // GitHub's example of a delivery and its signature, from its documentation of validating deliveries
    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String SIGNATURE = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    @Test
    void takesADeliverySignedWithTheSecret() {
        assertThat(WebhookSignature.isValid(SECRET, BODY, SIGNATURE)).isTrue();
    }

    @Test
    void refusesAnotherBodyOrSecret() {
        assertThat(WebhookSignature.isValid(SECRET, "Hello, World?".getBytes(StandardCharsets.UTF_8), SIGNATURE)).isFalse();
        assertThat(WebhookSignature.isValid("another secret", BODY, SIGNATURE)).isFalse();
    }

    @Test
    void refusesADeliveryWithoutASignatureOrASecret() {
        assertThat(WebhookSignature.isValid(SECRET, BODY, null)).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, BODY, "sha1=757107ea")).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, BODY, "sha256=not hex")).isFalse();
        assertThat(WebhookSignature.isValid(" ", BODY, SIGNATURE)).isFalse();
    }
}
