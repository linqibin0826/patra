# Logging Operations Guide

**Version**: 1.0
**Last Updated**: 2025-10-17
**Target Audience**: DevOps, SRE, Operations Team

---

## Overview

This guide provides operational procedures for managing logging across the Papertrace microservices platform. It covers dynamic log level management, troubleshooting, monitoring, and incident response.

---

## Table of Contents

1. [Dynamic Log Level Management](#dynamic-log-level-management)
2. [Monitoring and Alerting](#monitoring-and-alerting)
3. [Troubleshooting Procedures](#troubleshooting-procedures)
4. [Incident Response](#incident-response)
5. [Performance Tuning](#performance-tuning)
6. [Security Operations](#security-operations)
7. [Maintenance Tasks](#maintenance-tasks)

---

## Dynamic Log Level Management

### Nacos Configuration Structure

```
nacos/
└── config/
    └── logging/
        ├── logging-common.yml          # Global defaults for all services
        ├── logging-patra-registry.yml  # Service-specific overrides
        ├── logging-patra-ingest.yml
        ├── logging-patra-gateway.yml
        └── logging-patra-egress-gateway.yml
```

### Changing Log Levels (Nacos UI)

**Access**: http://nacos-server:8848/nacos
**Credentials**: Stored in 1Password (search: "Nacos Admin")

#### Step 1: Navigate to Configuration
1. Login to Nacos UI
2. Go to **Configuration Management** → **Configurations**
3. Filter by `Data ID` = `logging-*`

#### Step 2: Edit Configuration
1. Select the target configuration file (e.g., `logging-patra-registry.yml`)
2. Click **Edit**
3. Modify log levels following the pattern:

```yaml
logging:
  level:
    root: INFO
    com.patra: DEBUG
    com.patra.registry.adapter: INFO
    com.patra.registry.app: DEBUG
    com.patra.registry.infra: DEBUG
```

#### Step 3: Publish Changes
1. Click **Publish**
2. Confirm the change
3. **Verify propagation** (see below)

### Verifying Log Level Changes

**Timeline**: Changes propagate within **60 seconds** (SC-007)

**Verification Steps**:

```bash
# 1. Check application logs for config refresh event
tail -f /var/log/patra/patra-registry.log | grep "Refresh"

# Expected output:
# 2025-10-17 10:30:45 INFO  [o.s.cloud.context.scope.refresh.RefreshScope] Refreshed keys: [logging.level]

# 2. Verify current log level via actuator endpoint
curl http://localhost:8081/actuator/loggers/com.patra.registry.app

# Expected output:
# {
#   "configuredLevel": "DEBUG",
#   "effectiveLevel": "DEBUG"
# }

# 3. Generate test logs to confirm
# (In development/staging only)
curl -X POST http://localhost:8081/api/internal/test/log-levels
```

### Common Log Level Scenarios

#### Scenario 1: Production Issue Diagnosis
**Problem**: Users reporting slow response times
**Action**: Temporarily increase log level to DEBUG

```yaml
# Edit logging-patra-registry.yml (Nacos)
logging:
  level:
    com.patra.registry.app: DEBUG  # Was: INFO
    com.patra.registry.infra: DEBUG  # Was: INFO
```

**Duration**: Keep DEBUG for maximum **30 minutes** to avoid log flood
**Rollback**: Revert to INFO once issue identified

#### Scenario 2: External API Integration Issues
**Problem**: External API calls failing intermittently
**Action**: Enable DEBUG for HTTP client and specific adapter

```yaml
logging:
  level:
    com.patra.starter.logging.interceptor: DEBUG
    com.patra.registry.adapter.client: DEBUG
    org.springframework.web.client.RestTemplate: DEBUG
```

#### Scenario 3: Database Query Performance
**Problem**: Slow database queries suspected
**Action**: Enable SQL logging

```yaml
logging:
  level:
    com.patra.starter.mybatis: DEBUG
    org.mybatis: DEBUG
```

**Warning**: SQL logging generates **high volume** of logs. Use sparingly.

#### Scenario 4: Batch Processing Debugging
**Problem**: Batch job failing sporadically
**Action**: Enable TRACE for specific package

```yaml
logging:
  level:
    com.patra.ingest.app.batch: TRACE
    com.patra.ingest.adapter.job: DEBUG
```

---

## Monitoring and Alerting

### Log Volume Metrics

**Monitor these metrics via Prometheus/Grafana**:

```promql
# Log events per second by level
rate(logback_events_total{level="ERROR"}[5m])
rate(logback_events_total{level="WARN"}[5m])
rate(logback_events_total{level="INFO"}[5m])

# Log volume reduction (target: 40% at INFO level)
(1 - (rate(logback_events_total{level="INFO"}[1d]) / <baseline>)) * 100
```

**Alert Rules**:

```yaml
# High error rate
- alert: HighErrorLogRate
  expr: rate(logback_events_total{level="ERROR"}[5m]) > 10
  for: 5m
  annotations:
    summary: "High ERROR log rate detected in {{ $labels.service }}"

# Log flood (DEBUG enabled in production)
- alert: DebugLogsInProduction
  expr: rate(logback_events_total{level="DEBUG"}[5m]) > 50
  for: 10m
  annotations:
    summary: "DEBUG logs detected in production {{ $labels.service }}"
```

### Trace ID Coverage

**Target**: 100% for synchronous, 95% for asynchronous (SC-002)

```promql
# Trace ID coverage percentage
(count(log_entries_with_trace_id) / count(log_entries_total)) * 100
```

**Alert if below threshold**:

```yaml
- alert: LowTraceIdCoverage
  expr: (count(log_entries_with_trace_id) / count(log_entries_total)) * 100 < 95
  for: 15m
  annotations:
    summary: "Trace ID coverage below 95% in {{ $labels.service }}"
```

---

## Troubleshooting Procedures

### Problem: Log Level Changes Not Taking Effect

**Symptoms**: Logs still showing old level after Nacos config change

**Diagnosis**:
1. Verify Nacos config version incremented
2. Check service connection to Nacos
3. Review application logs for refresh events

**Resolution**:

```bash
# 1. Verify Nacos config listener is active
curl http://localhost:8081/actuator/nacos-config
# Check: listener.count > 0

# 2. Force configuration refresh
curl -X POST http://localhost:8081/actuator/refresh

# 3. If still not working, restart the service (last resort)
systemctl restart patra-registry
```

### Problem: Logs Missing Trace IDs

**Symptoms**: Cannot trace requests across services

**Diagnosis**:
```bash
# Check trace context filter is active
curl http://localhost:8081/actuator/mappings | grep TraceContextFilter

# Check SkyWalking agent is running
ps aux | grep skywalking
```

**Resolution**:

```bash
# 1. Verify TraceContextFilter is registered
# Expected in logs at startup:
# INFO [o.s.b.w.s.FilterRegistrationBean] Mapping filter: 'traceContextFilter'

# 2. If missing, check logging starter dependency in pom.xml
grep -A 2 "patra-spring-boot-starter-logging" pom.xml

# 3. Restart service to reinitialize filters
systemctl restart patra-registry
```

### Problem: High Log Volume Causing Disk Space Issues

**Symptoms**: Disk usage alert, /var/log/ partition full

**Diagnosis**:
```bash
# Check log file sizes
du -sh /var/log/patra/*

# Identify services with highest log volume
find /var/log/patra -name "*.log" -exec ls -lh {} \; | sort -k5 -hr | head -10
```

**Resolution**:

```bash
# 1. IMMEDIATE: Compress old logs
find /var/log/patra -name "*.log.*" -mtime +1 -exec gzip {} \;

# 2. Reduce log level to INFO (if DEBUG enabled)
# Update Nacos: logging-<service>.yml

# 3. Enable log sampling for high-frequency events
# Edit Nacos config:
logging:
  sampling:
    enabled: true
    threshold: 100  # logs per second

# 4. Verify log rotation is working
cat /etc/logrotate.d/patra-services
```

---

## Incident Response

### Incident: Production Outage - Need Detailed Logs

**Timeline**: 10 minutes

**Actions**:

1. **T+0min**: Increase log level to DEBUG for affected service
   ```bash
   # Via Nacos UI or CLI
   nacos-cli config set logging-patra-<service>.yml --level DEBUG
   ```

2. **T+2min**: Verify log level propagated
   ```bash
   curl http://<service>:8081/actuator/loggers/com.patra
   ```

3. **T+2-10min**: Collect logs for analysis
   ```bash
   # Stream recent logs
   tail -n 5000 /var/log/patra/<service>.log > /tmp/incident-<timestamp>.log

   # Filter by time window
   grep "2025-10-17 10:[3-4][0-9]" /var/log/patra/<service>.log > /tmp/incident.log
   ```

4. **T+10min**: Revert log level to INFO
   ```bash
   nacos-cli config set logging-patra-<service>.yml --level INFO
   ```

5. **Post-incident**: Archive logs for analysis
   ```bash
   tar -czf incident-<timestamp>.tar.gz /tmp/incident-*.log
   aws s3 cp incident-<timestamp>.tar.gz s3://patra-incidents/
   ```

### Incident: Trace Request Across Services

**Scenario**: User reported failed request, need to trace entire flow

**Actions**:

1. Obtain trace ID from user or API gateway logs
   ```bash
   grep "user_request_id=12345" /var/log/patra/patra-gateway.log
   # Extract: [TID: 550e8400-e29b-41d4-a716-446655440000]
   ```

2. Search all service logs by trace ID
   ```bash
   # Via log aggregation (ELK/Loki)
   grep -r "TID: 550e8400" /var/log/patra/

   # Or via Kibana/Grafana:
   # Query: trace_id:"550e8400-e29b-41d4-a716-446655440000"
   ```

3. Reconstruct request timeline
   ```bash
   # Extract and sort by timestamp
   grep "TID: 550e8400" /var/log/patra/*.log | sort -k1,2
   ```

4. Identify failure point
   ```bash
   # Filter for ERROR/WARN
   grep "TID: 550e8400" /var/log/patra/*.log | grep -E "ERROR|WARN"
   ```

---

## Performance Tuning

### Async Appender Configuration

**File**: `patra-spring-boot-starter-logging/src/main/resources/logback-spring.xml`

**Tuning Parameters**:

```xml
<appender name="ASYNC-INFO" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>512</queueSize>  <!-- Default: 256. Increase for high throughput -->
    <discardingThreshold>0</discardingThreshold>  <!-- 0 = never discard -->
    <neverBlock>false</neverBlock>  <!-- false = block when queue full (for ERROR/WARN) -->
    <maxFlushTime>5000</maxFlushTime>  <!-- Max wait time on shutdown (ms) -->
    <includeCallerData>false</includeCallerData>  <!-- false = better performance -->
</appender>
```

**Guidelines**:
- **queueSize**: Increase if you see "queue full" warnings
- **discardingThreshold**: Set to 20 for DEBUG appenders to drop logs under load
- **neverBlock**: true for DEBUG/TRACE, false for ERROR/WARN

### Log Sampling Configuration

**File**: Nacos `logging-<service>.yml`

```yaml
logging:
  sampling:
    enabled: true
    threshold: 100  # Max logs per second per logger
    rules:
      - logger: com.patra.ingest.adapter.batch
        threshold: 50  # Lower for noisy loggers
      - logger: com.patra.registry.infra.http
        threshold: 200  # Higher for critical components
```

---

## Security Operations

### Sensitive Data Audit

**Schedule**: Monthly

**Procedure**:

```bash
# 1. Sample recent logs
find /var/log/patra -name "*.log" -mtime -1 | xargs cat | head -10000 > /tmp/log-sample.txt

# 2. Run PII scanner
./scripts/scan-logs-for-pii.sh /tmp/log-sample.txt

# 3. Verify sanitization patterns
grep -E "(password|token|apiKey)=" /tmp/log-sample.txt
# Expected: All should show "***REDACTED***"

# 4. Check for email/phone leaks
grep -E "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}" /tmp/log-sample.txt
# Expected: All should be "***REDACTED***"
```

### Security Event Monitoring

**Alert on**:
- Failed login attempts (>5 per user per hour)
- Authorization failures (>10 per service per 5 min)
- Suspicious API key usage

```promql
# Failed auth attempts
rate(logback_events_total{level="WARN", logger="SecurityEventLogger"}[1h]) > 5
```

---

## Maintenance Tasks

### Weekly Tasks

- [ ] Review log volume metrics (target: 40% reduction at INFO)
- [ ] Check disk usage on log partitions (<80%)
- [ ] Verify log rotation is working
- [ ] Review ERROR logs for recurring issues

### Monthly Tasks

- [ ] Audit log sanitization (PII scan)
- [ ] Review and optimize log levels per service
- [ ] Analyze trace ID coverage metrics
- [ ] Update log retention policies if needed

### Quarterly Tasks

- [ ] Review and update log level configurations
- [ ] Optimize async appender settings based on metrics
- [ ] Conduct incident response drill
- [ ] Review and update this operations guide

---

## Reference

### Log Level Decision Matrix

| Level | Use Case | Example | Volume Impact |
|-------|----------|---------|---------------|
| ERROR | Unrecoverable failures | Database connection lost | Low |
| WARN  | Recoverable issues, retries | External API timeout (with retry) | Low-Medium |
| INFO  | Key business events | Batch job started/completed | Medium |
| DEBUG | Detailed flow for diagnosis | Method entry/exit, variable states | High |
| TRACE | Diagnostic information | Full request/response bodies | Very High |

### Service-Specific Log Level Recommendations

| Service | Production | Staging | Development |
|---------|-----------|---------|-------------|
| patra-gateway | INFO | DEBUG | DEBUG |
| patra-registry | INFO | INFO | DEBUG |
| patra-ingest | INFO | DEBUG | DEBUG |
| patra-egress-gateway | INFO | DEBUG | DEBUG |

### Contact Information

**On-Call Rotation**: See PagerDuty schedule
**Slack Channel**: #patra-ops
**Runbook Location**: https://wiki.internal/patra/runbooks
**Log Aggregation**: https://kibana.internal/app/discover

---

**Document Owner**: DevOps Team
**Review Cycle**: Quarterly
**Last Reviewed**: 2025-10-17
