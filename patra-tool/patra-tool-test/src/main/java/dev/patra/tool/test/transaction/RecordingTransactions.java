package dev.patra.tool.test.transaction;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// 测试用的「事务」：直接执行回调，并把边界记进事件列表，供断言判断调用是否落在事务里外。
///
/// 被测对象的其他协作者（Mockito `doAnswer`）往同一个列表里记事件，就能断言先后顺序，
/// 例如「远程调用在 `tx-begin` 之前」。
///
/// `events` 有意不做防御性拷贝：它是测试与协作者共享的可变事件槽，拷贝之后就记不到同一个列表里了。
///
/// @param events 事件列表（由测试持有，本类只往里追加）
public record RecordingTransactions(List<String> events) implements TransactionOperations {

  /// 事务开始的事件。
  public static final String BEGIN = "tx-begin";

  /// 事务结束的事件。
  public static final String END = "tx-end";

  /// 在「事务」里执行回调并记下边界。
  ///
  /// @param action 回调
  /// @param <T> 返回类型
  /// @return 回调结果
  @Override
  public <T> @Nullable T execute(TransactionCallback<T> action) {
    events.add(BEGIN);
    TransactionStatus status = new SimpleTransactionStatus();
    T result = action.doInTransaction(status);
    events.add(END);
    return result;
  }

  /// 不返回结果的版本。
  ///
  /// @param action 回调
  @Override
  public void executeWithoutResult(Consumer<TransactionStatus> action) {
    execute(
        status -> {
          action.accept(status);
          return null;
        });
  }
}
