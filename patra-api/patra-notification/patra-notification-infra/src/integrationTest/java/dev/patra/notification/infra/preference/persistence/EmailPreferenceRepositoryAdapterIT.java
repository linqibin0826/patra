package dev.patra.notification.infra.preference.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;

/// 偏好的写端：插入不覆盖已有行，按用户 / 按令牌读，行锁串行化并发修改，注销清理的游标与删除，表约束。
class EmailPreferenceRepositoryAdapterIT extends PreferencePersistenceITSupport {

  private static final Instant T1 = Instant.parse("2026-10-31T09:00:00Z");

  @Autowired private EmailPreferenceRepository preferences;

  /// 已经有行时插入什么都不做：档位和令牌都不被默认值覆盖。
  @Test
  void insert_if_absent_does_not_overwrite_existing_row() {
    insertRow(7L, "DIGEST", "OFF", TOKEN_A);

    transactions.executeWithoutResult(
        s ->
            preferences.insertIfAbsent(
                EmailPreference.createDefault(7L, new UnsubscribeToken(TOKEN_B)), T1));

    EmailPreference found = preferences.findByUserId(7L).orElseThrow();
    assertThat(found.getReply()).isEqualTo(EmailMode.DIGEST);
    assertThat(found.getMention()).isEqualTo(EmailMode.OFF);
    assertThat(found.getUnsubscribeToken().value()).isEqualTo(TOKEN_A);
    assertThat(rows()).isEqualTo(1L);
  }

  /// 没有行时插入，写上档位、令牌与时间。
  @Test
  void insert_if_absent_creates_row() {
    transactions.executeWithoutResult(
        s ->
            preferences.insertIfAbsent(
                EmailPreference.createDefault(7L, new UnsubscribeToken(TOKEN_A)), T1));

    EmailPreference found = preferences.findByUserId(7L).orElseThrow();
    assertThat(found.getReply()).isEqualTo(EmailMode.INSTANT);
    assertThat(found.getMention()).isEqualTo(EmailMode.INSTANT);
    assertThat(createdAt(7L)).isEqualTo(T1);
  }

  /// 按用户、按令牌都能读到同一行；读不到为空。
  @Test
  void finds_by_user_and_by_token() {
    insertRow(7L, "INSTANT", "DIGEST", TOKEN_A);

    assertThat(preferences.findByUserId(7L))
        .get()
        .extracting(EmailPreference::getMention)
        .isEqualTo(EmailMode.DIGEST);
    assertThat(preferences.findByToken(new UnsubscribeToken(TOKEN_A)))
        .get()
        .extracting(EmailPreference::getUserId)
        .isEqualTo(7L);
    assertThat(preferences.findByUserId(8L)).isEmpty();
    assertThat(preferences.findByToken(new UnsubscribeToken(TOKEN_B))).isEmpty();
  }

  /// 加锁读必须在调用方的事务里。
  @Test
  void for_update_reads_require_transaction() {
    assertThatThrownBy(() -> preferences.findByUserIdForUpdate(7L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> preferences.findByTokenForUpdate(new UnsubscribeToken(TOKEN_A)))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 写回两类档位和更新时间，令牌与创建时间不动。
  @Test
  void update_modes_writes_both_modes_and_updated_at() {
    insertRow(7L, "INSTANT", "INSTANT", TOKEN_A);

    transactions.executeWithoutResult(
        s -> {
          EmailPreference locked =
              preferences.findByTokenForUpdate(new UnsubscribeToken(TOKEN_A)).orElseThrow();
          locked.change(NotificationCategory.REPLY, EmailMode.OFF);
          locked.change(NotificationCategory.MENTION, EmailMode.DIGEST);
          preferences.updateModes(locked, T1);
        });

    EmailPreference found = preferences.findByUserId(7L).orElseThrow();
    assertThat(found.getReply()).isEqualTo(EmailMode.OFF);
    assertThat(found.getMention()).isEqualTo(EmailMode.DIGEST);
    assertThat(found.getUnsubscribeToken().value()).isEqualTo(TOKEN_A);
    assertThat(updatedAt(7L)).isEqualTo(T1);
    assertThat(createdAt(7L)).isEqualTo(Instant.parse("2026-10-30T08:00:00Z"));
  }

  /// 同一个人同时改两类：后一个事务在行锁上等前一个提交，读到的是新值，两项都生效、不会互相覆盖。
  @Test
  void should_serialize_concurrent_changes_on_row_lock() throws Exception {
    insertRow(7L, "INSTANT", "INSTANT", TOKEN_A);
    CountDownLatch firstLocked = new CountDownLatch(1);

    CompletableFuture<Void> first =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      EmailPreference locked = preferences.findByUserIdForUpdate(7L).orElseThrow();
                      firstLocked.countDown();
                      sleep(300);
                      locked.change(NotificationCategory.REPLY, EmailMode.DIGEST);
                      preferences.updateModes(locked, T1);
                    }));
    assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Void> second =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      EmailPreference locked = preferences.findByUserIdForUpdate(7L).orElseThrow();
                      locked.change(NotificationCategory.MENTION, EmailMode.OFF);
                      preferences.updateModes(locked, T1);
                    }));
    CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

    EmailPreference found = preferences.findByUserId(7L).orElseThrow();
    assertThat(found.getReply()).isEqualTo(EmailMode.DIGEST);
    assertThat(found.getMention()).isEqualTo(EmailMode.OFF);
  }

  /// 注销清理的游标：按用户 ID 升序、游标不含、受条数上限约束。
  @Test
  void find_users_after_respects_cursor_and_limit() {
    insertRow(9L, "INSTANT", "INSTANT", TOKEN_A);
    insertRow(3L, "INSTANT", "INSTANT", TOKEN_B);
    insertRow(5L, "INSTANT", "INSTANT", "C".repeat(43));

    assertThat(preferences.findUsersAfter(0L, 10)).containsExactly(3L, 5L, 9L);
    assertThat(preferences.findUsersAfter(3L, 1)).containsExactly(5L);
    assertThat(preferences.findUsersAfter(9L, 10)).isEmpty();
  }

  /// 删掉这个人的行，令牌随之查不到。
  @Test
  void delete_by_user_id_returns_rows_and_frees_token() {
    insertRow(7L, "INSTANT", "INSTANT", TOKEN_A);

    Integer deleted = transactions.execute(s -> preferences.deleteByUserId(7L));
    Integer again = transactions.execute(s -> preferences.deleteByUserId(7L));

    assertThat(deleted).isEqualTo(1);
    assertThat(again).isZero();
    assertThat(preferences.findByToken(new UnsubscribeToken(TOKEN_A))).isEmpty();
  }

  /// 档位只能是三个值之一；两个人不能有同一个令牌。断言约束名，确认是哪条约束挡下的。
  @Test
  void constraints_reject_bad_mode_and_duplicate_token() {
    assertThatThrownBy(() -> insertRow(7L, "WEEKLY", "INSTANT", TOKEN_A))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_email_preference_reply_mode");

    insertRow(7L, "INSTANT", "INSTANT", TOKEN_A);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    s ->
                        preferences.insertIfAbsent(
                            EmailPreference.createDefault(8L, new UnsubscribeToken(TOKEN_A)), T1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_email_preference_token");
  }

  /// 读创建时间。
  ///
  /// @param userId 用户 ID
  /// @return 创建时间
  private Instant createdAt(long userId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT created_at FROM preference.email_preference WHERE user_id = ?",
            OffsetDateTime.class,
            userId)
        .toInstant();
  }

  /// 读更新时间。
  ///
  /// @param userId 用户 ID
  /// @return 更新时间
  private Instant updatedAt(long userId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT updated_at FROM preference.email_preference WHERE user_id = ?",
            OffsetDateTime.class,
            userId)
        .toInstant();
  }

  /// 睡一会儿，让另一个事务撞上行锁。
  ///
  /// @param millis 毫秒
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
