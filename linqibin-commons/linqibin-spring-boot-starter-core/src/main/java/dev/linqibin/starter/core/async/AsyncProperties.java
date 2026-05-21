package dev.linqibin.starter.core.async;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 通用异步线程池配置属性。
///
/// 支持配置多个命名线程池，按业务场景使用。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   async:
///     enabled: true
///     pools:
///       cache-upload:
///         core-size: 2
///         max-size: 4
///         queue-capacity: 50
///       data-sync:
///         core-size: 4
///         max-size: 8
///         queue-capacity: 200
/// ```
///
/// **使用方式**：
///
/// ```java
/// @Autowired
/// private AsyncExecutorRegistry asyncExecutorRegistry;
///
/// public void doAsync() {
///   CompletableFuture.runAsync(
///       () -> { ... },
///       asyncExecutorRegistry.getExecutor("cache-upload")
///   );
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "linqibin.starter.core.async")
public class AsyncProperties {

  /// 是否启用异步线程池（默认 true）。
  private boolean enabled = true;

  /// 命名线程池配置映射。
  ///
  /// Key 为线程池名称（如 "cache-upload"、"data-sync"），
  /// Value 为对应的线程池配置。
  private Map<String, AsyncPoolProperties> pools = new HashMap<>();

  /// 判断是否启用异步线程池。
  ///
  /// @return 是否启用
  public boolean isEnabled() {
    return enabled;
  }

  /// 设置是否启用异步线程池。
  ///
  /// @param enabled 是否启用
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /// 获取命名线程池配置映射。
  ///
  /// @return 命名线程池配置映射
  public Map<String, AsyncPoolProperties> getPools() {
    return pools;
  }

  /// 设置命名线程池配置映射。
  ///
  /// @param pools 命名线程池配置映射
  public void setPools(Map<String, AsyncPoolProperties> pools) {
    this.pools = pools;
  }
}
