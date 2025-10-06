package com.patra.starter.provenance.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Provenance metrics recorder.
 * Records API call duration, success count, and failure count.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ProvenanceMetrics {

    private final MeterRegistry meterRegistry;

    public ProvenanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record API call with metrics
     *
     * @param provenanceCode data source code (e.g., "PUBMED", "EPMC")
     * @param apiName        API name (e.g., "esearch", "efetch")
     * @param supplier       API call logic
     * @param <T>           result type
     * @return API call result
     */
    public <T> T recordApiCall(String provenanceCode, String apiName, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            T result = supplier.get();

            // Record success
            sample.stop(Timer.builder("provenance.client.api.duration")
                .tag("provenanceCode", provenanceCode)
                .tag("apiName", apiName)
                .tag("status", "success")
                .register(meterRegistry));

            Counter.builder("provenance.client.api.success")
                .tag("provenanceCode", provenanceCode)
                .tag("apiName", apiName)
                .register(meterRegistry)
                .increment();

            return result;
        } catch (Exception e) {
            // Record failure
            sample.stop(Timer.builder("provenance.client.api.duration")
                .tag("provenanceCode", provenanceCode)
                .tag("apiName", apiName)
                .tag("status", "failure")
                .register(meterRegistry));

            Counter.builder("provenance.client.api.failure")
                .tag("provenanceCode", provenanceCode)
                .tag("apiName", apiName)
                .tag("errorType", e.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();

            throw e;
        }
    }
}
