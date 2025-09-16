package com.patra.starter.core.json.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.json.JacksonProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    @Bean
    public JacksonProvider jacksonProvider() {
        log.debug("loaded JacksonAutoConfiguration.jacksonProvider()");
        return new JacksonProvider();
    }
}
