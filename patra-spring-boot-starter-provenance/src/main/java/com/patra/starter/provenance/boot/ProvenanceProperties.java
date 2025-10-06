package com.patra.starter.provenance.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provenance starter configuration properties.
 *
 * <p>Expose source specific configuration that can be overridden in Nacos or
 * application.yml. Each nested properties object keeps optional fields nullable
 * so that callers may decide which knobs to configure.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.provenance")
public class ProvenanceProperties {

    /**
     * Whether to enable provenance clients (default true).
     */
    private boolean enabled = true;

    /**
     * PubMed configuration namespace.
     */
    private SourceProperties pubmed = SourceProperties.withDefaults(
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
    );

    /**
     * Europe PMC configuration namespace.
     */
    private SourceProperties epmc = SourceProperties.withDefaults(
        "https://www.ebi.ac.uk/europepmc/webservices/rest"
    );

    /**
     * Properties shared by each provenance source.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class SourceProperties {

        private String baseUrl;
        private HttpConfigProperties http = new HttpConfigProperties();
        private PaginationProperties pagination = new PaginationProperties();
        private WindowOffsetProperties windowOffset = new WindowOffsetProperties();
        private BatchingProperties batching = new BatchingProperties();
        private RetryProperties retry = new RetryProperties();
        private RateLimitProperties rateLimit = new RateLimitProperties();

        private static SourceProperties withDefaults(String baseUrl) {
            SourceProperties properties = new SourceProperties();
            properties.setBaseUrl(baseUrl);
            return properties;
        }
    }

    /**
     * HTTP configuration overrides.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class HttpConfigProperties {
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private Integer timeoutConnectMillis = 5_000;
        private Integer timeoutReadMillis = 30_000;
        private Integer timeoutTotalMillis = 60_000;
    }

    /**
     * Pagination defaults.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class PaginationProperties {
        private Integer pageSizeValue;
        private Integer maxPagesPerExecution;
    }

    /**
     * Window offset defaults for incremental harvest.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class WindowOffsetProperties {
        private String windowModeCode;
        private Integer windowSizeValue;
        private String windowSizeUnitCode;
        private Integer lookbackValue;
        private String lookbackUnitCode;
        private Integer overlapValue;
        private String overlapUnitCode;
        private String offsetTypeCode;
        private Integer maxIdsPerWindow;
    }

    /**
     * Batch handling defaults.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class BatchingProperties {
        private Integer detailFetchBatchSize;
        private Integer maxIdsPerRequest;
    }

    /**
     * Retry policy defaults (delegated to gateway but overridable per client).
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class RetryProperties {
        private Integer maxRetryTimes;
        private Integer initialDelayMillis;
    }

    /**
     * Rate limit hints passed to the gateway.
     *
     * @author linqibin
     * @since 0.1.0
     */
    @Data
    public static class RateLimitProperties {
        private Integer maxConcurrentRequests;
        private Integer perCredentialQpsLimit;
    }
}
