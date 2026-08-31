package com.catalogue.verg.cropcategory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CropcategoryService {

    // token: the raw Authorization header from the caller
    CustomResponse createCropcategory(JsonNode cropcategoryEntity, String token);

    CustomResponse updateCropcategory(String id, JsonNode cropcategoryEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftCropcategory(JsonNode cropcategoryEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addCropcategory(String id, JsonNode cropcategoryEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveCropcategory(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewCropcategory(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchCropcategory(SearchCriteria searchCriteria, String token);

    CustomResponse assignCropcategory(JsonNode cropcategoryEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryCropcategory();
}