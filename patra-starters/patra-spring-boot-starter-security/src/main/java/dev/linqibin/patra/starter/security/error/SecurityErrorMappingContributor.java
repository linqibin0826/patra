package dev.linqibin.patra.starter.security.error;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;

/// 把 Spring Security 的异常映射到统一错误码。
///
/// 没有它，错误解析引擎会把拒绝访问解析成 500、把凭据错误解析成 422。
public class SecurityErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 创建映射。
  ///
  /// @param http 带服务前缀的标准 HTTP 错误码组
  public SecurityErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /// 映射安全异常。判断顺序从最具体的类型到最宽的类型。
  ///
  /// @param exception 要映射的异常
  /// @return 错误码；不是安全异常时返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof MalformedIdentityException) {
      return Optional.of(http.INTERNAL_ERROR());
    }
    if (exception instanceof AuthenticationServiceException) {
      return Optional.of(http.UNAVAILABLE());
    }
    if (exception instanceof AuthenticationException) {
      return Optional.of(http.UNAUTHORIZED());
    }
    if (exception instanceof AccessDeniedException) {
      return Optional.of(http.FORBIDDEN());
    }
    return Optional.empty();
  }
}
