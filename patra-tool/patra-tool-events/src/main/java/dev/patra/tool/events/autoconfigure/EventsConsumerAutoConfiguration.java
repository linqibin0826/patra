package dev.patra.tool.events.autoconfigure;

import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.events.consumer.JdbcProcessedEventAdapter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/// 消费端去重的装配：有 `JdbcClient` 时注册 `ProcessedEventPort` 的 JDBC 实现，服务自己定义了就让位。
@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean(JdbcClient.class)
public final class EventsConsumerAutoConfiguration {

  /// 去重端口；用到它的服务库里要有 `processed_event` 表。
  ///
  /// @param jdbcClient 服务库
  /// @param clock 应用时钟，没有时用 UTC 系统时钟
  /// @return 去重端口
  @Bean
  @ConditionalOnMissingBean(ProcessedEventPort.class)
  public JdbcProcessedEventAdapter processedEventPort(
      JdbcClient jdbcClient, ObjectProvider<Clock> clock) {
    return new JdbcProcessedEventAdapter(jdbcClient, clock.getIfAvailable(Clock::systemUTC));
  }
}
