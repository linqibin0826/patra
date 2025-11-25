package com.patra.starter.redisson.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.patra.starter.redisson.config.RedissonProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LockLoggingRecorder 单元测试。
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockLoggingRecorder 单元测试")
class LockLoggingRecorderTest {

    private LockLoggingRecorder recorder;
    private RedissonProperties properties;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        properties = new RedissonProperties();

        // 配置日志捕获
        logger = (Logger) LoggerFactory.getLogger(LockLoggingRecorder.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        // 设置日志级别为 DEBUG 以捕获所有日志
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Nested
    @DisplayName("onLockAcquired 方法测试")
    class OnLockAcquiredTest {

        @Test
        @DisplayName("当日志级别为 DEBUG 时，应使用 DEBUG 级别记录")
        void shouldLogAtDebugLevelWhenLogLevelIsDebug() {
            // Given
            properties.getObservability().setLogLevel("DEBUG");
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockAcquired("patra:lock:user:123", 150);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logs.get(0).getFormattedMessage())
                .contains("成功获取分布式锁")
                .contains("patra:lock:user:123")
                .contains("150ms");
        }

        @Test
        @DisplayName("当日志级别为 INFO 时，应使用 INFO 级别记录")
        void shouldLogAtInfoLevelWhenLogLevelIsInfo() {
            // Given
            properties.getObservability().setLogLevel("INFO");
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockAcquired("patra:lock:order:456", 200);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.get(0).getFormattedMessage())
                .contains("成功获取分布式锁")
                .contains("patra:lock:order:456")
                .contains("200ms");
        }

        @Test
        @DisplayName("当日志级别为小写 info 时，应使用 INFO 级别记录")
        void shouldLogAtInfoLevelWhenLogLevelIsLowercaseInfo() {
            // Given
            properties.getObservability().setLogLevel("info");
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockAcquired("patra:lock:task:789", 100);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.INFO);
        }

        @Test
        @DisplayName("当日志级别为其他值时，应使用 DEBUG 级别记录")
        void shouldLogAtDebugLevelWhenLogLevelIsOther() {
            // Given
            properties.getObservability().setLogLevel("WARN");
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockAcquired("patra:lock:item:111", 50);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.DEBUG);
        }
    }

    @Nested
    @DisplayName("onLockFailed 方法测试")
    class OnLockFailedTest {

        @Test
        @DisplayName("应使用 WARN 级别记录锁获取失败")
        void shouldLogAtWarnLevel() {
            // Given
            properties.getObservability().setLogLevel("DEBUG");
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockFailed("patra:lock:user:123", "timeout");

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(logs.get(0).getFormattedMessage())
                .contains("获取分布式锁失败")
                .contains("patra:lock:user:123")
                .contains("timeout");
        }

        @Test
        @DisplayName("应正确记录不同的失败原因")
        void shouldLogDifferentReasons() {
            // Given
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockFailed("patra:lock:order:456", "interrupted");

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs.get(0).getFormattedMessage()).contains("interrupted");
        }
    }

    @Nested
    @DisplayName("onLockReleased 方法测试")
    class OnLockReleasedTest {

        @Test
        @DisplayName("应使用 DEBUG 级别记录锁释放")
        void shouldLogAtDebugLevel() {
            // Given
            recorder = new LockLoggingRecorder(properties);

            // When
            recorder.onLockReleased("patra:lock:user:123", 5000);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logs.get(0).getFormattedMessage())
                .contains("释放分布式锁")
                .contains("patra:lock:user:123")
                .contains("5000ms");
        }
    }

    @Nested
    @DisplayName("onLockError 方法测试")
    class OnLockErrorTest {

        @Test
        @DisplayName("应使用 ERROR 级别记录锁操作错误")
        void shouldLogAtErrorLevel() {
            // Given
            recorder = new LockLoggingRecorder(properties);
            RuntimeException exception = new RuntimeException("Redis connection failed");

            // When
            recorder.onLockError("patra:lock:user:123", "锁释放失败", exception);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(logs.get(0).getFormattedMessage())
                .contains("分布式锁操作错误")
                .contains("patra:lock:user:123")
                .contains("锁释放失败");
            assertThat(logs.get(0).getThrowableProxy().getMessage())
                .isEqualTo("Redis connection failed");
        }

        @Test
        @DisplayName("应正确记录异常信息")
        void shouldLogExceptionDetails() {
            // Given
            recorder = new LockLoggingRecorder(properties);
            IllegalStateException exception = new IllegalStateException("Lock not held by current thread");

            // When
            recorder.onLockError("patra:lock:order:456", "非法释放", exception);

            // Then
            List<ILoggingEvent> logs = listAppender.list;
            assertThat(logs.get(0).getThrowableProxy()).isNotNull();
            assertThat(logs.get(0).getThrowableProxy().getClassName())
                .isEqualTo("java.lang.IllegalStateException");
        }
    }
}
