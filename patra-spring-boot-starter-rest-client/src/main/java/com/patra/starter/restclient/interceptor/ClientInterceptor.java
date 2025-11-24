package com.patra.starter.restclient.interceptor;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/// HTTP 客户端拦截器扩展点。
///
/// <p>允许外部模块（如 patra-spring-boot-starter-observability）注入自定义逻辑。
///
/// <h2>设计目的</h2>
/// <p>本接口为 REST Client 提供插件式扩展能力，遵循开放封闭原则（OCP）和依赖倒置原则（DIP）。
/// 外部模块可以通过实现此接口来添加可观测性、安全、审计等横切关注点，而无需修改 core 模块代码。
///
/// <h2>生命周期钩子</h2>
/// <ul>
///   <li>{@link #beforeRequest} - HTTP 请求发送前执行（可用于添加 Header、记录日志等）
///   <li>{@link #afterResponse} - HTTP 响应接收后执行（可用于记录指标、提取追踪信息等）
///   <li>{@link #onError} - HTTP 请求过程中发生异常时执行（可用于错误记录、告警等）
/// </ul>
///
/// <h2>执行顺序</h2>
/// <p>拦截器按 {@link #getOrder()} 返回值从小到大执行，值越小优先级越高。
///
/// <h2>使用示例</h2>
/// <pre>{@code
/// @Component
/// @Order(10)
/// public class MetricsInterceptor implements ClientInterceptor {
///     private final MeterRegistry meterRegistry;
///
///     @Override
///     public void beforeRequest(HttpRequest request) {
///         // 记录请求开始时间
///     }
///
///     @Override
///     public void afterResponse(HttpRequest request, ClientHttpResponse response) {
///         // 记录请求成功指标
///     }
///
///     @Override
///     public void onError(HttpRequest request, Exception e) {
///         // 记录请求失败指标
///     }
/// }
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
public interface ClientInterceptor {

  /// HTTP 请求发送前执行。
  ///
  /// <p>可用于：
  /// <ul>
  ///   <li>添加自定义 Headers（如追踪 ID、认证 Token）
  ///   <li>记录请求日志
  ///   <li>启动性能计时器
  /// </ul>
  ///
  /// @param request HTTP 请求对象
  void beforeRequest(HttpRequest request);

  /// HTTP 响应接收后执行。
  ///
  /// <p>可用于：
  /// <ul>
  ///   <li>记录响应日志
  ///   <li>收集性能指标
  ///   <li>提取响应 Headers
  /// </ul>
  ///
  /// @param request HTTP 请求对象
  /// @param response HTTP 响应对象
  void afterResponse(HttpRequest request, ClientHttpResponse response);

  /// HTTP 请求过程中发生异常时执行。
  ///
  /// <p>可用于：
  /// <ul>
  ///   <li>记录错误日志
  ///   <li>收集失败指标
  ///   <li>发送告警
  /// </ul>
  ///
  /// @param request HTTP 请求对象
  /// @param e 异常对象
  void onError(HttpRequest request, Exception e);

  /// 执行优先级（值越小优先级越高）。
  ///
  /// <p>默认返回 0，可以覆盖此方法以指定优先级。
  ///
  /// @return 优先级值
  default int getOrder() {
    return 0;
  }
}
