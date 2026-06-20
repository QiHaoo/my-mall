package com.mymall.common.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * <p>
 * 每个模块对应一个 @Test 方法，在 IDEA 中右键运行即可生成对应模块的代码。
 * 生成输出到 mall-{module}/src/main/java/com/mymall/{module}/ 下。
 * <p>
 * 使用步骤：
 * 1. 确保目标模块的数据库和表已创建
 * 2. 在 IDEA 中右键点击对应模块的方法 → Run
 * 3. 生成代码自动输出到目标模块
 */
class CodeGenerator {

    // ==================== 公共配置 ====================
    private static final String DB_HOST     = "localhost";
    private static final String DB_PORT     = "3306";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "root123";
    // ==================================================

    // ==================== 商品模块 (mall-product) ====================
    @Test
    void generateProduct() {
        generate("mymall-pms", "product", "pms_", new String[]{
                "pms_attr", "pms_attr_attrgroup_relation", "pms_attr_group",
                "pms_brand", "pms_category", "pms_category_brand_relation",
                "pms_comment_replay", "pms_product_attr_value",
                "pms_sku_images", "pms_sku_info", "pms_sku_sale_attr_value",
                "pms_spu_comment", "pms_spu_images", "pms_spu_info", "pms_spu_info_desc"
        });
    }

    // ==================== 订单模块 (mall-order) ====================
    @Test
    void generateOrder() {
        generate("mymall-oms", "order", "oms_", new String[]{
                "oms_order", "oms_order_item", "oms_order_operate_history",
                "oms_order_return_apply", "oms_order_return_reason",
                "oms_order_setting", "oms_payment_info", "oms_refund_info"
        });
    }

    // ==================== 会员模块 (mall-member) ====================
    @Test
    void generateMember() {
        generate("mymall-ums", "member", "ums_", new String[]{
                "ums_growth_change_history", "ums_integration_change_history",
                "ums_member", "ums_member_collect_spu", "ums_member_collect_subject",
                "ums_member_level", "ums_member_login_log",
                "ums_member_receive_address", "ums_member_statistics_info"
        });
    }

    // ==================== 库存模块 (mall-ware) ====================
    @Test
    void generateWare() {
        generate("mymall-wms", "ware", "wms_", new String[]{
                "wms_purchase", "wms_purchase_detail",
                "wms_ware_info", "wms_ware_order_task",
                "wms_ware_order_task_detail", "wms_ware_sku"
        });
    }

    // ==================== 营销模块 (mall-coupon) ====================
    @Test
    void generateCoupon() {
        generate("mymall-sms", "coupon", "sms_", new String[]{
                "sms_coupon", "sms_coupon_history",
                "sms_coupon_spu_category_relation", "sms_coupon_spu_relation",
                "sms_home_adv", "sms_home_subject", "sms_home_subject_spu",
                "sms_member_price", "sms_seckill_promotion", "sms_seckill_session",
                "sms_seckill_sku_notice", "sms_seckill_sku_relation",
                "sms_sku_full_reduction", "sms_sku_ladder", "sms_spu_bounds"
        });
    }

    // ==================== 生成引擎 ====================

    private void generate(String database, String moduleName, String tablePrefix, String[] tables) {
        Path projectRoot = getProjectRoot();
        Path javaOutputDir = projectRoot.resolve("mall-" + moduleName + "/src/main/java");
        Path xmlOutputDir  = projectRoot.resolve("mall-" + moduleName + "/src/main/resources/mapper/" + moduleName);
        String dbUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

        System.out.println("========== 生成模块: mall-" + moduleName + " ==========");
        System.out.println("数据库: " + database + "，表数量: " + tables.length);
        System.out.println("Java 输出: " + javaOutputDir);
        System.out.println("XML  输出: " + xmlOutputDir);

        FastAutoGenerator.create(dbUrl, DB_USERNAME, DB_PASSWORD)
                .globalConfig(builder -> builder
                        .author("mymall")
                        .outputDir(javaOutputDir.toString())
                        .disableOpenDir()
                )
                .packageConfig(builder -> builder
                        .parent("com.mymall")
                        .moduleName(moduleName)
                        .pathInfo(Collections.singletonMap(OutputFile.xml, xmlOutputDir.toString()))
                )
                .strategyConfig(builder -> {
                    builder.addInclude(tables)
                           .addTablePrefix(tablePrefix);

                    builder.entityBuilder()
                           .enableLombok();

                    builder.mapperBuilder()
                           .enableBaseResultMap();

                    builder.controllerBuilder()
                           .disable();
                })
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("代码生成完成！");
    }

    /**
     * 定位项目根目录（兼容 IDEA 从任意子模块运行）
     */
    private static Path getProjectRoot() {
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 2; i++) {
            if (dir.resolve("mall-common").toFile().isDirectory()) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Path.of(System.getProperty("user.dir"));
    }
}
