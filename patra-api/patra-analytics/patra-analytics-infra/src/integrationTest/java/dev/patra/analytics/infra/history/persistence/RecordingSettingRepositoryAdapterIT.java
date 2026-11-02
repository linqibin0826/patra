package dev.patra.analytics.infra.history.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 设置表：没有行为空；改开关是 upsert 且保留清空时刻；记清空时刻不动开关、没有行时插入开着的。
class RecordingSettingRepositoryAdapterIT extends HistoryPersistenceITSupport {

  private static final Instant T0 = Instant.parse("2026-11-02T08:00:00.123Z");

  @Autowired private RecordingSettingRepository settings;

  @Test
  void missing_setting_is_empty() {
    assertThat(settings.find(1L)).isEmpty();
  }

  @Test
  void save_enabled_upserts() {
    settings.saveEnabled(1L, false, T0);
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, false, null));

    settings.saveEnabled(1L, true, T0.plusSeconds(60));
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, null));
    assertThat(updatedAt(1L)).isEqualTo(T0.plusSeconds(60));
  }

  @Test
  void mark_cleared_inserts_enabled_row() {
    settings.markCleared(1L, T0);

    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, T0));
    assertThat(updatedAt(1L)).isEqualTo(T0);
  }

  @Test
  void mark_cleared_keeps_disabled_flag() {
    settings.saveEnabled(1L, false, T0);
    settings.markCleared(1L, T0.plusSeconds(60));
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, false, T0.plusSeconds(60)));

    settings.saveEnabled(1L, true, T0.plusSeconds(120));
    assertThat(settings.find(1L)).contains(RecordingSetting.of(1L, true, T0.plusSeconds(60)));
  }

  @Test
  void find_all_returns_only_existing() {
    settings.saveEnabled(1L, false, T0);
    settings.markCleared(3L, T0);

    assertThat(settings.findAll(List.of(1L, 2L, 3L)))
        .containsOnlyKeys(1L, 3L)
        .containsEntry(1L, RecordingSetting.of(1L, false, null))
        .containsEntry(3L, RecordingSetting.of(3L, true, T0));
    assertThat(settings.findAll(List.of())).isEmpty();
  }

  private Instant updatedAt(long userId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT updated_at FROM history.recording_setting WHERE user_id = ?",
            OffsetDateTime.class,
            userId)
        .toInstant();
  }
}
