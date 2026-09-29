package com.catalogue.verg.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.util.List;

/**
 * Stamps "who did what, and when" onto a record's JSON payload so the UI can show
 * names (not raw user ids) in the approval queues and the My Submission screen.
 *
 * <p>Fields written into {@code data}:
 * <ul>
 *   <li>{@code createdBy / createdByName}   - maker who created the record</li>
 *   <li>{@code updatedBy / updatedByName}   - last user to touch it</li>
 *   <li>{@code approvedBy / approvedByName / approvedOn} - L1 approver ({@code approve -> APPROVED})</li>
 *   <li>{@code reviewedBy / reviewedByName / reviewedOn} - L2 reviewer ({@code review -> ACTIVE})</li>
 *   <li>{@code rejectedBy / rejectedByName / rejectedOn} - whoever rejected or sent it back for rework</li>
 * </ul>
 * Timestamps are ISO-8601 strings, the same format as {@code createdOn} / {@code updatedOn}.
 */
public final class AuditStampUtil {

    private AuditStampUtil() {
    }

    public static final String CREATED_BY = "createdBy";
    public static final String CREATED_BY_NAME = "createdByName";
    public static final String UPDATED_BY = "updatedBy";
    public static final String UPDATED_BY_NAME = "updatedByName";
    public static final String APPROVED_BY = "approvedBy";
    public static final String APPROVED_BY_NAME = "approvedByName";
    public static final String APPROVED_ON = "approvedOn";
    public static final String REVIEWED_BY = "reviewedBy";
    public static final String REVIEWED_BY_NAME = "reviewedByName";
    public static final String REVIEWED_ON = "reviewedOn";
    public static final String REJECTED_BY = "rejectedBy";
    public static final String REJECTED_BY_NAME = "rejectedByName";
    public static final String REJECTED_ON = "rejectedOn";

    private static final List<String> CREATOR_FIELDS = List.of(
            CREATED_BY, CREATED_BY_NAME, UPDATED_BY, UPDATED_BY_NAME);

    private static final List<String> DECISION_FIELDS = List.of(
            APPROVED_BY, APPROVED_BY_NAME, APPROVED_ON,
            REVIEWED_BY, REVIEWED_BY_NAME, REVIEWED_ON,
            REJECTED_BY, REJECTED_BY_NAME, REJECTED_ON);

    /** New record: caller is both creator and last updater. Client-supplied audit fields are discarded. */
    public static void stampCreate(ObjectNode payload, JsonNode userContext) {
        payload.remove(DECISION_FIELDS);
        String id = userContext.path("userId").asText(null);
        String name = userContext.path("userName").asText(null);
        payload.put(CREATED_BY, id);
        payload.put(CREATED_BY_NAME, name);
        payload.put(UPDATED_BY, id);
        payload.put(UPDATED_BY_NAME, name);
    }

    /**
     * (Re-)submit from DRAFT/REWORK: keep the original creator, update "updatedBy", and clear every
     * earlier approve/review/reject stamp because the record starts the approval chain again.
     */
    public static void stampResubmit(ObjectNode payload, JsonNode before, JsonNode userContext) {
        payload.remove(DECISION_FIELDS);
        String existingId = before == null ? null : before.path(CREATED_BY).asText(null);
        String existingName = before == null ? null : before.path(CREATED_BY_NAME).asText(null);
        if (existingId != null) {
            payload.put(CREATED_BY, existingId);
            if (existingName != null) {
                payload.put(CREATED_BY_NAME, existingName);
            } else {
                payload.remove(CREATED_BY_NAME);
            }
        } else {
            // Legacy record with no creator stamped: fall back to the submitter
            payload.put(CREATED_BY, userContext.path("userId").asText(null));
            payload.put(CREATED_BY_NAME, userContext.path("userName").asText(null));
        }
        payload.put(UPDATED_BY, userContext.path("userId").asText(null));
        payload.put(UPDATED_BY_NAME, userContext.path("userName").asText(null));
    }

    /**
     * Plain update (which replaces {@code data} wholesale): carry every audit field over from the
     * stored record so an edit cannot wipe the maker / approver information.
     */
    public static void carryOver(ObjectNode payload, JsonNode existing) {
        payload.remove(CREATOR_FIELDS);
        payload.remove(DECISION_FIELDS);
        if (existing == null || !existing.isObject()) {
            return;
        }
        for (String f : CREATOR_FIELDS) {
            if (existing.hasNonNull(f)) {
                payload.set(f, existing.get(f));
            }
        }
        for (String f : DECISION_FIELDS) {
            if (existing.hasNonNull(f)) {
                payload.set(f, existing.get(f));
            }
        }
    }

    /**
     * Records an approve/review decision on a deep COPY of the stored data (the original is left
     * untouched so it can still be used as the audit "before" image). Returns the stamped copy.
     */
    public static JsonNode stampDecision(JsonNode data, String operation, String targetStatus,
                                         JsonNode userContext, Timestamp when) {
        ObjectNode copy = (data != null && data.isObject())
                ? ((ObjectNode) data).deepCopy()
                : new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        String id = userContext.path("userId").asText(null);
        String name = userContext.path("userName").asText(null);
        String on = when.toInstant().toString();

        if ("approve".equals(operation) && Constants.APPROVED.equals(targetStatus)) {
            copy.put(APPROVED_BY, id);
            copy.put(APPROVED_BY_NAME, name);
            copy.put(APPROVED_ON, on);
        } else if ("review".equals(operation) && Constants.ACTIVE.equals(targetStatus)) {
            copy.put(REVIEWED_BY, id);
            copy.put(REVIEWED_BY_NAME, name);
            copy.put(REVIEWED_ON, on);
        } else if (Constants.REJECTED.equals(targetStatus) || Constants.REWORK.equals(targetStatus)) {
            copy.put(REJECTED_BY, id);
            copy.put(REJECTED_BY_NAME, name);
            copy.put(REJECTED_ON, on);
        }
        copy.put(UPDATED_BY, id);
        copy.put(UPDATED_BY_NAME, name);
        return copy;
    }
}