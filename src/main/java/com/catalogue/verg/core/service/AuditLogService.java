package com.catalogue.verg.core.service;

import com.catalogue.verg.audit.service.AuditService;
import com.catalogue.verg.core.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

/**
 * Shared, cross-cutting audit-trail helper. Entity services call {@link #logAudit} to record a
 * lifecycle/read/search event; this builds the audit payload and delegates to the audit module's
 * {@link AuditService#createAudit(JsonNode)}.
 *
 * <p><b>Best-effort:</b> audit failures (validation, ES/DB/Redis errors) are caught and logged,
 * never propagated, so an audit problem cannot break the caller's operation.
 *
 * <p>Actor identity ({@code userId}/{@code userName}/{@code userRole}) is defaulted to placeholder
 * constants here, so it is not a parameter. {@code createdOn}/{@code updatedOn} carry the SOURCE
 * entity record's timestamps (supplied by the caller), not the audit row's own time — the audit
 * row's own time is stamped on the Postgres audit entity by {@code AuditServiceImpl.createAudit}.
 *
 * <p>Note: the audit write is currently synchronous (runs on the caller's thread before its
 * response returns). Making it asynchronous is a documented follow-up.
 */
@Slf4j
@Service
public class AuditLogService {

    // Audit payload field names (single location — mirrors auditPayloadValidation.json).
    private static final String FIELD_ENTITY_ID = "entityId";
    private static final String FIELD_ENTITY_NAME = "entityName";
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_USER_NAME = "userName";
    private static final String FIELD_USER_ROLE = "userRole";
    private static final String FIELD_AUDIT_STATUS = "auditStatus";
    private static final String FIELD_ENTITY_BEFORE = "entityBeforeChanges";
    private static final String FIELD_ENTITY_AFTER = "entityAfterChanges";
    private static final String FIELD_CREATED_ON = "createdOn";
    private static final String FIELD_UPDATED_ON = "updatedOn";

    private static final String EMPTY_JSON = "{}";

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Records a single audit event. Never throws — on any failure it logs and returns.
     *
     * @param entityId            primary key of the source record (optional; omitted when blank, e.g. read/search)
     * @param entityName          catalogue the record belongs to (e.g. {@code cropcategory})
     * @param operation           the endpoint/operation that produced this row (e.g. {@code create}, {@code read})
     * @param auditStatus         resulting lifecycle state (optional; omitted when blank, e.g. read/search)
     * @param entityBeforeChanges record before the action (optional; omitted when null)
     * @param entityAfterChanges  record (or endpoint response) after the action
     * @param createdOn           SOURCE entity record's createdOn (optional; omitted when null)
     * @param updatedOn           SOURCE entity record's updatedOn (optional; omitted when null)
     */
    public void logAudit(String entityId, String entityName, String userId, String userRole, String functionRole, String operation, String auditStatus,
                         JsonNode entityBeforeChanges, JsonNode entityAfterChanges,
                         Timestamp createdOn, Timestamp updatedOn) {
        try {
            ObjectNode node = objectMapper.createObjectNode();

            // Always present (actor identity defaulted to placeholders until real context exists).
            node.put(FIELD_ENTITY_NAME, entityName);
            node.put(FIELD_OPERATION, operation);
            node.put(FIELD_USER_ID, userId);
            node.put(FIELD_USER_NAME, userRole);
            node.put(FIELD_USER_ROLE, functionRole);

            node.put(FIELD_ENTITY_AFTER, stringify(entityAfterChanges));

            // Optional — included only when supplied.
            if (StringUtils.isNotEmpty(entityId)) {
                node.put(FIELD_ENTITY_ID, entityId);
            }
            if (StringUtils.isNotEmpty(auditStatus)) {
                node.put(FIELD_AUDIT_STATUS, auditStatus);
            }
            if (entityBeforeChanges != null) {
                node.put(FIELD_ENTITY_BEFORE, stringify(entityBeforeChanges));
            }
            // Source entity record's timestamps (not the audit row's own time).
            if (createdOn != null) {
                node.put(FIELD_CREATED_ON, createdOn.toInstant().toString());
            }
            if (updatedOn != null) {
                node.put(FIELD_UPDATED_ON, updatedOn.toInstant().toString());
            }

            auditService.createAudit(node);
            log.debug("AuditLogService::logAudit: recorded audit for entity={} id={} operation={} status={}",
                    entityName, entityId, operation, auditStatus);
        } catch (Exception e) {
            log.error("AuditLogService::logAudit: audit write failed (non-fatal) for entity={} id={} operation={}",
                    entityName, entityId, operation, e);
        }
    }

    /** Serialize a node to stringified JSON, defaulting null to an empty object. */
    private String stringify(JsonNode node) throws Exception {
        return node == null ? EMPTY_JSON : objectMapper.writeValueAsString(node);
    }
}
