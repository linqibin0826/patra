package com.patra.starter.redisson.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LockMetricsRecorder} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockMetricsRecorder 指标记录器测试")
class LockMetricsRecorderTest {

    private MeterRegistry meterRegistry;
    private LockMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        recorder = new LockMetricsRecorder(meterRegistry);
    }

    // ==================== 指标记录测试 ====================

    @Nested
    @DisplayName("onLockAcquired - 锁获取成功指标")
    class OnLockAcquiredTest {

        @Test
        @DisplayName("应该正确记录锁获取成功计数")
        void shouldRecordAcquiredCounter() {
            recorder.onLockAcquired("patra:lock:user:123", "REENTRANT", 50);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", "user")
                .tag("lock_type", "REENTRANT")
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("应该正确记录锁等待时间")
        void shouldRecordWaitTime() {
            recorder.onLockAcquired("patra:lock:order:456", "FAIR", 100);

            Timer timer = meterRegistry.find("patra.redisson.lock.wait_time")
                .tag("key_pattern", "order")
                .tag("lock_type", "FAIR")
                .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(100);
        }

        @Test
        @DisplayName("多次获取锁应该累加计数")
        void shouldAccumulateCount() {
            recorder.onLockAcquired("patra:lock:user:1", "REENTRANT", 10);
            recorder.onLockAcquired("patra:lock:user:2", "REENTRANT", 20);
            recorder.onLockAcquired("patra:lock:user:3", "REENTRANT", 30);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", "user")
                .tag("lock_type", "REENTRANT")
                .counter();

            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("onLockFailed - 锁获取失败指标")
    class OnLockFailedTest {

        @Test
        @DisplayName("应该正确记录锁获取失败计数")
        void shouldRecordFailedCounter() {
            recorder.onLockFailed("patra:lock:user:123", "REENTRANT", "timeout");

            Counter counter = meterRegistry.find("patra.redisson.lock.failed")
                .tag("key_pattern", "user")
                .tag("lock_type", "REENTRANT")
                .tag("reason", "timeout")
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @ParameterizedTest
        @ValueSource(strings = {"timeout", "interrupted", "infrastructure_error"})
        @DisplayName("应该正确记录不同的失败原因")
        void shouldRecordDifferentReasons(String reason) {
            recorder.onLockFailed("patra:lock:task:1", "FAIR", reason);

            Counter counter = meterRegistry.find("patra.redisson.lock.failed")
                .tag("reason", reason)
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("onLockReleased - 锁释放指标")
    class OnLockReleasedTest {

        @Test
        @DisplayName("应该正确记录锁持有时间")
        void shouldRecordHoldTime() {
            recorder.onLockReleased("patra:lock:order:789", "WRITE", 500);

            Timer timer = meterRegistry.find("patra.redisson.lock.hold_time")
                .tag("key_pattern", "order")
                .tag("lock_type", "WRITE")
                .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(500);
        }
    }

    // ==================== extractKeyPattern 测试 ====================

    @Nested
    @DisplayName("extractKeyPattern - 锁键模式提取")
    class ExtractKeyPatternTest {

        @ParameterizedTest
        @CsvSource({
            // 标准格式
            "patra:lock:user:123, user",
            "patra:lock:order:456, order",
            "catalog:lock:mesh-import:2024, mesh-import",
            "ingest:lock:harvest:provenance:20241125, harvest.provenance",
            // 嵌套模式
            "patra:lock:order:456:item:789, order.item",
            "patra:lock:user:123:profile:settings, user.profile.settings",
            // 无前缀
            "user:123, user",
            "order:456:item:789, order.item"
        })
        @DisplayName("应该正确提取锁键模式")
        void shouldExtractPattern(String lockKey, String expectedPattern) {
            recorder.onLockAcquired(lockKey, "REENTRANT", 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", expectedPattern)
                .counter();

            assertThat(counter)
                .as("锁键 '%s' 应该提取模式 '%s'", lockKey, expectedPattern)
                .isNotNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 或空字符串应该返回 'unknown'")
        void shouldReturnUnknownForNullOrEmpty(String lockKey) {
            recorder.onLockAcquired(lockKey, "REENTRANT", 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", "unknown")
                .counter();

            assertThat(counter).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "patra:lock:123",           // 全数字
            "patra:lock:123:456:789"    // 全数字
        })
        @DisplayName("全数字锁键应该返回 'unknown'")
        void shouldReturnUnknownForAllNumeric(String lockKey) {
            recorder.onLockAcquired(lockKey, "REENTRANT", 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", "unknown")
                .counter();

            assertThat(counter).isNotNull();
        }
    }

    // ==================== isStaticPart 测试（通过 extractKeyPattern 间接验证） ====================

    @Nested
    @DisplayName("isStaticPart - 静态部分判断（通过锁键提取间接测试）")
    class IsStaticPartTest {

        @ParameterizedTest
        @CsvSource({
            // 纯数字应该被过滤
            "patra:lock:user:123, user",
            "patra:lock:order:999999, order",
            // UUID 应该被过滤
            "patra:lock:task:550e8400-e29b-41d4-a716-446655440000, task",
            // 日期格式应该被过滤（YYYY-MM-DD）
            "patra:lock:report:2024-01-15, report",
            "patra:lock:report:2024-12, report",
            // 日期格式应该被过滤（YYYYMMDD）
            "patra:lock:daily:20241125, daily",
            // 混合场景
            "patra:lock:user:123:order:456, user.order",
            "patra:lock:task:550e8400-e29b-41d4-a716-446655440000:status, task.status"
        })
        @DisplayName("应该正确过滤动态部分（数字、UUID、日期）")
        void shouldFilterDynamicParts(String lockKey, String expectedPattern) {
            recorder.onLockAcquired(lockKey, "REENTRANT", 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", expectedPattern)
                .counter();

            assertThat(counter)
                .as("锁键 '%s' 应该提取模式 '%s'（过滤动态部分）", lockKey, expectedPattern)
                .isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "patra:lock:user-profile:123",      // 静态部分带连字符
            "patra:lock:order_item:456",        // 静态部分带下划线
            "patra:lock:meshImport:2024"        // 驼峰命名
        })
        @DisplayName("应该保留合法的静态部分")
        void shouldKeepValidStaticParts(String lockKey) {
            recorder.onLockAcquired(lockKey, "REENTRANT", 10);

            // 验证指标被记录（模式不是 unknown）
            Counter unknownCounter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("key_pattern", "unknown")
                .counter();

            // unknown 计数器不存在或为 0，说明模式被正确提取
            if (unknownCounter != null) {
                assertThat(unknownCounter.count()).isEqualTo(0);
            }
        }
    }

    // ==================== 锁类型标签测试 ====================

    @Nested
    @DisplayName("lock_type 标签测试")
    class LockTypeTagTest {

        @ParameterizedTest
        @ValueSource(strings = {"REENTRANT", "FAIR", "READ", "WRITE"})
        @DisplayName("应该正确记录不同的锁类型")
        void shouldRecordDifferentLockTypes(String lockType) {
            recorder.onLockAcquired("patra:lock:test:1", lockType, 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired")
                .tag("lock_type", lockType)
                .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ==================== 指标描述测试 ====================

    @Nested
    @DisplayName("指标元数据测试")
    class MetricMetadataTest {

        @Test
        @DisplayName("锁获取成功指标应该有描述")
        void acquiredCounterShouldHaveDescription() {
            recorder.onLockAcquired("patra:lock:test:1", "REENTRANT", 10);

            Counter counter = meterRegistry.find("patra.redisson.lock.acquired").counter();

            assertThat(counter).isNotNull();
            assertThat(counter.getId().getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("锁获取失败指标应该有描述")
        void failedCounterShouldHaveDescription() {
            recorder.onLockFailed("patra:lock:test:1", "REENTRANT", "timeout");

            Counter counter = meterRegistry.find("patra.redisson.lock.failed").counter();

            assertThat(counter).isNotNull();
            assertThat(counter.getId().getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("锁等待时间指标应该有描述")
        void waitTimeTimerShouldHaveDescription() {
            recorder.onLockAcquired("patra:lock:test:1", "REENTRANT", 10);

            Timer timer = meterRegistry.find("patra.redisson.lock.wait_time").timer();

            assertThat(timer).isNotNull();
            assertThat(timer.getId().getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("锁持有时间指标应该有描述")
        void holdTimeTimerShouldHaveDescription() {
            recorder.onLockReleased("patra:lock:test:1", "REENTRANT", 100);

            Timer timer = meterRegistry.find("patra.redisson.lock.hold_time").timer();

            assertThat(timer).isNotNull();
            assertThat(timer.getId().getDescription()).isNotBlank();
        }
    }
}
