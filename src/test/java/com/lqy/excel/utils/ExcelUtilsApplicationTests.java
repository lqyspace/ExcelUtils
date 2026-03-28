package com.lqy.excel.utils;

import com.lqy.excel.utils.bean.Student;
import com.lqy.excel.utils.utils.ModelData2ExcelOrCsvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class ExcelUtilsApplicationTests {

    @Test
    void contextLoads() throws Exception {
        List<Student> students = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            Student student = new Student();
//            student.setStudentId(i + 1L);
//            student.setStudentName("小王" + i);
//            student.setAge(i + 10);
//            student.setBirthday(LocalDate.now());
//            student.setSex(i % 2 + 1);
//            student.setClassNo("No1000" + i);
//            student.setLevelNo("Level" + i);
//            student.setRemark("这是第" + i + "条数据");
//            students.add(student);
//        }
        File tempFile = ModelData2ExcelOrCsvUtil.export(students, Student.class, "student", ModelData2ExcelOrCsvUtil.FileType.CSV);
        System.out.println("临时文件生成路径：" + tempFile.getAbsolutePath());

        // 将临时文件复制到指定本地文件
        File localFile = new File("student2.csv");
        Files.copy(tempFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("已写入本地文件：" + localFile.getAbsolutePath());


        List<Student> students1 = ModelData2ExcelOrCsvUtil.importFile(new File("student2.csv"), Student.class);
        System.out.println(students1);
    }

}
