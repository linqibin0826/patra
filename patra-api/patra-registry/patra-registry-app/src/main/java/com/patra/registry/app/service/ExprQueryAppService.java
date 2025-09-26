package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ExprQueryAssembler;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.port.ExprRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Expr 查询应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExprQueryAppService {

    private final ExprRepository exprRepository;
    private final ExprQueryAssembler assembler;

    /** 加载指定来源的表达式聚合快照。 */
    public ExprSnapshotQuery loadSnapshot(String provenanceCode,
                                          String taskType,
                                          String operationCode,
                                          Instant at) {
        ProvenanceCode code = ProvenanceCode.parse(provenanceCode);
        log.debug("Loading expr snapshot: provenanceCode={}, taskType={}, operationCode={}",
                code, taskType, operationCode);
        return assembler.toQuery(exprRepository.loadSnapshot(code, taskType, operationCode, at));
    }
}
