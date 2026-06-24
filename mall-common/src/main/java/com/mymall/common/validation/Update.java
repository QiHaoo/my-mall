package com.mymall.common.validation;

/**
 * 校验分组标记接口 —— 修改
 *
 * <p>用于 {@code @Validated(Update.class)} 触发修改场景的参数校验（如要求 id 必填）。
 * 与 {@link Create} 配合，使同一个 DTO 可在新增/修改时应用不同校验规则。
 *
 * @see com.mymall.common.validation.Create
 */
public interface Update {
}
