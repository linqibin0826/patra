package com.patra.registry.api.rpc.dto.dict;

import java.util.Collections;
import java.util.List;

/**
 * Response DTO describing dictionary health metrics for subsystem monitoring.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>totalTypes - total dictionary types registered</li>
 *   <li>totalItems - total dictionary items across all types</li>
 *   <li>enabledItems - enabled dictionary items across all types</li>
 *   <li>typesWithoutDefault - type codes lacking a default item</li>
 *   <li>typesWithMultipleDefaults - type codes having multiple default items</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthResp(
        int totalTypes,
        int totalItems,
        int enabledItems,
        List<String> typesWithoutDefault,
        List<String> typesWithMultipleDefaults
) {
    /**
     * Canonical constructor enforcing invariants and defensive copies.
     *
     * @param totalTypes total dictionary types registered
     * @param totalItems total dictionary items across all types
     * @param enabledItems enabled dictionary items across all types
     * @param typesWithoutDefault type codes lacking a default item
     * @param typesWithMultipleDefaults type codes with multiple defaults configured
     */
    public DictionaryHealthResp {
        if (totalTypes < 0) {
            throw new IllegalArgumentException("Total types count cannot be negative");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items count cannot be negative");
        }
        if (enabledItems < 0) {
            throw new IllegalArgumentException("Enabled items count cannot be negative");
        }
        typesWithoutDefault = typesWithoutDefault != null
                ? List.copyOf(typesWithoutDefault)
                : Collections.emptyList();
        typesWithMultipleDefaults = typesWithMultipleDefaults != null
                ? List.copyOf(typesWithMultipleDefaults)
                : Collections.emptyList();
    }

    /**
     * Indicates whether dictionary configuration is healthy (no missing or duplicate defaults).
     *
     * @return {@code true} when no anomalies are detected
     */
    public boolean isHealthy() {
        return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
    }
}
