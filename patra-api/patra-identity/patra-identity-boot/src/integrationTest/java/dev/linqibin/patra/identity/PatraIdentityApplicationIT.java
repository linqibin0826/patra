package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// identity 应用能启动，Flyway 建好表。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@DisplayName("identity 应用启动")
class PatraIdentityApplicationIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Environment environment;

  @Test
  @DisplayName("启动时 Flyway 建好两张表")
  void should_create_identity_tables_on_startup() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name LIKE 'idn\\_%' "
                + "ORDER BY table_name",
            String.class);

    assertThat(tables).containsExactly("idn_user", "idn_user_password_credential");
  }

  @Test
  @DisplayName("不靠 dev 配置也有 Redis 超时：Redis 卡住时登录几秒内返回 503，不用等 60 秒")
  void should_bound_redis_timeouts_without_dev_profile() {
    assertThat(environment.getProperty("spring.data.redis.timeout")).isEqualTo("5s");
    assertThat(environment.getProperty("spring.data.redis.connect-timeout")).isEqualTo("10s");
  }
}
