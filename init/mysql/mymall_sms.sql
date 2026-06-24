drop table if exists sms_coupon;

drop table if exists sms_coupon_history;

drop table if exists sms_coupon_spu_category_relation;

drop table if exists sms_coupon_spu_relation;

drop table if exists sms_home_adv;

drop table if exists sms_home_subject;

drop table if exists sms_home_subject_spu;

drop table if exists sms_member_price;

drop table if exists sms_seckill_promotion;

drop table if exists sms_seckill_session;

drop table if exists sms_seckill_sku_notice;

drop table if exists sms_seckill_sku_relation;

drop table if exists sms_sku_full_reduction;

drop table if exists sms_sku_ladder;

drop table if exists sms_spu_bounds;

/*==============================================================*/
/* Table: sms_coupon                                            */
/*==============================================================*/
create table sms_coupon
(
   id                   bigint not null comment '主键',
   coupon_type          tinyint not null default 0 comment '优惠券类型[0-全场赠券 1-会员赠券 2-购物赠券 3-注册赠券]',
   coupon_img           varchar(512) default null comment '优惠券图片URL',
   coupon_name          varchar(100) not null default '' comment '优惠券名字',
   num                  int not null default 0 comment '数量',
   amount               decimal(18,4) not null default 0 comment '金额',
   per_limit           int not null default 1 comment '每人限领张数',
   min_point            decimal(18,4) not null default 0 comment '使用门槛',
   start_time           datetime default null comment '开始时间',
   end_time             datetime default null comment '结束时间',
   use_type             tinyint not null default 0 comment '使用类型[0-全场通用 1-指定分类 2-指定商品]',
   note                 varchar(200) default null comment '备注',
   publish_count        int not null default 0 comment '发行数量',
   use_count            int not null default 0 comment '已使用数量',
   receive_count        int not null default 0 comment '领取数量',
   enable_start_time    datetime default null comment '可以领取的开始日期',
   enable_end_time      datetime default null comment '可以领取的结束日期',
   code                 varchar(64) default null comment '优惠码',
   member_level         tinyint not null default 0 comment '可以领取的会员等级[0-不限等级 其他-对应等级]',
   publish              tinyint not null default 0 comment '发布状态[0-未发布 1-已发布]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_code (code)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='优惠券信息';

/*==============================================================*/
/* Table: sms_coupon_history                                    */
/*==============================================================*/
create table sms_coupon_history
(
   id                   bigint not null comment '主键',
   coupon_id            bigint not null comment '优惠券id',
   member_id            bigint not null comment '会员id',
   member_nick_name     varchar(64) default null comment '会员名字',
   get_type             tinyint not null default 0 comment '获取方式[0-后台赠送 1-主动领取]',
   get_time             datetime default null comment '领取时间',
   use_type             tinyint not null default 0 comment '使用状态[0-未使用 1-已使用 2-已过期]',
   use_time             datetime default null comment '使用时间',
   order_id             bigint default null comment '订单id',
   order_sn             varchar(64) default null comment '订单号',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_coupon_id (coupon_id),
   index idx_member_id (member_id),
   index idx_order_sn (order_sn)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='优惠券领取历史记录';

/*==============================================================*/
/* Table: sms_coupon_spu_category_relation                      */
/*==============================================================*/
create table sms_coupon_spu_category_relation
(
   id                   bigint not null comment '主键',
   coupon_id            bigint not null comment '优惠券id',
   category_id          bigint not null comment '产品分类id',
   category_name        varchar(64) not null default '' comment '产品分类名称',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_coupon_id (coupon_id),
   index idx_category_id (category_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='优惠券分类关联';

/*==============================================================*/
/* Table: sms_coupon_spu_relation                               */
/*==============================================================*/
create table sms_coupon_spu_relation
(
   id                   bigint not null comment '主键',
   coupon_id            bigint not null comment '优惠券id',
   spu_id               bigint not null comment 'spu_id',
   spu_name             varchar(255) not null default '' comment 'spu_name',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_coupon_id (coupon_id),
   index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='优惠券与产品关联';

/*==============================================================*/
/* Table: sms_home_adv                                          */
/*==============================================================*/
create table sms_home_adv
(
   id                   bigint not null comment '主键',
   name                 varchar(100) not null default '' comment '名字',
   pic                  varchar(500) default null comment '图片地址',
   start_time           datetime default null comment '开始时间',
   end_time             datetime default null comment '结束时间',
   status               tinyint not null default 0 comment '状态[0-禁用 1-启用]',
   click_count          int not null default 0 comment '点击数',
   url                  varchar(500) default null comment '广告详情连接地址',
   note                 varchar(500) default null comment '备注',
   sort                 int not null default 0 comment '排序',
   publisher_id         bigint default null comment '发布者',
   auth_id              bigint default null comment '审核者',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='首页轮播广告';

/*==============================================================*/
/* Table: sms_home_subject                                      */
/*==============================================================*/
create table sms_home_subject
(
   id                   bigint not null comment '主键',
   name                 varchar(200) not null default '' comment '专题名字',
   title                varchar(255) not null default '' comment '专题标题',
   sub_title            varchar(255) default null comment '专题副标题',
   status               tinyint not null default 1 comment '显示状态[0-不显示 1-显示]',
   url                  varchar(500) default null comment '详情连接',
   sort                 int not null default 0 comment '排序',
   img                  varchar(500) default null comment '专题图片地址',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='首页专题表【jd首页下面很多专题，每个专题链接新的页面，展示专题商品信息】';

/*==============================================================*/
/* Table: sms_home_subject_spu                                  */
/*==============================================================*/
create table sms_home_subject_spu
(
   id                   bigint not null comment '主键',
   name                 varchar(200) not null default '' comment '专题名字',
   subject_id           bigint not null comment '专题id',
   spu_id               bigint not null comment 'spu_id',
   sort                 int not null default 0 comment '排序',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_subject_id (subject_id),
   index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='专题商品';

/*==============================================================*/
/* Table: sms_member_price                                      */
/*==============================================================*/
create table sms_member_price
(
   id                   bigint not null comment '主键',
   sku_id               bigint not null comment 'sku_id',
   member_level_id      bigint not null comment '会员等级id',
   member_level_name    varchar(100) not null default '' comment '会员等级名',
   member_price         decimal(18,4) not null default 0 comment '会员对应价格',
   add_other            tinyint not null default 0 comment '可否叠加其他优惠[0-不可叠加 1-可叠加]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_sku_id (sku_id),
   index idx_member_level_id (member_level_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品会员价格';

/*==============================================================*/
/* Table: sms_seckill_promotion                                 */
/*==============================================================*/
create table sms_seckill_promotion
(
   id                   bigint not null comment '主键',
   title                varchar(255) not null default '' comment '活动标题',
   start_time           datetime default null comment '开始日期',
   end_time             datetime default null comment '结束日期',
   status               tinyint not null default 0 comment '上下线状态[0-下线 1-上线]',
   user_id              bigint default null comment '创建人',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='秒杀活动';

/*==============================================================*/
/* Table: sms_seckill_session                                   */
/*==============================================================*/
create table sms_seckill_session
(
   id                   bigint not null comment '主键',
   name                 varchar(200) not null default '' comment '场次名称',
   start_time           datetime default null comment '每日开始时间',
   end_time             datetime default null comment '每日结束时间',
   status               tinyint not null default 1 comment '启用状态[0-禁用 1-启用]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='秒杀活动场次';

/*==============================================================*/
/* Table: sms_seckill_sku_notice                                */
/*==============================================================*/
create table sms_seckill_sku_notice
(
   id                   bigint not null comment '主键',
   member_id            bigint not null comment '会员id',
   sku_id               bigint not null comment 'sku_id',
   session_id           bigint not null comment '活动场次id',
   subcribe_time        datetime default null comment '订阅时间',
   send_time            datetime default null comment '发送时间',
   notice_type          tinyint not null default 0 comment '通知方式[0-短信 1-邮件]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_id (member_id),
   index idx_sku_id (sku_id),
   index idx_session_id (session_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='秒杀商品通知订阅';

/*==============================================================*/
/* Table: sms_seckill_sku_relation                              */
/*==============================================================*/
create table sms_seckill_sku_relation
(
   id                   bigint not null comment '主键',
   promotion_id         bigint not null comment '活动id',
   promotion_session_id bigint not null comment '活动场次id',
   sku_id               bigint not null comment '商品id',
   seckill_price        decimal(18,4) not null default 0 comment '秒杀价格',
   seckill_count        int not null default 0 comment '秒杀总量',
   seckill_limit        int not null default 1 comment '每人限购数量',
   seckill_sort         int not null default 0 comment '排序',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_promotion_id (promotion_id),
   index idx_session_id (promotion_session_id),
   index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='秒杀活动商品关联';

/*==============================================================*/
/* Table: sms_sku_full_reduction                                */
/*==============================================================*/
create table sms_sku_full_reduction
(
   id                   bigint not null comment '主键',
   sku_id               bigint not null comment 'sku_id',
   full_price           decimal(18,4) not null default 0 comment '满多少',
   reduce_price         decimal(18,4) not null default 0 comment '减多少',
   add_other            tinyint not null default 0 comment '是否参与其他优惠[0-不可叠加 1-可叠加]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品满减信息';

/*==============================================================*/
/* Table: sms_sku_ladder                                        */
/*==============================================================*/
create table sms_sku_ladder
(
   id                   bigint not null comment '主键',
   sku_id               bigint not null comment 'sku_id',
   full_count           int not null default 0 comment '满几件',
   discount             decimal(4,2) not null default 1.00 comment '打几折',
   price                decimal(18,4) not null default 0 comment '折后价',
   add_other            tinyint not null default 0 comment '是否叠加其他优惠[0-不可叠加 1-可叠加]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品阶梯价格';

/*==============================================================*/
/* Table: sms_spu_bounds                                        */
/*==============================================================*/
create table sms_spu_bounds
(
   id                   bigint not null comment '主键',
   spu_id               bigint not null comment 'spu_id',
   grow_bounds          decimal(18,4) not null default 0 comment '成长积分',
   buy_bounds           decimal(18,4) not null default 0 comment '购物积分',
   work                 tinyint not null default 0 comment '优惠生效情况[0-无优惠不赠送 1-无优惠成长积分赠送 2-有优惠成长积分赠送 3-有优惠购物积分赠送]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_spu_id (spu_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品spu积分设置';
