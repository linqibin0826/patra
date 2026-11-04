package dev.patra.identity.infra.account.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.infra.account.persistence.mapper.UserJpaMapper;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.infra.session.persistence.mapper.UserLoginRecordJpaMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// ExternalIdentityRepositoryAdapter 集成测试：两条唯一约束靠 `ON CONFLICT DO NOTHING` 判定，事务还能继续用。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  ExternalIdentityRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackageClasses = {UserJpaMapper.class, UserLoginRecordJpaMapper.class})
@ActiveProfiles("test")
@DisplayName("ExternalIdentityRepositoryAdapter 集成测试")
class ExternalIdentityRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private ExternalIdentityRepositoryAdapter identities;
  @Autowired private JdbcTemplate jdbcTemplate;

  private long newUser(String name) {
    return users
        .save(User.register(EmailAddress.of(name + "@example.com"), Username.of(name)))
        .getId();
  }

  private static ExternalIdentity github(long userId, String subject) {
    return ExternalIdentity.link(userId, IdentityProvider.GITHUB, subject, "octocat", T0);
  }

  @Test
  @DisplayName("写入后按 (provider, subject) 和 (user_id, provider) 都能查回")
  void should_insert_and_find() {
    long userId = newUser("u-ext-find");

    assertThat(identities.insertIfAbsent(github(userId, "1001"))).isTrue();

    ExternalIdentity found =
        identities.findByProviderAndSubject(IdentityProvider.GITHUB, "1001").orElseThrow();
    assertThat(found.getId()).isNotNull();
    assertThat(found.getUserId()).isEqualTo(userId);
    assertThat(found.getAccountLabel()).isEqualTo("octocat");
    assertThat(found.getLinkedAt()).isEqualTo(T0);
    assertThat(identities.findByUserIdAndProvider(userId, IdentityProvider.GITHUB)).isPresent();
    assertThat(identities.findByUserIdAndProvider(userId, IdentityProvider.ORCID)).isEmpty();
    assertThat(identities.findAllByUserId(userId)).hasSize(1);
    assertThat(identities.countByUserId(userId)).isEqualTo(1);
  }

  @Test
  @DisplayName("同一个第三方账号绑第二个用户：返回 false，事务可以继续")
  void should_report_conflict_on_same_subject() {
    long first = newUser("u-ext-first");
    long second = newUser("u-ext-second");
    identities.insertIfAbsent(github(first, "1002"));

    assertThat(identities.insertIfAbsent(github(second, "1002"))).isFalse();

    assertThat(identities.findAllByUserId(second)).isEmpty();
  }

  @Test
  @DisplayName("同一个用户绑同一家的另一个账号：返回 false")
  void should_report_conflict_on_same_user_and_provider() {
    long userId = newUser("u-ext-twice");
    identities.insertIfAbsent(github(userId, "1003"));

    assertThat(identities.insertIfAbsent(github(userId, "1004"))).isFalse();
  }

  @Test
  @DisplayName("登录后保存：账号标识和最近登录时间落库")
  void should_save_login_update() {
    long userId = newUser("u-ext-login");
    identities.insertIfAbsent(github(userId, "1005"));
    ExternalIdentity found =
        identities.findByProviderAndSubject(IdentityProvider.GITHUB, "1005").orElseThrow();

    found.recordLogin("renamed", T0.plusSeconds(60));
    identities.save(found);

    ExternalIdentity reloaded =
        identities.findByProviderAndSubject(IdentityProvider.GITHUB, "1005").orElseThrow();
    assertThat(reloaded.getAccountLabel()).isEqualTo("renamed");
    assertThat(reloaded.getLastLoginAt()).isEqualTo(T0.plusSeconds(60));
  }

  @Test
  @DisplayName("按用户和平台删一行；按用户删全部")
  void should_delete() {
    long userId = newUser("u-ext-delete");
    identities.insertIfAbsent(github(userId, "1006"));
    identities.insertIfAbsent(
        ExternalIdentity.link(userId, IdentityProvider.ORCID, "0000-0002-1825-0097", "x", T0));

    assertThat(identities.deleteByUserIdAndProvider(userId, IdentityProvider.GITHUB)).isTrue();
    assertThat(identities.deleteByUserIdAndProvider(userId, IdentityProvider.GITHUB)).isFalse();
    assertThat(identities.countByUserId(userId)).isEqualTo(1);

    identities.deleteAllByUserId(userId);

    assertThat(identities.countByUserId(userId)).isZero();
    Long remaining =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM idn_user_external_identity WHERE user_id = ?",
            Long.class,
            userId);
    assertThat(remaining).isZero();
  }
}
