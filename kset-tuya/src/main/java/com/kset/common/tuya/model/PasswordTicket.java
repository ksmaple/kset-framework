package com.kset.common.tuya.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 门锁临时密钥ticket
 *
 * @author kset
 */
@Data
public class PasswordTicket {

    /**
     * 临时密钥ID
     */
    @JSONField(name = "ticket_id")
    private String ticketId;

    /**
     * 临时密钥Key（已加密，需用开发者accessKey通过AES解密后使用）
     */
    @JSONField(name = "ticket_key")
    private String ticketKey;

    /**
     * 剩余有效时间（秒）
     */
    @JSONField(name = "expire_time")
    private Long expireTime;
}
