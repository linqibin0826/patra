package com.patra.starter.core.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 核心基础设施 Bean 自动配置类。
 *
 * <p>配置内容:
 *
 * <ul>
 *   <li>{@link Clock} - 统一的时间源,用于生成时间戳
 * </ul>
 *
 * <p>设计原则:
 *
 * <ul>
 *   <li><strong>集中化</strong>: 基础设施 Bean 的单一真实来源
 *   <li><strong>可覆盖性</strong>: 使用 {@code @ConditionalOnMissingBean} 允许自定义实现
 *   <li><strong>可测试性</strong>: Bean 可在测试中替换(例如使用 Clock.fixed() 实现确定性测试)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@AutoConfiguration
public class CoreAutoConfiguration {

  /**
   * 提供系统 UTC 时钟 Bean,用于生成时间戳。
   *
   * <p>优势:
   *
   * <ul>
   *   <li><strong>一致性</strong>: 整个应用程序的所有时间戳使用相同的时区 (UTC)
   *   <li><strong>可测试性</strong>: 可在测试中使用 {@code Clock.fixed()} 进行 Mock,实现确定性测试
   *   <li><strong>集中化</strong>: 单一时间源消除了重复的 Clock Bean 定义
   * </ul>
   *
   * <p>使用示例:
   *
   * <pre>{@code
   * @Service
   * public class MyService {
   *   private final Clock clock;
   *
   *   public MyService(Clock clock) {
   *     this.clock = clock;
   *   }
   *
   *   public void doSomething() {
   *     Instant now = Instant.now(clock);
   *   }
   * }
   * }</pre>
   *
   * @return 使用系统默认时区 (UTC) 的 Clock 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
