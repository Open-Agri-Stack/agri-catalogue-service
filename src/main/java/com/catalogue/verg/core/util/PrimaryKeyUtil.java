package com.catalogue.verg.core.util;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrimaryKeyUtil {

    private final ObjectMapper objectMapper;

    public String generateKey(String fileName) {
        log.info("PrimaryKeyUtil::generateKey::reading schema: {}", fileName);
        String prefix = extractPrefixFromSchema(fileName);
        UUID idUuid = Uuids.timeBased();

        if (prefix != null) {
            String key = prefix + generateRandomDigits();
            log.info("PrimaryKeyUtil::generateKey::generated prefix+random key: {}+{}",prefix, key);
            return key;
        } else {
            log.info("PrimaryKeyUtil::generateKey::no prefix found, falling back to UUID");
            return String.valueOf(idUuid);
        }
    }

    private String extractPrefixFromSchema(String fileName) {
        try {
            InputStream schemaStream = getClass().getResourceAsStream(fileName);
            if (schemaStream == null) {
                log.warn("PrimaryKeyUtil::extractPrefixFromSchema::schema file not found: {}", fileName);
                return null;
            }
            JsonNode schemaNode = objectMapper.readTree(schemaStream);
            JsonNode properties = schemaNode.get("properties");

            if (properties != null) {
                for (JsonNode property : properties) {
                    // Look for any property that has BOTH "prefix" and "key" attributes
                    if (property.has("prefix") && property.has("key")&& property.get("key").asText().equalsIgnoreCase("primary")) {
                        String prefix = property.get("prefix").asText();
                        log.debug("PrimaryKeyUtil::extractPrefixFromSchema::found prefix: {}", prefix);
                        return prefix;
                    }
                }
            }
        } catch (Exception e) {
            log.error("PrimaryKeyUtil::extractPrefixFromSchema::error parsing schema file", e);
        }
        return null;
    }

    private String generateRandomDigits() {
        SecureRandom random = new SecureRandom();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }


}