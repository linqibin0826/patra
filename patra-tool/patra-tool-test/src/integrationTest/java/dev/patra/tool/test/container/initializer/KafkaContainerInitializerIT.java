package dev.patra.tool.test.container.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/// KafkaContainerInitializer 集成测试，需要本机 Docker。
@DisplayName("KafkaContainerInitializer 集成测试")
class KafkaContainerInitializerIT {

  private static final String TOPIC = "pap165.it";

  @Test
  @DisplayName("启动 Kafka 容器，写进环境的地址能收发一条消息")
  void should_start_kafka_and_publish_bootstrap_servers() throws Exception {
    String bootstrapServers;
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new KafkaContainerInitializer().initialize(context);
      bootstrapServers = context.getEnvironment().getProperty("spring.kafka.bootstrap-servers");
    }
    assertThat(bootstrapServers).isNotBlank();

    try (AdminClient admin =
        AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
      admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
    }
    try (KafkaProducer<String, String> producer =
        new KafkaProducer<>(
            Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
            new StringSerializer(),
            new StringSerializer())) {
      producer.send(new ProducerRecord<>(TOPIC, "k", "hello")).get();
    }

    // assign 而不是 subscribe：不加入消费组，没有重平衡，发一条就能稳定读到一条
    List<String> received = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(
            Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
            new StringDeserializer(),
            new StringDeserializer())) {
      TopicPartition partition = new TopicPartition(TOPIC, 0);
      consumer.assign(List.of(partition));
      consumer.seekToBeginning(List.of(partition));
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                for (ConsumerRecord<String, String> record :
                    consumer.poll(Duration.ofMillis(500))) {
                  received.add(record.value());
                }
                return !received.isEmpty();
              });
    }
    assertThat(received).containsExactly("hello");
  }

  @Test
  @DisplayName("多次获取拿到的是同一个容器")
  void should_reuse_the_same_container() {
    assertThat(KafkaContainerInitializer.getKafkaContainer())
        .isSameAs(KafkaContainerInitializer.getKafkaContainer());
  }
}
