package dev.patra.tool.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

/// 已处理事件清理的集成测试：真实 PostgreSQL 上按保留期（默认 30 天）分批删除。
///
/// 上下文用完即关：上下文里有订单队列的监听容器，不能和别的用例的上下文同时开着抢消息。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(
    initializers = {PostgreSQLContainerInitializer.class, RabbitMQContainerInitializer.class})
@DirtiesContext
@DisplayName("已处理事件清理集成测试")
class ProcessedEventPurgerIT {

  @Autowired private ProcessedEventPurger purger;

  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void clean() {
    jdbcClient.sql("DELETE FROM processed_event").update();
  }

  @Test
  @DisplayName("删掉超过保留期的行，保留期内的留着；超过一批的分几批删完")
  void should_delete_rows_older_than_retention_in_batches() {
    insert(2500, "40 days");
    insert(3, "29 days");

    assertThat(purger.purge()).isEqualTo(2500);

    assertThat(count()).isEqualTo(3);
  }

  @Test
  @DisplayName("没有过期的行时删除 0 条")
  void should_delete_nothing_when_all_rows_are_recent() {
    insert(2, "1 day");

    assertThat(purger.purge()).isZero();

    assertThat(count()).isEqualTo(2);
  }

  /// 插入若干条处理于指定时长之前的行。
  ///
  /// @param rows 行数
  /// @param age 多久之前处理的，PostgreSQL interval 写法
  private void insert(int rows, String age) {
    jdbcClient
        .sql(
            """
            INSERT INTO processed_event (consumer, event_id, processed_at)
            SELECT 'events-it.orders', gen_random_uuid(), now() - CAST(? AS interval)
              FROM generate_series(1, ?)
            """)
        .param(age)
        .param(rows)
        .update();
  }

  /// 表的行数。
  ///
  /// @return 行数
  private int count() {
    return jdbcClient.sql("SELECT count(*) FROM processed_event").query(Integer.class).single();
  }
}
