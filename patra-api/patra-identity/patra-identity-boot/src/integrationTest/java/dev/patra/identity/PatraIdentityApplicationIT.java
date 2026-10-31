package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// identity 应用能启动，Flyway 建好表。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@Import(IdentitySecurityMailITSpringConfig.class)
@DisplayName("identity 应用启动")
class PatraIdentityApplicationIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Environment environment;

  @Test
  @DisplayName("启动时 Flyway 用一份 V1 建好八张表")
  void should_create_identity_tables_on_startup() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name LIKE 'idn\\_%' "
                + "ORDER BY table_name",
            String.class);

    assertThat(tables)
        .containsExactly(
            "idn_email_token",
            "idn_user",
            "idn_user_former_username",
            "idn_user_login_record",
            "idn_user_password_credential",
            "idn_user_profile",
            "idn_user_profile_link",
            "idn_user_research_interest");
  }

  @Test
  @DisplayName("Flyway 建好事件发布登记表 event_publication（表名由 Modulith 固定，不加 idn_ 前缀）")
  void should_create_event_publication_table() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'event_publication'",
            String.class);

    assertThat(tables).containsExactly("event_publication");
  }

  @Test
  @DisplayName("不靠 dev 配置也有 Redis 超时：Redis 卡住时登录几秒内返回 503，不用等 60 秒")
  void should_bound_redis_timeouts_without_dev_profile() {
    assertThat(environment.getProperty("spring.data.redis.timeout")).isEqualTo("5s");
    assertThat(environment.getProperty("spring.data.redis.connect-timeout")).isEqualTo("10s");
  }
}
