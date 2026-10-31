package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITFixture;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 哈希期间不占数据库连接：Argon2 可能排队几秒，这期间占着连接会拖垮连接池。
///
/// 量的是整个连接池的活动连接数。安全邮件提交后由异步线程外送（PAP-104），它删登记行时也要借连接，所以每个请求之前
/// 先等登记表清空、连接池空闲：请求里的哈希都在它自己的事务之前，那时池里有连接只可能是请求线程占着。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@Import(IdentitySecurityMailITSpringConfig.class)
@AutoConfigureRestTestClient
@DisplayName("哈希期间不占数据库连接")
class PasswordHashingConnectionIT {

  @Autowired private RestTestClient restClient;
  @Autowired private DataSource dataSource;
  @Autowired private IdentitySecurityMailITFixture securityMails;

  @MockitoSpyBean private PasswordHashingPort passwordHashing;

  @Test
  @DisplayName("注册、登录、邮箱不存在的登录：哈希和校验期间活动连接数都是 0")
  void should_not_hold_database_connection_while_hashing() throws SQLException {
    HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
    List<Integer> activeConnections = new CopyOnWriteArrayList<>();
    Answer<Object> recordActiveConnections =
        invocation -> {
          activeConnections.add(pool.getActiveConnections());
          return invocation.callRealMethod();
        };
    doAnswer(recordActiveConnections).when(passwordHashing).hash(any());
    doAnswer(recordActiveConnections).when(passwordHashing).matches(any(), any());
    doAnswer(recordActiveConnections).when(passwordHashing).verifyAgainstDummy(any());

    awaitIdle(pool);
    post("/auth/register", "pool@example.com", "Pool-Holder-01");
    awaitIdle(pool);
    post("/auth/login", "pool@example.com", "Pool-Holder-01");
    awaitIdle(pool);
    post("/auth/login", "pool-nobody@example.com", "Pool-Holder-02");

    assertThat(activeConnections).hasSize(3).containsOnly(0);
  }

  /// 等上一个请求登记的安全邮件外送完、连接池里没有借出的连接。
  ///
  /// @param pool 连接池
  private void awaitIdle(HikariPoolMXBean pool) {
    securityMails.awaitRegistryDrained();
    await().atMost(Duration.ofSeconds(5)).until(() -> pool.getActiveConnections() == 0);
  }

  /// 发一个带邮箱和密码的 POST。
  ///
  /// @param uri 路径
  /// @param email 邮箱
  /// @param password 密码
  private void post(String uri, String email, String password) {
    restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "username", usernameFor(email), "password", password))
        .exchange();
  }

  /// 按邮箱派生一个合法的用户名：本地部分小写、`.` 换成 `-`，前面加 `u-`。
  ///
  /// @param email 邮箱
  /// @return 用户名
  private static String usernameFor(String email) {
    String local = email.strip().toLowerCase(Locale.ROOT);
    return "u-" + local.substring(0, local.indexOf('@')).replace('.', '-');
  }
}
