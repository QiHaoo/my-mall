package com.mymall.order.service.impl;

import com.mymall.order.entity.OrderOperateHistory;
import com.mymall.order.mapper.OrderOperateHistoryMapper;
import com.mymall.order.service.IOrderOperateHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单操作历史记录 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class OrderOperateHistoryServiceImpl extends ServiceImpl<OrderOperateHistoryMapper, OrderOperateHistory> implements IOrderOperateHistoryService {

}
