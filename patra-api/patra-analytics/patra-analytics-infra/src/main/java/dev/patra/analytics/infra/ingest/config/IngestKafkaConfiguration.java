package dev.patra.analytics.infra.ingest.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.EnableScheduling;

/// 入库查 Kafka 元数据用的管理客户端（topic ID、已提交与末端偏移量），以及刷新消费滞后的定时调度。
@Configuration
@EnableScheduling
public class IngestKafkaConfiguration {

  /// 用 Boot 的 Kafka 连接配置建管理客户端（只在第一次请求时连 broker）。
  ///
  /// @param kafkaAdmin Boot 的 KafkaAdmin（带 `spring.kafka.*` 的地址）
  /// @return 管理客户端
  @Bean(destroyMethod = "close")
  public Admin ingestKafkaAdmin(KafkaAdmin kafkaAdmin) {
    return Admin.create(kafkaAdmin.getConfigurationProperties());
  }
}
