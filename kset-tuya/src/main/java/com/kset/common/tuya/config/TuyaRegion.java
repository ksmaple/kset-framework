package com.kset.common.tuya.config;

/**
 * 涂鸦云数据中心区域
 * <p>
 * 请根据设备所在区域选择接入地址，缩短调试响应时长。
 *
 * @author kset
 */
public enum TuyaRegion {

    /** 中国数据中心 */
    CN("https://openapi.tuyacn.com"),
    /** 美西数据中心 */
    US_WEST("https://openapi.tuyaus.com"),
    /** 美东数据中心 */
    US_EAST("https://openapi-ueaz.tuyaus.com"),
    /** 中欧数据中心 */
    EU_CENTRAL("https://openapi.tuyaeu.com"),
    /** 西欧数据中心 */
    EU_WEST("https://openapi-weaz.tuyaeu.com"),
    /** 印度数据中心 */
    IN("https://openapi.tuyain.com"),
    /** 新加坡数据中心 */
    SG("https://openapi-sg.iotbing.com");

    private final String baseUrl;

    TuyaRegion(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
