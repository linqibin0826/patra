package com.patra.ingest.integration;

import java.io.File;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/// RocketMQ TestContainers 容器管理类。
///
/// 使用 Docker Compose + ComposeContainer 管理 RocketMQ 容器，解决网络配置问题。
///
/// ### 职责范围
///
/// - **容器生命周期管理**: 启动、停止 RocketMQ 容器（NameServer + Broker）
///   - **网络配置**: 确保宿主机可访问容器内的 RocketMQ 服务
///
/// ### 核心技术突破
///
/// - **brokerIP1=127.0.0.1**: Broker advertise 宿主机可访问的地址
///   - **1:1 端口映射**: 10911:10911，确保客户端连接端口匹配
///   - **Here-Document 格式**: 使用 <<EOF 格式化配置文件
///   - **Docker Compose**: 声明式配置，易于维护
///
/// ### 为什么不使用 GenericContainer?
///
/// GenericContainer 的动态端口映射和容器内部 IP 检测机制，在 RocketMQ 场景下会导致：
///
/// - Broker 自动检测到容器内部 IP (如 172.17.0.x)
///   - 客户端从 NameServer 获取到错误的 Broker 地址
///   - 宿主机无法连接到 Broker
///
/// ### 设计原则
///
/// - **单一职责**: 只管理容器生命周期，不管理 Topic（Topic 管理由 {@link
///       com.patra.ingest.integration.config.RocketMQTopicAdmin} 负责）
///   - **关注点分离**: 容器管理与业务配置分离
///
/// @author linqibin
/// @since 0.1.0
/// @see ComposeContainer
/// @see com.patra.ingest.integration.config.RocketMQTopicAdmin
public class RocketMQContainerSupport {

  private static final Logger log = LoggerFactory.getLogger(RocketMQContainerSupport.class);

  private static final int NAMESRV_PORT = 9876;
  private static final int BROKER_PORT = 10911;

  private final ComposeContainer composeContainer;

  /// 构造函数，初始化 Docker Compose 容器
  public RocketMQContainerSupport() {
    // 从 classpath 加载 docker-compose 文件
    File composeFile =
        new File(getClass().getClassLoader().getResource("docker-compose-rocketmq.yml").getFile());

    if (!composeFile.exists()) {
      throw new IllegalStateException(
          "找不到 docker-compose-rocketmq.yml 文件，路径: " + composeFile.getAbsolutePath());
    }

    log.info("加载 Docker Compose 配置: {}", composeFile.getAbsolutePath());

    this.composeContainer =
        new ComposeContainer(composeFile)
            .withExposedService(
                "nameserver",
                NAMESRV_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
            .withExposedService(
                "broker",
                BROKER_PORT,
                Wait.forLogMessage(".*The broker.*boot success.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)))
            .withLocalCompose(true);
  }

  /// 启动 RocketMQ 容器
  public void start() {
    log.info("========================================");
    log.info("启动 RocketMQ Docker Compose 环境");
    log.info("========================================");

    composeContainer.start();

    // 等待服务完全就绪
    log.info("等待 RocketMQ 服务就绪...");
    try {
      Thread.sleep(10000); // 给予充足时间让 Broker 注册到 NameServer
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    log.info("========================================");
    log.info("RocketMQ Docker Compose 环境启动完成");
    log.info("NameServer 地址: {}", getNameserverAddress());
    log.info("========================================");
  }

  /// 停止 RocketMQ 容器
  public void stop() {
    log.info("停止 RocketMQ Docker Compose 环境");
    if (composeContainer != null) {
      composeContainer.stop();
    }
    log.info("RocketMQ 容器已停止");
  }

  /// 获取 NameServer 地址（供客户端连接）
  ///
  /// 由于使用 1:1 端口映射，直接使用 localhost:9876
  ///
  /// @return NameServer 地址，格式: localhost:9876
  public String getNameserverAddress() {
    return "localhost:" + NAMESRV_PORT;
  }

  /// 获取 ComposeContainer 实例
  ///
  /// @return ComposeContainer 实例
  public ComposeContainer getComposeContainer() {
    return composeContainer;
  }
}
