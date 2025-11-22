package com.patra.ingest.app.usecase.relay.config;

import com.patra.ingest.domain.policy.RelayRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Outbox 中继用例的 Bean 注册
///
/// 职责: 暴露基于配置的重试策略
///
/// 注意: Clock bean 由 patra-spring-boot-starter-core 提供,在需要时自动注入
@Configuration
public class OutboxRelayConfiguration {

  /// 构建重试策略: 带上限的指数退避
  @Bean
  public RelayRetryPolicy relayRetryPolicy(OutboxRelayProperties properties) {
    return new RelayRetryPolicy(
        properties.getInitialBackoff(),
        properties.getBackoffMultiplier(),
        properties.getMaxBackoff());
  }
}
