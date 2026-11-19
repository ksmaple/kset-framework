package com.kset.common.constant;

/**
 * 全局常量定义
 *
 * @author kset
 */
public final class GlobalConstants {

    private GlobalConstants() {
        // 禁止实例化
    }

    // ==================== 系统常量 ====================

    /**
     * 系统名称
     */
    public static final String SYSTEM_NAME = "KSET";

    /**
     * 默认字符集
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * 默认时区
     */
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * 默认日期格式
     */
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 默认时间格式
     */
    public static final String TIME_FORMAT = "HH:mm:ss";

    /**
     * 默认日期时间格式
     */
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 时间戳格式（精确到毫秒）
     */
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    // ==================== HTTP Header 常量 ====================

    /**
     * Token Header 名称
     */
    public static final String HEADER_TOKEN = "Authorization";

    /**
     * 用户ID Header
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 用户名 Header
     */
    public static final String HEADER_USERNAME = "X-Username";

    /**
     * 请求ID Header（链路追踪）
     */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 请求来源 Header
     */
    public static final String HEADER_SOURCE = "X-Source";

    /**
     * 设备ID Header
     */
    public static final String HEADER_DEVICE_ID = "X-Device-Id";

    /**
     * 客户端版本 Header
     */
    public static final String HEADER_VERSION = "X-Version";

    /**
     * 平台类型 Header（iOS/Android/Web）
     */
    public static final String HEADER_PLATFORM = "X-Platform";

    /**
     * 签名 Header
     */
    public static final String HEADER_SIGN = "X-Sign";

    /**
     * 时间戳 Header
     */
    public static final String HEADER_TIMESTAMP = "X-Timestamp";

    // ==================== 分页常量 ====================

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页数量
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页数量
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 状态常量 ====================

    /**
     * 启用状态
     */
    public static final int STATUS_ENABLED = 1;

    /**
     * 禁用状态
     */
    public static final int STATUS_DISABLED = 0;

    /**
     * 删除标记：未删除
     */
    public static final int NOT_DELETED = 0;

    /**
     * 删除标记：已删除
     */
    public static final int DELETED = 1;

    // ==================== 业务常量 ====================

    /**
     * 超级管理员角色ID
     */
    public static final Long SUPER_ADMIN_ROLE_ID = 1L;

    /**
     * 系统用户ID
     */
    public static final Long SYSTEM_USER_ID = 0L;

    /**
     * 匿名用户ID
     */
    public static final Long ANONYMOUS_USER_ID = -1L;

    // ==================== 平台类型 ====================

    /**
     * 平台：Web
     */
    public static final String PLATFORM_WEB = "web";

    /**
     * 平台：iOS
     */
    public static final String PLATFORM_IOS = "ios";

    /**
     * 平台：Android
     */
    public static final String PLATFORM_ANDROID = "android";

    /**
     * 平台：小程序
     */
    public static final String PLATFORM_MINI_PROGRAM = "miniprogram";

    // ==================== 正则表达式 ====================

    /**
     * 手机号正则（中国大陆）
     */
    public static final String REGEX_MOBILE = "^1[3-9]\\d{9}$";

    /**
     * 邮箱正则
     */
    public static final String REGEX_EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    /**
     * 用户名正则（字母开头，4-20位字母数字下划线）
     */
    public static final String REGEX_USERNAME = "^[a-zA-Z][a-zA-Z0-9_]{3,19}$";

    /**
     * 密码正则（8-20位，包含字母和数字）
     */
    public static final String REGEX_PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,20}$";
}
