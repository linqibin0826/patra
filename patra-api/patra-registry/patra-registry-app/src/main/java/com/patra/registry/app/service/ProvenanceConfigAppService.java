package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ProvenanceQueryAssembler;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for querying provenance configuration.
 *
 * <p>Orchestrates read operations on provenance metadata and effective configuration by assembling
 * domain objects into query DTOs for consumption by external clients.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceConfigAppService {

  private final ProvenanceConfigRepository repository;
  private final ProvenanceQueryAssembler assembler;

  /**
   * Lists all available provenances.
   *
   * @return list of provenance query DTOs
   */
  public List<ProvenanceQuery> listProvenances() {
    return repository.findAllProvenances().stream().map(assembler::toQuery).toList();
  }

  /**
   * Finds a specific provenance by its code.
   *
   * @param provenanceCode the provenance code to look up
   * @return optional containing the provenance query DTO if found
   */
  public Optional<ProvenanceQuery> findProvenance(ProvenanceCode provenanceCode) {
    return repository.findProvenanceByCode(provenanceCode).map(assembler::toQuery);
  }

  /**
   * Loads the aggregated configuration for a provenance under a specific operation type.
   *
   * <p>Retrieves the effective configuration by resolving temporal slices at the given instant and
   * assembling all dimension configs (HTTP, pagination, retry, etc.) into a unified view.
   *
   * @param provenanceCode the provenance code to load configuration for
   * @param operationType the operation type discriminator (e.g., HARVEST/UPDATE); {@code null}
   *     means ALL
   * @param at the instant to query effective configs; {@code null} defaults to current time
   * @return optional containing the configuration query DTO if provenance exists
   */
  public Optional<ProvenanceConfigQuery> loadConfiguration(
      ProvenanceCode provenanceCode, String operationType, Instant at) {
    log.debug(
        "Loading configuration for provenance [{}] with operationType [{}]",
        provenanceCode.getCode(),
        operationType);

    Optional<Provenance> provenanceOpt = repository.findProvenanceByCode(provenanceCode);
    if (provenanceOpt.isEmpty()) {
      log.warn("Provenance not found for code [{}]", provenanceCode.getCode());
      return Optional.empty();
    }

    Provenance provenance = provenanceOpt.get();
    Optional<ProvenanceConfiguration> configuration =
        repository.loadConfiguration(
            provenance.id(), operationType, at != null ? at : Instant.now());

    return configuration.map(assembler::toQuery);
  }
}
