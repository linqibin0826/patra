package dev.patra.interaction.infra.subject.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/// 发表计数：每个作者每种一行的读写、作者锁、注销清理的游标与删除。
class PublishQuotaRepositoryAdapterIT extends SubjectRegistryITSupport {

  private static final long U = 1001L;
  private static final Instant T0 = Instant.parse("2026-10-27T08:00:00Z");
  private static final LocalDate DAY = LocalDate.of(2026, 10, 27);

  @Autowired private PublishQuotaRepository quotas;
  @Autowired private DataSource dataSource;

  /// 每例从空表开始。
  @BeforeEach
  void truncateQuotas() {
    jdbcTemplate.update("TRUNCATE subject.publish_quota");
  }

  /// 配额锁在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @DisplayName("配额锁必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> quotas.lock(1L)).isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void should_round_trip_quota() {
    PublishQuota first = new PublishQuota(DAY, 1, T0);
    PublishQuota second = new PublishQuota(DAY, 2, T0.plusSeconds(30));

    transactions.executeWithoutResult(s -> quotas.save(U, PublishKind.COMMENT, first));
    assertThat(quotas.find(U, PublishKind.COMMENT)).contains(first);
    transactions.executeWithoutResult(s -> quotas.save(U, PublishKind.COMMENT, second));
    assertThat(quotas.find(U, PublishKind.COMMENT)).contains(second);
    assertThat(quotas.find(U, PublishKind.POST)).isEmpty();
  }

  @Test
  void should_keep_kinds_apart() {
    transactions.executeWithoutResult(
        s -> {
          quotas.save(U, PublishKind.COMMENT, new PublishQuota(DAY, 5, T0));
          quotas.save(U, PublishKind.POST, new PublishQuota(DAY, 1, T0));
        });

    assertThat(quotas.find(U, PublishKind.COMMENT).orElseThrow().dayCount()).isEqualTo(5);
    assertThat(quotas.find(U, PublishKind.POST).orElseThrow().dayCount()).isEqualTo(1);
  }

  /// 作者锁：别的连接持有同一把锁时，`lock` 一直等到它释放。
  @Test
  void should_block_on_publish_lock_held_elsewhere() throws Exception {
    try (Connection holder = dataSource.getConnection()) {
      try (PreparedStatement lock =
          holder.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
        lock.setString(1, "subject.publish:" + U);
        lock.execute();
      }
      try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
        CompletableFuture<Void> locked =
            CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(s -> quotas.lock(U)), pool);
        awaitBlockedOnLock();
        assertThat(locked).isNotDone();

        try (PreparedStatement unlock =
            holder.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
          unlock.setString(1, "subject.publish:" + U);
          unlock.execute();
        }
        locked.get();
      }
    }
  }

  /// 注销清理：按用户 ID 升序的游标（每人只出现一次），删一个人的全部行。
  @Test
  void should_list_users_after_cursor_and_delete_all_of_one() {
    transactions.executeWithoutResult(
        s -> {
          quotas.save(1L, PublishKind.COMMENT, new PublishQuota(DAY, 1, T0));
          quotas.save(1L, PublishKind.POST, new PublishQuota(DAY, 1, T0));
          quotas.save(2L, PublishKind.COMMENT, new PublishQuota(DAY, 1, T0));
          quotas.save(3L, PublishKind.POST, new PublishQuota(DAY, 1, T0));
        });

    assertThat(quotas.findUsersAfter(0L, 2)).containsExactly(1L, 2L);
    assertThat(quotas.findUsersAfter(2L, 2)).containsExactly(3L);
    Integer deleted = transactions.execute(s -> quotas.deleteAllOf(1L));
    assertThat(deleted).isEqualTo(2);
    assertThat(quotas.findUsersAfter(0L, 10)).containsExactly(2L, 3L);
  }

  /// 等到有会话在等 advisory lock。
  ///
  /// @throws InterruptedException 被中断
  private void awaitBlockedOnLock() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      Long waiting =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND wait_event = 'advisory'",
              Long.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("没有等到 advisory lock 上的等待");
  }
}
