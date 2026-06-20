package com.mymall.order.service.impl;

import com.mymall.order.entity.OrderSetting;
import com.mymall.order.mapper.OrderSettingMapper;
import com.mymall.order.service.IOrderSettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单配置信息 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class OrderSettingServiceImpl extends ServiceImpl<OrderSettingMapper, OrderSetting> implements IOrderSettingService {

}
