package dev.patra.notification.adapter.preference.rest.response;

import dev.patra.notification.app.preference.usecase.query.EmailPreferenceView;
import dev.patra.notification.domain.preference.model.read.EmailMode;

/// 两类提醒的邮件档位。「有帮助」只在站内，不在这里。
///
/// @param reply 「回复我的」档位
/// @param mention 「@ 我的」档位
public record EmailPreferenceResponse(EmailMode reply, EmailMode mention) {

  /// 转换。
  ///
  /// @param view 档位
  /// @return 响应
  public static EmailPreferenceResponse from(EmailPreferenceView view) {
    return new EmailPreferenceResponse(view.reply(), view.mention());
  }
}
