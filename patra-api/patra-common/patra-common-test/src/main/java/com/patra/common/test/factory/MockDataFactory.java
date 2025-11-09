package com.patra.common.test.factory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 数据工厂
 *
 * <p>提供批量生成随机测试数据的功能,支持字符串、数值、日期、枚举等常见类型。
 * 所有方法都是线程安全的,使用 {@link ThreadLocalRandom} 确保并发环境下的正确性。</p>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>静态工厂方法模式: 提供简洁的静态方法调用</li>
 *   <li>工具类模式: 所有方法都是静态的,不允许实例化</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 生成随机字符串
 * String taskId = MockDataFactory.randomUuid();
 * String articleTitle = MockDataFactory.randomString("Article_", 10);
 *
 * // 生成随机数值
 * int priority = MockDataFactory.randomInt(1, 10);
 * long timestamp = MockDataFactory.randomLong(0, System.currentTimeMillis());
 *
 * // 生成随机日期
 * LocalDateTime createdAt = MockDataFactory.randomDateTime(-7); // 7天前
 * LocalDate dueDate = MockDataFactory.randomDate(-30);
 *
 * // 生成随机枚举
 * TaskStatus status = MockDataFactory.randomEnum(TaskStatus.class);
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class MockDataFactory {

    private MockDataFactory() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 生成随机字符串
     *
     * @param prefix 前缀
     * @param length 长度(不包含前缀)
     * @return 随机字符串
     */
    public static String randomString(String prefix, int length) {
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机 UUID
     *
     * @return UUID 字符串
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成随机整数
     *
     * @param min 最小值(包含)
     * @param max 最大值(包含)
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 生成随机长整数
     *
     * @param min 最小值(包含)
     * @param max 最大值(包含)
     * @return 随机长整数
     */
    public static long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * 生成随机日期时间
     *
     * @param daysAgo 距离今天的天数(负数表示过去,正数表示未来)
     * @return LocalDateTime
     */
    public static LocalDateTime randomDateTime(int daysAgo) {
        return LocalDateTime.now().minusDays(Math.abs(daysAgo));
    }

    /**
     * 生成随机日期
     *
     * @param daysAgo 距离今天的天数(负数表示过去,正数表示未来)
     * @return LocalDate
     */
    public static LocalDate randomDate(int daysAgo) {
        return LocalDate.now().minusDays(Math.abs(daysAgo));
    }

    /**
     * 从枚举中随机选择一个值
     *
     * @param <E> 枚举类型
     * @param enumClass 枚举类
     * @return 随机枚举值
     */
    public static <E extends Enum<E>> E randomEnum(Class<E> enumClass) {
        E[] values = enumClass.getEnumConstants();
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("枚举类没有定义任何常量: " + enumClass.getName());
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * 生成随机布尔值
     *
     * @return 随机布尔值
     */
    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 生成随机邮箱地址
     *
     * @return 随机邮箱
     */
    public static String randomEmail() {
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机 URL
     *
     * @return 随机 URL
     */
    public static String randomUrl() {
        throw new UnsupportedOperationException("待实现");
    }
}
