package com.catalogue.verg.livestock.util;

import com.catalogue.verg.livestock.constants.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
public class NotificationUtil {

    private static final int MAX_RETRIES = 3;

    private final RestClient restClient;

    @Value("${org-user-service.base-url}")
    private String baseUrl;

    @Value("${org-user-service.api-key}")
    private String apiKey;

    public NotificationUtil(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public void sendNotification(
            NotificationTemplate template,
            Map<String, String> templateVariables
    ) {

        String templateModule = template.templateModule();
        String templateCode = template.templateCode();

        NotificationRequest request = new NotificationRequest(
                templateModule,
                templateCode,
                templateVariables,
                "PORTAL",
                null,
                null
        );

        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            try {
                ResponseEntity<Void> response = restClient.post()
                        .uri(baseUrl + "/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("apikey", apiKey)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();

                log.info(
                        "Notification sent: templateCode={} attempt={} status={}",
                        templateCode,
                        attempt,
                        response.getStatusCode()
                );

                return;

            } catch (RestClientException e) {

                lastError = e;

                log.error(
                        "Notification failed: templateCode={} attempt={}/{} error={}",
                        templateCode,
                        attempt,
                        MAX_RETRIES,
                        e.getMessage(),
                        e
                );
            }
        }

        // All retry attempts failed
        log.error(
                "Notification failed after {} attempts: templateCode={}",
                MAX_RETRIES,
                templateCode,
                lastError
        );
    }

    /**
     * Request body sent to the notification service.
     */
    private record NotificationRequest(
            String templateModule,
            String templateCode,
            Map<String, String> templateVariables,
            String notificationChannel,
            String emailId,
            String phoneNumber
    ) {
    }
}