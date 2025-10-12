# patra-spring-boot-starter-provenance

## Purpose
Provenance clients for PubMed and Europe PMC, backed by the Egress Gateway. Ships shared config, metrics, XML→JSON converter, and request helpers.

## Auto-Configuration
- `com.patra.starter.provenance.boot.ProvenanceAutoConfiguration`
  - Conditions: `EgressGatewayClient` on classpath and `patra.provenance.enabled=true` (default)
  - Falls back to `NoOp` clients when the gateway client is missing

## Beans and Features
- Shared
  - `GatewayRequestBuilder` to construct gateway requests
  - `DefaultConfigProvider` bound from `ProvenanceProperties`
  - `XmlToJsonConverter` for PubMed EFetch payloads
  - `provenanceObjectMapper` (preconfigured `ObjectMapper`)
  - `ProvenanceMetrics` (when Micrometer present)
- Clients
  - `PubMedClient` and `EPMCClient` (or their `NoOp` variants)

## Properties
```yaml
patra:
  provenance:
    enabled: true
    pubmed:
      base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
      http:
        timeout-connect-millis: 5000
        timeout-read-millis: 30000
        timeout-total-millis: 60000
    epmc:
      base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
      pagination:
        page-size-value: 25
        max-pages-per-execution: 10
```

## Usage Example
```java
@Service
class HarvestService {
  private final PubMedClient pubmed;
  HarvestService(PubMedClient pubmed) { this.pubmed = pubmed; }

  void run() {
    var req = new ESearchRequest("heart failure");
    var resp = pubmed.eSearch(req);
    // iterate IDs, use EFetch for details...
  }
}
```

## Notes
- Egress Gateway error/trace handling is provided by the Feign and Core starters.
