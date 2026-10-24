package dev.patra.interaction.domain.subject.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 对账规则：按 catalog 的结果刷新 `source_id`、`state`、`snapshot`。
class SubjectAggregateTest {

  private static final NaturalKey PMID_1 = NaturalKey.of(NaturalKeyScheme.PMID, "1");
  private static final SubjectSnapshot SNAPSHOT_A = SubjectSnapshot.of("A", "Journal A", "2019");
  private static final SubjectSnapshot SNAPSHOT_B = SubjectSnapshot.of("B", "Journal B", "2020-03");
  private static final Instant T0 = Instant.parse("2026-10-23T06:30:00Z");
  private static final Instant T1 = Instant.parse("2026-10-24T06:30:00Z");

  /// catalog 换了内部 ID：跟着换，`subject_id` 不变。
  @Test
  void should_follow_new_source_id_when_active() {
    SubjectAggregate result =
        active(100L).reconcile(Optional.of(record(200L, SourceState.ACTIVE, SNAPSHOT_B)), T1);

    assertThat(result.getSubjectId()).isEqualTo(SubjectId.of(7L));
    assertThat(result.getSourceId()).isEqualTo(200L);
    assertThat(result.getState()).isEqualTo(SubjectState.ACTIVE);
    assertThat(result.getSnapshot()).isEqualTo(SNAPSHOT_B);
    assertThat(result.getSnapshotAt()).isEqualTo(T1);
  }

  /// catalog 软删：失效，ID 保留，快照用新值。
  @Test
  void should_become_unavailable_when_soft_deleted() {
    SubjectAggregate result =
        active(100L).reconcile(Optional.of(record(100L, SourceState.DELETED, SNAPSHOT_B)), T1);

    assertThat(result.getState()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(result.getSourceId()).isEqualTo(100L);
    assertThat(result.getSnapshot()).isEqualTo(SNAPSHOT_B);
    assertThat(result.getSnapshotAt()).isEqualTo(T1);
  }

  /// catalog 整行消失：失效，ID 清空，快照与快照时间保留。
  @Test
  void should_clear_source_and_keep_snapshot_when_missing() {
    SubjectAggregate result = active(100L).reconcile(Optional.empty(), T1);

    assertThat(result.getState()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(result.getSourceId()).isNull();
    assertThat(result.getSnapshot()).isEqualTo(SNAPSHOT_A);
    assertThat(result.getSnapshotAt()).isEqualTo(T0);
  }

  /// 失效对象在 catalog 里重新出现：复活。
  @Test
  void should_revive_unavailable_subject() {
    SubjectAggregate unavailable =
        active(100L).toBuilder().sourceId(null).state(SubjectState.UNAVAILABLE).build();

    SubjectAggregate result =
        unavailable.reconcile(Optional.of(record(300L, SourceState.ACTIVE, SNAPSHOT_B)), T1);

    assertThat(result.getState()).isEqualTo(SubjectState.ACTIVE);
    assertThat(result.getSourceId()).isEqualTo(300L);
    assertThat(result.isActive()).isTrue();
  }

  /// 没有来源 ID 的对象不能是 ACTIVE（库里同一条 CHECK）。
  @Test
  void should_reject_active_without_source_id() {
    assertThatThrownBy(() -> active(100L).toBuilder().sourceId(null).build())
        .isInstanceOf(IllegalStateException.class);
  }

  /// 基线：ACTIVE、快照 A、快照时间 T0。
  ///
  /// @param sourceId 来源 ID
  /// @return 聚合
  private static SubjectAggregate active(long sourceId) {
    return SubjectAggregate.builder()
        .subjectId(SubjectId.of(7L))
        .type(SubjectType.PUBLICATION)
        .naturalKey(PMID_1)
        .sourceId(sourceId)
        .state(SubjectState.ACTIVE)
        .snapshot(SNAPSHOT_A)
        .snapshotAt(T0)
        .build();
  }

  /// PMID 1 的来源记录。
  ///
  /// @param sourceId 来源 ID
  /// @param state 来源状态
  /// @param snapshot 快照
  /// @return 来源记录
  private static SourceRecord record(long sourceId, SourceState state, SubjectSnapshot snapshot) {
    return SourceRecord.of(sourceId, PMID_1, state, snapshot);
  }
}
