package dev.graphnous.persistence.notification;

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

import java.util.function.Function;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryImpl(final NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(final Notification notification) {
        return toDomain(this.jpaRepository.save(toEntity(notification)));
    }

    @Override
    public Notification findById(final Notification.NotificationId id) {
        return this.jpaRepository.findById(id.id())
            .map(NotificationRepositoryImpl::toDomain)
            .orElseThrow(() -> new NotFoundException(id.id().toString()));
    }

    @Override
    public Page<Notification> findAll(final System.SystemId systemId, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findAllBySystemId(systemId.id(), pageable));
    }

    @Override
    public Page<Notification> findAll(final Project.ProjectId projectId, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findAllByProjectId(projectId.id(), pageable));
    }

    @Override
    public Page<Notification> findAll(final Scan.ScanId scanId, final PageQuery pageQuery) {
        return page(pageQuery, pageable -> this.jpaRepository.findAllByScanId(scanId.id(), pageable));
    }

    @Override
    public void delete(final Notification.NotificationId id) {
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

    private static Page<Notification> page(
        final PageQuery pageQuery,
        final Function<Pageable, org.springframework.data.domain.Page<NotificationEntity>> query
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
        entity.setRead(notification.read());
        entity.setCreatedAt(notification.createdAt());

        return entity;
    }

    private static Notification toDomain(final NotificationEntity entity) {
        return new Notification(
            new Notification.NotificationId(entity.getId()),
            new System.SystemId(entity.getSystemId()),
            entity.getProjectId() == null ? null : new Project.ProjectId(entity.getProjectId()),
            entity.getScanId() == null ? null : new Scan.ScanId(entity.getScanId()),
            entity.getTitle(),
            entity.getContent(),
            entity.isRead(),
            entity.getCreatedAt()
        );
    }
}
