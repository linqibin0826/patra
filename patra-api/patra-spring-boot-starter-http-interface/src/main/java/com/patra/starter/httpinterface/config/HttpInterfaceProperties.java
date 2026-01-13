package com.patra.starter.httpinterface.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// HTTP Interface 客户端配置属性
///
/// 通过 `patra.http.interface` 前缀配置 HTTP Interface 客户端行为，包括：
///
/// - 全局超时设置
/// - 错误处理策略
/// - 服务分组配置
/// - 可观测性配置
///
/// **示例配置：**
/// ```yaml
/// patra:
///   http:
///     interface:
///       enabled: true
///       connect-timeout: 5s
///       read-timeout: 30s
///       error-handling:
///         tolerant: true
///       groups:
///         registry:
///           base-url: lb://patra-registry
///           read-timeout: 10s
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Data
@ConfigurationProperties(prefix = "patra.http.interface")
public class HttpInterfaceProperties {

  /// 整体启用或禁用 HTTP Interface 自动配置
  private boolean enabled = true;

  /// 全局连接超时时间（默认 5 秒）
  private Duration connectTimeout = Duration.ofSeconds(5);

  /// 全局读取超时时间（默认 30 秒）
  private Duration readTimeout = Duration.ofSeconds(30);

  /// 错误处理配置
  private ErrorHandlingProperties errorHandling = new ErrorHandlingProperties();

  /// 服务分组配置（key 为分组名称）
  private Map<String, ServiceGroupProperties> groups = new HashMap<>();

  /// 错误处理配置属性
  @Data
  public static class ErrorHandlingProperties {

    /// 启用 ProblemDetail (RFC 7807) 错误解析
    private boolean problemDetailEnabled = true;

    /// 启用宽容模式（将非 ProblemDetail 响应包装为 RemoteCallException）
    private boolean tolerant = true;

    /// 从下游错误响应读取的最大字节数（默认 64KB）
    private int maxErrorBodySize = 64 * 1024;
  }

  /// 服务分组配置属性
  ///
  /// 每个服务分组可以配置独立的超时时间和基础 URL。
  /// 使用 `lb://` 前缀启用 Spring Cloud LoadBalancer 支持。
  @Data
  public static class ServiceGroupProperties {

    /// 服务基础 URL（支持 `lb://service-name` 格式）
    private String baseUrl;

    /// 该分组的连接超时时间（覆盖全局配置）
    private Duration connectTimeout;

    /// 该分组的读取超时时间（覆盖全局配置）
    private Duration readTimeout;
  }
}
