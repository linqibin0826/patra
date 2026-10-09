package dev.linqibin.patra.gateway.security;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import java.util.Optional;

/// 网关的安全异常映射：在 starter 的表上加一行，`SessionLookupFailedException` → 0500。
///
/// 声明成 Bean 后 starter 那个 `@ConditionalOnMissingBean` 的让位，不存在两个映射谁先谁后的问题。
public final class GatewaySecurityErrorMappingContributor extends SecurityErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 创建映射。
  ///
  /// @param http 按网关前缀生成错误码的组
  public GatewaySecurityErrorMappingContributor(HttpStdErrors.Group http) {
    super(http);
    this.http = http;
  }

  /// 先判网关自己的异常，其余交给 starter 的表。
  ///
  /// @param exception 要映射的异常
  /// @return 错误码；不是安全异常时返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof SessionLookupFailedException) {
      return Optional.of(http.INTERNAL_ERROR());
    }
    return super.mapException(exception);
  }
}
