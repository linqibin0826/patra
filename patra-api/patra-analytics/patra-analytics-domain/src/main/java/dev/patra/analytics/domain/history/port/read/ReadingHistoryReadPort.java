package dev.patra.analytics.domain.history.port.read;

import dev.patra.analytics.domain.history.model.vo.HistoryCursor;
import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import java.time.Instant;
import java.util.List;

/// 我的历史的翻页查询。
public interface ReadingHistoryReadPort {

  /// 按最近打开时间倒序、同一时间按文献 ID 倒序的一页。
  ///
  /// @param userId 用户 ID
  /// @param since 只要不早于它的
  /// @param after 上一页最后一行；第一页为 `null`
  /// @param limit 条数
  /// @return 这一页
  List<ReadingEntry> findPage(long userId, Instant since, HistoryCursor after, int limit);
}
