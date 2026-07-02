package com.catalogue.verg.livestock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LivestockService {

    CustomResponse createLivestock(JsonNode livestockEntity);

    CustomResponse searchLivestock(SearchCriteria searchCriteria);

    CustomResponse assignLivestock(JsonNode livestockEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}