package dev.patra.interaction.domain.subject.model.aggregate;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/// 登记表的一行：一个被互动过的外部对象。互动数据只引用 `subjectId`；`sourceId` 是来源内部 ID 的缓存，
/// 每日对账按 `naturalKey` 刷新。
///
/// 不变量：`sourceId` 为空时 `state` 必须是 `UNAVAILABLE`。
@Getter
@ToString
@EqualsAndHashCode
public final class SubjectAggregate {

  private final SubjectId subjectId;
  private final SubjectType type;
  private final NaturalKey naturalKey;
  private final Long sourceId;
  private final SubjectState state;
  private final SubjectSnapshot snapshot;
  private final Instant snapshotAt;

  /// 创建并校验不变量。
  ///
  /// @param subjectId 本地 ID
  /// @param type 对象种类
  /// @param naturalKey 身份锚
  /// @param sourceId 来源内部 ID，可空
  /// @param state 状态
  /// @param snapshot 展示快照
  /// @param snapshotAt 快照取自来源的时间
  @Builder(toBuilder = true)
  private SubjectAggregate(
      SubjectId subjectId,
      SubjectType type,
      NaturalKey naturalKey,
      Long sourceId,
      SubjectState state,
      SubjectSnapshot snapshot,
      Instant snapshotAt) {
    this.subjectId = Objects.requireNonNull(subjectId, "subjectId 不能为 null");
    this.type = Objects.requireNonNull(type, "type 不能为 null");
    this.naturalKey = Objects.requireNonNull(naturalKey, "naturalKey 不能为 null");
    this.state = Objects.requireNonNull(state, "state 不能为 null");
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot 不能为 null");
    this.snapshotAt = Objects.requireNonNull(snapshotAt, "snapshotAt 不能为 null");
    if (sourceId == null && state == SubjectState.ACTIVE) {
      throw new IllegalStateException("没有来源 ID 的对象不能是 ACTIVE：" + subjectId);
    }
    this.sourceId = sourceId;
  }

  /// 是否正常。
  ///
  /// @return ACTIVE 时为 `true`
  public boolean isActive() {
    return state == SubjectState.ACTIVE;
  }

  /// 按来源的当前结果对账。
  ///
  /// | 来源结果 | `sourceId` | `state` | `snapshot` / `snapshotAt` |
  /// |---|---|---|---|
  /// | 存在，ACTIVE | 来源给的 ID | ACTIVE | 新值 / `now` |
  /// | 存在，DELETED | 来源给的 ID | UNAVAILABLE | 新值 / `now` |
  /// | 不存在 | 清空 | UNAVAILABLE | 保留 |
  ///
  /// @param found 来源结果；不存在时为空
  /// @param now 对账时间
  /// @return 对账后的新实例
  public SubjectAggregate reconcile(Optional<SourceRecord> found, Instant now) {
    if (found.isEmpty()) {
      return toBuilder().sourceId(null).state(SubjectState.UNAVAILABLE).build();
    }
    SourceRecord record = found.get();
    return toBuilder()
        .sourceId(record.sourceId())
        .state(
            record.state() == SourceState.ACTIVE ? SubjectState.ACTIVE : SubjectState.UNAVAILABLE)
        .snapshot(record.snapshot())
        .snapshotAt(now)
        .build();
  }
}
