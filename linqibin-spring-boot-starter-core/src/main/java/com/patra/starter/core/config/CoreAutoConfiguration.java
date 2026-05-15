package com.patra.starter.core.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 核心基础设施 Bean 自动配置类。
///
/// 配置内容:
///
/// - {@link Clock} - 统一的时间源,用于生成时间戳
///
/// 设计原则:
///
/// - **集中化**: 基础设施 Bean 的单一真实来源
///   - **可覆盖性**: 使用 `@ConditionalOnMissingBean` 允许自定义实现
///   - **可测试性**: Bean 可在测试中替换(例如使用 Clock.fixed() 实现确定性测试)
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
public class CoreAutoConfiguration {

  /// 提供系统 UTC 时钟 Bean,用于生成时间戳。
  ///
  /// 优势:
  ///
  /// - **一致性**: 整个应用程序的所有时间戳使用相同的时区 (UTC)
  ///   - **可测试性**: 可在测试中使用 `Clock.fixed()` 进行 Mock,实现确定性测试
  ///   - **集中化**: 单一时间源消除了重复的 Clock Bean 定义
  ///
  /// 使用示例:
  ///
  /// ```java
  /// @Service
  /// public class MyService {
  ///   private final Clock clock;
  ///
  ///   public MyService(Clock clock) {
  ///     this.clock = clock;
  ///
  ///   public void doSomething() {
  ///     Instant now = Instant.now(clock);
  /// ```
  ///
  /// @return 使用系统默认时区 (UTC) 的 Clock 实例
  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
