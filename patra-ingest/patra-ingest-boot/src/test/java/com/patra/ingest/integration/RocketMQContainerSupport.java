package com.patra.ingest.integration;

import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ TestContainers 支持类
 * <p>
 * 基于 Apache Camel 的成功实现:
 * https://github.com/apache/camel/tree/main/test-infra/camel-test-infra-rocketmq
 */
public class RocketMQContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(RocketMQContainerSupport.class);

    private static final String ROCKETMQ_IMAGE = "apache/rocketmq:5.3.1";
    private static final int NAMESRV_PORT = 9876;
    private static final int BROKER_PORT_10909 = 10909;
    private static final int BROKER_PORT_10911 = 10911;
    private static final int BROKER_PORT_10912 = 10912;

    private final Network network;
    private final GenericContainer<?> nameserverContainer;
    private final GenericContainer<?> brokerContainer;

    public RocketMQContainerSupport() {
        this.network = Network.newNetwork();
        this.nameserverContainer = createNameserverContainer();
        this.brokerContainer = createBrokerContainer();
    }

    /**
     * 创建 NameServer 容器
     * <p>
     * 关键配置:
     * - 使用网络别名 "nameserver" 供 Broker 连接
     * - 暴露端口 9876
     * - 使用 tmpfs 提升性能
     */
    private GenericContainer<?> createNameserverContainer() {
        log.info("创建 RocketMQ NameServer 容器");

        return new GenericContainer<>(ROCKETMQ_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("nameserver")  // ✅ 关键: 容器网络别名
                .withExposedPorts(NAMESRV_PORT)
                .withTmpFs(Collections.singletonMap("/home/rocketmq/logs", "rw"))
                .withCommand("sh", "mqnamesrv")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    /**
     * 创建 Broker 容器
     * <p>
     * 关键配置:
     * - 通过别名 "nameserver:9876" 连接 NameServer
     * - 不配置 brokerIP1（让 RocketMQ 自动检测）
     * - 使用 tmpfs 避免磁盘 IO
     * - 暴露多个端口（10909, 10911, 10912）
     */
    private GenericContainer<?> createBrokerContainer() {
        log.info("创建 RocketMQ Broker 容器");

        return new GenericContainer<>(ROCKETMQ_IMAGE)
                .withNetwork(network)
                .withExposedPorts(BROKER_PORT_10909, BROKER_PORT_10911, BROKER_PORT_10912)
                // ✅ 关键: 通过容器别名连接 NameServer
                .withEnv("NAMESRV_ADDR", "nameserver:9876")
                // Broker 配置: 启用自动创建 Topic（仅用于测试）
                .withEnv("BROKER_CONF", buildBrokerConfig())
                .withTmpFs(Collections.singletonMap("/home/rocketmq/store", "rw"))
                .withTmpFs(Collections.singletonMap("/home/rocketmq/logs", "rw"))
                .withCommand("sh", "-c", buildBrokerStartCommand())
                .waitingFor(Wait.forLogMessage(".*The broker.*boot success.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2))
                .dependsOn(nameserverContainer);
    }

    /**
     * 构建 Broker 配置
     * <p>
     * 注意: 不配置 brokerIP1，让 RocketMQ 自动检测
     */
    private String buildBrokerConfig() {
        return "brokerClusterName=DefaultCluster\n" +
                "brokerName=broker-a\n" +
                "brokerId=0\n" +
                "deleteWhen=04\n" +
                "fileReservedTime=48\n" +
                "brokerRole=ASYNC_MASTER\n" +
                "flushDiskType=ASYNC_FLUSH\n" +
                "autoCreateTopicEnable=true\n" +  // ✅ 启用自动创建（仅测试）
                "autoCreateSubscriptionGroup=true\n";
    }

    /**
     * 构建 Broker 启动命令
     */
    private String buildBrokerStartCommand() {
        return "echo '" + buildBrokerConfig() + "' > /tmp/broker.conf && " +
                "cat /tmp/broker.conf && " +
                "sh mqbroker -n nameserver:9876 -c /tmp/broker.conf";
    }

    /**
     * 启动容器
     */
    public void start() {
        log.info("启动 RocketMQ NameServer 容器");
        nameserverContainer.start();

        log.info("启动 RocketMQ Broker 容器");
        brokerContainer.start();

        log.info("RocketMQ 容器启动完成");
        log.info("NameServer 地址: {}", getNameserverAddress());
    }

    /**
     * 停止容器
     */
    public void stop() {
        log.info("停止 RocketMQ 容器");
        if (brokerContainer != null && brokerContainer.isRunning()) {
            brokerContainer.stop();
        }
        if (nameserverContainer != null && nameserverContainer.isRunning()) {
            nameserverContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    /**
     * 获取 NameServer 地址（供客户端连接）
     * <p>
     * ✅ 关键: 使用宿主机映射端口
     */
    public String getNameserverAddress() {
        return nameserverContainer.getHost() + ":"
                + nameserverContainer.getMappedPort(NAMESRV_PORT);
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

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollDelay(1, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        var result = brokerContainer.execInContainer(
                                "sh", "mqadmin", "updateTopic",
                                "-n", "nameserver:9876",  // ✅ 容器内部使用别名
                                "-t", topic,
                                "-c", "DefaultCluster"
                        );

                        String output = result.getStdout();
                        boolean success = result.getExitCode() == 0 && output.contains("success");

                        if (success) {
                            log.info("Topic 创建成功: {}", topic);
                            // ✅ 等待路由信息同步
                            return verifyTopicRoute(topic);
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
     */
    private boolean verifyTopicRoute(String topic) {
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
     */
    public void deleteTopic(String topic) {
        log.info("删除 Topic: {}", topic);
        try {
            brokerContainer.execInContainer(
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

    public GenericContainer<?> getNameserverContainer() {
        return nameserverContainer;
    }

    public GenericContainer<?> getBrokerContainer() {
        return brokerContainer;
    }
}
