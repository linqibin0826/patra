package dev.patra.notification.domain.mail.policy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.enums.UnsubscribeLinkCategory;
import java.util.Objects;

/// 提醒邮件里的门户链接（spec 8.3）。域名只来自配置，不来自请求；链接不改写、不加追踪参数。
/// 两个退订地址是 PAP-142 第 7 节的长期契约：已经发出去的邮件要一直能用，路径与参数名不能改。
///
/// @param baseUrl 门户地址，末尾不带 `/`
public record ReminderLinks(String baseUrl) {

  /// 校验非空。
  public ReminderLinks {
    Objects.requireNonNull(baseUrl, "baseUrl 不能为 null");
  }

  /// 构造，去掉末尾的 `/`。
  ///
  /// @param baseUrl 门户地址
  /// @return 链接
  public static ReminderLinks of(String baseUrl) {
    String base = baseUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return new ReminderLinks(base);
  }

  /// 评论的永久链接（单楼视图），同门户 `notificationHref`。
  ///
  /// @param publication 是否文献（否则是投稿经验）
  /// @param key 对象在门户链接里的 ID
  /// @param commentId 评论
  /// @return 链接
  public String comment(boolean publication, String key, long commentId) {
    return baseUrl + (publication ? "/papers/" : "/posts/") + key + "?comment=" + commentId;
  }

  /// 提醒页里这一类的标签。
  ///
  /// @param category 回复或 @
  /// @return 链接
  public String inbox(NotificationCategory category) {
    return category == NotificationCategory.MENTION
        ? baseUrl + "/notifications/mentions"
        : inboxHome();
  }

  /// 提醒页（默认是「回复我的」标签）。
  ///
  /// @return 链接
  public String inboxHome() {
    return baseUrl + "/notifications";
  }

  /// 通知设置页。
  ///
  /// @return 链接
  public String settings() {
    return baseUrl + "/settings/notifications";
  }

  /// 退订确认页（邮件底部的「退订这类邮件」）：令牌放片段，预取拿不到。
  ///
  /// @param token 退订令牌，base64url，不用转义
  /// @param category 退订类别
  /// @return 链接
  public String unsubscribeConfirm(String token, UnsubscribeLinkCategory category) {
    return baseUrl + "/unsubscribe#token=" + token + "&category=" + category.name();
  }

  /// RFC 8058 一键退订地址（`List-Unsubscribe` 头）：邮箱服务商 POST 它，片段不会发给服务器，令牌只能放查询串。
  ///
  /// @param token 退订令牌
  /// @param category 退订类别
  /// @return 链接
  public String unsubscribeOneClick(String token, UnsubscribeLinkCategory category) {
    return baseUrl + "/unsubscribe/one-click?token=" + token + "&category=" + category.name();
  }
}
