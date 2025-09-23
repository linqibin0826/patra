# 0. 文档约定与范围

## 0.1 技术栈与版本

* 语言与框架：Java 21、Spring Boot 3.2.4、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0
* 数据访问：MyBatis-Plus 3.5.12、HikariCP
* 序列化：Jackson（禁用字段可见性自动探测，仅限显式注解/构造器），统一时区 UTC、ISO-8601 纳秒精度
* 数据库：MySQL 8.0（InnoDB、utf8mb4\_0900\_ai\_ci），无物理外键
* 架构：六边形架构（Hexagonal）+ 领域驱动设计（DDD）+ CQRS（命令/查询职责分离）

## 0.2 术语与缩写（文档内统一）

* **Registry**：系统唯一真实数据来源（`reg_*` 表族），承载数据源/端点/分页/限流/鉴权/重试/表达式与映射等配置
* **Ingest**：采集运行态（`ing_*` 表族），承载计划/切片/任务/运行/批次/游标与事件
* **Planner**：计划制定流水线（Plan/Slice/Task 生成端），受独立触发器驱动
* **Executor**：任务执行流水线（Task/Run/Batch 与游标推进端），受独立触发器驱动
* **Spec Snapshot**：将 Registry 配置**编译**为执行期不可变的快照（鉴权/分页/窗口/映射/限流/重试/两段式等）
* **Enumerator**：候选记录/ID/页的枚举策略（SearchByDateRange / ScanByCursor / TwoPhase / NoCount）
* **Slicer**：切片策略（时间窗/页区间/ID 段/预算驱动、可在线再切片）
* **Budgeter**：预算器（限流与配额的执行前/执行中约束）
* **Cursor/Watermark**：多形态游标与水位（TIME/ID/TOKEN），事件先行（`ing_cursor_event`）再推进现值（`ing_cursor`）
* **Outbox**：集成事件的幂等发布模式（不在本章展开）

## 0.3 目标与非目标

* 目标

    1. 以“配置驱动 + 执行期快照 + 抽象编排”统一 **HARVEST / BACKFILL / UPDATE** 三类采集模式
    2. 将**计划制定**与**任务执行**拆分为两个独立可伸缩单元，由两个触发器分治
    3. 用幂等键、游标水位与批次黑匣子保证**可重放、可回溯**
    4. 在不修改现有 DDL 的前提下，通过**设计规约**落地新行为
* 非目标（本阶段不覆盖）

    * OpenAPI 契约与多语言国际化、全量观测指标体系、CI/CD、限流中台化/Sentinel

## 0.4 假设与约束

* 所有 \*\_code 字段为跨环境稳定键，**不**使用物理外键，完整性由应用层保证
* 所有时间字段使用 UTC，窗口统一采用**半开区间** `[from, to)`
* Planner 与 Executor 分别由独立调度/触发器驱动，可水平扩展；两者仅通过数据库与幂等键解耦
* 生产路由遵守“温和对上游”的原则：优先遵循供应商速率与配额限制，优先正确再追求吞吐

---

# 1. 背景、问题与目标

## 1.1 背景

医学文献平台（如 PubMed、Crossref 等）在协议、分页、鉴权、限流、时间窗与数据模型等方面高度异构；同时采集诉求并不止于*
*增量（HARVEST）**，还包括**历史回填（BACKFILL）**与**内容刷新/纠错（UPDATE）**。现有两类 SQL 脚本明确了“**Registry = 真相源**、*
*Ingest = 运行态**”的职责分离，为配置驱动的抽象编排提供了稳定落点。

## 1.2 主要问题

* **异构分页与游标**：offset/page、nextToken/nextUrl、时间窗推进并存
* **两段式拉取**：先枚举 ID，再批量详情的模式普遍存在
* **速率/配额差异大**：429/Retry-After 处理不一致，易被封禁
* **配置漂移**：执行中 Registry 改动可能导致非确定性
* **幂等与一致性**：重复投递、重复入库、窗口边界抖动
* **历史缺口与修正**：回填与刷新往往与增量并行，彼此干扰风险高
* **可回放与排障**：缺乏“批次黑匣子”，难以精确重演问题现场

## 1.3 设计目标

1. **抽象统一**：通过“策略位矩阵 + Spec Snapshot”覆盖 90% 来源；提供 Provider 特化钩子处理极端个例
2. **流水线解耦**：Planner/Executor 两个小单元、两个触发器，彼此独立伸缩与演进
3. **确定性执行**：执行期快照不受后续配置漂移影响；窗口语义明确（UTC、半开）
4. **稳健可恢复**：幂等键、游标事件先行、批次黑匣子，支持精确重放与数据一致性
5. **上游友好**：预算器统一限流/配额，自适应降速与在线再切片，避免打爆上游

---

# 2. 总体架构（Hexagonal + DDD + CQRS）

## 2.1 分层与依赖（与既有约定一致）

* **API（对外契约）**：REST/RPC DTO、IntegrationEvent DTO（不依赖其他层）
* **CONTRACT（内部查询契约）**：QueryPort 与只读 ReadModel/DTO（不依赖其他层）
* **DOMAIN（领域内核）**：聚合/实体/值对象、领域事件、仓储端口（无外部依赖）
* **APP（应用服务）**：

    * Command 用例：Planner、Executor 的编排用例（依赖 DOMAIN、CONTRACT）
    * Query 用例：读侧查询编排（依赖 CONTRACT）
    * Event Publisher Port：对外发布端口（由 INFRA 实现）
* **INFRA（技术实现）**：

    * RepositoryImpl、QueryPortImpl、Outbox、RateLimiter/Retry/Transport/Expr 等端口实现
    * MyBatis-Plus 持久化与数据源配置
* **ADAPTER（协议适配）**：

    * REST Controllers、MQ Producer/Consumer、Schedulers（PlannerTrigger、ExecutorTrigger）
* **BOOT（装配启动）**：Spring Boot 应用与依赖注入、配置绑定、自动装配

> 依赖方向与既有 Mermaid 一致：ADAPTER→APP，APP→DOMAIN/CONTRACT，INFRA→DOMAIN/CONTRACT，BOOT→ADAPTER/APP/INFRA。

## 2.2 领域边界与上下文

* **Registry 上下文**（外部或邻接域）：提供数据源与端点等**配置真相**；通过 **RegistryPort** 被 Ingest 使用
* **Ingest 上下文**（本域）：计划与执行的全生命周期；通过 **EventPublisherPort** 对外发布集成事件
* **读侧上下文**（CONTRACT）：向看板/运营提供只读查询；支持 UPDATE 候选集筛选（按“陈旧性/表达式变更/外部信号”）

## 2.3 关键端口（Ports）

* **RegistryPort**：加载 `reg_*` 配置并编译为 **Spec Snapshot**
* **ExprPort**（由 starter-expr 提供实现）：模板渲染与 JSON 路径提取
* **TransportPort**：HTTP 传输（鉴权策略、签名、重试前置）
* **RateLimiterPort / RetryPort**：统一速率与退避策略（支持 429/Retry-After 自适应）
* **EventPublisherPort**：Outbox 幂等事件发布（对下游系统）
* **PlanRepository / TaskRepository / CursorRepository**：Ingest 运行态表的持久化端口
* **ReadModelQueryPort（CONTRACT）**：为 UPDATE/运营看板提供查询

## 2.4 关键组件（领域与应用）

* **Spec Compiler（Planner 内）**：将 Registry 配置编译为执行期快照（鉴权/分页/窗口/映射/限流/重试/两段式）
* **Enumerator**：抽象“如何得到候选集”（SearchByDateRange / ScanByCursor / TwoPhase / NoCount）
* **Adaptive Slicer**：按规模估算与预算生成 `ing_plan_slice`（时间窗/页/ID 段），支持在线再切片
* **Budgeter**：限流与配额在计划期和执行期的统一约束
* **Runner**（Executor 内）：

    * **Pager**：分页/令牌推进（`ing_task_run_batch`）
    * **Mapper**：响应解析与字段映射（expr），唯一键与 `updatedAt` 判定
    * **Idempotence**：批次幂等、事件幂等、入库幂等
* **Cursor Manager**：事件先行（`ing_cursor_event`）→ 乐观锁推进现值（`ing_cursor`）

## 2.5 Planner / Executor 双流水线（高层时序）

1. **PlannerTrigger** 触发 → 写入 `ing_schedule_instance`
2. **Spec Compiler** 编译快照 → 生成 `ing_plan`
3. **Adaptive Slicer** 切 `ing_plan_slice` → 下发 `ing_task(status=QUEUED)`（幂等）
4. **ExecutorTrigger** 轮询/拉取 `QUEUED` 任务（租约/CAS） → 生成 `ing_task_run`
5. **Runner** 分页推进，写 `ing_task_run_batch` → 发事件/入库（幂等）
6. **Cursor Manager** 写 `ing_cursor_event` → 推进 `ing_cursor`（FORWARD/BACKFILL 命名空间分离）
7. 任务/运行状态迁移，必要时回写“在线再切片”请求给 Planner

## 2.6 一致性与事务边界

* **数据库层面**：单表操作使用本地事务；跨表写入采用“先事件后现值”的顺序化提交
* **游标推进**：事件 append-only，现值乐观锁“仅前进”；BACKFILL 走独立命名空间，**不回退**前向水位
* **事件发布**：Outbox 表（或等效实现）与业务写入在同一事务内，消费者侧以幂等键去重
* **重放**：以 `run_id/batch_no` + `expr_hash` + `slice_signature_hash` 为定位信息，保证可重演

## 2.7 安全与合规（总则）

* **凭证最小化**：仅使用 `reg_prov_credential` 指向的必要信息，避免在运行态表中复制敏感值
* **日志脱敏**：请求/响应摘要存档不含密钥；需要签名的源采用特化钩子在 TransportPort 实现
* **速率与配额**：统一通过 Budgeter 执行，避免任意并发对上游造成冲击

# 3. 与 Registry 的契约（唯一真实数据来源）

## 3.1 责任边界与总则

* **Registry（`reg_*` 表族 + 系统字典 `sys_*`）= 唯一真实数据来源**：承载“数据源（Provenance）与端点（Endpoint）及其行为策略”的
  **声明式配置**，包括鉴权、HTTP 请求模板、分页、时间窗口、批处理、重试、限流、请求参数映射、表达式规则、能力标注、字段字典与凭据等。
* **Ingest 只消费配置，不在运行期修改配置**；运行期由 Planner 将 Registry 配置\*\*编译为执行快照（Spec Snapshot）\*\*并固化在
  `ing_plan/ing_plan_slice`，Executor 仅使用快照执行，不受后续配置变更影响。
* **编码即主识别**：全链路使用 \*\_code（如 `provenance_code`、字典项 code、`endpoint_name` 等）作为稳定键；不建物理外键，完整性由应用层校验。
* **时区与时间语义**：Registry 中所有与时间相关的阈值/窗口/偏移解释为 **UTC** 语义，Planner 层统一规范化为半开区间
  `[from, to)`。

## 3.2 Registry 数据域与关键表（职能视图）

> 以下为“职能-表”的对齐关系，字段以你现有表为准，这里给出功能边界与读取要点。

* **数据源与端点**

    * `reg_provenance`：来源定义（`provenance_code`、名称、说明、默认策略基线/安全策略等）。
    * `reg_prov_endpoint_def`：端点定义（`endpoint_name` / 用途：search/detail/metrics…，HTTP 方法、基础路径、响应主实体路径、是否两段式拉取等）。
* **HTTP 与鉴权**

    * `reg_prov_http_cfg`：HTTP 层通用配置（基础 URL、默认 Header/Query 模板、超时、压缩、代理、重定向策略等）。
    * `reg_prov_credential`：凭据（API Key、Bearer、OAuth2 客户端等），与来源/端点的绑定关系与优先级。
* **分页与时间窗**

    * `reg_prov_pagination_cfg`：分页策略（offset/page/size、`nextToken`/`nextUrl`、最大页/最大 size、排序要求、初始游标等）。
    * `reg_prov_window_offset_cfg`：时间窗推进策略（`updatedSince` 支持、初始水位、窗口偏移/安全延迟 `safetyLag`
      、最大窗口粒度、越界处理）。
* **可靠性与吞吐**

    * `reg_prov_retry_cfg`：重试策略（可重试错误范围、退避曲线、最大尝试）。
    * `reg_prov_rate_limit_cfg`：限流与配额（QPS、突发、日配额、供应商 Header 自适应策略）。
    * `reg_prov_batching_cfg`：批处理约束（每请求最大记录数、ID 批量查询上限、两段式合并策略）。
* **请求/响应映射与表达式**

    * `reg_prov_api_param_map`：请求参数映射（命名 → Query/Header/Body 放置位置、默认值/表达式模板、必填校验）。
    * `reg_prov_expr_capability`：来源/端点可用表达式能力宣告（允许的函数库/安全沙箱限制）。
    * `reg_prov_expr_render_rule`：表达式渲染规则（时区格式化、空值策略、模板合成顺序）。
    * `reg_expr_field_dict`：字段字典/路径约定（响应 `itemsPath`、`nextTokenPath`、唯一键路径 `idPath`、更新时间路径
      `updatedAtPath` 等）。
* **系统字典**

    * `sys_dict_type` / `sys_dict_item` / `sys_dict_item_alias`：统一的 code 空间（如 `http_method`、`ing_operation`、
      `ing_cursor_type`、`ing_namespace_scope` 等）。

## 3.3 运行期快照（Spec Snapshot）编译流程（Planner 内）

> 目标：将分散在 `reg_*` 的声明式配置合并、校验与规范化，形成**不可变快照**，在 `ing_plan` 记录“表达式原型”（proto）与在
`ing_plan_slice` 记录“局部化表达式”（slice 级）。

### 3.3.1 输入

* 触发上下文：`provenance_code`、`endpoint_name`、`operation_code(HARVEST|BACKFILL|UPDATE)`、窗口/参数/优先级等。
* Registry 读取集：上述关键表中与该来源/端点相关的所有配置（含默认/继承链）。

### 3.3.2 合并与优先级（从低到高）

1. 平台级默认（系统内置）
2. `reg_provenance` 级默认
3. `reg_prov_endpoint_def` 端点级覆盖
4. `*_cfg` 子域覆盖（http/pagination/window/retry/rate/batching）
5. 触发入参/运行时覆盖（如这次计划特有的窗口/限速/批量大小上限）

> 原则：**显式覆盖优先**、**端点优于来源**、**运行时优于静态**。合并过程记录“来源标签（provenance）/端点/操作/版本/最终哈希”。

### 3.3.3 规范化与校验

* **字典校验**：所有 code（方法、分页类型、游标类型、操作类型、命名空间等）必须存在于 `sys_dict_item`，否则编译失败。
* **结构校验**：必填项（基础 URL、HTTP 方法、关键路径如 `itemsPath/idPath/updatedAtPath`、分页策略关键字段）存在性与类型合理性。
* **安全校验**：表达式能力对齐 `reg_prov_expr_capability`；模板渲染规则对齐 `reg_prov_expr_render_rule`。
* **窗口校验**：`[from,to)` 合法、`from < to`、`safetyLag` 非负；操作类型与窗口/游标的兼容性（HARVEST 必须可前进；BACKFILL
  禁止回退前向水位）。
* **性能/配额校验**：页大小、最大页数、ID 批量上限不超过 `rate/batching` 与供应商限定。
* **两段式一致性**：若标注为 two-phase（ID→详情），须提供 ID 列表端点与详情端点的对应模板与分页策略。

### 3.3.4 产物

* **Spec Snapshot（规范化结构体）**：

    * 鉴权/HTTP：method、baseUrl、pathTemplate、headersTemplate、queryTemplate、bodyTemplate、timeout、压缩、代理、签名钩子引用、凭据引用。
    * 分页：`type=OFFSET|PAGE|TOKEN|URL|TIME`、初始位点、`pageSize/maxPage`、`nextTokenPath/nextUrlPath`、排序要求。
    * 时间窗：`supportsUpdatedSince`、`safetyLag`、最大/最小窗口粒度、窗口推进策略与越界规则。
    * 可靠性：retryPolicy、rateLimitPolicy、配额上限、供应商 Header 自适应策略。
    * 批处理：idBatchSize、合并/拆分规则。
    * 表达式：渲染规则、允许函数、响应路径（`itemsPath/idPath/updatedAtPath` 等）。
    * 映射：请求参数映射、输出字段映射（用于标准化 DTO/IntegrationEvent）。
    * 两段式切换：是否 two-phase、两端点的关联与批处理阈值。
* **哈希与版本**：

    * `expr_proto_hash`：表达式原型哈希（落在 `ing_plan`）；
    * `expr_hash`：局部化表达式哈希（落在 `ing_plan_slice`）；
    * `spec_fingerprint`：整体快照指纹（可存 plan 侧），用于审计与回放对齐。
* **错误与警告**：不可编译时报错并停止计划；可降级项以 Warning 记录在 `record_remarks`。

## 3.4 策略位来源映射（Registry → Spec 字段）

> 仅列“映射原理与示例”，字段以你的表定义为准。

| 策略位       | Registry 来源                                            | 说明（编译后的 Spec 字段示意）                                                                        |                 |                                            |                               |                                                   |
|-----------|--------------------------------------------------------|-------------------------------------------------------------------------------------------|-----------------|--------------------------------------------|-------------------------------|---------------------------------------------------|
| 鉴权        | `reg_prov_credential` + `reg_prov_http_cfg`            | \`auth.type=NONE                                                                          | API\_KEY        | BEARER                                     | OAUTH2`，`auth.location=HEADER | QUERY`，`oauth2.refresh=true/false`，`signingHook\` |
| HTTP 请求模板 | `reg_prov_http_cfg`、`reg_prov_api_param_map`           | `method/baseUrl/pathTemplate/headersTemplate/queryTemplate/bodyTemplate`；参数映射渲染到对应位置      |                 |                                            |                               |                                                   |
| 分页        | `reg_prov_pagination_cfg`                              | `pagination.type`, `pageSize/maxPage`, `initial`, `nextTokenPath/nextUrlPath`, `orderBy`  |                 |                                            |                               |                                                   |
| 时间窗       | `reg_prov_window_offset_cfg`                           | `supportsUpdatedSince`, `safetyLag`, `maxWindow`, `minWindow`, \`watermarkKey=updated\_at | published\_at\` |                                            |                               |                                                   |
| 重试        | `reg_prov_retry_cfg`                                   | `retryPolicy.backoff=exponential/jitter`, `maxAttempts`, `retryableStatus/errCodes`       |                 |                                            |                               |                                                   |
| 限流/配额     | `reg_prov_rate_limit_cfg`                              | `rate.qps/burst/dailyQuota`, `headerAdaptive=Retry-After/X-RateLimit-*`                   |                 |                                            |                               |                                                   |
| 批处理       | `reg_prov_batching_cfg`                                | `idBatchSize`, `combineThreshold`, `twoPhase.enabled/threshold`                           |                 |                                            |                               |                                                   |
| 表达式能力     | `reg_prov_expr_capability`/`reg_prov_expr_render_rule` | `expr.allowedFunctions`, `nullPolicy`, `timeFormat`, `timezone=UTC`                       |                 |                                            |                               |                                                   |
| 响应解析      | `reg_expr_field_dict`（或端点定义）                           | `itemsPath`, `idPath`, `updatedAtPath`, `hasMorePath/totalPath`                           |                 |                                            |                               |                                                   |
| 请求参数      | `reg_prov_api_param_map`                               | `requestParams[].{name,location,default,required,expr}`                                   |                 |                                            |                               |                                                   |
| 端点用途      | `reg_prov_endpoint_def`                                | \`endpoint.role=SEARCH                                                                    | DETAIL          | METRICS`、`twoPhase.linkedEndpoint=DETAIL\` |                               |                                                   |

## 3.5 变更与一致性策略

* **运行期不感知配置变更**：一切以 **Spec Snapshot** 为准；同一 `ing_plan` 生命周期内快照不变。
* **增量演进**：配置更新仅影响**新触发**的计划；老计划继续使用旧快照。
* **灰度**：可通过在 `reg_provenance` 增设“发布标志/发布批次”字段（如已有）或使用“环境标签”实现灰度快照编译。
* **回放/审计**：`spec_fingerprint` + `expr_proto_hash/expr_hash` + `slice_signature_hash` 三要素锁定一次执行上下文。

## 3.6 失败处理与降级

* **编译失败（必填缺失/字典非法/两段式不一致）**：Planner 终止该 `ing_plan` 生成，记录错误并告警。
* **可降级项（count 不支持/部分 header 无效）**：标记 Warning，切换到退化路径（如抽样估算总量、以 `hasNext` 推进），并写入
  `record_remarks`。
* **安全降级**：鉴权失效/OAuth2 刷新失败 → 立刻阻断执行线，提示凭据维护；不得以匿名兜底有敏感范围的端点。

## 3.7 缓存与性能

* **冷/热路径**：Registry 读取走只读连接池；Planner 端对“静态块”（字典、能力、通用规则）设置本地缓存（TTL 可配），对“来源-端点”配置设置短
  TTL/ETag 缓存。
* **最少 SQL**：按 `provenance_code + endpoint_name` 聚合批量读取相关 `*_cfg`，避免 N+1 次查询。
* **哈希短路**：若新触发与最近一次快照指纹一致，可复用缓存快照（需谨慎，默认关闭，作为优化开关）。

## 3.8 安全与合规

* **凭证隔离**：`reg_prov_credential` 仅持久化在 Registry；快照中只引用凭据 ID/别名，不复制敏感值到 `ing_*`。
* **脱敏与签名**：请求日志摘要仅保留必要元数据；签名算法通过 Transport 特化钩子实现，不在快照中保存可逆信息。
* **授权边界**：不同来源的凭据使用最小权限原则；Planner/Executor 的运行身份仅可读 Registry，不可写。

## 3.9 示例：Spec Snapshot（示意 JSON）

> 字段命名为“概念名”，便于理解；实际存储拆分为 `ing_plan.expr_proto_snapshot`（原型）与 `ing_plan_slice.expr_snapshot`（局部化）。

```json
{
  "provenanceCode": "pubmed",
  "endpointCode": "search",
  "operation": "HARVEST",
  "http": {
    "method": "GET",
    "baseUrl": "https://eutils.ncbi.nlm.nih.gov",
    "pathTemplate": "/entrez/eutils/esearch.fcgi",
    "headersTemplate": {
      "Accept": "application/json"
    },
    "queryTemplate": {
      "db": "pubmed",
      "retmode": "json",
      "term": "${expr.q}",
      "retstart": "${page.offset}",
      "retmax": "${page.size}",
      "api_key": "${credential.apiKey}"
    },
    "timeoutMs": 10000
  },
  "auth": {
    "type": "API_KEY",
    "location": "QUERY",
    "credentialRef": "cred_pubmed"
  },
  "pagination": {
    "type": "OFFSET",
    "pageSize": 100,
    "maxPage": 1000,
    "orderBy": "updated"
  },
  "window": {
    "supportsUpdatedSince": true,
    "safetyLag": "PT10M",
    "watermarkKey": "updated_at"
  },
  "retry": {
    "policy": "exponential_jitter",
    "maxAttempts": 5,
    "retryableStatus": [
      429,
      500,
      502,
      503
    ]
  },
  "rateLimit": {
    "qps": 5,
    "burst": 10,
    "headerAdaptive": true
  },
  "batching": {
    "idBatchSize": 200,
    "twoPhase": false
  },
  "expr": {
    "allowedFunctions": [
      "now",
      "formatDateTime",
      "coalesce"
    ],
    "timezone": "UTC",
    "nullPolicy": "omit"
  },
  "response": {
    "itemsPath": "$.esearchresult.idlist",
    "idPath": "$",
    "updatedAtPath": "$.updated",
    "hasMorePath": "$.esearchresult.more"
  },
  "mapping": {
    "requestParams": [
      {
        "name": "term",
        "location": "QUERY",
        "required": true,
        "expr": "renderQuery()"
      }
    ],
    "outputFields": [
      {
        "name": "provider_id",
        "expr": "$"
      },
      {
        "name": "title",
        "expr": "$.title"
      },
      {
        "name": "updated_at",
        "expr": "$.updated"
      }
    ]
  },
  "specFingerprint": "af8f2e...c1"
}
```

## 3.10 与 Ingest 的落点对应

* `ing_plan`：记录 `provenance_code/endpoint_name/operation_code/window_from/to`、`expr_proto_hash/expr_proto_snapshot`、
  `spec_fingerprint`。
* `ing_plan_slice`：记录 `slice_spec`（窗口/页/ID 边界）、`expr_hash/expr_snapshot`（已局部化，包含本 slice 的窗口与分页初始位）、
  `provenance_code`。
* `ing_task`：记录 `provenance_code/operation_code/credential_id`、`params`（规范化执行参数）与 `idempotent_key`。
* Executor 只读上述快照/参数，不回读 Registry。

下面是第 **4 章：策略位矩阵（可配置抽象能力）** 的完整详细设计。它是编排内核的“变位表”，决定 90%
来源在同一套逻辑下如何被正确、稳定地拉通。文档严格对齐你现有的 Registry / Ingest SQL 与上一章快照编译流程，*
*不写代码，仅给可执行的设计规约**。

---

# 4. 策略位矩阵（可配置抽象能力）

> 目标：把“来源差异”压缩到一组有限、可组合的策略位（Strategy Bits）。每个策略位都有**来源表**（Registry）、**快照字段**（Spec）、*
*验证规则**、**默认值/降级**与**执行期约束**，确保 Planner/Executor 两条流水线在不改 DDL 的前提下稳定运行。

## 4.1 总则与优先级

* **优先级（从低到高）**：平台缺省 < `reg_provenance` 缺省 < `reg_prov_endpoint_def` 端点级 < 子域 `*_cfg` <触发入参（本次计划特有覆盖）。
* **验证失败即拒绝编译**：必填项缺失、字典 code 非法、互斥配置冲突，Planner 直接终止本次计划，记录错误并告警。
* **可降级则降级**：无 `count`、无 `updatedSince`、无明确 `hasMore` 等场景，进入既定降级路径（见各策略位“降级”）。
* **执行期不变**：所有策略位在 Spec Snapshot 固化；Executor 严格按快照执行，不受 Registry 后续变更影响。
* **UTC + 半开区间**：涉及时间窗一律按 UTC、半开 `[from, to)` 处理。

---

## 4.2 鉴权（Auth）与 HTTP（Transport）策略

| 项       | Registry 来源                                    | Spec 字段                                                   | 验证与默认                  | 降级与约束           |                   |          |            |                                 |
|---------|------------------------------------------------|-----------------------------------------------------------|------------------------|-----------------|-------------------|----------|------------|---------------------------------|
| 鉴权类型    | `reg_prov_credential`                          | \`auth.type=NONE                                          | API\_KEY               | BEARER          | OAUTH2            | CUSTOM\` | 必填；与端点要求一致 | 不可降级到匿名的受限端点；OAuth2 必须具备刷新/过期策略 |
| 凭据放置    | `reg_prov_http_cfg` + `reg_prov_credential`    | \`auth.location=HEADER                                    | QUERY                  | BODY\`          | 与方法匹配；避免 GET+BODY | 违规直接失败   |            |                                 |
| 自定义签名   | `reg_prov_http_cfg`                            | `auth.signingHook`                                        | 如声明 `CUSTOM` 必须给出签名钩子名 | 缺失失败            |                   |          |            |                                 |
| HTTP 方法 | `reg_prov_endpoint_def`                        | `http.method`                                             | 必填，字典校验 `http_method`  | 无               |                   |          |            |                                 |
| 基础 URL  | `reg_prov_http_cfg`                            | `http.baseUrl`                                            | 必填，HTTPS 优先            | 必须 HTTPS，除非显式允许 |                   |          |            |                                 |
| 模板      | `reg_prov_http_cfg` + `reg_prov_api_param_map` | `pathTemplate/headersTemplate/queryTemplate/bodyTemplate` | 表达式可渲染、参数位置合法          | 空值策略见 4.8       |                   |          |            |                                 |

**执行期约束**

* 超时（connect/read）默认 10s/30s（可配），最大不超过 120s。
* 重定向默认关闭；如开启仅限 3xx → GET/HEAD 且最多 3 次。
* 请求/响应日志摘要**脱敏**（不落密钥与签名）。

---

## 4.3 分页（Pagination）策略

### 4.3.1 类型与驱动字段

* **OFFSET**：`offset` / `limit` 驱动（或 `retstart` / `retmax` 等）。
* **PAGE**：`page` / `size` 驱动（基 1 或基 0 需在 cfg 指明）。
* **TOKEN**：`nextToken` 驱动（不可预知总数）。
* **URL**：`nextUrl` 驱动（服务端返回下一页完整 URL）。
* **TIME**：按时间窗推进（无显式分页参数或分页深度受限时使用）。

**Registry → Spec**

* 来源：`reg_prov_pagination_cfg`。
* 快照：`pagination.type`, `pageSize`, `maxPage`, `initial`, `orderBy`, `nextTokenPath`, `nextUrlPath`, `base1`（仅 PAGE）。

### 4.3.2 验证规则

* `pageSize` 必须 ≤ 供应商上限；`maxPage` 为正整数或空（不限制）。
* `TOKEN` 必须提供 `nextTokenPath`；`URL` 必须提供 `nextUrlPath`。
* `PAGE` 需指明基数（`base1=true|false`）。
* `TIME` 与时间窗策略（4.4）联动，必须存在 `supportsUpdatedSince` 或服务端排序保证。

### 4.3.3 hasNext 计算与降级

* **优先级**：显式 `hasMorePath` > `nextToken/nextUrl` 非空 > `items.size < pageSize`（保守） > 达到 `maxPage` 即停。
* 无 `hasMorePath` 且 `items.size == pageSize` 时，**默认认为可能有下一页**（除非已命中 `maxPage`）；以防漏数。
* 无 `pageSize` 的 `TOKEN/URL`，依赖 `next*` 是否存在；若源偶发漏 `next*` 导致断流，允许在 Executor 中启用“**尾页探针**
  ”：最后一页再重试 1 次。

### 4.3.4 互斥与桥接

* `TOKEN/URL` 与 `PAGE/OFFSET` 互斥；若 Registry 同时声明，Planner 失败。
* 达到 `maxPage` 且源支持时间窗时，允许 **桥接到 TIME**：按时间窗切片继续推进（由 Planner 触发在线再切片）。

---

## 4.4 时间窗（Window）与水位（Watermark）策略

### 4.4.1 基本语义

* **半开区间**：统一使用 **`[from, to)`**，保证切片拼接不重不漏。
* **统一时标**：所有时间一律**规范化为 UTC，精度 TIMESTAMP(6)**；源端本地时区在快照编译期完成转换。
* **安全延迟（safetyLag）**：执行截断点为 **`now - safetyLag`**，避免采到未稳定/晚到记录（默认 5–15 分钟，可配置）。
* **水位键（watermarkKey）**：通常为 `updated_at` 或等价字段；必须能从响应中**可解析**。
* **推进顺序（强约束）**：写水位时先**记录事件**（`ing_cursor_event`），再**更新当前值**（`ing_cursor`）——“**先事件、后现值**”。
* **仅前进（Monotonic）**：`ing_cursor` 只允许**严格前进**；当新值 `<=` 旧值时拒绝更新（记录诊断，不降级覆盖）。
* **比较规则**（按游标类型统一判定）：

    * `TIME`：比较 **UTC `normalized_instant`**；
    * `ID`：比较 **`normalized_numeric`**（将可比较的有序号/雪花 ID 归一）；
    * `TOKEN`：不参与长期排序，仅作**批次断点**（由 `run_batch` 管理）。
* **命名空间语义**：

    * **HARVEST**：`namespace_scope=EXPR`（或 `GLOBAL`），`direction=FORWARD`；
    * **BACKFILL**：`namespace_scope=CUSTOM`（如以 `plan_id`/`slice_signature_hash` 作为 `namespace_key`），
      `direction=BACKFILL`，**不回退**前向水位；
    * **UPDATE**：使用 `ID` 或“刷新检查时间”进度指针，独立于 HARVEST 水位。
* **粒度对齐**：窗口对齐到 `align`（分钟/小时/天），以保证跨任务一致切片与可复现性。

---

### 4.4.2 Registry → Spec

* **配置来源**：`reg_prov_window_offset_cfg`。
* **快照编译字段**（写入 Plan/Slice 的执行规格快照）：

    * `window.supportsUpdatedSince`（是否支持服务端按 `updated` 过滤）；
    * `window.safetyLag`（默认值与上限）；
    * `window.maxWindow` / `window.minWindow`（最大/最小窗口宽度）；
    * `window.watermarkKey`（如 `updated_at`、`last_modified` 等）；
    * `window.align`（对齐粒度：`min` / `hour` / `day`）；
    * `window.timezone=UTC`（规范化承诺）；
    * `window.boundaryPolicy='[from,to)'`（边界策略指纹）。
* **指纹与复用**：将上述字段参与 `spec/expr` 指纹计算；相同指纹的触发可复用快照，指纹变化则重新编译。

---

### 4.4.3 验证与默认

* **能力降级**：当 `supportsUpdatedSince=false` 时，禁止宣称 `HARVEST` 的“服务端过滤”；必须改为

    * `TIME` 窗 + **客户端过滤**（要求源端 `updated` 或替代排序字段**稳定且单调**），或
    * 走 `UPDATE` 路线（基于既有记录候选集刷新）。
* **参数合法性**：`maxWindow ≥ minWindow`；`safetyLag ≥ 0`；`align` 与窗口宽度需一致（小时级窗口不能用天级对齐）。
* **必填要求**：`watermarkKey` 必填；若源**无更新时间**，需提供**替代排序字段**并声明**单调性**，否则不得使用 HARVEST（仅允许
  UPDATE）。
* **默认策略**：

    * `safetyLag` 默认 10 分钟（可按来源覆盖）；
    * 初始窗口宽度取 `min(maxWindow, 最近历史推荐值)`，由在线再切片自适应收敛；
    * HARVEST 一律 `direction=FORWARD`；BACKFILL 强制 `CUSTOM` 命名空间与 `direction=BACKFILL`；UPDATE 使用独立进度指针。
* **更新判定**：以“**唯一业务键（来源+端点+providerItemId）+ `updatedAt`**”为覆盖依据；
  `incoming.updatedAt > existing.updatedAt` 才更新。

---

### 4.4.4 边界规则

* **边界值归属**：等于 `to` 的记录**归下一窗**；等于 `from` 的记录归当前窗（统一半开区间）。
* **跨时区处理**：Planner 将触发入参与 Registry 声明**统一转换为 UTC**；切片按 `align` 对齐后固化进快照，执行期不得漂移。
* **晚到与乱序**：通过 `safetyLag` 降低晚到概率；仍发生时依赖幂等（唯一键 + `updatedAt` 覆盖）保证最终一致。
* **在线再切片（自适应）**：当观测到“窗口过大、页数过多、频繁 429/超时/超配额”时，对**剩余窗口**按二分重切，形成新 `PlanSlice`
  与 `Task`（原切片仅就已完成部分推进水位）。
* **并发一致性**：同一 `(provenance, operation, cursor_key, namespace)` 的水位以“**先事件后现值** + **仅前进** +
  主键唯一约束”实现幂等与无锁并发；多执行器同时推进不会倒退。

---

## 4.5 排序（Ordering）与一致性

* **orderBy**：来源声明要求（如 `updated desc`）。若未声明，默认 `updated desc`。
* **一致性要求**：若返回集在执行期可能插入新项（写扩散），**必须**使用 `safetyLag` 或服务端快照机制（有些源提供 `asOf`）；否则
  HARVEST 的“最后一页重复拉取一次”作为保守兜底。
* **去重策略**：`(provenance_code, endpoint, provider_id)` 唯一；以 `updatedAt` 决定覆盖。

---

## 4.6 两段式拉取（Two-Phase: IDs → Details）

* **何时启用**：搜索端点仅返回 ID 列表或精简视图，需要再按 ID 拉详情；或详情端点的字段映射与聚合逻辑与搜索响应不一致。
* **Registry → Spec**：`reg_prov_endpoint_def` 标记 `endpoint.role=SEARCH/DETAIL` 与 `twoPhase.enabled=true`；
  `reg_prov_batching_cfg` 定义 `idBatchSize`、合并策略。
* **编排规则**：

    * **Phase 1**：枚举器产出 ID 流（分页/窗口遵循 4.3/4.4）；
    * **Phase 2**：按 `idBatchSize` 聚合成批请求详情端点；
    * 失败隔离：单批失败不影响已成功的前置批；支持“坏 ID”黑名单。
* **统计对齐**：如果 SEARCH 提供总数而 DETAIL 不提供，**以 SEARCH 为估计值**，执行期以实际成功条数校准。

---

## 4.7 响应解析（Response Parsing）

* **必填路径**：`itemsPath`（列表或 ID 列表）、`idPath`（唯一键）、`updatedAtPath`（更新时间）——三者缺一不可；否则 Planner 失败。
* **可选路径**：`hasMorePath`、`totalPath`、`nextTokenPath/nextUrlPath`（TOKEN/URL 必需）。
* **类型与容错**：

    * `itemsPath` 可指向数组或对象（对象需再指定子路径为数组）；
    * `updatedAt` 必须可转成 UTC 时间戳（支持 ISO-8601 / epoch ms 等，转换规则写在 `reg_prov_expr_render_rule`）。
* **空值策略**：见 4.8 表达式渲染；`idPath` 解析为空 → 该条**数据污染**，入隔离库，不传播。

---

## 4.8 表达式（Expr）与模板渲染策略

* **能力来源**：`reg_prov_expr_capability` + `reg_prov_expr_render_rule`。
* **默认规则**：

    * 时区：UTC；时间格式：ISO-8601 纳秒；
    * Null 处理：`nullPolicy=omit`（缺省不输出，避免生成空参数/空字段）；
    * 仅开放白名单函数（`now/formatDateTime/coalesce/concat/jsonPath` 等），禁止 I/O 与反射。
* **渲染顺序**：先全局上下文（plan/slice/window/page）→ 再来源级默认 → 再端点级 → 最后运行时参数。
* **失败处理**：表达式执行异常 → 该请求跳过并记错误；若为批量字段映射，单条失败入隔离库，其余继续。
* **防注入**：对 Query/Header/Body 渲染后的关键参数做白名单/正则校验；失败则拒绝发起请求。

---

## 4.9 去重、唯一键与更新判定

* **唯一键**：`(provenance_code, endpoint_name, provider_id)`。
* **更新覆盖**：若 `incoming.updatedAt > existing.updatedAt` 则覆盖；等于时以来源优先级或稳定排序键（如 provider
  内部版本号）决定，默认**不覆盖**。
* **事件幂等键**：同唯一键；与 Outbox 配合确保下游“至多一次可见，多次幂等”。
* **批次幂等**：`ing_task_run_batch` 以 `(run_id, batch_no)` 与 `(run_id, before_token)` 唯一；再以 `idempotent_key`做最终兜底。

---

## 4.10 速率限制（Rate Limit）与配额（Quota）

### 4.10.1 目标与术语

* **目标**：在**不打爆上游**（429/封禁/配额用尽）的前提下，最大化吞吐，并对不同来源/端点/凭据保持**公平与可预测**。
* **Rate Gate（集中式速率门）**：一个**全局协调**的令牌服务；执行器在**每次请求之前**向其申请令牌。
* **Quota（配额预算）**：供应商给出的**时间窗口内请求上限**（如日/小时）；Planner 在计划期“预占”，Executor 在运行期“扣减”。

---

### 4.10.2 速率门抽象与键（Key）

* **速率键**：`rateKey = (provenance_code, endpoint_name [, credential_id])`

    * 默认到端点级；当供应商配额与鉴权绑定时，**加上 credential\_id**。
    * 如供应商按 IP 限流，运维层再加一层 `(ip)` 分片（实现层面可映射进 key）。
* **配置来源**：`reg_prov_rate_limit_cfg`（Registry）

    * 字段建议（示例）：`refillRatePerSec`、`burstCapacity`、`warmupSec`、`retryAfterRespect=true`、`quotaDaily/hours`、`demoteRateMaxMultiplier`。
* **调用协议（抽象）**：

    * `acquire(rateKey, cost=1, deadline=Δt)` → `ALLOW | WAIT_UNTIL(ts) | REJECT(reason)`
    * `feedback(rateKey, signal)`：`OK | HTTP_429 | RETRY_AFTER=Δt | LIMIT_REMAINING=n | WINDOW_RESET=ts | 5xx_TRANSIENT`。

---

### 4.10.3 令牌与决策（算法）

* **令牌桶（Token Bucket）**

    * **补充速率** `r`（permits/sec），**桶容量** `B`（突发）；初始装满 `B`。
    * **成本** `cost`（一般为 1），可按响应体权重动态调整（例如详情接口 `cost=2`）。
    * 当 `tokens ≥ cost` → `ALLOW` 并扣减；否则计算等待时间 `wait = (cost - tokens) / r`：

        * `wait ≤ deadline` → `WAIT_UNTIL(now + wait)`（执行器“睡到”该时刻再发）；
        * 否则 `REJECT(OVER_DEADLINE)`（把任务稍后再调度）。
* **预取与本地化（降低争用）**

    * 执行器可从 Rate Gate **批量预取 N 个令牌的租约**（短 TTL），本地耗尽或超时归还，减少集中式竞争。
* **公平量子（避免饥饿）**

    * 同一 `rateKey` 下给单个任务的连续令牌不超过 `quantum`（如 10），强制在任务间轮转，防止大任务独占。

---

### 4.10.4 自适应降速与供应商反馈（AIMD）

* **AIMD（加性增、乘性减）**

    * 正常：每 `coolDown` 周期**小幅上调** `r += δ`，直至 `reg_prov_rate_limit_cfg.refillRatePerSec` 上限。
    * 退让：遇 **429 或 5xx-Transient** → `r = max(r / demoteRate, r_min)`（`demoteRate` 默认 2–4，可配；`r_min ≥ 0.1`）。
* **Retry-After 与配额头**

    * 存在 `Retry-After=Δt`：Rate Gate 置**冷却期**到 `now+Δt`（期间直接 `WAIT/REJECT`）。
    * 供应商头 `X-RateLimit-Remaining/Reset`：重置**桶余量**与**窗口重置时点**，优先级高于本地推断。
* **抖动（Jitter）**

    * WAIT 与冷却期都引入 5–15% 抖动，避免大量执行器在同一时刻“惊群”。

---

### 4.10.5 配额预算（Quota Budget）

* **计划期预占（Planner）**

    * 从 `reg_prov_rate_limit_cfg.quota*` 读取**日/小时**配额与安全边际（如 10% 头寸保留），计算该 `rateKey` 在**本窗口内**可用预算 `quotaRemaining`。
    * 为每个 `PlanSlice` 估算**请求数**（`expectedRequests`），**不超过** `quotaRemaining`；超额部分延后或切更细。
* **运行期扣减（Executor）**

    * 每次请求成功后**原子扣减**该 `rateKey` 的当前窗口余量；余量 ≤ 0 →

        * **软超**（≤ safety buffer）：降并发/延后；
        * **硬超**（< 0）：**暂停该 key 的出队**并把任务回退到 `scheduled_at+Δt`（窗口重置后再跑）。
* **优先级与配额分配**

    * 全局优先级：`HARVEST > UPDATE > BACKFILL`（可配置）。
    * 当 `quotaRemaining` 不足时，优先满足高优先级操作，并把低优先级切片**分期**（跨窗口滚动）。

> 备注：配额账本的落地可采用“集中式计数 + TTL 窗口”（如分布式缓存）或与 Rate Gate 同源实现；本设计阶段不强制新增表。

---

### 4.10.6 与 Planner / Executor 的协作

* **Planner → Rate Gate**

    * 调用 `estimate(rateKey, horizon)` 获取“**可用预算与建议并发**”，用于决定切片粒度与出队节奏。
    * 将 `budgetHint` 与 `expectedRequests` 写入 `plan_slice.slice_spec`，作为执行期参考。
* **Executor → Rate Gate**

    * **每次请求前** `acquire()`；若 `WAIT_UNTIL`，按返回时间挂起批次；若 `REJECT`，将任务**回写**到稍后的 `scheduled_at`。
    * **每次请求后** `feedback()`：上报状态码、`Retry-After`、剩余额度等，用于自适应调参。
* **停止/恢复**

    * 当 Rate Gate 对某 `rateKey` 进入**冷却或配额耗尽**状态，Executor 不再出队该 key 的任务；窗口重置或冷却结束自动恢复。

---

### 4.10.7 观测与黑匣子字段（最小集）

* 在 `ing_task_run_batch.response_digest` 中**追加**：

    * `rate.limitKey`（序列化的 `rateKey`）、`rate.permitCost`、`rate.waitMs`、`rate.decision(ALLOW|WAIT|REJECT)`、
    * `rate.r(now)`、`rate.burst`、`rate.cooldownUntil`（如有）、`quota.remaining/limit/windowReset`（如读到供应商头）。
* 在 `stats` 聚合：`429_count / retryAfter_hits / demote_events / wait_total_ms / permits_total / quota_soft/hard_hits`。
* **报警阈值**：`429_rate > X%`、`hard_quota_hits > 0`、`average_wait_ms ↑` 持续 T 分钟。

---

### 4.10.8 默认值与回退

* **缺省**（Registry 未配置时）：`refillRatePerSec=1`、`burstCapacity=1`、`warmupSec=0`、`demoteRate=2`、`r_min=0.1`、`quota* = null`。
* **强制尊重 Retry-After**：即使 Registry 未开启，也**必须**遵守服务端 `Retry-After`。
* **失联回退**（Rate Gate 不可用）：执行器退化为**本地速率门**（同一 key 进程内互斥），并把速率保守到 `min(本地默认, 历史观测的 P10)`。

---

### 4.10.9 边界与例外

* **多端点共享配额**：若供应商配额**跨端点**共享，`rateKey` 上升到 `(provenance_code [, credential_id])`，`endpoint_name` 只作为次级维度做**配额内分配权重**。
* **两段式（ID→详情）**：可为 `search` 与 `detail` 设置**不同 cost** 与子配额（如 `search:detail = 1:3`），避免详情洪峰挤占搜索。
* **强突发窗口**：当供应商允许短时高突发，可把 `burstCapacity` 提高，**但**启用更强的 `demoteRate` 与更长的 `coolDown`；Planner 也应缩小切片以分摊峰值。

**落地要点**

* 速率与配额是**跨实例的集中协调**问题；**所有 HTTP 请求**都必须经过 Rate Gate 的 `acquire()`。
* Planner 决定“**切多大、发多快**”，Executor 保证“**不越线、会退避**”。
* 自适应机制用“**AIMD + 供应商头**”双轨控制，既能迅速退让，也能慢速恢复。

---

## 4.11 重试（Retry）与退避（Backoff）

* **Registry → Spec**：`reg_prov_retry_cfg`。
* **可重试分类**：5xx / 网络超时 / 429（配合限流）/ 供应商声明的暂态错误码；4xx 业务错误默认不可重试（除 408/409/423
  等经声明允许者）。
* **退避策略**：`exponential_jitter` 缺省（最小 100ms，最大 30s，倍率 2.0，随机抖动 20%）；最大尝试 `maxAttempts` 默认 5。
* **幂等配合**：重试前检查批次幂等键是否已存在，存在则**读旧结果**并继续推进。

---

## 4.12 预算器（Budgeter）与优先级（Priority）

* **维度**：按 `(provenance_code, endpoint_name, operation_code)` 维护预算（请求数、估计条数、时间片）。
* **Planner 阶段**：根据规模估算（count/抽样/历史）为每个 slice 绑定预算与 `priority`；库存过量时暂停下发。
* **Executor 阶段**：消耗预算；超限时自动降级/暂停；优先级顺序建议 `HARVEST > UPDATE > BACKFILL`（可配）。
* **反馈闭环**：Executor 将实际消耗与 429/失败统计回写 Planner，用于下次切片粒度调整。

---

## 4.13 错误分级（Error Taxonomy）与隔离

* **可重试**：网络/5xx/429 → 重试 + 限速。
* **不可重试（业务）**：4xx（400/403/404 等）→ 标记失败并记录 `error`；若可判定“坏请求参数”，回溯到表达式/映射修正。
* **数据污染**：`idPath` 为空、`updatedAt` 解析失败、必填字段缺失 → 入隔离库（设计外表或日志池），不阻塞其余条目。
* **致命**：鉴权失败、签名钩子异常、Schema 大变更 → 停止来源下发并告警。

---

## 4.14 UPDATE 专属策略位（候选集）

* **候选来源**（通过 CONTRACT 读侧）：

    * **Staleness**：按“上次刷新时间”分桶（例如每天 1/N 刷新）→ `cursor_type=TIME`；
    * **ExprChange**：当表达式/映射变更，筛选“由旧 `expr_hash` 产出的记录”重拉；
    * **Signals**：下游反馈（撤稿、合并、缺字段）触发按 ID 小批更新 → `cursor_type=ID`。
* **切片**：ID 段（哈希/范围）；两段式普遍开启。
* **游标命名空间**：不影响 HARVEST 前向水位；使用 `namespace=CUSTOM(plan_id|活动hash)`。

---

## 4.15 BACKFILL 专属策略位（方向与命名空间）

* **方向**：`direction=BACKFILL`（写 `ing_cursor_event`）；
* **命名空间**：`namespace=CUSTOM(plan_id|活动hash)` 或 `EXPR` 的独立 key；
* **禁止回退**：不可回退 HARVEST 的前向现值 `ing_cursor`；仅在回填命名空间推进。
* **并发/优先级**：预算器赋予低优先级，允许与 HARVEST 并行但限速更严。

---

## 4.16 估算与计划自适应

* **规模估算来源**：`totalPath`（可靠）> 供应商 meta 提示 > 抽样页回归 > 历史经验。
* **误差纠偏**：Executor 观测到“每页实际条数偏小/频繁 429/超大响应” → 回写 Planner 触发**在线再切片**（二分/合并）。
* **终止条件**：到达 `now-safetyLag`、耗尽预算、达到 `maxPage/maxWindow`、错误比率超阈值。

---

## 4.17 策略位一致性检查清单（编译期必须通过）

1. 鉴权类型与端点要求一致（不可匿名访问受限端点）。
2. 分页类型与所需路径一致（TOKEN 必有 `nextTokenPath`，URL 必有 `nextUrlPath`）。
3. 时间窗支持与排序一致（无 `supportsUpdatedSince` 时必须有稳定排序与客户端过滤能力）。
4. 两段式配置成对完整（SEARCH/DETAIL 端点与批处理阈值）。
5. 响应解析三件套：`itemsPath/idPath/updatedAtPath` 齐备且类型可转换。
6. 速率/重试与供应商约束一致（不可超过上限）。
7. 字典 code 存在且合法，枚举取值与表定义一致（`ing_operation`、`ing_cursor_type` 等）。
8. 命名空间与方向设置与操作类型一致（HARVEST→FORWARD；BACKFILL→BACKFILL；UPDATE→自定义/ID/TIME）。
9. 安全项：签名钩子非空、凭据引用有效、日志脱敏开启。

---

## 4.18 示例对照（两例摘要）

**PubMed（示意）**

* 分页：`OFFSET`（`retstart/retmax`），`pageSize=100`，`maxPage=1000`
* 时间窗：`supportsUpdatedSince=true`，`watermarkKey=updated_at`，`safetyLag=10m`
* 两段式：关（SEARCH 直接给 ID 即可作为主键）
* 限流：`qps=5`，`headerAdaptive=true`（尊重 NCBI 限制）
* 表达式：UTC，`itemsPath=$.esearchresult.idlist`，`idPath=$`，`updatedAtPath` 由二次查询/或另一路 meta 提供（若缺，降级为
  UPDATE 路线）

**Crossref（示意）**

* 分页：`TOKEN`（`next-cursor`），`pageSize=100`
* 时间窗：`supportsUpdatedSince=true`（`filter=from-update-date`）
* 两段式：可开（搜索→详情 DOI 批量）
* 限流：`qps=10`，429 自动降速
* 表达式：`itemsPath=$.message.items`，`idPath=$.DOI`，`updatedAtPath=$.deposited.date-time`

# 5. 双流水线拆分（两个小单元 + 两个触发器）

## 5.1 设计目标与原则

* **解耦**：将“计划制定（Planning）”与“任务执行（Execution）”分离，分别由 `PlannerTrigger` 与 `ExecutorTrigger` 驱动。
* **独立伸缩**：Planner 低频、CPU 轻；Executor 高频、可水平扩容。
* **只经数据库交接**：以 `ing_plan / ing_plan_slice / ing_task` 为边界对象；幂等键与状态机确保强一致交接。
* **确定性**：执行以 **Spec Snapshot** 为唯一依据；计划与执行之间不相互覆写快照。
* **温和对上游**：预算与限流前置在 Planner，运行时再由 Executor 动态降速与退避。

---

## 5.2 触发器定义与调度策略

### 5.2.1 PlannerTrigger

* **频率**：分钟/小时级（可按来源/操作自定义）；支持人工一次性触发。
* **输入**：`provenance_code`、`endpoint_name`、`operation_code(HARVEST/BACKFILL/UPDATE)`、`window_from/to`
  （或相对窗）、优先级、最大并发预算、可选参数。
* **并发**：单实例或小规模冗余实例（幂等派生保障无双发）。
* **背压**：当任务库存（`ing_task.status=QUEUED`）超过阈值或预算不足时，暂停新的切片与下发。

### 5.2.2 ExecutorTrigger

* **频率**：秒级轮询；多实例横向扩展。
* **任务选择**：按优先级/计划时间/来源分区的公平调度（见 5.6）。
* **限流联动**：共享 `provenance_code + endpoint_name` 的集中式令牌桶；遇 429/Retry-After 动态降速。
* **健康门控**：数据库压力/错误率超过阈值时自动降并发。

---

## 5.3 Planner 流水线（Plan & Slice & Task Producer）

### 5.3.1 输入与前置写入

1. **记录触发根**：写 `ing_schedule_instance`（固化触发参数、`provenance_code`、`endpoint_name`、`operation_code`、表达式原型/备注）。
2. **读取 Registry**：加载 `reg_*` 配置与系统字典。

### 5.3.2 快照编译（Spec Compiler）

* 参照第 3 章编译为 **Spec Snapshot**，得到 `expr_proto_snapshot / expr_proto_hash / spec_fingerprint`。
* **校验失败**：`ing_plan` 不创建；在 `ing_schedule_instance.record_remarks` 记录错误并告警。

### 5.3.3 计划创建（ing\_plan）

* 关键字段：

    * `provenance_code`、`endpoint_name`、`operation_code`；
    * `window_from/to`（UTC、半开区间）与 `plan_strategy`（时间/页/ID/预算）；
    * `expr_proto_hash / expr_proto_snapshot`；
    * `status_code=DRAFT → SLICING → READY/PARTIAL/FAILED`。
* **索引建议**：`idx_plan_src_op_time (provenance_code, operation_code, created_at)`。

### 5.3.4 自适应切片（ing\_plan\_slice）

* **切片策略**：

    * **时间窗**（HARVEST/BACKFILL 主流）：按 `maxWindow/minWindow` 先粗后细，超大/超深时二分；
    * **页区间**：源只支持 PAGE/OFFSET 且窗口弱时使用；
    * **ID 段**（UPDATE 主流）：哈希分桶或范围分段；
    * **预算绑定**：每 slice 绑定请求/预计记录数上限与 `priority`。
* **落库**：

    * `slice_spec`（JSON，含边界与预算）、`slice_signature_hash`（唯一）、`expr_snapshot / expr_hash`（局部化模板）。
    * `status_code=PENDING → READY/FAILED`。
* **唯一性**：`uk_slice_sig (plan_id, slice_signature_hash)` 防止重复切片。
* **在线再切片接口**：接受来自 Executor 的“重切请求”（窗口过大/429 频繁），在剩余区间上再生成新 slice。

### 5.3.5 派生任务（ing\_task）

* **一片一任务**：每个 slice 生成一个 `ing_task(status=QUEUED)`，携带：

    * `schedule_instance_id / plan_id / slice_id`；
    * `provenance_code / operation_code / credential_id`；
    * `params`（规范化执行参数：分页初始位、窗口边界等）；
    * `idempotent_key`（`slice_signature_hash + expr_hash + operation_code + trigger + normalized(params)`）。
* **幂等**：`uk_task_idem (idempotent_key)` 保证重复触发不重复建任务。
* **任务库存阈值**：当 `(provenance_code, endpoint_name, operation_code)` 下 `QUEUED` 数量超过阈值或预算不足，Planner
  进入“部分就绪（PARTIAL）”，停止新任务下发。

### 5.3.6 Planner 完结与度量

* `ing_plan.status`: DRAFT→SLICING→READY / PARTIAL / FAILED。
* 记录规模估算（`totalEstimated`）与切片统计（`sliceCount / avgWindow / budget`）到 `record_remarks` 或计划扩展字段。
* 对 UPDATE/BACKFILL 场景，固化**命名空间策略**（cursor namespace）说明。

---

## 5.4 Executor 流水线（Task Consumer & Runner）

### 5.4.1 取任务与租约

* **选择器**：按 `priority DESC, scheduled_at ASC, id ASC` 从 `ing_task` 取 `status=QUEUED`；可加来源分区权重（见 5.6）。
* **租约/CAS**：更新任务为 `status=DISPATCHED` 并写 `leased_until=now+T`（建议字段），失败则放弃本条；避免多实例重复消费。
* **重取策略**：`leased_until` 超时或 `DISPATCHED` 长时间无 `run` 关联，任务回滚为 `QUEUED`。

### 5.4.2 运行与批次账目

1. **创建 Run**：写 `ing_task_run(attempt_no++)`，`status=RUNNING`，冗余 `provenance_code / operation_code`、窗口快照。
2. **分页循环**（Batch Loop）：

    * **限流获取**：从集中令牌桶获取；
    * **渲染请求**：使用 `expr_snapshot` + 当前分页状态（offset/page/token/url/time）→ 实参；
    * **发送与解析**：写 `ing_task_run_batch`（`batch_no`，`before_token`，请求摘要）；解析响应：`itemsPath` / `next*` /
      `hasMore` / `total`；
    * **统计与入库/事件**：对每条记录进行唯一键去重与 `updatedAt` 覆盖判定；对外事件使用 Outbox 幂等；
    * **推进分页**：计算 `after_token/page_no/next_url`；写回 `ing_task_run_batch.after_*` 与 `stats`；
    * **检查终止条件**：无下一页 / 达到 `maxPage` / 预算耗尽 / 错误率超阈值。
3. **Run 收口**：`status=SUCCEEDED/FAILED/PARTIAL`；将关键统计写入 `stats`，错误写入 `error`。

### 5.4.3 游标推进（Cursor Manager）

* **顺序**：**先事件、后现值**。

    * 写 `ing_cursor_event`：
      `provenance_code / operation_code / cursor_key / namespace_* / prev/new / observed_max / direction(FORWARD|BACKFILL)`
      ，并记录 lineage（schedule/plan/slice/task/run/batch）。
    * 乐观锁更新 `ing_cursor`：仅允许“前进”；BACKFILL/UPDATE 使用独立命名空间，不影响 HARVEST 前向水位。
* **水位键**：HARVEST 常用 `updated_at`；UPDATE 可能使用 `refresh_checked_at`（TIME）或 `item_id`（ID）。
* **幂等**：`ing_cursor_event.idempotent_key` 保证重复写事件不重复；`ing_cursor` 以 `(source,op,key,ns)` 唯一并“仅前进”写入。

### 5.4.4 重试与降速

* **可重试错误**：网络/5xx/429 → 退避（`exponential_jitter`） + 重试；
* **429/Retry-After**：动态降低 `qps`，必要时将**剩余窗口**上报 Planner 触发在线再切片。
* **不可重试**：4xx-业务 → 当前 batch `FAILED`，run 视阈值转 `PARTIAL/FAILED`。

### 5.4.5 停止条件与回放

* **停止条件**：无 `hasNext`、预算耗尽、错误率超阈值、显式取消。
* **回放**：凭 `run_id/batch_no` 与 `expr_hash/slice_signature_hash` 精确重演；若幂等键命中，批次会被判定为已执行并跳过。

---

## 5.5 Planner ↔ Executor 协作协议

* **库存门限**：Planner 仅在库存 < 阈值时继续下发；阈值按 `(source, endpoint, op)` 配置。
* **反馈与重切**：Executor 将“页深/429/大响应/失败比”作为建议写入计划备注或重切队列表；Planner 在剩余区间上二分重切。
* **安全延迟（safetyLag）屏障**：Planner 固化 `now-safetyLag` 为上界；Executor 不得逾越。
* **就绪语义**：`ing_plan.status=READY/PARTIAL` 即可被 Executor 消费；`FAILED` 不消费。

---

## 5.6 并发模型与隔离

* **分区键**：`(provenance_code, endpoint_name, operation_code)`；Executor 以分区为单位做**公平轮询**。
* **租约隔离**：同一分区的租约时间统一（例如 60s），防止“短租约抖动”。
* **并发上限**：每分区最大并发 `C_max`；全局并发 `C_total`；避免某来源“饿死”或“打爆”。
* **索引**：`idx_task_pick (status_code, priority, scheduled_at, provenance_code, operation_code)` 加速取任务。

---

## 5.7 背压与自适应

* **背压信号**：数据库压力、429 比例、重试次数、平均批次时延、失败比。
* **自适应动作**：

    * 降低 `qps` 与 `C_max`；
    * 增大安全延迟 `safetyLag`；
    * 触发在线再切片（更细窗口/更小页）；
    * 暂停 Planner 的新任务下发，直至库存恢复。
* **阈值建议**（可配）：`429_rate > 5%`、`batch_avg_latency > 3s`、`error_rate > 2%`、`db_queue_time > 200ms`。

---

## 5.8 故障恢复与一致性

* **进程崩溃**：租约超时任务回滚为 `QUEUED`；未提交的 batch 因幂等键不会重复生效。
* **数据库故障**：使用短事务；批次级写入失败允许重试；游标“先事件后现值”的顺序避免丢进度。
* **跨版本升级**：Planner 与 Executor 可独立升级；快照不变保证执行面的确定性。

---

## 5.9 运维操作（最小手册）

* **暂停/恢复某来源/端点**：Planner 侧停止下发；Executor 侧过滤该分区任务。
* **取消计划/切片/任务**：将对应对象 `status=CANCELLED`；Executor 读取到即跳过。
* **重放**：按 `run_id/batch_no` 或 `task_id` 维度触发；使用快照哈希确保与原执行等价。
* **人工回填/刷新**：创建 BACKFILL/UPDATE 计划，命名空间独立，安全不干扰前向水位。
* **凭证更新**：仅在 Registry 修改；Planner 新计划生效；老计划用旧快照跑完。

---

## 5.10 验收标准（Definition of Done）

1. Planner 能在库存与预算约束下生成 `ing_task(status=QUEUED)`，并保证 `uk_task_idem` 命中时零重复。
2. Executor 能稳定消费任务，保证批次幂等（`uk_run_batch_no / uk_run_before_tok / uk_batch_idem`），在 429/网络波动下自动降速。
3. HARVEST/BACKFILL/UPDATE 在各自命名空间/方向推进游标，互不干扰；水位推进严格遵循“先事件后现值”。
4. 任意失败场景均可通过黑匣子（`ing_task_run[_batch]` + 快照哈希）**精确重放**到批次级。
5. Planner 与 Executor 可独立扩缩容与灰度发布，不造成执行期非确定性。

# 6. 三类操作的差异化设计（HARVEST / BACKFILL / UPDATE）

> 三条路径共用同一抽象机器（策略位矩阵 + Planner/Executor 双流水线）。差异只体现在：**候选枚举方式、切片策略、游标/命名空间、优先级与停止条件
**。

## 6.1 HARVEST（增量采集）

### 6.1.1 目标与适用

* **目标**：将“前次水位”推进至“now - safetyLag”，捕获所有新增/更新记录。
* **适用**：来源支持 `updatedSince` 或具备稳定时间排序（可 TOKEN/URL 分页但能解析更新时间）。

### 6.1.2 关键策略

* **枚举器**：`SearchByDateRange`（优先）或 `ScanByCursor`（若仅 TOKEN/URL）；
* **切片**：时间窗优先；按 `maxWindow/minWindow` 粗细化，页深/429 触发**在线再切片**；
* **预算与限流**：为该来源/端点/操作分配较高优先级与 QPS；
* **游标**：`cursor_type=TIME`，`cursor_key=watermarkKey(如 updated_at)`；`namespace_scope=EXPR`（或 GLOBAL，取决于端点复用策略）；
  `direction=FORWARD`；
* **边界**：统一半开 `[from, to)`；`to = now - safetyLag`；等于 `to` 的记录归下一窗；
* **幂等**：唯一键（来源+端点+provider\_id）+ `updatedAt` 覆盖；Outbox 幂等键对外一致。

### 6.1.3 停止与收口

* 到达 `to`、无 `hasNext`、预算耗尽、错误率超阈值；
* 推进 `ing_cursor_event(direction=FORWARD)` → 乐观锁更新 `ing_cursor`（仅前进）。

---

## 6.2 BACKFILL（历史回填）

### 6.2.1 目标与适用

* **目标**：补齐历史空洞（通常早于当前前向水位），不扰动增量链路。
* **适用**：来源可时间过滤或可在客户端按时间排序过滤；即便无 `updatedSince` 也可用“固定窗 + 客户端过滤”。

### 6.2.2 关键策略

* **枚举器**：`SearchByDateRange`（向过去扩展）；无搜索则`TIME`窗 + 普通扫描；
* **切片**：**反向**时间窗推进；大窗二分；低优先级预算；
* **游标**：使用**独立命名空间**，常见两种：

    * `namespace_scope=CUSTOM`，`namespace_key=plan_id|活动hash`；
    * 或 `namespace_scope=EXPR` 但 key 与 HARVEST 区分；
      `direction=BACKFILL`；
* **幂等**：与 HARVEST 相同的唯一键与覆盖规则；
* **互不干扰**：**严禁**回退 HARVEST 的现值 `ing_cursor`；仅在回填命名空间推进。

### 6.2.3 停止与收口

* 到达最早目标 `earliestNeeded` 或预算用尽；
* 事件只写回填命名空间；计划可多次断点续跑。

---

## 6.3 UPDATE（刷新/核对）

### 6.3.1 目标与适用

* **目标**：对**已入库**记录做“再抓取/校正/补齐”（撤稿、合并、字段修复、表达式变更重算）。
* **适用**：下游反馈“脏数据信号”、表达式/映射变更、或“陈旧性巡检”。

### 6.3.2 候选集来源（CONTRACT 读侧）

* **Staleness**：按“上次刷新时间”分桶（例如 N 分桶，日巡）；
* **ExprChange**：筛选由旧 `expr_hash` 产出的记录；
* **Signals**：按 ID 列表（撤稿/更正/缺字段）触发。

### 6.3.3 关键策略

* **切片**：**ID 段**优先（哈希分桶/范围）；
* **两段式**：常开启“ID→详情”；ID 批量阈值由 `reg_prov_batching_cfg` 提供；
* **游标**：

    * `cursor_type=ID` 且命名空间自定义；或
    * `cursor_type=TIME` 且 `cursor_key=refresh_checked_at`（巡检方案）；
* **优先级**：通常次于 HARVEST，高于 BACKFILL（可配）；
* **幂等**：唯一键与 `updatedAt` 覆盖；不影响 HARVEST 前向水位。

### 6.3.4 停止与收口

* 候选集耗尽、预算用完、错误阈值超限；
* 仅推进“刷新进度”型游标（不触碰 HARVEST 水位）。

---

## 6.4 三类操作对比摘要（执行要点）

| 维度  | HARVEST                              | BACKFILL                        | UPDATE                                    |
|-----|--------------------------------------|---------------------------------|-------------------------------------------|
| 枚举器 | SearchByDateRange / ScanByCursor     | SearchByDateRange（向过去）/ TIME 扫描 | ExistingSet（Staleness/ExprChange/Signals） |
| 切片  | 时间窗（二分自适应）                           | 反向时间窗（二分）                       | ID 段（哈希/范围）                               |
| 两段式 | 视来源                                  | 视来源                             | 常见                                        |
| 游标  | TIME，FORWARD，EXPR/GLOBAL             | TIME，BACKFILL，CUSTOM/EXPR       | ID 或 TIME（刷新），CUSTOM                      |
| 优先级 | 高                                    | 低                               | 中                                         |
| 停止  | 到达 now-safetyLag / 无 hasNext / 预算/阈值 | 到达 earliest / 预算/阈值             | 候选耗尽 / 预算/阈值                              |

---

# 7. 数据模型与表映射（与 Ingest SQL 对齐）

> 本节将**流程对象**与现有 `ing_*` 表逐字段落点对齐，并给出状态机、唯一性与索引策略。**不新增表列**，通过规约实现语义。

## 7.1 触发根：`ing_schedule_instance`

* **作用**：一次外部触发的“上下文根”。固化触发参数、来源配置原型摘要。
* **关键字段（示意）**：`scheduler_code`、`scheduler_job_id/log_id`、`provenance_code`、`trigger_type_code`、`expr_proto_*`、
  `record_remarks`。
* **读取者**：Planner。Executor 不直接依赖。
* **索引**：`idx_sched_src (scheduler_code, job_id, log_id)`。

## 7.2 计划蓝图：`ing_plan`

* **语义**：本次采集的**总窗口/策略**与**表达式原型**；**不执行**。
* **关键字段**：

    * 识别：`schedule_instance_id`、`provenance_code`、`endpoint_name`、`operation_code`；
    * 窗口：`window_from/to`（UTC，半开）；
    * 表达式原型：`expr_proto_hash / expr_proto_snapshot`；
    * 状态：`status_code=DRAFT|SLICING|READY|PARTIAL|FAILED`；
    * 备注：规模估算、预算摘要写入 `record_remarks`。
* **索引**：`idx_plan_src_op_time (provenance_code, operation_code, created_at)`。
* **状态机**：DRAFT → SLICING → READY / PARTIAL / FAILED。
* **幂等**：由下游 slice 与 task 的唯一键承担；plan 本身不暴露幂等键。

## 7.3 计划切片：`ing_plan_slice`

* **语义**：并行与幂等的最小单元；承载**局部化表达式快照**。
* **关键字段**：

    * 关联：`plan_id`、`provenance_code`；
    * 边界/预算：`slice_spec`（时间/页/ID + 预算）、`slice_signature_hash`（唯一签名）；
    * 表达式：`expr_hash / expr_snapshot`（局部化，含窗口/分页初值）；
    * 状态：`status_code=PENDING|READY|FAILED`。
* **唯一性**：`uk_slice_sig(plan_id, slice_signature_hash)`；同一计划下同签名不重复。
* **索引**：`idx_slice_status (plan_id, status_code)`。
* **在线再切片**：新增子切片时，父 slice 可标记备注，不强制层级关系（保持简单）。

## 7.4 任务队列：`ing_task`

* **语义**：待执行的**任务实体**，由 slice 派生，一片一任务。
* **关键字段**：

    * 关联：`schedule_instance_id / plan_id / slice_id`；
    * 识别：`provenance_code / operation_code / credential_id`；
    * 参数：`params`（规范化执行参数：窗口/分页起点/ID 范围…）、`expr_hash`；
    * 幂等：`idempotent_key`（强唯一，见下）；
    * 调度：`status_code=QUEUED|DISPATCHED|EXECUTING|SUCCEEDED|FAILED|PARTIAL|CANCELLED`、`scheduled_at`、`priority`（建议）。
* **唯一性**：`uk_task_idem (idempotent_key)`；构成建议：
  `slice_signature_hash + expr_hash + operation_code + trigger + normalized(params)`。
* **索引**：

    * 取任务：`idx_task_pick (status_code, priority, scheduled_at, provenance_code, operation_code)`；
    * 溯源：`idx_task_slice (slice_id)`、`idx_task_src_op (provenance_code, operation_code)`、
      `idx_task_cred (credential_id)`。

## 7.5 运行尝试：`ing_task_run`

* **语义**：一次具体执行尝试（首次/重试/回放），不覆盖 task 状态。
* **关键字段**：

    * 关联：`task_id`；
    * 冗余：`provenance_code / operation_code`；
    * 运行：`attempt_no`、`status_code=PLANNED|RUNNING|SUCCEEDED|FAILED|CANCELLED`；
    * 检查点与统计：`checkpoint`（如 `nextHint/resumeToken`）、`stats`；
    * 窗口冗余：`window_from/to`（便于回放/审计）；
    * 错误：`error`（文本）。
* **唯一性**：`uk_run_attempt (task_id, attempt_no)`。
* **索引**：`idx_run_task_status (task_id, status_code)`。

## 7.6 运行批次：`ing_task_run_batch`

* **语义**：分页/令牌步进的最小账目，是**断点续跑**与**幂等去重**的关键。
* **关键字段**：

    * 关联：`run_id`（冗余 `task_id/slice_id/plan_id/expr_hash`）；
    * 序号：`batch_no`（1 起，连续）；
    * 分页：`page_no/page_size` 或 `before_token/after_token/next_url`；
    * 请求上下文：展开后的 URL/Query/Header/Body 摘要；
    * 统计：本批拉取/入库/失败数、hasNext、耗时、429 次数、重试次数（存入 `stats`）；
    * 幂等：`idempotent_key`（如 `run_id + batch_no` 或 `run_id + before_token` 的签名）。
* **唯一性**：

    * `uk_run_batch_no (run_id, batch_no)`；
    * `uk_run_before_tok (run_id, before_token)`（TOKEN/URL）；
    * `uk_batch_idem (idempotent_key)`。
* **索引**：按时间与状态常用查询建立（运行时观测与排障）。

## 7.7 游标现值：`ing_cursor`

* **语义**：在某 `(provenance_code, operation_code, cursor_key, namespace_scope, namespace_key)` 上**当前推进到的值**。
* **关键字段**：

    * 标识：`provenance_code / operation_code / cursor_key`；
    * 命名空间：`namespace_scope_code=GLOBAL|EXPR|CUSTOM`、`namespace_key`；
    * 类型与值：`cursor_type_code=TIME|ID|TOKEN`、`cursor_value`；
    * 归一：`normalized_instant/numeric`（便于排序/范围查询）；
    * 观测：`observed_max_value`（可选）；
    * 溯源：最近一次推进的 `schedule/plan/slice/task/run/batch/expr_hash`。
* **唯一性**：`uk_cursor_ns (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)`。
* **更新规则**：**仅前进**；HARVEST 的现值不受 BACKFILL/UPDATE 影响（命名空间隔离）。

## 7.8 游标事件：`ing_cursor_event`

* **语义**：**append-only** 审计事件，记录每次成功推进；用于回放/审计与跨表回溯。
* **关键字段**：

    * 标识与命名空间同上；
    * 方向：`direction_code=FORWARD|BACKFILL`；
    * 前后值：`prev_value/new_value`、`prev_instant/new_instant`、`observed_max_value`；
    * 幂等：`idempotent_key`（防重复写）；
    * 溯源：`schedule/plan/slice/task/run/batch/expr_hash`；
    * 时间线：`event_time`（可由 `created_at` 代替）。
* **唯一性**：`uk_cur_evt_idem (idempotent_key)`。
* **索引**：`idx_cur_evt_timeline`、`idx_cur_evt_instant/numeric`、`idx_cur_evt_lineage`。

## 7.9 状态机与转换规则（执行面）

### 7.9.1 Planner 侧

* `ing_plan`: DRAFT → SLICING → READY / PARTIAL / FAILED
* `ing_plan_slice`: PENDING → READY / FAILED
* `ing_task`: **创建为** QUEUED；Planner 不做执行状态迁移

### 7.9.2 Executor 侧

* `ing_task`: QUEUED → DISPATCHED（租约） → EXECUTING → SUCCEEDED / FAILED / PARTIAL / CANCELLED
* `ing_task_run`: PLANNED → RUNNING → SUCCEEDED / FAILED / CANCELLED
* `ing_task_run_batch`: PLANNED → RUNNING → SUCCEEDED / SKIPPED / FAILED
* **推进游标**：每个 Run 成功推进一次或多次事件；更新现值严格“先事件后现值”，且仅前进。

## 7.10 幂等与一致性（黑匣子）

* **任务幂等**：`uk_task_idem` 杜绝重复派生；
* **批次幂等**：三重唯一约束 + `idempotent_key`；
* **事件幂等**：`ing_cursor_event.idempotent_key`；
* **重放**：以 `run_id/batch_no` + `expr_hash` + `slice_signature_hash` 复位参数并重跑；若命中幂等键则跳过写入直接对齐状态。
* **覆盖规则**：唯一键 + `updatedAt` 决定覆盖；等值不覆盖（默认）。

## 7.11 索引与查询（读侧最小集合）

* **取任务**：`idx_task_pick`；
* **回溯链**：按 `schedule_id/plan_id/slice_id/task_id/run_id/batch_id` 可快速追溯（各表已有冗余可建组合索引）；
* **游标观测**：`idx_cursor_sort_time / idx_cursor_sort_id`；
* **看板聚合**：按 `(provenance_code, operation_code, status_code, 时间)` 维度建覆盖索引。

## 7.12 规划与执行的协定字段

* Planner 必填：`provenance_code / endpoint_name / operation_code / window_from/to / expr_proto_hash`；
* Slice 必填：`slice_spec / slice_signature_hash / expr_hash / expr_snapshot`；
* Task 必填：`idempotent_key / params / scheduled_at / priority`（建议）；
* Executor 必填：Run/Batch 的 `stats` 与请求上下文摘要；游标事件的 `direction/namespace/*` 全量。

# 8. 计划与切片（Plan/Slice）设计

> 目标：把一次触发的“总意图”编译成**可并行、可幂等、可重放**的切片集合。Planner 只负责**确定性产出**与**库存控制**，不做网络
> I/O。

## 8.1 Plan 的生成与约束（`ing_plan`）

* **生成时机**：每次 `PlannerTrigger` 触发后，完成 Registry 读取与 Spec Snapshot 编译（见第 3 章），随即创建 `ing_plan`。
* **核心字段约束**

    * `provenance_code / endpoint_name / operation_code`：来自触发参数与 Registry 校验。
    * `window_from/to`：UTC、**半开区间** `[from, to)`；`to = now - safetyLag`（HARVEST）或“目标上界/最小值”（BACKFILL/UPDATE）。
    * `expr_proto_hash / expr_proto_snapshot`：表达式原型，**执行期不变**。
    * `status_code`：`DRAFT → SLICING → READY / PARTIAL / FAILED`。
    * **备注**：规模估算、预算摘要、降级/警告写入 `record_remarks`。
* **库存与预算**

    * 生成 Plan 前读取当前 `(source, endpoint, op)` 的**库存**（`ing_task.status=QUEUED` 数）；超过阈值则**不切片**，Plan 直接
      `PARTIAL`，等待库存回落。
    * 预算（请求数/时间片）从 `reg_prov_rate_limit_cfg`、`reg_prov_batching_cfg` 与触发参数综合得出，作为切片时的强约束。

## 8.2 切片策略（`ing_plan_slice`）

> 切片是**并行与幂等的最小单元**，承载局部化表达式快照。

### 8.2.1 切片维度与选择

* **时间窗切片**（默认，HARVEST/BACKFILL 主流）：

    * 以 `maxWindow` 为初始步长；若估算页深 > 阈值或配额不足，**二分**为更细粒度；
    * 支持 `align=hour/day` 对齐（UTC），避免跨窗重叠。
* **页区间切片**（仅当源只支持 PAGE/OFFSET 且无法稳定按时间过滤）：

    * 根据 `maxPage`、`pageSize` 与估算结果划段（例如每 200 页一片）。
* **ID 段切片**（UPDATE 主流）：

    * 对候选 ID 做**哈希分桶**（如 128 桶）或**范围分段**；每片包含一段 ID 集合；
    * 两段式源在 Phase 2（详情）按 `idBatchSize` 再做批内分批。

### 8.2.2 `slice_spec` 统一结构（JSON 草图）

> 提供规范字段名以便 Planner/Executor/观测统一识别。

```json
{
  "dimension": "TIME|PAGE|ID",
  "bounds": {
    "time": {
      "from": "2025-09-20T00:00:00Z",
      "to": "2025-09-21T00:00:00Z"
    },
    "page": {
      "fromPage": 1,
      "toPage": 200,
      "pageSize": 100,
      "base1": true
    },
    "id": {
      "from": "A0000000",
      "to": "A000FFFF",
      "hashBucket": 37
    }
  },
  "phase": {
    "twoPhase": false,
    "idBatchSize": 200
  },
  "pagination": {
    "type": "OFFSET|PAGE|TOKEN|URL|TIME",
    "initial": {
      "offset": 0,
      "page": 1,
      "token": null,
      "url": null
    }
  },
  "window": {
    "safetyLag": "PT10M",
    "watermarkKey": "updated_at"
  },
  "budget": {
    "maxRequests": 800,
    "maxDurationSec": 900,
    "priority": 100
  },
  "telemetry": {
    "totalEstimated": 42000,
    "comment": "auto-bisect from estimate"
  }
}
```

* **强制字段**：`dimension`、`bounds.*`（与维度对应）、`pagination.type`、`budget.*`。
* **HARVEST**：必须包含 `window.safetyLag` 与 `window.watermarkKey`。
* **BACKFILL**：`dimension=TIME`，时间向过去推进；命名空间策略在 Plan 备注中说明。
* **UPDATE**：`dimension=ID`；若两段式，`phase.twoPhase=true` 与 `phase.idBatchSize` 必填。

### 8.2.3 `slice_signature_hash` 规范

* 计算对象：`(provenance_code, endpoint_name, operation_code, expr_hash, slice_spec.normalized)`；
* 规范化：移除无关键、排序属性、时间统一到毫秒或纳秒精度（与系统统一）；
* 语义：**同签名即同片**，保证幂等派生任务时只建一条 `ing_task`。

### 8.2.4 局部化表达式（`expr_snapshot / expr_hash`）

* 局部化内容：将 `expr_proto_snapshot` 中涉及窗口边界、分页初值、ID 范围等**具体化**；
* 变更边界：任何会影响请求渲染或响应解析的上下文都要进入 `expr_snapshot`，以保障 Executor 重放时完全等价。

## 8.3 切片产能与并发规划

* **片大小控制**：以“页深/429 比例/预测请求数”作为反馈信号；超过阈值的片自动**二分**。
* **并发上限**：每 `(source, endpoint, op)` 的并发 `C_max`；每个 slice 允许至多 1 个活跃任务（保持简单）。
* **优先级**：`budget.priority` 与全局策略（HARVEST > UPDATE > BACKFILL）一致；Planner 在派生任务时写入。

## 8.4 在线再切片（自愈）

* **触发条件**（由 Executor 回报或自动检测）：

    * 单批响应体超阈（例如 > 5MB 或 > pageSize\*2）；
    * 429 比例 > 5%；
    * 单片耗时 > 目标上限/配额不足。
* **处理**：对**剩余窗口**按二分再切，生成新 slice，并停止当前 slice 的后续任务派生（或标记 `PARTIAL`）。
* **幂等**：新 slice 拥有新的 `slice_signature_hash`；原 slice 仍保留历史记录用于审计。

## 8.5 任务派生与库存控制（桥接到第 9 章）

* 每个 slice **派生 1 条** `ing_task(status=QUEUED)`；幂等键 `uk_task_idem` 避免重复。
* **库存阈值**：Planner 若发现库存 > 阈值，Plan 标记 `PARTIAL` 停止派生（见 5.5）。
* **可见性延迟**：支持 `scheduled_at` 未来时间，以平滑执行侧负载。

---

# 9. 任务与运行（Task/Run/Batch）语义

> 目标：Executor 在**幂等与可重放**保障下，稳定推进分页/令牌，产出事件/入库，并按操作类型推进游标/水位。

## 9.1 任务（`ing_task`）

* **来源**：由 `ing_plan_slice` 一对一派生。
* **关键责任**：封装“本片需要执行的上下文与参数”，成为 Executor 的“领取单位”。
* **必要字段约束**

    * 关联：`schedule_instance_id / plan_id / slice_id`；
    * 标识：`provenance_code / operation_code / credential_id`；
    * 表达式：`expr_hash`（与 slice 冗余一致）；
    * 参数：`params`（规范化执行参数——窗口边界、分页初值、ID 范围、twoPhase 标志等）；
    * 幂等：`idempotent_key`（由 `slice_signature_hash + expr_hash + operation + trigger + normalized(params)` 生成）；
    * 调度：`status_code` 初始 `QUEUED`，`priority` 与 `scheduled_at` 可选。
* **租约**：Executor 领取时原子更新为 `DISPATCHED` + `leased_until`；租约到期未开跑回滚为 `QUEUED`。
* **取消/暂停**：设置 `status=CANCELLED` 或 `scheduled_at` 推后，Executor 侧读取即跳过或延时。

## 9.2 运行尝试（`ing_task_run`）

* **生命周期**：每领取一次或重试/回放，创建一条 `run`（`attempt_no++`），不覆盖 `task`。
* **初始写入**：`status=RUNNING`，冗余窗口 `window_from/to`、`provenance_code / operation_code`、`checkpoint`（可空）。
* **Checkpoint 语义**：

    * 用于**非幂等**或**需要 resumeHint** 的场景（例如部分两段式异常中断）；
    * 与 `ing_task_run_batch` 的 `before/after_token/next_url` 协同，做到“最近一次有效推进点”可恢复。
* **统计（`stats`）建议字段**：

    * `reqCount/retCount/inserted/updated/skipped/failed`；
    * `retryCount/429Count/avgLatencyMs/maxLatencyMs`；
    * `pagesVisited/hasNextLast`；
    * `errors[]`（摘要、首尾堆栈位置）。
* **收口**：

    * 全量成功 → `SUCCEEDED`；
    * 部分成功或预算耗尽 → `PARTIAL`；
    * 致命失败 → `FAILED`。
    * 对应地更新 `ing_task.status`（若还有“剩余片段”需 Planner 再切，task 可 `PARTIAL` 结束）。

## 9.3 运行批次（`ing_task_run_batch`）

* **作用**：分页或令牌步进的**最小账目单位**，确保**幂等与重放**。
* **字段语义**

    * 关联：`run_id`（冗余 `task_id/slice_id/plan_id/expr_hash` 便于观测）。
    * 序号：`batch_no`（从 1 递增，连续）；
    * 分页/令牌：

        * PAGE/OFFSET：`page_no/page_size`；
        * TOKEN：`before_token/after_token`；
        * URL：`before_url/next_url`；
        * TIME：`window_from/to` 的**子窗口**或“水位推进点”（可选）。
    * 请求摘要：展开后的 URL/Query/Header/Body **摘要**（脱敏），便于排障与重放对齐；
    * 响应摘要：`items_count/hasNext/next_token_hint/next_url_hint/total_hint`；
    * 统计：`inserted/updated/skipped/failed/retryCount/429Count/latencyMs`；
    * 幂等：`idempotent_key`（建议 `hash(run_id, batch_no, before_token|page_no|before_url)`）。
* **唯一性约束**

    * `uk_run_batch_no (run_id, batch_no)`；
    * `uk_run_before_tok (run_id, before_token)`（TOKEN/URL）；
    * `uk_batch_idem (idempotent_key)`；
    * **语义**：三重保证“重复写入变成读旧结果”，使批次级重试/重放**无副作用**。
* **批次推进规则**

    1. 取限流令牌；
    2. 渲染请求（`expr_snapshot + 当前分页/窗口 context`）；
    3. 执行请求，解析 `itemsPath / idPath / updatedAtPath / next* / hasMore`；
    4. 对每条记录做**唯一键去重**与 `updatedAt` 覆盖判定，产出入库或事件（Outbox 幂等）；
    5. 计算下一步 `after_token|page_no+1|next_url`；
    6. 写本批 `stats`，并将“下一步 hint”存入 `after_*` 字段；
    7. 终止条件命中则退出循环。

## 9.4 幂等与一致性（执行面）

* **入库幂等**：唯一键 `(provenance_code, endpoint_name, provider_id)` + `updatedAt` 覆盖；等时不覆盖（默认）。
* **事件幂等**：对外发布使用同一幂等键；下游消费者“至多一次可见，多次幂等”。
* **批次幂等**：命中 `uk_*` 时，Executor 将读取既有批次结果并继续推进（**读旧不重写**）。
* **游标推进顺序**：**先 `ing_cursor_event` 后 `ing_cursor`**；现值仅允许**前进**。
* **跨失败恢复**：

    * 进程退出/崩溃：租约到期任务回滚为 `QUEUED`；下次领取时从最近一次成功批次的 `after_*` 处继续；
    * 网络/5xx/429：重试 + 降速；保持批次幂等；
    * 数据污染：坏记录入隔离，不阻塞其余处理。

## 9.5 错误分级与处置（批次级）

* **可重试**：网络错误、5xx、429 → 当前批重试；超过 `maxAttempts` 标记本批 `FAILED`，Run 视比例转 `PARTIAL/FAILED`。
* **不可重试**：4xx-业务 → 本批 `FAILED`；若可定位为参数/表达式问题，Run `FAILED` 并提示 Planner 暂停该来源/端点班次。
* **致命**：鉴权失败/签名逻辑异常 → 立即终止 Run，Task `FAILED`，并触发全局告警；Planner 停止下发该来源。
* **异常速率高**：回写 Planner 触发**在线再切片**或调小 `pageSize/qps`。

## 9.6 游标/水位推进（FORWARD 与 BACKFILL 命名空间）

* **HARVEST**：`direction=FORWARD`，命名空间 `EXPR`（或 GLOBAL）；推进 `watermarkKey` 的最大已确认值。
* **BACKFILL**：`direction=BACKFILL`，命名空间 `CUSTOM(plan|活动hash)`；**不影响**前向现值。
* **UPDATE**：推进“刷新进度”指针（ID 或 TIME），命名空间 `CUSTOM`；**不影响** HARVEST 现值。

## 9.7 调度与公平性（取任务策略）

* **优先队列**：按 `(priority DESC, scheduled_at ASC, id ASC)`；同优先级下按
  `(provenance_code, endpoint_name, operation_code)` **分区轮询**，保证公平。
* **并发上限**：分区并发 `C_max` + 全局并发 `C_total`；避免某来源“饿死/打爆”。
* **健康门控**：错误率/429 比例超阈时，动态降低该分区并发与 qps。

## 9.8 观测与黑匣子（回放能力）

* **批次级**记录（必须）：请求摘要、响应计数、下一页 token/url、窗口边界、耗时、重试/429 次数；
* **Run 级**统计：`stats` 聚合、错误摘要、窗口冗余；
* **回放入口**：以 `run_id/batch_no` + `expr_hash` + `slice_signature_hash` 定位；命中幂等键则“读旧跳写”；
* **可视化最小集**（对接读侧 CONTRACT）：

    * 任务队列看板（按来源/操作/状态）；
    * Run/Batch 时间线（失败点突出）；
    * 游标/事件时间线（命名空间并列展示）。

## 9.9 验收与 SLO 建议

* **准确性**：相邻时间窗不重不漏；等边界归属遵循 `[from,to)`。
* **稳定性**：429/5xx 场景下自动降速，重试成功率 ≥ 95%。
* **可恢复性**：崩溃重启后可在 **1 批** 内恢复到上次推进点。
* **吞吐**：在 `qps` 与配额约束下，Executor 实例线性扩展（分区并发 × 实例数）。
* **可回放**：给定任意 `run_id/batch_no`，可**无副作用**复现请求参数与推进过程。

# 10. 游标与水位（Cursor / Watermark）

> 目标：在不同操作（HARVEST / BACKFILL / UPDATE）与不同来源分页形态（TIME / ID / TOKEN）下，提供**确定性、可审计、可回放**
> 的推进机制。核心规则：**事件先行，现值后置；仅前进；命名空间隔离**。

## 10.1 结构落点与命名约定

* **现值表**：`ing_cursor`

    * 唯一键：`uk_cursor_ns (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)`
    * 关键列：

        * `cursor_type_code=TIME|ID|TOKEN`（与 Spec 对齐）
        * `cursor_value`（原值，字符串或时间文本）
        * `normalized_instant`（TIME 时的 UTC 时间戳；可空） / `normalized_numeric`（ID/hash 映射数值；可空）
        * `observed_max_value`（可选，用于“最大已见值”观测）
        * 溯源冗余：最近一次推进的 `schedule/plan/slice/task/run/batch/expr_hash`
* **事件表**：`ing_cursor_event`（append-only）

    * 唯一键：`uk_cur_evt_idem (idempotent_key)`
    * 关键列：

        * 与 `ing_cursor` 相同的标识与命名空间列
        * `direction_code=FORWARD|BACKFILL`
        * `prev_value/new_value`、`prev_instant/new_instant`、`observed_max_value`
        * 溯源链路：`schedule/plan/slice/task/run/batch/expr_hash`

> **命名空间**：`namespace_scope_code=GLOBAL|EXPR|CUSTOM`
>
> * HARVEST 常用 `EXPR`（表达式/端点语义下的共享水位），也可选 `GLOBAL`。
> * BACKFILL/UPDATE 使用 `CUSTOM`（如 `plan_id` 或“活动哈希”），**不得**影响 HARVEST 的前向现值。

## 10.2 游标类型与推进语义

* **TIME 型**（主用于 HARVEST/BACKFILL；UPDATE 的巡检也可用）

    * 主键：`cursor_key=watermarkKey`（如 `updated_at`）
    * 推进规则：以**本批已成功确认**的记录中 `updatedAt` 的**最小保障上界**推进；遇乱序/重复数据，仍保证**不回退**
    * 边界：半开区间 `[from, to)`，等于 `to` 归下一窗
* **ID 型**（主用于 UPDATE 的 ID 列表巡检）

    * 主键：`cursor_key=item_id`（或逻辑名，如 `provider_id`）
    * 推进规则：按**候选集合顺序**推进“已检查到的最大/最后 ID”；不决定业务“新旧”覆盖，只表明“刷新进度”
* **TOKEN/URL 型**（少见作为长期水位，更多用于批次级分页）

    * 主键：`cursor_key=page_token|next_url`
    * 推进规则：记录最近一次确认的 `token/url`；一般仅在“长游标扫描型”来源使用
    * 建议：TOKEN/URL 作为**批次级**推进字段，`ing_cursor` 长期仍以 TIME/ID 为主

## 10.3 推进算法（执行侧：顺序与仅前进）

**顺序**：`写事件（event） → 乐观锁更新现值（cursor）`

1. **计算 new\_value/new\_instant**

    * TIME：取“本批所有成功处理记录的 `updatedAt` 最大值”（或“下一窗口 `from`”）
    * ID：取“候选集中已完成批的末尾 ID”
    * TOKEN：取 `after_token` 或 `next_url`

2. **生成事件**

    * 组装 `prev_*`（从 `ing_cursor` 读）与 `new_*`，写 `ing_cursor_event(direction, namespace, lineage, idempotent_key)`
    * **幂等**：若同 `idempotent_key` 已存在，视为重复推进，直接读取已存在事件进入第 3 步

3. **更新现值**（`ing_cursor`）

    * 乐观锁：`WHERE version = ?` 或 `WHERE cursor_value <= new_value`（TIME/ID）
    * **仅前进**：若比较结果表明“不前进或回退”，则**跳过更新**（但事件已记录）
    * 失败重试：并发下 CAS 失败则重读现值，若仍需前进再重试一次

> **说明**：记录事件即承诺“本次批次已确认”；即使现值因并发未前进，事件也能支撑审计与回放。

## 10.4 边界与乱序处理

* **半开区间**：执行期使用 `[from, to)`；“等于 `to`”的记录在下一窗采集，避免重/漏
* **乱序**：若源返回数据存在“晚到”，`safetyLag` 作为缓冲；仍漏入下一窗，通过唯一键与 `updatedAt` 覆盖保证一致性
* **重复**：重复项通过唯一键幂等过滤，不影响推进；事件中 `observed_max_value` 记录本窗见到的最大 `updatedAt`（便于问责与调参）

## 10.5 命名空间策略

* **HARVEST**：`namespace_scope=EXPR` 或 `GLOBAL`（多个端点复用一条水位时，用 `GLOBAL`）
* **BACKFILL**：`namespace_scope=CUSTOM`，`namespace_key=plan_id 或 backfill_activity_hash`；**不回退**前向现值
* **UPDATE**：`namespace_scope=CUSTOM`，按“刷新策略”选择 TIME 或 ID 型游标；**不影响** HARVEST 现值

## 10.6 并发与一致性

* **分区推进**：以 `(provenance_code, endpoint_name, operation_code, namespace)` 为推进分区；同一分区内可并发跑多个任务，但
  **同一游标键**更新需 CAS
* **幂等键**：事件层面以 `(run_id, batch_no, new_value)`（或 token/url）构造；重复写入被自然去重
* **失败恢复**：进程崩溃不影响已落事件；现值可能“落后事件”，下一批推进会对齐

## 10.7 观测、GC 与保留

* **观测**：建立 `idx_cursor_sort_time / idx_cursor_sort_id` 与 `idx_cur_evt_timeline`，支持看板“水位线”与“推进事件时间线”
* **保留策略**：`ing_cursor_event` 建议按来源/操作维度**保留 90 天**（可配）；冷归档后仍需可查询 lineage
* **压缩**：可做“事件采样压缩”（例如每 100 次推进保留 1 次中间事件 + 所有跨窗口边界事件），不影响回放根因定位

---

# 11. 幂等、一致性与去重

> 目标：保证“多次执行一次效果（幂等）”“不因重复导致冲突或污染（去重）”“前后窗口与多操作并行下的数据一致性（一致性）”。核心对象：*
*唯一业务键、批次幂等键、事件幂等键**。

## 11.1 业务唯一键与覆盖规则

* **唯一业务键**：`(provenance_code, endpoint_name, provider_item_id)`
* **覆盖判定**：以 `updatedAt`（或等价版本字段）为主：

    * `incoming.updatedAt > existing.updatedAt` ⇒ **覆盖**（更新/重发事件）
    * `incoming.updatedAt == existing.updatedAt` ⇒ **不覆盖**（默认；如来源声明“同时间戳存在版本号”，则以版本号次序）
    * `incoming.updatedAt < existing.updatedAt` ⇒ **丢弃**（视为到达晚的旧版本）
* **字段级补齐**（可选）：当 UPDATE 场景需要“只补空”时，可声明“空值不覆盖策略”（在映射层实现，不改 DDL）

## 11.2 批次幂等

* **约束**：

    * `uk_run_batch_no (run_id, batch_no)`
    * `uk_run_before_tok (run_id, before_token)`（TOKEN/URL 型）
    * `uk_batch_idem (idempotent_key)`（自由签名字段）
* **行为**：命中任意唯一约束 ⇒ **读旧不重写**（Executor 直接复用既有统计与 `after_*`，继续推进）
* **重放**：以 `run_id/batch_no` 为入口，若幂等命中则无副作用；若批次不存在则按 `expr_hash + slice_signature_hash`重组请求参数再执行

## 11.3 任务幂等

* **约束**：`uk_task_idem (idempotent_key)`
* **构成建议**：`slice_signature_hash + expr_hash + operation_code + trigger_source + normalized(params)`
* **行为**：重复派生同一切片的任务时，Planner 得到“已存在”并**不再新建**；确保库存与执行的确定性

## 11.4 事件幂等（Outbox + CursorEvent）

* **对外事件（IntegrationEvent）**：幂等键与业务唯一键一致（或补充版本号）；消费者侧“多次可见，效果一次”
* **游标事件**：`ing_cursor_event.idempotent_key` 防重复写；重复推进时“事件已存在”即视为成功

## 11.5 窗口一致性与边界不重不漏

* **规则**：半开 `[from, to)`；等于 `to` 的记录进入下一窗
* **HARVEST 连续窗口**：窗口序列 `{[t0,t1), [t1,t2), ...}`；唯一键 + 覆盖规则保证“重复项不会造成冲突”；**不得跨窗回退水位**
* **与 BACKFILL 并行**：BACKFILL 命名空间推进**不影响** HARVEST；同一记录的重复到达以覆盖规则解决一致性
* **与 UPDATE 并行**：UPDATE 仅刷新已存在项；其游标（ID/TIME）与 HARVEST 水位相互独立

## 11.6 两段式（ID→详情）一致性

* **Phase 1（ID 枚举）**：去重 ID；允许“ID 缺失详情”进入“坏 ID”列表，避免死循环
* **Phase 2（详情拉取）**：以 ID 批为单位；任一批失败不影响已成功批；下一次重试/重放只执行失败批
* **覆盖规则**：详情返回 `updatedAt` 小于等于现存值 ⇒ 不覆盖

## 11.7 重试与一致性

* **可重试错误（网络/5xx/429）**：在批次幂等保护下重发；成功后推进游标
* **不可重试（4xx-业务）**：当前批失败；若为映射/参数错误，阻断任务并提示 Planner 暂停该来源端点
* **长尾一致性**：在 `safetyLag` 控制下，可能仍有晚到；通过后续窗口与 UPDATE 修正

## 11.8 数据污染与隔离

* **判定**：`idPath` 为空、`updatedAt` 解析失败、必填字段缺失、非法枚举值
* **处理**：本条数据**不入库**，写“隔离库/错误池”，记录上下文（请求参数、响应片段、表达式栈）；不阻塞同批其他记录
* **后续**：由 UPDATE 的 `Signals` 路线对隔离项做定向重试或人工介入

## 11.9 顺序保证与并发安全

* **分区定义**：`(provenance_code, endpoint_name, operation_code)` 为最小并发域；同域内任务可并行，但**同一业务唯一键**
  的写入需依赖存储幂等（唯一约束或乐观锁）
* **游标推进**：同域内 CAS，确保“仅前进”；不同域（命名空间）互不影响
* **任务租约**：`leased_until` 防止多实例重复消费

## 11.10 观测与度量（幂等/一致性相关）

* **幂等命中率**：任务幂等命中、批次幂等命中占比（预期很低，异常升高说明重复派生或网络抖动）
* **覆盖比**：`updated / (inserted + updated)`，观察来源数据“更新频率”
* **重复率**：同唯一键在窗口内重复出现比例（过高提示分页/排序问题或上游回放）
* **晚到比例**：进入下一窗才覆盖成功的记录占比（用于调优 `safetyLag`）

## 11.11 验收与回放标准

* **幂等**：同一切片/批次在任意重试/重放下产生**相同效果**；幂等键命中即“读旧跳写”
* **一致性**：相邻窗口不重不漏；并行操作互不污染水位；覆盖规则严格执行
* **回放**：给定 `run_id/batch_no`，可在无副作用前提下还原请求参数与推进轨迹；错误可复现到**批次级**定位

---

# 12. 错误分级与自愈（Error Taxonomy & Self-Healing）

> 目标：把错误**标准化分级**，在 **Planner/Executor** 两条流水线上实现“判定→隔离→退避/重切→恢复”的闭环；确保在
> 429、网络波动、Schema 漂移、数据污染等场景下**自动降级**仍可持续推进游标，并给出**最小观测+告警**。

## 12.1 错误分级（强约束）

| 等级 | 名称                     | 典型症状                                   | 处置原则                                          | 对任务/批次的影响                  |
|----|------------------------|----------------------------------------|-----------------------------------------------|----------------------------|
| L1 | **可重试（Transient）**     | 网络超时、DNS、5xx、429/`Retry-After`         | **退避重试 + 限速**；保留幂等，优先读旧                       | 批次重试；超阈→Run `PARTIAL`      |
| L2 | **不可重试（业务/请求）**        | 4xx（400/403/404/422 等）、参数非法、Body 不被接受  | **停止本批**；标记 Run `FAILED` 或 `PARTIAL`；回溯表达式/映射 | 批次 `FAILED`；任务可能 `FAILED`  |
| L3 | **数据污染（Data Quality）** | `idPath` 为空、`updatedAt` 解析失败、必填缺失、非法枚举 | **隔离单条**，不中断同批；记录隔离池并触发 UPDATE-Signals        | 批次 `SUCCEEDED`（带失败数）；单条入隔离 |
| L4 | **致命（Fatal）**          | 鉴权失败、凭据过期且不可刷新、签名逻辑异常、Schema 大变更       | **立刻停线**；Planner 暂停该来源/端点下发，人工介入              | 任务 `FAILED`；来源级熔断          |

> **判定来源**：Executor 在批次层做初判；Planner 在编译/切片阶段做结构与字典校验（见第 3、4 章）。

## 12.2 自愈动作矩阵（错误→动作）

| 触发条件                                     | 自愈动作                                           | 反馈与记录                                           |
|------------------------------------------|------------------------------------------------|-------------------------------------------------|
| 429 比例 > 阈值（默认 5%/5 分钟）或收到 `Retry-After` | 降低分区 `qps`（最小降到 30%），延长退避；必要时**暂停 Planner 下发** | 在 `ing_task_run(stats)` 记录降速；Planner 记入 Plan 备注 |
| 平均批次时延 > 阈值（默认 3s）                       | 降低并发 `C_max`；缩小页大小（若允许）                        | Run/Batch `stats` 增加“降并发”标记                     |
| 估算偏差大（实际页深 >> 估算）                        | **在线再切片**：对剩余窗口二分；旧 slice 标记 `PARTIAL`         | Planner 为新增 slice 赋新 `slice_signature_hash`     |
| 5xx/网络超时连续 N 次（默认 3）                     | 短路 60–120s；进入指数退避 + 抖动（`exponential_jitter`）   | Run `stats` 记 `circuit_open=true`               |
| L2 请求错误率>阈值（默认 10%）                      | 停止该任务，标记 `FAILED`；回溯 `expr_snapshot` 与参数映射     | `error` 写明确字段名与示例响应                             |
| L3 数据污染                                  | 单条**跳过+隔离**；不影响批次继续                            | 隔离项写“错误池/隔离库”（外部或日志）并形成 UPDATE-Signal           |
| L4 致命                                    | **来源/端点级熔断**；Planner 停下发；告警                    | 需要人工恢复；恢复后再手动解封                                 |

> 阈值可在配置中心管理；默认值写入运维手册。

## 12.3 批次级重试策略（与幂等配合）

* **退避算法**：`exponential_jitter`（起始 100ms，倍率 2.0，上限 30s，抖动 20%），最大尝试 `maxAttempts` 默认 5（来自 Registry；见
  4.11）。
* **幂等保护**：命中 `uk_run_batch_no / uk_run_before_tok / uk_batch_idem` ⇒ **读旧结果**、不重写（见第 9 章）。
* **复合容错**：当 TOKEN/URL 最后一页出现断流，允许“**尾页探针**”额外重试 1 次（配置开关）。

## 12.4 Planner 阶段错误与降级

* **字典/必填缺失**：Plan 不创建，`ing_schedule_instance.record_remarks` 写明缺项；告警。
* **能力降级**：无 `count` / 无 `hasMore` / 无 `updatedSince` ⇒ 切换到抽样估算、基于 `items.size < pageSize` 的保守
  `hasNext`、或 客户端 TIME 扫描；Plan 备注记录降级原因。
* **预算不足**：库存超阈或日配额接近上限 ⇒ Plan `PARTIAL`；待库存/配额回落由下次触发继续切片。

## 12.5 错误隔离库（Data Isolation）

* **内容**：来源/端点、批次 lineage、请求摘要、响应片段（脱敏）、失败原因（字段路径/枚举值非法/时间解析失败等）。
* **边界**：*不*阻塞同批其他记录；*不*推进该条的业务状态。
* **后续**：通过 CONTRACT 读侧暴露为 UPDATE 的 `Signals` 候选集，走“按 ID 重拉/纠正”。

## 12.6 告警与运维阈值（最小集合）

* **致命**：L4 任何一次（立即钉钉/邮件）；凭据/签名失败；Schema 变更。
* **持续失败**：5xx/429 连续 3 次；错误率 > 5% 持续 10 分钟。
* **游标停滞**：某来源/端点/操作在 30 分钟内 `ing_cursor` 无推进（HARVEST）；
* **库存异常**：QUEUED 任务 > 上限；或 READY Plan 长时间无人消费。
* **回放请求**：人工触发批次重放后，必须记录“回放标记”，避免与线上混淆。

## 12.7 验收（Error DoD）

1. 任意 L1 场景能在幂等保护下自动恢复并推进游标。
2. L2 能明确指出责任字段/路径，停止当前任务并给出可操作诊断。
3. L3 隔离率可观测，且能被 UPDATE-Signals 消化。
4. L4 熔断后，Planner 不再下发该来源/端点，直至人工解除。
5. 在线再切片能把“页深过大/频繁 429”降解为更多、更小、可完成的 slice。

---

# 13. 性能与索引策略（贴表设计）

> 目标：在**不改字段**的前提下，通过**并发/批量参数、速率预算、索引布局、函数索引、连接池与事务策略**，让 Planner/Executor
> 在吞吐、可回放与 DB 压力之间取得平衡。数据库为 **MySQL 8.0 / InnoDB**。

## 13.1 并发/批量参数（运行层面）

* **Executor 并发**

    * 全局并发 `C_total`、分区并发 `C_max`（维度：`provenance_code, endpoint_name, operation_code`）。
    * 建议初值：`C_total = CPU*2`，`C_max = min(4, C_total/来源数)`；在 429/时延升高时自动下调。
* **QPS/配额**：由 Budgeter 统一控制；共享 `provenance+endpoint` 令牌桶，避免“实例间竞争”。
* **Batch 粒度**

    * pageSize：以供应商上限的 60–80% 起步；
    * idBatchSize（两段式）：200–500 条/批（按响应体大小微调）。
* **连接池（HikariCP）**

    * `maximumPoolSize ≈ C_total + 4`；
    * `connectionTimeout` 2–5s，`idleTimeout` 30–60s；
    * 避免“每批一个事务”里做重型长占用（控制 SQL 数与行数）。

## 13.2 事务与隔离级别

* **本地短事务**：Run/Batch 写入与游标推进分批提交；避免“大事务”阻塞。
* **隔离级别**：`READ COMMITTED`（默认推荐）；避免 `REPEATABLE READ` 下的间隙锁膨胀。
* **写顺序**：严格遵循“**先事件（event），后现值（cursor）**”，并各自独立事务，降低冲突面。
* **长读查询**（看板/统计）：走 CONTRACT 读侧，避免与写冲突；必要时加二级库或延时只读副本。

## 13.3 索引策略（仅新增索引，不增字段）

> 以**典型查询模式**反推索引；优先**合成/覆盖索引**；必要时使用**函数索引（Functional Index）**，不引入生成列。

好的，下面是修改后的 **13.3.1 `ing_task`（取任务队列）** 小节内容，你可以直接替换掉原文。

---

## 13.3.1 `ing_task`（取任务队列）

### 队列筛选规则

执行器在领取任务时，必须遵循以下 SQL 模式：

```sql
SELECT id, ...
FROM ing_task
WHERE status_code = 'QUEUED'
  AND (leased_until IS NULL OR leased_until < NOW(6))
ORDER BY priority ASC, scheduled_at ASC, id ASC
LIMIT ?;
```

* **status\_code**：仅拉取 `QUEUED` 状态的任务。
* **租约判断**：仅选择 `leased_until` 为空或已过期的任务，避免与其他执行器抢占冲突。
* **排序策略**：

    * `priority ASC`：数值越小优先级越高。
    * `scheduled_at ASC`：越早调度的任务越先执行。
    * `id ASC`：作为最终平衡，保证相同条件下的稳定顺序。

### 抢占与续租语义

* **抢占**：

    * 执行器在出队时，使用 `UPDATE … WHERE id=? AND (leased_until IS NULL OR leased_until < NOW(6)) AND status_code='QUEUED'` 进行原子更新，写入 `lease_owner`、`leased_until`。
    * 成功更新 = 抢占成功；更新数为 0 = 已被其他执行器抢占。
* **续租**：

    * 执行中，执行器必须周期性延长 `leased_until`，避免长跑任务被误判为过期。
    * 续租次数计入 `lease_count`，可用于熔断或诊断。
* **归还**：

    * 执行完毕，写入 `status_code = SUCCEEDED | FAILED | PARTIAL` 等终态，租约字段不再生效。
    * 若执行中断，租约超时后其他执行器可重新抢占。

### 索引要求

* 必须建立以下复合索引来保证公平出队与并发性能：

```sql
CREATE INDEX idx_task_queue
  ON ing_task (status_code, leased_until, priority, scheduled_at, id);
```

该索引保证了按状态过滤、租约判断、优先级与调度时间排序的高效执行。

### 状态机约束

* `QUEUED` → 被抢占后更新为 `DISPATCHED` 或直接进入 `EXECUTING`。
* 终态：`SUCCEEDED / FAILED / PARTIAL / CANCELLED`。
* 状态变迁必须通过 CAS 或事务内检查，确保无并发覆盖。

### 观测字段（最小集）

每次任务领取/续租/完成时，记录以下观测数据：

* `lease_owner`、`leased_until`、`lease_count`；
* 抢占成功/失败次数；
* 队列等待时间 = `now - scheduled_at`；
* 执行时长 = `finished_at - started_at`。

这些字段支撑后续的可观测性与调度优化。

### 13.3.2 `ing_task_run`

* **查询模式**：按 `task_id` 查最近一次 run；按 `status` 聚合；回放用 `run_id` 精确查。
* **建议索引**：

    * `idx_run_task(task_id, status_code, created_at)`；
    * `uk_run_attempt(task_id, attempt_no)`（已存在）；
    * `idx_run_id(run_id)`（主键或唯一）。

### 13.3.3 `ing_task_run_batch`

* **查询模式**：按 `run_id` 顺序浏览批次；以 `before_token` 去重；按时间/状态聚合。
* **建议索引**：

    * `uk_run_batch_no(run_id, batch_no)`（已存在）；
    * `uk_run_before_tok(run_id, before_token)`（已存在）；
    * `idx_batch_time(run_id, created_at)`（时间线）；
    * `idx_batch_status(run_id, status_code)`（失败定位）。
* **覆盖字段**：在同一索引 `idx_batch_time` 的 `INCLUDE`（MySQL 无 INCLUDE，可将常查的 `has_next/latency`
  放行内列或走回表——保持简单，回表即可）。

### 13.3.4 `ing_cursor`

* **查询模式**：展示当前水位线、按来源/操作/命名空间过滤；对 TIME 型，按时间排序。
* **建议索引**：

    * `uk_cursor_ns(provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)`（已存在建议）
    * `idx_cursor_time(provenance_code, operation_code, normalized_instant)`（TIME 型）
    * `idx_cursor_id(provenance_code, operation_code, normalized_numeric)`（ID 型）

### 13.3.5 `ing_cursor_event`

* **查询模式**：按 `(provenance_code, operation_code, namespace…)` 查看时间线；按时间范围检索；按 lineage 回溯。
* **建议索引**：

    * `uk_cur_evt_idem(idempotent_key)`（已存在建议）
    * `idx_cur_evt_timeline(provenance_code, operation_code, namespace_scope_code, namespace_key, created_at)`
    * `idx_cur_evt_lineage(schedule_instance_id, plan_id, slice_id, task_id, run_id)`（组合或分解多个）
    * 若 TIME 型使用多：`idx_cur_evt_instant(provenance_code, operation_code, new_instant)`

### 13.3.6 `ing_plan / ing_plan_slice`

* **查询模式**：查看计划进度；按 `plan_id` 找 slice；按状态查未就绪 slice。
* **建议索引**：

    * `idx_plan_srcop_time(provenance_code, operation_code, created_at)`
    * `idx_slice_status(plan_id, status_code)`
    * `uk_slice_sig(plan_id, slice_signature_hash)`

## 13.4 函数索引（MySQL 8.0）

* **场景**：对 JSON 字段或时间字符串的高频筛选/排序。
* **做法**：使用 **Functional Index** 直接索引表达式（例如 `JSON_EXTRACT(params, '$.window.from')`、
  `CAST(cursor_value AS DATETIME)`），避免新增生成列。
* **注意**：函数索引需要 MySQL 8.0+，表达式应与查询保持**完全一致**；变更需评估对写入开销的影响。

## 13.5 大表增长与分层

* **冷热分层**：

    * `ing_task_run_batch / ing_cursor_event` 增长最快：保留 90 天在线，其余归档到历史库或对象存储（仅回溯时加载）。
* **分区策略**（可选）：对事件/批次表按 `created_at` 做 RANGE 分区（需要 DDL；如不便，暂不启用分区，依赖归档）。
* **归档作业**：每日/每周离线任务，将过期数据转存并保留 lineage 可检索键（如 `run_id`/`batch_no`/`plan_id`）。

## 13.6 读侧查询（CONTRACT）性能

* **物化视图/汇总表**（可选）：为看板聚合（来源/操作/状态）准备轻量物化表（由离线任务维护）；
* **避免跨大表 JOIN**：读侧优先使用冗余字段（第 7 章已建议 run/batch 冗余 lineage），减少实时 JOIN。

## 13.7 MySQL/InnoDB 调优（基线）

* **表引擎**：InnoDB；`innodb_flush_log_at_trx_commit=1`（强一致）；在高写入压力且可承受轻微数据丢失时可短暂设为 2（不推荐长期）。
* **Buffer Pool**：≥ 数据热集的 2–3 倍；监控 `Buffer Pool hit ratio`。
* **Redo/Undo**：写多时增大 `innodb_log_file_size`（例如 1–2GB/文件）。
* **连接数**：与 Hikari `maximumPoolSize` 对齐，避免过量空闲连接。
* **慢查询**：开启慢日志；对超过 500ms 的查询逐一优化（多为少索引或回表过多）。

## 13.8 应用侧开销与序列化

* **Jackson**：关闭反射自动探测，使用显式构造器；启用 `Afterburner` 可选（评估 CPU）。
* **批量写**：MyBatis-Plus 用批量插入（但每批条数控制在 200–500 之间，避免单事务过大）。
* **请求并发**：HTTP 连接复用（Keep-Alive）；压缩（GZIP）在响应体较大时开启，避免 CPU 过载。

## 13.9 基准与压测（建议）

* **单来源基线**：在 `qps=供应商上限 70%`、`pageSize=上限 70%` 时，测得**批次平均时延/429 比例/错误率**；
* **扩展性**：实例数从 1→2→4 线性扩展，观察“队列耗尽时间”与 DB CPU/IO；
* **极端场景**：限流骤降/网络抖动/响应大对象；验证在线再切片与退避是否生效。

## 13.10 验收（Performance DoD）

1. 取任务查询平均 < 10ms，P95 < 30ms。
2. 批次写入（`ing_task_run_batch`）平均 < 20ms，P95 < 80ms（不含外部请求时延）。
3. 在供应商 70% 限流下，Executor 实例数翻倍吞吐**近似翻倍**（瓶颈不在 DB）。
4. 看板查询（读侧）P95 < 200ms，且对写入无显著干扰。
5. 归档后主库事件/批次表行数稳定在目标范围内，Buffer Pool 命中率 > 99%。

---

# 14. 可观测最小集与黑匣子

> 目标：用**最少的埋点**覆盖 Planner/Executor 的关键路径，任何异常都能在**分钟级**定位与**批次级**复现。黑匣子 = 基于
`ing_task_run` / `ing_task_run_batch` / `ing_cursor_event` 的“可重放证据包”。

## 14.1 范围与设计原则

* **范围**：计划与切片、任务与运行/批次、HTTP I/O、限流与重试、游标推进、错误分级、自愈动作。
* **原则**：

    1. **批次粒度**优先：观测与回放都以 `run_batch` 为最小单位；
    2. **强关联**：统一 lineage（`schedule→plan→slice→task→run→batch`）贯穿所有观测点；
    3. **脱敏**：日志与证据包只存**请求/响应摘要**，不落密钥与可逆签名；
    4. **降噪**：指标归档、日志抽样，保证生产稳定与成本可控；
    5. **只读读侧**：可视化与看板全部通过 CONTRACT 读模型提供，不干扰写路径。

---

## 14.2 统一关联键（Lineage & Correlation）

* **Lineage 六元组**（贯穿所有观测记录）
  `schedule_instance_id, plan_id, slice_id, task_id, run_id, batch_id`
* **CorrelationId（请求级）**
  `ing-{run_id}-{batch_no}`（落到 HTTP Header：`X-Ingest-Correlation-Id`），便于跨系统排障。
* **Spec 指纹**
  `spec_fingerprint, expr_proto_hash, expr_hash, slice_signature_hash`：确保重放时 1:1 对齐执行上下文。

---

## 14.3 最小观测字段清单（落在既有表的 JSON 字段内）

> 不增列，仅规定 `stats` / `record_remarks` / `error` / `checkpoint` 内的结构。

### 14.3.1 `ing_task_run.stats`（示例 JSON）

```json
{
  "reqCount": 42,
  "retCount": 3800,
  "inserted": 2900,
  "updated": 850,
  "skipped": 50,
  "failed": 0,
  "retryCount": 7,
  "429Count": 3,
  "avgLatencyMs": 480,
  "maxLatencyMs": 2100,
  "pagesVisited": 42,
  "hasNextLast": false,
  "rateAdjusted": true,
  "remarks": "online-reslice-suggested:false"
}
```

### 14.3.2 `ing_task_run_batch.stats`（示例 JSON）

```json
{
  "itemsCount": 100,
  "inserted": 71,
  "updated": 28,
  "skipped": 1,
  "failed": 0,
  "latencyMs": 520,
  "retryCount": 1,
  "429Count": 1,
  "hasNext": true,
  "nextHint": {
    "token": "Abc...",
    "page": 12,
    "url": null
  },
  "http": {
    "status": 200,
    "bytes": 145678
  },
  "selfHealing": {
    "qpsDownshift": true,
    "pageSizeShrink": false
  }
}
```

### 14.3.3 `ing_task_run_batch` 请求/响应摘要（脱敏）

```json
{
  "request": {
    "method": "GET",
    "urlTemplate": "https://api.example.com/search",
    "query": {
      "q": "${expr.q}",
      "page": "${page.no}"
    },
    "headers": [
      "Accept: application/json"
    ],
    "bodyHash": "sha256:... (optional)"
  },
  "response": {
    "status": 200,
    "headerHints": {
      "retryAfter": null,
      "rateLimitRemaining": 12
    },
    "itemsPath": "$.data.items",
    "nextTokenPath": "$.next",
    "sample": "sha256:response-body-sample-hash"
  }
}
```

### 14.3.4 `ing_cursor_event` 观测字段

* `direction_code, prev_value, new_value, observed_max_value`
* 追加 `record_remarks`: `{"reason":"HARVEST","batchNo":12,"lateArrival":3}`

### 14.3.5 Planner 备注（`ing_plan.record_remarks`）

```json
{
  "totalEstimated": 42000,
  "sliceCount": 210,
  "window": {
    "from": "...",
    "to": "...",
    "safetyLag": "PT10M"
  },
  "degradation": [
    "noCount",
    "hasNextByPageSize"
  ],
  "budget": {
    "maxRequests": 800,
    "qps": 5
  },
  "warnings": []
}
```

---

## 14.4 指标（Metrics）最小集合

> 建议基于 Micrometer/OpenTelemetry 导出（Prometheus 语义）。名称统一 `patra_ingest_*` 前缀。

* **吞吐**

    * `patra_ingest_batches_total{source,endpoint,op}` 计数器
    * `patra_ingest_items_total{source,endpoint,op,action=insert|update|skip}`
* **延迟**

    * `patra_ingest_batch_latency_ms{source,endpoint,op}` 直方图（P50/P90/P99）
    * `patra_ingest_http_latency_ms{source,endpoint,op}`
* **错误与自愈**

    * `patra_ingest_errors_total{level=L1|L2|L3|L4,source,endpoint,op}`
    * `patra_ingest_429_total{source,endpoint,op}`
    * `patra_ingest_retries_total{source,endpoint,op}`
    * `patra_ingest_self_heal_events_total{type=qpsDownshift|reslice|circuitOpen}`
* **队列与并发**

    * `patra_ingest_tasks_queued{source,endpoint,op}` Gauge
    * `patra_ingest_executor_concurrency{source,endpoint,op}` Gauge
* **游标推进**

    * `patra_ingest_cursor_lag_seconds{source,endpoint,op,ns}`（`now - cursor_instant`）
    * `patra_ingest_cursor_advance_events_total{direction}`
* **预算与限流**

    * `patra_ingest_budget_remaining{source,endpoint,op}` Gauge
    * `patra_ingest_qps_effective{source,endpoint,op}` Gauge

> **最小告警线**：见 14.8。

---

## 14.5 日志（Logs）与抽样

* **结构化日志**（JSON 行）：分级 `INFO/WARN/ERROR`；字段必须包含 lineage、correlationId、source/endpoint/op。
* **关键打点**

    * 领取/释放任务（包含租约信息、并发槽位）；
    * 每批次的**摘要**（不落大响应体）；
    * 自愈动作决策（降速/再切片建议/断路）；
    * L2/L4 错误细节（字段路径、示例值、响应片段 hash）。
* **抽样策略**

    * `INFO` 批次日志 **抽样 1%**（成功路径降噪）；
    * `WARN` 全量；`ERROR` 全量；
    * 对于 PII/机密，统一脱敏（见 14.10 安全）。

---

## 14.6 追踪（Traces）

* **Span 层级**

    * `PlannerTrigger` → `CompileSpec` → `Slice`（每个 slice 1 span）
    * `ExecutorTrigger` → `PickTask` → `Run` → `Batch`（每个批次 1 span） → `HTTP Call`（每次请求 1 span）
* **传播**：`X-Ingest-Correlation-Id` + W3C Trace Context；HTTP 客户端自动注入。
* **标签**：`source/endpoint/op/priority/page/token/hasNext/status/http.status/error.level`。

---

## 14.7 看板（Dashboards）与查询契约（CONTRACT）

> CONTRACT 提供只读查询，Dashboard 从 CONTRACT 拉取聚合数据。

* **队列看板**

    * 维度：`source/endpoint/op`
    * 指标：队列深度、取任务速率、成功/失败率、平均批次延迟、429 比例
* **运行时间线**

    * Drill-down：`plan → slice → task → run → batch`
    * 展示：每批次状态、错误点、重试次数、hasNext/nextHint、HTTP 状态直方图
* **游标面板**

    * 每 `(source,op,ns)` 的当前水位、滞后（秒）、推进事件时间线
    * 晚到比例（跨窗覆盖的记录占比）
* **预算/限流面板**

    * 实际 QPS 与预算 QPS 对比；降速次数与原因
    * 自愈动作计数

> **查询最小 API**（由 CONTRACT 输出）：

1. `getQueueSnapshot(source?,op?)`
2. `getPlanLineage(planId)`（级联至 slice/task/run/batch）
3. `getCursorTimeline(source,op,ns,from,to)`
4. `getErrorTopN(source?,op?,level,from,to)`

---

## 14.8 告警（Alerts）与阈值（最小集合）

* **致命（L4）**：出现即报警（高优先级，含来源/端点/原因）。
* **游标停滞**：`cursor_lag_seconds > 1800` 持续 2 个采样周期（HARVEST）。
* **错误率**：`errors(L1+L2)/batches > 5%` 且持续 10 分钟。
* **429 占比**：`429_total/batches > 5%` 持续 5 分钟。
* **队列异常**：`tasks_queued` 突增超过日均 + 3σ 或 READY Plan 超 30 分钟未被消费。
* **预算逼近**：`budget_remaining < 10%` 触发“降速 + 暂停 Planner 下发”通知。

---

## 14.9 黑匣子（Blackbox）定义与回放流程

### 14.9.1 黑匣子包含物

* **定位键**：`run_id, batch_no`（主），以及 `slice_signature_hash, expr_hash, spec_fingerprint`（对齐上下文）。
* **请求证据**：请求模板、渲染参数（去密）、URL/Query/Header 摘要、Body 哈希。
* **响应证据**：HTTP 状态、Header 提示（`Retry-After` 等）、`itemsPath/next*`、响应体哈希与**最多 1KB 片段样本**（脱敏）。
* **处理证据**：`stats`、唯一键/覆盖判定摘要（仅计数或前 3 个样例）、自愈动作。
* **游标证据**：对应的 `ing_cursor_event` 记录（prev/new/observed\_max 与 lineage）。

> 所有证据均已存在于 `ing_task_run[_batch]` 与 `ing_cursor_event` 的 JSON 字段中；黑匣子 = **按键聚合导出**。

### 14.9.2 回放流程（只读环境）

1. **选择批次**：以 `run_id/batch_no` 定位（或按时间线选择）。
2. **恢复上下文**：加载 `expr_hash / slice_signature_hash / spec_fingerprint`，锁定 Spec Snapshot 与 slice 边界。
3. **重建请求**：用 `expr_snapshot + before_token/page_no/url + params` 渲染请求；如需 Body，取 `bodyHash` 对齐生成。
4. **执行回放**：在沙箱网络/只读模式下发起请求，核对 HTTP 状态、`itemsCount/nextHint`、样本哈希。
5. **比对结果**：若源已变更，标注“时点不一致”；若一致，按唯一键与覆盖规则重放本地处理（可模拟、不落库）。
6. **生成结论**：输出回放报告（差异表、失败原因、是否与自愈建议一致）。

> **注意**：回放默认**不推进游标、不写入 Outbox**；仅在“全链路回放”模式（人工授权）下可对比写入路径，但仍不提交。

---

## 14.10 脱敏与合规

* **请求**：不落 API Key/Bearer/OAuth token；签名仅记录算法名与输入字段哈希，不记录密钥或明文。
* **响应**：不落患者/作者邮箱等 PII；仅保留**哈希或前缀化样本**。
* **日志**：字段值含疑似 PII（邮箱/手机号/身份证）→ 正则掩码；保存 hash 以便比对。
* **访问控制**：黑匣子导出需审计与授权；导出包带有效期与审计标识（操作者、时间、原因）。

---

## 14.11 数据保留与成本控制

* **批次与事件**：在线保留 90 天；历史归档到对象存储（JSONL + index）并保存 lineage 键，便于按需回拉。
* **指标**：原始时间序列 14–30 天，长期聚合（小时/天粒度）保留 180 天。
* **日志**：INFO 抽样 1%，WARN/ERROR 全量，统一 30 天在线 + 冷存。
* **导出**：黑匣子证据包按需导出，默认 7 天有效。

---

## 14.12 验收标准（Observability DoD）

1. 发生 L1/L2/L3/L4 时，可在**一个仪表盘**上定位来源/端点/操作与最近的错误批次。
2. 给定任意 `run_id/batch_no`，能导出黑匣子并在沙箱环境**复现请求与解析**；产出一致性报告。
3. HARVEST 的 `cursor_lag_seconds` 超阈会告警，处理后 **15 分钟内**恢复到健康阈值。
4. 自愈动作（降速/再切片/断路）在指标与日志中**可见且可追溯**。
5. 所有观测与证据均不泄露密钥与 PII，导出受控且可审计。
