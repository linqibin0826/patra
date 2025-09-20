package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.adapter.rest._internal.convertor.ExprApiConvertor;
import com.patra.registry.api.rpc.client.ExprClient;
import com.patra.registry.api.rpc.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.rpc.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.rpc.dto.expr.ExprFieldResp;
import com.patra.registry.api.rpc.dto.expr.ExprRenderRuleResp;
import com.patra.registry.app.service.ExprQueryAppService;
import com.patra.registry.contract.query.view.expr.ApiParamMappingQuery;
import com.patra.registry.contract.query.view.expr.ExprCapabilityQuery;
import com.patra.registry.contract.query.view.expr.ExprRenderRuleQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Expr 内部 API 实现。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExprClientImpl implements ExprClient {

    private final ExprQueryAppService exprQueryAppService;
    private final ExprApiConvertor convertor;

    @Override
    public List<ExprFieldResp> listFields() {
        return convertor.toResp(exprQueryAppService.listExprFields());
    }

    @Override
    public ApiParamMappingResp getParamMapping(Long provenanceId,
                                               String taskType,
                                               String operationCode,
                                               String stdKey,
                                               Instant at) {
        Optional<ApiParamMappingQuery> result = exprQueryAppService.findParamMapping(
                provenanceId, taskType, operationCode, stdKey, at);
        return result.map(convertor::toResp).orElse(null);
    }

    @Override
    public ExprCapabilityResp getCapability(Long provenanceId,
                                            String taskType,
                                            String fieldKey,
                                            Instant at) {
        Optional<ExprCapabilityQuery> result = exprQueryAppService.findCapability(
                provenanceId, taskType, fieldKey, at);
        return result.map(convertor::toResp).orElse(null);
    }

    @Override
    public ExprRenderRuleResp getRenderRule(Long provenanceId,
                                            String taskType,
                                            String fieldKey,
                                            String opCode,
                                            String matchTypeCode,
                                            Boolean negated,
                                            String valueTypeCode,
                                            String emitTypeCode,
                                            Instant at) {
        Optional<ExprRenderRuleQuery> result = exprQueryAppService.findRenderRule(
                provenanceId, taskType, fieldKey, opCode, matchTypeCode, negated, valueTypeCode, emitTypeCode, at);
        return result.map(convertor::toResp).orElse(null);
    }
}
