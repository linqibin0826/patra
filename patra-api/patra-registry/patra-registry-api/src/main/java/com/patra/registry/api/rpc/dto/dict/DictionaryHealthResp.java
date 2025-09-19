package com.patra.registry.api.rpc.dto.dict;

import java.util.Collections;
import java.util.List;

/**
 * Response DTO describing dictionary health metrics for subsystem monitoring.
 */
public record DictionaryHealthResp(
        int totalTypes,
        int totalItems,
        int enabledItems,
        List<String> typesWithoutDefault,
        List<String> typesWithMultipleDefaults
) {
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

    public boolean isHealthy() {
        return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
    }
}
