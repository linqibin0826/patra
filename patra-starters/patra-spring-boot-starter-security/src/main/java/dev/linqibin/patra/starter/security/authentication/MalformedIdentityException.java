package dev.linqibin.patra.starter.security.authentication;

import org.springframework.security.authentication.AuthenticationServiceException;

/// 断言签名正确、但内容不合法时抛出：`sub`、`sid` 不是正整数，或账号 / 客户端类型不认识。
///
/// 只可能是网关自己的缺陷，所以按服务端错误（500）处理，不伪装成 401。
public class MalformedIdentityException extends AuthenticationServiceException {

  /// 创建异常。
  ///
  /// @param reason 不合法的原因，只含声明名，不含声明的值
  public MalformedIdentityException(String reason) {
    super("身份断言的内容不合法: " + reason);
  }
}
