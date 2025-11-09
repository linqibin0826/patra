package com.patra.common.test.assertion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;

/**
 * 通用断言辅助工具类
 *
 * <p>提供通用的断言辅助方法,补充 AssertJ 的功能,简化常见的断言场景。</p>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>静态辅助方法模式: 所有方法都是静态的</li>
 *   <li>委托模式: 提供业务语义化的断言方法</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 断言字符串非空且长度符合预期
 * AssertionHelper.assertStringNotBlankAndLength(user.getName(), 1, 50);
 *
 * // 断言日期在指定范围内
 * LocalDateTime now = LocalDateTime.now();
 * AssertionHelper.assertDateTimeBetween(task.getCreatedAt(),
 *     now.minusMinutes(5), now);
 *
 * // 断言集合非空且包含指定元素
 * AssertionHelper.assertCollectionContains(users, expectedUser);
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class AssertionHelper {

    private AssertionHelper() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 断言字符串非空且长度在指定范围内
     *
     * @param actual 实际字符串
     * @param minLength 最小长度(包含)
     * @param maxLength 最大长度(包含)
     * @throws AssertionError 如果字符串为空或长度不符合
     */
    public static void assertStringNotBlankAndLength(String actual, int minLength, int maxLength) {
        if (actual == null) {
            throw new AssertionError("字符串不能为null");
        }
        if (actual.isBlank()) {
            throw new AssertionError("字符串不能为空白");
        }
        int length = actual.length();
        if (length < minLength || length > maxLength) {
            throw new AssertionError(String.format(
                "字符串长度应该在 [%d, %d] 范围内，实际长度: %d",
                minLength, maxLength, length
            ));
        }
    }

    /**
     * 断言日期时间在指定范围内
     *
     * @param actual 实际日期时间
     * @param start 开始时间(包含)
     * @param end 结束时间(包含)
     * @throws AssertionError 如果日期时间不在范围内
     */
    public static void assertDateTimeBetween(LocalDateTime actual, LocalDateTime start, LocalDateTime end) {
        if (actual == null) {
            throw new AssertionError("日期时间不能为null");
        }
        if (start == null) {
            throw new AssertionError("开始时间不能为null");
        }
        if (end == null) {
            throw new AssertionError("结束时间不能为null");
        }
        if (actual.isBefore(start) || actual.isAfter(end)) {
            throw new AssertionError(String.format(
                "日期时间应该在 [%s, %s] 范围内，实际: %s",
                start, end, actual
            ));
        }
    }

    /**
     * 断言日期在指定范围内
     *
     * @param actual 实际日期
     * @param start 开始日期(包含)
     * @param end 结束日期(包含)
     * @throws AssertionError 如果日期不在范围内
     */
    public static void assertDateBetween(LocalDate actual, LocalDate start, LocalDate end) {
        if (actual == null) {
            throw new AssertionError("日期不能为null");
        }
        if (start == null) {
            throw new AssertionError("开始日期不能为null");
        }
        if (end == null) {
            throw new AssertionError("结束日期不能为null");
        }
        if (actual.isBefore(start) || actual.isAfter(end)) {
            throw new AssertionError(String.format(
                "日期应该在 [%s, %s] 范围内，实际: %s",
                start, end, actual
            ));
        }
    }

    /**
     * 断言集合包含指定元素
     *
     * @param <T> 元素类型
     * @param collection 集合
     * @param element 元素
     * @throws AssertionError 如果集合不包含该元素
     */
    public static <T> void assertCollectionContains(Iterable<T> collection, T element) {
        if (collection == null) {
            throw new AssertionError("集合不能为null");
        }

        boolean found = false;
        Iterator<T> iterator = collection.iterator();
        while (iterator.hasNext()) {
            T item = iterator.next();
            if (element == null && item == null) {
                found = true;
                break;
            }
            if (element != null && element.equals(item)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new AssertionError(String.format(
                "集合应该包含元素: %s", element
            ));
        }
    }

    /**
     * 断言数值在指定范围内
     *
     * @param actual 实际数值
     * @param min 最小值(包含)
     * @param max 最大值(包含)
     * @throws AssertionError 如果数值不在范围内
     */
    public static void assertNumberBetween(Number actual, Number min, Number max) {
        if (actual == null) {
            throw new AssertionError("数值不能为null");
        }
        if (min == null) {
            throw new AssertionError("最小值不能为null");
        }
        if (max == null) {
            throw new AssertionError("最大值不能为null");
        }

        double actualValue = actual.doubleValue();
        double minValue = min.doubleValue();
        double maxValue = max.doubleValue();

        if (actualValue < minValue || actualValue > maxValue) {
            throw new AssertionError(String.format(
                "数值应该在 [%s, %s] 范围内，实际: %s",
                min, max, actual
            ));
        }
    }
}
