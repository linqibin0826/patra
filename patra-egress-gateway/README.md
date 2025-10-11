# patra-egress-gateway

## Overview

`patra-egress-gateway` (the southbound gateway) is the Papertrace microservice responsible for centrally orchestrating every outbound call to external systems. It offers upstream services a uniform façade for interacting with medical literature data sources (PubMed/PMC/Crossref, etc.), object storage services (OSS/MinIO/S3, etc.), email providers, SMS/OTP gateways, and more.

## Core Responsibilities

1. **Proxy external requests**: Accept request payloads and authentication material from upstream systems and relay them transparently.
2. **Provide resilience**: Offer shared capabilities such as rate limiting, retries, circuit breaking, and timeout management.
3. **Normalize responses**: Wrap provider-specific responses in a consistent semantic model.
4. **Manage configuration**: Maintain system-wide defaults while allowing bounded overrides per consumer.
5. **Enable observability**: Capture detailed logs, metrics, and traces for every outbound invocation.

## Out of Scope

- Performing business-level data transformation.
- Embedding domain-specific decision logic.
- Persisting business data.
- Parsing or interpreting provider response payloads.

## Module Layout

```
patra-egress-gateway/
├── patra-egress-gateway-api/          # Error codes and external DTOs
├── patra-egress-gateway-adapter/      # Inbound adapters (REST)
├── patra-egress-gateway-app/          # Use case orchestration
├── patra-egress-gateway-domain/       # Aggregates, entities, domain ports
├── patra-egress-gateway-infra/        # Outbound implementations
└── patra-egress-gateway-boot/         # Spring Boot application
```

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Resilience4j**: Resilience primitives (rate limiters, retries, circuit breakers)
- **Spring RestClient**: Declarative HTTP client
- **Micrometer**: Metrics export
- **Lombok**: Boilerplate reduction
- **Hutool**: Utility toolkit

## Getting Started

### Build

```bash
mvn -q -DskipTests compile

# Package the module
mvn clean package -DskipTests
```

### Run

```bash
mvn -pl patra-egress-gateway/patra-egress-gateway-boot spring-boot:run
```

### Configuration

Key settings live in `application.yaml`:

```yaml
patra:
  egress:
    resilience:
      max:
        timeout: 60s
        maxRetries: 5
        rateLimit: 1000
      default:
        timeout: 30s
        maxRetries: 3
        rateLimit: 100
```

## Usage Examples

### Call the PubMed API

```java
ExternalCallRequest request = ExternalCallRequest.builder()
    .url("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi")
    .method("GET")
    .headers(Map.of("User-Agent", "Papertrace/0.1.0"))
    .resilienceConfig(ResilienceConfigDTO.builder()
        .timeout(10)
        .maxRetries(3)
        .rateLimit(10)
        .build())
    .build();

ExternalCallResponse response = egressClient.call(request);
```

### Call the OSS API

```java
ExternalCallRequest request = ExternalCallRequest.builder()
    .url("https://bucket.oss-cn-hangzhou.aliyuncs.com/file.pdf")
    .method("PUT")
    .headers(Map.of(
        "Authorization", "OSS " + accessKeyId + ":" + signature,
        "Content-Type", "application/pdf"
    ))
    .body(fileContent)
    .resilienceConfig(ResilienceConfigDTO.builder()
        .timeout(60)
        .maxRetries(5)
        .build())
    .build();

ExternalCallResponse response = egressClient.call(request);
```

## Further Reading

For additional context, consult:

- [Requirements](.kiro/specs/patra-egress-gateway/requirements.md)
- [Design](.kiro/specs/patra-egress-gateway/design.md)
- [Task Tracker](.kiro/specs/patra-egress-gateway/tasks.md)

## Versioning

Current version: `0.1.0-SNAPSHOT`

## Authors

@linqibin

## License

Copyright © 2025 Papertrace
