package com.catalogue.verg.insecticide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;


public interface InsecticideService {

    CustomResponse createInsecticide(JsonNode insecticideEntity);

    CustomResponse searchInsecticide(SearchCriteria searchCriteria);

    CustomResponse assignInsecticide(JsonNode insecticideEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);
}