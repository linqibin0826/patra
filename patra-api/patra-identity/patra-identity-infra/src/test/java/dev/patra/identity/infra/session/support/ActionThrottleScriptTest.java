package dev.patra.identity.infra.session.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/// ActionThrottleScript 单元测试：键与参数的排列、空结果与暂时失败的处理、IP 哈希。
@DisplayName("ActionThrottleScript 单元测试")
class ActionThrottleScriptTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final ActionThrottleScript script = new ActionThrottleScript(redis);

  @Test
  @DisplayName("键 = 冷却键 + 各窗口键；参数 = now、冷却毫秒、判或计、后缀、每个窗口的毫秒与上限")
  @SuppressWarnings("unchecked")
  void should_lay_out_keys_and_argv_in_order() {
    AtomicReference<List<String>> keys = new AtomicReference<>();
    AtomicReference<List<Object>> args = new AtomicReference<>();
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              keys.set(new ArrayList<>(invocation.getArgument(1)));
              Object[] all = invocation.getArguments();
              args.set(Arrays.asList(Arrays.copyOfRange(all, 2, all.length)));
              return 0L;
            });

    long wait =
        script.run(
            "c",
            Duration.ofSeconds(60),
            List.of(
                new ActionThrottleScript.Window("w1", Duration.ofHours(1), 5),
                new ActionThrottleScript.Window("w2", Duration.ofHours(24), 10)),
            NOW,
            true);

    assertThat(wait).isZero();
    assertThat(keys.get()).containsExactly("c", "w1", "w2");
    assertThat(args.get())
        .hasSize(8)
        .startsWith(Long.toString(NOW.toEpochMilli()), "60000", "1")
        .endsWith("3600000", "5", "86400000", "10");
    assertThat((String) args.get().get(3)).isNotBlank();
  }

  @Test
  @DisplayName("没有冷却时冷却键是占位的 -、冷却毫秒是 0；只计不判时第三个参数是 0")
  @SuppressWarnings("unchecked")
  void should_pass_zero_cooldown_and_count_only_flag() {
    AtomicReference<List<Object>> args = new AtomicReference<>();
    AtomicReference<List<String>> keys = new AtomicReference<>();
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              keys.set(new ArrayList<>(invocation.getArgument(1)));
              Object[] all = invocation.getArguments();
              args.set(Arrays.asList(Arrays.copyOfRange(all, 2, all.length)));
              return 0L;
            });

    script.run(
        "-",
        Duration.ZERO,
        List.of(new ActionThrottleScript.Window("w", Duration.ofMinutes(1), 10)),
        NOW,
        false);

    assertThat(keys.get()).containsExactly("-", "w");
    assertThat(args.get().get(1)).isEqualTo("0");
    assertThat(args.get().get(2)).isEqualTo("0");
  }

  @Test
  @DisplayName("脚本没有真正执行（返回 null）按不可用处理；Redis 暂时失败转 503")
  @SuppressWarnings("unchecked")
  void should_treat_null_result_and_transient_failures_as_unavailable() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(null)
        .thenThrow(new QueryTimeoutException("timeout"));
    List<ActionThrottleScript.Window> windows =
        List.of(new ActionThrottleScript.Window("w", Duration.ofMinutes(1), 10));

    assertThatThrownBy(() -> script.run("-", Duration.ZERO, windows, NOW, true))
        .isInstanceOf(TemporarilyUnavailableException.class);
    assertThatThrownBy(() -> script.run("-", Duration.ZERO, windows, NOW, true))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("键里的哈希是 SHA-256 十六进制前 32 位")
  void should_hash_to_32_hex_characters() {
    assertThat(ActionThrottleScript.keyHash("203.0.113.9"))
        .matches("^[0-9a-f]{32}$")
        .isNotEqualTo(ActionThrottleScript.keyHash("203.0.113.10"));
  }
}
