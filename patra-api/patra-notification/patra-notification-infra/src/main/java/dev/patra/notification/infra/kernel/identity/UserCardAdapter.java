package dev.patra.notification.infra.kernel.identity;

import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.notification.domain.kernel.port.identity.UserCardPort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 的批量用户摘要（`UserEndpoint.summaries`，带访客）取名片。失败的翻译同 `AccountStateAdapter`：
/// 回了错误响应、连不上或超时、注册中心里没有实例，都是 {@link IdentityUnavailableException}（503）。
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCardAdapter implements UserCardPort {

  private final UserEndpoint users;

  /// 批量取名片。
  ///
  /// @param userIds 用户 ID
  /// @param viewerId 访客，可空
  /// @return 用户 ID → 名片
  @Override
  public Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerId) {
    if (userIds.isEmpty() || userIds.size() > MAX_USERS) {
      throw new IllegalArgumentException("一次查 1 ~ " + MAX_USERS + " 个用户：" + userIds.size());
    }
    List<UserSummaryResp> summaries;
    try {
      summaries = users.summaries(UserSummariesReq.of(List.copyOf(userIds), viewerId)).users();
    } catch (RemoteCallException ex) {
      log.warn(
          "identity 用户摘要接口返回错误: users={}, httpStatus={}, errorCode={}",
          userIds.size(),
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new IdentityUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn(
          "identity 用户摘要接口不可用: users={}, cause={}", userIds.size(), ex.getClass().getSimpleName());
      throw new IdentityUnavailableException(ex);
    }
    return summaries.stream()
        .collect(Collectors.toUnmodifiableMap(UserSummaryResp::userId, UserCardAdapter::toCard));
  }

  /// 摘要 → 名片。
  ///
  /// @param summary 摘要
  /// @return 名片
  private static UserCard toCard(UserSummaryResp summary) {
    return UserCard.builder()
        .userId(summary.userId())
        .username(summary.username())
        .displayName(summary.displayName())
        .avatarUrl(summary.avatarUrl())
        .avatarPalette(summary.avatarPalette())
        .state(AccountState.valueOf(summary.accountStatus().name()))
        .profileVisible(summary.profileVisible())
        .build();
  }
}
