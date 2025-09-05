# Papertrace 六边形架构与模块规范

> SpringCloud 微服务；六边形架构 + DDD。

---

## 1. 架构总览与依赖原则

项目采用六边形架构（Hexagonal/Ports & Adapters）并结合 DDD 分层，目标是通过**明确依赖方向**与**模块职责**保护领域模型纯净，降低技术细节对业务逻辑的侵入。

### 1.1 分层与依赖

- **适配层（adapter）**：对外协议与入/出站端口（Web、RPC、MQ、Scheduler）。只依赖 **app + api**；可选依赖 `patra-starter-web`。
- **应用层（app）**：用例编排、权限与事务、集成事件。只依赖 **domain** 与共享库：`patra-starter-query`、`gateways-*`、`patra-common`。
- **基础设施层（infra）**：持久化/缓存/消息等技术实现。只依赖 **domain** 与持久化/映射库：`patra-starter-mybatis-plus`。
- **领域层（domain）**：实体/值对象/聚合/领域事件与仓储端口，仅依赖 `patra-shared-kernel`（**禁止**依赖 Spring/MP/Web/api）。
- **接口层（api）**：对外 DTO/枚举/路径，不依赖 Spring（最多 `jakarta.validation`）。

> 注：`medoc-query` 被 **ingest**（当前）与 **search**（未来）两个应用层模块复用，用于编译查询条件；其对 `registry` 的依赖通过**端口**倒置实现，见第 4 章。

---

## 2. 业务背景与阶段目标

### 2.1 背景

管理员通过**页面操作**与**定时任务**触发采集，目标平台覆盖十余个开放医学文献源（如 PubMed、EPMC）。SpringCloud 微服务架构，六边形架构 + DDD 分层。

### 2.2 当前阶段目标：数据落地

统一接入各平台并解析，将原始文献数据可靠落库，完成基础清洗，支撑后续检索与智能分析。

### 2.3 演进路线

稳定后将拆出独立 **search 模块**.

---

## 3. 模块约定示例：`registry` (唯一真实数据来源管理-字典等)

> 以下说明包结构、类放置与硬约束，确保边界“薄而清晰”。

### 3.1 adapter 包结构与规范

**包根**：`com.patra.registry.adapter`

```
rest/
  controller/
    ProvenanceController.java     // 转发：请求→命令/查询→调用 app；不写业务逻辑
  dto/                            // app 不足时的 REST 专用 Req/View
    UpdateConfigReq.java
    ProvenanceView.java

scheduler/
  SyncJobScheduler.java           // 定时任务触发入口（xxl-job）

mq/
  consumer/
    ProvenanceEventConsumer.java  // 订阅 MQ/事件→调用 app 用例
  producer/
    ProvenanceEventProducer.java  // 发布 MQ/事件（事件模型在 api）

config/ (可选)
  RegistryAdapterConfig.java      // Web/MQ/Scheduler 等适配器所需 Bean
```

**硬性约束**：仅依赖 **app+api**（可选 `patra-starter-web`）；无业务逻辑/无事务/不构造领域对象；DTO 校验在 controller；统一返回 View/Response；记录访问日志与追踪但不泄露领域细节。

**REST 命名**：前缀 `/api/registry/**`；资源复数；命令式动作用冒号后缀，如 `POST /provenances/{id}:sync`。

### 3.2 app 包结构与规范

**包根**：`com.patra.registry.app`

```
service/                            // 用例编排入口（权限/事务/聚合协作）
  ProvenanceAppService.java

usecase/                            // 入参模型（与 adapter 共享）
  command/
    UpdateProvenanceConfigCmd.java
  query/
    GetProvenanceByCodeQry.java

mapping/                            // app↔domain 映射（MapStruct/Assembler）
  ProvenanceAppMapper.java

security/
  RegistryPermissionChecker.java   // 权限/策略检查接口

event/
  ProvenanceConfigChanged.java     // 应用级集成事件模型
  publisher/
    ProvenanceEventPublisher.java         

tx/
  IdempotencyGuard.java            // 幂等/分布式锁等工具

config/
  RegistryAppConfig.java           // 仅 app 需要的 Bean
```

**硬性约束**：只依赖 **domain** 与允许的共享库；返回 View/Dto，不暴露领域实体；将领域异常转换为应用异常；事件通过 `ProvenanceEventPublisher` 对外发布。

**用例示例**：

- `ProvenanceAppService.updateConfig(cmd)`：权限校验 → 载入聚合 → 调用聚合行为 → 仓储保存 → 发布 `ProvenanceConfigChanged`。

### 3.3 domain 包结构与规范

**包根**：`com.patra.registry.domain`

```
model/
  aggregate/
    provenance/
      Provenance.java              // 聚合根
      ProvenanceConfig.java        // 子实体/值对象（视是否有标识）
  vo/
    ProvenanceId.java              // 不可变值对象
    ProvenanceCode.java
  event/
    ProvenanceConfigChanged.java   // 领域事件（事实）
  enums/
    xxxEnum.java                  // 领域枚举(贯穿所有层、其他层不定义枚举（api除外）) xxDO.java直接使用该枚举
port/
  ProvenanceRepository.java        // 仓储端口
```

**硬性约束**：依赖 `hutool-core`；无任何注解；不变量在构造/变更中使用hutool校验；领域事件不承载技术细节。

**建模要点**：`code` 唯一与规范；`config` 内含限流/拉取间隔等规则校验；`enable/disable` 行为需幂等；仓储仅以聚合为单位加载/保存。

### 3.4 infra 包结构与规范

**包根**：`com.patra.registry.infra`

```
persistence/
  entity/
    RegProvenanceEntity.java        // 实体全部继承 BaseEntity（patra-starter-mybatis-plus）
    RegProvenanceConfigEntity.java
  mapper/                          // MyBatis-Plus Mapper 接口
    ProvenanceMapper.java
    ProvenanceConfigConfigMapper.java
  repository/
    ProvenanceRepositoryMpImpl.java  // 实现 domain.port.Repository

mapstruct/
  ProvenanceConverter.java
  ProvenanceConfigConverter.java

config/
  xxxConfig.java               // 数据源/事务/MP 配置
```

**硬性约束**：依赖 **domain** + 持久化/映射库；`entity↔aggregate` 转换集中在仓储实现；事务边界以 app 为主；审计/版本号在 `entity` 处理；Mapper 只写访问 SQL；转换规则集中在 MapStruct。

**仓储实现职责**：`findById`/`findByCode` 查询→转换→返回聚合；`save` 将聚合拆分为多表写入；统一将底层异常转换为上层可识别异常。
