package dev.linqibin.patra.starter.security.error;

import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.json.JsonMapper;

/// 在安全过滤器链里把失败输出成统一的 ProblemDetail。
///
/// 过滤器链里的失败到不了全局异常处理器，由这个类直接写响应。它同时充当
/// 未登录的入口点、拒绝访问的处理器和认证失败的处理器。
@Slf4j
public class SecurityProblemWriter
    implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

  private static final String BEARER_CHALLENGE = "Bearer";

  private final ProblemDetailAdapter problemDetailAdapter;
  private final JsonMapper jsonMapper;

  /// 创建写出器。
  ///
  /// @param problemDetailAdapter 把异常变成 ProblemDetail 的适配器
  /// @param jsonMapper 容器里的 JSON 映射器（已带 ProblemDetail 的序列化规则）
  public SecurityProblemWriter(ProblemDetailAdapter problemDetailAdapter, JsonMapper jsonMapper) {
    this.problemDetailAdapter = problemDetailAdapter;
    this.jsonMapper = jsonMapper;
  }

  /// 需要登录但没登录时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param authException 触发的认证异常
  /// @throws IOException 写响应失败时
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(request, response, authException);
  }

  /// 登录了但不允许访问时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param accessDeniedException 触发的拒绝访问异常
  /// @throws IOException 写响应失败时
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(request, response, accessDeniedException);
  }

  /// 认证过滤器里建立认证失败时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param exception 触发的认证异常
  /// @throws IOException 写响应失败时
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    write(request, response, exception);
  }

  /// 把异常写成 ProblemDetail 响应。
  ///
  /// `detail` 换成固定短句，`instance` 显式设成请求路径：这两个字段在控制器路径上
  /// 分别来自异常消息和 Spring MVC，过滤器里都得自己处理。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param exception 要输出的异常
  /// @throws IOException 写响应失败时
  private void write(
      HttpServletRequest request, HttpServletResponse response, RuntimeException exception)
      throws IOException {
    ProblemDetailResponse adapted = problemDetailAdapter.adapt(exception, request);
    HttpStatus status = adapted.httpStatus();
    ProblemDetail problemDetail = adapted.problemDetail();
    problemDetail.setDetail(clientDetailFor(status));
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    if (status.is5xxServerError()) {
      log.error(
          "安全过滤器链返回 {}: {} {}",
          status.value(),
          request.getMethod(),
          request.getRequestURI(),
          exception);
    } else {
      log.debug(
          "安全过滤器链返回 {}: {} {}，原因: {}",
          status.value(),
          request.getMethod(),
          request.getRequestURI(),
          exception.getMessage());
    }

    response.setStatus(status.value());
    if (status == HttpStatus.UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
    }
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problemDetail);
  }

  /// 给客户端看的固定短句，不带框架异常的原始消息。
  ///
  /// @param status 响应状态
  /// @return 固定短句
  private static String clientDetailFor(HttpStatus status) {
    return switch (status) {
      case UNAUTHORIZED -> "Authentication required";
      case FORBIDDEN -> "Access denied";
      default -> status.getReasonPhrase();
    };
  }
}
