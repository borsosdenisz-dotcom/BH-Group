package com.bhstays.pms.service;

import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.AuditLog;
import com.bhstays.pms.dto.audit.AuditLogResponse;
import com.bhstays.pms.repository.AuditLogRepository;
import com.bhstays.pms.repository.AuditLogSpecifications;
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String entityName, String action, String actorEmail,
                                                  Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = AuditLogSpecifications.combine(
                AuditLogSpecifications.hasEntityName(entityName),
                AuditLogSpecifications.hasAction(action),
                AuditLogSpecifications.actorEmailContains(actorEmail),
                AuditLogSpecifications.createdFrom(from),
                AuditLogSpecifications.createdTo(to)
        );

        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
        return PageResponse.of(page, AuditService::toResponse);
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getEntityName(), log.getEntityId(), log.getAction(),
                log.getActorId(), log.getActorEmail(), log.getIpAddress(), log.getUserAgent(),
                log.getDescription(), log.getCreatedAt());
    }

    /**
     * Records an action the system took on its own (no logged-in actor),
     * inside the caller's transaction rather than a separate one: the audit
     * row commits or rolls back together with the change it describes.
     */
    @Transactional
    public void recordSystemEvent(AuditAction action, String entityName, UUID entityId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .entityName(entityName)
                .entityId(entityId != null ? entityId.toString() : null)
                .action(action.name())
                .description(description)
                .build());
    }

    /**
     * Records a change a logged-in user made to an entity, inside the
     * caller's transaction so the audit row commits or rolls back with the
     * change itself.
     */
    @Transactional
    public void recordEntityChange(AuditAction action, String entityName, UUID entityId, UUID actorId,
                                   String actorEmail, String description) {
        auditLogRepository.save(AuditLog.builder()
                .entityName(entityName)
                .entityId(entityId != null ? entityId.toString() : null)
                .action(action.name())
                .actorId(actorId)
                .actorEmail(actorEmail)
                .description(description)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, User actor, String description, String ipAddress, String userAgent) {
        try {
            AuditLog log = AuditLog.builder()
                    .entityName("User")
                    .entityId(actor != null ? actor.getId().toString() : null)
                    .action(action.name())
                    .actorId(actor != null ? actor.getId() : null)
                    .actorEmail(actor != null ? actor.getEmail() : null)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .description(description)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception ex) {
            log.error("Failed to persist audit log for action {}", action, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForUserId(AuditAction action, UUID actorId, String actorEmail, String description,
                                 String ipAddress, String userAgent) {
        try {
            AuditLog log = AuditLog.builder()
                    .entityName("User")
                    .entityId(actorId != null ? actorId.toString() : null)
                    .action(action.name())
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .description(description)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception ex) {
            log.error("Failed to persist audit log for action {}", action, ex);
        }
    }
}
