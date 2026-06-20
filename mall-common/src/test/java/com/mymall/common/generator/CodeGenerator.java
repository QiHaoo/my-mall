package com.mymall.common.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * <p>
 * 放在 src/test/java 下，不打包进正式产物，仅开发时运行。
 * <p>
 * 使用步骤：
 * 1. 修改下方常量配置（URL / MODULE_NAME / TABLES / TABLE_PREFIX）
 * 2. 右键 Run 'CodeGenerator.main()'
 * 3. 生成代码输出到 mall-{MODULE_NAME}/src/main/java/com/mymall/{MODULE_NAME}/
 */
public class CodeGenerator {

    // ========== 修改这几项配置 ==========
    private static final String URL = "jdbc:mysql://localhost:3306/mymall_product?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root123";
    private static final String MODULE_NAME = "product";           // 模块名
    private static final String[] TABLES = {"pms_spu_info", "pms_sku_info"};  // 表名
    private static final String TABLE_PREFIX = "pms_";             // 表前缀（生成时去掉）
    // ====================================

    private static final String PARENT_PACKAGE = "com.mymall";
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + "/mall-" + MODULE_NAME + "/src/main/java";
    private static final String XML_OUTPUT_DIR = System.getProperty("user.dir") + "/mall-" + MODULE_NAME + "/src/main/resources/mapper/" + MODULE_NAME;

    public static void main(String[] args) {
        FastAutoGenerator.create(URL, USERNAME, PASSWORD)
                .globalConfig(builder -> builder
                        .author("mymall")
                        .outputDir(OUTPUT_DIR)
                        .disableOpenDir()
                )
                .packageConfig(builder -> builder
                        .parent(PARENT_PACKAGE)
                        .moduleName(MODULE_NAME)
                        .pathInfo(Collections.singletonMap(
                                OutputFile.xml, XML_OUTPUT_DIR))
                )
                .strategyConfig(builder -> builder
                        .addInclude(TABLES)
                        .addTablePrefix(TABLE_PREFIX)
                )
                .strategyConfig(builder -> builder
                        .entityBuilder()
                        .superClass("com.mymall.common.entity.BaseEntity")
                        .enableLombok()
                        .naming(NamingStrategy.underline_to_camel)
                        .columnNaming(NamingStrategy.underline_to_camel)
                        .addIgnoreColumns("id", "create_time", "update_time")  // 公共字段由 BaseEntity 管理
                )
                .strategyConfig(builder -> builder
                        .mapperBuilder()
                        .enableBaseResultMap()
                )
                .strategyConfig(builder -> builder
                        .serviceBuilder()
                        .formatServiceFileName("%sService")
                        .formatServiceImplFileName("%sServiceImpl")
                )
                .strategyConfig(builder -> builder
                        .controllerBuilder()
                        .disable()  // 不生成 Controller，手写
                )
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("代码生成完成！输出目录：" + OUTPUT_DIR);
    }
}
