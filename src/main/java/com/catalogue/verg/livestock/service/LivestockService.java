package com.catalogue.verg.livestock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.dto.PreviewDecisionRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LivestockService {

    // token: the raw Authorization header from the caller
    CustomResponse createLivestock(JsonNode livestockEntity, String token, String operation, Boolean isPreviewRequired, JsonNode userContext);

    CustomResponse updateLivestock(String id, JsonNode livestockEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftLivestock(JsonNode livestockEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addLivestock(String id, JsonNode livestockEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveLivestock(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewLivestock(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchLivestock(SearchCriteria searchCriteria, String token);

    CustomResponse assignLivestock(JsonNode livestockEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    CustomResponse importDataWithPreview(MultipartFile file, String token);

    CustomResponse decidePreview(PreviewDecisionRequest request, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryLivestock();
}