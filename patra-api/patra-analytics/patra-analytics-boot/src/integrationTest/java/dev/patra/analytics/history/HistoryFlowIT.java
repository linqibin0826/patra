package dev.patra.analytics.history;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.collect.CollectExamples;
import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/// 阅读历史端到端（spec §9）：经真实采集端点写历史、真实验签、真实 PG 与 Kafka。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.analytics.kafka.raw-topic=it-history.raw",
      "patra.analytics.kafka.dlq-topic=it-history.dlq"
    })
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      ClickHouseContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class HistoryFlowIT {

  private static final long ME = 5001L;
  private static final String ARTICLE = "39871245";
  private static final String HISTORY = "/me/reading-history";
  private static final String RECORDING = HISTORY + "/recording";
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void truncateHistory() {
    jdbcTemplate.update(
        "TRUNCATE history.reading_history, history.recording_setting, history.deleted_entry");
  }

  @Test
  void anonymous_requests_are_401() {
    assertThat(status(call(HttpMethod.GET, null, HISTORY, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.DELETE, null, HISTORY, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.DELETE, null, HISTORY + "/" + ARTICLE, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.GET, null, RECORDING, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.PUT, null, RECORDING, "{\"enabled\":false}"))).isEqualTo(401);
  }

  @Test
  void collected_view_appears_in_history() {
    collect(viewed(null));

    JsonNode page = json(call(HttpMethod.GET, ME, HISTORY, null));

    assertThat(page.get("recordingEnabled").asBoolean()).isTrue();
    assertThat(page.get("items")).hasSize(1);
    JsonNode item = page.get("items").get(0);
    assertThat(item.get("articleId").isString()).isTrue();
    assertThat(item.get("articleId").asString()).isEqualTo(ARTICLE);
    assertThat(item.get("fulltextRead").asBoolean()).isFalse();
    assertThat(item.get("lastReadAt").asString()).endsWith("Z");
    assertThat(page.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void disabled_recording_skips_history_but_not_kafka() {
    EntityExchangeResult<String> saved = call(HttpMethod.PUT, ME, RECORDING, "{\"enabled\":false}");
    assertThat(status(saved)).isEqualTo(200);
    assertThat(json(saved).get("enabled").asBoolean()).isFalse();
    assertThat(json(call(HttpMethod.GET, ME, RECORDING, null)).get("enabled").asBoolean())
        .isFalse();

    ObjectNode event = viewed(null);
    collect(event);

    JsonNode page = json(call(HttpMethod.GET, ME, HISTORY, null));
    assertThat(page.get("recordingEnabled").asBoolean()).isFalse();
    assertThat(page.get("items")).isEmpty();
    assertThat(readRaw(event.get("event_id").asString())).hasSize(1);
  }

  @Test
  void late_event_after_clear_is_dropped() {
    collect(viewed(null));
    assertThat(status(call(HttpMethod.DELETE, ME, HISTORY, null))).isEqualTo(204);
    assertThat(json(call(HttpMethod.GET, ME, HISTORY, null)).get("items")).isEmpty();

    collect(viewed(Instant.now().minus(Duration.ofMinutes(1))));
    assertThat(json(call(HttpMethod.GET, ME, HISTORY, null)).get("items")).isEmpty();

    collect(viewed(Instant.now()));
    assertThat(json(call(HttpMethod.GET, ME, HISTORY, null)).get("items")).hasSize(1);
  }

  @Test
  void delete_one_is_idempotent() {
    collect(viewed(null));

    assertThat(status(call(HttpMethod.DELETE, ME, HISTORY + "/" + ARTICLE, null))).isEqualTo(204);
    assertThat(status(call(HttpMethod.DELETE, ME, HISTORY + "/" + ARTICLE, null))).isEqualTo(204);
    assertThat(json(call(HttpMethod.GET, ME, HISTORY, null)).get("items")).isEmpty();
  }

  @Test
  void bad_cursor_and_bad_id_are_422() {
    assertThat(status(call(HttpMethod.GET, ME, HISTORY + "?cursor=!!", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.DELETE, ME, HISTORY + "/0", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.PUT, ME, RECORDING, "{}"))).isEqualTo(422);
  }

  /// 登录用户打开样例文献的事件。
  ///
  /// @param happenedAt 发生时刻；为 `null` 时用样例原有的时间（校正后约等于收到时刻）
  /// @return 事件
  private static ObjectNode viewed(Instant happenedAt) {
    ObjectNode event = CollectExamples.example("collect-envelope--article-viewed.json");
    event.put("user_id", String.valueOf(ME));
    if (happenedAt != null) {
      event.put("created_at", TIMESTAMP.format(happenedAt));
      event.put("sent_at", TIMESTAMP.format(Instant.now()));
    }
    return event;
  }

  private void collect(ObjectNode event) {
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(CollectExamples.batch(event))
        .exchange()
        .expectStatus()
        .isAccepted();
  }

  private EntityExchangeResult<String> call(HttpMethod method, Long user, String uri, String body) {
    HttpHeaders headers = user == null ? new HttpHeaders() : TestIdentity.headers(user);
    RestTestClient.RequestBodySpec spec =
        restClient.method(method).uri(uri).headers(h -> h.addAll(headers));
    if (body != null) {
      spec.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return spec.exchange().expectBody(String.class).returnResult();
  }

  private static int status(EntityExchangeResult<String> result) {
    return result.getStatus().value();
  }

  private static JsonNode json(EntityExchangeResult<String> result) {
    assertThat(status(result)).isEqualTo(200);
    return CollectExamples.MAPPER.readTree(result.getResponseBody());
  }

  private static List<ConsumerRecord<String, String>> readRaw(String eventId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of("it-history.raw"));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (System.nanoTime() < deadline) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          if (record.value().contains(eventId)) {
            found.add(record);
          }
        }
        if (!found.isEmpty()) {
          return found;
        }
      }
    }
    return List.of();
  }
}
