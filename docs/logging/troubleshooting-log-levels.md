# Troubleshooting Log Level Issues (FR-011, SC-007)

**Version**: 1.0.0
**Status**: Active
**Related**: [Log Level Guidelines](./log-level-guidelines.md) | [Log Level Examples](./log-level-examples.md)

---

## Overview

This guide helps troubleshoot common issues related to dynamic log level configuration via Nacos (FR-011). If log level changes are not taking effect as expected (SC-007 target: <60 seconds), follow the diagnostic steps below.

---

## Quick Diagnostics

### Symptom: Log level changes in Nacos console have no effect

**Root Causes:**
1. Nacos config not loaded by application
2. Wrong Data ID or Group configured
3. EnvironmentChangeEvent not fired
4. Application not restarted after adding Nacos dependencies
5. Incorrect property format in Nacos config

**Solution Steps:**

#### Step 1: Verify Nacos Config Loaded

Check application startup logs for Nacos config loading:

```
INFO  c.a.cloud.nacos.client.config.NacosConfigService  : Loading config: dataId=logging-patra-registry.yml, group=DEFAULT_GROUP
INFO  c.a.nacos.client.config.impl.ClientWorker         : [fixed-localhost] [subscribe] logging-patra-registry.yml+DEFAULT_GROUP
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Initialized DynamicLoggingConfiguration - listening for Nacos log level changes
```

**If missing:**
- Check `application.yml` for Nacos config settings:
  ```yaml
  spring:
    cloud:
      nacos:
        config:
          server-addr: localhost:8848
          file-extension: yml
          shared-configs:
            - data-id: logging-common.yml
              refresh: true
            - data-id: logging-${spring.application.name}.yml
              refresh: true
  ```

- Verify Nacos server is running: `curl http://localhost:8848/nacos/v1/console/health/readiness`

---

#### Step 2: Verify Data ID and Group

**Common Mistake:** Wrong Data ID or Group in Nacos console.

**Expected Config:**
- **Data ID**: `logging-patra-registry.yml` (for service patra-registry)
- **Group**: `DEFAULT_GROUP`
- **Format**: YAML

**Check in Nacos Console:**
1. Navigate to Configuration Management → Configurations
2. Search for `logging-patra-registry.yml`
3. Verify Group is `DEFAULT_GROUP`
4. Click "Edit" and verify format is YAML (not Properties or JSON)

---

#### Step 3: Verify Property Format

**Common Mistake:** Incorrect YAML indentation or property key format.

**Correct Format:**
```yaml
logging:
  level:
    root: INFO
    com.papertrace.registry.adapter: DEBUG
    com.papertrace.registry.app: INFO
```

**Incorrect Format (will NOT work):**
```yaml
# ❌ Missing 'logging:' root key
level:
  root: INFO

# ❌ Wrong indentation
logging:
level:
  root: INFO

# ❌ Properties format (use YAML)
logging.level.root=INFO
```

---

#### Step 4: Verify EnvironmentChangeEvent Fired

Enable DEBUG logging for Spring Cloud Context:

```yaml
logging:
  level:
    org.springframework.cloud.context.refresh: DEBUG
```

After changing Nacos config, check logs for:

```
DEBUG o.s.cloud.context.refresh.ContextRefresher     : Refresh keys changed: [logging.level.com.papertrace.registry.adapter]
DEBUG o.s.cloud.context.scope.refresh.RefreshScope   : Refreshing scope=refresh
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Detected logging.level changes in Nacos config, applying dynamic log level updates
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Updated logger 'com.papertrace.registry.adapter' to level DEBUG
```

**If missing:**
- Check Nacos config `refresh: true` is set (see Step 1)
- Verify `@RefreshScope` not interfering (DynamicLoggingConfiguration does NOT need @RefreshScope)
- Check Nacos client version compatibility (requires Spring Cloud Alibaba 2023.0.1.0+)

---

#### Step 5: Force Refresh via Actuator Endpoint

If automatic refresh is not working, manually trigger refresh:

**Enable Actuator:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: refresh
```

**Trigger Refresh:**
```bash
curl -X POST http://localhost:8080/actuator/refresh
```

**Expected Response:**
```json
[
  "logging.level.com.papertrace.registry.adapter",
  "logging.level.com.papertrace.registry.app"
]
```

Check application logs for:
```
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Updated logger 'com.papertrace.registry.adapter' to level DEBUG
```

---

### Symptom: Log level changes take longer than 60 seconds (SC-007 violation)

**Root Causes:**
1. Nacos client polling interval too long
2. Network latency to Nacos server
3. Application thread blocking (unlikely with async appenders)

**Solution Steps:**

#### Step 1: Check Nacos Client Polling Interval

Default Nacos client polling interval is **30 seconds** (config change detected within 30s, SC-007 target: <60s).

**Verify Configuration:**
```yaml
spring:
  cloud:
    nacos:
      config:
        refresh-enabled: true  # Default: true
        # Optional: Reduce polling interval for faster refresh (not recommended in production)
        # com.alibaba.nacos.client.config.longPollingTimeout: 30000  # 30 seconds (default)
```

**Measure Actual Latency:**
1. Note timestamp when changing Nacos config: `T1`
2. Check application logs for update confirmation: `T2`
   ```
   INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Updated logger '...' to level DEBUG
   ```
3. Calculate latency: `T2 - T1`

**Expected:** <60 seconds (SC-007)
**Typical:** 10-40 seconds (Nacos long-polling + event processing)

**If >60 seconds:**
- Check network connectivity to Nacos server
- Review application logs for errors in `DynamicLoggingConfiguration`
- Verify Nacos server is not overloaded (check Nacos metrics)

---

#### Step 2: Verify No Thread Blocking

Enable TRACE logging for DynamicLoggingConfiguration:

```yaml
logging:
  level:
    com.patra.starter.logging.autoconfigure.DynamicLoggingConfiguration: TRACE
```

Check logs for immediate execution:
```
DEBUG c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Received EnvironmentChangeEvent with 1 changed keys
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Detected logging.level changes in Nacos config, applying dynamic log level updates
DEBUG c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Found 1 logger configurations to apply
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Updated logger '...' to level DEBUG
INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Dynamic log level refresh completed: 1 successful, 0 failed, 1 total
```

**If delays observed:**
- Review thread dumps for blocking (unlikely)
- Check for excessive log volume causing I/O contention

---

### Symptom: Specific logger not responding to Nacos config changes

**Root Causes:**
1. Logger level hardcoded in logback-spring.xml
2. Wrong logger name (case-sensitive)
3. Parent logger overriding child logger

**Solution Steps:**

#### Step 1: Check for Hardcoded Levels in logback-spring.xml

Review `src/main/resources/logback-spring.xml`:

```xml
<!-- ❌ Hardcoded level - will OVERRIDE Nacos config -->
<logger name="com.papertrace.registry.adapter" level="WARN"/>

<!-- ✅ No hardcoded level - Nacos config applies -->
<logger name="com.papertrace.registry.adapter"/>
```

**Fix:** Remove hardcoded `level` attribute from logger definitions in logback-spring.xml.

---

#### Step 2: Verify Logger Name (Case-Sensitive)

**Common Mistake:** Logger name in Nacos config does not match actual package name.

**Example:**
- **Nacos Config**: `logging.level.com.papertrace.Registry.Adapter: DEBUG`
- **Actual Package**: `com.papertrace.registry.adapter`

**Fix:** Use exact package name (case-sensitive):
```yaml
logging:
  level:
    com.papertrace.registry.adapter: DEBUG  # ✅ Correct
```

---

#### Step 3: Check Parent Logger Override

**Logback Inheritance:** Child loggers inherit level from parent unless explicitly set.

**Example:**
```yaml
logging:
  level:
    com.papertrace: WARN                         # Parent logger
    com.papertrace.registry.adapter: DEBUG       # Child logger
```

**Result:**
- `com.papertrace.registry.adapter` logs at DEBUG (explicit)
- `com.papertrace.registry.app` logs at WARN (inherited from parent)

**Verify via Actuator:**
```bash
curl http://localhost:8080/actuator/loggers/com.papertrace.registry.adapter
```

**Expected Response:**
```json
{
  "configuredLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

**If `effectiveLevel` ≠ `configuredLevel`:**
- Parent logger is overriding child
- Set child logger explicitly in Nacos config

---

### Symptom: Log level changes work but logs still not appearing

**Root Causes:**
1. Log level changed but code not logging at that level
2. Async appender discarding logs under pressure
3. Log sampling filtering out DEBUG/TRACE logs

**Solution Steps:**

#### Step 1: Verify Code is Logging at Expected Level

**Example:** Nacos config set to DEBUG, but code logs at TRACE.

```java
// Nacos: logging.level.com.papertrace.registry.adapter = DEBUG
log.trace("This will NOT appear (TRACE < DEBUG)");
log.debug("This WILL appear (DEBUG = DEBUG)");
```

**Fix:** Change code to log at DEBUG or set Nacos config to TRACE.

---

#### Step 2: Check Async Appender Discard Policy

Async appenders discard low-priority logs under pressure to prevent blocking.

**Default Policy (from logback-spring.xml):**
- Queue size: 256
- Discard threshold: 20% (drop DEBUG/TRACE when queue >80% full)
- Never discard: ERROR, WARN

**Verify via Logs:**
```
WARN  c.q.logback.core.AsyncAppenderBase : Appender [ASYNC] discarding 10 events due to queue full
```

**Fix:**
- Increase queue size (temporary):
  ```xml
  <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>512</queueSize>  <!-- Increase from 256 -->
  </appender>
  ```
- Reduce log volume (preferred): Change log level to INFO temporarily

---

#### Step 3: Check Log Sampling

If DEBUG/TRACE logs exceed 100 logs/sec, `SamplingFilter` (T075) applies sampling.

**Verify via Logs:**
```
DEBUG c.p.starter.logging.filter.SamplingFilter : Sampling applied: dropped 90/100 DEBUG logs in last second
```

**Fix:**
- Reduce log frequency (preferred): Avoid logging in tight loops
- Disable sampling (temporary, development only):
  ```yaml
  # Not yet implemented in Phase 4 (T075 is Phase 6)
  ```

---

## Common Scenarios

### Scenario 1: Troubleshoot production issue with DEBUG logging

**Goal:** Enable DEBUG logging for specific module without redeploying.

**Steps:**
1. Navigate to Nacos Console → Configuration Management
2. Edit `logging-patra-registry.yml`
3. Add/update log level:
   ```yaml
   logging:
     level:
       com.papertrace.registry.infra.provenance: DEBUG
   ```
4. Click "Publish"
5. Wait <60 seconds for change to propagate (SC-007)
6. Verify in application logs:
   ```
   INFO  c.p.starter.logging.autoconfigure.DynamicLoggingConfiguration : Updated logger 'com.papertrace.registry.infra.provenance' to level DEBUG
   ```
7. Check for DEBUG logs:
   ```
   DEBUG c.p.registry.infra.provenance.ProvenanceRepositoryImpl : Querying provenance config by ID: id=12345
   ```
8. **IMPORTANT:** Revert to INFO after troubleshooting (high log volume)

---

### Scenario 2: Enable TRACE logging for specific class

**Goal:** Enable TRACE logging for `ProvenanceSelectionOrchestrator` only.

**Steps:**
1. Find fully qualified class name: `com.papertrace.registry.app.provenance.select.ProvenanceSelectionOrchestrator`
2. Edit Nacos config:
   ```yaml
   logging:
     level:
       com.papertrace.registry.app.provenance.select.ProvenanceSelectionOrchestrator: TRACE
   ```
3. Publish and wait <60s
4. Verify TRACE logs appear:
   ```
   TRACE c.p.registry.app.provenance.select.ProvenanceSelectionOrchestrator : Candidate provenance: id=abc, priority=10, lastUpdate=2025-10-17
   ```

---

### Scenario 3: Reduce log volume in production

**Goal:** Reduce log volume by 40% (SC-004) by adjusting log levels.

**Steps:**
1. Identify noisy loggers via log aggregation tool (Splunk, ELK)
2. Example: `com.baomidou.mybatisplus` logging SQL at DEBUG
3. Edit Nacos config:
   ```yaml
   logging:
     level:
       com.baomidou.mybatisplus: WARN  # Reduce from INFO to WARN
       com.papertrace.registry.adapter: INFO  # Keep at INFO for key events
   ```
4. Publish and monitor log volume metrics
5. Iterate until SC-004 target achieved (40% reduction)

---

## Verification Commands

### Check Effective Log Levels via Actuator

**Enable Loggers Endpoint:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: loggers
```

**Query Specific Logger:**
```bash
curl http://localhost:8080/actuator/loggers/com.papertrace.registry.adapter
```

**Response:**
```json
{
  "configuredLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

**Query All Loggers:**
```bash
curl http://localhost:8080/actuator/loggers | jq '.loggers | to_entries[] | select(.value.configuredLevel != null)'
```

---

### Monitor Nacos Config Changes

**Enable Nacos Client DEBUG Logging:**
```yaml
logging:
  level:
    com.alibaba.nacos.client.config: DEBUG
```

**Logs:**
```
DEBUG c.a.nacos.client.config.impl.ClientWorker : [fixed-localhost] [data-received] dataId=logging-patra-registry.yml, group=DEFAULT_GROUP, md5=abc123
INFO  c.a.nacos.client.config.impl.CacheData      : [fixed-localhost] [notify-ok] dataId=logging-patra-registry.yml, group=DEFAULT_GROUP
```

---

## Escalation Path

If log level changes still not working after following this guide:

1. **Enable Full DEBUG Logging:**
   ```yaml
   logging:
     level:
       com.patra.starter.logging: DEBUG
       org.springframework.cloud.context: DEBUG
       com.alibaba.nacos.client: DEBUG
   ```

2. **Collect Diagnostics:**
   - Application logs (last 5 minutes after Nacos config change)
   - Nacos config export (Data ID: logging-{service}.yml)
   - Actuator endpoints: `/actuator/loggers`, `/actuator/env`

3. **Check Known Issues:**
   - Spring Cloud Alibaba version compatibility (requires 2023.0.1.0+)
   - Logback version (requires 1.4.x+)
   - Java version (requires Java 21)

4. **Contact Platform Team:**
   - Provide diagnostics from step 2
   - Include reproduction steps and expected behavior

---

## References

- [Log Level Guidelines](./log-level-guidelines.md)
- [Log Level Examples](./log-level-examples.md)
- [Quickstart Guide](../../specs/001-logging-starter/quickstart.md)
- FR-011: Dynamic log level configuration
- SC-007: Log level changes within 60 seconds
