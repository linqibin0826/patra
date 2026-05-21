package dev.linqibin.starter.redisson.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LockAcquisitionException} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockAcquisitionException 锁获取失败异常测试")
class LockAcquisitionExceptionTest {

  @Test
  @DisplayName("应该继承自 ApplicationException")
  void shouldExtendApplicationException() {
    LockAcquisitionException exception = new LockAcquisitionException("test:lock", 3000L);

    assertThat(exception).isInstanceOf(ApplicationException.class);
  }

  @Test
  @DisplayName("基本构造函数应该生成正确的错误消息")
  void basicConstructor_ShouldGenerateCorrectMessage() {
    LockAcquisitionException exception = new LockAcquisitionException("test:lock", 3000L);

    assertThat(exception.getMessage()).isEqualTo("无法获取分布式锁: test:lock（等待时间: 3000 ms）");
    assertThat(exception.getErrorCode()).isEqualTo(LockErrorCode.ACQUISITION_FAILED);
  }

  @Test
  @DisplayName("自定义消息构造函数应该包含锁键和等待时间")
  void customMessageConstructor_ShouldIncludeLockKeyAndWaitTime() {
    LockAcquisitionException exception =
        new LockAcquisitionException("业务冲突", "user:123:action", 5000L);

    assertThat(exception.getMessage())
        .isEqualTo("业务冲突 (lockKey: user:123:action, waitTime: 5000 ms)");
    assertThat(exception.getErrorCode()).isEqualTo(LockErrorCode.ACQUISITION_FAILED);
  }

  @Test
  @DisplayName("带 cause 构造函数应该保留根本原因")
  void causeConstructor_ShouldPreserveCause() {
    RuntimeException rootCause = new RuntimeException("Redis 连接超时");
    LockAcquisitionException exception =
        new LockAcquisitionException("payment:lock", 2000L, rootCause);

    assertThat(exception.getMessage()).isEqualTo("无法获取分布式锁: payment:lock（等待时间: 2000 ms）");
    assertThat(exception.getCause()).isEqualTo(rootCause);
    assertThat(exception.getErrorCode()).isEqualTo(LockErrorCode.ACQUISITION_FAILED);
  }

  @Test
  @DisplayName("应该正确获取 ErrorCode")
  void shouldReturnCorrectErrorCode() {
    LockAcquisitionException exception = new LockAcquisitionException("test:lock", 1000L);

    assertThat(exception.getErrorCode().code()).isEqualTo("LOCK-0409");
    assertThat(exception.getErrorCode().httpStatus()).isEqualTo(409);
  }
}
