package com.catalogue.verg.livestock.service.impl;

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
import com.catalogue.verg.livestock.entity.LivestockEntity;
import com.catalogue.verg.livestock.repository.LivestockRepository;
import com.catalogue.verg.livestock.service.LivestockService;
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

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.catalogue.verg.livestock.util.NotificationUtil;
import com.catalogue.verg.livestock.constants.NotificationTemplateConstants;
import org.springframework.security.core.context.SecurityContextHolder;
import com.catalogue.verg.livestock.constants.NotificationTemplate;

@Service
@Slf4j
public class LivestockServiceImpl implements LivestockService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private LivestockRepository livestockRepository;

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

    private final NotificationUtil notificationUtil;
    @Autowired
    private AuthValidationService authValidationService;

    @Autowired
    private NotificationUtil notificationUtil;

    /**
     * Catalogue name recorded on every audit row emitted by this service. Doubles as the key
     * this catalogue is looked up by in the lifecycle switches ({@link LifecyclePolicy}).
     */
    private static final String CATALOGUE_NAME = "livestock";
    private static final String TEMPLATE_NAME = "Livestock";
    private static final String TEMPLATE_CONSTANT = "LIVESTOCK";

    private Logger logger = LoggerFactory.getLogger(LivestockServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    public LivestockServiceImpl(NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public CustomResponse createLivestock(JsonNode livestockEntity, String token) {
        log.info("LivestockServiceImpl::createLivestock:entered the method: " + livestockEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::createLivestock:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        payloadValidation.validatePayload(
                Constants.LIVESTOCK_VALIDATION_FILE_JSON,
                livestockEntity
        );

        log.debug("LivestockServiceImpl::createLivestock:validated the payload");

        try {
            log.info("LivestockServiceImpl::createLivestock:creating livestock");

            LivestockEntity livestockEntity1 = new LivestockEntity();

            // Generate Primary Key
            String primaryID =
                    primaryKeyUtil.generateKey(Constants.LIVESTOCK_VALIDATION_FILE_JSON);

            livestockEntity1.setLivestockId(primaryID);

            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());

            String initialStatus = lifecyclePolicy.initialStatus(AUDIT_ENTITY_NAME);

            
            String initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            livestockEntity1.setCreatedOn(currentTime);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockEntity1.setStatus(initialStatus);
            livestockEntity1.setData(livestockEntity);

            livestockRepository.save(livestockEntity1);

            log.info(
                    "LivestockServiceImpl::createLivestock::persisted livestock in postgres"
            );

            ObjectNode jsonNode =
                    buildDocument(
                            livestockEntity,
                            initialStatus,
                            currentTime,
                            currentTime
                    );

            Map<String, Object> map =
                    objectMapper.convertValue(jsonNode, Map.class);

            esUtilService.addDocument(
                    Constants.LIVESTOCK_INDEX_NAME,
                    Constants.INDEX_TYPE,
                    String.valueOf(primaryID),
                    map,
                    vergProperties.getElasticLivestockJsonPath()
            );

            cacheService.putCache(primaryID, jsonNode);

          /*  // Fetch the currently authenticated maker
            String makerName = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "UNKNOWN";

            // Send notification to Supervisor
            notificationUtil.sendNotification(
                    NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                    Map.of(
                            "makerName", "makerName",
                            "submissionId", primaryID,
                            "submissionDate", currentTime.toString()
                    )
            );
*/
            // Send notification to Supervisor
            notificationUtil.sendNotification(
                    NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                    Map.of(
                            "makerName", "LO",
                            "submissionId", primaryID,
                            "submissionDate", currentTime.toString()
                    )
            );

            response.setMessage(Constants.SUCCESSFULLY_CREATED);

            map.put(Constants.LIVESTOCK_ID_RQST, primaryID);

            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);

            log.info(
                    "LivestockServiceImpl::createLivestock::persisted livestock in OAS"
            );

            auditLogService.logAudit(
                    primaryID,
                    AUDIT_ENTITY_NAME,
                    "create",
                    initialStatus,
                    objectMapper.createObjectNode(),
                    livestockEntity,
                    livestockEntity1.getCreatedOn(),
                    livestockEntity1.getUpdatedOn()
            );

            log.info("LivestockServiceImpl::createLivestock::persisted livestock in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "create", initialStatus,
                    objectMapper.createObjectNode(), livestockEntity,
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());

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

            return response;

        } catch (Exception e) {
            throw new CustomException(
                    "error while processing",
                    e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    public CustomResponse searchLivestock(SearchCriteria searchCriteria, String token) {
        log.info("LivestockServiceImpl::searchLivestock");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LivestockServiceImpl::searchLivestock:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("LivestockServiceImpl::searchLivestock: livestock search result fetched from redis");
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
            searchResult =
                    esUtilService.searchDocuments(Constants.LIVESTOCK_INDEX_NAME, searchCriteria);
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
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
            redisTemplate.opsForValue()
                    .set(generateRedisJwtTokenKey(searchCriteria), searchResult, searchResultRedisTtl,
                            TimeUnit.SECONDS);
            return response;
        }
    }

    @Override
    public CustomResponse assignLivestock(JsonNode livestockEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("LivestockServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LivestockServiceImpl::read:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        JsonNode auditAfter = null;
        Timestamp auditCreatedOn = null;
        Timestamp auditUpdatedOn = null;
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("LivestockServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
                if (entityOptional.isPresent()) {
                    LivestockEntity livestockEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(livestockEntity.getData(),
                            livestockEntity.getStatus(), livestockEntity.getCreatedOn(),
                            livestockEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("LivestockServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = livestockEntity.getCreatedOn();
                    auditUpdatedOn = livestockEntity.getUpdatedOn();
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

    /*@Override
    public CustomResponse updateLivestock(String id, JsonNode livestockEntity) {
        log.info("LivestockServiceImpl::updateLivestock:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LivestockServiceImpl::updateLivestock:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.LIVESTOCK_VALIDATION_FILE_JSON, livestockEntity);
        log.debug("LivestockServiceImpl::updateLivestock:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LivestockServiceImpl::updateLivestock:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LivestockEntity livestockEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(livestockEntity1.getStatus())) {
                log.warn("LivestockServiceImpl::updateLivestock:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setData(livestockEntity);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::updateLivestock:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(livestockEntity, livestockEntity1.getStatus(),
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            log.info("LivestockServiceImpl::updateLivestock:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("LivestockServiceImpl::updateLivestock:refreshed cache for id: {}", id);

            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        }

        catch (Exception e) {
            log.error("LivestockServiceImpl::updateLivestock:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }


        // Send notification to Supervisor
        notificationUtil.sendNotification(
                NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW,
                Map.of(
                        "makerName", "xyz",
                        "submissionId", primaryID,
                        "submissionDate", currentTime.toString()
                )
        );

        response.setMessage(Constants.SUCCESSFULLY_CREATED);

        map.put(Constants.LIVESTOCK_ID_RQST, primaryID);

        response.setResult(map);
        response.setResponseCode(HttpStatus.OK);

        log.info(
                "LivestockServiceImpl::createLivestock::persisted livestock in OAS"
        );

        auditLogService.logAudit(
                primaryID,
                AUDIT_ENTITY_NAME,
                "create",
                initialStatus,
                objectMapper.createObjectNode(),
                livestockEntity,
                livestockEntity1.getCreatedOn(),
                livestockEntity1.getUpdatedOn()
        );

        return response;
    }
*/

    @Override
    public CustomResponse updateLivestock(String id, JsonNode livestockEntity) {
        log.info("LivestockServiceImpl::updateLivestock:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        if (StringUtils.isEmpty(id)) {
            log.warn("LivestockServiceImpl::updateLivestock:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        payloadValidation.validatePayload(Constants.LIVESTOCK_VALIDATION_FILE_JSON, livestockEntity);
        log.debug("LivestockServiceImpl::updateLivestock:validated the payload");

        try {
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LivestockServiceImpl::updateLivestock:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LivestockEntity livestockEntity1 = entityOptional.get();

            if (Constants.DELETED.equals(livestockEntity1.getStatus())) {
                log.warn("LivestockServiceImpl::updateLivestock:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Status doesn't change on a plain update — capture it once, used below for both
            // the ES re-index and deciding which approver (if any) gets notified.
            String currentStatus = livestockEntity1.getStatus();

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setData(livestockEntity);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::updateLivestock:updated record in postgres for id: {}", id);

            ObjectNode jsonNode = buildDocument(livestockEntity, currentStatus,
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            log.info("LivestockServiceImpl::updateLivestock:updated document in elasticsearch for id: {}", id);

            cacheService.putCache(id, jsonNode);
            log.info("LivestockServiceImpl::updateLivestock:refreshed cache for id: {}", id);

            // Notify the current approver only if the record is sitting at PENDING (L1) or APPROVED (L2).
            // Any other status (DRAFT, REWORK, ACTIVE, REJECTED) sends nothing.
            NotificationTemplate template = resolvePlainUpdateTemplate(currentStatus);
            if (template != null) {
                String makerName = currentActorName();
                notificationUtil.sendNotification(
                        template,
                        Map.of(
                                "makerName", makerName,
                                "submissionId", id,
                                "updateDate", currentTime.toString()
                        )
                );
                log.info("LivestockServiceImpl::updateLivestock:notification {} sent for id: {} by: {}",
                        template.templateCode(), id, makerName);
            }

            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("LivestockServiceImpl::updateLivestock:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @Override
    public CustomResponse delete(String id) {
    public CustomResponse delete(String id, String token) {
        log.info("LivestockServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LivestockServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LivestockServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LivestockEntity livestockEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(livestockEntity.getStatus())) {
                log.warn("LivestockServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            livestockEntity.setStatus(Constants.DELETED);
            livestockEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            livestockRepository.save(livestockEntity);
            log.info("LivestockServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.LIVESTOCK_INDEX_NAME);
            log.info("LivestockServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("LivestockServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    livestockEntity.getData(), livestockEntity.getData(),
                    livestockEntity.getCreatedOn(), livestockEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("LivestockServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("LivestockServiceImpl::importData::started");
        return importService.processBulkImport(
                file,
                Constants.LIVESTOCK_VALIDATION_FILE_JSON,
                payload -> createLivestock(payload, token)   // every row is created as the calling user
        );
    }

    @Override
    public CustomResponse loadFromPrimaryLivestock() {
        log.info("LivestockServiceImpl::loadFromPrimaryLivestock::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.LIVESTOCK_INDEX_NAME,
                vergProperties.getElasticLivestockJsonPath(),
                livestockRepository.findAll(),
                LivestockEntity::getLivestockId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftLivestock(JsonNode livestockEntity, String token) {
        log.info("LivestockServiceImpl::draftLivestock:entered the method: " + livestockEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::draftLivestock:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.LIVESTOCK_VALIDATION_FILE_JSON, livestockEntity);
        log.debug("LivestockServiceImpl::draftLivestock:validated the payload (relaxed)");
        try {
            LivestockEntity livestockEntity1 = new LivestockEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.LIVESTOCK_VALIDATION_FILE_JSON);
            livestockEntity1.setLivestockId(primaryID);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setCreatedOn(currentTime);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockEntity1.setStatus(Constants.DRAFT);
            livestockEntity1.setData(livestockEntity);

            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::draftLivestock::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(livestockEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticLivestockJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.LIVESTOCK_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), livestockEntity,
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @Override
    public CustomResponse addLivestock(String id, JsonNode livestockEntity, String token) {
        log.info("LivestockServiceImpl::addLivestock:entered the method with id: {}", id);
        lifecyclePolicy.requireEnabled(AUDIT_ENTITY_NAME);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::addLivestock:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        payloadValidation.validatePayload(Constants.LIVESTOCK_VALIDATION_FILE_JSON, livestockEntity);
        log.debug("LivestockServiceImpl::addLivestock:validated the payload");
        try {
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LivestockEntity livestockEntity1 = entityOptional.get();
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(livestockEntity1.getStatus())) {
                log.warn("LivestockServiceImpl::addLivestock:record {} not in DRAFT/REWORK (status={})",
                        id, livestockEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }

            // Capture status BEFORE overwrite, so we know submit (DRAFT) vs resubmit (REWORK)
            String previousStatus = livestockEntity1.getStatus();

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = livestockEntity1.getData();
            livestockEntity1.setData(livestockEntity);
            livestockEntity1.setStatus(Constants.PENDING);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::addLivestock:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(livestockEntity, Constants.PENDING,
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            cacheService.putCache(id, jsonNode);

            String makerName = currentActorName();

            NotificationTemplate template = resolveSubmitTemplate(previousStatus);
            notificationUtil.sendNotification(
                    template,
                    Map.of(
                            "makerName", makerName,
                            "submissionId", id,
                            "submissionDate", currentTime.toString()
                    )
            );
            log.info("LivestockServiceImpl::addLivestock:notification {} sent for id: {} by maker: {}",
                    template.templateCode(), id, makerName);

            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, livestockEntity,
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());

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
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse approveLivestock(LifecycleRequest request, String token) {
        log.info("LivestockServiceImpl::approveLivestock:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::approveLivestock:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewLivestock(LifecycleRequest request, String token) {
        log.info("LivestockServiceImpl::reviewLivestock:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::reviewLivestock:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("LivestockServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LivestockServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LivestockEntity livestockEntity1 = entityOptional.get();
            String currentStatus = livestockEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("LivestockServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setStatus(newStatus);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(livestockEntity1.getData(), newStatus,
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    livestockEntity1.getData(), livestockEntity1.getData(),
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());
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
    /*private CustomResponse transitionStatus(LifecycleRequest request, String operation,
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
            log.warn("LivestockServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LivestockEntity livestockEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(livestockEntity1.getStatus())) {
                log.warn("LivestockServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, livestockEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setStatus(targetStatus);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(livestockEntity1.getData(), targetStatus,
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, AUDIT_ENTITY_NAME, operation, targetStatus,
                    livestockEntity1.getData(), livestockEntity1.getData(),
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }*/
    private CustomResponse transitionStatus(LifecycleRequest request, String operation,
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
            log.warn("LivestockServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<LivestockEntity> entityOptional = livestockRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LivestockEntity livestockEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(livestockEntity1.getStatus())) {
                log.warn("LivestockServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, livestockEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            livestockEntity1.setStatus(targetStatus);
            livestockEntity1.setUpdatedOn(currentTime);
            livestockRepository.save(livestockEntity1);
            log.info("LivestockServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(livestockEntity1.getData(), targetStatus,
                    livestockEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LIVESTOCK_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLivestockJsonPath());
            cacheService.putCache(id, jsonNode);

            String makerName = currentActorName();
            NotificationTemplate template = resolveDecisionTemplate(operation, targetStatus);
            notificationUtil.sendNotification(
                    template,
                    Map.of(
                            "makerName", makerName,
                            "submissionId", id,
                            "actionDate", currentTime.toString()
                    )
            );
            log.info("LivestockServiceImpl::transitionStatus:notification {} sent for id: {} by: {}",
                    template.templateCode(), id, makerName);

            map.put(Constants.LIVESTOCK_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    livestockEntity1.getData(), livestockEntity1.getData(),
                    livestockEntity1.getCreatedOn(), livestockEntity1.getUpdatedOn());

             NotificationTemplate template = NotificationTemplateResolver.resolveDecisionTemplate(
                      operation,
                      targetStatus
              );
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
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    /**
     * Picks the "submitted" template based on the record's status BEFORE this submission —
     * first-time submit (DRAFT) vs. resubmission after correction (REWORK).
     */
    /**
     * Picks the notification template for a plain (non-lifecycle) update, based on which
     * approver currently owns the record: L1 supervisor for PENDING, L2 admin for APPROVED.
     * Returns null for any other status — no notification is sent for those.
     */
   /* private NotificationTemplate resolvePlainUpdateTemplate(String currentStatus) {
        if (Constants.PENDING.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_RESUBMITTED_FOR_REVIEW;
        }
        if (Constants.APPROVED.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_REVIEWED_BY_ADMIN_L2;
        }
        return null;
    }*/

    private NotificationTemplate resolveSubmitTemplate(String previousStatus) {
        if (Constants.REWORK.equals(previousStatus)) {
            log.info("RECORD_RESUBMITTED_FOR_REVIEW");
            return NotificationTemplateConstants.RECORD_RESUBMITTED_FOR_REVIEW;
        }
        return NotificationTemplateConstants.NEW_RECORD_SUBMITTED_FOR_REVIEW;
    }

    /**
     * Picks the approve/reject/send-back template based on the operation
     * ("approve" = L1 supervisor, "review" = L2 admin) and the requested target status.
     */
    private NotificationTemplate resolveDecisionTemplate(String operation, String targetStatus) {
        boolean isL2 = "review".equals(operation);

        if (Constants.REJECTED.equals(targetStatus)) {
            log.info("RECORD_REJECTED_BY_ADMIN_L2 Or RECORD_REJECTED_BY_SUPERVISOR");
            return isL2
                    ? NotificationTemplateConstants.RECORD_REJECTED_BY_ADMIN_L2
                    : NotificationTemplateConstants.RECORD_REJECTED_BY_SUPERVISOR;
        }
        if (Constants.REWORK.equals(targetStatus)) {
            log.info("RECORD_SENT_BACK_FOR_CORRECTION");
            return NotificationTemplateConstants.RECORD_SENT_BACK_FOR_CORRECTION;
        }
        // approve: PENDING -> APPROVED | review: APPROVED -> ACTIVE
        return isL2
                ? NotificationTemplateConstants.RECORD_APPROVED_BY_ADMIN_L2
                : NotificationTemplateConstants.RECORD_APPROVED_BY_SUPERVISOR;
    }

    /** Resolves the currently authenticated maker/actor name for notification payloads. */
    private String currentActorName() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "UNKNOWN";
    }
    /**
     * Builds the projection stored in Elasticsearch and Redis (and returned by read): the payload
     * plus the lifecycle status and the Postgres createdOn/updatedOn timestamps (ISO-8601). ES keeps
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esLivestockRequiredFields.json.
     */

    /**
     * Picks the notification template for a plain (non-lifecycle) update, based on the record's
     * current lifecycle status — i.e. whoever currently "owns" the record at that stage:
     *   PENDING  -> owned by L1 Supervisor (awaiting their decision)
     *   APPROVED -> owned by L2 Admin (awaiting their decision)
     *   REWORK   -> owned by L0 Maker (needs correction)
     *   REJECTED -> owned by L0 Maker (final rejection, informational)
     *   ACTIVE   -> owned by L0 Maker (published, informational)
     *   DRAFT / anything else -> no owner yet, no notification
     */
    private NotificationTemplate resolvePlainUpdateTemplate(String currentStatus) {
        if (Constants.PENDING.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_RESUBMITTED_FOR_REVIEW;
        }
        if (Constants.APPROVED.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_REVIEWED_BY_ADMIN_L2;
        }
        if (Constants.REWORK.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_SENT_BACK_FOR_CORRECTION;
        }
        if (Constants.REJECTED.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_REJECTED_BY_SUPERVISOR;
        }
        if (Constants.ACTIVE.equals(currentStatus)) {
            return NotificationTemplateConstants.RECORD_APPROVED_BY_ADMIN_L2;
        }
        return null;
    }
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
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
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