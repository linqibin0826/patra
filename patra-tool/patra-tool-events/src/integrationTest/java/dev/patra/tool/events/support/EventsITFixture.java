package dev.patra.tool.events.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.Container.ExecResult;

/// 集成测试的公共操作：清场、查登记表与效果表、停启 broker。
@Component
@RequiredArgsConstructor
public class EventsITFixture {

  private final JdbcClient jdbcClient;

  private final AmqpAdmin amqpAdmin;

  private final EventsITOrderListener listener;

  private final EventsITAfterCommitListener afterCommitListener;

  /// 清空四张表与三个队列，监听计数归零。
  public void reset() {
    jdbcClient.sql("DELETE FROM event_publication").update();
    jdbcClient.sql("DELETE FROM processed_event").update();
    jdbcClient.sql("DELETE FROM events_it_order").update();
    jdbcClient.sql("DELETE FROM events_it_effect").update();
    amqpAdmin.purgeQueue(EventsITSpringConfig.ORDERS_QUEUE, false);
    amqpAdmin.purgeQueue(EventsITSpringConfig.ORDERS_DLQ, false);
    amqpAdmin.purgeQueue(EventsITSpringConfig.FULL_QUEUE, false);
    listener.reset();
    afterCommitListener.reset();
  }

  /// 登记表里每一行的状态。
  ///
  /// @return 状态列表
  public List<String> publicationStatuses() {
    return jdbcClient.sql("SELECT status FROM event_publication").query(String.class).list();
  }

  /// 登记表里每一行的监听器 ID。
  ///
  /// @return 监听器 ID 列表
  public List<String> publicationListenerIds() {
    return jdbcClient.sql("SELECT listener_id FROM event_publication").query(String.class).list();
  }

  /// 订单表行数。
  ///
  /// @return 行数
  public int orderCount() {
    return count("events_it_order");
  }

  /// 效果表行数，即消费方业务真正执行的次数。
  ///
  /// @return 行数
  public int effectCount() {
    return count("events_it_effect");
  }

  /// 已处理事件表行数。
  ///
  /// @return 行数
  public int processedCount() {
    return count("processed_event");
  }

  /// 把唯一一条登记行改成卡在 `RESUBMITTED`、上次补投在 11 分钟前（超过 10 分钟算卡住）。
  public void markStuckInResubmission() {
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE event_publication
                   SET status = 'RESUBMITTED', last_resubmission_date = now() - interval '11 minutes'
                """)
            .update();
    assertThat(updated).isEqualTo(1);
  }

  /// 停掉 RabbitMQ 应用（容器不停，映射端口不变）。
  public void stopBroker() {
    rabbitmqctl("stop_app");
  }

  /// 启动 RabbitMQ 应用，等订单队列重新有消费者（监听容器恢复连接）。
  public void startBroker() {
    rabbitmqctl("start_app");
    await()
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              QueueInformation info = amqpAdmin.getQueueInfo(EventsITSpringConfig.ORDERS_QUEUE);
              assertThat(info).isNotNull();
              assertThat(info.getConsumerCount()).isPositive();
            });
  }

  /// 在容器里执行一条 `rabbitmqctl`。
  ///
  /// @param command 子命令
  private static void rabbitmqctl(String command) {
    try {
      ExecResult result =
          RabbitMQContainerInitializer.getRabbitMQContainer()
              .execInContainer("rabbitmqctl", command);
      assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    } catch (IOException ex) {
      throw new IllegalStateException("rabbitmqctl " + command + " 执行失败", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("rabbitmqctl " + command + " 被中断", ex);
    }
  }

  /// 表的行数。
  ///
  /// @param table 表名
  /// @return 行数
  private int count(String table) {
    return jdbcClient.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
  }
}
