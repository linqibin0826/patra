package dev.patra.identity.domain.account.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ExternalIdentity 单元测试。
@DisplayName("ExternalIdentity 单元测试")
class ExternalIdentityTest {

  private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");

  @Test
  @DisplayName("新绑定：记下绑定时间，还没用它登录过")
  void should_link_without_last_login() {
    ExternalIdentity identity =
        ExternalIdentity.link(42L, IdentityProvider.GITHUB, "583231", "octocat", T0);

    assertThat(identity.getUserId()).isEqualTo(42L);
    assertThat(identity.getProvider()).isEqualTo(IdentityProvider.GITHUB);
    assertThat(identity.getSubject()).isEqualTo("583231");
    assertThat(identity.getAccountLabel()).isEqualTo("octocat");
    assertThat(identity.getLinkedAt()).isEqualTo(T0);
    assertThat(identity.getLastLoginAt()).isNull();
  }

  @Test
  @DisplayName("登录：更新账号标识和最近登录时间，绑定时间不变")
  void should_record_login() {
    ExternalIdentity identity =
        ExternalIdentity.link(42L, IdentityProvider.GITHUB, "583231", "octocat", T0);

    identity.recordLogin("renamed-cat", T0.plusSeconds(60));

    assertThat(identity.getAccountLabel()).isEqualTo("renamed-cat");
    assertThat(identity.getLastLoginAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(identity.getLinkedAt()).isEqualTo(T0);
  }
}
