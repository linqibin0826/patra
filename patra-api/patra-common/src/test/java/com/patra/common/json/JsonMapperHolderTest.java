package com.patra.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonMapperHolder 行为测试：懒加载与注册覆盖。
 */
class JsonMapperHolderTest {

    private ObjectMapper previous;

    @AfterEach
    void tearDown() {
        if (previous != null) {
            JsonMapperHolder.register(previous);
        }
    }

    @Test
    void getObjectMapper_should_lazyInit_and_registerOverride() {
        // 先拿当前实例作为基线
        previous = JsonMapperHolder.getObjectMapper();
        ObjectMapper first = JsonMapperHolder.getObjectMapper();
        ObjectMapper second = JsonMapperHolder.getObjectMapper();
        assertThat(first).isSameAs(second);

        // 注册新实例应当覆盖
        ObjectMapper custom = new ObjectMapper();
        JsonMapperHolder.register(custom);
        assertThat(JsonMapperHolder.getObjectMapper()).isSameAs(custom);
    }
}

