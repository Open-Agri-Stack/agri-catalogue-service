package com.catalogue.verg.extensionequipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface ExtensionequipmentService {

    CustomResponse createExtensionequipment(JsonNode extensionequipmentEntity);

    CustomResponse searchExtensionequipment(SearchCriteria searchCriteria);

    CustomResponse assignExtensionequipment(JsonNode extensionequipmentEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}