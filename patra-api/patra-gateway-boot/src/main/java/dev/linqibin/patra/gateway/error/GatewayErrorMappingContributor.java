package dev.linqibin.patra.gateway.error;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/// 网关到不了下游时的错误码：到不了（连接被拒、连接超时、域名解析失败、无实例）是 503，等不到（读超时）是 504。
///
/// 下游自己的状态码经 `RestClient.exchange(…, false)` 原样成为响应、不抛异常，所以这里看到的
/// `HttpServerErrorException` 只会是 LoadBalancer 过滤器在找不到实例时抛的那个。
public class GatewayErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造。
  ///
  /// @param http 按网关前缀生成错误码的组
  public GatewayErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof HttpServerErrorException serverError) {
      return serverError.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
          ? Optional.of(http.UNAVAILABLE())
          : Optional.empty();
    }
    if (exception instanceof ResourceAccessException ioFailure) {
      return Optional.of(isReadTimeout(ioFailure) ? http.GATEWAY_TIMEOUT() : http.UNAVAILABLE());
    }
    return Optional.empty();
  }

  /// 沿原因链判断是不是读超时。连接阶段的失败（含 `HttpConnectTimeoutException`，它是
  /// `HttpTimeoutException` 的子类）先于读超时判定，归 503。
  ///
  /// @param exception RestClient 抛出的 I/O 异常
  /// @return 读超时为 true
  private static boolean isReadTimeout(ResourceAccessException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof HttpConnectTimeoutException
          || cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof UnresolvedAddressException) {
        return false;
      }
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
