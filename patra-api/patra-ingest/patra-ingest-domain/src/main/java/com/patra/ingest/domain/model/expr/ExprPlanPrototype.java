package com.patra.ingest.domain.model.expr;

import java.util.Map;
import java.util.Objects;

/**
 * 表达式原型快照（JSON 字符串形式）。
 */
public record ExprPlanPrototype(String exprProtoHash,
                                String exprDefinitionJson,
                                Map<String, Object> metadata) {
    public ExprPlanPrototype {
        Objects.requireNonNull(exprProtoHash, "exprProtoHash不能为空");
        exprDefinitionJson = exprDefinitionJson == null ? "{}" : exprDefinitionJson;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
