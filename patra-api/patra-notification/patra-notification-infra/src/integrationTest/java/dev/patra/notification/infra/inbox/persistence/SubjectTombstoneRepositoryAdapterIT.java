package dev.patra.notification.infra.inbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 经验墓碑：幂等记下、查得到、保留期清理。
class SubjectTombstoneRepositoryAdapterIT extends InboxPersistenceITSupport {

  @Autowired private SubjectTombstoneRepositoryAdapter tombstones;

  /// 重复记下保留第一次的删除时刻；别的对象查不到。
  @Test
  void should_save_idempotently_and_report_exists() {
    tombstones.save(40L, NOW);
    tombstones.save(40L, NOW.plusSeconds(60));

    assertThat(tombstones.exists(40L)).isTrue();
    assertThat(tombstones.exists(41L)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted_at = ?::timestamptz FROM inbox.subject_tombstone",
                Boolean.class,
                NOW.toString()))
        .isTrue();
  }

  /// 只删删除时刻早于截止的。
  @Test
  void should_purge_before() {
    tombstones.save(40L, Instant.parse("2026-04-01T00:00:00Z"));
    tombstones.save(41L, NOW);

    assertThat(tombstones.purgeBefore(Instant.parse("2026-05-31T00:00:00Z"))).isEqualTo(1);
    assertThat(tombstones.exists(41L)).isTrue();
  }
}
