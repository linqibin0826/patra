package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.rocketmq.RocketMQContainerSupport;
import dev.linqibin.starter.test.container.rocketmq.RocketMQTopicAdmin;
import java.io.IOException;
import java.net.Socket;
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

  /// RocketMQ NameServer 主机。
  private static final String NAMESRV_HOST = "localhost";

  /// RocketMQ NameServer 端口。
  private static final int NAMESRV_PORT = 9876;

  /// RocketMQ 容器支持类单例实例（使用 Testcontainers 启动时才非 null）。
  private static volatile RocketMQContainerSupport rocketmqSupport;

  /// RocketMQ 连接地址（host 模式时直接使用，容器模式时由 rocketmqSupport 提供）。
  private static volatile String resolvedNameserverAddr;

  /// RocketMQ Topic 管理工具单例实例（仅容器模式可用）。
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

  /// 检测宿主机 RocketMQ NameServer 是否已在运行。
  ///
  /// 尝试 TCP 连接 localhost:9876，连接成功则认为宿主机已有 RocketMQ 实例。
  ///
  /// @return true 表示宿主机 RocketMQ 已运行，false 表示需要启动容器
  private static boolean isHostRocketMQRunning() {
    try (Socket socket = new Socket(NAMESRV_HOST, NAMESRV_PORT)) {
      return true;
    } catch (IOException e) {
      return false;
    }
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
          log.info("初始化 RocketMQ 测试环境 (线程: {})", Thread.currentThread().getName());
          log.info("========================================");

          try {
            if (isHostRocketMQRunning()) {
              // 宿主机已有 RocketMQ 实例（如 docker-compose 启动的 patra-rocketmq-namesrv）
              // 直接复用，无需启动 Testcontainers 容器
              resolvedNameserverAddr = NAMESRV_HOST + ":" + NAMESRV_PORT;
              log.info("检测到宿主机 RocketMQ 已运行，复用现有实例");
              log.info("  - NameServer 地址: {}", resolvedNameserverAddr);
              log.info("  - Topics 将由 Broker 自动创建（autoCreateTopicEnable=true）");
            } else {
              // 宿主机无 RocketMQ，启动 Testcontainers 容器
              rocketmqSupport = new RocketMQContainerSupport();
              rocketmqSupport.start();
              resolvedNameserverAddr = rocketmqSupport.getNameserverAddress();

              log.info("RocketMQ Testcontainers 容器已启动");
              log.info("  - NameServer 地址: {}", resolvedNameserverAddr);

              // 初始化 Topic 管理工具并预创建 Topics
              topicAdmin = new RocketMQTopicAdmin(rocketmqSupport.getComposeContainer());
              String[] topics = getTopicsToCreate();
              for (String topic : topics) {
                log.info("创建测试 Topic: {}", topic);
                topicAdmin.createTopic(topic);
              }
            }

            // 标记初始化完成（volatile 写操作，确保其他线程可见）
            initialized = true;

            log.info("========================================");
            log.info("RocketMQ 测试环境初始化完成");
            log.info("========================================");
          } catch (Exception e) {
            log.error("RocketMQ 测试环境初始化失败", e);
            throw new IllegalStateException("RocketMQ 测试环境初始化失败", e);
          }
        } else {
          log.info("RocketMQ 已由其他线程初始化，复用现有实例 (线程: {})", Thread.currentThread().getName());
        }
      }
    } else {
      log.debug("RocketMQ 已初始化，跳过 (线程: {})", Thread.currentThread().getName());
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

    TestPropertyValues.of("rocketmq.name-server=" + resolvedNameserverAddr)
        .applyTo(applicationContext.getEnvironment());

    log.info("RocketMQ 动态配置注入完成");
    log.info("  - rocketmq.name-server: {}", resolvedNameserverAddr);
  }

  /// 获取已解析的 NameServer 地址（供测试代码访问）。
  ///
  /// 不论使用宿主机模式还是容器模式，此方法均可返回有效地址。
  ///
  /// @return NameServer 地址，格式 host:port
  public static String getResolvedNameserverAddr() {
    return resolvedNameserverAddr;
  }

  /// 获取 RocketMQ 容器支持实例（供测试代码访问）。
  ///
  /// 仅在容器模式下非 null；宿主机模式下返回 null。
  ///
  /// @return RocketMQ 容器支持实例，或 null（宿主机模式）
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
