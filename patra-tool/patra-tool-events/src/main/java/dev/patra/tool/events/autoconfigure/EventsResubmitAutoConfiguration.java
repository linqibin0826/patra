package dev.patra.tool.events.autoconfigure;

import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.tool.events.resubmit.EventPublicationResubmitJob;
import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.core.EventPublicationRegistry;

/// 补投的装配：有 Modulith 事件登记（`IncompleteEventPublications` 由它的多播器实现）时注册补投器，
/// 类路径上有 XXL-Job 时再注册任务入口；`XxlJobSpringExecutor` 会扫到 bean 上的 `@XxlJob` 方法。
@AutoConfiguration(after = EventPublicationAutoConfiguration.class)
@ConditionalOnBean({IncompleteEventPublications.class, EventPublicationRegistry.class})
@EnableConfigurationProperties(EventsProperties.class)
public final class EventsResubmitAutoConfiguration {

  /// 补投器。
  ///
  /// @param publications Modulith 的未完成登记行入口
  /// @param registry Modulith 的事件发布登记
  /// @param clock 应用时钟，没有时用 UTC 系统时钟
  /// @param properties 模块配置
  /// @return 补投器
  @Bean
  public EventPublicationResubmitter eventPublicationResubmitter(
      IncompleteEventPublications publications,
      EventPublicationRegistry registry,
      ObjectProvider<Clock> clock,
      EventsProperties properties) {
    return new EventPublicationResubmitter(
        publications,
        registry,
        clock.getIfAvailable(Clock::systemUTC),
        properties.resubmit().minAge());
  }

  /// 类路径上有 XXL-Job 时注册任务入口。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(XxlJob.class)
  static class XxlJobResubmitConfiguration {

    /// 补投任务。
    ///
    /// @param resubmitter 补投器
    /// @return 任务入口
    @Bean
    EventPublicationResubmitJob eventPublicationResubmitJob(
        EventPublicationResubmitter resubmitter) {
      return new EventPublicationResubmitJob(resubmitter);
    }
  }
}
