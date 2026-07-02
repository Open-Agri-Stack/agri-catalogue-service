package com.catalogue.verg.soil.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface SoilService {

    CustomResponse createSoil(JsonNode soilEntity);

    CustomResponse searchSoil(SearchCriteria searchCriteria);

    CustomResponse assignSoil(JsonNode soilEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}