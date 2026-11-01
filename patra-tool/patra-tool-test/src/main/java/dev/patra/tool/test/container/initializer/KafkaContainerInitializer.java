package dev.patra.tool.test.container.initializer;

import dev.patra.tool.test.container.ContainerRegistry;
import dev.patra.tool.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/// Kafka 测试容器初始化器。
///
/// 同一个 JVM 里只启动一个容器，注册到 {@link ContainerRegistry}，多个测试类共用。
/// 用 `apache/kafka-native`（GraalVM 编译的原生版，启动快、省内存，官方只建议用于测试），
/// 版本号与 mini 上 pipeline 栈的 `apache/kafka`（JVM 版）一致，CI 核对两边版本号。
///
/// 和 mini 不同：容器允许自动建 topic。测试要验证「topic 不存在时报错」的，自己先关掉或换 topic 名。
///
/// 用法：`@ContextConfiguration(initializers = KafkaContainerInitializer.class)`。
public class KafkaContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(KafkaContainerInitializer.class);

  private static final String KAFKA_IMAGE = "apache/kafka-native:4.3.2";

  private static final Object LOCK = new Object();

  /// 返回已启动的 Kafka 容器，第一次调用时启动。
  ///
  /// @return Kafka 容器
  public static KafkaContainer getKafkaContainer() {
    synchronized (LOCK) {
      if (!ContainerRegistry.isRegistered(ContainerType.KAFKA)) {
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
        kafka.start();
        ContainerRegistry.register(ContainerType.KAFKA, kafka);
        log.info("Kafka 容器已启动: {}", kafka.getBootstrapServers());
      }
      return ContainerRegistry.get(ContainerType.KAFKA, KafkaContainer.class);
    }
  }

  /// 把容器的连接地址写进 Spring 环境。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    TestPropertyValues.of(
            "spring.kafka.bootstrap-servers=" + getKafkaContainer().getBootstrapServers())
        .applyTo(applicationContext.getEnvironment());
  }
}
