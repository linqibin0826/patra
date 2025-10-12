# patra-spring-cloud-starter-feign

## Purpose
Feign enhancements for cross-service calls: unified ProblemDetail error decoding, trace propagation, header injection, and optional metrics.

## Auto-Configuration
- `com.patra.starter.feign.runtime.PatraFeignAutoConfiguration`
  - Registers `PatraFeignRequestInterceptor` (propagates caller service header)
- `com.patra.starter.feign.error.config.FeignErrorAutoConfiguration`
  - Registers `ProblemDetailErrorDecoder`, `TraceIdRequestInterceptor`, and optional `FeignErrorObservationRecorder`

## Beans and Features
- `PatraFeignRequestInterceptor`
  - Adds `X-Service-Name` (configurable) from `spring.application.name` to outbound requests
- `ProblemDetailErrorDecoder`
  - Parses RFC7807 responses to exceptions; tolerant mode wraps non-ProblemDetail payloads
- `TraceIdRequestInterceptor`
  - Propagates trace headers using Core’s `TraceProvider` and `TracingProperties`
- `FeignErrorObservationRecorder`
  - Micrometer-backed recorder when registry present

## Properties
```yaml
patra:
  feign:
    enabled: true
    service-header: X-Service-Name
    max-error-body-size: 65536
    redact-keys: [token, password, secret, apiKey]
    problem:
      enabled: true
      tolerant: true
      max-error-body-size: 65536
      include-stack-trace: false
      observation:
        enabled: true
        slow-parsing-threshold-ms: 150
        log-slow-parsing: true
        slow-body-reading-threshold-ms: 80
        log-slow-body-reading: true
        log-tolerant-usage: true
```

## Usage Example
```java
@FeignClient(name = "registry", url = "http://registry.internal")
interface RegistryClient {
  @GetMapping("/_internal/provenances")
  List<Provenance> list();
}
```

## Notes
- Trace propagation uses Core starter’s `patra.tracing.header-names` and `TraceProvider`.
- Works in non-web contexts; does not depend on Servlet APIs.
