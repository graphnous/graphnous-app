package dev.graphnous.domain.notification;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final Instant CREATED = Instant.parse("2026-10-07T10:00:00Z");

    private final System.SystemId systemId = System.SystemId.generate();
    private final Project.ProjectId projectId = Project.ProjectId.generate();
    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void isAboutASystemAProjectOrAScan() {
        assertThat(notification(null, null, "Title", "Content").projectId()).isNull();
        assertThat(notification(projectId, null, "Title", "Content").projectId()).isEqualTo(projectId);
        assertThat(notification(projectId, scanId, "Title", "Content").scanId()).isEqualTo(scanId);
    }

    @Test
    void needsASystem() {
        assertThatThrownBy(() -> new Notification(
            Notification.NotificationId.generate(), null, null, null, null, "Content", false, CREATED
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("system");
    }

    @Test
    void needsTheProjectOfAScan() {
        assertThatThrownBy(() -> notification(null, scanId, null, "Content"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("project");
    }

    @Test
    void needsContent() {
        assertThatThrownBy(() -> notification(null, null, "Title", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content");
    }

    @Test
    void hasNoTitleForABlankOne() {
        assertThat(notification(null, null, " ", "Content").title()).isNull();
    }

    @Test
    void isMarkedReadAndUnread() {
        final var notification = notification(projectId, scanId, "Title", "Content");

        final var read = notification.withRead(true);

        assertThat(notification.read()).isFalse();
        assertThat(read.read()).isTrue();
        assertThat(read.withRead(false)).isEqualTo(notification);
    }

    private Notification notification(
        final Project.ProjectId projectId,
        final Scan.ScanId scanId,
        final String title,
        final String content
    ) {
        return new Notification(
            Notification.NotificationId.generate(),
            systemId,
            projectId,
            scanId,
            title,
            content,
            false,
            CREATED
        );
    }
}
