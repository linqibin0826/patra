package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.exception.LockAcquisitionException;
import com.patra.starter.redisson.exception.LockInfrastructureException;
import com.patra.starter.redisson.listener.LockLoggingRecorder;
import com.patra.starter.redisson.listener.LockMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * {@link LockExecutor} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LockExecutor 锁执行器测试")
class LockExecutorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private RReadWriteLock readWriteLock;

    @Mock
    private LockMetricsRecorder metricsRecorder;

    @Mock
    private LockLoggingRecorder loggingRecorder;

    private LockExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LockExecutor(redissonClient, metricsRecorder, loggingRecorder);
    }

    // ==================== 锁获取成功测试 ====================

    @Nested
    @DisplayName("锁获取成功场景")
    class LockAcquiredSuccessTest {

        @Test
        @DisplayName("成功获取锁时应该记录指标和日志")
        void shouldRecordMetricsAndLogsOnSuccess() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:user:123", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:123")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            String result = executor.execute(context, () -> "success");

            // Then
            assertThat(result).isEqualTo("success");

            // 验证指标记录
            verify(metricsRecorder).onLockAcquired(
                eq("patra:lock:user:123"),
                eq("REENTRANT"),
                anyLong()
            );

            // 验证日志记录
            verify(loggingRecorder).onLockAcquired(
                eq("patra:lock:user:123"),
                anyLong()
            );

            // 验证锁释放时记录
            verify(metricsRecorder).onLockReleased(
                eq("patra:lock:user:123"),
                eq("REENTRANT"),
                anyLong()
            );
            verify(loggingRecorder).onLockReleased(
                eq("patra:lock:user:123"),
                anyLong()
            );
        }

        @Test
        @DisplayName("使用看门狗模式时应该正确获取锁")
        void shouldAcquireLockWithWatchdog() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:task:1", LockType.REENTRANT, 1000, -1);
            given(redissonClient.getLock("patra:lock:task:1")).willReturn(rLock);
            given(rLock.tryLock(1000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            executor.execute(context, () -> "done");

            // Then
            verify(rLock).tryLock(1000, TimeUnit.MILLISECONDS);
            verify(metricsRecorder).onLockAcquired(anyString(), eq("REENTRANT"), anyLong());
        }

        @Test
        @DisplayName("不同锁类型应该获取对应的锁实例")
        void shouldGetCorrectLockTypeInstance() throws InterruptedException {
            // FAIR 锁
            LockContext fairContext = createContext("patra:lock:queue:1", LockType.FAIR, 1000, 30000);
            given(redissonClient.getFairLock("patra:lock:queue:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            executor.execute(fairContext, () -> "done");

            verify(redissonClient).getFairLock("patra:lock:queue:1");
        }

        @Test
        @DisplayName("READ 锁应该获取读写锁的读锁")
        void shouldGetReadLock() throws InterruptedException {
            // Given
            LockContext readContext = createContext("patra:lock:config:1", LockType.READ, 1000, 30000);
            given(redissonClient.getReadWriteLock("patra:lock:config:1")).willReturn(readWriteLock);
            given(readWriteLock.readLock()).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            executor.execute(readContext, () -> "read");

            // Then
            verify(readWriteLock).readLock();
            verify(metricsRecorder).onLockAcquired(anyString(), eq("READ"), anyLong());
        }

        @Test
        @DisplayName("WRITE 锁应该获取读写锁的写锁")
        void shouldGetWriteLock() throws InterruptedException {
            // Given
            LockContext writeContext = createContext("patra:lock:config:1", LockType.WRITE, 1000, 30000);
            given(redissonClient.getReadWriteLock("patra:lock:config:1")).willReturn(readWriteLock);
            given(readWriteLock.writeLock()).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            executor.execute(writeContext, () -> "write");

            // Then
            verify(readWriteLock).writeLock();
            verify(metricsRecorder).onLockAcquired(anyString(), eq("WRITE"), anyLong());
        }
    }

    // ==================== 锁获取失败测试 ====================

    @Nested
    @DisplayName("锁获取失败场景")
    class LockAcquiredFailedTest {

        @Test
        @DisplayName("获取锁超时应该记录失败指标并抛出异常")
        void shouldRecordFailedMetricsOnTimeout() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:user:123", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:123")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> executor.execute(context, () -> "never"))
                .isInstanceOf(LockAcquisitionException.class);

            // 验证失败指标记录
            verify(metricsRecorder).onLockFailed(
                eq("patra:lock:user:123"),
                eq("REENTRANT"),
                eq("timeout")
            );
            verify(loggingRecorder).onLockFailed(
                eq("patra:lock:user:123"),
                eq("timeout")
            );

            // 不应该记录成功指标
            verify(metricsRecorder, never()).onLockAcquired(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("获取锁被中断应该记录失败指标并抛出异常")
        void shouldRecordFailedMetricsOnInterrupted() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:task:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:task:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willThrow(new InterruptedException());

            // When & Then
            assertThatThrownBy(() -> executor.execute(context, () -> "never"))
                .isInstanceOf(LockInfrastructureException.class);

            verify(metricsRecorder).onLockFailed(
                eq("patra:lock:task:1"),
                eq("REENTRANT"),
                eq("interrupted")
            );
        }

        @Test
        @DisplayName("Redis 基础设施错误应该记录失败指标")
        void shouldRecordFailedMetricsOnInfrastructureError() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:task:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:task:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS))
                .willThrow(new RuntimeException("Redis connection failed"));

            // When & Then
            assertThatThrownBy(() -> executor.execute(context, () -> "never"))
                .isInstanceOf(LockInfrastructureException.class);

            verify(metricsRecorder).onLockFailed(
                eq("patra:lock:task:1"),
                eq("REENTRANT"),
                eq("infrastructure_error")
            );
        }
    }

    // ==================== Recorder 为 null 的场景 ====================

    @Nested
    @DisplayName("Recorder 为 null 的场景")
    class NullRecorderTest {

        @Test
        @DisplayName("MetricsRecorder 为 null 时不应该抛出异常")
        void shouldNotThrowWhenMetricsRecorderIsNull() throws InterruptedException {
            // Given
            LockExecutor executorWithNullMetrics = new LockExecutor(redissonClient, null, loggingRecorder);
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            String result = executorWithNullMetrics.execute(context, () -> "success");

            // Then
            assertThat(result).isEqualTo("success");
            verify(loggingRecorder).onLockAcquired(anyString(), anyLong());
        }

        @Test
        @DisplayName("LoggingRecorder 为 null 时不应该抛出异常")
        void shouldNotThrowWhenLoggingRecorderIsNull() throws InterruptedException {
            // Given
            LockExecutor executorWithNullLogging = new LockExecutor(redissonClient, metricsRecorder, null);
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            String result = executorWithNullLogging.execute(context, () -> "success");

            // Then
            assertThat(result).isEqualTo("success");
            verify(metricsRecorder).onLockAcquired(anyString(), eq("REENTRANT"), anyLong());
        }

        @Test
        @DisplayName("两个 Recorder 都为 null 时不应该抛出异常")
        void shouldNotThrowWhenBothRecordersAreNull() throws InterruptedException {
            // Given
            LockExecutor executorWithNullRecorders = new LockExecutor(redissonClient, null, null);
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When
            String result = executorWithNullRecorders.execute(context, () -> "success");

            // Then
            assertThat(result).isEqualTo("success");
        }
    }

    // ==================== 锁释放场景 ====================

    @Nested
    @DisplayName("锁释放场景")
    class LockReleaseTest {

        @Test
        @DisplayName("锁不由当前线程持有时不应该尝试释放")
        void shouldNotReleaseWhenNotHeldByCurrentThread() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(false);

            // When
            executor.execute(context, () -> "success");

            // Then
            verify(rLock, never()).unlock();
            // 不应该记录锁释放指标
            verify(metricsRecorder, never()).onLockReleased(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("释放锁异常不应该影响业务结果")
        void shouldNotAffectBusinessResultWhenReleaseThrows() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            doThrow(new RuntimeException("unlock failed")).when(rLock).unlock();

            // When
            String result = executor.execute(context, () -> "success");

            // Then - 业务结果不受影响
            assertThat(result).isEqualTo("success");
        }
    }

    // ==================== 业务逻辑异常测试 ====================

    @Nested
    @DisplayName("业务逻辑异常场景")
    class BusinessLogicExceptionTest {

        @Test
        @DisplayName("业务逻辑抛出异常时应该正常释放锁并记录指标")
        void shouldReleaseLockWhenBusinessThrows() throws InterruptedException {
            // Given
            LockContext context = createContext("patra:lock:user:1", LockType.REENTRANT, 1000, 30000);
            given(redissonClient.getLock("patra:lock:user:1")).willReturn(rLock);
            given(rLock.tryLock(1000, 30000, TimeUnit.MILLISECONDS)).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> executor.execute(context, () -> {
                throw new RuntimeException("business error");
            })).isInstanceOf(RuntimeException.class)
               .hasMessage("business error");

            // 验证锁被释放
            verify(rLock).unlock();

            // 验证成功指标被记录（获取锁成功了）
            verify(metricsRecorder).onLockAcquired(anyString(), eq("REENTRANT"), anyLong());

            // 验证释放指标被记录
            verify(metricsRecorder).onLockReleased(anyString(), eq("REENTRANT"), anyLong());
        }
    }

    // ==================== 辅助方法 ====================

    private LockContext createContext(String lockKey, LockType lockType, long waitTime, long leaseTime) {
        return LockContext.builder()
            .lockKey(lockKey)
            .lockType(lockType)
            .waitTime(waitTime)
            .leaseTime(leaseTime)
            .methodName("testMethod")
            .className("TestClass")
            .build();
    }
}
