package com.catalogue.verg.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * Converts a Map<String, String> (from CSV/XLSX parsing) into a properly-typed
 * JsonNode based on the types declared in the payload validation JSON schema.
 *
 * For example, if the schema declares "Number": { "type": "integer" },
 * the string value "42" is converted to an IntNode(42) instead of a TextNode("42").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TypeCoercionUtil {

    private final ObjectMapper objectMapper;

    /**
     * Converts a row of string values into a typed ObjectNode based on the JSON schema.
     *
     * @param validationFileName the classpath resource path to the validation schema
     * @param rowData            a map of column-header → string-value from CSV/XLSX
     * @return a properly-typed ObjectNode ready for JSON schema validation
     */
    public ObjectNode coerceRow(String validationFileName, Map<String, String> rowData) {
        ObjectNode result = objectMapper.createObjectNode();
        JsonNode schemaProperties = loadSchemaProperties(validationFileName);

        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                // Skip empty values — let schema validation catch missing required fields
                continue;
            }

            String declaredType = getSchemaType(schemaProperties, key);
            result.set(key, convertValue(value, declaredType));
        }

        return result;
    }

    /**
     * Loads the "properties" section from a JSON schema file.
     */
    private JsonNode loadSchemaProperties(String fileName) {
        try {
            InputStream schemaStream = getClass().getResourceAsStream(fileName);
            if (schemaStream == null) {
                log.warn("TypeCoercionUtil::loadSchemaProperties::schema not found: {}", fileName);
                return objectMapper.createObjectNode();
            }
            JsonNode schemaNode = objectMapper.readTree(schemaStream);
            JsonNode properties = schemaNode.get("properties");
            return properties != null ? properties : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("TypeCoercionUtil::loadSchemaProperties::error reading schema: {}", fileName, e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Looks up the declared "type" for a given property name in the schema.
     * Returns "string" as the default if the property or type is not found.
     */
    private String getSchemaType(JsonNode schemaProperties, String propertyName) {
        if (schemaProperties == null || !schemaProperties.has(propertyName)) {
            return "string";
        }
        JsonNode propertySchema = schemaProperties.get(propertyName);
        if (propertySchema.has("type")) {
            return propertySchema.get("type").asText("string");
        }
        return "string";
    }

    /**
     * Converts a string value to the appropriate JsonNode type based on the schema type declaration.
     */
    private JsonNode convertValue(String value, String declaredType) {
        try {
            switch (declaredType) {
                case "integer":
                    // Try parsing as long to handle large integers
                    long longVal = Long.parseLong(value.trim());
                    if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                        return new IntNode((int) longVal);
                    }
                    return new LongNode(longVal);
                case "number":
                    return new DoubleNode(Double.parseDouble(value.trim()));
                default:
                    // "string", "date", or any unrecognized type → keep as text
                    return new TextNode(value);
            }
        } catch (NumberFormatException e) {
            log.warn("TypeCoercionUtil::convertValue::failed to convert '{}' to type '{}', keeping as string",
                    value, declaredType);
            // Return as text; JSON schema validation will catch the type mismatch
            return new TextNode(value);
        }
    }
}
