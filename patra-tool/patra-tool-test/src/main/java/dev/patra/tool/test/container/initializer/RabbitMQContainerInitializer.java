package dev.patra.tool.test.container.initializer;

import dev.patra.tool.test.container.ContainerRegistry;
import dev.patra.tool.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/// RabbitMQ 测试容器初始化器。
///
/// 同一个 JVM 里只启动一个容器，注册到 {@link ContainerRegistry}，多个测试类共用。
/// 镜像版本与 compose 里的 RabbitMQ 一致。
///
/// 用法：`@ContextConfiguration(initializers = RabbitMQContainerInitializer.class)`。
/// 要模拟 broker 不可达，用 `getRabbitMQContainer().execInContainer("rabbitmqctl", "stop_app")`
/// 停掉应用再 `start_app`：容器不停，映射端口不变。
public class RabbitMQContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQContainerInitializer.class);

  private static final String RABBITMQ_IMAGE = "rabbitmq:4.4.0-management";

  private static final Object LOCK = new Object();

  /// 返回已启动的 RabbitMQ 容器，第一次调用时启动。
  ///
  /// @return RabbitMQ 容器
  public static RabbitMQContainer getRabbitMQContainer() {
    synchronized (LOCK) {
      if (!ContainerRegistry.isRegistered(ContainerType.RABBITMQ)) {
        RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse(RABBITMQ_IMAGE));
        rabbitmq.start();
        ContainerRegistry.register(ContainerType.RABBITMQ, rabbitmq);
        log.info("RabbitMQ 容器已启动: {}:{}", rabbitmq.getHost(), rabbitmq.getAmqpPort());
      }
      return ContainerRegistry.get(ContainerType.RABBITMQ, RabbitMQContainer.class);
    }
  }

  /// 把容器的连接参数写进 Spring 环境。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    RabbitMQContainer rabbitmq = getRabbitMQContainer();
    TestPropertyValues.of(
            "spring.rabbitmq.host=" + rabbitmq.getHost(),
            "spring.rabbitmq.port=" + rabbitmq.getAmqpPort(),
            "spring.rabbitmq.username=" + rabbitmq.getAdminUsername(),
            "spring.rabbitmq.password=" + rabbitmq.getAdminPassword())
        .applyTo(applicationContext.getEnvironment());
  }
}
