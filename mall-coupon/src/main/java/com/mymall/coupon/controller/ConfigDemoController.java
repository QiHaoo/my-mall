package com.mymall.coupon.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nacos 配置中心演示 Controller
 * <p>
 * 演示功能：
 * 1. @Value + @RefreshScope 动态刷新
 * 2. 功能开关（Feature Toggle）
 * 3. 动态阈值调整（限流模拟）
 * 4. 服务降级策略
 * 5. Map 类型配置（分类折扣）
 * 6. A/B 测试（百分比灰度）
 * 7. SpEL 表达式配置
 * 8. ContextRefresher 手动刷新 API
 * <p>
 * 使用方式：
 * 1. 在 Nacos 控制台创建配置：mall-coupon.yaml
 * 2. 调用接口查看配置效果
 * 3. 修改 Nacos 配置后再次调用，观察动态刷新（无需重启）
 */
@RestController
@RequestMapping("/config-demo")
@RefreshScope
public class ConfigDemoController {

    private final ContextRefresher contextRefresher;

    public ConfigDemoController(
            // Spring Cloud 2023+ 注册了两个 ContextRefresher:
            // configDataContextRefresher (spring.config.import) 和 legacyContextRefresher (bootstrap)
            @Qualifier("configDataContextRefresher") ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    // ==================== 1. 基础配置 + 动态刷新 ====================

    @Value("${coupon.demo.message:默认消息-本地配置}")
    private String message;

    @Value("${coupon.demo.discount:0.9}")
    private Double discount;

    // ==================== 2. 功能开关（Feature Toggle）====================

    @Value("${coupon.demo.promotion-enabled:false}")
    private Boolean promotionEnabled;

    @Value("${coupon.demo.promotion-name:默认促销}")
    private String promotionName;

    @Value("${coupon.demo.promotion-rule:满100减10}")
    private String promotionRule;

    // ==================== 3. 动态阈值（限流模拟）====================

    @Value("${coupon.demo.rate-limit:100}")
    private Integer rateLimit;

    private final AtomicLong requestCounter = new AtomicLong(0);

    // ==================== 4. 服务降级策略 ====================

    @Value("${coupon.demo.degrade-level:0}")
    private Integer degradeLevel;

    // ==================== 5. Map 配置（分类折扣）====================

    @Value("${coupon.demo.category-discount.数码:0.95}")
    private Double discountDigital;

    @Value("${coupon.demo.category-discount.家电:0.90}")
    private Double discountAppliance;

    @Value("${coupon.demo.category-discount.服饰:0.85}")
    private Double discountClothing;

    @Value("${coupon.demo.category-discount.食品:0.98}")
    private Double discountFood;

    // ==================== 6. A/B 测试（灰度百分比）====================

    @Value("${coupon.demo.ab-test.enabled:false}")
    private Boolean abTestEnabled;

    @Value("${coupon.demo.ab-test.percentage:50}")
    private Integer abTestPercentage;

    private final AtomicLong abTestCounter = new AtomicLong(0);

    // ==================== 7. SpEL 表达式配置 ====================

    @Value("#{'${coupon.demo.hot-categories:数码,家电,服饰}'.split(',')}")
    private List<String> hotCategories;

    @Value("#{${coupon.demo.max-order-items:5}}")
    private Integer maxOrderItems;

    // ==================== 接口 ====================

    /**
     * 获取所有演示配置概览
     * GET /config-demo/all
     */
    @GetMapping("/all")
    public Map<String, Object> getAllConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("discount", discount);
        result.put("promotionEnabled", promotionEnabled);
        result.put("rateLimit", rateLimit);
        result.put("degradeLevel", degradeLevel);
        result.put("abTestEnabled", abTestEnabled);
        result.put("hotCategories", hotCategories);
        result.put("_tip", "修改 Nacos 配置后无需重启，再次调用即可看到新值");
        result.put("_timestamp", currentTime());
        return result;
    }

    /**
     * 手动触发配置刷新（演示 ContextRefresher）
     * POST /config-demo/refresh
     * <p>
     * 说明：正常情况 Nacos 会自动推送变更，此接口用于手动触发刷新
     */
    @PostMapping("/refresh")
    public Map<String, Object> manualRefresh() {
        Set<String> changedKeys = contextRefresher.refresh();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refreshed", true);
        result.put("changedKeys", changedKeys);
        result.put("_tip", "通常不需要手动刷新，Nacos 会自动推送变更。此接口演示 ContextRefresher API");
        return result;
    }

    /**
     * 功能开关演示：促销活动中台
     * GET /config-demo/promotion-center
     * <p>
     * 演示：通过配置开关控制整个促销模块的可用性
     * 关闭时返回降级响应，不执行促销逻辑
     */
    @GetMapping("/promotion-center")
    public Map<String, Object> promotionCenter() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!promotionEnabled) {
            // 功能关闭：直接返回降级响应
            result.put("status", "CLOSED");
            result.put("message", "促销模块已关闭，所有促销活动不可用");
            result.put("promotionList", Collections.emptyList());
            result.put("_tip", "将 coupon.demo.promotion-enabled 改为 true 开启促销模块");
            return result;
        }

        // 功能开启：返回完整促销信息
        result.put("status", "OPEN");
        result.put("promotionName", promotionName);
        result.put("promotionRule", promotionRule);

        // 模拟促销活动列表
        List<Map<String, Object>> promotions = new ArrayList<>();
        promotions.add(Map.of("id", 1, "name", promotionName, "rule", promotionRule, "status", "进行中"));
        promotions.add(Map.of("id", 2, "name", "新人专享", "rule", "首单立减20", "status", "进行中"));
        result.put("promotionList", promotions);
        result.put("_tip", "动态修改 promotion-name 和 promotion-rule 可实时更新促销内容");
        return result;
    }

    /**
     * 动态限流演示：模拟 API 调用
     * GET /config-demo/api-call
     * <p>
     * 演示：通过配置动态调整限流阈值，无需重启
     * 真实生产环境应使用 Sentinel，这里仅演示配置动态生效的能力
     */
    @GetMapping("/api-call")
    public Map<String, Object> simulateApiCall() {
        long currentCount = requestCounter.incrementAndGet();
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("currentRateLimit", rateLimit);
        result.put("totalRequests", currentCount);

        if (currentCount > rateLimit) {
            // 触发限流
            result.put("allowed", false);
            result.put("message", "请求被限流，当前阈值: " + rateLimit + " 次");
            result.put("_tip", "将 coupon.demo.rate-limit 调大可以允许更多请求");
        } else {
            // 放行
            result.put("allowed", true);
            result.put("remaining", rateLimit - currentCount);
            result.put("message", "请求放行，剩余配额: " + (rateLimit - currentCount));
        }
        return result;
    }

    /**
     * 重置限流计数器
     * POST /config-demo/reset-counter
     */
    @PostMapping("/reset-counter")
    public Map<String, Object> resetCounter() {
        requestCounter.set(0);
        abTestCounter.set(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reset", true);
        result.put("message", "所有计数器已重置");
        return result;
    }

    /**
     * 服务降级演示：模拟优惠券查询
     * GET /config-demo/coupons
     * <p>
     * 演示：通过 degrade-level 控制服务降级程度
     * 0 = 完整模式（查数据库 + 缓存 + 推荐）
     * 1 = 部分降级（只查缓存，不推荐）
     * 2 = 完全降级（返回静态数据）
     */
    @GetMapping("/coupons")
    public Map<String, Object> queryCoupons() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("degradeLevel", degradeLevel);

        switch (degradeLevel) {
            case 0 -> {
                // 完整模式：模拟查 DB + 缓存 + 推荐算法
                result.put("mode", "完整模式");
                result.put("dataSource", "DB + Cache + Recommend");
                result.put("coupons", List.of(
                        Map.of("id", 1, "name", "满100减20", "source", "DB"),
                        Map.of("id", 2, "name", "满200减50", "source", "Cache"),
                        Map.of("id", 3, "name", "新人专享券", "source", "Recommend")
                ));
                result.put("recommendReason", "根据您的购物偏好推荐");
            }
            case 1 -> {
                // 部分降级：只返回缓存数据
                result.put("mode", "部分降级");
                result.put("dataSource", "Cache Only");
                result.put("coupons", List.of(
                        Map.of("id", 1, "name", "满100减20", "source", "Cache")
                ));
                result.put("recommendReason", null);
                result.put("_warning", "推荐服务已降级，仅展示缓存数据");
            }
            case 2 -> {
                // 完全降级：返回静态兜底数据
                result.put("mode", "完全降级");
                result.put("dataSource", "Static Fallback");
                result.put("coupons", Collections.emptyList());
                result.put("_warning", "服务繁忙，优惠券功能暂时不可用");
            }
            default -> {
                result.put("mode", "未知降级级别");
                result.put("_error", "不支持的降级级别: " + degradeLevel);
            }
        }

        result.put("_tip", "修改 coupon.demo.degrade-level (0/1/2) 切换降级策略");
        return result;
    }

    /**
     * Map 配置演示：分类折扣计算
     * GET /config-demo/category-price?category=数码&price=200
     * <p>
     * 演示：通过 Map 类型配置为不同品类设置不同折扣
     * 修改 Nacos 配置可实时调整各品类折扣
     */
    @GetMapping("/category-price")
    public Map<String, Object> categoryPrice(
            @RequestParam(defaultValue = "数码") String category,
            @RequestParam(defaultValue = "100") BigDecimal price) {

        Map<String, Double> categoryDiscountMap = new LinkedHashMap<>();
        categoryDiscountMap.put("数码", discountDigital);
        categoryDiscountMap.put("家电", discountAppliance);
        categoryDiscountMap.put("服饰", discountClothing);
        categoryDiscountMap.put("食品", discountFood);

        Double categoryDiscount = categoryDiscountMap.getOrDefault(category, 1.0);
        BigDecimal finalPrice = price.multiply(BigDecimal.valueOf(categoryDiscount))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("originalPrice", price);
        result.put("categoryDiscount", categoryDiscount);
        result.put("finalPrice", finalPrice);
        result.put("allCategoryDiscounts", categoryDiscountMap);
        result.put("_tip", "修改 coupon.demo.category-discount.{品类} 可实时调整各品类折扣");
        return result;
    }

    /**
     * A/B 测试演示：模拟商品详情页两种布局
     * GET /config-demo/ab-test
     * <p>
     * 演示：通过配置控制灰度百分比，实现 A/B 测试
     * 根据请求计数模拟用户分流
     */
    @GetMapping("/ab-test")
    public Map<String, Object> abTest() {
        long requestNum = abTestCounter.incrementAndGet();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("abTestEnabled", abTestEnabled);

        if (!abTestEnabled) {
            result.put("version", "A（默认版本）");
            result.put("layout", "经典列表布局");
            result.put("_tip", "将 coupon.demo.ab-test.enabled 改为 true 开启 A/B 测试");
            return result;
        }

        // 根据百分比分流
        boolean isVersionB = (requestNum % 100) < abTestPercentage;
        result.put("version", isVersionB ? "B（实验版本）" : "A（默认版本）");
        result.put("layout", isVersionB ? "瀑布流卡片布局" : "经典列表布局");
        result.put("abPercentage", abTestPercentage + "%");
        result.put("requestNum", requestNum);
        result.put("totalA", abTestPercentage);
        result.put("totalB", 100 - abTestPercentage);
        result.put("_tip", "修改 coupon.demo.ab-test.percentage 调整 B 版本流量占比");
        return result;
    }

    /**
     * 折扣计算（配置驱动的业务逻辑）
     * GET /config-demo/calculate?price=100
     */
    @GetMapping("/calculate")
    public Map<String, Object> calculatePrice(Double price) {
        BigDecimal originalPrice = BigDecimal.valueOf(price);
        BigDecimal finalPrice = originalPrice.multiply(BigDecimal.valueOf(discount))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalPrice", originalPrice);
        result.put("discount", discount);
        result.put("finalPrice", finalPrice);
        result.put("_tip", "修改 coupon.demo.discount 可动态调整全局折扣");
        return result;
    }

    // ==================== 工具方法 ====================

    private String currentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

