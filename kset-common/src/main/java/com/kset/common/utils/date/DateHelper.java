package com.kset.common.utils.date;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日期时间工具（基于 {@link Date} 和 {@link Calendar}，链式 API）。
 * <p>
 * 周期边界统一使用服务器默认时区。{@code *EndExclusive} 表示下一周期起点，
 * 适用于 SQL 和统计的左闭右开区间。
 */
public class DateHelper {

    public static final String PATTERN_DEF = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DEF_MS = "yyyy-MM-dd HH:mm:ss:SSS";
    public static final String PATTERN_DAY_DEF = "yyyyMMdd";
    public static final String PATTERN_DAY_SP = "yyyy-MM-dd";
    public static final String PATTERN_MONTH_DEF = "yyyyMM";
    public static final String PATTERN_YEAR_DEF = "yyyy";
    public static final String PATTERN_ONLY_MONTH = "MM";
    public static final String PATTERN_ONLY_DAY = "dd";
    public static final String PATTERN_MM_DD = "MM-dd";
    public static final int MinSec = 60;
    public static final int HouSec = 60 * 60;
    public static final int DaySec = 24 * 60 * 60;
    public static final long SecMil = 1000;
    public static final long MinMil = 60 * 1000;
    public static final long HouMil = 60 * 60 * 1000;
    public static final long DayMil = 24 * 60 * 60 * 1000;
    public static final int ISO_MONDAY = 1;
    public static final int ISO_SUNDAY = 7;

    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private Date date;

    private DateHelper(Date date) {
        this.date = copyOf(date);
    }

    public static long nowMil() {
        return System.currentTimeMillis();
    }

    public static long nowSecond() {
        return nowMil() / 1000;
    }

    public static DateHelper build() {
        return new DateHelper(new Date());
    }

    public static DateHelper now() {
        return build();
    }

    public static DateHelper parse(String text) {
        return new DateHelper(parseDate(text));
    }

    public static DateHelper of(String text) {
        return parse(text);
    }

    public static DateHelper of(Date date) {
        return new DateHelper(date);
    }

    public static DateHelper of(long epochMillis) {
        return new DateHelper(new Date(epochMillis));
    }

    public DateHelper copy() {
        return new DateHelper(date);
    }

    public static DatePeriod thisMonthRange() {
        return build().monthRangeExclusive();
    }

    public static DatePeriod thisMonthRangeInclusive() {
        return build().monthRangeInclusive();
    }

    public static DatePeriod lastMonthRange() {
        return build().previousMonth().monthRangeExclusive();
    }

    public static DatePeriod lastMonthRangeInclusive() {
        return build().previousMonth().monthRangeInclusive();
    }

    public static DatePeriod thisWeekRange() {
        return build().weekRangeExclusive();
    }

    public static DatePeriod thisWeekRangeInclusive() {
        return build().weekRangeInclusive();
    }

    public static DatePeriod lastWeekRange() {
        return build().previousWeek().weekRangeExclusive();
    }

    public static DatePeriod lastWeekRangeInclusive() {
        return build().previousWeek().weekRangeInclusive();
    }

    public static DatePeriod thisYearRange() {
        return build().yearRangeExclusive();
    }

    public static DatePeriod thisYearRangeInclusive() {
        return build().yearRangeInclusive();
    }

    public static DatePeriod todayRange() {
        return build().dayRangeExclusive();
    }

    public static DatePeriod todayRangeInclusive() {
        return build().dayRangeInclusive();
    }

    public static DatePeriod rangeInclusive(Date start, Date end) {
        return DatePeriod.ofInclusiveEnd(start, end);
    }

    public static DatePeriod rangeExclusive(Date start, Date endExclusive) {
        return DatePeriod.ofExclusiveEnd(start, endExclusive);
    }

    public static long daysUntil(Date start, Date end) {
        return (calendarDayMillis(end) - calendarDayMillis(start)) / DayMil;
    }

    public static long daysBetween(Date a, Date b) {
        return Math.abs(daysUntil(a, b));
    }

    public static long hoursBetween(Date a, Date b) {
        return Math.abs(a.getTime() - b.getTime()) / HouMil;
    }

    public static long minutesBetween(Date a, Date b) {
        return Math.abs(a.getTime() - b.getTime()) / MinMil;
    }

    public static long secondsBetween(Date a, Date b) {
        return Math.abs(a.getTime() - b.getTime()) / SecMil;
    }

    public static boolean isBefore(Date a, Date b) {
        return a.before(b);
    }

    public static boolean isAfter(Date a, Date b) {
        return a.after(b);
    }

    public static boolean isSameDay(Date a, Date b) {
        Calendar first = calendar(a);
        Calendar second = calendar(b);
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isSameMonth(Date a, Date b) {
        Calendar first = calendar(a);
        Calendar second = calendar(b);
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.MONTH) == second.get(Calendar.MONTH);
    }

    public static boolean isToday(Date point) {
        return isSameDay(point, new Date());
    }

    public static boolean isInRange(Date point, Date start, Date end) {
        long time = point.getTime();
        return time >= start.getTime() && time <= end.getTime();
    }

    public static boolean isInRangeExclusive(Date point, Date start, Date endExclusive) {
        long time = point.getTime();
        return time >= start.getTime() && time < endExclusive.getTime();
    }

    public static boolean isInRangeInclusive(Date point, Date start, Date endInclusive) {
        return isInRange(point, start, endInclusive);
    }

    public static Date max(Date a, Date b) {
        return isAfter(a, b) ? a : b;
    }

    public static Date min(Date a, Date b) {
        return isBefore(a, b) ? a : b;
    }

    public boolean isRange(Date start, Date end) {
        return isInRange(date, start, end);
    }

    public boolean isRangeExclusive(Date start, Date endExclusive) {
        return isInRangeExclusive(date, start, endExclusive);
    }

    public boolean isRangeInclusive(Date start, Date end) {
        return isInRange(date, start, end);
    }

    public boolean isBefore(Date other) {
        return isBefore(date, other);
    }

    public boolean isAfter(Date other) {
        return isAfter(date, other);
    }

    public boolean isSameDay(Date other) {
        return isSameDay(date, other);
    }

    public boolean isSameMonth(Date other) {
        return isSameMonth(date, other);
    }

    public DateHelper at(String text) {
        this.date = parseDate(text);
        return this;
    }

    public DateHelper withDate(Date date) {
        this.date = copyOf(date);
        return this;
    }

    public DateHelper withDate(long epochMillis) {
        this.date = new Date(epochMillis);
        return this;
    }

    public DateHelper withTime(String time) {
        String pattern = time.contains(".") ? "HH:mm:ss.SSS" : "HH:mm:ss";
        Date parsed = parseExact(time, pattern);
        Calendar source = calendar(parsed);
        Calendar target = calendar(date);
        target.set(Calendar.HOUR_OF_DAY, source.get(Calendar.HOUR_OF_DAY));
        target.set(Calendar.MINUTE, source.get(Calendar.MINUTE));
        target.set(Calendar.SECOND, source.get(Calendar.SECOND));
        target.set(Calendar.MILLISECOND, source.get(Calendar.MILLISECOND));
        return update(target);
    }

    public DateHelper withPattern(String text, String pattern) {
        this.date = parseExact(text, pattern);
        return this;
    }

    public DateHelper addYear(int years) {
        return add(Calendar.YEAR, years);
    }

    public DateHelper addMonth(int month) {
        return add(Calendar.MONTH, month);
    }

    public DateHelper addWeeks(int weeks) {
        return add(Calendar.WEEK_OF_YEAR, weeks);
    }

    public DateHelper addDay(int day) {
        return add(Calendar.DAY_OF_MONTH, day);
    }

    public DateHelper addHour(int hour) {
        return add(Calendar.HOUR_OF_DAY, hour);
    }

    public DateHelper addMinutes(int minutes) {
        return add(Calendar.MINUTE, minutes);
    }

    public DateHelper addSecond(int second) {
        return add(Calendar.SECOND, second);
    }

    public DateHelper previousMonth() {
        return addMonth(-1);
    }

    public DateHelper nextMonth() {
        return addMonth(1);
    }

    public DateHelper previousWeek() {
        return addWeeks(-1);
    }

    public DateHelper nextWeek() {
        return addWeeks(1);
    }

    public DateHelper previousYear() {
        return addYear(-1);
    }

    public DateHelper nextYear() {
        return addYear(1);
    }

    public DateHelper firstDayOfPreviousMonth() {
        return monthFirstDate().addMonth(-1);
    }

    public DateHelper firstDayOfNextMonth() {
        return monthFirstDate().addMonth(1);
    }

    public DateHelper firstDayOfPreviousWeek() {
        return weekFirstDate().addWeeks(-1);
    }

    public DateHelper firstDayOfPreviousYear() {
        return yearFirstDate().addYear(-1);
    }

    public DateHelper dayFirstDate() {
        return update(startOfDay(calendar(date)));
    }

    public DateHelper dayLastDate() {
        Calendar value = startOfDay(calendar(date));
        value.add(Calendar.DAY_OF_MONTH, 1);
        value.add(Calendar.MILLISECOND, -1);
        return update(value);
    }

    public DateHelper dayEndExclusive() {
        Calendar value = startOfDay(calendar(date));
        value.add(Calendar.DAY_OF_MONTH, 1);
        return update(value);
    }

    public DatePeriod dayRangeExclusive() {
        return DatePeriod.ofExclusiveEnd(copy().dayFirstDate().toDate(), copy().dayEndExclusive().toDate());
    }

    public DatePeriod dayRangeInclusive() {
        return DatePeriod.ofInclusiveEnd(copy().dayFirstDate().toDate(), copy().dayLastDate().toDate());
    }

    public DateHelper weekFirstDate() {
        return weekFirstDate(ISO_MONDAY);
    }

    public DateHelper weekFirstDate(int weekDay) {
        validateWeekDay(weekDay);
        Calendar value = startOfDay(calendar(date));
        int daysToStart = weekDay - isoWeekDay(value);
        if (daysToStart > 0) {
            daysToStart -= 7;
        }
        value.add(Calendar.DAY_OF_MONTH, daysToStart);
        return update(value);
    }

    public DateHelper weekLastDate() {
        return weekLastDate(ISO_MONDAY);
    }

    public DateHelper weekLastDate(int weekStartDay) {
        return weekFirstDate(weekStartDay).addDay(7).add(Calendar.MILLISECOND, -1);
    }

    public DateHelper weekEndExclusive() {
        return weekEndExclusive(ISO_MONDAY);
    }

    public DateHelper weekEndExclusive(int weekStartDay) {
        return weekFirstDate(weekStartDay).addDay(7);
    }

    public DatePeriod weekRangeExclusive() {
        return weekRangeExclusive(ISO_MONDAY);
    }

    public DatePeriod weekRangeExclusive(int weekStartDay) {
        return DatePeriod.ofExclusiveEnd(copy().weekFirstDate(weekStartDay).toDate(),
                copy().weekEndExclusive(weekStartDay).toDate());
    }

    public DatePeriod weekRangeInclusive() {
        return weekRangeInclusive(ISO_MONDAY);
    }

    public DatePeriod weekRangeInclusive(int weekStartDay) {
        return DatePeriod.ofInclusiveEnd(copy().weekFirstDate(weekStartDay).toDate(),
                copy().weekLastDate(weekStartDay).toDate());
    }

    public DateHelper monthFirstDate() {
        Calendar value = startOfDay(calendar(date));
        value.set(Calendar.DAY_OF_MONTH, 1);
        return update(value);
    }

    public DateHelper monthLastDate() {
        return monthFirstDate().addMonth(1).add(Calendar.MILLISECOND, -1);
    }

    public DateHelper monthEndExclusive() {
        return monthFirstDate().addMonth(1);
    }

    public DatePeriod monthRangeExclusive() {
        return DatePeriod.ofExclusiveEnd(copy().monthFirstDate().toDate(), copy().monthEndExclusive().toDate());
    }

    public DatePeriod monthRangeInclusive() {
        return DatePeriod.ofInclusiveEnd(copy().monthFirstDate().toDate(), copy().monthLastDate().toDate());
    }

    public DateHelper quarterFirstDate() {
        Calendar value = startOfDay(calendar(date));
        int firstMonth = value.get(Calendar.MONTH) / 3 * 3;
        value.set(Calendar.DAY_OF_MONTH, 1);
        value.set(Calendar.MONTH, firstMonth);
        return update(value);
    }

    public DateHelper quarterLastDate() {
        return quarterFirstDate().addMonth(3).add(Calendar.MILLISECOND, -1);
    }

    public DateHelper quarterEndExclusive() {
        return quarterFirstDate().addMonth(3);
    }

    public DatePeriod quarterRangeExclusive() {
        return DatePeriod.ofExclusiveEnd(copy().quarterFirstDate().toDate(), copy().quarterEndExclusive().toDate());
    }

    public DatePeriod quarterRangeInclusive() {
        return DatePeriod.ofInclusiveEnd(copy().quarterFirstDate().toDate(), copy().quarterLastDate().toDate());
    }

    public DateHelper yearFirstDate() {
        Calendar value = startOfDay(calendar(date));
        value.set(Calendar.MONTH, Calendar.JANUARY);
        value.set(Calendar.DAY_OF_MONTH, 1);
        return update(value);
    }

    public DateHelper yearLastDate() {
        return yearFirstDate().addYear(1).add(Calendar.MILLISECOND, -1);
    }

    public DateHelper yearEndExclusive() {
        return yearFirstDate().addYear(1);
    }

    public DatePeriod yearRangeExclusive() {
        return DatePeriod.ofExclusiveEnd(copy().yearFirstDate().toDate(), copy().yearEndExclusive().toDate());
    }

    public DatePeriod yearRangeInclusive() {
        return DatePeriod.ofInclusiveEnd(copy().yearFirstDate().toDate(), copy().yearLastDate().toDate());
    }

    public long daysUntil(Date other) {
        return daysUntil(date, other);
    }

    public long daysBetween(Date other) {
        return daysBetween(date, other);
    }

    public long hoursBetween(Date other) {
        return hoursBetween(date, other);
    }

    public long minutesBetween(Date other) {
        return minutesBetween(date, other);
    }

    public long secondsBetween(Date other) {
        return secondsBetween(date, other);
    }

    public Date toDate() {
        return copyOf(date);
    }

    public String format(String pattern) {
        return formatDate(date, pattern);
    }

    public long toMil() {
        return date.getTime();
    }

    public int toSecond() {
        return (int) (toMil() / 1000);
    }

    public String toyyyyMMddHHmmss() {
        return format(PATTERN_DEF);
    }

    public String toMM() {
        return format(PATTERN_ONLY_MONTH);
    }

    public String toDD() {
        return format(PATTERN_ONLY_DAY);
    }

    public String toyyyyMMddHHmmssSSS() {
        return format(PATTERN_DEF_MS);
    }

    public String toyyyyMMdd() {
        return format(PATTERN_DAY_DEF);
    }

    public String toyyyyMMddT() {
        return format(PATTERN_DAY_SP);
    }

    public String toyyyyMM() {
        return format(PATTERN_MONTH_DEF);
    }

    public String toMMDD() {
        return format(PATTERN_MM_DD);
    }

    public String toyyyy() {
        return format(PATTERN_YEAR_DEF);
    }

    public String uniqueKeyPer5Min() {
        Calendar value = calendar(date);
        value.set(Calendar.MINUTE, value.get(Calendar.MINUTE) / 5 * 5);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return formatDate(value.getTime(), "yyyyMMddHHmm") + "_5";
    }

    public String uniqueKeyPer1Min() {
        Calendar value = calendar(date);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return formatDate(value.getTime(), "yyyyMMddHHmm") + "_1";
    }

    private DateHelper add(int field, int amount) {
        Calendar value = calendar(date);
        value.add(field, amount);
        return update(value);
    }

    private DateHelper update(Calendar value) {
        this.date = value.getTime();
        return this;
    }

    private static Calendar calendar(Date value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        return calendar;
    }

    private static Calendar startOfDay(Calendar value) {
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return value;
    }

    private static int isoWeekDay(Calendar value) {
        return (value.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    private static void validateWeekDay(int weekDay) {
        if (weekDay < ISO_MONDAY || weekDay > ISO_SUNDAY) {
            throw new IllegalArgumentException("weekDay must be between 1 and 7");
        }
    }

    private static long calendarDayMillis(Date value) {
        Calendar local = calendar(value);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private static Date parseDate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("date text must not be blank");
        }
        String value = text.trim();
        if (value.length() >= 23 && value.charAt(4) == '-' && value.charAt(7) == '-'
                && value.charAt(10) == ' ' && value.charAt(19) == ':') {
            return parseExact(value.substring(0, 23), PATTERN_DEF_MS);
        }
        if (value.length() >= 19 && value.charAt(4) == '-' && value.charAt(7) == '-'
                && value.charAt(10) == ' ') {
            return parseExact(value.substring(0, 19), PATTERN_DEF);
        }
        if (value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            return parseExact(value, PATTERN_DAY_SP);
        }
        if (value.length() == 8 && isDigits(value)) {
            return parseExact(value, PATTERN_DAY_DEF);
        }
        if (value.length() == 6 && isDigits(value)) {
            return parseExact(value, PATTERN_MONTH_DEF);
        }
        if (value.length() == 4 && isDigits(value)) {
            return parseExact(value, PATTERN_YEAR_DEF);
        }
        throw new IllegalArgumentException("unsupported date text: " + text);
    }

    private static Date parseExact(String text, String pattern) {
        try {
            TemporalAccessor parsed = formatter(pattern).parse(text);
            return Date.from(toLocalDateTime(parsed).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date text: " + text, e);
        }
    }

    private static String formatDate(Date value, String pattern) {
        return formatter(pattern).format(LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()));
    }

    private static LocalDateTime toLocalDateTime(TemporalAccessor parsed) {
        int year = parsed.isSupported(ChronoField.YEAR) ? parsed.get(ChronoField.YEAR) : 1970;
        int month = parsed.isSupported(ChronoField.MONTH_OF_YEAR) ? parsed.get(ChronoField.MONTH_OF_YEAR) : 1;
        int day = parsed.isSupported(ChronoField.DAY_OF_MONTH) ? parsed.get(ChronoField.DAY_OF_MONTH) : 1;
        int hour = parsed.isSupported(ChronoField.HOUR_OF_DAY) ? parsed.get(ChronoField.HOUR_OF_DAY) : 0;
        int minute = parsed.isSupported(ChronoField.MINUTE_OF_HOUR) ? parsed.get(ChronoField.MINUTE_OF_HOUR) : 0;
        int second = parsed.isSupported(ChronoField.SECOND_OF_MINUTE) ? parsed.get(ChronoField.SECOND_OF_MINUTE) : 0;
        int nano = parsed.isSupported(ChronoField.NANO_OF_SECOND) ? parsed.get(ChronoField.NANO_OF_SECOND) : 0;
        return LocalDateTime.of(year, month, day, hour, minute, second, nano);
    }

    private static DateTimeFormatter formatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    /**
     * 保留原因：每次 new SimpleDateFormat，热路径分配高。
     */
    @SuppressWarnings("unused")
    private static SimpleDateFormat formatterForRollback(String pattern) {
        return new SimpleDateFormat(pattern);
    }

    private static Date copyOf(Date value) {
        if (value == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        return new Date(value.getTime());
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 时间区间，支持左闭右闭和左闭右开两种边界。 */
    public static final class DatePeriod {

        public enum BoundaryType {
            INCLUSIVE_END,
            EXCLUSIVE_END
        }

        private final Date startInclusive;
        private final Date end;
        private final BoundaryType boundaryType;

        private DatePeriod(Date startInclusive, Date end, BoundaryType boundaryType) {
            this.startInclusive = copyOf(startInclusive);
            this.end = copyOf(end);
            this.boundaryType = boundaryType;
        }

        public static DatePeriod ofInclusiveEnd(Date startInclusive, Date endInclusive) {
            return new DatePeriod(startInclusive, endInclusive, BoundaryType.INCLUSIVE_END);
        }

        public static DatePeriod ofExclusiveEnd(Date startInclusive, Date endExclusive) {
            return new DatePeriod(startInclusive, endExclusive, BoundaryType.EXCLUSIVE_END);
        }

        public BoundaryType getBoundaryType() {
            return boundaryType;
        }

        public boolean isExclusiveEnd() {
            return boundaryType == BoundaryType.EXCLUSIVE_END;
        }

        public Date getStartInclusive() {
            return copyOf(startInclusive);
        }

        public Date getEnd() {
            return copyOf(end);
        }

        public Date getEndInclusive() {
            if (boundaryType != BoundaryType.INCLUSIVE_END) {
                throw new IllegalStateException("not an inclusive-end period, use getEndExclusive()");
            }
            return copyOf(end);
        }

        public Date getEndExclusive() {
            if (boundaryType != BoundaryType.EXCLUSIVE_END) {
                throw new IllegalStateException("not an exclusive-end period, use getEndInclusive()");
            }
            return copyOf(end);
        }

        public boolean contains(Date instant) {
            return boundaryType == BoundaryType.INCLUSIVE_END
                    ? isInRange(instant, startInclusive, end)
                    : isInRangeExclusive(instant, startInclusive, end);
        }

        public boolean containsNow() {
            return contains(new Date());
        }

        @Override
        public String toString() {
            return boundaryType == BoundaryType.INCLUSIVE_END
                    ? "[" + startInclusive + ", " + end + "]"
                    : "[" + startInclusive + ", " + end + ")";
        }
    }
}
