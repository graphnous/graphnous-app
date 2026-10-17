package dev.graphnous.github;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * The GitHub App setup registered, in the server's database, with its
 * private key and webhook secret encrypted: one at most.
 */
class StoredApps {

    static final String PRIVATE_KEY = "github.private-key";
    static final String WEBHOOK_SECRET = "github.webhook-secret";

    private final JdbcClient jdbc;
    private final SecretBox box;

    /**
     * @param box what encrypts the secrets; null without a secrets key, so
     *            nothing can be stored or read
     */
    StoredApps(final JdbcClient jdbc, final SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    boolean canStore() {
        return box != null;
    }

    /**
     * Replaces the App stored before, if any.
     *
     * @throws IllegalStateException without a secrets key
     */
    void save(final StoredApp app) {
        requireBox();

        jdbc.sql("DELETE FROM github_apps").update();
        jdbc.sql("""
                INSERT INTO github_apps (id, app_id, slug, name, html_url, private_key, webhook_secret, created_at)
                VALUES (1, :appId, :slug, :name, :htmlUrl, :privateKey, :webhookSecret, :createdAt)
                """)
            .param("appId", app.appId())
            .param("slug", app.slug())
            .param("name", app.name())
            .param("htmlUrl", app.htmlUrl())
            .param("privateKey", box.seal(PRIVATE_KEY, app.privateKey()))
            .param("webhookSecret", box.seal(WEBHOOK_SECRET, app.webhookSecret()))
            .param("createdAt", Timestamp.from(app.createdAt()))
            .update();
    }

    /**
     * @throws IllegalStateException when there is one, but no secrets key,
     *                               or another, to read it with
     */
    Optional<StoredApp> find() {
        return jdbc.sql("SELECT * FROM github_apps WHERE id = 1")
            .query((row, number) -> {
                requireBox();

                return new StoredApp(
                    row.getString("app_id"),
                    row.getString("slug"),
                    row.getString("name"),
                    row.getString("html_url"),
                    box.open(PRIVATE_KEY, row.getString("private_key")),
                    box.open(WEBHOOK_SECRET, row.getString("webhook_secret")),
                    row.getTimestamp("created_at").toInstant()
                );
            })
            .optional();
    }

    void delete() {
        jdbc.sql("DELETE FROM github_apps").update();
    }

    private void requireBox() {
        if (box == null) {
            throw new IllegalStateException(
                "Set a secrets key (graphnous.secrets.key, GRAPHNOUS_SECRETS_KEY) to keep the GitHub App's secrets: "
                    + "32 bytes in base64, such as openssl rand -base64 32 gives"
            );
        }
    }
}
