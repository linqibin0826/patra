package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 全局 ObjectMapper 提供者。
 * Starter 内部需要 JSON 转换时统一从这里获取，保证配置一致。
 */
public class JacksonProvider implements ApplicationContextAware {

    private static ObjectMapper objectMapper;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // Spring Boot 默认会注册一个全局 ObjectMapper Bean
        objectMapper = applicationContext.getBean(ObjectMapper.class);
    }

    public static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            throw new IllegalStateException("ObjectMapper not initialized yet");
        }
        return objectMapper;
    }
}
