package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
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
        String taskKey = normalizeTaskKey(taskType);
        String normalizedOperation = normalizeCode(operationCode);

        Long provenanceId = resolveProvenanceId(provenanceCode);

        List<ExprField> fields = fieldDictMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();

        Map<String, ExprCapability> capabilityMap = new LinkedHashMap<>();
        capabilityMapper.selectActiveByScope(provenanceId, "SOURCE", "ALL", timestamp)
                .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        if (taskType != null) {
            capabilityMapper.selectActiveByScope(provenanceId, "TASK", taskKey, timestamp)
                    .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        }
        List<ExprCapability> capabilities = new ArrayList<>(capabilityMap.values());

        Map<String, ExprRenderRule> renderRuleMap = new LinkedHashMap<>();
        renderRuleMapper.selectActiveByScope(provenanceId, "SOURCE", "ALL", timestamp)
                .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        if (taskType != null) {
            renderRuleMapper.selectActiveByScope(provenanceId, "TASK", taskKey, timestamp)
                    .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        }
        List<ExprRenderRule> renderRules = new ArrayList<>(renderRuleMap.values());

        Map<String, ApiParamMapping> paramMappings = new LinkedHashMap<>();
        apiParamMapMapper.selectActiveByScope(provenanceId, "SOURCE", "ALL", normalizedOperation, timestamp)
                .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        if (taskType != null) {
            apiParamMapMapper.selectActiveByScope(provenanceId, "TASK", taskKey, normalizedOperation, timestamp)
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

    private String normalizeTaskKey(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "ALL";
        }
        return taskType.trim();
    }

    private String normalizeCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        return value.trim();
    }

    private String normalizeMatchKey(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return "ANY";
        }
        return matchTypeCode.trim().toUpperCase();
    }

    private String normalizeNegatedKey(Boolean negated) {
        if (negated == null) {
            return "ANY";
        }
        return negated ? "T" : "F";
    }

    private String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return "ANY";
        }
        return valueTypeCode.trim().toUpperCase();
    }

    private String renderRuleKey(RegProvExprRenderRuleDO entity) {
        String field = normalizeKey(entity.getFieldKey());
        String op = normalizeCode(entity.getOpCode());
        String matchKey = entity.getMatchTypeKey() == null || entity.getMatchTypeKey().isBlank()
                ? "ANY" : entity.getMatchTypeKey();
        String negatedKey = entity.getNegatedKey() == null || entity.getNegatedKey().isBlank()
                ? "ANY" : entity.getNegatedKey();
        String valueKey = entity.getValueTypeKey() == null || entity.getValueTypeKey().isBlank()
                ? "ANY" : entity.getValueTypeKey();
        String emit = normalizeCode(entity.getEmitTypeCode());
        return String.join("|", field, op, matchKey, negatedKey, valueKey, emit);
    }
}
