package com.catalogue.verg.core.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    private int totalRows;
    private int successCount;
    private int failureCount;
    private List<Map<String, Object>> successRecords = new ArrayList<>();
    private List<Map<String, Object>> failureRecords = new ArrayList<>();
}
