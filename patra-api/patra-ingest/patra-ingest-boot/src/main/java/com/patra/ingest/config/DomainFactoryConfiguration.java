package com.patra.ingest.config;

import com.patra.ingest.domain.factory.OutboxRelayLogFactory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域层工厂 Spring 集成配置类。
 *
 * <p>在 Spring 容器中注册领域层工厂 Bean,桥接纯 Java 领域层与 Spring 框架基础设施,确保六边形架构的依赖方向正确性。
 *
 * <p>职责:
 *
 * <ul>
 *   <li>将领域层工厂(纯 Java 类)注册为 Spring Bean
 *   <li>向工厂注入基础设施依赖(如 Clock、IdGenerator 等)
 *   <li>保持领域层纯净性(工厂类不使用 @Component 等 Spring 注解)
 *   <li>实现启动层到领域层的单向依赖(启动层 → 领域层,非领域层 → 框架)
 * </ul>
 *
 * <p>架构设计理由:
 *
 * <ul>
 *   <li>领域层必须保持为纯 Java - 无 Spring/框架依赖,可独立测试
 *   <li>工厂类位于领域层,但不使用 @Component 注解
 *   <li>启动层(Boot 模块)负责桥接领域层与 Spring 生态
 *   <li>依赖方向严格遵守: Boot → Domain(正确),Domain ↛ Spring(禁止)
 *   <li>基础设施 Bean(Clock)由 patra-spring-boot-starter-core 统一提供
 * </ul>
 *
 * <p>优势:
 *
 * <ul>
 *   <li>领域层可脱离 Spring 容器进行单元测试
 *   <li>工厂可在测试中使用 Mock 依赖轻松实例化
 *   <li>严格遵守六边形架构和洋葱架构依赖规则
 *   <li>集中式基础设施 Bean 配置,避免重复定义
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
