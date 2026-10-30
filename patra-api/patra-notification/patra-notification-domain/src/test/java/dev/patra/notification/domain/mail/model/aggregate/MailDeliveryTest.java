package dev.patra.notification.domain.mail.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.mail.model.enums.MailDeliveryOutcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MailDelivery 单元测试：两种结果的记录与必填项。
@DisplayName("MailDelivery 单元测试")
class MailDeliveryTest {

  private static final UUID ID = UUID.fromString("9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a");
  private static final Instant AT = Instant.parse("2026-10-31T02:00:00Z");

  /// `sent` 记「发出去了」，`linkExpired` 记「链接过期没发」，其余三项原样。
  @Test
  void should_build_sent_and_link_expired_records() {
    MailDelivery sent = MailDelivery.sent(ID, "password-reset", AT);
    MailDelivery expired = MailDelivery.linkExpired(ID, "password-reset", AT);

    assertThat(sent.outcome()).isEqualTo(MailDeliveryOutcome.SENT);
    assertThat(expired.outcome()).isEqualTo(MailDeliveryOutcome.LINK_EXPIRED);
    assertThat(sent.deliveryId()).isEqualTo(ID);
    assertThat(sent.template()).isEqualTo("password-reset");
    assertThat(sent.recordedAt()).isEqualTo(AT);
  }

  /// 四项都必填。
  @Test
  void should_require_all_fields() {
    assertThatThrownBy(() -> MailDelivery.sent(null, "password-reset", AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MailDelivery.sent(ID, null, AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MailDelivery.sent(ID, "password-reset", null))
        .isInstanceOf(NullPointerException.class);
  }
}
