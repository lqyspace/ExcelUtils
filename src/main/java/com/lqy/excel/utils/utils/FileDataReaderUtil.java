package com.lqy.excel.utils.utils;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @ClassName: FileDataReaderUtils
 * @Description: 文件数据读取工具类，可兼容处理xlsx、xls、csv格式文件
 * @Author: XiaoYun
 * @Date: 2026/3/6 15:58
 **/
@Slf4j
public class FileDataReaderUtil {
    /**
     * 泛型读取 CSV 或 Excel 文件
     *
     * @param filePath 文件路径（支持 .csv, .xlsx, .xls）
     * @param startRow 起始行索引（从0开始），从0开始表示读取所有行，包括标题行
     * @param rowMapper 每行数据转换函数
     * @param <T>      泛型对象
     * @return List<T>
     */
    public static <T> List<T> readFile(String filePath, int startRow, Function<List<String>, T> rowMapper) {
        if (filePath.endsWith(".csv")) {
            return readCsv(filePath, startRow, rowMapper);
        } else if (filePath.endsWith(".xlsx") || filePath.endsWith(".xls")) {
            return readExcel(filePath, startRow, rowMapper);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filePath);
        }
    }

    private static <T> List<T> readCsv(String filePath, int startRow, Function<List<String>, T> rowMapper) {
        List<T> result = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> allRows = reader.readAll();
            for (int i = startRow; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                if (Objects.isNull(row)) continue;
                List<String> rowData = Arrays.stream(row)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .collect(Collectors.toList());
                try {
                    T item = rowMapper.apply(rowData);
                    result.add(item);
                } catch (Exception e) {
                    System.err.println("Error mapping CSV row " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[FileDataReaderUtils.readCsv] Error reading file, e: {}", e.getMessage(), e);
            throw new RuntimeException("Error reading CSV file: " + e.getMessage(), e);
        }
        return result;
    }

    private static <T> List<T> readExcel(String filePath, int startRow, Function<List<String>, T> rowMapper) {
        List<T> result = new ArrayList<>();
        Path path = Paths.get(filePath);
        try (Workbook workbook = filePath.endsWith(".xls")? new HSSFWorkbook(Files.newInputStream(path)) :new XSSFWorkbook(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (Objects.isNull(row)) continue;
                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    if (Objects.isNull(cell)) {
                        rowData.add(""); // 空单元格占位
                        continue;
                    }
                    switch (cell.getCellType()) {
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                rowData.add(cell.getDateCellValue().toString());
                            } else {
                                rowData.add(String.valueOf((long) cell.getNumericCellValue()));
                            }
                            break;
                        case STRING:
                            rowData.add(cell.getStringCellValue().trim());
                            break;
                        case BOOLEAN:
                            rowData.add(String.valueOf(cell.getBooleanCellValue()));
                            break;
                        case FORMULA:
                            rowData.add(cell.getCellFormula());
                            break;
                        default:
                            rowData.add("");
                    }
                }
                try {
                    T item = rowMapper.apply(rowData);
                    result.add(item);
                } catch (Exception e) {
                    log.error("[FileDataReaderUtils.readExcel] Error mapping Excel row " + (i + 1) + ": " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[FileDataReaderUtils.readExcel] Error reading file, e: {}", e.getMessage(), e);
            throw new RuntimeException("Error reading Excel file: " + e.getMessage(), e);
        }
        return result;
    }
}
