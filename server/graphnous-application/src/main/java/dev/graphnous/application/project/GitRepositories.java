package dev.graphnous.application.project;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What identifies a git repository, whatever form of its url a project or
 * a git host gives.
 */
public final class GitRepositories {

    // The host and path of an scp-like git url, such as git@github.com:org/repo.git
    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/]+@)?([^:/]+):(?!//)(.+)$");

    // The scheme, and the user of a url such as ssh://git@github.com/org/repo
    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*://(?:[^@/]+@)?", Pattern.CASE_INSENSITIVE);

    private GitRepositories() {
    }

    /**
     * Who hosts the repository and where, the same for each form of its url:
     * {@code github.com/org/repo} for {@code https://github.com/org/repo},
     * {@code git@github.com:org/repo.git} and
     * {@code ssh://git@github.com/org/repo}.
     */
    public static String key(final String gitUrl) {
        var url = gitUrl.trim();

        final var scheme = SCHEME.matcher(url);

        if (scheme.find()) {
            url = url.substring(scheme.end());
        } else {
            final var scpLike = SCP_LIKE.matcher(url);

            if (scpLike.matches()) {
                url = scpLike.group(1) + "/" + scpLike.group(2);
            }
        }

        url = url.replaceAll("/+$", "").replaceAll("(?i)\\.git$", "");

        // A port, as in ssh://git@host:2222/org/repo, does not change the repository
        url = url.replaceFirst("^([^/:]+):\\d+/", "$1/");

        return url.toLowerCase(Locale.ROOT);
    }
}
