package dev.patra.interaction.infra.subject.user;

import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 的内部接口（`patra-identity-api` 的 `UserEndpoint`）取用户摘要。
///
/// 三种失败都翻成 {@link UserDirectoryUnavailableException}（503）：回了错误响应（`RemoteCallException`；4xx 是
/// 本服务的程序错误，日志里看得出来）、连不上或超时（`ResourceAccessException`）、注册中心里没有实例
/// （`IllegalStateException`）。`try` 只包住一次代理调用。
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDirectoryAdapter implements UserDirectoryPort {

  private final UserEndpoint users;

  /// 批量取摘要。
  ///
  /// @param userIds 用户 ID
  /// @param viewerUserId 访客，可空
  /// @return 用户 ID → 摘要
  @Override
  public Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerUserId) {
    if (userIds.isEmpty() || userIds.size() > MAX_USERS) {
      throw new IllegalArgumentException("一次查 1 ~ " + MAX_USERS + " 个用户：" + userIds.size());
    }
    List<UserSummaryResp> summaries;
    try {
      summaries = users.summaries(UserSummariesReq.of(List.copyOf(userIds), viewerUserId)).users();
    } catch (RemoteCallException ex) {
      log.warn(
          "identity 用户摘要接口返回错误: users={}, httpStatus={}, errorCode={}",
          userIds.size(),
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new UserDirectoryUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn(
          "identity 用户摘要接口不可用: users={}, cause={}", userIds.size(), ex.getClass().getSimpleName());
      throw new UserDirectoryUnavailableException(ex);
    }
    return summaries.stream()
        .collect(
            Collectors.toUnmodifiableMap(UserSummaryResp::userId, UserDirectoryAdapter::toCard));
  }

  /// 摘要 → 领域卡片。
  ///
  /// @param summary 摘要
  /// @return 卡片
  private static UserCard toCard(UserSummaryResp summary) {
    return UserCard.builder()
        .userId(summary.userId())
        .username(summary.username())
        .displayName(summary.displayName())
        .avatarUrl(summary.avatarUrl())
        .avatarPalette(summary.avatarPalette())
        .state(AccountState.valueOf(summary.accountStatus().name()))
        .emailVerified(summary.emailVerified())
        .profileVisible(summary.profileVisible())
        .build();
  }
}
