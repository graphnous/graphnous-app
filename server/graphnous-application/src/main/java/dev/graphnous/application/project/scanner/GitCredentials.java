package dev.graphnous.application.project.scanner;

import java.util.Optional;

/**
 * Access to a repository over HTTPS that something other than the server's
 * SSH key gives, such as a GitHub App installed on it.
 */
@FunctionalInterface
public interface GitCredentials {

    /**
     * None, for every repository.
     */
    GitCredentials NONE = gitUrl -> Optional.empty();

    /**
     * @param gitUrl the repository's url, in any form git takes
     * @return how to fetch it, if this gives access to it
     */
    Optional<Credential> forRepository(String gitUrl);

    /**
     * @param url      the repository's https url to fetch instead of the
     *                 project's, which may be an SSH one
     * @param username as git sends it, with the password, as basic
     *                 authentication
     * @param source   what gave it, for the scan's log, such as "the GitHub
     *                 App"
     */
    record Credential(String url, String username, String password, String source) {

        @Override
        public String toString() {
            return "Credential[url=" + url + ", username=" + username + ", source=" + source + "]";
        }
    }
}
