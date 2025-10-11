package com.patra.common.messaging;

import java.util.Locale;

/**
 * Contract representing the three-part naming convention for messaging channels
 * ({@code domain_resource_event}).
 * <p>Goals:</p>
 * <ul>
 *   <li>Provide a consistent naming scheme for publishers and consumers.</li>
 *   <li>Remain decoupled from specific messaging implementations.</li>
 *   <li>Live in {@code patra-common} so API modules stay free of messaging details.</li>
 * </ul>
 *
 * <p><b>Typical usage</b>:</p>
 * <ul>
 *   <li>Publishers implement this interface in API modules (for example,
 *       {@code PublishedChannels}).</li>
 *   <li>Consumers import the published contract to subscribe to channels.</li>
 *   <li>Domain modules may expose enums implementing the interface.</li>
 * </ul>
 */
public interface ChannelKey {

    /**
     * Business domain segment (for example, {@code ingest}, {@code registry}, {@code analysis}).
     * Prefer lowercase names aligned with service boundaries.
     */
    String domain();

    /**
     * Resource or aggregate segment (for example, {@code task}, {@code article}, {@code plan}).
     * Prefer lowercase names tied to core aggregates or business objects.
     */
    String resource();

    /**
     * Event segment (for example, {@code ready}, {@code created}, {@code updated}).
     * Prefer lowercase past-tense verbs that describe facts.
     */
    String event();

    /**
     * Builds a normalized uppercase channel key using underscores (e.g., {@code INGEST_TASK_READY}).
     */
    default String channel() {
        return domain().toUpperCase(Locale.ROOT) + "_"
                + resource().toUpperCase(Locale.ROOT) + "_"
                + event().toUpperCase(Locale.ROOT);
    }
}
