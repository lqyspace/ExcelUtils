package com.lqy.excel.utils.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqy.excel.utils.annotation.ExcelColumn;
import com.lqy.excel.utils.service.Converter;
import com.lqy.excel.utils.service.impl.DefaultConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName: ModelData2ExcelOrCsvUtil
 * @Description: 模型数据转换成Excel或者Csv, 泛型工具类
 * @Author: XiaoYun
 * @Date: 2026/3/13 14:35
 **/
@Slf4j
public class ModelData2ExcelOrCsvUtil {
    /**
     * 使用方法说明：
     * 1、创建一个实体类，并打上ExcelColumn注解，指定表头名称和排序等信息；
     * 1.1、创建一个实体类，并打上ExcelColumn注解，指定表头名称和排序等信息；可以将List<T>数据转换成Excel或者Csv文件，或者将Excel或者Csv文件转换成List<T>数据。
     * 1.2、创建一个实体类，并打上ExcelColumn注解，可以通过importable或exportable注解，指定该字段是否参与导入或导出；
     * 1.3、创建一个实体类，对于没有ExcelColumn注解的属性字段，将忽略不处理；
     * 2、List<T> --> Excel 时创建的文件会通过MAX_ROWS_PER_SHEET进行自动分页；Excel --> List<T> 时，只读第一个sheet，将Excel文件转换成List<T>数据。
     */
    /**
     * 文件类型枚举
     */
    public enum FileType {
        EXCEL,
        CSV
    }

    // 默认每个Sheet最大行数（Excel限制约1048576）
    private static final int MAX_ROWS_PER_SHEET = 100_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ---------------------------- 导出接口 ----------------------------
    /**
     * 导出入口, 将list数据转换成Excel或者Csv
     * @param data
     * @param clazz
     * @param fileName
     * @param type
     * @return File
     * @param <T>
     */
    public static <T> File export(
            List<T> data,
            Class<T> clazz,
            String fileName,
            FileType type) {
        try {
            File file = File.createTempFile(
                    fileName,
                    type == FileType.EXCEL ? ".xlsx" : ".csv"
            );
            List<ColumnTitleMeta> columns = parseColumns(clazz, true);
            if (type == FileType.EXCEL) {
                writeExcel(file, data, columns);
            } else {
                writeCsv(file, data, columns);
            }

            return file;

        } catch (Exception e) {
            throw new RuntimeException("export error", e);
        }
    }

    /**
     * 导出到HTTP响应流
     */
    public static <T> void exportToResponse(
            HttpServletResponse response,
            List<T> data,
            Class<T> clazz,
            String fileName,
            FileType type) throws Exception {

        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8")
                + (type == FileType.EXCEL ? ".xlsx" : ".csv"));
        response.setContentType("application/octet-stream");

        if (type == FileType.EXCEL) {
            writeExcel(response.getOutputStream(), data, parseColumns(clazz, true));
        } else {
            writeCsv(response.getOutputStream(), data, parseColumns(clazz, true));
        }
    }

    // ---------------------------- 导入接口 ----------------------------
    /**
     * 导入入口, 读取Excel或者Csv文件, 转换成List数据
     * @param file
     * @param clazz
     * @return
     * @param <T>
     */
    public static <T> List<T> importFile(
            File file,
            Class<T> clazz) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".csv")) {
            return readCsv(file, clazz);
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return readExcel(file, clazz);
        }
        throw new RuntimeException("unsupported file");
    }

    // ---------------------------- 核心方法 ----------------------------
    private static <T> void writeExcel(File file, List<T> data, List<ColumnTitleMeta> columns) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             FileOutputStream out = new FileOutputStream(file)) {
            writeExcel(workbook, data, columns);
            workbook.write(out);
        }
    }
    private static <T> void writeExcel(OutputStream out, List<T> data, List<ColumnTitleMeta> columns) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            writeExcel(workbook, data, columns);
            workbook.write(out);
        }
    }
    private static <T> void writeExcel(SXSSFWorkbook workbook, List<T> data, List<ColumnTitleMeta> columns) throws Exception {
        int totalRows = data.size();
        int sheetCount = (totalRows + MAX_ROWS_PER_SHEET - 1) / MAX_ROWS_PER_SHEET;

        for (int s = 0; s < sheetCount; s++) {
            Sheet sheet = workbook.createSheet("Sheet" + (s + 1));
            int start = s * MAX_ROWS_PER_SHEET;
            int end = Math.min(start + MAX_ROWS_PER_SHEET, totalRows);

            // 写表头
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).header);
            }

            // 写数据
            for (int i = start; i < end; i++) {
                T item = data.get(i);
                Row row = sheet.createRow(i - start + 1);
                for (int j = 0; j < columns.size(); j++) {
                    ColumnTitleMeta col = columns.get(j);
                    Object val = col.field.get(item);
                    row.createCell(j).setCellValue(formatValue(val, col));
                }
            }
        }
    }

    private static <T> void writeCsv(File file, List<T> data, List<ColumnTitleMeta> columns) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columns.stream().map(c -> c.header).toArray(String[]::new)))) {

            for (T item : data) {
                List<String> row = new ArrayList<>();
                for (ColumnTitleMeta col : columns) {
                    Object val = col.field.get(item);
                    row.add(formatValue(val, col));
                }
                printer.printRecord(row);
            }
        }
    }
    private static <T> void writeCsv(OutputStream out, List<T> data, List<ColumnTitleMeta> columns) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columns.stream().map(c -> c.header).toArray(String[]::new)))) {

            for (T item : data) {
                List<String> row = new ArrayList<>();
                for (ColumnTitleMeta col : columns) {
                    Object val = col.field.get(item);
                    row.add(formatValue(val, col));
                }
                printer.printRecord(row);
            }
        }
    }
    private static <T> List<T> readExcel(File file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ColumnTitleMeta> columns = parseColumns(clazz, false);
            Map<Integer, ColumnTitleMeta> map = new HashMap<>();

            Row header = sheet.getRow(0);
            for (Cell cell : header) {
                String h = cell.getStringCellValue();
                columns.stream().filter(c -> c.header.equals(h)).findFirst().ifPresent(c -> map.put(cell.getColumnIndex(), c));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                T obj = clazz.newInstance();
                for (Map.Entry<Integer, ColumnTitleMeta> e : map.entrySet()) {
                    Cell cell = row.getCell(e.getKey());
                    if (cell == null) continue;
                    try {
                        e.getValue().field.set(obj, e.getValue().converter.read(cell.toString()));
                    } catch (Exception ex) {
                        log.warn("Row {} Column {} convert fail: {}", i, e.getValue().header, ex.getMessage());
                    }
                }
                list.add(obj);
            }
        }
        return list;
    }

    private static <T> List<T> readCsv(File file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            List<ColumnTitleMeta> columns = parseColumns(clazz, false);
            Map<String, ColumnTitleMeta> map = columns.stream().collect(Collectors.toMap(c -> c.header, c -> c));

            for (CSVRecord record : parser) {
                T obj = clazz.newInstance();
                for (String header : record.toMap().keySet()) {
                    ColumnTitleMeta col = map.get(header);
                    if (col == null) continue;
                    try {
                        col.field.set(obj, col.converter.read(record.get(header)));
                    } catch (Exception e) {
                        log.warn("CSV row {} header {} convert fail: {}", record.getRecordNumber(), header, e.getMessage());
                    }
                }
                list.add(obj);
            }
        }
        return list;
    }

    // ---------------------------- 辅助方法 ----------------------------
    /**
     * 解析字段
     * @param clazz
     * @param export
     * @return
     */
    private static List<ColumnTitleMeta> parseColumns(
            Class<?> clazz,
            boolean export) {
        List<ColumnTitleMeta> columns = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            ExcelColumn column = field.getAnnotation(ExcelColumn.class);
            if (column == null) {
                continue;
            }
            if (export && !column.export()) {
                continue;
            }
            field.setAccessible(true);
            ColumnTitleMeta meta = new ColumnTitleMeta();
            meta.field = field;
            meta.header = column.header();
            meta.order = column.order();
            meta.dateFormat = column.dateFormat();
            meta.converter = createConverter(column.converter(), field);
            columns.add(meta);
        }
        return columns.stream()
                .sorted(Comparator.comparingInt(c -> c.order))
                .collect(Collectors.toList());
    }
    /**
     * 创建转换器
     * @param clazz
     * @return
     */
    private static Converter createConverter(
            Class<? extends Converter> clazz,
            Field field) {
        try {
            if (clazz == DefaultConverter.class) {
                return new DefaultConverter(field);
            }
            return clazz.newInstance();
        } catch (Exception e) {
            return new DefaultConverter(field);
        }
    }

    /**
     * 格式化值
     * @param value
     * @param column
     * @return
     */
    private static String formatValue(
            Object value,
            ColumnTitleMeta column) {
        if (value == null) {
            return "";
        }
        if (value instanceof Date &&
                !column.dateFormat.isEmpty()) {
            return new SimpleDateFormat(
                    column.dateFormat)
                    .format((Date) value);
        }
        return column.converter.write(value);
    }

    // ---------------------------- 内部类 ----------------------------
    /**
     * 解析列标题元数据
     */
    private static class ColumnTitleMeta {
        Field field;
        String header;
        int order;
        String dateFormat;
        Converter converter;
    }
}
