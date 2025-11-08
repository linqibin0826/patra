package com.patra.ingest.integration;

import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ TestContainers 支持类
 * <p>
 * 使用 Docker Compose + ComposeContainer 管理 RocketMQ 容器，解决网络配置问题。
 *
 * <h3>核心技术突破</h3>
 * <ul>
 *   <li><strong>brokerIP1=127.0.0.1</strong>: Broker advertise 宿主机可访问的地址
 *   <li><strong>1:1 端口映射</strong>: 10911:10911，确保客户端连接端口匹配
 *   <li><strong>Here-Document 格式</strong>: 使用 <<EOF 格式化配置文件
 *   <li><strong>Docker Compose</strong>: 声明式配置，易于维护
 * </ul>
 *
 * <h3>为什么不使用 GenericContainer?</h3>
 * <p>GenericContainer 的动态端口映射和容器内部 IP 检测机制，在 RocketMQ 场景下会导致：
 * <ul>
 *   <li>Broker 自动检测到容器内部 IP (如 172.17.0.x)
 *   <li>客户端从 NameServer 获取到错误的 Broker 地址
 *   <li>宿主机无法连接到 Broker
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 * @see ComposeContainer
 */
public class RocketMQContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(RocketMQContainerSupport.class);

    private static final int NAMESRV_PORT = 9876;
    private static final int BROKER_PORT = 10911;

    private final ComposeContainer composeContainer;

    /**
     * 构造函数，初始化 Docker Compose 容器
     */
    public RocketMQContainerSupport() {
        File composeFile = new File("docker-compose-rocketmq.yml");

        if (!composeFile.exists()) {
            throw new IllegalStateException(
                "找不到 docker-compose-rocketmq.yml 文件，路径: " + composeFile.getAbsolutePath()
            );
        }

        log.info("加载 Docker Compose 配置: {}", composeFile.getAbsolutePath());

        this.composeContainer = new ComposeContainer(composeFile)
                .withExposedService("nameserver", NAMESRV_PORT,
                        Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
                .withExposedService("broker", BROKER_PORT,
                        Wait.forLogMessage(".*The broker.*boot success.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(2)))
                .withLocalCompose(true);
    }

    /**
     * 启动 RocketMQ 容器
     */
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

    /**
     * 停止 RocketMQ 容器
     */
    public void stop() {
        log.info("停止 RocketMQ Docker Compose 环境");
        if (composeContainer != null) {
            composeContainer.stop();
        }
        log.info("RocketMQ 容器已停止");
    }

    /**
     * 获取 NameServer 地址（供客户端连接）
     * <p>
     * 由于使用 1:1 端口映射，直接使用 localhost:9876
     *
     * @return NameServer 地址，格式: localhost:9876
     */
    public String getNameserverAddress() {
        return "localhost:" + NAMESRV_PORT;
    }

    /**
     * 创建 Topic（使用 Awaitility 等待成功）
     * <p>
     * 基于 Apache Camel 的方案，确保 Topic 创建成功并等待路由信息同步
     *
     * @param topic Topic 名称
     */
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
                .until(() -> {
                    try {
                        var result = brokerContainer.execInContainer(
                                "sh", "mqadmin", "updateTopic",
                                "-n", "nameserver:9876",  // 容器内部使用别名
                                "-t", topic,
                                "-c", "DefaultCluster"
                        );

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

    /**
     * 验证 Topic 路由信息
     *
     * @param topic Topic 名称
     * @param brokerContainer Broker 容器
     * @return 路由信息是否可用
     */
    private boolean verifyTopicRoute(String topic, org.testcontainers.containers.ContainerState brokerContainer) {
        try {
            var result = brokerContainer.execInContainer(
                    "sh", "mqadmin", "topicRoute",
                    "-n", "nameserver:9876",
                    "-t", topic
            );

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

    /**
     * 删除 Topic
     *
     * @param topic Topic 名称
     */
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
            brokerContainerOpt.get().execInContainer(
                    "sh", "mqadmin", "deleteTopic",
                    "-n", "nameserver:9876",
                    "-t", topic,
                    "-c", "DefaultCluster"
            );
            log.info("Topic {} 已删除", topic);
        } catch (Exception e) {
            log.warn("删除 Topic 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取 ComposeContainer 实例
     *
     * @return ComposeContainer 实例
     */
    public ComposeContainer getComposeContainer() {
        return composeContainer;
    }
}
