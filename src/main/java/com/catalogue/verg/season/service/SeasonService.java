package com.catalogue.verg.season.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface SeasonService {

    // token: the raw Authorization header from the caller
    CustomResponse createSeason(JsonNode seasonEntity, String token);

    CustomResponse updateSeason(String id, JsonNode seasonEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftSeason(JsonNode seasonEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addSeason(String id, JsonNode seasonEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveSeason(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewSeason(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchSeason(SearchCriteria searchCriteria, String token);

    CustomResponse assignSeason(JsonNode seasonEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimarySeason();
}