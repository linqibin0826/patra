---
title: 可观测性系统设计 - 实现路线图
type: design
status: completed
date: 2025-11-29
module: patra-spring-boot-starter-observability
related_adrs: [ADR-005]
tags:
  - design/roadmap
  - tech/opentelemetry
---

# 实现路线图

## 实施阶段概览

```mermaid
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#3b82f6',
    'primaryTextColor': '#1e293b',
    'primaryBorderColor': '#1d4ed8',
    'lineColor': '#64748b',
    'sectionBkgColor': '#f1f5f9',
    'taskBkgColor': '#dcfce7',
    'taskTextColor': '#1e293b',
    'gridColor': '#e2e8f0',
    'doneTaskBkgColor': '#22c55e'
  }
}}%%
gantt
    title 可观测性系统实施路线图（已完成）
    dateFormat YYYY-MM-DD
    axisFormat %m-%d

    section Phase 1
    基础设施搭建           :done, p1, 2025-11-25, 2d
    OTel Collector 配置    :done, p1a, after p1, 1d
    Grafana Stack 部署     :done, p1b, after p1a, 1d

    section Phase 2
    Starter 模块实现       :done, p2, after p1b, 2d
    Logback Converter      :done, p2a, after p2, 1d

    section Phase 3
    服务集成               :done, p3, after p2a, 1d
    端到端验证             :done, p3a, after p3, 1d

    section Phase 4
    仪表盘开发             :done, p4, after p3a, 1d
    文档完善               :done, p4a, after p4, 1d
```

## Phase 1：基础设施搭建 ✓

### 目标

搭建完整的可观测性基础设施，包括 OTel Collector、Prometheus、Loki、Tempo、Grafana。

### 任务清单

| 任务 | 产出物 | 状态 |
|------|--------|------|
| 创建 Docker Compose 配置 | `docker/docker-compose.observability.yaml` | ✅ |
| 配置 OTel Collector | `docker/otel-collector/config.yaml` | ✅ |
| 配置 Prometheus | `docker/prometheus/prometheus.yml` | ✅ |
| 配置 Loki | `docker/loki/loki-config.yaml` | ✅ |
| 配置 Tempo | `docker/tempo/tempo.yaml` | ✅ |
| 配置 Grafana Provisioning | `docker/grafana/provisioning/*` | ✅ |

### 目录结构

```
docker/
├── docker-compose.observability.yaml
├── otel-agent/
│   └── opentelemetry-javaagent.jar     # OTel Java Agent
├── otel-collector/
│   └── config.yaml
├── prometheus/
│   ├── prometheus.yml
│   └── rules/
│       └── alerts.yaml
├── loki/
│   └── loki-config.yaml
├── tempo/
│   └── tempo.yaml
├── alertmanager/
│   └── alertmanager.yml
└── grafana/
    ├── grafana.ini
    └── provisioning/
        ├── datasources/
        │   └── datasources.yaml
        ├── dashboards/
        │   └── dashboards.yaml
        └── alerting/
            └── alerting.yaml
```

### 验证命令

```bash
# 1. 启动基础设施
docker compose -f docker/docker-compose.observability.yaml up -d

# 2. 验证服务健康
curl http://localhost:4318/v1/traces  # OTel Collector (HTTP)
curl http://localhost:9090/-/healthy   # Prometheus
curl http://localhost:3100/ready       # Loki
curl http://localhost:3200/ready       # Tempo
curl http://localhost:3000/api/health  # Grafana
```

## Phase 2：Starter 模块实现 ✓

### 目标

实现 `patra-spring-boot-starter-observability` 模块，采用 OTel Agent + Micrometer 混合模式。

### 架构决策

| 职责 | 方案 | 说明 |
|------|------|------|
| Tracing | OTel Java Agent | 通过 `-javaagent` JVM 参数注入，零代码侵入 |
| Metrics | OTel Agent + Micrometer Bridge | Micrometer 指标通过 Agent 桥接，统一走 OTLP 导出到 OTel Collector |
| Logging | Agent MDC 注入 + 自定义 Converter | Agent 自动注入 `trace_id`/`span_id` 到 MDC |

### 任务清单

| 任务 | 产出物 | 状态 |
|------|--------|------|
| 核心自动配置 | `ObservabilityAutoConfiguration.java` | ✅ |
| Micrometer 配置 | `MicrometerAutoConfiguration.java`（含 OTel Bridge） | ✅ |
| 拦截器配置 | `ObservationInterceptorsAutoConfiguration.java` | ✅ |
| 配置属性 | `ObservabilityProperties.java` | ✅ |
| MeterFilter | `HighCardinalityMeterFilter`、`CommonTagsMeterFilter`（前缀由 Collector 添加） | ✅ |
| 自定义 Logback Converter | `TraceIdConverter`、`SpanIdConverter` | ✅ |
| Converter 单元测试 | `*ConverterTest.java` | ✅ |

> [!note] 架构简化
> ObservationHandler 和 ObservationFilter 已移除，Tracing 由 OTel Java Agent 自动处理。

### 依赖配置

```xml
<!-- 实际依赖（无 OpenTelemetry SDK） -->
<dependencies>
    <!-- Micrometer (Metrics + Observations) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-observation</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- Spring Boot Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

> [!note] Metrics 导出架构
> Metrics 通过 OTel Agent 的 Micrometer Bridge 自动桥接，无需 `micrometer-registry-prometheus`。
> Agent 配置 `otel.instrumentation.micrometer.enabled=true` 后，Micrometer 指标统一走 OTLP 导出。

> [!note] 零代码侵入
> Tracing 由 OTel Java Agent 通过 `-javaagent` 参数自动处理，无需在应用代码中引入 OTel SDK 依赖。

### 验证命令

```bash
# 1. 编译验证
mvn clean compile -pl patra-spring-boot-starter-observability

# 2. 测试验证
mvn test -pl patra-spring-boot-starter-observability

# 3. 依赖检查（不应包含 OpenTelemetry SDK）
mvn dependency:tree -pl patra-spring-boot-starter-observability | grep opentelemetry
# 期望输出：无输出（仅 Agent 运行时使用）
```

## Phase 3：服务集成验证 ✓

### 目标

将微服务接入可观测性系统，验证三信号（Metrics/Traces/Logs）完整链路。

### 任务清单

| 任务 | 涉及服务 | 状态 |
|------|----------|------|
| 下载 OTel Agent | 所有服务 | ✅ |
| 配置 JVM 参数 | 所有服务 | ✅ |
| 验证 Metrics | patra-registry | ✅ |
| 验证 Traces | patra-registry | ✅ |
| 验证 Logs | patra-registry | ✅ |

### 服务启动配置

```bash
java -javaagent:docker/otel-agent/opentelemetry-javaagent.jar \
     -Dotel.service.name=patra-registry \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.traces.sampler=parentbased_traceidratio \
     -Dotel.traces.sampler.arg=1.0 \
     -Dotel.metrics.exporter=none \
     -Dotel.logs.exporter=otlp \
     -jar patra-registry-boot.jar \
     --spring.profiles.active=dev
```

### Docker Compose 配置

```yaml
services:
  patra-registry:
    environment:
      JAVA_TOOL_OPTIONS: "-javaagent:/opt/otel/opentelemetry-javaagent.jar"
      OTEL_SERVICE_NAME: "patra-registry"
      OTEL_EXPORTER_OTLP_ENDPOINT: "http://otel-collector:4317"
      OTEL_RESOURCE_ATTRIBUTES: "service.version=0.1.0,deployment.environment=dev"
      OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
      OTEL_TRACES_SAMPLER_ARG: "1.0"
      OTEL_METRICS_EXPORTER: "none"
      OTEL_LOGS_EXPORTER: "otlp"
    volumes:
      - ./docker/otel-agent/opentelemetry-javaagent.jar:/opt/otel/opentelemetry-javaagent.jar:ro
```

### 验证场景

```mermaid
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#dbeafe',
    'primaryTextColor': '#1e293b',
    'primaryBorderColor': '#3b82f6',
    'lineColor': '#64748b',
    'actorLineColor': '#64748b',
    'signalColor': '#3b82f6',
    'signalTextColor': '#1e293b',
    'noteBkgColor': '#dcfce7',
    'noteTextColor': '#1e293b',
    'noteBorderColor': '#22c55e'
  }
}}%%
sequenceDiagram
    participant Tester as 测试人员
    participant Registry as patra-registry
    participant Grafana as Grafana

    Note over Tester,Grafana: 验证场景：完整链路追踪

    Tester->>Registry: GET /api/journals
    Registry-->>Tester: HTTP 200

    Tester->>Grafana: 检查 Tempo
    Note right of Grafana: ✅ 看到完整 Trace

    Tester->>Grafana: 检查 Metrics（Prometheus 数据源）
    Note right of Grafana: ✅ 看到 patra_http_server_requests

    Tester->>Grafana: 检查 Loki
    Note right of Grafana: ✅ 日志带 TraceId
```

## Phase 4：仪表盘与文档 ✓

### 目标

开发 Grafana 仪表盘，完成设计文档。

### 任务清单

| 任务 | 产出物 | 状态 |
|------|--------|------|
| Services Overview 仪表盘 | Grafana Dashboard | ✅ |
| 设计文档系列 | `docs/designs/observability/*` | ✅ |
| ADR 决策记录 | `ADR-005` | ✅ |

### 仪表盘验收

| 仪表盘 | 核心面板 | 状态 |
|--------|----------|------|
| Services Overview | 服务存活数、请求速率、错误率、P99 延迟 | ✅ |

## 测试策略

### 测试层级

```mermaid
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#dbeafe',
    'primaryTextColor': '#1e293b',
    'primaryBorderColor': '#3b82f6',
    'lineColor': '#64748b',
    'edgeLabelBackground': '#f1f5f9'
  }
}}%%
flowchart TD
    subgraph Unit["单元测试 ✓"]
        direction TB
        U1[TraceIdConverter 测试]
        U2[SpanIdConverter 测试]
        U3[ObservabilityProperties 绑定]
    end

    subgraph Integration["集成测试"]
        direction TB
        I1[Micrometer Bridge 桥接]
        I2[AutoConfiguration 条件装配]
    end

    Unit --> Integration

    classDef done fill:#dcfce7,stroke:#22c55e,color:#1e293b;
    classDef default fill:#dbeafe,stroke:#3b82f6,color:#1e293b;
    class U1,U2,U3 done;
```

### 已完成测试用例

| 测试类 | 测试点 | 状态 |
|--------|--------|------|
| `TraceIdConverterTest` | OTel/Micrometer MDC 键兼容 | ✅ |
| `SpanIdConverterTest` | OTel/Micrometer MDC 键兼容 | ✅ |

## 风险评估与缓解

### 风险矩阵

| 风险 | 可能性 | 影响 | 缓解措施 | 状态 |
|------|--------|------|----------|------|
| OTel Agent 性能开销 | 中 | 中 | 采样率调优 | ✅ 已验证 |
| OTLP 网络故障 | 低 | 中 | Collector 重试配置 | ✅ 已配置 |
| 配置复杂度高 | 高 | 中 | 模板化配置，详细文档 | ✅ 文档完善 |

### 采样配置

```yaml
# 开发环境：100% 采样
OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
OTEL_TRACES_SAMPLER_ARG: "1.0"

# 生产环境：10% 采样（推荐）
OTEL_TRACES_SAMPLER: "parentbased_traceidratio"
OTEL_TRACES_SAMPLER_ARG: "0.1"
```

## 回滚策略

### 快速回滚步骤

如果可观测性系统出现严重问题，可按以下步骤快速回滚：

```bash
# 1. 禁用 OTel Agent（移除 JAVA_TOOL_OPTIONS）
docker compose down
# 编辑 docker-compose.yaml，注释 JAVA_TOOL_OPTIONS
docker compose up -d

# 2. 验证服务正常
curl http://localhost:8081/actuator/health
```

### 回滚检查清单

- [x] 服务正常启动
- [x] 核心业务功能正常
- [x] 无性能异常
- [x] 日志正常输出（控制台）

## 验收标准总览

### Phase 1 验收 ✅

- [x] Docker Compose 一键启动所有组件
- [x] Grafana 自动加载所有数据源
- [x] 各组件健康检查通过

### Phase 2 验收 ✅

- [x] 基于 OTel Java Agent 模式（零代码侵入）
- [x] Micrometer 用于 Metrics 和 Observations
- [x] 自定义 Logback Converter 实现 MDC 提取
- [x] Converter 单元测试全部通过

### Phase 3 验收 ✅

- [x] OTel Agent 成功加载（日志显示 Agent 初始化）
- [x] HTTP Tracing 自动集成
- [x] 服务 Traces 可在 Tempo 查看
- [x] 日志带 TraceId（MDC 注入正常）

### Phase 4 验收 ✅

- [x] Services Overview 仪表盘可用
- [x] 设计文档系列完整
- [x] ADR-005 决策记录完成

## 相关链接

- 上一章：[[06-grafana-visualization|Grafana 可视化]]
- 下一章：[[08-version-matrix|版本矩阵]]
- 索引：[[_MOC|可观测性系统设计]]
- ADR：[[../../decisions/ADR-005-adopt-opentelemetry-grafana-stack-for-observability|ADR-005]]
