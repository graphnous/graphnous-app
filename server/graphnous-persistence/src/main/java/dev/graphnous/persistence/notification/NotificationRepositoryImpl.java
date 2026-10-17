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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The notifications, and their recipients: a row for each user who marked
 * one read or unread, with no user where there is none. A notification is
 * read for its recipient as their row says, and unread without one.
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

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
        final var entity = this.jpaRepository.findById(id.id())
            .orElseThrow(() -> new NotFoundException(id.id().toString()));

        return toDomain(entity, recipientOf(id.id(), recipient).map(NotificationRecipientEntity::isRead).orElse(false));
    }

    @Override
    public Page<Notification> findAll(final System.SystemId systemId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, recipient, pageable -> this.jpaRepository.findAllBySystemId(systemId.id(), pageable));
    }

    @Override
    public Page<Notification> findAll(final Project.ProjectId projectId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, recipient, pageable -> this.jpaRepository.findAllByProjectId(projectId.id(), pageable));
    }

    @Override
    public Page<Notification> findAll(final Scan.ScanId scanId, final UserContext recipient, final PageQuery pageQuery) {
        return page(pageQuery, recipient, pageable -> this.jpaRepository.findAllByScanId(scanId.id(), pageable));
    }

    @Override
    public Page<Notification> findUnread(final System.SystemId systemId, final UserContext recipient, final PageQuery pageQuery) {
        return unread(pageQuery, NotificationJpaRepository.ofSystem(systemId.id()), recipient);
    }

    @Override
    public Page<Notification> findUnread(final Project.ProjectId projectId, final UserContext recipient, final PageQuery pageQuery) {
        return unread(pageQuery, NotificationJpaRepository.ofProject(projectId.id()), recipient);
    }

    @Override
    public Page<Notification> findUnread(final Scan.ScanId scanId, final UserContext recipient, final PageQuery pageQuery) {
        return unread(pageQuery, NotificationJpaRepository.ofScan(scanId.id()), recipient);
    }

    @Override
    public long countUnread(final System.SystemId systemId, final UserContext recipient) {
        return this.jpaRepository.count(
            NotificationJpaRepository.ofSystem(systemId.id()).and(NotificationJpaRepository.unreadBy(userId(recipient)))
        );
    }

    @Override
    @Transactional
    public void markRead(final Notification.NotificationId id, final UserContext recipient, final boolean read) {
        final var row = recipientOf(id.id(), recipient).orElseGet(() -> newRecipient(id.id(), recipient));

        row.setRead(read);
        row.setReadAt(read ? this.clock.instant() : null);

        this.recipients.save(row);
    }

    /**
     * Marks the system's notifications the recipient did not read, adding
     * them as a recipient of those they never marked.
     */
    @Override
    @Transactional
    public int markAllRead(final System.SystemId systemId, final UserContext recipient) {
        final var unread = this.jpaRepository.findAll(
                NotificationJpaRepository.ofSystem(systemId.id()).and(NotificationJpaRepository.unreadBy(userId(recipient)))
            )
            .stream()
            .map(NotificationEntity::getId)
            .toList();

        if (unread.isEmpty()) {
            return 0;
        }

        final var rows = new HashMap<UUID, NotificationRecipientEntity>();
        recipientsOf(unread, recipient).forEach(row -> rows.put(row.getNotificationId(), row));

        final var now = this.clock.instant();
        final var marked = unread.stream()
            .map(notificationId -> {
                final var row = rows.computeIfAbsent(notificationId, id -> newRecipient(id, recipient));
                row.setRead(true);
                row.setReadAt(now);
                return row;
            })
            .toList();

        this.recipients.saveAll(marked);

        return marked.size();
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
     * The recipient's id; null for no user, as without security.
     */
    private static UUID userId(final UserContext recipient) {
        return recipient == null ? null : recipient.id();
    }

    private Optional<NotificationRecipientEntity> recipientOf(final UUID notificationId, final UserContext recipient) {
        final var userId = userId(recipient);

        return userId == null
            ? this.recipients.findByNotificationIdAndUserIdIsNull(notificationId)
            : this.recipients.findByNotificationIdAndUserId(notificationId, userId);
    }

    private List<NotificationRecipientEntity> recipientsOf(final List<UUID> notificationIds, final UserContext recipient) {
        final var userId = userId(recipient);

        return userId == null
            ? this.recipients.findAllByNotificationIdInAndUserIdIsNull(notificationIds)
            : this.recipients.findAllByNotificationIdInAndUserId(notificationIds, userId);
    }

    private static NotificationRecipientEntity newRecipient(final UUID notificationId, final UserContext recipient) {
        final var row = new NotificationRecipientEntity();
        row.setId(UUID.randomUUID());
        row.setNotificationId(notificationId);
        row.setUserId(userId(recipient));

        return row;
    }

    private Page<Notification> unread(
        final PageQuery pageQuery,
        final Specification<NotificationEntity> of,
        final UserContext recipient
    ) {
        // None of them is read for the recipient
        final var result = this.jpaRepository.findAll(of.and(NotificationJpaRepository.unreadBy(userId(recipient))), pageable(pageQuery));

        return new Page<>(
            result.stream().map(entity -> toDomain(entity, false)).toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    /**
     * The page of notifications, each read or not as the recipient's row
     * says: one lookup of the rows of the page.
     */
    private Page<Notification> page(
        final PageQuery pageQuery,
        final UserContext recipient,
        final Function<Pageable, org.springframework.data.domain.Page<NotificationEntity>> query
    ) {
        final var result = query.apply(pageable(pageQuery));
        final var ids = result.stream().map(NotificationEntity::getId).toList();

        final var read = new HashSet<UUID>();

        if (!ids.isEmpty()) {
            recipientsOf(ids, recipient).stream()
                .filter(NotificationRecipientEntity::isRead)
                .forEach(row -> read.add(row.getNotificationId()));
        }

        return new Page<>(
            result.stream().map(entity -> toDomain(entity, read.contains(entity.getId()))).toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    private static Pageable pageable(final PageQuery pageQuery) {
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> Sort.by(pageQuery.sort().property()).ascending();
            case DESC -> Sort.by(pageQuery.sort().property()).descending();
        };

        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
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
