# Papertrace Registry · Provenance Ops / SQL Practices

导航： [体系总览](../README.md) ｜ 同域： [Prov Guide](Registry-prov-config-guide.md) ｜ [Prov Reference](Registry-prov-config-reference.md) ｜ [Prov Examples](Registry-prov-config-examples.md)

## 目录
- [1. 读写操作实践（SQL 模板与最佳实践）](#sec-1)
  - [1.1 写入与变更（新增配置、灰度切换、回滚）](#sec-1-1)
  - [1.2 幂等写入模板（Upsert/去重）](#sec-1-2)
  - [1.3 读取“当前生效配置”SQL 模板（单维度）](#sec-1-3)
  - [1.4 跨维度聚合查询（装配“运行合同”）](#sec-1-4)
  - [1.5 端点级覆盖的使用示例](#sec-1-5)
  - [1.6 凭证选择：单把 / 多把](#sec-1-6)
  - [1.7 诊断与运维 SQL（常用）](#sec-1-7)
  - [1.8 性能与一致性建议](#sec-1-8)
  - [1.9 常见错误与应对](#sec-1-9)
- [2. FAQ 与常见陷阱](#sec-2)
  - [2.1 为什么不在数据库做“区间不重叠”校验？](#sec-2-1)
  - [2.2 `SOURCE` 与 `TASK` 的关系是“覆盖合并”吗？](#sec-2-2)
  - [2.3 端点级覆盖与维度配置冲突时，谁优先？](#sec-2-3)
  - [2.4 PubMed 是“偏移量分页”，为什么模型里用 `PAGE_NUMBER`？](#sec-2-4)
  - [2.5 Crossref 的下一游标提取不到，怎么办？](#sec-2-5)
  - [2.6 如何避免“同一时刻多条配置同时生效”？](#sec-2-6)
  - [2.7 想“改起点时间”怎么办？可以 `UPDATE effective_from` 吗？](#sec-2-7)
  - [2.8 JSON 字段能当过滤条件用吗？](#sec-2-8)
  - [2.9 凭证存明文安全吗？](#sec-2-9)
  - [2.10 429（限流）或 503（拥塞）居高不下，优先调哪个表？](#sec-2-10)
  - [2.11 是否需要在库里保存“增量水位/游标”？](#sec-2-11)
  - [2.12 如何决定字段放“结构列”还是“JSON 扩展”？](#sec-2-12)
  - [2.13 应用端是否需要事务地写入多维度的变更？](#sec-2-13)
  - [2.14 用 `UNION ALL ... LIMIT 1` 选“当前生效”会不会拿到两行？](#sec-2-14)
  - [2.15 如何“时间回放”定位某个事故发生时的配置？](#sec-2-15)
  - [2.16 PubMed：为什么窗口覆盖 1 天还会有重复 PMID？](#sec-2-16)
  - [2.17 Crossref：全量拉取时 `total-results` 很大，是否要依赖它终止？](#sec-2-17)
  - [2.18 为什么有时 `endpoint_usage_code='DETAIL'`（legacy `FETCH`）不需要？](#sec-2-18)
  - [2.19 `effective_to` 需要长期保留 NULL 吗？](#sec-2-19)
  - [2.20 索引仍然慢，可能踩到哪些坑？](#sec-2-20)
  - [2.21 示例：一次“安全变更”的最小 SQL 序列？](#sec-2-21)
  - [2.22 有没有推荐的“配置冷启动”顺序？](#sec-2-22)
  - [2.23 多把凭证如何“加权分流”？](#sec-2-23)
  - [2.24 如何在不变更生产表结构的前提下扩展？](#sec-2-24)
  - [2.25 我应不应该在一张视图里把所有维度 join 好？](#sec-2-25)


## <a id="sec-1"></a> 1. 读写操作实践（SQL 模板与最佳实践）

### <a id="sec-1-1"></a> 1.1 写入与变更（新增配置、灰度切换、回滚）

#### <a id="sec-1-1-1"></a> 1.1.1 新增来源（Provenance）

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

#### <a id="sec-1-1-2"></a> 1.1.2 新增配置（以分页为例）：写前冲突预检

> 以 `reg_prov_pagination_cfg` 为例；其他 `reg_prov_*` 同理，替换表名即可。
> 目标：同维度（`provenance_id, scope_code, task_type`）内，**新区间**与既有区间**不得重叠**（业务规则在应用层保证）。

```sql
-- 约束：:from ≤ :to（若 :to 非空）；校验由应用层做。
-- 交集判定：存在返回1表示有冲突。
SELECT 1
FROM reg_prov_pagination_cfg
WHERE provenance_id = @pid
  AND scope_code = :scope_code
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

#### <a id="sec-1-1-3"></a> 1.1.3 灰度切换（Zero-Downtime）

> 策略：**先加新**（未来 T+Δ 生效），**后关旧**（观察期结束再收口），保证在途任务不受影响。

**步骤 A：插入新配置（未来生效）**

```sql
-- 以分页为例（PAGE_NUMBER 100/页，从1开始）
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 pagination_mode_code, page_size_value, page_number_param_name, page_size_param_name, start_page_number)
VALUES (@pid, :scope_code, :taskType, :newFrom, :newTo,
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
  AND scope_code = :scope_code
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

#### <a id="sec-1-1-4"></a> 1.1.4 快速回滚

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
              AND scope_code = :scope_code
              AND IFNULL(task_type, 'ALL') = IFNULL(:taskType, 'ALL')
              AND effective_from < (SELECT effective_from FROM reg_prov_pagination_cfg WHERE id = :newCfgId)
            ORDER BY effective_from DESC, id DESC
            LIMIT 1);
```

---

### <a id="sec-1-2"></a> 1.2 幂等写入模板（Upsert/去重）

> 由于表的唯一键为 `(provenance_id, scope_code, task_type_key, [endpoint_name], effective_from)`，**一般不建议**直接
`ON DUPLICATE KEY` 覆盖 `effective_from`（可能破坏历史）。
> 推荐做**幂等插入**（若相同维度+起点已存在则更新非关键字段）。

**示例：端点定义幂等写**

```sql
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope_code, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage_code, http_method_code, path_template, default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@pid, :scope_code, :taskType, :endpointName, :from, :to,
        :usage, :method, :path, :defaultQueryJson, :defaultBodyJson,
        :contentType, :needAuth, :credHint,
        :pageParam, :sizeParam, :cursorParam, :idsParam)
ON DUPLICATE KEY UPDATE effective_to         = VALUES(effective_to),
                        endpoint_usage_code  = VALUES(endpoint_usage_code),
                        http_method_code     = VALUES(http_method_code),
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

### <a id="sec-1-3"></a> 1.3 读取“当前生效配置”SQL 模板（单维度）

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
   AND lifecycle_status_code = 'ACTIVE'
   AND deleted = 0
   AND scope_code = 'TASK'
   AND task_type = :taskType
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
(SELECT *
 FROM reg_prov_http_cfg
 WHERE provenance_id = @pid
   AND lifecycle_status_code = 'ACTIVE'
   AND deleted = 0
   AND scope_code = 'SOURCE'
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY effective_from DESC, id DESC
 LIMIT 1)
LIMIT 1;
```

**端点定义（按用途/名称）**

```sql
-- :usage in ('SEARCH','DETAIL','TOKEN','METADATA','PING','RATE')
WITH ep AS ((SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'TASK'
               AND task_type = :taskType
               AND endpoint_usage_code = :usage
               AND (:endpointName IS NULL OR endpoint_name = :endpointName)
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            UNION ALL
            (SELECT *
             FROM reg_prov_endpoint_def
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'SOURCE'
               AND endpoint_usage_code = :usage
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

### <a id="sec-1-4"></a> 1.4 跨维度聚合查询（装配“运行合同”）

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
     http AS ((SELECT *
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
     win AS ((SELECT *
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
     pg AS ((SELECT *
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
     bt AS ((SELECT *
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
     rt AS ((SELECT *
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
     rl AS ((SELECT *
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
     cred AS ((SELECT *
               FROM reg_prov_credential
               WHERE provenance_id = @pid
                 AND lifecycle_status_code = 'ACTIVE'
                 AND deleted = 0
                 AND endpoint_id = (SELECT id FROM ep LIMIT 1)
                 AND ((scope_code = 'TASK' AND task_type = 'update') OR scope_code = 'SOURCE')
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
                 AND ((scope_code = 'TASK' AND task_type = 'update') OR scope_code = 'SOURCE')
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

### <a id="sec-1-5"></a> 1.5 端点级覆盖的使用示例

**场景**：大多数端点用 `page=...&retmax=...`，但 `works` 端点要求 `rows=...`。

* 在 `reg_prov_endpoint_def` 为 `works` 端点填 `page_size_param_name='rows'`；
* 读取时优先使用端点级参数名，忽略分页维度中的 `page_size_param_name`。

---

### <a id="sec-1-6"></a> 1.6 凭证选择：单把 / 多把

**单把（首选）**

* 端点绑定优先 → `is_default_preferred=1` → 最新 `effective_from` → `id` 兜底。
* 参考 [§3.5](Registry-prov-config-guide.md#sec-3-5) 的 SQL。

**多把（并行/分流）**

* 将 `LIMIT 1` 改为 `LIMIT N`，按排序得到候选列表。
* 应用端做**轮询**或**加权**（结合健康探测与熔断）。

---

### <a id="sec-1-7"></a> 1.7 诊断与运维 SQL（常用）

**列出某来源在当前时刻“生效”的所有维度**

```sql
SELECT id
INTO @pid
FROM reg_provenance
WHERE provenance_code = :code;
SET @now = UTC_TIMESTAMP();

-- 以分页为例；其他维度同理
(SELECT 'pagination' AS cfg, id, scope_code, task_type, effective_from, effective_to
 FROM reg_prov_pagination_cfg
 WHERE provenance_id = @pid
   AND effective_from <= @now
   AND (effective_to IS NULL OR effective_to > @now)
 ORDER BY scope_code = 'TASK' DESC, effective_from DESC, id DESC
 LIMIT 1)
UNION ALL
-- 加上 window/http/batching/retry/rate/endpoint 等同样写法……
SELECT 'window', id, scope_code, task_type, effective_from, effective_to
FROM reg_prov_window_offset_cfg
WHERE provenance_id = @pid
  AND effective_from <= @now
  AND (effective_to IS NULL OR effective_to > @now)
ORDER BY scope_code = 'TASK' DESC, effective_from DESC, id DESC
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
                  AND a.scope_code = b.scope_code
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
  AND endpoint_usage_code = :usage
  AND effective_from <= UTC_TIMESTAMP()
  AND (effective_to IS NULL OR effective_to > UTC_TIMESTAMP())
ORDER BY scope_code = 'TASK' DESC, effective_from DESC, id DESC;
```

---

### <a id="sec-1-8"></a> 1.8 性能与一致性建议

* **索引命中**：查询条件顺序尽量贴合复合索引顺序（
  `provenance_id, scope_code, task_type_key, [endpoint_name], effective_from`
  ），并带上时间过滤。
* **一次运行内冻结**：作业启动时一次性读取并缓存“合同”，运行中不变（避免半途切换）。
* **幂等**：写入接口携带**明确的维度键**与 `effective_from`，避免随机生成时间造成误判。
* **事务**：灰度切换写入建议放在同一事务；多维度切换时先“加新”，观测后逐表“关旧”。
* **归档**：历史记录可定期归档到冷表/历史库；读取仅关注活跃与最近历史。
* **JSON**：只在少量扩展位使用；不要把**关键过滤维度**塞进 JSON。

---

### <a id="sec-1-9"></a> 1.9 常见错误与应对

| 错误        | 原因                                   | 快速修复                                     |
|-----------|--------------------------------------|------------------------------------------|
| 查不到“当前生效” | 区间写反或 `effective_from` 在未来           | 调整区间；或设置临时回退的 SOURCE 级配置                 |
| 任务/来源混淆   | `scope_code='TASK'` 但 `task_type` 为空 | 回填 `task_type`；或改成 `scope_code='SOURCE'` |
| 端点参数冲突    | 端点与分页维度同时配置了不同参数名                    | 遵循“端点 > 维度”的覆盖；清理不必要的字段                  |
| 频繁 429/限流 | 客户端限流过高或服务端配额不足                      | 下调 `rate_limit`/`parallelism`；或启用多把凭证分流  |
| 游标翻页失败    | `next_cursor_jsonpath` 不正确           | 调整提取路径；打印原始响应以定位                         |

---


## <a id="sec-2"></a> 2. FAQ 与常见陷阱

### <a id="sec-2-1"></a> 2.1 为什么不在数据库做“区间不重叠”校验？

**答**：

* 业务上需要**灰度切换**（新旧短暂重叠）与**回滚**（回开旧记录），这与严格的 DB 级约束天然冲突；
* MySQL 不提供原生“区间排他”索引；用触发器/锁模拟易带来**写入死锁**、**演进困难**；
* 我们采用**应用层预检 + 观察期策略**：先加新（未来 `effective_from`）、到点切流、再收口旧。

---

### <a id="sec-2-2"></a> 2.2 `SOURCE` 与 `TASK` 的关系是“覆盖合并”吗？

**答**：**不是。**

* 读取时先尝试 `TASK` 级（`scope_code='TASK' AND task_type=?`），没有才回退 `SOURCE`；
* **不会**把 `TASK` 与 `SOURCE` 两条记录按字段做合并（除了“端点级小范围覆盖”，见 [§3.4](Registry-prov-config-guide.md#sec-3-4)）；
* 字段为 `NULL` 的含义是“交给应用默认值”。

---

### <a id="sec-2-3"></a> 2.3 端点级覆盖与维度配置冲突时，谁优先？

**答**：**端点级参数名 > 维度配置 > 应用默认**。

* 例如：PubMed `esearch` 端点设置 `page_size_param_name='retmax'`，即便 `reg_prov_pagination_cfg.page_size_param_name`
  写成别名，**最终仍以端点为准**。
* 覆盖范围仅限少量**参数名类**字段（如 `page_param_name/cursor_param_name/ids_param_name`）。

---

### <a id="sec-2-4"></a> 2.4 PubMed 是“偏移量分页”，为什么模型里用 `PAGE_NUMBER`？

**答**：为了统一抽象。

* 应用层把页号换算为 `retstart=(page-1)*retmax`；
* 这样可与 Crossref 的 `CURSOR` 在同一套分页抽象下实现；
* 端点级可以覆盖参数名（`retmax`、`page`），但**偏移换算**在应用层。

### <a id="sec-2-5"></a> 2.5 Crossref 的下一游标提取不到，怎么办？

**答**：

* 通常是 `next_cursor_jsonpath` 配置不正确或服务端响应变化；
* 建议把整包响应样本入日志，核对路径（常见为 `$.message["next-cursor"]`）；
* 通过**新增一条** `reg_prov_pagination_cfg`（未来生效）修复路径，观察后关旧配置；
* 也可配置 `max_pages_per_execution` 做保护，避免无限循环。

---

### <a id="sec-2-6"></a> 2.6 如何避免“同一时刻多条配置同时生效”？

**答**：

* **写前预检**：使用“交集判定 SQL”（见 [§3.2](Registry-prov-config-guide.md#sec-3-2)）；
* **统一时钟**：所有判断用 DB 的 `UTC_TIMESTAMP()`；
* **一次运行内冻结**：作业启动时取一次“运行合同”，整个运行过程不再刷新配置，避免半途切换导致抖动。

---

### <a id="sec-2-7"></a> 2.7 想“改起点时间”怎么办？可以 `UPDATE effective_from` 吗？

**答**：不推荐直接改起点。

* 以时间点为“版本锚点”，**新建一条记录**代表新版本，旧记录通过 `effective_to` 关窗；
* 这样保留了历史轨迹，便于回溯与“时间穿越”诊断（见 [§2.15](Registry-prov-config-reference.md#sec-2-15)）。

---

### <a id="sec-2-8"></a> 2.8 JSON 字段能当过滤条件用吗？

**答**：不建议。

* JSON 仅作**扩展位**（默认参数、模板、状态码数组等）；
* 关键过滤键（如 `pagination_mode_code`、`endpoint_usage_code`、`scope_code/task_type`）必须是结构化列，并建索引；
* 如需对 JSON 做轻度查询，可在应用侧解析，或在后续演进中增加“**生成列 + 索引**”。

---

### <a id="sec-2-9"></a> 2.9 凭证存明文安全吗？

**答**：**不安全，不建议。**

* 建议在 `credential_value_plain` 存“**引用**”（如 `KMS_KEY:xyz`），实际密钥在安全服务中拉取；
* 轮换采用“**多把并存 + 默认标记**”，先加新后降级旧（见 [§2.4](Registry-prov-config-reference.md#sec-2-4)）；
* 对端点有专用配额时，使用 `endpoint_id` 绑定到具体端点。

---

### <a id="sec-2-10"></a> 2.10 429（限流）或 503（拥塞）居高不下，优先调哪个表？

**答**（优先级）：

1. `reg_prov_rate_limit_cfg`：降低 `rate_tokens_per_second` / `max_concurrent_requests`；
2. `reg_prov_retry_cfg`：用 `EXP_JITTER`，增大 `initial_delay_millis` / `max_delay_millis`，包含 429/5xx；
3. `reg_prov_batching_cfg`：减小 `detail_fetch_batch_size` 与并行度；
4. `reg_prov_http_cfg`：确保 `retry_after_policy_code='RESPECT'` 并设置上限。

> 采用“先加新→观察→关旧”的灰度节奏，不要直接覆盖旧配置。

---

### <a id="sec-2-11"></a> 2.11 是否需要在库里保存“增量水位/游标”？

**答**：**不在本库**。

* 本库只负责**静态配置**；
* 动态水位（窗口开始/结束、最近成功推进）、游标/断点应在**作业状态表**或**事件流水**里维护（通常由另一套运行时表承担）。

---

### <a id="sec-2-12"></a> 2.12 如何决定字段放“结构列”还是“JSON 扩展”？

**答**：

* **查询路径会用到的**（过滤、排序、唯一性）→ 结构列；
* **供应商特有且非高频过滤** → JSON；
* 无法确定时，优先保守：先结构化常用键，剩余放 JSON，避免未来加索引改表。

---

### <a id="sec-2-13"></a> 2.13 应用端是否需要事务地写入多维度的变更？

**答**：视规模决定。

* 单维度灰度：一个事务足够（预检→插入新→提交）；
* 多维度联动（比如分页+批量+限流一起调）：建议**分两步**：先新增未来生效的新配置（全部），到点后**逐表关旧**，并观察；
* 即便跨表不在同一事务，因“按时间点切换”，读侧也能拿到一致的“最新”组合。

---

### <a id="sec-2-14"></a> 2.14 用 `UNION ALL ... LIMIT 1` 选“当前生效”会不会拿到两行？

**答**：不会。

* 每个分支本身 `ORDER BY ... LIMIT 1`；外层 `UNION ALL ... LIMIT 1` 只取第一条（即 TASK 分支优先），若 TASK 分支没命中，才会落到
  SOURCE 分支；
* 这是**确定性**且可读性好的实现方式。

---

### <a id="sec-2-15"></a> 2.15 如何“时间回放”定位某个事故发生时的配置？

**答**：用“时间穿越”查询（Time-Travel-like）：

```sql
SET @T = '2025-08-31 23:59:00';
SELECT *
FROM reg_prov_pagination_cfg
WHERE provenance_id = @pid
  AND effective_from <= @T
  AND (effective_to IS NULL OR effective_to > @T)
ORDER BY scope_code = 'TASK' DESC, effective_from DESC, id DESC
LIMIT 1;
```

对其他维度亦然（换表名）。把 `@T` 设置为事故发生时刻，即可重建当时“当前生效”的组合。

---

### <a id="sec-2-16"></a> 2.16 PubMed：为什么窗口覆盖 1 天还会有重复 PMID？

**答**：

* 迟到/乱序（晚到的更新落在此前窗口）与 E-utilities 的数据刷新节奏都会导致重复；
* 解决：

    * 保持**窗口重叠**（例如 `overlap=1 DAY`）+ **水位滞后**；
    * 下游**按主键去重**（PMID 或 DOI），保持**幂等写入**；
    * 将“已处理清单”保存在运行库以避免重复消费。

---

### <a id="sec-2-17"></a> 2.17 Crossref：全量拉取时 `total-results` 很大，是否要依赖它终止？

**答**：不依赖。

* 以 `next-cursor` 的有无作为主终止条件；
* `max_pages_per_execution` 仅用于**安全刹车**；
* `total-results` 更像统计信息，可能变化。

---

### <a id="sec-2-18"></a> 2.18 为什么有时 `endpoint_usage_code='DETAIL'`（legacy `FETCH`）不需要？

**答**：

* 对 Crossref，`/works` 的 `items` 已经包含大量详情字段，可直接入库；
* 若业务仍需补齐（例如作者机构的丰富化），再使用 `work_by_doi`（`DETAIL`）作为**可选补充**；
* 因而 `DETAIL` 端点并非总是必需。

---

### <a id="sec-2-19"></a> 2.19 `effective_to` 需要长期保留 NULL 吗？

**答**：可以。

* NULL 表示“开放至未来”；
* 归档策略：把**完全过期的历史**（`effective_to < NOW() - 90d`）转移到 `_hist` 表，维持主表精简（见 [§2.9](Registry-prov-config-reference.md#sec-2-9)）。

---

### <a id="sec-2-20"></a> 2.20 索引仍然慢，可能踩到哪些坑？

**答**：

* WHERE 子句写法与复合索引顺序不匹配（应优先
  `provenance_id, scope_code, task_type_key, [endpoint_name], effective_from`）；
* 遗漏时间过滤（`effective_from <= now < effective_to`），导致范围扩大；
* 对列做函数运算（如 `DATE(effective_from)`），破坏索引；
* 使用 `%like%` 搜索 JSON 串；
* `ORDER BY` 不与索引顺序对齐，且缺少 `LIMIT`。

---

### <a id="sec-2-21"></a> 2.21 示例：一次“安全变更”的最小 SQL 序列？

**答**：以“把 Crossref rows 从 200 调整到 150”为例：

1. 预检无重叠；
2. 插入新分页配置（未来 10 分钟生效）：

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from, pagination_mode_code, page_size_value,
 page_size_param_name, cursor_param_name)
VALUES (@pid, 'TASK', 'harvest', UTC_TIMESTAMP() + INTERVAL 10 MINUTE, 'CURSOR', 150, 'rows', 'cursor');
```

3. 到点后观察 15 分钟；
4. 收口旧配置的 `effective_to = 新起点 + 15分钟观察`。

> 全程**无 UPDATE 起点**，历史可追溯，可随时回滚。

---

### <a id="sec-2-22"></a> 2.22 有没有推荐的“配置冷启动”顺序？

**答**：

1. `reg_provenance`（来源）
2. `reg_prov_endpoint_def`（至少 SEARCH）
3. `reg_prov_pagination_cfg`
4. `reg_prov_http_cfg`
5. `reg_prov_window_offset_cfg`
6. （如需详情）`reg_prov_batching_cfg`
7. `reg_prov_retry_cfg`、`reg_prov_rate_limit_cfg`
8. （可选）`reg_prov_credential`

> 然后用 [§1.4](Registry-prov-config-reference.md#sec-1-4) 的“跨维度聚合查询”一次性验证读侧装配是否正常。

---

### <a id="sec-2-23"></a> 2.23 多把凭证如何“加权分流”？

**答**：

* 读取时把候选凭证按优先级排序取 **前 N** 把；
* 应用侧实现**轮询/加权轮询**（基于配额或历史 429 比例），并结合健康探测做**熔断**；
* 若需要**按密钥限流**，在 `reg_prov_rate_limit_cfg.bucket_granularity_scope_code='PER_KEY'` 前提下，应用层把“当前凭证标识”作为限流桶
  Key。

---

### <a id="sec-2-24"></a> 2.24 如何在不变更生产表结构的前提下扩展？

**答**：

* 新增字段优先考虑放到对应表的 **JSON 扩展位**（如端点默认参数、HTTP 扩展头等）；
* 若会成为常用过滤键，则**增补结构列 + 索引**；
* 坚持“**维度化拆分** + **强语义命名**”，避免把所有参数堆进某一张胖表。

---

### <a id="sec-2-25"></a> 2.25 我应不应该在一张视图里把所有维度 join 好？

**答**：不建议在 DB 端做“总大视图”。

* 各维度都有**时间区间与优先级**，在视图中很难写成一个既高效又正确的通用逻辑；
* 推荐按 [§3.6](Registry-prov-config-guide.md#sec-3-6) / [§1.4](Registry-prov-config-reference.md#sec-1-4) 的模式由**应用端**拼装，或在读侧做**短 TTL 聚合缓存**。
