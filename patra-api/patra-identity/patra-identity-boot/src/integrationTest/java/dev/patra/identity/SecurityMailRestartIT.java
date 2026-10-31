package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.identity.api.account.event.EmailVerificationMailEvent;
import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITFixture;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.identity.config.IdentityStalledAsyncITSpringConfig;
import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/// 验收：identity 登记了安全邮件、还没外送出去就没了，重启后补投送达（PAP-104）。
///
/// 两种「没发出去」：外送还没开始进程就没了（登记行停在 `PUBLISHED`，用丢掉一切异步任务的执行器模拟），以及外送失败
/// （broker 停着，登记行 `FAILED`）。之后都关掉整个 identity（进程里的一切都没了，只剩库），换一个全新的实例跑补投。
/// 正在发时被杀（`PROCESSING`）的 10 分钟保护由事件模块的测试覆盖。两个实例都用真实时钟，补投的最小年龄调成 1 毫秒。
@DisplayName("安全邮件：重启后补投")
class SecurityMailRestartIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

  private final HttpClient http = HttpClient.newHttpClient();

  private ConfigurableApplicationContext running;

  /// 收尾：broker 起来、实例关掉、残留的登记行删掉，不拖累同一个 JVM 里的其他测试。
  @AfterEach
  void cleanUp() throws SQLException {
    rabbitmqctl("start_app");
    if (running != null) {
      running.close();
    }
    PostgreSQLContainer<?> postgres = PostgreSQLContainerInitializer.getPostgreSQLContainer();
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM event_publication");
    }
  }

  @Test
  @Timeout(value = 3, unit = TimeUnit.MINUTES)
  @DisplayName("登记后、外送前 identity 没了：用户与 PUBLISHED 的登记行一起落库；换新实例补投后验证邮件事件送达、事件 ID 不变、链接能用、登记行删除")
  void should_deliver_mail_registered_by_an_instance_gone_before_externalizing() throws Exception {
    String email = "restart.killed@example.com";
    running = start(IdentityStalledAsyncITSpringConfig.class);
    register(email, "u-restart-killed");
    JdbcTemplate firstJdbc = running.getBean(JdbcTemplate.class);
    assertThat(firstJdbc.queryForList("SELECT status FROM event_publication", String.class))
        .as("外送任务没跑，登记行停在 PUBLISHED")
        .containsExactly("PUBLISHED");
    UUID registeredEventId = registeredEventId(firstJdbc);
    running.close();

    running = start();
    assertDeliveredAfterResubmit(email, registeredEventId);
  }

  @Test
  @Timeout(value = 3, unit = TimeUnit.MINUTES)
  @DisplayName("broker 停着时注册：用户与登记行一起落库、外送失败；关掉 identity、换新实例补投后验证邮件事件送达、事件 ID 不变、链接能用、登记行删除")
  void should_deliver_registered_mail_after_identity_restarts() throws Exception {
    String email = "restart.outbox@example.com";
    rabbitmqctl("stop_app");
    running = start();
    register(email, "u-restart-outbox");
    JdbcTemplate firstJdbc = running.getBean(JdbcTemplate.class);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        firstJdbc.queryForList(
                            "SELECT status FROM event_publication", String.class))
                    .as("外送失败，登记行留着")
                    .containsExactly("FAILED"));
    UUID registeredEventId = registeredEventId(firstJdbc);
    running.close();

    rabbitmqctl("start_app");
    running = start();
    assertDeliveredAfterResubmit(email, registeredEventId);
  }

  /// 在新实例上补投，断言验证邮件事件送达、事件 ID 不变、链接能用、登记行删除。
  ///
  /// @param email 注册邮箱
  /// @param registeredEventId 登记行里存的事件 ID
  private void assertDeliveredAfterResubmit(String email, UUID registeredEventId)
      throws IOException, InterruptedException {
    int resubmitted = running.getBean(EventPublicationResubmitter.class).resubmit();

    assertThat(resubmitted).isEqualTo(1);
    IdentitySecurityMailITFixture securityMails =
        running.getBean(IdentitySecurityMailITFixture.class);
    EmailVerificationMailEvent mail = securityMails.await(EmailVerificationMailEvent.class, email);
    assertThat(mail.eventId()).as("补投不换事件 ID").isEqualTo(registeredEventId);
    HttpResponse<String> verified =
        post("/auth/email/verify", Map.of("token", tokenIn(mail.verifyUrl())));
    assertThat(verified.statusCode()).as(verified.body()).isEqualTo(200);
    securityMails.awaitRegistryDrained();
    assertThat(securityMails.registryRows()).as("送达后登记行删除").isZero();
  }

  /// 经 HTTP 注册一个用户，断言 201。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  private void register(String email, String username) throws IOException, InterruptedException {
    HttpResponse<String> registered =
        post(
            "/auth/register",
            Map.of("email", email, "username", username, "password", "Correct-Horse-02"));
    assertThat(registered.statusCode()).as(registered.body()).isEqualTo(201);
  }

  /// 登记表里唯一一行存的事件 ID。
  ///
  /// @param jdbc 实例的 JDBC 模板
  /// @return 事件 ID
  private static UUID registeredEventId(JdbcTemplate jdbc) {
    return UUID.fromString(
        JSON.readTree(
                jdbc.queryForObject("SELECT serialized_event FROM event_publication", String.class))
            .get("eventId")
            .asString());
  }

  /// 起一个 identity 实例：测试 profile、真实容器、随机端口，带捕获队列。
  ///
  /// @param extraSources 额外的配置类
  /// @return 应用上下文
  private static ConfigurableApplicationContext start(Class<?>... extraSources) {
    Class<?>[] sources = new Class<?>[extraSources.length + 2];
    sources[0] = PatraIdentityApplication.class;
    sources[1] = IdentitySecurityMailITSpringConfig.class;
    System.arraycopy(extraSources, 0, sources, 2, extraSources.length);
    return new SpringApplicationBuilder(sources)
        .profiles("test")
        .initializers(
            new IdentityITPostgreSQLContainerInitializer(),
            new RedisContainerInitializer(),
            new RabbitMQContainerInitializer())
        .properties("server.port=0", "patra.events.resubmit.min-age=1ms")
        .run();
  }

  /// 向正在跑的实例发一个 JSON POST。
  ///
  /// @param path 路径
  /// @param body 请求体
  /// @return 响应
  private HttpResponse<String> post(String path, Map<String, Object> body)
      throws IOException, InterruptedException {
    String port = running.getEnvironment().getRequiredProperty("local.server.port");
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /// 从链接里取令牌。
  ///
  /// @param link 链接
  /// @return 令牌
  private static String tokenIn(String link) {
    Matcher matcher = TOKEN_IN_LINK.matcher(link);
    assertThat(matcher.find()).as("链接里有令牌").isTrue();
    return matcher.group(1);
  }

  /// 在 RabbitMQ 容器里执行一条 `rabbitmqctl`：停启应用、容器不停，映射端口不变。
  ///
  /// @param command 子命令
  private static void rabbitmqctl(String command) {
    try {
      ExecResult result =
          RabbitMQContainerInitializer.getRabbitMQContainer()
              .execInContainer("rabbitmqctl", command);
      assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    } catch (IOException e) {
      throw new IllegalStateException("rabbitmqctl " + command + " 执行失败", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("rabbitmqctl " + command + " 被中断", e);
    }
  }
}
