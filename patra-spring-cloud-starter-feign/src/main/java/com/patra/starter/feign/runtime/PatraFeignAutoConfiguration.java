package com.patra.starter.feign.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration entry point for the Papertrace Feign starter.
 *
 * <p>Registers cross-cutting components for Feign clients, including the
 * {@link PatraFeignRequestInterceptor} responsible for propagating shared headers such as the
 * caller service identifier.</p>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PatraFeignProperties.class)
@ConditionalOnClass(name = {
        "feign.Feign",
})
@ConditionalOnProperty(prefix = "patra.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PatraFeignRequestInterceptor patraFeignRequestInterceptor(PatraFeignProperties props,
                                                                     Environment env) {
        return new PatraFeignRequestInterceptor(props, env);
    }
}
