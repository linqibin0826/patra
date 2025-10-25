package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ProvenanceQueryAssembler;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates provenance configuration query operations.
 *
 * <p>Coordinates read operations on provenance metadata and effective configuration by delegating
 * to domain repositories and converting results to query DTOs for external clients.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceConfigOrchestrator {

  private final ProvenanceConfigRepository repository;
  private final ProvenanceQueryAssembler assembler;

  /**
   * Lists all available provenances.
   *
   * @return list of provenance query DTOs
   */
  public List<ProvenanceQuery> listProvenances() {
    log.info("Listing all available provenances");
    List<ProvenanceQuery> provenances =
        repository.findAllProvenances().stream().map(assembler::toQuery).toList();
    log.info("Successfully retrieved {} provenances", provenances.size());
    return provenances;
  }

  /**
   * Finds a specific provenance by its code.
   *
   * @param provenanceCode the provenance code to look up
   * @return optional containing the provenance query DTO if found
   */
  public Optional<ProvenanceQuery> findProvenance(ProvenanceCode provenanceCode) {
    log.info("Finding provenance by code [{}]", provenanceCode.getCode());
    Optional<ProvenanceQuery> result =
        repository.findProvenanceByCode(provenanceCode).map(assembler::toQuery);

    if (result.isPresent()) {
      log.info("Successfully found provenance [{}]", provenanceCode.getCode());
    } else {
      log.warn("Provenance not found for code [{}]", provenanceCode.getCode());
    }

    return result;
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
    log.info(
        "Loading configuration for provenance [{}] with operationType [{}]",
        provenanceCode.getCode(),
        operationType);

    Instant effectiveTime = at != null ? at : Instant.now();
    Optional<ProvenanceConfigQuery> result =
        repository
            .findProvenanceByCode(provenanceCode)
            .flatMap(
                provenance ->
                    repository
                        .loadConfiguration(provenance.id(), operationType, effectiveTime)
                        .map(assembler::toQuery));

    if (result.isPresent()) {
      log.info(
          "Successfully loaded configuration for provenance [{}] with operationType [{}]",
          provenanceCode.getCode(),
          operationType);
    } else {
      log.warn(
          "Configuration not found for provenance [{}] with operationType [{}]",
          provenanceCode.getCode(),
          operationType);
    }

    return result;
  }
}
