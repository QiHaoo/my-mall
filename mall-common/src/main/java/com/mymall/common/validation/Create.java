package com.mymall.common.validation;

/**
 * 校验分组标记接口 —— 新增
 *
 * <p>用于 {@code @Validated(Create.class)} 触发新增场景的参数校验。
 * 与 {@link Update} 配合，使同一个 DTO 可在新增/修改时应用不同校验规则。
 *
 * @see com.mymall.common.validation.Update
 */
public interface Create {
}
