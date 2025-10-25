package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.*;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import com.patra.registry.domain.support.RegistryKeyStandardizer;
import com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.provenance.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-based read-side repository for provenance configuration aggregates.
 *
 * <p>Applies the precedence hierarchy of operation scope over source scope, falling back to
 * source-level defaults when operation-specific slices are absent.
 *
 * <p>All scope keys are normalized consistently to avoid string literal drift across components.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

  private final RegProvenanceMapper provenanceMapper;
  private final RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
  private final RegProvPaginationCfgMapper paginationCfgMapper;
  private final RegProvHttpCfgMapper httpCfgMapper;
  private final RegProvBatchingCfgMapper batchingCfgMapper;
  private final RegProvRetryCfgMapper retryCfgMapper;
  private final RegProvRateLimitCfgMapper rateLimitCfgMapper;
  private final ProvenanceEntityConverter converter;

  /**
   * Retrieves provenance by business code.
   *
   * @param provenanceCode the provenance code to query
   * @return optional provenance domain object
   */
  @Override
  public Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode) {
    log.debug("Finding provenance by code: {}", provenanceCode.getCode());
    return provenanceMapper.selectByCode(provenanceCode.getCode()).map(converter::toDomain);
  }

  /**
   * Lists all active provenances.
   *
   * @return list of active provenance domain objects
   */
  @Override
  public List<Provenance> findAllProvenances() {
    log.debug("Loading all active provenances");
    List<Provenance> provenances =
        provenanceMapper.selectAllActive().stream().map(converter::toDomain).toList();
    log.debug("Loaded {} active provenances", provenances.size());
    return provenances;
  }

  /**
   * Finds active window offset configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional window offset configuration
   */
  @Override
  public Optional<WindowOffsetConfig> findActiveWindowOffset(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "window offset",
        windowOffsetCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Finds active pagination configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional pagination configuration
   */
  @Override
  public Optional<PaginationConfig> findActivePagination(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "pagination",
        paginationCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Finds active HTTP configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional HTTP configuration
   */
  @Override
  public Optional<HttpConfig> findActiveHttpConfig(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "HTTP",
        httpCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Finds active batching configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional batching configuration
   */
  @Override
  public Optional<BatchingConfig> findActiveBatching(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "batching",
        batchingCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Finds active retry configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional retry configuration
   */
  @Override
  public Optional<RetryConfig> findActiveRetry(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "retry",
        retryCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Finds active rate limit configuration for a provenance and operation.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional rate limit configuration
   */
  @Override
  public Optional<RateLimitConfig> findActiveRateLimit(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "rate limit",
        rateLimitCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * Loads complete provenance configuration aggregate for a specific operation. Assembles all
   * configuration slices (window, pagination, HTTP, batching, retry, rate limit) into a single
   * aggregate, applying operation-specific overrides where available.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @return optional complete configuration aggregate
   */
  @Override
  public Optional<ProvenanceConfiguration> loadConfiguration(
      Long provenanceId, String operationType, Instant at) {
    log.info(
        "Loading provenance configuration for provenanceId [{}] with operationType [{}]",
        provenanceId,
        operationType);

    Optional<Provenance> provenanceOpt = findProvenanceById(provenanceId);
    if (provenanceOpt.isEmpty()) {
      log.warn("Provenance not found for provenanceId [{}]", provenanceId);
      return Optional.empty();
    }

    Instant timestamp = atOrNow(at);
    Provenance provenance = provenanceOpt.get();
    ProvenanceConfiguration configuration =
        assembleConfiguration(provenance, operationType, timestamp);

    log.info(
        "Successfully loaded configuration for provenance [{}] with {} configs applied",
        provenance.code(),
        countNonNullConfigs(configuration));
    return Optional.of(configuration);
  }

  /**
   * Assembles complete configuration aggregate from individual config slices.
   *
   * @param provenance the provenance domain object
   * @param operationType the operation type key
   * @param timestamp the effective timestamp
   * @return assembled configuration aggregate
   */
  private ProvenanceConfiguration assembleConfiguration(
      Provenance provenance, String operationType, Instant timestamp) {
    Long provenanceId = provenance.id();
    return new ProvenanceConfiguration(
        provenance,
        findActiveWindowOffset(provenanceId, operationType, timestamp).orElse(null),
        findActivePagination(provenanceId, operationType, timestamp).orElse(null),
        findActiveHttpConfig(provenanceId, operationType, timestamp).orElse(null),
        findActiveBatching(provenanceId, operationType, timestamp).orElse(null),
        findActiveRetry(provenanceId, operationType, timestamp).orElse(null),
        findActiveRateLimit(provenanceId, operationType, timestamp).orElse(null));
  }

  /**
   * Counts non-null configuration components.
   *
   * @param configuration the configuration aggregate
   * @return count of non-null configs
   */
  private int countNonNullConfigs(ProvenanceConfiguration configuration) {
    return (int)
        Stream.of(
                configuration.windowOffset(),
                configuration.pagination(),
                configuration.http(),
                configuration.batching(),
                configuration.retry(),
                configuration.rateLimit())
            .filter(Objects::nonNull)
            .count();
  }

  /**
   * Generic helper to find active configuration with temporal slicing and operation scope merging.
   *
   * @param provenanceId the provenance identifier
   * @param operationType the operation type key
   * @param at the query timestamp (null for current time)
   * @param configName the configuration type name (for logging)
   * @param selector the mapper selection function
   * @param converter the DO to domain conversion function
   * @param <DO> the database object type
   * @param <DOMAIN> the domain object type
   * @return optional domain configuration object
   */
  private <DO, DOMAIN> Optional<DOMAIN> findActiveConfig(
      Long provenanceId,
      String operationType,
      Instant at,
      String configName,
      ConfigSelector<DO> selector,
      java.util.function.Function<DO, DOMAIN> converter) {
    Instant timestamp = atOrNow(at);
    String operationKey = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);
    log.debug(
        "Finding {} config for provenanceId [{}] with operationKey [{}]",
        configName,
        provenanceId,
        operationKey);
    return selector.select(provenanceId, operationKey, timestamp).map(converter);
  }

  /**
   * Finds provenance domain object by ID.
   *
   * @param provenanceId the provenance identifier
   * @return optional provenance domain object
   */
  private Optional<Provenance> findProvenanceById(Long provenanceId) {
    if (provenanceId == null) {
      log.debug("Provenance ID is null");
      return Optional.empty();
    }
    RegProvenanceDO entity = provenanceMapper.selectById(provenanceId);
    if (entity == null) {
      log.debug("Provenance entity not found for id [{}]", provenanceId);
      return Optional.empty();
    }
    return Optional.of(converter.toDomain(entity));
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
   * Functional interface for configuration selector operations.
   *
   * @param <DO> the database object type
   */
  @FunctionalInterface
  private interface ConfigSelector<DO> {
    /**
     * Selects active configuration from database.
     *
     * @param provenanceId provenance identifier
     * @param operationKey normalized operation key
     * @param timestamp effective timestamp
     * @return optional database object
     */
    Optional<DO> select(Long provenanceId, String operationKey, Instant timestamp);
  }
}
