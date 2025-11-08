package com.patra.ingest.integration.config;

import com.patra.ingest.integration.RocketMQContainerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * RocketMQ 容器初始化器。
 *
 * <p>提供 RocketMQ 5.3.1 容器的单例管理和动态配置注入。
 *
 * <h3>功能特性</h3>
 *
 * <ul>
 *   <li><strong>单例容器</strong>: 所有测试共享同一个 RocketMQ 容器实例 (NameServer + Broker)
 *   <li><strong>自动启动</strong>: 在类加载时启动容器（静态块）
 *   <li><strong>Topic 创建</strong>: 自动创建测试所需的 Topics (INGEST_TASK_READY, INGEST_LITERATURE_READY)
 *   <li><strong>动态配置</strong>: 自动注入 NameServer 地址到 Spring 测试上下文
 *   <li><strong>路由验证</strong>: 使用 Awaitility 确保 Topic 路由信息同步完成
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @SpringBootTest
 * @ContextConfiguration(initializers = RocketMQContainerInitializer.class)
 * class RocketMqOutboxPublisherIT {
 *
 *     @Autowired
 *     private RocketMqOutboxPublisher publisher;
 *
 *     @Test
 *     @DisplayName("应该发送消息到 RocketMQ")
 *     void shouldPublishMessage() {
 *         // 测试实现...
 *     }
 * }
 * }</pre>
 *
 * <h3>容器配置</h3>
 *
 * <ul>
 *   <li><strong>镜像版本</strong>: apache/rocketmq:5.3.1
 *   <li><strong>组件</strong>: NameServer + Broker (使用 Docker Compose 编排)
 *   <li><strong>自动创建 Topic</strong>: 启用 (仅测试环境)
 *   <li><strong>预创建 Topics</strong>: INGEST_TASK_READY, INGEST_LITERATURE_READY
 * </ul>
 *
 * <h3>性能表现</h3>
 *
 * <ul>
 *   <li>首次启动: ~30-40 秒 (NameServer + Broker + Topic 创建)
 *   <li>后续测试: 复用容器，无需重启
 * </ul>
 *
 * <h3>设计说明</h3>
 *
 * <p>采用 Docker Compose + ComposeContainer 方案，解决网络配置问题。
 *
 * <h4>核心技术突破</h4>
 * <ul>
 *   <li><strong>brokerIP1=127.0.0.1</strong>: Broker advertise 宿主机可访问的地址，解决容器内部 IP 无法访问的问题
 *   <li><strong>1:1 端口映射</strong>: 10911:10911，确保客户端连接端口与 Broker advertise 端口匹配
 *   <li><strong>Here-Document 格式</strong>: 使用 <<EOF 格式化配置文件，确保 RocketMQ 正确解析多行配置
 *   <li><strong>Docker Compose</strong>: 声明式配置，易于维护和调试
 * </ul>
 *
 * <h4>为什么不使用 GenericContainer?</h4>
 * <p>GenericContainer 的动态端口映射和容器内部 IP 检测机制，在 RocketMQ 场景下会导致：
 * <ul>
 *   <li>Broker 自动检测到容器内部 IP (如 172.17.0.x)，宿主机无法访问
 *   <li>客户端从 NameServer 获取到错误的 Broker 地址
 *   <li>动态端口映射导致端口不匹配
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 * @see ApplicationContextInitializer
 * @see RocketMQContainerSupport
 * @see MySQLContainerInitializer
 */
public class RocketMQContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RocketMQContainerInitializer.class);

  /**
   * RocketMQ 容器支持类单例实例。
   *
   * <p>包含 NameServer 和 Broker 容器的管理。
   */
  private static final RocketMQContainerSupport rocketmqSupport;

  // 静态初始化块：在类加载时启动容器并创建 Topics
  static {
    log.info("========================================");
    log.info("初始化 RocketMQ TestContainers");
    log.info("========================================");

    rocketmqSupport = new RocketMQContainerSupport();
    rocketmqSupport.start();

    log.info("RocketMQ 容器已启动");
    log.info("  - NameServer 地址: {}", rocketmqSupport.getNameserverAddress());

    // 创建测试所需的 Topics
    String[] topics = {"INGEST_TASK_READY", "INGEST_LITERATURE_READY"};
    for (String topic : topics) {
      log.info("创建测试 Topic: {}", topic);
      rocketmqSupport.createTopic(topic);
    }

    log.info("========================================");
    log.info("RocketMQ TestContainers 初始化完成");
    log.info("========================================");
  }

  /**
   * 初始化 Spring 应用上下文，注入 RocketMQ 动态配置。
   *
   * <p>注入的配置项:
   *
   * <ul>
   *   <li>{@code rocketmq.name-server}: NameServer 地址 (宿主机可访问，包含动态端口)
   * </ul>
   *
   * @param applicationContext Spring 应用上下文
   */
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    log.info("注入 RocketMQ 动态配置到 Spring 上下文");

    String nameServerAddr = rocketmqSupport.getNameserverAddress();

    TestPropertyValues.of("rocketmq.name-server=" + nameServerAddr)
        .applyTo(applicationContext.getEnvironment());

    log.info("RocketMQ 动态配置注入完成");
    log.info("  - rocketmq.name-server: {}", nameServerAddr);
  }

  /**
   * 获取 RocketMQ 容器支持实例（供测试代码访问）。
   *
   * @return RocketMQ 容器支持实例
   */
  public static RocketMQContainerSupport getRocketMQSupport() {
    return rocketmqSupport;
  }
}
