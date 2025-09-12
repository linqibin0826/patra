package com.patra.starter.core.json.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.patra.common.enums.ProvenanceCode;

import java.io.IOException;

public class ProvenanceCodeDeserializer extends JsonDeserializer<ProvenanceCode> {
    @Override
    public ProvenanceCode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return ProvenanceCode.parse(p.getText());
    }
}
