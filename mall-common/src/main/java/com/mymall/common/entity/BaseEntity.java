package com.mymall.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共实体基类
 *
 * <p>所有业务实体应继承本类，复用主键、审计、逻辑删除、乐观锁字段，表中不再重复定义。
 *
 * <ul>
 *   <li>主键：ASSIGN_ID（雪花算法，分布式友好，对 ShardingSphere 分片友好）</li>
 *   <li>审计：createTime/updateTime/createBy/updateBy（由 MyMetaObjectHandler 自动填充）</li>
 *   <li>逻辑删除：isDeleted（0=正常，1=已删除，配合 @TableLogic）</li>
 *   <li>乐观锁：version（配合 @Version + OptimisticLockerInnerInterceptor）</li>
 * </ul>
 *
 * <p>对应数据库列：id / create_time / update_time / create_by / update_by / is_deleted / version。
 * 建表规范见 docs/table-design-specification.md。
 */
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人用户 ID，由 MetaObjectHandler 从 UserContext 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 更新人用户 ID，由 MetaObjectHandler 从 UserContext 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    /** 乐观锁版本号，新增时填充为 1，更新时由插件自动 +1 并校验 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
