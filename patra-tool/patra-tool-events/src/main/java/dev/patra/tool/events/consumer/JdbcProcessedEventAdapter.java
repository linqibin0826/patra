package dev.patra.tool.events.consumer;

import dev.patra.tool.core.event.ProcessedEventPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 去重端口的 JDBC 实现：往 `processed_event` 插一行，主键（消费方, 事件 ID）冲突就说明处理过。
///
/// 用 `ON CONFLICT DO NOTHING` 而不是捕获主键冲突异常：PostgreSQL 里语句失败会作废整个事务，
/// 业务写入就做不下去了。
@RequiredArgsConstructor
public class JdbcProcessedEventAdapter implements ProcessedEventPort {

  private static final String INSERT =
      """
      INSERT INTO processed_event (consumer, event_id, processed_at)
      VALUES (?, ?, ?)
      ON CONFLICT (consumer, event_id) DO NOTHING
      """;

  private final JdbcClient jdbcClient;

  private final Clock clock;

  /// 记下处理过；调用方没有事务时报错，不自己开事务。
  ///
  /// @param consumer 消费方，填队列名
  /// @param eventId 事件 ID
  /// @return 插入了新行返回 `true`
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean markProcessed(String consumer, UUID eventId) {
    return jdbcClient
            .sql(INSERT)
            .param(consumer)
            .param(eventId)
            .param(Timestamp.from(clock.instant()))
            .update()
        == 1;
  }
}
