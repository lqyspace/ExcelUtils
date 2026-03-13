package com.lqy.excel.utils.service.impl;

import com.lqy.excel.utils.service.Converter;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @ClassName: DefaultConverter
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/13 15:37
 **/
public class DefaultConverter implements Converter {
    private Field field;

    public DefaultConverter() {}

    public DefaultConverter(Field field) {
        this.field = field;
    }

    @Override
    public String write(Object value) {
        return value == null? "" : value.toString();
    }

    @Override
    public Object read(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (field == null) return value; // 没有字段信息默认返回 String

        Class<?> type = field.getType();
        try {
            if (type == Long.class) return Long.parseLong(value.trim());
            if (type == Integer.class) return Integer.parseInt(value.trim());
            if (type == Double.class) return Double.parseDouble(value.trim());
            if (type == Boolean.class) return Boolean.parseBoolean(value.trim());
            if (type == String.class) return value.trim();
            if (type == LocalDate.class) return LocalDate.parse(value.trim());
            if (type == LocalDateTime.class) return LocalDateTime.parse(value.trim());
            // 其它类型可继续扩展
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert value: " + value + " to " + type, e);
        }
        return value;
    }
}
