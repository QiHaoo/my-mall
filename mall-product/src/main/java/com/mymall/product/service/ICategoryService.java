package com.mymall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mymall.product.dto.category.*;
import com.mymall.product.entity.Category;

import java.util.List;

/**
 * 商品三级分类服务接口
 */
public interface ICategoryService extends IService<Category> {

    /**
     * 查询分类树（仅返回 show_status = 1 的分类）
     *
     * @return 一级分类列表，每个节点嵌套 children
     */
    List<CategoryVO> listTree();

    /**
     * 新增分类
     * <p>
     * catLevel 由 parentCid 自动计算，前端不传。
     *
     * @param dto 新增参数
     */
    void saveCategory(CategorySaveDTO dto);

    /**
     * 修改分类基础信息
     *
     * @param dto 修改参数（catId 必填，其余非 null 字段更新）
     */
    void updateCategory(CategoryUpdateDTO dto);

    /**
     * 批量删除分类（逻辑删除：show_status 置 0）
     * <p>
     * 会递归检查子孙分类的引用关系，有引用则拒绝删除。
     *
     * @param ids 待删除分类 ID 列表
     */
    void batchDelete(List<Long> ids);

    /**
     * 拖拽排序（调整父节点、层级、排序值）
     * <p>
     * 支持批量节点同时拖拽。前端必须将本次受影响的子孙节点一并传入，
     * 并携带正确的 catLevel，服务端不做级联更新。
     *
     * @param dto 排序参数
     */
    void sortCategories(CategorySortDTO dto);
}
