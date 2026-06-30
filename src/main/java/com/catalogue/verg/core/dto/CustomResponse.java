package com.catalogue.verg.core.dto;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomResponse {
    private String message;
    private RespParam params;
    private HttpStatus responseCode;
    private Map<String, Object> result = new HashMap<>();
    public RespParam getParams() {
        if (params == null) {
            params = new RespParam();
        }
        return params;
    }
}