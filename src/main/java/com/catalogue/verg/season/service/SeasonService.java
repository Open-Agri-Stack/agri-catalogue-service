package com.catalogue.verg.season.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface SeasonService {

    CustomResponse createSeason(JsonNode seasonEntity);

    CustomResponse searchSeason(SearchCriteria searchCriteria);

    CustomResponse assignSeason(JsonNode seasonEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}