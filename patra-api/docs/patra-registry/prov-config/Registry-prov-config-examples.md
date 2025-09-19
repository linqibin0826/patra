# Papertrace Registry · Provenance Examples (PubMed + Crossref)

导航： [体系总览](../README.md) ｜ 同域： [Prov Guide](Registry-prov-config-guide.md) ｜ [Prov Reference](Registry-prov-config-reference.md) ｜ [Prov Ops](Registry-prov-config-ops.md)

## 目录
- [1. PubMed 示例（端到端）](#sec-1)
  - [1.1 背景与 API 速览（面向本库的映射）](#sec-1-1)
  - [1.2 主数据：登记 PubMed 来源](#sec-1-2)
  - [1.3 端点定义（搜索与详情）](#sec-1-3)
  - [1.4 时间窗口与增量指针（按天滑动 + 重叠）](#sec-1-4)
  - [1.5 分页/游标（页码模型 → retstart）](#sec-1-5)
  - [1.6 HTTP 策略（Headers/超时/Retry-After）](#sec-1-6)
  - [1.7 批量抓取与请求成型（EFetch 批量）](#sec-1-7)
  - [1.8 重试与退避（含断路器）](#sec-1-8)
  - [1.9 限流与并发（客户端令牌桶）](#sec-1-9)
  - [1.10 凭证（可选：API Key 提升配额）](#sec-1-10)
  - [1.11 组合查询：装配“PubMed / update”的当前生效配置](#sec-1-11)
  - [1.7 读侧封装模板（视图/存储过程）](#sec-1-7)
  - [1.12 运行片段：从“窗口”到“请求”的一步步](#sec-1-12)
- [2. Crossref 示例（端到端）](#sec-2)
  - [2.1 背景与 API 速览（面向本库的映射）](#sec-2-1)
  - [2.2 主数据：登记 Crossref 来源](#sec-2-2)
  - [2.3 端点定义（works 检索与 DOI 详情）](#sec-2-3)
  - [2.4 时间窗口与增量指针（按 indexed 滑动）](#sec-2-4)
  - [2.5 分页：CURSOR（游标）](#sec-2-5)
  - [2.6 HTTP 策略（User-Agent / mailto）](#sec-2-6)
  - [2.7 批量抓取与请求成型（详情为单条）](#sec-2-7)
  - [2.8 重试与退避（尊重 Retry-After）](#sec-2-8)
  - [2.9 限流与并发（按端点或全局）](#sec-2-9)
  - [2.10 凭证（可选：Bearer / 自定义）](#sec-2-10)
  - [2.11 组合查询：装配“Crossref / harvest”的当前生效配置](#sec-2-11)
  - [2.12 运行片段：从“窗口”到“请求”的一步步](#sec-2-12)


## <a id="sec-1"></a> 1. PubMed 示例（端到端）

### <a id="sec-1-1"></a> 1.1 背景与 API 速览（面向本库的映射）

* **E-utilities** 家族常用端点：

    * `ESearch`：检索 PMID 列表（支持 `term`、日期过滤等），响应包含总数 `count` 与 `idlist`。
    * `EFetch`：按 PMID 列表返回详情（常用 `retmode=xml`、`rettype=abstract`）。
* **日期/增量**：常用 `EDAT/PDAT/MHDA` 等字段；本库将其建模为 `reg_prov_window_offset_cfg.default_date_field_name`。
* **分页**：ESearch 实际为**偏移量**分页（`retstart` + `retmax`）。在我们的模型中用 `PAGE_NUMBER` 表示，应用层将**页号换算为偏移量
  **（`retstart=(page-1)*retmax`）。
* **鉴权**：匿名可用；有 **API Key** 可提高配额。我们示例中用 `reg_prov_credential`（`QUERY` 参数名 `api_key`）。
* **Headers**：建议包含 `User-Agent` 与可联系的邮箱（可配在 HTTP 策略的 JSON 头中）。

---

### <a id="sec-1-2"></a> 1.2 主数据：登记 PubMed 来源

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

### <a id="sec-1-3"></a> 1.3 端点定义（搜索与详情）

```sql
/* 搜索端点：ESearch（返回 PMID 列表） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope_code, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage_code, http_method_code, path_template,
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
(provenance_id, scope_code, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage_code, http_method_code, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@pubmed_id, 'TASK', 'update', 'efetch', '2025-01-01 00:00:00', NULL,
        'DETAIL', 'GET', '/eutils/efetch.fcgi',
           /* 缺省 query：库名=pubmed，返回 xml；rettype 视业务需要 */
        JSON_OBJECT('db', 'pubmed', 'retmode', 'xml', 'rettype', 'abstract'),
        NULL,
        'application/xml', 0, NULL,
        NULL, NULL, NULL, 'id'); -- 批量 ID 的参数名：id（逗号分隔）
```

> **要点**
>
> * `endpoint_usage_code='SEARCH'` / `'DETAIL'` 用于运行时按用途快速选择端点。
> * `page_param_name='page'`、`page_size_param_name='retmax'` 仅是**应用层参数名**，最终在发送请求时转为
    `retstart=(page-1)*retmax`。
> * `ids_param_name='id'` 表示 EFetch 的 ID 列表放在 `id=1,2,3`。

---

### <a id="sec-1-4"></a> 1.4 时间窗口与增量指针（按天滑动 + 重叠）

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 lookback_value, lookback_unit_code, overlap_value, overlap_unit_code, watermark_lag_seconds,
 offset_type_code, offset_field_name, offset_date_format, default_date_field_name,
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

### <a id="sec-1-5"></a> 1.5 分页/游标（页码模型 → retstart）

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 pagination_mode_code, page_size_value, max_pages_per_execution,
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

### <a id="sec-1-6"></a> 1.6 HTTP 策略（Headers/超时/Retry-After）

```sql
INSERT INTO reg_prov_http_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 base_url_override, default_headers_json,
 timeout_connect_millis, timeout_read_millis, timeout_total_millis,
 tls_verify_enabled, proxy_url_value,
 accept_compress_enabled, prefer_http2_enabled,
 retry_after_policy_code, retry_after_cap_millis,
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

### <a id="sec-1-7"></a> 1.7 批量抓取与请求成型（EFetch 批量）

```sql
INSERT INTO reg_prov_batching_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 detail_fetch_batch_size, ids_param_name, ids_join_delimiter, max_ids_per_request,
 prefer_compact_payload, payload_compress_strategy_code,
 app_parallelism_degree, per_host_concurrency_limit, http_conn_pool_size,
 backpressure_strategy_code, request_template_json)
VALUES (@pubmed_id, 'TASK', 'update', '2025-01-01 00:00:00', NULL,
        200, 'id', ',', 200,
        1, 'NONE',
        8, 8, 64,
        'BLOCK', NULL);
```

> **含义**：一次 EFetch 取 200 个 PMID；最大 200/请求；并行 8；客户端连接池 64。

---

### <a id="sec-1-8"></a> 1.8 重试与退避（含断路器）

```sql
INSERT INTO reg_prov_retry_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 max_retry_times, backoff_policy_type_code, initial_delay_millis, max_delay_millis, exp_multiplier_value,
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

### <a id="sec-1-9"></a> 1.9 限流与并发（客户端令牌桶）

```sql
INSERT INTO reg_prov_rate_limit_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 rate_tokens_per_second, burst_bucket_capacity, max_concurrent_requests,
 per_credential_qps_limit, bucket_granularity_scope_code,
 smoothing_window_millis, respect_server_rate_header)
VALUES (@pubmed_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        5, 5, 10,
        NULL, 'GLOBAL',
        1000, 1);
```

> **示例**：全局 5 QPS，突发 5，并发 10；遵循服务端速率头（如有）。

---

### <a id="sec-1-10"></a> 1.10 凭证（可选：API Key 提升配额）

```sql
/* 若有 API Key，可配置为查询参数 api_key=xxxx */
INSERT INTO reg_prov_credential
(provenance_id, scope_code, task_type, endpoint_id,
 credential_name, auth_type, inbound_location_code,
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

### <a id="sec-1-11"></a> 1.11 组合查询：装配“PubMed / update”的当前生效配置

> 运行一次 `SEARCH`（ESearch）与后续 `DETAIL`（EFetch）通常要**同时读取**多个维度。以下 SQL 取齐端点/HTTP/窗口/分页/批量/重试/限流/凭证各
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
                      AND lifecycle_status_code = 'ACTIVE'
                      AND deleted = 0
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
                      AND lifecycle_status_code = 'ACTIVE'
                      AND deleted = 0
                      AND scope_code = 'SOURCE'
                      AND endpoint_usage_code = 'SEARCH'
                      AND effective_from <= @now
                      AND (effective_to IS NULL OR effective_to > @now)
                    ORDER BY effective_from DESC, id DESC
                    LIMIT 1)
                   LIMIT 1),
     ep_fetch AS ((SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND lifecycle_status_code = 'ACTIVE'
                     AND deleted = 0
                     AND scope_code = 'TASK'
                     AND task_type = 'update'
                     AND endpoint_usage_code = 'DETAIL'
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
                     AND endpoint_usage_code = 'DETAIL'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  LIMIT 1),
     http AS ((SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND lifecycle_status_code = 'ACTIVE'
                 AND deleted = 0
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
                 AND lifecycle_status_code = 'ACTIVE'
                 AND deleted = 0
                 AND scope_code = 'SOURCE'
                 AND effective_from <= @now
                 AND (effective_to IS NULL OR effective_to > @now)
               ORDER BY effective_from DESC, id DESC
               LIMIT 1)
              LIMIT 1),
     win AS ((SELECT *
              FROM reg_prov_window_offset_cfg
              WHERE provenance_id = @pid
                AND lifecycle_status_code = 'ACTIVE'
                AND deleted = 0
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
                AND lifecycle_status_code = 'ACTIVE'
                AND deleted = 0
                AND scope_code = 'SOURCE'
                AND effective_from <= @now
                AND (effective_to IS NULL OR effective_to > @now)
              ORDER BY effective_from DESC, id DESC
              LIMIT 1)
             LIMIT 1),
     pg AS ((SELECT *
             FROM reg_prov_pagination_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
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
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     bt AS ((SELECT *
             FROM reg_prov_batching_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
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
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rt AS ((SELECT *
             FROM reg_prov_retry_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
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
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'SOURCE'
               AND effective_from <= @now
               AND (effective_to IS NULL OR effective_to > @now)
             ORDER BY effective_from DESC, id DESC
             LIMIT 1)
            LIMIT 1),
     rl AS ((SELECT *
             FROM reg_prov_rate_limit_cfg
             WHERE provenance_id = @pid
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
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
               AND lifecycle_status_code = 'ACTIVE'
               AND deleted = 0
               AND scope_code = 'SOURCE'
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
            AND lifecycle_status_code = 'ACTIVE'
            AND deleted = 0
            AND endpoint_id = (SELECT id FROM ep_search LIMIT 1)
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

### <a id="sec-1-7"></a> 1.7 读侧封装模板（视图/存储过程）

为减少重复 SQL，可在 MySQL 8.0 中使用窗口函数构建“当前生效”视图（按维度取 Top 1），或提供存储过程按入参返回单条配置。

示例一：HTTP 策略“当前生效”视图（每个 `(provenance_id, scope_code, task_type_key)` 一条）

```sql
CREATE OR REPLACE VIEW v_reg_prov_http_cfg_active AS
SELECT *
FROM (SELECT h.*,
             ROW_NUMBER() OVER (
                 PARTITION BY h.provenance_id, h.scope_code, h.task_type_key
                 ORDER BY h.effective_from DESC, h.id DESC
                 ) AS rn
      FROM reg_prov_http_cfg h
      WHERE h.lifecycle_status_code = 'ACTIVE'
        AND h.deleted = 0) t
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
 WHERE h.provenance_id = (SELECT id
                          FROM reg_provenance
                          WHERE provenance_code = :provenance_code
                            AND lifecycle_status_code = 'ACTIVE'
                            AND deleted = 0
                          LIMIT 1)
   AND h.lifecycle_status_code = 'ACTIVE'
   AND h.deleted = 0
   AND h.scope_code = 'TASK'
   AND h.task_type = :task_type
   AND h.effective_from <= UTC_TIMESTAMP()
   AND (h.effective_to IS NULL OR h.effective_to > UTC_TIMESTAMP())
 ORDER BY h.effective_from DESC, h.id DESC
 LIMIT 1)
UNION ALL
(SELECT h.*
 FROM reg_prov_http_cfg h
 WHERE h.provenance_id = (SELECT id
                          FROM reg_provenance
                          WHERE provenance_code = :provenance_code
                            AND lifecycle_status_code = 'ACTIVE'
                            AND deleted = 0
                          LIMIT 1)
   AND h.lifecycle_status_code = 'ACTIVE'
   AND h.deleted = 0
   AND h.scope_code = 'SOURCE'
   AND h.effective_from <= UTC_TIMESTAMP()
   AND (h.effective_to IS NULL OR h.effective_to > UTC_TIMESTAMP())
 ORDER BY h.effective_from DESC, h.id DESC
 LIMIT 1)
LIMIT 1;
```

---

### <a id="sec-1-12"></a> 1.12 运行片段：从“窗口”到“请求”的一步步

> 目标：**增量 Update**，以 `EDAT` 为增量字段，将窗口 `[T, T+1d)` 的新增文献拉全。
> 示例取 `T=2025-07-01 00:00:00Z`，**重叠** 1 天（窗口来自 [§3.2](#sec-3-2)）。

#### <a id="sec-1-12-1"></a> 1.12.1 计算时间窗口（应用层）

* 配置：`SLIDING`、`window_size=1 DAY`、`overlap=1 DAY`、`default_date_field_name='EDAT'`。
* 假设上次水位为 `2025-07-01T00:00:00Z`，则本次窗口：

    * `start = 2025-07-01`
    * `end   = 2025-07-02`（半开区间）
    * `datetype = 'edat'`
    * 请求中可映射为：`mindate=2025/07/01&maxdate=2025/07/02`（或等效 term 语法）

#### <a id="sec-1-12-2"></a> 1.12.2 构造检索请求（ESearch）

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

#### <a id="sec-1-12-3"></a> 1.12.3 详情抓取（EFetch 批量）

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

#### <a id="sec-1-12-4"></a> 1.12.4 稳健性与配额

* **限流**：`rate_tokens_per_second=5`、`burst=5`、`max_concurrent_requests=10`；
* **重试**：429/5xx 退避重试；尊重 `Retry-After`；
* **水位推进**：当窗口内的 ESearch+EFetch 完整成功后，推进增量水位到 `end`（考虑 `watermark_lag_seconds` 与 `overlap`）。

---

### 1.13（可选）回填 Backfill 的差异化配置

若要大规模回填可：

* `task_type='backfill'` 新增一组区间；
* **窗口**改为 `CALENDAR+DAY` 或更大跨度；
* **分页**增加 `max_pages_per_execution`；
* **限流**适度上调（若对方允许）并延长观察期；
* **重试**更保守（更大退避上限）。

> 示例（仅给出窗口差异）：

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 offset_type_code, default_date_field_name)
VALUES (@pubmed_id, 'TASK', 'backfill', '2025-01-15 00:00:00',
        'CALENDAR', 1, 'DAY', 'DAY',
        'DATE', 'PDAT'); -- 回填按出版日期 PDAT
```


## <a id="sec-2"></a> 2. Crossref 示例（端到端）

### <a id="sec-2-1"></a> 2.1 背景与 API 速览（面向本库的映射）

* **核心资源**：`/works`

    * **检索（SEARCH）**：`GET /works` 支持按时间过滤、排序、分页；
    * **详情（DETAIL）**：`GET /works/{doi}` 以 DOI 拉取单条详情；
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

### <a id="sec-2-2"></a> 2.2 主数据：登记 Crossref 来源

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

### <a id="sec-2-3"></a> 2.3 端点定义（works 检索与 DOI 详情）

```sql
/* 搜索端点：GET /works（cursor-based） */
INSERT INTO reg_prov_endpoint_def
(provenance_id, scope_code, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage_code, http_method_code, path_template,
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
(provenance_id, scope_code, task_type, endpoint_name, effective_from, effective_to,
 endpoint_usage_code, http_method_code, path_template,
 default_query_params, default_body_payload,
 request_content_type, is_auth_required, credential_hint_name,
 page_param_name, page_size_param_name, cursor_param_name, ids_param_name)
VALUES (@crossref_id, 'TASK', 'harvest', 'work_by_doi', '2025-01-01 00:00:00', NULL,
        'DETAIL', 'GET', '/works/{doi}',
        NULL, NULL,
        'application/json', 0, NULL,
        NULL, NULL, NULL, NULL);
```

> **要点**
>
> * `endpoint_usage_code='SEARCH'` → 当前生效检索端点；`'DETAIL'` → 按 DOI 的详情端点。
> * 端点级参数名覆盖：`page_size_param_name='rows'`、`cursor_param_name='cursor'`；
> * 详情端点 `path_template` 使用占位符 `{doi}`，应用层在发送请求前替换。

---

### <a id="sec-2-4"></a> 2.4 时间窗口与增量指针（按 indexed 滑动）

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 lookback_value, lookback_unit_code, overlap_value, overlap_unit_code, watermark_lag_seconds,
 offset_type_code, offset_field_name, offset_date_format, default_date_field_name,
 max_ids_per_window, max_window_span_seconds)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01 00:00:00', NULL,
        'SLIDING', 1, 'DAY', NULL,
        NULL, NULL, 1, 'DAY', 1800, -- 重叠1天，水位滞后30分钟
        'DATE', NULL, 'YYYY-MM-DD', 'indexed',
        1000000, 259200); -- 单窗口最多100万条，最大跨度3天（示例）
```

> 对于 Crossref 全量/增量，`indexed` 常作为更稳定的推进字段；如需切换到 `created`，只需新增一个 `TASK=update` 区间配置即可。

---

### <a id="sec-2-5"></a> 2.5 分页：CURSOR（游标）

```sql
INSERT INTO reg_prov_pagination_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 pagination_mode_code, page_size_value, max_pages_per_execution,
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

### <a id="sec-2-6"></a> 2.6 HTTP 策略（User-Agent / mailto）

```sql
INSERT INTO reg_prov_http_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 base_url_override, default_headers_json,
 timeout_connect_millis, timeout_read_millis, timeout_total_millis,
 tls_verify_enabled, proxy_url_value,
 accept_compress_enabled, prefer_http2_enabled,
 retry_after_policy_code, retry_after_cap_millis,
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

### <a id="sec-2-7"></a> 2.7 批量抓取与请求成型（详情为单条）

```sql
INSERT INTO reg_prov_batching_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 detail_fetch_batch_size, ids_param_name, ids_join_delimiter, max_ids_per_request,
 prefer_compact_payload, payload_compress_strategy_code,
 app_parallelism_degree, per_host_concurrency_limit, http_conn_pool_size,
 backpressure_strategy_code, request_template_json)
VALUES (@crossref_id, 'TASK', 'harvest', '2025-01-01 00:00:00', NULL,
        1, NULL, NULL, 1, -- 详情按 DOI 单条请求
        1, 'NONE',
        16, 16, 128, -- 并行适当上调（示例值）
        'BLOCK', NULL);
```

> 由于 `/works/{doi}` 为单条详情，请将 `detail_fetch_batch_size` 设为 1，并使用应用层**并发**多请求以提升吞吐。

---

### <a id="sec-2-8"></a> 2.8 重试与退避（尊重 Retry-After）

```sql
INSERT INTO reg_prov_retry_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 max_retry_times, backoff_policy_type_code, initial_delay_millis, max_delay_millis, exp_multiplier_value,
 jitter_factor_ratio,
 retry_http_status_json, giveup_http_status_json, retry_on_network_error,
 circuit_break_threshold, circuit_cooldown_millis)
VALUES (@crossref_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        6, 'EXP_JITTER', 800, 15000, 2.0, 0.4,
        JSON_ARRAY(429, 500, 502, 503, 504), JSON_ARRAY(400, 401, 403, 404), 1,
        12, 60000);
```

> 结合 [§2.5](Registry-prov-config-reference.md#sec-2-5) 的 `retry_after_policy_code='RESPECT'`，当返回 `Retry-After` 时按上限 60s 遵循。

---

### <a id="sec-2-9"></a> 2.9 限流与并发（按端点或全局）

```sql
INSERT INTO reg_prov_rate_limit_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 rate_tokens_per_second, burst_bucket_capacity, max_concurrent_requests,
 per_credential_qps_limit, bucket_granularity_scope_code,
 smoothing_window_millis, respect_server_rate_header)
VALUES (@crossref_id, 'SOURCE', NULL, '2025-01-01 00:00:00', NULL,
        10, 10, 24,
        NULL, 'PER_ENDPOINT', -- 按端点粒度限流（SEARCH 与 DETAIL 分别计数）
        1000, 1);
```

> **示例**：全局 10 QPS、突发 10，并发 24；按端点独立限流，避免搜索/详情互相影响。

---

### <a id="sec-2-10"></a> 2.10 凭证（可选：Bearer / 自定义）

Crossref 公网通常**无需密钥**（但礼仪头必须）；若有合作或私有网关，可如下配置 **Bearer** 示例：

```sql
INSERT INTO reg_prov_credential
(provenance_id, scope_code, task_type, endpoint_id,
 credential_name, auth_type, inbound_location_code,
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

### <a id="sec-2-11"></a> 2.11 组合查询：装配“Crossref / harvest”的当前生效配置

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
                      AND scope_code = 'TASK'
                      AND task_type = 'harvest'
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
     ep_fetch AS ((SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND scope_code = 'TASK'
                     AND task_type = 'harvest'
                     AND endpoint_usage_code = 'DETAIL'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  UNION ALL
                  (SELECT *
                   FROM reg_prov_endpoint_def
                   WHERE provenance_id = @pid
                     AND scope_code = 'SOURCE'
                     AND endpoint_usage_code = 'DETAIL'
                     AND effective_from <= @now
                     AND (effective_to IS NULL OR effective_to > @now)
                   ORDER BY effective_from DESC, id DESC
                   LIMIT 1)
                  LIMIT 1),
     http AS ((SELECT *
               FROM reg_prov_http_cfg
               WHERE provenance_id = @pid
                 AND scope_code = 'TASK'
                 AND task_type = 'harvest'
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
                AND task_type = 'harvest'
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
               AND task_type = 'harvest'
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
               AND task_type = 'harvest'
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
               AND task_type = 'harvest'
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
               AND task_type = 'harvest'
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
         -- 凭证（若有）：先尝试端点绑定，再尝试全局
         (SELECT *
          FROM reg_prov_credential
          WHERE provenance_id = @pid
            AND lifecycle_status_code = 'ACTIVE'
            AND deleted = 0
            AND endpoint_id = (SELECT id FROM ep_search LIMIT 1)
            AND ((scope_code = 'TASK' AND task_type = 'harvest') OR scope_code = 'SOURCE')
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
            AND ((scope_code = 'TASK' AND task_type = 'harvest') OR scope_code = 'SOURCE')
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

### <a id="sec-2-12"></a> 2.12 运行片段：从“窗口”到“请求”的一步步

> 目标：**全量/增量 Harvest**，以 `indexed` 字段、**day 窗口 + 1 天重叠**，使用 **cursor-based** 翻页。

#### <a id="sec-2-12-1"></a> 2.12.1 计算时间窗口（应用层）

* 配置：`SLIDING`；`window_size=1 DAY`；`overlap=1 DAY`；`default_date_field_name='indexed'`；
* 假设上次水位 `2025-07-01`，本次窗口：

    * `start=2025-07-01`，`end=2025-07-02`（半开区间）
* 映射到 Crossref 的过滤参数：

    * `filter=from-index-date:2025-07-01,until-index-date:2025-07-02`

#### <a id="sec-2-12-2"></a> 2.12.2 构造检索请求（`GET /works`）

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

#### <a id="sec-2-12-3"></a> 2.12.3 详情抓取（`GET /works/{doi}`，可选）

* 许多场景可以直接以 `items` 入库；若需要详情补全：

    * 将 `doi` 替换入 `path_template='/works/{doi}'`；
    * 并发度由 `reg_prov_batching_cfg.app_parallelism_degree` 控制（单条请求）。
* **示意**：

  ```
  GET https://api.crossref.org/works/10.1000/xyz123
  ```

#### <a id="sec-2-12-4"></a> 2.12.4 稳健性与配额

* **限流**：`PER_ENDPOINT` 粒度，SEARCH 与 DETAIL 分别记数；
* **重试**：对 429/5xx 指定指数退避并**尊重 Retry-After**；
* **水位推进**：窗口内成功完成后推进到 `end`；考虑 `watermark_lag_seconds` 与 `overlap`。

---

### 2.13（可选）任务差异化：Update / Backfill

* **Update**：将 `task_type='update'` 的窗口/分页/限流等作为**另一组区间**写入；例如窗口设置更短、并发限制更小；
* **Backfill**：可用 `CALENDAR` 模式与较大跨度（如周/月），并提升 `max_pages_per_execution`。

**示例：为 `update` 新增窗口配置（按 created 字段）**

```sql
INSERT INTO reg_prov_window_offset_cfg
(provenance_id, scope_code, task_type, effective_from, effective_to,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 lookback_value, lookback_unit_code, overlap_value, overlap_unit_code, watermark_lag_seconds,
 offset_type_code, offset_field_name, offset_date_format, default_date_field_name)
VALUES (@crossref_id, 'TASK', 'update', '2025-02-01 00:00:00', NULL,
        'SLIDING', 1, 'DAY', NULL,
        NULL, NULL, 1, 'DAY', 900,
        'DATE', NULL, 'YYYY-MM-DD', 'created');
```
