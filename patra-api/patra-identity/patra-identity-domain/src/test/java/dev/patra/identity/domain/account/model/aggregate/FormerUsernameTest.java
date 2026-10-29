package dev.patra.identity.domain.account.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.Username;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// FormerUsername 单元测试。
@DisplayName("FormerUsername 单元测试")
class FormerUsernameTest {

  private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");

  @Test
  @DisplayName("保留到期时间是改名时间加保留期；到期当刻不再保留")
  void should_reserve_until_replaced_at_plus_period() {
    FormerUsername former =
        FormerUsername.reserve(7L, Username.of("old-name"), T0, Duration.ofDays(90));

    assertThat(former.getId()).isNull();
    assertThat(former.getUserId()).isEqualTo(7L);
    assertThat(former.getUsername()).isEqualTo(Username.of("old-name"));
    assertThat(former.getReplacedAt()).isEqualTo(T0);
    assertThat(former.getReservedUntil()).isEqualTo(T0.plus(Duration.ofDays(90)));
    assertThat(former.isReservedAt(T0.plus(Duration.ofDays(89)))).isTrue();
    assertThat(former.isReservedAt(T0.plus(Duration.ofDays(90)))).isFalse();
  }

  @Test
  @DisplayName("从库里恢复时保留全部字段")
  void should_restore_all_fields() {
    Instant until = T0.plus(Duration.ofDays(90));

    FormerUsername former = FormerUsername.restore(1L, 7L, Username.of("old-name"), T0, until, 2L);

    assertThat(former.getId()).isEqualTo(1L);
    assertThat(former.getUserId()).isEqualTo(7L);
    assertThat(former.getUsername().value()).isEqualTo("old-name");
    assertThat(former.getReplacedAt()).isEqualTo(T0);
    assertThat(former.getReservedUntil()).isEqualTo(until);
    assertThat(former.getVersion()).isEqualTo(2L);
  }
}
