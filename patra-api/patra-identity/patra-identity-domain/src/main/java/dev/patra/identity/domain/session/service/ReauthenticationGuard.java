package dev.patra.identity.domain.session.service;

import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.policy.ReauthenticationPolicy;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// 再认证守卫：敏感操作（改邮箱、踢设备、注销、绑定第三方登录）要求当前会话在窗口内用密码证明过身份。
///
/// 按断言里的会话 ID 读会话的 `auth_at`：会话已不在（网关的断言最多 60 秒前签的，这期间被踢、被封、过期）
/// 是 401；超过窗口是 403 并带 `REAUTH_REQUIRED`，门户弹密码框、调 `POST /auth/reauth` 后重试。
/// 放在 identity 的领域层而不是安全 starter：下游服务不碰 Redis（PAP-64），需要再认证的操作都在 identity。
public final class ReauthenticationGuard {

  private final SessionStorePort sessions;
  private final ReauthenticationPolicy policy;
  private final Clock clock;

  /// 创建守卫。
  ///
  /// @param sessions 会话存储端口
  /// @param policy 再认证规则
  /// @param clock 时钟
  public ReauthenticationGuard(
      SessionStorePort sessions, ReauthenticationPolicy policy, Clock clock) {
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 要求当前会话最近一次密码验证在窗口内。
  ///
  /// @param current 当前用户（断言里的用户 ID 与会话 ID）
  /// @throws AuthenticationRequiredException 会话已不在
  /// @throws ReauthenticationRequiredException 最近一次密码验证已超过窗口
  public void requireRecent(CurrentUser current) {
    Objects.requireNonNull(current, "current 不能为 null");
    Instant authenticatedAt =
        sessions
            .findAuthenticatedAt(current.accountType(), current.userId(), current.sessionId())
            .orElseThrow(AuthenticationRequiredException::new);
    if (!policy.isRecent(authenticatedAt, clock.instant())) {
      throw new ReauthenticationRequiredException();
    }
  }
}
