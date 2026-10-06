package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserPasswordCredentialRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  UserRepositoryAdapter.class,
  UserPasswordCredentialRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserPasswordCredentialRepositoryAdapter 集成测试")
class UserPasswordCredentialRepositoryAdapterIT {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHQ$aGFzaGhhc2g");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserPasswordCredentialRepositoryAdapter credentials;

  @Test
  @DisplayName("保存后按用户 ID 查回哈希")
  void should_save_and_find_by_user_id() {
    User user = users.save(User.register(EmailAddress.of("credential@example.com")));

    UserPasswordCredential saved =
        credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThat(saved.getId()).isPositive();
    assertThat(credentials.findByUserId(user.getId()))
        .get()
        .extracting(UserPasswordCredential::getPasswordHash)
        .isEqualTo(HASH);
    assertThat(credentials.findByUserId(-1L)).isEmpty();
  }

  @Test
  @DisplayName("同一个用户不能有两份密码凭据")
  void should_reject_second_credential_for_same_user() {
    User user = users.save(User.register(EmailAddress.of("twice@example.com")));
    credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThatThrownBy(() -> credentials.save(UserPasswordCredential.create(user.getId(), HASH)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
