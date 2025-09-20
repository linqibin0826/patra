# Error Handling Monitoring and Observability Guide

This guide covers the monitoring and observability features built into the Patra Error Handling System, including metrics collection, circuit breaker protection, and performance monitoring.

## Overview

The error handling system provides comprehensive monitoring capabilities to help you understand error patterns, performance characteristics, and system health. The monitoring features include:

- **Error Resolution Performance Metrics**: Track how long it takes to resolve exceptions to error codes
- **Error Code Distribution Tracking**: Monitor which error codes are occurring across services
- **Feign Error Decoding Metrics**: Track success rates and performance of Feign error decoding
- **Circuit Breaker Protection**: Protect against cascading failures in error mapping contributors
- **Structured Logging**: Detailed logging for troubleshooting and analysis

## Configuration

### Core Error Monitoring Configuration

```yaml
patra:
  error:
    context-prefix: "REG"  # Required
    monitoring:
      enabled: true  # Enable monitoring (default: true)
      circuit-breaker:
        enabled: true  # Enable circuit breakers (default: true)
        failure-threshold: 5  # Consecutive failures to open circuit (default: 5)
        failure-rate-threshold: 0.5  # Failure rate to open circuit (default: 0.5)
        timeout-ms: 60000  # Timeout before attempting to close circuit (default: 60000)
        sliding-window-size: 100  # Size of sliding window for tracking calls (default: 100)
      metrics:
        enabled: true  # Enable metrics collection (default: true)
        log-slow-resolution: true  # Log slow resolution performance (default: true)
        slow-resolution-threshold-ms: 100  # Threshold for slow resolution logging (default: 100)
        log-cache-performance: true  # Log cache performance statistics (default: true)
        cache-performance-log-interval: 50  # Interval for cache performance logging (default: 50)
```

### Feign Error Monitoring Configuration

```yaml
patra:
  feign:
    problem:
      enabled: true
      tolerant: true
      monitoring:
        enabled: true  # Enable Feign monitoring (default: true)
        log-slow-parsing: true  # Log slow parsing operations (default: true)
        slow-parsing-threshold-ms: 100  # Threshold for slow parsing logging (default: 100)
        log-response-body-reading: true  # Log response body reading performance (default: true)
        slow-body-reading-threshold-ms: 50  # Threshold for slow body reading (default: 50)
        decoding-success-log-interval: 10  # Interval for success rate logging (default: 10)
        trace-id-extraction-log-interval: 25  # Interval for trace ID extraction logging (default: 25)
        content-type-distribution-log-interval: 50  # Interval for content type logging (default: 50)
```

## Metrics Collection

### Error Resolution Metrics

The system automatically collects metrics for error resolution performance:

```java
// Example structured log output
log.info("error_resolution_performance exception_class=RuntimeException error_code=REG-0500 " +
         "resolution_time_ms=45 cache_hit=false");

// Slow resolution warning
log.warn("slow_error_resolution exception_class=ComplexException error_code=REG-1001 " +
         "resolution_time_ms=150 cache_hit=false");
```

### Error Code Distribution

Track which error codes are occurring across your services:

```java
// Example structured log output
log.info("error_code_distribution service=REG error_code=REG-1401 http_status=404 count=25");

// High frequency alert
log.warn("high_error_frequency service=REG error_code=REG-1401 http_status=404 count=100");
```

### Cache Performance

Monitor cache hit rates for error resolution:

```java
// Example structured log output
log.info("cache_performance exception_class=DictionaryNotFoundException hit_rate=0.85 " +
         "total_requests=100 hits=85 misses=15");
```

### Contributor Performance

Track the performance of error mapping contributors:

```java
// Example structured log output
log.debug("contributor_performance contributor=RegistryErrorMappingContributor success=true " +
          "execution_time_ms=15 success_rate=0.95");

// Slow contributor warning
log.warn("slow_contributor contributor=SlowContributor execution_time_ms=75 success=true");

// Low success rate alert
log.warn("low_contributor_success_rate contributor=FailingContributor success_rate=0.65 " +
         "total_executions=20");
```

## Circuit Breaker Protection

### How Circuit Breakers Work

Circuit breakers protect error mapping contributors from cascading failures:

1. **CLOSED**: Normal operation, all calls pass through
2. **OPEN**: Circuit is open, calls are rejected to prevent cascading failures
3. **HALF_OPEN**: Limited calls allowed to test if the service has recovered

### Circuit Breaker States

```java
// Circuit breaker state transitions
log.info("Circuit breaker 'RegistryErrorMappingContributor' transitioning from OPEN to HALF_OPEN");
log.info("Circuit breaker 'RegistryErrorMappingContributor' transitioning from HALF_OPEN to CLOSED after success");
log.warn("Circuit breaker 'RegistryErrorMappingContributor' opening due to failures: consecutive=5, rate=0.60");
```

### Manual Circuit Breaker Control

You can manually control circuit breakers if needed:

```java
@Autowired
private List<ErrorMappingContributor> contributors;

public void manualCircuitBreakerControl() {
    contributors.stream()
        .filter(c -> c instanceof CircuitBreakerProtectedContributor)
        .map(c -> (CircuitBreakerProtectedContributor) c)
        .forEach(protected -> {
            CircuitBreaker cb = protected.getCircuitBreaker();
            
            // Check state
            CircuitBreaker.State state = cb.getState();
            double failureRate = cb.getFailureRate();
            long callCount = cb.getRecentCallCount();
            
            // Manual control
            if (failureRate > 0.8) {
                cb.forceOpen();  // Force open if failure rate too high
            }
            
            // Force close for recovery
            cb.forceClose();
        });
}
```

## Feign Error Monitoring

### ProblemDetail Parsing Metrics

Track the success and performance of ProblemDetail parsing:

```java
// Successful parsing
log.info("feign_problem_detail_parsing method=UserClient#getUser() http_status=404 " +
         "success=true parse_time_ms=25");

// Slow parsing warning
log.warn("slow_problem_detail_parsing method=UserClient#getUser() http_status=500 parse_time_ms=150");

// Parsing failure
log.warn("problem_detail_parsing_failed method=UserClient#getUser() http_status=400");
```

### Error Decoding Success Rates

Monitor overall Feign error decoding success:

```java
// Decoding success tracking
log.info("feign_error_decoding method=UserClient#getUser() http_status=404 success=true " +
         "tolerant_mode=false success_rate=0.95");

// Low success rate alert
log.warn("low_feign_decoding_success_rate method=UserClient#getUser() success_rate=0.75 total_attempts=20");

// Tolerant mode usage
log.info("feign_tolerant_mode_usage method=UserClient#getUser() tolerant_mode_rate=0.30 total_attempts=50");
```

### Trace ID Extraction

Track trace ID extraction from response headers:

```java
// Successful extraction
log.debug("feign_trace_id_extracted method=UserClient#getUser() header_used=X-B3-TraceId");

// Extraction rate monitoring
log.info("feign_trace_id_extraction_rate method=UserClient#getUser() extraction_rate=0.85 total_attempts=100");
```

### Response Body Reading Performance

Monitor response body reading performance:

```java
// Normal reading
log.debug("feign_response_body_reading method=UserClient#getUser() body_size_bytes=1024 " +
          "read_time_ms=15 truncated=false");

// Large response body warning
log.warn("large_feign_response_body method=UserClient#getUser() body_size_bytes=15000 truncated=false");

// Slow reading warning
log.warn("slow_feign_response_body_reading method=UserClient#getUser() read_time_ms=75 body_size_bytes=2048");

// Truncated response alert
log.warn("feign_response_body_truncated method=UserClient#getUser() body_size_bytes=65536");
```

## Custom Metrics Implementation

### Implementing Custom Error Metrics

You can provide your own metrics implementation:

```java
@Component
public class MicrometerErrorMetrics implements ErrorMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer resolutionTimer;
    private final Counter errorCodeCounter;
    
    public MicrometerErrorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.resolutionTimer = Timer.builder("error.resolution.time")
            .description("Time taken to resolve exceptions to error codes")
            .register(meterRegistry);
        this.errorCodeCounter = Counter.builder("error.code.distribution")
            .description("Distribution of error codes across services")
            .register(meterRegistry);
    }
    
    @Override
    public void recordResolutionTime(Class<?> exceptionClass, ErrorCodeLike errorCode, 
                                   long resolutionTimeMs, boolean cacheHit) {
        resolutionTimer.record(Duration.ofMillis(resolutionTimeMs));
        
        Tags tags = Tags.of(
            "exception_class", exceptionClass.getSimpleName(),
            "error_code", errorCode.code(),
            "cache_hit", String.valueOf(cacheHit)
        );
        
        Timer.Sample.start(meterRegistry).stop(resolutionTimer.withTags(tags));
    }
    
    @Override
    public void recordErrorCodeDistribution(ErrorCodeLike errorCode, int httpStatus, String serviceName) {
        errorCodeCounter.increment(Tags.of(
            "error_code", errorCode.code(),
            "http_status", String.valueOf(httpStatus),
            "service", serviceName
        ));
    }
    
    // Implement other methods...
}
```

### Implementing Custom Feign Metrics

```java
@Component
public class MicrometerFeignErrorMetrics implements FeignErrorMetrics {
    
    private final Timer parsingTimer;
    private final Counter decodingCounter;
    
    public MicrometerFeignErrorMetrics(MeterRegistry meterRegistry) {
        this.parsingTimer = Timer.builder("feign.problem.detail.parsing.time")
            .description("Time taken to parse ProblemDetail responses")
            .register(meterRegistry);
        this.decodingCounter = Counter.builder("feign.error.decoding")
            .description("Feign error decoding attempts")
            .register(meterRegistry);
    }
    
    @Override
    public void recordProblemDetailParsing(String methodKey, int httpStatus, boolean success, long parseTimeMs) {
        Tags tags = Tags.of(
            "method", methodKey,
            "http_status", String.valueOf(httpStatus),
            "success", String.valueOf(success)
        );
        
        parsingTimer.record(Duration.ofMillis(parseTimeMs), tags);
    }
    
    @Override
    public void recordErrorDecodingSuccess(String methodKey, int httpStatus, boolean decodingSuccess, boolean tolerantMode) {
        Tags tags = Tags.of(
            "method", methodKey,
            "http_status", String.valueOf(httpStatus),
            "success", String.valueOf(decodingSuccess),
            "tolerant_mode", String.valueOf(tolerantMode)
        );
        
        decodingCounter.increment(tags);
    }
    
    // Implement other methods...
}
```

## Troubleshooting

### Common Monitoring Issues

1. **High Resolution Times**
   - Check if error mapping contributors are performing expensive operations
   - Consider caching strategies for complex mappings
   - Review cause chain depth for deeply nested exceptions

2. **Low Cache Hit Rates**
   - Verify that similar exception types are being thrown
   - Check if exception classes are being created dynamically
   - Consider the cache eviction policy

3. **Circuit Breaker Frequently Opening**
   - Review contributor implementation for reliability
   - Adjust failure thresholds based on expected behavior
   - Check for external dependencies in contributors

4. **Low Feign Decoding Success Rates**
   - Verify downstream services are returning proper ProblemDetail responses
   - Check content-type headers in responses
   - Consider enabling tolerant mode for better compatibility

### Performance Tuning

1. **Optimize Error Resolution**
   ```yaml
   patra:
     error:
       monitoring:
         metrics:
           slow-resolution-threshold-ms: 50  # Lower threshold for stricter monitoring
   ```

2. **Adjust Circuit Breaker Sensitivity**
   ```yaml
   patra:
     error:
       monitoring:
         circuit-breaker:
           failure-threshold: 3  # More sensitive to failures
           failure-rate-threshold: 0.3  # Lower threshold
   ```

3. **Fine-tune Feign Monitoring**
   ```yaml
   patra:
     feign:
       problem:
         max-error-body-size: 32768  # Reduce body size limit
         monitoring:
           slow-parsing-threshold-ms: 50  # Stricter parsing performance
   ```

## Integration with External Monitoring Systems

### Prometheus Integration

```java
@Configuration
public class PrometheusErrorMetricsConfiguration {
    
    @Bean
    @ConditionalOnClass(PrometheusMeterRegistry.class)
    public ErrorMetrics prometheusErrorMetrics(PrometheusMeterRegistry registry) {
        return new MicrometerErrorMetrics(registry);
    }
}
```

### Grafana Dashboard Queries

```promql
# Error resolution time percentiles
histogram_quantile(0.95, rate(error_resolution_time_seconds_bucket[5m]))

# Error code distribution
rate(error_code_distribution_total[5m])

# Cache hit rate
rate(error_cache_hits_total[5m]) / rate(error_cache_total[5m])

# Circuit breaker state
circuit_breaker_state{name="RegistryErrorMappingContributor"}
```

This comprehensive monitoring system provides deep visibility into your error handling performance and helps maintain system reliability through proactive monitoring and circuit breaker protection.