package com.patra.ingest.integration;

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
   *   <li><strong>容器重用</strong>: 启用,加速测试执行
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
   *   <li><strong>端口</strong>: 10909 (VIP), 10911 (主服务), 8081 (Dashboard)
   *   <li><strong>依赖</strong>: NameServer 容器必须先启动
   *   <li><strong>配置</strong>: autoCreateTopicEnable=true (自动创建 Topic,简化测试)
   *   <li><strong>等待策略</strong>: 日志出现 "The broker.*success"
   *   <li><strong>容器重用</strong>: 启用,加速测试执行
   * </ul>
   *
   * <p><strong>重要</strong>: Broker 配置为 ASYNC_MASTER 模式,适合测试环境。
   */
  @Container
  protected static final GenericContainer<?> rocketmqBroker =
      new GenericContainer<>("apache/rocketmq:5.3.1")
          .withExposedPorts(10909, 10911, 8081)
          .withEnv("NAMESRV_ADDR", "namesrv:9876")
          .withEnv("JAVA_OPT_EXT", "-Xms512m -Xmx512m")
          .withCommand(
              "sh",
              "-c",
              // 动态生成 broker.conf 并启动
              // 关键：brokerIP1 使用 host.testcontainers.internal（跨平台兼容）
              "echo 'brokerClusterName=DefaultCluster' > /tmp/broker.conf && "
                  + "echo 'brokerName=broker-a' >> /tmp/broker.conf && "
                  + "echo 'brokerId=0' >> /tmp/broker.conf && "
                  + "echo 'autoCreateTopicEnable=true' >> /tmp/broker.conf && "
                  + "echo 'autoCreateSubscriptionGroup=true' >> /tmp/broker.conf && "
                  + "echo 'defaultTopicQueueNums=4' >> /tmp/broker.conf && "
                  + "echo 'brokerRole=ASYNC_MASTER' >> /tmp/broker.conf && "
                  + "echo 'flushDiskType=ASYNC_FLUSH' >> /tmp/broker.conf && "
                  + "echo 'deleteWhen=04' >> /tmp/broker.conf && "
                  + "echo 'fileReservedTime=48' >> /tmp/broker.conf && "
                  + "echo 'listenPort=10911' >> /tmp/broker.conf && "
                  + "echo 'brokerIP1=host.testcontainers.internal' >> /tmp/broker.conf && "
                  + "cat /tmp/broker.conf && " // 调试：打印配置文件
                  + "sh mqbroker -n namesrv:9876 -c /tmp/broker.conf")
          .withNetwork(network)
          .withNetworkAliases("broker")
          .withAccessToHost(true)
          .dependsOn(rocketmqNamesrv)
          .waitingFor(Wait.forLogMessage(".*The broker.*success.*", 1))
          .withReuse(false); // 禁用容器重用以避免配置缓存问题

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
    String namesrvAddr = rocketmqNamesrv.getHost() + ":" + rocketmqNamesrv.getMappedPort(9876);
    registry.add("rocketmq.name-server", () -> namesrvAddr);
  }
}
