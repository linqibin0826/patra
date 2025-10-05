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
 * MyBatis-based read-side repository for expression metadata.
 * <p>Aggregates field dictionaries, capabilities, render rules, and API parameter mappings to
 * build domain snapshots. Operation-specific configuration is preferred and the logic falls
 * back to {@code ALL} when a dedicated slice is unavailable.</p>
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

    /**
     * Loads expression metadata snapshot for a specific provenance and operation.
     * Aggregates field dictionaries, capabilities, render rules, and API parameter mappings.
     *
     * @param provenanceCode the provenance code
     * @param operationType  the operation type key
     * @param endpointName   the API endpoint name
     * @param at             the query timestamp (null for current time)
     * @return complete expression metadata snapshot
     */
    @Override
    public ExprSnapshot loadSnapshot(ProvenanceCode provenanceCode,
                                     String operationType,
                                     String endpointName,
                                     Instant at) {
        log.info("Loading expression snapshot: provenanceCode={}, operationType={}, endpoint={}",
                provenanceCode.getCode(), operationType, endpointName);

        Instant timestamp = atOrNow(at);
        Long provenanceId = resolveProvenanceId(provenanceCode);

        String operationKey = RegistryKeyNormalizer.normalizeOperationKey(operationType);
        String normalizedEndpoint = RegistryKeyNormalizer.normalizeCode(endpointName);

        List<ExprField> fields = loadFields();
        List<ExprCapability> capabilities = loadCapabilities(provenanceId, operationKey, timestamp);
        List<ExprRenderRule> renderRules = loadRenderRules(provenanceId, operationKey, timestamp);
        List<ApiParamMapping> apiParams = loadApiParamMappings(provenanceId, operationKey, normalizedEndpoint, timestamp);

        log.info("Expression snapshot loaded: provenanceCode={}, fields={}, capabilities={}, renderRules={}, apiParams={}",
                provenanceCode.getCode(), fields.size(), capabilities.size(), renderRules.size(), apiParams.size());

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
                                                       String normalizedEndpoint,
                                                       Instant timestamp) {
        return apiParamMapMapper.selectActiveByTask(provenanceId, operationKey, normalizedEndpoint, timestamp).stream()
                .map(converter::toDomain)
                .toList();
    }

    /**
     * Returns the provided instant or current time if null.
     *
     * @param at the instant to check
     * @return the instant or current time
     */
    private Instant atOrNow(Instant at) {
        return at != null ? at : Instant.now();
    }

    /**
     * Resolves provenance ID from business code.
     *
     * @param provenanceCode the provenance code
     * @return provenance ID
     * @throws IllegalArgumentException if provenance code not found
     */
    private Long resolveProvenanceId(ProvenanceCode provenanceCode) {
        String code = provenanceCode.getCode();
        log.debug("Resolving provenance ID for code: {}", code);
        return provenanceMapper.selectByCode(code)
                .map(RegProvenanceDO::getId)
                .orElseThrow(() -> {
                    log.error("Provenance code not found: {}", code);
                    return new IllegalArgumentException("Provenance code not found: " + code);
                });
    }
}
