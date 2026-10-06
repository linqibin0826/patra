package dev.linqibin.starter.core.cqrs.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/// LoggingCommandInterceptor 单元测试。
@DisplayName("LoggingCommandInterceptor 单元测试")
class LoggingCommandInterceptorTest {

  private final LoggingCommandInterceptor interceptor = new LoggingCommandInterceptor();
  private Logger interceptorLogger;
  private ListAppender<ILoggingEvent> logAppender;

  /// 挂上日志收集器。
  @BeforeEach
  void attachLogAppender() {
    interceptorLogger = (Logger) LoggerFactory.getLogger(LoggingCommandInterceptor.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    interceptorLogger.addAppender(logAppender);
  }

  /// 摘掉日志收集器。
  @AfterEach
  void detachLogAppender() {
    interceptorLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("领域异常记 WARN，异常照常抛出")
  void should_log_domain_exception_at_warn() {
    assertThatThrownBy(
            () ->
                interceptor.intercept(
                    new ProbeCommand(),
                    command -> {
                      throw new ProbeDomainException();
                    }))
        .isInstanceOf(ProbeDomainException.class);

    assertThat(failureEvent().getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  @DisplayName("其他异常仍记 ERROR")
  void should_log_unexpected_exception_at_error() {
    assertThatThrownBy(
            () ->
                interceptor.intercept(
                    new ProbeCommand(),
                    command -> {
                      throw new IllegalStateException("意外");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(failureEvent().getLevel()).isEqualTo(Level.ERROR);
  }

  /// 取出「命令失败」那条日志。
  ///
  /// @return 日志事件
  private ILoggingEvent failureEvent() {
    return logAppender.list.stream()
        .filter(event -> event.getFormattedMessage().contains("命令失败"))
        .findFirst()
        .orElseThrow();
  }

  /// 测试用命令。
  private record ProbeCommand() implements Command<Void> {}

  /// 测试用领域异常。
  private static final class ProbeDomainException extends DomainException {

    /// 创建测试异常。
    ProbeDomainException() {
      super("业务上拒绝", StandardErrorTrait.CONFLICT);
    }
  }
}
