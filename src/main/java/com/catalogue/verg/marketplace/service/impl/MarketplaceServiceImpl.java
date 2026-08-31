package com.catalogue.verg.marketplace.service.impl;

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
import com.catalogue.verg.marketplace.entity.MarketplaceEntity;
import com.catalogue.verg.marketplace.repository.MarketplaceRepository;
import com.catalogue.verg.marketplace.service.MarketplaceService;
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
public class MarketplaceServiceImpl implements MarketplaceService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private MarketplaceRepository marketplaceRepository;

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
    private static final String CATALOGUE_NAME = "marketplace";

    private Logger logger = LoggerFactory.getLogger(MarketplaceServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createMarketplace(JsonNode marketplaceEntity, String token) {
        log.info("MarketplaceServiceImpl::createMarketplace:entered the method: " + marketplaceEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::createMarketplace:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.MARKETPLACE_VALIDATION_FILE_JSON, marketplaceEntity);

        log.debug("MarketplaceServiceImpl::createMarketplace:validated the payload");
        try {
            log.info("MarketplaceServiceImpl::createMarketplace:creating marketplace");
            MarketplaceEntity marketplaceEntity1 = new MarketplaceEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.MARKETPLACE_VALIDATION_FILE_JSON);
            marketplaceEntity1.setMarketplaceId(primaryID);
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            
            String initialStatus = lifecyclePolicy.initialStatus(CATALOGUE_NAME);
            marketplaceEntity1.setCreatedOn(currentTime);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceEntity1.setStatus(initialStatus);
            marketplaceEntity1.setData(marketplaceEntity);

            marketplaceRepository.save(marketplaceEntity1);

            log.info("MarketplaceServiceImpl::createMarketplace::persisted marketplace in postgres");
            ObjectNode jsonNode = buildDocument(marketplaceEntity, initialStatus, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticMarketplaceJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.MARKETPLACE_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("MarketplaceServiceImpl::createMarketplace::persisted marketplace in OAS");
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "create", initialStatus,
                    objectMapper.createObjectNode(), marketplaceEntity,
                    marketplaceEntity1.getCreatedOn(), marketplaceEntity1.getUpdatedOn());
            return response;

        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse searchMarketplace(SearchCriteria searchCriteria, String token) {
        log.info("MarketplaceServiceImpl::searchMarketplace");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("MarketplaceServiceImpl::searchMarketplace:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("MarketplaceServiceImpl::searchMarketplace: marketplace search result fetched from redis");
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
                    esUtilService.searchDocuments(Constants.MARKETPLACE_INDEX_NAME, searchCriteria);
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
    public CustomResponse assignMarketplace(JsonNode marketplaceEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id, String token) {
        log.info("MarketplaceServiceImpl::read:inside the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token, false);
        log.debug("MarketplaceServiceImpl::read:token validated, user context: {}", userContext);

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
                log.info("MarketplaceServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
                auditAfter = objectMapper.readTree(cachedJson);
            } else {
                Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
                if (entityOptional.isPresent()) {
                    MarketplaceEntity marketplaceEntity = entityOptional.get();
                    ObjectNode jsonNode = buildDocument(marketplaceEntity.getData(),
                            marketplaceEntity.getStatus(), marketplaceEntity.getCreatedOn(),
                            marketplaceEntity.getUpdatedOn());
                    cacheService.putCache(id, jsonNode);
                    log.info("MarketplaceServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            jsonNode, new TypeReference<Object>() {
                                            }));
                    auditAfter = jsonNode;
                    auditCreatedOn = marketplaceEntity.getCreatedOn();
                    auditUpdatedOn = marketplaceEntity.getUpdatedOn();
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
    public CustomResponse updateMarketplace(String id, JsonNode marketplaceEntity) {
        log.info("MarketplaceServiceImpl::updateMarketplace:entered the method with id: {}", id);
        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("MarketplaceServiceImpl::updateMarketplace:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        // Validate the incoming payload against the entity schema (same as create)
        payloadValidation.validatePayload(Constants.MARKETPLACE_VALIDATION_FILE_JSON, marketplaceEntity);
        log.debug("MarketplaceServiceImpl::updateMarketplace:validated the payload");

        try {
            // Check if the entity exists in the database
            Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("MarketplaceServiceImpl::updateMarketplace:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            MarketplaceEntity marketplaceEntity1 = entityOptional.get();

            // Reject updates on soft-deleted (DELETED) records
            if (Constants.DELETED.equals(marketplaceEntity1.getStatus())) {
                log.warn("MarketplaceServiceImpl::updateMarketplace:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Replace payload; preserve id / createdOn / status, bump updatedOn
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            marketplaceEntity1.setData(marketplaceEntity);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceRepository.save(marketplaceEntity1);
            log.info("MarketplaceServiceImpl::updateMarketplace:updated record in postgres for id: {}", id);

            // Re-index the document in Elasticsearch (filtered to whitelisted fields)
            ObjectNode jsonNode = buildDocument(marketplaceEntity, marketplaceEntity1.getStatus(),
                    marketplaceEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticMarketplaceJsonPath());
            log.info("MarketplaceServiceImpl::updateMarketplace:updated document in elasticsearch for id: {}", id);

            // Refresh the Redis cache
            cacheService.putCache(id, jsonNode);
            log.info("MarketplaceServiceImpl::updateMarketplace:refreshed cache for id: {}", id);

            map.put(Constants.MARKETPLACE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            return response;

        } catch (Exception e) {
            log.error("MarketplaceServiceImpl::updateMarketplace:error while updating record for id: {}", id, e);
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse delete(String id, String token) {
        log.info("MarketplaceServiceImpl::delete:inside the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::delete:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();

        // Validate that the ID is not null or empty
        if (StringUtils.isEmpty(id)) {
            log.warn("MarketplaceServiceImpl::delete:id is null or empty");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }

        try {
            // Check if the entity exists in the database
            Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
            if (entityOptional.isEmpty()) {
                log.warn("MarketplaceServiceImpl::delete:no record found for id: {}", id);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }

            MarketplaceEntity marketplaceEntity = entityOptional.get();

            // Check if the entity is already deleted
            if (Constants.DELETED.equals(marketplaceEntity.getStatus())) {
                log.warn("MarketplaceServiceImpl::delete:record already deleted for id: {}", id);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.setMessage("Record is already deleted");
                return response;
            }

            // Soft delete: mark the status DELETED and set updatedOn timestamp
            marketplaceEntity.setStatus(Constants.DELETED);
            marketplaceEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
            marketplaceRepository.save(marketplaceEntity);
            log.info("MarketplaceServiceImpl::delete:soft deleted record in postgres for id: {}", id);

            // Remove document from Elasticsearch
            esUtilService.deleteDocument(id, Constants.MARKETPLACE_INDEX_NAME);
            log.info("MarketplaceServiceImpl::delete:deleted document from elasticsearch for id: {}", id);

            // Remove from Redis cache
            cacheService.deleteCache(id);
            log.info("MarketplaceServiceImpl::delete:evicted cache for id: {}", id);

            response.setMessage(Constants.SUCCESSFULLY_DELETED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "delete", Constants.DELETED,
                    marketplaceEntity.getData(), marketplaceEntity.getData(),
                    marketplaceEntity.getCreatedOn(), marketplaceEntity.getUpdatedOn());
            return response;

        } catch (Exception e) {
            log.error("MarketplaceServiceImpl::delete:error while deleting record for id: {}", id, e);
            throw new CustomException(Constants.ERROR, "error while deleting record",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse importData(MultipartFile file, String token) {
        log.info("MarketplaceServiceImpl::importData::started");
        return importService.processBulkImport(
                file,
                Constants.MARKETPLACE_VALIDATION_FILE_JSON,
                payload -> createMarketplace(payload, token)   // every row is created as the calling user
        );
    }

    @Override
    public CustomResponse loadFromPrimaryMarketplace() {
        log.info("MarketplaceServiceImpl::loadFromPrimaryMarketplace::started");
        return loadFromPrimaryService.loadFromPrimary(
                Constants.MARKETPLACE_INDEX_NAME,
                vergProperties.getElasticMarketplaceJsonPath(),
                marketplaceRepository.findAll(),
                MarketplaceEntity::getMarketplaceId,
                e -> objectMapper.convertValue(
                        buildDocument(e.getData(), e.getStatus(), e.getCreatedOn(), e.getUpdatedOn()),
                        Map.class),
                e -> !Constants.DELETED.equals(e.getStatus()));   // skip DELETED; INACTIVE is indexed
    }

    @Override
    public CustomResponse draftMarketplace(JsonNode marketplaceEntity, String token) {
        log.info("MarketplaceServiceImpl::draftMarketplace:entered the method: " + marketplaceEntity);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::draftMarketplace:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        // Relaxed validation: types/structure enforced, but required fields may be missing
        payloadValidation.validatePayloadRelaxed(Constants.MARKETPLACE_VALIDATION_FILE_JSON, marketplaceEntity);
        log.debug("MarketplaceServiceImpl::draftMarketplace:validated the payload (relaxed)");
        try {
            MarketplaceEntity marketplaceEntity1 = new MarketplaceEntity();
            String primaryID = primaryKeyUtil.generateKey(Constants.MARKETPLACE_VALIDATION_FILE_JSON);
            marketplaceEntity1.setMarketplaceId(primaryID);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            marketplaceEntity1.setCreatedOn(currentTime);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceEntity1.setStatus(Constants.DRAFT);
            marketplaceEntity1.setData(marketplaceEntity);

            marketplaceRepository.save(marketplaceEntity1);
            log.info("MarketplaceServiceImpl::draftMarketplace::persisted draft in postgres");

            ObjectNode jsonNode = buildDocument(marketplaceEntity, Constants.DRAFT, currentTime, currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticMarketplaceJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            map.put(Constants.MARKETPLACE_ID_RQST, primaryID);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(primaryID, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "draft", Constants.DRAFT,
                    objectMapper.createObjectNode(), marketplaceEntity,
                    marketplaceEntity1.getCreatedOn(), marketplaceEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse addMarketplace(String id, JsonNode marketplaceEntity, String token) {
        log.info("MarketplaceServiceImpl::addMarketplace:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::addMarketplace:token validated, user context: {}", userContext);

        // Guard before the try block: the 404 must not be swallowed by the catch below
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        // Full validation: all required fields must be present to submit for approval
        payloadValidation.validatePayload(Constants.MARKETPLACE_VALIDATION_FILE_JSON, marketplaceEntity);
        log.debug("MarketplaceServiceImpl::addMarketplace:validated the payload");
        try {
            Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            MarketplaceEntity marketplaceEntity1 = entityOptional.get();
            // Only DRAFT or REWORK records can be (re-)submitted for approval
            if (!LifecycleUtil.ADD_PROMOTABLE.contains(marketplaceEntity1.getStatus())) {
                log.warn("MarketplaceServiceImpl::addMarketplace:record {} not in DRAFT/REWORK (status={})",
                        id, marketplaceEntity1.getStatus());
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            JsonNode auditBefore = marketplaceEntity1.getData();
            marketplaceEntity1.setData(marketplaceEntity);
            marketplaceEntity1.setStatus(Constants.PENDING);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceRepository.save(marketplaceEntity1);
            log.info("MarketplaceServiceImpl::addMarketplace:submitted record {} for approval (PENDING)", id);

            ObjectNode jsonNode = buildDocument(marketplaceEntity, Constants.PENDING,
                    marketplaceEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticMarketplaceJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.MARKETPLACE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "add-promote", Constants.PENDING,
                    auditBefore, marketplaceEntity,
                    marketplaceEntity1.getCreatedOn(), marketplaceEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse approveMarketplace(LifecycleRequest request, String token) {
        log.info("MarketplaceServiceImpl::approveMarketplace:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::approveMarketplace:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "approve",
                LifecycleUtil.APPROVE_FROM, LifecycleUtil.APPROVE_TARGETS);
    }

    @Override
    public CustomResponse reviewMarketplace(LifecycleRequest request, String token) {
        log.info("MarketplaceServiceImpl::reviewMarketplace:entered the method");

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::reviewMarketplace:token validated, user context: {}", userContext);

        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        return transitionStatus(request, userContext, "review",
                LifecycleUtil.REVIEW_FROM, LifecycleUtil.REVIEW_TARGETS);
    }

    @Override
    public CustomResponse toggleStatus(String id, String token) {
        log.info("MarketplaceServiceImpl::toggleStatus:entered the method with id: {}", id);

        // Validate the caller's api token against the OAS auth service
        JsonNode userContext = authValidationService.validateToken(token);
        log.debug("MarketplaceServiceImpl::toggleStatus:token validated, user context: {}", userContext);

        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            MarketplaceEntity marketplaceEntity1 = entityOptional.get();
            String currentStatus = marketplaceEntity1.getStatus();
            String newStatus;
            if (Constants.ACTIVE.equals(currentStatus)) {
                newStatus = Constants.IN_ACTIVE;
            } else if (Constants.IN_ACTIVE.equals(currentStatus)) {
                newStatus = Constants.ACTIVE;
            } else {
                // Only a published (ACTIVE) or deactivated (INACTIVE) record can be toggled
                log.warn("MarketplaceServiceImpl::toggleStatus:record {} is {}, can only toggle ACTIVE<->INACTIVE",
                        id, currentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            marketplaceEntity1.setStatus(newStatus);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceRepository.save(marketplaceEntity1);
            log.info("MarketplaceServiceImpl::toggleStatus:record {} toggled {} -> {}", id, currentStatus, newStatus);

            ObjectNode jsonNode = buildDocument(marketplaceEntity1.getData(), newStatus,
                    marketplaceEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticMarketplaceJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.MARKETPLACE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    "toggle", newStatus,
                    marketplaceEntity1.getData(), marketplaceEntity1.getData(),
                    marketplaceEntity1.getCreatedOn(), marketplaceEntity1.getUpdatedOn());
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
            log.warn("MarketplaceServiceImpl::transitionStatus:invalid target status '{}' for id {}",
                    request.getStatus(), id);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage(Constants.INVALID_STATUS);
            return response;
        }
        try {
            Optional<MarketplaceEntity> entityOptional = marketplaceRepository.findById(id);
            if (entityOptional.isEmpty()) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.setMessage(Constants.INVALID_ID);
                return response;
            }
            MarketplaceEntity marketplaceEntity1 = entityOptional.get();
            if (!requiredCurrentStatus.equals(marketplaceEntity1.getStatus())) {
                log.warn("MarketplaceServiceImpl::transitionStatus:record {} is {}, requires {}",
                        id, marketplaceEntity1.getStatus(), requiredCurrentStatus);
                response.setResponseCode(HttpStatus.CONFLICT);
                response.setMessage(Constants.INVALID_STATUS_TRANSITION);
                return response;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            marketplaceEntity1.setStatus(targetStatus);
            marketplaceEntity1.setUpdatedOn(currentTime);
            marketplaceRepository.save(marketplaceEntity1);
            log.info("MarketplaceServiceImpl::transitionStatus:record {} moved {} -> {}",
                    id, requiredCurrentStatus, targetStatus);

            ObjectNode jsonNode = buildDocument(marketplaceEntity1.getData(), targetStatus,
                    marketplaceEntity1.getCreatedOn(), currentTime);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(Constants.MARKETPLACE_INDEX_NAME, Constants.INDEX_TYPE,
                    id, map, vergProperties.getElasticMarketplaceJsonPath());
            cacheService.putCache(id, jsonNode);
            map.put(Constants.MARKETPLACE_ID_RQST, id);
            response.setResult(map);
            response.setMessage(Constants.SUCCESSFULLY_UPDATED);
            response.setResponseCode(HttpStatus.OK);
            auditLogService.logAudit(id, CATALOGUE_NAME,
                    userContext.path("userId").asText(null),
                    userContext.path("userName").asText(null),
                    userContext.path("functionalRole").asText(null),
                    operation, targetStatus,
                    marketplaceEntity1.getData(), marketplaceEntity1.getData(),
                    marketplaceEntity1.getCreatedOn(), marketplaceEntity1.getUpdatedOn());
            return response;
        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Builds the projection stored in Elasticsearch and Redis (and returned by read): the payload
     * plus the lifecycle status and the Postgres createdOn/updatedOn timestamps (ISO-8601). ES keeps
     * only whitelisted keys, so status/createdOn/updatedOn must be present in esMarketplaceRequiredFields.json.
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