package dev.patra.interaction.infra.discussion.mention;

import dev.patra.identity.api.account.dto.UserResolutionResp;
import dev.patra.identity.api.account.dto.UserResolveByUsernamesReq;
import dev.patra.identity.api.account.dto.UserSuggestReq;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 的内部接口（`patra-identity-api` 的 `UserEndpoint`）解析 `@用户名`、做 @ 联想。
///
/// 三种失败都翻成 {@link UserDirectoryUnavailableException}（503），同 subject 的 `UserDirectoryAdapter`：回了错误响应
/// （`RemoteCallException`）、连不上或超时（`ResourceAccessException`）、注册中心里没有实例（`IllegalStateException`）。
/// `try` 只包住一次代理调用。
@Slf4j
@Component
@RequiredArgsConstructor
public class MentionDirectoryAdapter implements MentionDirectoryPort {

  private final UserEndpoint users;

  /// 批量解析用户名。
  ///
  /// @param usernames 用户名原文
  /// @return 原文 → 解析结果
  @Override
  public Map<String, MentionTarget> resolve(Collection<String> usernames) {
    if (usernames.isEmpty() || usernames.size() > MAX_USERNAMES) {
      throw new IllegalArgumentException("一次解析 1 ~ " + MAX_USERNAMES + " 个用户名：" + usernames.size());
    }
    List<UserResolutionResp> resolved =
        call(
            "解析用户名",
            usernames.size(),
            () ->
                users
                    .resolveByUsernames(UserResolveByUsernamesReq.of(List.copyOf(usernames)))
                    .users());
    Map<String, MentionTarget> targets = new LinkedHashMap<>();
    for (UserResolutionResp row : resolved) {
      targets.put(
          row.username(),
          MentionTarget.of(row.userId(), AccountState.valueOf(row.accountStatus().name())));
    }
    return Map.copyOf(targets);
  }

  /// 前缀联想。
  ///
  /// @param prefix 前缀原文
  /// @param viewerUserId 访客
  /// @param limit 最多几位
  /// @return 名片
  @Override
  public List<UserCard> suggest(String prefix, long viewerUserId, int limit) {
    if (limit < 1 || limit > MAX_SUGGESTIONS) {
      throw new IllegalArgumentException("一次联想 1 ~ " + MAX_SUGGESTIONS + " 位：" + limit);
    }
    List<UserSummaryResp> summaries =
        call(
            "联想用户",
            limit,
            () -> users.suggest(UserSuggestReq.of(prefix, viewerUserId, limit)).users());
    return summaries.stream().map(MentionDirectoryAdapter::toCard).toList();
  }

  /// 调一次代理，三种失败翻成依赖不可用。
  ///
  /// @param action 日志里的动作名
  /// @param size 日志里的数量
  /// @param proxyCall 代理调用
  /// @param <T> 结果类型
  /// @return 结果
  private static <T> T call(String action, int size, Supplier<T> proxyCall) {
    try {
      return proxyCall.get();
    } catch (RemoteCallException ex) {
      log.warn(
          "identity {}接口返回错误: size={}, httpStatus={}, errorCode={}",
          action,
          size,
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new UserDirectoryUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn("identity {}接口不可用: size={}, cause={}", action, size, ex.getClass().getSimpleName());
      throw new UserDirectoryUnavailableException(ex);
    }
  }

  /// 摘要 → 领域卡片（同 subject 的 `UserDirectoryAdapter`；跨子域不能用它的 infra 类）。
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
