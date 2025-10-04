package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.support.RegistryKeyNormalizer;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
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
import java.util.List;

/**
 * Expr 仓储 MyBatis 实现。
 * <p>职责：聚合 Expr 相关 DO（字段字典 / 能力 / 渲染规则 / 参数映射）并生成领域快照。</p>
 * <p>说明：优先匹配精确 operationTypeKey，若不存在则回退到 ALL 默认配置。</p>
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
                                     String operationType,
                                     String operationCode,
                                     Instant at) {
        Instant timestamp = atOrNow(at);
        Long provenanceId = resolveProvenanceId(provenanceCode);

        String operationKey = RegistryKeyNormalizer.normalizeOperationKey(operationType);
        String normalizedOperation = RegistryKeyNormalizer.normalizeCode(operationCode);

        List<ExprField> fields = loadFields();
        List<ExprCapability> capabilities = loadCapabilities(provenanceId, operationKey, timestamp);
        List<ExprRenderRule> renderRules = loadRenderRules(provenanceId, operationKey, timestamp);
        List<ApiParamMapping> apiParams = loadApiParamMappings(provenanceId, operationKey, normalizedOperation, timestamp);

        return new ExprSnapshot(fields, capabilities, renderRules, apiParams);
    }

    private List<ExprField> loadFields() {
        return fieldDictMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();
    }

    private List<ExprCapability> loadCapabilities(Long provenanceId,
                                                   String operationKey,
                                                   Instant timestamp) {
        return capabilityMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
                .map(converter::toDomain)
                .toList();
    }

    private List<ExprRenderRule> loadRenderRules(Long provenanceId,
                                                 String operationKey,
                                                 Instant timestamp) {
        return renderRuleMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
                .map(converter::toDomain)
                .toList();
    }

    private List<ApiParamMapping> loadApiParamMappings(Long provenanceId,
                                                       String operationKey,
                                                       String normalizedOperation,
                                                       Instant timestamp) {
        return apiParamMapMapper.selectActiveByTask(provenanceId, operationKey, normalizedOperation, timestamp).stream()
                .map(converter::toDomain)
                .toList();
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
}
