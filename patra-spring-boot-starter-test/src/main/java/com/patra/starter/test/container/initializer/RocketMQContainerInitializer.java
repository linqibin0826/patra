package com.patra.starter.test.container.initializer;

import com.patra.starter.test.container.rocketmq.RocketMQContainerSupport;
import com.patra.starter.test.container.rocketmq.RocketMQTopicAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// RocketMQ 容器初始化器。
///
/// 提供 RocketMQ 5.3.1 容器的单例管理和动态配置注入。
/// 通过子类化支持不同服务使用不同的 Topic 配置。
///
/// ### 核心特性
///
/// - **单例容器**: 所有测试共享同一个 RocketMQ 容器实例 (NameServer + Broker)
/// - **线程安全初始化**: 使用双重检查锁模式，确保并发场景下容器只启动一次
/// - **配置化 Topic**: 子类通过重写 `getTopicsToCreate()` 指定预创建的 Topics
/// - **动态配置**: 自动注入 NameServer 地址到 Spring 测试上下文
/// - **路由验证**: 使用 Awaitility 确保 Topic 路由信息同步完成
///
/// ### 使用方式
///
/// 方式一：直接使用（无预创建 Topic）
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = RocketMQContainerInitializer.class)
/// class SomeMessageListenerIT {
///     // ...
/// }
/// ```
///
/// 方式二：子类化指定预创建 Topics
///
/// ```java
/// public class IngestRocketMQInitializer extends RocketMQContainerInitializer {
///     @Override
///     protected String[] getTopicsToCreate() {
///         return new String[]{"INGEST_TASK_READY", "INGEST_PUBLICATION_READY"};
///     }
/// }
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**: apache/rocketmq:5.3.1
/// - **组件**: NameServer + Broker (使用 Docker Compose 编排)
/// - **自动创建 Topic**: 启用 (仅测试环境)
///
/// ### 性能表现
///
/// - 首次启动: ~30-40 秒 (NameServer + Broker + Topic 创建)
/// - 后续测试: 复用容器，无需重启
///
/// ### 前置条件
///
/// 使用此初始化器需要在 `test/resources` 目录下提供 `docker-compose-rocketmq.yml` 文件。
/// 可从 starter-test 的资源目录复制模板文件。
///
/// @author linqibin
/// @since 0.1.0
/// @see RocketMQContainerSupport
/// @see RocketMQTopicAdmin
/// @see ApplicationContextInitializer
public class RocketMQContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RocketMQContainerInitializer.class);

  /// RocketMQ 容器支持类单例实例。
  private static volatile RocketMQContainerSupport rocketmqSupport;

  /// RocketMQ Topic 管理工具单例实例。
  private static volatile RocketMQTopicAdmin topicAdmin;

  /// 初始化状态标志，使用 volatile 确保多线程可见性。
  private static volatile boolean initialized = false;

  /// 同步锁对象，用于保护初始化过程的线程安全。
  private static final Object LOCK = new Object();

  /// 获取需要预创建的 Topics。
  ///
  /// 子类可重写此方法以指定预创建的 Topics。
  /// 默认返回空数组（不预创建任何 Topic）。
  ///
  /// @return Topic 名称数组
  protected String[] getTopicsToCreate() {
    return new String[0];
  }

  /// 初始化 RocketMQ 容器（线程安全的单例模式）。
  ///
  /// 使用双重检查锁（Double-Checked Locking）模式确保：
  ///
  /// - 容器只启动一次，即使多个测试类并发加载
  /// - 避免 Docker Compose 端口冲突和资源竞争
  /// - 线程安全，防止竞态条件
  ///
  /// ### 并发场景
  ///
  /// 在 Maven 并行测试模式（-T 1C）下，多个测试类可能同时加载此类。
  /// 双重检查锁确保只有第一个线程执行初始化，其他线程等待并复用已初始化的容器。
  ///
  /// @throws IllegalStateException 如果容器启动失败
  private void initializeContainer() {
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
            String[] topics = getTopicsToCreate();
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
    // 确保容器已初始化
    initializeContainer();

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
