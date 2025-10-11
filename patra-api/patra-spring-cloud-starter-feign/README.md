# patra-spring-cloud-starter-feign

Starter for Spring Cloud OpenFeign that unifies remote error decoding and trace propagation.

## 1. Module Scope
- **Purpose** – Decode downstream `ProblemDetail`/JSON errors into a structured `RemoteCallException` so calling services see consistent error semantics.
- **Primary consumers** – Any Papertrace module that issues Feign requests (for example ingest, registry, gateway).
- **Boundaries** – Provides HTTP client cross-cutting behaviour only. Business-specific fallbacks and retries stay with the caller.

## 2. Capabilities
- **ProblemDetail decoding** – Supports `application/problem+json`, extracting `code`, `traceId`, and extension fields.
- **Tolerant mode** – Wraps non-ProblemDetail responses or parsing failures in `RemoteCallException` instead of bubbling raw Feign exceptions.
- **Trace propagation** – Injects trace headers on outbound calls and mirrors trace information back into decoded responses.
- **Observability hooks** – Captures parse latency, slow body reads, and trace extraction results.
- **Utility helpers** – `RemoteErrorHelper` exposes convenience methods such as `isNotFound` and `isRetryable`.

Use this README for configuration and extension guidance. For comparison with other starters, see the `patra-spring-boot-starter-*` module READMEs.

## 3. Package Layout and Dependencies
- Packages: `decoder` (error decoding), `observation`, `interceptor/tracing`, `util`.
- Depends on `patra-spring-boot-starter-core`, Spring Cloud OpenFeign, and Micrometer.

## 4. Getting Started
- Maven dependency:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- `ProblemDetailErrorDecoder` is enabled by default; disable via `patra.feign.problem.enabled=false`.
- Common configuration:
  ```yaml
  patra:
    feign:
      problem:
        enabled: true
        tolerant: true
        max-error-body-size: 65536
        include-stack-trace: false
        observation:
          enabled: true
          slow-parsing-threshold-ms: 150
  ```

## 5. Operations and Observability
- Metrics include decoding latency, tolerant-mode activations, and trace extraction success rate.
- Alert on slow parsing events (often caused by large payloads) and tune `max-error-body-size` accordingly.
- Upstream web and gateway layers must supply trace IDs for propagation to work end-to-end.

## 6. Testing Guidelines
- Use `@FeignClient` with MockWebServer to simulate downstream ProblemDetail, generic JSON, and plain-text responses.
- Verify tolerant-mode behaviour, error-code extraction, and trace header propagation.
- Combine with `RemoteErrorHelper` for orchestrating business fallbacks in tests.

## 7. Roadmap and Risks
| Item | Status | Notes / Risks |
|------|--------|---------------|
| Reactive client support | Planned | Requires WebClient + Reactor context integration. |
| Multi-protocol decoding | Planned | Extend tolerant handling to XML / Protobuf error bodies. |
| Finer metric labels | Planned | Capture downstream service + error code dimensions. |

Risks include downstream services omitting ProblemDetail payloads, excessively large bodies leading to memory pressure, or missing trace headers. Keep tolerant mode enabled and monitor the supplied metrics.

## 8. References
- Other starters: `patra-spring-boot-starter-core/README.md`, `patra-spring-boot-starter-web/README.md`, `patra-spring-boot-starter-mybatis/README.md`, `patra-spring-boot-starter-expr/README.md`
- Error handling standard: `docs/standards/platform-error-handling.md`
- Core starter overview: `patra-spring-boot-starter-core/README.md`
