package com.patra.starter.feign.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Feign 错误解码行为、宽容模式和可观测性的配置属性
/// 
/// 通过 `patra.feign.problem` 前缀配置 Feign 错误处理行为。
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@ConfigurationProperties(prefix = "patra.feign.problem")
public class FeignErrorProperties {

  /// 整体启用或禁用错误解码器
  private boolean enabled = true;

  /// 启用宽容模式(将非 ProblemDetail 载荷包装在 `RemoteCallException` 中)
  private boolean tolerant = true;

  /// 从下游错误响应读取的最大字节数
  private int maxErrorBodySize = 64 * 1024;

  /// 在宽容模式响应中包含堆栈跟踪(仅用于调试)
  private boolean includeStackTrace = false;

  /// 观察/度量配置
  private ObservationProperties observation = new ObservationProperties();

  /// 观察阈值和日志记录的嵌套配置
  @Data
  public static class ObservationProperties {
    /// 启用观察记录器
    private boolean enabled = true;

    /// 记录和标记慢于此阈值的解析操作(毫秒)
    private long slowParsingThresholdMs = 150;

    /// 当解析超过慢阈值时发出日志行
    private boolean logSlowParsing = true;

    /// 记录和标记慢于此阈值的响应体读取(毫秒)
    private long slowBodyReadingThresholdMs = 80;

    /// 当读取响应体超过慢阈值时发出日志行
    private boolean logSlowBodyReading = true;

    /// 当调用宽容模式时发出信息日志
    private boolean logTolerantUsage = true;
  }
}
