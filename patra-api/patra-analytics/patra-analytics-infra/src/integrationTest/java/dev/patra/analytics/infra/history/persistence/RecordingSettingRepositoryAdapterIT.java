package dev.patra.analytics.infra.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 设置表：没有行为空；改开关是 upsert 且保留清空时刻；记清空时刻不动开关、没有行时插入开着的；操作人记成用户本人。
class RecordingSettingRepositoryAdapterIT extends HistoryPersistenceITSupport {

  private static final Instant T0 = Instant.parse("2026-11-02T08:00:00.123Z");

  @Autowired private RecordingSettingRepository settings;

  @Test
  void missing_setting_is_empty() {
    assertThat(settings.find(1L)).isEmpty();
  }

  @Test
  void save_enabled_upserts() {
    settings.saveEnabled(1L, false);
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, false, null));

    settings.saveEnabled(1L, true);
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, null));
  }

  @Test
  void mark_cleared_inserts_enabled_row() {
    settings.markCleared(1L, T0);

    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, T0));
  }

  @Test
  void mark_cleared_keeps_disabled_flag() {
    settings.saveEnabled(1L, false);
    settings.markCleared(1L, T0.plusSeconds(60));
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, false, T0.plusSeconds(60)));

    settings.saveEnabled(1L, true);
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, T0.plusSeconds(60)));
  }

  @Test
  void find_all_returns_only_existing() {
    settings.saveEnabled(1L, false);
    settings.markCleared(3L, T0);

    assertThat(settings.findAll(List.of(1L, 2L, 3L)))
        .containsOnlyKeys(1L, 3L)
        .containsEntry(1L, RecordingSetting.of(1L, false, null))
        .containsEntry(3L, RecordingSetting.of(3L, true, T0));
    assertThat(settings.findAll(List.of())).isEmpty();
  }

  @Test
  void should_record_user_as_actor() {
    settings.saveEnabled(1L, false);
    settings.markCleared(3L, T0);

    assertThat(actors(1L)).containsExactly(1L, 1L);
    assertThat(actors(3L)).containsExactly(3L, 3L);
  }

  /// 本人改自己的设置：操作人就是这个用户。
  private List<Long> actors(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT created_by, updated_by FROM anl_recording_setting WHERE user_id = ?",
        (rs, row) -> List.of(rs.getLong("created_by"), rs.getLong("updated_by")),
        userId);
  }
}
