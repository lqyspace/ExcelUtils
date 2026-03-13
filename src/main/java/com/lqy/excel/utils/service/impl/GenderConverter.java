package com.lqy.excel.utils.service.impl;

import com.lqy.excel.utils.service.Converter;

import java.util.Objects;

/**
 * @ClassName: SexConverter
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/13 17:17
 **/
public class GenderConverter implements Converter {
    @Override
    public String write(Object value) {
        if (Objects.isNull(value)) return "";
        int gender;
        if (value instanceof Number) {
            gender = ((Number) value).intValue();
        } else {
            gender = Integer.parseInt(value.toString());
        }
        switch (gender) {
            case 1:
                return "男";
            case 2:
                return "女";
            default:
                return "未知";
        }
    }

    @Override
    public Object read(String value) {
        if (Objects.isNull(value) || value.trim().isEmpty()) {
            return null;
        }
        switch (value.trim()) {
            case "男":
                return 1;
            case "女":
                return 2;
            default:
                return -1;
        }
    }
}
