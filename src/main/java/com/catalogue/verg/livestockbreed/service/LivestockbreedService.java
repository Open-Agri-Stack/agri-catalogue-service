package com.catalogue.verg.livestockbreed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LivestockbreedService {

    // token: the raw Authorization header from the caller
    CustomResponse createLivestockbreed(JsonNode livestockbreedEntity, String token);

    CustomResponse updateLivestockbreed(String id, JsonNode livestockbreedEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftLivestockbreed(JsonNode livestockbreedEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addLivestockbreed(String id, JsonNode livestockbreedEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveLivestockbreed(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewLivestockbreed(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchLivestockbreed(SearchCriteria searchCriteria, String token);

    CustomResponse assignLivestockbreed(JsonNode livestockbreedEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryLivestockbreed();
}