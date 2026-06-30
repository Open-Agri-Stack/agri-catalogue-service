package com.catalogue.verg.core.exception;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandling {
    @ExceptionHandler(Exception.class)
    public ResponseEntity handleException(Exception ex) {
        log.debug("RestExceptionHandler::handleException::" + ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse errorResponse = null;
        if (ex instanceof CustomException) {
            CustomException CustomException = (CustomException) ex;
            status = HttpStatus.BAD_REQUEST;
            // Check if the CustomException provides an HTTP status code
            if (CustomException != null) {
                try {
                    status = CustomException.getHttpStatusCode();
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid HTTP status code provided in CustomException: " + CustomException.getHttpStatusCode());
                }
            }
            errorResponse = ErrorResponse.builder()
                    .code(CustomException.getCode())
                    .message(CustomException.getMessage())
                    .httpStatusCode(CustomException.getHttpStatusCode() != null
                            ? CustomException.getHttpStatusCode().value()
                            : status.value())
                    .build();
            if (StringUtils.isNotBlank(CustomException.getMessage())) {
                log.error(CustomException.getMessage());
            }

            return new ResponseEntity<>(errorResponse, status);
        }
        errorResponse = ErrorResponse.builder()
                .code("ERROR")
                .message(ex.getMessage())
                .httpStatusCode(status.value())
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }
}
