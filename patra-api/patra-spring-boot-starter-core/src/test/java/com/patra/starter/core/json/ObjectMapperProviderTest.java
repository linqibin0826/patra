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
        // Container not ready yet → fall back to the holder
        assertThat(ObjectMapperProvider.getObjectMapper()).isNotNull();

        // Register an ObjectMapper bean in a StaticApplicationContext
        StaticApplicationContext ctx = new StaticApplicationContext();
        ObjectMapper custom = new ObjectMapper();
        ctx.getBeanFactory().registerSingleton("objectMapper", custom);

        provider.setApplicationContext(ctx);
        assertThat(ObjectMapperProvider.getObjectMapper()).isSameAs(custom);
    }
}
