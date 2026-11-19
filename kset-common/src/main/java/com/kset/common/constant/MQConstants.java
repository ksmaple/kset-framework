package com.kset.common.constant;

/**
 * MQ 消息队列常量定义
 *
 * @author kset
 */
public final class MQConstants {

    private MQConstants() {
        // 禁止实例化
    }

    // ==================== Topic 定义 ====================

    /**
     * 用户相关 Topic
     */
    public static final String TOPIC_USER = "kset-topic-user";

    /**
     * 订单相关 Topic
     */
    public static final String TOPIC_ORDER = "kset-topic-order";

    /**
     * 通知消息 Topic
     */
    public static final String TOPIC_NOTIFICATION = "kset-topic-notification";

    /**
     * 系统事件 Topic
     */
    public static final String TOPIC_SYSTEM_EVENT = "kset-topic-system-event";

    /**
     * 日志收集 Topic
     */
    public static final String TOPIC_LOG = "kset-topic-log";

    // ==================== Consumer Group 定义 ====================

    /**
     * 用户服务消费组
     */
    public static final String GROUP_USER_SERVICE = "kset-group-user-service";

    /**
     * 订单服务消费组
     */
    public static final String GROUP_ORDER_SERVICE = "kset-group-order-service";

    /**
     * 通知服务消费组
     */
    public static final String GROUP_NOTIFICATION_SERVICE = "kset-group-notification-service";

    /**
     * 日志服务消费组
     */
    public static final String GROUP_LOG_SERVICE = "kset-group-log-service";

    // ==================== Tag 定义 ====================

    /**
     * 用户注册 Tag
     */
    public static final String TAG_USER_REGISTER = "user-register";

    /**
     * 用户登录 Tag
     */
    public static final String TAG_USER_LOGIN = "user-login";

    /**
     * 用户信息更新 Tag
     */
    public static final String TAG_USER_UPDATE = "user-update";

    /**
     * 订单创建 Tag
     */
    public static final String TAG_ORDER_CREATE = "order-create";

    /**
     * 订单支付 Tag
     */
    public static final String TAG_ORDER_PAY = "order-pay";

    /**
     * 订单取消 Tag
     */
    public static final String TAG_ORDER_CANCEL = "order-cancel";

    /**
     * 短信通知 Tag
     */
    public static final String TAG_SMS = "sms";

    /**
     * 邮件通知 Tag
     */
    public static final String TAG_EMAIL = "email";

    /**
     * 推送通知 Tag
     */
    public static final String TAG_PUSH = "push";

    // ==================== 延迟级别定义 (RocketMQ) ====================

    /**
     * 延迟级别：1秒
     */
    public static final int DELAY_LEVEL_1S = 1;

    /**
     * 延迟级别：5秒
     */
    public static final int DELAY_LEVEL_5S = 2;

    /**
     * 延迟级别：10秒
     */
    public static final int DELAY_LEVEL_10S = 3;

    /**
     * 延迟级别：30秒
     */
    public static final int DELAY_LEVEL_30S = 4;

    /**
     * 延迟级别：1分钟
     */
    public static final int DELAY_LEVEL_1M = 5;

    /**
     * 延迟级别：5分钟
     */
    public static final int DELAY_LEVEL_5M = 6;

    /**
     * 延迟级别：10分钟
     */
    public static final int DELAY_LEVEL_10M = 7;

    /**
     * 延迟级别：30分钟
     */
    public static final int DELAY_LEVEL_30M = 8;

    /**
     * 延迟级别：1小时
     */
    public static final int DELAY_LEVEL_1H = 9;
}
