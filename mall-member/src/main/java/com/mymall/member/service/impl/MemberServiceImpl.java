package com.mymall.member.service.impl;

import com.mymall.member.entity.Member;
import com.mymall.member.mapper.MemberMapper;
import com.mymall.member.service.IMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 会员 服务实现类
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements IMemberService {

}
