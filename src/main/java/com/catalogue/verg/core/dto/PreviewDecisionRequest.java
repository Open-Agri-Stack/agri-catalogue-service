package com.catalogue.verg.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for the bulk preview decision endpoint: the ids handed back by importWithPreview,
 * plus what to do with them.
 * <p>
 * The wire format keeps the singular key {@code id} for the array, e.g.
 * {@code {"id": ["season-1", "season-2"], "decision": "confirm"}}. The Java field is named
 * {@code ids} so it does not read as a single value; {@code ids} is also accepted on the wire.
 * <p>
 * Unknown properties are ignored deliberately: the shared ObjectMapper bean is built from a plain
 * {@code new ObjectMapper()} (see JacksonConfig), so Spring Boot's relaxed defaults do not apply and
 * an unexpected key would otherwise surface as a 500 rather than a clean response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreviewDecisionRequest {

    @JsonProperty("id")
    @JsonAlias("ids")
    private List<String> ids;

    // "confirm" or "discard", matched case-insensitively
    private String decision;
}
