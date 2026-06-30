package com.catalogue.verg.core.exception;

import lombok.Builder;
import lombok.Value;
import org.springframework.http.HttpStatus;

@Value
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private int httpStatusCode;
}
