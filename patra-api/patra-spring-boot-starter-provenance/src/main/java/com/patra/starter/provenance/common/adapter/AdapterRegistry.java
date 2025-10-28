package com.patra.starter.provenance.common.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry responsible for discovering and querying {@link DataSourceAdapter} implementations.
 *
 * <p>The registry is populated via Spring's component scanning and provides fast lookup by
 * provenance code.
 */
@Slf4j
public class AdapterRegistry {

  private final Map<String, List<DataSourceAdapter>> adapters = new ConcurrentHashMap<>();

  /**
   * Creates the registry and registers all discovered adapters.
   *
   * @param discoveredAdapters adapters provided by Spring
   */
  public AdapterRegistry(List<DataSourceAdapter> discoveredAdapters) {
    List<DataSourceAdapter> safeAdapters =
        discoveredAdapters == null ? List.of() : List.copyOf(discoveredAdapters);
    safeAdapters.forEach(this::register);
  }

  /**
   * Tests whether an adapter exists for the requested provenance code.
   *
   * @param provenanceCode provenance identifier such as {@code pubmed}
   * @return true when a matching adapter is available
   */
  public boolean supports(String provenanceCode) {
    return findAdapter(provenanceCode).isPresent();
  }

  /**
   * Returns the adapter matching the requested provenance code.
   *
   * @param provenanceCode provenance identifier such as {@code pubmed}
   * @return matching adapter
   * @throws IllegalArgumentException if no adapter exists for the provenance
   */
  public DataSourceAdapter getAdapter(String provenanceCode) {
    return findAdapter(provenanceCode)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No adapter found for provenance=%s".formatted(provenanceCode)));
  }

  private Optional<DataSourceAdapter> findAdapter(String provenanceCode) {
    if (provenanceCode == null || provenanceCode.isBlank()) {
      return Optional.empty();
    }
    String normalizedCode = normalize(provenanceCode);
    List<DataSourceAdapter> candidates = adapters.get(normalizedCode);
    if (candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }
    // Return the first adapter for this provenance.
    // Each provenance should have exactly one adapter implementation.
    return candidates.stream().findFirst();
  }

  private void register(DataSourceAdapter adapter) {
    if (adapter == null) {
      return;
    }
    String normalizedCode = normalize(adapter.getProvenanceCode());
    adapters.compute(
        normalizedCode,
        (code, list) -> {
          if (list == null || list.isEmpty()) {
            return List.of(adapter);
          }
          if (list.stream().anyMatch(existing -> existing.getClass().equals(adapter.getClass()))) {
            log.warn("Duplicate adapter registration ignored: {}", adapter.getClass().getName());
            return list;
          }
          return createExpandedList(list, adapter);
        });
  }

  private List<DataSourceAdapter> createExpandedList(
      List<DataSourceAdapter> existing, DataSourceAdapter adapter) {
    List<DataSourceAdapter> combined = new ArrayList<>(existing.size() + 1);
    combined.addAll(existing);
    combined.add(adapter);
    return List.copyOf(combined);
  }

  private String normalize(String provenanceCode) {
    return provenanceCode == null ? "" : provenanceCode.trim().toLowerCase(Locale.ROOT);
  }
}
