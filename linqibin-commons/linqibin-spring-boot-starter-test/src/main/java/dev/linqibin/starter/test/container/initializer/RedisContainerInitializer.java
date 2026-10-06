package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.ContainerRegistry;
import dev.linqibin.starter.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/// Redis 测试容器初始化器。
///
/// 同一个 JVM 里只启动一个容器，注册到 {@link ContainerRegistry}，多个测试类共用。
/// 镜像版本与 mini 上的 Redis 一致。
///
/// 用法：`@ContextConfiguration(initializers = RedisContainerInitializer.class)`。
public class RedisContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RedisContainerInitializer.class);

  private static final String REDIS_IMAGE = "redis:7.0.15";

  private static final int REDIS_PORT = 6379;

  private static final Object LOCK = new Object();

  /// 返回已启动的 Redis 容器，第一次调用时启动。
  ///
  /// @return Redis 容器
  public static GenericContainer<?> getRedisContainer() {
    synchronized (LOCK) {
      if (!ContainerRegistry.isRegistered(ContainerType.REDIS)) {
        GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);
        redis.start();
        ContainerRegistry.register(ContainerType.REDIS, redis);
        log.info("Redis 容器已启动: {}:{}", redis.getHost(), redis.getMappedPort(REDIS_PORT));
      }
      return ContainerRegistry.get(ContainerType.REDIS, GenericContainer.class);
    }
  }

  /// 把容器的连接参数写进 Spring 环境。
  ///
  /// `url` 的优先级高于 `host`/`port`，三个都写，避免被 dev 配置里的 `url` 盖掉。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    GenericContainer<?> redis = getRedisContainer();
    String host = redis.getHost();
    int port = redis.getMappedPort(REDIS_PORT);
    TestPropertyValues.of(
            "spring.data.redis.host=" + host,
            "spring.data.redis.port=" + port,
            "spring.data.redis.url=redis://" + host + ":" + port)
        .applyTo(applicationContext.getEnvironment());
  }
}
