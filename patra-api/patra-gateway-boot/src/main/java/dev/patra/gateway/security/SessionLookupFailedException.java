package dev.patra.gateway.security;

import java.io.Serial;
import org.springframework.security.authentication.AuthenticationServiceException;

/// 查会话时的非暂时失败：identity 回了 503 以外的错误（它那边 Redis 配置错、键被写坏等）、
/// 响应解析不了。
///
/// 是缺陷不是「稍后再试」，按服务端错误（500）输出，不伪装成 503 或 401。文案固定，不含令牌。
public final class SessionLookupFailedException extends AuthenticationServiceException {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public SessionLookupFailedException(Throwable cause) {
    super("查会话失败", cause);
  }
}
