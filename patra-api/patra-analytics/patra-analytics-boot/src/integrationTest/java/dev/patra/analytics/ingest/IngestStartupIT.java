package dev.patra.analytics.ingest;

import static dev.patra.analytics.ingest.IngestITSupport.eventually;
import static dev.patra.analytics.ingest.IngestITSupport.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.clickhouse.ClickHouseContainer;

/// 服务启动时 ClickHouse 连不上：照常启动、采集照收（事件在 Kafka 里等）；ClickHouse 恢复后再建表、启动入库消费者。
///
/// 经可开关的 TCP 转发连 ClickHouse，启动前就关掉。不挂 `ClickHouseContainerInitializer`（它写的地址会绕过转发）。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.analytics.ingest.enabled=true",
      "patra.analytics.kafka.raw-topic=it-ingest-startup.raw",
      "patra.analytics.kafka.dlq-topic=it-ingest-startup.dlq"
    })
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class IngestStartupIT {

  private static final TcpRelay RELAY = TcpRelay.start();

  static {
    RELAY.disable();
  }

  @Autowired private RestTestClient restClient;
  @Autowired private KafkaListenerEndpointRegistry listeners;
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
  void collect_works_and_ingest_waits_until_clickhouse_is_reachable() throws IOException {
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(example("collect-batch--two.json"))
        .exchange()
        .expectStatus()
        .isAccepted();
    assertThat(listeners.getListenerContainer("ingest-raw").isRunning()).isFalse();

    RELAY.enable();

    assertThat(
            eventually(
                Duration.ofSeconds(45),
                () ->
                    listeners.getListenerContainer("ingest-raw").isRunning()
                        && listeners.getListenerContainer("ingest-dlq").isRunning()))
        .isTrue();
    assertThat(number(clickhouse, "SELECT count() AS n FROM schema_migrations")).isEqualTo(13);
  }

  private static String example(String file) throws IOException {
    try (InputStream in =
        IngestStartupIT.class
            .getClassLoader()
            .getResourceAsStream("analytics/examples/valid/" + file)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
