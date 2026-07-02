package com.catalogue.verg.cropvariety.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import org.springframework.web.multipart.MultipartFile;


public interface CropvarietyService {

    CustomResponse createCropvariety(JsonNode cropvarietyEntity);

    CustomResponse searchCropvariety(SearchCriteria searchCriteria);

    CustomResponse assignCropvariety(JsonNode cropvarietyEntity, String token);

    CustomResponse read(String id);

    CustomResponse delete(String id);

    CustomResponse importData(MultipartFile file);
}