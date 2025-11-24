/// REST Client 拦截器包。
///
/// <p>本包提供 HTTP 客户端拦截器扩展点和内置拦截器。
///
/// ## 职责
///
/// - 提供 {@link ClientInterceptor} 扩展点接口，允许外部模块注入自定义逻辑
/// - 提供 {@link LoggingInterceptor} 日志拦截器（基础设施层调试工具）
///
/// 注意: 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
/// 通过 {@link ClientInterceptor} 扩展点机制提供，符合关注点分离原则。
///
/// ## 核心组件
///
/// - {@link ClientInterceptor} - 拦截器扩展点接口（插件式架构）
/// - {@link LoggingInterceptor} - 日志拦截器（可选，用于调试）
///
/// ## 扩展点说明
///
/// ### ClientInterceptor 接口
///
/// **设计目的**: 为 REST Client 提供插件式扩展能力，遵循开放封闭原则（OCP）和依赖倒置原则（DIP）。
///
/// **生命周期钩子**:
/// - `beforeRequest(HttpRequest)` - HTTP 请求发送前执行
/// - `afterResponse(HttpRequest, ClientHttpResponse)` - HTTP 响应接收后执行
/// - `onError(HttpRequest, Exception)` - HTTP 请求过程中发生异常时执行
///
/// **执行顺序**: 拦截器按 `getOrder()` 返回值从小到大执行，值越小优先级越高。
///
/// ## 使用示例
///
/// ### 实现自定义拦截器
///
/// ```java
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
/// ```
///
/// ### 启用/禁用日志拦截器
///
/// ```yaml
/// patra:
///   rest-client:
///     interceptors:
///       logging:
///         enabled: true         # 启用日志拦截器
///         log-headers: false    # 避免记录敏感 Headers
///         log-body: false       # 避免记录大文件
/// ```
///
/// ## 可观测性支持
///
/// 追踪传播和指标收集由 patra-spring-boot-starter-observability 提供:
/// - 添加 patra-spring-boot-starter-observability 依赖自动启用
/// - 通过 ClientInterceptor 扩展点无缝集成
/// - 符合依赖倒置原则（DIP）和关注点分离（SoC）原则
///
/// @author linqibin
/// @since 0.1.0
package com.patra.starter.restclient.interceptor;
