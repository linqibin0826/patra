package dev.patra.tool.events.consumer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

/// 删掉 `processed_event` 里超过保留期的行。
///
/// 去重表只增不减，一直长下去；行处理完、超过保留期，同一事件就不会再来（保留期怎么定见 spec 4.14），可以删。
/// 分批删：每批一条语句、各自提交（前提是调用方不带事务、连接池开着自动提交，Hikari 默认如此；关掉自动提交时
/// 连接归还会把删除回滚），不开长事务，也不一次锁住大量行；删到某批为 0 才停，有并发清理时也不会提前收工。
@Slf4j
@RequiredArgsConstructor
public class ProcessedEventPurger {

  /// 每批最多删的行数。
  private static final int BATCH_SIZE = 1000;

  private static final String DELETE_BATCH =
      """
      DELETE FROM processed_event
       WHERE ctid IN (SELECT ctid FROM processed_event WHERE processed_at < ? LIMIT ?)
      """;

  private final JdbcClient jdbcClient;

  private final Clock clock;

  private final Duration retention;

  /// 执行一次清理。
  ///
  /// @return 删掉的行数
  public int purge() {
    Timestamp cutoff = Timestamp.from(clock.instant().minus(retention));
    int total = 0;
    int deleted;
    do {
      deleted = jdbcClient.sql(DELETE_BATCH).param(cutoff).param(BATCH_SIZE).update();
      total += deleted;
    } while (deleted > 0);
    if (total > 0) {
      log.info("删除 {} 条超过保留期 {} 的已处理事件", total, retention);
    }
    return total;
  }
}
