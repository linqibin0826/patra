package dev.patra.notification.domain.mail.model.vo;

import java.util.Objects;

/// 安全通知片段的内容：那次操作的时间、设备、IP 与属地，加这一类信的「不是我怎么办」。
///
/// 模板里共用的片段按它渲染；变量名 `notice`。
///
/// @param context 触发的那次操作
/// @param notMeAdvice 不是本人操作时该怎么办
public record SecurityNotice(OperationContext context, String notMeAdvice) {

  /// 两项都不能为 `null`。
  public SecurityNotice {
    Objects.requireNonNull(context, "context 不能为 null");
    Objects.requireNonNull(notMeAdvice, "notMeAdvice 不能为 null");
  }

  /// 创建。
  ///
  /// @param context 触发的那次操作
  /// @param notMeAdvice 不是本人操作时该怎么办
  /// @return 安全通知
  public static SecurityNotice of(OperationContext context, String notMeAdvice) {
    return new SecurityNotice(context, notMeAdvice);
  }
}
