package dev.linqibin.starter.httpinterface.config;

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

  /// 连接池配置
  ///
  /// 控制 Apache HttpClient 连接池行为，包括最大连接数、空闲连接清理等。
  /// 使用 Apache HttpClient 替代 JDK HttpClient 以获得更可靠的 stale connection 检测。
  private ConnectionPoolProperties connectionPool = new ConnectionPoolProperties();

  /// 错误处理配置
  private ErrorHandlingProperties errorHandling = new ErrorHandlingProperties();

  /// 重试配置（保留以兼容现有配置，但 Apache HttpClient 内置连接管理通常不需要额外重试）
  private RetryProperties retry = new RetryProperties();

  /// 服务分组配置（key 为分组名称）
  private Map<String, ServiceGroupProperties> groups = new HashMap<>();

  /// Apache HttpClient 连接池配置属性
  ///
  /// 使用 Apache HttpClient 5.x 的连接池管理，提供：
  ///
  /// - 主动 stale connection 检测（`validateAfterInactivity`）
  /// - 自动空闲连接清理（`evictIdleConnectionsAfter`）
  /// - 精细的连接池大小控制
  ///
  /// **解决的问题：**
  ///
  /// - "HTTP/1.1 header parser received no bytes" 错误
  /// - "EOF reached while reading" 错误
  /// - JDK HttpClient stale connection 检测不可靠的问题
  @Data
  public static class ConnectionPoolProperties {

    /// 总最大连接数（默认 100）
    ///
    /// 连接池中所有路由的最大连接总数。
    private int maxConnTotal = 100;

    /// 单路由最大连接数（默认 20）
    ///
    /// 单个目标主机的最大连接数。
    private int maxConnPerRoute = 20;

    /// 验证空闲连接的时间阈值（默认 5 秒）
    ///
    /// 连接空闲超过此时间后，下次使用前会先验证连接是否有效。
    /// 这是防止 stale connection 的关键配置。
    private Duration validateAfterInactivity = Duration.ofSeconds(5);

    /// 空闲连接清理间隔（默认 30 秒）
    ///
    /// 空闲超过此时间的连接会被主动清理。
    /// 此值应小于服务端的 `server.tomcat.keep-alive-timeout`。
    private Duration evictIdleConnectionsAfter = Duration.ofSeconds(30);
  }

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

  /// 重试配置属性
  ///
  /// 控制 stale connection 错误的自动重试行为。
  @Data
  public static class RetryProperties {

    /// 是否启用 stale connection 重试（默认启用）
    private boolean enabled = true;

    /// 最大重试次数（不包括首次请求，默认 2 次）
    private int maxRetries = 2;
  }
}
