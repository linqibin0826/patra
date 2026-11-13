package dev.patra.notification.support;

import dev.patra.notification.PatraNotificationApplication;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/// 同一个 JVM 里的第二个通知服务实例（#406 的多实例测试）：和测试类自己的上下文共用 PostgreSQL、RabbitMQ 容器与假
/// identity / interaction，端口随机。
///
/// 身份断言的测试密钥不用另配：`patra-tool-security` 的测试夹具经 `spring.factories` 里的环境后处理器装进每个
/// `SpringApplication`。
public final class SecondNotificationInstance implements AutoCloseable {

  private final ConfigurableApplicationContext context;

  private SecondNotificationInstance(ConfigurableApplicationContext context) {
    this.context = context;
  }

  /// 启动。
  ///
  /// @param identityBaseUrl 假 identity 的地址
  /// @param interactionBaseUrl 假 interaction 的地址
  /// @return 已启动的实例
  public static SecondNotificationInstance start(
      String identityBaseUrl, String interactionBaseUrl) {
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(PatraNotificationApplication.class)
            .profiles("test")
            .initializers(
                new NotificationITPostgreSQLContainerInitializer(),
                new RabbitMQContainerInitializer())
            // 用命令行参数而不是 properties()：后者是默认属性，优先级最低，会被 application.yml 与测试配置盖掉
            .run(
                "--server.port=0",
                "--spring.cloud.discovery.enabled=true",
                // 测试配置默认不起监听；不打开的话广播监听和信箱监听都不会起
                "--spring.rabbitmq.listener.simple.auto-startup=true",
                "--spring.cloud.discovery.client.simple.instances.patra-identity[0].uri="
                    + identityBaseUrl,
                "--spring.cloud.discovery.client.simple.instances.patra-interaction[0].uri="
                    + interactionBaseUrl,
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.datasource.hikari.minimum-idle=1");
    return new SecondNotificationInstance(context);
  }

  /// HTTP 端口。
  ///
  /// @return 端口
  public int port() {
    return context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
  }

  /// 取这个实例里的 bean。
  ///
  /// @param type 类型
  /// @param <T> 类型
  /// @return bean
  public <T> T bean(Class<T> type) {
    return context.getBean(type);
  }

  /// 关掉这个实例。
  @Override
  public void close() {
    context.close();
  }
}
