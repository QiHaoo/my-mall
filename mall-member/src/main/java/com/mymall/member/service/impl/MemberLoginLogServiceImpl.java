package com.mymall.member.service.impl;

import com.mymall.member.entity.MemberLoginLog;
import com.mymall.member.mapper.MemberLoginLogMapper;
import com.mymall.member.service.IMemberLoginLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 会员登录记录 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class MemberLoginLogServiceImpl extends ServiceImpl<MemberLoginLogMapper, MemberLoginLog> implements IMemberLoginLogService {

}
