package com.kset.demo.user.web;

import com.kset.boot.web.response.ApiResponse;
import com.kset.core.annotation.OpLog;
import com.kset.demo.user.entity.UserEntity;
import com.kset.demo.user.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) {
        return ApiResponse.success(userMapper.selectById(id));
    }

    @PostMapping
    @OpLog(type = "CREATE", target = "user", recordParams = true)
    public ApiResponse<UserEntity> create(@RequestBody UserEntity user) {
        userMapper.insert(user);
        return ApiResponse.success(user);
    }
}
