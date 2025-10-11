package com.patra.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonNodeMappingsTest {

    @Test
    void jsonStringToNode_and_nodeToString_roundtrip() {
        assertThat(JsonNodeMappings.jsonStringToNode(null)).isNull();
        assertThat(JsonNodeMappings.jsonStringToNode(" \t\n")).isNull();
        JsonNode node = JsonNodeMappings.jsonStringToNode("{\"a\":1}");
        assertThat(node).isNotNull();
        assertThat(JsonNodeMappings.jsonNodeToString(node)).isEqualTo("{\"a\":1}");

        assertThat(JsonNodeMappings.jsonNodeToString(null)).isNull();
        assertThatThrownBy(() -> JsonNodeMappings.jsonStringToNode("{oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse JSON");
    }

    @Test
    void map_and_node_conversions() {
        assertThat(JsonNodeMappings.mapToJsonNode(null)).isNull();
        assertThat(JsonNodeMappings.mapToJsonNode(Map.of())).isNull();

        Map<String, Object> map = new HashMap<>();
        map.put("x", 1);
        map.put("y", "z");
        JsonNode node = JsonNodeMappings.mapToJsonNode(map);
        assertThat(node).isNotNull();

        Map<String, Object> back = JsonNodeMappings.jsonNodeToMap(node);
        assertThat(back).containsEntry("x", 1).containsEntry("y", "z");

        assertThat(JsonNodeMappings.jsonNodeToMap(null)).isEmpty();
    }
}

