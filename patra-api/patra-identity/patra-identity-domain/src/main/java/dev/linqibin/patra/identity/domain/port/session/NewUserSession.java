package dev.linqibin.patra.identity.domain.port.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 要签发的会话：domain 交给会话存储端口的输入。
///
/// @param userId 用户 ID，正数
/// @param sessionId 会话 ID，正数，等于登录记录的 ID
/// @param accountType 账号类型
/// @param client 客户端信息
/// @param now 签发时间，Redis 和登录记录都按它算过期
/// @param lifetime 有效期
/// @param maxSessionsPerUser 每用户的会话上限
@Builder
public record NewUserSession(
    long userId,
    long sessionId,
    AccountType accountType,
    LoginClient client,
    Instant now,
    SessionLifetime lifetime,
    int maxSessionsPerUser) {

  /// 校验各字段。
  public NewUserSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(client, "client 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    Objects.requireNonNull(lifetime, "lifetime 不能为 null");
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
  }

  /// 绝对过期时间。
  ///
  /// @return `now + lifetime.absolute()`
  public Instant expiresAt() {
    return now.plus(lifetime.absolute());
  }
}
