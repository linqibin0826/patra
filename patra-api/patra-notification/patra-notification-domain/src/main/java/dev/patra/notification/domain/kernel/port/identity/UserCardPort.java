package dev.patra.notification.domain.kernel.port.identity;

import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import java.util.Collection;
import java.util.Map;

/// 替某位访客按用户 ID 取名片（identity 的 `POST /_internal/users/summaries`）：站内列表的触发人与 @ 到的人、提醒
/// 邮件里 @ 到的人。主页可见性规则只在 identity 里有一份，这里只透传访客。
public interface UserCardPort {

  /// 一次最多多少个用户，等于 identity 接口的上限。
  int MAX_USERS = 100;

  /// 批量取名片。
  ///
  /// @param userIds 用户 ID，1 ~ {@value #MAX_USERS} 个
  /// @param viewerId 访客（收件人）；后台任务为 `null`
  /// @return 用户 ID → 名片；identity 不认识的省略
  /// @throws IdentityUnavailableException identity 不可用
  Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerId);
}
