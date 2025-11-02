package com.patra.ingest.config;

import com.patra.ingest.domain.factory.OutboxRelayLogFactory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用于在 Spring 容器中注册领域层工厂的配置类。
 *
 * <p>职责:
 *
 * <ul>
 *   <li>在 Spring 容器中注册领域层工厂(纯 Java 类)
 *   <li>向领域层工厂注入基础设施依赖(例如来自 patra-starter-core 的 Clock)
 *   <li>保持领域层的纯净(工厂保持为纯 Java,无 @Component 注解)
 * </ul>
 *
 * <p>设计理由:
 *
 * <ul>
 *   <li>领域层必须保持为纯 Java(无框架依赖)
 *   <li>领域层中的工厂类不使用 @Component 注解
 *   <li>启动层桥接领域层(纯 Java)与 Spring 框架
 *   <li>依赖方向: 启动层 → 领域层(正确),非 领域层 → Spring(错误)
 *   <li>基础设施 Bean(Clock)由 patra-spring-boot-starter-core 提供
 * </ul>
 *
 * <p>优势:
 *
 * <ul>
 *   <li>领域层可以不使用 Spring 上下文进行测试
 *   <li>工厂可以在单元测试中用测试特定的依赖进行实例化
 *   <li>遵守六边形架构依赖规则
 *   <li>集中式基础设施 Bean 消除重复
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Configuration
public class DomainFactoryConfiguration {

  /**
   * 将 OutboxRelayLogFactory 注册为 Spring Bean。
   *
   * <p>工厂方法:
   *
   * <ul>
   *   <li>createForPublished - 成功场景
   *   <li>createForDeferred - 临时错误并重试
   *   <li>createForFailed - 永久失败
   *   <li>createForLeaseMissed - 并发租约冲突
   * </ul>
   *
   * @param clock 用于时间戳生成的 Clock 实例(从 patra-spring-boot-starter-core 自动注入)
   * @return OutboxRelayLogFactory 实例
   */
  @Bean
  public OutboxRelayLogFactory outboxRelayLogFactory(Clock clock) {
    return new OutboxRelayLogFactory(clock);
  }
}
