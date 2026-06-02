package com.kset.common.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 常见版本号判断工具。
 * <p>
 * 数字段逐段比较，缺失段按 0 处理，因此 {@code 1.0}、{@code 1.0.0}、{@code 1.0.0.0}
 * 视为相同版本。支持常见 {@code v} 前缀、{@code -SNAPSHOT}、{@code -RC1}、{@code .Final}
 * 等后缀，构建元数据 {@code +build} 不参与比较。
 */
public final class VersionUtil {

    private static final String QUALIFIER_RELEASE = "";
    private static final int UNKNOWN_QUALIFIER_RANK = 4;

    private VersionUtil() {
    }

    public static int compare(String leftVersion, String rightVersion) {
        ParsedVersion left = parse(leftVersion);
        ParsedVersion right = parse(rightVersion);

        int numberCompare = compareNumbers(left.numbers(), right.numbers());
        if (numberCompare != 0) {
            return numberCompare;
        }
        return compareQualifier(left.qualifier(), right.qualifier());
    }

    public static boolean isEqual(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) == 0;
    }

    public static boolean greaterThan(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) > 0;
    }

    public static boolean greaterThanOrEqual(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) >= 0;
    }

    public static boolean lessThan(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) < 0;
    }

    public static boolean lessThanOrEqual(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) <= 0;
    }

    /**
     * 判断当前版本是否满足最低版本要求。
     */
    public static boolean isAtLeast(String currentVersion, String requiredVersion) {
        return greaterThanOrEqual(currentVersion, requiredVersion);
    }

    /**
     * 判断版本是否位于左闭右开区间 [minInclusive, maxExclusive)。
     */
    public static boolean inRange(String version, String minInclusive, String maxExclusive) {
        return greaterThanOrEqual(version, minInclusive) && lessThan(version, maxExclusive);
    }

    public static boolean isValid(String version) {
        try {
            parse(version);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static ParsedVersion parse(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("version 不能为空");
        }

        String value = version.trim();
        if (value.length() > 1 && (value.charAt(0) == 'v' || value.charAt(0) == 'V')
                && Character.isDigit(value.charAt(1))) {
            value = value.substring(1);
        }

        int buildIndex = value.indexOf('+');
        if (buildIndex >= 0) {
            value = value.substring(0, buildIndex);
        }

        int qualifierIndex = value.indexOf('-');
        if (qualifierIndex < 0) {
            qualifierIndex = firstQualifierIndex(value);
        }

        String numberPart;
        String qualifier = QUALIFIER_RELEASE;
        if (qualifierIndex >= 0) {
            numberPart = cleanNumberPart(value.substring(0, qualifierIndex), true);
            qualifier = cleanQualifier(value.substring(qualifierIndex));
        } else {
            numberPart = cleanNumberPart(value, false);
        }

        List<BigInteger> numbers = parseNumbers(numberPart);
        return new ParsedVersion(numbers, qualifier);
    }

    private static int firstQualifierIndex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isDigit(ch) && ch != '.' && ch != '_') {
                return i;
            }
        }
        return -1;
    }

    private static String cleanNumberPart(String numberPart, boolean beforeQualifier) {
        String value = numberPart == null ? "" : numberPart.trim().replace('_', '.');
        while (beforeQualifier && value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("version 数字段不能为空");
        }
        return value;
    }

    private static List<BigInteger> parseNumbers(String numberPart) {
        String[] parts = numberPart.split("\\.", -1);
        List<BigInteger> numbers = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("version 数字段非法: " + numberPart);
            }
            numbers.add(new BigInteger(part));
        }
        return List.copyOf(numbers);
    }

    private static String cleanQualifier(String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            return QUALIFIER_RELEASE;
        }
        String value = qualifier.trim();
        while (!value.isEmpty() && isQualifierSeparator(value.charAt(0))) {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("version 后缀非法: " + qualifier);
        }
        if (value.equalsIgnoreCase("final") || value.equalsIgnoreCase("ga") || value.equalsIgnoreCase("release")) {
            return QUALIFIER_RELEASE;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean isQualifierSeparator(char ch) {
        return ch == '-' || ch == '.' || ch == '_';
    }

    private static int compareNumbers(List<BigInteger> leftNumbers, List<BigInteger> rightNumbers) {
        int maxSize = Math.max(leftNumbers.size(), rightNumbers.size());
        for (int i = 0; i < maxSize; i++) {
            BigInteger left = i < leftNumbers.size() ? leftNumbers.get(i) : BigInteger.ZERO;
            BigInteger right = i < rightNumbers.size() ? rightNumbers.get(i) : BigInteger.ZERO;
            int compare = left.compareTo(right);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static int compareQualifier(String leftQualifier, String rightQualifier) {
        if (Objects.equals(leftQualifier, rightQualifier)) {
            return 0;
        }

        Qualifier left = Qualifier.parse(leftQualifier);
        Qualifier right = Qualifier.parse(rightQualifier);
        int rankCompare = Integer.compare(left.rank(), right.rank());
        if (rankCompare != 0) {
            return rankCompare;
        }
        int nameCompare = left.name().compareTo(right.name());
        if (nameCompare != 0) {
            return nameCompare;
        }
        return Integer.compare(left.number(), right.number());
    }

    private record ParsedVersion(List<BigInteger> numbers, String qualifier) {
    }

    private record Qualifier(String name, int rank, int number) {

        private static Qualifier parse(String qualifier) {
            if (qualifier == null || qualifier.isEmpty()) {
                return new Qualifier(QUALIFIER_RELEASE, 5, 0);
            }

            String value = qualifier.toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
            String normalized = value.replace(".", "");
            String name = normalized.replaceAll("\\d+$", "");
            String numberText = normalized.substring(name.length());
            int number = numberText.isEmpty() ? 0 : Integer.parseInt(numberText);
            return new Qualifier(name, rankOf(name), number);
        }

        private static int rankOf(String name) {
            return switch (name) {
                case "snapshot", "dev" -> 0;
                case "alpha", "a" -> 1;
                case "beta", "b" -> 2;
                case "milestone", "m" -> 3;
                case "rc", "cr" -> 4;
                case QUALIFIER_RELEASE -> 5;
                case "sp" -> 6;
                default -> UNKNOWN_QUALIFIER_RANK;
            };
        }
    }
}
