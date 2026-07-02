package com.catalogue.verg.insecticide.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.catalogue.verg.core.cache.CacheService;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.RespParam;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.core.elasticsearch.dto.SearchResult;
import com.catalogue.verg.core.elasticsearch.service.ESUtilService;
import com.catalogue.verg.core.exception.CustomException;
import com.catalogue.verg.core.util.Constants;
import com.catalogue.verg.core.util.PayloadValidation;
import com.catalogue.verg.core.util.VergProperties;
import com.catalogue.verg.core.service.ImportService;
import com.catalogue.verg.core.util.PrimaryKeyUtil;
import com.catalogue.verg.insecticide.entity.InsecticideEntity;
import com.catalogue.verg.insecticide.repository.InsecticideRepository;
import com.catalogue.verg.insecticide.service.InsecticideService;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class InsecticideServiceImpl implements InsecticideService {
    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private PrimaryKeyUtil primaryKeyUtil;

    @Autowired
    private InsecticideRepository insecticideRepository;

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

    private Logger logger = LoggerFactory.getLogger(InsecticideServiceImpl.class);

    @Value("${spring.redis.cacheTtl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse createInsecticide(JsonNode insecticideEntity) {
        log.info("InsecticideServiceImpl::createInsecticide:entered the method: " + insecticideEntity);
        CustomResponse response = new CustomResponse();
        payloadValidation.validatePayload(Constants.INSECTICIDE_VALIDATION_FILE_JSON, insecticideEntity);

        log.debug("InsecticideServiceImpl::createInsecticide:validated the payload");
        try {
            log.info("InsecticideServiceImpl::createInsecticide:creating insecticide");
            InsecticideEntity insecticideEntity1 = new InsecticideEntity();
            // Generate Primary Key
            String primaryID = primaryKeyUtil.generateKey(Constants.INSECTICIDE_VALIDATION_FILE_JSON);
            insecticideEntity1.setInsecticideId(primaryID);
            // Create Parameters like createdDate / updateDate / Data and Status
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            insecticideEntity1.setCreatedOn(currentTime);
            insecticideEntity1.setUpdatedOn(currentTime);
            insecticideEntity1.setStatus(Constants.ACTIVE);
            insecticideEntity1.setData(insecticideEntity);

            insecticideRepository.save(insecticideEntity1);

            log.info("InsecticideServiceImpl::createInsecticide::persisted insecticide in postgres");
            ObjectNode jsonNode = objectMapper.createObjectNode();
            //            jsonNode.put("status", Constants.ACTIVE);
            jsonNode.setAll((ObjectNode) insecticideEntity);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.INSECTICIDE_INDEX_NAME, Constants.INDEX_TYPE,
                    String.valueOf(primaryID), map, vergProperties.getElasticInsecticideJsonPath());
            cacheService.putCache(primaryID, jsonNode);
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.INSECTICIDE_ID_RQST, primaryID);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            log.info("InsecticideServiceImpl::createInsecticide::persisted insecticide in Verg");
            return response;

        } catch (Exception e) {
            throw new CustomException("error while processing", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse searchInsecticide(SearchCriteria searchCriteria) {
        log.info("InsecticideServiceImpl::searchInsecticide");
        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("InsecticideServiceImpl::searchInsecticide: insecticide search result fetched from redis");
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
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
                    esUtilService.searchDocuments(Constants.INSECTICIDE_INDEX_NAME, searchCriteria);
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
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
    public CustomResponse assignInsecticide(JsonNode insecticideEntity, String token) {
        return null;
    }

    @Override
    public CustomResponse read(String id) {
        log.info("InsecticideServiceImpl::read:inside the method");
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("InsecticideServiceImpl::read:Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
            } else {
                Optional<InsecticideEntity> entityOptional = insecticideRepository.findById(id);
                if (entityOptional.isPresent()) {
                    InsecticideEntity insecticideEntity = entityOptional.get();
                    cacheService.putCache(id, insecticideEntity.getData());
                    log.info("InsecticideServiceImpl::read:Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            insecticideEntity.getData(), new TypeReference<Object>() {
                                            }));
                } else {
                    response.setResponseCode(HttpStatus.NOT_FOUND);
                    response.setMessage(Constants.INVALID_ID);
                }
            }
        } catch (Exception e) {
            throw new CustomException(Constants.ERROR, "error while processing",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @Override
    public CustomResponse delete(String id) {
        return null;
    }

    public void createSuccessResponse(CustomResponse response) {
        response.setParams(new RespParam());
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
    }

    @Override
    public CustomResponse importData(MultipartFile file) {
        log.info("InsecticideServiceImpl::importData::started");
        return importService.processBulkImport(
                file,
                Constants.INSECTICIDE_VALIDATION_FILE_JSON,
                this::createInsecticide
        );
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