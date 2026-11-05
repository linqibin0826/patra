package dev.patra.analytics.infra.collect.publish;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/// 采集写 Kafka 的生产者设置（采集 spec §2.2、§4.5），运行时与测试共用这一份。
///
/// `acks=all` + 幂等：返回成功时消息已落盘，网络重试不会写出重复；`max.block.ms` 让连不上时 2 秒就失败，
/// `delivery.timeout.ms` 是一条消息含重试的总时限。值是 JSON 文本，键是设备 ID。
public final class KafkaProducerSettings {

  /// 覆盖 Boot 默认值的生产者设置
  public static final Map<String, Object> OVERRIDES =
      Map.of(
          ProducerConfig.ACKS_CONFIG,
          "all",
          ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
          true,
          ProducerConfig.COMPRESSION_TYPE_CONFIG,
          "zstd",
          ProducerConfig.LINGER_MS_CONFIG,
          10,
          ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
          5_000,
          ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
          4_000,
          ProducerConfig.MAX_BLOCK_MS_CONFIG,
          2_000,
          ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
          StringSerializer.class,
          ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
          StringSerializer.class);

  private KafkaProducerSettings() {}

  /// 指定 broker 地址的完整生产者设置（测试与工具用；运行时地址来自 `spring.kafka.bootstrap-servers`）。
  ///
  /// @param bootstrapServers broker 地址
  /// @return 生产者设置
  public static Map<String, Object> forBootstrap(String bootstrapServers) {
    Map<String, Object> settings = new HashMap<>(OVERRIDES);
    settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return settings;
  }
}
