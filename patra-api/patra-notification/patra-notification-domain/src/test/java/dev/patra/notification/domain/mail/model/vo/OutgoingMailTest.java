package dev.patra.notification.domain.mail.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// OutgoingMail 单元测试：模板变量的防御性拷贝、不接受 null 值、不泄露内容。
@DisplayName("OutgoingMail 单元测试")
class OutgoingMailTest {

  private static final UUID DELIVERY_ID = UUID.fromString("5f0c2d8e-8a41-4f5e-9d6a-3b0b6f1f2a11");

  /// 改动传入的 Map 不影响已建好的邮件。
  @Test
  void should_copy_model_defensively() {
    Map<String, Object> model = new HashMap<>(Map.of("displayName", "陈宇"));
    OutgoingMail mail = mail(model);

    model.put("resetUrl", "https://patra.example/reset-password#token=t");

    assertThat(mail.model()).containsOnlyKeys("displayName");
  }

  /// 模板变量的值不能为 null：可空的东西只放在值对象里，由渲染器换成「未知」。
  @Test
  void should_reject_null_model_values() {
    Map<String, Object> model = new HashMap<>();
    model.put("device", null);

    assertThatThrownBy(() -> mail(model)).isInstanceOf(NullPointerException.class);
  }

  /// `toString()` 只输出投递 ID 与模板名，不输出收件地址、主题与变量（里面有链接）。
  @Test
  void should_not_print_recipient_or_model() {
    OutgoingMail mail = mail(Map.of("resetUrl", "https://patra.example/reset-password#token=t"));

    assertThat(mail.toString())
        .contains(DELIVERY_ID.toString())
        .contains("password-reset")
        .doesNotContain("chen.yu@example.com")
        .doesNotContain("token=");
  }

  /// 收件地址为 `chen.yu@example.com` 的找回密码信。
  ///
  /// @param model 模板变量
  /// @return 邮件
  private static OutgoingMail mail(Map<String, Object> model) {
    return OutgoingMail.builder()
        .deliveryId(DELIVERY_ID)
        .to("chen.yu@example.com")
        .template("password-reset")
        .subject("重设你的 Patra 密码")
        .model(model)
        .build();
  }
}
