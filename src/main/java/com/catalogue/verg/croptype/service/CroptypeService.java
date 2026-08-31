package com.catalogue.verg.croptype.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CroptypeService {

    // token: the raw Authorization header from the caller
    CustomResponse createCroptype(JsonNode croptypeEntity, String token);

    CustomResponse updateCroptype(String id, JsonNode croptypeEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftCroptype(JsonNode croptypeEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addCroptype(String id, JsonNode croptypeEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveCroptype(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewCroptype(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchCroptype(SearchCriteria searchCriteria, String token);

    CustomResponse assignCroptype(JsonNode croptypeEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryCroptype();
}