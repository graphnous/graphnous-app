package dev.graphnous.api.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import dev.graphnous.api.event.SpringEventPublisher;
import dev.graphnous.api.project.scanner.docker.DockerRepositoryScanner;
import dev.graphnous.api.project.scanner.docker.DockerScanContainers;
import dev.graphnous.api.project.scanner.docker.DockerSourceCheckout;
import dev.graphnous.api.project.scanner.docker.ScannerProperties;
import dev.graphnous.api.project.scanner.docker.ScannersProperties;
import dev.graphnous.application.enhancer.EnhancementRepository;
import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.scanner.ProjectScanner;
import dev.graphnous.application.project.scanner.RepositoryScanner;
import dev.graphnous.application.project.scanner.ScanExecutor;
import dev.graphnous.application.project.scanner.SourceCheckout;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.application.ssh.SshKeyRetriever;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.scanner.docker.DockerGitCheckout;
import dev.graphnous.scanner.docker.DockerSandbox;
import dev.graphnous.scanner.docker.DockerWorkspace;
import dev.graphnous.scanner.java.language.JavaVersionDetector;
import dev.graphnous.scanner.java.targetdetector.MavenScanTargetDetector;
import dev.graphnous.scanner.plan.DefaultScanPlanner;
import dev.graphnous.scanner.typescript.language.NodeVersionDetector;
import dev.graphnous.scanner.typescript.targetdetector.NpmTargetDetector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties({ScannerProperties.class, ScannersProperties.class})
public class ScannerConfiguration {

    /**
     * The scanner model is Jackson 2; kept out of the context so it does not
     * interfere with the JSON mapper Spring configures.
     */
    private final ObjectMapper scannerObjectMapper = new ObjectMapper();

    @Bean
    public ProjectScanner projectScanner(
        final ScanService scanService,
        final ProjectService projectService,
        final ScanLogService scanLogService,
        final ScanExecutor scanExecutor,
        final SourceCheckout sourceCheckout,
        final RepositoryScanner repositoryScanner,
        final ScanResultRepository scanResultRepository,
        final EnhancerProvider enhancerProvider,
        final EnhancementRepository enhancementRepository,
        final ScanSteps scanSteps,
        final SpringEventPublisher eventPublisher,
        final ScanNotifier scanNotifier
    ) {
        return new ProjectScanner(
            scanService,
            projectService,
            scanLogService,
            scanExecutor,
            sourceCheckout,
            repositoryScanner,
            scanResultRepository,
            enhancerProvider,
            enhancementRepository,
            scanSteps,
            eventPublisher,
            scanNotifier
        );
    }

    @Bean
    public DockerClient dockerClient() {
        return DockerSandbox.defaultClient();
    }

    @Bean
    public DockerGitCheckout dockerGitCheckout(
        final DockerClient dockerClient,
        final ScannerProperties properties
    ) {
        final var workspace = properties.workspace();

        return switch (workspace.type()) {
            case VOLUME -> DockerGitCheckout.inVolume(
                dockerClient,
                new DockerWorkspace.NamedVolume(workspace.volume(), workspace.path()),
                properties.gitImage()
            );
            case HOST -> DockerGitCheckout.inHostDirectory(
                dockerClient,
                workspace.path().toAbsolutePath(),
                properties.gitImage()
            );
        };
    }

    @Bean
    public DockerScanContainers dockerScanContainers(
        final DockerClient dockerClient,
        final ScannerProperties properties
    ) {
        return new DockerScanContainers(dockerClient, properties.instance());
    }

    @Bean
    public SourceCheckout sourceCheckout(
        final DockerGitCheckout dockerGitCheckout,
        final DockerScanContainers dockerScanContainers,
        final SshKeyRetriever sshKeyRetriever,
        final ScannerProperties properties
    ) {
        return new DockerSourceCheckout(
            dockerGitCheckout,
            dockerScanContainers,
            sshKeyRetriever,
            properties.sshKnownHosts()
        );
    }

    @Bean
    public RepositoryScanner repositoryScanner(
        final DockerClient dockerClient,
        final DockerGitCheckout dockerGitCheckout,
        final DockerScanContainers dockerScanContainers,
        final ScannersProperties scanners
    ) {
        final var planner = new DefaultScanPlanner(
            List.of(
                new MavenScanTargetDetector(),
                new NpmTargetDetector()
            ),
            List.of(
                new JavaVersionDetector(),
                new NodeVersionDetector(scannerObjectMapper, NodeVersionDetector.DEFAULT_NODE_VERSION)
            )
        );

        return new DockerRepositoryScanner(
            planner,
            scanners.definitions(),
            dockerClient,
            // The scanners get the workspace the source was checked out in
            dockerGitCheckout.workspace(),
            scannerObjectMapper,
            dockerScanContainers
        );
    }

}
