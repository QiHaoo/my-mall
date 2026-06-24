drop table if exists ums_growth_change_history;

drop table if exists ums_integration_change_history;

drop table if exists ums_member;

drop table if exists ums_member_collect_spu;

drop table if exists ums_member_collect_subject;

drop table if exists ums_member_level;

drop table if exists ums_member_login_log;

drop table if exists ums_member_receive_address;

drop table if exists ums_member_statistics_info;

/*==============================================================*/
/* Table: ums_growth_change_history                             */
/*==============================================================*/
create table ums_growth_change_history
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   change_count         int not null default 0 comment '改变的值（正负计数）',
   note                 varchar(255) default null comment '备注',
   source_type          tinyint not null default 0 comment '成长值来源[0-购物 1-管理员修改]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='成长值变化历史记录';

/*==============================================================*/
/* Table: ums_integration_change_history                        */
/*==============================================================*/
create table ums_integration_change_history
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   change_count         int not null default 0 comment '变化的值',
   note                 varchar(255) default null comment '备注',
   source_type          tinyint not null default 0 comment '积分来源[0-购物 1-管理员修改 2-活动]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='积分变化历史记录';

/*==============================================================*/
/* Table: ums_member                                            */
/*==============================================================*/
create table ums_member
(
   id                   bigint not null comment '主键',
   level_id             bigint default null comment '会员等级ID',
   username             varchar(64) not null comment '用户名',
   password             varchar(64) not null comment '密码（加密存储）',
   nickname             varchar(64) default null comment '昵称',
   mobile               varchar(20) default null comment '手机号码',
   email                varchar(64) default null comment '邮箱',
   header               varchar(500) default null comment '头像URL',
   gender               tinyint not null default 0 comment '性别[0-未知 1-男 2-女]',
   birth                date default null comment '生日',
   city                 varchar(500) default null comment '所在城市',
   job                  varchar(255) default null comment '职业',
   sign                 varchar(255) default null comment '个性签名',
   source_type          tinyint not null default 0 comment '用户来源[0-PC 1-移动 2-小程序 3-第三方]',
   integration          int not null default 0 comment '积分',
   growth               int not null default 0 comment '成长值',
   status               tinyint not null default 1 comment '启用状态[0-禁用 1-启用]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_username (username),
   unique key uk_mobile (mobile),
   index idx_level_id (level_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员';

/*==============================================================*/
/* Table: ums_member_collect_spu                                */
/*==============================================================*/
create table ums_member_collect_spu
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   spu_id               bigint default null comment 'SPU_ID',
   spu_name             varchar(500) default null comment 'SPU名称',
   spu_img              varchar(500) default null comment 'SPU图片URL',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id),
   index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员收藏的商品';

/*==============================================================*/
/* Table: ums_member_collect_subject                            */
/*==============================================================*/
create table ums_member_collect_subject
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   subject_id           bigint default null comment '专题ID',
   subject_name         varchar(255) default null comment '专题名称',
   subject_img          varchar(500) default null comment '专题图片URL',
   subject_url          varchar(500) default null comment '活动URL',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id),
   index idx_subject_id (subject_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员收藏的专题活动';

/*==============================================================*/
/* Table: ums_member_level                                      */
/*==============================================================*/
create table ums_member_level
(
   id                   bigint not null comment '主键',
   name                 varchar(100) not null comment '等级名称',
   growth_point         int not null default 0 comment '等级需要的成长值',
   default_status       tinyint not null default 0 comment '是否为默认等级[0-否 1-是]',
   free_freight_point   decimal(18,4) not null default 0 comment '免运费标准',
   comment_growth_point int not null default 0 comment '每次评价获取的成长值',
   priviledge_free_freight tinyint not null default 0 comment '是否有免邮特权[0-否 1-是]',
   priviledge_member_price tinyint not null default 0 comment '是否有会员价格特权[0-否 1-是]',
   priviledge_birthday  tinyint not null default 0 comment '是否有生日特权[0-否 1-是]',
   note                 varchar(255) default null comment '备注',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_name (name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员等级';

/*==============================================================*/
/* Table: ums_member_login_log                                  */
/*==============================================================*/
create table ums_member_login_log
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   ip                   varchar(64) default null comment '登录IP',
   city                 varchar(64) default null comment '登录城市',
   login_type           tinyint not null default 1 comment '登录类型[1-web 2-app 3-小程序]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员登录记录';

/*==============================================================*/
/* Table: ums_member_receive_address                            */
/*==============================================================*/
create table ums_member_receive_address
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   name                 varchar(255) not null comment '收货人姓名',
   phone                varchar(20) not null comment '电话',
   post_code            varchar(64) default null comment '邮政编码',
   province             varchar(100) default null comment '省份/直辖市',
   city                 varchar(100) default null comment '城市',
   region               varchar(100) default null comment '区',
   detail_address       varchar(255) not null comment '详细地址（街道）',
   areacode             varchar(15) default null comment '省市区代码',
   default_status       tinyint not null default 0 comment '是否默认地址[0-否 1-是]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员收货地址';

/*==============================================================*/
/* Table: ums_member_statistics_info                            */
/*==============================================================*/
create table ums_member_statistics_info
(
   id                   bigint not null comment '主键',
   member_id            bigint default null comment '会员ID',
   consume_amount       decimal(18,4) not null default 0 comment '累计消费金额',
   coupon_amount        decimal(18,4) not null default 0 comment '累计优惠金额',
   order_count          int not null default 0 comment '订单数量',
   coupon_count         int not null default 0 comment '优惠券数量',
   comment_count        int not null default 0 comment '评价数',
   return_order_count   int not null default 0 comment '退货数量',
   login_count          int not null default 0 comment '登录次数',
   attend_count         int not null default 0 comment '关注数量',
   fans_count           int not null default 0 comment '粉丝数量',
   collect_product_count int not null default 0 comment '收藏的商品数量',
   collect_subject_count int not null default 0 comment '收藏的专题活动数量',
   collect_comment_count int not null default 0 comment '收藏的评论数量',
   invite_friend_count  int not null default 0 comment '邀请的朋友数量',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_member_id (member_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='会员统计信息';
