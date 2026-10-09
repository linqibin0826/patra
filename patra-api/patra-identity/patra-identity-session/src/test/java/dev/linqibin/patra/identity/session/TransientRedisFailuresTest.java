package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.trait.StandardErrorTrait;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandInterruptedException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisLoadingException;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.RedisReadOnlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/// TransientRedisFailures 单元测试。
@DisplayName("TransientRedisFailures 单元测试")
class TransientRedisFailuresTest {

  @Test
  @DisplayName("连不上和超时是暂时失败")
  void should_treat_connection_failure_and_timeout_as_transient() {
    assertThat(TransientRedisFailures.isTransient(new RedisConnectionFailureException("refused")))
        .isTrue();
    assertThat(TransientRedisFailures.isTransient(new QueryTimeoutException("timeout"))).isTrue();
  }

  @Test
  @DisplayName("LOADING、READONLY、BUSY、MASTERDOWN 是暂时失败")
  void should_treat_transient_server_states_as_transient() {
    assertThat(wrapped(new RedisLoadingException("LOADING Redis is loading the dataset in memory")))
        .isTrue();
    assertThat(
            wrapped(
                new RedisReadOnlyException(
                    "READONLY You can't write against a read only replica.")))
        .isTrue();
    assertThat(wrapped(new RedisBusyException("BUSY Redis is busy running a script."))).isTrue();
    assertThat(wrapped(new RedisCommandExecutionException("MASTERDOWN Link with MASTER is down")))
        .isTrue();
  }

  @Test
  @DisplayName("脚本错误、WRONGTYPE、NOAUTH、没有原因的系统异常、其他异常都不是暂时失败")
  void should_not_treat_defects_as_transient() {
    assertThat(wrapped(new RedisNoScriptException("NOSCRIPT No matching script."))).isFalse();
    assertThat(wrapped(new RedisCommandExecutionException("WRONGTYPE Operation against a key")))
        .isFalse();
    assertThat(wrapped(new RedisCommandExecutionException("NOAUTH Authentication required.")))
        .isFalse();
    assertThat(TransientRedisFailures.isTransient(new RedisSystemException("x", null))).isFalse();
    assertThat(TransientRedisFailures.isTransient(new IllegalStateException("x"))).isFalse();
  }

  @Test
  @DisplayName("连接层的 RedisException（连接关闭、命令被中断）是暂时失败")
  void should_treat_connection_level_redis_exceptions_as_transient() {
    assertThat(
            TransientRedisFailures.isTransient(
                new RedisSystemException(
                    "Redis exception", new RedisException("Connection closed"))))
        .isTrue();
    assertThat(
            TransientRedisFailures.isTransient(
                new RedisSystemException(
                    "Redis command interrupted",
                    new RedisCommandInterruptedException(new InterruptedException()))))
        .isTrue();
  }

  @Test
  @DisplayName("SessionStoreUnavailableException 带 DEP_UNAVAILABLE 特征和固定文案")
  void should_carry_dep_unavailable_trait() {
    SessionStoreUnavailableException e =
        new SessionStoreUnavailableException(new RedisConnectionFailureException("refused"));

    assertThat(e.getMessage()).isEqualTo("服务暂时不可用");
    assertThat(e.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(e.getCause()).isInstanceOf(RedisConnectionFailureException.class);
  }

  /// Spring Data Redis 把 Lettuce 的命令错误包成 RedisSystemException，原因是 Lettuce 异常本身。
  ///
  /// @param cause Lettuce 异常
  /// @return 判定结果
  private static boolean wrapped(RuntimeException cause) {
    return TransientRedisFailures.isTransient(
        new RedisSystemException("Error in execution", cause));
  }
}
