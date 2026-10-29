package dev.patra.identity.app.session.usecase.purge;

import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 清理结束或过期满保留期的登录记录：按「结束或过期的时刻」（`COALESCE(ended_at, expires_at)`）删，
/// 按批删、每批一个短事务，直到一批不足为止（PAP-99 设计第 14 节）。还活着的会话的绝对过期在未来，永远不会被删。
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeRetiredLoginRecordsHandler
    implements CommandHandler<PurgeRetiredLoginRecordsCommand, Integer> {

  /// 一批最多删几行。
  static final int BATCH_SIZE = 1000;

  private final UserLoginRecordRepository records;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 一共删了几行
  @Override
  public Integer handle(PurgeRetiredLoginRecordsCommand command) {
    Instant cutoff = clock.instant().minus(command.retention());
    int total = 0;
    int batch;
    do {
      batch =
          Objects.requireNonNull(
              transactions.execute(status -> records.deleteRetiredBefore(cutoff, BATCH_SIZE)));
      total += batch;
    } while (batch >= BATCH_SIZE);
    log.info("退役的登录记录已清理: cutoff={}, deleted={}", cutoff, total);
    return total;
  }
}
