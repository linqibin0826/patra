package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.model.aggregate.FormerUsername;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.time.Duration;
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

/// FormerUsernameRepositoryAdapter 集成测试：保留期按传入的时间判断。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  FormerUsernameRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("FormerUsernameRepositoryAdapter 集成测试")
class FormerUsernameRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private FormerUsernameRepositoryAdapter formerUsernames;

  @Test
  @DisplayName("到期前能查到保留记录，到期后查不到；本人不算「别人」")
  void should_find_reserved_record_before_expiry_only() {
    long userId = saveUser("former-a@example.com");
    Username oldName = Username.of("old-name");

    FormerUsername saved =
        formerUsernames.save(FormerUsername.reserve(userId, oldName, T0, Duration.ofDays(90)));

    assertThat(saved.getId()).isPositive();
    assertThat(formerUsernames.findReserved(oldName, T0.plus(Duration.ofDays(89))))
        .hasValueSatisfying(former -> assertThat(former.getUserId()).isEqualTo(userId));
    assertThat(formerUsernames.findReserved(oldName, T0.plus(Duration.ofDays(90)))).isEmpty();
    assertThat(formerUsernames.existsReserved(oldName, T0)).isTrue();
    assertThat(formerUsernames.existsReserved(oldName, T0.plus(Duration.ofDays(90)))).isFalse();
    assertThat(formerUsernames.existsReservedByOther(oldName, userId, T0)).isFalse();
    assertThat(formerUsernames.existsReservedByOther(oldName, userId + 1, T0)).isTrue();
  }

  @Test
  @DisplayName("同一个旧名先后属于两个人：取未到期里最近的一条")
  void should_return_latest_unexpired_record_when_same_name_reused() {
    long a = saveUser("former-b@example.com");
    long b = saveUser("former-c@example.com");
    Username shared = Username.of("shared");
    formerUsernames.save(FormerUsername.reserve(a, shared, T0, Duration.ofDays(90)));
    formerUsernames.save(
        FormerUsername.reserve(b, shared, T0.plus(Duration.ofDays(100)), Duration.ofDays(90)));

    assertThat(formerUsernames.findReserved(shared, T0.plus(Duration.ofDays(120))))
        .hasValueSatisfying(former -> assertThat(former.getUserId()).isEqualTo(b));
  }

  /// 存一个用户，返回 ID。
  ///
  /// @param email 邮箱
  /// @return 用户 ID
  private long saveUser(String email) {
    return users.save(User.register(EmailAddress.of(email))).getId();
  }
}
