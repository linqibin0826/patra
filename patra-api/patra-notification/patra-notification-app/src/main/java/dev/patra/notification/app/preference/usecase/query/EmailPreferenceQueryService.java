package dev.patra.notification.app.preference.usecase.query;

import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.notification.domain.kernel.port.identity.RecipientPort;
import dev.patra.notification.domain.preference.exception.UnsubscribeLinkInvalidException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.MaskedEmail;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 提醒邮件偏好的读端：本人的偏好（需登录），退订落地页的数据（免登录、只读，可被预取）。不开事务。
@Service
@RequiredArgsConstructor
public class EmailPreferenceQueryService {

  private final EmailPreferenceRepository preferences;
  private final RecipientPort recipients;
  private final CurrentUserPort currentUser;

  /// 本人两类的档位；没改过的是默认的离站逐条（不落库）。
  ///
  /// @return 档位
  public EmailPreferenceView mine() {
    long userId = currentUser.require().userId();
    return preferences
        .findByUserId(userId)
        .map(EmailPreferenceView::from)
        .orElseGet(() -> EmailPreferenceView.of(EmailMode.INSTANT, EmailMode.INSTANT));
  }

  /// 退订落地页的数据：先解析链接（不合法不查库），再按令牌读偏好，最后向 identity 取邮箱与账号状态。
  /// 已注销、identity 不认识、没有邮箱都算链接无效（spec 5.4）。
  ///
  /// @param token 链接里的令牌，可空
  /// @param category 链接里的类别，可空；`ALL` 不会出现在链接里，算无效
  /// @return 落地页数据
  public UnsubscribeLinkView inspect(String token, String category) {
    UnsubscribeToken parsedToken =
        UnsubscribeToken.parse(token).orElseThrow(UnsubscribeLinkInvalidException::new);
    UnsubscribeCategory parsedCategory =
        UnsubscribeCategory.parse(category)
            .filter(UnsubscribeCategory::inLinks)
            .orElseThrow(UnsubscribeLinkInvalidException::new);
    EmailPreference preference =
        preferences.findByToken(parsedToken).orElseThrow(UnsubscribeLinkInvalidException::new);
    Recipient recipient =
        recipients
            .find(preference.getUserId())
            .filter(r -> r.state() != AccountState.DELETED && r.email() != null)
            .orElseThrow(UnsubscribeLinkInvalidException::new);
    return UnsubscribeLinkView.builder()
        .maskedEmail(MaskedEmail.of(recipient.email()))
        .category(parsedCategory)
        .reply(preference.getReply())
        .mention(preference.getMention())
        .alreadyUnsubscribed(preference.isUnsubscribedFrom(parsedCategory))
        .othersEnabled(preference.anyEmailOnAfter(parsedCategory))
        .build();
  }
}
