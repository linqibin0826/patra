package dev.patra.identity.domain.session.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityNotice 单元测试：时间必填，设备、IP、属地可空。
@DisplayName("SecurityNotice 单元测试")
class SecurityNoticeTest {

  @Test
  @DisplayName("只填时间也能建；设备、IP、属地为 null；时间为 null 时拒绝")
  void should_require_occurred_at_but_allow_unknown_origin() {
    SecurityNotice notice =
        SecurityNotice.of(Instant.parse("2026-10-21T08:30:00Z"), null, null, null);

    assertThat(notice.device()).isNull();
    assertThat(notice.ipAddress()).isNull();
    assertThat(notice.ipLocation()).isNull();
    assertThatThrownBy(() -> SecurityNotice.of(null, "Chrome", "1.1.1.1", "中国"))
        .isInstanceOf(NullPointerException.class);
  }
}
