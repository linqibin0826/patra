package dev.patra.tool.events.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/// 测试用的普通提交后监听器，相当于服务里「提交后可丢的副作用」（如通知服务的实时推送）。
///
/// 订单号以 {@link #FAILING_PREFIX} 开头时抛异常：它若进了事件发布登记表，就会留下一条 `FAILED` 的登记行。
@Component
public class EventsITAfterCommitListener {

  /// 订单号以它开头时监听器失败。
  public static final String FAILING_PREFIX = "AC-";

  private final AtomicInteger invocations = new AtomicInteger();

  /// 提交后处理「下了一单」。
  ///
  /// @param event 领域事件
  @TransactionalEventListener
  public void on(EventsITOrderPlaced event) {
    invocations.incrementAndGet();
    if (event.orderNo().startsWith(FAILING_PREFIX)) {
      throw new IllegalStateException("提交后的副作用失败：" + event.orderNo());
    }
  }

  /// 监听方法被调用的次数。
  ///
  /// @return 次数
  public int invocations() {
    return invocations.get();
  }

  /// 用例开始前清零。
  public void reset() {
    invocations.set(0);
  }
}
