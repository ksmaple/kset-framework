package com.kset.common.constant;

/**
 * 缓存常量定义
 *
 * @author kset
 */
public final class CacheConstants {

    private CacheConstants() {
        // 禁止实例化
    }

    // ==================== 缓存 Key 前缀 ====================

    /**
     * 通用前缀
     */
    public static final String PREFIX = "kset:";

    /**
     * 用户缓存前缀
     */
    public static final String USER_PREFIX = PREFIX + "user:";

    /**
     * Token 缓存前缀
     */
    public static final String TOKEN_PREFIX = PREFIX + "token:";

    /**
     * 验证码缓存前缀
     */
    public static final String CAPTCHA_PREFIX = PREFIX + "captcha:";

    /**
     * 短信验证码前缀
     */
    public static final String SMS_CODE_PREFIX = PREFIX + "sms:code:";

    /**
     * 邮箱验证码前缀
     */
    public static final String EMAIL_CODE_PREFIX = PREFIX + "email:code:";

    /**
     * 登录失败计数前缀
     */
    public static final String LOGIN_FAIL_PREFIX = PREFIX + "login:fail:";

    /**
     * 分布式锁前缀
     */
    public static final String LOCK_PREFIX = PREFIX + "lock:";

    /**
     * 限流前缀
     */
    public static final String RATE_LIMIT_PREFIX = PREFIX + "ratelimit:";

    /**
     * 配置缓存前缀
     */
    public static final String CONFIG_PREFIX = PREFIX + "config:";

    /**
     * 字典缓存前缀
     */
    public static final String DICT_PREFIX = PREFIX + "dict:";

    /**
     * 权限缓存前缀
     */
    public static final String PERMISSION_PREFIX = PREFIX + "perm:";

    /**
     * 菜单缓存前缀
     */
    public static final String MENU_PREFIX = PREFIX + "menu:";

    // ==================== 缓存过期时间（秒） ====================

    /**
     * 1分钟
     */
    public static final long EXPIRE_1_MIN = 60L;

    /**
     * 5分钟
     */
    public static final long EXPIRE_5_MIN = 5 * 60L;

    /**
     * 10分钟
     */
    public static final long EXPIRE_10_MIN = 10 * 60L;

    /**
     * 30分钟
     */
    public static final long EXPIRE_30_MIN = 30 * 60L;

    /**
     * 1小时
     */
    public static final long EXPIRE_1_HOUR = 60 * 60L;

    /**
     * 2小时
     */
    public static final long EXPIRE_2_HOUR = 2 * 60 * 60L;

    /**
     * 6小时
     */
    public static final long EXPIRE_6_HOUR = 6 * 60 * 60L;

    /**
     * 12小时
     */
    public static final long EXPIRE_12_HOUR = 12 * 60 * 60L;

    /**
     * 1天
     */
    public static final long EXPIRE_1_DAY = 24 * 60 * 60L;

    /**
     * 7天
     */
    public static final long EXPIRE_7_DAY = 7 * 24 * 60 * 60L;

    /**
     * 30天
     */
    public static final long EXPIRE_30_DAY = 30 * 24 * 60 * 60L;

    /**
     * 永不过期（-1）
     */
    public static final long EXPIRE_NEVER = -1L;

    // ==================== 业务缓存时间 ====================

    /**
     * Token 过期时间（24小时）
     */
    public static final long TOKEN_EXPIRE = EXPIRE_1_DAY;

    /**
     * 刷新Token 过期时间（7天）
     */
    public static final long REFRESH_TOKEN_EXPIRE = EXPIRE_7_DAY;

    /**
     * 验证码过期时间（5分钟）
     */
    public static final long CAPTCHA_EXPIRE = EXPIRE_5_MIN;

    /**
     * 短信验证码过期时间（5分钟）
     */
    public static final long SMS_CODE_EXPIRE = EXPIRE_5_MIN;

    /**
     * 登录失败锁定时间（30分钟）
     */
    public static final long LOGIN_LOCK_EXPIRE = EXPIRE_30_MIN;

    /**
     * 用户信息缓存时间（2小时）
     */
    public static final long USER_CACHE_EXPIRE = EXPIRE_2_HOUR;

    /**
     * 配置缓存时间（1小时）
     */
    public static final long CONFIG_CACHE_EXPIRE = EXPIRE_1_HOUR;

    /**
     * 权限缓存时间（30分钟）
     */
    public static final long PERMISSION_CACHE_EXPIRE = EXPIRE_30_MIN;
}
