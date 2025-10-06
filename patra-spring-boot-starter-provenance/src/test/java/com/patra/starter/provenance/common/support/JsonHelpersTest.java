package com.patra.starter.provenance.common.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonHelpersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void textValueShouldPreferTextNode() throws Exception {
        assertThat(JsonHelpers.textValue(MAPPER.readTree("\"plain\""))).isEqualTo("plain");
    }

    @Test
    void textValueShouldExtractHashText() throws Exception {
        assertThat(JsonHelpers.textValue(MAPPER.readTree("{\"#text\":\"value\"}"))).isEqualTo("value");
    }

    @Test
    void toStringListShouldHandleArraysAndScalars() throws Exception {
        assertThat(JsonHelpers.toStringList(MAPPER.readTree("[\"a\",\"b\"]"))).containsExactly("a", "b");
        assertThat(JsonHelpers.toStringList(MAPPER.readTree("\"c\""))).containsExactly("c");
    }

    @Test
    void toNodeListShouldWrapSingleton() throws Exception {
        assertThat(JsonHelpers.toNodeList(MAPPER.readTree("{\"field\":1}"))).hasSize(1);
    }
}
