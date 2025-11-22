package com.patra.ingest.integration.config;

import com.patra.ingest.integration.RocketMQContainerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// RocketMQ 容器初始化器。
/// 
/// 提供 RocketMQ 5.3.1 容器的单例管理和动态配置注入。
/// 
/// ### 功能特性
/// 
/// - **单例容器**: 所有测试共享同一个 RocketMQ 容器实例 (NameServer + Broker)
///   - **线程安全初始化**: 使用双重检查锁模式，确保并发场景下容器只启动一次
///   - **自动启动**: 在类加载时启动容器（静态块）
///   - **Topic 创建**: 自动创建测试所需的 Topics (INGEST_TASK_READY, INGEST_PUBLICATION_READY)
///   - **动态配置**: 自动注入 NameServer 地址到 Spring 测试上下文
///   - **路由验证**: 使用 Awaitility 确保 Topic 路由信息同步完成
/// 
/// ### 使用示例
/// 
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = RocketMQContainerInitializer.class)
/// class RocketMqOutboxPublisherIT {
/// 
///     @Autowired
///     private RocketMqOutboxPublisher publisher;
/// 
///     @Test
///     @DisplayName("应该发送消息到 RocketMQ")
///     void shouldPublishMessage() {
///         // 测试实现...
/// ```
/// 
/// ### 容器配置
/// 
/// - **镜像版本**: apache/rocketmq:5.3.1
///   - **组件**: NameServer + Broker (使用 Docker Compose 编排)
///   - **自动创建 Topic**: 启用 (仅测试环境)
///   - **预创建 Topics**: INGEST_TASK_READY, INGEST_PUBLICATION_READY
/// 
/// ### 性能表现
/// 
/// - 首次启动: ~30-40 秒 (NameServer + Broker + Topic 创建)
///   - 后续测试: 复用容器，无需重启
/// 
/// ### 并发安全保证
/// 
/// 在 Maven 并行测试模式（`-T 1C`）下，多个集成测试类可能同时加载此初始化器， 导致静态代码块并发执行。 为避免 Docker Compose
/// 端口冲突和资源竞争，采用 **双重检查锁（Double-Checked Locking）** 模式：
/// 
/// - **volatile 变量**: `initialized` 标志保证多线程可见性
///   - **第一次检查**: 避免已初始化情况下的锁竞争（性能优化）
///   - **synchronized 块**: 确保只有一个线程执行初始化逻辑
///   - **第二次检查**: 防止多个线程同时通过第一次检查后重复初始化
/// 
/// **问题场景**: 修复前，`RocketMqOutboxPublisherIT` 和 `TaskReadyMessageListenerIT` 并发启动时，两个线程同时调用 `docker compose up -d`， 导致端口 9876 冲突，容器启动失败，错误代码
/// 1。
/// 
/// **修复效果**: 修复后，第一个线程启动容器，其他线程等待并复用，避免并发冲突。
/// 
/// ### 设计说明
/// 
/// 采用 Docker Compose + ComposeContainer 方案，解决网络配置问题。
/// 
/// #### 核心技术突破
/// 
/// - **brokerIP1=127.0.0.1**: Broker advertise 宿主机可访问的地址，解决容器内部 IP 无法访问的问题
///   - **1:1 端口映射**: 10911:10911，确保客户端连接端口与 Broker advertise 端口匹配
///   - **Here-Document 格式**: 使用 <<EOF 格式化配置文件，确保 RocketMQ 正确解析多行配置
///   - **Docker Compose**: 声明式配置，易于维护和调试
/// 
/// #### 为什么不使用 GenericContainer?
/// 
/// GenericContainer 的动态端口映射和容器内部 IP 检测机制，在 RocketMQ 场景下会导致：
/// 
/// - Broker 自动检测到容器内部 IP (如 172.17.0.x)，宿主机无法访问
///   - 客户端从 NameServer 获取到错误的 Broker 地址
///   - 动态端口映射导致端口不匹配
/// 
/// @author linqibin
/// @since 0.2.0
/// @see ApplicationContextInitializer
/// @see RocketMQContainerSupport
/// @see MySQLContainerInitializer
public class RocketMQContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RocketMQContainerInitializer.class);

  /// RocketMQ 容器支持类单例实例。
/// 
/// 负责容器生命周期管理（启动、停止）。
  private static volatile RocketMQContainerSupport rocketmqSupport;

  /// RocketMQ Topic 管理工具单例实例。
/// 
/// 负责 Topic 的创建、删除和验证。
  private static volatile RocketMQTopicAdmin topicAdmin;

  /// 初始化状态标志，使用 volatile 确保多线程可见性。
  private static volatile boolean initialized = false;

  /// 同步锁对象，用于保护初始化过程的线程安全。
  private static final Object LOCK = new Object();

  // 静态初始化块：在类加载时触发容器初始化
  static {
    initializeContainer();
  }

  /// 初始化 RocketMQ 容器（线程安全的单例模式）。
/// 
/// 使用双重检查锁（Double-Checked Locking）模式确保：
/// 
/// - 容器只启动一次，即使多个测试类并发加载
///   - 避免 Docker Compose 端口冲突和资源竞争
///   - 线程安全，防止竞态条件
/// 
/// ### 并发场景
/// 
/// 在 Maven 并行测试模式（-T 1C）下，多个测试类可能同时加载此类，导致静态代码块并发执行。 双重检查锁确保只有第一个线程执行初始化，其他线程等待并复用已初始化的容器。
/// 
/// @throws IllegalStateException 如果容器启动失败
  private static void initializeContainer() {
    // 第一次检查：避免已初始化情况下的锁竞争
    if (!initialized) {
      synchronized (LOCK) {
        // 第二次检查：确保只有一个线程执行初始化
        if (!initialized) {
          log.info("========================================");
          log.info("初始化 RocketMQ TestContainers (线程: {})", Thread.currentThread().getName());
          log.info("========================================");

          try {
            // 启动容器
            rocketmqSupport = new RocketMQContainerSupport();
            rocketmqSupport.start();

            log.info("RocketMQ 容器已启动");
            log.info("  - NameServer 地址: {}", rocketmqSupport.getNameserverAddress());

            // 初始化 Topic 管理工具
            topicAdmin = new RocketMQTopicAdmin(rocketmqSupport.getComposeContainer());

            // 创建测试所需的 Topics
            String[] topics = {"INGEST_TASK_READY", "INGEST_PUBLICATION_READY"};
            for (String topic : topics) {
              log.info("创建测试 Topic: {}", topic);
              topicAdmin.createTopic(topic);
            }

            // 标记初始化完成（volatile 写操作，确保其他线程可见）
            initialized = true;

            log.info("========================================");
            log.info("RocketMQ TestContainers 初始化完成");
            log.info("========================================");
          } catch (Exception e) {
            log.error("RocketMQ 容器初始化失败", e);
            throw new IllegalStateException("RocketMQ 容器初始化失败", e);
          }
        } else {
          log.info("RocketMQ 容器已由其他线程初始化，复用现有实例 (线程: {})", Thread.currentThread().getName());
        }
      }
    } else {
      log.debug("RocketMQ 容器已初始化，跳过 (线程: {})", Thread.currentThread().getName());
    }
  }

  /// 初始化 Spring 应用上下文，注入 RocketMQ 动态配置。
/// 
/// 注入的配置项:
/// 
/// - `rocketmq.name-server`: NameServer 地址 (宿主机可访问，包含动态端口)
/// 
/// @param applicationContext Spring 应用上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    log.info("注入 RocketMQ 动态配置到 Spring 上下文");

    String nameServerAddr = rocketmqSupport.getNameserverAddress();

    TestPropertyValues.of("rocketmq.name-server=" + nameServerAddr)
        .applyTo(applicationContext.getEnvironment());

    log.info("RocketMQ 动态配置注入完成");
    log.info("  - rocketmq.name-server: {}", nameServerAddr);
  }

  /// 获取 RocketMQ 容器支持实例（供测试代码访问）。
/// 
/// @return RocketMQ 容器支持实例
  public static RocketMQContainerSupport getRocketMQSupport() {
    return rocketmqSupport;
  }

  /// 获取 RocketMQ Topic 管理工具实例（供测试代码访问）。
/// 
/// @return RocketMQ Topic 管理工具实例
  public static RocketMQTopicAdmin getTopicAdmin() {
    return topicAdmin;
  }
}
