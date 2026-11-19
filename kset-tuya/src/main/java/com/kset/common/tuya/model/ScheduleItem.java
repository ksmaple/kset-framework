package com.kset.common.tuya.model;

import lombok.Builder;
import lombok.Data;

/**
 * 周期性密码调度项
 *
 * @author kset
 */
@Data
@Builder
public class ScheduleItem {

    /**
     * 开始时间（分钟形式）
     * 例如 "07:30" 的取值为 7*60+30=450
     */
    private Long effectiveTime;

    /**
     * 结束时间（分钟形式）
     * 例如 "07:30" 的取值为 7*60+30=450
     */
    private Long invalidTime;

    /**
     * 循环工作日
     * 一周的时间用一个字节表示，bit0~bit6 分别代表周日~周六
     * 1表示有效，0表示无效，最后一位以0表示
     * 例如: 周一/周二/周五 = 00100110 = 38
     */
    private Integer workingDay;
}
