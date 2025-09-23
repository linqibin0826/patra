package com.patra.ingest.app.port.outbound;

import com.patra.ingest.domain.model.expr.ExprPlanPrototype;

public interface ExprPrototypePort {

    ExprPlanPrototype fetchPrototype(String provenanceCode, String endpointName, String operationCode);
}
