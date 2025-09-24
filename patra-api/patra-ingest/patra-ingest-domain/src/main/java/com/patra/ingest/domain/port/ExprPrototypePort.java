package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.expr.ExprPlanPrototype;

/**
 * 表达式原型获取端口（Domain Port）
 */
public interface ExprPrototypePort {

    ExprPlanPrototype fetchPrototype(String provenanceCode, String endpointName, String operationCode);
}
