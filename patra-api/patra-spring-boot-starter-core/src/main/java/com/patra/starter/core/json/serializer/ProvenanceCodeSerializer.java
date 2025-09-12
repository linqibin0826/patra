package com.patra.starter.core.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.patra.common.enums.ProvenanceCode;

import java.io.IOException;

// 在依赖了 jackson 的模块里写
public class ProvenanceCodeSerializer extends JsonSerializer<ProvenanceCode> {
    @Override
    public void serialize(ProvenanceCode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.getCode());
    }
}

