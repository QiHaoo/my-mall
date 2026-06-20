package com.mymall.order.service.impl;

import com.mymall.order.entity.PaymentInfo;
import com.mymall.order.mapper.PaymentInfoMapper;
import com.mymall.order.service.IPaymentInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 支付信息表 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements IPaymentInfoService {

}
