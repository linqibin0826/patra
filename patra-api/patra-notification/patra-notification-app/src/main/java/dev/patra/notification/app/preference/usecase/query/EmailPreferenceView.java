package dev.patra.notification.app.preference.usecase.query;

import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import java.util.Objects;

/// 一个人「回复我的」「@ 我的」两类的邮件档位。
///
/// @param reply 「回复我的」档位
/// @param mention 「@ 我的」档位
public record EmailPreferenceView(EmailMode reply, EmailMode mention) {

  /// 校验非空。
  public EmailPreferenceView {
    Objects.requireNonNull(reply, "reply 不能为 null");
    Objects.requireNonNull(mention, "mention 不能为 null");
  }

  /// 创建。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @return 视图
  public static EmailPreferenceView of(EmailMode reply, EmailMode mention) {
    return new EmailPreferenceView(reply, mention);
  }

  /// 从偏好取两类档位。
  ///
  /// @param preference 偏好
  /// @return 视图
  public static EmailPreferenceView from(EmailPreference preference) {
    return new EmailPreferenceView(preference.getReply(), preference.getMention());
  }
}
