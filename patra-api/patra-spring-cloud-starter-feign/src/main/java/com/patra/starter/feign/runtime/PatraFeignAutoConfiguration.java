package com.patra.starter.feign.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration entry point for the Papertrace Feign starter.
 *
 * <p>Registers cross-cutting components for Feign clients, including the {@link
 * PatraFeignRequestInterceptor} responsible for propagating shared headers such as the caller
 * service identifier.
 *
 * <p><b>Convention-based Feign Client Scanning:</b> Automatically enables Feign client scanning for
 * all {@code @FeignClient} annotated interfaces under {@code com.patra} package. By convention,
 * standard RPC clients should be placed in {@code com.patra.{module}.api.rpc.client} packages.
 *
 * <p><b>Note:</b> Specialized infrastructure clients (e.g., egress gateway) may define their own
 * {@code @EnableFeignClients} in their specific starters if they need custom scanning
 * configuration.
 */
@AutoConfiguration
@EnableConfigurationProperties(PatraFeignProperties.class)
@EnableFeignClients(basePackages = "com.patra")
@ConditionalOnClass(
    name = {
      "feign.Feign",
    })
@ConditionalOnProperty(
    prefix = "patra.feign",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PatraFeignAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PatraFeignRequestInterceptor patraFeignRequestInterceptor(
      PatraFeignProperties props, Environment env) {
    return new PatraFeignRequestInterceptor(props, env);
  }
}
