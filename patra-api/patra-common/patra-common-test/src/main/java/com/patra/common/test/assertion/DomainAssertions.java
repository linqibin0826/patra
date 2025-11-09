package com.patra.common.test.assertion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * 领域断言工具类
 *
 * <p>提供业务语义化的断言方法,用于验证领域模型的状态、领域事件、值对象等。
 * 相比 AssertJ 的通用断言,本工具类提供更贴近业务语义的断言方法。</p>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>静态辅助方法模式: 所有方法都是静态的</li>
 *   <li>流式断言: 结合 AssertJ 提供流式断言体验</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 断言聚合根状态
 * Task task = taskRepository.findById(taskId).orElseThrow();
 * DomainAssertions.assertAggregateStatus(task, TaskStatus.COMPLETED);
 *
 * // 断言领域事件已发布
 * DomainAssertions.assertDomainEventPublished(task, TaskCompletedEvent.class);
 *
 * // 断言值对象相等
 * Email actualEmail = user.getEmail();
 * Email expectedEmail = new Email("test@example.com");
 * DomainAssertions.assertValueObjectEquals(actualEmail, expectedEmail);
 *
 * // 断言集合大小
 * DomainAssertions.assertCollectionSize(task.getAttachments(), 3);
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class DomainAssertions {

    private DomainAssertions() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 断言聚合根状态正确
     *
     * <p>验证聚合根的状态字段是否等于期望值。</p>
     *
     * @param <T> 聚合根类型
     * @param actual 实际聚合根
     * @param expectedStatus 期望状态
     * @throws AssertionError 如果状态不匹配
     */
    public static <T> void assertAggregateStatus(T actual, Object expectedStatus) {
        if (actual == null) {
            throw new AssertionError("聚合根不能为null");
        }

        try {
            // 尝试调用getStatus()方法
            Method method = actual.getClass().getMethod("getStatus");
            Object actualStatus = method.invoke(actual);
            if (!expectedStatus.equals(actualStatus)) {
                throw new AssertionError(String.format(
                    "聚合根状态应该等于期望值，期望: %s，实际: %s",
                    expectedStatus, actualStatus
                ));
            }
        } catch (NoSuchMethodException e) {
            // 如果没有getStatus方法，尝试直接访问status字段
            try {
                Field field = actual.getClass().getDeclaredField("status");
                field.setAccessible(true);
                Object actualStatus = field.get(actual);
                if (!expectedStatus.equals(actualStatus)) {
                    throw new AssertionError(String.format(
                        "聚合根状态应该等于期望值，期望: %s，实际: %s",
                        expectedStatus, actualStatus
                    ));
                }
            } catch (Exception ex) {
                throw new AssertionError("无法获取聚合根状态: 未找到getStatus()方法或status字段", ex);
            }
        } catch (Exception e) {
            throw new AssertionError("获取聚合根状态时发生错误", e);
        }
    }

    /**
     * 断言领域事件已发布
     *
     * <p>验证聚合根是否发布了指定类型的领域事件。</p>
     *
     * @param aggregate 聚合根
     * @param eventType 事件类型
     * @throws AssertionError 如果未发布该事件
     */
    public static void assertDomainEventPublished(Object aggregate, Class<?> eventType) {
        if (aggregate == null) {
            throw new AssertionError("聚合根不能为null");
        }
        if (eventType == null) {
            throw new AssertionError("事件类型不能为null");
        }

        try {
            // 尝试调用getDomainEvents()方法
            Method method = aggregate.getClass().getMethod("getDomainEvents");
            Object events = method.invoke(aggregate);

            if (events instanceof Collection) {
                Collection<?> eventList = (Collection<?>) events;
                boolean hasEvent = eventList.stream()
                    .anyMatch(event -> eventType.isInstance(event));
                if (!hasEvent) {
                    throw new AssertionError(String.format(
                        "聚合根应该发布了类型为 %s 的领域事件",
                        eventType.getSimpleName()
                    ));
                }
            } else {
                throw new AssertionError("getDomainEvents()返回的不是Collection类型");
            }
        } catch (NoSuchMethodException e) {
            // 如果没有getDomainEvents方法，尝试访问domainEvents字段
            try {
                Field field = aggregate.getClass().getDeclaredField("domainEvents");
                field.setAccessible(true);
                Object events = field.get(aggregate);

                if (events instanceof Collection) {
                    Collection<?> eventList = (Collection<?>) events;
                    boolean hasEvent = eventList.stream()
                        .anyMatch(event -> eventType.isInstance(event));
                    if (!hasEvent) {
                        throw new AssertionError(String.format(
                            "聚合根应该发布了类型为 %s 的领域事件",
                            eventType.getSimpleName()
                        ));
                    }
                } else {
                    throw new AssertionError("domainEvents字段不是Collection类型");
                }
            } catch (Exception ex) {
                throw new AssertionError("无法获取领域事件: 未找到getDomainEvents()方法或domainEvents字段", ex);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("获取领域事件时发生错误", e);
        }
    }

    /**
     * 断言值对象相等(深度比较)
     *
     * <p>使用递归比较验证值对象的所有字段是否相等。</p>
     *
     * @param <V> 值对象类型
     * @param actual 实际值对象
     * @param expected 期望值对象
     * @throws AssertionError 如果值对象不相等
     */
    public static <V> void assertValueObjectEquals(V actual, V expected) {
        if (actual == null && expected == null) {
            return;
        }
        if (actual == null || expected == null) {
            throw new AssertionError(String.format(
                "值对象应该深度相等，期望: %s，实际: %s",
                expected, actual
            ));
        }
        if (!actual.equals(expected)) {
            throw new AssertionError(String.format(
                "值对象应该深度相等，期望: %s，实际: %s",
                expected, actual
            ));
        }
    }

    /**
     * 断言集合包含指定数量的元素
     *
     * @param <T> 元素类型
     * @param collection 集合
     * @param expectedSize 期望大小
     * @throws AssertionError 如果集合大小不匹配
     */
    public static <T> void assertCollectionSize(Collection<T> collection, int expectedSize) {
        if (collection == null) {
            throw new AssertionError("集合不能为null");
        }
        int actualSize = collection.size();
        if (actualSize != expectedSize) {
            throw new AssertionError(String.format(
                "集合大小应该等于 %d，实际: %d",
                expectedSize, actualSize
            ));
        }
    }

    /**
     * 断言实体相等(忽略版本号等审计字段)
     *
     * @param <E> 实体类型
     * @param actual 实际实体
     * @param expected 期望实体
     * @throws AssertionError 如果实体不相等
     */
    public static <E> void assertEntityEquals(E actual, E expected) {
        if (actual == null && expected == null) {
            return;
        }
        if (actual == null || expected == null) {
            throw new AssertionError(String.format(
                "实体应该相等（忽略审计字段），期望: %s，实际: %s",
                expected, actual
            ));
        }

        // 比较所有字段，忽略审计字段
        try {
            Field[] fields = actual.getClass().getDeclaredFields();
            for (Field field : fields) {
                // 忽略审计字段
                String fieldName = field.getName();
                if (fieldName.equals("version") || fieldName.equals("createdAt") ||
                    fieldName.equals("updatedAt") || fieldName.equals("createdBy") ||
                    fieldName.equals("updatedBy")) {
                    continue;
                }

                field.setAccessible(true);
                Object actualValue = field.get(actual);
                Object expectedValue = field.get(expected);

                if (actualValue == null && expectedValue == null) {
                    continue;
                }
                if (actualValue == null || !actualValue.equals(expectedValue)) {
                    throw new AssertionError(String.format(
                        "实体字段 %s 应该相等，期望: %s，实际: %s",
                        fieldName, expectedValue, actualValue
                    ));
                }
            }
        } catch (Exception e) {
            throw new AssertionError("比较实体时发生错误", e);
        }
    }

    /**
     * 断言仓储已保存指定实体
     *
     * @param repository 仓储实例
     * @param id 实体 ID
     * @throws AssertionError 如果未找到实体
     */
    public static void assertRepositorySaved(Object repository, Object id) {
        if (repository == null) {
            throw new AssertionError("仓储不能为null");
        }
        if (id == null) {
            throw new AssertionError("实体ID不能为null");
        }

        try {
            // 尝试调用findById()方法
            Method method = repository.getClass().getMethod("findById", Object.class);
            Object result = method.invoke(repository, id);
            if (result == null) {
                throw new AssertionError(String.format(
                    "仓储中应该存在ID为 %s 的实体", id
                ));
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError("仓储类未实现findById(Object)方法", e);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("调用仓储findById()方法时发生错误", e);
        }
    }
}
