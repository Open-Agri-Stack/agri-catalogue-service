package com.catalogue.verg.locationconfig.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LocationconfigService {

    // token: the raw Authorization header from the caller
    CustomResponse createLocationconfig(JsonNode locationconfigEntity, String token);

    CustomResponse updateLocationconfig(String id, JsonNode locationconfigEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftLocationconfig(JsonNode locationconfigEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addLocationconfig(String id, JsonNode locationconfigEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveLocationconfig(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewLocationconfig(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchLocationconfig(SearchCriteria searchCriteria, String token);

    CustomResponse assignLocationconfig(JsonNode locationconfigEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryLocationconfig();
}