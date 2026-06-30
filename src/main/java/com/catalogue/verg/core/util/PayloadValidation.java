package com.catalogue.verg.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;

import com.catalogue.verg.core.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PayloadValidation {

    private Logger logger = LoggerFactory.getLogger(PayloadValidation.class);

    public void validatePayload(String fileName, JsonNode payload) {
//    log.info("PayloadValidation::validatePayload:inside");
        try {
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance();
            InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(fileName);
            JsonSchema schema = schemaFactory.getSchema(schemaStream);
            if (payload.isArray()) {
                for (JsonNode objectNode : payload) {
                    validateObject(schema, objectNode);
                }
            } else{
                validateObject(schema, payload);
            }
        } catch (Exception e) {
            logger.error("Failed to validate payload", e);
            throw new CustomException("Failed to validate payload", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private void validateObject(JsonSchema schema, JsonNode objectNode) {
        Set<ValidationMessage> validationMessages = schema.validate(objectNode);
        if (!validationMessages.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Validation error(s): \n");
            for (ValidationMessage message : validationMessages) {
                errorMessage.append(message.getMessage()).append("\n");
            }
            logger.error("Validation Error", errorMessage.toString());
            throw new CustomException("Validation Error", errorMessage.toString(), HttpStatus.BAD_REQUEST);
        }
    }
}