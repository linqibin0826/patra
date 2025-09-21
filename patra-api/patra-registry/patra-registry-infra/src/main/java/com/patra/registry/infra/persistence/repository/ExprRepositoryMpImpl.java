package com.patra.registry.infra.persistence.repository;

import com.patra.common.constant.RegistryKeys;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.RegistryScope;
import com.patra.common.util.RegistryKeyUtils;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.infra.mapstruct.ExprEntityConverter;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expr 仓储 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExprRepositoryMpImpl implements ExprRepository {

    private final RegExprFieldDictMapper fieldDictMapper;
    private final RegProvApiParamMapMapper apiParamMapMapper;
    private final RegProvExprCapabilityMapper capabilityMapper;
    private final RegProvExprRenderRuleMapper renderRuleMapper;
    private final RegProvenanceMapper provenanceMapper;
    private final ExprEntityConverter converter;

    @Override
    public ExprSnapshot loadSnapshot(ProvenanceCode provenanceCode,
                                     String taskType,
                                     String operationCode,
                                     Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = RegistryKeyUtils.normalizeTaskKey(taskType);
        String normalizedOperation = RegistryKeyUtils.normalizeCode(operationCode);

        Long provenanceId = resolveProvenanceId(provenanceCode);

        List<ExprField> fields = fieldDictMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();

        Map<String, ExprCapability> capabilityMap = new LinkedHashMap<>();
        capabilityMapper.selectActiveByScope(provenanceId, RegistryScope.SOURCE.code(), RegistryKeys.ALL, timestamp)
                .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        if (taskType != null) {
            capabilityMapper.selectActiveByScope(provenanceId, RegistryScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        }
        List<ExprCapability> capabilities = new ArrayList<>(capabilityMap.values());

        Map<String, ExprRenderRule> renderRuleMap = new LinkedHashMap<>();
        renderRuleMapper.selectActiveByScope(provenanceId, RegistryScope.SOURCE.code(), RegistryKeys.ALL, timestamp)
                .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        if (taskType != null) {
            renderRuleMapper.selectActiveByScope(provenanceId, RegistryScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        }
        List<ExprRenderRule> renderRules = new ArrayList<>(renderRuleMap.values());

        Map<String, ApiParamMapping> paramMappings = new LinkedHashMap<>();
        apiParamMapMapper.selectActiveByScope(provenanceId, RegistryScope.SOURCE.code(), RegistryKeys.ALL, normalizedOperation, timestamp)
                .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        if (taskType != null) {
            apiParamMapMapper.selectActiveByScope(provenanceId, RegistryScope.TASK.code(), taskKey, normalizedOperation, timestamp)
                    .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        }
        List<ApiParamMapping> apiParams = new ArrayList<>(paramMappings.values());

        return new ExprSnapshot(fields, capabilities, renderRules, apiParams);
    }

    private Long resolveProvenanceId(ProvenanceCode provenanceCode) {
        String code = provenanceCode.getCode();
        return provenanceMapper.selectByCode(code)
                .map(RegProvenanceDO::getId)
                .orElseThrow(() -> new IllegalArgumentException("Provenance code not found: " + code));
    }

    private String renderRuleKey(RegProvExprRenderRuleDO entity) {
        String field = RegistryKeyUtils.normalizeFieldKey(entity.getFieldKey());
        String op = RegistryKeyUtils.normalizeCode(entity.getOpCode());
        String matchKey = RegistryKeyUtils.normalizeMatchKey(entity.getMatchTypeKey());
        String negatedKey = RegistryKeyUtils.normalizeNegatedKey(entity.getNegatedKey());
        String valueKey = RegistryKeyUtils.normalizeValueKey(entity.getValueTypeKey());
        String emit = RegistryKeyUtils.normalizeCode(entity.getEmitTypeCode());
        return String.join("|", field, op, matchKey, negatedKey, valueKey, emit);
    }
}
