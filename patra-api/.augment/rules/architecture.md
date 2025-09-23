---
type: "always_apply"
---

> Papertrace（医学文献数据平台）的项目内规与操作指北。**测试与 PR/Commit 规范暂不纳入本文件**（后续再开）。

---

## 0. 你是谁 & 你该如何工作

* 你是本仓库的“编码智能体”，在 IDE / CLI 中协助：写代码、重构、修 bug、生成接口与适配层。
* **遵循六边形架构 + DDD** 分层；**严守依赖方向**；**不越层**、不泄漏实现细节。
* 在信息不足时，先提问再动手；偏好复用已有能力（`patra-*` starters、`patra-common`、Hutool）。

---

## 1. 项目概览

* **名称**：Papertrace – 医学文献数据平台
* **目标**：

    1. 统一采集 10+ 医学文献源（PubMed、EPMC…）
    2. 以 **SSOT**（单一可信源）管理配置、词典、元数据
    3. 对原始文献数据解析、清洗与标准化
    4. 后续提供搜索与智能分析
* **架构**：微服务 + 六边形架构 + 事件驱动（异步通信）
* **当前重点**：确保数据“可靠落地”（采集 → 解析/清洗 → 入库）

---

## 2. 技术栈 & 版本

* **语言/构建**：Java 21，Maven（多模块；父 POM 统一依赖），UTF-8
* **核心框架**：Spring Boot **3.2.4**；Spring Cloud **2023.0.1**；Spring Cloud Alibaba **2023.0.1.0**
* **数据持久**：MyBatis-Plus **3.5.12**，MySQL **8.0**，Redis **7.0**，Elasticsearch **8.14**
* **基础设施**：Nacos（注册/配置），SkyWalking **10.2**（APM/Tracing），XXL-Job **3.2.0**（调度），Docker Compose（本地）
* **代码生成/映射/工具**：Lombok **1.18.38**，MapStruct **1.6.3**，Hutool **5.8.22**
* **自研 Starters**：`patra-spring-boot-starter-core` / `-web` / `-mybatis` / `patra-spring-cloud-starter-feign`
* **表达式引擎**：`patra-expr-kernel`

---

## 3. 仓库结构（精简）

```

Papertrace/
├─ patra-parent/ # 父 POM（依赖/插件管理）
├─ patra-common/ # 公共工具&基类
├─ patra-expr-kernel/ # 表达式引擎
├─ patra-gateway-boot/ # API Gateway
├─ patra-registry/ # SSOT 注册微服务
├─ patra-ingest/ # 采集/摄取微服务
├─ patra-spring-boot-starter-\*/ # 自研 starters
└─ docker/ # 本地基础设施

```

### 3.1 微服务模块通用子结构

```

patra-{service}/
├─ patra-{service}-boot/ # 可执行入口
├─ patra-{service}-api/ # 外部 API 契约（DTO/接口）
├─ patra-{service}-contract/ # 内部契约（QueryPorts/ReadModels）
├─ patra-{service}-domain/ # 领域（实体/聚合/枚举/端口）
├─ patra-{service}-app/ # 应用（用例/服务编排）
├─ patra-{service}-infra/ # 基础设施（仓储/适配）
└─ patra-{service}-adapter/ # 适配层（REST/调度/MQ）

```

### 3.2 依赖方向（必须遵守）

* `adapter` → `app` + `api`（+ 可选 web starters）
* `app` → `domain` + `contract` + `patra-common` + core starter
* `infra` → `domain` + `contract` + mybatis starter + core starter
* `domain` → **仅** `patra-common`（**禁止**引入 Spring/框架）
* `api`、`contract`：不依赖框架

---

## 4. 包结构与命名（关键片段）

### 4.1 Domain（`com.patra.{service}.domain`）

```

model/
  aggregate/{name}/ # 聚合根/实体
  vo/ # 值对象
  event/ # 领域事件
  enums/ # 领域枚举（可跨层使用）
port/ # 仓储/服务端口

```

### 4.2 Application（`com.patra.{service}.app`）

```
service/ # 应用服务（用例编排）
usecase/
  command/ # 命令对象
  query/ # 查询对象
mapping/ # Domain ↔ App 映射（MapStruct）
security/ # 权限校验
event/ # 集成事件/发布
tx/ # 事务工具（幂等/锁）
config/ # 应用配置

```

### 4.3 Infrastructure（`com.patra.{service}.infra`）

```
persistence/
  entity/ # DB 实体（继承 BaseDO）
  mapper/ # MyBatis-Plus Mapper
  repository/ # 仓储实现
mapstruct/ # Entity ↔ Domain 转换器
config/ # 基建配置

```

### 4.4 Adapter（`com.patra.{service}.adapter`）

```
rest/controller # REST 控制器
rest/dto # REST 专用 DTO（如需）
scheduler # XXL-Job 任务
mq/consumer | mq/producer # 消费者/生产者
config # 适配层配置

```

### 4.5 REST 规范与类命名

* Base path：`/api/{service}/**`
* 资源名：**复数**；命令动作使用冒号后缀（例：`POST /provenances/{id}:sync`）
* 控制器：`{Resource}Controller`
* 应用服务：`{Aggregate}AppService`
* 仓储：接口 `{Aggregate}Repository`；实现 `{Aggregate}RepositoryMpImpl`
* 实体：`{Service}{Table}DO`；Mapper：`{Entity}Mapper`；MapStruct：`{Entity}Converter`

---

## 5. 代码约定（**务必遵守**）

### 5.1 DO/枚举/JSON

* **DO 中不要使用 Java `enum`**。
* 数据库存 JSON 的字段在 DO 中统一使用 **Jackson `JsonNode`** 表示。

### 5.2 POJO 形态

* **不可变/值对象**优先使用 **`record`**。
* **需要可变**时使用 **Lombok + class**（见 5.3）。

> 说明：`record` 天生不可变；若需可变请显式选用 `class`，避免语义冲突。

### 5.3 Lombok 一律代替样板代码

* 不手写 `getter/setter/toString/equals/hashCode`；使用 `@Data` 或组合注解（`@Getter`/`@Setter`/`@ToString` 等）。
* 注意：在 `record` 中无需 Lombok；在 class 中合理选用，避免过度注解。

### 5.4 工具类复用优先

* **不重复造轮子**：优先使用 **Hutool** 与 `patra-common`/starters 提供的工具能力；新增前先检索是否已有。

---

## 6. 数据处理 & 一致性

* **幂等**：采集/解析/清洗流程需要可重入；为关键步骤设计幂等键或去重策略。
* **事务**：在 `app` 层编排事务；跨聚合尽量用事件最终一致；避免在 `domain` 引入事务框架。
* **仓储**：按聚合**整体持久化**；`infra` 以 MyBatis-Plus 实现，转换由 MapStruct 完成。
* **索引/查询**：ES 用于检索/分析；MySQL 作为主存；需约定好主键/路由键/索引策略（新增前先查是否已有）。

---

## 7. 基础设施 & 可观测性

* **注册/配置**：Nacos；服务与配置均外置，不在代码中硬编码敏感信息。
* **Tracing/APM**：SkyWalking（传播 traceId/spanId；关键路径埋点）。
* **调度**：XXL-Job；任务放 `adapter/scheduler`，注意幂等、重试、限流。

---

## 8. 本地开发与运行

### 8.1 启动依赖

```bash
cd docker/compose
docker-compose -f docker-compose.dev.yml up -d
```

**端口一览（dev）：**

* MySQL: `13306` | Redis: `16379` | ES: `9200`
* Nacos: `8848`（console: `4000`）| SkyWalking UI: `8088` | XXL-Job Admin: `7070`

### 8.2 构建与打包

```bash
# 全仓编译
mvn clean compile

# 指定模块
cd patra-registry && mvn clean compile

# 打包（当前阶段默认跳过测试）
mvn clean package -DskipTests
```

---

## 9. 安全与合规

* **严禁**硬编码凭据（DB 密码、API Key 等）；统一通过环境变量/配置中心注入。
* SQL 全面参数化，**禁止**拼接式注入风险；日志中避免输出敏感字段。
* 如涉及 PII/敏感数据，按最小化与脱敏原则处理。

---

## 10. 性能偏好

* 避免 N+1；善用批处理/分页/异步；尽量在 `infra` 层就近优化 IO。
* 优先考虑内存友好与流式处理；必要时引入限流/熔断。
* 在公共库（`common`/`starter`/`expr-kernel`）改动要保守，优先稳定与向后兼容。

---

## 11. 智能体行为准则（TL;DR）

* **不明确 → 先问**；**能复用 → 不重造**；**不越层 → 不破边界**。
* 以 **清晰、短小、可维护** 的实现为先；显式说明假设与权衡。
* 若子目录存在额外 `AGENTS.md`，按“就近优先”采用更细规则（根级为全局基线）。

---

## 12. codex 规则速览

> 面向 codex 智能体的可执行清单；**冲突时以本文件前述章节为准**。

### 12.1 核心原则

* 需求不明先提问；能复用不重造；严格遵守分层与依赖方向。

### 12.2 DO/枚举/JSON 规则

* DO **不用 Java enum**；持久化枚举用字符串/整数字段。
* 数据库存 JSON 列统一用 **`JsonNode`** 表示。
* DO 命名模式：`{Service}{Table}DO`（如 `RegProvenanceDO`）。

### 12.3 POJO 与 Lombok

* 不可变/值对象用 **`record`**；需要可变则用 **class + Lombok**。
* 一律不手写样板方法；`record` 不使用 Lombok，class 选 `@Data` 或组合注解。

### 12.4 工具与框架使用顺序

* **Hutool → patra-common → patra-* starters →（最后才）自定义新工具*\*。
* 映射用 **MapStruct**；DB 访问用 **MyBatis-Plus**（Mapper 需 `extends BaseMapper<DO>`）；JSON 处理用 **Jackson**。

### 12.5 安全与数据处理

* 不硬编码密钥；仅用环境变量/配置中心注入。
* **所有 SQL 参数化**；日志对敏感字段脱敏。
* 关键链路需 **幂等/去重**；失败可安全重试。
* 事务在 **app** 层编排；**domain 零框架依赖**。

### 12.6 性能与可靠性

* 避免 N+1；优先批处理/分页/异步/流式。
* 提前规划 **索引/路由键/缓存**；外部调用设置 **限流/熔断**。
* 写入路径优先 **批量** 与 **压缩** 支持；必要时开启回压与排队。

### 12.7 代码组织与命名

* 严格遵守包结构与依赖方向；禁止越层访问。
* 命名规范：

    * 控制器 `{Resource}Controller`
    * 应用服务 `{Aggregate}AppService`
    * 仓储接口 `{Aggregate}Repository` / 实现 `{Aggregate}RepositoryMpImpl`
    * MyBatis `{Entity}Mapper`；MapStruct `{Entity}Converter`

### 12.8 CQRS 与 Contract

* **读写分离**：Query 侧只读，不包含 C/U/D；模型分离（ReadModel vs Command）。
* 用例命名后缀：`*Query` / `*Command`；查询经 `contract.QueryPort`。
* 跨子系统集成：**API（Feign） → Contract（Query/View） → Adapter**。
* 内部调用路径：`/_internal/{service}/**`；服务发现按约定配置。

### 12.9 JavaDoc（极简版）

* 每个类需作者与版本：`@author linqibin @since 0.1.0`。
* 公共方法补全 `@param/@return/@throws`；复杂类加用途说明与层级/CQRS 角色。
* 仅在关键业务规则处写必要注释，避免注释噪声。

### 12.10 日志规范（@Slf4j）

* 需要日志的类使用 `@Slf4j`；统一 SLF4J API。
* 级别：**ERROR** 系统异常；**WARN** 业务违例；**INFO** 关键操作；**DEBUG** 诊断细节。
* 统一 **参数化日志** 格式：`log.info("op: id={}", id)`；
* 不打印敏感信息；异常使用 `log.error("msg", e)` 输出堆栈。
* 传递 **关联/追踪 ID**（trace/correlation）便于排查。

### 12.11 维护一致性

* 公共库（`patra-common`/starters/`expr-kernel`）改动要保守、保持向后兼容。
* 若子模块存在本地 `AGENTS.md`，**就近优先**；本节为 codex 精简清单，细则见上文。