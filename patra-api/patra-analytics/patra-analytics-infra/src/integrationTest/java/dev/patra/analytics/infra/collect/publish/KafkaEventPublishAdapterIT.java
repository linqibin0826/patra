package dev.patra.analytics.infra.collect.publish;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaEventPublishAdapterIT {

  private static String bootstrap;
  private static DefaultKafkaProducerFactory<String, String> producerFactory;

  @BeforeAll
  static void start() {
    bootstrap = KafkaContainerInitializer.getKafkaContainer().getBootstrapServers();
    producerFactory =
        new DefaultKafkaProducerFactory<>(KafkaProducerSettings.forBootstrap(bootstrap));
  }

  @AfterAll
  static void stop() {
    producerFactory.destroy();
  }

  @Test
  void publishes_raw_with_device_key_and_dlq_without_key() {
    String suffix = UUID.randomUUID().toString();
    KafkaPublishProperties properties =
        new KafkaPublishProperties("raw-" + suffix, "dlq-" + suffix, Duration.ofSeconds(5));
    KafkaEventPublishAdapter adapter =
        new KafkaEventPublishAdapter(
            new KafkaTemplate<>(producerFactory),
            properties,
            Clock.systemUTC(),
            new SimpleMeterRegistry());

    adapter.publish(
        List.of(
            OutboundMessage.raw("d1", "{\"n\":1}"),
            OutboundMessage.raw("d2", "{\"n\":2}"),
            OutboundMessage.dlq("{\"reason_code\":\"schema_invalid\"}")));

    List<ConsumerRecord<String, String>> raw = readAll(properties.rawTopic(), 2);
    assertThat(raw).extracting(ConsumerRecord::key).containsExactlyInAnyOrder("d1", "d2");
    assertThat(raw).extracting(ConsumerRecord::value).contains("{\"n\":1}", "{\"n\":2}");
    List<ConsumerRecord<String, String>> dlq = readAll(properties.dlqTopic(), 1);
    assertThat(dlq.getFirst().key()).isNull();
    assertThat(dlq.getFirst().value()).isEqualTo("{\"reason_code\":\"schema_invalid\"}");
  }

  private static List<ConsumerRecord<String, String>> readAll(String topic, int expected) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG,
            "it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(topic));
      List<ConsumerRecord<String, String>> records = new ArrayList<>();
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (records.size() < expected && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(500)).forEach(records::add);
      }
      assertThat(records).hasSize(expected);
      return records;
    }
  }
}
