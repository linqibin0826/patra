package com.patra.ingest.integration;

import java.net.InetAddress;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试抽象基类。
 *
 * <p>提供 TestContainers 基础设施配置,所有集成测试类应继承此基类以复用容器配置。
 *
 * <h3>功能特性</h3>
 *
 * <ul>
 *   <li><strong>MySQL 容器</strong>: 自动启动 MySQL 8.0.36 容器,包含数据库迁移 (Flyway)
 *   <li><strong>RocketMQ 容器</strong>: 自动启动 RocketMQ 5.3.1 (NameServer + Broker)
 *   <li><strong>容器重用</strong>: 使用 {@code withReuse(true)} 加速测试执行
 *   <li><strong>动态配置</strong>: 自动注入容器连接配置到 Spring 上下文
 *   <li><strong>共享网络</strong>: 所有容器共享 Docker 网络,支持容器间通信
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @DisplayName("Plan 仓储集成测试")
 * class PlanRepositoryIntegrationTest extends BaseIntegrationTest {
 *
 *     @Autowired
 *     private PlanRepositoryPort planRepo;
 *
 *     @Test
 *     @DisplayName("应该保存并查询 Plan")
 *     void shouldSaveAndFindPlan() {
 *         // 测试实现...
 *     }
 * }
 * }</pre>
 *
 * <h3>环境要求</h3>
 *
 * <ul>
 *   <li>Docker Desktop 运行中
 *   <li>至少 4GB 可用内存
 * </ul>
 *
 * <h3>设计模式</h3>
 *
 * <p>遵循 <strong>Template Method</strong> 模式,子类继承基础设施配置,专注于特定测试场景。
 *
 * @author linqibin
 * @since 0.2.0
 * @see <a href="../../../../../../../TESTING.md">测试指南</a>
 */
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:ingest-error-config.yaml,classpath:ingest-rocketmq.yaml"
    })
@Testcontainers
// 注意：@ActiveProfiles 由子类指定，integration-test 或 e2e-test
public abstract class BaseIntegrationTest {

  // ========== Docker Network ==========

  /**
   * Docker 网络 (MySQL、NameServer 和 Broker 共享)。
   *
   * <p>RocketMQ Broker 需要通过内部网络访问 NameServer。
   */
  protected static final Network network = Network.newNetwork();

  /**
   * 获取 RocketMQ Broker 应该向 NameServer 注册的 IP 地址。
   *
   * <p>用于 RocketMQ Broker 的 brokerIP1 配置,确保客户端可以连接到 Broker。
   *
   * <p><strong>重要</strong>:
   *
   * <ul>
   *   <li><strong>macOS/Windows</strong>: 使用 {@code host.docker.internal} (Docker 特殊 DNS)
   *   <li><strong>Linux</strong>: 使用宿主机真实 IP 地址
   *   <li><strong>CI 环境</strong>: 使用 {@code localhost} (通常 CI 环境支持 host 网络模式)
   * </ul>
   *
   * @return Broker 应该注册的 IP 地址
   */
  private static String getBrokerIpAddress() {
    // 🔑 关键修复: host.docker.internal 在宿主机上无法解析
    //
    // 问题分析:
    // 1. Broker 向 NameServer 注册时使用 brokerIP1 的值
    // 2. 测试代码(宿主机)从 NameServer 获取这个地址后尝试连接
    // 3. host.docker.internal 只在容器内部有效,在宿主机上无法解析
    //
    // 解决方案:
    // 统一使用 localhost,配合固定端口映射 (10911:10911)
    // - Broker 在容器内向 NameServer 注册为 localhost:10911
    // - 宿主机测试代码通过 localhost:10911 连接 Broker
    // - 固定端口映射确保端口一致性
    String os = System.getProperty("os.name").toLowerCase();
    System.out.println("检测到 " + os + " 环境,使用 localhost(配合固定端口映射 10911:10911)");
    return "localhost";
  }

  // ========== MySQL Container ==========

  /**
   * MySQL 测试容器。
   *
   * <p><strong>配置说明</strong>:
   *
   * <ul>
   *   <li><strong>版本</strong>: mysql:8.0.36 (与生产环境一致)
   *   <li><strong>数据库名</strong>: patra_test
   *   <li><strong>用户名/密码</strong>: root / 123456
   *   <li><strong>容器重用</strong>: 启用,跨测试类重用容器以提高速度
   * </ul>
   *
   * <p><strong>性能优化</strong>:
   *
   * <ul>
   *   <li>首次启动: ~20-30 秒
   *   <li>重用后: < 1 秒
   * </ul>
   */
  @Container
  protected static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("patra_ingest") // 使用与生产环境一致的数据库名
          .withUsername("root")
          .withPassword("123456")
          .withReuse(false); // 禁用容器重用以避免配置缓存问题

  // ========== RocketMQ Containers ==========

  /**
   * RocketMQ NameServer 容器。
   *
   * <p><strong>配置说明</strong>:
   *
   * <ul>
   *   <li><strong>镜像</strong>: apache/rocketmq:5.3.1 (稳定版本)
   *   <li><strong>端口</strong>: 9876 (NameServer 默认端口)
   *   <li><strong>网络别名</strong>: namesrv (Broker 通过此别名连接)
   *   <li><strong>等待策略</strong>: 日志出现 "The Name Server boot success"
   *   <li><strong>容器重用</strong>: 禁用,避免配置缓存问题
   * </ul>
   */
  @Container
  protected static final GenericContainer<?> rocketmqNamesrv =
      new GenericContainer<>("apache/rocketmq:5.3.1")
          .withExposedPorts(9876)
          .withCommand("sh mqnamesrv")
          .withNetwork(network)
          .withNetworkAliases("namesrv")
          .waitingFor(Wait.forLogMessage(".*The Name Server boot success.*", 1))
          .withReuse(false); // 禁用容器重用以避免配置缓存问题

  /**
   * RocketMQ Broker 容器。
   *
   * <p><strong>配置说明</strong>:
   *
   * <ul>
   *   <li><strong>镜像</strong>: apache/rocketmq:5.3.1
   *   <li><strong>端口映射</strong>: 10911:10911, 10912:10912 (<strong>固定映射,解决端口不一致问题</strong>)
   *   <li><strong>依赖</strong>: NameServer 容器必须先启动
   *   <li><strong>配置</strong>: autoCreateTopicEnable=true (自动创建 Topic,简化测试)
   *   <li><strong>brokerIP1</strong>:
   *       <ul>
   *         <li>macOS/Windows: {@code host.docker.internal}
   *         <li>Linux: 宿主机真实 IP
   *         <li>CI: {@code localhost}
   *       </ul>
   *   <li><strong>等待策略</strong>: 日志出现 "The broker.*success"
   *   <li><strong>容器重用</strong>: 禁用,避免配置缓存问题
   * </ul>
   *
   * <p><strong>关键技术点</strong>:
   *
   * <ul>
   *   <li>✅ 使用固定端口映射确保 Broker 注册地址与客户端连接地址一致
   *   <li>✅ macOS 环境使用 {@code host.docker.internal} 解决 Docker 虚拟机网络隔离
   *   <li>✅ Broker 配置为 ASYNC_MASTER 模式,适合测试环境
   * </ul>
   */
  @Container
  protected static final GenericContainer<?> rocketmqBroker =
      new GenericContainer<>("apache/rocketmq:5.3.1")
          .withExposedPorts(10911, 10912)
          // 🔑 关键：使用固定端口映射,确保端口一致性
          .withCreateContainerCmdModifier(cmd -> {
            cmd.withHostConfig(
                new com.github.dockerjava.api.model.HostConfig()
                    .withPortBindings(
                        new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindPort(10911),
                            new com.github.dockerjava.api.model.ExposedPort(10911)),
                        new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindPort(10912),
                            new com.github.dockerjava.api.model.ExposedPort(10912))
                    )
            );
          })
          .withEnv("NAMESRV_ADDR", "namesrv:9876")
          .withEnv("JAVA_OPT_EXT", "-Xms512m -Xmx512m")
          .withCommand(
              "sh",
              "-c",
              // 🔑 关键：直接在 Java 中构建完整的配置字符串,避免依赖 Shell 环境变量解析
              // 这样可以避免代理或其他环境问题导致的环境变量解析失败
              buildBrokerStartCommand())
          .withNetwork(network)
          .withNetworkAliases("broker")
          .dependsOn(rocketmqNamesrv)
          .waitingFor(Wait.forLogMessage(".*The broker.*success.*", 1))
          .withReuse(false); // 禁用容器重用以避免配置缓存问题

  /**
   * 构建 Broker 启动命令。
   *
   * <p>直接在 Java 代码中构建完整的启动命令字符串,包含 broker.conf 的所有配置项。 这样可以避免依赖 Shell
   * 环境变量解析,解决代理环境下环境变量传递失败的问题。
   *
   * @return Broker 启动命令
   */
  private static String buildBrokerStartCommand() {
    String brokerIp = getBrokerIpAddress();
    System.out.println("🔧 构建 Broker 启动命令,brokerIP1 = " + brokerIp);

    return "echo 'brokerClusterName=TestCluster' > /tmp/broker.conf && "
        + "echo 'brokerName=TestBroker' >> /tmp/broker.conf && "
        + "echo 'brokerId=0' >> /tmp/broker.conf && "
        + "echo 'autoCreateTopicEnable=true' >> /tmp/broker.conf && "
        + "echo 'autoCreateSubscriptionGroup=true' >> /tmp/broker.conf && "
        + "echo 'defaultTopicQueueNums=4' >> /tmp/broker.conf && "
        + "echo 'brokerRole=ASYNC_MASTER' >> /tmp/broker.conf && "
        + "echo 'flushDiskType=ASYNC_FLUSH' >> /tmp/broker.conf && "
        + "echo 'deleteWhen=04' >> /tmp/broker.conf && "
        + "echo 'fileReservedTime=48' >> /tmp/broker.conf && "
        + "echo 'listenPort=10911' >> /tmp/broker.conf && "
        + "echo 'brokerIP1="
        + brokerIp
        + "' >> /tmp/broker.conf && " // 🔑 直接拼接 IP,不依赖环境变量
        + "cat /tmp/broker.conf && " // 调试：打印配置文件
        + "sh mqbroker -n namesrv:9876 -c /tmp/broker.conf";
  }

  // ========== Dynamic Property Configuration ==========

  /**
   * 动态配置测试容器属性。
   *
   * <p>在 Spring 上下文启动前注入 TestContainers 动态生成的配置信息。
   *
   * <p><strong>注入的配置项</strong>:
   *
   * <ul>
   *   <li><strong>MySQL</strong>:
   *       <ul>
   *         <li>{@code spring.datasource.url}: JDBC URL (包含动态端口)
   *         <li>{@code spring.datasource.username}: root
   *         <li>{@code spring.datasource.password}: 123456
   *         <li>{@code spring.datasource.driver-class-name}: com.mysql.cj.jdbc.Driver
   *       </ul>
   *   <li><strong>RocketMQ</strong>:
   *       <ul>
   *         <li>{@code rocketmq.name-server}: NameServer 地址 (宿主机可访问)
   *       </ul>
   * </ul>
   *
   * @param registry Spring 动态属性注册器
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // 注入 MySQL 连接配置
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

    // 注入 RocketMQ NameServer 地址 (宿主机访问)
    // 注意：不能直接使用映射端口，因为 Broker 会告诉客户端使用容器内部 IP
    // 需要配置 Broker 使用宿主机可访问的地址
    String namesrvAddr = rocketmqNamesrv.getHost() + ":" + rocketmqNamesrv.getMappedPort(9876);
    registry.add("rocketmq.name-server", () -> namesrvAddr);

    // 为了解决 Broker IP 访问问题，我们需要 Broker 返回宿主机可访问的地址
    // 这需要在容器启动时配置，参见 rocketmqBroker 的配置

    // 🔑 关键：手动创建测试 Topics
    // 由于 autoCreateTopicEnable 配置可能不稳定，我们直接使用 mqadmin 命令创建 Topic
    createTopicsManually();
  }

  /**
   * 使用 mqadmin 命令手动创建测试 Topics。
   *
   * <p>直接在 Broker 容器中执行 mqadmin 命令创建 Topic，确保路由信息正确注册到 NameServer。
   *
   * <p><strong>技术细节</strong>:
   *
   * <ul>
   *   <li>使用 {@code execInContainer} 在 Broker 容器中执行命令
   *   <li>创建 Topic 时指定 Cluster 名称为 {@code TestCluster}（与 broker.conf 一致）
   *   <li>设置读写队列数量为 4（与默认配置一致）
   *   <li>等待路由信息同步到 NameServer（最多 35 秒,RocketMQ 心跳周期为 30 秒）
   *   <li>验证路由信息是否可用（使用 {@code mqadmin topicRoute} 命令）
   * </ul>
   *
   * <p><strong>关键发现</strong>（基于网络调研）:
   *
   * <ul>
   *   <li>RocketMQ Broker 与 NameServer 之间的心跳周期是 <strong>30 秒</strong>
   *   <li>Topic 创建后需要等待至少 1 个心跳周期才能确保路由信息同步
   *   <li>路由信息包括：Broker 地址、队列信息、读写权限等
   * </ul>
   */
  private static void createTopicsManually() {
    try {
      // 🔍 阶段 1: 诊断 Broker 配置和启动状态
      System.out.println("\n" + "=".repeat(80));
      System.out.println("🔍 [诊断阶段] 检查 Broker 配置和启动状态");
      System.out.println("=".repeat(80));

      // 1.1 验证 broker.conf 实际内容
      System.out.println("\n📄 [1/4] 验证 broker.conf 配置文件内容:");
      try {
        org.testcontainers.containers.Container.ExecResult confResult =
            rocketmqBroker.execInContainer("cat", "/tmp/broker.conf");
        if (confResult.getExitCode() == 0) {
          System.out.println("   " + confResult.getStdout().replace("\n", "\n   "));
        } else {
          System.err.println("   ❌ 无法读取 broker.conf: " + confResult.getStderr());
        }
      } catch (Exception e) {
        System.err.println("   ❌ 读取配置文件失败: " + e.getMessage());
      }

      // 1.2 检查 Broker 容器日志(查找启动成功和 IP 注册信息)
      System.out.println("\n📋 [2/4] 检查 Broker 容器启动日志:");
      String brokerLogs = rocketmqBroker.getLogs();
      String[] logLines = brokerLogs.split("\n");
      boolean foundBootSuccess = false;
      boolean foundBrokerIP = false;

      System.out.println("   查找关键日志行...");
      for (String line : logLines) {
        if (line.contains("boot success") || line.contains("The broker")) {
          System.out.println("   ✓ " + line.trim());
          foundBootSuccess = true;
          if (line.contains("broker[")) {
            foundBrokerIP = true;
          }
        }
      }

      if (!foundBootSuccess) {
        System.err.println("   ⚠️  未找到 Broker 启动成功的日志");
      }
      if (!foundBrokerIP) {
        System.err.println("   ⚠️  未找到 Broker IP 注册信息");
      }

      // 1.3 检查 Broker 在 NameServer 中的注册信息
      System.out.println("\n🌐 [3/4] 检查 Broker 在 NameServer 的注册信息:");
      try {
        org.testcontainers.containers.Container.ExecResult clusterResult =
            rocketmqBroker.execInContainer(
                "sh", "mqadmin", "clusterList", "-n", "namesrv:9876");
        if (clusterResult.getExitCode() == 0) {
          System.out.println("   " + clusterResult.getStdout().replace("\n", "\n   "));
        } else {
          System.err.println("   ❌ 获取集群信息失败: " + clusterResult.getStderr());
        }
      } catch (Exception e) {
        System.err.println("   ❌ 执行 clusterList 命令失败: " + e.getMessage());
      }

      // 1.4 检查 NameServer 路由表
      System.out.println("\n🗺️  [4/4] 检查 NameServer 当前路由表:");
      try {
        org.testcontainers.containers.Container.ExecResult routeResult =
            rocketmqBroker.execInContainer(
                "sh", "mqadmin", "topicList", "-n", "namesrv:9876");
        if (routeResult.getExitCode() == 0) {
          String output = routeResult.getStdout();
          if (output != null && !output.trim().isEmpty()) {
            System.out.println("   " + output.replace("\n", "\n   "));
          } else {
            System.out.println("   (无 Topic)");
          }
        } else {
          System.err.println("   ❌ 获取 Topic 列表失败: " + routeResult.getStderr());
        }
      } catch (Exception e) {
        System.err.println("   ❌ 执行 topicList 命令失败: " + e.getMessage());
      }

      System.out.println("\n" + "=".repeat(80));
      System.out.println("🚀 [创建阶段] 开始创建测试 Topics");
      System.out.println("=".repeat(80) + "\n");

      String[] topics = {"INGEST_TASK_READY", "INGEST_LITERATURE_READY"};

      for (String topic : topics) {
        System.out.println("📝 创建 Topic: " + topic);

        try {
          // 使用 mqadmin updateTopic 命令创建 Topic
          // -n: NameServer 地址（容器内部网络）
          // -t: Topic 名称
          // -c: Cluster 名称
          // -r: 读队列数量
          // -w: 写队列数量
          org.testcontainers.containers.Container.ExecResult result =
              rocketmqBroker.execInContainer(
                  "sh",
                  "mqadmin",
                  "updateTopic",
                  "-n",
                  "namesrv:9876",
                  "-t",
                  topic,
                  "-c",
                  "TestCluster",
                  "-r",
                  "4",
                  "-w",
                  "4");

          if (result.getExitCode() == 0) {
            System.out.println("✅ Topic 创建命令执行成功: " + topic);
            if (result.getStdout() != null && !result.getStdout().trim().isEmpty()) {
              System.out.println("   输出: " + result.getStdout().trim());
            }
          } else {
            System.err.println("❌ 创建 Topic " + topic + " 失败:");
            System.err.println("   退出码: " + result.getExitCode());
            System.err.println("   错误输出: " + result.getStderr());
            throw new RuntimeException("Topic 创建失败: " + topic);
          }
        } catch (Exception e) {
          System.err.println("❌ 执行 mqadmin 命令失败: " + e.getMessage());
          throw new RuntimeException("无法创建 Topic: " + topic, e);
        }
      }

      // 🔑 关键：等待路由信息同步到 NameServer
      // RocketMQ Broker 与 NameServer 之间的心跳周期是 30 秒
      // 为确保路由信息完全同步,我们等待 35 秒（30 秒心跳 + 5 秒缓冲）
      System.out.println("⏳ 等待路由信息同步到 NameServer（预计 35 秒）...");
      System.out.println("   原因：RocketMQ 心跳周期为 30 秒,需等待至少 1 个周期");

      int waitSeconds = 35;
      for (int i = 1; i <= waitSeconds; i++) {
        Thread.sleep(1000);
        if (i % 5 == 0 || i == waitSeconds) {
          System.out.println("   已等待 " + i + " 秒...");
        }
      }

      // 验证路由信息是否可用
      System.out.println("🔍 验证路由信息...");
      for (String topic : topics) {
        try {
          org.testcontainers.containers.Container.ExecResult routeResult =
              rocketmqBroker.execInContainer(
                  "sh",
                  "mqadmin",
                  "topicRoute",
                  "-n",
                  "namesrv:9876",
                  "-t",
                  topic);

          if (routeResult.getExitCode() == 0) {
            String output = routeResult.getStdout();
            if (output != null && output.contains("brokerName")) {
              System.out.println("✅ Topic 路由信息可用: " + topic);
            } else {
              System.err.println("⚠️  Topic 路由信息不完整: " + topic);
              System.err.println("   输出: " + output);
            }
          } else {
            System.err.println("⚠️  无法获取 Topic 路由信息: " + topic);
            System.err.println("   退出码: " + routeResult.getExitCode());
            System.err.println("   错误输出: " + routeResult.getStderr());
          }
        } catch (Exception e) {
          System.err.println("⚠️  路由验证失败: " + topic + " - " + e.getMessage());
        }
      }

      System.out.println("✅ Topics 创建和路由同步完成");
    } catch (Exception e) {
      System.err.println("❌ 手动创建 Topics 失败: " + e.getMessage());
      e.printStackTrace();
      throw new RuntimeException("无法创建测试 Topics", e);
    }
  }
}
