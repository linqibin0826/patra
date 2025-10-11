package com.patra.ingest.domain.messaging;

import java.util.Locale;

/**
 * Utility for generating consumer group names using the pattern {@code svc-{service}-{consumer}-cg}.
 */
public final class ConsumerGroups {
    private ConsumerGroups() {}

    /**
     * Build a consumer group name in lowercase kebab-case.
     * @param service microservice name (e.g., ingest/registry)
     * @param consumer responsibility identifier (e.g., relay/task-ready)
     */
    public static String svc(String service, String consumer) {
        String s = normalize(service);
        String c = normalize(consumer);
        return "svc-" + s + '-' + c + "-cg";
    }

    private static String normalize(String s) {
        if (s == null) return "unknown";
        return s.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
