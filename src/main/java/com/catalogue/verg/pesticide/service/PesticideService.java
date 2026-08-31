package com.catalogue.verg.pesticide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface PesticideService {

    // token: the raw Authorization header from the caller
    CustomResponse createPesticide(JsonNode pesticideEntity, String token);

    CustomResponse updatePesticide(String id, JsonNode pesticideEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftPesticide(JsonNode pesticideEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addPesticide(String id, JsonNode pesticideEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approvePesticide(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewPesticide(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchPesticide(SearchCriteria searchCriteria, String token);

    CustomResponse assignPesticide(JsonNode pesticideEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryPesticide();
}