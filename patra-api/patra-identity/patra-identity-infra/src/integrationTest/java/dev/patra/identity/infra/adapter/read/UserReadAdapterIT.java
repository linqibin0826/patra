package dev.patra.identity.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.infra.adapter.persistence.UserRepositoryAdapter;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserReadAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserReadAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserReadAdapter 集成测试")
class UserReadAdapterIT {

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserReadAdapter reader;

  @Test
  @DisplayName("按 ID 读账号：邮箱和状态；封禁后状态跟着变；不存在返回空")
  void should_read_account_by_id() {
    User saved = users.save(User.register(EmailAddress.of("read.me@example.com")));

    assertThat(reader.findAccount(saved.getId()))
        .contains(UserAccountReadModel.of(saved.getId(), "read.me@example.com", UserStatus.ACTIVE));

    saved.ban(Instant.parse("2026-10-09T08:00:00Z"));
    users.save(saved);
    assertThat(reader.findAccount(saved.getId()))
        .map(UserAccountReadModel::status)
        .contains(UserStatus.BANNED);
    assertThat(reader.findAccount(1L)).isEmpty();
  }
}
