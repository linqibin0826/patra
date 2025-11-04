---
skill: patra-domain
description: Patra 业务领域知识 - Provenance、Plan、Task、表达式引擎与工作流模式
type: domain
enforcement: suggest
priority: high
---

# Patra 领域知识

## 目的

本技能提供关于 Patra 业务领域概念、工作流模式和架构决策的全面知识。在处理 Patra 特定的业务逻辑、数据模型和集成模式时使用本技能。

**核心原则**: Patra 是一个医学文献数据平台,使用可配置的基于 Provenance 的工作流,从多个来源(PubMed、EPMC、Crossref)采集、处理和索引科学出版物。

---

## 何时使用本技能

本技能在以下情况下自动激活:

**处理核心概念时:**
- Provenance(数据源)配置与管理
- Plan(采集计划)创建与切片策略
- Task(任务)执行工作流
- 表达式引擎定制

**实现功能时:**
- 数据源集成(PubMed、EPMC、Crossref)
- 批处理管道
- 时间切片与窗口管理
- 幂等性与重试机制

**调试问题时:**
- Plan 切片问题
- Task 执行失败
- 表达式渲染错误
- 配置范围优先级

**文件上下文:**
- 编辑 `patra-registry` 中的文件(配置服务)
- 在 `patra-ingest` 中工作(数据摄取服务)
- 修改 `patra-expr-kernel`(表达式引擎)

---

## 快速开始

### 理解 Patra 架构

**三服务架构:**

```
┌─────────────────────────────────────────────────────────┐
│  patra-gateway (API Gateway)                            │
│  - Routing, Auth, Rate Limiting                         │
└─────────────────────────────────────────────────────────┘
              │
              ├──────────────┬──────────────┐
              ▼              ▼              ▼
┌──────────────────┐  ┌─────────────────┐  ┌───────────────┐
│ patra-registry   │  │ patra-ingest    │  │ patra-storage │
│ (SSOT Service)   │  │ (Orchestrator)  │  │ (Data Storage)│
├──────────────────┤  ├─────────────────┤  ├───────────────┤
│ • Provenance     │  │ • Plan Mgmt     │  │ • Documents   │
│   Config         │  │ • Task Exec     │  │ • Indexing    │
│ • Expressions    │  │ • Slicing       │  │ • Search      │
│ • Dictionary     │  │ • Harvest       │  │               │
└──────────────────┘  └─────────────────┘  └───────────────┘
```

**关键职责:**
- **patra-registry**: Provenance 配置、表达式、字典的单一数据源(SSOT)
- **patra-ingest**: Plan/Task 生命周期管理、数据采集编排
- **patra-storage**: 文档存储、索引和搜索

---

## 核心领域概念

### 1. **Provenance(数据源)**

**定义**: 采集医学文献数据的数据源(如 PubMed、EPMC、Crossref)。

**关键属性:**
```java
public record Provenance(
    ProvenanceCode provenanceCode,  // PUBMED, EPMC, CROSSREF
    String name,
    String baseUrl,
    boolean isActive
) { }
```

**Provenance 配置**: 聚合特定 Provenance 的所有设置:
- `WindowOffsetConfig`: 时间窗口切片参数
- `PaginationConfig`: API 分页设置
- `HttpConfig`: HTTP 客户端配置
- `BatchingConfig`: 批处理大小与并发
- `RetryConfig`: 重试策略与限制
- `RateLimitConfig`: 限流规则

**参见**: [business-concepts.md](resources/business-concepts.md) 获取完整详情。

---

### 2. **Plan(批处理计划)**

**定义**: 定义如何在特定时间窗口内从 Provenance 采集数据的批处理计划。

**生命周期:**
```
PENDING → RUNNING → COMPLETED
   ↓
CANCELLED (用户操作)
```

**关键概念:**
- **时间窗口**: 数据采集的 `[startTime, endTime)` 范围
- **切片(Slicing)**: 将大窗口分解为更小的 Slice 以并行处理
- **Slice(切片)**: Plan 中的原子工作单元

**示例场景:**
```
Plan: 从 2024-01-01 到 2024-12-31 采集 PubMed 数据

切片策略(按月):
  Slice 1: 2024-01-01 到 2024-01-31
  Slice 2: 2024-02-01 到 2024-02-29
  ...
  Slice 12: 2024-12-01 到 2024-12-31

每个 Slice → 多个 Task(基于预估记录数)
```

**参见**: [plan-task-workflow.md](resources/plan-task-workflow.md) 获取工作流详情。

---

### 3. **Task(批处理任务)**

**定义**: 从特定 Provenance API 端点获取数据的原子工作单元。

**关键属性:**
```java
public record BatchTask(
    TaskId taskId,
    PlanId planId,
    ProvenanceCode provenanceCode,
    String businessKey,      // 幂等性键
    TaskStatus status,
    Map<String, Object> params,  // API 请求参数
    int retryCount
) { }
```

**幂等性设计:**
- `businessKey` = hash(Provenance + API 参数)
- 防止重复 API 调用
- 启用安全重试

**状态转换:**
```
PENDING → RUNNING → SUCCEEDED
   ↓          ↓
   └─────→ FAILED → PENDING (重试)
              ↓
          EXHAUSTED (达到最大重试次数)
```

**参见**: [plan-task-workflow.md](resources/plan-task-workflow.md) 获取完整工作流。

---

### 4. **表达式引擎**

**定义**: 动态参数映射系统,将 Patra 的抽象查询模型转换为提供者特定的 API 参数。

**核心组件:**

```java
public record Expression(
    String exprField,       // 抽象字段名(如 "publicationDate")
    String capability,      // 搜索能力(如 "range", "exact")
    String renderRule       // 提供者特定模板(如 "mindate={start}&maxdate={end}")
) { }
```

**使用示例:**

```
用户查询(抽象):
  publicationDate: [2024-01-01, 2024-12-31]
  capability: range

PubMed 表达式:
  exprField: publicationDate
  capability: range
  renderRule: "mindate={start}&maxdate={end}"

渲染输出:
  mindate=2024-01-01&maxdate=2024-12-31
```

**参见**: [expression-engine.md](resources/expression-engine.md) 获取完整指南。

---

## 设计模式

### 1. **时间切片(Temporal Slicing)**

**问题**: 大时间窗口导致 API 超时或限流。

**解决方案**: 将时间窗口分解为更小、可管理的切片。

**实现**:
- 每个 Provenance 配置窗口大小
- 切片边界对齐到自然单位(天/周/月)
- 每个切片独立处理

### 2. **范围优先级(Scope Precedence)**

**问题**: 配置可在多个级别定义(全局、Provenance、Task)。

**解决方案**: 范围优先级层次结构。

```
TASK > SOURCE > GLOBAL
(最具体的优先)
```

**示例**:
```
全局: retryLimit = 3
Provenance PUBMED: retryLimit = 5
Task ABC: retryLimit = 1

→ Task ABC 使用 retryLimit = 1
```

### 3. **基于业务键的幂等性**

**问题**: 网络故障导致重复 API 调用。

**解决方案**: 基于哈希的业务键。

```
businessKey = hash(
  provenanceCode +
  apiEndpoint +
  requestParams
)
```

**效果**: 重复的 Task(相同业务键)会被跳过。

---

## 常见工作流

### 工作流 1: 创建新 Provenance

1. **定义 Provenance** (registry domain):
   - `ProvenanceCode`、`name`、`baseUrl`

2. **配置设置** (registry domain):
   - 窗口偏移: 采集多久之前的数据?
   - 分页: 页大小、偏移/游标策略
   - HTTP: 超时、请求头、认证
   - 重试: 最大重试次数、退避策略
   - 限流: 每秒/分钟请求数

3. **定义表达式** (expr-kernel):
   - 将抽象字段映射到 API 参数
   - 支持多种能力(精确、范围、通配符)

4. **测试配置**:
   - 创建小型测试 Plan
   - 验证切片逻辑
   - 验证 API 参数渲染

### 工作流 2: 执行批处理计划

1. **Plan 创建** (ingest app):
   ```
   输入: Provenance, 时间窗口, 用户
   输出: 带 Slice 的 Plan
   ```

2. **Slice 生成**:
   ```
   for each slice in [plan.startTime, plan.endTime):
     估算记录数
     if count > threshold:
       拆分为多个 Task
     else:
       创建单个 Task
   ```

3. **Task 执行**:
   ```
   for each Task:
     使用表达式渲染 API 参数
     检查 businessKey (如果存在则跳过)
     调用 Provenance API
     将结果存储在 patra-storage
     更新 Task 状态
   ```

4. **重试机制**:
   ```
   if Task fails:
     if retryCount < maxRetries:
       status = PENDING (重新排队)
     else:
       status = EXHAUSTED
   ```

**参见**: [plan-task-workflow.md](resources/plan-task-workflow.md) 获取完整工作流。

---

## 资源文件

特定主题的详细指南(每个 <500 行):

### 业务领域
- **[business-concepts.md](resources/business-concepts.md)** - 核心概念: Provenance、Plan、Task、表达式
- **[provenance-config-system.md](resources/provenance-config-system.md)** - 配置层次结构与范围优先级
- **[expression-engine.md](resources/expression-engine.md)** - 表达式渲染与能力系统

### 工作流模式
- **[plan-task-workflow.md](resources/plan-task-workflow.md)** - Plan/Task 生命周期与执行模式
- **[slicing-strategies.md](resources/slicing-strategies.md)** - 时间切片与窗口管理
- **[idempotency-patterns.md](resources/idempotency-patterns.md)** - 业务键设计与去重

### 集成
- **[service-integration.md](resources/service-integration.md)** - patra-registry、patra-ingest、patra-storage 如何协同工作
- **[pubmed-integration.md](resources/pubmed-integration.md)** - PubMed 特定配置与注意事项
- **[common-patterns.md](resources/common-patterns.md)** - 来自实际代码库的常见代码模式

### 故障排查
- **[troubleshooting.md](resources/troubleshooting.md)** - 常见问题与调试策略

---

## 快速决策树

**需要添加新数据源?**

1. **Registry 服务**: 定义 Provenance 和配置
2. **表达式引擎**: 将抽象字段映射到 API 参数
3. **Ingest 服务**: 无需代码更改(使用通用工作流)
4. **Storage 服务**: 无需代码更改(与提供者无关)

**Plan 执行失败?**

1. 检查 Task 状态分布(有多少 FAILED?)
2. 查看 Task 错误消息
3. 验证表达式渲染(API 参数是否正确?)
4. 检查限流(是否达到提供者限制?)

**表达式不工作?**

1. 验证 `exprField` 与用户查询匹配
2. 检查支持的 `capability`(精确/范围/通配符)
3. 验证 `renderRule` 模板语法
4. 在表达式引擎中使用实际值测试

**配置未应用?**

1. 检查范围优先级(TASK > SOURCE > GLOBAL)
2. 验证 `operationType` 匹配(如 "harvest" vs "update")
3. 确保时间有效性(effectiveFrom/effectiveTo)

---

## Patra 代码库示例

### 示例 1: ProvenanceConfiguration 聚合

```java
// patra-registry-domain/src/main/java/com/patra/registry/domain/model/aggregate/
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit
) {
    public boolean isComplete() {
        return provenance != null &&
               provenance.isActive() &&
               windowOffset != null &&
               pagination != null;
    }

    public boolean supportsOperation(String operationType) {
        // 范围优先级逻辑
        return // ... 实现
    }
}
```

### 示例 2: Plan 切片逻辑

```java
// patra-ingest-app/src/main/java/com/patra/ingest/app/service/
public class PlanSlicingOrchestrator {

    public List<Slice> createSlices(
        Plan plan,
        WindowOffsetConfig windowConfig
    ) {
        Duration sliceDuration = windowConfig.sliceDuration();
        LocalDateTime current = plan.startTime();
        List<Slice> slices = new ArrayList<>();

        while (current.isBefore(plan.endTime())) {
            LocalDateTime sliceEnd = current.plus(sliceDuration);
            if (sliceEnd.isAfter(plan.endTime())) {
                sliceEnd = plan.endTime();
            }

            slices.add(new Slice(
                current,
                sliceEnd,
                estimateRecordCount(current, sliceEnd)
            ));

            current = sliceEnd;
        }

        return slices;
    }
}
```

**参见**: [common-patterns.md](resources/common-patterns.md) 获取更多示例。

---

## 与其他技能集成

本技能与 `java-backend-guidelines` 互补:

- **java-backend-guidelines**: 如何构建代码(六边形 + DDD)
- **patra-domain**: 业务概念的含义

**在 Patra 中实现新功能时一起使用两者。**

---

## 下一步

1. 浏览资源文件深入了解特定主题
2. 查看 [common-patterns.md](resources/common-patterns.md) 获取实际代码库的代码示例
3. 遇到调试问题时参考 [troubleshooting.md](resources/troubleshooting.md)

**本技能在编辑 patra-registry、patra-ingest 或 patra-expr-kernel 文件时,或提示中提到 Provenance、Plan、Task 或表达式相关关键词时自动激活。**
