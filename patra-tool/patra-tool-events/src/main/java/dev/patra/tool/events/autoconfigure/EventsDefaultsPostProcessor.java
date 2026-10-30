package dev.patra.tool.events.autoconfigure;

import static java.util.Map.entry;

import dev.patra.tool.core.PatraOrders;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/// 事件登记表与 RabbitMQ 收发的默认配置：以最低优先级并入 `defaultProperties`，服务 yml、Nacos、环境变量都能覆盖。
///
/// 发布端：表由 Flyway 建、只登记带 `@ApplicationModuleListener` 的监听器（即 Modulith 的外送器，服务里普通的
/// `@TransactionalEventListener` 不进登记表）、发完即删、连接上开确认与退回、broker 不可达时 5 秒内失败；关掉 RabbitMQ 健康检查：
/// broker 停着时业务照常提交、事件留给补投，服务不该因此判成 DOWN（部署会被回滚），积压看登记表行数；
/// 消费端：容器在处理成功后才 ack（Spring 的 `AUTO`，即 RabbitMQ 的手动确认），失败按 1、2、4 秒退避重试 3 次，
/// 之后拒绝且不回队列，进死信；prefetch 10。理由见 `docs/patra/specs/2026-10-30-event-module-design.md` 4.9。
public final class EventsDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 本模块出的默认键。
  static final Map<String, Object> DEFAULTS =
      Map.ofEntries(
          entry("spring.modulith.events.jdbc.schema-initialization.enabled", "false"),
          entry("spring.modulith.events.completion-mode", "delete"),
          entry(
              "spring.modulith.events.registry-trigger-annotation",
              "org.springframework.modulith.events.ApplicationModuleListener"),
          entry("spring.modulith.events.rabbitmq.enable-json", "false"),
          entry("spring.rabbitmq.publisher-confirm-type", "correlated"),
          entry("spring.rabbitmq.publisher-returns", "true"),
          entry("spring.rabbitmq.connection-timeout", "5s"),
          entry("management.health.rabbit.enabled", "false"),
          entry("spring.rabbitmq.listener.simple.acknowledge-mode", "auto"),
          entry("spring.rabbitmq.listener.simple.prefetch", "10"),
          entry("spring.rabbitmq.listener.simple.default-requeue-rejected", "false"),
          entry("spring.rabbitmq.listener.simple.retry.enabled", "true"),
          entry("spring.rabbitmq.listener.simple.retry.max-retries", "3"),
          entry("spring.rabbitmq.listener.simple.retry.initial-interval", "1s"),
          entry("spring.rabbitmq.listener.simple.retry.multiplier", "2"),
          entry("spring.rabbitmq.listener.simple.retry.max-interval", "10s"));

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

  /// 最后运行：别的 EPP 先放进去的键优先。
  ///
  /// @return 最低优先级
  @Override
  public int getOrder() {
    return PatraOrders.DEFAULTS_POST_PROCESSOR;
  }
}
