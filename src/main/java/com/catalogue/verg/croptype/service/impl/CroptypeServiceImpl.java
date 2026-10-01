package com.catalogue.verg.croptype.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.catalogue.verg.core.cache.CacheService;
import com.catalogue.verg.core.config.LifecyclePolicy;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.dto.PreviewDecisionRequest;
import com.catalogue.verg.core.dto.RespParam;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.core.elasticsearch.dto.SearchResult;
import com.catalogue.verg.core.elasticsearch.service.ESUtilService;
import com.catalogue.verg.core.exception.CustomException;
import com.catalogue.verg.core.util.AuditStampUtil;
import com.catalogue.verg.core.util.Constants;
import com.catalogue.verg.core.util.LifecycleUtil;
import com.catalogue.verg.core.util.PayloadValidation;
import com.catalogue.verg.core.util.VergProperties;
import com.catalogue.verg.core.service.AuditLogService;
import com.catalogue.verg.core.service.AuthValidationService;
import com.catalogue.verg.core.service.ImportService;
import com.catalogue.verg.core.service.LoadFromPrimaryService;
import com.catalogue.verg.core.util.PrimaryKeyUtil;
import com.catalogue.verg.croptype.entity.CroptypeEntity;
import com.catalogue.verg.croptype.repository.CroptypeRepository;
import com.catalogue.verg.croptype.service.CroptypeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.catalogue.verg.core.constants.NotificationTemplate;
import com.catalogue.verg.core.constants.NotificationTemplateConstants;
import com.catalogue.verg.core.service.NotificationUtil;
import com.catalogue.verg.core.util.NotificationTemplateResolver;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class CroptypeServiceImpl implements CroptypeService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private CroptypeRepository croptypeRepository;

    @Autowired
    private ESUtilService esUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Autowired
    private VergProperties vergProperties;

    @Autowired
    private ImportService importService;

    @Autowired
    private LoadFromPrimaryService loadFromPrimaryService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private LifecyclePolicy lifecyclePolicy;

    @Autowired
    private AuthValidationService authValidationService;

    @Autowired
    private NotificationUtil notificationUtil;

    /**
     * Catalogue name recorded on every audit row emitted by this service. Doubles as the key
     * this catalogue is looked up by in the lifecycle switches ({@link LifecyclePolicy}).
     */
    private static final String CATALOGUE_NAME = "croptype";
    private static final String TEMPLATE_NAME = "Croptype";
    private static final String TEMPLATE_CONSTANT = "CROPTYPE";


//    private static final int MAX_PREVIEW_BATCH = 500;

    private Logger logger = LoggerFactory.getLogger(CroptypeServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createCroptype(JsonNode croptypeEntity, String token, String operation, Boolean isPreviewRequired, JsonNode userContext) {
        log.info("CroptypeServiceImpl::createCroptype:entered the method: " + croptypeEntity);

        if (userContext == null) {
            // Validate the caller's api token against the OAS auth service
            userContext = authValidationService.validateToken(token);
            log.debug("CroptypeServiceImpl::createCroptype:token validated, user context: {}", userContext);
        }

        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.CROPTYPE_VALIDATION_FILE_JSON, croptypeEntity);

        log.debug("CroptypeServiceImpl::createCroptype:validated the payload");
        try {
            log.info("CroptypeServiceImpl::createCroptype:creating croptype");
            CroptypeEntity croptypeEntity1 = new CroptypeEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.CROPTYPE_VALIDATION_FILE_JSON);
            croptypeEntity1.setCroptypeId(primaryID);
            // Stamp createdBy/updatedBy into the payload itself, before it's persisted as `data`
            if (croptypeEntity instanceof ObjectNode) {
                AuditStampUtil.stampCreate((ObjectNode) croptypeEntity, userContext);
            }
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String initialStatus;
            if (Boolean.TRUE.equals(isPreviewRequired)) {
                initialStatus = Constants.PREVIEW;
            } else {
                initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            }
            croptypeEntity1.setCreatedOn(currentTime);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeEntity1.setStatus(initialStatus);
            croptypeEntity1.setData(croptypeEntity);

            croptypeRepository.save(croptypeEntity1);

            log.info("CroptypeServiceImpl::createCroptype::persisted croptype in postgres");
            ObjectNode jsonNode = buildDocument(croptypeEntity, initialStatus, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticCroptypeJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.CROPTYPE_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("CroptypeServiceImpl::createCroptype::persisted croptype in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, initialStatus,
                    objectMapper.createObjectNode(), croptypeEntity,
                    croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());

            // Lifecycle-disabled catalogues create ACTIVE records that are never reviewed
            if (lifecyclePolicy.isEnabledFor(CATALOGUE_NAME) && !Boolean.TRUE.equals(isPreviewRequired) && vergProperties.isNotificationEnabled()
                    && StringUtils.isNotBlank(userContext.path("orgId").asText(null))) {
                notificationUtil.sendNotification(
                        TEMPLATE_NAME,
                        TEMPLATE_CONSTANT,
                        NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                        Map.of(
                                "makerName", userContext.path("userName").asText(null),
                                "submissionId", primaryID,
                                "submissionDate", currentTime.toString()
                        ),
                        userContext.path("orgId").asText(null)
                );
            }

            return response;

        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse searchCroptype(SearchCriteria searchCriteria, String token) {
        log.info("CroptypeServiceImpl::searchCroptype");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("CroptypeServiceImpl::searchCroptype:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null && !Boolean.TRUE.equals(searchCriteria.getOverrideCache())) {
            log.info("CroptypeServiceImpl::searchCroptype: croptype search result fetched from redis");
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
            auditLogService.logAudit(null, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "search", null, null,
                    objectMapper.valueToTree(searchResult), null, null);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && searchString.length() < 2) {
            createErrorResponse(response, "Minimum 3 characters are required to search",
                    HttpStatus.BAD_REQUEST,
                    Constants.FAILED_CONST);
            return response;
        }
        try {
            log.info("CroptypeServiceImpl::searchCroptype: croptype search result fetched from ES");
            searchResult =
                    esUtilService.searchDocuments(Constants.CROPTYPE_INDEX_NAME, searchCriteria);
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
            redisTemplate.opsForValue()
                    .set(generateRedisJwtTokenKey(searchCriteria), searchResult, searchResultRedisTtl,
                            TimeUnit.SECONDS);

            auditLogService.logAudit(null, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "search", null, null,
                    objectMapper.valueToTree(searchResult), null, null);
            return response;
        } catch (Exception e) {
            createErrorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.FAILED_CONST);
            //redisTemplate.opsForValue()
            //        .set(generateRedisJwtTokenKey(searchCriteria), searchResult, searchResultRedisTtl,
            //                TimeUnit.SECONDS);
            return response;
        }
    }

    @Override
    public CustomResponse assignCroptype(JsonNode croptypeEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("CroptypeServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("CroptypeServiceImpl::read:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        primaryKeyUtil.validateKey(Constants.CROPTYPE_VALIDATION_FILE_JSON, id);
        JsonNode auditAfter = null;
        Timestamp auditCreatedOn = null;
        Timestamp auditUpdatedOn = null;
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("CroptypeServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
                if (entityOptional.isPresent()) {
                    CroptypeEntity croptypeEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(croptypeEntity.getData(),
                            croptypeEntity.getStatus(), croptypeEntity.getCreatedOn(),
                            croptypeEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("CroptypeServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = croptypeEntity.getCreatedOn();
                    auditUpdatedOn = croptypeEntity.getUpdatedOn();
                } else {
                    response.setResponseCode(HttpStatus.NOT_FOUND);
                    response.setMessage(Constants.INVALID_ID);
                }
            }
        } catch (Exception e) {
            throw new CustomException(Constants.ERROR, "error while processing",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (auditAfter != null) {
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "read", null, null, auditAfter,
                    auditCreatedOn, auditUpdatedOn);
        }
        return response;
    }

    @Override
    public CustomResponse updateCroptype(String id, JsonNode croptypeEntity) {
        log.info("CroptypeServiceImpl::updateCroptype:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("CroptypeServiceImpl::updateCroptype:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.CROPTYPE_VALIDATION_FILE_JSON, croptypeEntity);
        log.debug("CroptypeServiceImpl::updateCroptype:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("CroptypeServiceImpl::updateCroptype:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            CroptypeEntity croptypeEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(croptypeEntity1.getStatus())) {
                log.warn("CroptypeServiceImpl::updateCroptype:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            if (croptypeEntity instanceof ObjectNode) {
                AuditStampUtil.carryOver((ObjectNode) croptypeEntity, croptypeEntity1.getData());
            }
            croptypeEntity1.setData(croptypeEntity);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeRepository.save(croptypeEntity1);
            log.info("CroptypeServiceImpl::updateCroptype:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(croptypeEntity, croptypeEntity1.getStatus(),
                    croptypeEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticCroptypeJsonPath());
            log.info("CroptypeServiceImpl::updateCroptype:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("CroptypeServiceImpl::updateCroptype:refreshed cache for id: {}", id);

            map.put(Constants.CROPTYPE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("CroptypeServiceImpl::updateCroptype:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse delete(String id, String token) {
        log.info("CroptypeServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("CroptypeServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("CroptypeServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            CroptypeEntity croptypeEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(croptypeEntity.getStatus())) {
                log.warn("CroptypeServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            croptypeEntity.setStatus(Constants.DELETED);
            croptypeEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            croptypeRepository.save(croptypeEntity);
            log.info("CroptypeServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.CROPTYPE_INDEX_NAME);
            log.info("CroptypeServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("CroptypeServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    croptypeEntity.getData(), croptypeEntity.getData(),
                    croptypeEntity.getCreatedOn(), croptypeEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("CroptypeServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("CroptypeServiceImpl::importData::started");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::importData:token validated, user context: {}", userContext);

        CustomResponse response = importService.processBulkImport(
                file,
                Constants.CROPTYPE_VALIDATION_FILE_JSON,
                payload -> createCroptype(payload, token, "import", false, userContext)   // every row is created as the calling user
        );

        JsonNode importStats = objectMapper.valueToTree(response.getResult());
        auditLogService.logAudit(null, CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                "import", null, null, importStats, null, null);

        return response;
    }

    @Override
    public CustomResponse importDataWithPreview(MultipartFile file, String token) {
        log.info("CroptypeServiceImpl :: importDataWithPreview :: started");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl :: importDataWithPreview : token validated, user context: {}", userContext);

        CustomResponse response = importService.processBulkImport(
                file,
                Constants.CROPTYPE_VALIDATION_FILE_JSON,
                payload -> createCroptype(payload, token, "import", true, userContext)   // every row is created as the calling user
        );

        JsonNode importStats = objectMapper.valueToTree(response.getResult());
        auditLogService.logAudit(null, CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                "importDataWithPreview", null, null, importStats, null, null);

        return response;
    }

    @Override
    public CustomResponse decidePreview(PreviewDecisionRequest request, String token) {
        log.info("CroptypeServiceImpl::decidePreview:entered the method");

        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::decidePreview:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Matched case-insensitively, in the same trim-and-fold style as LifecycleUtil.normalizeTarget
        String decision = request == null || request.getDecision() == null
                ? null
                : request.getDecision().trim().toLowerCase(Locale.ROOT);
        boolean confirm = Constants.CONFIRM.equals(decision);
        if (!confirm && !Constants.DISCARD.equals(decision)) {
            log.warn("CroptypeServiceImpl::decidePreview:invalid decision '{}'",
                    request == null ? null : request.getDecision());
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_DECISION);
            return response;
        }

        // De-duplicate up front: a repeated id would otherwise be processed twice, and the second pass
        // would report a spurious failure because the record is no longer PREVIEW.
        Set<String> ids = new LinkedHashSet<>();
        if (request.getIds() != null) {
            for (String requestedId : request.getIds()) {
                if (StringUtils.isNotBlank(requestedId)) {
                    ids.add(requestedId.trim());
                }
            }
        }
        if (ids.isEmpty()) {
            log.warn("CroptypeServiceImpl::decidePreview:no usable ids in the request");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
//        if (ids.size() > MAX_PREVIEW_BATCH) {
//            log.warn("CroptypeServiceImpl::decidePreview:batch of {} exceeds the limit of {}",
//                    ids.size(), MAX_PREVIEW_BATCH);
//            throw new CustomException(Constants.ERROR,
//                    "A maximum of " + MAX_PREVIEW_BATCH + " ids can be decided in one request",
//                    HttpStatus.BAD_REQUEST);
//        }


        String targetStatus = confirm ? lifecyclePolicy.initialStatus(CATALOGUE_NAME) : Constants.DELETED;
        String operation = confirm ? "confirmPreview" : "discardPreview";

        List<Map<String, Object>> successRecords = new ArrayList<>();
        List<Map<String, Object>> failureRecords = new ArrayList<>();
        List<String> confirmedIds = new ArrayList<>();

        // One lookup for the whole batch rather than a findById per id
        Map<String, CroptypeEntity> foundById = new HashMap<>();
        for (CroptypeEntity found : croptypeRepository.findAllById(ids)) {
            foundById.put(found.getCroptypeId(), found);
        }

        for (String id : ids) {
            try {
                CroptypeEntity croptypeEntity1 = foundById.get(id);
                if (croptypeEntity1 == null) {
                    log.warn("CroptypeServiceImpl::decidePreview:no record found for id: {}", id);
                    failureRecords.add(buildFailureRecord(id, Constants.INVALID_ID));
                    continue;
                }

                if (!Constants.PREVIEW.equals(croptypeEntity1.getStatus())) {
                    log.warn("CroptypeServiceImpl::decidePreview:record {} is {}, requires {}",
                            id, croptypeEntity1.getStatus(), Constants.PREVIEW);
                    failureRecords.add(buildFailureRecord(id, Constants.INVALID_STATUS_TRANSITION));
                    continue;
                }

                if (confirm) {
                    applyPreviewConfirm(croptypeEntity1, targetStatus, userContext, operation);
                    confirmedIds.add(id);
                } else {
                    applyPreviewDiscard(croptypeEntity1, userContext, operation);
                }

                Map<String, Object> successRecord = new HashMap<>();
                successRecord.put(Constants.ID, id);
                successRecord.put(Constants.STATUS, targetStatus);
                successRecords.add(successRecord);
                log.info("CroptypeServiceImpl::decidePreview:record {} moved {} -> {}",
                        id, Constants.PREVIEW, targetStatus);

            } catch (Exception e) {
                log.error("CroptypeServiceImpl::decidePreview:error while processing id: {}", id, e);
                failureRecords.add(buildFailureRecord(id, "Unexpected error: " + e.getMessage()));
            }
        }


        if (confirm && !confirmedIds.isEmpty() && lifecyclePolicy.isEnabledFor(CATALOGUE_NAME)
                && vergProperties.isNotificationEnabled()
                && StringUtils.isNotBlank(userContext.path("orgId").asText(null))) {
            notificationUtil.sendNotification(
                    TEMPLATE_NAME,
                    TEMPLATE_CONSTANT,
                    NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                    Map.of(
                            "makerName", userContext.path("userName").asText(""),
                            "submissionId", String.join(", ", confirmedIds),
                            "submissionDate", new Timestamp(System.currentTimeMillis()).toString()
                    ),
                    userContext.path("orgId").asText("")
            );
        }

        response.getResult().put("decision", decision);
        response.getResult().put("totalIds", ids.size());
        response.getResult().put("successCount", successRecords.size());
        response.getResult().put("failureCount", failureRecords.size());
        response.getResult().put("successRecords", successRecords);
        response.getResult().put("failureRecords", failureRecords);

        if (failureRecords.isEmpty()) {
            response.setResponseCode(HttpStatus.OK);
            response.setMessage("Preview " + decision + " completed");
        } else if (successRecords.isEmpty()) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage("Preview " + decision + " failed - all ids had errors");
        } else {
            response.setResponseCode(HttpStatus.OK);
            response.setMessage("Preview " + decision + " completed with some errors");
        }

        // Batch-level audit row alongside the per-id ones, mirroring importData
        auditLogService.logAudit(null, CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                operation, null, null,
                objectMapper.valueToTree(response.getResult()), null, null);

        log.info("CroptypeServiceImpl::decidePreview:{} completed. Total: {}, Success: {}, Failures: {}",
                decision, ids.size(), successRecords.size(), failureRecords.size());
        return response;
    }


    private void applyPreviewConfirm(CroptypeEntity croptypeEntity1, String targetStatus, JsonNode userContext,
                                     String operation) throws IOException {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        croptypeEntity1.setStatus(targetStatus);
        croptypeEntity1.setUpdatedOn(currentTime);
        croptypeRepository.save(croptypeEntity1);

        ObjectNode jsonNode = buildDocument(croptypeEntity1.getData(), targetStatus,
                croptypeEntity1.getCreatedOn(), currentTime);
        Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
        esUtilService.updateDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                croptypeEntity1.getCroptypeId(), map, vergProperties.getElasticCroptypeJsonPath());
        cacheService.putCache(croptypeEntity1.getCroptypeId(), jsonNode);

        auditLogService.logAudit(croptypeEntity1.getCroptypeId(), CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                operation, targetStatus,
                croptypeEntity1.getData(), croptypeEntity1.getData(),
                croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());
    }


    private void applyPreviewDiscard(CroptypeEntity croptypeEntity1, JsonNode userContext, String operation)
            throws IOException {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        croptypeEntity1.setStatus(Constants.DELETED);
        croptypeEntity1.setUpdatedOn(currentTime);
        croptypeRepository.save(croptypeEntity1);

        esUtilService.deleteDocument(croptypeEntity1.getCroptypeId(), Constants.CROPTYPE_INDEX_NAME);
        cacheService.deleteCache(croptypeEntity1.getCroptypeId());

        auditLogService.logAudit(croptypeEntity1.getCroptypeId(), CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                operation, Constants.DELETED,
                croptypeEntity1.getData(), croptypeEntity1.getData(),
                croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());
    }

    private Map<String, Object> buildFailureRecord(String id, String error) {
        Map<String, Object> failureRecord = new HashMap<>();
        failureRecord.put(Constants.ID, id);
        failureRecord.put("errors", error);
        return failureRecord;
    }

    @Override
    public CustomResponse loadFromPrimaryCroptype() {
        log.info("CroptypeServiceImpl::loadFromPrimaryCroptype::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.CROPTYPE_INDEX_NAME,
                vergProperties.getElasticCroptypeJsonPath(),
                croptypeRepository.findAll(),
                CroptypeEntity::getCroptypeId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftCroptype(JsonNode croptypeEntity, String token) {
        log.info("CroptypeServiceImpl::draftCroptype:entered the method: " + croptypeEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::draftCroptype:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.CROPTYPE_VALIDATION_FILE_JSON, croptypeEntity);
        log.debug("CroptypeServiceImpl::draftCroptype:validated the payload (relaxed)");
        try {
            CroptypeEntity croptypeEntity1 = new CroptypeEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.CROPTYPE_VALIDATION_FILE_JSON);
            croptypeEntity1.setCroptypeId(primaryID);
            if (croptypeEntity instanceof ObjectNode) {
                AuditStampUtil.stampCreate((ObjectNode) croptypeEntity, userContext);
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            croptypeEntity1.setCreatedOn(currentTime);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeEntity1.setStatus(Constants.DRAFT);
            croptypeEntity1.setData(croptypeEntity);

            croptypeRepository.save(croptypeEntity1);
            log.info("CroptypeServiceImpl::draftCroptype::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(croptypeEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticCroptypeJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.CROPTYPE_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), croptypeEntity,
                    croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse addCroptype(String id, JsonNode croptypeEntity, String token) {
        log.info("CroptypeServiceImpl::addCroptype:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::addCroptype:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        // Full validation: all required fields must be present to submit for approval
        payloadValidation.validatePayload(Constants.CROPTYPE_VALIDATION_FILE_JSON, croptypeEntity);
        log.debug("CroptypeServiceImpl::addCroptype:validated the payload");
        try {
            Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            CroptypeEntity croptypeEntity1 = entityOptional.get();
            // Only DRAFT or REWORK records can be (re-)submitted for approval
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(croptypeEntity1.getStatus())) {
                log.warn("CroptypeServiceImpl::addCroptype:record {} not in DRAFT/REWORK (status={})",
                        id, croptypeEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = croptypeEntity1.getData();
            // Preserve the original creator; only updatedBy changes to whoever is submitting
            if (croptypeEntity instanceof ObjectNode) {
                AuditStampUtil.stampResubmit((ObjectNode) croptypeEntity, auditBefore, userContext);
            }
            croptypeEntity1.setData(croptypeEntity);
            croptypeEntity1.setStatus(Constants.PENDING);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeRepository.save(croptypeEntity1);
            log.info("CroptypeServiceImpl::addCroptype:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(croptypeEntity, Constants.PENDING,
                    croptypeEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticCroptypeJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.CROPTYPE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, croptypeEntity,
                    croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());

            if (vergProperties.isNotificationEnabled()
                    && StringUtils.isNotBlank(userContext.path("orgId").asText(null))) {
                notificationUtil.sendNotification(
                        TEMPLATE_NAME,
                        TEMPLATE_CONSTANT,
                        NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                        Map.of(
                                "makerName", userContext.path("userName").asText(null),
                                "submissionId", id,
                                "submissionDate", currentTime.toString()
                        ),
                        userContext.path("orgId").asText(null)
                );
            }
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse approveCroptype(LifecycleRequest request, String token) {
        log.info("CroptypeServiceImpl::approveCroptype:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::approveCroptype:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewCroptype(LifecycleRequest request, String token) {
        log.info("CroptypeServiceImpl::reviewCroptype:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::reviewCroptype:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("CroptypeServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("CroptypeServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            CroptypeEntity croptypeEntity1 = entityOptional.get();
            String currentStatus = croptypeEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("CroptypeServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            croptypeEntity1.setStatus(newStatus);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeRepository.save(croptypeEntity1);
            log.info("CroptypeServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(croptypeEntity1.getData(), newStatus,
                    croptypeEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticCroptypeJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.CROPTYPE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    croptypeEntity1.getData(), croptypeEntity1.getData(),
                    croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Shared status-transition logic for approve/review. Validates the id and requested target status,
     * enforces the required current status, then persists the new status to Postgres, ES and Redis.
     */
    private CustomResponse transitionStatus(LifecycleRequest request, JsonNode userContext, String operation,
                                            String requiredCurrentStatus, Set<String> allowedTargets) {
        CustomResponse response = new CustomResponse();
        if (request == null || StringUtils.isEmpty(request.getId())) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        String id = request.getId();
        String targetStatus = LifecycleUtil.normalizeTarget(request.getStatus());
        if (targetStatus == null || !allowedTargets.contains(targetStatus)) {
            log.warn("CroptypeServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<CroptypeEntity> entityOptional = croptypeRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            CroptypeEntity croptypeEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(croptypeEntity1.getStatus())) {
                log.warn("CroptypeServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, croptypeEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode dataBefore = croptypeEntity1.getData();
            croptypeEntity1.setData(AuditStampUtil.stampDecision(
                    dataBefore, operation, targetStatus, userContext, currentTime));
            croptypeEntity1.setStatus(targetStatus);
            croptypeEntity1.setUpdatedOn(currentTime);
            croptypeRepository.save(croptypeEntity1);
            log.info("CroptypeServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(croptypeEntity1.getData(), targetStatus,
                    croptypeEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.CROPTYPE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticCroptypeJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.CROPTYPE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    dataBefore, croptypeEntity1.getData(),
                    croptypeEntity1.getCreatedOn(), croptypeEntity1.getUpdatedOn());

            if (vergProperties.isNotificationEnabled()
                    && StringUtils.isNotBlank(userContext.path("orgId").asText(null))) {
                List<NotificationTemplate> templates = NotificationTemplateResolver.resolveDecisionTemplates(
                        operation,
                        targetStatus
                );
                for (NotificationTemplate template : templates) {
                    notificationUtil.sendNotification(
                            TEMPLATE_NAME,
                            TEMPLATE_CONSTANT,
                            template,
                            Map.of(
                                    "makerName", userContext.path("userName").asText(null),
                                    "submissionId", id,
                                    "actionDate", currentTime.toString()
                            ),
                            userContext.path("orgId").asText(null)
                    );
                }
            }
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Builds the projection stored in Elasticsearch and Redis (and returned by read): the payload
     * plus the lifecycle status and the Postgres createdOn/updatedOn timestamps (ISO-8601). ES keeps
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esCroptypeRequiredFields.json.
     */
    private ObjectNode buildDocument(JsonNode data, String status, Timestamp createdOn, Timestamp updatedOn) {
        ObjectNode node = objectMapper.createObjectNode();
        if (data != null && data.isObject()) {
            node.setAll((ObjectNode) data);
        }
        node.put(Constants.STATUS, status);
        if (createdOn != null) {
            node.put(Constants.CREATED_ON, createdOn.toInstant().toString());
        }
        if (updatedOn != null) {
            node.put(Constants.UPDATED_ON, updatedOn.toInstant().toString());
        }
        return node;
    }

    public void createSuccessResponse(CustomResponse response) {
        response.setParams(new RespParam());
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
    }

    public String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload)+CATALOGUE_NAME;
                return JWT.create()
                        .withClaim(Constants.REQUEST_PAYLOAD, reqJsonString)
                        .sign(Algorithm.HMAC256(Constants.JWT_SECRET_KEY));
            } catch (JsonProcessingException e) {
                // logger.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }

    public void createErrorResponse(
            CustomResponse response, String errorMessage, HttpStatus httpStatus, String status) {
        response.setParams(new RespParam());
        response.getParams().setStatus(status);
        response.setResponseCode(httpStatus);
    }
}