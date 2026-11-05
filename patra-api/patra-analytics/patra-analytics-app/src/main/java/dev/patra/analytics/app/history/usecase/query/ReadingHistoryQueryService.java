package dev.patra.analytics.app.history.usecase.query;

import dev.patra.analytics.domain.history.model.vo.HistoryCursor;
import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import dev.patra.analytics.domain.history.model.vo.RecordingSetting;
import dev.patra.analytics.domain.history.policy.HistoryRetention;
import dev.patra.analytics.domain.history.port.read.ReadingHistoryReadPort;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 当前用户的阅读历史与开关（spec §6.1、§6.3）。不开事务。
@Service
@RequiredArgsConstructor
public class ReadingHistoryQueryService {

  private final ReadingHistoryReadPort reads;
  private final RecordingSettingRepository settings;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  /// 一页历史，只含 12 个月以内的；多取一行判断还有没有下一页。
  ///
  /// @param cursor 上一页给的游标；第一页为 `null`
  /// @return 这一页与开关状态
  public ReadingHistoryPage page(String cursor) {
    long userId = currentUser.require().userId();
    HistoryCursor after = cursor == null ? null : HistoryCursor.decode(cursor);
    List<ReadingEntry> rows =
        reads.findPage(
            userId,
            HistoryRetention.historyCutoff(clock.instant()),
            after,
            HistoryRetention.PAGE_SIZE + 1);
    boolean more = rows.size() > HistoryRetention.PAGE_SIZE;
    List<ReadingEntry> items = more ? rows.subList(0, HistoryRetention.PAGE_SIZE) : rows;
    return ReadingHistoryPage.of(
        enabled(userId), items, more ? items.getLast().cursor().encode() : null);
  }

  /// 阅读记录是否开着；没设置过为开。
  ///
  /// @return 开着为 `true`
  public boolean recordingEnabled() {
    return enabled(currentUser.require().userId());
  }

  /// 读开关。
  ///
  /// @param userId 用户 ID
  /// @return 开着为 `true`
  private boolean enabled(long userId) {
    return settings.find(userId).map(RecordingSetting::enabled).orElse(true);
  }
}
