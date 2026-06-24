drop table if exists oms_order;

drop table if exists oms_order_item;

drop table if exists oms_order_operate_history;

drop table if exists oms_order_return_apply;

drop table if exists oms_order_return_reason;

drop table if exists oms_order_setting;

drop table if exists oms_payment_info;

drop table if exists oms_refund_info;

/*==============================================================*/
/* Table: oms_order                                             */
/*==============================================================*/
create table oms_order
(
   id                      bigint not null comment '主键',
   member_id               bigint default null comment '会员ID',
   order_sn                varchar(64) not null comment '订单号（雪花ID字符串）',
   coupon_id               bigint default null comment '使用的优惠券ID',
   member_username         varchar(200) default null comment '用户名',
   total_amount            decimal(18,4) not null default 0 comment '订单总额',
   pay_amount              decimal(18,4) not null default 0 comment '应付总额',
   freight_amount          decimal(18,4) not null default 0 comment '运费金额',
   promotion_amount        decimal(18,4) not null default 0 comment '促销优化金额（促销价、满减、阶梯价）',
   integration_amount      decimal(18,4) not null default 0 comment '积分抵扣金额',
   coupon_amount           decimal(18,4) not null default 0 comment '优惠券抵扣金额',
   discount_amount         decimal(18,4) not null default 0 comment '后台调整订单使用的折扣金额',
   pay_type                tinyint not null default 0 comment '支付方式[1-支付宝 2-微信 3-银联 4-货到付款]',
   source_type             tinyint not null default 0 comment '订单来源[0-PC订单 1-app订单]',
   status                  tinyint not null default 0 comment '订单状态[0-待付款 1-待发货 2-已发货 3-已完成 4-已关闭 5-无效订单]',
   delivery_company        varchar(64) default null comment '物流公司(配送方式)',
   delivery_sn             varchar(64) default null comment '物流单号',
   auto_confirm_day        int not null default 0 comment '自动确认时间（天）',
   integration             int not null default 0 comment '可以获得的积分',
   growth                  int not null default 0 comment '可以获得的成长值',
   bill_type               tinyint not null default 0 comment '发票类型[0-不开发票 1-电子发票 2-纸质发票]',
   bill_header             varchar(255) default null comment '发票抬头',
   bill_content            varchar(255) default null comment '发票内容',
   bill_receiver_phone     varchar(20) default null comment '收票人电话',
   bill_receiver_email     varchar(64) default null comment '收票人邮箱',
   receiver_name           varchar(100) default null comment '收货人姓名',
   receiver_phone          varchar(20) default null comment '收货人电话',
   receiver_post_code      varchar(20) default null comment '收货人邮编',
   receiver_province       varchar(32) default null comment '省份/直辖市',
   receiver_city           varchar(32) default null comment '城市',
   receiver_region         varchar(32) default null comment '区',
   receiver_detail_address varchar(200) default null comment '详细地址',
   note                    varchar(500) default null comment '订单备注',
   confirm_status          tinyint not null default 0 comment '确认收货状态[0-未确认 1-已确认]',
   delete_status           tinyint not null default 0 comment '删除状态[0-未删除 1-已删除]',
   use_integration         int not null default 0 comment '下单时使用的积分',
   payment_time            datetime default null comment '支付时间',
   delivery_time           datetime default null comment '发货时间',
   receive_time            datetime default null comment '确认收货时间',
   comment_time            datetime default null comment '评价时间',
   modify_time             datetime default null comment '修改时间',
   create_time             datetime not null default current_timestamp comment '创建时间',
   update_time             datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by               bigint default null comment '创建人用户ID',
   update_by               bigint default null comment '更新人用户ID',
   is_deleted              tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version                 int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_order_sn (order_sn),
   index idx_member_id (member_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单';

/*==============================================================*/
/* Table: oms_order_item                                        */
/*==============================================================*/
create table oms_order_item
(
   id                   bigint not null comment '主键',
   order_id             bigint not null comment '订单ID',
   order_sn             varchar(64) not null comment '订单号',
   spu_id               bigint default null comment 'SPU ID',
   spu_name             varchar(255) default null comment 'SPU 名称',
   spu_pic              varchar(500) default null comment 'SPU 图片',
   spu_brand            varchar(200) default null comment '品牌',
   category_id          bigint default null comment '商品分类ID',
   sku_id               bigint default null comment '商品SKU编号',
   sku_name             varchar(255) default null comment '商品SKU名称',
   sku_pic              varchar(500) default null comment '商品SKU图片',
   sku_price            decimal(18,4) not null default 0 comment '商品SKU价格',
   sku_quantity         int not null default 1 comment '商品购买数量',
   sku_attrs_vals       varchar(500) default null comment '商品销售属性组合（JSON）',
   promotion_amount     decimal(18,4) not null default 0 comment '商品促销分解金额',
   coupon_amount        decimal(18,4) not null default 0 comment '优惠券优惠分解金额',
   integration_amount   decimal(18,4) not null default 0 comment '积分优惠分解金额',
   real_amount          decimal(18,4) not null default 0 comment '该商品经过优惠后的分解金额',
   gift_integration     int not null default 0 comment '赠送积分',
   gift_growth          int not null default 0 comment '赠送成长值',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_order_id (order_id),
   index idx_sku_id (sku_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单项信息';

/*==============================================================*/
/* Table: oms_order_operate_history                             */
/*==============================================================*/
create table oms_order_operate_history
(
   id                   bigint not null comment '主键',
   order_id             bigint not null comment '订单ID',
   operate_man          varchar(100) default null comment '操作人[用户/系统/后台管理员]',
   order_status         tinyint not null default 0 comment '订单状态[0-待付款 1-待发货 2-已发货 3-已完成 4-已关闭 5-无效订单]',
   note                 varchar(500) default null comment '备注',
   create_time          datetime not null default current_timestamp comment '创建时间（操作时间）',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单操作历史记录';

/*==============================================================*/
/* Table: oms_order_return_apply                                */
/*==============================================================*/
create table oms_order_return_apply
(
   id                   bigint not null comment '主键',
   order_id             bigint not null comment '订单ID',
   sku_id               bigint default null comment '退货商品ID',
   order_sn             varchar(64) not null comment '订单编号',
   member_username      varchar(64) default null comment '会员用户名',
   return_amount        decimal(18,4) not null default 0 comment '退款金额',
   return_name          varchar(100) default null comment '退货人姓名',
   return_phone         varchar(20) default null comment '退货人电话',
   status               tinyint not null default 0 comment '申请状态[0-待处理 1-退货中 2-已完成 3-已拒绝]',
   handle_time          datetime default null comment '处理时间',
   sku_img              varchar(500) default null comment '商品图片',
   sku_name             varchar(200) default null comment '商品名称',
   sku_brand            varchar(200) default null comment '商品品牌',
   sku_attrs_vals       varchar(500) default null comment '商品销售属性(JSON)',
   sku_count            int not null default 1 comment '退货数量',
   sku_price            decimal(18,4) not null default 0 comment '商品单价',
   sku_real_price       decimal(18,4) not null default 0 comment '商品实际支付单价',
   reason               varchar(200) default null comment '退货原因',
   description          varchar(500) default null comment '描述',
   desc_pics            varchar(2000) default null comment '凭证图片，以逗号隔开',
   handle_note          varchar(500) default null comment '处理备注',
   handle_man           varchar(200) default null comment '处理人员',
   receive_man          varchar(100) default null comment '收货人',
   receive_time         datetime default null comment '收货时间',
   receive_note         varchar(500) default null comment '收货备注',
   receive_phone        varchar(20) default null comment '收货电话',
   company_address      varchar(500) default null comment '公司收货地址',
   create_time          datetime not null default current_timestamp comment '创建时间（申请时间）',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_order_id (order_id),
   index idx_sku_id (sku_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单退货申请';

/*==============================================================*/
/* Table: oms_order_return_reason                               */
/*==============================================================*/
create table oms_order_return_reason
(
   id                   bigint not null comment '主键',
   name                 varchar(200) not null comment '退货原因名',
   sort                 int not null default 0 comment '排序',
   status               tinyint not null default 1 comment '启用状态[0-禁用 1-启用]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_name (name)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='退货原因';

/*==============================================================*/
/* Table: oms_order_setting                                     */
/*==============================================================*/
create table oms_order_setting
(
   id                      bigint not null comment '主键',
   flash_order_overtime    int not null default 0 comment '秒杀订单超时关闭时间(分)',
   normal_order_overtime   int not null default 0 comment '正常订单超时时间(分)',
   confirm_overtime        int not null default 0 comment '发货后自动确认收货时间（天）',
   finish_overtime         int not null default 0 comment '自动完成交易时间，不能申请退货（天）',
   comment_overtime        int not null default 0 comment '订单完成后自动好评时间（天）',
   member_level            tinyint not null default 0 comment '会员等级[0-不限会员等级，全部通用；其他-对应的会员等级]',
   create_time             datetime not null default current_timestamp comment '创建时间',
   update_time             datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by               bigint default null comment '创建人用户ID',
   update_by               bigint default null comment '更新人用户ID',
   is_deleted              tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version                 int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_member_level (member_level)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单配置信息';

/*==============================================================*/
/* Table: oms_payment_info                                      */
/*==============================================================*/
create table oms_payment_info
(
   id                   bigint not null comment '主键',
   order_sn             varchar(64) not null comment '订单号（对外业务号）',
   order_id             bigint not null comment '订单ID',
   alipay_trade_no      varchar(50) default null comment '支付宝交易流水号',
   total_amount         decimal(18,4) not null default 0 comment '支付总金额',
   subject              varchar(200) default null comment '交易内容',
   payment_status       tinyint not null default 0 comment '支付状态[0-待支付 1-支付中 2-支付成功 3-支付失败 4-已关闭]',
   confirm_time         datetime default null comment '确认时间',
   callback_content     varchar(4000) default null comment '回调内容',
   callback_time        datetime default null comment '回调时间',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_order_sn (order_sn),
   index idx_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='支付信息表';

/*==============================================================*/
/* Table: oms_refund_info                                       */
/*==============================================================*/
create table oms_refund_info
(
   id                   bigint not null comment '主键',
   order_return_id      bigint not null comment '退货申请ID',
   refund               decimal(18,4) not null default 0 comment '退款金额',
   refund_sn            varchar(64) default null comment '退款交易流水号',
   refund_status        tinyint not null default 0 comment '退款状态[0-待退款 1-退款中 2-退款成功 3-退款失败]',
   refund_channel       tinyint not null default 0 comment '退款渠道[1-支付宝 2-微信 3-银联 4-汇款]',
   refund_content       varchar(5000) default null comment '退款回调内容',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_refund_sn (refund_sn),
   index idx_order_return_id (order_return_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='退款信息';
