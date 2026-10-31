package dev.patra.tool.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 去重端口集成测试：真实 PostgreSQL 上的插入冲突与事务语义。
///
/// 上下文用完即关：上下文里有订单队列的监听容器，不能和别的用例的上下文同时开着抢消息。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(
    initializers = {PostgreSQLContainerInitializer.class, RabbitMQContainerInitializer.class})
@DirtiesContext
@DisplayName("JdbcProcessedEventAdapter 集成测试")
class JdbcProcessedEventAdapterIT {

  @Autowired private ProcessedEventPort processedEvents;

  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void clean() {
    jdbcClient.sql("DELETE FROM processed_event").update();
  }

  @Test
  @DisplayName("同一消费方同一事件只有第一次返回 true；换消费方各记各的")
  void should_return_true_only_the_first_time() {
    UUID eventId = UUID.randomUUID();

    assertThat(mark("inbox", eventId)).isTrue();
    assertThat(mark("inbox", eventId)).isFalse();
    assertThat(mark("mail", eventId)).isTrue();
  }

  @Test
  @DisplayName("事务外调用直接报错，不自己开事务")
  void should_require_existing_transaction() {
    assertThatThrownBy(() -> processedEvents.markProcessed("inbox", UUID.randomUUID()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("业务事务回滚时去重记录一起回滚，重试不会被当成重复")
  void should_forget_mark_when_business_transaction_rolls_back() {
    UUID eventId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      processedEvents.markProcessed("inbox", eventId);
                      throw new IllegalStateException("业务写入失败");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(mark("inbox", eventId)).isTrue();
  }

  /// 在一个新事务里标记一次。
  ///
  /// @param consumer 消费方
  /// @param eventId 事件 ID
  /// @return 是否首次
  private boolean mark(String consumer, UUID eventId) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(status -> processedEvents.markProcessed(consumer, eventId)));
  }
}
