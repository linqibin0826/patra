# Patra 可观测性架构实施计划

> **版本**: 1.0.0
> **创建日期**: 2025-11-24
> **状态**: 待确认
> **实施类型**: 质量优先、保守策略、PoC 先行

---

## 📋 文档说明

**目的**: 作为可观测性架构实施的统一上下文，跟踪多轮实施的进度和决策。

**使用方式**:
- 每次实施前阅读当前进度
- 每次完成任务后更新检查清单
- 记录关键决策和变更

**关键原则**:
- ✅ 质量优先，无时间压力
- ✅ PoC 性能测试先行
- ✅ 保守策略，充分验证
- ✅ 追求架构卓越

---

## 🎯 总体目标

### 核心目标

设计并实现一个**统一的、生产级的、一步到位的**可观测性 Starter，整合 **Metrics（指标）**、**Tracing（追踪）**、**Logging（日志）** 三大支柱。

### 技术选型

| 组件 | 版本 | 理由 |
|-----|------|------|
| **Spring Boot** | 3.5.7（待验证） | 项目基础框架 |
| **Micrometer Observation** | 1.14.0+ | Spring Boot 3.x 官方推荐，统一抽象 |
| **SkyWalking Agent** | 9.5.0 | APM 完整性、性能优秀、中文友好 |
| **Prometheus** | 保留 | 备份方案、生态丰富 |

### 架构方案

**插件式架构（完全解耦）**:

```
patra-starter-core（纯净）
├─ ErrorResolutionPipeline（核心功能）
└─ ResolutionInterceptor 接口（已有扩展点）✅

patra-starter-rest-client（纯净）
├─ RestClient（核心功能）
└─ ClientInterceptor 接口（新增扩展点）✅

patra-starter-batch（纯净）
├─ Batch 配置（核心功能）
└─ JobExecutionListener（Spring Batch 原生扩展点）

patra-spring-boot-starter-observability（新建插件）
├─ ObservationResolutionInterceptor（实现 ResolutionInterceptor）
├─ RestClientObservationInterceptor（实现 ClientInterceptor）
├─ BatchObservationJobListener（实现 JobExecutionListener）
├─ SensitiveDataObservationFilter（敏感数据脱敏）🔒
└─ 所有 ObservationHandlers、MeterFilters
```

**依赖方向**:
- ✅ observability → core（单向依赖，符合 DIP）
- ✅ core ❌→ observability（无依赖，正确）

---

## 📊 现状分析

### 代码库验证结果

| 项目 | 设计文档假设 | 实际代码库状态 | 影响 |
|-----|------------|------------|------|
| **扩展点接口** | 假设需要创建 `ErrorInterceptor` | 实际已有 `ResolutionInterceptor` | ⚠️ 适配现有接口，不创建新接口 |
| **可观测性实现** | 假设未实现 | 已有 `TracingInterceptor` 和 `MetricsInterceptor` | ⚠️ 需要重构现有实现 |
| **追踪机制** | 假设使用 Micrometer | 实际使用自定义 `TraceProvider` SPI | ⚠️ 需要迁移到 Micrometer |
| **现有 Starters** | 假设有可观测性代码 | core、rest-client 确实有部分实现 | ✅ 符合预期 |

### 已有的可观测性实现

**patra-starter-core**:
```java
// 已存在的拦截器
com.patra.starter.core.error.pipeline.interceptor.TracingInterceptor
com.patra.starter.core.error.pipeline.interceptor.MetricsInterceptor

// 已存在的扩展点接口
com.patra.starter.core.error.pipeline.ResolutionInterceptor
```

**patra-starter-rest-client**:
- ❌ 未发现 ObservationRegistry 或 TracingInterceptor
- ⚠️ 需要查找是否有其他可观测性实现

**patra-starter-batch**:
- ⚠️ 待验证是否有 TODO 标记的代码

---

## 🚨 P0 级别任务（阻塞生产部署）

根据架构评审总结，必须完成以下 P0 任务才能进入生产环境：

### P0-1: 插件式架构设计
- **状态**: ✅ 已完成设计
- **文档**: `plugin-architecture-refactoring-guide.md`
- **下一步**: 实施重构

### P0-2: 防腐层设计
- **状态**: ✅ 已完成设计
- **文档**: `observability-starter-design.md` Lines 1490-1500
- **推荐**: 优先使用 Domain Events，防腐层作为备选
- **下一步**: 实施时集成 Domain Events

### P0-3: ArchUnit 架构测试
- **状态**: ❌ 待实施
- **目的**: 防止 Domain 层依赖框架代码
- **优先级**: 高（必须在实施阶段 4 完成）

### P0-4: PoC 性能测试
- **状态**: ❌ 待执行
- **范围**: 在 patra-ingest 验证 SkyWalking Agent 性能
- **批准条件**:
  - CPU 开销 < 10%
  - 内存开销 < 50MB
  - TPS 下降 < 5%
  - P99 响应时间增加 < 10%
- **优先级**: 🚨 最高（Go/No-Go 决策点）

### P0-5: 敏感数据脱敏
- **状态**: ✅ 已完成设计
- **文档**: `observability-starter-design.md` Lines 842-1109
- **实现**: SensitiveDataObservationFilter
- **下一步**: 实施并验证

### P0-6: Actuator 安全加固
- **状态**: ❌ 待实施
- **范围**:
  - 配置 Actuator 访问控制
  - 启用 HTTP Basic 认证
  - 生产环境仅暴露必要端点
- **优先级**: 高（必须在实施阶段 4 完成）

---

## 📅 实施时间线（质量优先）

### 总体时间估算

```
预计总工时: 15-20 天（单人开发，质量优先）
关键里程碑: Day 3（Go/No-Go 决策点）
```

### 详细阶段划分

#### 阶段 0: 先决条件验证（Day 0，半天）

**目标**: 验证实施的基础条件

**任务清单**:
- [ ] 验证 Spring Boot 实际版本（期望 3.5.7）
- [ ] 验证 SkyWalking Docker 环境状态
- [ ] 分析现有业务代码对 TracingInterceptor/MetricsInterceptor 的依赖
- [ ] 检查 pom.xml 中是否已引入 SkyWalking 依赖
- [ ] 确认 patra-ingest 为 PoC 测试目标服务

**产出物**:
- 环境验证报告（记录在本文档）
- 依赖分析报告

**验证脚本**:
```bash
# 1. 验证 Spring Boot 版本
grep -r "spring-boot.version" patra-parent/pom.xml

# 2. 验证 SkyWalking Docker 环境
docker-compose -f docker/docker-compose.dev.yaml config | grep skywalking

# 3. 分析现有可观测性依赖
grep -r "TracingInterceptor\|MetricsInterceptor" patra-*/src --include="*.java"
```

---

#### 阶段 1: PoC 性能测试（Day 1-3，3天）

**🚨 关键决策点**: 基于 PoC 结果决定是否继续实施

**目标**: 在 patra-ingest 验证 SkyWalking Agent 实际性能

**任务清单**:
- [ ] 在 patra-ingest 创建 PoC 分支 `poc/skywalking-performance`
- [ ] 下载 SkyWalking Agent 9.5.0
- [ ] 配置 agent.config（默认配置）
- [ ] 编写 JMH 基准测试（模拟批量数据处理）
- [ ] 使用 JMeter 进行负载测试（1000 TPS，持续 10 分钟）
- [ ] 使用 Arthas 监控运行时指标
- [ ] 对比三种场景性能数据:
  - 基准场景（不启用 SkyWalking Agent）
  - 启用 SkyWalking Agent（默认配置）
  - 启用完整可观测性（SkyWalking + Prometheus + 自定义 Handler）

**批准条件**（Go/No-Go）:
| 指标 | 阈值 | 实测值（待填写） | 是否通过 |
|-----|------|----------------|---------|
| CPU 开销 | < 10% | ___ | ⬜ |
| 内存开销 | < 50MB | ___ | ⬜ |
| TPS 下降 | < 5% | ___ | ⬜ |
| P99 响应时间增加 | < 10% | ___ | ⬜ |

**决策规则**:
- ✅ **全部通过**: 继续实施
- ⚠️ **部分通过**: 调整配置后重新测试
- ❌ **未通过**: 评估是否调整方案或放弃 SkyWalking

**产出物**:
- PoC 性能测试报告（Markdown 文档）
- JMH 基准测试代码
- JMeter 测试计划文件
- Arthas 监控截图

**参考文档**:
- [SkyWalking Agent 配置文档](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/readme/)
- [SkyWalking Agent Benchmarks](https://skyapmtest.github.io/Agent-Benchmarks/)

---

#### 阶段 2: 创建 patra-starter-observability（Day 4-7，4天）

**前置条件**: PoC 性能测试通过

**目标**: 创建统一的可观测性 Starter

**任务清单**:

**2.1 创建模块结构**
- [ ] 创建 `patra-spring-boot-starter-observability` 目录
- [ ] 创建 pom.xml 并配置依赖
- [ ] 创建包结构（autoconfigure、config、handler、filter、convention）

**2.2 实现配置类**
- [ ] 实现 `ObservabilityProperties`（主配置属性）
- [ ] 实现 `MetricsConfig`、`TracingConfig`、`LoggingConfig`
- [ ] 实现 `SecurityConfig`（敏感数据脱敏配置）
- [ ] 创建 `additional-spring-configuration-metadata.json`

**2.3 实现自动配置**
- [ ] 实现 `ObservabilityAutoConfiguration`（主配置类）
- [ ] 实现 `MicrometerAutoConfiguration`（Micrometer 配置）
- [ ] 实现 `SkyWalkingMeterAutoConfiguration`（SkyWalking Meter）
- [ ] 实现 `PrometheusAutoConfiguration`（Prometheus 配置）
- [ ] 实现 `ObservationInterceptorsAutoConfiguration`（拦截器配置）
- [ ] 配置 `AutoConfiguration.imports`

**2.4 实现 ObservationFilter**
- [ ] 🔒 实现 `SensitiveDataObservationFilter`（P0-5）
  - [ ] 检测并脱敏密码、Token、API Key
  - [ ] 检测并脱敏身份证号、手机号、邮箱
  - [ ] 支持自定义敏感数据模式
  - [ ] 生产环境强制启用
- [ ] 实现 `CommonTagsObservationFilter`（添加公共标签）

**2.5 实现 ObservationHandler**
- [ ] 实现 `LoggingObservationHandler`（日志 Handler）
- [ ] 实现 `PerformanceObservationHandler`（性能 Handler）
- [ ] 实现 `MetricsObservationHandler`（指标 Handler）

**2.6 实现 MeterFilter**
- [ ] 实现 `CommonTagsMeterFilter`（公共标签）
- [ ] 实现 `MetricNamingMeterFilter`（命名规范）
- [ ] 实现 `HighCardinalityMeterFilter`（高基数过滤）

**2.7 实现拦截器（插件式架构）**
- [ ] 实现 `ObservationResolutionInterceptor`（实现 `ResolutionInterceptor`）
- [ ] 实现 `RestClientObservationInterceptor`（实现 `ClientInterceptor`）
- [ ] 实现 `BatchObservationJobListener`（实现 `JobExecutionListener`）

**2.8 编写测试**
- [ ] 单元测试: `ObservabilityAutoConfigurationTest`
- [ ] 单元测试: `SensitiveDataObservationFilterTest`
- [ ] 单元测试: `PerformanceObservationHandlerTest`
- [ ] 集成测试: `TestObservabilityApplication`

**产出物**:
- patra-spring-boot-starter-observability 模块（可编译、可测试）
- 单元测试覆盖率 > 80%
- README.md（使用说明）

---

#### 阶段 3: 重构现有 Starters（Day 8-11，4天）

**目标**: 从现有 Starter 中移除可观测性代码，保留扩展点

**任务清单**:

**3.1 重构 patra-starter-core**
- [ ] 分析 `TracingInterceptor` 和 `MetricsInterceptor` 的依赖
- [ ] ⚠️ 保留 `ResolutionInterceptor` 接口（不创建新接口）
- [ ] 删除 `TracingInterceptor`（移动到 observability starter）
- [ ] 删除 `MetricsInterceptor`（移动到 observability starter）
- [ ] 删除 Micrometer 依赖（从 pom.xml）
- [ ] 验证编译通过
- [ ] 更新 package-info.java 文档

**3.2 重构 patra-starter-rest-client**
- [ ] 查找现有的可观测性代码
- [ ] 定义 `ClientInterceptor` 扩展点接口
- [ ] 修改 RestClient 配置以支持拦截器注入
- [ ] 删除现有的可观测性代码
- [ ] 删除 Micrometer 依赖（从 pom.xml）
- [ ] 验证编译通过
- [ ] 更新 package-info.java 文档

**3.3 重构 patra-starter-batch**
- [ ] 查找 TODO 标记的 Metrics 代码
- [ ] 删除 TODO 标记的代码
- [ ] 删除 Micrometer 依赖（如果有）
- [ ] 验证编译通过
- [ ] 更新 package-info.java 文档

**3.4 验证依赖方向**
- [ ] 验证: core ❌→ observability（无依赖）
- [ ] 验证: rest-client ❌→ observability（无依赖）
- [ ] 验证: batch ❌→ observability（无依赖）
- [ ] 验证: observability → core（单向依赖）
- [ ] 使用 `mvn dependency:tree` 验证

**产出物**:
- 重构后的 core、rest-client、batch 模块（无 Micrometer 依赖）
- 扩展点接口文档

---

#### 阶段 4: 安全加固（Day 12-13，2天）

**目标**: 完成 P0-3 和 P0-6，确保生产环境安全

**任务清单**:

**4.1 实现 ArchUnit 架构测试（P0-3）**
- [ ] 在各服务的 domain 模块添加 ArchUnit 依赖
- [ ] 创建 `HexagonalArchitectureTest`
- [ ] 规则 1: Domain 层不依赖 Infrastructure
- [ ] 规则 2: Domain 层不依赖框架（Spring、Micrometer、SkyWalking）
- [ ] 规则 3: 六边形架构层次边界验证
- [ ] 验证测试通过

**4.2 配置 Actuator 访问控制（P0-6）**
- [ ] 配置生产环境 Actuator 端点暴露策略
- [ ] 实现 `ActuatorSecurityConfiguration`
- [ ] 配置 HTTP Basic 认证
- [ ] 健康检查端点公开，其他端点需认证
- [ ] 使用环境变量注入密码（禁止硬编码）
- [ ] 验证配置生效

**4.3 SkyWalking Agent 安全配置**
- [ ] 生产环境禁用 SQL 参数收集
- [ ] 生产环境禁用 HTTP Body 收集
- [ ] 配置环境变量控制
- [ ] 更新 Docker Compose 配置

**产出物**:
- ArchUnit 测试代码（所有服务的 domain 模块）
- Actuator 安全配置（application-prod.yml）
- SkyWalking Agent 安全配置（agent.config）
- 安全配置文档

---

#### 阶段 5: 集成与验证（Day 14-16，3天）

**目标**: 将 observability starter 集成到各服务，端到端验证

**任务清单**:

**5.1 服务集成**
- [ ] patra-ingest 集成（已在 PoC 完成）
- [ ] patra-catalog 集成
- [ ] patra-registry 集成
- [ ] patra-gateway 集成

**5.2 功能验证**
- [ ] 验证: ObservationResolutionInterceptor 正常工作
- [ ] 验证: RestClient HTTP 调用追踪正常
- [ ] 验证: Batch Job 追踪正常
- [ ] 验证: SkyWalking UI 能看到完整的追踪链路
- [ ] 验证: Prometheus 能收集到所有指标
- [ ] 验证: 日志中 traceId 正常显示
- [ ] 验证: 敏感数据脱敏生效

**5.3 架构验证**
- [ ] 依赖方向正确（observability → core，单向）
- [ ] 移除 observability 依赖后，应用仍能正常启动
- [ ] ArchUnit 测试全部通过

**5.4 性能验证（回归测试）**
- [ ] CPU 开销 < 10%（相对基准）
- [ ] 内存开销 < 50MB（相对基准）
- [ ] TPS 下降 < 5%

**产出物**:
- 集成验证报告
- 功能验证清单（全部通过）
- 性能回归测试报告

---

#### 阶段 6: 文档更新（Day 17-18，2天）

**目标**: 更新所有相关文档

**任务清单**:

**6.1 模块 README 更新**
- [ ] patra-starter-core README（说明扩展点机制）
- [ ] patra-starter-rest-client README（说明扩展点机制）
- [ ] patra-starter-batch README（说明扩展点机制）
- [ ] patra-starter-observability README（使用说明）

**6.2 包级文档更新**
- [ ] 各模块 package-info.java 更新
- [ ] 扩展点接口的 JavaDoc

**6.3 配置文档更新**
- [ ] 更新 `observability-config-examples.yaml`
- [ ] 添加安全配置示例
- [ ] 添加多环境配置示例

**6.4 故障排查文档**
- [ ] 更新 `observability-quick-start.md`
- [ ] 添加常见问题 FAQ
- [ ] 添加故障排查指南

**产出物**:
- 所有 README.md 更新完成
- package-info.java 更新完成
- 配置示例文档更新完成

---

#### 阶段 7: 性能优化与最终验证（Day 19-20，2天）

**目标**: 性能调优和最终验证

**任务清单**:

**7.1 性能优化**
- [ ] 分析性能瓶颈（如果 PoC 性能不理想）
- [ ] 调整 SkyWalking Agent 配置
- [ ] 调整 Observation 采样率
- [ ] 优化 ObservationHandler 实现

**7.2 最终验证**
- [ ] 所有 P0 任务完成验证
- [ ] 所有测试通过（单元测试 + 集成测试 + ArchUnit）
- [ ] 性能指标达标
- [ ] 文档完整性检查

**7.3 生产就绪检查**
- [ ] 敏感数据脱敏验证
- [ ] Actuator 安全验证
- [ ] SkyWalking Agent 安全配置验证
- [ ] 多环境配置验证（dev、test、prod）

**产出物**:
- 最终性能测试报告
- 生产就绪检查清单（全部通过）
- 实施总结报告

---

## ✅ 生产就绪检查清单（Production Readiness Checklist）

在进入生产环境之前，必须满足以下所有条件：

### 必要条件（Must Have）

**P0 任务完成度**:
- [ ] **P0-1**: 插件式架构设计已实施
- [ ] **P0-2**: Domain Events 驱动可观测性已集成
- [ ] **P0-3**: ArchUnit 架构测试已实施且通过
- [ ] **P0-4**: PoC 性能测试已完成且达标
- [ ] **P0-5**: SensitiveDataObservationFilter 已实施且验证
- [ ] **P0-6**: Actuator 访问控制已配置且验证

**功能验证**:
- [ ] patra-starter-core 编译通过，无 Micrometer 依赖
- [ ] patra-starter-rest-client 编译通过，无 Micrometer 依赖
- [ ] patra-starter-batch 编译通过，无 Micrometer 依赖
- [ ] patra-starter-observability 编译通过
- [ ] 业务服务引入 observability 后，可观测性功能正常
- [ ] 业务服务移除 observability 后，应用仍能正常启动

**架构验证**:
- [ ] **依赖方向正确**: observability → core（单向）
- [ ] **无反向依赖**: core ❌→ observability
- [ ] **无反向依赖**: rest-client ❌→ observability
- [ ] **无反向依赖**: batch ❌→ observability

**可观测性验证**:
- [ ] ErrorResolutionPipeline 的追踪正常
- [ ] RestClient HTTP 调用的追踪正常
- [ ] Batch 任务的追踪正常
- [ ] SkyWalking UI 能看到完整的追踪链路
- [ ] Prometheus 能收集到所有指标
- [ ] 日志中 traceId 正常显示

**性能验证**:
- [ ] CPU 开销 < 10%（相对基准）
- [ ] 内存开销 < 50MB（相对基准）
- [ ] TPS 下降 < 5%
- [ ] P99 延迟增加 < 10%

**安全验证**:
- [ ] 敏感数据脱敏生效（密码、Token、身份证号等）
- [ ] Actuator 端点访问控制生效
- [ ] 生产环境 SkyWalking Agent 已禁用 SQL 参数收集
- [ ] 生产环境 SkyWalking Agent 已禁用 HTTP Body 收集

**文档验证**:
- [ ] 所有模块 README.md 已更新
- [ ] 所有 package-info.java 已更新
- [ ] 配置示例文档已更新
- [ ] 快速开始指南已更新
- [ ] 故障排查指南已更新

### 强烈推荐（Should Have）

**P1 任务**（可选，但强烈推荐）:
- [ ] 提供业务友好的注解（如 `@Trackable`）
- [ ] 与 DomainEvent 系统集成（如 `SlowOperationDetectedEvent`）
- [ ] 完成 OpenTelemetry 对比分析，并记录迁移路径
- [ ] 提供完整的 Prometheus 告警规则和 Grafana Dashboard
- [ ] 实现 `HighCardinalityMeterFilter` 防止标签爆炸

---

## 🚧 风险与应对措施

### 高风险（Critical）

| 风险 | 影响 | 概率 | 应对措施 | 责任人 |
|-----|------|------|---------|-------|
| **PoC 性能测试失败** | 🔴 阻塞实施 | 中 | 调整配置或更换方案 | Jobs |
| **现有业务代码强依赖当前可观测性实现** | 🔴 重构工作量翻倍 | 低 | 保留兼容性过渡期 | Jobs |

### 中风险（High）

| 风险 | 影响 | 概率 | 应对措施 | 责任人 |
|-----|------|------|---------|-------|
| **SkyWalking Agent 配置复杂** | 🟡 延迟交付 | 中 | 提供详细文档和脚本 | Jobs |
| **依赖冲突** | 🟡 编译失败 | 低 | 使用 Maven Dependency Tree 分析 | Jobs |
| **Docker 环境配置不一致** | 🟡 追踪失败 | 中 | 提供统一的 Docker Compose 配置 | Jobs |

### 低风险（Medium）

| 风险 | 影响 | 概率 | 应对措施 | 责任人 |
|-----|------|------|---------|-------|
| **文档更新遗漏** | 🟢 影响使用体验 | 低 | 使用检查清单验证 | Jobs |
| **测试覆盖率不足** | 🟢 潜在 Bug | 低 | 强制 80% 覆盖率 | Jobs |

---

## 📝 关键决策记录

### 决策 1: 采用保守策略（PoC 先行）

**日期**: 2025-11-24
**状态**: 🟡 待确认
**理由**:
- SkyWalking 官方 Benchmark 数据不能替代实际环境测试
- 性能不达标会导致整个方案失败
- 单人开发，无时间压力，应优先验证风险

**影响**:
- 增加 3 天 PoC 测试时间
- Day 3 为 Go/No-Go 决策点

**确认人**: _待用户确认_

---

### 决策 2: 适配现有 ResolutionInterceptor 而非创建新接口

**日期**: 2025-11-24
**状态**: ✅ 已确认
**理由**:
- 代码库已有 `ResolutionInterceptor` 接口
- 创建新接口会增加混乱
- 适配现有接口更符合最小化变更原则

**影响**:
- 设计文档中的 `ErrorInterceptor` 改为 `ResolutionInterceptor`
- 重构指南需要调整

---

### 决策 3: 推荐 Domain Events 优先，防腐层备选

**日期**: 2025-11-24
**状态**: 🟡 待确认
**理由**:
- Domain Events 是 DDD 最佳实践
- 完全解耦，领域层不知道可观测性存在
- 易于扩展和测试

**影响**:
- 需要实现 Domain Event 基础设施（Spring 内置）
- 需要实现 Event Listener（如 `SlowOperationDetectedEvent`）

**确认人**: _待用户确认_

---

## 🔄 进度跟踪

### 总体进度

| 阶段 | 计划天数 | 实际天数 | 状态 | 完成度 |
|-----|---------|---------|------|-------|
| 阶段 0: 先决条件验证 | 0.5 | - | 🟡 待开始 | 0% |
| 阶段 1: PoC 性能测试 | 3 | - | 🟡 待开始 | 0% |
| 阶段 2: 创建 observability starter | 4 | - | ⬜ 未开始 | 0% |
| 阶段 3: 重构现有 starters | 4 | - | ⬜ 未开始 | 0% |
| 阶段 4: 安全加固 | 2 | - | ⬜ 未开始 | 0% |
| 阶段 5: 集成与验证 | 3 | - | ⬜ 未开始 | 0% |
| 阶段 6: 文档更新 | 2 | - | ⬜ 未开始 | 0% |
| 阶段 7: 性能优化与最终验证 | 2 | - | ⬜ 未开始 | 0% |
| **总计** | **20.5** | **-** | 🟡 **待确认** | **0%** |

### 当前阶段详情

**当前阶段**: 阶段 0 - 先决条件验证
**状态**: 🟡 待用户确认实施策略
**下一步**:
1. 用户确认实施方案
2. 开始阶段 0 的验证工作

---

## 📋 待确认事项

### ⏸️ 等待用户确认

在开始实施前，需要你明确确认以下问题：

#### Q1: PoC 性能测试优先级 ✋
- [ ] **问题**: 是否同意先执行 PoC 性能测试，再开始大规模重构？
- [ ] **问题**: 如果 PoC 失败（性能不达标），是否接受调整方案甚至放弃 SkyWalking？

**你的确认**: _待填写_

---

#### Q2: 现有可观测性代码的处理 ✋
- [ ] **问题**: 是否允许删除现有的 `TracingInterceptor` 和 `MetricsInterceptor`？
- [ ] **问题**: 还是需要保留兼容性，逐步迁移？

**你的确认**: _待填写_

---

#### Q3: 实施节奏 ✋
- [ ] **问题**: 是否认同"质量优先，无时间压力"的原则？
- [ ] **问题**: 预计 15-20 天的实施周期是否可接受？

**你的确认**: _待填写_

---

#### Q4: 技术选型 ✋
- [ ] **问题**: 是否同意使用 Micrometer Observation + SkyWalking 的混合架构？
- [ ] **问题**: 是否需要对比 OpenTelemetry 方案？

**你的确认**: _待填写_

---

#### Q5: Domain Events vs 防腐层 ✋
- [ ] **问题**: 是否同意优先使用 Domain Events 驱动可观测性？
- [ ] **问题**: 是否接受防腐层（ObservabilityPort）作为备选方案？

**你的确认**: _待填写_

---

### 🎯 确认方式

请在上述问题后填写你的确认意见，或者直接回复：

**"确认并开始实施"** - 如果你同意以上所有方案
**或者**
**"需要调整"** - 并说明你的具体调整建议

---

## 📚 参考文档

### 设计文档（已完成）
- [完整设计方案](./observability-starter-design.md) - 171KB，核心设计文档
- [插件式架构重构指南](./plugin-architecture-refactoring-guide.md) - 27KB，操作手册
- [架构评审总结](./architecture-review-summary.md) - 23KB，评审报告
- [配置示例](./observability-config-examples.yaml) - 14KB，配置模板
- [快速开始指南](./observability-quick-start.md) - 8.3KB，入门指南

### 外部文档
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)
- [Micrometer Observation](https://micrometer.io/docs/observation)
- [SkyWalking Documentation](https://skywalking.apache.org/docs/)
- [SkyWalking Agent 配置](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/readme/)
- [SkyWalking Agent Benchmarks](https://skyapmtest.github.io/Agent-Benchmarks/)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)

---

## 🔧 工具与脚本

### 常用脚本

```bash
# 验证 Spring Boot 版本
grep -r "spring-boot.version" patra-parent/pom.xml

# 验证 SkyWalking Docker 环境
docker-compose -f docker/docker-compose.dev.yaml config | grep skywalking

# 分析依赖树
mvn dependency:tree -Dverbose > dependency-tree.txt

# 查找可观测性代码
grep -r "ObservationRegistry\|TracingInterceptor\|MetricsInterceptor" patra-*/src --include="*.java"

# 编译所有模块
mvn clean compile -DskipTests

# 运行测试
mvn test

# 运行 ArchUnit 测试
mvn test -Dtest=*ArchitectureTest
```

---

## 📌 更新日志

| 日期 | 版本 | 变更内容 | 作者 |
|-----|------|---------|------|
| 2025-11-24 | 1.0.0 | 初始版本，创建实施计划 | Jobs |

---

**下一步**: 等待用户确认实施方案，然后开始阶段 0 的先决条件验证。
