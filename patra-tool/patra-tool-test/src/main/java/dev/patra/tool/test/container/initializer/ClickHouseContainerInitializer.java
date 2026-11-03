package dev.patra.tool.test.container.initializer;

import dev.patra.tool.test.container.ContainerRegistry;
import dev.patra.tool.test.container.ContainerType;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.DockerImageName;

/// ClickHouse 测试容器初始化器。
///
/// 同一个 JVM 里只启动一个容器，注册到 {@link ContainerRegistry}，多个测试类共用。
/// 镜像版本与 mini 上 pipeline 栈的 ClickHouse 一致；建好 `patra_analytics` 库和一个能读写它的账号。
/// 不挂 mini 的低内存配置与账号文件：测试关心的是 SQL 与客户端行为。
///
/// Spring Boot 没有 ClickHouse 的自动配置，连接参数写成 `patra.clickhouse.*`：
/// `url` 是 HTTP 接口地址（`http://主机:端口`），另有 `username`、`password`。
///
/// 用法：`@ContextConfiguration(initializers = ClickHouseContainerInitializer.class)`。
public class ClickHouseContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(ClickHouseContainerInitializer.class);

  private static final String CLICKHOUSE_IMAGE = "clickhouse/clickhouse-server:26.8.36.3";

  private static final String DATABASE = "patra_analytics";

  private static final int HTTP_PORT = 8123;

  private static final Object LOCK = new Object();

  /// 返回已启动的 ClickHouse 容器，第一次调用时启动。
  ///
  /// @return ClickHouse 容器
  public static ClickHouseContainer getClickHouseContainer() {
    synchronized (LOCK) {
      if (!ContainerRegistry.isRegistered(ContainerType.CLICKHOUSE)) {
        ClickHouseContainer clickhouse =
            new ClickHouseContainer(DockerImageName.parse(CLICKHOUSE_IMAGE))
                .withDatabaseName(DATABASE);
        clickhouse.start();
        flushSystemLogs(clickhouse);
        ContainerRegistry.register(ContainerType.CLICKHOUSE, clickhouse);
        log.info("ClickHouse 容器已启动: {}", httpUrl(clickhouse));
      }
      return ContainerRegistry.get(ContainerType.CLICKHOUSE, ClickHouseContainer.class);
    }
  }

  /// 把容器的连接参数写进 Spring 环境。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    ClickHouseContainer clickhouse = getClickHouseContainer();
    TestPropertyValues.of(
            "patra.clickhouse.url=" + httpUrl(clickhouse),
            "patra.clickhouse.username=" + clickhouse.getUsername(),
            "patra.clickhouse.password=" + clickhouse.getPassword())
        .applyTo(applicationContext.getEnvironment());
  }

  /// 立刻落一次系统日志：`system.part_log` 等表要到第一次落盘（启动后约 7.5 秒）才建出来，
  /// 读它的视图（patra-analytics 的数据质量 ADS 表）在那之前建不了。
  ///
  /// @param clickhouse 已启动的容器
  private static void flushSystemLogs(ClickHouseContainer clickhouse) {
    try {
      Container.ExecResult result =
          clickhouse.execInContainer("clickhouse-client", "-q", "SYSTEM FLUSH LOGS");
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("ClickHouse 落系统日志失败: " + result.getStderr());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("ClickHouse 落系统日志失败", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ClickHouse 落系统日志被中断", e);
    }
  }

  /// 拼出容器 HTTP 接口的地址。
  ///
  /// @param clickhouse 已启动的容器
  /// @return `http://主机:映射端口`
  private static String httpUrl(ClickHouseContainer clickhouse) {
    return "http://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(HTTP_PORT);
  }
}
