package com.mymall.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mymall.product.dto.brand.BrandQueryDTO;
import com.mymall.product.dto.brand.BrandSaveDTO;
import com.mymall.product.dto.brand.BrandShowStatusDTO;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.entity.Brand;

import java.util.List;

/**
 * 品牌服务接口
 */
public interface IBrandService extends IService<Brand> {

    /**
     * 分页查询品牌（支持名称模糊、首字母、显示状态筛选）
     *
     * @param query 查询条件
     * @return 品牌分页结果
     */
    PageVO<BrandVO> pageQuery(BrandQueryDTO query);

    /**
     * 品牌详情（含关联三级分类 ID 列表）
     *
     * @param id 品牌ID
     * @return 品牌详情
     */
    BrandVO getBrandDetail(Long id);

    /**
     * 新增品牌（含 logo URL，可选关联多个三级分类）
     *
     * @param dto 新增参数
     */
    void saveBrand(BrandSaveDTO dto);

    /**
     * 修改品牌（同步刷新关联表冗余品牌名，categoryIds 非 null 时全量覆盖关联）
     *
     * @param dto 修改参数（id/version 必填）
     */
    void updateBrand(BrandSaveDTO dto);

    /**
     * 更新显示状态（0-隐藏 1-显示）
     *
     * @param id   品牌ID
     * @param dto  显示状态
     */
    void updateShowStatus(Long id, BrandShowStatusDTO dto);

    /**
     * 删除品牌（逻辑删除 + 引用检查：存在关联商品则拒绝）
     *
     * @param id 品牌ID
     */
    void removeBrand(Long id);

    /**
     * 查询某三级分类下的品牌（前台检索用，仅返回显示中的品牌）
     *
     * @param catelogId 三级分类ID
     * @return 品牌精简列表
     */
    List<BrandSimpleVO> listByCategory(Long catelogId);
}
