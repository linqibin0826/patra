package dev.patra.analytics.collect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.analytics.kafka.raw-topic=it-collect.raw",
      "patra.analytics.kafka.dlq-topic=it-collect.dlq"
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
class CollectFlowIT {

  private static final JsonMapper MAPPER = CollectExamples.MAPPER;
  private static final String GOOGLEBOT =
      "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

  @Autowired private RestTestClient restClient;

  @Test
  void mixed_batch_returns_202_and_routes_events() {
    ObjectNode viewed = CollectExamples.example("collect-envelope--article-viewed.json");
    ObjectNode page = CollectExamples.example("collect-envelope--page-viewed.json");
    ObjectNode mismatch = CollectExamples.example("invalid/collect-envelope--source-mismatch.json");

    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(CollectExamples.batch(viewed, page, mismatch))
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.accepted")
        .isEqualTo(2)
        .jsonPath("$.rejected")
        .isEqualTo(1);

    List<ConsumerRecord<String, String>> raw = read("it-collect.raw", id(viewed), id(page));
    assertThat(raw).hasSize(2);
    for (ConsumerRecord<String, String> record : raw) {
      JsonNode event = MAPPER.readTree(record.value());
      assertThat(event.has("client_ip")).isFalse();
      assertThat(event.has("user_agent")).isFalse();
      assertThat(event.has("received_at")).isTrue();
      assertThat(event.has("derived_at")).isTrue();
      assertThat(event.get("is_bot").asBoolean()).isFalse();
      assertThat(record.key()).isEqualTo(event.get("device_id").asString());
    }
    List<ConsumerRecord<String, String>> dlq = read("it-collect.dlq", id(mismatch));
    JsonNode rejected = MAPPER.readTree(dlq.getFirst().value());
    assertThat(rejected.get("reason_code").asString()).isEqualTo("source_mismatch");
    assertThat(rejected.get("raw").asString()).doesNotContain("client_ip", "203.0.113.24");
  }

  @Test
  void bot_user_agent_is_marked() {
    ObjectNode viewed = CollectExamples.example("collect-envelope--article-viewed.json");
    viewed.put("user_agent", GOOGLEBOT);

    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(CollectExamples.batch(viewed))
        .exchange()
        .expectStatus()
        .isAccepted();

    JsonNode event = MAPPER.readTree(read("it-collect.raw", id(viewed)).getFirst().value());
    assertThat(event.get("is_bot").asBoolean()).isTrue();
    assertThat(event.get("bot_reason").asString()).isEqualTo("ua_list");
  }

  @Test
  void empty_batch_returns_400() {
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"events\":[]}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ANALYTICS-0400");
  }

  @Test
  void invalid_json_returns_400() {
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void oversized_body_returns_413() {
    String body = "{\"events\":[\"" + "x".repeat(1_048_577) + "\"]}";

    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(413)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ANALYTICS-0413");
  }

  @Test
  void collect_metrics_are_registered() {
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(CollectExamples.batch(CollectExamples.example("collect-envelope--page-viewed.json")))
        .exchange()
        .expectStatus()
        .isAccepted();

    restClient
        .get()
        .uri("/actuator/metrics/analytics.collect.events")
        .exchange()
        .expectStatus()
        .isOk();
    restClient
        .get()
        .uri("/actuator/metrics/analytics.collect.batches")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private static String id(JsonNode event) {
    return event.get("event_id").asString();
  }

  private static List<ConsumerRecord<String, String>> read(String topic, String... eventIds) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    List<String> wanted = List.of(eventIds);
    List<ConsumerRecord<String, String>> found = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (found.size() < wanted.size() && System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          if (wanted.stream().anyMatch(record.value()::contains)) {
            found.add(record);
          }
        }
      }
    }
    assertThat(found).hasSize(wanted.size());
    return found;
  }
}
