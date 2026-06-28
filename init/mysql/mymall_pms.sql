-- ============================================================
-- 商品中心 pms 库初始化脚本（生产级 DDL，符合 docs/table-design-specification.md）
-- 主键统一 id BIGINT（雪花算法）；审计/逻辑删除/乐观锁字段统一；utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS mymall_pms
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mymall_pms;

drop table if exists pms_attr;
drop table if exists pms_attr_attrgroup_relation;
drop table if exists pms_attr_group;
drop table if exists pms_brand;
drop table if exists pms_category;
drop table if exists pms_category_brand_relation;
drop table if exists pms_comment_replay;
drop table if exists pms_product_attr_value;
drop table if exists pms_sku_images;
drop table if exists pms_sku_info;
drop table if exists pms_sku_sale_attr_value;
drop table if exists pms_spu_comment;
drop table if exists pms_spu_images;
drop table if exists pms_spu_info;
drop table if exists pms_spu_info_desc;

/*==============================================================*/
/* Table: pms_attr  商品属性                                    */
/*==============================================================*/
create table pms_attr
(
    id                bigint        not null                    comment '主键',
    attr_name         varchar(64)   not null                    comment '属性名',
    search_type       tinyint       not null default 0          comment '是否需要检索[0-不需要 1-需要]',
    value_type        tinyint                default 0          comment '值类型[0-单个值 1-多选值]',
    icon              varchar(512)           default null       comment '属性图标',
    value_select      varchar(512)           default null       comment '可选值列表[逗号分隔]',
    attr_type         tinyint       not null default 0          comment '属性类型[0-销售属性 1-基本属性 2-既是销售又是基本]',
    enable            tinyint       not null default 1          comment '启用状态[0-禁用 1-启用]',
    catelog_id        bigint                 default null       comment '所属分类id',
    show_desc         tinyint       not null default 0          comment '快速展示[0-否 1-是]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_catelog_id (catelog_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品属性';

/*==============================================================*/
/* Table: pms_attr_attrgroup_relation  属性&属性分组关联        */
/*==============================================================*/
create table pms_attr_attrgroup_relation
(
    id                bigint        not null                    comment '主键',
    attr_id           bigint        not null                    comment '属性id',
    attr_group_id     bigint        not null                    comment '属性分组id',
    attr_sort         int           not null default 0          comment '属性组内排序',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    unique key uk_attr_group (attr_id, attr_group_id),
    index idx_attr_group_id (attr_group_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='属性&属性分组关联';

/*==============================================================*/
/* Table: pms_attr_group  属性分组                              */
/*==============================================================*/
create table pms_attr_group
(
    id                bigint        not null                    comment '主键',
    attr_group_name   varchar(64)   not null                    comment '组名',
    sort              int           not null default 0          comment '排序',
    descript          varchar(512)           default null       comment '描述',
    icon              varchar(512)           default null       comment '组图标',
    catelog_id        bigint                 default null       comment '所属分类id',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_catelog_id (catelog_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='属性分组';

/*==============================================================*/
/* Table: pms_brand  品牌                                       */
/*==============================================================*/
create table pms_brand
(
    id                bigint        not null                    comment '主键',
    name              varchar(64)   not null                    comment '品牌名',
    logo              varchar(1024)           default null      comment '品牌logo地址',
    descript          text                    default null      comment '介绍',
    show_status       tinyint       not null default 1          comment '显示状态[0-不显示 1-显示]',
    first_letter      varchar(1)              default null      comment '检索首字母',
    sort              int           not null default 0          comment '排序',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    unique key uk_name (name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='品牌';

/*==============================================================*/
/* Table: pms_category  商品三级分类                            */
/*==============================================================*/
create table pms_category
(
    id                bigint        not null                    comment '主键',
    name              varchar(64)   not null                    comment '分类名称',
    parent_cid        bigint        not null default 0          comment '父分类id[0-一级分类]',
    cat_level         int           not null default 1          comment '层级[1/2/3]',
    show_status       tinyint       not null default 1          comment '是否显示[0-不显示 1-显示]',
    sort              int           not null default 0          comment '排序',
    icon              varchar(512)           default null       comment '图标地址',
    product_unit      varchar(64)            default null       comment '计量单位',
    product_count     int           not null default 0          comment '商品数量',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_parent (parent_cid, show_status, sort),
    unique index uk_parent_name (parent_cid, name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品三级分类';

/*==============================================================*/
/* Table: pms_category_brand_relation  品牌分类关联             */
/*==============================================================*/
create table pms_category_brand_relation
(
    id                bigint        not null                    comment '主键',
    brand_id          bigint        not null                    comment '品牌id',
    catelog_id        bigint        not null                    comment '分类id',
    brand_name        varchar(64)            default null       comment '品牌名（冗余）',
    catelog_name      varchar(64)            default null       comment '分类名（冗余）',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    unique key uk_brand_catelog (brand_id, catelog_id),
    index idx_catelog_id (catelog_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='品牌分类关联';

/*==============================================================*/
/* Table: pms_comment_replay  商品评价回复关系                  */
/*==============================================================*/
create table pms_comment_replay
(
    id                bigint        not null                    comment '主键',
    comment_id        bigint        not null                    comment '评论id',
    reply_id          bigint                 default null       comment '回复id',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_comment_id (comment_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品评价回复关系';

/*==============================================================*/
/* Table: pms_product_attr_value  spu属性值                     */
/*==============================================================*/
create table pms_product_attr_value
(
    id                bigint        not null                    comment '主键',
    spu_id            bigint        not null                    comment '商品id',
    attr_id           bigint        not null                    comment '属性id',
    attr_name         varchar(128)           default null       comment '属性名',
    attr_value        varchar(256)           default null       comment '属性值',
    attr_sort         int           not null default 0          comment '顺序',
    quick_show        tinyint       not null default 0          comment '快速展示[0-否 1-是]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='spu属性值';

/*==============================================================*/
/* Table: pms_sku_images  sku图片                               */
/*==============================================================*/
create table pms_sku_images
(
    id                bigint        not null                    comment '主键',
    sku_id            bigint        not null                    comment 'sku_id',
    img_url           varchar(1024)           default null      comment '图片地址',
    img_sort          int           not null default 0          comment '排序',
    default_img       tinyint       not null default 0          comment '默认图[0-否 1-是]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='sku图片';

/*==============================================================*/
/* Table: pms_sku_info  sku信息                                 */
/*==============================================================*/
create table pms_sku_info
(
    id                bigint        not null                    comment '主键',
    spu_id            bigint        not null                    comment 'spu_id',
    sku_name          varchar(255)           default null       comment 'sku名称',
    sku_desc          varchar(1024)          default null       comment 'sku介绍描述',
    catalog_id        bigint                 default null       comment '所属分类id',
    brand_id          bigint                 default null       comment '品牌id',
    sku_default_img   varchar(1024)          default null       comment '默认图片',
    sku_title         varchar(255)           default null       comment '标题',
    sku_subtitle      varchar(1024)          default null       comment '副标题',
    price             decimal(18,4)          default null       comment '价格',
    sale_count        bigint        not null default 0          comment '销量',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_spu_id (spu_id),
    index idx_catalog_id (catalog_id),
    index idx_brand_id (brand_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='sku信息';

/*==============================================================*/
/* Table: pms_sku_sale_attr_value  sku销售属性&值               */
/*==============================================================*/
create table pms_sku_sale_attr_value
(
    id                bigint        not null                    comment '主键',
    sku_id            bigint        not null                    comment 'sku_id',
    attr_id           bigint        not null                    comment '属性id',
    attr_name         varchar(128)           default null       comment '销售属性名',
    attr_value        varchar(256)           default null       comment '销售属性值',
    attr_sort         int           not null default 0          comment '顺序',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_sku_id (sku_id),
    index idx_attr_id (attr_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='sku销售属性&值';

/*==============================================================*/
/* Table: pms_spu_comment  商品评价                             */
/*==============================================================*/
create table pms_spu_comment
(
    id                bigint        not null                    comment '主键',
    sku_id            bigint                 default null       comment 'sku_id',
    spu_id            bigint                 default null       comment 'spu_id',
    spu_name          varchar(255)           default null       comment '商品名字',
    member_nick_name  varchar(64)            default null       comment '会员昵称',
    star              tinyint       not null default 5          comment '星级[1-5]',
    member_ip         varchar(64)            default null       comment '会员ip',
    show_status       tinyint       not null default 1          comment '显示状态[0-不显示 1-显示]',
    spu_attributes    varchar(512)           default null       comment '购买时属性组合',
    likes_count       int           not null default 0          comment '点赞数',
    reply_count       int           not null default 0          comment '回复数',
    resources         varchar(1024)          default null       comment '评论图片/视频[json]',
    content           text                    default null      comment '内容',
    member_icon       varchar(512)           default null       comment '用户头像',
    comment_type      tinyint       not null default 0          comment '评论类型[0-对商品评论 1-对评论回复]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_spu_id (spu_id),
    index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品评价';

/*==============================================================*/
/* Table: pms_spu_images  spu图片                               */
/*==============================================================*/
create table pms_spu_images
(
    id                bigint        not null                    comment '主键',
    spu_id            bigint        not null                    comment 'spu_id',
    img_name          varchar(128)           default null       comment '图片名',
    img_url           varchar(1024)          default null       comment '图片地址',
    img_sort          int           not null default 0          comment '顺序',
    default_img       tinyint       not null default 0          comment '是否默认图[0-否 1-是]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='spu图片';

/*==============================================================*/
/* Table: pms_spu_info  spu信息                                 */
/*==============================================================*/
create table pms_spu_info
(
    id                bigint        not null                    comment '主键',
    spu_name          varchar(200)           default null       comment '商品名称',
    spu_description   varchar(1024)          default null       comment '商品描述',
    catalog_id        bigint                 default null       comment '所属分类id',
    brand_id          bigint                 default null       comment '品牌id',
    weight            decimal(18,4)          default null       comment '重量',
    publish_status    tinyint       not null default 0          comment '上架状态[0-下架 1-上架]',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    index idx_catalog_id (catalog_id),
    index idx_brand_id (brand_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='spu信息';

/*==============================================================*/
/* Table: pms_spu_info_desc  spu信息介绍（1:1 扩展表）          */
/*==============================================================*/
create table pms_spu_info_desc
(
    id                bigint        not null                    comment '主键',
    spu_id            bigint        not null                    comment '商品id',
    decript           text                    default null      comment '商品介绍',
    create_time       datetime      not null default current_timestamp comment '创建时间',
    update_time       datetime      not null default current_timestamp on update current_timestamp comment '更新时间',
    create_by         bigint                 default null       comment '创建人用户id',
    update_by         bigint                 default null       comment '更新人用户id',
    is_deleted        tinyint       not null default 0          comment '逻辑删除[0-正常 1-删除]',
    version           int           not null default 0          comment '乐观锁版本号',
    primary key (id),
    unique key uk_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='spu信息介绍';
