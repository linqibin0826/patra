package dev.patra.notification.infra.kernel.identity;

import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.port.identity.AccountStatePort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 的批量用户摘要（`UserEndpoint.summaries`，后台任务没有访客，传 `null`）取账号状态，只用摘要里的
/// `accountStatus`。失败的翻译同 `RecipientAdapter`：三种失败都是 {@link IdentityUnavailableException}（503）。
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStateAdapter implements AccountStatePort {

  private final UserEndpoint users;

  /// 批量取账号状态。
  ///
  /// @param userIds 用户 ID
  /// @return 用户 ID → 账号状态
  @Override
  public Map<Long, AccountState> findStates(Collection<Long> userIds) {
    if (userIds.isEmpty() || userIds.size() > MAX_USERS) {
      throw new IllegalArgumentException("一次查 1 ~ " + MAX_USERS + " 个用户：" + userIds.size());
    }
    List<UserSummaryResp> summaries;
    try {
      summaries = users.summaries(UserSummariesReq.of(List.copyOf(userIds), null)).users();
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
        .collect(
            Collectors.toUnmodifiableMap(
                UserSummaryResp::userId,
                summary -> AccountState.valueOf(summary.accountStatus().name())));
  }
}
