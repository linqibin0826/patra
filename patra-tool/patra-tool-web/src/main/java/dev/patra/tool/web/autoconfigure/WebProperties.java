package dev.patra.tool.web.autoconfigure;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// Web 模块配置，前缀 `patra.web`。
///
/// @param enabled 总开关
/// @param error 错误解析与 ProblemDetail 输出
@Validated
@ConfigurationProperties(prefix = "patra.web")
public record WebProperties(@DefaultValue("true") boolean enabled, @DefaultValue Error error) {

  /// 错误处理。
  ///
  /// @param enabled 关掉后不注册引擎与全局处理器
  /// @param contextPrefix 错误码前缀（CAT、REG…），空则用 UNKNOWN
  /// @param typeBaseUrl ProblemDetail `type` 的基址
  /// @param includeStack 响应里是否带堆栈
  /// @param engine 解析引擎
  /// @param observation 慢解析观测
  /// @param trace 追踪 ID 来源
  public record Error(
      @DefaultValue("true") boolean enabled,
      String contextPrefix,
      @DefaultValue("https://errors.example.com/") String typeBaseUrl,
      @DefaultValue("false") boolean includeStack,
      @DefaultValue Engine engine,
      @DefaultValue Observation observation,
      @DefaultValue Trace trace) {

    /// 解析引擎参数。
    ///
    /// @param maxCauseDepth 遍历 cause 链的最大深度
    /// @param enableTraitMapping 是否按 ErrorTrait 语义映射
    /// @param enableNamingHeuristic 是否按异常类名启发式映射
    public record Engine(
        @DefaultValue("10") @Min(1) int maxCauseDepth,
        @DefaultValue("true") boolean enableTraitMapping,
        @DefaultValue("true") boolean enableNamingHeuristic) {}

    /// 慢解析观测。
    ///
    /// @param enabled 开关
    /// @param slowThresholdMs 超过多少毫秒算慢
    /// @param logSlowResolution 慢解析是否打 WARN
    public record Observation(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("200") @Min(0) long slowThresholdMs,
        @DefaultValue("true") boolean logSlowResolution) {}

    /// 追踪 ID 从 MDC 的哪些键取。
    ///
    /// @param headerNames 候选键，按顺序取第一个非空的
    public record Trace(
        @DefaultValue({"traceId", "X-B3-TraceId", "traceparent"}) List<String> headerNames) {
      public Trace {
        headerNames = List.copyOf(headerNames);
      }
    }
  }
}
