package com.catalogue.verg.extensionequipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface ExtensionequipmentService {

    // token: the raw Authorization header from the caller
    CustomResponse createExtensionequipment(JsonNode extensionequipmentEntity, String token);

    CustomResponse updateExtensionequipment(String id, JsonNode extensionequipmentEntity);

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    CustomResponse draftExtensionequipment(JsonNode extensionequipmentEntity, String token);

    // Lifecycle: (re-)submit a DRAFT/REWORK record for approval -> PENDING (full validation)
    CustomResponse addExtensionequipment(String id, JsonNode extensionequipmentEntity, String token);

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    CustomResponse approveExtensionequipment(LifecycleRequest request, String token);

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK | PENDING
    CustomResponse reviewExtensionequipment(LifecycleRequest request, String token);

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status)
    CustomResponse toggleStatus(String id, String token);

    CustomResponse searchExtensionequipment(SearchCriteria searchCriteria, String token);

    CustomResponse assignExtensionequipment(JsonNode extensionequipmentEntity, String token);

    CustomResponse read(String id, String token);

    CustomResponse delete(String id, String token);

    CustomResponse importData(MultipartFile file, String token);

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    CustomResponse loadFromPrimaryExtensionequipment();
}