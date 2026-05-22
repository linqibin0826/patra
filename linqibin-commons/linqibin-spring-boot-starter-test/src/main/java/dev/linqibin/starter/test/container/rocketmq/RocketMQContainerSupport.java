package dev.linqibin.starter.test.container.rocketmq;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
/// - **网络配置**: 确保宿主机可访问容器内的 RocketMQ 服务
///
/// ### 核心技术突破
///
/// - **brokerIP1=127.0.0.1**: Broker advertise 宿主机可访问的地址
/// - **1:1 端口映射**: 10911:10911，确保客户端连接端口匹配
/// - **Here-Document 格式**: 使用 <<EOF 格式化配置文件
/// - **Docker Compose**: 声明式配置，易于维护
///
/// ### 为什么不使用 GenericContainer?
///
/// GenericContainer 的动态端口映射和容器内部 IP 检测机制，在 RocketMQ 场景下会导致：
///
/// - Broker 自动检测到容器内部 IP (如 172.17.0.x)
/// - 客户端从 NameServer 获取到错误的 Broker 地址
/// - 宿主机无法连接到 Broker
///
/// ### 设计原则
///
/// - **单一职责**: 只管理容器生命周期，不管理 Topic（Topic 管理由 {@link RocketMQTopicAdmin} 负责）
/// - **关注点分离**: 容器管理与业务配置分离
///
/// @author linqibin
/// @since 0.1.0
/// @see ComposeContainer
/// @see RocketMQTopicAdmin
public class RocketMQContainerSupport {

  private static final Logger log = LoggerFactory.getLogger(RocketMQContainerSupport.class);

  /// NameServer 端口。
  private static final int NAMESRV_PORT = 9876;

  /// Broker 端口。
  private static final int BROKER_PORT = 10911;

  /// Docker Compose 配置文件名。
  private static final String COMPOSE_FILE_NAME = "docker-compose-rocketmq.yml";

  /// Docker Compose 容器实例。
  private final ComposeContainer composeContainer;

  /// 构造函数，初始化 Docker Compose 容器。
  ///
  /// @throws IllegalStateException 如果找不到 docker-compose-rocketmq.yml 文件
  public RocketMQContainerSupport() {
    // 从 classpath 加载 docker-compose 文件并复制到临时文件
    // 用 ComposeContainer 需要真实 File 对象；当 starter-test 被打成 jar 引用时
    // classpath resource URL 形如 `jar:file:.../starter-test.jar!/docker-compose-rocketmq.yml`，
    // 不能直接 new File() 解析。把流复制到临时文件兼容两种场景（classes 目录 / jar 内 entry）。
    File composeFile;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(COMPOSE_FILE_NAME)) {
      if (is == null) {
        throw new IllegalStateException(
            "找不到 " + COMPOSE_FILE_NAME + " 文件，请确保该文件在 test/resources 目录下");
      }
      composeFile = File.createTempFile("docker-compose-rocketmq-", ".yml");
      composeFile.deleteOnExit();
      Files.copy(is, composeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("复制 " + COMPOSE_FILE_NAME + " 到临时文件失败", e);
    }

    log.info("加载 Docker Compose 配置: {}", composeFile.getAbsolutePath());

    // Testcontainers 1.21.x ComposeContainer 默认 containerised 模式（启动 docker:24.0.2 容器跑 compose），
    // 与 OrbStack 等部分 Docker daemon 不兼容（报 Status 500: unexpected EOF）。
    // 显式 withLocalCompose(true) 走本地 docker compose CLI，兼容性更好；
    // CI ubuntu-latest 自带 docker compose CLI，本地 OrbStack / Docker Desktop 也都自带，无副作用。
    this.composeContainer =
        new ComposeContainer(composeFile)
            .withLocalCompose(true)
            .withExposedService(
                "nameserver",
                NAMESRV_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
            .withExposedService(
                "broker",
                BROKER_PORT,
                Wait.forLogMessage(".*The broker.*boot success.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));
  }

  /// 启动 RocketMQ 容器。
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

  /// 停止 RocketMQ 容器。
  public void stop() {
    log.info("停止 RocketMQ Docker Compose 环境");
    if (composeContainer != null) {
      composeContainer.stop();
    }
    log.info("RocketMQ 容器已停止");
  }

  /// 获取 NameServer 地址（供客户端连接）。
  ///
  /// 由于使用 1:1 端口映射，直接使用 localhost:9876
  ///
  /// @return NameServer 地址，格式: localhost:9876
  public String getNameserverAddress() {
    return "localhost:" + NAMESRV_PORT;
  }

  /// 获取 ComposeContainer 实例。
  ///
  /// @return ComposeContainer 实例
  public ComposeContainer getComposeContainer() {
    return composeContainer;
  }
}
