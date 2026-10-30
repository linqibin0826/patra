package dev.patra.notification.infra.kernel.identity;

import dev.patra.identity.api.account.dto.UserRecipientResp;
import dev.patra.identity.api.account.dto.UserRecipientsReq;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.notification.domain.kernel.port.identity.RecipientPort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 的内部接口（`patra-identity-api` 的 `UserEndpoint.recipients`）取收件信息。
///
/// 三种失败都翻成 {@link IdentityUnavailableException}（503）：回了错误响应（`RemoteCallException`；4xx 是本服务的
/// 程序错误，日志里看得出来）、连不上或超时（`ResourceAccessException`）、注册中心里没有实例
/// （`IllegalStateException`）。`try` 只包住一次代理调用；日志不打邮箱。
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipientAdapter implements RecipientPort {

  private final UserEndpoint users;

  /// 批量取收件信息。
  ///
  /// @param userIds 用户 ID
  /// @return 用户 ID → 收件信息
  @Override
  public Map<Long, Recipient> findAll(Collection<Long> userIds) {
    if (userIds.isEmpty() || userIds.size() > MAX_USERS) {
      throw new IllegalArgumentException("一次查 1 ~ " + MAX_USERS + " 个用户：" + userIds.size());
    }
    List<UserRecipientResp> recipients;
    try {
      recipients = users.recipients(UserRecipientsReq.of(List.copyOf(userIds))).recipients();
    } catch (RemoteCallException ex) {
      log.warn(
          "identity 收件信息接口返回错误: users={}, httpStatus={}, errorCode={}",
          userIds.size(),
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new IdentityUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn(
          "identity 收件信息接口不可用: users={}, cause={}", userIds.size(), ex.getClass().getSimpleName());
      throw new IdentityUnavailableException(ex);
    }
    return recipients.stream()
        .collect(
            Collectors.toUnmodifiableMap(UserRecipientResp::userId, RecipientAdapter::toRecipient));
  }

  /// 响应 → 领域读模型。
  ///
  /// @param resp 响应
  /// @return 收件信息
  private static Recipient toRecipient(UserRecipientResp resp) {
    return Recipient.builder()
        .userId(resp.userId())
        .email(resp.email())
        .emailVerified(resp.emailVerified())
        .state(AccountState.valueOf(resp.accountStatus().name()))
        .username(resp.username())
        .displayName(resp.displayName())
        .build();
  }
}
