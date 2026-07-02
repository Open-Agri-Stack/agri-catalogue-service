package com.catalogue.verg.seed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface SeedService {

    CustomResponse createSeed(JsonNode seedEntity);

    CustomResponse searchSeed(SearchCriteria searchCriteria);

    CustomResponse assignSeed(JsonNode seedEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}