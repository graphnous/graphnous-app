package dev.graphnous.persistence.chat;

import dev.graphnous.application.chat.ChatThreadRepository;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatThreadRepositoryImpl implements ChatThreadRepository {

    private final ChatThreadJpaRepository jpaRepository;

    public ChatThreadRepositoryImpl(final ChatThreadJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ChatThread save(final ChatThread thread) {
        return toDomain(this.jpaRepository.save(toEntity(thread)));
    }

    @Override
    public Optional<ChatThread> findById(final ChatThread.ChatThreadId id) {
        return this.jpaRepository.findById(id.id()).map(ChatThreadRepositoryImpl::toDomain);
    }

    @Override
    public Page<ChatThread> findAll(
        final System.SystemId systemId,
        final UUID userId,
        final PageQuery pageQuery
    ) {
        final var pageable = pageable(pageQuery);

        // A parameter equal to null matches no row, so null is asked for apart
        return page(userId == null
            ? this.jpaRepository.findAllBySystemIdAndUserIdIsNullAndProjectIdIsNull(systemId.id(), pageable)
            : this.jpaRepository.findAllBySystemIdAndUserIdAndProjectIdIsNull(systemId.id(), userId, pageable));
    }

    @Override
    public Page<ChatThread> findAllOfProject(
        final Project.ProjectId projectId,
        final UUID userId,
        final PageQuery pageQuery
    ) {
        final var pageable = pageable(pageQuery);

        return page(userId == null
            ? this.jpaRepository.findAllByProjectIdAndUserIdIsNull(projectId.id(), pageable)
            : this.jpaRepository.findAllByProjectIdAndUserId(projectId.id(), userId, pageable));
    }

    private static PageRequest pageable(final PageQuery pageQuery) {
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> Sort.by(pageQuery.sort().property()).ascending();
            case DESC -> Sort.by(pageQuery.sort().property()).descending();
        };

        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
    }

    private static Page<ChatThread> page(final org.springframework.data.domain.Page<ChatThreadEntity> result) {
        return new Page<>(
            result.stream().map(ChatThreadRepositoryImpl::toDomain).toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    @Override
    public void delete(final ChatThread.ChatThreadId id) {
        this.jpaRepository.deleteById(id.id());
    }

    @Override
    public void deleteByProjectId(final Project.ProjectId projectId) {
        this.jpaRepository.deleteByProjectId(projectId.id());
    }

    @Override
    public void deleteBySystemId(final System.SystemId systemId) {
        this.jpaRepository.deleteBySystemId(systemId.id());
    }

    private static ChatThreadEntity toEntity(final ChatThread thread) {
        final var entity = new ChatThreadEntity();

        entity.setId(thread.id().id());
        entity.setSystemId(thread.systemId().id());
        entity.setUserId(thread.userId());
        entity.setProjectId(thread.projectId() == null ? null : thread.projectId().id());
        entity.setTitle(thread.title());
        entity.setMessages(thread.messages());
        entity.setCreatedAt(thread.createdAt());
        entity.setUpdatedAt(thread.updatedAt());

        return entity;
    }

    private static ChatThread toDomain(final ChatThreadEntity entity) {
        return new ChatThread(
            new ChatThread.ChatThreadId(entity.getId()),
            new System.SystemId(entity.getSystemId()),
            entity.getUserId(),
            entity.getTitle(),
            entity.getMessages(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getProjectId() == null ? null : new Project.ProjectId(entity.getProjectId())
        );
    }
}
