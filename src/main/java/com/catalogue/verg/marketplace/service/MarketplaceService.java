package com.catalogue.verg.marketplace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface MarketplaceService {

    // token: the raw Authorization header from the caller
    CustomResponse createMarketplace(JsonNode marketplaceEntity, String token);

    CustomResponse updateMarketplace(String id, JsonNode marketplaceEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftMarketplace(JsonNode marketplaceEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addMarketplace(String id, JsonNode marketplaceEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveMarketplace(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewMarketplace(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchMarketplace(SearchCriteria searchCriteria, String token);

    CustomResponse assignMarketplace(JsonNode marketplaceEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryMarketplace();
}