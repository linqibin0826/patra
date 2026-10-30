package dev.patra.notification.domain.mail.model.vo;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 一封要发出去的邮件：投递 ID、收件地址、模板名、主题与模板变量。
///
/// 发信端口按模板名找文本与 HTML 两份模板、用变量渲染，不认识具体是哪一类信；安全邮件与提醒邮件都用它。
/// 投递 ID 就是服务商的幂等键：同一个 ID 必须对应同一份内容（变量里不放随发送时刻变化的东西）。
///
/// @param deliveryId 投递 ID；安全邮件就是 identity 的事件 ID
/// @param to 收件地址
/// @param template 模板名
/// @param subject 主题
/// @param model 模板变量，值不为 `null`
@Builder
public record OutgoingMail(
    UUID deliveryId, String to, String template, String subject, Map<String, Object> model) {

  /// 字段都不能为 `null`；变量防御性拷贝，值为 `null` 时抛 {@link NullPointerException}。
  public OutgoingMail {
    Objects.requireNonNull(deliveryId, "deliveryId 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(template, "template 不能为 null");
    Objects.requireNonNull(subject, "subject 不能为 null");
    model = Map.copyOf(Objects.requireNonNull(model, "model 不能为 null"));
  }

  /// 只输出投递 ID 与模板名：收件地址、变量里的链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "OutgoingMail[deliveryId=" + deliveryId + ", template=" + template + "]";
  }
}
