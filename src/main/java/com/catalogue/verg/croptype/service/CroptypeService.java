package com.catalogue.verg.croptype.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CroptypeService {

    CustomResponse createCroptype(JsonNode croptypeEntity);

    CustomResponse searchCroptype(SearchCriteria searchCriteria);

    CustomResponse assignCroptype(JsonNode croptypeEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}