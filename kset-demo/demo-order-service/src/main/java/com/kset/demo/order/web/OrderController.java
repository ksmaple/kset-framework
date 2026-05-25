package com.kset.demo.order.web;

import com.kset.boot.web.response.ApiResponse;
import com.kset.core.annotation.OpLog;
import com.kset.demo.api.UserQueryService;
import com.kset.demo.order.entity.OrderEntity;
import com.kset.demo.order.mapper.OrderMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderMapper orderMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @DubboReference
    private UserQueryService userQueryService;

    public OrderController(OrderMapper orderMapper, RedisTemplate<String, Object> redisTemplate) {
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> get(@PathVariable Long id) {
        String cacheKey = "order:" + id;
        OrderView cached = (OrderView) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            return ApiResponse.fail("order not found");
        }
        String userName = userQueryService.getUserName(order.getUserId());
        OrderView view = new OrderView(order.getId(), order.getProductName(), order.getUserId(), userName);
        redisTemplate.opsForValue().set(cacheKey, view, Duration.ofMinutes(10));
        return ApiResponse.success(view);
    }

    @PostMapping
    @OpLog(type = "CREATE", target = "order", recordParams = true)
    public ApiResponse<OrderEntity> create(@RequestBody OrderEntity order) {
        orderMapper.insert(order);
        return ApiResponse.success(order);
    }

    public record OrderView(Long id, String productName, Long userId, String userName) {
    }
}
