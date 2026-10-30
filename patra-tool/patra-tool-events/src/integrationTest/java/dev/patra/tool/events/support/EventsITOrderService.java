package dev.patra.tool.events.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/// 测试用的发布方：在一个事务里写订单并发布领域事件，相当于服务里的命令 Handler。
@Component
@RequiredArgsConstructor
public class EventsITOrderService {

  private final TransactionTemplate transactionTemplate;

  private final JdbcClient jdbcClient;

  private final ApplicationEventPublisher events;

  /// 下单并发布事件。
  ///
  /// @param event 领域事件
  public void place(EventsITOrderPlaced event) {
    transactionTemplate.executeWithoutResult(
        status -> {
          insertOrder(event);
          events.publishEvent(event);
        });
  }

  /// 下单、发布事件后抛异常，整个事务回滚。
  ///
  /// @param event 领域事件
  /// @throws IllegalStateException 总是抛出
  public void placeAndFail(EventsITOrderPlaced event) {
    transactionTemplate.executeWithoutResult(
        status -> {
          insertOrder(event);
          events.publishEvent(event);
          throw new IllegalStateException("下单后业务校验失败，回滚");
        });
  }

  /// 写订单行。
  ///
  /// @param event 领域事件
  private void insertOrder(EventsITOrderPlaced event) {
    jdbcClient
        .sql("INSERT INTO events_it_order (order_no) VALUES (?)")
        .param(event.orderNo())
        .update();
  }
}
