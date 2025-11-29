package com.patra.starter.observability.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

///
/// Logback OTLP Appender 自动配置。
///
/// 在 Spring 上下文刷新后安装 OpenTelemetry Logback Appender，
/// 使应用日志能够通过 OTLP 协议发送到 OpenTelemetry Collector。
///
/// 前置条件：
///
/// - `opentelemetry-logback-appender-1.0` 在 classpath
/// - `OpenTelemetry` Bean 已配置
/// - `patra.observability.logging.enabled=true`（默认）
///
/// Logback 配置示例（logback-spring.xml）：
///
/// ```xml
/// <appender name="OpenTelemetry"
///   class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
///   <captureExperimentalAttributes>true</captureExperimentalAttributes>
///   <captureMdcAttributes>*</captureMdcAttributes>
/// </appender>
///
/// <root level="INFO">
///   <appender-ref ref="OpenTelemetry"/>
/// </root>
/// ```
///
/// @author Jobs
/// @since 1.0.0
/// @see OpenTelemetryAppender
///
@AutoConfiguration(after = OtelAutoConfiguration.class)
@ConditionalOnClass({OpenTelemetry.class, OpenTelemetryAppender.class})
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(
    prefix = "patra.observability.logging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LogbackOtlpAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(LogbackOtlpAutoConfiguration.class);

  private final OpenTelemetry openTelemetry;

  ///
  /// 构造函数。
  ///
  /// @param openTelemetry OpenTelemetry SDK 实例
  ///
  public LogbackOtlpAutoConfiguration(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  ///
  /// 在 Spring 上下文刷新后安装 OpenTelemetry Appender。
  ///
  /// 使用 `ContextRefreshedEvent` 确保在所有 Bean 初始化完成后执行，
  /// 这样 OpenTelemetry SDK 已完全配置好。
  ///
  /// @param event 上下文刷新事件
  ///
  @EventListener(ContextRefreshedEvent.class)
  public void installOpenTelemetryAppender(ContextRefreshedEvent event) {
    if (openTelemetry instanceof OpenTelemetrySdk sdk) {
      log.info("安装 OpenTelemetry Logback Appender");
      OpenTelemetryAppender.install(sdk);
    } else {
      log.warn("OpenTelemetry 实例不是 SDK 类型，无法安装 Logback Appender");
    }
  }
}
