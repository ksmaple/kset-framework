package com.kset.common.utils.date;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时区转换工具（基于 {@link ZonedDateTime}，链式 API）。
 * <p>
 * 支持人类常见输入：整小时偏移（如 {@code 8}、{@code -5}）、{@code GMT+8} / {@code UTC+8}、
 * 内置 {@link DateZone} 枚举；仅负责已有时间的跨时区转换与格式化。
 */
public class DateZoneHelper {

    /** 中国时区标识：GMT+8，同 {@link DateZone#CN} */
    public static final String CN_GMT = "GMT+8";
    /** 沙特时区标识：GMT+3，同 {@link DateZone#SAU} */
    public static final String SAU_GMT = "GMT+3";

    private static final Pattern PLAIN_OFFSET = Pattern.compile("^[+-]?\\d{1,2}$");
    private static final Pattern HOUR_MIN_OFFSET = Pattern.compile("^[+-]?(\\d{1,2}):(\\d{2})$");

    private ZonedDateTime dateTime;
    private ZoneId formatZone;

    private DateZoneHelper(ZonedDateTime dateTime) {
        this.dateTime = dateTime;
    }

    // ── 时区解析 ──────────────────────────────────────────

    /**
     * 整小时偏移转 {@link ZoneId}，如 {@code 8 → GMT+8}，{@code -5 → GMT-5}。
     */
    public static ZoneId zoneOf(int offsetHours) {
        return zoneOf(offsetHours, 0);
    }

    /**
     * 小时 + 分钟偏移转 {@link ZoneId}，如 {@code (5, 30) → GMT+05:30}。
     */
    public static ZoneId zoneOf(int offsetHours, int offsetMinutes) {
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(offsetHours, offsetMinutes);
        return ZoneId.ofOffset("GMT", offset);
    }

    /**
     * 内置常见时区枚举转 {@link ZoneId}。
     */
    public static ZoneId zoneOf(DateZone zone) {
        return zone.toZoneId();
    }

    /**
     * 解析人类常见时区写法为 {@link ZoneId}。
     * <p>
     * 支持：{@code 8}、{@code +8}、{@code -5}、{@code GMT+8}、{@code UTC+8}、{@code GMT+05:30}、
     * {@code CN}/{@code SAU} 等 {@link DateZone} 枚举名、IANA ID（如 {@code Asia/Shanghai}）。
     */
    public static ZoneId parseZone(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("zone text must not be blank");
        }
        String raw = text.trim();
        String upper = raw.toUpperCase();

        if ("Z".equals(upper) || "UTC".equals(upper) || "GMT".equals(upper)) {
            return DateZone.UTC.toZoneId();
        }

        try {
            return DateZone.valueOf(upper).toZoneId();
        } catch (IllegalArgumentException ignored) {
            // not enum name
        }

        if (PLAIN_OFFSET.matcher(raw).matches()) {
            return zoneOf(Integer.parseInt(raw));
        }

        Matcher hm = HOUR_MIN_OFFSET.matcher(raw);
        if (hm.matches()) {
            int sign = raw.startsWith("-") ? -1 : 1;
            int hours = Integer.parseInt(hm.group(1)) * sign;
            int minutes = Integer.parseInt(hm.group(2)) * sign;
            return zoneOf(hours, minutes);
        }

        String normalized = upper
                .replace("GTM", "GMT")
                .replace("UTC", "GMT");
        if (normalized.startsWith("GMT")) {
            if ("GMT".equals(normalized)) {
                return DateZone.UTC.toZoneId();
            }
            return ZoneId.of(normalized);
        }
        if (raw.startsWith("+") || raw.startsWith("-")) {
            return ZoneId.of("GMT" + raw);
        }

        return ZoneId.of(raw);
    }

    /**
     * 偏移量转 GMT 标签，如 {@code GMT+8}、{@code GMT+05:30}、{@code GMT-5}。
     */
    public static String toGmtLabel(int offsetHours, int offsetMinutes) {
        if (offsetMinutes == 0) {
            if (offsetHours == 0) {
                return "GMT";
            }
            return offsetHours > 0 ? "GMT+" + offsetHours : "GMT" + offsetHours;
        }
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(offsetHours, offsetMinutes);
        return "GMT" + offset.getId();
    }

    // ── 解析（指定源时区）────────────────────────────────

    /**
     * 由 {@link Date} 解析，源时区为服务器默认时区。
     */
    public static DateZoneHelper of(Date date) {
        return of(date, ZoneId.systemDefault());
    }

    /**
     * 由 {@link Date} 解析，并指定源时区。
     */
    public static DateZoneHelper of(Date date, ZoneId zone) {
        return new DateZoneHelper(date.toInstant().atZone(zone));
    }

    /**
     * 由 {@link Date} 解析，源时区为整小时偏移。
     */
    public static DateZoneHelper of(Date date, int offsetHours) {
        return of(date, zoneOf(offsetHours));
    }

    /**
     * 由 {@link Date} 解析，源时区为内置枚举。
     */
    public static DateZoneHelper of(Date date, DateZone zone) {
        return of(date, zone.toZoneId());
    }

    /**
     * 由 epoch 毫秒解析，源时区为服务器默认时区。
     */
    public static DateZoneHelper of(long epochMillis) {
        return of(epochMillis, ZoneId.systemDefault());
    }

    /**
     * 由 epoch 毫秒解析，并指定源时区。
     */
    public static DateZoneHelper of(long epochMillis, ZoneId zone) {
        return new DateZoneHelper(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /**
     * 由 epoch 毫秒解析，源时区为整小时偏移。
     */
    public static DateZoneHelper of(long epochMillis, int offsetHours) {
        return of(epochMillis, zoneOf(offsetHours));
    }

    /**
     * 由 epoch 毫秒解析，源时区为内置枚举。
     */
    public static DateZoneHelper of(long epochMillis, DateZone zone) {
        return of(epochMillis, zone.toZoneId());
    }

    /**
     * 由 {@link DateHelper} 保存的时刻构造。
     */
    public static DateZoneHelper ofLocal(DateHelper local) {
        return of(local.toDate());
    }

    /**
     * 复制当前带时区时刻，用于链式运算中避免修改原实例。
     */
    public DateZoneHelper copy() {
        return new DateZoneHelper(dateTime);
    }

    // ── 时区转换 ──────────────────────────────────────────

    /**
     * 同一时刻切换到目标时区（墙钟变化，epoch 不变）。
     */
    public DateZoneHelper toZone(ZoneId zone) {
        this.dateTime = dateTime.withZoneSameInstant(zone);
        return this;
    }

    /**
     * 同一时刻切换到整小时偏移时区。
     */
    public DateZoneHelper toZone(int offsetHours) {
        return toZone(zoneOf(offsetHours));
    }

    /**
     * 同一时刻切换到内置枚举时区。
     */
    public DateZoneHelper toZone(DateZone zone) {
        return toZone(zone.toZoneId());
    }

    /**
     * 同一时刻切换到目标时区。
     */
    public DateZoneHelper toZone(TimeZone timeZone) {
        return toZone(timeZone.toZoneId());
    }

    /**
     * 同一时刻切换到目标时区（字符串解析，支持 {@code 8}、{@code GMT+8}、{@code CN} 等）。
     */
    public DateZoneHelper toZone(String zoneText) {
        return toZone(parseZone(zoneText));
    }

    /**
     * 切换到中国区（东八区）。
     */
    public DateZoneHelper toCN() {
        return toZone(DateZone.CN);
    }

    /**
     * 切换到沙特区（东三区）。
     */
    public DateZoneHelper toSAU() {
        return toZone(DateZone.SAU);
    }

    /**
     * 转为服务器本地 {@link DateHelper}（同一时刻）。
     */
    public DateHelper toLocal() {
        return DateHelper.of(toDate());
    }

    /**
     * 按自定义格式将沙特墙钟字符串解析为时刻。
     */
    public static DateHelper sauToLocal(String time, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone(DateZone.SAU.toZoneId()));
        try {
            return DateHelper.of(format.parse(time));
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid date text: " + time, e);
        }
    }

    /**
     * 按 {@link DateHelper#PATTERN_DEF} 解析沙特墙钟字符串，转为服务器本地 {@link DateHelper}。
     */
    public static DateHelper sauToLocalDef(String time) {
        return sauToLocal(time, DateHelper.PATTERN_DEF);
    }

    /**
     * 服务器本地墙钟时间 → 沙特墙钟字符串（{@link DateHelper#PATTERN_DEF}）。
     */
    public static String localToSauDef(DateHelper local) {
        return ofLocal(local).formatZone(DateZone.SAU).format(DateHelper.PATTERN_DEF);
    }

    // ── 按目标时区格式化 ──────────────────────────────────

    /**
     * 指定输出时区（不改变内部时刻，仅影响后续 {@link #format}）。
     */
    public DateZoneHelper formatZone(ZoneId zone) {
        this.formatZone = zone;
        return this;
    }

    public DateZoneHelper formatZone(int offsetHours) {
        return formatZone(zoneOf(offsetHours));
    }

    public DateZoneHelper formatZone(DateZone zone) {
        return formatZone(zone.toZoneId());
    }

    public DateZoneHelper formatZone(String zoneText) {
        return formatZone(parseZone(zoneText));
    }

    /**
     * 指定输出时区（不改变内部时刻，仅影响后续 {@link #format}）。
     */
    public DateZoneHelper formatZone(TimeZone zone) {
        return formatZone(zone.toZoneId());
    }

    /**
     * 按自定义格式格式化为目标时区墙钟字符串。
     */
    public String format(String pattern) {
        ZoneId zone = formatZone != null ? formatZone : dateTime.getZone();
        return dateTime.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 将同一 {@link Date} 时刻格式化为目标时区墙钟字符串。
     */
    public static String format(Date date, ZoneId zone, String pattern) {
        return of(date).toZone(zone).format(pattern);
    }

    public static String format(Date date, int offsetHours, String pattern) {
        return format(date, zoneOf(offsetHours), pattern);
    }

    public static String format(Date date, DateZone zone, String pattern) {
        return format(date, zone.toZoneId(), pattern);
    }

    public static String format(Date date, String zoneText, String pattern) {
        return format(date, parseZone(zoneText), pattern);
    }

    /**
     * 将 epoch 毫秒格式化为目标时区墙钟字符串。
     */
    public static String format(long epochMillis, ZoneId zone, String pattern) {
        return of(epochMillis).toZone(zone).format(pattern);
    }

    // ── 导出 ──────────────────────────────────────────────

    /**
     * 转为 {@link ZonedDateTime}。
     */
    public ZonedDateTime toZonedDateTime() {
        return dateTime;
    }

    /**
     * 当前绑定的时区。
     */
    public ZoneId getZone() {
        return dateTime.getZone();
    }

    /**
     * 转为 {@link Date}（同一时刻）。
     */
    public Date toDate() {
        return Date.from(dateTime.toInstant());
    }

    /**
     * 转为 epoch 毫秒时间戳。
     */
    public long toEpochMilli() {
        return dateTime.toInstant().toEpochMilli();
    }
}
