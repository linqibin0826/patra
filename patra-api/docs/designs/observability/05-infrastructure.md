---
title: 可观测性系统设计 - 基础设施部署
type: design
status: draft
date: 2025-11-29
module: patra-spring-boot-starter-observability
related_adrs: [ADR-005]
tags:
  - design/infrastructure
  - tech/docker
  - tech/opentelemetry
---

# 基础设施部署

## Docker Compose 架构

### 目录结构

```
docker/
├── docker-compose.yaml              # 核心服务（MySQL, Redis, Nacos）
├── docker-compose.observability.yaml # 可观测性服务（重写）
├── otel-collector/
│   └── otel-collector-config.yaml   # Collector 配置
├── prometheus/
│   ├── prometheus.yml               # Prometheus 配置
│   └── alert.rules.yml              # 告警规则
├── loki/
│   └── loki-config.yaml             # Loki 配置
├── tempo/
│   └── tempo-config.yaml            # Tempo 配置
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── datasources.yaml     # 数据源配置
│   │   ├── dashboards/
│   │   │   ├── dashboards.yaml      # 仪表盘配置
│   │   │   └── *.json               # 仪表盘 JSON
│   │   └── alerting/
│   │       └── alerting.yaml        # 告警配置
│   └── grafana.ini                  # Grafana 配置
├── alertmanager/
│   └── alertmanager.yml             # Alertmanager 配置
└── otel-agent/
    └── opentelemetry-javaagent.jar  # OTel Java Agent
```

### docker-compose.observability.yaml

```yaml
version: "3.9"

# 可观测性服务
# OpenTelemetry + Grafana Stack

services:
  # ============================================
  # OpenTelemetry Collector
  # ============================================
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.96.0
    container_name: patra-otel-collector
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector/otel-collector-config.yaml:/etc/otel-collector-config.yaml:ro
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Collector metrics
      - "8889:8889"   # Prometheus exporter
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:13133/"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ============================================
  # Prometheus (Metrics Storage)
  # ============================================
  prometheus:
    image: prom/prometheus:v2.51.0
    container_name: patra-prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=7d'
      - '--web.enable-lifecycle'
      - '--web.enable-remote-write-receiver'
      - '--enable-feature=exemplar-storage'
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/alert.rules.yml:/etc/prometheus/alert.rules.yml:ro
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:9090/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ============================================
  # Loki (Logs Storage)
  # ============================================
  loki:
    image: grafana/loki:2.9.0
    container_name: patra-loki
    command: -config.file=/etc/loki/loki-config.yaml
    volumes:
      - ./loki/loki-config.yaml:/etc/loki/loki-config.yaml:ro
      - loki_data:/loki
    ports:
      - "3100:3100"
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3100/ready"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ============================================
  # Tempo (Traces Storage)
  # ============================================
  tempo:
    image: grafana/tempo:2.4.0
    container_name: patra-tempo
    command: ["-config.file=/etc/tempo/tempo-config.yaml"]
    volumes:
      - ./tempo/tempo-config.yaml:/etc/tempo/tempo-config.yaml:ro
      - tempo_data:/var/tempo
    ports:
      - "3200:3200"   # Tempo HTTP
      - "9095:9095"   # Tempo gRPC (internal)
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3200/ready"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ============================================
  # Grafana (Visualization)
  # ============================================
  grafana:
    image: grafana/grafana:10.4.0
    container_name: patra-grafana
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/grafana.ini:/etc/grafana/grafana.ini:ro
      - grafana_data:/var/lib/grafana
    ports:
      - "3000:3000"
    environment:
      TZ: Asia/Shanghai
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: patra123
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    depends_on:
      prometheus:
        condition: service_healthy
      loki:
        condition: service_healthy
      tempo:
        condition: service_healthy
    restart: unless-stopped

  # ============================================
  # Alertmanager (Alert Routing)
  # ============================================
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: patra-alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - alertmanager_data:/alertmanager
    ports:
      - "9093:9093"
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:9093/-/healthy"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  prometheus_data:
  loki_data:
  tempo_data:
  grafana_data:
  alertmanager_data:

networks:
  default:
    name: patra-net
```

## OpenTelemetry Collector 配置

### otel-collector-config.yaml

```yaml
# OpenTelemetry Collector 配置
# 接收 OTLP 数据，处理后导出到 Prometheus/Loki/Tempo

receivers:
  # OTLP 接收器
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  # 批处理（优化性能）
  batch:
    timeout: 5s
    send_batch_size: 1000
    send_batch_max_size: 1500

  # 内存限制（防止 OOM）
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

  # 资源属性处理
  resource:
    attributes:
      - key: service.namespace
        value: patra
        action: upsert

  # Tail Sampling（保留错误和慢请求）
  tail_sampling:
    decision_wait: 10s
    num_traces: 100000
    expected_new_traces_per_sec: 100
    policies:
      # 始终保留错误
      - name: errors
        type: status_code
        status_code:
          status_codes: [ERROR]
      # 始终保留慢请求（>1s）
      - name: slow-traces
        type: latency
        latency:
          threshold_ms: 1000
      # 正常请求 10% 采样
      - name: normal-sampling
        type: probabilistic
        probabilistic:
          sampling_percentage: 10

  # 属性处理
  attributes:
    actions:
      # 移除敏感属性
      - key: http.request.header.authorization
        action: delete
      - key: db.statement
        action: hash

exporters:
  # Prometheus Remote Write
  prometheusremotewrite:
    endpoint: http://prometheus:9090/api/v1/write
    resource_to_telemetry_conversion:
      enabled: true

  # Loki（日志）
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
    labels:
      resource:
        service.name: "service"
        service.namespace: "namespace"
        deployment.environment: "environment"
      attributes:
        level: ""
        severity_text: "level"

  # OTLP（Tempo）
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

  # Debug（开发调试）
  debug:
    verbosity: basic

extensions:
  # 健康检查
  health_check:
    endpoint: 0.0.0.0:13133

  # Collector 自身指标
  pprof:
    endpoint: 0.0.0.0:1888

  # zPages（调试页面）
  zpages:
    endpoint: 0.0.0.0:55679

service:
  extensions: [health_check, pprof, zpages]

  pipelines:
    # Traces Pipeline
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource, tail_sampling, attributes]
      exporters: [otlp/tempo]

    # Metrics Pipeline
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [prometheusremotewrite]

    # Logs Pipeline
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [loki]

  telemetry:
    logs:
      level: info
    metrics:
      address: 0.0.0.0:8888
```

## 存储层配置

### Prometheus 配置

**prometheus/prometheus.yml**

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: patra-local
    env: dev

# Alertmanager 配置
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

# 告警规则文件
rule_files:
  - /etc/prometheus/alert.rules.yml

# 抓取配置
scrape_configs:
  # Prometheus 自身指标
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # OTel Collector 指标
  - job_name: 'otel-collector'
    static_configs:
      - targets: ['otel-collector:8888']

  # Spring Boot Actuator（直接抓取）
  - job_name: 'patra-services'
    scrape_interval: 30s
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - 'patra-gateway:8080'
          - 'patra-registry:8081'
          - 'patra-ingest:8082'
          - 'patra-catalog:8083'
        labels:
          namespace: patra
```

**prometheus/alert.rules.yml**

```yaml
groups:
  - name: patra-alerts
    rules:
      # 高错误率告警
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)
          /
          sum(rate(http_server_requests_seconds_count[5m])) by (service)
          > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.service }} 错误率过高"
          description: "错误率: {{ $value | humanizePercentage }}"

      # 高延迟告警
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "服务 {{ $labels.service }} P99 延迟过高"
          description: "P99 延迟: {{ $value | humanizeDuration }}"

      # 服务下线告警
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.job }} 不可用"
          description: "实例 {{ $labels.instance }} 已下线"
```

### Loki 配置

**loki/loki-config.yaml**

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

storage_config:
  filesystem:
    directory: /loki/storage

limits_config:
  retention_period: 168h  # 7 天
  ingestion_rate_mb: 10
  ingestion_burst_size_mb: 20
  max_streams_per_user: 10000
  max_line_size: 256kb

compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m
  retention_enabled: true
  retention_delete_delay: 2h
  retention_delete_worker_count: 150

analytics:
  reporting_enabled: false
```

### Tempo 配置

**tempo/tempo-config.yaml**

```yaml
server:
  http_listen_port: 3200
  grpc_listen_port: 9095

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 168h  # 7 天

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal

query_frontend:
  search:
    duration_slo: 5s
    throughput_bytes_slo: 1.073741824e+09

metrics_generator:
  registry:
    external_labels:
      source: tempo
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]
```

## Alertmanager 配置

**alertmanager/alertmanager.yml**

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity', 'service']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'

  routes:
    # 严重告警：立即通知
    - match:
        severity: critical
      receiver: 'critical'
      group_wait: 10s
      repeat_interval: 1h

    # 警告告警：正常节奏
    - match:
        severity: warning
      receiver: 'warning'

receivers:
  # 默认接收器（Webhook）
  - name: 'default'
    webhook_configs:
      - url: 'http://host.docker.internal:8080/api/alerts/webhook'
        send_resolved: true

  # 严重告警接收器
  - name: 'critical'
    webhook_configs:
      - url: 'http://host.docker.internal:8080/api/alerts/webhook'
        send_resolved: true
    # 可扩展：邮件、飞书、钉钉等
    # email_configs:
    #   - to: 'ops@example.com'
    #     from: 'alertmanager@example.com'

  # 警告告警接收器
  - name: 'warning'
    webhook_configs:
      - url: 'http://host.docker.internal:8080/api/alerts/webhook'
        send_resolved: true

# 抑制规则
inhibit_rules:
  # critical 告警触发时，抑制同服务的 warning
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'service']

# 静默规则（维护窗口等）
# 通过 Alertmanager UI 或 API 配置
```

## 资源限制

### 开发环境资源分配

| 服务 | CPU Limit | Memory Limit | 说明 |
|------|-----------|--------------|------|
| otel-collector | 0.5 | 512MB | 处理层，需要一定内存缓冲 |
| prometheus | 0.5 | 1GB | 时序数据库，内存敏感 |
| loki | 0.25 | 256MB | 日志索引，相对轻量 |
| tempo | 0.25 | 256MB | Trace 存储，相对轻量 |
| grafana | 0.25 | 256MB | 可视化，按需分配 |
| alertmanager | 0.1 | 64MB | 告警路由，极轻量 |
| **合计** | **~2 核** | **~2.3GB** | 符合 4GB 限制 |

### Docker Compose 资源配置

```yaml
services:
  otel-collector:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          cpus: '0.1'
          memory: 128M

  prometheus:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 1G
        reservations:
          cpus: '0.1'
          memory: 256M

  loki:
    deploy:
      resources:
        limits:
          cpus: '0.25'
          memory: 256M
        reservations:
          cpus: '0.05'
          memory: 64M

  tempo:
    deploy:
      resources:
        limits:
          cpus: '0.25'
          memory: 256M
        reservations:
          cpus: '0.05'
          memory: 64M

  grafana:
    deploy:
      resources:
        limits:
          cpus: '0.25'
          memory: 256M
        reservations:
          cpus: '0.05'
          memory: 64M

  alertmanager:
    deploy:
      resources:
        limits:
          cpus: '0.1'
          memory: 64M
        reservations:
          cpus: '0.02'
          memory: 32M
```

## 相关链接

- 上一章：[[04-otel-integration|OTel 集成方案]]
- 下一章：[[06-grafana-visualization|Grafana 可视化]]
- 索引：[[_MOC|可观测性系统设计]]
