package dev.graphnous.persistence.notification;

import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;

/**
 * The notifications, and their recipients: a row for each user who marked
 * one read or unread, with no user where there is none. A notification is
 * read for its recipient as their row says, and unread without one.
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    /**
     * What stands for no user when matching recipients, as no user has no
     * id to compare; no user has this id.
     */
    static final UUID NO_USER = new UUID(0, 0);

    private final NotificationJpaRepository jpaRepository;
    private final NotificationRecipientJpaRepository recipients;
    private final Clock clock;

    public NotificationRepositoryImpl(
        final NotificationJpaRepository jpaRepository,
        final NotificationRecipientJpaRepository recipients
    ) {
        this.jpaRepository = jpaRepository;
        this.recipients = recipients;
        this.clock = Clock.systemUTC();
    }

    @Override
    public Notification save(final Notification notification) {
        return toDomain(this.jpaRepository.save(toEntity(notification)), notification.read());
    }

    @Override
    public Notification findById(final Notification.NotificationId id, final UserContext recipient) {
        return this.jpaRepository.findRow(id.id(), user(recipient), NO_USER)
            .map(NotificationRepositoryImpl::toDomain)
            .orElseThrow(() -> new NotFoundException(id.id().toString()));
    }

    @Override
    public Page<Notification> findAll(final System.SystemId systemId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findRowsOfSystem(systemId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public Page<Notification> findAll(final Project.ProjectId projectId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findRowsOfProject(projectId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public Page<Notification> findAll(final Scan.ScanId scanId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findRowsOfScan(scanId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public Page<Notification> findUnread(final System.SystemId systemId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findUnreadOfSystem(systemId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public Page<Notification> findUnread(final Project.ProjectId projectId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findUnreadOfProject(projectId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public Page<Notification> findUnread(final Scan.ScanId scanId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findUnreadOfScan(scanId.id(), user(recipient), NO_USER, pageable));
    }

    @Override
    public long countUnread(final System.SystemId systemId, final UserContext recipient) {
        return this.jpaRepository.countUnreadOfSystem(systemId.id(), user(recipient), NO_USER);
    }

    @Override
    @Transactional
    public void markRead(final Notification.NotificationId id, final UserContext recipient, final boolean read) {
        final var userId = recipient == null ? null : recipient.id();
        final var row = this.recipients.findRecipient(id.id(), user(recipient), NO_USER).orElseGet(() -> {
            final var created = new NotificationRecipientEntity();
            created.setId(UUID.randomUUID());
            created.setNotificationId(id.id());
            created.setUserId(userId);
            return created;
        });

        row.setRead(read);
        row.setReadAt(read ? this.clock.instant() : null);

        this.recipients.save(row);
    }

    @Override
    public int markAllRead(final System.SystemId systemId, final UserContext recipient) {
        return recipient == null || recipient.id() == null
            ? this.recipients.markAllReadWithoutUser(systemId.id())
            : this.recipients.markAllRead(systemId.id(), recipient.id());
    }

    @Override
    public void delete(final Notification.NotificationId id) {
        // Its recipients go with it, as the database cascades
        this.jpaRepository.deleteById(id.id());
    }

    @Override
    public void deleteBySystemId(final System.SystemId systemId) {
        this.jpaRepository.deleteBySystemId(systemId.id());
    }

    @Override
    public void deleteByProjectId(final Project.ProjectId projectId) {
        this.jpaRepository.deleteByProjectId(projectId.id());
    }

    @Override
    public void deleteByScanId(final Scan.ScanId scanId) {
        this.jpaRepository.deleteByScanId(scanId.id());
    }

    /**
     * The recipient's id as the queries match it.
     */
    private static UUID user(final UserContext recipient) {
        return recipient == null || recipient.id() == null ? NO_USER : recipient.id();
    }

    private static Page<Notification> page(
        final PageQuery pageQuery,
        final Function<Pageable, org.springframework.data.domain.Page<NotificationRow>> query
    ) {
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> Sort.by(pageQuery.sort().property()).ascending();
            case DESC -> Sort.by(pageQuery.sort().property()).descending();
        };

        final var result = query.apply(PageRequest.of(pageQuery.page(), pageQuery.size(), sort));

        return new Page<>(
            result
                .stream()
                .map(NotificationRepositoryImpl::toDomain)
                .toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    private static NotificationEntity toEntity(final Notification notification) {
        final var entity = new NotificationEntity();

        entity.setId(notification.id().id());
        entity.setSystemId(notification.systemId().id());
        entity.setProjectId(notification.projectId() == null ? null : notification.projectId().id());
        entity.setScanId(notification.scanId() == null ? null : notification.scanId().id());
        entity.setTitle(notification.title());
        entity.setContent(notification.content());
        entity.setCreatedAt(notification.createdAt());

        return entity;
    }

    private static Notification toDomain(final NotificationRow row) {
        return toDomain(row.notification(), Boolean.TRUE.equals(row.read()));
    }

    private static Notification toDomain(final NotificationEntity entity, final boolean read) {
        return new Notification(
            new Notification.NotificationId(entity.getId()),
            new System.SystemId(entity.getSystemId()),
            entity.getProjectId() == null ? null : new Project.ProjectId(entity.getProjectId()),
            entity.getScanId() == null ? null : new Scan.ScanId(entity.getScanId()),
            entity.getTitle(),
            entity.getContent(),
            read,
            entity.getCreatedAt()
        );
    }
}
