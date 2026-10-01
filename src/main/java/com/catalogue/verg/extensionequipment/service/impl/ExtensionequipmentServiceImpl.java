package com.catalogue.verg.extensionequipment.service.impl;

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
import com.catalogue.verg.core.util.Constants;
import com.catalogue.verg.core.util.LifecycleUtil;
import com.catalogue.verg.core.util.PayloadValidation;
import com.catalogue.verg.core.util.VergProperties;
import com.catalogue.verg.core.service.AuditLogService;
import com.catalogue.verg.core.service.AuthValidationService;
import com.catalogue.verg.core.service.ImportService;
import com.catalogue.verg.core.service.LoadFromPrimaryService;
import com.catalogue.verg.core.util.PrimaryKeyUtil;
import com.catalogue.verg.extensionequipment.entity.ExtensionequipmentEntity;
import com.catalogue.verg.extensionequipment.repository.ExtensionequipmentRepository;
import com.catalogue.verg.extensionequipment.service.ExtensionequipmentService;
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
public class ExtensionequipmentServiceImpl implements ExtensionequipmentService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private ExtensionequipmentRepository extensionequipmentRepository;

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
    private static final String CATALOGUE_NAME = "extensionequipment";
    private static final String TEMPLATE_NAME = "Extensionequipment";
    private static final String TEMPLATE_CONSTANT = "EXTENSIONEQUIPMENT";


//    private static final int MAX_PREVIEW_BATCH = 500;

    private Logger logger = LoggerFactory.getLogger(ExtensionequipmentServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createExtensionequipment(JsonNode extensionequipmentEntity, String token, String operation, Boolean isPreviewRequired, JsonNode userContext) {
        log.info("ExtensionequipmentServiceImpl::createExtensionequipment:entered the method: " + extensionequipmentEntity);

        if (userContext == null) {
            // Validate the caller's api token against the OAS auth service
            userContext = authValidationService.validateToken(token);
            log.debug("ExtensionequipmentServiceImpl::createExtensionequipment:token validated, user context: {}", userContext);
        }

        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON, extensionequipmentEntity);

        log.debug("ExtensionequipmentServiceImpl::createExtensionequipment:validated the payload");
        try {
            log.info("ExtensionequipmentServiceImpl::createExtensionequipment:creating extensionequipment");
            ExtensionequipmentEntity extensionequipmentEntity1 = new ExtensionequipmentEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON);
            extensionequipmentEntity1.setExtensionequipmentId(primaryID);
            // Stamp createdBy/updatedBy into the payload itself, before it's persisted as `data`
            if (extensionequipmentEntity instanceof ObjectNode) {
                String makerId = userContext.path("userId").asText(null);
                ((ObjectNode) extensionequipmentEntity).put("createdBy", makerId);
                ((ObjectNode) extensionequipmentEntity).put("updatedBy", makerId);
            }
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            
            String initialStatus;
            if (Boolean.TRUE.equals(isPreviewRequired)) {
                initialStatus = Constants.PREVIEW;
            } else {
                initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            }
            extensionequipmentEntity1.setCreatedOn(currentTime);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentEntity1.setStatus(initialStatus);
            extensionequipmentEntity1.setData(extensionequipmentEntity);

            extensionequipmentRepository.save(extensionequipmentEntity1);

            log.info("ExtensionequipmentServiceImpl::createExtensionequipment::persisted extensionequipment in postgres");
            ObjectNode jsonNode = buildDocument(extensionequipmentEntity, initialStatus, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticExtensionequipmentJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("ExtensionequipmentServiceImpl::createExtensionequipment::persisted extensionequipment in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, initialStatus,
                    objectMapper.createObjectNode(), extensionequipmentEntity,
                    extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());

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
    public CustomResponse searchExtensionequipment(SearchCriteria searchCriteria, String token) {
        log.info("ExtensionequipmentServiceImpl::searchExtensionequipment");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("ExtensionequipmentServiceImpl::searchExtensionequipment:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null && !Boolean.TRUE.equals(searchCriteria.getOverrideCache())) {
            log.info("ExtensionequipmentServiceImpl::searchExtensionequipment: extensionequipment search result fetched from redis");
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
            log.info("ExtensionequipmentServiceImpl::searchExtensionequipment: extensionequipment search result fetched from ES");
            searchResult =
                    esUtilService.searchDocuments(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, searchCriteria);
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
    public CustomResponse assignExtensionequipment(JsonNode extensionequipmentEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("ExtensionequipmentServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("ExtensionequipmentServiceImpl::read:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        primaryKeyUtil.validateKey(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON, id);
        JsonNode auditAfter = null;
        Timestamp auditCreatedOn = null;
        Timestamp auditUpdatedOn = null;
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("ExtensionequipmentServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
                if (entityOptional.isPresent()) {
                    ExtensionequipmentEntity extensionequipmentEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(extensionequipmentEntity.getData(),
                            extensionequipmentEntity.getStatus(), extensionequipmentEntity.getCreatedOn(),
                            extensionequipmentEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("ExtensionequipmentServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = extensionequipmentEntity.getCreatedOn();
                    auditUpdatedOn = extensionequipmentEntity.getUpdatedOn();
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
    public CustomResponse updateExtensionequipment(String id, JsonNode extensionequipmentEntity) {
        log.info("ExtensionequipmentServiceImpl::updateExtensionequipment:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("ExtensionequipmentServiceImpl::updateExtensionequipment:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON, extensionequipmentEntity);
        log.debug("ExtensionequipmentServiceImpl::updateExtensionequipment:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("ExtensionequipmentServiceImpl::updateExtensionequipment:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            ExtensionequipmentEntity extensionequipmentEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(extensionequipmentEntity1.getStatus())) {
                log.warn("ExtensionequipmentServiceImpl::updateExtensionequipment:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            extensionequipmentEntity1.setData(extensionequipmentEntity);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentRepository.save(extensionequipmentEntity1);
            log.info("ExtensionequipmentServiceImpl::updateExtensionequipment:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(extensionequipmentEntity, extensionequipmentEntity1.getStatus(),
                    extensionequipmentEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticExtensionequipmentJsonPath());
            log.info("ExtensionequipmentServiceImpl::updateExtensionequipment:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("ExtensionequipmentServiceImpl::updateExtensionequipment:refreshed cache for id: {}", id);

            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("ExtensionequipmentServiceImpl::updateExtensionequipment:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse delete(String id, String token) {
        log.info("ExtensionequipmentServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("ExtensionequipmentServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("ExtensionequipmentServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            ExtensionequipmentEntity extensionequipmentEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(extensionequipmentEntity.getStatus())) {
                log.warn("ExtensionequipmentServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            extensionequipmentEntity.setStatus(Constants.DELETED);
            extensionequipmentEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            extensionequipmentRepository.save(extensionequipmentEntity);
            log.info("ExtensionequipmentServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.EXTENSIONEQUIPMENT_INDEX_NAME);
            log.info("ExtensionequipmentServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("ExtensionequipmentServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    extensionequipmentEntity.getData(), extensionequipmentEntity.getData(),
                    extensionequipmentEntity.getCreatedOn(), extensionequipmentEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("ExtensionequipmentServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("ExtensionequipmentServiceImpl::importData::started");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::importData:token validated, user context: {}", userContext);

        CustomResponse response = importService.processBulkImport(
                file,
                Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON,
                payload -> createExtensionequipment(payload, token, "import", false, userContext)   // every row is created as the calling user
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
        log.info("ExtensionequipmentServiceImpl :: importDataWithPreview :: started");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl :: importDataWithPreview : token validated, user context: {}", userContext);

        CustomResponse response = importService.processBulkImport(
                file,
                Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON,
                payload -> createExtensionequipment(payload, token, "import", true, userContext)   // every row is created as the calling user
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
        log.info("ExtensionequipmentServiceImpl::decidePreview:entered the method");

        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::decidePreview:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Matched case-insensitively, in the same trim-and-fold style as LifecycleUtil.normalizeTarget
        String decision = request == null || request.getDecision() == null
                ? null
                : request.getDecision().trim().toLowerCase(Locale.ROOT);
        boolean confirm = Constants.CONFIRM.equals(decision);
        if (!confirm && !Constants.DISCARD.equals(decision)) {
            log.warn("ExtensionequipmentServiceImpl::decidePreview:invalid decision '{}'",
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
            log.warn("ExtensionequipmentServiceImpl::decidePreview:no usable ids in the request");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
//        if (ids.size() > MAX_PREVIEW_BATCH) {
//            log.warn("ExtensionequipmentServiceImpl::decidePreview:batch of {} exceeds the limit of {}",
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
        Map<String, ExtensionequipmentEntity> foundById = new HashMap<>();
        for (ExtensionequipmentEntity found : extensionequipmentRepository.findAllById(ids)) {
            foundById.put(found.getExtensionequipmentId(), found);
        }

        for (String id : ids) {
            try {
                ExtensionequipmentEntity extensionequipmentEntity1 = foundById.get(id);
                if (extensionequipmentEntity1 == null) {
                    log.warn("ExtensionequipmentServiceImpl::decidePreview:no record found for id: {}", id);
                    failureRecords.add(buildFailureRecord(id, Constants.INVALID_ID));
                    continue;
                }

                if (!Constants.PREVIEW.equals(extensionequipmentEntity1.getStatus())) {
                    log.warn("ExtensionequipmentServiceImpl::decidePreview:record {} is {}, requires {}",
                            id, extensionequipmentEntity1.getStatus(), Constants.PREVIEW);
                    failureRecords.add(buildFailureRecord(id, Constants.INVALID_STATUS_TRANSITION));
                    continue;
                }

                if (confirm) {
                    applyPreviewConfirm(extensionequipmentEntity1, targetStatus, userContext, operation);
                    confirmedIds.add(id);
                } else {
                    applyPreviewDiscard(extensionequipmentEntity1, userContext, operation);
                }

                Map<String, Object> successRecord = new HashMap<>();
                successRecord.put(Constants.ID, id);
                successRecord.put(Constants.STATUS, targetStatus);
                successRecords.add(successRecord);
                log.info("ExtensionequipmentServiceImpl::decidePreview:record {} moved {} -> {}",
                        id, Constants.PREVIEW, targetStatus);

            } catch (Exception e) {
                log.error("ExtensionequipmentServiceImpl::decidePreview:error while processing id: {}", id, e);
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

        log.info("ExtensionequipmentServiceImpl::decidePreview:{} completed. Total: {}, Success: {}, Failures: {}",
                decision, ids.size(), successRecords.size(), failureRecords.size());
        return response;
    }


    private void applyPreviewConfirm(ExtensionequipmentEntity extensionequipmentEntity1, String targetStatus, JsonNode userContext,
                                     String operation) throws IOException {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        extensionequipmentEntity1.setStatus(targetStatus);
        extensionequipmentEntity1.setUpdatedOn(currentTime);
        extensionequipmentRepository.save(extensionequipmentEntity1);

        ObjectNode jsonNode = buildDocument(extensionequipmentEntity1.getData(), targetStatus,
                extensionequipmentEntity1.getCreatedOn(), currentTime);
        Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
        esUtilService.updateDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                extensionequipmentEntity1.getExtensionequipmentId(), map, vergProperties.getElasticExtensionequipmentJsonPath());
        cacheService.putCache(extensionequipmentEntity1.getExtensionequipmentId(), jsonNode);

        auditLogService.logAudit(extensionequipmentEntity1.getExtensionequipmentId(), CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                operation, targetStatus,
                extensionequipmentEntity1.getData(), extensionequipmentEntity1.getData(),
                extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());
    }


    private void applyPreviewDiscard(ExtensionequipmentEntity extensionequipmentEntity1, JsonNode userContext, String operation)
            throws IOException {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        extensionequipmentEntity1.setStatus(Constants.DELETED);
        extensionequipmentEntity1.setUpdatedOn(currentTime);
        extensionequipmentRepository.save(extensionequipmentEntity1);

        esUtilService.deleteDocument(extensionequipmentEntity1.getExtensionequipmentId(), Constants.EXTENSIONEQUIPMENT_INDEX_NAME);
        cacheService.deleteCache(extensionequipmentEntity1.getExtensionequipmentId());

        auditLogService.logAudit(extensionequipmentEntity1.getExtensionequipmentId(), CATALOGUE_NAME,
                userContext.path("userId").asText(null),
                userContext.path("userName").asText(null),
                userContext.path("functionalRole").asText(null),
                operation, Constants.DELETED,
                extensionequipmentEntity1.getData(), extensionequipmentEntity1.getData(),
                extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());
    }

    private Map<String, Object> buildFailureRecord(String id, String error) {
        Map<String, Object> failureRecord = new HashMap<>();
        failureRecord.put(Constants.ID, id);
        failureRecord.put("errors", error);
        return failureRecord;
    }

    @Override
    public CustomResponse loadFromPrimaryExtensionequipment() {
        log.info("ExtensionequipmentServiceImpl::loadFromPrimaryExtensionequipment::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.EXTENSIONEQUIPMENT_INDEX_NAME,
                vergProperties.getElasticExtensionequipmentJsonPath(),
                extensionequipmentRepository.findAll(),
                ExtensionequipmentEntity::getExtensionequipmentId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftExtensionequipment(JsonNode extensionequipmentEntity, String token) {
        log.info("ExtensionequipmentServiceImpl::draftExtensionequipment:entered the method: " + extensionequipmentEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::draftExtensionequipment:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON, extensionequipmentEntity);
        log.debug("ExtensionequipmentServiceImpl::draftExtensionequipment:validated the payload (relaxed)");
        try {
            ExtensionequipmentEntity extensionequipmentEntity1 = new ExtensionequipmentEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON);
            extensionequipmentEntity1.setExtensionequipmentId(primaryID);
            if (extensionequipmentEntity instanceof ObjectNode) {
                String makerId = userContext.path("userId").asText(null);
                ((ObjectNode) extensionequipmentEntity).put("createdBy", makerId);
                ((ObjectNode) extensionequipmentEntity).put("updatedBy", makerId);
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            extensionequipmentEntity1.setCreatedOn(currentTime);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentEntity1.setStatus(Constants.DRAFT);
            extensionequipmentEntity1.setData(extensionequipmentEntity);

            extensionequipmentRepository.save(extensionequipmentEntity1);
            log.info("ExtensionequipmentServiceImpl::draftExtensionequipment::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(extensionequipmentEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticExtensionequipmentJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), extensionequipmentEntity,
                    extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse addExtensionequipment(String id, JsonNode extensionequipmentEntity, String token) {
        log.info("ExtensionequipmentServiceImpl::addExtensionequipment:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::addExtensionequipment:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        // Full validation: all required fields must be present to submit for approval
        payloadValidation.validatePayload(Constants.EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON, extensionequipmentEntity);
        log.debug("ExtensionequipmentServiceImpl::addExtensionequipment:validated the payload");
        try {
            Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            ExtensionequipmentEntity extensionequipmentEntity1 = entityOptional.get();
            // Only DRAFT or REWORK records can be (re-)submitted for approval
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(extensionequipmentEntity1.getStatus())) {
                log.warn("ExtensionequipmentServiceImpl::addExtensionequipment:record {} not in DRAFT/REWORK (status={})",
                        id, extensionequipmentEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = extensionequipmentEntity1.getData();
            // Preserve the original creator; only updatedBy changes to whoever is submitting
            if (extensionequipmentEntity instanceof ObjectNode) {
                String existingCreatedBy = (auditBefore != null) ? auditBefore.path("createdBy").asText(null) : null;
                if (existingCreatedBy != null) {
                    ((ObjectNode) extensionequipmentEntity).put("createdBy", existingCreatedBy);
                }
                ((ObjectNode) extensionequipmentEntity).put("updatedBy", userContext.path("userId").asText(null));
            }
            extensionequipmentEntity1.setData(extensionequipmentEntity);
            extensionequipmentEntity1.setStatus(Constants.PENDING);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentRepository.save(extensionequipmentEntity1);
            log.info("ExtensionequipmentServiceImpl::addExtensionequipment:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(extensionequipmentEntity, Constants.PENDING,
                    extensionequipmentEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticExtensionequipmentJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, extensionequipmentEntity,
                    extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());

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
    public CustomResponse approveExtensionequipment(LifecycleRequest request, String token) {
        log.info("ExtensionequipmentServiceImpl::approveExtensionequipment:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::approveExtensionequipment:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewExtensionequipment(LifecycleRequest request, String token) {
        log.info("ExtensionequipmentServiceImpl::reviewExtensionequipment:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::reviewExtensionequipment:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("ExtensionequipmentServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("ExtensionequipmentServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            ExtensionequipmentEntity extensionequipmentEntity1 = entityOptional.get();
            String currentStatus = extensionequipmentEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("ExtensionequipmentServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            extensionequipmentEntity1.setStatus(newStatus);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentRepository.save(extensionequipmentEntity1);
            log.info("ExtensionequipmentServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(extensionequipmentEntity1.getData(), newStatus,
                    extensionequipmentEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticExtensionequipmentJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    extensionequipmentEntity1.getData(), extensionequipmentEntity1.getData(),
                    extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());
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
            log.warn("ExtensionequipmentServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<ExtensionequipmentEntity> entityOptional = extensionequipmentRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            ExtensionequipmentEntity extensionequipmentEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(extensionequipmentEntity1.getStatus())) {
                log.warn("ExtensionequipmentServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, extensionequipmentEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            extensionequipmentEntity1.setStatus(targetStatus);
            extensionequipmentEntity1.setUpdatedOn(currentTime);
            extensionequipmentRepository.save(extensionequipmentEntity1);
            log.info("ExtensionequipmentServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(extensionequipmentEntity1.getData(), targetStatus,
                    extensionequipmentEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.EXTENSIONEQUIPMENT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticExtensionequipmentJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.EXTENSIONEQUIPMENT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    extensionequipmentEntity1.getData(), extensionequipmentEntity1.getData(),
                    extensionequipmentEntity1.getCreatedOn(), extensionequipmentEntity1.getUpdatedOn());

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
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esExtensionequipmentRequiredFields.json.
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