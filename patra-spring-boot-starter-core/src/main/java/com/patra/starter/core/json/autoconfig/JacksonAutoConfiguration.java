package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.json.ObjectMapperProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that exposes an {@link ObjectMapperProvider} when Jackson is present.
 *
 * <p>Design goals:
 *
 * <ul>
 *   <li>Register the provider only when {@link ObjectMapper} is on the classpath.
 *   <li>Bridge the Spring-managed mapper into {@code JsonMapperHolder} so non-Spring code paths
 *       share the same configuration.
 * </ul>
 *
 * <h3>Relationship with dependency injection</h3>
 *
 * Application code should still <b>prefer dependency injection</b> to obtain an {@code
 * ObjectMapper}. This auto-configuration merely supplies a bridge for contexts where DI is
 * impossible, and it is not intended to act as a service locator.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

  /**
   * Registers the {@link ObjectMapperProvider} unless the application supplies its own bean. The
   * provider synchronizes the Spring-managed mapper with {@code JsonMapperHolder} once the
   * container is ready.
   */
  @ConditionalOnMissingBean(ObjectMapperProvider.class)
  @Bean
  public ObjectMapperProvider jacksonProvider() {
    log.debug("loaded JacksonAutoConfiguration.jacksonProvider()");
    return new ObjectMapperProvider();
  }
}
