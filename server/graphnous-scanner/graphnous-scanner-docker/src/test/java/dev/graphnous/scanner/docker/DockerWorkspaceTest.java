package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.model.MountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Volume paths are Linux paths")
class DockerWorkspaceTest {

    @Test
    void bindsHostDirectoryReadOnlyAtWorkspace() {
        final var mounted = DockerWorkspace.hostDirectory().mount(Path.of("/home/me/repo/./"));

        assertThat(mounted.repository()).isEqualTo("/workspace");
        assertThat(mounted.mount().getType()).isEqualTo(MountType.BIND);
        assertThat(mounted.mount().getSource()).isEqualTo("/home/me/repo");
        assertThat(mounted.mount().getTarget()).isEqualTo("/workspace");
        assertThat(mounted.mount().getReadOnly()).isTrue();
    }

    @Test
    void mountsVolumeAtTheSamePathSoTheRepositoryPathStaysValid() {
        final var mounted = DockerWorkspace
            .volume("checkouts", Path.of("/checkouts"))
            .mount(Path.of("/checkouts/org/repo"));

        assertThat(mounted.repository()).isEqualTo("/checkouts/org/repo");
        assertThat(mounted.mount().getType()).isEqualTo(MountType.VOLUME);
        assertThat(mounted.mount().getSource()).isEqualTo("checkouts");
        assertThat(mounted.mount().getTarget()).isEqualTo("/checkouts");
        assertThat(mounted.mount().getReadOnly()).isTrue();
    }

    @Test
    void acceptsTheVolumeRootAsRepository() {
        final var mounted = DockerWorkspace
            .volume("checkouts", Path.of("/checkouts/"))
            .mount(Path.of("/checkouts"));

        assertThat(mounted.repository()).isEqualTo("/checkouts");
    }

    @Test
    void normalizesPaths() {
        final var mounted = DockerWorkspace
            .volume("checkouts", Path.of("/data/../checkouts"))
            .mount(Path.of("/checkouts/a/../repo"));

        assertThat(mounted.repository()).isEqualTo("/checkouts/repo");
        assertThat(mounted.mount().getTarget()).isEqualTo("/checkouts");
    }

    @Test
    void rejectsRepositoryOutsideTheVolume() {
        final var workspace = DockerWorkspace.volume("checkouts", Path.of("/checkouts"));

        assertThatThrownBy(() -> workspace.mount(Path.of("/checkouts-other/repo")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Repository /checkouts-other/repo is not inside volume checkouts, which is mounted at /checkouts");
    }

    @Test
    void rejectsRelativeMountPath() {
        assertThatThrownBy(() -> DockerWorkspace.volume("checkouts", Path.of("checkouts")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be absolute");
    }

    @Test
    void rejectsBlankVolumeName() {
        assertThatThrownBy(() -> DockerWorkspace.volume(" ", Path.of("/checkouts")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Volume name is required");
    }
}
