# Trace Context Troubleshooting Guide

**Version**: 1.0.0 | **Last Updated**: 2025-01-17 | **Status**: Phase 5 Complete (T061)

This guide helps developers and operations teams troubleshoot trace context propagation issues across the Papertrace microservices platform.

---

## Quick Diagnosis Checklist

Use this checklist to quickly identify common trace context issues:

- [ ] **Gateway Entry**: Is trace ID being generated at gateway? (Check `patra-gateway` logs for "generated new trace ID")
- [ ] **Header Propagation**: Are `X-Trace-Id` and `X-Correlation-Id` headers present in downstream requests?
- [ ] **MDC Populated**: Do logs show `[traceId=...]` and `[correlationId=...]` in the log pattern?
- [ ] **Async Operations**: For async operations, is `MdcTaskDecorator` configured in `@Async` methods?
- [ ] **Service Integration**: Has the service added `patra-spring-boot-starter-logging` dependency?
- [ ] **Logback Configuration**: Is the service using the enhanced `logback-spring.xml` with MDC pattern?

---

## Common Issues & Solutions

### Issue 1: Missing Trace ID in Logs

**Symptom**: Logs show `[traceId=N/A]` instead of actual trace ID

**Possible Causes**:

1. **Service missing logging starter dependency**

   **Solution**: Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.papertrace</groupId>
       <artifactId>patra-spring-boot-starter-logging</artifactId>
   </dependency>
   ```

2. **TraceContextFilter not registered**

   **Verification**:
   ```bash
   # Check if TraceContextFilter is registered
   curl http://localhost:8081/actuator/beans | grep -i "traceContext"
   ```

   **Solution**: Ensure `TraceContextAutoConfiguration` is loaded. Check `spring.factories`:
   ```properties
   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
     com.patra.starter.logging.autoconfigure.TraceContextAutoConfiguration
   ```

3. **External client (Postman, curl) not sending trace headers**

   **Verification**:
   ```bash
   # Test with trace headers
   curl -H "X-Trace-Id: test-trace-123" \
        -H "X-Correlation-Id: test-corr-456" \
        http://localhost:9528/patra-registry/provenance/pubmed
   ```

   **Expected**: Gateway should accept the trace ID and propagate it downstream.

   **Actual (if missing)**: Gateway will generate a new trace ID and log a WARN message.

---

### Issue 2: Trace ID Changes Between Services

**Symptom**: Gateway has `traceId=abc-123` but `patra-registry` has `traceId=def-456`

**Possible Causes**:

1. **Missing header propagation in HTTP client**

   **Verification**: Check if Feign/RestTemplate has `TraceContextInterceptor` registered

   **Solution**: For **Feign**:
   ```java
   @Bean
   public RequestInterceptor traceContextInterceptor(LogContextEnricher enricher) {
       return new TraceContextInterceptor(enricher);
   }
   ```

   For **RestTemplate**:
   ```java
   @Bean
   public RestTemplate restTemplate(LogContextEnricher enricher) {
       RestTemplate restTemplate = new RestTemplate();
       restTemplate.getInterceptors().add(new RestTemplateInterceptor(enricher));
       return restTemplate;
   }
   ```

2. **Service not extracting trace headers from request**

   **Verification**: Check if `TraceContextFilter` runs (should log at DEBUG level)

   **Solution**: Ensure filter order is correct (should be highest precedence):
   ```java
   @Bean
   public FilterRegistrationBean<TraceContextFilter> traceContextFilterRegistration(
       TraceContextFilter filter) {
       FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>(filter);
       registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
       return registration;
   }
   ```

---

### Issue 3: Correlation ID Lost in Async Operations

**Symptom**: Parent thread has `correlationId=batch-001` but async thread has `correlationId=N/A`

**Possible Causes**:

1. **MdcTaskDecorator not configured for `@Async` methods**

   **Verification**: Check `AsyncAutoConfiguration` is loaded

   **Solution**: Ensure async executor uses `MdcTaskDecorator`:
   ```java
   @Configuration
   @EnableAsync
   public class AsyncAutoConfiguration implements AsyncConfigurer {

       @Override
       public Executor getAsyncExecutor() {
           ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
           executor.setTaskDecorator(new MdcTaskDecorator());  // Critical!
           executor.initialize();
           return executor;
       }
   }
   ```

2. **Custom `@Async` executor not using `MdcTaskDecorator`**

   **Solution**: If you have custom async executors, explicitly set task decorator:
   ```java
   @Bean(name = "customAsyncExecutor")
   public Executor customAsyncExecutor() {
       ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
       executor.setTaskDecorator(new MdcTaskDecorator());  // Add this!
       executor.setCorePoolSize(10);
       executor.setMaxPoolSize(20);
       executor.initialize();
       return executor;
   }
   ```

3. **RocketMQ/XXL-Job async operations missing trace context setup**

   **Solution**: Use decorators for message listeners and scheduled tasks:

   **RocketMQ**:
   ```java
   @RocketMQMessageListener(
       topic = "data-ingest",
       consumerGroup = "ingest-consumer-group"
   )
   public class DataIngestListener implements RocketMQListener<String> {

       @Override
       public void onMessage(String message) {
           // Extract trace context from message headers
           MessageExt messageExt = (MessageExt) message;
           String traceId = messageExt.getUserProperty("X-Trace-Id");
           String correlationId = messageExt.getUserProperty("X-Correlation-Id");

           // Set MDC before processing
           MDC.put("traceId", traceId);
           MDC.put("correlationId", correlationId);

           try {
               // Process message
           } finally {
               MDC.clear();  // Cleanup
           }
       }
   }
   ```

   **XXL-Job**:
   ```java
   @XxlJob("batchIngestJob")
   public void batchIngest() {
       // Use XxlJobTraceContextDecorator to set trace context
       XxlJobTraceContextDecorator.execute(() -> {
           // Job logic with trace context
       });
   }
   ```

---

### Issue 4: Trace Context Leakage Between Requests

**Symptom**: Request A's trace ID appears in Request B's logs (thread pool reuse)

**Root Cause**: MDC not cleared after request completes

**Solution**: Ensure cleanup in filters/interceptors:

**Servlet Filter** (TraceContextFilter):
```java
@Override
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    try {
        // Set trace context
        traceContextHolder.setContext(context);
        logContextEnricher.enrich(context);

        chain.doFilter(request, response);
    } finally {
        // CRITICAL: Always cleanup
        traceContextHolder.clearContext();
        logContextEnricher.clear();
    }
}
```

**WebFlux GlobalFilter** (Gateway):
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // Set trace context
    return chain.filter(exchange)
        .doFinally(signalType -> {
            // CRITICAL: Cleanup on completion
            traceContextHolder.clearContext();
            logContextEnricher.clear();
        });
}
```

---

### Issue 5: Spring Cloud Gateway Not Propagating Trace Headers

**Symptom**: Gateway logs show trace ID, but downstream services don't receive headers

**Possible Causes**:

1. **TraceContextGlobalFilter not registered**

   **Verification**:
   ```bash
   # Check gateway logs for initialization message
   grep "Initialized TraceContextGlobalFilter" logs/patra-gateway.log
   ```

   **Solution**: Ensure `TraceContextGlobalFilter` bean is created in gateway autoconfiguration.

2. **Filter order too low (runs after routing)**

   **Solution**: Set `Ordered.HIGHEST_PRECEDENCE` to run before routing:
   ```java
   @Override
   public int getOrder() {
       return Ordered.HIGHEST_PRECEDENCE;  // Must be -100
   }
   ```

3. **Request mutation not working**

   **Verification**: Log request headers in filter:
   ```java
   log.debug("Outbound headers: {}", mutatedRequest.getHeaders());
   ```

   **Expected**: Should see `X-Trace-Id`, `X-Span-Id`, `X-Correlation-Id` headers.

---

### Issue 6: Inconsistent Trace IDs Across Service Mesh

**Symptom**: Service A has `traceId=123`, Service B has `traceId=456`, Service C has `traceId=789` for the same request

**Diagnostic Steps**:

1. **Verify gateway entry point**:
   ```bash
   # Search gateway logs for the original request
   grep "Gateway trace context extracted" logs/patra-gateway.log | grep "generated=true"
   ```

   Expected: Only ONE trace ID generated at gateway entry point.

2. **Trace the propagation chain**:
   ```bash
   # Follow trace ID through services
   grep "traceId=abc-123" logs/*.log | sort
   ```

   Expected: Same trace ID should appear in all service logs.

3. **Check Feign/RestTemplate configuration**:
   ```bash
   # Verify interceptor is registered
   curl http://localhost:8081/actuator/beans | jq '.contexts.application.beans | to_entries[] | select(.value.type | contains("Interceptor"))'
   ```

**Solution**: Ensure **all** HTTP clients use trace context interceptors (see Issue 2).

---

## Debugging Commands

### 1. View Current Trace Context (Live Debug)

Add this temporary endpoint to your service:

```java
@RestController
@RequestMapping("/debug")
public class TraceDebugController {

    @Autowired
    private TraceContextHolder traceContextHolder;

    @GetMapping("/trace-context")
    public Map<String, String> getCurrentTraceContext() {
        Optional<DistributedTraceContext> context = traceContextHolder.getContext();
        return Map.of(
            "traceId", context.map(DistributedTraceContext::traceId).orElse("MISSING"),
            "spanId", context.map(DistributedTraceContext::spanId).orElse("MISSING"),
            "correlationId", context.flatMap(DistributedTraceContext::correlationId).orElse("MISSING"),
            "mdcTraceId", MDC.get("traceId") != null ? MDC.get("traceId") : "MISSING"
        );
    }
}
```

### 2. Enable Trace-Level Logging

Add to Nacos `logging-common.yml`:

```yaml
logging:
  level:
    com.patra.starter.logging: TRACE  # See all trace context operations
    com.patra.common.logging: TRACE
```

### 3. Search Logs by Trace ID

```bash
# Find all logs for a specific trace ID across services
grep "traceId=abc-123-def-456" logs/*.log | cut -d: -f1,3 | sort

# Count unique trace IDs in gateway logs (should be 1 per request)
grep "Gateway trace context extracted" logs/patra-gateway.log | \
  grep -oP 'traceId=\K[^,]+' | sort | uniq -c

# Find requests with missing correlation ID
grep "correlationId=N/A" logs/*.log
```

### 4. Verify Header Propagation

Use a test client with verbose logging:

```bash
# Test with curl (verbose)
curl -v -H "X-Trace-Id: test-trace-001" \
        -H "X-Correlation-Id: test-corr-001" \
        http://localhost:9528/patra-registry/provenance/pubmed 2>&1 | \
  grep -i "x-trace\|x-correlation"
```

---

## Log Analysis Examples

### Example 1: Successful Trace Propagation

```log
# Gateway (entry point)
2025-01-17T10:30:00.123+08:00 [http-nio-9528-exec-1] INFO  c.p.s.l.g.TraceContextGlobalFilter [traceId=abc-123][spanId=span-1][correlationId=N/A][service=patra-gateway][layer=adapter] - Gateway trace context extracted: traceId=abc-123, generated=false

# Registry (downstream service)
2025-01-17T10:30:00.145+08:00 [http-nio-8081-exec-5] INFO  c.p.r.a.ProvenanceController [traceId=abc-123][spanId=span-2][correlationId=N/A][service=patra-registry][layer=adapter] - Fetching provenance: source=pubmed

# Ingest (further downstream)
2025-01-17T10:30:00.167+08:00 [http-nio-8082-exec-3] INFO  c.p.i.a.DataIngestOrchestrator [traceId=abc-123][spanId=span-3][correlationId=N/A][service=patra-ingest][layer=app] - Starting data ingest
```

**Analysis**: Trace ID `abc-123` is consistent across all services ✅

---

### Example 2: Trace ID Lost (Missing Interceptor)

```log
# Gateway
2025-01-17T10:30:00.123+08:00 ... [traceId=abc-123][correlationId=N/A][service=patra-gateway] - Gateway entry

# Registry (WRONG - different trace ID!)
2025-01-17T10:30:00.145+08:00 ... [traceId=def-456][correlationId=N/A][service=patra-registry] - Fetching provenance
2025-01-17T10:30:00.145+08:00 ... WARN ... TraceContextFilter - Trace ID missing from request - generated new trace ID: def-456
```

**Analysis**: Registry generated a new trace ID because it didn't receive headers from gateway ❌

**Root Cause**: Gateway's HTTP client (Feign/RestTemplate) is missing `TraceContextInterceptor`

---

### Example 3: Correlation ID Lost in Async Operation

```log
# Parent thread (synchronous)
2025-01-17T10:30:00.123+08:00 [http-nio-8082-exec-1] INFO  ... [traceId=abc-123][correlationId=batch-001][service=patra-ingest] - Starting batch job

# Async thread (WRONG - correlation ID missing!)
2025-01-17T10:30:00.234+08:00 [async-executor-1] INFO  ... [traceId=abc-123][correlationId=N/A][service=patra-ingest] - Processing batch item
```

**Analysis**: Trace ID propagated but correlation ID lost ❌

**Root Cause**: Async executor is missing `MdcTaskDecorator`

---

## Prevention Best Practices

### 1. Always Use Logging Starter

**Rule**: Every microservice MUST depend on `patra-spring-boot-starter-logging`

```xml
<!-- Required in every patra-*-boot module -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-logging</artifactId>
</dependency>
```

### 2. Never Customize Logback Without Trace Pattern

**Rule**: If you override `logback-spring.xml`, ensure it includes MDC trace context

```xml
<!-- REQUIRED pattern for all services -->
<property name="ENHANCED_LOG_PATTERN"
          value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} [traceId=%X{traceId:-N/A}][spanId=%X{spanId:-N/A}][correlationId=%X{correlationId:-N/A}][service=${SERVICE_NAME}][layer=%X{layer:-${LAYER_NAME}}] - %msg%n"/>
```

### 3. Always Use Trace-Aware HTTP Clients

**Rule**: Never use plain `RestTemplate` or `Feign` without trace interceptors

**Wrong** ❌:
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();  // Missing trace interceptor!
}
```

**Correct** ✅:
```java
@Bean
public RestTemplate restTemplate(LogContextEnricher enricher) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.getInterceptors().add(new RestTemplateInterceptor(enricher));
    return restTemplate;
}
```

### 4. Always Cleanup MDC

**Rule**: Any code that sets MDC MUST clear it in a finally block

**Pattern**:
```java
try {
    MDC.put("traceId", traceId);
    MDC.put("correlationId", correlationId);
    // ... business logic
} finally {
    MDC.clear();  // CRITICAL!
}
```

---

## Testing Trace Propagation

### Unit Test Template

```java
@Test
void shouldPropagateTraceContextAcrossLayers() {
    // Given: Trace context set
    String expectedTraceId = UUID.randomUUID().toString();
    DistributedTraceContext context = new DistributedTraceContext(
        expectedTraceId, UUID.randomUUID().toString(), Optional.empty(), Optional.empty()
    );

    traceContextHolder.setContext(context);
    logContextEnricher.enrich(context);

    // When: Call service method
    service.doSomething();

    // Then: Verify trace ID maintained
    Optional<DistributedTraceContext> resultContext = traceContextHolder.getContext();
    assertThat(resultContext).isPresent();
    assertThat(resultContext.get().traceId()).isEqualTo(expectedTraceId);
}
```

### Integration Test Template

See `TraceContextPropagationIntegrationTest.java` and `AsyncTraceContextPropagationTest.java` for complete examples.

---

## Escalation Path

If trace context issues persist after following this guide:

1. **Check Dependencies**: Verify `patra-spring-boot-starter-logging` version matches parent POM
2. **Check Autoconfiguration**: Enable debug logging for Spring Boot autoconfiguration:
   ```yaml
   logging:
     level:
       org.springframework.boot.autoconfigure: DEBUG
   ```
3. **Contact Platform Team**: Provide:
   - Service name and version
   - Sample logs showing the issue
   - Request trace ID (if known)
   - Configuration files (application.yml, logback-spring.xml)

---

## Related Documentation

- [Log Level Guidelines](./log-level-guidelines.md) - Semantic usage of log levels
- [Logging Quickstart](../../specs/001-logging-starter/quickstart.md) - Getting started with the logging system
- [Exception Logging Guide](./exception-logging-guide.md) - Standards for logging exceptions

---

**Last Updated**: 2025-01-17 | **Phase**: 5 (User Story 3 - Cross-Service Request Tracing) | **Task**: T061
