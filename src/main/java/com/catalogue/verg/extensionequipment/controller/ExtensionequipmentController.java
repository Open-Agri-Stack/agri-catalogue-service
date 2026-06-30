package com.catalogue.verg.extensionequipment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.extensionequipment.service.ExtensionequipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/extensionequipment")
public class ExtensionequipmentController {
    @Autowired
    private ExtensionequipmentService extensionequipmentService;

    @PostMapping("/v1/create")
    public ResponseEntity<CustomResponse> create(@RequestBody JsonNode extensionequipmentDetails) {
        CustomResponse response = extensionequipmentService.createExtensionequipment(extensionequipmentDetails);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/v1/search")
    public ResponseEntity<?> search(@RequestBody SearchCriteria searchCriteria) {
        CustomResponse response = extensionequipmentService.searchExtensionequipment(searchCriteria);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v1/read/{id}")
    public ResponseEntity<?> read(@PathVariable String id) {
        CustomResponse response = extensionequipmentService.read(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}