package dev.patra.identity.infra.account.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.infra.account.persistence.mapper.UserJpaMapper;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.infra.session.persistence.mapper.UserLoginRecordJpaMapper;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserPasswordCredentialRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserPasswordCredentialRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackageClasses = {UserJpaMapper.class, UserLoginRecordJpaMapper.class})
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
    User user =
        users.save(
            User.register(EmailAddress.of("credential@example.com"), Username.of("u-credential")));

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
    User user =
        users.save(User.register(EmailAddress.of("twice@example.com"), Username.of("u-twice")));
    credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThatThrownBy(() -> credentials.save(UserPasswordCredential.create(user.getId(), HASH)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("按用户删凭据：删掉返回真，再删返回假")
  void should_delete_credential_by_user_id_and_report_whether_a_row_existed() {
    User user =
        users.save(User.register(EmailAddress.of("delete@example.com"), Username.of("u-delete")));
    credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThat(credentials.deleteByUserId(user.getId())).isTrue();
    assertThat(credentials.findByUserId(user.getId())).isEmpty();
    assertThat(credentials.deleteByUserId(user.getId())).isFalse();
  }
}
