package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.util.Optional;
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

/// UserProfileRepositoryAdapter 集成测试。不用 JUnit 的 `@DisplayName`：它和值对象同名。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserProfileRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
class UserProfileRepositoryAdapterIT {

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserProfileRepositoryAdapter profiles;

  @Test
  void should_save_empty_profile_and_find_by_user_id() {
    User user = users.save(User.register(EmailAddress.of("profile@example.com")));

    UserProfile saved = profiles.save(UserProfile.create(user.getId()));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getVersion()).isZero();
    assertThat(profiles.findByUserId(user.getId()))
        .get()
        .extracting(UserProfile::getDisplayName)
        .isEqualTo(Optional.empty());
    assertThat(profiles.findByUserId(-1L)).isEmpty();
  }

  @Test
  void should_persist_rename() {
    User user = users.save(User.register(EmailAddress.of("rename@example.com")));
    UserProfile saved = profiles.save(UserProfile.create(user.getId()));

    saved.rename(new DisplayName("陈宇"));
    profiles.save(saved);

    assertThat(profiles.findByUserId(user.getId()))
        .get()
        .extracting(profile -> profile.getDisplayName().map(DisplayName::value))
        .isEqualTo(Optional.of("陈宇"));
  }

  @Test
  void should_reject_second_profile_for_same_user() {
    User user = users.save(User.register(EmailAddress.of("twice-profile@example.com")));
    profiles.save(UserProfile.create(user.getId()));

    assertThatThrownBy(() -> profiles.save(UserProfile.create(user.getId())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
