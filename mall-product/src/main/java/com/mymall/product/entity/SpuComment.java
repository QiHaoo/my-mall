package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评价
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu_comment")
public class SpuComment extends BaseEntity {

    /**
     * sku_id
     */
    private Long skuId;

    /**
     * spu_id
     */
    private Long spuId;

    /**
     * 商品名字
     */
    private String spuName;

    /**
     * 会员昵称
     */
    private String memberNickName;

    /**
     * 星级
     */
    private Boolean star;

    /**
     * 会员ip
     */
    private String memberIp;

    /**
     * 显示状态[0-不显示，1-显示]
     */
    private Boolean showStatus;

    /**
     * 购买时属性组合
     */
    private String spuAttributes;

    /**
     * 点赞数
     */
    private Integer likesCount;

    /**
     * 回复数
     */
    private Integer replyCount;

    /**
     * 评论图片/视频[json数据；[{type:文件类型,url:资源路径}]]
     */
    private String resources;

    /**
     * 内容
     */
    private String content;

    /**
     * 用户头像
     */
    private String memberIcon;

    /**
     * 评论类型[0 - 对商品的直接评论，1 - 对评论的回复]
     */
    private Byte commentType;
}
