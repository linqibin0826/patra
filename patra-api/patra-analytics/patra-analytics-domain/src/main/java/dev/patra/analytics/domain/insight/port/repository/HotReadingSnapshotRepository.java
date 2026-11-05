package dev.patra.analytics.domain.insight.port.repository;

import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import java.time.Instant;
import java.util.List;

/// 热读快照的写入：整表替换（要在调用方的事务里）。
public interface HotReadingSnapshotRepository {

  /// 用新快照替换旧快照，并记下计算时间。
  ///
  /// @param entries 新快照的全部行
  /// @param computedAt 计算时间
  void replace(List<HotReadingEntry> entries, Instant computedAt);
}
