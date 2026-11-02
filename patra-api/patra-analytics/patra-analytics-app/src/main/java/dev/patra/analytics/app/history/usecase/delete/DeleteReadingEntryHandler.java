package dev.patra.analytics.app.history.usecase.delete;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 删一条阅读历史（spec §6.2）：一个事务里删掉这一行、写删除记录（`deleted_at` = 现在）。这一篇本来不在也照样写，
/// 重复删除按成功处理。
@Component
@RequiredArgsConstructor
public class DeleteReadingEntryHandler implements CommandHandler<DeleteReadingEntryCommand, Void> {

  private final ReadingHistoryRepository history;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 删一条。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(DeleteReadingEntryCommand command) {
    long userId = currentUser.require().userId();
    Instant now = clock.instant();
    transactions.executeWithoutResult(
        status -> history.deleteOne(userId, command.articleId(), now));
    return null;
  }
}
