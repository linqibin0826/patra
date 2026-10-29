package dev.patra.interaction.infra.reaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/// 有帮助的写端：明细的插入与删除只在真发生时报告，两张统计表按 upsert 加减，删除联动清空一个目标，目标锁串行化。
class HelpfulRepositoryAdapterIT extends ReactionPersistenceITSupport {

  private static final HelpfulTarget C1 = HelpfulTarget.of(HelpfulTargetType.COMMENT, 101L);
  private static final HelpfulTarget C2 = HelpfulTarget.of(HelpfulTargetType.COMMENT, 102L);
  private static final HelpfulTarget P1 = HelpfulTarget.of(HelpfulTargetType.POST, 101L);
  private static final Instant T0 = Instant.parse("2026-10-29T08:00:00Z");

  @Autowired private HelpfulRepository helpful;

  /// 同一人同一目标只插一次；同 ID 不同种类是两个目标。
  @Test
  void should_insert_once_and_ignore_duplicate() {
    Boolean first = transactions.execute(s -> helpful.insert(1L, C1, 9L, T0));
    Boolean again = transactions.execute(s -> helpful.insert(1L, C1, 9L, T0));
    Boolean post = transactions.execute(s -> helpful.insert(1L, P1, null, T0));

    assertThat(first).isTrue();
    assertThat(again).isFalse();
    assertThat(post).isTrue();

    assertThat(count("SELECT count(*) FROM reaction.helpful")).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT credited_user_id FROM reaction.helpful WHERE target_type = 'COMMENT'",
                Long.class))
        .isEqualTo(9L);
    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT created_at FROM reaction.helpful WHERE target_type = 'COMMENT'",
                    OffsetDateTime.class)
                .toInstant())
        .isEqualTo(T0);
    assertThat(helpful.exists(1L, C1)).isTrue();
    assertThat(helpful.exists(2L, C1)).isFalse();
  }

  /// 删到了才有值，带上当初的受益人；没有这条为空。
  @Test
  void should_return_mark_only_when_deleted() {
    transactions.executeWithoutResult(
        s -> {
          helpful.insert(1L, C1, 9L, T0);
          helpful.insert(1L, P1, null, T0);
        });

    Optional<HelpfulMark> deleted = transactions.execute(s -> helpful.delete(1L, C1));
    Optional<HelpfulMark> again = transactions.execute(s -> helpful.delete(1L, C1));
    Optional<HelpfulMark> anonymous = transactions.execute(s -> helpful.delete(1L, P1));

    assertThat(deleted).contains(HelpfulMark.of(9L));
    assertThat(again).isEmpty();
    assertThat(anonymous).contains(HelpfulMark.of(null));
  }

  /// 两张统计表都能加减（加是 upsert）；没有统计行的目标计数为 0。
  @Test
  void should_adjust_counters_by_upsert() {
    transactions.executeWithoutResult(
        s -> {
          helpful.adjustTargetCount(C1, 1);
          helpful.adjustTargetCount(C1, 1);
          helpful.adjustTargetCount(C1, -1);
          helpful.adjustReceived(9L, 1);
          helpful.adjustReceived(9L, 1);
          helpful.adjustReceived(9L, -1);
        });

    assertThat(helpful.countOf(C1)).isEqualTo(1);
    assertThat(helpful.countOf(C2)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT helpful_received FROM reaction.user_stats WHERE user_id = 9", Long.class))
        .isEqualTo(1L);
  }

  /// 删除联动：删掉一个目标的全部明细与统计行，按受益人汇总（受益人为空的不在结果里），别的目标不动。
  @Test
  void should_delete_all_on_target_and_group_by_credited_user() {
    transactions.executeWithoutResult(
        s -> {
          helpful.insert(1L, C1, 9L, T0);
          helpful.insert(2L, C1, 9L, T0);
          helpful.insert(3L, C1, null, T0);
          helpful.adjustTargetCount(C1, 3);
          helpful.insert(1L, C2, 9L, T0);
          helpful.adjustTargetCount(C2, 1);
        });

    Map<Long, Integer> removed = transactions.execute(s -> helpful.deleteAllOn(C1));

    assertThat(removed).containsExactlyEntriesOf(Map.of(9L, 2));

    assertThat(count("SELECT count(*) FROM reaction.helpful WHERE target_id = 101")).isZero();
    assertThat(count("SELECT count(*) FROM reaction.target_stats WHERE target_id = 101")).isZero();
    assertThat(helpful.countOf(C2)).isEqualTo(1);
    Map<Long, Integer> nothing = transactions.execute(s -> helpful.deleteAllOn(C1));
    assertThat(nothing).isEmpty();
  }

  /// 目标锁在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  void should_require_transaction_for_lock() {
    assertThatThrownBy(() -> helpful.lockTarget(C1))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 同一目标的锁在别的事务持有时一直等到它提交；不同目标（含同 ID 不同种类）不互等。
  @Test
  void should_serialize_on_target_lock() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
      CompletableFuture<Void> holder =
          CompletableFuture.runAsync(
              () ->
                  transactions.executeWithoutResult(
                      s -> {
                        helpful.lockTarget(C1);
                        locked.countDown();
                        await(release);
                      }),
              pool);
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

      CompletableFuture<Void> other =
          CompletableFuture.runAsync(
              () -> transactions.executeWithoutResult(s -> helpful.lockTarget(P1)), pool);
      other.get(5, TimeUnit.SECONDS);
      CompletableFuture<Void> same =
          CompletableFuture.runAsync(
              () -> transactions.executeWithoutResult(s -> helpful.lockTarget(C1)), pool);
      Thread.sleep(300);
      assertThat(same).isNotDone();

      release.countDown();
      holder.get(5, TimeUnit.SECONDS);
      same.get(5, TimeUnit.SECONDS);
    }
  }

  /// 等信号。
  ///
  /// @param latch 信号
  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
