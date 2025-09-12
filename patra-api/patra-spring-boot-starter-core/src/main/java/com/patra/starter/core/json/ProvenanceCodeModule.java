package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.core.json.serializer.ProvenanceCodeDeserializer;
import com.patra.starter.core.json.serializer.ProvenanceCodeSerializer;

public class ProvenanceCodeModule extends SimpleModule {
    public ProvenanceCodeModule() {
        addSerializer(ProvenanceCode.class, new ProvenanceCodeSerializer());
        addDeserializer(ProvenanceCode.class, new ProvenanceCodeDeserializer());
    }
}
