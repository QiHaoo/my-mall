package com.mymall.product.service.impl;

import com.mymall.product.entity.Brand;
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.service.IBrandService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 品牌 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

}
