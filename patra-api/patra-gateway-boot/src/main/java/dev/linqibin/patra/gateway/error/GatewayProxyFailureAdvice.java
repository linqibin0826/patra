package dev.linqibin.patra.gateway.error;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.web.error.handler.GlobalRestExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/// 代理失败先经网关这一层：把 `RestClient` 到不了下游的异常和 LoadBalancer 无实例的异常包成网关自己的
/// 应用异常（固定文案、明确错误码），再交给 starter-web 的全局处理器渲染。排在全局处理器之前，
/// 否则 ProblemDetail 的 `detail` 会原样回显异常消息，把下游实例地址和内部路径泄露给调用方。
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayProxyFailureAdvice {

  private final HttpStdErrors.Group http;
  private final GlobalRestExceptionHandler globalHandler;

  /// 构造。
  ///
  /// @param http 按网关前缀生成错误码的组
  /// @param globalHandler starter-web 的全局处理器
  public GatewayProxyFailureAdvice(
      HttpStdErrors.Group http, GlobalRestExceptionHandler globalHandler) {
    this.http = http;
    this.globalHandler = globalHandler;
  }

  /// 包装后交给全局处理器；分不出类别的原样交过去。
  ///
  /// @param ex 代理失败异常
  /// @param request HTTP 请求
  /// @param response HTTP 响应
  /// @return 全局处理器的响应
  @ExceptionHandler({ResourceAccessException.class, HttpServerErrorException.class})
  public ResponseEntity<ProblemDetail> handleProxyFailure(
      RuntimeException ex, HttpServletRequest request, HttpServletResponse response) {
    Exception toRender =
        ProxyFailureClassifier.wrap(ex, http).map(Exception.class::cast).orElse(ex);
    return globalHandler.handleException(toRender, request, response);
  }
}
