package dev.patra.analytics.app.history.usecase.clear;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.analytics.domain.history.port.repository.RecordingSettingRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 清空阅读历史（spec §6.2）：一个事务里删掉全部历史与删除记录、记下清空时刻；之后晚到的、发生在清空之前的事件不再写入。
/// 开关不变；没有记录也算成功。
@Component
@RequiredArgsConstructor
public class ClearReadingHistoryHandler
    implements CommandHandler<ClearReadingHistoryCommand, Void> {

  private final ReadingHistoryRepository history;
  private final RecordingSettingRepository settings;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 清空。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(ClearReadingHistoryCommand command) {
    long userId = currentUser.require().userId();
    Instant now = clock.instant();
    transactions.executeWithoutResult(
        status -> {
          history.deleteAllOf(userId);
          settings.markCleared(userId, now);
        });
    return null;
  }
}
