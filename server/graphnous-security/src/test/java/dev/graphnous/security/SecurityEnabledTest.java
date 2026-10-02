package dev.graphnous.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security on: tokens of the platform, which the test signs itself, checked
 * as the server checks the platform's.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
    "graphnous.security.enabled=true",
    "graphnous.security.issuer-uri=" + SecurityEnabledTest.ISSUER
})
@AutoConfigureMockMvc
class SecurityEnabledTest {

    static final String ISSUER = "http://platform.test";

    private static final RSAKey KEY = generateKey();

    private final UUID user = UUID.randomUUID();
    private final UUID organization = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SecurityProperties properties;

    /**
     * The platform's keys, without fetching them from it; the validation
     * is the server's own.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @BeforeEach
    void decodeWithTheTestKey() throws Exception {
        final var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(SecurityConfiguration.validator(this.properties));
        when(this.jwtDecoder.decode(anyString())).thenAnswer(call -> decoder.decode(call.getArgument(0)));
    }

    private String token(final Consumer<JwtClaimsSet.Builder> change) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(this.user.toString())
            .audience(List.of("graphnous-api", "graphnous-app"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("org_id", this.organization.toString())
            .claim("org_slug", "acme")
            .claim("org_role", "MEMBER");
        change.accept(claims);

        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)))
            .encode(JwtEncoderParameters.from(claims.build()))
            .getTokenValue();
    }

    @Test
    void takesTheUserAndOrganizationFromTheToken() throws Exception {
        this.mvc.perform(get("/api/v1/context").header("Authorization", "Bearer " + token(claims -> { })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value(this.user.toString()))
            .andExpect(jsonPath("$.organization").value(this.organization.toString()));
    }

    @Test
    void takesAnApiKeysTokenForItsOrganizationWithoutAUser() throws Exception {
        final var key = UUID.randomUUID();
        final var token = token(claims -> claims
            .subject(key.toString())
            .claim("api_key", key.toString())
            .claims(all -> all.remove("org_role")));

        this.mvc.perform(get("/api/v1/context").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(jsonPath("$.organization").value(this.organization.toString()));
    }

    @Test
    void refusesARequestWithoutAToken() throws Exception {
        this.mvc.perform(get("/api/v1/context")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesTokensThatAreNotForTheApiAndAnOrganization() throws Exception {
        final List<Consumer<JwtClaimsSet.Builder>> wrong = List.of(
            claims -> claims.audience(List.of("something-else")),
            claims -> claims.issuer("http://someone-else.test"),
            claims -> claims.claims(all -> all.remove("org_id")),
            claims -> claims.claim("org_id", "not-an-id"),
            claims -> claims.issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(1800))
        );

        for (final var change : wrong) {
            this.mvc.perform(get("/api/v1/context").header("Authorization", "Bearer " + token(change)))
                .andExpect(status().isUnauthorized());
        }
    }
}
