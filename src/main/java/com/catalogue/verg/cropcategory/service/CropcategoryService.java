package com.catalogue.verg.cropcategory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CropcategoryService {

    CustomResponse createCropcategory(JsonNode cropcategoryEntity);

    CustomResponse searchCropcategory(SearchCriteria searchCriteria);

    CustomResponse assignCropcategory(JsonNode cropcategoryEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}