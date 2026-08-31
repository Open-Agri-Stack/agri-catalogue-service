package com.catalogue.verg.cropvariety.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CropvarietyService {

    // token: the raw Authorization header from the caller
    CustomResponse createCropvariety(JsonNode cropvarietyEntity, String token);

    CustomResponse updateCropvariety(String id, JsonNode cropvarietyEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftCropvariety(JsonNode cropvarietyEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addCropvariety(String id, JsonNode cropvarietyEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveCropvariety(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewCropvariety(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchCropvariety(SearchCriteria searchCriteria, String token);

    CustomResponse assignCropvariety(JsonNode cropvarietyEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryCropvariety();
}