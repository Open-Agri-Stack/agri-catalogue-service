package com.catalogue.verg.core.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface AuthValidationService {

    /** Validates the caller's token, requiring one to be present. */
    default JsonNode validateToken(String authorizationHeader) {
        return validateToken(authorizationHeader, true);
    }

    /**
     * @param jwtRequired when false, a request arriving with no token is allowed through as the
     *                    anonymous PUBLIC caller instead of being rejected. A token that IS present
     *                    is always validated, whatever this flag says.
     */
    JsonNode validateToken(String authorizationHeader, boolean jwtRequired);
}