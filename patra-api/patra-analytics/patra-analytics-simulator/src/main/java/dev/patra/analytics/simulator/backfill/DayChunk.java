package dev.patra.analytics.simulator.backfill;

import dev.patra.analytics.simulator.model.SimSession;
import java.util.List;

/// 一天里的一块：一组固定的会话（已打爬虫标记）。
///
/// @param sessions 会话
public record DayChunk(List<SimSession> sessions) {

  /// 防御性拷贝。
  public DayChunk {
    sessions = List.copyOf(sessions);
  }
}
