package com.patra.registry.app.service;

import com.patra.registry.app.mapping.ExprQueryAssembler;
import com.patra.registry.contract.query.view.expr.ApiParamMappingQuery;
import com.patra.registry.contract.query.view.expr.ExprCapabilityQuery;
import com.patra.registry.contract.query.view.expr.ExprFieldQuery;
import com.patra.registry.contract.query.view.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.port.ExprRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Expr 查询应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExprQueryAppService {

    private final ExprRepository exprRepository;
    private final ExprQueryAssembler assembler;

    /** 查询全部统一字段。 */
    public List<ExprFieldQuery> listExprFields() {
        return exprRepository.findAllFields().stream()
                .map(assembler::toQuery)
                .toList();
    }

    /** 查找当前生效的参数映射。 */
    public Optional<ApiParamMappingQuery> findParamMapping(Long provenanceId,
                                                           String taskType,
                                                           String operationCode,
                                                           String stdKey,
                                                           Instant at) {
        log.debug("Finding param mapping: provenanceId={}, taskType={}, operationCode={}, stdKey={}",
                provenanceId, taskType, operationCode, stdKey);
        return exprRepository.findActiveParamMapping(provenanceId, taskType, operationCode, stdKey, at)
                .map(assembler::toQuery);
    }

    /** 查找字段能力。 */
    public Optional<ExprCapabilityQuery> findCapability(Long provenanceId,
                                                        String taskType,
                                                        String fieldKey,
                                                        Instant at) {
        log.debug("Finding capability: provenanceId={}, taskType={}, fieldKey={}",
                provenanceId, taskType, fieldKey);
        return exprRepository.findActiveCapability(provenanceId, taskType, fieldKey, at)
                .map(assembler::toQuery);
    }

    /** 查找渲染规则。 */
    public Optional<ExprRenderRuleQuery> findRenderRule(Long provenanceId,
                                                        String taskType,
                                                        String fieldKey,
                                                        String opCode,
                                                        String matchTypeCode,
                                                        Boolean negated,
                                                        String valueTypeCode,
                                                        String emitTypeCode,
                                                        Instant at) {
        log.debug("Finding render rule: provenanceId={}, taskType={}, fieldKey={}, opCode={}",
                provenanceId, taskType, fieldKey, opCode);
        return exprRepository.findActiveRenderRule(provenanceId, taskType, fieldKey, opCode,
                        matchTypeCode, negated, valueTypeCode, emitTypeCode, at)
                .map(assembler::toQuery);
    }
}
