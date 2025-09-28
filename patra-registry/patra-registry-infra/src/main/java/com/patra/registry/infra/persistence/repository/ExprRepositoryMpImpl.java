package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.RegistryConfigScope;
import com.patra.registry.domain.support.RegistryKeyNormalizer;
import com.patra.registry.domain.support.RegistryKeyPlaceholders;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
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
 * <p>职责：聚合 Expr 相关 DO（字段字典 / 能力 / 渲染规则 / 参数映射）并生成领域快照。</p>
 * <p>说明：按 SOURCE → TASK 作用域顺序合并，TASK 级覆盖同 key 的 SOURCE 级条目。</p>
 *
 * @author linqibin
 * @since 0.1.0
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
        Instant timestamp = atOrNow(at);
        Long provenanceId = resolveProvenanceId(provenanceCode);

        String taskKey = RegistryKeyNormalizer.normalizeTaskKey(taskType);
        boolean hasTaskScope = !RegistryKeyPlaceholders.ALL.equals(taskKey);
        String normalizedOperation = RegistryKeyNormalizer.normalizeCode(operationCode);

        List<ExprField> fields = loadFields();
        List<ExprCapability> capabilities = loadCapabilities(provenanceId, taskKey, hasTaskScope, timestamp);
        List<ExprRenderRule> renderRules = loadRenderRules(provenanceId, taskKey, hasTaskScope, timestamp);
        List<ApiParamMapping> apiParams = loadApiParamMappings(provenanceId, taskKey, normalizedOperation, hasTaskScope, timestamp);

        return new ExprSnapshot(fields, capabilities, renderRules, apiParams);
    }

    private List<ExprField> loadFields() {
        return fieldDictMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();
    }

    private List<ExprCapability> loadCapabilities(Long provenanceId,
                                                   String taskKey,
                                                   boolean hasTaskScope,
                                                   Instant timestamp) {
        Map<String, ExprCapability> capabilityMap = new LinkedHashMap<>();
        capabilityMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeyPlaceholders.ALL, timestamp)
                .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        if (hasTaskScope) {
            capabilityMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        }
        return new ArrayList<>(capabilityMap.values());
    }

    private List<ExprRenderRule> loadRenderRules(Long provenanceId,
                                                 String taskKey,
                                                 boolean hasTaskScope,
                                                 Instant timestamp) {
        Map<String, ExprRenderRule> renderRuleMap = new LinkedHashMap<>();
        renderRuleMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeyPlaceholders.ALL, timestamp)
                .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        if (hasTaskScope) {
            renderRuleMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        }
        return new ArrayList<>(renderRuleMap.values());
    }

    private List<ApiParamMapping> loadApiParamMappings(Long provenanceId,
                                                       String taskKey,
                                                       String normalizedOperation,
                                                       boolean hasTaskScope,
                                                       Instant timestamp) {
        Map<String, ApiParamMapping> paramMappings = new LinkedHashMap<>();
        apiParamMapMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeyPlaceholders.ALL, normalizedOperation, timestamp)
                .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        if (hasTaskScope) {
            apiParamMapMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, normalizedOperation, timestamp)
                    .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        }
        return new ArrayList<>(paramMappings.values());
    }

    private Instant atOrNow(Instant at) {
        return at != null ? at : Instant.now();
    }

    private Long resolveProvenanceId(ProvenanceCode provenanceCode) {
        String code = provenanceCode.getCode();
        return provenanceMapper.selectByCode(code)
                .map(RegProvenanceDO::getId)
                .orElseThrow(() -> new IllegalArgumentException("Provenance code not found: " + code));
    }

    private String renderRuleKey(RegProvExprRenderRuleDO entity) {
        String field = RegistryKeyNormalizer.normalizeFieldKey(entity.getFieldKey());
        String op = RegistryKeyNormalizer.normalizeCode(entity.getOpCode());
        String matchKey = RegistryKeyNormalizer.normalizeMatchKey(entity.getMatchTypeKey());
        String negatedKey = RegistryKeyNormalizer.normalizeNegatedKey(entity.getNegated());
        String valueKey = RegistryKeyNormalizer.normalizeValueKey(entity.getValueTypeKey());
        String emit = RegistryKeyNormalizer.normalizeCode(entity.getEmitTypeCode());
        return String.join("|", field, op, matchKey, negatedKey, valueKey, emit);
    }
}
