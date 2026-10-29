package dev.patra.interaction.domain.subject.port.user;

import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.util.Collection;
import java.util.Map;

/// 向 identity 取用户摘要（批量用户摘要内部接口）。主页可见性规则只在 identity 里有一份，这里只透传访客 ID。
public interface UserDirectoryPort {

  /// 一次最多多少个用户（identity 接口的上限）。
  int MAX_USERS = 100;

  /// 批量取摘要。
  ///
  /// @param userIds 用户 ID，1 ~ {@value #MAX_USERS} 个
  /// @param viewerUserId 访客的用户 ID，匿名或后台任务为 `null`
  /// @return 用户 ID → 摘要；identity 不认识的 ID 不在结果里（不等于已注销）
  /// @throws UserDirectoryUnavailableException identity 不可用
  /// @throws IllegalArgumentException 个数越界
  Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerUserId);
}
