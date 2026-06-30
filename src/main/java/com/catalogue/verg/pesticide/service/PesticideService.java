package com.catalogue.verg.pesticide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;


public interface PesticideService {

    CustomResponse createPesticide(JsonNode pesticideEntity);

    CustomResponse searchPesticide(SearchCriteria searchCriteria);

    CustomResponse assignPesticide(JsonNode pesticideEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);
}