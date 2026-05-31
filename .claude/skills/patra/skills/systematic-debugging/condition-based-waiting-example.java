/*
 * 基于条件的等待——轻量级 Java 工具
 *
 * 何时用本工具而非 Awaitility：
 *   - 不想引入 org.awaitility:awaitility 依赖（极少数情况）
 *   - 需要极简的内嵌实现
 *
 * 通常情况下直接用 Awaitility（org.awaitility:awaitility:4.2.x）：
 *   Awaitility.await()
 *       .atMost(Duration.ofSeconds(5))
 *       .pollInterval(Duration.ofMillis(10))
 *       .untilAsserted(() -> assertThat(...).isEqualTo(...));
 *
 * 本文件只是为了说明"基于条件等待"的极简内核：
 *   1. 反复执行 condition，直到返回 true 或超时
 *   2. 轮询间隔保持适度（10ms），避免烧 CPU
 *   3. 超时时抛出带 description 的清晰异常
 */

package patra.skill.examples;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class WaitFor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_POLL = Duration.ofMillis(10);

    private WaitFor() {}

    /**
     * 等待布尔条件为 true。
     *
     * @param condition   返回 true 时停止
     * @param description 用于超时异常的可读描述
     * @param timeout     最大等待时长
     * @throws ConditionTimeoutException 超时抛出
     */
    public static void condition(BooleanSupplier condition,
                                 String description,
                                 Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(DEFAULT_POLL);
        }
        throw new ConditionTimeoutException(
            "Timeout waiting for " + description + " after " + timeout.toMillis() + "ms");
    }

    public static void condition(BooleanSupplier condition, String description) {
        condition(condition, description, DEFAULT_TIMEOUT);
    }

    /**
     * 等待 supplier 返回非 null / 非空 Optional 的值。
     */
    public static <T> T value(Supplier<Optional<T>> supplier,
                              String description,
                              Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> result = supplier.get();
            if (result.isPresent()) {
                return result.get();
            }
            sleep(DEFAULT_POLL);
        }
        throw new ConditionTimeoutException(
            "Timeout waiting for " + description + " after " + timeout.toMillis() + "ms");
    }

    /**
     * 等待集合达到指定大小或更多元素，返回当时快照。
     */
    public static <T> List<T> collectionSize(Supplier<List<T>> supplier,
                                             int minSize,
                                             String description,
                                             Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<T> snapshot = supplier.get();
            if (snapshot.size() >= minSize) {
                return snapshot;
            }
            sleep(DEFAULT_POLL);
        }
        throw new ConditionTimeoutException(
            "Timeout waiting for " + description + " (≥" + minSize + " items) after "
                + timeout.toMillis() + "ms");
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis(), d.toNanosPart() % 1_000_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConditionTimeoutException("Interrupted while polling", e);
        }
    }

    public static final class ConditionTimeoutException extends RuntimeException {
        public ConditionTimeoutException(String message) {
            super(message);
        }

        public ConditionTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

/* ============================================================
 *  使用示例（从实际调试场景中提炼）
 * ============================================================
 *
 * BEFORE（不稳定）：
 *   publisher.publish(orderPlacedEvent);
 *   Thread.sleep(300);                          // 希望 handler 在 300ms 内消费
 *   assertThat(handler.received()).isNotNull(); // 在慢机 / CI 上随机失败
 *
 * AFTER（基于条件）：
 *   publisher.publish(orderPlacedEvent);
 *   WaitFor.condition(
 *       () -> handler.received() != null,
 *       "OrderPlacedEvent 被 handler 消费");
 *   assertThat(handler.received()).isEqualTo(expected);
 *
 * AFTER（Awaitility 版，更推荐）：
 *   publisher.publish(orderPlacedEvent);
 *   Awaitility.await()
 *       .atMost(Duration.ofSeconds(5))
 *       .untilAsserted(() ->
 *           assertThat(handler.received()).isEqualTo(expected));
 *   // ↑ untilAsserted 失败时直接抛出 AssertJ 异常，错误信息精确
 *
 * 等待 N 个事件被消费：
 *   var events = WaitFor.collectionSize(
 *       () -> handler.allEvents(),
 *       3,
 *       "至少消费 3 个 OutboxRelayed 事件",
 *       Duration.ofSeconds(10));
 *
 * 等待外部数据库行出现（@DataJpaTest + TestContainers）：
 *   var entity = WaitFor.value(
 *       () -> dao.findByCode("PUBMED"),
 *       "PUBMED ProvenanceEntity 持久化完成",
 *       Duration.ofSeconds(3));
 *
 *  迁移效果：消除随机失败、平均测试耗时下降（条件满足即返回）、
 *           错误信息更清晰（带 description）。
 */
