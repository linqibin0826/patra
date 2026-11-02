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
import dev.patra.analytics.infra.ingest.topic.KafkaIngestLagGauge;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 入库端到端：events.raw / events.dlq → 批量消费 → ClickHouse，写成后才提交位点。
@SpringBootTest(
    properties = {
      "patra.analytics.ingest.enabled=true",
      "patra.analytics.kafka.raw-topic=it-ingest-flow.raw",
      "patra.analytics.kafka.dlq-topic=it-ingest-flow.dlq"
    })
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      ClickHouseContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class IngestFlowIT {

  private static final String RAW = "it-ingest-flow.raw";

  @Autowired private Client clickhouse;
  @Autowired private KafkaListenerEndpointRegistry listeners;
  @Autowired private KafkaIngestLagGauge lagGauge;
  @Autowired private MeterRegistry meters;

  @Test
  @Timeout(60)
  void records_are_ingested_and_committed() {
    String session = UUID.randomUUID().toString();
    send(RAW, IntStream.range(0, 20).mapToObj(i -> event(session)).toArray(String[]::new));

    assertThat(eventually(Duration.ofSeconds(20), () -> rows(session) == 20)).isTrue();
    assertThat(eventually(Duration.ofSeconds(10), () -> committed(RAW) == end(RAW))).isTrue();
  }

  @Test
  @Timeout(60)
  void garbage_record_goes_to_dlq_table() {
    String session = UUID.randomUUID().toString();
    String garbage = "not json " + session;

    send(RAW, event(session), garbage, event(session));

    assertThat(eventually(Duration.ofSeconds(20), () -> rows(session) == 2)).isTrue();
    assertThat(
            eventually(
                Duration.ofSeconds(10),
                () ->
                    number(
                            clickhouse,
                            "SELECT count() AS n FROM dlq_events WHERE reason_code = 'ingest_rejected'"
                                + " AND raw = '"
                                + garbage
                                + "'")
                        == 1))
        .isTrue();
  }

  @Test
  @Timeout(60)
  void lag_gauge_reports_backlog() {
    MessageListenerContainer raw = listeners.getListenerContainer("ingest-raw");
    raw.pause();
    try {
      assertThat(eventually(Duration.ofSeconds(10), raw::isContainerPaused)).isTrue();
      String session = UUID.randomUUID().toString();
      send(RAW, IntStream.range(0, 5).mapToObj(i -> event(session)).toArray(String[]::new));

      lagGauge.refresh();

      double lag =
          meters.get("analytics.ingest.lag").tag("topic", RAW).gauges().stream()
              .mapToDouble(Gauge::value)
              .sum();
      assertThat(lag).isGreaterThanOrEqualTo(5);
    } finally {
      raw.resume();
    }
  }

  private long rows(String session) {
    return number(
        clickhouse, "SELECT count() AS n FROM events WHERE session_id = '" + session + "'");
  }
}
