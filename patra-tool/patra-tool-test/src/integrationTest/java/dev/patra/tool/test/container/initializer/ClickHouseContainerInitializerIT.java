package dev.patra.tool.test.container.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// ClickHouseContainerInitializer 集成测试，需要本机 Docker。
///
/// 直接用 ClickHouse 的 HTTP 接口：SQL 放在请求体里 POST，账号密码放在两个请求头里。
@DisplayName("ClickHouseContainerInitializer 集成测试")
class ClickHouseContainerInitializerIT {

  /// 本机 JVM 可能继承系统代理，连容器映射端口时显式不走代理。
  private final HttpClient http =
      HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

  /// 容器在这里启动：拉镜像、等 ClickHouse 就绪在 CI 上要十几秒，放在测试方法里会撞上单个测试 15 秒的默认超时
  /// （它只管测试方法，不管 `@BeforeAll`）。
  @BeforeAll
  static void startContainer() {
    ClickHouseContainerInitializer.getClickHouseContainer();
  }

  @Test
  @DisplayName("启动 ClickHouse 容器，写进环境的账号能在 patra_analytics 里写读一行")
  void should_start_clickhouse_and_publish_connection_properties() throws Exception {
    ConfigurableEnvironment environment;
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new ClickHouseContainerInitializer().initialize(context);
      environment = context.getEnvironment();
    }
    String url = environment.getProperty("patra.clickhouse.url");
    String username = environment.getProperty("patra.clickhouse.username");
    String password = environment.getProperty("patra.clickhouse.password");
    assertThat(url).startsWith("http://");
    assertThat(username).isNotBlank();
    assertThat(password).isNotBlank();

    execute(
        url,
        username,
        password,
        "CREATE TABLE patra_analytics.pap165_it (id UInt32, name String)"
            + " ENGINE = MergeTree ORDER BY id");
    execute(url, username, password, "INSERT INTO patra_analytics.pap165_it VALUES (1, 'hello')");

    assertThat(
            execute(
                url,
                username,
                password,
                "SELECT id, name FROM patra_analytics.pap165_it FORMAT TabSeparated"))
        .isEqualTo("1\thello\n");
  }

  @Test
  @DisplayName("多次获取拿到的是同一个容器")
  void should_reuse_the_same_container() {
    assertThat(ClickHouseContainerInitializer.getClickHouseContainer())
        .isSameAs(ClickHouseContainerInitializer.getClickHouseContainer());
  }

  /// 执行一条 SQL，断言 200 并返回响应体。
  ///
  /// @param url HTTP 接口地址
  /// @param username 账号
  /// @param password 密码
  /// @param sql 要执行的 SQL
  /// @return 响应体
  private String execute(String url, String username, String password, String sql)
      throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("X-ClickHouse-User", username)
                .header("X-ClickHouse-Key", password)
                .POST(HttpRequest.BodyPublishers.ofString(sql))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return response.body();
  }
}
