package com.patra.ingest.infra.expr;

import com.patra.ingest.app.port.outbound.ExprCompilerPort;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.expr.ExprPlanPrototype;
import com.patra.ingest.domain.model.expr.ExprSliceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 表达式编译适配器：当前阶段直接透传原型，后续接入 starter-expr。
 */
@Component
public class ExprCompilerPortImpl implements ExprCompilerPort {

    @Override
    public ExprPlanArtifacts compilePrototype(ExprPlanPrototype prototype) {
        // TODO: 接入 ExprCompiler 进行真实编译
        ExprSliceTemplate template = new ExprSliceTemplate(prototype.exprProtoHash(), prototype.exprDefinitionJson(), Map.of());
        return new ExprPlanArtifacts(
                prototype.exprProtoHash(),
                prototype.exprDefinitionJson(),
                prototype.metadata(),
                List.of(template));
    }
}
