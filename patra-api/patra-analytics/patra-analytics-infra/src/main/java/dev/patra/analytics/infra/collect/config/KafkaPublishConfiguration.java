package dev.patra.analytics.infra.collect.config;

import dev.patra.analytics.infra.collect.publish.KafkaProducerSettings;
import dev.patra.analytics.infra.collect.publish.KafkaPublishProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 采集写 Kafka 的装配：topic 配置，以及把 `KafkaProducerSettings` 套到 Boot 的生产者工厂上。
@Configuration
@EnableConfigurationProperties(KafkaPublishProperties.class)
public class KafkaPublishConfiguration {

  /// 用采集的生产者设置覆盖 Boot 默认值（broker 地址仍来自 `spring.kafka.bootstrap-servers`）。
  ///
  /// @return 生产者工厂定制器
  @Bean
  public DefaultKafkaProducerFactoryCustomizer collectProducerSettings() {
    return factory -> factory.updateConfigs(KafkaProducerSettings.OVERRIDES);
  }
}
