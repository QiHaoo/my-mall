drop table if exists wms_purchase;

drop table if exists wms_purchase_detail;

drop table if exists wms_ware_info;

drop table if exists wms_ware_order_task;

drop table if exists wms_ware_order_task_detail;

drop table if exists wms_ware_sku;

/*==============================================================*/
/* Table: wms_purchase                                          */
/*==============================================================*/
create table wms_purchase
(
   id                   bigint not null comment '主键',
   assignee_id          bigint default null comment '采购人用户ID',
   assignee_name        varchar(64) default null comment '采购人姓名',
   phone                varchar(20) default null comment '采购人联系方式',
   priority             tinyint not null default 1 comment '优先级[0-低 1-中 2-高]',
   status               tinyint not null default 0 comment '状态[0-新建 1-已分配 2-正在采购 3-已完成 4-采购失败]',
   ware_id              bigint default null comment '仓库ID',
   amount               decimal(18,4) not null default 0.0000 comment '采购总金额',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_assignee_id (assignee_id),
   index idx_ware_id (ware_id),
   index idx_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='采购信息';

/*==============================================================*/
/* Table: wms_purchase_detail                                   */
/*==============================================================*/
create table wms_purchase_detail
(
   id                   bigint not null comment '主键',
   purchase_id          bigint not null comment '采购单ID',
   sku_id               bigint not null comment '采购商品SKU ID',
   sku_num              int not null default 0 comment '采购数量',
   sku_price            decimal(18,4) not null default 0.0000 comment '采购金额',
   ware_id              bigint default null comment '仓库ID',
   status               tinyint not null default 0 comment '状态[0-新建 1-已分配 2-正在采购 3-已完成 4-采购失败]',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_purchase_id (purchase_id),
   index idx_sku_id (sku_id),
   index idx_ware_id (ware_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='采购单明细';

/*==============================================================*/
/* Table: wms_ware_info                                         */
/*==============================================================*/
create table wms_ware_info
(
   id                   bigint not null comment '主键',
   name                 varchar(64) not null comment '仓库名称',
   address              varchar(255) default null comment '仓库地址',
   areacode             varchar(20) default null comment '区域编码',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_name (name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='仓库信息';

/*==============================================================*/
/* Table: wms_ware_order_task                                   */
/*==============================================================*/
create table wms_ware_order_task
(
   id                   bigint not null comment '主键',
   order_id             bigint default null comment '订单ID',
   order_sn             varchar(64) default null comment '订单号',
   consignee            varchar(100) default null comment '收货人',
   consignee_tel        varchar(20) default null comment '收货人电话',
   delivery_address     varchar(500) default null comment '配送地址',
   order_comment        varchar(200) default null comment '订单备注',
   payment_way          tinyint not null default 1 comment '付款方式[1-在线付款 2-货到付款]',
   task_status          tinyint not null default 0 comment '任务状态[0-待处理 1-已分配 2-正在出库 3-已发货 4-已完成 5-已取消]',
   order_body           varchar(255) default null comment '订单描述',
   tracking_no          varchar(30) default null comment '物流单号',
   ware_id              bigint default null comment '仓库ID',
   task_comment         varchar(500) default null comment '工作单备注',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_order_id (order_id),
   index idx_order_sn (order_sn),
   index idx_ware_id (ware_id),
   index idx_task_status (task_status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='库存工作单';

/*==============================================================*/
/* Table: wms_ware_order_task_detail                            */
/*==============================================================*/
create table wms_ware_order_task_detail
(
   id                   bigint not null comment '主键',
   sku_id               bigint not null comment 'SKU ID',
   sku_name             varchar(200) default null comment 'SKU名称',
   sku_num              int not null default 0 comment '购买数量',
   task_id              bigint not null comment '工作单ID',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   index idx_task_id (task_id),
   index idx_sku_id (sku_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='库存工作单明细';

/*==============================================================*/
/* Table: wms_ware_sku                                          */
/*==============================================================*/
create table wms_ware_sku
(
   id                   bigint not null comment '主键',
   sku_id               bigint not null comment 'SKU ID',
   ware_id              bigint not null comment '仓库ID',
   stock                int not null default 0 comment '库存数',
   sku_name             varchar(200) default null comment 'SKU名称',
   stock_locked         int not null default 0 comment '锁定库存',
   create_time          datetime not null default current_timestamp comment '创建时间',
   update_time          datetime not null default current_timestamp on update current_timestamp comment '更新时间',
   create_by            bigint default null comment '创建人用户ID',
   update_by            bigint default null comment '更新人用户ID',
   is_deleted           tinyint not null default 0 comment '逻辑删除[0-正常 1-删除]',
   version              int not null default 0 comment '乐观锁版本号',
   primary key (id),
   unique key uk_sku_ware (sku_id, ware_id),
   index idx_ware_id (ware_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商品库存';
