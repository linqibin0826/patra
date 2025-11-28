package com.patra.starter.redisson.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * LockContext 单元测试。
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockContext 单元测试")
class LockContextTest {

  @Nested
  @DisplayName("isWatchdogEnabled 方法测试")
  class IsWatchdogEnabledTest {

    @Test
    @DisplayName("当 leaseTime=-1 时，返回 true")
    void shouldReturnTrueWhenLeaseTimeIsMinusOne() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(-1)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      // When & Then
      assertThat(context.isWatchdogEnabled()).isTrue();
    }

    @Test
    @DisplayName("当 leaseTime>0 时，返回 false")
    void shouldReturnFalseWhenLeaseTimeIsPositive() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      // When & Then
      assertThat(context.isWatchdogEnabled()).isFalse();
    }

    @Test
    @DisplayName("当 leaseTime=0 时，返回 false")
    void shouldReturnFalseWhenLeaseTimeIsZero() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(0)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      // When & Then
      assertThat(context.isWatchdogEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("getActualWaitTime 方法测试")
  class GetActualWaitTimeTest {

    @Test
    @DisplayName("当 lockAcquireStartTime=0 时，返回 0")
    void shouldReturnZeroWhenAcquireStartTimeIsZero() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();
      // lockAcquireStartTime 默认为 0

      // When & Then
      assertThat(context.getActualWaitTime()).isZero();
    }

    @Test
    @DisplayName("当 lockAcquiredTime=0 时，返回 0")
    void shouldReturnZeroWhenAcquiredTimeIsZero() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();
      context.markAcquireStart(System.currentTimeMillis());
      // lockAcquiredTime 默认为 0

      // When & Then
      assertThat(context.getActualWaitTime()).isZero();
    }

    @Test
    @DisplayName("正常计算等待时间")
    void shouldCalculateCorrectWaitTime() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      long startTime = 1000L;
      long acquiredTime = 1150L;
      context.markAcquireStart(startTime);
      context.markAcquired(acquiredTime);

      // When
      long actualWaitTime = context.getActualWaitTime();

      // Then
      assertThat(actualWaitTime).isEqualTo(150L);
    }
  }

  @Nested
  @DisplayName("isAcquired 方法测试")
  class IsAcquiredTest {

    @Test
    @DisplayName("当锁未获取时，返回 false")
    void shouldReturnFalseWhenLockNotAcquired() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      // When & Then
      assertThat(context.isAcquired()).isFalse();
    }

    @Test
    @DisplayName("当锁已获取时，返回 true")
    void shouldReturnTrueWhenLockAcquired() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();
      context.markAcquired(System.currentTimeMillis());

      // When & Then
      assertThat(context.isAcquired()).isTrue();
    }
  }

  @Nested
  @DisplayName("markAcquireStart 和 markAcquired 方法测试")
  class MarkMethodsTest {

    @Test
    @DisplayName("markAcquireStart 应正确设置开始时间")
    void shouldSetAcquireStartTime() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      long timestamp = 12345L;

      // When
      context.markAcquireStart(timestamp);

      // Then
      assertThat(context.getLockAcquireStartTime()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("markAcquired 应正确设置获取时间")
    void shouldSetAcquiredTime() {
      // Given
      LockContext context =
          LockContext.builder()
              .lockKey("test:key")
              .lockType(LockType.REENTRANT)
              .waitTime(3000)
              .leaseTime(30000)
              .methodName("testMethod")
              .className("TestClass")
              .build();

      long timestamp = 67890L;

      // When
      context.markAcquired(timestamp);

      // Then
      assertThat(context.getLockAcquiredTime()).isEqualTo(timestamp);
    }
  }

  @Nested
  @DisplayName("Builder 模式测试")
  class BuilderTest {

    @Test
    @DisplayName("应正确构建所有字段")
    void shouldBuildAllFields() {
      // Given & When
      LockContext context =
          LockContext.builder()
              .lockKey("patra:lock:user:123")
              .lockType(LockType.FAIR)
              .waitTime(5000)
              .leaseTime(60000)
              .methodName("updateUser")
              .className("UserService")
              .build();

      // Then
      assertThat(context.getLockKey()).isEqualTo("patra:lock:user:123");
      assertThat(context.getLockType()).isEqualTo(LockType.FAIR);
      assertThat(context.getWaitTime()).isEqualTo(5000);
      assertThat(context.getLeaseTime()).isEqualTo(60000);
      assertThat(context.getMethodName()).isEqualTo("updateUser");
      assertThat(context.getClassName()).isEqualTo("UserService");
    }
  }
}
