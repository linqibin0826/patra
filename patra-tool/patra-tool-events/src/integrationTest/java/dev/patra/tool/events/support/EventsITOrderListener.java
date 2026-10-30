package dev.patra.tool.events.support;

import dev.patra.tool.core.event.ProcessedEventPort;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/// 测试用的消费方：在业务事务里先去重再写处理效果，相当于服务里消费事件的 Handler。
@Component
@RequiredArgsConstructor
public class EventsITOrderListener {

  private final TransactionTemplate transactionTemplate;

  private final ProcessedEventPort processedEvents;

  private final JdbcClient jdbcClient;

  private final AtomicInteger invocations = new AtomicInteger();

  private final List<String> messageIds = new CopyOnWriteArrayList<>();

  /// 处理一条「下了一单」。
  ///
  /// @param message 集成事件
  /// @param messageId AMQP `messageId`
  @RabbitListener(queues = EventsITSpringConfig.ORDERS_QUEUE)
  public void on(
      EventsITOrderPlacedMessage message,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    invocations.incrementAndGet();
    messageIds.add(String.valueOf(messageId));
    transactionTemplate.executeWithoutResult(
        status -> {
          if (!processedEvents.markProcessed(
              EventsITSpringConfig.ORDERS_QUEUE, message.eventId())) {
            return;
          }
          if (message.poison()) {
            throw new IllegalStateException("处理失败：" + message.orderNo());
          }
          jdbcClient
              .sql("INSERT INTO events_it_effect (event_id, order_no) VALUES (?, ?)")
              .param(message.eventId())
              .param(message.orderNo())
              .update();
        });
  }

  /// 监听方法被调用的次数（含重试与重复投递）。
  ///
  /// @return 次数
  public int invocations() {
    return invocations.get();
  }

  /// 每次调用收到的 AMQP `messageId`。
  ///
  /// @return 按调用顺序
  public List<String> messageIds() {
    return List.copyOf(messageIds);
  }

  /// 用例开始前清零。
  public void reset() {
    invocations.set(0);
    messageIds.clear();
  }
}
