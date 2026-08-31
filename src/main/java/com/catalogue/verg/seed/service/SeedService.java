package com.catalogue.verg.seed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface SeedService {

    // token: the raw Authorization header from the caller
    CustomResponse createSeed(JsonNode seedEntity, String token);

    CustomResponse updateSeed(String id, JsonNode seedEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftSeed(JsonNode seedEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addSeed(String id, JsonNode seedEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveSeed(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewSeed(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchSeed(SearchCriteria searchCriteria, String token);

    CustomResponse assignSeed(JsonNode seedEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimarySeed();
}