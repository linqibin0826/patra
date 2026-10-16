package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/// UserRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserRepositoryAdapter 集成测试")
class UserRepositoryAdapterIT {

  @Autowired private UserRepositoryAdapter repository;
  @Autowired private PlatformTransactionManager transactionManager;

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
        .isInstanceOf(UserModifiedConcurrentlyException.class);
  }

  @Test
  @DisplayName("加锁读用户后，另一个事务的封禁保存要等到这个事务提交才完成")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void should_block_concurrent_ban_until_locking_transaction_commits() throws Exception {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    long userId =
        transactions.execute(
            status ->
                repository.save(User.register(EmailAddress.of("lock.me@example.com"))).getId());
    CountDownLatch locked = new CountDownLatch(1);
    // 加锁事务即将提交时置位：放在回调里而不是 execute 返回之后，否则提交与置位之间有空档，
    // 被放行的封禁事务可能先读到 false
    AtomicBoolean lockerCommitting = new AtomicBoolean(false);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> locker =
          pool.submit(
              () -> {
                transactions.executeWithoutResult(
                    status -> {
                      assertThat(repository.findByIdForUpdate(userId)).isPresent();
                      locked.countDown();
                      sleepQuietly(500);
                      lockerCommitting.set(true);
                    });
              });
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      long started = System.nanoTime();
      Future<Boolean> banner =
          pool.submit(
              () ->
                  transactions.execute(
                      status -> {
                        User user = repository.findById(userId).orElseThrow();
                        user.ban(Instant.parse("2026-10-09T08:00:00Z"));
                        repository.save(user);
                        return lockerCommitting.get();
                      }));

      boolean lockerWasCommitting = banner.get(10, TimeUnit.SECONDS);
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
      locker.get(10, TimeUnit.SECONDS);

      assertThat(lockerWasCommitting).isTrue();
      assertThat(elapsedMillis).isGreaterThanOrEqualTo(400);
      assertThat(repository.findById(userId).orElseThrow().getStatus())
          .isEqualTo(UserStatus.BANNED);
    } finally {
      pool.shutdownNow();
    }
  }

  /// 睡一会儿，中断就提前结束。
  ///
  /// @param millis 毫秒
  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
