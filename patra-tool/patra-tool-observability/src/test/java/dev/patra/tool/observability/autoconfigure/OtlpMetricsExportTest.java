package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// 本模块的类路径要让 Boot 的 OTLP 指标导出生效：七个服务都经它把指标推给 OTel Collector。
///
/// Boot 4.1 的 `OtlpMetricsExportAutoConfiguration` 除了 `OtlpMeterRegistry` 还要求
/// `spring-boot-opentelemetry` 的 `OpenTelemetryProperties`，缺一个就静默跳过、不报错。
class OtlpMetricsExportTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MetricsAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class));

  /// 类路径齐全时 Boot 建出 OTLP 注册表。
  @Test
  void should_create_otlp_meter_registry_from_module_classpath() {
    runner.run(ctx -> assertThat(ctx).hasBean("otlpMeterRegistry"));
  }
}
