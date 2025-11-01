package com.patra.ingest.config;

import com.patra.ingest.domain.factory.OutboxRelayLogFactory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for registering Domain layer factories as Spring beans.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Register Domain factories (pure Java classes) in Spring container
 *   <li>Inject infrastructure dependencies (e.g., Clock from patra-starter-core) into Domain
 *       factories
 *   <li>Maintain Domain layer purity (factories remain pure Java, no @Component)
 * </ul>
 *
 * <p>Design rationale:
 *
 * <ul>
 *   <li>Domain layer must remain pure Java (no framework dependencies)
 *   <li>Factory classes in Domain don't use @Component annotation
 *   <li>Boot layer bridges Domain (pure Java) with Spring framework
 *   <li>Dependency direction: Boot → Domain (correct), NOT Domain → Spring (wrong)
 *   <li>Infrastructure beans (Clock) provided by patra-spring-boot-starter-core
 * </ul>
 *
 * <p>Benefits:
 *
 * <ul>
 *   <li>Domain layer can be tested without Spring context
 *   <li>Factories can be instantiated in unit tests with test-specific dependencies
 *   <li>Adheres to Hexagonal Architecture dependency rules
 *   <li>Centralized infrastructure beans eliminate duplication
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Configuration
public class DomainFactoryConfiguration {

  /**
   * Registers OutboxRelayLogFactory as Spring bean.
   *
   * <p>Factory methods:
   *
   * <ul>
   *   <li>createForPublished - Success scenario
   *   <li>createForDeferred - Transient error with retry
   *   <li>createForFailed - Permanent failure
   *   <li>createForLeaseMissed - Concurrent lease conflict
   * </ul>
   *
   * @param clock Clock instance for timestamp generation (auto-injected from
   *     patra-spring-boot-starter-core)
   * @return OutboxRelayLogFactory instance
   */
  @Bean
  public OutboxRelayLogFactory outboxRelayLogFactory(Clock clock) {
    return new OutboxRelayLogFactory(clock);
  }
}
