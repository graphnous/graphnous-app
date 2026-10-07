package dev.graphnous.api.ssh;

import dev.graphnous.application.ssh.SshKeyRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The SSH private key configured for this server, or none (empty) when
 * only public repositories are scanned.
 */
@Component
public class LocalSshKeyRetriever implements SshKeyRetriever {

    private final String key;

    public LocalSshKeyRetriever(
        @Value("${graphnous.scanner.ssh-key:}") final String key
    ) {
        this.key = key;
    }

    @Override
    public String retrieve() {
        return key;
    }
}
