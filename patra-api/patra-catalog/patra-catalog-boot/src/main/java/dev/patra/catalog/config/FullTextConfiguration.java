package dev.patra.catalog.config;

import dev.patra.catalog.domain.fulltext.policy.FullTextRetrySchedule;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 全文抓取的领域服务装配（domain 层不能用 Spring 注解，在这里注册）。
///
/// @author linqibin
/// @since 0.11.0
@Configuration
public class FullTextConfiguration {

  /// 全文重试与再查时间表。
  ///
  /// @param clock 时钟
  /// @return 时间表
  @Bean
  public FullTextRetrySchedule fullTextRetrySchedule(Clock clock) {
    return new FullTextRetrySchedule(clock);
  }
}
