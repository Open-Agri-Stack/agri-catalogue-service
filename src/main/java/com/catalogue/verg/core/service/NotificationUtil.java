package com.catalogue.verg.core.service;

import com.catalogue.verg.core.constants.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class NotificationUtil {

    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 5000;

    private final RestClient restClient;

    @Value("${org-user-service.base-url}")
    private String baseUrl;

    @Value("${org-user-service.api-key}")
    private String apiKey;

    public NotificationUtil(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public void sendNotification(
            String templateModule,
            String templateCodeSuffix,
            NotificationTemplate template,
            Map<String, String> templateVariables,
            String orgId
    ) {

        String templateCode =
                template.templateCode()
                        + "_"
                        + templateCodeSuffix;

        NotificationRequest request = new NotificationRequest(
                templateModule,
                templateCode,
                templateVariables,
                "PORTAL",
                null,
                null,
                orgId
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
                        "Notification sent: templateModule={} templateCode={} attempt={} status={}",
                        templateModule,
                        templateCode,
                        attempt,
                        response.getStatusCode()
                );

                return;

            } catch (HttpClientErrorException e) {

                // 4xx is permanent: unknown template code, module mismatch, or nobody in this
                // org holds the receiver role. Retrying cannot change it.
                log.error(
                        "Notification rejected: templateModule={} templateCode={} orgId={} status={} body={}",
                        templateModule,
                        templateCode,
                        orgId,
                        e.getStatusCode(),
                        e.getResponseBodyAsString(),
                        e
                );

                return;

            } catch (Exception e) {

                lastError = e;

                log.error(
                        "Notification failed: templateModule={} templateCode={} attempt={}/{} error={}",
                        templateModule,
                        templateCode,
                        attempt,
                        MAX_RETRIES,
                        e.getMessage(),
                        e
                );
            }
        }

        log.error(
                "Notification failed after {} attempts: templateModule={} templateCode={}",
                MAX_RETRIES,
                templateModule,
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
            String phoneNumber,
            String orgId
    ) {
    }
}