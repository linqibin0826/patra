package dev.patra.notification.adapter.preference.rest.response;

import dev.patra.notification.app.preference.usecase.query.UnsubscribeLinkView;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;

/// 退订落地页打开时的数据（spec 8.2）。
///
/// @param maskedEmail 脱敏后的账号邮箱，如 `c***@example.com`
/// @param category 链接里的退订类别
/// @param reply 「回复我的」当前档位
/// @param mention 「@ 我的」当前档位
/// @param alreadyUnsubscribed 这一类是不是已经退订过
/// @param othersEnabled 退订这一类之后还有没有提醒邮件开着
public record UnsubscribeLinkResponse(
    String maskedEmail,
    UnsubscribeCategory category,
    EmailMode reply,
    EmailMode mention,
    boolean alreadyUnsubscribed,
    boolean othersEnabled) {

  /// 转换。
  ///
  /// @param view 落地页数据
  /// @return 响应
  public static UnsubscribeLinkResponse from(UnsubscribeLinkView view) {
    return new UnsubscribeLinkResponse(
        view.maskedEmail(),
        view.category(),
        view.reply(),
        view.mention(),
        view.alreadyUnsubscribed(),
        view.othersEnabled());
  }
}
