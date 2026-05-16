package dev.linqibin.starter.web.error.spi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/// 用于向 {@link org.springframework.http.ProblemDetail} 贡献 Web 特定扩展字段的 SPI。 提供对 {@link
/// jakarta.servlet.http.HttpServletRequest} 的访问,以便实现可以提取请求上下文进行诊断。
///
/// @author linqibin
/// @since 0.1.0
/// @see dev.linqibin.starter.web.error.builder.ProblemDetailBuilder
public interface WebProblemFieldContributor {

  /// 向 `ProblemDetail` 贡献扩展字段,包括 Web 特定的上下文。
  ///
  /// @param fields 将被合并到响应中的可变映射
  /// @param exception 当前异常
  /// @param request HTTP 请求
  void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request);
}
