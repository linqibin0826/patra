package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
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
  @Autowired private JdbcTemplate jdbcTemplate;

  /// 锁用户行在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("锁用户行必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> repository.findByIdForUpdate(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("保存新用户时分配雪花 ID，能按邮箱、用户名和 ID 查回")
  void should_assign_id_and_find_saved_user() {
    EmailAddress email = EmailAddress.of("save.find@example.com");
    Username username = Username.of("u-save-find");

    User saved = repository.save(User.register(email, username));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getVersion()).isNotNull();
    assertThat(saved.isEmailVerified()).isFalse();
    assertThat(repository.findByEmail(email))
        .get()
        .extracting(User::getId)
        .isEqualTo(saved.getId());
    assertThat(repository.findByUsername(username))
        .get()
        .extracting(User::getId)
        .isEqualTo(saved.getId());
    assertThat(repository.findById(saved.getId())).isPresent();
    assertThat(repository.existsByEmail(email)).isTrue();
    assertThat(repository.existsByEmail(EmailAddress.of("nobody@example.com"))).isFalse();
    assertThat(repository.existsByUsername(username)).isTrue();
    assertThat(repository.existsByUsername(Username.of("nobody"))).isFalse();
  }

  @Test
  @DisplayName("邮箱撞上唯一约束时抛 UserIdentifierTakenException，字段是 email")
  void should_translate_email_unique_violation() {
    EmailAddress email = EmailAddress.of("duplicate@example.com");
    repository.save(User.register(email, Username.of("u-dup-1")));

    assertThatThrownBy(() -> repository.save(User.register(email, Username.of("u-dup-2"))))
        .isInstanceOfSatisfying(
            UserIdentifierTakenException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .satisfies(
                        v -> {
                          assertThat(v.field()).isEqualTo("email");
                          assertThat(v.code()).isEqualTo("ALREADY_REGISTERED");
                        }));
  }

  @Test
  @DisplayName("用户名撞上唯一约束时抛 UserIdentifierTakenException，字段是 username")
  void should_translate_username_unique_violation() {
    Username username = Username.of("u-same-name");
    repository.save(User.register(EmailAddress.of("same-1@example.com"), username));

    assertThatThrownBy(
            () -> repository.save(User.register(EmailAddress.of("same-2@example.com"), username)))
        .isInstanceOfSatisfying(
            UserIdentifierTakenException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .extracting(FieldViolation::code)
                    .isEqualTo("ALREADY_TAKEN"));
  }

  @Test
  @DisplayName("已注销的用户没有邮箱也能保存和查回；用户名仍被占用，邮箱已释放")
  void should_persist_and_find_deleted_user_without_email() {
    EmailAddress email = EmailAddress.of("gone@example.com");
    Username username = Username.of("u-gone");
    User saved = repository.save(User.register(email, username));
    Instant now = Instant.parse("2026-10-20T08:00:00Z");
    saved.requestDeletion(now, Duration.ofDays(15));
    saved.completeDeletion(now.plus(Duration.ofDays(15)));

    repository.save(saved);

    User reloaded = repository.findByUsername(username).orElseThrow();
    assertThat(reloaded.isDeleted()).isTrue();
    assertThat(reloaded.getEmail()).isEmpty();
    assertThat(reloaded.getDeletedAt()).isEqualTo(now.plus(Duration.ofDays(15)));
    assertThat(repository.existsByUsername(username)).isTrue();
    assertThat(repository.existsByEmail(email)).isFalse();
    assertThat(repository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("封禁后保存，查回来是封禁状态")
  void should_persist_ban() {
    User saved =
        repository.save(
            User.register(EmailAddress.of("ban.me@example.com"), Username.of("u-ban-me")));
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
    User saved =
        repository.save(
            User.register(EmailAddress.of("stale@example.com"), Username.of("u-stale")));
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
                repository
                    .save(
                        User.register(
                            EmailAddress.of("lock.me@example.com"), Username.of("u-lock-me")))
                    .getId());
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

  /// 注销申请时刻。
  private static final Instant REQUESTED = Instant.parse("2026-10-20T08:00:00Z");

  /// 冷静期。
  private static final Duration COOLING = Duration.ofDays(15);

  /// 存一个在冷静期的用户，到期时间是申请时刻加冷静期。
  ///
  /// @param name 用户名，也拼进邮箱
  /// @param requestedAt 申请时刻
  /// @return 保存后的用户
  private User savePending(String name, Instant requestedAt) {
    User user =
        repository.save(User.register(EmailAddress.of(name + "@example.com"), Username.of(name)));
    user.requestDeletion(requestedAt, COOLING);
    return repository.save(user);
  }

  @Test
  @DisplayName("注销提醒标记能存能读，取消后读回为空")
  void should_round_trip_deletion_reminded_at() {
    User user = savePending("u-remind-trip", REQUESTED);
    Instant remindedAt = REQUESTED.plus(Duration.ofDays(12));
    user.markDeletionReminded(remindedAt);
    repository.save(user);

    User reloaded = repository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getDeletionRemindedAt()).isEqualTo(remindedAt);

    reloaded.cancelDeletion();
    repository.save(reloaded);
    assertThat(repository.findById(user.getId()).orElseThrow().getDeletionRemindedAt()).isNull();
  }

  @Test
  @DisplayName("不在冷静期的行写注销提醒标记被约束拒绝")
  void should_reject_reminded_at_outside_pending_deletion() {
    User active =
        repository.save(
            User.register(
                EmailAddress.of("remind.active@example.com"), Username.of("u-remind-act")));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE idn_user SET deletion_reminded_at = now() WHERE id = ?",
                    active.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("到期扫描：只取冷静期且已到期的，按 ID 升序、从游标之后取、受上限约束")
  void should_find_due_ids_in_id_order_after_cursor() {
    User first = savePending("u-due-first", REQUESTED);
    User second = savePending("u-due-second", REQUESTED);
    User third = savePending("u-due-third", REQUESTED);
    savePending("u-due-later", REQUESTED.plus(Duration.ofDays(1)));
    repository.save(
        User.register(EmailAddress.of("due.active@example.com"), Username.of("u-due-active")));
    Instant now = REQUESTED.plus(COOLING);
    long cursor = first.getId() - 1;

    assertThat(repository.findIdsDueForDeletion(now, cursor, 2))
        .containsExactly(first.getId(), second.getId());
    assertThat(repository.findIdsDueForDeletion(now, second.getId(), 2))
        .containsExactly(third.getId());
  }

  @Test
  @DisplayName("提醒扫描：只取窗口内、未提醒的冷静期用户")
  void should_find_reminder_ids_inside_window_only() {
    Instant now = REQUESTED.plus(Duration.ofDays(12));
    User inside = savePending("u-rem-inside", REQUESTED);
    User reminded = savePending("u-rem-done", REQUESTED);
    reminded.markDeletionReminded(now);
    repository.save(reminded);
    savePending("u-rem-outside", REQUESTED.plus(Duration.ofDays(1)));

    List<Long> ids =
        repository.findIdsDueForDeletionReminder(
            now, now.plus(Duration.ofDays(3)), inside.getId() - 1, 100);

    assertThat(ids).containsExactly(inside.getId());
  }

  @Test
  @DisplayName("提醒扫描不取已经到期的用户：交给执行任务")
  void should_not_pick_overdue_users_for_reminder() {
    User overdue = savePending("u-rem-overdue", REQUESTED);
    Instant now = REQUESTED.plus(COOLING).plusSeconds(1);

    assertThat(
            repository.findIdsDueForDeletionReminder(
                now, now.plus(Duration.ofDays(3)), overdue.getId() - 1, 100))
        .doesNotContain(overdue.getId());
  }
}
