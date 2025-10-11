package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonNodeSupportTest {

    static class Helper implements JsonNodeSupport { }

    @Test
    void read_and_write_should_roundtrip_and_validate() {
        Helper h = new Helper();
        assertThat(h.readJsonNode(null)).isNull();
        JsonNode node = h.readJsonNode("{\"a\":1}");
        assertThat(h.writeJsonString(node)).isEqualTo("{\"a\":1}");
        assertThat(h.writeJsonString(null)).isNull();
        assertThatThrownBy(() -> h.readJsonNode("{oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse JSON");
    }
}

