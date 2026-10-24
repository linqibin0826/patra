package dev.patra.interaction.app.collection;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// 测试用的「事务」：直接执行回调，并把边界记进事件列表，供 `InOrder` 式的断言判断调用是否在事务外。
///
/// @param events 事件列表
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
