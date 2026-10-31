package dev.patra.notification.app.preference.usecase.query;

import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import lombok.Builder;

/// 退订落地页打开时要的数据（spec 8.2）。
///
/// @param maskedEmail 脱敏后的账号邮箱
/// @param category 链接里的退订类别
/// @param reply 「回复我的」当前档位
/// @param mention 「@ 我的」当前档位
/// @param alreadyUnsubscribed 这一类是不是已经退订过（spec 5.3）
/// @param othersEnabled 退订这一类之后还有没有提醒邮件开着（spec 5.3）
@Builder
public record UnsubscribeLinkView(
    String maskedEmail,
    UnsubscribeCategory category,
    EmailMode reply,
    EmailMode mention,
    boolean alreadyUnsubscribed,
    boolean othersEnabled) {}
