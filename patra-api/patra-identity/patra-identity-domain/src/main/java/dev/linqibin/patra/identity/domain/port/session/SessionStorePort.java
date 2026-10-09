package dev.linqibin.patra.identity.domain.port.session;

import dev.linqibin.patra.common.security.AccountType;
import java.util.List;

/// 会话存储：建会话、按会话 ID 删、按用户删全部。
///
/// Redis 暂时不可用时，三个方法都抛带 `DEP_UNAVAILABLE` 特征的异常（由适配器原样传出，映射成 503）。
public interface SessionStorePort {

  /// 建一条会话，返回令牌。超过上限时挤掉最老的，返回被挤掉的会话 ID。
  ///
  /// @param session 要签发的会话
  /// @return 签发结果
  IssuedUserSession issue(NewUserSession session);

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
}
