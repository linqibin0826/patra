package dev.patra.tool.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import dev.patra.tool.events.support.EventsITFixture;
import dev.patra.tool.events.support.EventsITOrderPlaced;
import dev.patra.tool.events.support.EventsITOrderService;
import dev.patra.tool.events.support.EventsITSpringConfig;
import dev.patra.tool.events.support.EventsITUnreachableConfirmConfig;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/// broker 卡住时，等确认的外送不占数据库连接，业务照常拿得到连接。
///
/// 连接池只开 2 条、取连接最多等 3 秒；4 个外送同时等一个永远不回的确认（{@link EventsITUnreachableConfirmConfig}）。
/// 外送若在事务里等确认，4 个外送会占满连接池，业务写库取不到连接（临时加一个在 `REQUIRES_NEW` 事务里拿着连接
/// 睡眠的异步提交后监听器，本测试即报 `Connection is not available`）。Modulith 的外送器方法是
/// `@ApplicationModuleListener(propagation = SUPPORTS)`，没有物理事务；进入与完成的登记是各自的短事务，不跨等待。
/// 路由到一直拒收的队列，消费方不会因收到消息而借连接。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(
    initializers = {PostgreSQLContainerInitializer.class, RabbitMQContainerInitializer.class})
@TestPropertySource(
    properties = {
      "patra.events.publisher.confirm-timeout=8s",
      "spring.datasource.hikari.maximum-pool-size=2",
      "spring.datasource.hikari.connection-timeout=3000"
    })
@Import(EventsITUnreachableConfirmConfig.class)
@DirtiesContext
@DisplayName("等确认时不占数据库连接")
class ExternalizationConnectionIT {

  private static final int WAITING_SENDS = 4;

  @Autowired private EventsITOrderService orders;

  @Autowired private EventsITFixture fixture;

  @Autowired private DataSource dataSource;

  @Autowired private EventPublicationResubmitter resubmitter;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void reset() {
    fixture.reset();
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  @DisplayName("首次外送 4 个、补投 5 个外送等确认，期间活动连接数一直是 0，业务写库照常成功")
  void should_not_hold_database_connections_while_waiting_for_confirms() {
    // 与服务一致：JPA 在类路径上，事务管理器是 JpaTransactionManager
    assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    for (int i = 1; i <= WAITING_SENDS; i++) {
      orders.place(EventsITOrderPlaced.routedTo("W-" + i, EventsITSpringConfig.ORDER_REJECTED));
    }
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () ->
                assertThat(fixture.publicationStatuses())
                    .hasSize(WAITING_SENDS)
                    .containsOnly("PROCESSING"));

    HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
    await()
        .during(Duration.ofMillis(800))
        .atMost(Duration.ofSeconds(2))
        .until(() -> pool.getActiveConnections() == 0);
    orders.place(EventsITOrderPlaced.routedTo("W-5", EventsITSpringConfig.ORDER_REJECTED));
    assertThat(fixture.orderCount()).isEqualTo(WAITING_SENDS + 1);

    awaitAllFailed();

    // 补投走同一个外送器：再等一轮确认，同样不占连接
    assertThat(resubmitter.resubmit()).isEqualTo(WAITING_SENDS + 1);
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsOnly("PROCESSING"));
    await()
        .during(Duration.ofMillis(800))
        .atMost(Duration.ofSeconds(2))
        .until(() -> pool.getActiveConnections() == 0);
    awaitAllFailed();
  }

  /// 等所有登记行都因确认超时变回 `FAILED`。
  private void awaitAllFailed() {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(fixture.publicationStatuses())
                    .hasSize(WAITING_SENDS + 1)
                    .containsOnly("FAILED"));
  }
}
