package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.command.InspectImageResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainersTest {

    @Test
    void knowsAnImageOfTheRequestedPlatform() {
        final var image = new InspectImageResponse().withOs("linux").withArch("amd64");

        assertThat(Containers.isFor(image, "linux/amd64")).isTrue();
    }

    @Test
    void pullsAnImageThereForAnotherArchitecture() {
        // Such as alpine on Apple silicon, when scanners run as amd64
        final var image = new InspectImageResponse().withOs("linux").withArch("arm64");

        assertThat(Containers.isFor(image, "linux/amd64")).isFalse();
    }

    @Test
    void ignoresTheVariant() {
        final var image = new InspectImageResponse().withOs("linux").withArch("arm64");

        assertThat(Containers.isFor(image, "linux/arm64/v8")).isTrue();
    }

    @Test
    void doesNotMatchAPlatformWithoutAnArchitecture() {
        final var image = new InspectImageResponse().withOs("linux").withArch("amd64");

        assertThat(Containers.isFor(image, "linux")).isFalse();
    }
}
