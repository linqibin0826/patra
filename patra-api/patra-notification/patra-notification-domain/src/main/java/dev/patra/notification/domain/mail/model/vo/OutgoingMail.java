package dev.patra.notification.domain.mail.model.vo;

import java.util.Map;
import java.util.Objects;
import lombok.Builder;

/// 一封要发出去的邮件：幂等键、收件地址、模板名、主题与模板变量。
///
/// 发信端口按模板名找文本与 HTML 两份模板、用变量渲染，不认识具体是哪一类信；安全邮件与提醒邮件都用它。
/// 幂等键交给服务商去重，也拼进 `Message-ID`：同一个键必须对应同一份内容（变量里不放随发送时刻变化的东西）。
///
/// @param idempotencyKey 幂等键：安全邮件是 identity 事件 ID 的字符串，提醒邮件是投递 ID 的十进制串
/// @param to 收件地址
/// @param template 模板名
/// @param subject 主题
/// @param model 模板变量，值不为 `null`
/// @param headers 附加的邮件头（如提醒邮件的 `List-Unsubscribe`），可不传；固定头由发信端口写，不会被它覆盖
@Builder
public record OutgoingMail(
    String idempotencyKey,
    String to,
    String template,
    String subject,
    Map<String, Object> model,
    Map<String, String> headers) {

  /// 前五项不能为 `null`；变量与附加头防御性拷贝，变量值为 `null` 时抛 {@link NullPointerException}；
  /// 附加头为 `null` 时当作没有，值里有换行时抛 {@link IllegalArgumentException}（换行能注入别的头）。
  public OutgoingMail {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(template, "template 不能为 null");
    Objects.requireNonNull(subject, "subject 不能为 null");
    model = Map.copyOf(Objects.requireNonNull(model, "model 不能为 null"));
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    headers.forEach(
        (name, value) -> {
          if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("邮件头 " + name + " 的值不能换行");
          }
        });
  }

  /// 只输出幂等键与模板名：收件地址、变量里的链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "OutgoingMail[idempotencyKey=" + idempotencyKey + ", template=" + template + "]";
  }
}
