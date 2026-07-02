package com.catalogue.verg.location.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface LocationService {

    CustomResponse createLocation(JsonNode locationEntity);

    CustomResponse searchLocation(SearchCriteria searchCriteria);

    CustomResponse assignLocation(JsonNode locationEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}