package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.json.JacksonProvider;
import com.patra.starter.core.json.ProvenanceCodeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动注册 ProvenanceCode 的 Jackson Module。
 * 放在可以依赖 Jackson 的 starter/infra 模块中。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "provenanceCodeModule")
    public Module provenanceCodeModule() {
        log.debug("loaded JacksonAutoConfiguration.provenanceCodeModule()");
        return new ProvenanceCodeModule();
    }

    @Bean
    public JacksonProvider jacksonProvider() {
        log.debug("loaded JacksonAutoConfiguration.jacksonProvider()");
        return new JacksonProvider();
    }
}
