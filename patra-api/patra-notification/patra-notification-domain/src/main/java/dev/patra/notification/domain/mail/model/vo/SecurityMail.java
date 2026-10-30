package dev.patra.notification.domain.mail.model.vo;

import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 一封安全邮件的事实：identity 说「请给这个地址发一封这一类的信」，以及这一类模板要用的东西。
///
/// @param eventId identity 的事件 ID，也是这封信的投递 ID
/// @param kind 类别
/// @param to 收件地址
/// @param displayName 收件人昵称
/// @param facts 这一类模板专属的变量（链接、遮掩过的新邮箱、到期时刻等），值不为 `null`
/// @param context 触发的那次操作；带安全通知片段的类别必填，其余为 `null`
/// @param linkExpiresAt 链接过期时刻；不带一次性链接的类别为 `null`
@Builder
public record SecurityMail(
    UUID eventId,
    SecurityMailKind kind,
    String to,
    String displayName,
    Map<String, Object> facts,
    OperationContext context,
    Instant linkExpiresAt) {

  /// 必填项不能为 `null`，带安全通知片段的类别还要有 `context`；`facts` 为 `null` 时当作空，否则防御性拷贝。
  public SecurityMail {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(kind, "kind 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    if (kind.hasNotice()) {
      Objects.requireNonNull(context, "context 不能为 null：" + kind + " 带安全通知片段");
    }
    facts = facts == null ? Map.of() : Map.copyOf(facts);
  }

  /// 组成要发的邮件：投递 ID 就是事件 ID，模板名与主题取自类别；变量是昵称、这一类的事实，
  /// 带片段的类别加 `notice`（{@link SecurityNotice}），有链接的加 `linkExpiresAt`。
  ///
  /// @return 邮件
  public OutgoingMail compose() {
    Map<String, Object> model = new HashMap<>(facts);
    model.put("displayName", displayName);
    if (kind.hasNotice()) {
      model.put("notice", SecurityNotice.of(context, kind.notMeAdvice()));
    }
    if (linkExpiresAt != null) {
      model.put("linkExpiresAt", linkExpiresAt);
    }
    return OutgoingMail.builder()
        .deliveryId(eventId)
        .to(to)
        .template(kind.template())
        .subject(kind.subjectFor(facts))
        .model(model)
        .build();
  }

  /// 链接是否已过期、这封信因此不该再发：只对只为链接而发的类别成立，到了过期时刻就算过期。
  ///
  /// @param now 当前时刻
  /// @return 不该再发为 `true`
  public boolean linkExpiredAt(Instant now) {
    return kind.skipsWhenLinkExpired() && linkExpiresAt != null && !now.isBefore(linkExpiresAt);
  }

  /// 只输出事件 ID 与类别：收件地址、链接不进日志。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SecurityMail[eventId=" + eventId + ", kind=" + kind + "]";
  }
}
