package com.kset.demo.standalone.web;

import com.kset.common.annotation.OpLog;
import com.kset.demo.standalone.entity.UserEntity;
import com.kset.demo.standalone.mapper.UserMapper;
import com.kset.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "用户", description = "单机示例 API")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserController(UserMapper userMapper, RedisTemplate<String, Object> redisTemplate) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    @Operation(summary = "按 ID 查询用户（Redis 缓存）")
    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) {
        String cacheKey = "standalone:user:" + id;
        UserEntity cached = (UserEntity) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        UserEntity user = userMapper.selectById(id);
        if (user != null) {
            redisTemplate.opsForValue().set(cacheKey, user, Duration.ofMinutes(5));
        }
        return ApiResponse.success(user);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @OpLog(type = "CREATE", target = "user", recordParams = true)
    public ApiResponse<UserEntity> create(@RequestBody UserEntity user) {
        userMapper.insert(user);
        return ApiResponse.success(user);
    }
}
