package dev.linqibin.patra.common.security;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 需要登录但取不到当前用户时抛出。
///
/// 带 `UNAUTHORIZED` 特征，现有的错误解析引擎会把它解析成 `{PREFIX}-0401`。
public class AuthenticationRequiredException extends DomainException {

  /// 创建异常。消息是固定的英文短句，会作为错误响应的 `detail` 返回给客户端。
  public AuthenticationRequiredException() {
    super("Authentication required", StandardErrorTrait.UNAUTHORIZED);
  }
}
