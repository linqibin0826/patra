package dev.patra.interaction.infra.collection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
