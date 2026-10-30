package dev.patra.interaction.infra.collection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/// 文献库条目的写端：幂等插入、备注、连带移出收藏夹、注销删除、用户锁。
class LibraryRepositoryAdapterIT extends CollectionPersistenceITSupport {

  @Autowired private LibraryRepository library;
  @Autowired private CollectionRepository collections;
  @Autowired private DataSource dataSource;

  /// 文献库锁在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @DisplayName("文献库锁必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> library.lockOwner(U))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 同一 (用户, 对象) 只插一次，第二次不改收藏时间。
  @Test
  void should_insert_item_once() {
    assertThat(tx(() -> library.insertItem(U, S1, T0))).isTrue();
    assertThat(tx(() -> library.insertItem(U, S1, T1))).isFalse();

    assertThat(library.findItem(U, S1)).contains(LibraryEntry.of(S1, null, T0));
    assertThat(library.countItems(U)).isEqualTo(1);
    assertThat(library.countItems(V)).isZero();
  }

  /// 复制用的批量插入：已有的跳过，收藏时间按序递减 1 微秒。
  @Test
  void should_insert_items_in_order_skipping_existing() {
    txRun(() -> library.insertItem(U, S2, T0));

    assertThat(tx(() -> library.insertItems(U, List.of(S1, S2, S3), T1))).isEqualTo(2);

    assertThat(library.findItem(U, S1).orElseThrow().collectedAt()).isEqualTo(T1);
    assertThat(library.findItem(U, S3).orElseThrow().collectedAt())
        .isEqualTo(T1.minus(2, ChronoUnit.MICROS));
    assertThat(library.findItem(U, S2).orElseThrow().collectedAt()).isEqualTo(T0);
  }

  /// 查哪些已在库里。
  @Test
  void should_find_existing_items() {
    txRun(() -> library.insertItem(U, S1, T0));

    assertThat(library.findExistingItems(U, List.of(S1, S2))).isEqualTo(Set.of(S1));
  }

  /// 改备注与清空备注；不在库里回 false。
  @Test
  void should_update_and_clear_note() {
    txRun(() -> library.insertItem(U, S1, T0));

    assertThat(tx(() -> library.updateNote(U, S1, "组会讲"))).isTrue();
    assertThat(library.findItem(U, S1).orElseThrow().note()).isEqualTo("组会讲");
    assertThat(tx(() -> library.updateNote(U, S1, null))).isTrue();
    assertThat(library.findItem(U, S1).orElseThrow().note()).isNull();
    assertThat(tx(() -> library.updateNote(U, S2, "x"))).isFalse();
  }

  /// 移除条目时同时移出它所在的每个收藏夹，篇数减一、更新时间推进。
  @Test
  void should_remove_item_from_every_collection() {
    CollectionSummary a = tx(() -> collections.create(U, name("A"), null, T0));
    CollectionSummary b = tx(() -> collections.create(U, name("B"), null, T0));
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(U, S2, T0);
          collections.addMembers(U, a.collectionId(), List.of(S1, S2), T1);
          collections.addMembers(U, b.collectionId(), List.of(S1), T1);
        });

    txRun(() -> library.deleteItems(U, List.of(S1), T2));

    CollectionSummary aNow = collections.findOwned(U, a.collectionId()).orElseThrow();
    assertThat(aNow.itemCount()).isEqualTo(1);
    assertThat(aNow.updatedAt()).isEqualTo(T2);
    assertThat(collections.findOwned(U, b.collectionId()).orElseThrow().itemCount()).isZero();
    assertThat(count("collection.collection_item")).isEqualTo(1L);
    assertThat(library.findItem(U, S1)).isEmpty();
    assertThat(library.findItem(U, S2)).isPresent();
  }

  /// 删收藏夹不删条目。
  @Test
  void should_keep_items_when_collection_deleted() {
    CollectionSummary a = tx(() -> collections.create(U, name("A"), null, T0));
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          collections.addMembers(U, a.collectionId(), List.of(S1), T1);
        });

    txRun(() -> collections.delete(U, a.collectionId()));

    assertThat(count("collection.collection")).isZero();
    assertThat(count("collection.collection_item")).isZero();
    assertThat(library.findItem(U, S1)).isPresent();
  }

  /// 注销删除只删这个人的三类数据。
  @Test
  void should_delete_all_data_of_one_user_only() {
    for (long user : List.of(U, V)) {
      CollectionSummary c = tx(() -> collections.create(user, name("组会"), null, T0));
      txRun(
          () -> {
            library.insertItem(user, S1, T0);
            library.insertItem(user, S2, T0);
            collections.addMembers(user, c.collectionId(), List.of(S1), T1);
          });
    }

    DeletedUserData deleted = tx(() -> library.deleteAllOf(U));

    assertThat(deleted).isEqualTo(DeletedUserData.of(2, 1, 1));
    assertThat(library.countItems(V)).isEqualTo(2);
    assertThat(count("collection.collection")).isEqualTo(1L);
    assertThat(count("collection.collection_item")).isEqualTo(1L);
  }

  /// 用户锁：别的连接持有同一把锁时，`lockOwner` 一直等到它释放。
  @Test
  void should_block_on_owner_lock_held_elsewhere() throws Exception {
    try (Connection holder = dataSource.getConnection()) {
      try (PreparedStatement lock =
          holder.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
        lock.setString(1, "collection.user:" + U);
        lock.execute();
      }
      try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
        CompletableFuture<Boolean> insert =
            CompletableFuture.supplyAsync(
                () ->
                    tx(
                        () -> {
                          library.lockOwner(U);
                          return library.insertItem(U, S1, T0);
                        }),
                pool);
        awaitBlockedOnLock();
        assertThat(insert).isNotDone();

        try (PreparedStatement unlock =
            holder.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
          unlock.setString(1, "collection.user:" + U);
          unlock.execute();
        }
        assertThat(insert.get()).isTrue();
      }
    }
  }

  /// 收藏人数：每人每篇只算一次，重复收藏不加。
  @Test
  void should_count_collector_once_per_user() {
    assertThat(tx(() -> library.insertItem(U, S1, T0))).isTrue();
    assertThat(tx(() -> library.insertItem(U, S1, T1))).isFalse();
    txRun(() -> library.insertItem(V, S1, T0));

    assertThat(collectors(S1)).isEqualTo(2);
    assertThat(collectors(S2)).isZero();
  }

  /// 复制收藏夹的批量插入只给新插入的条目 +1。
  @Test
  void should_count_only_new_items_when_copying() {
    txRun(() -> library.insertItem(U, S1, T0));

    assertThat(tx(() -> library.insertItems(U, List.of(S1, S2, S3), T1))).isEqualTo(2);

    assertThat(collectors(S1)).isEqualTo(1);
    assertThat(collectors(S2)).isEqualTo(1);
    assertThat(collectors(S3)).isEqualTo(1);
  }

  /// 移出文献库只给真删掉的条目 −1；减到 0 保留行，再删一次不变。
  @Test
  void should_decrement_only_removed_items() {
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(U, S2, T0);
        });

    txRun(() -> library.deleteItems(U, List.of(S1, S3), T1));
    txRun(() -> library.deleteItems(U, List.of(S1), T2));

    assertThat(collectors(S1)).isZero();
    assertThat(collectors(S2)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection.subject_stats WHERE subject_id = ?",
                Long.class,
                S1.value()))
        .isEqualTo(1L);
  }

  /// 注销删除这个人的文献库条目时，他收藏过的对象各 −1，别人的不动。
  @Test
  void should_decrement_when_deleting_all_of_user() {
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(U, S2, T0);
          library.insertItem(V, S1, T0);
        });

    txRun(() -> library.deleteAllOf(U));

    assertThat(collectors(S1)).isEqualTo(1);
    assertThat(collectors(S2)).isZero();
  }

  /// 两个人同时反序地批量收藏、移出同一组对象：统计行按升序加锁，不会互等成死锁，计数始终等于条目数。
  @Test
  void should_not_deadlock_on_overlapping_batches() throws Exception {
    List<SubjectId> forward = List.of(S1, S2, S3);
    List<SubjectId> backward = List.of(S3, S2, S1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int round = 0; round < 20; round++) {
        CyclicBarrier start = new CyclicBarrier(2);
        CompletableFuture<Void> u =
            CompletableFuture.runAsync(() -> churn(start, U, forward, backward), pool);
        CompletableFuture<Void> v =
            CompletableFuture.runAsync(() -> churn(start, V, backward, forward), pool);
        u.get(30, TimeUnit.SECONDS);
        v.get(30, TimeUnit.SECONDS);
      }
    }

    for (SubjectId id : List.of(S1, S2, S3)) {
      assertThat(collectors(id)).isZero();
    }
  }

  /// 一个用户：同时起跑，批量收进、再按另一个顺序移出，各一个事务。
  ///
  /// @param start 起跑线
  /// @param user 用户
  /// @param insertOrder 收进的顺序
  /// @param deleteOrder 移出的顺序
  private void churn(
      CyclicBarrier start, long user, List<SubjectId> insertOrder, List<SubjectId> deleteOrder) {
    try {
      start.await(10, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    txRun(
        () -> {
          library.lockOwner(user);
          library.insertItems(user, insertOrder, T0);
        });
    txRun(
        () -> {
          library.lockOwner(user);
          library.deleteItems(user, deleteOrder, T1);
        });
  }

  /// 重算第一步：明细与统计任一边多、少、缺行都算差异；一致后为空。
  @Test
  void should_find_collector_count_drift() {
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(V, S1, T0);
          library.insertItem(U, S2, T0);
        });
    jdbcTemplate.update(
        "UPDATE collection.subject_stats SET collector_count = 1 WHERE subject_id = 11");
    jdbcTemplate.update("DELETE FROM collection.subject_stats WHERE subject_id = 12");
    jdbcTemplate.update(
        "INSERT INTO collection.subject_stats (subject_id, collector_count) VALUES (13, 4)");

    assertThat(library.findCollectorCountDrift()).containsExactlyInAnyOrder(S1, S2, S3);

    for (SubjectId id : List.of(S1, S2, S3)) {
      tx(() -> library.recountCollectorCount(id));
    }
    assertThat(library.findCollectorCountDrift()).isEmpty();
  }

  /// 重算第二步：改成明细数并带回前后值；已一致时为空；明细没了置 0、保留行；缺行补出来。
  @Test
  void should_recount_collector_count_to_detail() {
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(V, S1, T0);
          library.insertItem(U, S2, T0);
        });
    jdbcTemplate.update(
        "UPDATE collection.subject_stats SET collector_count = 5 WHERE subject_id = 11");
    jdbcTemplate.update("DELETE FROM collection.subject_stats WHERE subject_id = 12");
    jdbcTemplate.update(
        "INSERT INTO collection.subject_stats (subject_id, collector_count) VALUES (13, 4)");

    assertThat(tx(() -> library.recountCollectorCount(S1))).contains(StatsCorrection.of(5, 2));
    assertThat(tx(() -> library.recountCollectorCount(S1))).isEmpty();
    assertThat(tx(() -> library.recountCollectorCount(S2))).contains(StatsCorrection.of(0, 1));
    assertThat(tx(() -> library.recountCollectorCount(S3))).contains(StatsCorrection.of(4, 0));

    assertThat(collectors(S1)).isEqualTo(2);
    assertThat(collectors(S2)).isEqualTo(1);
    assertThat(collectors(S3)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection.subject_stats WHERE subject_id = 13", Long.class))
        .isEqualTo(1L);
  }

  /// 重算必须在事务里调用（锁要活到事务结束）。
  @Test
  void should_reject_recount_outside_transaction() {
    assertThatThrownBy(() -> library.recountCollectorCount(S1))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 重算与并发收藏交错：收藏的事务先加了 1 未提交，重算等它的行锁，提交后再数，计数不丢、也不误报差异。
  @Test
  void should_not_lose_concurrent_collect_while_recounting() throws Exception {
    txRun(() -> library.insertItem(V, S1, T0));
    CountDownLatch inserted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Void> collect =
          CompletableFuture.runAsync(
              () ->
                  txRun(
                      () -> {
                        library.lockOwner(U);
                        library.insertItem(U, S1, T1);
                        inserted.countDown();
                        await(release);
                      }),
              pool);
      assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
      CompletableFuture<Optional<StatsCorrection>> recount =
          CompletableFuture.supplyAsync(() -> tx(() -> library.recountCollectorCount(S1)), pool);
      awaitBlockedOnRowLock();
      assertThat(recount).isNotDone();

      release.countDown();
      collect.get(10, TimeUnit.SECONDS);

      assertThat(recount.get(10, TimeUnit.SECONDS)).isEmpty();
    }
    assertThat(collectors(S1)).isEqualTo(2);
  }

  /// 等闸门打开。
  ///
  /// @param latch 闸门
  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("等闸门超时");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  /// 等到有会话在等行锁或事务锁（不含 advisory lock）。
  private void awaitBlockedOnRowLock() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      Long waiting =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND wait_event IN ('transactionid', 'tuple')",
              Long.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("没有等到行锁上的等待");
  }

  /// 对象的收藏人数（没有统计行为 0）。
  ///
  /// @param subjectId 对象
  /// @return 收藏人数
  private int collectors(SubjectId subjectId) {
    return jdbcTemplate.queryForObject(
        "SELECT COALESCE(sum(collector_count), 0) FROM collection.subject_stats"
            + " WHERE subject_id = ?",
        Integer.class,
        subjectId.value());
  }

  /// 等到有会话在等 advisory lock。
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
