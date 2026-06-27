package com.mymall.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mymall.common.query.PageQuery;
import com.mymall.common.result.PageVO;

import java.util.List;

/**
 * 分页工具类
 *
 * <p>封装 PageQuery → MyBatis-Plus Page、Page → PageVO 的转换，
 * 避免每个 Service 重复构造和转换代码。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Service 层
 * Page<Brand> result = page(PageUtils.toPage(query), wrapper);
 * List<BrandVO> voList = result.getRecords().stream().map(this::toVO).toList();
 * return PageUtils.toPageVO(result, voList);
 * }</pre>
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * PageQuery → MyBatis-Plus Page
     *
     * @param query 分页查询参数
     * @return MyBatis-Plus 分页对象
     */
    public static Page<?> toPage(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /**
     * Page + VO 列表 → PageVO
     *
     * <p>从 MyBatis-Plus Page 提取分页信息（total/current/size/pages），
     * 配合调用方转换好的 VO 列表组装成 PageVO。
     *
     * @param page   MyBatis-Plus 查询结果（records 类型可能为 Entity）
     * @param records VO 列表（由调用方从 Entity 转换）
     * @return 分页响应 VO
     */
    public static <T> PageVO<T> toPageVO(Page<?> page, List<T> records) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }
}
