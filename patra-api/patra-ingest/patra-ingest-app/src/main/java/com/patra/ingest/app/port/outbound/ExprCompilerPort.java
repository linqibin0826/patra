package com.patra.ingest.app.port.outbound;

import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.expr.ExprPlanPrototype;

public interface ExprCompilerPort {

    ExprPlanArtifacts compilePrototype(ExprPlanPrototype prototype);
}
