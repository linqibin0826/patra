package com.patra.ingest.infra.registry;

import com.patra.ingest.domain.port.ExprPrototypePort;
import com.patra.ingest.domain.model.expr.ExprPlanPrototype;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表达式原型获取适配器（后续对接 Registry expr API）。
 */
@Component
public class ExprPrototypePortImpl implements ExprPrototypePort {

    @Override
    public ExprPlanPrototype fetchPrototype(String provenanceCode, String endpointName, String operationCode) {
        // TODO 调用真实接口，此处返回基础 JSON
        String protoHash = provenanceCode + ":" + operationCode;
        String definition = "{\"type\":\"MATCH_ALL\"}";
        return new ExprPlanPrototype(protoHash, definition, Map.of());
    }
}
