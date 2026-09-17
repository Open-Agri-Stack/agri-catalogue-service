package com.catalogue.verg.locationobject.service.impl;

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
import com.catalogue.verg.locationobject.entity.LocationobjectEntity;
import com.catalogue.verg.locationobject.repository.LocationobjectRepository;
import com.catalogue.verg.locationobject.service.LocationobjectService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class LocationobjectServiceImpl implements LocationobjectService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private LocationobjectRepository locationobjectRepository;

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
    private static final String CATALOGUE_NAME = "locationobject";
    private static final String TEMPLATE_NAME = "Locationobject";
    private static final String TEMPLATE_CONSTANT = "LOCATIONOBJECT";

    private Logger logger = LoggerFactory.getLogger(LocationobjectServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createLocationobject(JsonNode locationobjectEntity, String token) {
        log.info("LocationobjectServiceImpl::createLocationobject:entered the method: " + locationobjectEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::createLocationobject:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON, locationobjectEntity);

        log.debug("LocationobjectServiceImpl::createLocationobject:validated the payload");
        try {
            log.info("LocationobjectServiceImpl::createLocationobject:creating locationobject");
            LocationobjectEntity locationobjectEntity1 = new LocationobjectEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON);
            locationobjectEntity1.setLocationobjectId(primaryID);
            // Stamp createdBy/updatedBy into the payload itself, before it's persisted as `data`
            if (locationobjectEntity instanceof ObjectNode) {
                String makerId = userContext.path("userId").asText(null);
                ((ObjectNode) locationobjectEntity).put("createdBy", makerId);
                ((ObjectNode) locationobjectEntity).put("updatedBy", makerId);
            }
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            
            String initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            locationobjectEntity1.setCreatedOn(currentTime);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectEntity1.setStatus(initialStatus);
            locationobjectEntity1.setData(locationobjectEntity);

            locationobjectRepository.save(locationobjectEntity1);

            log.info("LocationobjectServiceImpl::createLocationobject::persisted locationobject in postgres");
            ObjectNode jsonNode = buildDocument(locationobjectEntity, initialStatus, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticLocationobjectJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.LOCATIONOBJECT_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("LocationobjectServiceImpl::createLocationobject::persisted locationobject in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "create", initialStatus,
                    objectMapper.createObjectNode(), locationobjectEntity,
                    locationobjectEntity1.getCreatedOn(), locationobjectEntity1.getUpdatedOn());

            // Lifecycle-disabled catalogues create ACTIVE records that are never reviewed
            if (lifecyclePolicy.isEnabledFor(CATALOGUE_NAME)) {
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
    public CustomResponse searchLocationobject(SearchCriteria searchCriteria, String token) {
        log.info("LocationobjectServiceImpl::searchLocationobject");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LocationobjectServiceImpl::searchLocationobject:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("LocationobjectServiceImpl::searchLocationobject: locationobject search result fetched from redis");
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
                    esUtilService.searchDocuments(Constants.LOCATIONOBJECT_INDEX_NAME, searchCriteria);
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
    public CustomResponse assignLocationobject(JsonNode locationobjectEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("LocationobjectServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LocationobjectServiceImpl::read:token validated, user context: {}", userContext);

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
                log.info("LocationobjectServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
                if (entityOptional.isPresent()) {
                    LocationobjectEntity locationobjectEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(locationobjectEntity.getData(),
                            locationobjectEntity.getStatus(), locationobjectEntity.getCreatedOn(),
                            locationobjectEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("LocationobjectServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = locationobjectEntity.getCreatedOn();
                    auditUpdatedOn = locationobjectEntity.getUpdatedOn();
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
    public CustomResponse updateLocationobject(String id, JsonNode locationobjectEntity) {
        log.info("LocationobjectServiceImpl::updateLocationobject:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LocationobjectServiceImpl::updateLocationobject:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON, locationobjectEntity);
        log.debug("LocationobjectServiceImpl::updateLocationobject:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LocationobjectServiceImpl::updateLocationobject:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LocationobjectEntity locationobjectEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(locationobjectEntity1.getStatus())) {
                log.warn("LocationobjectServiceImpl::updateLocationobject:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationobjectEntity1.setData(locationobjectEntity);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectRepository.save(locationobjectEntity1);
            log.info("LocationobjectServiceImpl::updateLocationobject:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(locationobjectEntity, locationobjectEntity1.getStatus(),
                    locationobjectEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationobjectJsonPath());
            log.info("LocationobjectServiceImpl::updateLocationobject:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("LocationobjectServiceImpl::updateLocationobject:refreshed cache for id: {}", id);

            map.put(Constants.LOCATIONOBJECT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("LocationobjectServiceImpl::updateLocationobject:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse delete(String id, String token) {
        log.info("LocationobjectServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LocationobjectServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LocationobjectServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LocationobjectEntity locationobjectEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(locationobjectEntity.getStatus())) {
                log.warn("LocationobjectServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            locationobjectEntity.setStatus(Constants.DELETED);
            locationobjectEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            locationobjectRepository.save(locationobjectEntity);
            log.info("LocationobjectServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.LOCATIONOBJECT_INDEX_NAME);
            log.info("LocationobjectServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("LocationobjectServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    locationobjectEntity.getData(), locationobjectEntity.getData(),
                    locationobjectEntity.getCreatedOn(), locationobjectEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("LocationobjectServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("LocationobjectServiceImpl::importData::started");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::importData:token validated, user context: {}", userContext);

        CustomResponse response = importService.processBulkImport(
                file,
                Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON,
                payload -> createLocationobject(payload, token)   // every row is created as the calling user
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
    public CustomResponse loadFromPrimaryLocationobject() {
        log.info("LocationobjectServiceImpl::loadFromPrimaryLocationobject::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.LOCATIONOBJECT_INDEX_NAME,
                vergProperties.getElasticLocationobjectJsonPath(),
                locationobjectRepository.findAll(),
                LocationobjectEntity::getLocationobjectId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftLocationobject(JsonNode locationobjectEntity, String token) {
        log.info("LocationobjectServiceImpl::draftLocationobject:entered the method: " + locationobjectEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::draftLocationobject:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON, locationobjectEntity);
        log.debug("LocationobjectServiceImpl::draftLocationobject:validated the payload (relaxed)");
        try {
            LocationobjectEntity locationobjectEntity1 = new LocationobjectEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON);
            locationobjectEntity1.setLocationobjectId(primaryID);
            if (locationobjectEntity instanceof ObjectNode) {
                String makerId = userContext.path("userId").asText(null);
                ((ObjectNode) locationobjectEntity).put("createdBy", makerId);
                ((ObjectNode) locationobjectEntity).put("updatedBy", makerId);
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationobjectEntity1.setCreatedOn(currentTime);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectEntity1.setStatus(Constants.DRAFT);
            locationobjectEntity1.setData(locationobjectEntity);

            locationobjectRepository.save(locationobjectEntity1);
            log.info("LocationobjectServiceImpl::draftLocationobject::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(locationobjectEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticLocationobjectJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.LOCATIONOBJECT_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), locationobjectEntity,
                    locationobjectEntity1.getCreatedOn(), locationobjectEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse addLocationobject(String id, JsonNode locationobjectEntity, String token) {
        log.info("LocationobjectServiceImpl::addLocationobject:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::addLocationobject:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        // Full validation: all required fields must be present to submit for approval
        payloadValidation.validatePayload(Constants.LOCATIONOBJECT_VALIDATION_FILE_JSON, locationobjectEntity);
        log.debug("LocationobjectServiceImpl::addLocationobject:validated the payload");
        try {
            Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationobjectEntity locationobjectEntity1 = entityOptional.get();
            // Only DRAFT or REWORK records can be (re-)submitted for approval
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(locationobjectEntity1.getStatus())) {
                log.warn("LocationobjectServiceImpl::addLocationobject:record {} not in DRAFT/REWORK (status={})",
                        id, locationobjectEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = locationobjectEntity1.getData();
            // Preserve the original creator; only updatedBy changes to whoever is submitting
            if (locationobjectEntity instanceof ObjectNode) {
                String existingCreatedBy = (auditBefore != null) ? auditBefore.path("createdBy").asText(null) : null;
                if (existingCreatedBy != null) {
                    ((ObjectNode) locationobjectEntity).put("createdBy", existingCreatedBy);
                }
                ((ObjectNode) locationobjectEntity).put("updatedBy", userContext.path("userId").asText(null));
            }
            locationobjectEntity1.setData(locationobjectEntity);
            locationobjectEntity1.setStatus(Constants.PENDING);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectRepository.save(locationobjectEntity1);
            log.info("LocationobjectServiceImpl::addLocationobject:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(locationobjectEntity, Constants.PENDING,
                    locationobjectEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationobjectJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONOBJECT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, locationobjectEntity,
                    locationobjectEntity1.getCreatedOn(), locationobjectEntity1.getUpdatedOn());

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
    public CustomResponse approveLocationobject(LifecycleRequest request, String token) {
        log.info("LocationobjectServiceImpl::approveLocationobject:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::approveLocationobject:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewLocationobject(LifecycleRequest request, String token) {
        log.info("LocationobjectServiceImpl::reviewLocationobject:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::reviewLocationobject:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("LocationobjectServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationobjectServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationobjectEntity locationobjectEntity1 = entityOptional.get();
            String currentStatus = locationobjectEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("LocationobjectServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationobjectEntity1.setStatus(newStatus);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectRepository.save(locationobjectEntity1);
            log.info("LocationobjectServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(locationobjectEntity1.getData(), newStatus,
                    locationobjectEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationobjectJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONOBJECT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    locationobjectEntity1.getData(), locationobjectEntity1.getData(),
                    locationobjectEntity1.getCreatedOn(), locationobjectEntity1.getUpdatedOn());
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
            log.warn("LocationobjectServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<LocationobjectEntity> entityOptional = locationobjectRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationobjectEntity locationobjectEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(locationobjectEntity1.getStatus())) {
                log.warn("LocationobjectServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, locationobjectEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationobjectEntity1.setStatus(targetStatus);
            locationobjectEntity1.setUpdatedOn(currentTime);
            locationobjectRepository.save(locationobjectEntity1);
            log.info("LocationobjectServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(locationobjectEntity1.getData(), targetStatus,
                    locationobjectEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONOBJECT_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationobjectJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONOBJECT_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    locationobjectEntity1.getData(), locationobjectEntity1.getData(),
                    locationobjectEntity1.getCreatedOn(), locationobjectEntity1.getUpdatedOn());

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
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Builds the projection stored in Elasticsearch and Redis (and returned by read): the payload
     * plus the lifecycle status and the Postgres createdOn/updatedOn timestamps (ISO-8601). ES keeps
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esLocationobjectRequiredFields.json.
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