package com.patra.registry.domain.exception;

/**
 * 领域通用参数/状态校验异常（替换零散的 IllegalArgumentException）。
 * <p>
 * 使用场景：领域对象构造 / 不变式校验 / 查询条件值对象参数检查；表示<strong>调用方提供的输入不符合领域约束</strong>，
 * 而非系统内部错误。统一抛出便于：
 * <ul>
 *   <li>网关 / 适配层统一映射为 HTTP 400（或后续可按需区分 422）</li>
 *   <li>日志过滤（可降低告警噪音）</li>
 *   <li>后续追加错误码细分时可集中回溯</li>
 * </ul>
 * 约束：不携带业务错误码（避免 domain 依赖 api）；错误码映射在 boot 层完成。
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }

    public DomainValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 快捷工厂：当 condition 为 false 时抛出。
     * @param condition 条件
     * @param message 失败消息
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainValidationException(message);
        }
    }

    /**
     * 断言字符串非 null 且非空白。
     * @param value 被检查值
     * @param field 字段名（用于拼接消息）
     */
    public static String notBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainValidationException(field + " cannot be blank");
        }
        return value.trim();
    }

    /**
     * 断言对象非 null。
     * @param obj 对象
     * @param field 字段名
     */
    public static <T> T nonNull(T obj, String field) {
        if (obj == null) {
            throw new DomainValidationException(field + " cannot be null");
        }
        return obj;
    }

    /**
     * 断言数字为正数（>0）。
     * @param number 数值
     * @param field 字段名
     */
    public static long positive(Long number, String field) {
        if (number == null || number <= 0) {
            throw new DomainValidationException(field + " must be positive");
        }
        return number;
    }

    /**
     * 断言整数非负（>=0）。
     */
    public static int nonNegative(Integer number, String field) {
        if (number == null || number < 0) {
            throw new DomainValidationException(field + " cannot be negative");
        }
        return number;
    }

    /**
     * 断言集合或数组非空（仅检查 null / length==0，不做深度）。
     */
    public static <T> T[] notEmpty(T[] arr, String field) {
        if (arr == null || arr.length == 0) {
            throw new DomainValidationException(field + " cannot be empty");
        }
        return arr;
    }

    /**
     * 断言数值在闭区间 [min, max] 内。
     */
    public static long withinRange(long value, long minInclusive, long maxInclusive, String field) {
        if (value < minInclusive || value > maxInclusive) {
            throw new DomainValidationException(field + " must be between " + minInclusive + " and " + maxInclusive);
        }
        return value;
    }
}
