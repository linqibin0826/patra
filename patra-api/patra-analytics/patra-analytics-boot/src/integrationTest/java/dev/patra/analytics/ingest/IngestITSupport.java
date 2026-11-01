package dev.patra.analytics.ingest;

import com.clickhouse.client.api.Client;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 入库端到端测试的公共操作：造富化事件、写 Kafka、查 ClickHouse 与已提交位点、等条件成立。
final class IngestITSupport {

  /// 入库的消费组（boot `application.yml`）
  static final String GROUP = "patra-analytics-ingest";

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private IngestITSupport() {}

  /// 一条富化事件（服务端的文献浏览），放进指定会话。
  ///
  /// @param sessionId 会话 ID，用例按它数自己的行
  /// @return 事件 JSON
  static String event(String sessionId) {
    try (InputStream in =
        IngestITSupport.class
            .getClassLoader()
            .getResourceAsStream(
                "analytics/examples/valid/enriched-event--server-article-viewed.json")) {
      ObjectNode node =
          (ObjectNode) MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      node.put("event_id", UUID.randomUUID().toString());
      node.put("session_id", sessionId);
      return MAPPER.writeValueAsString(node);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 同步写几条消息。
  ///
  /// @param topic topic
  /// @param values 消息值
  static void send(String topic, String... values) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers());
    try (KafkaProducer<String, String> producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      for (String value : values) {
        producer.send(new ProducerRecord<>(topic, value)).get(10, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      throw new IllegalStateException("写 Kafka 失败", e);
    }
  }

  /// 消费组在这个 topic 上已提交位点之和。
  ///
  /// @param topic topic
  /// @return 已提交位点之和；没有提交过为 0
  static long committed(String topic) {
    Map<String, Object> config =
        Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers());
    try (Admin admin = Admin.create(config)) {
      return admin
          .listConsumerGroupOffsets(GROUP)
          .partitionsToOffsetAndMetadata()
          .get(10, TimeUnit.SECONDS)
          .entrySet()
          .stream()
          .filter(entry -> entry.getKey().topic().equals(topic))
          .map(Map.Entry::getValue)
          .filter(Objects::nonNull)
          .mapToLong(OffsetAndMetadata::offset)
          .sum();
    } catch (Exception e) {
      throw new IllegalStateException("查已提交位点失败", e);
    }
  }

  /// 这个 topic 各分区末端偏移量之和（已写入的条数）。
  ///
  /// @param topic topic
  /// @return 末端偏移量之和
  static long end(String topic) {
    Map<String, Object> config =
        Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers());
    try (Admin admin = Admin.create(config)) {
      Map<TopicPartition, OffsetSpec> partitions =
          admin
              .describeTopics(List.of(topic))
              .allTopicNames()
              .get(10, TimeUnit.SECONDS)
              .get(topic)
              .partitions()
              .stream()
              .collect(
                  Collectors.toMap(
                      info -> new TopicPartition(topic, info.partition()),
                      info -> OffsetSpec.latest()));
      return admin.listOffsets(partitions).all().get(10, TimeUnit.SECONDS).values().stream()
          .mapToLong(ListOffsetsResultInfo::offset)
          .sum();
    } catch (Exception e) {
      throw new IllegalStateException("查末端偏移量失败", e);
    }
  }

  /// 执行一条只返回一个数的查询（列名 `n`）。
  ///
  /// @param client ClickHouse 客户端
  /// @param sql 查询
  /// @return 结果
  static long number(Client client, String sql) {
    return client.queryAll(sql).getFirst().getLong("n");
  }

  /// 等条件成立，超时则失败。
  ///
  /// @param timeout 最多等多久
  /// @param condition 条件
  /// @return 是否在超时前成立
  static boolean eventually(Duration timeout, BooleanSupplier condition) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return condition.getAsBoolean();
  }
}
