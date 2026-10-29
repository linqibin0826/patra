package dev.patra.identity.domain.session.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityNotice 单元测试：时间与「不是我怎么办」必填，设备、IP、属地可空。
@DisplayName("SecurityNotice 单元测试")
class SecurityNoticeTest {

  @Test
  @DisplayName("只填时间和建议也能建；设备、IP、属地为 null")
  void should_require_occurred_at_and_advice_but_allow_unknown_device() {
    SecurityNotice notice =
        SecurityNotice.builder()
            .occurredAt(Instant.parse("2026-10-21T08:30:00Z"))
            .notMeAdvice("请立即修改密码")
            .build();

    assertThat(notice.device()).isNull();
    assertThat(notice.ipAddress()).isNull();
    assertThat(notice.ipLocation()).isNull();
    assertThat(notice.notMeAdvice()).isEqualTo("请立即修改密码");
    assertThatThrownBy(() -> SecurityNotice.builder().notMeAdvice("x").build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> SecurityNotice.builder().occurredAt(Instant.EPOCH).build())
        .isInstanceOf(NullPointerException.class);
  }
}
