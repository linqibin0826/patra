---
title: 可观测性系统设计 - 基础设施部署
type: design
status: completed
date: 2025-12-01
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
├── docker-compose.yaml               # 核心服务（MySQL, Redis, Nacos, MinIO）
├── docker-compose.observability.yaml # 可观测性服务
├── otel-collector/
│   └── config.yaml                   # OTel Collector 配置
├── prometheus/
│   └── prometheus.yml                # Prometheus 配置
├── loki/
│   └── loki-config.yaml              # Loki 配置
├── tempo/
│   └── tempo.yaml                    # Tempo 配置
├── grafana/
│   ├── grafana.ini                   # Grafana 主配置
│   └── provisioning/
│       ├── datasources/
│       │   └── datasources.yaml      # 数据源自动配置
│       └── dashboards/
│           ├── dashboards.yaml       # 仪表盘加载配置
│           └── patra/*.json          # 预置仪表盘
├── alertmanager/
│   └── alertmanager.yml              # Alertmanager 配置
└── otel-agent/
    ├── opentelemetry-javaagent.jar   # OTel Java Agent
    └── otel-dev.properties           # Agent 开发环境配置
```

### 版本矩阵（2025-12-01）

| 组件 | 版本 | 说明 |
|------|------|------|
| OTel Collector | 0.140.1 | opentelemetry-collector-contrib |
| Prometheus | v3.7.3 | 时序数据库，支持 OTLP 和 Native Histograms |
| Loki | 3.6.2 | 日志聚合，原生支持 OTLP |
| Tempo | 2.9.0 | 分布式链路存储 |
| Grafana | 12.3.0 | 统一可视化 |
| Alertmanager | v0.29.0 | 告警路由 |

### docker-compose.observability.yaml

> [!note] Docker Compose 格式
> 使用 Docker Compose V2 格式（无 `version` 字段），通过 `name` 指定项目名称。
> 数据卷使用主机路径 `${HOME}/.patra/docker/` 以便持久化和调试。

```yaml
# 可观测性服务 - OpenTelemetry + Grafana Stack
# 统一的 Metrics/Logs/Traces 采集平台
#
# 端口分配:
# - 4317: OTel Collector gRPC (OTLP)
# - 4318: OTel Collector HTTP (OTLP)
# - 8889: OTel Collector Prometheus metrics
# - 9090: Prometheus Web UI
# - 3100: Loki Push/Query API
# - 3200: Tempo Query API
# - 3000: Grafana Web UI
# - 9093: Alertmanager Web UI

name: patra

services:
  # ============================================
  # OpenTelemetry Collector - 遥测数据中枢
  # ============================================
  # 职责：接收 OTLP 格式的 Traces/Metrics/Logs，路由到对应后端
  # 架构：应用 → (OTLP) → Collector → Tempo/Prometheus/Loki
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.140.1
    container_name: patra-otel-collector
    restart: unless-stopped
    command: ["--config=/etc/otelcol/config.yaml"]
    volumes:
      - ./otel-collector/config.yaml:/etc/otelcol/config.yaml:ro
    ports:
      - "4317:4317"   # OTLP gRPC - 应用主要使用此端口
      - "4318:4318"   # OTLP HTTP - 备用
      - "8889:8889"   # Prometheus metrics 暴露（供 Prometheus 抓取）
    environment:
      TZ: Asia/Shanghai
    # 注意：otel/opentelemetry-collector-contrib 是 FROM scratch 镜像
    # 没有 shell、curl、wget 等工具，无法使用传统 healthcheck
    # 可通过宿主机 curl http://localhost:13133 手动验证健康状态
    deploy:
      resources:
        limits:
          memory: 2G    # 处理高吞吐量 OTLP 数据，含发送队列缓冲
    depends_on:
      tempo:
        condition: service_started
      loki:
        condition: service_started

  # ============================================
  # Prometheus - 指标存储与查询
  # ============================================
  # 职责：从 OTel Collector 抓取指标，提供 PromQL 查询
  # 架构：OTel Collector :8889 ← (Scrape) ← Prometheus
  prometheus:
    image: prom/prometheus:v3.7.3
    container_name: patra-prometheus
    restart: unless-stopped
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"
      - "--storage.tsdb.retention.time=7d"            # 数据保留 7 天
      - "--web.enable-lifecycle"                       # 启用热重载 /-/reload
      - "--web.enable-remote-write-receiver"           # 启用 Remote Write 接收（Tempo 使用）
      - "--enable-feature=exemplar-storage"            # 启用 Exemplar 存储（Trace 关联）
      - "--enable-feature=native-histograms"           # 启用 Native Histogram（OTel 兼容）
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ${HOME}/.patra/docker/prometheus/data:/prometheus
    ports:
      - "9090:9090"
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:9090/-/healthy || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 10s
    deploy:
      resources:
        limits:
          memory: 512M   # 7 天数据保留，512M 足够开发环境

  # ============================================
  # Loki - 日志聚合存储
  # ============================================
  # 职责：接收 OTLP 格式日志，提供 LogQL 查询
  # 架构：OTel Collector → (OTLP/HTTP) → Loki
  loki:
    image: grafana/loki:3.6.2
    container_name: patra-loki
    restart: unless-stopped
    command: ["-config.file=/etc/loki/loki-config.yaml"]
    volumes:
      - ./loki/loki-config.yaml:/etc/loki/loki-config.yaml:ro
      - ${HOME}/.patra/docker/loki/data:/loki
    ports:
      - "3100:3100"
    environment:
      TZ: Asia/Shanghai
    # 注意：grafana/loki 是 FROM scratch 镜像，没有 shell
    # 可通过宿主机 curl http://localhost:3100/ready 验证
    deploy:
      resources:
        limits:
          memory: 512M

  # ============================================
  # Tempo - 分布式链路存储
  # ============================================
  # 职责：接收 OTLP 格式链路数据，提供 TraceQL 查询
  # 架构：OTel Collector → (OTLP/gRPC) → Tempo
  tempo:
    image: grafana/tempo:2.9.0
    container_name: patra-tempo
    restart: unless-stopped
    command: ["-config.file=/etc/tempo/tempo.yaml"]
    volumes:
      - ./tempo/tempo.yaml:/etc/tempo/tempo.yaml:ro
      - ${HOME}/.patra/docker/tempo/data:/var/tempo
    ports:
      - "3200:3200"   # Tempo Query API（Grafana 使用）
      - "9095:9095"   # Tempo internal gRPC
    environment:
      TZ: Asia/Shanghai
    # 注意：grafana/tempo 是 FROM scratch 镜像，没有 shell
    # 可通过宿主机 curl http://localhost:3200/ready 验证
    deploy:
      resources:
        limits:
          memory: 512M

  # ============================================
  # Grafana - 统一可视化
  # ============================================
  # 职责：连接 Prometheus/Loki/Tempo，提供统一查询和仪表盘
  grafana:
    image: grafana/grafana:12.3.0
    container_name: patra-grafana
    restart: unless-stopped
    volumes:
      - ./grafana/grafana.ini:/etc/grafana/grafana.ini:ro
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ${HOME}/.patra/docker/grafana/data:/var/lib/grafana
    ports:
      - "3000:3000"
    environment:
      TZ: Asia/Shanghai
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: patra123
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:3000/api/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 15s
    deploy:
      resources:
        limits:
          memory: 256M
    depends_on:
      prometheus:
        condition: service_healthy
      loki:
        condition: service_started
      tempo:
        condition: service_started

  # ============================================
  # Alertmanager - 告警路由
  # ============================================
  # 职责：接收 Prometheus 告警，按规则路由到通知渠道
  alertmanager:
    image: prom/alertmanager:v0.29.0
    container_name: patra-alertmanager
    restart: unless-stopped
    command:
      - "--config.file=/etc/alertmanager/alertmanager.yml"
      - "--storage.path=/alertmanager"
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - ${HOME}/.patra/docker/alertmanager/data:/alertmanager
    ports:
      - "9093:9093"
    environment:
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:9093/-/healthy || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 10s
    deploy:
      resources:
        limits:
          memory: 128M

networks:
  default:
    name: patra-net
    external: true
```

## OpenTelemetry Collector 配置

> [!important] 核心架构
> OTel Collector 作为遥测数据中枢，接收来自应用的 OTLP 数据，处理后路由到存储后端：
> - **Traces** → Tempo（OTLP/gRPC）
> - **Metrics** → Prometheus（Pull/Scrape :8889）
> - **Logs** → Loki（OTLP/HTTP）

### config.yaml

```yaml
# ============================================
# OpenTelemetry Collector 配置
# ============================================
# 接收 OTLP 格式的 Traces/Metrics/Logs，路由到对应后端
#
# 数据流：
#   应用 (OTel Agent)
#     → OTLP (4317/4318)
#     → Collector
#     → Tempo/Prometheus/Loki

# --------------------------------------------
# Connectors - 信号转换器
# --------------------------------------------
# Connector 既是 exporter 又是 receiver，用于在 pipeline 之间转换数据
connectors:
  # Spanmetrics Connector - 从 Traces 生成 RED 指标
  # RED = Rate (请求速率) + Errors (错误率) + Duration (延迟分布)
  # 生成的指标：
  #   - traces_spanmetrics_calls_total (Counter) - 请求总数
  #   - traces_spanmetrics_duration_bucket (Histogram) - 延迟分布
  spanmetrics:
    # 指标命名空间，最终变成 patra_traces_spanmetrics_*
    namespace: traces.spanmetrics
    # 直方图桶配置 - 覆盖毫秒到秒级延迟
    histogram:
      explicit:
        buckets: [5ms, 10ms, 25ms, 50ms, 100ms, 250ms, 500ms, 1s, 2.5s, 5s, 10s]
    # 额外维度 - 从 span attributes 提取
    dimensions:
      - name: http.method      # GET/POST/PUT/DELETE
      - name: http.status_code # 200/400/500
      - name: http.route       # /api/users/{id}
    # 启用 Exemplar - 关联到具体 trace
    exemplars:
      enabled: true
    # 维度缓存大小 - 防止高基数问题
    dimensions_cache_size: 1000
    # 使用累积时间聚合（Prometheus 兼容）
    aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE

# --------------------------------------------
# Receivers - 数据接收器
# --------------------------------------------
receivers:
  # OTLP 接收器 - 接收来自应用的遥测数据
  # 支持 gRPC (4317) 和 HTTP (4318) 两种协议
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317    # OTel Agent 默认使用此端口
      http:
        endpoint: 0.0.0.0:4318    # 备用，某些客户端偏好 HTTP

# --------------------------------------------
# Processors - 数据处理器
# --------------------------------------------
# 处理器按声明顺序执行，顺序很重要！
processors:
  # 批处理 - 提高吞吐量，减少网络开销
  # 将多个小请求合并为大批次发送
  batch:
    timeout: 5s              # 最长等待时间
    send_batch_size: 1000    # 触发发送的记录数
    send_batch_max_size: 1500 # 单批最大记录数

  # 内存限制 - 防止 OOM（Out of Memory）
  # 核心参数说明：
  #   - limit_mib: 硬限制，容器内存的 ~90%（2GB ≈ 1800MB）
  #   - spike_limit_mib: 峰值缓冲，软限制 = limit - spike
  #   - 当内存达到软限制 (1500MB) 时开始拒绝数据
  #   - 当内存达到硬限制 (1800MB) 时强制拒绝所有数据
  memory_limiter:
    check_interval: 1s       # 内存检查频率
    limit_mib: 1800          # 硬限制 ~90% 容器内存 (2GB)
    spike_limit_mib: 300     # 峰值缓冲，预留给突发流量

  # 资源处理器 - 添加通用属性
  # 为所有遥测数据添加环境和 Collector 标识
  resource:
    attributes:
      - key: environment
        value: development
        action: upsert         # 存在则更新，不存在则插入
      - key: collector.name
        value: patra-otel-collector
        action: upsert

# --------------------------------------------
# Exporters - 数据导出器
# --------------------------------------------
exporters:
  # 调试导出器 - 开发环境日志输出
  # 生产环境应移除或设置更高的采样率
  debug:
    verbosity: basic         # basic/normal/detailed
    sampling_initial: 5      # 启动后前 N 条全量输出
    sampling_thereafter: 200 # 之后每 N 条输出一次

  # Prometheus 导出器 - Pull 模式
  # Prometheus 主动抓取此端口的指标
  # 注意：这是 Pull 模式，不是 Remote Write
  prometheus:
    endpoint: 0.0.0.0:8889   # Prometheus 抓取端点
    namespace: patra         # 指标前缀，所有指标变成 patra_*
    enable_open_metrics: true # 启用 OpenMetrics 格式（支持 Exemplar）
    resource_to_telemetry_conversion:
      enabled: true          # 将 resource attributes 转为 metric labels

  # OTLP/gRPC 导出器 - 链路数据发送到 Tempo
  # sending_queue: 异步发送，防止下游阻塞导致 Collector 内存积压
  # retry_on_failure: 网络抖动时自动重试，避免数据丢失
  otlp/tempo:
    endpoint: tempo:4317     # Tempo 的 OTLP gRPC 端口
    tls:
      insecure: true         # 开发环境不使用 TLS
    sending_queue:
      enabled: true
      num_consumers: 4       # 并发消费者数（加快发送速度）
      queue_size: 100        # 队列大小（缓冲突发流量）
    retry_on_failure:
      enabled: true
      initial_interval: 5s   # 首次重试间隔
      max_interval: 30s      # 最大重试间隔（指数退避上限）
      max_elapsed_time: 120s # 最长重试时间（超过则丢弃）

  # OTLP/HTTP 导出器 - 日志发送到 Loki
  # Loki 3.x 原生支持 OTLP，无需 loki exporter
  otlphttp/loki:
    endpoint: http://loki:3100/otlp
    tls:
      insecure: true
    sending_queue:
      enabled: true
      num_consumers: 4
      queue_size: 100
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 120s

# --------------------------------------------
# Extensions - 扩展功能
# --------------------------------------------
extensions:
  # 健康检查端点
  health_check:
    endpoint: 0.0.0.0:13133
    path: /

  # pprof 性能分析（调试用）
  pprof:
    endpoint: 0.0.0.0:1777

  # zpages 调试页面（调试用）
  # 访问 http://localhost:55679/debug/tracez
  zpages:
    endpoint: 0.0.0.0:55679

# --------------------------------------------
# Service - 服务配置
# --------------------------------------------
service:
  extensions: [health_check, pprof, zpages]

  pipelines:
    # 链路管道
    # traces → Tempo + spanmetrics connector（生成 RED 指标）
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlp/tempo, spanmetrics, debug]

    # 指标管道
    # 接收应用指标 + spanmetrics 生成的指标 → Prometheus 抓取
    metrics:
      receivers: [otlp, spanmetrics]  # spanmetrics 作为 receiver
      processors: [memory_limiter, resource, batch]
      exporters: [prometheus, debug]

    # 日志管道
    logs:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlphttp/loki, debug]

  # Collector 自身遥测
  telemetry:
    logs:
      level: info
    metrics:
      level: detailed
      readers:
        - pull:
            exporter:
              prometheus:
                host: "0.0.0.0"
                port: 8888       # Collector 自身指标端口
```

## 存储层配置

### Prometheus 配置

> [!note] Pull vs Push
> Prometheus 使用 **Pull 模式**从 OTel Collector 的 :8889 端口抓取指标。
> 不使用 Remote Write（Push 模式），因为 Pull 模式更易于调试和监控。

**prometheus/prometheus.yml**

```yaml
# ============================================
# Prometheus 配置
# ============================================
# 从 OTel Collector 抓取指标，启用 Exemplar 存储
#
# 数据流：
#   OTel Collector :8889 ← (Scrape) ← Prometheus
#   Tempo → (Remote Write) → Prometheus（metrics_generator 生成的指标）

global:
  scrape_interval: 15s       # 默认抓取间隔
  evaluation_interval: 15s   # 规则评估间隔
  external_labels:           # 外部标签（联邦/远程写入时使用）
    monitor: patra-prometheus
    environment: development

# Alertmanager 配置
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

# 抓取配置
scrape_configs:
  # Prometheus 自监控
  - job_name: prometheus
    static_configs:
      - targets: ["localhost:9090"]

  # OTel Collector 导出的应用指标
  # 这是主要的指标来源，包含所有应用的 Metrics
  - job_name: otel-collector
    static_configs:
      - targets: ["otel-collector:8889"]
    # 启用 Exemplar 采集（关联到 Trace）
    enable_http2: true

  # OTel Collector 内部指标
  # 用于监控 Collector 自身的健康状态
  - job_name: otel-collector-internal
    static_configs:
      - targets: ["otel-collector:8888"]

# 远程写入配置（可选，用于长期存储）
# remote_write:
#   - url: http://mimir:9009/api/v1/push

# Exemplar 存储配置
# 通过命令行参数 --enable-feature=exemplar-storage 启用
```

### Loki 配置

> [!note] OTLP 原生支持
> Loki 3.x 原生支持 OTLP 协议，无需 OTel Collector 使用旧版 `loki` exporter。
> 使用 `otlphttp/loki` exporter 直接推送到 `/otlp` 端点。

**loki/loki-config.yaml**

```yaml
# ============================================
# Loki 配置 - 单机模式（开发环境）
# ============================================
# 接收来自 OTel Collector 的 OTLP 格式日志
#
# 数据流：
#   OTel Collector → (OTLP/HTTP) → Loki :3100/otlp

# 禁用多租户认证（开发环境）
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
  log_level: info
  # gRPC 消息大小限制
  # 解决 OTLP 日志查询 ResourceExhausted 错误
  # OTLP 日志携带结构化元数据，查询响应会显著膨胀
  # 压缩存储 128KB → gRPC 响应 ~8MB
  grpc_server_max_recv_msg_size: 33554432  # 32MB
  grpc_server_max_send_msg_size: 33554432  # 32MB

# 查询前端配置（gRPC 消息限制）
frontend:
  grpc_client_config:
    max_recv_msg_size: 33554432  # 32MB
    max_send_msg_size: 33554432  # 32MB

# 前端 Worker 配置
frontend_worker:
  grpc_client_config:
    max_recv_msg_size: 33554432  # 32MB
    max_send_msg_size: 33554432  # 32MB

# 通用配置
common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1        # 单机模式
  ring:
    kvstore:
      store: inmemory          # 单机使用内存存储

# 查询结果缓存
query_range:
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100       # 缓存大小

# Schema 配置
schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb              # 使用 TSDB 存储引擎
      object_store: filesystem
      schema: v13              # 最新 schema 版本
      index:
        prefix: index_
        period: 24h            # 索引分片周期

# Ruler 配置（告警规则）
ruler:
  alertmanager_url: http://alertmanager:9093

# 限制配置
limits_config:
  retention_period: 168h       # 日志保留 7 天
  allow_structured_metadata: true  # 允许 OTLP 结构化元数据
  max_line_size: 256kb         # 单行日志最大长度
  ingestion_rate_mb: 10        # 每租户每秒日志量
  ingestion_burst_size_mb: 20  # 突发日志量
  max_entries_limit_per_query: 5000  # 单次查询最大条目

# 压缩器配置
compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m     # 压缩间隔
  retention_enabled: true      # 启用保留期清理
  retention_delete_delay: 2h   # 删除延迟
  retention_delete_worker_count: 150
  delete_request_store: filesystem

# 分析（遥测）配置
analytics:
  reporting_enabled: false     # 禁用 Grafana 遥测
```

### Tempo 配置

> [!note] Metrics Generator
> Tempo 内置 Metrics Generator，可从 traces 生成 service graphs 和 span metrics。
> 生成的指标通过 Remote Write 推送到 Prometheus。

**tempo/tempo.yaml**

```yaml
# ============================================
# Tempo 配置 - 单机模式（开发环境）
# ============================================
# 接收来自 OTel Collector 的 OTLP 格式链路数据
#
# 数据流：
#   OTel Collector → (OTLP/gRPC) → Tempo :4317

server:
  http_listen_port: 3200       # Query API（Grafana 使用）
  grpc_listen_port: 9095
  log_level: info

# 分布式追踪接收器
# 接收来自 OTel Collector 的 OTLP 数据
distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317

# Ingester 配置
ingester:
  max_block_duration: 5m       # 块最大持续时间

# 压缩器配置
compactor:
  compaction:
    block_retention: 168h      # 链路数据保留 7 天

# Metrics Generator - 从 traces 生成指标
# 生成的指标推送到 Prometheus
metrics_generator:
  registry:
    external_labels:
      source: tempo
      environment: development
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      # 将生成的指标推送到 Prometheus
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true   # 发送 Exemplar（关联到 Trace）
  traces_storage:
    path: /var/tempo/generator/traces
  # 处理器配置
  processor:
    span_metrics:              # Span 级别指标
      dimensions:
        - service.name
        - http.method
        - http.status_code
    service_graphs:            # 服务调用图
      dimensions:
        - service.name

# 存储配置
storage:
  trace:
    backend: local             # 本地文件存储
    wal:
      path: /var/tempo/wal
    local:
      path: /var/tempo/blocks
    pool:
      max_workers: 100
      queue_depth: 10000

# Querier 配置
querier:
  frontend_worker:
    frontend_address: localhost:9095

# 覆盖配置
overrides:
  defaults:
    metrics_generator:
      # 启用的处理器
      processors: [service-graphs, span-metrics, local-blocks]
```

### Alertmanager 配置

**alertmanager/alertmanager.yml**

```yaml
# ============================================
# Alertmanager 配置
# ============================================
# 接收 Prometheus 告警，按规则路由到通知渠道
#
# 开发环境：所有 receiver 仅为占位符，实际通知渠道未配置

global:
  resolve_timeout: 5m          # 告警自动解决超时

# 告警路由
route:
  group_by: ['alertname', 'service']  # 分组键
  group_wait: 30s              # 分组等待时间（等待同组告警）
  group_interval: 5m           # 分组发送间隔
  repeat_interval: 4h          # 重复发送间隔
  receiver: 'default-receiver' # 默认接收者

  # 子路由
  routes:
    # 严重告警 - 立即发送
    - match:
        severity: critical
      receiver: 'critical-receiver'
      group_wait: 10s          # 快速发送
      repeat_interval: 1h      # 频繁提醒

    # 警告级别
    - match:
        severity: warning
      receiver: 'warning-receiver'
      group_wait: 1m
      repeat_interval: 6h

# 接收者配置
# 开发环境仅为占位符，实际部署时配置 webhook/email/slack
receivers:
  - name: 'default-receiver'
    # webhook_configs:
    #   - url: 'http://localhost:5001/webhook'

  - name: 'critical-receiver'
    # webhook_configs:
    #   - url: 'http://your-webhook-server/critical'
    # email_configs:
    #   - to: 'oncall@example.com'

  - name: 'warning-receiver'
    # webhook_configs:
    #   - url: 'http://your-webhook-server/warning'

# 抑制规则 - 防止告警风暴
inhibit_rules:
  # 当存在严重告警时，抑制同服务的警告告警
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'service']
```

## 资源限制

### 开发环境资源分配

| 服务 | Memory Limit | 说明 |
|------|--------------|------|
| otel-collector | 2GB | 处理高吞吐量 OTLP 数据，含发送队列缓冲 |
| prometheus | 512MB | 时序数据库，7 天数据保留 |
| loki | 512MB | 日志索引与查询，支持 OTLP 结构化元数据 |
| tempo | 512MB | 链路存储，包含 Metrics Generator |
| grafana | 256MB | 可视化界面 |
| alertmanager | 128MB | 告警路由，极轻量 |
| **合计** | **~3.9GB** | 适合 8GB+ 内存的开发机器 |

> [!tip] 内存调优
> 如果 OTel Collector 频繁出现 `data refused due to high memory usage` 错误，
> 说明 `memory_limiter` 的软限制被触发。解决方案：
> 1. **增加容器内存**：调整 docker-compose 中的 `memory` 限制
> 2. **调整 memory_limiter**：`limit_mib` 应为容器内存的 ~90%
> 3. **启用 sending_queue**：为 exporter 添加异步队列，防止下游阻塞积压
> 4. **检查数据源**：排查是否有异常的高吞吐量数据源

## 相关链接

- 上一章：[[04-otel-integration|OTel 集成方案]]
- 下一章：[[06-grafana-visualization|Grafana 可视化]]
- 索引：[[_MOC|可观测性系统设计]]
