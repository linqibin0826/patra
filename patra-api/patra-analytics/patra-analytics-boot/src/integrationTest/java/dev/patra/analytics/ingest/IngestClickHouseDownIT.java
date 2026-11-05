package dev.patra.analytics.ingest;

import static dev.patra.analytics.ingest.IngestITSupport.committed;
import static dev.patra.analytics.ingest.IngestITSupport.end;
import static dev.patra.analytics.ingest.IngestITSupport.event;
import static dev.patra.analytics.ingest.IngestITSupport.eventually;
import static dev.patra.analytics.ingest.IngestITSupport.number;
import static dev.patra.analytics.ingest.IngestITSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;

/// ClickHouse 宕机期间消费停在原地、不提交位点；恢复后同一批补齐（入库设计 §4.4）。
///
/// 服务经一个可开关的 TCP 转发连 ClickHouse：关掉转发就是「连不上」。不挂 `ClickHouseContainerInitializer`：
/// 它写进环境的地址优先级高于 `@DynamicPropertySource`，会绕过转发。
@SpringBootTest(
    properties = {
      "patra.analytics.ingest.enabled=true",
      "patra.analytics.kafka.raw-topic=it-ingest-down.raw",
      "patra.analytics.kafka.dlq-topic=it-ingest-down.dlq"
    })
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class IngestClickHouseDownIT {

  private static final String RAW = "it-ingest-down.raw";

  private static final TcpRelay RELAY = TcpRelay.start();

  @Autowired private Client clickhouse;

  @DynamicPropertySource
  static void clickhouseViaRelay(DynamicPropertyRegistry registry) {
    ClickHouseContainer container = ClickHouseContainerInitializer.getClickHouseContainer();
    registry.add("patra.clickhouse.url", () -> "http://127.0.0.1:" + RELAY.port());
    registry.add("patra.clickhouse.username", container::getUsername);
    registry.add("patra.clickhouse.password", container::getPassword);
  }

  @AfterAll
  static void stopRelay() {
    RELAY.close();
  }

  @Test
  @Timeout(90)
  void clickhouse_down_keeps_offsets_until_recovered() throws Exception {
    String session = UUID.randomUUID().toString();
    long before = committed(RAW);
    RELAY.disable();

    send(RAW, event(session), event(session), event(session));
    Thread.sleep(5000);

    assertThat(committed(RAW)).isEqualTo(before);
    RELAY.enable();
    assertThat(eventually(Duration.ofSeconds(30), () -> rows(session) == 3)).isTrue();
    assertThat(eventually(Duration.ofSeconds(10), () -> committed(RAW) == end(RAW))).isTrue();
  }

  private long rows(String session) {
    return number(
        clickhouse, "SELECT count() AS n FROM events WHERE session_id = '" + session + "'");
  }
}
