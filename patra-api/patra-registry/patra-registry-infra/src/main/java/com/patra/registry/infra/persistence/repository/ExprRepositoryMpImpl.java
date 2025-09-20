package com.patra.registry.infra.persistence.repository;

import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.infra.mapstruct.ExprEntityConverter;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final ExprEntityConverter converter;

    @Override
    public List<ExprField> findAllFields() {
        List<RegExprFieldDictDO> entities = fieldDictMapper.selectAllActive();
        return entities.stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<ApiParamMapping> findActiveParamMapping(Long provenanceId,
                                                            String taskType,
                                                            String operationCode,
                                                            String stdKey,
                                                            Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        // Prefer TASK scope when taskType is provided
        if (taskType != null) {
            Optional<RegProvApiParamMapDO> taskLevel = apiParamMapMapper.selectActive(
                    provenanceId, "TASK", taskKey, normalizeCode(operationCode), normalizeKey(stdKey), timestamp);
            if (taskLevel.isPresent()) {
                return taskLevel.map(converter::toDomain);
            }
        }

        Optional<RegProvApiParamMapDO> sourceLevel = apiParamMapMapper.selectActive(
                provenanceId, "SOURCE", "ALL", normalizeCode(operationCode), normalizeKey(stdKey), timestamp);
        return sourceLevel.map(converter::toDomain);
    }

    @Override
    public Optional<ExprCapability> findActiveCapability(Long provenanceId,
                                                         String taskType,
                                                         String fieldKey,
                                                         Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<RegProvExprCapabilityDO> taskLevel = capabilityMapper.selectActive(
                    provenanceId, "TASK", taskKey, normalizeKey(fieldKey), timestamp);
            if (taskLevel.isPresent()) {
                return taskLevel.map(converter::toDomain);
            }
        }

        Optional<RegProvExprCapabilityDO> sourceLevel = capabilityMapper.selectActive(
                provenanceId, "SOURCE", "ALL", normalizeKey(fieldKey), timestamp);
        return sourceLevel.map(converter::toDomain);
    }

    @Override
    public Optional<ExprRenderRule> findActiveRenderRule(Long provenanceId,
                                                         String taskType,
                                                         String fieldKey,
                                                         String opCode,
                                                         String matchTypeCode,
                                                         Boolean negated,
                                                         String valueTypeCode,
                                                         String emitTypeCode,
                                                         Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);
        String normalizedOp = normalizeCode(opCode);
        String normalizedMatchKey = normalizeMatchKey(matchTypeCode);
        String normalizedNegatedKey = normalizeNegatedKey(negated);
        String normalizedValueKey = normalizeValueKey(valueTypeCode);
        String normalizedEmit = normalizeCode(emitTypeCode);
        String normalizedField = normalizeKey(fieldKey);

        if (taskType != null) {
            Optional<RegProvExprRenderRuleDO> taskLevel = renderRuleMapper.selectActive(
                    provenanceId, "TASK", taskKey, normalizedField, normalizedOp,
                    normalizedMatchKey, normalizedNegatedKey, normalizedValueKey, normalizedEmit, timestamp);
            if (taskLevel.isPresent()) {
                return taskLevel.map(converter::toDomain);
            }
        }

        Optional<RegProvExprRenderRuleDO> sourceLevel = renderRuleMapper.selectActive(
                provenanceId, "SOURCE", "ALL", normalizedField, normalizedOp,
                normalizedMatchKey, normalizedNegatedKey, normalizedValueKey, normalizedEmit, timestamp);
        return sourceLevel.map(converter::toDomain);
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
        return Boolean.TRUE.equals(negated) ? "T" : "F";
    }

    private String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return "ANY";
        }
        return valueTypeCode.trim().toUpperCase();
    }
}
