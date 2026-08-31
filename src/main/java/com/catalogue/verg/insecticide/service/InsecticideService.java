package com.catalogue.verg.insecticide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface InsecticideService {

    // token: the raw Authorization header from the caller
    CustomResponse createInsecticide(JsonNode insecticideEntity, String token);

    CustomResponse updateInsecticide(String id, JsonNode insecticideEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftInsecticide(JsonNode insecticideEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addInsecticide(String id, JsonNode insecticideEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveInsecticide(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewInsecticide(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchInsecticide(SearchCriteria searchCriteria, String token);

    CustomResponse assignInsecticide(JsonNode insecticideEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryInsecticide();
}