package com.catalogue.verg.fertilizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;


public interface FertilizerService {

    CustomResponse createFertilizer(JsonNode fertilizerEntity);

    CustomResponse searchFertilizer(SearchCriteria searchCriteria);

    CustomResponse assignFertilizer(JsonNode fertilizerEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);
}