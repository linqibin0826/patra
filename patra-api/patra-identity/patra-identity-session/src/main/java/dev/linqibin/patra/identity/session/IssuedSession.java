package dev.linqibin.patra.identity.session;

import java.util.List;
import java.util.Objects;

/// 建会话的结果：新令牌，以及因超过上限被挤掉的会话 ID。
///
/// @param token 新令牌，只在这里出现一次，交给客户端后不再保存
/// @param replacedSessionIds 被挤掉的会话 ID，可能为空
public record IssuedSession(SessionToken token, List<Long> replacedSessionIds) {

  /// 校验令牌，拷贝列表。
  public IssuedSession {
    Objects.requireNonNull(token, "token 不能为 null");
    replacedSessionIds = replacedSessionIds == null ? List.of() : List.copyOf(replacedSessionIds);
  }

  /// 创建结果。
  ///
  /// @param token 新令牌
  /// @param replacedSessionIds 被挤掉的会话 ID，可以为 `null`
  /// @return 结果
  public static IssuedSession of(SessionToken token, List<Long> replacedSessionIds) {
    return new IssuedSession(token, replacedSessionIds);
  }

  /// 令牌按 `SessionToken.toString()` 遮掩。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "IssuedSession[token=" + token + ", replacedSessionIds=" + replacedSessionIds + "]";
  }
}
