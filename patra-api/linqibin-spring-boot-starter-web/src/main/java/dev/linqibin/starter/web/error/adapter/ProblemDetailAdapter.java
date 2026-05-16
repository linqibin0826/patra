package dev.linqibin.starter.web.error.adapter;

import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;

/// 将异常转换为一致的 `ProblemDetail` 响应的适配器。
public interface ProblemDetailAdapter {

  /// 将提供的异常转换为 {@link ProblemDetailResponse}。
  ///
  /// @param exception 正在处理的异常
  /// @param request HTTP 请求上下文；在非 servlet 流中可能为 `null`
  /// @return 已解析的 ProblemDetail 元数据和 HTTP 状态
  ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request);
}
