package com.catalogue.verg.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class FileProcessService {

    public List<Map<String, String>> processExcelFile(MultipartFile incomingFile) {
        log.info("DesignationServiceImpl::processExcelFile");
        try {
            return validateFileAndProcessRows(incomingFile);
        } catch (Exception e) {
            log.error("Error occurred during file processing: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<Map<String, String>> validateFileAndProcessRows(MultipartFile file) {
        log.info("DesignationServiceImpl::validateFileAndProcessRows");
        log.info("DesignationServiceImpl::validateFileAndProcessRows");
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new RuntimeException("File name is null");
        }

        try (InputStream inputStream = file.getInputStream()) {
            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(inputStream)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    return processSheetAndSendMessage(sheet);
                }
            } else if (fileName.endsWith(".csv")) {
                return processCsvAndSendMessage(inputStream);
            } else {
                throw new RuntimeException("Unsupported file type: " + fileName);
            }
        } catch (IOException e) {
            log.error("Error while processing file: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<Map<String, String>> processSheetAndSendMessage(Sheet sheet) {
        log.info("DesignationServiceImpl::processSheetAndSendMessage");
        try {
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            List<Map<String, String>> dataRows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row dataRow = sheet.getRow(rowIndex);
                if (dataRow == null) {
                    break; // No more data rows, exit the loop
                }
                boolean allBlank = true;
                Map<String, String> rowData = new HashMap<>();
                for (int colIndex = 0; colIndex < headerRow.getLastCellNum(); colIndex++) {
                    Cell headerCell = headerRow.getCell(colIndex);
                    Cell valueCell = dataRow.getCell(colIndex);
                    if (headerCell != null && headerCell.getCellType() != CellType.BLANK) {
                        String excelHeader =
                                formatter.formatCellValue(headerCell).replaceAll("[\\n*]", "").trim();
                        String cellValue = "";
                        if (valueCell != null && valueCell.getCellType() != CellType.BLANK) {
                            if (valueCell.getCellType() == CellType.NUMERIC
                                    && DateUtil.isCellDateFormatted(valueCell)) {
                                // Handle date format
                                Date date = valueCell.getDateCellValue();
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                                cellValue = dateFormat.format(date);
                            } else {
                                cellValue = formatter.formatCellValue(valueCell).replace("\n", ",").trim();
                            }
                            allBlank = false;
                        }
                        rowData.put(excelHeader, cellValue);
                    }
                }
                if (allBlank) {
                    break; // If all cells are blank in the current row, stop processing
                }
                dataRows.add(rowData);
            }
            log.info("Number of Data Rows Processed: " + dataRows.size());
            return dataRows;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<Map<String, String>> processCsvAndSendMessage(InputStream inputStream) throws IOException {
        log.info("DesignationServiceImpl::processCsvAndSendMessage");
        List<Map<String, String>> dataRows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            List<String> headers = csvParser.getHeaderNames();

            for (CSVRecord csvRecord : csvParser) {
                boolean allBlank = true;
                Map<String, String> rowData = new HashMap<>();
                for (String header : headers) {
                    String cellValue = csvRecord.get(header);
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        // Handle date format (assuming date is in a specific format)
                        if (isDate(cellValue)) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                            cellValue = dateFormat.format(parseDate(cellValue));
                        } else {
                            cellValue = cellValue.replace("\n", ",").trim();
                        }
                        allBlank = false;
                    }
                    rowData.put(header, cellValue);
                }
                if (allBlank) {
                    break; // If all cells are blank in the current row, stop processing
                }
                dataRows.add(rowData);
            }
            log.info("Number of Data Rows Processed: " + dataRows.size());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
        return dataRows;
    }

    private boolean isDate(String value) {
        try {
            parseDate(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Date parseDate(String value) throws Exception {
        // Customize this date parsing logic based on the expected date format in your CSV
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return dateFormat.parse(value);
    }


}