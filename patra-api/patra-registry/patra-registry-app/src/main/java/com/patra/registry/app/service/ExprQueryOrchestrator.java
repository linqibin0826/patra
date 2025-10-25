package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ExprQueryAssembler;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import com.patra.registry.domain.port.ExprRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates expression configuration query operations.
 *
 * <p>Coordinates retrieval of expression snapshots from the domain layer and conversion to query
 * DTOs for external consumption. Handles field dictionaries, capabilities, render rules, and API
 * parameter mappings.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExprQueryOrchestrator {

  private final ExprRepository exprRepository;
  private final ExprQueryAssembler assembler;

  /**
   * Loads the aggregated expression snapshot for a specific provenance.
   *
   * <p>Retrieves effective expression configuration by resolving temporal slices at the given
   * instant, filtered by operation type and endpoint name.
   *
   * @param provenanceCode the provenance code to load snapshot for
   * @param operationType the operation type discriminator (e.g., HARVEST/UPDATE); {@code null}
   *     means ALL
   * @param endpointName the endpoint name (e.g., SEARCH/DETAIL); {@code null} means all endpoints
   * @param at the instant to query effective configs; {@code null} defaults to current time
   * @return the expression snapshot query DTO
   */
  public ExprSnapshotQuery loadSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    ProvenanceCode code = ProvenanceCode.parse(provenanceCode);
    log.info(
        "Loading expression snapshot for provenance [{}] with operationType [{}] and endpoint [{}]",
        code.getCode(),
        operationType,
        endpointName);

    Instant effectiveTime = at != null ? at : Instant.now();
    log.debug(
        "Querying expression repository for provenance [{}] at effective time [{}]",
        code.getCode(),
        effectiveTime);

    ExprSnapshot domainSnapshot =
        exprRepository.loadSnapshot(code, operationType, endpointName, at);
    log.debug(
        "Retrieved domain snapshot for provenance [{}]: {} fields, {} capabilities, {} render rules, {} API parameter mappings",
        code.getCode(),
        domainSnapshot.fields().size(),
        domainSnapshot.capabilities().size(),
        domainSnapshot.renderRules().size(),
        domainSnapshot.apiParamMappings().size());

    log.debug("Assembling expression snapshot query DTO from domain snapshot");
    ExprSnapshotQuery snapshot = assembler.toQuery(domainSnapshot);

    log.info(
        "Successfully loaded expression snapshot for provenance [{}]: {} fields, {} capabilities, {} render rules, {} API parameter mappings",
        code.getCode(),
        snapshot.fields().size(),
        snapshot.capabilities().size(),
        snapshot.renderRules().size(),
        snapshot.apiParamMappings().size());

    return snapshot;
  }
}
