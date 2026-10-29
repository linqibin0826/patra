package dev.patra.identity.app.session.usecase.purge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// PurgeRetiredLoginRecordsHandler 单元测试：按批删到不足一批为止，每批一个事务（PAP-99 设计第 14 节）。
@DisplayName("PurgeRetiredLoginRecordsHandler 单元测试")
class PurgeRetiredLoginRecordsHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final Duration RETENTION = Duration.ofDays(190);

  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final AtomicInteger transactionsStarted = new AtomicInteger();
  private final PurgeRetiredLoginRecordsHandler handler =
      new PurgeRetiredLoginRecordsHandler(
          records, counting(transactionsStarted), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("一批 1000：1000、1000、7 → 删 2007 行，三批三个事务，截止时间 = 现在 − 保留期")
  void should_delete_in_batches_until_a_short_batch() {
    when(records.deleteRetiredBefore(NOW.minus(RETENTION), 1000)).thenReturn(1000, 1000, 7);

    int deleted = handler.handle(PurgeRetiredLoginRecordsCommand.of(RETENTION));

    assertThat(deleted).isEqualTo(2007);
    verify(records, times(3)).deleteRetiredBefore(NOW.minus(RETENTION), 1000);
    assertThat(transactionsStarted).hasValue(3);
  }

  @Test
  @DisplayName("没有满保留期的：一批就停")
  void should_stop_immediately_when_nothing_expired() {
    when(records.deleteRetiredBefore(NOW.minus(RETENTION), 1000)).thenReturn(0);

    assertThat(handler.handle(PurgeRetiredLoginRecordsCommand.of(RETENTION))).isZero();
    verify(records, times(1)).deleteRetiredBefore(NOW.minus(RETENTION), 1000);
    assertThat(transactionsStarted).hasValue(1);
  }

  /// 数一数开了几个事务。
  ///
  /// @param counter 计数器
  /// @return 事务操作
  private static TransactionOperations counting(AtomicInteger counter) {
    return new TransactionOperations() {
      /// 计数后执行回调。
      ///
      /// @param action 回调
      /// @param <T> 结果类型
      /// @return 回调结果
      @Override
      public <T> T execute(TransactionCallback<T> action) throws TransactionException {
        counter.incrementAndGet();
        return action.doInTransaction(new SimpleTransactionStatus());
      }
    };
  }
}
