package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评价回复关系
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_comment_replay")
public class CommentReplay extends BaseEntity {

    /**
     * 评论id
     */
    private Long commentId;

    /**
     * 回复id
     */
    private Long replyId;
}
