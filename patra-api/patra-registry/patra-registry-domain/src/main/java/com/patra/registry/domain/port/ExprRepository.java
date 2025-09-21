package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;

import java.time.Instant;

/**
 * Expr 相关的仓储端口，提供只读访问能力。
 */
public interface ExprRepository {

    /**
     * 加载指定来源与任务范围的聚合快照（字段、能力、渲染规则、参数映射）。
     */
    ExprSnapshot loadSnapshot(ProvenanceCode provenanceCode,
                              String taskType,
                              String operationCode,
                              Instant at);
}
