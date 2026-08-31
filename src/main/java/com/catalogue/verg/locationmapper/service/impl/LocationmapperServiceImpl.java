package com.catalogue.verg.locationmapper.service.impl;

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
import com.catalogue.verg.locationmapper.entity.LocationmapperEntity;
import com.catalogue.verg.locationmapper.repository.LocationmapperRepository;
import com.catalogue.verg.locationmapper.service.LocationmapperService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class LocationmapperServiceImpl implements LocationmapperService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private LocationmapperRepository locationmapperRepository;

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

    /**
     * Catalogue name recorded on every audit row emitted by this service. Doubles as the key
     * this catalogue is looked up by in the lifecycle switches ({@link LifecyclePolicy}).
     */
    private static final String CATALOGUE_NAME = "locationmapper";

    private Logger logger = LoggerFactory.getLogger(LocationmapperServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createLocationmapper(JsonNode locationmapperEntity, String token) {
        log.info("LocationmapperServiceImpl::createLocationmapper:entered the method: " + locationmapperEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::createLocationmapper:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON, locationmapperEntity);

        log.debug("LocationmapperServiceImpl::createLocationmapper:validated the payload");
        try {
            log.info("LocationmapperServiceImpl::createLocationmapper:creating locationmapper");
            LocationmapperEntity locationmapperEntity1 = new LocationmapperEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON);
            locationmapperEntity1.setLocationmapperId(primaryID);
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            
            String initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            locationmapperEntity1.setCreatedOn(currentTime);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperEntity1.setStatus(initialStatus);
            locationmapperEntity1.setData(locationmapperEntity);

            locationmapperRepository.save(locationmapperEntity1);

            log.info("LocationmapperServiceImpl::createLocationmapper::persisted locationmapper in postgres");
            ObjectNode jsonNode = buildDocument(locationmapperEntity, initialStatus, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticLocationmapperJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.LOCATIONMAPPER_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("LocationmapperServiceImpl::createLocationmapper::persisted locationmapper in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "create", initialStatus,
                    objectMapper.createObjectNode(), locationmapperEntity,
                    locationmapperEntity1.getCreatedOn(), locationmapperEntity1.getUpdatedOn());
            return response;

        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse searchLocationmapper(SearchCriteria searchCriteria, String token) {
        log.info("LocationmapperServiceImpl::searchLocationmapper");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LocationmapperServiceImpl::searchLocationmapper:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("LocationmapperServiceImpl::searchLocationmapper: locationmapper search result fetched from redis");
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
                    esUtilService.searchDocuments(Constants.LOCATIONMAPPER_INDEX_NAME, searchCriteria);
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
    public CustomResponse assignLocationmapper(JsonNode locationmapperEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("LocationmapperServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("LocationmapperServiceImpl::read:token validated, user context: {}", userContext);

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
                log.info("LocationmapperServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
                if (entityOptional.isPresent()) {
                    LocationmapperEntity locationmapperEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(locationmapperEntity.getData(),
                            locationmapperEntity.getStatus(), locationmapperEntity.getCreatedOn(),
                            locationmapperEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("LocationmapperServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = locationmapperEntity.getCreatedOn();
                    auditUpdatedOn = locationmapperEntity.getUpdatedOn();
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
    public CustomResponse updateLocationmapper(String id, JsonNode locationmapperEntity) {
        log.info("LocationmapperServiceImpl::updateLocationmapper:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LocationmapperServiceImpl::updateLocationmapper:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON, locationmapperEntity);
        log.debug("LocationmapperServiceImpl::updateLocationmapper:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LocationmapperServiceImpl::updateLocationmapper:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LocationmapperEntity locationmapperEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(locationmapperEntity1.getStatus())) {
                log.warn("LocationmapperServiceImpl::updateLocationmapper:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationmapperEntity1.setData(locationmapperEntity);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperRepository.save(locationmapperEntity1);
            log.info("LocationmapperServiceImpl::updateLocationmapper:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(locationmapperEntity, locationmapperEntity1.getStatus(),
                    locationmapperEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationmapperJsonPath());
            log.info("LocationmapperServiceImpl::updateLocationmapper:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("LocationmapperServiceImpl::updateLocationmapper:refreshed cache for id: {}", id);

            map.put(Constants.LOCATIONMAPPER_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("LocationmapperServiceImpl::updateLocationmapper:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse delete(String id, String token) {
        log.info("LocationmapperServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("LocationmapperServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("LocationmapperServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            LocationmapperEntity locationmapperEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(locationmapperEntity.getStatus())) {
                log.warn("LocationmapperServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            locationmapperEntity.setStatus(Constants.DELETED);
            locationmapperEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            locationmapperRepository.save(locationmapperEntity);
            log.info("LocationmapperServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.LOCATIONMAPPER_INDEX_NAME);
            log.info("LocationmapperServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("LocationmapperServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    locationmapperEntity.getData(), locationmapperEntity.getData(),
                    locationmapperEntity.getCreatedOn(), locationmapperEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("LocationmapperServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("LocationmapperServiceImpl::importData::started");
        return importService.processBulkImport(
                file,
                Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON,
                payload -> createLocationmapper(payload, token)   // every row is created as the calling user
        );
    }

    @Override
    public CustomResponse loadFromPrimaryLocationmapper() {
        log.info("LocationmapperServiceImpl::loadFromPrimaryLocationmapper::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.LOCATIONMAPPER_INDEX_NAME,
                vergProperties.getElasticLocationmapperJsonPath(),
                locationmapperRepository.findAll(),
                LocationmapperEntity::getLocationmapperId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftLocationmapper(JsonNode locationmapperEntity, String token) {
        log.info("LocationmapperServiceImpl::draftLocationmapper:entered the method: " + locationmapperEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::draftLocationmapper:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON, locationmapperEntity);
        log.debug("LocationmapperServiceImpl::draftLocationmapper:validated the payload (relaxed)");
        try {
            LocationmapperEntity locationmapperEntity1 = new LocationmapperEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON);
            locationmapperEntity1.setLocationmapperId(primaryID);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationmapperEntity1.setCreatedOn(currentTime);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperEntity1.setStatus(Constants.DRAFT);
            locationmapperEntity1.setData(locationmapperEntity);

            locationmapperRepository.save(locationmapperEntity1);
            log.info("LocationmapperServiceImpl::draftLocationmapper::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(locationmapperEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticLocationmapperJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.LOCATIONMAPPER_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), locationmapperEntity,
                    locationmapperEntity1.getCreatedOn(), locationmapperEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse addLocationmapper(String id, JsonNode locationmapperEntity, String token) {
        log.info("LocationmapperServiceImpl::addLocationmapper:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::addLocationmapper:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        // Full validation: all required fields must be present to submit for approval
        payloadValidation.validatePayload(Constants.LOCATIONMAPPER_VALIDATION_FILE_JSON, locationmapperEntity);
        log.debug("LocationmapperServiceImpl::addLocationmapper:validated the payload");
        try {
            Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationmapperEntity locationmapperEntity1 = entityOptional.get();
            // Only DRAFT or REWORK records can be (re-)submitted for approval
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(locationmapperEntity1.getStatus())) {
                log.warn("LocationmapperServiceImpl::addLocationmapper:record {} not in DRAFT/REWORK (status={})",
                        id, locationmapperEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = locationmapperEntity1.getData();
            locationmapperEntity1.setData(locationmapperEntity);
            locationmapperEntity1.setStatus(Constants.PENDING);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperRepository.save(locationmapperEntity1);
            log.info("LocationmapperServiceImpl::addLocationmapper:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(locationmapperEntity, Constants.PENDING,
                    locationmapperEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationmapperJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONMAPPER_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, locationmapperEntity,
                    locationmapperEntity1.getCreatedOn(), locationmapperEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse approveLocationmapper(LifecycleRequest request, String token) {
        log.info("LocationmapperServiceImpl::approveLocationmapper:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::approveLocationmapper:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewLocationmapper(LifecycleRequest request, String token) {
        log.info("LocationmapperServiceImpl::reviewLocationmapper:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::reviewLocationmapper:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("LocationmapperServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("LocationmapperServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationmapperEntity locationmapperEntity1 = entityOptional.get();
            String currentStatus = locationmapperEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("LocationmapperServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationmapperEntity1.setStatus(newStatus);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperRepository.save(locationmapperEntity1);
            log.info("LocationmapperServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(locationmapperEntity1.getData(), newStatus,
                    locationmapperEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationmapperJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONMAPPER_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    locationmapperEntity1.getData(), locationmapperEntity1.getData(),
                    locationmapperEntity1.getCreatedOn(), locationmapperEntity1.getUpdatedOn());
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
            log.warn("LocationmapperServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<LocationmapperEntity> entityOptional = locationmapperRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            LocationmapperEntity locationmapperEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(locationmapperEntity1.getStatus())) {
                log.warn("LocationmapperServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, locationmapperEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            locationmapperEntity1.setStatus(targetStatus);
            locationmapperEntity1.setUpdatedOn(currentTime);
            locationmapperRepository.save(locationmapperEntity1);
            log.info("LocationmapperServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(locationmapperEntity1.getData(), targetStatus,
                    locationmapperEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.LOCATIONMAPPER_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticLocationmapperJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.LOCATIONMAPPER_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    locationmapperEntity1.getData(), locationmapperEntity1.getData(),
                    locationmapperEntity1.getCreatedOn(), locationmapperEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Builds the projection stored in Elasticsearch and Redis (and returned by read): the payload
     * plus the lifecycle status and the Postgres createdOn/updatedOn timestamps (ISO-8601). ES keeps
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esLocationmapperRequiredFields.json.
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