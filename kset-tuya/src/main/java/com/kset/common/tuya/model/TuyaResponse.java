package com.kset.common.tuya.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 涂鸦API通用响应
 *
 * @author kset
 */
@Data
public class TuyaResponse<T> {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 时间戳
     */
    private Long t;

    /**
     * 错误码
     */
    private String code;

    /**
     * 错误消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T result;

    /**
     * 请求ID
     */
    @JSONField(name = "tid")
    private String tid;
}
