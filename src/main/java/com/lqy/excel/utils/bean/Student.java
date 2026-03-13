package com.lqy.excel.utils.bean;

import com.lqy.excel.utils.annotation.ExcelColumn;
import com.lqy.excel.utils.service.impl.GenderConverter;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @ClassName: Student
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/13 17:09
 **/
@Data
public class Student implements Serializable {
    private static final long serialVersionUID = -3860790841533416521L;

    @ExcelColumn(header = "学号", order = 1)
    private Long studentId;

    @ExcelColumn(header = "姓名", order = 2)
    private String StudentName;

    @ExcelColumn(header = "年龄", order = 3)
    private Integer age;

    @ExcelColumn(header = "生日", order = 4, dateFormat = "yyyy-MM-dd")
    private LocalDate birthday;

    @ExcelColumn(header = "性别", order = 5, converter = GenderConverter.class)
    private Integer sex;

    @ExcelColumn(header = "班级", order = 6)
    private String classNo;

    @ExcelColumn(header = "年级", order = 7)
    private String levelNo;

    private String remark;
}
