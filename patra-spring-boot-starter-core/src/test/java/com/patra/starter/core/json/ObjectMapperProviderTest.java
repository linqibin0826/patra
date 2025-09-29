package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectMapperProviderTest {

    private ObjectMapper previous;

    @AfterEach
    void tearDown() {
        if (previous != null) {
            JsonMapperHolder.register(previous);
        }
    }

    @Test
    void getObjectMapper_should_fallback_before_context_and_register_after_context() {
        previous = JsonMapperHolder.getObjectMapper();

        ObjectMapperProvider provider = new ObjectMapperProvider();
        // 容器未就绪，走 Holder
        assertThat(ObjectMapperProvider.getObjectMapper()).isNotNull();

        // 使用 StaticApplicationContext 注册一个 ObjectMapper bean
        StaticApplicationContext ctx = new StaticApplicationContext();
        ObjectMapper custom = new ObjectMapper();
        ctx.getBeanFactory().registerSingleton("objectMapper", custom);

        provider.setApplicationContext(ctx);
        assertThat(ObjectMapperProvider.getObjectMapper()).isSameAs(custom);
    }
}
