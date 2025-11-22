package com.patra.ingest.integration.config;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;

/// RocketMQ Topic 管理工具类。
///
/// 负责 RocketMQ Topic 的创建、删除和路由验证，与容器管理职责分离。
///
/// ### 职责范围
///
/// - **Topic 创建**: 使用 mqadmin 命令创建 Topic
///   - **路由验证**: 等待 Topic 路由信息同步完成
///   - **Topic 删除**: 测试清理时删除 Topic（可选）
///
/// ### 设计原则
///
/// - **单一职责**: 只管理 Topic，不管理容器生命周期
///   - **幂等性**: 重复创建同一 Topic 不会报错
///   - **健壮性**: 使用 Awaitility 等待操作成功，自动重试
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.ingest.integration.RocketMQContainerSupport
public class RocketMQTopicAdmin {

  private static final Logger log = LoggerFactory.getLogger(RocketMQTopicAdmin.class);

  private final ComposeContainer composeContainer;

  /// 构造函数。
  ///
  /// @param composeContainer RocketMQ Compose 容器实例
  public RocketMQTopicAdmin(ComposeContainer composeContainer) {
    this.composeContainer = composeContainer;
  }

  /// 创建 Topic（使用 Awaitility 等待成功）。
  ///
  /// 基于 Apache Camel 的方案，确保 Topic 创建成功并等待路由信息同步。
  ///
  /// @param topic Topic 名称
  public void createTopic(String topic) {
    log.info("创建 Topic: {}", topic);

    // 获取 broker 容器（Docker Compose 的服务名称格式可能是 <service>-1 或 <service>_1）
    var brokerContainerOpt = composeContainer.getContainerByServiceName("broker-1");
    if (!brokerContainerOpt.isPresent()) {
      brokerContainerOpt = composeContainer.getContainerByServiceName("broker_1");
    }

    if (!brokerContainerOpt.isPresent()) {
      throw new IllegalStateException("找不到 Broker 容器");
    }

    var brokerContainer = brokerContainerOpt.get();

    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .pollDelay(1, TimeUnit.SECONDS)
        .pollInterval(2, TimeUnit.SECONDS)
        .until(
            () -> {
              try {
                var result =
                    brokerContainer.execInContainer(
                        "sh",
                        "mqadmin",
                        "updateTopic",
                        "-n",
                        "nameserver:9876", // 容器内部使用别名
                        "-t",
                        topic,
                        "-c",
                        "DefaultCluster");

                String output = result.getStdout();
                boolean success = result.getExitCode() == 0 && output.contains("success");

                if (success) {
                  log.info("Topic 创建成功: {}", topic);
                  // 等待路由信息同步
                  return verifyTopicRoute(topic, brokerContainer);
                } else {
                  log.warn("Topic 创建失败，重试中... 输出: {}", output);
                  return false;
                }
              } catch (Exception e) {
                log.warn("执行 mqadmin 命令失败: {}", e.getMessage());
                return false;
              }
            });

    log.info("Topic {} 已创建并且路由信息已同步", topic);
  }

  /// 验证 Topic 路由信息。
  ///
  /// @param topic Topic 名称
  /// @param brokerContainer Broker 容器
  /// @return 路由信息是否可用
  private boolean verifyTopicRoute(String topic, ContainerState brokerContainer) {
    try {
      var result =
          brokerContainer.execInContainer(
              "sh", "mqadmin", "topicRoute", "-n", "nameserver:9876", "-t", topic);

      String output = result.getStdout();
      boolean routeAvailable = output != null && output.contains("brokerName");

      if (routeAvailable) {
        log.info("Topic {} 路由信息已可用", topic);
      } else {
        log.warn("Topic {} 路由信息尚未同步，输出: {}", topic, output);
      }

      return routeAvailable;
    } catch (Exception e) {
      log.warn("验证路由信息失败: {}", e.getMessage());
      return false;
    }
  }

  /// 删除 Topic。
  ///
  /// @param topic Topic 名称
  public void deleteTopic(String topic) {
    log.info("删除 Topic: {}", topic);

    var brokerContainerOpt = composeContainer.getContainerByServiceName("broker-1");
    if (!brokerContainerOpt.isPresent()) {
      brokerContainerOpt = composeContainer.getContainerByServiceName("broker_1");
    }

    if (!brokerContainerOpt.isPresent()) {
      log.warn("找不到 Broker 容器，无法删除 Topic");
      return;
    }

    try {
      brokerContainerOpt
          .get()
          .execInContainer(
              "sh",
              "mqadmin",
              "deleteTopic",
              "-n",
              "nameserver:9876",
              "-t",
              topic,
              "-c",
              "DefaultCluster");
      log.info("Topic {} 已删除", topic);
    } catch (Exception e) {
      log.warn("删除 Topic 失败: {}", e.getMessage());
    }
  }
}
