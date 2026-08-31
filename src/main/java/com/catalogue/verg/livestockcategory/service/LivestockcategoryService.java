package com.catalogue.verg.livestockcategory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LivestockcategoryService {

    // token: the raw Authorization header from the caller
    CustomResponse createLivestockcategory(JsonNode livestockcategoryEntity, String token);

    CustomResponse updateLivestockcategory(String id, JsonNode livestockcategoryEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftLivestockcategory(JsonNode livestockcategoryEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addLivestockcategory(String id, JsonNode livestockcategoryEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveLivestockcategory(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewLivestockcategory(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchLivestockcategory(SearchCriteria searchCriteria, String token);

    CustomResponse assignLivestockcategory(JsonNode livestockcategoryEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryLivestockcategory();
}