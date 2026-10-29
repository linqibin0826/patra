package dev.patra.identity.app.account.usecase.email;

import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 清理过期够久的邮件令牌：按批删，每批一个短事务，直到一批不足为止。
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredEmailTokensHandler
    implements CommandHandler<PurgeExpiredEmailTokensCommand, Integer> {

  /// 一批最多删几行。
  static final int BATCH_SIZE = 1000;

  private final EmailTokenRepository tokens;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 一共删了几行
  @Override
  public Integer handle(PurgeExpiredEmailTokensCommand command) {
    Instant cutoff = clock.instant().minus(command.retention());
    int total = 0;
    int batch;
    do {
      batch =
          Objects.requireNonNull(
              transactions.execute(status -> tokens.deleteExpiredBefore(cutoff, BATCH_SIZE)));
      total += batch;
    } while (batch >= BATCH_SIZE);
    log.info("过期邮件令牌已清理: cutoff={}, deleted={}", cutoff, total);
    return total;
  }
}
