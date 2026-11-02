package dev.patra.analytics.adapter.ingest.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/// 入库消费者的容器工厂（入库设计 §4.1）：批量监听、并发 1、写成后手动提交，失败按指数退避无限重试同一批。
@Configuration
public class IngestListenerConfiguration {

  /// 退避起点 1 秒
  private static final long INITIAL_BACKOFF_MS = 1_000;

  /// 退避上限 30 秒
  private static final long MAX_BACKOFF_MS = 30_000;

  /// 入库监听用的容器工厂（broker 地址仍来自 `spring.kafka.bootstrap-servers`）。
  ///
  /// 监听方法抛普通异常时，错误处理器用内存里的同一批重新调用（不重新拉取），批次边界与去重令牌不变。
  ///
  /// @param bootConsumerFactory Boot 按 `spring.kafka.*` 建的消费者工厂
  /// @param groupId 消费组
  /// @return 容器工厂
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> ingestListenerFactory(
      ConsumerFactory<?, ?> bootConsumerFactory,
      @Value("${patra.analytics.ingest.group-id}") String groupId) {
    Map<String, Object> config = new HashMap<>(bootConsumerFactory.getConfigurationProperties());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50_000);
    config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1_048_576);
    config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 1_000);
    config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 16_777_216);
    config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 67_108_864);
    config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(
        new DefaultKafkaConsumerFactory<>(
            config, new StringDeserializer(), new StringDeserializer()));
    factory.setBatchListener(true);
    factory.setConcurrency(1);
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);
    ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, 2.0);
    backOff.setMaxInterval(MAX_BACKOFF_MS);
    factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));
    return factory;
  }
}
