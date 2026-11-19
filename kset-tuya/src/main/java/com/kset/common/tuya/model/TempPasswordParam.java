package com.kset.common.tuya.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 创建门锁临时密码参数
 *
 * @author kset
 */
@Data
@Builder
public class TempPasswordParam {

    /**
     * 密码名称
     */
    private String name;

    /**
     * 密码明文
     * Wi-Fi 锁: 7位
     * Zigbee 锁/蓝牙锁: 6位
     */
    private String password;

    /**
     * 生效时间（10位时间戳，秒）
     */
    private Long effectiveTime;

    /**
     * 过期时间（10位时间戳，秒）
     */
    private Long invalidTime;

    /**
     * 手机号码（可选）
     */
    private String phone;

    /**
     * 密码有效类型（可选）
     * 1: 一次性有效
     * 0: 有效时间范围内一直有效
     */
    private Integer type;

    /**
     * 时区，周期性功能需填入（可选）
     */
    private String timeZone;

    /**
     * 周期性信息（可选）
     */
    private List<ScheduleItem> scheduleList;

    /**
     * 关联设备列表（仅蓝牙门锁配件需要，可选）
     */
    private List<String> relateDevList;
}
