# patra-spring-boot-starter-provenance

Spring Boot Starter for literature data source client (PubMed, EPMC)

## Overview

`patra-spring-boot-starter-provenance` provides type-safe client interfaces for literature data sources (PubMed, EPMC). It encapsulates API parameters and response models, calling external services through the egress gateway (patra-egress-gateway).

**Package**: `com.patra.starter.provenance`

### Core Responsibilities

1. **API Encapsulation**: Independent client interfaces for each data source (PubMedClient, EPMCClient)
2. **Parameter Models**: Strongly-typed Request objects covering all API parameters (required and optional)
3. **Response Models**: Strongly typed Response objects exposing curated fields plus `raw()` access to the original payload
4. **Gateway Calling**: Internally calls egress gateway via EgressGatewayClient
5. **Configuration Management**: Supports 2-tier config priority (caller override > local config)
6. **Auto-Configuration**: Spring Boot auto-configuration, just add dependency to use

### Non-Responsibilities

- No parameter conversion (Expr → data source parameters handled by business layer)
- No business logic (e.g., literature deduplication, data validation)
- No over-abstraction (no unified data source interface)
- No automatic pagination (caller handles pagination logic)

## Dependencies

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-provenance</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Configuration

### application.yml

```yaml
patra:
  provenance:
    enabled: true
    pubmed:
      base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
      http:
        default-headers:
          User-Agent: Papertrace/0.1.0
        timeout-connect-millis: 5000
        timeout-read-millis: 30000
        timeout-total-millis: 60000
    epmc:
      base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
      http:
        default-headers:
          User-Agent: Papertrace/0.1.0
        timeout-connect-millis: 5000
        timeout-read-millis: 30000
        timeout-total-millis: 60000
```

## Usage

### Basic Usage (JSON-first, recommended)

```java
@Service
public class LiteratureService {

    @Autowired
    private PubMedClient pubMedClient;

    @Autowired
    private EPMCClient epmcClient;

    /**
     * PubMed ESearch - uses default JSON format (recommended)
     */
    public void searchPubMed() {
        // 1. Use simplified constructor (defaults to JSON format)
        ESearchRequest request = new ESearchRequest("pubmed", "cancer AND therapy");

        // 2. Call API (automatically uses JSON format, no XML conversion)
        ESearchResponse response = pubMedClient.esearch(request);

        // 3. Process response
        System.out.println("Total count: " + response.result().count());
        System.out.println("ID list: " + response.result().idList());
    }

    /**
     * PubMed EFetch - get article details (uses XML format)
     */
    public void fetchPubMedDetails(String ids) {
        // 1. Build request (defaults to XML format, since abstract type only supports XML)
        EFetchRequest request = new EFetchRequest("pubmed", ids);

        // 2. Call API (automatically performs XML → JSON conversion)
        EFetchResponse response = pubMedClient.efetch(request);

        // 3. Process response
        System.out.println("Articles: " + response.articles().size());
    }

    /**
     * EPMC Search - native JSON format
     */
    public void searchEPMC() {
        // 1. Build request
        SearchRequest request = new SearchRequest("cancer AND therapy");

        // 2. Call API (native JSON, no conversion needed)
        SearchResponse response = epmcClient.search(request);

        // 3. Process response
        System.out.println("Hit count: " + response.hitCount());
        System.out.println("Results: " + response.results().size());
    }
}
```

### With Configuration Override

```java
@Service
public class LiteratureService {

    @Autowired
    private PubMedClient pubMedClient;

    @Autowired
    private ProvenanceClient provenanceClient;  // From patra-registry-api

    public void searchWithCustomConfig() {
        // 1. Build request
        ESearchRequest request = new ESearchRequest("pubmed", "cancer AND therapy");

        // 2. Get config from registry (business layer responsibility)
        ProvenanceConfigResp configResp = provenanceClient.getConfig(ProvenanceCode.PUBMED);

        // 3. Convert to ProvenanceConfig (business layer responsibility)
        ProvenanceConfig config = convertToProvenanceConfig(configResp);

        // 4. Call API with config override
        ESearchResponse response = pubMedClient.esearch(request, config);

        // 5. Process response
        System.out.println("Total count: " + response.result().count());
    }

    private ProvenanceConfig convertToProvenanceConfig(ProvenanceConfigResp resp) {
        // Business layer handles conversion logic
        // ...
    }
}
```

## Key Features

### JSON-First Strategy

- **PubMed ESearch**: Defaults to JSON format (retmode="json"), 30-50% performance improvement
- **PubMed EFetch**: Smart format selection - XML for detailed data, JSON for ID lists
- **EPMC**: Native JSON support, no conversion needed

### Complete Parameter Coverage

ESearch and EFetch include all PubMed E-utilities parameters:

- Authentication: `apiKey`, `tool`, `email` (increase rate limit from 3/sec to 10/sec)
- Sorting/Filtering: `sort`, `datetype`, `mindate`, `maxdate`, `field`, `reldate`
- History/Session: `usehistory`, `webenv`, `queryKey`

### Conditional Assembly

- Gracefully handles missing dependencies (`@ConditionalOnClass`, `@Autowired(required=false)`)
- Noop implementations when EgressGatewayClient unavailable
- Optional metrics recording when Micrometer available

### Configuration Priority

1. **Caller-provided config** (highest priority): Pass ProvenanceConfig to API methods
2. **Local config** (fallback): Configuration from application.yml

## Architecture

### Module Structure

```
patra-spring-boot-starter-provenance/
├── pubmed/                     # PubMed data source
│   ├── PubMedClient.java
│   ├── PubMedClientImpl.java
│   ├── PubMedClientNoOpImpl.java
│   └── model/
│       ├── request/
│       │   ├── ESearchRequest.java
│       │   └── EFetchRequest.java
│       └── response/
│           ├── ESearchResponse.java
│           └── EFetchResponse.java
├── epmc/                       # EPMC data source
│   ├── EPMCClient.java
│   ├── EPMCClientImpl.java
│   ├── EPMCClientNoOpImpl.java
│   └── model/
│       ├── request/
│       │   └── SearchRequest.java
│       └── response/
│           └── SearchResponse.java
├── common/                     # Common components
│   ├── config/                 # Configuration
│   │   ├── ProvenanceConfig.java
│   │   ├── HttpConfig.java
│   │   └── DefaultConfigProvider.java
│   ├── gateway/                # Gateway calling
│   │   ├── ApiRequest.java
│   │   └── GatewayRequestBuilder.java
│   ├── converter/              # Format conversion
│   │   └── XmlToJsonConverter.java
│   ├── metrics/                # Performance metrics
│   │   └── ProvenanceMetrics.java
│   └── exception/              # Exception definitions
│       └── ProvenanceClientException.java
└── boot/                       # Auto-configuration
    ├── ProvenanceAutoConfiguration.java
    └── ProvenanceProperties.java
```

### Dependency Chain

```
Business Layer (patra-ingest)
  ↓ depends on
patra-spring-boot-starter-provenance
  ↓ depends on (compile)
patra-common (ProvenanceCode enum)
  ↓ optional dependency (runtime check)
patra-egress-gateway-api (EgressGatewayClient)
```

### Provided Beans
- `GatewayRequestBuilder`: Builds `ExternalCallRequestDTO` with URL/headers/resilience hints.
- `DefaultConfigProvider`: Supplies sanitized fallback configuration per data source.
- `XmlToJsonConverter`: Converts PubMed XML payloads to JSON structures when required.
- `provenanceObjectMapper` (`ObjectMapper`): Shared Jackson mapper configured for tolerant parsing.
- `ProvenanceMetrics` (conditional): Micrometer wrapper for latency and success-rate metrics.

## Performance Metrics

When Micrometer is available, the starter records:

- `provenance.client.api.duration`: API call duration (Timer)
- `provenance.client.api.success`: API call success count (Counter)
- `provenance.client.api.failure`: API call failure count (Counter)

Dimensions: `provenanceCode` (PUBMED/EPMC), `apiName` (esearch/efetch/search)

## Observability

### Logging Specification

All logs use `[PROVENANCE][LAYER]` prefix:

- `[PROVENANCE][CORE]`: Client layer logs
- `[PROVENANCE][INTERNAL]`: Internal implementation logs
- `[PROVENANCE][BOOT]`: Auto-configuration logs

Example:

```
INFO  [PROVENANCE][CORE] API call completed: provenance=PUBMED api=esearch duration=245ms
DEBUG [PROVENANCE][CORE] Using XML to JSON conversion for efetch
WARN  [PROVENANCE][BOOT] EgressGatewayClient not available, using no-op PubMedClient
ERROR [PROVENANCE][CORE] API call failed: provenance=PUBMED api=esearch error=TimeoutException
```

## Design Principles

1. **Single Responsibility**: Only handles API encapsulation and gateway calling
2. **Type Safety**: Uses strongly-typed Request and Response objects
3. **Independent Design**: Each data source has independent Client, no unified abstraction
4. **JSON-First**: Prioritizes JSON format, reduces XML conversion overhead (30-50% performance improvement)
5. **Flexible Configuration**: Supports 2-tier config priority (caller override > local config)
6. **Clear Boundaries**: Configuration conversion handled by business layer, Starter remains infrastructure-focused
7. **Easy to Use**: Spring Boot auto-configuration, just add dependency
8. **Easy to Extend**: Adding new data source only requires creating new Client and models

## Version

- **Current Version**: 0.1.0-SNAPSHOT
- **Spring Boot Version**: 3.2.4
- **Java Version**: 21

## Author

- linqibin

## License

Internal use only
