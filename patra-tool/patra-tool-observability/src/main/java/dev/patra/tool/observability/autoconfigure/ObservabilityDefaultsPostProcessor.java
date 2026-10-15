package dev.patra.tool.observability.autoconfigure;

import dev.patra.tool.core.PatraOrders;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/// 日志与 actuator 的默认配置（决策 D）：以最低优先级并入 `defaultProperties`，服务 yml、Nacos、环境变量都能覆盖。
///
/// 每个键只能有一个归属模块出默认值，本模块出的键见 `DEFAULTS`；`logging.*` 必须在这里给，
/// `@PropertySource` 加入得太晚，日志系统初始化时读不到。
public final class ObservabilityDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 本模块出的默认键。
  static final Map<String, Object> DEFAULTS =
      Map.of(
          "logging.file.path", "${PATRA_LOG_DIR:logs}",
          "logging.level.dev.patra", "DEBUG",
          "logging.config", "classpath:dev/patra/tool/observability/logback-patra.xml",
          "management.endpoints.web.exposure.include", "health,info,metrics",
          "management.endpoint.health.show-details", "when-authorized",
          "management.metrics.tags.application", "${spring.application.name}");

  /// 并入默认值；`defaultProperties` 里已有的键不动（`addOrMerge` 会用新值覆盖旧值，所以先剔除）。
  ///
  /// @param environment 当前环境
  /// @param application 正在启动的应用
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults = new LinkedHashMap<>(DEFAULTS);
    PropertySource<?> existing =
        environment.getPropertySources().get(DefaultPropertiesPropertySource.NAME);
    if (existing != null) {
      defaults.keySet().removeIf(existing::containsProperty);
    }
    DefaultPropertiesPropertySource.addOrMerge(defaults, environment.getPropertySources());
  }

  /// 最后运行：别的 EPP（含 launch 的 Nacos 默认值）先放进去的键优先。
  ///
  /// @return 最低优先级
  @Override
  public int getOrder() {
    return PatraOrders.DEFAULTS_POST_PROCESSOR;
  }
}
