package dev.patra.tool.events.autoconfigure;

import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.events.consumer.JdbcProcessedEventAdapter;
import dev.patra.tool.events.consumer.ProcessedEventPurgeJob;
import dev.patra.tool.events.consumer.ProcessedEventPurger;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/// 消费端去重的装配：有 `JdbcClient` 时注册 `ProcessedEventPort` 的 JDBC 实现（服务自己定义了就让位）与
/// `processed_event` 的保留期清理器；类路径上有 XXL-Job 时再注册清理任务入口，只有消费事件的服务需要在调度中心登记它。
@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean(JdbcClient.class)
@EnableConfigurationProperties(EventsProperties.class)
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

  /// 已处理事件的保留期清理器。
  ///
  /// @param jdbcClient 服务库
  /// @param clock 应用时钟，没有时用 UTC 系统时钟
  /// @param properties 模块配置
  /// @return 清理器
  @Bean
  public ProcessedEventPurger processedEventPurger(
      JdbcClient jdbcClient, ObjectProvider<Clock> clock, EventsProperties properties) {
    return new ProcessedEventPurger(
        jdbcClient,
        clock.getIfAvailable(Clock::systemUTC),
        properties.consumer().processedRetention());
  }

  /// 类路径上有 XXL-Job 时注册清理任务入口。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(XxlJob.class)
  static class XxlJobPurgeConfiguration {

    /// 清理任务。
    ///
    /// @param purger 清理器
    /// @return 任务入口
    @Bean
    ProcessedEventPurgeJob processedEventPurgeJob(ProcessedEventPurger purger) {
      return new ProcessedEventPurgeJob(purger);
    }
  }
}
