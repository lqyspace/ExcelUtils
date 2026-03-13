package com.lqy.excel.utils.service;

/**
 * @ClassName: Converter
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/13 15:37
 **/
public interface Converter {
    String write(Object value);

    Object read(String value);
}
