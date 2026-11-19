package com.kset.common.tuya.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 涂鸦API访问令牌
 *
 * @author kset
 */
@Data
public class TuyaToken {

    /**
     * 访问令牌
     */
    @JSONField(name = "access_token")
    private String accessToken;

    /**
     * 刷新令牌
     */
    @JSONField(name = "refresh_token")
    private String refreshToken;

    /**
     * 令牌有效期（秒）
     */
    @JSONField(name = "expire_time")
    private Long expireTime;

    /**
     * 用户ID
     */
    private String uid;

    /**
     * Token获取时的系统时间（毫秒），用于判断过期
     */
    @JSONField(serialize = false, deserialize = false)
    private long obtainedAt;

    /**
     * 判断Token是否已过期（提前60秒刷新）
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return true;
        }
        return System.currentTimeMillis() - obtainedAt >= (expireTime - 60) * 1000;
    }
}
