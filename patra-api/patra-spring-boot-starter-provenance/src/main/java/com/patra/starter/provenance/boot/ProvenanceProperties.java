package com.patra.starter.provenance.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Provenance starter configuration properties
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.provenance")
public class ProvenanceProperties {

    /**
     * Enable Provenance Client (default true)
     */
    private boolean enabled = true;

    /**
     * PubMed configuration
     */
    private PubMedProperties pubmed = new PubMedProperties();

    /**
     * EPMC configuration
     */
    private EPMCProperties epmc = new EPMCProperties();

    @Data
    public static class PubMedProperties {
        /**
         * PubMed base URL (default https://eutils.ncbi.nlm.nih.gov/entrez/eutils)
         */
        private String baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils";

        /**
         * HTTP configuration
         */
        private HttpConfigProperties http = new HttpConfigProperties();
    }

    @Data
    public static class EPMCProperties {
        /**
         * EPMC base URL (default https://www.ebi.ac.uk/europepmc/webservices/rest)
         */
        private String baseUrl = "https://www.ebi.ac.uk/europepmc/webservices/rest";

        /**
         * HTTP configuration
         */
        private HttpConfigProperties http = new HttpConfigProperties();
    }

    @Data
    public static class HttpConfigProperties {
        /**
         * Default headers
         */
        private Map<String, String> defaultHeaders = new HashMap<>();

        /**
         * Connection timeout (milliseconds, default 5000)
         */
        private Integer timeoutConnectMillis = 5000;

        /**
         * Read timeout (milliseconds, default 30000)
         */
        private Integer timeoutReadMillis = 30000;

        /**
         * Total timeout (milliseconds, default 60000)
         */
        private Integer timeoutTotalMillis = 60000;
    }
}
