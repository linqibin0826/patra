package com.patra.ingest.integration;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
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
 *   <li><strong>路由验证</strong>: 使用 Awaitility 确保 Topic 路由信息同步完成
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @DisplayName("Plan 仓储集成测试")
 * class PlanRepositoryIT extends BaseIT {
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
 * <h3>RocketMQ TestContainers 配置说明</h3>
 *
 * <p>基于 Apache Camel 的成功实现,关键配置:
 * <ul>
 *   <li>不配置 brokerIP1 - 让 RocketMQ 自动检测</li>
 *   <li>使用容器网络别名 (nameserver, broker)</li>
 *   <li>使用 Awaitility 等待 Topic 路由信息同步</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 * @see <a href="../../../../../../../TESTING.md">测试指南</a>
 * @see RocketMQContainerSupport
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
public abstract class BaseIT {

  private static final Logger log = LoggerFactory.getLogger(BaseIT.class);

  // ========== Docker Network ==========

  /**
   * Docker 网络 (MySQL 和 RocketMQ 容器共享)。
   *
   * <p>RocketMQ Broker 需要通过内部网络访问 NameServer。
   */
  protected static final Network network = Network.newNetwork();

  // ========== RocketMQ Container Support ==========

  /**
   * RocketMQ TestContainers 支持类。
   *
   * <p>基于 Apache Camel 的成功实现,关键特性:
   * <ul>
   *   <li>不配置 brokerIP1 - 让 RocketMQ 自动检测</li>
   *   <li>使用容器网络别名进行内部通信</li>
   *   <li>使用 Awaitility 等待 Topic 路由信息同步</li>
   * </ul>
   */
  protected static RocketMQContainerSupport rocketmqSupport;

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

  // ========== Container Lifecycle Management ==========

  /**
   * 初始化 RocketMQ 容器并创建必需的 Topics。
   *
   * <p>在所有测试之前执行一次,负责:
   * <ul>
   *   <li>启动 RocketMQ NameServer 和 Broker 容器</li>
   *   <li>创建测试所需的 Topics (INGEST_TASK_READY, INGEST_LITERATURE_READY)</li>
   *   <li>等待 Topic 路由信息同步完成</li>
   * </ul>
   */
  @BeforeAll
  static void setupRocketMQ() {
    log.info("========================================");
    log.info("初始化 RocketMQ TestContainers");
    log.info("========================================");

    rocketmqSupport = new RocketMQContainerSupport();
    rocketmqSupport.start();

    log.info("NameServer 地址: {}", rocketmqSupport.getNameserverAddress());

    // 创建测试所需的 Topics
    String[] topics = {"INGEST_TASK_READY", "INGEST_LITERATURE_READY"};
    for (String topic : topics) {
      log.info("创建测试 Topic: {}", topic);
      rocketmqSupport.createTopic(topic);
    }

    log.info("========================================");
    log.info("RocketMQ TestContainers 初始化完成");
    log.info("========================================");
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

    // 注入 RocketMQ NameServer 地址 (宿主机访问,使用动态映射端口)
    String nameServerAddr = rocketmqSupport.getNameserverAddress();
    registry.add("rocketmq.name-server", () -> nameServerAddr);

    log.info("动态配置 RocketMQ NameServer: {}", nameServerAddr);
  }
}
