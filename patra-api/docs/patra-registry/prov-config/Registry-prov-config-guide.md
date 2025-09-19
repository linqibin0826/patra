# Papertrace Registry · Provenance Config Guide

导航： [体系总览](../README.md) ｜ 同域： [Prov Reference](Registry-prov-config-reference.md) ｜ [Prov Ops](Registry-prov-config-ops.md) ｜ [Prov Examples](Registry-prov-config-examples.md)

## 目录
- [1. 摘要（Executive Summary）](#sec-1)
- [2. 背景与总体设计](#sec-2)
  - [2.1 业务背景](#sec-2-1)
  - [2.2 设计原则与不做的事](#sec-2-2)
  - [2.3 表与关系（概览）](#sec-2-3)
  - [2.4 作用域与时间模型](#sec-2-4)
  - [2.5 技术选型与实现边界](#sec-2-5)
  - [2.6 使用方式（读写路径概要）](#sec-2-6)
  - [2.7 示例预览（PubMed / Crossref，简版）](#sec-2-7)
- [3. 作用域与优先级模型](#sec-3)
  - [3.1 作用域模型（Scope Model）](#sec-3-1)
  - [3.2 时间区间模型（Effective Interval）](#sec-3-2)
  - [3.3 维度优先级与决策树（Deterministic Selection）](#sec-3-3)
  - [3.4 字段覆盖与合并顺序（仅端点级覆盖，非全局字段合并）](#sec-3-4)
  - [3.5 凭证选择优先级（`reg_prov_credential`）](#sec-3-5)
  - [3.6 多维度聚合：装配“运行合同”（Execution Contract）](#sec-3-6)
  - [3.7 灰度切换与回滚（运营流程）](#sec-3-7)
  - [3.8 异常与兜底策略](#sec-3-8)
  - [3.9 最佳实践小结（可落地）](#sec-3-9)


## <a id="sec-1"></a> 1. 摘要（Executive Summary）

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

    * 领域层只依赖 **端口（Ports）/仓储（Repositories）** 抽象；
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
> (provenance_id, scope_code, task_type, effective_from, window_mode_code, window_size_value, window_size_unit_code,
>  overlap_value, overlap_unit_code, offset_type_code, default_date_field_name)
> VALUES ( (SELECT id FROM reg_provenance WHERE provenance_code='pubmed'),
>          'TASK','update','2025-01-01','SLIDING',1,'DAY',1,'DAY','DATE','EDAT');
>
> -- 3) 为 Crossref harvest 配置游标分页（cursor）
> INSERT INTO reg_prov_pagination_cfg
> (provenance_id, scope_code, task_type, effective_from, pagination_mode_code,
>  cursor_param_name, next_cursor_jsonpath)
> VALUES ( (SELECT id FROM reg_provenance WHERE provenance_code='crossref'),
>          'TASK','harvest','2025-01-01','CURSOR','cursor','$.message.next-cursor');
> ```

---


## <a id="sec-2"></a> 2. 背景与总体设计

### <a id="sec-2-1"></a> 2.1 业务背景

* 医学文献生态存在多个主流数据源（如 **PubMed**、**Crossref**），**API 风格差异大**：增量字段命名不同、分页/游标机制不同、端点与
  HTTP 约束不同、配额与限流策略不同、鉴权方式也不同。
* 采集团队需要一个**统一的配置模型**来描述这些差异，做到\*\*“新增/切换数据源不改代码或少改代码”\*\*，同时满足：

    * **可灰度**：支持在不下线的情况下切换配置（新增新区间记录→切流）；
    * **可并存**：同维度可并存多条历史配置（只要同一时间不冲突）；
    * **可观测**：字段直观、命名清晰、便于排障与对账；
    * **可扩展**：为少量“供应商特有参数”预留 JSON 扩展位；
    * **跨任务差异**：同一数据源在 `harvest | update | backfill` 的策略可不同（窗口、分页、重试、限流等）。

### <a id="sec-2-2"></a> 2.2 设计原则与不做的事

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

### <a id="sec-2-3"></a> 2.3 表与关系（概览）

* **主数据表**

    * `reg_provenance`：登记来源（`provenance_code`、`base_url_default`、`timezone_default`…）。
* **配置/定义表（均含 `provenance_id` → `reg_provenance.id`）**

    * `reg_prov_endpoint_def`：端点名称、用途（SEARCH/DETAIL/TOKEN…）、HTTP 方法、路径模板、默认 Query/Body；端点级参数名（如
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
      `(provenance_id, scope_code, task_type_key, effective_from)` 与查询过滤。

> **ER 关系（文字版）**
> `reg_provenance (1) ──< (N) reg_prov_endpoint_def`
> `reg_provenance (1) ──< (N) reg_prov_*_cfg`
> `reg_prov_endpoint_def (1) ──< (0..N) reg_prov_credential`（可选）
> 运行时：应用分别从各表**各取一条当前生效**（优先 TASK → 回退 SOURCE），再拼成“执行合同”。

### <a id="sec-2-4"></a> 2.4 作用域与时间模型

* **作用域**

    * `SOURCE`：来源级通用策略；
    * `TASK`：来源 + 任务类型（`harvest | update | backfill`）的特化策略；
    * 读取规则：**若存在 TASK 级**，取 TASK；**否则**取 SOURCE；**不进行字段级 merge**。
* **时间模型**

    * 区间 `[effective_from, effective_to)`；`effective_to` 可为 `NULL`（表示“截至未来”）；
    * **不重叠由应用层保证**（建表时无触发器/CHECK）；
    * 灰度切换：**先插入新记录（新起点），再关闭旧记录**（写入 `effective_to`），观察后清理。

### <a id="sec-2-5"></a> 2.5 技术选型与实现边界

* **数据库**：MySQL 8.0 / InnoDB / `utf8mb4_0900_ai_ci`；
* **结构化为主、JSON 为辅**：端点默认参数、请求模板、状态码列表等用 `JSON` 存储；
* **索引**：覆盖 `(provenance_id, scope_code, task_type_key, effective_from)` 与常用维度（如端点用途、时间上界）；
* **应用层**（建议栈）：Java 21 + Spring Boot 3.2.x + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0 +
  MyBatis-Plus 3.5.12 + Jackson。

    * **Hexagonal**：适配器（REST/MQ/调度）→ 应用服务用例（CQRS）→ 领域（配置装配）→ 仓储（MyBatis-Plus 映射到 `reg_prov_*`）。
    * **读侧缓存**：可将“当前生效配置”以 `(provenance_code, task_type)` 为 Key 做短TTL缓存，降低 DB 压力。

### <a id="sec-2-6"></a> 2.6 使用方式（读写路径概要）

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
           WHERE provenance_id=@pid AND scope_code='TASK' AND task_type='update'
           AND effective_from<=@now AND (effective_to IS NULL OR effective_to>@now)
           ORDER BY effective_from DESC, id DESC
           LIMIT 1)
           UNION ALL
           (SELECT *
           FROM reg_prov_pagination_cfg
           WHERE provenance_id=@pid AND scope_code='SOURCE'
           AND effective_from<=@now AND (effective_to IS NULL OR effective_to>@now)
           ORDER BY effective_from DESC, id DESC
           LIMIT 1)
           LIMIT 1;      
  ```

    * 应用侧按此模式对**各维度**各取一条，组装为一次运行的“执行合同”。

### <a id="sec-2-7"></a> 2.7 示例预览（PubMed / Crossref，简版）

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
-- PubMed: ESearch（检索）与 EFetch（详情，规范 code=DETAIL，legacy=FETCH）
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope_code, task_type, endpoint_name, effective_from,
 endpoint_usage_code, http_method_code, path_template, default_query_params, request_content_type, is_auth_required)
VALUES (@pubmed_id, 'TASK', 'update', 'esearch', '2025-01-01', 'SEARCH', 'GET', '/eutils/esearch.fcgi',
        JSON_OBJECT('db', 'pubmed', 'retmode', 'json'), 'application/json', 0),
       (@pubmed_id, 'TASK', 'update', 'efetch', '2025-01-01', 'DETAIL', 'GET', '/eutils/efetch.fcgi',
        JSON_OBJECT('db', 'pubmed', 'retmode', 'xml'), 'application/xml', 0);

-- Crossref: works 搜索端点（cursor-based）
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope_code, task_type, endpoint_name, effective_from,
 endpoint_usage_code, http_method_code, path_template, request_content_type, is_auth_required)
VALUES (@crossref_id, 'TASK', 'harvest', 'works', '2025-01-01', 'SEARCH', 'GET', '/works',
        'application/json', 0);

-- 3) 时间窗口与增量指针
-- PubMed update：按天滑动 + 1 天重叠；DATE 指针默认 EDAT
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from,
 window_mode_code, window_size_value, window_size_unit_code, overlap_value, overlap_unit_code,
 offset_type_code, default_date_field_name)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01',
        'SLIDING', 1, 'DAY', 1, 'DAY',
        'DATE', 'EDAT');

-- Crossref harvest：按天滑动；DATE 指针使用 indexed
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from,
 window_mode_code, window_size_value, window_size_unit_code,
 offset_type_code, default_date_field_name)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01',
        'SLIDING', 1, 'DAY',
        'DATE', 'indexed');

-- 4) 分页/游标
-- PubMed：页码分页
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from,
 pagination_mode_code, page_size_value, page_number_param_name, page_size_param_name, start_page_number)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01',
        'PAGE_NUMBER', 100, 'page', 'retmax', 1);

-- Crossref：cursor 分页，下一游标在 $.message.next-cursor
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from,
 pagination_mode_code, cursor_param_name, next_cursor_jsonpath)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01',
        'CURSOR', 'cursor', '$.message["next-cursor"]');

-- 5) HTTP 策略（示例：设置 UA / 超时）
INSERT INTO reg_prov_http_cfg
(provenance_id, scope_code, task_type, effective_from,
 default_headers_json, timeout_connect_millis, timeout_read_millis)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01',
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0', 'From', 'ops@example.com'), 2000, 10000),
       (@crossref_id, 'SOURCE', NULL, '2025-01-01',
        JSON_OBJECT('User-Agent', 'PapertraceHarvester/1.0', 'mailto', 'ops@example.com'), 2000, 12000);
```


## <a id="sec-3"></a> 3. 作用域与优先级模型

本章给出**统一的“配置选取与合并”规则**，确保在多来源（Provenance）、多任务（`harvest|update|backfill`）、多时间区间并存的情况下，应用端能
**稳定、可预期**地装配出“当前生效”的采集运行合同（端点 + HTTP + 窗口/指针 + 分页/游标 + 批量 + 重试 + 限流 + 凭证）。

---

### <a id="sec-3-1"></a> 3.1 作用域模型（Scope Model）

* **字段**（所有 `reg_prov_*_cfg` 与 `reg_prov_endpoint_def` 都具备）

    * `provenance_id`：归属来源（FK → `reg_provenance.id`）
    * `scope_code ∈ {SOURCE, TASK}`：作用域
    * `task_type ∈ {harvest, update, backfill} | NULL`：当 `scope_code='TASK'` 时**必须**设置；`scope_code='SOURCE'` 时建议置
      `NULL`
    * `task_type_key`：生成列，`IFNULL(task_type,'ALL')`，仅用于索引与筛选
* **选择意图**

    * **SOURCE 级**：来源通用策略（默认）
    * **TASK 级**：来源+任务的特化策略（覆盖 SOURCE 级，但**不是字段级合并**，见 [§3.3](#sec-3-3)）

> 约束不在数据库层执行；由应用层保证：
>
> * `scope_code='TASK'` ⇒ `task_type IS NOT NULL`
> * `scope_code='SOURCE'` ⇒ `task_type IS NULL`（推荐）

---

### <a id="sec-3-2"></a> 3.2 时间区间模型（Effective Interval）

* **字段**：`effective_from TIMESTAMP(6)`、`effective_to TIMESTAMP(6) NULL`
* **语义**：区间采用 **半开区间** `[effective_from, effective_to)`；`effective_to IS NULL` 表示持续有效
* **不重叠**：数据库**不做**触发器/CHECK；由应用在写入与发布流程中保证
* **冲突检测 SQL（参考）**：同维度新增前可做一次交集检查

  ```sql
  -- :pid, :scope_code, :task_type_key, :from, :to
  SELECT 1
  FROM reg_prov_pagination_cfg
  WHERE provenance_id = :pid
    AND scope_code = :scope_code
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

### <a id="sec-3-3"></a> 3.3 维度优先级与决策树（Deterministic Selection）

**目标**：对于每个维度（端点定义/窗口/分页/HTTP/批量/重试/限流/凭证），**恰好选出 1 条“当前生效记录”**。

#### <a id="sec-3-3-1"></a> 3.3.1 基本决策树（除凭证外）

以“分页维度”为例，其他维度同理（仅换表名）：

1. **锁定来源**：`provenance_id` = 通过 `reg_provenance.provenance_code` 查得的 `id`
2. **时间过滤**：`effective_from <= now < COALESCE(effective_to, +∞)`
3. **任务优先**：

#### <a id="sec-3-3-3"></a> 3.3.3 字段合并与优先级总则

总则：除“端点默认 Query/Body”与“HTTP Headers”在应用层进行键级合并外，其他字段不做字段级覆盖。

- 端点分页参数名：`endpoint_def.*_param_name` > `pagination_cfg.*` > 应用默认。
- BaseURL：`http_cfg.base_url_override` > `provenance.base_url_default` > 应用默认。
- Headers 合并顺序：`http_cfg.default_headers_json` 为基底，运行时请求头覆盖同名键，发送前删除值为 NULL 的键。
- 时间窗口/指针：TASK 级命中优先，其次 SOURCE 级；仅取单条当前生效记录。
- 凭证选择链：端点绑定优先，其次全局；TASK 优先，其次 SOURCE；时间过滤；默认标记优先；按 `effective_from DESC, id DESC`
  决定并列；如需多把，取前 N 把供应用轮询/加权。
- NULL 语义：字段为 NULL 表示“由应用使用默认值”。

    * 若传入了 `task_type`：先在 `scope_code='TASK' AND task_type=:task_type` 中选；若没有结果，再回退
      `scope_code='SOURCE'`
    * 若未传入 `task_type`：直接走 `scope_code='SOURCE'`

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
   AND scope_code = 'TASK'
   AND task_type = :taskType
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND scope_code = 'SOURCE'
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

#### <a id="sec-3-3-2"></a> 3.3.2 端点定义的选择（`reg_prov_endpoint_def`）

在以上 1\~5 的基础上，多了端点维度过滤：

* **按用途**：`endpoint_usage_code IN ('SEARCH','DETAIL','TOKEN',...)`
* **按名称**：如明确指定 `endpoint_name`，则在同一用途下**精确匹配**
* **端点级覆盖**：若选出的端点记录含 `page_param_name`/`cursor_param_name` 等，则这些**仅对该端点请求**优先生效（见 [§3.4](#sec-3-4)）

> **PubMed 例**：
>
> * `endpoint_usage_code='SEARCH'` ⇒ 选出 `esearch` 当前生效记录
> * `endpoint_usage_code='DETAIL'` ⇒ 选出 `efetch` 当前生效记录

---

### <a id="sec-3-4"></a> 3.4 字段覆盖与合并顺序（仅端点级覆盖，非全局字段合并）

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

### <a id="sec-3-5"></a> 3.5 凭证选择优先级（`reg_prov_credential`）

凭证与其他维度不同：凭证可能**并存多把有效**，用于轮换/分流。建议优先级如下：

1. **端点绑定优先**：若当前请求明确绑定到某端点（或端点用途），则优先筛选 `endpoint_id = 该端点.id` 的凭证；若无绑定，再用
   `endpoint_id IS NULL` 的全局凭证
2. **作用域优先**：任务内优先 `scope_code='TASK' AND task_type=:taskType`，否则 `scope_code='SOURCE'`
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
   AND lifecycle_status_code = 'ACTIVE'
   AND deleted = 0
   AND (@endpoint_id IS NOT NULL AND endpoint_id = @endpoint_id)
   AND ((scope_code = 'TASK' AND task_type = @taskType) OR (scope_code = 'SOURCE' AND @taskType IS NULL) OR
        (scope_code = 'SOURCE'))
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_credential
 WHERE provenance_id = @pid
   AND lifecycle_status_code = 'ACTIVE'
   AND deleted = 0
   AND endpoint_id IS NULL
   AND ((scope_code = 'TASK' AND task_type = @taskType) OR (scope_code = 'SOURCE' AND @taskType IS NULL) OR
        (scope_code = 'SOURCE'))
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

> **多把负载均衡**：把 `LIMIT 1` 换成 `LIMIT N`，按排序获取前 N 把供应用轮询或加权；并结合健康探测做熔断。

---

### <a id="sec-3-6"></a> 3.6 多维度聚合：装配“运行合同”（Execution Contract）

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
               AND scope_code = 'TASK'
               AND task_type = 'update'
               AND endpoint_usage_code = 'SEARCH'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND scope_code = 'SOURCE'
               AND endpoint_usage_code = 'SEARCH'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_pagination_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_http_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_window_offset_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_batching_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_retry_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND scope_code = 'TASK'
            AND task_type = 'update'
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
          ORDER BY effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_rate_limit_cfg
          WHERE provenance_id = @pid
            AND scope_code = 'SOURCE'
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
            AND lifecycle_status_code = 'ACTIVE'
            AND deleted = 0
            AND endpoint_id = (SELECT id FROM ep LIMIT 1)
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
            AND ((scope_code = 'TASK' AND task_type = 'update') OR scope_code = 'SOURCE')
          ORDER BY is_default_preferred DESC, effective_from DESC, id DESC
          LIMIT 1)
         UNION ALL
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status_code = 'ACTIVE'
            AND deleted = 0
            AND endpoint_id IS NULL
            AND effective_from <= @now
            AND (effective_to IS NULL OR effective_to > @now)
            AND ((scope_code = 'TASK' AND task_type = 'update') OR scope_code = 'SOURCE')
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

> **Crossref（harvest + cursor）** 仅需把任务与用途替换为 `task_type='harvest'`、`endpoint_usage_code='SEARCH'`，分页维度取
`pagination_mode_code='CURSOR'` 的当前生效记录即可。

---

### <a id="sec-3-7"></a> 3.7 灰度切换与回滚（运营流程）

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

### <a id="sec-3-8"></a> 3.8 异常与兜底策略

| 场景                     | 表现/风险         | 兜底建议                                                         |
|------------------------|---------------|--------------------------------------------------------------|
| **缺少 TASK 级配置**        | 回退到了 SOURCE 级 | 允许；在告警与审计中记录“回退事件”                                           |
| **同一维度在同一时刻并存多条**      | 选取歧义          | 写入前做交集检查；运行时若确实并存，按 `effective_from DESC, id DESC` 选择并**报警** |
| **某维度完全缺失**            | 合同不完整         | 应用侧使用**默认值**；或禁用该任务并报警                                       |
| **端点未定义**              | 无法发出请求        | 返回配置错误；由运维补齐 `reg_prov_endpoint_def`                         |
| **凭证过期**               | 401/403 或配额归零 | 尝试下一把候选凭证；若全部不可用，降级为匿名（若允许）或暂停任务                             |
| **时钟漂移**               | 切换时刻不一致       | 统一从 DB 读 `UTC_TIMESTAMP()`；任务启动前刷新配置                         |
| **跨维度冲突**（如分页与端点参数名冲突） | 请求生成错误        | 遵循 [§3.4](#sec-3-4) 覆盖顺序；配置评审时静态检查冲突                                     |

---

### <a id="sec-3-9"></a> 3.9 最佳实践小结（可落地）

* 写侧：**先加新、再关旧**；所有修改先跑**预检 SQL**（交集检查、必填检查、依赖存在性检查）。
* 读侧：统一封装“取当前生效”的**通用函数**，并做**短 TTL 缓存**；一次运行内冻结配置。
* 合并：**只允许端点对少数参数名做覆盖**；其余“按维度整条取一条”，**不要字段级 merge**。
* 凭证：支持**多把候选** + **健康探测** + **熔断**；默认标记 + 最新优先。
* 时区：DB 存 UTC；展示/窗口对齐用 `reg_provenance.timezone_default` 或任务级 HTTP 配置（如需）。
