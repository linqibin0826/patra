package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({UserRepositoryAdapter.class, JpaAuditingConfig.class, HibernatePropertiesCustomizer.class})
@ComponentScan(
    basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserRepositoryAdapter 集成测试")
class UserRepositoryAdapterIT {

  @Autowired private UserRepositoryAdapter repository;

  @Test
  @DisplayName("保存新用户时分配雪花 ID，能按邮箱和 ID 查回")
  void should_assign_id_and_find_saved_user() {
    EmailAddress email = EmailAddress.of("save.find@example.com");

    User saved = repository.save(User.register(email));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getVersion()).isNotNull();
    assertThat(repository.findByEmail(email))
        .get()
        .extracting(User::getId)
        .isEqualTo(saved.getId());
    assertThat(repository.findById(saved.getId())).isPresent();
    assertThat(repository.existsByEmail(email)).isTrue();
    assertThat(repository.existsByEmail(EmailAddress.of("nobody@example.com"))).isFalse();
  }

  @Test
  @DisplayName("邮箱撞上唯一约束时抛 EmailAlreadyRegisteredException")
  void should_translate_email_unique_violation() {
    EmailAddress email = EmailAddress.of("duplicate@example.com");
    repository.save(User.register(email));

    assertThatThrownBy(() -> repository.save(User.register(email)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  @DisplayName("封禁后保存，查回来是封禁状态")
  void should_persist_ban() {
    User saved = repository.save(User.register(EmailAddress.of("ban.me@example.com")));
    Instant now = Instant.parse("2026-10-06T08:00:00Z");

    saved.ban(now);
    repository.save(saved);

    User reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(reloaded.getBannedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("用过期的版本保存时报乐观锁冲突")
  void should_reject_stale_version() {
    User saved = repository.save(User.register(EmailAddress.of("stale@example.com")));
    User stale = repository.findById(saved.getId()).orElseThrow();
    saved.ban(Instant.parse("2026-10-06T08:00:00Z"));
    repository.save(saved);

    stale.ban(Instant.parse("2026-10-06T09:00:00Z"));

    assertThatThrownBy(() -> repository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }
}
