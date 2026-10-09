package dev.linqibin.patra.identity.domain.port.session;

import java.util.List;

/// 签发结果：令牌原文和被挤掉的会话 ID。
///
/// @param token 令牌原文，只交给客户端，不保存
/// @param replacedSessionIds 被挤掉的会话 ID，可能为空
public record IssuedUserSession(String token, List<Long> replacedSessionIds) {

  /// 校验令牌，拷贝列表。
  public IssuedUserSession {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token 不能为空");
    }
    replacedSessionIds = replacedSessionIds == null ? List.of() : List.copyOf(replacedSessionIds);
  }

  /// 创建结果。
  ///
  /// @param token 令牌原文
  /// @param replacedSessionIds 被挤掉的会话 ID，可以为 `null`
  /// @return 结果
  public static IssuedUserSession of(String token, List<Long> replacedSessionIds) {
    return new IssuedUserSession(token, replacedSessionIds);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "IssuedUserSession[token=***, replacedSessionIds=" + replacedSessionIds + "]";
  }
}
