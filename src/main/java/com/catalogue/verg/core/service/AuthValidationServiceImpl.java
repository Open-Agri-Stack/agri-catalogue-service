package com.catalogue.verg.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.catalogue.verg.core.exception.CustomException;
import com.catalogue.verg.core.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@Slf4j
public class AuthValidationServiceImpl implements AuthValidationService {

        private static final String API_KEY_HEADER = "apikey";

        /** Identity recorded for an untokened caller on an endpoint where the jwt is optional. */
        private static final String ANONYMOUS_USER = "Anonymous";
        private static final String ANONYMOUS_ROLE = "PUBLIC";

        private final RestTemplate restTemplate;
        private final ObjectMapper objectMapper;

        @Value("${oas.auth.validate-url}")
        private String authValidateUrl;

        @Value("${oas.auth.api-key:}")
        private String authServiceApiKey;

        public AuthValidationServiceImpl(
                        RestTemplate restTemplate,
                        ObjectMapper objectMapper) {
                this.restTemplate = restTemplate;
                this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode validateToken(String authorizationHeader, boolean jwtRequired) {

                if (authorizationHeader == null || authorizationHeader.isBlank()) {
                        // Open endpoint: no token supplied, so the caller is the anonymous public user
                        if (!jwtRequired) {
                                log.debug("AuthValidationService::validateToken::no token supplied on an "
                                                + "optional-jwt endpoint, continuing as {}", ANONYMOUS_USER);
                                return anonymousContext();
                        }
                        throw new IllegalArgumentException(
                                        "Authorization header is required");
                }

                try {

                        // Remove "Bearer " before sending the token to OAS
                        String token = authorizationHeader;

                        if (token.startsWith("Bearer ")) {
                                token = token.substring(7);
                        }

                        // Prepare request headers
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        // OAS requires the API key using the "apikey" header
                        if (authServiceApiKey != null && !authServiceApiKey.isBlank()) {
                                headers.set(API_KEY_HEADER, authServiceApiKey.trim());
                        } else {
                                log.warn("AuthValidationService::validateToken::no api key configured, "
                                                + "calling auth_service without the {} header", API_KEY_HEADER);
                        }

                        // OAS expects the caller's token in the request body, under "apikey"
                        ObjectNode body = objectMapper.createObjectNode();
                        body.put("token", token);

                        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, headers);

                        log.info("AuthValidationService::validateToken::posting to {} for token validation",
                                        authValidateUrl);

                        // Call OAS Auth Service
                        ResponseEntity<JsonNode> response = restTemplate.exchange(
                                        authValidateUrl,
                                        HttpMethod.POST,
                                        requestEntity,
                                        JsonNode.class);

                        log.info("AuthValidationService::validateToken::auth_service responded with status: {}",
                                        response.getStatusCode());

                        // Check HTTP response
                        if (!response.getStatusCode().is2xxSuccessful()) {
                                throw new IllegalStateException(
                                                "Token validation failed. HTTP status: "
                                                                + response.getStatusCode());
                        }

                        JsonNode responseBody = response.getBody();

                        if (responseBody == null || responseBody.isNull()) {
                                throw new IllegalStateException(
                                                "Empty response from Auth Service");
                        }

                        log.debug("AuthValidationService::validateToken::auth_service response body: {}",
                                        responseBody);
                        return extractUserContext(responseBody);

                } catch (HttpClientErrorException e) {

                        // 4xx: OAS answered and refused the call - a bad/expired token, or a bad api key.
                        // Actual API key is never logged.
                        log.error("AuthValidationService::validateToken::auth_service rejected the call to {} "
                                        + "with status {} (api key present: {}), body: {}",
                                        authValidateUrl,
                                        e.getStatusCode(),
                                        authServiceApiKey != null && !authServiceApiKey.isBlank(),
                                        e.getResponseBodyAsString());

                        throw new CustomException(Constants.ERROR, "Token validation failed",
                                        HttpStatus.UNAUTHORIZED);

                } catch (HttpServerErrorException e) {

                        // 5xx: OAS answered, but it is broken - not the caller's fault
                        log.error("AuthValidationService::validateToken::auth_service errored on {} "
                                        + "with status {}, body: {}",
                                        authValidateUrl, e.getStatusCode(), e.getResponseBodyAsString());

                        throw new CustomException(Constants.ERROR, "Auth service returned an error",
                                        HttpStatus.BAD_GATEWAY);

                } catch (RestClientException e) {

                        // No HTTP response at all - connection refused, timeout, DNS failure
                        log.error("AuthValidationService::validateToken::unable to reach auth_service at {}: {}",
                                        authValidateUrl, e.getMessage());

                        throw new CustomException(Constants.ERROR,
                                        "Unable to communicate with OAS Auth Service",
                                        HttpStatus.SERVICE_UNAVAILABLE);
                }
        }

        /**
         * Stand-in caller identity for an untokened request on an endpoint that does not require a
         * jwt. Uses the same three keys as {@link #extractUserContext}, so audit rows populate the
         * userName / userId / userRole columns exactly as they do for an authenticated caller.
         */
        private JsonNode anonymousContext() {
                ObjectNode userContext = objectMapper.createObjectNode();
                userContext.put("userName", ANONYMOUS_USER);
                userContext.put("userId", ANONYMOUS_USER);
                userContext.put("functionalRole", ANONYMOUS_ROLE);
                return userContext;
        }

        /**
         * Narrows the full OAS envelope down to the caller identity this service cares about.
         *
         * <pre>
         * {
         *   "message": null,
         *   "params": { "status": "success", ... },
         *   "responseCode": "OK",
         *   "result": {
         *     "sub": "...", "active": true, "user_id": "user-123521372921",
         *     "display_name": "FIELD_OFFICER", "functional_role": "MAKER",
         *     "org_id": "...", "email": "...", "exp": 1787735740, ...
         *   }
         * }
         * </pre>
         *
         * becomes {@code {"display_name": ..., "user_id": ..., "functional_role": ...}}.
         *
         * <p>All three fields are required: a response missing any of them is rejected as
         * UNAUTHORIZED rather than passed upstream half-populated.
         */
        private JsonNode extractUserContext(JsonNode response) {

                // 1. Identity lives under "result"; everything above it is response plumbing
                JsonNode result = response.path("result");
                if (!result.isObject()) {
                        log.error("AuthValidationService::extractUserContext::auth_service returned no result object");
                        throw new CustomException(Constants.ERROR, "Token validation failed",
                                        HttpStatus.UNAUTHORIZED);
                }

                // 2. Without a user id there is no caller to attribute the request to
                String userId = result.path("user_id").asText(null);
                if (userId == null || userId.isBlank()) {
                        log.error("AuthValidationService::extractUserContext::auth_service returned no user_id");
                        throw new CustomException(Constants.ERROR, "Token validation failed",
                                        HttpStatus.UNAUTHORIZED);
                }

                // 3. Display name identifies the caller in audit trails
                String displayName = result.path("display_name").asText(null);
                if (displayName == null || displayName.isBlank()) {
                        log.error("AuthValidationService::extractUserContext::auth_service returned no display_name "
                                        + "for user_id: {}", userId);
                        throw new CustomException(Constants.ERROR, "Token validation failed",
                                        HttpStatus.UNAUTHORIZED);
                }

                // 4. Functional role is what any downstream permission check keys off
                String functionalRole = result.path("functional_role").asText(null);
                if (functionalRole == null || functionalRole.isBlank()) {
                        log.error("AuthValidationService::extractUserContext::auth_service returned no "
                                        + "functional_role for user_id: {}", userId);
                        throw new CustomException(Constants.ERROR, "Token validation failed",
                                        HttpStatus.UNAUTHORIZED);
                }

                ObjectNode userContext = objectMapper.createObjectNode();
                userContext.put("userName", displayName);
                userContext.put("userId", userId);
                userContext.put("functionalRole", functionalRole);

                return userContext;
        }

}