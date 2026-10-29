package dev.patra.identity.api.session.dto;

import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import lombok.Builder;

/// 查会话的结果，形状参照 RFC 7662 的 `active`。
///
/// @param active 会话是否有效；为 `false` 时其余字段都是 `null`
/// @param userId 用户 ID
/// @param sessionId 会话 ID
/// @param accountType 账号类型
/// @param clientType 客户端类型
@Builder
public record SessionIntrospectionResp(
    boolean active, Long userId, Long sessionId, AccountType accountType, ClientType clientType) {

  /// 没有有效会话。
  ///
  /// @return 结果
  public static SessionIntrospectionResp inactive() {
    return SessionIntrospectionResp.builder().active(false).build();
  }
}
