package com.lqy.excel.utils.annotation;

import com.lqy.excel.utils.service.Converter;
import com.lqy.excel.utils.service.impl.DefaultConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @ClassName: ExcelColumn
 * @Description:
 *  - 该注解使用在实体类字段上，用于定义字段的标题和顺序等；
 *  - 不加此注解的字段将不会被导出或导入，所以如果想要某个字段不参与导出或导入，可以加上此注解，并设置export和importable为false，或者不加此注解。
 * @Author: XiaoYun
 * @Date: 2026/3/13 15:32
 **/
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    /**
     * 标题字段
     */
    String header();

    /**
     * 列顺序
     */
    int order() default 0;

    /**
     * 字段是否参与导出
     */
    boolean export() default true;

    /**
     * 字段是否参与导入
     */
    boolean importable() default true;

    /**
     * 日期格式
     */
    String dateFormat() default "";

    /**
     * 字段值得 转换器
     */
    Class<? extends Converter> converter() default DefaultConverter.class;
}
