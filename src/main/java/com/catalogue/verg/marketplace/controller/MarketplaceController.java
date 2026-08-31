package com.catalogue.verg.marketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.config.LifecyclePolicy;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.LifecycleRequest;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.marketplace.service.MarketplaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/marketplace")
public class MarketplaceController {
    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private LifecyclePolicy lifecyclePolicy;

    /** Key this catalogue is looked up by in the lifecycle switches. */
    private static final String CATALOGUE_NAME = "marketplace";

    //@PostMapping("/v1/create")
    public ResponseEntity<CustomResponse> create(@RequestBody JsonNode marketplaceDetails) {
        CustomResponse response = marketplaceService.createMarketplace(marketplaceDetails, null);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Lifecycle: create an incomplete DRAFT (relaxed validation)
    @PostMapping("/v1/draft")
    public ResponseEntity<CustomResponse> draft(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody JsonNode marketplaceDetails) {
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = marketplaceService.draftMarketplace(marketplaceDetails, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Creates a new record (full validation). With the lifecycle on it lands PENDING and has
    // to be approved then reviewed; with the lifecycle off it lands ACTIVE straight away.
    @PostMapping("/v1/add")
    public ResponseEntity<CustomResponse> add(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody JsonNode marketplaceDetails) {
        CustomResponse response = marketplaceService.createMarketplace(marketplaceDetails, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Lifecycle: (re-)submit an existing DRAFT/REWORK record for approval (PENDING, full validation)
    @PutMapping("/v1/add/{id}")
    public ResponseEntity<CustomResponse> addById(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String id, @RequestBody JsonNode marketplaceDetails) {
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = marketplaceService.addMarketplace(id, marketplaceDetails, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Lifecycle: PENDING -> APPROVED | REJECTED | REWORK
    @PutMapping("/v1/approve")
    public ResponseEntity<CustomResponse> approve(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody LifecycleRequest request) {
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = marketplaceService.approveMarketplace(request, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Lifecycle: APPROVED -> ACTIVE(published) | REJECTED | REWORK
    @PutMapping("/v1/review")
    public ResponseEntity<CustomResponse> review(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody LifecycleRequest request) {
        lifecyclePolicy.requireEnabled(CATALOGUE_NAME);
        CustomResponse response = marketplaceService.reviewMarketplace(request, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Toggle a live record between ACTIVE and INACTIVE (rejects any other status).
    // Deliberately NOT gated: plain activate/deactivate, not part of the approval chain, and
    // with the lifecycle off it is the only way to take a record offline short of deleting it.
    @PutMapping("/v1/toggle/{id}")
    public ResponseEntity<CustomResponse> toggle(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String id) {
        CustomResponse response = marketplaceService.toggleStatus(id, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/v1/search")
    public ResponseEntity<?> search(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody SearchCriteria searchCriteria) {
        CustomResponse response = marketplaceService.searchMarketplace(searchCriteria, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v1/read/{id}")
    public ResponseEntity<?> read(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String id) {
        CustomResponse response = marketplaceService.read(id, token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/v1/update/{id}")
    public ResponseEntity<CustomResponse> update(@PathVariable String id, @RequestBody JsonNode marketplaceDetails) {
        CustomResponse response = marketplaceService.updateMarketplace(id, marketplaceDetails);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/v1/delete/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String id) {
        CustomResponse response = marketplaceService.delete(id, token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/v1/import")
    public ResponseEntity<CustomResponse> importData(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("file") MultipartFile file) {
        CustomResponse response = marketplaceService.importData(file, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    // Drops the ES index and rebuilds it from the primary store (Postgres); skips DELETED records
    @PostMapping("/v1/loadFromPrimary")
    public ResponseEntity<CustomResponse> loadFromPrimary() {
        CustomResponse response = marketplaceService.loadFromPrimaryMarketplace();
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}