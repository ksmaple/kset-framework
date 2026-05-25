package com.kset.demo.user.service;

import com.kset.demo.api.UserQueryService;
import com.kset.demo.user.entity.UserEntity;
import com.kset.demo.user.mapper.UserMapper;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class UserQueryServiceImpl implements UserQueryService {

    private final UserMapper userMapper;

    public UserQueryServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String getUserName(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user != null ? user.getName() : "unknown";
    }
}
