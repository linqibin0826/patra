# Papertrace Registry Schema & SQL 指南

## 0. 摘要（Executive Summary）

本设计面向**医学文献数据采集**（如 **PubMed / Crossref** 等多源对接）的**通用配置库**，目标是在 **MySQL 8.0** 上以*
*高度拆分、强语义命名、低耦合、可演进**的方式，沉淀采集端最常见且多变的配置维度（时间窗口、增量指针、分页/游标、端点与
HTTP、批量成型、重试退避、限流并发、鉴权密钥）。

**关键特点**

* **分维度建模 + 精准命名**：

    * 主表：`reg_provenance`（数据来源登记）；
    * 维度配置表（统一前缀 `reg_prov_`）：

        * `reg_prov_endpoint_def`（端点定义）
        * `reg_prov_window_offset_cfg`（时间窗口与增量指针）
        * `reg_prov_pagination_cfg`（分页与游标）
        * `reg_prov_http_cfg`（HTTP 策略）
        * `reg_prov_batching_cfg`（批量抓取与请求成型）
        * `reg_prov_retry_cfg`（重试与退避）
        * `reg_prov_rate_limit_cfg`（限流与并发）
        * `reg_prov_credential`（鉴权/密钥，支持可选绑定端点）
* **统一作用域模型**：每个配置可作用于**来源级（SOURCE）**或**来源+任务级（TASK）**；任务枚举：`harvest | update | backfill`。
* **时间区间语义**：配置采用 `[effective_from, effective_to)`；**不使用触发器与 CHECK**，**不做数据库层校验**，一切业务规则由应用层保证。
* **无链式覆盖**：不做模板/继承，不做字段级“层层 override”。推荐策略是**优先取 TASK 级配置**（若存在），否则**回退 SOURCE 级**
  ；字段为 **NULL** 表示“由应用使用默认值”。
* **可扩展 & 可维护**：

    * 为少量非结构化参数预留 `JSON` 字段；
    * 常用查询路径下建好复合索引；
    * 外键只指向**来源主键**与**端点定义主键**，其余跨维度依赖留在应用侧装配。
* **契合 DDD / 六边形 / CQRS 的落地**：

    * 领域层只依赖\*\*端口（Ports）/仓储（Repositories）\*\*抽象；
    * 基础设施层（MyBatis-Plus + Jackson）做关系-对象映射与 JSON 映射；
    * 读写分离：读侧可直接组织“当前生效配置”的查询与缓存；写侧通过新增区间记录实现“灰度切换、平滑回滚”。

**最小可用流程（Quick Start）**

1. 在 `reg_provenance` 登记来源（如 `pubmed`、`crossref`）；
2. 为目标任务类型（如 `update`）各维度插入**一条**生效配置（`reg_prov_*_cfg` 与/或 `reg_prov_endpoint_def`）；
3. 运行时**按维度取单条“当前生效记录”**（优先 TASK 级，否则 SOURCE 级）；
4. 将这些维度拼成一次抓取的“执行合同”，驱动采集器完成：**搜索 → 翻页/游标 → 详情批量 → 重试退避 → 限流并发 → 鉴权**。

> 预览示例（简化版）：
>
> ```sql
> -- 1) 登记来源
> INSERT INTO reg_provenance (provenance_code, provenance_name, base_url_default)
> VALUES ('pubmed','PubMed','https://eutils.ncbi.nlm.nih.gov/entrez'),
>        ('crossref','Crossref','https://api.crossref.org');
>
> -- 2) 为 PubMed update 任务配置窗口与增量指针（按天滑动，EDAT 字段）
> INSERT INTO reg_prov_window_offset_cfg
> (provenance_id, scope, task_type, effective_from, window_mode, window_size_value, window_size_unit,
>  overlap_value, overlap_unit, offset_type, default_date_field_name)
> VALUES ( (SELECT id FROM reg_provenance WHERE provenance_code='pubmed'),
>          'TASK','update','2025-01-01','SLIDING',1,'DAY',1,'DAY','DATE','EDAT');
>
> -- 3) 为 Crossref harvest 配置游标分页（cursor）
> INSERT INTO reg_prov_pagination_cfg
> (provenance_id, scope, task_type, effective_from, pagination_mode,
>  cursor_param_name, next_cursor_jsonpath)
> VALUES ( (SELECT id FROM reg_provenance WHERE provenance_code='crossref'),
>          'TASK','harvest','2025-01-01','CURSOR','cursor','$.message.next-cursor');
> ```

---

## 1. 背景与总体设计

### 1.1 业务背景

* 医学文献生态存在多个主流数据源（如 **PubMed**、**Crossref**），**API 风格差异大**：增量字段命名不同、分页/游标机制不同、端点与
  HTTP 约束不同、配额与限流策略不同、鉴权方式也不同。
* 采集团队需要一个**统一的配置模型**来描述这些差异，做到\*\*“新增/切换数据源不改代码或少改代码”\*\*，同时满足：

    * **可灰度**：支持在不下线的情况下切换配置（新增新区间记录→切流）；
    * **可并存**：同维度可并存多条历史配置（只要同一时间不冲突）；
    * **可观测**：字段直观、命名清晰、便于排障与对账；
    * **可扩展**：为少量“供应商特有参数”预留 JSON 扩展位；
    * **跨任务差异**：同一数据源在 `harvest | update | backfill` 的策略可不同（窗口、分页、重试、限流等）。

### 1.2 设计原则与不做的事

* **维度拆分优先**：将“时间窗口/指针、分页/游标、端点与 HTTP、批量、重试、限流、鉴权”分别建表，**避免大而全的胖表**与后续频繁改表。
* **强语义命名**：表使用 `reg_prov_` 前缀 + 清晰后缀，字段避免缩写歧义；`provenance_id` 统一指向 `reg_provenance(id)`。
* **时间区间驱动的演进**：不做版本号；采用 `[effective_from, effective_to)`；**不在 DB 层做重叠校验**（**无触发器、无 CHECK**
  ），由应用层保证。
* **无链式覆盖/继承**：**不做模板引入**、**不提供字段级 override**；**字段为 NULL** 意味着“由应用使用默认值”。
* **作用域简洁**：仅两层——`SOURCE`（来源级通用）与 `TASK`（来源+任务级）；应用读取时**优先 TASK、否则回退 SOURCE**，而**不是**
  层层字段覆盖合并。
* **读写分离**（契合 CQRS）：

    * 写侧：新增一条新区间记录用于灰度切换，旧记录可带 `effective_to` 关窗；
    * 读侧：每个维度**只取一条当前生效记录**，再组合成“执行合同”。
* **扩展而不过度设计**：特殊参数放 `JSON`，但常用路径（索引键、过滤键）必须是结构化列。

### 1.3 表与关系（概览）

* **主数据表**

    * `reg_provenance`：登记来源（`provenance_code`、`base_url_default`、`timezone_default`…）。
* **配置/定义表（均含 `provenance_id` → `reg_provenance.id`）**

    * `reg_prov_endpoint_def`：端点名称、用途（SEARCH/FETCH/TOKEN…）、HTTP 方法、路径模板、默认 Query/Body；端点级参数名（如
      `page_param_name`）仅用于**描述该端点本身**。
    * `reg_prov_window_offset_cfg`：时间窗口模型（SLIDING/CALENDAR）、窗口长度/重叠/回看、水位滞后；增量指针（DATE/ID/COMPOSITE、默认日期字段）。
    * `reg_prov_pagination_cfg`：分页模式（PAGE\_NUMBER/CURSOR/TOKEN/SCROLL）、参数名、游标提取 JSONPath。
    * `reg_prov_http_cfg`：基础 URL 覆盖、Headers、超时、TLS/代理、`Retry-After` 与幂等等。
    * `reg_prov_batching_cfg`：详情批量、ID 参数名/分隔符、并行与背压、请求模板。
    * `reg_prov_retry_cfg`：最大重试、退避策略、状态码白/黑名单、断路器阈值与冷却。
    * `reg_prov_rate_limit_cfg`：QPS、突发桶、并发上限、桶粒度（GLOBAL/PER\_KEY/PER\_ENDPOINT）。
    * `reg_prov_credential`：鉴权/密钥；可**可选**绑定到 `reg_prov_endpoint_def.id`（当某凭证只用于某端点）。
* **生成列**

    * 所有 `*_cfg`/`_def`（除凭证）含 `task_type_key`（`IFNULL(task_type,'ALL')`），用于唯一索引
      `(provenance_id, scope, task_type_key, effective_from)` 与查询过滤。

> **ER 关系（文字版）**
> `reg_provenance (1) ──< (N) reg_prov_endpoint_def`
> `reg_provenance (1) ──< (N) reg_prov_*_cfg`
> `reg_prov_endpoint_def (1) ──< (0..N) reg_prov_credential`（可选）
> 运行时：应用分别从各表**各取一条当前生效**（优先 TASK → 回退 SOURCE），再拼成“执行合同”。

### 1.4 作用域与时间模型

* **作用域**

    * `SOURCE`：来源级通用策略；
    * `TASK`：来源 + 任务类型（`harvest | update | backfill`）的特化策略；
    * 读取规则：**若存在 TASK 级**，取 TASK；**否则**取 SOURCE；**不进行字段级 merge**。
* **时间模型**

    * 区间 `[effective_from, effective_to)`；`effective_to` 可为 `NULL`（表示“截至未来”）；
    * **不重叠由应用层保证**（建表时无触发器/CHECK）；
    * 灰度切换：**先插入新记录（新起点），再关闭旧记录**（写入 `effective_to`），观察后清理。

### 1.5 技术选型与实现边界

* **数据库**：MySQL 8.0 / InnoDB / `utf8mb4_0900_ai_ci`；
* **结构化为主、JSON 为辅**：端点默认参数、请求模板、状态码列表等用 `JSON` 存储；
* **索引**：覆盖 `(provenance_id, scope, task_type_key, effective_from)` 与常用维度（如端点用途、时间上界）；
* **应用层**（建议栈）：Java 21 + Spring Boot 3.2.x + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0 +
  MyBatis-Plus 3.5.12 + Jackson。

    * **Hexagonal**：适配器（REST/MQ/调度）→ 应用服务用例（CQRS）→ 领域（配置装配）→ 仓储（MyBatis-Plus 映射到 `reg_prov_*`）。
    * **读侧缓存**：可将“当前生效配置”以 `(provenance_code, task_type)` 为 Key 做短TTL缓存，降低 DB 压力。

### 1.6 使用方式（读写路径概要）

* **写入顺序**

    1. `reg_provenance` 建立来源；
    2. 按需向各 `reg_prov_*` 维度插入**一条**或多条**不重叠**区间记录；
    3. 鉴权如需端点绑定，先插入 `reg_prov_endpoint_def`，再写 `reg_prov_credential.endpoint_id`。
* **读取“当前生效配置”（模板 SQL）**

    * 以 **PubMed update** 为例（只演示分页维度；其它维度同理改表名）：
        ```mysql
              -- 取 pid
          SELECT id INTO @pid FROM reg_provenance WHERE provenance_code='pubmed';
           SET @now = UTC_TIMESTAMP();
           (SELECT *
           FROM reg_prov_pagination_cfg
           WHERE provenance_id=@pid AND scope='TASK' AND task_type='update'
           AND effective_from<=@now AND (effective_to IS NULL OR effective_to>@now)
           ORDER BY effective_from DESC, id DESC
           LIMIT 1)
           UNION ALL
           (SELECT *
           FROM reg_prov_pagination_cfg
           WHERE provenance_id=@pid AND scope='SOURCE'
           AND effective_from<=@now AND (effective_to IS NULL OR effective_to>@now)
           ORDER BY effective_from DESC, id DESC
           LIMIT 1)
           LIMIT 1;      
  ```

    * 应用侧按此模式对**各维度**各取一条，组装为一次运行的“执行合同”。

### 1.7 示例预览（PubMed / Crossref，简版）

> 详细完整示例会在后续章节给出，这里先放一组**可直接执行**的核心样例，帮助理解模型。

```sql
-- 1) 来源登记
INSERT INTO reg_provenance (provenance_code, provenance_name, base_url_default, timezone_default)
VALUES ('pubmed', 'PubMed', 'https://eutils.ncbi.nlm.nih.gov/entrez', 'UTC'),
       ('crossref', 'Crossref', 'https://api.crossref.org', 'UTC');

-- PubMed 的 id
SET @pubmed_id = (SELECT id
                  FROM reg_provenance
                  WHERE provenance_code = 'pubmed');
SET @crossref_id = (SELECT id
                    FROM reg_provenance
                    WHERE provenance_code = 'crossref');

-- 2) 端点定义（示例）
-- PubMed: ESearch（检索）与 EFetch（详情）
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from,
 endpoint_usage, http_method, path_template, default_query_params, request_content_type, is_auth_required)
VALUES (@pubmed_id, 'TASK', 'update', 'esearch', '2025-01-01', 'SEARCH', 'GET', '/eutils/esearch.fcgi',
        JSON_OBJECT('db', 'pubmed', 'retmode', 'json'), 'application/json', 0),
       (@pubmed_id, 'TASK', 'update', 'efetch', '2025-01-01', 'FETCH', 'GET', '/eutils/efetch.fcgi',
        JSON_OBJECT('db', 'pubmed', 'retmode', 'xml'), 'application/xml', 0);

-- Crossref: works 搜索端点（cursor-based）
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from,
 endpoint_usage, http_method, path_template, request_content_type, is_auth_required)
VALUES (@crossref_id, 'TASK', 'harvest', 'works', '2025-01-01', 'SEARCH', 'GET', '/works',
        'application/json', 0);

-- 3) 时间窗口与增量指针
-- PubMed update：按天滑动 + 1 天重叠；DATE 指针默认 EDAT
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from,
 window_mode, window_size_value, window_size_unit, overlap_value, overlap_unit,
 offset_type, default_date_field_name)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01',
        'SLIDING', 1, 'DAY', 1, 'DAY',
        'DATE', 'EDAT');

-- Crossref harvest：按天滑动；DATE 指针使用 indexed
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from,
 window_mode, window_size_value, window_size_unit,
 offset_type, default_date_field_name)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01',
        'SLIDING', 1, 'DAY',
        'DATE', 'indexed');

-- 4) 分页/游标
-- PubMed：页码分页
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from,
 pagination_mode, page_size_value, page_number_param_name, page_size_param_name, start_page_number)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01',
        'PAGE_NUMBER', 100, 'page', 'retmax', 1);

-- Crossref：cursor 分页，下一游标在 $.message.next-cursor
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from,
 pagination_mode, cursor_param_name, next_cursor_jsonpath)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01',
        'CURSOR', 'cursor', '$.message["next-cursor"]');

-- 5) HTTP 策略（示例：设置 UA / 超时）
INSERT INTO reg_prov_http_cfg
(provenance_id, scope, task_type, effective_from,
 default_headers_json, timeout_connect_millis, timeout_read_millis)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01',
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0', 'From', 'ops@example.com'), 2000, 10000),
       (@crossref_id, 'SOURCE', NULL, '2025-01-01',
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0', 'mailto', 'ops@example.com'), 2000, 12000);
```

## 2. 表清单与关系概览

### 2.1 表清单（逐表一句话定位）

* **`reg_provenance`**：来源主数据（Provenance Registry），登记外部数据源（如 PubMed、Crossref）的基础信息（编码、名称、默认
  BaseURL、默认时区、启用状态等）。
* **`reg_prov_endpoint_def`**：端点定义（Endpoint Definition），描述某来源在某时间区间内可用的**搜索/详情/令牌**
  等端点形态与默认参数（HTTP 方法、路径模板、默认 query/body、是否鉴权、端点级分页参数名等）。
* **`reg_prov_window_offset_cfg`**：时间窗口与增量指针配置（Window &
  Offset），定义窗口切分模式（滑动/日历对齐）、窗口长度/重叠/回看、水位滞后，以及增量指针类型与默认日期字段。
* **`reg_prov_pagination_cfg`**：分页与游标配置（Pagination），定义页码/游标/令牌式分页策略及响应中下一游标的提取方式。
* **`reg_prov_http_cfg`**：HTTP 策略（HTTP Policy），定义基础 URL 覆盖、默认请求头、超时、TLS、代理、`Retry-After` 与幂等等策略。
* **`reg_prov_batching_cfg`**：批量抓取与请求成型（Batching & Request Shaping），控制详情批量、ID 参数名/分隔符、并行度/背压、请求模板与压缩策略。
* **`reg_prov_retry_cfg`**：重试与退避（Retry & Backoff），定义最大重试、退避策略、状态码白/黑名单、断路器阈值与冷却时间。
* **`reg_prov_rate_limit_cfg`**：限流与并发（Rate Limit & Concurrency），定义 QPS、突发桶、并发上限、桶粒度（全局/按密钥/按端点）、平滑窗口、自适应服务端速率头。
* **`reg_prov_credential`**：鉴权/密钥（Credentials/Authentication），定义多把凭证（API Key、Bearer、Basic、OAuth2 等），可**可选
  **绑定到某个端点定义，支持区间重叠以实现平滑轮换。

> 注：除凭证外，所有 `reg_prov_*` 配置/定义表均支持**作用域** `scope={SOURCE|TASK}` 与**任务**
`task_type={harvest|update|backfill}`；生成列 `task_type_key = IFNULL(task_type,'ALL')` 用于唯一索引与查询过滤。
> 约定：**不使用触发器/CHECK**；区间不重叠、作用域-任务互斥等规则由**应用层保证**。

### 2.2 关系图（ER 概览）

```mermaid
erDiagram
    reg_provenance ||--o{ reg_prov_endpoint_def: "provenance_id"
    reg_provenance ||--o{ reg_prov_window_offset_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_pagination_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_http_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_batching_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_retry_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_rate_limit_cfg: "provenance_id"
    reg_provenance ||--o{ reg_prov_credential: "provenance_id"
    reg_prov_endpoint_def ||--o{ reg_prov_credential: "endpoint_id(Optional)"
```

* **强外键**

    * 所有 `reg_prov_*` 表通过 `provenance_id → reg_provenance.id` 强关联。
    * `reg_prov_credential.endpoint_id → reg_prov_endpoint_def.id` 为**可选外键**（即此凭证仅用于某个端点）。
* **弱组合（运行时装配）**

    * 端点定义 + HTTP 策略 + 分页/游标 + 批量成型 + 重试退避 + 限流并发 + 凭证，共同构成一次“采集运行合同”。
    * 组合时，**每个维度只取一条“当前生效记录”**（优先 TASK → 回退 SOURCE）。

### 2.3 维度、作用域与时间区间的组合规则

* **作用域选择**：当同一维度同时存在 `TASK` 与 `SOURCE` 两种配置，运行时**优先 TASK**（严格匹配 `task_type`），否则回退
  `SOURCE`。
* **时间区间**：采用 `[effective_from, effective_to)`；`effective_to` 可为 NULL 表示“未设结束时间”。同维度不重叠由**应用层
  **保证。
* **端点局部覆盖**：端点定义提供 `page_param_name / page_size_param_name / cursor_param_name / ids_param_name` 等**端点级覆盖
  **，只对该端点请求生效。

### 2.4 “当前生效配置”的装配流程（读侧）

**输入**：`provenance_code`、`task_type`（可选）、`endpoint_name`（可选）。
**输出**：每个维度一条“当前生效”记录。

伪 SQL 模板（以“分页维度”为例，其它维度仅换表名）：

```sql
-- 获取 provenance_id
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;

-- 统一时间戳（UTC）
SET @now = UTC_TIMESTAMP();

-- 优先 TASK 级（严格匹配 task_type）
(SELECT *
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND scope = 'TASK'
   AND task_type = :taskType
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
-- 回退 SOURCE 级
(SELECT *
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND scope = 'SOURCE'
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

> 建议：应用层封装“取一维度当前生效”的通用方法，避免重复 SQL。

---

### 2.5 发布与“仅一条当前生效”的应用侧护栏

不在 DB 端做区间重叠的强校验；发布流建议：

- 幂等键：每次“发布版本”生成 `publish_idempotency_key`，以免重复提交。
- 锁粒度：按维度获取行级锁（例如“当前维度最近一条”`SELECT ... FOR UPDATE`），再插入新记录；避免并发写入冲突。
- 乐观锁：使用 `version` 字段做乐观锁更新；冲突时重试。
- 重叠检测：应用侧在提交前做“与现有区间的轻量冲突检查”，只提示告警，不阻塞（允许有意轮换的重叠）。

读取侧一律依据 `effective_from/to` 与 `ORDER BY effective_from DESC, id DESC LIMIT 1` 选择当前生效，不受区间重叠影响。

`(provenance_id, scope, task_type_key, [endpoint_name], effective_from)`。

#### 2.5.A 伪代码（Java，应用层，无触发器）

```java
// 目标：发布一条“新区间配置”并确保读侧可预测、写侧无死锁/无触发器

class Dim {
    Long provenanceId;
    String scope;           // "TASK" | "SOURCE"
    String taskType;        // may be null when scope == SOURCE
    Long endpointId;        // optional for credential binding
}

interface PublishLogRepository {
    boolean existsByKey(String idempotencyKey);

    void save(String idempotencyKey, String table, Dim dim, Long newId);
}

interface GenericConfigRepository<T> {
    // 锁住该维度最近一条（FOR UPDATE），避免并发写冲突
    Optional<T> selectLatestForUpdate(String table, Dim dim);

    // 轻量重叠检查（仅提示，不强挡写）
    boolean existsOverlap(String table, Dim dim, Instant from, Instant toNullable);

    Long insert(String table, T record);
}

class PublishService {
    private final PublishLogRepository publishLogRepo;
    private final GenericConfigRepository<Object> repo;

    PublishService(PublishLogRepository publishLogRepo, GenericConfigRepository<Object> repo) {
        this.publishLogRepo = publishLogRepo;
        this.repo = repo;
    }

    // @Transactional
    public void publishConfig(String table, Dim dim, Object newRecord, String idempotencyKey) {
        if (publishLogRepo.existsByKey(idempotencyKey)) {
            return; // 幂等：重复提交直接返回
        }

        // tx.begin();
        Instant now = Instant.now();

        // 1) 锁维度最近一条，避免并发写入（行级锁）
        repo.selectLatestForUpdate(table, dim);

        // 2) 重叠检查（仅日志告警，不阻塞）
        // 假定 newRecord 有 getEffectiveFrom()/getEffectiveTo()
        Instant from = (Instant) invoke(newRecord, "getEffectiveFrom");
        Instant to = (Instant) invoke(newRecord, "getEffectiveTo");
        if (repo.existsOverlap(table, dim, from, to)) {
            // log.warn("interval overlapped; allowed for rotation/gray release");
        }

        // 3) 插入新记录（不修改旧记录的起点；如需回收，另行设置旧记录 effective_to）
        set(newRecord, "lifecycleStatus", "PUBLISHED");
        set(newRecord, "deleted", 0);
        set(newRecord, "createdAt", now);
        set(newRecord, "updatedAt", now);
        set(newRecord, "version", 0);
        Long newId = repo.insert(table, newRecord);

        publishLogRepo.save(idempotencyKey, table, dim, newId);
        // tx.commit();
    }
}

// 读取单维度当前生效（TASK 优先，SOURCE 回退）。返回 0/1 行
class ActiveSelector {
    /**
     * SQL 形态（两段 UNION ALL 取 Top1）：
     * 1) scope='TASK' and task_type=?
     * 2) scope='SOURCE'
     * where lifecycle_status='PUBLISHED' and deleted=0
     *   and effective_from<=now and (effective_to is null or effective_to>now)
     * order by effective_from desc, id desc limit 1
     */
    Optional<Object> selectActive(String table, long pid, String taskType, Instant now) {
        // 伪代码：用查询构造器/Mapper 实现上述 SQL
        return Optional.empty();
    }
}

// 凭证选择：端点绑定优先 → 默认标记 → 最新（按 effective_from desc, id desc）
class CredentialSelector {
    List<Object> listActiveCredentials(long pid, String taskType, Long endpointId, Instant now, int topN) {
        List<Object> bound = queryCredentials(pid, taskType, endpointId, now);
        List<Object> global = queryCredentials(pid, taskType, null, now);
        List<Object> merged = new ArrayList<>();
        merged.addAll(bound);
        merged.addAll(global);
        // 排序：is_default_preferred desc, effective_from desc, id desc
        merged.sort((a, b) -> compare(a, b));
        return merged.stream().limit(topN).toList();
    }

    private List<Object> queryCredentials(long pid, String taskType, Long endpointId, Instant now) {
        // 伪实现：SELECT * FROM reg_prov_credential WHERE ...
        return List.of();
    }
}

// 区间重叠检查（应用层；仅提示，不依赖触发器/CHECK）
class OverlapChecker {
    boolean overlapsWithExisting(String table, Dim dim, Instant from, Instant toNullable) {
        // exists (
        //  select 1 from {table}
        //   where provenance_id=? and scope=? and task_type_key=COALESCE(?, 'ALL')
        //     and NOT( (effective_to is not null and effective_to <= :from)
        //              or (:toNullable is not null and :toNullable <= effective_from) )
        //  limit 1
        // )
        return false;
    }
}
```

---

## 3. 表结构详解（字段、索引、外键、使用建议）

> 说明：以下与已发布的建表 SQL 一一对应；不含触发器/CHECK/审计字段。
> 公共字段模式（除凭证）：`provenance_id` + `scope` + `task_type` + `effective_from/to` + 生成列 `task_type_key` + 维度唯一索引

### 3.1 `reg_provenance` — 来源主数据

**定位**：登记外部数据源（Provenance），作为所有配置的外键根。
**关键字段**

* `id`：主键。
* `provenance_code`（唯一）：来源编码（如 `pubmed` / `crossref`），用于配置引用与查找。
* `provenance_name`：人类可读名称。
* `base_url_default`：默认基础 URL（例如 PubMed 的 `https://eutils.ncbi.nlm.nih.gov/entrez`），可被 HTTP 策略覆盖。
* `timezone_default`：默认时区（IANA，如 `UTC`）。
* `docs_url`：官方文档链接（便于排障）。
* `is_active`：是否启用。

**索引/唯一**

* `uk_reg_provenance_code (provenance_code)`：保证编码唯一。

**使用建议**

* 写入前先检查是否存在相同 `provenance_code`。
* **读侧**以 `provenance_code` 换取 `id`，作为其它维度的查询入参。

---

### 3.2 `reg_prov_endpoint_def` — 端点定义

**定位**：定义搜索/详情/令牌等端点，附带默认 query/body、HTTP 方法、是否鉴权；提供端点级分页参数覆盖。
**关键字段**

* `provenance_id`：外键 → `reg_provenance.id`。
* `scope` / `task_type` / `task_type_key`：作用域与任务。
* `endpoint_name`：端点逻辑名（如 `esearch`、`efetch`、`works`、`token`）。
* `effective_from/to`：生效区间。
* `endpoint_usage`：用途枚举（`SEARCH|FETCH|TOKEN|...`）。
* `http_method`：`GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS`。
* `path_template`：相对（推荐）或绝对路径。
* `default_query_params` / `default_body_payload`（JSON）：默认 query 与 body（应用层合并）。
* `request_content_type`：请求体类型（JSON/Form 等）。
* `is_auth_required`：是否需要鉴权。
* `credential_hint_name`：偏好的凭证标签（与 `reg_prov_credential.credential_name` 协作）。
* 覆盖参数名：`page_param_name | page_size_param_name | cursor_param_name | ids_param_name`。

**索引/唯一**

* **维度唯一** `uk_reg_prov_endpoint_def__dim_from (provenance_id, scope, task_type_key, endpoint_name, effective_from)`
* 辅助索引：`idx_reg_prov_endpoint_def__dim_to (...)`、`idx_reg_prov_endpoint_def__usage (endpoint_usage)`

**使用建议**

* 搜索请求：取 `endpoint_usage='SEARCH'` 的当前生效端点；详情请求：取 `FETCH`。
* 覆盖分页参数名：若设置则在该端点**优先生效**，否则落回 `reg_prov_pagination_cfg`。

---

### 3.3 `reg_prov_window_offset_cfg` — 时间窗口与增量指针

**定位**：切窗策略（滑动/日历对齐）、窗口长度/重叠/回看、水位滞后；增量指针（DATE/ID/COMPOSITE）。
**关键字段**

* 窗口：`window_mode (SLIDING|CALENDAR)`、`window_size_value + window_size_unit`、`calendar_align_to`、
  `lookback_value/unit`、`overlap_value/unit`、`watermark_lag_seconds`。
* 指针：`offset_type (DATE|ID|COMPOSITE)`、`offset_field_name`、`offset_date_format`、`default_date_field_name`。
* 限制：`max_ids_per_window`、`max_window_span_seconds`（防止超大窗口）。

**索引/唯一**

* `uk_reg_prov_window_offset_cfg__dim_from (provenance_id, scope, task_type_key, effective_from)`
* `idx_reg_prov_window_offset_cfg__dim_to (...)`

**使用建议**

* **PubMed Update**：常用 `SLIDING` + `window_size=1 DAY` + `overlap=1 DAY` + `default_date_field_name='EDAT'`。
* **Crossref Harvest**：常用 `SLIDING` + `default_date_field_name='indexed'`。
* `watermark_lag_seconds` 用于乱序/迟到处理，配合应用内的 Watermark 逻辑。

---

### 3.4 `reg_prov_pagination_cfg` — 分页与游标

**定位**：页码（`PAGE_NUMBER`）、游标（`CURSOR`）、令牌（`TOKEN`）、滚动（`SCROLL`）的参数与响应提取规则。
**关键字段**

* 基本：`pagination_mode`、`page_size_value`、`max_pages_per_execution`、`page_number_param_name`、`page_size_param_name`、
  `start_page_number`、`sort_field_param_name`、`sort_direction`。
* 游标/令牌：`cursor_param_name`、`initial_cursor_value`、`next_cursor_jsonpath`、`has_more_jsonpath`、
  `total_count_jsonpath`。

**索引/唯一**

* `uk_reg_prov_pagination_cfg__dim_from (...)`
* `idx_reg_prov_pagination_cfg__dim_to (...)`

**使用建议**

* **PubMed**：多用 `PAGE_NUMBER`，`page_number_param_name='page'`，`page_size_param_name='retmax'`。
* **Crossref**：多用 `CURSOR`，`cursor_param_name='cursor'`，`next_cursor_jsonpath='$.message["next-cursor"]'`。
* 若端点定义里设置了 `page_param_name` 等，则优先使用端点级覆盖。

---

### 3.5 `reg_prov_http_cfg` — HTTP 策略

**定位**：来源/TASK 级 HTTP 行为（BaseURL 覆盖、默认 Header、超时、TLS、代理、`Retry-After`、幂等等）。
**关键字段**

* `base_url_override`：覆盖 `reg_provenance.base_url_default`。
* `default_headers_json`：常见 UA/邮箱/`mailto` 等。
* 超时：`timeout_connect_millis`、`timeout_read_millis`、`timeout_total_millis`。
* TLS/代理：`tls_verify_enabled`、`proxy_url_value`、`prefer_http2_enabled`、`accept_compress_enabled`。
* `Retry-After`：`retry_after_policy (IGNORE|RESPECT|CLAMP)` + `retry_after_cap_millis`。
* 幂等：`idempotency_header_name` + `idempotency_ttl_seconds`。

**索引/唯一**

* `uk_reg_prov_http_cfg__dim_from (...)`
* `idx_reg_prov_http_cfg__dim_to (...)`

**使用建议**

* **Crossref** 官方建议加 `User-Agent` 与 `mailto`，可放入 `default_headers_json`。
* 若存在多个 BaseURL（如地域线路），可用区间切换实现灰度/回滚。

---

### 3.6 `reg_prov_batching_cfg` — 批量抓取与请求成型

**定位**：详情批量、ID 参数名/分隔符、并行/背压、请求模板与压缩。
**关键字段**

* 批量：`detail_fetch_batch_size`、`ids_param_name`、`ids_join_delimiter`、`max_ids_per_request`。
* 并发：`app_parallelism_degree`、`per_host_concurrency_limit`、`http_conn_pool_size`。
* 背压：`backpressure_strategy (BLOCK|DROP|YIELD)`。
* 成型：`request_template_json`（自定义 query/body 映射）。
* 压缩：`payload_compress_strategy (NONE|GZIP)`、`prefer_compact_payload`。

**索引/唯一**

* `uk_reg_prov_batching_cfg__dim_from (...)`
* `idx_reg_prov_batching_cfg__dim_to (...)`

**使用建议**

* 与 `reg_prov_endpoint_def.ids_param_name` 组合：端点覆盖优先生效。
* `max_ids_per_request` 需小于服务端限制；以免 414/413。
* 并发与限流需协同 `reg_prov_rate_limit_cfg`。

---

### 3.7 `reg_prov_retry_cfg` — 重试与退避

**定位**：网络/服务端异常的重试与退避规则，支持断路器。
**关键字段**

* 次数与退避：`max_retry_times`、`backoff_policy_type (FIXED|EXP|EXP_JITTER|DECOR_JITTER)`、`initial_delay_millis`、
  `max_delay_millis`、`exp_multiplier_value`、`jitter_factor_ratio`。
* 状态码：`retry_http_status_json`（可重试）与 `giveup_http_status_json`（不重试）。
* 网络错误：`retry_on_network_error`。
* 断路器：`circuit_break_threshold`、`circuit_cooldown_millis`。

**索引/唯一**

* `uk_reg_prov_retry_cfg__dim_from (...)`
* `idx_reg_prov_retry_cfg__dim_to (...)`

**使用建议**

* **429/503** 常设为可重试；**400/401** 通常不重试。
* **带抖动的指数退避**（`EXP_JITTER`）更稳健，减少惊群。
* 与 `HTTP.retry_after_policy` 联动：若服务端返回 `Retry-After`，可选择尊重或限幅。

---

### 3.8 `reg_prov_rate_limit_cfg` — 限流与并发

**定位**：客户端侧的令牌桶/QPS、突发、并发上限与桶粒度设置。
**关键字段**

* 速率与突发：`rate_tokens_per_second`、`burst_bucket_capacity`。
* 并发：`max_concurrent_requests`。
* 粒度：`bucket_granularity_scope (GLOBAL|PER_KEY|PER_ENDPOINT)`。
* 自适应：`respect_server_rate_header`（遵循服务端 `X-RateLimit-*`）。
* 平滑：`smoothing_window_millis`。

**索引/唯一**

* `uk_reg_prov_rate_limit_cfg__dim_from (...)`
* `idx_reg_prov_rate_limit_cfg__dim_to (...)`

**使用建议**

* 若使用多把密钥，可设 `bucket_granularity_scope='PER_KEY'`，并配合 `per_credential_qps_limit`（见字段
  `per_credential_qps_limit`）。
* 与 `reg_prov_batching_cfg.app_parallelism_degree` 协同，避免客户端拥塞。

> 注：本表字段名在 SQL 中为
> `rate_tokens_per_second` / `burst_bucket_capacity` / `max_concurrent_requests` /
> `per_credential_qps_limit` / `bucket_granularity_scope` /
> `smoothing_window_millis` / `respect_server_rate_header`。

---

### 3.9 `reg_prov_credential` — 鉴权/密钥

**定位**：为来源/TASK 配置多把凭证（API Key、Bearer、Basic、OAuth2…），**可选**绑定端点，允许区间重叠以支持轮换。
**关键字段**

* 归属：`provenance_id`（外键） + `scope/task_type`。
* **端点绑定**：`endpoint_id`（可空；外键 → `reg_prov_endpoint_def.id`），用于“某凭证只给某个端点使用”。
* 基本信息：`credential_name`（标签）、`auth_type`、`inbound_location (HEADER|QUERY|BODY)`。
* API Key/Bearer：`credential_field_name`、`credential_value_prefix`（如 `"Bearer "`）、`credential_value_plain`（建议存引用）。
* Basic：`basic_username`、`basic_password`。
* OAuth2：`oauth_token_url`、`oauth_client_id`、`oauth_client_secret`、`oauth_scope`、`oauth_audience`。
* 扩展：`extra_json`（签名算法、HMAC 盐、额外 Header 模板等）。
* 生效区间：`effective_from/effective_to`（允许重叠）；优先策略由应用侧决定（可参考 `is_default_preferred`）。

**索引/外键**

* 外键：`provenance_id → reg_provenance.id`；`endpoint_id → reg_prov_endpoint_def.id`（可空）。
* 索引：`idx_reg_prov_credential__dim (provenance_id, scope, task_type, endpoint_id, credential_name)`、
  `idx_reg_prov_credential__effective (effective_from, effective_to)`。

**使用建议**

* **轮换**：先插入新凭证（`effective_from=当前`），将旧凭证 `effective_to=当前+重叠窗口`，待观测稳定后再关闭旧凭证。
* **端点绑定**：若某端点需要单独的密钥或更高配额，可填 `endpoint_id` 指向对应端点定义。
* **安全**：`credential_value_plain` 建议存**引用**（如 KMS 的 key id），真实密钥由应用从安全存储获取。

---

### 3.10 查询与索引使用建议（通用）

* **过滤顺序**：`provenance_id` → `scope`/`task_type`（或 `task_type_key`）→ `effective_from <= now < effective_to` →
  `ORDER BY effective_from DESC, id DESC LIMIT 1`。
* **复合索引命中**：已在各表建立 `(provenance_id, scope, task_type_key, ..., effective_from)` 复合唯一索引，结合
  `effective_to` 辅助索引，能高效支撑“当前生效记录”的检索。
* **缓存建议**：读侧可将“（provenance\_code, task\_type）→ 维度配置聚合体”进行短 TTL 缓存；端点级也可按 `endpoint_name`
  做二级缓存。
* **并发写入**：灰度/回滚建议采用“先加新→再关旧”的顺序，并在应用侧做幂等与区间冲突检测。

## 4. 作用域与优先级模型

本章给出**统一的“配置选取与合并”规则**，确保在多来源（Provenance）、多任务（`harvest|update|backfill`）、多时间区间并存的情况下，应用端能
**稳定、可预期**地装配出“当前生效”的采集运行合同（端点 + HTTP + 窗口/指针 + 分页/游标 + 批量 + 重试 + 限流 + 凭证）。

---

### 4.1 作用域模型（Scope Model）

* **字段**（所有 `reg_prov_*_cfg` 与 `reg_prov_endpoint_def` 都具备）

    * `provenance_id`：归属来源（FK → `reg_provenance.id`）
    * `scope ∈ {SOURCE, TASK}`：作用域
    * `task_type ∈ {harvest, update, backfill} | NULL`：当 `scope='TASK'` 时**必须**设置；`scope='SOURCE'` 时建议置 `NULL`
    * `task_type_key`：生成列，`IFNULL(task_type,'ALL')`，仅用于索引与筛选
* **选择意图**

    * **SOURCE 级**：来源通用策略（默认）
    * **TASK 级**：来源+任务的特化策略（覆盖 SOURCE 级，但**不是字段级合并**，见 §4.3）

> 约束不在数据库层执行；由应用层保证：
>
> * `scope='TASK'` ⇒ `task_type IS NOT NULL`
> * `scope='SOURCE'` ⇒ `task_type IS NULL`（推荐）

---

### 4.2 时间区间模型（Effective Interval）

* **字段**：`effective_from TIMESTAMP(6)`、`effective_to TIMESTAMP(6) NULL`
* **语义**：区间采用 **半开区间** `[effective_from, effective_to)`；`effective_to IS NULL` 表示持续有效
* **不重叠**：数据库**不做**触发器/CHECK；由应用在写入与发布流程中保证
* **冲突检测 SQL（参考）**：同维度新增前可做一次交集检查

  ```sql
  -- :pid, :scope, :task_type_key, :from, :to
  SELECT 1
  FROM reg_prov_pagination_cfg
  WHERE provenance_id = :pid
    AND scope = :scope
    AND task_type_key = :task_type_key
    AND NOT(  -- 与新窗口无交集的条件取反
      (effective_to   IS NOT NULL AND effective_to   <= :from)
      OR
      (:to IS NOT NULL AND :to <= effective_from)
    )
  LIMIT 1;
  -- 若返回行存在，则视为“与既有区间重叠”，应阻止写入或提醒灰度策略
  ```

---

### 4.3 维度优先级与决策树（Deterministic Selection）

**目标**：对于每个维度（端点定义/窗口/分页/HTTP/批量/重试/限流/凭证），**恰好选出 1 条“当前生效记录”**。

#### 4.3.1 基本决策树（除凭证外）

以“分页维度”为例，其他维度同理（仅换表名）：

1. **锁定来源**：`provenance_id` = 通过 `reg_provenance.provenance_code` 查得的 `id`
2. **时间过滤**：`effective_from <= now < COALESCE(effective_to, +∞)`
3. **任务优先**：

#### 4.3.3 字段合并与优先级总则

总则：除“端点默认 Query/Body”与“HTTP Headers”在应用层进行键级合并外，其他字段不做字段级覆盖。

- 端点分页参数名：`endpoint_def.*_param_name` > `pagination_cfg.*` > 应用默认。
- BaseURL：`http_cfg.base_url_override` > `provenance.base_url_default` > 应用默认。
- Headers 合并顺序：`http_cfg.default_headers_json` 为基底，运行时请求头覆盖同名键，发送前删除值为 NULL 的键。
- 时间窗口/指针：TASK 级命中优先，其次 SOURCE 级；仅取单条当前生效记录。
- 凭证选择链：端点绑定优先，其次全局；TASK 优先，其次 SOURCE；时间过滤；默认标记优先；按 `effective_from DESC, id DESC`
  决定并列；如需多把，取前 N 把供应用轮询/加权。
- NULL 语义：字段为 NULL 表示“由应用使用默认值”。

    * 若传入了 `task_type`：先在 `scope='TASK' AND task_type=:task_type` 中选；若没有结果，再回退 `scope='SOURCE'`
    * 若未传入 `task_type`：直接走 `scope='SOURCE'`

4. **最新原则**：在候选集中按 `effective_from DESC` 取 **第一条**
5. **并列兜底**：若仍并列（理论上不会出现），可再按 `id DESC` 兜底

> **SQL 模板（分页维度）**

```sql
-- 输入：:code, :taskType 可空
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;
SET @now = UTC_TIMESTAMP();

(SELECT *
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND scope = 'TASK'
   AND task_type = :taskType
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND scope = 'SOURCE'
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

#### 4.3.2 端点定义的选择（`reg_prov_endpoint_def`）

在以上 1\~5 的基础上，多了端点维度过滤：

* **按用途**：`endpoint_usage IN ('SEARCH','FETCH','TOKEN',...)`
* **按名称**：如明确指定 `endpoint_name`，则在同一用途下**精确匹配**
* **端点级覆盖**：若选出的端点记录含 `page_param_name`/`cursor_param_name` 等，则这些**仅对该端点请求**优先生效（见 §4.4）

> **PubMed 例**：
>
> * `endpoint_usage='SEARCH'` ⇒ 选出 `esearch` 当前生效记录
> * `endpoint_usage='FETCH'` ⇒ 选出 `efetch` 当前生效记录

---

### 4.4 字段覆盖与合并顺序（仅端点级覆盖，非全局字段合并）

本设计**不做链式覆盖**，即**不会把 SOURCE 与 TASK 两条记录做字段级 merge**。唯一的覆盖例外发生在“端点 → 分页/批量参数名”的
**局部覆盖**：

**覆盖/回退顺序（参数名类）**

```
端点级参数名（endpoint_def.*_param_name）         >   维度配置（pagination_cfg/batching_cfg）   >   应用默认
```

**BaseURL 的覆盖**

```
reg_prov_http_cfg.base_url_override     >     reg_provenance.base_url_default     >    应用默认
```

**Headers 合并（建议策略）**

* 以 `reg_prov_http_cfg.default_headers_json` 作为**基底**
* 运行时请求头**覆盖**同名键
* 最终发送前**删除值为 NULL** 的键（表示显式清除）

**默认 Query/Body 合并（端点）**

* 以 `reg_prov_endpoint_def.default_query_params / default_body_payload` 为**基底**
* 运行时参数**覆盖**同名键
* 最终发送前**删除值为 NULL** 的键

> 合并细节在应用端实现；数据库只存储“基底/覆盖”所需字段。

---

### 4.5 凭证选择优先级（`reg_prov_credential`）

凭证与其他维度不同：凭证可能**并存多把有效**，用于轮换/分流。建议优先级如下：

1. **端点绑定优先**：若当前请求明确绑定到某端点（或端点用途），则优先筛选 `endpoint_id = 该端点.id` 的凭证；若无绑定，再用
   `endpoint_id IS NULL` 的全局凭证
2. **作用域优先**：任务内优先 `scope='TASK' AND task_type=:taskType`，否则 `scope='SOURCE'`
3. **时间过滤**：`effective_from <= now < COALESCE(effective_to,+∞)`
4. **默认标记**：优先 `is_default_preferred = 1`
5. **最新原则**：同一优先级并列时，按 `effective_from DESC, id DESC` 取第一把
6. **多把策略**（可选）：若需要并行多把，按上述规则得出**有序清单**，由应用以**轮询/加权/健康检查**进行使用与熔断

> **SQL（取“单把首选凭证”）**

```sql
-- 输入：@pid, @endpoint_id 可空, @taskType 可空
SET @now = UTC_TIMESTAMP();

-- 候选（端点优先）
(SELECT *
 FROM reg_prov_credential
 WHERE provenance_id = @pid
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND (@endpoint_id IS NOT NULL AND endpoint_id = @endpoint_id)
   AND ((scope = 'TASK' AND task_type = @taskType) OR (scope = 'SOURCE' AND @taskType IS NULL) OR (scope = 'SOURCE'))
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_credential
 WHERE provenance_id = @pid
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND endpoint_id IS NULL
   AND ((scope = 'TASK' AND task_type = @taskType) OR (scope = 'SOURCE' AND @taskType IS NULL) OR (scope = 'SOURCE'))
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

> **多把负载均衡**：把 `LIMIT 1` 换成 `LIMIT N`，按排序获取前 N 把供应用轮询或加权；并结合健康探测做熔断。

---

### 4.6 多维度聚合：装配“运行合同”（Execution Contract）

**目标**：一次请求/一次任务运行需要把多个维度拼成“合同”。推荐在应用端做**多子查询 + 组合**，每个子查询确保只返回 0/1 行，然后
**CROSS JOIN** 到一起（MySQL 用子查询 + CROSS JOIN 即可）。

> **SQL 模板（以“PubMed / update / SEARCH（检索）请求”为例）**

```sql
-- 入参
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = 'pubmed';
SET @now = UTC_TIMESTAMP();

-- 各维度单值子查询
-- 端点（SEARCH）
WITH ep AS ((SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND endpoint_usage = 'SEARCH'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND endpoint_usage = 'SEARCH'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     pg AS (
         -- 分页
         (SELECT *
          FROM reg_prov_pagination_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_pagination_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     http AS (
         -- HTTP
         (SELECT *
          FROM reg_prov_http_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_http_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     win AS (
         -- 窗口/指针
         (SELECT *
          FROM reg_prov_window_offset_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_window_offset_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     bt AS (
         -- 批量
         (SELECT *
          FROM reg_prov_batching_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_batching_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     rt AS (
         -- 重试退避
         (SELECT *
          FROM reg_prov_retry_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_retry_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     rl AS (
         -- 限流并发
         (SELECT *
          FROM reg_prov_rate_limit_cfg
          WHERE provenance_id = @pid
            AND scope = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_rate_limit_cfg
          WHERE provenance_id = @pid
            AND scope = 'SOURCE'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1),
     cred AS (
         -- 凭证（端点绑定优先，其次全局）
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
              AND lifecycle_status='PUBLISHED' AND deleted=0
            AND endpoint_id = (SELECT id FROM ep LIMIT 1)
              AND effective_from <= @now
              AND (effective_to IS NULL OR effective_to > @now)
            AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
            ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
              AND lifecycle_status='PUBLISHED' AND deleted=0
            AND endpoint_id IS NULL
              AND effective_from <= @now
              AND (effective_to IS NULL OR effective_to > @now)
            AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
            ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1)

-- 最终聚合（每个CTE仅 0/1 行），应用侧把各JSON字段再做合并
SELECT *
FROM ep,
     pg,
     http,
     win,
     bt,
     rt,
     rl,
     cred;
```

> **Crossref（harvest + cursor）** 仅需把任务与用途替换为 `task_type='harvest'`、`endpoint_usage='SEARCH'`，分页维度取
`pagination_mode='CURSOR'` 的当前生效记录即可。

---

### 4.7 灰度切换与回滚（运营流程）

**目标**：零停机切换配置，快速回滚。

**推荐步骤**

1. **新增**：插入一条新记录 `effective_from = T+Δ`（未来时刻）；**不改旧记录**
2. **观测**：等到 `T+Δ` 后流量自然命中新的“当前生效”记录
3. **关闭旧记录**：把旧记录的 `effective_to` 写为 `T+Δ + 观察期`（有重叠，保障在途任务）
4. **清理**：观察期结束后，若稳定，再把旧记录 `effective_to` 收紧到 `T+Δ` 或归档
5. **回滚**：若新记录出现问题，只需把其 `effective_to=当前`；或把旧记录 `effective_to=NULL` 重新“开窗”

**注意事项**

* 所有时间戳统一用 UTC（数据库即存 UTC）
* 在途任务读取配置应“**一次运行内冻结**”（避免同一运行跨越切换时刻导致参数跳变）

---

### 4.8 异常与兜底策略

| 场景                     | 表现/风险         | 兜底建议                                                         |
|------------------------|---------------|--------------------------------------------------------------|
| **缺少 TASK 级配置**        | 回退到了 SOURCE 级 | 允许；在告警与审计中记录“回退事件”                                           |
| **同一维度在同一时刻并存多条**      | 选取歧义          | 写入前做交集检查；运行时若确实并存，按 `effective_from DESC, id DESC` 选择并**报警** |
| **某维度完全缺失**            | 合同不完整         | 应用侧使用**默认值**；或禁用该任务并报警                                       |
| **端点未定义**              | 无法发出请求        | 返回配置错误；由运维补齐 `reg_prov_endpoint_def`                         |
| **凭证过期**               | 401/403 或配额归零 | 尝试下一把候选凭证；若全部不可用，降级为匿名（若允许）或暂停任务                             |
| **时钟漂移**               | 切换时刻不一致       | 统一从 DB 读 `UTC_TIMESTAMP()`；任务启动前刷新配置                         |
| **跨维度冲突**（如分页与端点参数名冲突） | 请求生成错误        | 遵循 §4.4 覆盖顺序；配置评审时静态检查冲突                                     |

---

### 4.9 最佳实践小结（可落地）

* 写侧：**先加新、再关旧**；所有修改先跑**预检 SQL**（交集检查、必填检查、依赖存在性检查）。
* 读侧：统一封装“取当前生效”的**通用函数**，并做**短 TTL 缓存**；一次运行内冻结配置。
* 合并：**只允许端点对少数参数名做覆盖**；其余“按维度整条取一条”，**不要字段级 merge**。
* 凭证：支持**多把候选** + **健康探测** + **熔断**；默认标记 + 最新优先。
* 时区：DB 存 UTC；展示/窗口对齐用 `reg_provenance.timezone_default` 或任务级 HTTP 配置（如需）。

## 5. 读写操作实践（SQL 模板与最佳实践）

### 5.1 写入与变更（新增配置、灰度切换、回滚）

#### 5.1.1 新增来源（Provenance）

> 幂等：`provenance_code` 唯一。存在则取回 `id`；不存在则插入。

```sql
-- 插入或忽略（若已存在）
INSERT IGNORE INTO reg_provenance (provenance_code, provenance_name, base_url_default, timezone_default, docs_url,
                                   is_active)
VALUES (:code, :name, :baseUrl, :tz, :docs, 1);

-- 获取 id
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;
```

**最佳实践**

* 代码侧对 `provenance_code` 统一小写/蛇形命名，避免大小写混淆。
* 各配置写入前，**必须**先解析成 `provenance_id`。

---

#### 5.1.2 新增配置（以分页为例）：写前冲突预检

> 以 `reg_prov_pagination_cfg` 为例；其他 `reg_prov_*` 同理，替换表名即可。
> 目标：同维度（`provenance_id, scope, task_type`）内，**新区间**与既有区间**不得重叠**（业务规则在应用层保证）。

```sql
-- 约束：:from ≤ :to（若 :to 非空）；校验由应用层做。
-- 交集判定：存在返回1表示有冲突。
SELECT 1
FROM reg_prov_pagination_cfg
WHERE provenance_id = @pid
  AND scope = :scope
  AND IFNULL(task_type, 'ALL') = IFNULL(:taskType, 'ALL')
  AND NOT ( -- 与新区间没有交集的条件取反
    (effective_to IS NOT NULL AND effective_to <= :from)
        OR
    (:to IS NOT NULL AND :to <= effective_from)
    )
LIMIT 1;
```

**若存在冲突**：阻止写入，提示用户选择**未来时刻**或**先收口旧区间**（见 5.1.4）。

---

#### 5.1.3 灰度切换（Zero-Downtime）

> 策略：**先加新**（未来 T+Δ 生效），**后关旧**（观察期结束再收口），保证在途任务不受影响。

**步骤 A：插入新配置（未来生效）**

```sql
-- 以分页为例（PAGE_NUMBER 100/页，从1开始）
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 pagination_mode, page_size_value, page_number_param_name, page_size_param_name, start_page_number)
VALUES (@pid, :scope, :taskType, :newFrom, :newTo,
        'PAGE_NUMBER', 100, 'page', 'retmax', 1);
```

**步骤 B：观察切流**

* 到达 `:newFrom` 后，读侧“当前生效”自动命中新配置；监控错误率/耗时/流量。

**步骤 C：收口旧配置（留出重叠观察期）**

```sql
-- 将旧配置的 effective_to 写到 :newFrom + 观察期（如 15 分钟/1 小时）
UPDATE reg_prov_pagination_cfg
SET effective_to = :newFrom + INTERVAL :grace MINUTE
WHERE provenance_id = @pid
  AND scope = :scope
  AND IFNULL(task_type, 'ALL') = IFNULL(:taskType, 'ALL')
  AND effective_from < :newFrom
  AND (effective_to IS NULL OR effective_to > :newFrom)
ORDER BY effective_from DESC, id DESC
LIMIT 1;
```

**注意**

* 写入/修改建议**放在事务**里：先做“冲突预检”→再 `INSERT/UPDATE`。
* 观察期允许旧新短暂重叠，但**不推荐**长期重叠。

---

#### 5.1.4 快速回滚

> 新配置异常时，执行**回滚**：
>
> * 方式 1：**关闭**新配置（把 `effective_to=NOW()`）；
> * 方式 2：**重开**旧配置（把旧配置 `effective_to=NULL` 或移到未来）。

```sql
-- 方式 1：立即关闭新配置
UPDATE reg_prov_pagination_cfg
SET effective_to = UTC_TIMESTAMP()
WHERE id = :newCfgId;

-- 方式 2：重开最近一次旧配置
UPDATE reg_prov_pagination_cfg
SET effective_to = NULL
WHERE id = (SELECT id
            FROM reg_prov_pagination_cfg
            WHERE provenance_id = @pid
              AND scope = :scope
              AND IFNULL(task_type, 'ALL') = IFNULL(:taskType, 'ALL')
              AND effective_from < (SELECT effective_from FROM reg_prov_pagination_cfg WHERE id = :newCfgId)
            ORDER BY effective_from DESC, id DESC
            LIMIT 1);
```

---

### 5.2 幂等写入模板（Upsert/去重）

> 由于表的唯一键为 `(provenance_id, scope, task_type_key, [endpoint_name], effective_from)`，**一般不建议**直接
`ON DUPLICATE KEY` 覆盖 `effective_from`（可能破坏历史）。
> 推荐做**幂等插入**（若相同维度+起点已存在则更新非关键字段）。

**示例：端点定义幂等写**

```sql
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage, http_method, path_template, default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@pid, :scope, :taskType, :endpointName, :from, :to,
        :usage, :method, :path, :defaultQueryJson, :defaultBodyJson,
        :contentType, :needAuth, :credHint,
        :pageParam, :sizeParam, :cursorParam, :idsParam)
ON DUPLICATE KEY UPDATE effective_to         = VALUES(effective_to),
                        endpoint_usage       = VALUES(endpoint_usage),
                        http_method          = VALUES(http_method),
                        path_template        = VALUES(path_template),
                        default_query_params = VALUES(default_query_params),
                        default_body_payload = VALUES(default_body_payload),
                        request_content_type = VALUES(request_content_type),
                        is_auth_required     = VALUES(is_auth_required),
                        credential_hint_name = VALUES(credential_hint_name),
                        page_param_name      = VALUES(page_param_name),
                        page_size_param_name = VALUES(page_size_param_name),
                        cursor_param_name    = VALUES(cursor_param_name),
                        ids_param_name       = VALUES(ids_param_name);
```

**注意**

* 若要**改变生效起点**（`effective_from`），建议**新插入一条记录**，而不是覆盖旧记录的起点。

---

### 5.3 读取“当前生效配置”SQL 模板（单维度）

> 通用查询模式（以 `reg_prov_http_cfg` 为例；其他维度换表名字段即可）。

```sql
-- 输入：:code (provenance_code), :taskType 可空
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;
SET @now = UTC_TIMESTAMP();

(SELECT *
 FROM reg_prov_http_cfg
 WHERE provenance_id = @pid
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND scope = 'TASK'
   AND task_type = :taskType
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_http_cfg
 WHERE provenance_id = @pid
   AND lifecycle_status = 'PUBLISHED'
   AND deleted = 0
   AND scope = 'SOURCE'
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

**端点定义（按用途/名称）**

```sql
-- :usage in ('SEARCH','FETCH','TOKEN','METADATA','PING','RATE')
WITH ep AS ((SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'TASK'
               AND task_type = :taskType
               AND endpoint_usage = :usage
               AND (:endpointName IS NULL OR endpoint_name = :endpointName)
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'SOURCE'
               AND endpoint_usage = :usage
               AND (:endpointName IS NULL OR endpoint_name = :endpointName)
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1)
SELECT *
FROM ep;
```

---

### 5.4 跨维度聚合查询（装配“运行合同”）

> 目标：一次取齐**端点 + HTTP + 窗口/指针 + 分页 + 批量 + 重试 + 限流 + 凭证**，由应用把 JSON 字段做合并并生成请求。

**示例：PubMed / `update` / `SEARCH`**

```sql
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = 'pubmed';
SET @now = UTC_TIMESTAMP();

WITH ep AS ((SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND endpoint_usage = 'SEARCH'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND endpoint_usage = 'SEARCH'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     http AS ((SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND scope = 'TASK'
                 AND task_type = 'update'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              UNION ALL
              (SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND scope = 'SOURCE'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              LIMIT 1),
     win AS ((SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND scope = 'TASK'
                AND task_type = 'update'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             UNION ALL
             (SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND scope = 'SOURCE'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             LIMIT 1),
     pg AS ((SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     bt AS ((SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rt AS ((SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rl AS ((SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     cred AS ((SELECT *
               FROM reg_prov_credential
               WHERE provenance_id = @pid
                 AND lifecycle_status='PUBLISHED' AND deleted=0
                 AND endpoint_id = (SELECT id FROM ep LIMIT 1)
                 AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
               LIMIT 1)
              UNION ALL
              (SELECT *
               FROM reg_prov_credential
               WHERE provenance_id = @pid
                 AND lifecycle_status='PUBLISHED' AND deleted=0
                 AND endpoint_id IS NULL
                 AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
               LIMIT 1)
              LIMIT 1)
SELECT *
FROM ep,
     http,
     win,
     pg,
     bt,
     rt,
     rl,
     cred;
```

**应用端合并要点**

* `base_url`：`http.base_url_override` > `reg_provenance.base_url_default`。
* Header：以 `http.default_headers_json` 为基底，运行时覆盖；NULL 表示删除。
* 端点分页参数名：`endpoint_def.*_param_name` > `pagination_cfg.*` > 应用默认。
* 请求 Query/Body：以 `endpoint_def.default_*` 为基底，运行时覆盖；NULL 删除。

---

### 5.5 端点级覆盖的使用示例

**场景**：大多数端点用 `page=...&retmax=...`，但 `works` 端点要求 `rows=...`。

* 在 `reg_prov_endpoint_def` 为 `works` 端点填 `page_size_param_name='rows'`；
* 读取时优先使用端点级参数名，忽略分页维度中的 `page_size_param_name`。

---

### 5.6 凭证选择：单把 / 多把

**单把（首选）**

* 端点绑定优先 → `is_default_preferred=1` → 最新 `effective_from` → `id` 兜底。
* 参考 §4.5 的 SQL。

**多把（并行/分流）**

* 将 `LIMIT 1` 改为 `LIMIT N`，按排序得到候选列表。
* 应用端做**轮询**或**加权**（结合健康探测与熔断）。

---

### 5.7 诊断与运维 SQL（常用）

**列出某来源在当前时刻“生效”的所有维度**

```sql
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;
SET @now = UTC_TIMESTAMP();

-- 以分页为例；其他维度同理
(SELECT 'pagination' AS cfg, id, scope, task_type, effective_from, effective_to
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY scope = 'TASK' DESC, effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
-- 加上 window/http/batching/retry/rate/endpoint 等同样写法……
SELECT 'window', id, scope, task_type, effective_from, effective_to
FROM reg_prov_window_offset_cfg
WHERE provenance_id = @pid
  AND effective_from <= @now
  AND (effective_to IS NULL OR effective_to > @now)
ORDER BY scope = 'TASK' DESC, effective_from DESC, id DESC
LIMIT 1;
```

**查找重叠区间（自检）**

```sql
-- 任一 reg_prov_* 表通用（替换表名）
SELECT a.id AS id_a, b.id AS id_b
FROM reg_prov_pagination_cfg a
         JOIN reg_prov_pagination_cfg b
              ON a.id < b.id
                  AND a.provenance_id = b.provenance_id
                  AND a.scope = b.scope
                  AND IFNULL(a.task_type, 'ALL') = IFNULL(b.task_type, 'ALL')
                  AND NOT (
                      (a.effective_to IS NOT NULL AND a.effective_to <= b.effective_from)
                          OR
                      (b.effective_to IS NOT NULL AND b.effective_to <= a.effective_from)
                      )
WHERE a.provenance_id = @pid;
```

**列出当前生效的所有端点（按用途）**

```sql
SELECT *
FROM reg_prov_endpoint_def
WHERE provenance_id = @pid
  AND endpoint_usage = :usage
  AND effective_from <= UTC_TIMESTAMP()
  AND (effective_to IS NULL OR effective_to > UTC_TIMESTAMP())
ORDER BY scope = 'TASK' DESC, effective_from DESC, id DESC;
```

---

### 5.8 性能与一致性建议

* **索引命中**：查询条件顺序尽量贴合复合索引顺序（`provenance_id, scope, task_type_key, [endpoint_name], effective_from`
  ），并带上时间过滤。
* **一次运行内冻结**：作业启动时一次性读取并缓存“合同”，运行中不变（避免半途切换）。
* **幂等**：写入接口携带**明确的维度键**与 `effective_from`，避免随机生成时间造成误判。
* **事务**：灰度切换写入建议放在同一事务；多维度切换时先“加新”，观测后逐表“关旧”。
* **归档**：历史记录可定期归档到冷表/历史库；读取仅关注活跃与最近历史。
* **JSON**：只在少量扩展位使用；不要把**关键过滤维度**塞进 JSON。

---

### 5.9 常见错误与应对

| 错误        | 原因                              | 快速修复                                    |
|-----------|---------------------------------|-----------------------------------------|
| 查不到“当前生效” | 区间写反或 `effective_from` 在未来      | 调整区间；或设置临时回退的 SOURCE 级配置                |
| 任务/来源混淆   | `scope='TASK'` 但 `task_type` 为空 | 回填 `task_type`；或改成 `scope='SOURCE'`     |
| 端点参数冲突    | 端点与分页维度同时配置了不同参数名               | 遵循“端点 > 维度”的覆盖；清理不必要的字段                 |
| 频繁 429/限流 | 客户端限流过高或服务端配额不足                 | 下调 `rate_limit`/`parallelism`；或启用多把凭证分流 |
| 游标翻页失败    | `next_cursor_jsonpath` 不正确      | 调整提取路径；打印原始响应以定位                        |

---

## 6. PubMed 示例（端到端）

### 6.1 背景与 API 速览（面向本库的映射）

* **E-utilities** 家族常用端点：

    * `ESearch`：检索 PMID 列表（支持 `term`、日期过滤等），响应包含总数 `count` 与 `idlist`。
    * `EFetch`：按 PMID 列表返回详情（常用 `retmode=xml`、`rettype=abstract`）。
* **日期/增量**：常用 `EDAT/PDAT/MHDA` 等字段；本库将其建模为 `reg_prov_window_offset_cfg.default_date_field_name`。
* **分页**：ESearch 实际为**偏移量**分页（`retstart` + `retmax`）。在我们的模型中用 `PAGE_NUMBER` 表示，应用层将**页号换算为偏移量
  **（`retstart=(page-1)*retmax`）。
* **鉴权**：匿名可用；有 **API Key** 可提高配额。我们示例中用 `reg_prov_credential`（`QUERY` 参数名 `api_key`）。
* **Headers**：建议包含 `User-Agent` 与可联系的邮箱（可配在 HTTP 策略的 JSON 头中）。

---

### 6.2 主数据：登记 PubMed 来源

```sql
-- ① 新增来源（若已存在则忽略）
INSERT IGNORE INTO reg_provenance (provenance_code, provenance_name, base_url_default, timezone_default, docs_url,
                                   is_active)
VALUES ('pubmed', 'PubMed', 'https://eutils.ncbi.nlm.nih.gov/entrez', 'UTC',
        'https://www.ncbi.nlm.nih.gov/books/NBK25501/', 1);

-- ② 取回 provenance_id 以便后续插入
SELECT id
INTO @pubmed_id
FROM reg_provenance
WHERE provenance_code = 'pubmed';
```

---

### 6.3 端点定义（搜索与详情）

```sql
/* 搜索端点：ESearch（返回 PMID 列表） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage, http_method, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@pubmed_id, 'TASK', 'update', 'esearch', '2025-01-01 00:00:00', NULL,
        'SEARCH', 'GET', '/eutils/esearch.fcgi',
           /* 缺省 query：库名=pubmed，返回 json；日期类型默认 EDAT（可被运行时覆盖） */
        JSON_OBJECT('db', 'pubmed', 'retmode', 'json', 'datetype', 'edat'),
        NULL,
        'application/json', 0, NULL,
           /* 端点级分页参数名覆盖（应用层把 page→retstart=(page-1)*retmax） */
        'page', 'retmax', NULL, NULL);

/* 详情端点：EFetch（按 PMID 批量返回详情） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage, http_method, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@pubmed_id, 'TASK', 'update', 'efetch', '2025-01-01 00:00:00', NULL,
        'FETCH', 'GET', '/eutils/efetch.fcgi',
           /* 缺省 query：库名=pubmed，返回 xml；rettype 视业务需要 */
        JSON_OBJECT('db', 'pubmed', 'retmode', 'xml', 'rettype', 'abstract'),
        NULL,
        'application/xml', 0, NULL,
        NULL, NULL, NULL, 'id'); -- 批量 ID 的参数名：id（逗号分隔）
```

> **要点**
>
> * `endpoint_usage='SEARCH'` / `'FETCH'` 用于运行时按用途快速选择端点。
> * `page_param_name='page'`、`page_size_param_name='retmax'` 仅是**应用层参数名**，最终在发送请求时转为
    `retstart=(page-1)*retmax`。
> * `ids_param_name='id'` 表示 EFetch 的 ID 列表放在 `id=1,2,3`。

---

### 6.4 时间窗口与增量指针（按天滑动 + 重叠）

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 window_mode, window_size_value, window_size_unit, calendar_align_to,
 lookback_value, lookback_unit, overlap_value, overlap_unit, watermark_lag_seconds,
 offset_type, offset_field_name, offset_date_format, default_date_field_name,
 max_ids_per_window, max_window_span_seconds)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01 00:00:00', NULL,
        'SLIDING', 1, 'DAY', NULL,
        NULL, NULL, 1, 'DAY', 3600,
        'DATE', NULL, 'YYYY-MM-DD', 'EDAT',
        200000, 172800); -- 单窗口最多20万条 & 最大跨度2天（示例值）
```

> **典型策略**
>
> * **日滑动** + **1 天重叠**：应对迟到。
> * 增量字段默认 `EDAT`（也可按任务切换为 `PDAT/MHDA`）。
> * `watermark_lag_seconds=3600`（1 小时）用于乱序容忍。

---

### 6.5 分页/游标（页码模型 → retstart）

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 pagination_mode, page_size_value, max_pages_per_execution,
 page_number_param_name, page_size_param_name, start_page_number,
 sort_field_param_name, sort_direction,
 cursor_param_name, initial_cursor_value, next_cursor_jsonpath, has_more_jsonpath, total_count_jsonpath)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01 00:00:00', NULL,
        'PAGE_NUMBER', 100, 1000,
        'page', 'retmax', 1,
        NULL, NULL,
        NULL, NULL, NULL, NULL, '$.esearchresult.count');
```

> **约定**：应用层把 `page / retmax` 转换为 `retstart=(page-1)*retmax`。
> `total_count_jsonpath` 便于统计与终止条件决策。

---

### 6.6 HTTP 策略（Headers/超时/Retry-After）

```sql
INSERT INTO reg_prov_http_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 base_url_override, default_headers_json,
 timeout_connect_millis, timeout_read_millis, timeout_total_millis,
 tls_verify_enabled, proxy_url_value,
 accept_compress_enabled, prefer_http2_enabled,
 retry_after_policy, retry_after_cap_millis,
 idempotency_header_name, idempotency_ttl_seconds)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        NULL,
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0 (+ops@example.com)', 'From', 'ops@example.com'),
        2000, 15000, 20000,
        1, NULL,
        1, 0,
        'RESPECT', 60000,
        NULL, NULL);
```

> **建议**：将邮箱放入 UA 或 `From` 头，便于对方联系；遵循 `Retry-After`。

---

### 6.7 批量抓取与请求成型（EFetch 批量）

```sql
INSERT INTO reg_prov_batching_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 detail_fetch_batch_size, ids_param_name, ids_join_delimiter, max_ids_per_request,
 prefer_compact_payload, payload_compress_strategy,
 app_parallelism_degree, per_host_concurrency_limit, http_conn_pool_size,
 backpressure_strategy, request_template_json)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01 00:00:00', NULL,
        200, 'id', ',', 200,
        1, 'NONE',
        8, 8, 64,
        'BLOCK', NULL);
```

> **含义**：一次 EFetch 取 200 个 PMID；最大 200/请求；并行 8；客户端连接池 64。

---

### 6.8 重试与退避（含断路器）

```sql
INSERT INTO reg_prov_retry_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 max_retry_times, backoff_policy_type, initial_delay_millis, max_delay_millis, exp_multiplier_value,
 jitter_factor_ratio,
 retry_http_status_json, giveup_http_status_json, retry_on_network_error,
 circuit_break_threshold, circuit_cooldown_millis)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        5, 'EXP_JITTER', 500, 8000, 2.0, 0.3,
        JSON_ARRAY(429, 500, 502, 503, 504), JSON_ARRAY(400, 401, 403, 404), 1,
        10, 60000);
```

> **典型**：429/5xx 重试，指数+抖动；连续 10 次失败进入断路器，冷却 60 秒后半开探测。

---

### 6.9 限流与并发（客户端令牌桶）

```sql
INSERT INTO reg_prov_rate_limit_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 rate_tokens_per_second, burst_bucket_capacity, max_concurrent_requests,
 per_credential_qps_limit, bucket_granularity_scope,
 smoothing_window_millis, respect_server_rate_header)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        5, 5, 10,
        NULL, 'GLOBAL',
        1000, 1);
```

> **示例**：全局 5 QPS，突发 5，并发 10；遵循服务端速率头（如有）。

---

### 6.10 凭证（可选：API Key 提升配额）

```sql
/* 若有 API Key，可配置为查询参数 api_key=xxxx */
INSERT INTO reg_prov_credential
(provenance_id, scope, task_type, endpoint_id,
 credential_name, auth_type, inbound_location,
 credential_field_name, credential_value_prefix, credential_value_ref,
 basic_username_ref, basic_password_ref,
 oauth_token_url, oauth_client_id_ref, oauth_client_secret_ref, oauth_scope, oauth_audience, extra_json,
 effective_from, effective_to, is_default_preferred)
VALUES (@pubmed_id, 'SOURCE', NULL, NULL,
        'default-api-key', 'API_KEY', 'QUERY',
        'api_key', NULL, 'kms://path/to/secret',
        NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL,
        '2025-01-01 00:00:00', NULL, 1);
```

> **端点绑定**：若某端点需要专用密钥，可将 `endpoint_id` 指向对应 `reg_prov_endpoint_def.id`。

---

### 6.11 组合查询：装配“PubMed / update”的当前生效配置

> 运行一次 `SEARCH`（ESearch）与后续 `FETCH`（EFetch）通常要**同时读取**多个维度。以下 SQL 取齐端点/HTTP/窗口/分页/批量/重试/限流/凭证各
> 1 条“当前生效”记录。
> 应用端将 JSON 字段做合并，并将端点级参数名覆盖应用到请求构造。

```sql
/* 入参：provenance_code='pubmed'，task_type='update' */
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = 'pubmed';
SET @now = UTC_TIMESTAMP();

WITH ep_search AS ((SELECT *
                    FROM reg_prov_endpoint_def
                    WHERE provenance_id = @pid
                      AND lifecycle_status = 'PUBLISHED'
                      AND deleted = 0
                      AND scope = 'TASK'
                      AND task_type = 'update'
                      AND endpoint_usage = 'SEARCH'
                      AND effective_from <= @now
                      AND (effective_to IS NULL OR effective_to > @now)
                    ORDER BY effective_from DESC, id DESC
                    LIMIT 1)
                   UNION ALL
                   (SELECT *
                    FROM reg_prov_endpoint_def
                    WHERE provenance_id = @pid
                      AND lifecycle_status = 'PUBLISHED'
                      AND deleted = 0
                      AND scope = 'SOURCE'
                      AND endpoint_usage = 'SEARCH'
                      AND effective_from <= @now
                      AND (effective_to IS NULL OR effective_to > @now)
                    ORDER BY effective_from DESC, id DESC
                    LIMIT 1)
                   LIMIT 1),
     ep_fetch AS ((SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND lifecycle_status = 'PUBLISHED'
                     AND deleted = 0
                     AND scope = 'TASK'
                     AND task_type = 'update'
                     AND endpoint_usage = 'FETCH'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  UNION ALL
                  (SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND lifecycle_status = 'PUBLISHED'
                     AND deleted = 0
                     AND scope = 'SOURCE'
                     AND endpoint_usage = 'FETCH'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  LIMIT 1),
     http AS ((SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND lifecycle_status = 'PUBLISHED'
                 AND deleted = 0
                 AND scope = 'TASK'
                 AND task_type = 'update'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              UNION ALL
              (SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND lifecycle_status = 'PUBLISHED'
                 AND deleted = 0
                 AND scope = 'SOURCE'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              LIMIT 1),
     win AS ((SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND lifecycle_status = 'PUBLISHED'
                AND deleted = 0
                AND scope = 'TASK'
                AND task_type = 'update'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             UNION ALL
             (SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND lifecycle_status = 'PUBLISHED'
                AND deleted = 0
                AND scope = 'SOURCE'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             LIMIT 1),
     pg AS ((SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     bt AS ((SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rt AS ((SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rl AS ((SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'TASK'
               AND task_type = 'update'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status = 'PUBLISHED'
               AND deleted = 0
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     cred AS (
         -- 凭证：先尝试端点绑定，再尝试全局
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status = 'PUBLISHED'
            AND deleted = 0
            AND endpoint_id = (SELECT id FROM ep_search LIMIT 1)
            AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status = 'PUBLISHED'
            AND deleted = 0
            AND endpoint_id IS NULL
            AND ((scope = 'TASK' AND task_type = 'update') OR scope = 'SOURCE')
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1)
SELECT *
FROM ep_search,
     ep_fetch,
     http,
     win,
     pg,
     bt,
     rt,
     rl,
     cred;
```

---

### 5.7 读侧封装模板（视图/存储过程）

为减少重复 SQL，可在 MySQL 8.0 中使用窗口函数构建“当前生效”视图（按维度取 Top 1），或提供存储过程按入参返回单条配置。

示例一：HTTP 策略“当前生效”视图（每个 `(provenance_id, scope, task_type_key)` 一条）

```sql
CREATE OR REPLACE VIEW v_reg_prov_http_cfg_active AS
SELECT *
FROM (
  SELECT h.*, ROW_NUMBER() OVER (
           PARTITION BY h.provenance_id, h.scope, h.task_type_key
           ORDER BY h.effective_from DESC, h.id DESC
         ) AS rn
  FROM reg_prov_http_cfg h
  WHERE h.lifecycle_status = 'PUBLISHED' AND h.deleted = 0
) t
WHERE t.rn = 1;
```

示例二：按来源与任务获取 HTTP 策略（无存储过程/无触发器，返回 0/1 行）

注：不使用触发器与存储过程。以下提供直接可执行的查询模板，供应用层 PreparedStatement 使用。

等价查询模板（无存储过程/无触发器）

```sql
-- 参数占位：:provenance_code, :task_type（harvest|update|backfill）
-- 说明：与上面的存储过程语义一致；可直接在应用层以 PreparedStatement 方式执行。

(SELECT h.*
 FROM reg_prov_http_cfg h
 WHERE h.provenance_id = (
         SELECT id
         FROM reg_provenance
         WHERE provenance_code = :provenance_code
           AND lifecycle_status = 'PUBLISHED'
           AND deleted = 0
         LIMIT 1
       )
   AND h.lifecycle_status = 'PUBLISHED'
   AND h.deleted = 0
   AND h.scope = 'TASK'
   AND h.task_type = :task_type
   AND h.effective_from <= UTC_TIMESTAMP()
   AND (h.effective_to IS NULL OR h.effective_to > UTC_TIMESTAMP())
 ORDER BY h.effective_from DESC, h.id DESC
 LIMIT 1)
UNION ALL
(SELECT h.*
 FROM reg_prov_http_cfg h
 WHERE h.provenance_id = (
         SELECT id
         FROM reg_provenance
         WHERE provenance_code = :provenance_code
           AND lifecycle_status = 'PUBLISHED'
           AND deleted = 0
         LIMIT 1
       )
   AND h.lifecycle_status = 'PUBLISHED'
   AND h.deleted = 0
   AND h.scope = 'SOURCE'
   AND h.effective_from <= UTC_TIMESTAMP()
   AND (h.effective_to IS NULL OR h.effective_to > UTC_TIMESTAMP())
 ORDER BY h.effective_from DESC, h.id DESC
 LIMIT 1)
LIMIT 1;
```

---

### 6.12 运行片段：从“窗口”到“请求”的一步步

> 目标：**增量 Update**，以 `EDAT` 为增量字段，将窗口 `[T, T+1d)` 的新增文献拉全。
> 示例取 `T=2025-07-01 00:00:00Z`，**重叠** 1 天（窗口来自 §6.4）。

#### 6.12.1 计算时间窗口（应用层）

* 配置：`SLIDING`、`window_size=1 DAY`、`overlap=1 DAY`、`default_date_field_name='EDAT'`。
* 假设上次水位为 `2025-07-01T00:00:00Z`，则本次窗口：

    * `start = 2025-07-01`
    * `end   = 2025-07-02`（半开区间）
    * `datetype = 'edat'`
    * 请求中可映射为：`mindate=2025/07/01&maxdate=2025/07/02`（或等效 term 语法）

#### 6.12.2 构造检索请求（ESearch）

* **端点**：从 `ep_search` 读 `path_template='/eutils/esearch.fcgi'`；
* **BaseURL**：`http.base_url_override` 若为空则用 `reg_provenance.base_url_default`；
* **Headers**：从 `http.default_headers_json` 合并；
* **分页**：`page_size_value=100`；`page=1` 起步 → 应用层转 `retstart=0`；
* **默认 query**：合并 `default_query_params={'db':'pubmed','retmode':'json','datetype':'edat'}`；
* **运行时 query**：加入 `term`（如 `'cancer'`），加入 `mindate/maxdate`；若有 `api_key` 凭证→放入查询参数 `api_key=`；
* **最终（示意 URL）**：

  ```

GET https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi
?db=pubmed
&retmode=json
&datetype=edat
&term=cancer
&mindate=2025/07/01
&maxdate=2025/07/02
&retmax=100
&retstart=0 -- 由 page=1 转换而来
[&api_key=XXXX]     -- 若有凭证

  ```
* **翻页**：若 `count>100`，下一页 `page=2 → retstart=100`，以此类推，直到达到 `max_pages_per_execution` 或拉全。

**响应解析（示意）**

* `total_count_jsonpath='$.esearchresult.count'` → 统计总条数；
* PMID 列表在 `$.esearchresult.idlist[*]`（应用层解析）；
* 产出 **PMID 集合** 供详情阶段使用。

#### 6.12.3 详情抓取（EFetch 批量）

* **端点**：从 `ep_fetch` 取 `path_template='/eutils/efetch.fcgi'`；
* **批量**：`detail_fetch_batch_size=200`；**ID 参数名** 从端点 `ids_param_name='id'`（或批量维度回退）读取；
* **构造**：将收集的 PMID 分块，每块最多 200；`id=pmid1,pmid2,...`；
* **最终（示意 URL）**：

  ```

GET https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi
?db=pubmed
&retmode=xml
&rettype=abstract
&id=34567890,34567891,34567892,...
[&api_key=XXXX]

  ```
* **解析**：返回 XML；应用层解析为内部结构并入库。

#### 6.12.4 稳健性与配额

* **限流**：`rate_tokens_per_second=5`、`burst=5`、`max_concurrent_requests=10`；
* **重试**：429/5xx 退避重试；尊重 `Retry-After`；
* **水位推进**：当窗口内的 ESearch+EFetch 完整成功后，推进增量水位到 `end`（考虑 `watermark_lag_seconds` 与 `overlap`）。

---

### 6.13（可选）回填 Backfill 的差异化配置

若要大规模回填可：

* `task_type='backfill'` 新增一组区间；
* **窗口**改为 `CALENDAR+DAY` 或更大跨度；
* **分页**增加 `max_pages_per_execution`；
* **限流**适度上调（若对方允许）并延长观察期；
* **重试**更保守（更大退避上限）。

> 示例（仅给出窗口差异）：

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from,
 window_mode, window_size_value, window_size_unit, calendar_align_to,
 offset_type, default_date_field_name)
VALUES (@pubmed_id, 'TASK', 'backfill', '2025-01-15 00:00:00',
        'CALENDAR', 1, 'DAY', 'DAY',
        'DATE', 'PDAT'); -- 回填按出版日期 PDAT
```

## 7. Crossref 示例（端到端）

### 7.1 背景与 API 速览（面向本库的映射）

* **核心资源**：`/works`

    * **检索（SEARCH）**：`GET /works` 支持按时间过滤、排序、分页；
    * **详情（FETCH）**：`GET /works/{doi}` 以 DOI 拉取单条详情；
* **时间/增量**：常用 `indexed`（索引时间）或 `created`（创建时间）作为增量字段；

    * 过滤语法（示例）：`filter=from-index-date:YYYY-MM-DD,until-index-date:YYYY-MM-DD`；
* **分页**：**cursor-based**（推荐，性能更稳）；

    * 起始游标通常为 `*`；
    * 下一个游标在响应 JSON `message.next-cursor`；
    * 每页大小参数为 `rows`；
* **鉴权与礼仪**：Crossref 强烈建议在请求头中提供 **`User-Agent` + `mailto`**（非必须，但对配额与服务质量有帮助）；本设计放入
  `reg_prov_http_cfg.default_headers_json`。
* **响应结构要点**：

    * `message.total-results`（总条数）、`message.items`（记录数组）
    * `message.next-cursor`（下一页用游标）

> 注：Crossref 通常不支持“批量按 DOI 一次返回多条”的端点（详情为 `/works/{doi}` 单条）。若需要批处理，请在应用层**并发多个详情请求
**或以 `rows` 拉取检索结果后再入库。

---

### 7.2 主数据：登记 Crossref 来源

```sql
-- ① 新增来源（若已存在则忽略）
INSERT IGNORE INTO reg_provenance (provenance_code, provenance_name, base_url_default, timezone_default, docs_url,
                                   is_active)
VALUES ('crossref', 'Crossref', 'https://api.crossref.org', 'UTC', 'https://api.crossref.org/swagger-ui/index.html', 1);

-- ② 取回 provenance_id
SELECT id
INTO @crossref_id
FROM reg_provenance
WHERE provenance_code = 'crossref';
```

---

### 7.3 端点定义（works 检索与 DOI 详情）

```sql
/* 搜索端点：GET /works（cursor-based） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage, http_method, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@crossref_id, 'TASK', 'harvest', 'works', '2025-01-01 00:00:00', NULL,
        'SEARCH', 'GET', '/works',
           /* 缺省 query：排序按 indexed 升序（可被运行时覆盖）；过滤条件由运行时拼接到 filter */
        JSON_OBJECT('sort', 'indexed', 'order', 'asc'),
        NULL,
        'application/json', 0, NULL,
           /* 端点级覆盖：cursor & rows 的参数名 */
        NULL, 'rows', 'cursor', NULL);

/* 详情端点：GET /works/{doi}（单条） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage, http_method, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@crossref_id, 'TASK', 'harvest', 'work_by_doi', '2025-01-01 00:00:00', NULL,
        'FETCH', 'GET', '/works/{doi}',
        NULL, NULL,
        'application/json', 0, NULL,
        NULL, NULL, NULL, NULL);
```

> **要点**
>
> * `endpoint_usage='SEARCH'` → 当前生效检索端点；`'FETCH'` → 按 DOI 的详情端点。
> * 端点级参数名覆盖：`page_size_param_name='rows'`、`cursor_param_name='cursor'`；
> * 详情端点 `path_template` 使用占位符 `{doi}`，应用层在发送请求前替换。

---

### 7.4 时间窗口与增量指针（按 indexed 滑动）

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 window_mode, window_size_value, window_size_unit, calendar_align_to,
 lookback_value, lookback_unit, overlap_value, overlap_unit, watermark_lag_seconds,
 offset_type, offset_field_name, offset_date_format, default_date_field_name,
 max_ids_per_window, max_window_span_seconds)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01 00:00:00', NULL,
        'SLIDING', 1, 'DAY', NULL,
        NULL, NULL, 1, 'DAY', 1800, -- 重叠1天，水位滞后30分钟
        'DATE', NULL, 'YYYY-MM-DD', 'indexed',
        1000000, 259200); -- 单窗口最多100万条，最大跨度3天（示例）
```

> 对于 Crossref 全量/增量，`indexed` 常作为更稳定的推进字段；如需切换到 `created`，只需新增一个 `TASK=update` 区间配置即可。

---

### 7.5 分页：CURSOR（游标）

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 pagination_mode, page_size_value, max_pages_per_execution,
 page_number_param_name, page_size_param_name, start_page_number,
 sort_field_param_name, sort_direction,
 cursor_param_name, initial_cursor_value, next_cursor_jsonpath, has_more_jsonpath, total_count_jsonpath)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01 00:00:00', NULL,
        'CURSOR', 200, 5000,
        NULL, 'rows', NULL,
        'indexed', 'asc', -- 仅作描述，具体排序参数由端点 default_query_params 控制
        'cursor', '*', '$.message["next-cursor"]', NULL, '$.message["total-results"]');
```

> **含义**
>
> * 每页 `rows=200`，最大翻页 5000（示例）；
> * 初始游标 `*`；
> * 下一游标在 `$.message["next-cursor"]`；
> * 总条数在 `$.message["total-results"]`。

---

### 7.6 HTTP 策略（User-Agent / mailto）

```sql
INSERT INTO reg_prov_http_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 base_url_override, default_headers_json,
 timeout_connect_millis, timeout_read_millis, timeout_total_millis,
 tls_verify_enabled, proxy_url_value,
 accept_compress_enabled, prefer_http2_enabled,
 retry_after_policy, retry_after_cap_millis,
 idempotency_header_name, idempotency_ttl_seconds)
VALUES (@crossref_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        NULL,
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0 (+ops@example.com)',
                    'mailto', 'ops@example.com'),
        2000, 15000, 20000,
        1, NULL,
        1, 0,
        'RESPECT', 60000,
        NULL, NULL);
```

> **建议**：严格设置 `User-Agent` 和 `mailto`，有助于 Crossref 侧识别与联系。

---

### 7.7 批量抓取与请求成型（详情为单条）

```sql
INSERT INTO reg_prov_batching_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 detail_fetch_batch_size, ids_param_name, ids_join_delimiter, max_ids_per_request,
 prefer_compact_payload, payload_compress_strategy,
 app_parallelism_degree, per_host_concurrency_limit, http_conn_pool_size,
 backpressure_strategy, request_template_json)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01 00:00:00', NULL,
        1, NULL, NULL, 1, -- 详情按 DOI 单条请求
        1, 'NONE',
        16, 16, 128, -- 并行适当上调（示例值）
        'BLOCK', NULL);
```

> 由于 `/works/{doi}` 为单条详情，请将 `detail_fetch_batch_size` 设为 1，并使用应用层**并发**多请求以提升吞吐。

---

### 7.8 重试与退避（尊重 Retry-After）

```sql
INSERT INTO reg_prov_retry_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 max_retry_times, backoff_policy_type, initial_delay_millis, max_delay_millis, exp_multiplier_value,
 jitter_factor_ratio,
 retry_http_status_json, giveup_http_status_json, retry_on_network_error,
 circuit_break_threshold, circuit_cooldown_millis)
VALUES (@crossref_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        6, 'EXP_JITTER', 800, 15000, 2.0, 0.4,
        JSON_ARRAY(429, 500, 502, 503, 504), JSON_ARRAY(400, 401, 403, 404), 1,
        12, 60000);
```

> 结合 §7.6 的 `retry_after_policy='RESPECT'`，当返回 `Retry-After` 时按上限 60s 遵循。

---

### 7.9 限流与并发（按端点或全局）

```sql
INSERT INTO reg_prov_rate_limit_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 rate_tokens_per_second, burst_bucket_capacity, max_concurrent_requests,
 per_credential_qps_limit, bucket_granularity_scope,
 smoothing_window_millis, respect_server_rate_header)
VALUES (@crossref_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        10, 10, 24,
        NULL, 'PER_ENDPOINT', -- 按端点粒度限流（SEARCH 与 FETCH 分别计数）
        1000, 1);
```

> **示例**：全局 10 QPS、突发 10，并发 24；按端点独立限流，避免搜索/详情互相影响。

---

### 7.10 凭证（可选：Bearer / 自定义）

Crossref 公网通常**无需密钥**（但礼仪头必须）；若有合作或私有网关，可如下配置 **Bearer** 示例：

```sql
INSERT INTO reg_prov_credential
(provenance_id, scope, task_type, endpoint_id,
 credential_name, auth_type, inbound_location,
 credential_field_name, credential_value_prefix, credential_value_ref,
 basic_username_ref, basic_password_ref,
 oauth_token_url, oauth_client_id_ref, oauth_client_secret_ref, oauth_scope, oauth_audience, extra_json,
 effective_from, effective_to, is_default_preferred)
VALUES (@crossref_id, 'SOURCE', NULL, NULL,
        'partner-bearer', 'BEARER', 'HEADER',
        'Authorization', 'Bearer ', 'kms://path/to/bearer/ref',
        NULL, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL,
        '2025-01-01 00:00:00', NULL, 1);
```

> 若仅需 `mailto` 标识，无需在凭证表中配置；可直接在 HTTP 策略的 `default_headers_json` 内设置。

---

### 7.11 组合查询：装配“Crossref / harvest”的当前生效配置

```sql
/* 入参：provenance_code='crossref'，task_type='harvest' */
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = 'crossref';
SET @now = UTC_TIMESTAMP();

WITH ep_search AS ((SELECT *
                    FROM reg_prov_endpoint_def
                    WHERE provenance_id = @pid
                      AND scope = 'TASK'
                      AND task_type = 'harvest'
                      AND endpoint_usage = 'SEARCH'
                      AND effective_from <= @now
                      AND (effective_to IS NULL OR effective_to > @now)
                    ORDER BY effective_from DESC, id DESC
                    LIMIT 1)
                   UNION ALL
                   (SELECT *
                    FROM reg_prov_endpoint_def
                    WHERE provenance_id = @pid
                      AND scope = 'SOURCE'
                      AND endpoint_usage = 'SEARCH'
                      AND effective_from <= @now
                      AND (effective_to IS NULL OR effective_to > @now)
                    ORDER BY effective_from DESC, id DESC
                    LIMIT 1)
                   LIMIT 1),
     ep_fetch AS ((SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND scope = 'TASK'
                     AND task_type = 'harvest'
                     AND endpoint_usage = 'FETCH'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  UNION ALL
                  (SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND scope = 'SOURCE'
                     AND endpoint_usage = 'FETCH'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  LIMIT 1),
     http AS ((SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND scope = 'TASK'
                 AND task_type = 'harvest'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              UNION ALL
              (SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND scope = 'SOURCE'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              LIMIT 1),
     win AS ((SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND scope = 'TASK'
                AND task_type = 'harvest'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             UNION ALL
             (SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND scope = 'SOURCE'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             LIMIT 1),
     pg AS ((SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'harvest'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     bt AS ((SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'harvest'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rt AS ((SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'harvest'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rl AS ((SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND scope = 'TASK'
               AND task_type = 'harvest'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND scope = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     cred AS (
         -- 凭证（若有）：先尝试端点绑定，再尝试全局
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status = 'PUBLISHED'
            AND deleted = 0
            AND endpoint_id = (SELECT id FROM ep_search LIMIT 1)
            AND ((scope = 'TASK' AND task_type = 'harvest') OR scope = 'SOURCE')
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status = 'PUBLISHED'
            AND deleted = 0
            AND endpoint_id IS NULL
            AND ((scope = 'TASK' AND task_type = 'harvest') OR scope = 'SOURCE')
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         LIMIT 1)
SELECT *
FROM ep_search,
     ep_fetch,
     http,
     win,
     pg,
     bt,
     rt,
     rl,
     cred;
```

---

### 7.12 运行片段：从“窗口”到“请求”的一步步

> 目标：**全量/增量 Harvest**，以 `indexed` 字段、**day 窗口 + 1 天重叠**，使用 **cursor-based** 翻页。

#### 7.12.1 计算时间窗口（应用层）

* 配置：`SLIDING`；`window_size=1 DAY`；`overlap=1 DAY`；`default_date_field_name='indexed'`；
* 假设上次水位 `2025-07-01`，本次窗口：

    * `start=2025-07-01`，`end=2025-07-02`（半开区间）
* 映射到 Crossref 的过滤参数：

    * `filter=from-index-date:2025-07-01,until-index-date:2025-07-02`

#### 7.12.2 构造检索请求（`GET /works`）

* **端点**：`path_template='/works'`；
* **BaseURL**：HTTP 策略或主数据；
* **Headers**：包含 `User-Agent` 与 `mailto`；
* **分页**：`rows=200`（来自分页维度或端点覆盖）；
* **游标**：初始 `cursor=*`；从响应取下一页 `message.next-cursor`；
* **默认 query**：`sort=indexed&order=asc`（可被运行时覆盖）；
* **最终（示意 URL）**：

  ```
  GET https://api.crossref.org/works
      ?filter=from-index-date:2025-07-01,until-index-date:2025-07-02
      &sort=indexed
      &order=asc
      &rows=200
      &cursor=*        -- 首次
  ```
* **响应解析**：

    * `$.message["total-results"]` → 总条数；
    * `$.message.items[*]` → 记录数组，内含 DOI、标题等；
    * `$.message["next-cursor"]` → 下一页游标，直到为空或达到 `max_pages_per_execution`。

#### 7.12.3 详情抓取（`GET /works/{doi}`，可选）

* 许多场景可以直接以 `items` 入库；若需要详情补全：

    * 将 `doi` 替换入 `path_template='/works/{doi}'`；
    * 并发度由 `reg_prov_batching_cfg.app_parallelism_degree` 控制（单条请求）。
* **示意**：

  ```
  GET https://api.crossref.org/works/10.1000/xyz123
  ```

#### 7.12.4 稳健性与配额

* **限流**：`PER_ENDPOINT` 粒度，SEARCH 与 FETCH 分别记数；
* **重试**：对 429/5xx 指定指数退避并**尊重 Retry-After**；
* **水位推进**：窗口内成功完成后推进到 `end`；考虑 `watermark_lag_seconds` 与 `overlap`。

---

### 7.13（可选）任务差异化：Update / Backfill

* **Update**：将 `task_type='update'` 的窗口/分页/限流等作为**另一组区间**写入；例如窗口设置更短、并发限制更小；
* **Backfill**：可用 `CALENDAR` 模式与较大跨度（如周/月），并提升 `max_pages_per_execution`。

**示例：为 `update` 新增窗口配置（按 created 字段）**

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope, task_type, effective_from, effective_to,
 window_mode, window_size_value, window_size_unit, calendar_align_to,
 lookback_value, lookback_unit, overlap_value, overlap_unit, watermark_lag_seconds,
 offset_type, offset_field_name, offset_date_format, default_date_field_name)
VALUES (@crossref_id, 'TASK', 'update', '2025-02-01 00:00:00', NULL,
        'SLIDING', 1, 'DAY', NULL,
        NULL, NULL, 1, 'DAY', 900,
        'DATE', NULL, 'YYYY-MM-DD', 'created');
```

## 8. FAQ 与常见陷阱

### 8.1 为什么不在数据库做“区间不重叠”校验？

**答**：

* 业务上需要**灰度切换**（新旧短暂重叠）与**回滚**（回开旧记录），这与严格的 DB 级约束天然冲突；
* MySQL 不提供原生“区间排他”索引；用触发器/锁模拟易带来**写入死锁**、**演进困难**；
* 我们采用**应用层预检 + 观察期策略**：先加新（未来 `effective_from`）、到点切流、再收口旧。

---

### 8.2 `SOURCE` 与 `TASK` 的关系是“覆盖合并”吗？

**答**：**不是。**

* 读取时先尝试 `TASK` 级（`scope='TASK' AND task_type=?`），没有才回退 `SOURCE`；
* **不会**把 `TASK` 与 `SOURCE` 两条记录按字段做合并（除了“端点级小范围覆盖”，见 §4.4）；
* 字段为 `NULL` 的含义是“交给应用默认值”。

---

### 8.3 端点级覆盖与维度配置冲突时，谁优先？

**答**：**端点级参数名 > 维度配置 > 应用默认**。

* 例如：PubMed `esearch` 端点设置 `page_size_param_name='retmax'`，即便 `reg_prov_pagination_cfg.page_size_param_name`
  写成别名，**最终仍以端点为准**。
* 覆盖范围仅限少量**参数名类**字段（如 `page_param_name/cursor_param_name/ids_param_name`）。

---

### 8.4 PubMed 是“偏移量分页”，为什么模型里用 `PAGE_NUMBER`？

**答**：为了统一抽象。

* 应用层把页号换算为 `retstart=(page-1)*retmax`；
* 这样可与 Crossref 的 `CURSOR` 在同一套分页抽象下实现；
* 端点级可以覆盖参数名（`retmax`、`page`），但**偏移换算**在应用层。

### 8.5 Crossref 的下一游标提取不到，怎么办？

**答**：

* 通常是 `next_cursor_jsonpath` 配置不正确或服务端响应变化；
* 建议把整包响应样本入日志，核对路径（常见为 `$.message["next-cursor"]`）；
* 通过**新增一条** `reg_prov_pagination_cfg`（未来生效）修复路径，观察后关旧配置；
* 也可配置 `max_pages_per_execution` 做保护，避免无限循环。

---

### 8.6 如何避免“同一时刻多条配置同时生效”？

**答**：

* **写前预检**：使用“交集判定 SQL”（见 §5.1.2 / §8.2.1）；
* **统一时钟**：所有判断用 DB 的 `UTC_TIMESTAMP()`；
* **一次运行内冻结**：作业启动时取一次“运行合同”，整个运行过程不再刷新配置，避免半途切换导致抖动。

---

### 8.7 想“改起点时间”怎么办？可以 `UPDATE effective_from` 吗？

**答**：不推荐直接改起点。

* 以时间点为“版本锚点”，**新建一条记录**代表新版本，旧记录通过 `effective_to` 关窗；
* 这样保留了历史轨迹，便于回溯与“时间穿越”诊断（见 §8.3 方式 B）。

---

### 8.8 JSON 字段能当过滤条件用吗？

**答**：不建议。

* JSON 仅作**扩展位**（默认参数、模板、状态码数组等）；
* 关键过滤键（如 `pagination_mode`、`endpoint_usage`、`scope/task_type`）必须是结构化列，并建索引；
* 如需对 JSON 做轻度查询，可在应用侧解析，或在后续演进中增加“**生成列 + 索引**”。

---

### 8.9 凭证存明文安全吗？

**答**：**不安全，不建议。**

* 建议在 `credential_value_plain` 存“**引用**”（如 `KMS_KEY:xyz`），实际密钥在安全服务中拉取；
* 轮换采用“**多把并存 + 默认标记**”，先加新后降级旧（见 §8.4）；
* 对端点有专用配额时，使用 `endpoint_id` 绑定到具体端点。

---

### 8.10 429（限流）或 503（拥塞）居高不下，优先调哪个表？

**答**（优先级）：

1. `reg_prov_rate_limit_cfg`：降低 `rate_tokens_per_second` / `max_concurrent_requests`；
2. `reg_prov_retry_cfg`：用 `EXP_JITTER`，增大 `initial_delay_millis` / `max_delay_millis`，包含 429/5xx；
3. `reg_prov_batching_cfg`：减小 `detail_fetch_batch_size` 与并行度；
4. `reg_prov_http_cfg`：确保 `retry_after_policy='RESPECT'` 并设置上限。

> 采用“先加新→观察→关旧”的灰度节奏，不要直接覆盖旧配置。

---

### 8.11 是否需要在库里保存“增量水位/游标”？

**答**：**不在本库**。

* 本库只负责**静态配置**；
* 动态水位（窗口开始/结束、最近成功推进）、游标/断点应在**作业状态表**或**事件流水**里维护（通常由另一套运行时表承担）。

---

### 8.12 如何决定字段放“结构列”还是“JSON 扩展”？

**答**：

* **查询路径会用到的**（过滤、排序、唯一性）→ 结构列；
* **供应商特有且非高频过滤** → JSON；
* 无法确定时，优先保守：先结构化常用键，剩余放 JSON，避免未来加索引改表。

---

### 8.13 应用端是否需要事务地写入多维度的变更？

**答**：视规模决定。

* 单维度灰度：一个事务足够（预检→插入新→提交）；
* 多维度联动（比如分页+批量+限流一起调）：建议**分两步**：先新增未来生效的新配置（全部），到点后**逐表关旧**，并观察；
* 即便跨表不在同一事务，因“按时间点切换”，读侧也能拿到一致的“最新”组合。

---

### 8.14 用 `UNION ALL ... LIMIT 1` 选“当前生效”会不会拿到两行？

**答**：不会。

* 每个分支本身 `ORDER BY ... LIMIT 1`；外层 `UNION ALL ... LIMIT 1` 只取第一条（即 TASK 分支优先），若 TASK 分支没命中，才会落到
  SOURCE 分支；
* 这是**确定性**且可读性好的实现方式。

---

### 8.15 如何“时间回放”定位某个事故发生时的配置？

**答**：用“时间穿越”查询（Time-Travel-like）：

```sql
SET @T = '2025-08-31 23:59:00';
SELECT *
FROM reg_prov_pagination_cfg
WHERE provenance_id = @pid
  AND effective_from <= @T
  AND (effective_to IS NULL OR effective_to > @T)
ORDER BY scope = 'TASK' DESC, effective_from DESC, id DESC
LIMIT 1;
```

对其他维度亦然（换表名）。把 `@T` 设置为事故发生时刻，即可重建当时“当前生效”的组合。

---

### 8.16 PubMed：为什么窗口覆盖 1 天还会有重复 PMID？

**答**：

* 迟到/乱序（晚到的更新落在此前窗口）与 E-utilities 的数据刷新节奏都会导致重复；
* 解决：

    * 保持**窗口重叠**（例如 `overlap=1 DAY`）+ **水位滞后**；
    * 下游**按主键去重**（PMID 或 DOI），保持**幂等写入**；
    * 将“已处理清单”保存在运行库以避免重复消费。

---

### 8.17 Crossref：全量拉取时 `total-results` 很大，是否要依赖它终止？

**答**：不依赖。

* 以 `next-cursor` 的有无作为主终止条件；
* `max_pages_per_execution` 仅用于**安全刹车**；
* `total-results` 更像统计信息，可能变化。

---

### 8.18 为什么有时 `endpoint_usage='FETCH'` 不需要？

**答**：

* 对 Crossref，`/works` 的 `items` 已经包含大量详情字段，可直接入库；
* 若业务仍需补齐（例如作者机构的丰富化），再使用 `work_by_doi`（`FETCH`）作为**可选补充**；
* 因而 `FETCH` 端点并非总是必需。

---

### 8.19 `effective_to` 需要长期保留 NULL 吗？

**答**：可以。

* NULL 表示“开放至未来”；
* 归档策略：把**完全过期的历史**（`effective_to < NOW() - 90d`）转移到 `_hist` 表，维持主表精简（见 §8.9）。

---

### 8.20 索引仍然慢，可能踩到哪些坑？

**答**：

* WHERE 子句写法与复合索引顺序不匹配（应优先 `provenance_id, scope, task_type_key, [endpoint_name], effective_from`）；
* 遗漏时间过滤（`effective_from <= now < effective_to`），导致范围扩大；
* 对列做函数运算（如 `DATE(effective_from)`），破坏索引；
* 使用 `%like%` 搜索 JSON 串；
* `ORDER BY` 不与索引顺序对齐，且缺少 `LIMIT`。

---

### 8.21 示例：一次“安全变更”的最小 SQL 序列？

**答**：以“把 Crossref rows 从 200 调整到 150”为例：

1. 预检无重叠；
2. 插入新分页配置（未来 10 分钟生效）：

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope, task_type, effective_from, pagination_mode, page_size_value,
 page_size_param_name, cursor_param_name)
VALUES (@pid, 'TASK', 'harvest', UTC_TIMESTAMP() + INTERVAL 10 MINUTE, 'CURSOR', 150, 'rows', 'cursor');
```

3. 到点后观察 15 分钟；
4. 收口旧配置的 `effective_to = 新起点 + 15分钟观察`。

> 全程**无 UPDATE 起点**，历史可追溯，可随时回滚。

---

### 8.22 有没有推荐的“配置冷启动”顺序？

**答**：

1. `reg_provenance`（来源）
2. `reg_prov_endpoint_def`（至少 SEARCH）
3. `reg_prov_pagination_cfg`
4. `reg_prov_http_cfg`
5. `reg_prov_window_offset_cfg`
6. （如需详情）`reg_prov_batching_cfg`
7. `reg_prov_retry_cfg`、`reg_prov_rate_limit_cfg`
8. （可选）`reg_prov_credential`

> 然后用 §5.4 的“跨维度聚合查询”一次性验证读侧装配是否正常。

---

### 8.23 多把凭证如何“加权分流”？

**答**：

* 读取时把候选凭证按优先级排序取 **前 N** 把；
* 应用侧实现**轮询/加权轮询**（基于配额或历史 429 比例），并结合健康探测做**熔断**；
* 若需要**按密钥限流**，在 `reg_prov_rate_limit_cfg.bucket_granularity_scope='PER_KEY'` 前提下，应用层把“当前凭证标识”作为限流桶
  Key。

---

### 8.24 如何在不变更生产表结构的前提下扩展？

**答**：

* 新增字段优先考虑放到对应表的 **JSON 扩展位**（如端点默认参数、HTTP 扩展头等）；
* 若会成为常用过滤键，则**增补结构列 + 索引**；
* 坚持“**维度化拆分** + **强语义命名**”，避免把所有参数堆进某一张胖表。

---

### 8.25 我应不应该在一张视图里把所有维度 join 好？

**答**：不建议在 DB 端做“总大视图”。

* 各维度都有**时间区间与优先级**，在视图中很难写成一个既高效又正确的通用逻辑；
* 推荐按 §4.6 / §5.4 的模式由**应用端**拼装，或在读侧做**短 TTL 聚合缓存**。

## 9. 附录 A：建表 SQL（索引/外键已包含）

```mysql
/* =====================================================================
 * 医学文献采集配置库（MySQL 8.0）
 * 说明：
 * - 仅数据库对象与索引；无触发器、无 CHECK 约束；包含通用审计字段（BaseDO）；校验交由应用层处理。
 * - 字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci；引擎：InnoDB。
 * - 命名规范：
 *   - 主数据表：reg_provenance（数据源/来源登记）。
 *   - 所有配置/定义表均使用前缀 reg_prov_，并用语义化缩写：
 *     reg_prov_window_offset_cfg        —— 时间窗口与增量指针配置
 *     reg_prov_pagination_cfg           —— 分页与游标配置
 *     reg_prov_http_cfg                 —— HTTP 策略配置
 *     reg_prov_endpoint_def             —— 端点定义
 *     reg_prov_batching_cfg             —— 批量抓取与请求成型配置
 *     reg_prov_retry_cfg                —— 重试与退避配置
 *     reg_prov_rate_limit_cfg           —— 限流与并发配置
 *     reg_prov_credential               —— 鉴权/密钥配置（可选绑定端点）
 * - 外键：所有配置表以 provenance_id 关联 reg_provenance(id)；凭证表可选关联端点 reg_prov_endpoint_def(id)。
 * - 生效区间：effective_from/effective_to 采用 [start, end) 语义；不重叠由应用层保证。
 * - 作用域：scope = SOURCE|TASK；当 scope='SOURCE' 时 task_type 置 NULL；当 scope='TASK' 时 task_type 必须设置（由应用层保证）。
 * - 生成列：task_type_key = IFNULL(task_type, 'ALL')，便于维度唯一键与检索。
 * ===================================================================== */

/* ===========================================================
 * 基础主数据：数据来源登记（Provenance Registry）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_provenance`;
CREATE TABLE `reg_provenance`
(
    `id`               BIGINT UNSIGNED                      NOT NULL AUTO_INCREMENT COMMENT '主键：数据来源唯一标识；被所有 reg_prov_* 配置表通过 provenance_id 引用',
    `provenance_code`  VARCHAR(64)                          NOT NULL COMMENT '来源编码：全局唯一、稳定（如 pubmed / crossref），用于程序内查找与约束',
    `provenance_name`  VARCHAR(128)                         NOT NULL COMMENT '来源名称：人类可读名称（如 PubMed / Crossref）',
    `base_url_default` VARCHAR(512)                         NULL COMMENT '默认基础URL：当未在 HTTP 策略中覆盖时，用于端点 path 的拼接',
    `timezone_default` VARCHAR(64)                          NOT NULL DEFAULT 'UTC' COMMENT '默认时区（IANA TZ，如 UTC/Asia/Shanghai）：窗口计算/展示的缺省时区',
    `docs_url`         VARCHAR(512)                         NULL COMMENT '官方文档/说明链接：便于排障或核对 API 用法',
    `is_active`        TINYINT(1)                           NOT NULL DEFAULT 1 COMMENT '是否启用该来源：1=启用；0=停用（应用读取时可据此过滤）',
    `lifecycle_status` ENUM ('DRAFT','PUBLISHED','RETIRED') NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：DRAFT 草稿；PUBLISHED 已发布；RETIRED 退役（读侧仅取 PUBLISHED）',

    -- BaseDO（统一审计字段）
    `record_remarks`   JSON                                 NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`       TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`       BIGINT UNSIGNED                      NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)                         NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`       BIGINT UNSIGNED                      NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)                         NULL COMMENT '更新人姓名',
    `version`          BIGINT UNSIGNED                      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)                        NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`          TINYINT(1)                           NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reg_provenance_code` (`provenance_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='数据来源登记：记录外部数据源（Provenance）的基础信息，作为所有配置的外键根。';


/* ===========================================================
 * 端点定义（Endpoint Definition）
 * 说明：定义各来源在不同时间区间内可用的搜索/详情/令牌等端点形态与默认参数。
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_endpoint_def`;
CREATE TABLE `reg_prov_endpoint_def`
(
    `id`                   BIGINT UNSIGNED                                             NOT NULL AUTO_INCREMENT COMMENT '主键：端点定义记录ID；可被凭证表 reg_prov_credential.endpoint_id 可选引用',
    `provenance_id`        BIGINT UNSIGNED                                             NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                ENUM ('SOURCE','TASK')                                      NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`            ENUM ('harvest','update','backfill')                        NULL COMMENT '任务类型：当 scope=TASK 时需填写；harvest=全量首采，update=增量，backfill=回填',
    `endpoint_name`        VARCHAR(64)                                                 NOT NULL COMMENT '端点逻辑名称：如 search / fetch / works / token；用于业务侧选择端点',

    `effective_from`       TIMESTAMP(6)                                                NOT NULL COMMENT '生效起始（含）；不重叠由应用层保证',
    `effective_to`         TIMESTAMP(6)                                                NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `endpoint_usage`       ENUM ('SEARCH','FETCH','TOKEN','METADATA','PING','RATE')    NOT NULL DEFAULT 'SEARCH' COMMENT '用途：SEARCH=检索列表；FETCH=详情；TOKEN=换取令牌；METADATA=元数据；PING=健康检查；RATE=配额端点',
    `http_method`          ENUM ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS') NOT NULL DEFAULT 'GET' COMMENT 'HTTP 方法：GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS',
    `path_template`        VARCHAR(512)                                                NOT NULL COMMENT '路径模板：相对路径或绝对路径；可包含占位符（如 /entrez/eutils/esearch.fcgi）',
    `default_query_params` JSON                                                        NULL COMMENT '默认查询参数JSON：作为每次请求的基础 query（运行时可覆盖/合并）',
    `default_body_payload` JSON                                                        NULL COMMENT '默认请求体JSON：POST/PUT/PATCH 的基础 body（运行时可覆盖/合并）',
    `request_content_type` VARCHAR(64)                                                 NULL COMMENT '请求体内容类型：如 application/json / application/x-www-form-urlencoded',
    `is_auth_required`     TINYINT(1)                                                  NOT NULL DEFAULT 0 COMMENT '是否需要鉴权：1=需要；0=不需要（匿名可调用）',
    `credential_hint_name` VARCHAR(64)                                                 NULL COMMENT '凭证提示：偏好使用的凭证标签/名称，辅助在多凭证中挑选',

    /* 端点级分页/批量覆盖（可为空：由 reg_prov_pagination_cfg 或应用决定） */
    `page_param_name`      VARCHAR(64)                                                 NULL COMMENT '分页页码参数名（端点级覆盖项）',
    `page_size_param_name` VARCHAR(64)                                                 NULL COMMENT '分页每页大小参数名（端点级覆盖项）',
    `cursor_param_name`    VARCHAR(64)                                                 NULL COMMENT '游标/令牌参数名（端点级覆盖项）',
    `ids_param_name`       VARCHAR(64)                                                 NULL COMMENT '批量详情请求中，ID列表的参数名（端点级覆盖项）',

    /* 生成列：将 task_type 标准化为 ALL 或实际值，便于唯一键 */
    `task_type_key`        VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：当 task_type 为空取 ALL，否则取其枚举值；用于维度唯一键',
    `lifecycle_status`     ENUM ('DRAFT','PUBLISHED','RETIRED')                        NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`       JSON                                                        NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`           TIMESTAMP(6)                                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`           BIGINT UNSIGNED                                             NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)                                                NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)                                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`           BIGINT UNSIGNED                                             NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)                                                NULL COMMENT '更新人姓名',
    `version`              BIGINT UNSIGNED                                             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)                                               NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`              TINYINT(1)                                                  NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_endpoint_def__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),

    /* 维度唯一：同 (provenance_id, scope, task_type_key, endpoint_name) 下 effective_from 唯一；不重叠由应用保证 */
    UNIQUE KEY `uk_reg_prov_endpoint_def__dim_from` (`provenance_id`, `scope`, `task_type_key`, `endpoint_name`,
                                                     `effective_from`),
    KEY `idx_reg_prov_endpoint_def__dim_to` (`provenance_id`, `scope`, `task_type_key`, `endpoint_name`,
                                             `effective_to`),
    KEY `idx_reg_prov_endpoint_def__usage` (`endpoint_usage`),
    KEY `idx_reg_prov_endpoint_def__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                             DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='端点定义：描述来源在特定时间区间内可用的搜索/详情/令牌等端点形态与默认参数；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 时间窗口与增量指针配置（Window & Offset Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_window_offset_cfg`;
CREATE TABLE `reg_prov_window_offset_cfg`
(
    `id`                      BIGINT UNSIGNED                       NOT NULL AUTO_INCREMENT COMMENT '主键：时间窗口与增量指针配置记录ID',
    `provenance_id`           BIGINT UNSIGNED                       NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   ENUM ('SOURCE','TASK')                NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`               ENUM ('harvest','update','backfill')  NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`          TIMESTAMP(6)                          NOT NULL COMMENT '生效起始（含）；不重叠由应用层保证',
    `effective_to`            TIMESTAMP(6)                          NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    /* 窗口定义 */
    `window_mode`             ENUM ('SLIDING','CALENDAR')           NOT NULL DEFAULT 'SLIDING' COMMENT '窗口模式：SLIDING=滑动窗口；CALENDAR=对齐日历边界（如按天/月）',
    `window_size_value`       INT                                   NOT NULL DEFAULT 1 COMMENT '窗口长度的数值部分，如 1/7/30；单位见 window_size_unit',
    `window_size_unit`        ENUM ('SECOND','MINUTE','HOUR','DAY') NOT NULL DEFAULT 'DAY' COMMENT '窗口长度单位：秒/分/时/天',
    `calendar_align_to`       ENUM ('HOUR','DAY','WEEK','MONTH')    NULL COMMENT 'CALENDAR 模式对齐粒度：若为空，则按 SLIDING 逻辑',
    `lookback_value`          INT                                   NULL COMMENT '回看长度数值：用于补偿延迟数据（与 lookback_unit 搭配）',
    `lookback_unit`           ENUM ('SECOND','MINUTE','HOUR','DAY') NULL COMMENT '回看长度单位：秒/分/时/天',
    `overlap_value`           INT                                   NULL COMMENT '窗口重叠长度数值：相邻窗口之间的重叠（迟到兜底）',
    `overlap_unit`            ENUM ('SECOND','MINUTE','HOUR','DAY') NULL COMMENT '窗口重叠单位：秒/分/时/天',
    `watermark_lag_seconds`   INT                                   NULL COMMENT '水位滞后秒数：处理乱序/迟到数据时允许的最大延迟',

    /* 增量指针定义 */
    `offset_type`             ENUM ('DATE','ID','COMPOSITE')        NOT NULL DEFAULT 'DATE' COMMENT '指针类型：DATE=按时间字段推进；ID=按唯一标识推进；COMPOSITE=复合键',
    `offset_field_name`       VARCHAR(128)                          NULL COMMENT '指针字段名或 JSONPath（如 DATE 字段名/ID 字段名/复合键主维度）',
    `offset_date_format`      VARCHAR(64)                           NULL COMMENT 'DATE 指针格式/语义：如 ISO_INSTANT、epochMillis、YYYYMMDD',
    `default_date_field_name` VARCHAR(64)                           NULL COMMENT '默认增量日期字段名（如 PubMed: EDAT/PDAT/MHDA；Crossref: indexed-date）',
    `max_ids_per_window`      INT                                   NULL COMMENT '单窗口最多可处理的ID数量（超过则需二次切窗）',
    `max_window_span_seconds` INT                                   NULL COMMENT '单窗口最大跨度（秒）：过长窗口将被强制切分',

    /* 生成列 */
    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        ENUM ('DRAFT','PUBLISHED','RETIRED')  NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON                                  NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED                       NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)                          NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED                       NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)                          NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED                       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)                         NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)                            NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_window_offset_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_window_offset_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_window_offset_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_window_offset_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                                  DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='时间窗口与增量指针配置：定义采集任务如何切分时间窗口与推进增量指针（DATE/ID/COMPOSITE）；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 分页与游标配置（Pagination Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_pagination_cfg`;
CREATE TABLE `reg_prov_pagination_cfg`
(
    `id`                      BIGINT UNSIGNED                                       NOT NULL AUTO_INCREMENT COMMENT '主键：分页与游标配置记录ID',
    `provenance_id`           BIGINT UNSIGNED                                       NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   ENUM ('SOURCE','TASK')                                NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`               ENUM ('harvest','update','backfill')                  NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`          TIMESTAMP(6)                                          NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)                                          NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `pagination_mode`         ENUM ('NONE','PAGE_NUMBER','CURSOR','TOKEN','SCROLL') NOT NULL DEFAULT 'PAGE_NUMBER' COMMENT '分页模式：NONE=不分页；PAGE_NUMBER=页码；CURSOR=游标；TOKEN=令牌；SCROLL=滚动',
    `page_size_value`         INT                                                   NULL COMMENT '每页大小：PAGE_NUMBER/SCROLL 模式常用；空表示使用应用默认',
    `max_pages_per_execution` INT                                                   NULL COMMENT '单次执行最多翻页数：防止深翻造成高成本',
    `page_number_param_name`  VARCHAR(64)                                           NULL COMMENT '页码参数名（如 page）',
    `page_size_param_name`    VARCHAR(64)                                           NULL COMMENT '每页大小参数名（如 pageSize/rows）',
    `start_page_number`       INT                                                   NOT NULL DEFAULT 1 COMMENT '起始页码（PAGE_NUMBER 模式的起点）',
    `sort_field_param_name`   VARCHAR(64)                                           NULL COMMENT '排序字段参数名（如 sort）',
    `sort_direction`          ENUM ('ASC','DESC')                                   NULL COMMENT '排序方向（ASC/DESC）',

    /* 游标/令牌模式 */
    `cursor_param_name`       VARCHAR(64)                                           NULL COMMENT '游标参数名（如 cursor/next_token）',
    `initial_cursor_value`    VARCHAR(256)                                          NULL COMMENT '初始游标值：为空表示由应用从响应或配置中确定',
    `next_cursor_jsonpath`    VARCHAR(512)                                          NULL COMMENT '如何从响应中提取下一页游标的路径（JSONPath/JMESPath等）',
    `has_more_jsonpath`       VARCHAR(512)                                          NULL COMMENT '如何判断是否还有下一页的路径（布尔）',
    `total_count_jsonpath`    VARCHAR(512)                                          NULL COMMENT '如何从响应中提取总条数的路径（可选）',
    `next_cursor_xpath`       VARCHAR(512)                                          NULL COMMENT '如何从 XML 响应中提取下一页游标的 XPath（可选）',
    `has_more_xpath`          VARCHAR(512)                                          NULL COMMENT '如何从 XML 响应中判断是否还有下一页的 XPath（布尔，可选）',
    `total_count_xpath`       VARCHAR(512)                                          NULL COMMENT '如何从 XML 响应中提取总条数的 XPath（可选）',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        ENUM ('DRAFT','PUBLISHED','RETIRED')                  NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON                                                  NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)                                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED                                       NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)                                          NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)                                          NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED                                       NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)                                          NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED                                       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)                                         NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)                                            NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_pagination_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_pagination_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_pagination_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_pagination_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                               DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='分页与游标配置：配置页码/游标/令牌式分页的参数与响应提取规则；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * HTTP 策略配置（HTTP Policy Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_http_cfg`;
CREATE TABLE `reg_prov_http_cfg`
(
    `id`                      BIGINT UNSIGNED                      NOT NULL AUTO_INCREMENT COMMENT '主键：HTTP 策略配置记录ID',
    `provenance_id`           BIGINT UNSIGNED                      NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   ENUM ('SOURCE','TASK')               NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`               ENUM ('harvest','update','backfill') NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`          TIMESTAMP(6)                         NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)                         NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `base_url_override`       VARCHAR(512)                         NULL COMMENT '基础URL覆盖：若不为空，将覆盖 reg_provenance.base_url_default',
    `default_headers_json`    JSON                                 NULL COMMENT '默认HTTP Headers（JSON），运行时与请求头合并',
    `timeout_connect_millis`  INT                                  NULL COMMENT '连接超时（毫秒）：建立 TCP/SSL 连接的超时时间',
    `timeout_read_millis`     INT                                  NULL COMMENT '读取超时（毫秒）：读取响应主体的超时时间',
    `timeout_total_millis`    INT                                  NULL COMMENT '总超时（毫秒）：一次请求从开始到结束的最大耗时',
    `tls_verify_enabled`      TINYINT(1)                           NOT NULL DEFAULT 1 COMMENT '是否校验 TLS 证书：1=开启；0=关闭（仅测试环境）',
    `proxy_url_value`         VARCHAR(512)                         NULL COMMENT '代理地址：如 http://user:pass@host:port 或 socks5://host:port',
    `accept_compress_enabled` TINYINT(1)                           NOT NULL DEFAULT 1 COMMENT '是否接受压缩响应：1=接受 gzip/deflate/br 等；0=不接受',
    `prefer_http2_enabled`    TINYINT(1)                           NOT NULL DEFAULT 0 COMMENT '是否优先使用 HTTP/2（若客户端/服务端支持）',
    `retry_after_policy`      ENUM ('IGNORE','RESPECT','CLAMP')    NOT NULL DEFAULT 'RESPECT' COMMENT '对服务端 Retry-After 的处理：IGNORE=忽略；RESPECT=遵循；CLAMP=按上限截断',
    `retry_after_cap_millis`  INT                                  NULL COMMENT '当选择 RESPECT/CLAMP 时的最大等待上限（毫秒）',
    `idempotency_header_name` VARCHAR(64)                          NULL COMMENT '幂等性 Header 名称（如 Idempotency-Key），用于避免重复提交',
    `idempotency_ttl_seconds` INT                                  NULL COMMENT '幂等性键过期时间（秒），仅客户端/服务端支持时有效',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        ENUM ('DRAFT','PUBLISHED','RETIRED') NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON                                 NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED                      NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)                         NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED                      NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)                         NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED                      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)                        NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)                           NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_http_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_http_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_http_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_http_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='HTTP 策略配置：基础URL/请求头/超时/代理/Retry-After/幂等等策略；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 批量抓取与请求成型配置（Batching & Request Shaping）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_batching_cfg`;
CREATE TABLE `reg_prov_batching_cfg`
(
    `id`                         BIGINT UNSIGNED                      NOT NULL AUTO_INCREMENT COMMENT '主键：批量抓取与请求成型配置记录ID',
    `provenance_id`              BIGINT UNSIGNED                      NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                      ENUM ('SOURCE','TASK')               NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`                  ENUM ('harvest','update','backfill') NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`             TIMESTAMP(6)                         NOT NULL COMMENT '生效起始（含）',
    `effective_to`               TIMESTAMP(6)                         NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `detail_fetch_batch_size`    INT                                  NULL COMMENT '单次详情抓取的批大小（条数），为空则由应用使用默认',
    `endpoint_id`                BIGINT UNSIGNED                      NULL COMMENT '可选：关联端点定义 → reg_prov_endpoint_def(id)',
    `credential_name`            VARCHAR(64)                          NULL COMMENT '可选：关联凭证逻辑名，用于细化控制',
    `ids_param_name`             VARCHAR(64)                          NULL COMMENT '批详情请求中，ID 列表的参数名；为空则由端点或应用决定',
    `ids_join_delimiter`         VARCHAR(8)                           NULL     DEFAULT ',' COMMENT 'ID 列表拼接的分隔符（如 , 或 +）',
    `max_ids_per_request`        INT                                  NULL COMMENT '每个 HTTP 请求允许携带的 ID 最大数量（硬上限）',
    `prefer_compact_payload`     TINYINT(1)                           NOT NULL DEFAULT 1 COMMENT '是否尽量压缩请求体（如去掉冗余空白）',
    `payload_compress_strategy`  ENUM ('NONE','GZIP')                 NOT NULL DEFAULT 'NONE' COMMENT '请求体压缩策略：NONE=不压缩；GZIP=gzip 压缩',
    `app_parallelism_degree`     INT                                  NULL COMMENT '应用层并行请求数：限制整体并发度',
    `per_host_concurrency_limit` INT                                  NULL COMMENT '每主机并发上限：限制与同一主机的并发连接数',
    `http_conn_pool_size`        INT                                  NULL COMMENT '连接池大小：HTTP 连接复用池的容量（客户端支持时生效）',
    `backpressure_strategy`      ENUM ('BLOCK','DROP','YIELD')        NOT NULL DEFAULT 'BLOCK' COMMENT '背压策略：BLOCK=阻塞等待；DROP=丢弃；YIELD=让出/降速',
    `request_template_json`      JSON                                 NULL COMMENT '请求成型模板：用于将内部字段映射到 query/body 的规则 JSON',

    `task_type_key`              VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`           ENUM ('DRAFT','PUBLISHED','RETIRED') NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`             JSON                                 NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                 TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                 BIGINT UNSIGNED                      NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                         NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                 BIGINT UNSIGNED                      NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                         NULL COMMENT '更新人姓名',
    `version`                    BIGINT UNSIGNED                      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)                        NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                    TINYINT(1)                           NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),
    UNIQUE KEY `uk_reg_prov_batching_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_batching_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_batching_cfg__by_ep_cred` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id`,
                                                 `credential_name`),
    KEY `idx_reg_prov_batching_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                             DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='批量抓取与请求成型配置：控制详情批量、ID 拼接、并行与背压、请求模板与压缩等；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 重试与退避配置（Retry & Backoff Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_retry_cfg`;
CREATE TABLE `reg_prov_retry_cfg`
(
    `id`                      BIGINT UNSIGNED                                  NOT NULL AUTO_INCREMENT COMMENT '主键：重试与退避配置记录ID',
    `provenance_id`           BIGINT UNSIGNED                                  NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   ENUM ('SOURCE','TASK')                           NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`               ENUM ('harvest','update','backfill')             NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`          TIMESTAMP(6)                                     NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)                                     NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `max_retry_times`         INT                                              NULL COMMENT '最大重试次数：为空则使用应用默认；0 表示不重试',
    `backoff_policy_type`     ENUM ('FIXED','EXP','EXP_JITTER','DECOR_JITTER') NOT NULL DEFAULT 'EXP_JITTER' COMMENT '退避策略：FIXED=固定；EXP=指数；EXP_JITTER=指数+抖动；DECOR_JITTER=装饰性抖动',
    `initial_delay_millis`    INT                                              NULL COMMENT '首个重试的延迟（毫秒）',
    `max_delay_millis`        INT                                              NULL COMMENT '单次重试的最大延迟（毫秒）',
    `exp_multiplier_value`    DOUBLE                                           NULL COMMENT '指数退避的乘数因子（如 2.0）',
    `jitter_factor_ratio`     DOUBLE                                           NULL COMMENT '抖动系数（0~1）：随机扰动的幅度',
    `retry_http_status_json`  JSON                                             NULL COMMENT '可重试的 HTTP 状态码列表（JSON 数组，如 [429,500,503]）',
    `giveup_http_status_json` JSON                                             NULL COMMENT '直接放弃的 HTTP 状态码列表（JSON 数组）',
    `retry_on_network_error`  TINYINT(1)                                       NOT NULL DEFAULT 1 COMMENT '网络错误是否重试：1=重试；0=不重试',
    `circuit_break_threshold` INT                                              NULL COMMENT '断路器阈值：连续失败次数达到该值后短路',
    `circuit_cooldown_millis` INT                                              NULL COMMENT '断路器冷却时间（毫秒）：过后允许半开探测',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        ENUM ('DRAFT','PUBLISHED','RETIRED')             NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON                                             NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)                                     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED                                  NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)                                     NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)                                     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED                                  NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)                                     NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED                                  NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)                                    NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)                                       NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_retry_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_retry_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_retry_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_retry_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='重试与退避配置：定义可重试次数、退避与抖动、网络错误策略以及断路器阈值与冷却时间；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 限流与并发配置（Rate Limit & Concurrency）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_rate_limit_cfg`;
CREATE TABLE `reg_prov_rate_limit_cfg`
(
    `id`                         BIGINT UNSIGNED                          NOT NULL AUTO_INCREMENT COMMENT '主键：限流与并发配置记录ID',
    `provenance_id`              BIGINT UNSIGNED                          NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                      ENUM ('SOURCE','TASK')                   NOT NULL DEFAULT 'SOURCE' COMMENT '配置作用域：SOURCE=按来源通用；TASK=限定到特定任务类型',
    `task_type`                  ENUM ('harvest','update','backfill')     NULL COMMENT '任务类型：当 scope=TASK 时需填写',

    `effective_from`             TIMESTAMP(6)                             NOT NULL COMMENT '生效起始（含）',
    `effective_to`               TIMESTAMP(6)                             NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `rate_tokens_per_second`     INT                                      NULL COMMENT '全局 QPS（令牌生成速率，令牌/秒）；为空表示使用应用默认或不限制',
    `burst_bucket_capacity`      INT                                      NULL COMMENT '突发桶容量（最大瞬时令牌数），用于吸收短时峰值',
    `max_concurrent_requests`    INT                                      NULL COMMENT '全局并发请求上限（连接/请求数），为空表示默认',
    `per_credential_qps_limit`   INT                                      NULL COMMENT '按密钥的 QPS 上限：多把密钥时可分摊流量',
    `bucket_granularity_scope`   ENUM ('GLOBAL','PER_KEY','PER_ENDPOINT') NOT NULL DEFAULT 'GLOBAL' COMMENT '令牌桶粒度：GLOBAL=整体；PER_KEY=按密钥；PER_ENDPOINT=按端点',
    `smoothing_window_millis`    INT                                      NULL COMMENT '平滑窗口（毫秒）：用于平滑令牌发放与计数',
    `respect_server_rate_header` TINYINT(1)                               NOT NULL DEFAULT 1 COMMENT '是否遵循服务端速率响应头（如 X-RateLimit-*）：1=遵循；0=忽略',
    `endpoint_id`                BIGINT UNSIGNED                          NULL COMMENT '可选：关联端点定义 → reg_prov_endpoint_def(id)',
    `credential_name`            VARCHAR(64)                              NULL COMMENT '可选：关联凭证逻辑名，用于细化限流',

    `task_type_key`              VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`           ENUM ('DRAFT','PUBLISHED','RETIRED')     NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`             JSON                                     NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                 TIMESTAMP(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                 BIGINT UNSIGNED                          NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                             NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                 BIGINT UNSIGNED                          NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                             NULL COMMENT '更新人姓名',
    `version`                    BIGINT UNSIGNED                          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)                            NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                    TINYINT(1)                               NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),
    UNIQUE KEY `uk_reg_prov_rate_limit_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_rate_limit_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_rate_limit_cfg__by_ep_cred` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id`,
                                                   `credential_name`),
    KEY `idx_reg_prov_rate_limit_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                               DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='限流与并发配置：配置 QPS/突发/并发与桶粒度（全局/按密钥/按端点），可结合服务端速率响应头进行自适应；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 鉴权/密钥配置（Credentials / Authentication）
 * 说明：为来源或任务配置多把凭证；可选绑定到具体端点。允许重叠有效期，以支持轮换与平滑切换。
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_credential`;
CREATE TABLE `reg_prov_credential`
(
    `id`                      BIGINT UNSIGNED                                     NOT NULL AUTO_INCREMENT COMMENT '主键：凭证记录ID',
    `provenance_id`           BIGINT UNSIGNED                                     NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   ENUM ('SOURCE','TASK')                              NOT NULL DEFAULT 'SOURCE' COMMENT '凭证作用域：SOURCE=按来源；TASK=限定到特定任务类型',
    `task_type`               ENUM ('harvest','update','backfill')                NULL COMMENT '任务类型：当 scope=TASK 时需填写',
    `endpoint_id`             BIGINT UNSIGNED                                     NULL COMMENT '可选外键：若该凭证仅用于某个端点，则指定端点配置ID → reg_prov_endpoint_def(id)',

    `credential_name`         VARCHAR(64)                                         NOT NULL COMMENT '凭证标签：用于人类可读标识与应用优先级选择（可与端点的 credential_hint_name 配合）',
    `auth_type`               ENUM ('API_KEY','BEARER','BASIC','OAUTH2','CUSTOM') NOT NULL COMMENT '鉴权类型：API_KEY=静态Key；BEARER=Bearer令牌；BASIC=Basic认证；OAUTH2=OAuth2；CUSTOM=自定义',
    `inbound_location`        ENUM ('HEADER','QUERY','BODY')                      NOT NULL DEFAULT 'HEADER' COMMENT '凭证放置位置：HTTP Header / Query 参数 / 请求体',
    `credential_field_name`   VARCHAR(128)                                        NULL COMMENT '凭证字段名：如 Authorization / api_key / access_token',
    `credential_value_prefix` VARCHAR(64)                                         NULL COMMENT '凭证值前缀：如 "Bearer "，会拼接在凭证值之前',
    `credential_value_ref`    VARCHAR(256)                                        NULL COMMENT '凭证值引用（如 KMS 密钥名/路径），不落明文',
    `basic_username_ref`      VARCHAR(256)                                        NULL COMMENT 'Basic 认证用户名引用',
    `basic_password_ref`      VARCHAR(256)                                        NULL COMMENT 'Basic 认证密码引用',

    /* OAuth2 客户端凭证（全部使用引用字段） */
    `oauth_token_url`         VARCHAR(512)                                        NULL COMMENT 'OAuth2 Token 端点 URL（client credentials 等流程）',
    `oauth_client_id_ref`     VARCHAR(256)                                        NULL COMMENT 'OAuth2 客户端 ID 的引用',
    `oauth_client_secret_ref` VARCHAR(256)                                        NULL COMMENT 'OAuth2 客户端密钥的引用',
    `oauth_scope`             VARCHAR(512)                                        NULL COMMENT 'OAuth2 请求的 scope（以空格或逗号分隔）',
    `oauth_audience`          VARCHAR(512)                                        NULL COMMENT 'OAuth2 audience/资源标识',
    `extra_json`              JSON                                                NULL COMMENT '自定义扩展字段：如签名算法、HMAC 盐、额外 Header 模板等（由应用解释）',

    `effective_from`          TIMESTAMP(6)                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生效起始（含）；允许多把凭证区间重叠以支持轮换',
    `effective_to`            TIMESTAMP(6)                                        NULL COMMENT '生效结束（不含）；NULL 表示长期有效',
    `is_default_preferred`    TINYINT(1)                                          NOT NULL DEFAULT 0 COMMENT '是否标记为默认/首选：1=默认（用于同维度多把时的优先选择）；0=普通',

    /* 生成列与弱互斥唯一键支持 */
    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `endpoint_id_key`         BIGINT AS (IFNULL(`endpoint_id`, 0)) STORED COMMENT '生成列：端点ID的统一键（NULL→0）用于唯一约束',
    `preferred_1`             CHAR(1) AS (CASE WHEN `is_default_preferred` = 1 THEN 'Y' ELSE NULL END) STORED COMMENT '生成列：默认优先=Y（NULL 可重复）',

    `lifecycle_status`        ENUM ('DRAFT','PUBLISHED','RETIRED')                NOT NULL DEFAULT 'PUBLISHED' COMMENT '生命周期：读侧仅取 PUBLISHED',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON                                                NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED                                     NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)                                        NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED                                     NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)                                        NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED                                     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)                                       NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)                                          NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_credential__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_credential__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),

    KEY `idx_reg_prov_credential__dim` (`provenance_id`, `scope`, `task_type`, `endpoint_id`, `credential_name`),
    KEY `idx_reg_prov_credential__effective` (`effective_from`, `effective_to`),
    KEY `idx_reg_prov_credential__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id` DESC),
    UNIQUE KEY `uk_reg_prov_credential__preferred_one` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id_key`,
                                                        `preferred_1`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='鉴权/密钥配置：凭证以“引用”存储（不落明文）；支持端点绑定与重叠有效期；弱互斥保证同维度默认首选唯一。';
```
