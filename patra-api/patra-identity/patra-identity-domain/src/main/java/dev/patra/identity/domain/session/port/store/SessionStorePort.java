package dev.patra.identity.domain.session.port.store;

import dev.patra.identity.domain.session.model.vo.ActiveUserSession;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.NewUserSession;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// 会话存储：建会话、按令牌查会话、换令牌、读再认证时间、按用户列出活会话、按会话 ID 删、按用户删全部、按用户删除指定一条外的全部。
///
/// Redis 暂时不可用时，所有方法都抛带 `DEP_UNAVAILABLE` 特征的异常（由适配器原样传出，映射成 503）。
public interface SessionStorePort {

  /// 建一条会话，返回令牌。超过上限时挤掉最老的，返回被挤掉的会话 ID。
  ///
  /// @param session 要签发的会话
  /// @return 签发结果
  IssuedUserSession issue(NewUserSession session);

  /// 按令牌原文查会话并节流续期：网关每个带令牌的请求经内部接口调到这里（PAP-106）。
  ///
  /// @param token 令牌原文，格式不对时直接为空
  /// @return 当前用户；令牌格式不对、会话不在或已过绝对期时为空
  Optional<CurrentUser> findAndTouch(String token);

  /// 换令牌、保留会话 ID：再认证、改密码用。`auth_at` 刷新成现在，旧令牌立即失效；
  /// `auth_at` 距今不足 `grace` 时不换（原子判断），返回「没换」。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param grace 换令牌的宽限期；`Duration.ZERO` 表示每次都换
  /// @return 换令牌的结果；会话不在或已过期时为空
  Optional<SessionRotation> rotate(
      AccountType accountType, long userId, long sessionId, Duration grace);

  /// 读一条会话最近一次用密码证明身份的时间，不续期。再认证守卫用。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 再认证时间；会话不在或已过期时为空
  Optional<Instant> findAuthenticatedAt(AccountType accountType, long userId, long sessionId);

  /// 按用户列出还活着的会话，不续期（登录设备列表，PAP-99）。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 活会话，顺序不保证
  List<ActiveUserSession> listActive(AccountType accountType, long userId);

  /// 按用户 ID 和会话 ID 删一条会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 会话真的被删掉时为 `true`；已经不在或不属于这个用户时为 `false`
  boolean revoke(AccountType accountType, long userId, long sessionId);

  /// 删掉一个用户的全部会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 真的被删掉的会话 ID
  List<Long> revokeAll(AccountType accountType, long userId);

  /// 删掉一个用户除指定一条之外的全部会话：验证邮箱、改密码时保留本机会话，踢其他设备。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param keepSessionId 要保留的会话 ID；它已不存在时等于删全部
  /// @return 真的被删掉的会话 ID
  List<Long> revokeAllExcept(AccountType accountType, long userId, long keepSessionId);
}
