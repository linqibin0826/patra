package dev.patra.interaction.infra.subject.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 登记表仓储：按身份锚 upsert、过期来源 ID 的清理、并发、分页、对账写回。
class SubjectRepositoryAdapterIT extends SubjectRegistryITSupport {

  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final Instant NOW = Instant.parse("2026-10-24T06:30:00Z");

  @Autowired private SubjectRepository repository;
  @Autowired private DataSource dataSource;

  @Test
  void should_insert_new_subject() {
    SubjectAggregate saved = upsert(active(100L, "1"));

    assertThat(saved.getState()).isEqualTo(SubjectState.ACTIVE);
    assertThat(saved.getSourceId()).isEqualTo(100L);
    assertThat(saved.getSnapshot()).isEqualTo(SubjectSnapshot.of("Title 1", "Journal", "2019-03"));
    assertThat(saved.getSnapshotAt()).isEqualTo(NOW);
    assertThat(repository.findBySourceId(PUB, 100L))
        .get()
        .extracting(SubjectAggregate::getSubjectId)
        .isEqualTo(saved.getSubjectId());
  }

  @Test
  void should_reuse_subject_for_same_pmid_with_new_source_id() {
    SubjectAggregate first = upsert(active(100L, "1"));

    SubjectAggregate second = upsert(active(200L, "1"));

    assertThat(second.getSubjectId()).isEqualTo(first.getSubjectId());
    assertThat(second.getSourceId()).isEqualTo(200L);
    assertThat(rowCount()).isEqualTo(1L);
    assertThat(repository.findBySourceId(PUB, 100L)).isEmpty();
  }

  @Test
  void should_clear_stale_holder_of_source_id() {
    SubjectAggregate stale = upsert(active(100L, "1"));

    SubjectAggregate fresh = upsert(active(100L, "2"));

    SubjectAggregate staleNow = byId(stale);
    assertThat(staleNow.getSourceId()).isNull();
    assertThat(staleNow.getState()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(fresh.getSubjectId()).isNotEqualTo(stale.getSubjectId());
    assertThat(repository.findBySourceId(PUB, 100L))
        .get()
        .extracting(SubjectAggregate::getSubjectId)
        .isEqualTo(fresh.getSubjectId());
  }

  /// 两个人同时首次收藏同一篇：第二个事务在第一个提交前就开始写，两者拿到同一个 `subject_id`、表里一行。
  ///
  /// 第一个事务写完先不提交，等第二个事务确实在数据库里阻塞后再提交，时序是确定的。
  @Test
  void should_return_same_subject_under_concurrent_upsert() throws Exception {
    CountDownLatch firstWritten = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      CompletableFuture<SubjectAggregate> first =
          CompletableFuture.supplyAsync(
              () ->
                  transactions.execute(
                      s -> {
                        SubjectAggregate saved =
                            repository.upsertResolved(PUB, active(100L, "1"), NOW);
                        firstWritten.countDown();
                        awaitQuietly(releaseFirst);
                        return saved;
                      }),
              pool);
      firstWritten.await();
      CompletableFuture<SubjectAggregate> second =
          CompletableFuture.supplyAsync(() -> upsert(active(100L, "1")), pool);
      awaitBlockedOnLock();
      releaseFirst.countDown();

      assertThat(second.get().getSubjectId()).isEqualTo(first.get().getSubjectId());
    }
    assertThat(rowCount()).isEqualTo(1L);
  }

  /// 同一身份锚的 upsert 先拿事务级 advisory lock 串行化：两个事务都通过 `ON CONFLICT` 的预检查后，
  /// 非判定索引 `uk_registry_source_id` 会直接报唯一冲突，而不是走 `DO UPDATE`。
  @Test
  void should_serialize_upserts_of_same_natural_key_with_advisory_lock() throws Exception {
    try (Connection holder = dataSource.getConnection()) {
      try (PreparedStatement lock =
          holder.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
        lock.setString(1, "subject.registry:1:pmid:1");
        lock.execute();
      }
      try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
        CompletableFuture<SubjectAggregate> upsert =
            CompletableFuture.supplyAsync(() -> upsert(active(100L, "1")), pool);
        awaitBlockedOnLock();
        assertThat(upsert).isNotDone();

        try (PreparedStatement unlock =
            holder.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
          unlock.setString(1, "subject.registry:1:pmid:1");
          unlock.execute();
        }
        assertThat(upsert.get().getSourceId()).isEqualTo(100L);
      }
    }
  }

  @Test
  void should_page_in_subject_id_order() {
    List<SubjectAggregate> saved =
        List.of("1", "2", "3", "4", "5").stream()
            .map(p -> upsert(active(Long.parseLong(p) * 100, p)))
            .sorted((a, b) -> Long.compare(a.getSubjectId().value(), b.getSubjectId().value()))
            .toList();

    List<SubjectAggregate> first = repository.findPage(PUB, 0L, 2);
    List<SubjectAggregate> second =
        repository.findPage(PUB, first.getLast().getSubjectId().value(), 2);
    List<SubjectAggregate> third =
        repository.findPage(PUB, second.getLast().getSubjectId().value(), 2);
    List<SubjectAggregate> rest =
        repository.findPage(PUB, third.getLast().getSubjectId().value(), 2);

    assertThat(first).containsExactly(saved.get(0), saved.get(1));
    assertThat(second).containsExactly(saved.get(2), saved.get(3));
    assertThat(third).containsExactly(saved.get(4));
    assertThat(rest).isEmpty();
  }

  @Test
  void should_save_reconciled_rows() {
    SubjectAggregate a = upsert(active(100L, "1"));
    SubjectAggregate b = upsert(active(200L, "2"));
    Instant later = NOW.plus(1, ChronoUnit.DAYS);
    SubjectAggregate aChanged =
        a.toBuilder()
            .sourceId(150L)
            .snapshot(SubjectSnapshot.of("New", "J2", "2020"))
            .snapshotAt(later)
            .build();
    SubjectAggregate bChanged = b.toBuilder().state(SubjectState.UNAVAILABLE).build();

    transactions.executeWithoutResult(s -> repository.saveReconciled(List.of(aChanged, bChanged)));

    assertThat(byId(a)).isEqualTo(aChanged);
    assertThat(byId(b)).isEqualTo(bChanged);
  }

  @Test
  void should_reassign_source_id_between_rows_in_one_batch() {
    SubjectAggregate a = upsert(active(100L, "1"));
    SubjectAggregate b = upsert(active(200L, "2"));
    SubjectAggregate aChanged = a.toBuilder().sourceId(200L).build();
    SubjectAggregate bChanged = b.toBuilder().sourceId(300L).build();

    transactions.executeWithoutResult(s -> repository.saveReconciled(List.of(aChanged, bChanged)));

    assertThat(byId(a).getSourceId()).isEqualTo(200L);
    assertThat(byId(b).getSourceId()).isEqualTo(300L);
  }

  @Test
  void should_bump_updated_at_through_trigger() {
    SubjectAggregate a = upsert(active(100L, "1"));
    OffsetDateTime before =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM subject.registry WHERE subject_id = ?",
            OffsetDateTime.class,
            a.getSubjectId().value());

    upsert(active(200L, "1"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT updated_at FROM subject.registry WHERE subject_id = ?",
                OffsetDateTime.class,
                a.getSubjectId().value()))
        .isAfter(before);
  }

  /// 在事务里 upsert。
  ///
  /// @param record 来源记录
  /// @return 写入后的聚合
  private SubjectAggregate upsert(SourceRecord record) {
    return transactions.execute(s -> repository.upsertResolved(PUB, record, NOW));
  }

  /// 按 `subjectId` 读回（经分页接口，游标取 ID - 1）。
  ///
  /// @param subject 聚合
  /// @return 当前库里的值
  private SubjectAggregate byId(SubjectAggregate subject) {
    return repository.findPage(PUB, subject.getSubjectId().value() - 1, 1).getFirst();
  }

  /// 等到有一个会话在数据库里等锁（第二个事务确实撞上了第一个），最多 10 秒。
  private void awaitBlockedOnLock() throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      Integer waiting =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND datname = current_database()",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("第二个事务没有阻塞在锁上");
  }

  /// 等发令枪。
  ///
  /// @param latch 发令枪
  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
