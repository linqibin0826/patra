package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.domain.support.RegistryKeyStandardizer;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-based read-side repository for expression metadata.
 *
 * <p>Aggregates field dictionaries, capabilities, render rules, and API parameter mappings to build
 * domain snapshots. Operation-specific configuration is preferred and the logic falls back to
 * {@code ALL} when a dedicated slice is unavailable.
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
   * Loads expression metadata snapshot for a specific provenance and operation. Aggregates field
   * dictionaries, capabilities, render rules, and API parameter mappings.
   *
   * @param provenanceCode the provenance code
   * @param operationType the operation type key
   * @param endpointName the API endpoint name (nullable for endpoint-agnostic queries)
   * @param at the query timestamp (null for current time)
   * @return complete expression metadata snapshot
   */
  @Override
  public ExprSnapshot loadSnapshot(
      ProvenanceCode provenanceCode, String operationType, String endpointName, Instant at) {
    Instant timestamp = atOrNow(at);
    Long provenanceId = resolveProvenanceId(provenanceCode);

    String operationKey = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);
    String normalizedEndpoint =
        (endpointName == null || endpointName.isBlank())
            ? null
            : RegistryKeyStandardizer.toUppercaseCode(endpointName);

    List<ExprField> fields = loadFields();
    List<ExprCapability> capabilities = loadCapabilities(provenanceId, operationKey, timestamp);
    List<ExprRenderRule> renderRules = loadRenderRules(provenanceId, operationKey, timestamp);
    List<ApiParamMapping> apiParams =
        loadApiParamMappings(provenanceId, operationKey, normalizedEndpoint, timestamp);

    return new ExprSnapshot(fields, capabilities, renderRules, apiParams);
  }

  /**
   * Loads all active expression fields from the field dictionary.
   *
   * @return list of active expression fields
   */
  private List<ExprField> loadFields() {
    log.debug("Querying all active expression fields from field dictionary");
    List<ExprField> fields =
        fieldDictMapper.selectAllActive().stream().map(converter::toDomain).toList();
    log.debug(
        "Converting {} ExprFieldDictDO entities to domain models, field keys: {}",
        fields.size(),
        fields.stream().map(ExprField::fieldKey).limit(10).toList()
            + (fields.size() > 10 ? "..." : ""));
    return fields;
  }

  /**
   * Loads expression capabilities for a specific provenance and operation.
   *
   * @param provenanceId the provenance ID
   * @param operationKey the normalized operation key
   * @param timestamp the query timestamp
   * @return list of expression capabilities
   */
  private List<ExprCapability> loadCapabilities(
      Long provenanceId, String operationKey, Instant timestamp) {
    log.debug(
        "Querying capabilities from database: provenanceId [{}], operationKey [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        timestamp);
    List<ExprCapability> capabilities =
        capabilityMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ExprCapabilityDO entities to domain models, field keys: {}",
        capabilities.size(),
        capabilities.stream().map(ExprCapability::fieldKey).toList());
    return capabilities;
  }

  /**
   * Loads expression render rules for a specific provenance and operation.
   *
   * @param provenanceId the provenance ID
   * @param operationKey the normalized operation key
   * @param timestamp the query timestamp
   * @return list of expression render rules
   */
  private List<ExprRenderRule> loadRenderRules(
      Long provenanceId, String operationKey, Instant timestamp) {
    log.debug(
        "Querying render rules from database: provenanceId [{}], operationKey [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        timestamp);
    List<ExprRenderRule> renderRules =
        renderRuleMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ExprRenderRuleDO entities to domain models for {} unique fields",
        renderRules.size(),
        renderRules.stream().map(ExprRenderRule::fieldKey).distinct().count());
    return renderRules;
  }

  /**
   * Loads API parameter mappings for a specific provenance, operation, and endpoint.
   *
   * @param provenanceId the provenance ID
   * @param operationKey the normalized operation key
   * @param normalizedEndpoint the normalized endpoint name (nullable for endpoint-agnostic queries)
   * @param timestamp the query timestamp
   * @return list of API parameter mappings
   */
  private List<ApiParamMapping> loadApiParamMappings(
      Long provenanceId, String operationKey, String normalizedEndpoint, Instant timestamp) {
    log.debug(
        "Querying API parameter mappings from database: provenanceId [{}], operationKey [{}], endpoint [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        normalizedEndpoint,
        timestamp);
    List<ApiParamMapping> apiParams =
        apiParamMapMapper
            .selectActiveByTask(provenanceId, operationKey, normalizedEndpoint, timestamp)
            .stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ApiParamMapDO entities to domain models, standard keys: {}",
        apiParams.size(),
        apiParams.stream().map(ApiParamMapping::stdKey).toList());
    return apiParams;
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
   * @throws ProvenanceNotFoundException if provenance code not found
   */
  private Long resolveProvenanceId(ProvenanceCode provenanceCode) {
    String code = provenanceCode.getCode();
    log.debug("Querying provenance ID for code [{}] from database", code);
    return provenanceMapper
        .selectByCode(code)
        .map(
            entity -> {
              log.debug("Resolved provenance code [{}] to ID [{}]", code, entity.getId());
              return entity.getId();
            })
        .orElseThrow(
            () -> {
              log.warn("Provenance code not found: {}", code);
              return new ProvenanceNotFoundException("Provenance code not found: " + code);
            });
  }
}
