package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 哈希期间不占数据库连接：Argon2 可能排队几秒，这期间占着连接会拖垮连接池。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@DisplayName("哈希期间不占数据库连接")
class PasswordHashingConnectionIT {

  @Autowired private RestTestClient restClient;
  @Autowired private DataSource dataSource;

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

    post("/auth/register", "pool@example.com", "Pool-Holder-01");
    post("/auth/login", "pool@example.com", "Pool-Holder-01");
    post("/auth/login", "pool-nobody@example.com", "Pool-Holder-02");

    assertThat(activeConnections).hasSize(3).containsOnly(0);
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
        .body(Map.of("email", email, "password", password))
        .exchange();
  }
}
