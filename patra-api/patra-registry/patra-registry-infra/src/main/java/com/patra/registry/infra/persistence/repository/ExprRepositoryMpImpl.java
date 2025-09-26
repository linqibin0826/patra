package com.patra.registry.infra.persistence.repository;

import com.patra.common.constant.RegistryKeys;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.RegistryConfigScope; // updated
import com.patra.common.util.RegistryKeyUtils;
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
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = RegistryKeyUtils.normalizeTaskKey(taskType);
        String normalizedOperation = RegistryKeyUtils.normalizeCode(operationCode);

        Long provenanceId = resolveProvenanceId(provenanceCode);

        List<ExprField> fields = fieldDictMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();

        Map<String, ExprCapability> capabilityMap = new LinkedHashMap<>();
        capabilityMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeys.ALL, timestamp)
                .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        if (taskType != null) {
            capabilityMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> capabilityMap.put(entity.getFieldKey(), converter.toDomain(entity)));
        }
        List<ExprCapability> capabilities = new ArrayList<>(capabilityMap.values());

        Map<String, ExprRenderRule> renderRuleMap = new LinkedHashMap<>();
        renderRuleMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeys.ALL, timestamp)
                .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        if (taskType != null) {
            renderRuleMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, timestamp)
                    .forEach(entity -> renderRuleMap.put(renderRuleKey(entity), converter.toDomain(entity)));
        }
        List<ExprRenderRule> renderRules = new ArrayList<>(renderRuleMap.values());

        Map<String, ApiParamMapping> paramMappings = new LinkedHashMap<>();
        apiParamMapMapper.selectActiveByScope(provenanceId, RegistryConfigScope.SOURCE.code(), RegistryKeys.ALL, normalizedOperation, timestamp)
                .forEach(entity -> paramMappings.put(entity.getStdKey(), converter.toDomain(entity)));
        if (taskType != null) {
            apiParamMapMapper.selectActiveByScope(provenanceId, RegistryConfigScope.TASK.code(), taskKey, normalizedOperation, timestamp)
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
        // FIX: 使用布尔字段 negated 生成规范化 key，而不是 String negatedKey 字段，避免类型不匹配。
        String negatedKey = RegistryKeyUtils.normalizeNegatedKey(entity.getNegated());
        String valueKey = RegistryKeyUtils.normalizeValueKey(entity.getValueTypeKey());
        String emit = RegistryKeyUtils.normalizeCode(entity.getEmitTypeCode());
        return String.join("|", field, op, matchKey, negatedKey, valueKey, emit);
    }
}
