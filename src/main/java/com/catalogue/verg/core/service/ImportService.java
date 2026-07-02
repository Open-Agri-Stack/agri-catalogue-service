package com.catalogue.verg.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.catalogue.verg.core.dto.CustomResponse;
import com.catalogue.verg.core.dto.ImportResult;
import com.catalogue.verg.core.exception.CustomException;
import com.catalogue.verg.core.util.FileProcessService;
import com.catalogue.verg.core.util.PayloadValidation;
import com.catalogue.verg.core.util.TypeCoercionUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic service that handles bulk import of CSV/XLSX files for any catalogue entity.
 * <p>
 * Each entity's ServiceImpl delegates to this service, passing its validation file path
 * and its existing create method as a Function reference.
 * <p>
 * Supports partial-success: valid rows are persisted, invalid rows are reported with errors.
 * Enforces a 5MB maximum file size.
 */
@Slf4j
@Service
public class ImportService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB

    @Autowired
    private FileProcessService fileProcessService;

    @Autowired
    private PayloadValidation payloadValidation;

    @Autowired
    private TypeCoercionUtil typeCoercionUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Processes a bulk import file (CSV or XLSX).
     *
     * @param file             the uploaded multipart file
     * @param validationFile   classpath path to the payload validation JSON schema
     * @param createFunction   reference to the entity's existing create method
     * @return a CustomResponse containing an ImportResult with per-row outcomes
     */
    public CustomResponse processBulkImport(
            MultipartFile file,
            String validationFile,
            Function<JsonNode, CustomResponse> createFunction) {

        log.info("ImportService::processBulkImport::started for schema: {}", validationFile);
        CustomResponse response = new CustomResponse();

        // 1. Validate file is present
        if (file == null || file.isEmpty()) {
            log.warn("ImportService::processBulkImport::file is null or empty");
            throw new CustomException("IMPORT_ERROR", "File is empty or not provided", HttpStatus.BAD_REQUEST);
        }

        // 2. Enforce 5MB file size limit
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("ImportService::processBulkImport::file size {} exceeds limit of {} bytes",
                    file.getSize(), MAX_FILE_SIZE_BYTES);
            throw new CustomException("IMPORT_ERROR",
                    "File size exceeds the maximum allowed limit of 5MB", HttpStatus.BAD_REQUEST);
        }

        // 3. Parse the file into rows
        List<Map<String, String>> rows;
        try {
            rows = fileProcessService.processExcelFile(file);
        } catch (Exception e) {
            log.error("ImportService::processBulkImport::error parsing file: {}", e.getMessage());
            throw new CustomException("IMPORT_ERROR",
                    "Failed to parse file: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        if (rows == null || rows.isEmpty()) {
            log.warn("ImportService::processBulkImport::no data rows found in file");
            throw new CustomException("IMPORT_ERROR", "No data rows found in the file", HttpStatus.BAD_REQUEST);
        }

        // 4. Process each row with partial-success semantics
        ImportResult importResult = new ImportResult();
        importResult.setTotalRows(rows.size());
        importResult.setSuccessRecords(new ArrayList<>());
        importResult.setFailureRecords(new ArrayList<>());

        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2; // +2 because row 1 is the header, data starts at row 2
            Map<String, String> rowData = rows.get(i);

            try {
                // Convert string values to properly-typed JsonNode based on schema
                ObjectNode typedRow = typeCoercionUtil.coerceRow(validationFile, rowData);

                // Validate against the JSON schema
                payloadValidation.validatePayload(validationFile, typedRow);

                // Delegate to the entity's existing create method
                CustomResponse createResponse = createFunction.apply(typedRow);

                // Record success
                Map<String, Object> successRecord = new HashMap<>();
                successRecord.put("rowNumber", rowNumber);
                successRecord.putAll(createResponse.getResult());
                importResult.getSuccessRecords().add(successRecord);
                successCount++;

                log.debug("ImportService::processBulkImport::row {} created successfully", rowNumber);

            } catch (CustomException e) {
                // Record failure with validation or processing error
                Map<String, Object> failureRecord = new HashMap<>();
                failureRecord.put("rowNumber", rowNumber);
                failureRecord.put("data", rowData);
                failureRecord.put("errors", e.getMessage());
                importResult.getFailureRecords().add(failureRecord);
                failureCount++;

                log.warn("ImportService::processBulkImport::row {} failed: {}", rowNumber, e.getMessage());

            } catch (Exception e) {
                // Record unexpected errors
                Map<String, Object> failureRecord = new HashMap<>();
                failureRecord.put("rowNumber", rowNumber);
                failureRecord.put("data", rowData);
                failureRecord.put("errors", "Unexpected error: " + e.getMessage());
                importResult.getFailureRecords().add(failureRecord);
                failureCount++;

                log.error("ImportService::processBulkImport::row {} unexpected error", rowNumber, e);
            }
        }

        importResult.setSuccessCount(successCount);
        importResult.setFailureCount(failureCount);

        // 5. Build response
        response.setMessage("Import completed");
        response.getResult().put("totalRows", importResult.getTotalRows());
        response.getResult().put("successCount", importResult.getSuccessCount());
        response.getResult().put("failureCount", importResult.getFailureCount());
        response.getResult().put("successRecords", importResult.getSuccessRecords());
        response.getResult().put("failureRecords", importResult.getFailureRecords());

        if (failureCount == 0) {
            response.setResponseCode(HttpStatus.OK);
        } else if (successCount == 0) {
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.setMessage("Import failed - all rows had errors");
        } else {
            response.setResponseCode(HttpStatus.OK);
            response.setMessage("Import completed with some errors");
        }

        log.info("ImportService::processBulkImport::completed. Total: {}, Success: {}, Failures: {}",
                importResult.getTotalRows(), successCount, failureCount);

        return response;
    }
}
