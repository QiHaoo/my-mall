package com.mymall.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应 VO
 *
 * <p>隔离 MyBatis-Plus {@link Page} 的内部字段（orders、optimizeCountSql 等），
 * 只暴露前端需要的 5 个字段。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Service 层
 * Page<Brand> page = new Page<>(query.getPageNum(), query.getPageSize());
 * Page<Brand> result = page(page, wrapper);
 * List<BrandVO> voList = result.getRecords().stream().map(this::toVO).toList();
 * PageVO<BrandVO> vo = PageVO.of(result).setRecords(voList);
 *
 * // Controller 层
 * public R<PageVO<BrandVO>> list(BrandQueryDTO query) {
 *     return R.ok(brandService.pageQuery(query));
 * }
 * }</pre>
 *
 * @param <T> 记录类型
 */
@Data
public class PageVO<T> implements Serializable {

    /** 数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long current;

    /** 每页数量 */
    private long size;

    /** 总页数 */
    private long pages;

    /**
     * 从 MyBatis-Plus Page 转换（records 类型一致时直接使用）
     */
    public static <T> PageVO<T> of(Page<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    /**
     * 从 MyBatis-Plus Page 转换（records 类型不一致时，调用方手动 setRecords）
     *
     * <pre>{@code
     * PageVO<BrandVO> vo = PageVO.ofRaw(result);
     * vo.setRecords(result.getRecords().stream().map(this::toVO).toList());
     * }</pre>
     */
    public static <T> PageVO<T> ofRaw(Page<?> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }
}
