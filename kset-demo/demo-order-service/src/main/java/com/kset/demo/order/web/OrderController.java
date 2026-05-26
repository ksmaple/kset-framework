package com.kset.demo.order.web;

import com.kset.common.annotation.OpLog;
import com.kset.demo.api.UserQueryService;
import com.kset.demo.order.entity.OrderEntity;
import com.kset.demo.order.mapper.OrderMapper;
import com.kset.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "订单", description = "订单查询与创建（含 Dubbo、Redis 缓存）")
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

    @Operation(summary = "按 ID 查询订单（含用户名）")
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

    @Operation(summary = "创建订单")
    @PostMapping
    @OpLog(type = "CREATE", target = "order", recordParams = true)
    public ApiResponse<OrderEntity> create(@RequestBody OrderEntity order) {
        orderMapper.insert(order);
        return ApiResponse.success(order);
    }

    @Schema(description = "订单视图（含 Dubbo 查询的用户名）")
    public record OrderView(
            @Schema(description = "订单 ID") Long id,
            @Schema(description = "商品名称") String productName,
            @Schema(description = "用户 ID") Long userId,
            @Schema(description = "用户名称") String userName) {
    }
}
