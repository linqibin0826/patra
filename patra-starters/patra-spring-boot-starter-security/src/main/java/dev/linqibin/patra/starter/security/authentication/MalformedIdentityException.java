package dev.linqibin.patra.starter.security.authentication;

import org.springframework.security.authentication.AuthenticationServiceException;

/// 内部令牌正确、但身份头残缺或格式不合法时抛出。
///
/// 只可能是网关自己的缺陷，所以按服务端错误（500）处理，不伪装成 401。
public class MalformedIdentityException extends AuthenticationServiceException {

  /// 创建异常。
  ///
  /// @param reason 不合法的原因，只含头名，不含头的值
  public MalformedIdentityException(String reason) {
    super("网关传来的身份头不合法: " + reason);
  }
}
