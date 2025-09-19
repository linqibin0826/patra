# Papertrace Registry · 字典（Dict）Reference

导航： [体系总览](../README.md) ｜ 同域： [字典 Guide](Registry-dict-guide.md) ｜ [字典 Ops](Registry-dict-ops.md)

## 目录
- [1. 表结构（包含统一审计字段 BaseDO）](#sec-1)
  - [1.1 `sys_dict_type` — 字典类型](#sec-1-1)
  - [1.2 `sys_dict_item` — 字典项目](#sec-1-2)
  - [1.3 `sys_dict_item_alias` — 外部映射（PubMed/Crossref/遗留）](#sec-1-3)
  - [1.4 读侧视图 `v_sys_dict_item_enabled`](#sec-1-4)
- [2. 字典类型清单（覆盖原文所有枚举）](#sec-2)
- [3. 兼容性与别名映射（legacy 同义词）](#sec-3)
- [3. 可执行种子数据（INSERT）](#sec-3)


## <a id="sec-1"></a> 1. 表结构（包含统一审计字段 BaseDO）

BaseDO 字段（所有表均包含）：

 ```sql
     `record_remarks`  JSON                 NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
     `created_at`      TIMESTAMP(6)         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
     `created_by`      BIGINT UNSIGNED      NULL COMMENT '创建人ID',
     `created_by_name` VARCHAR(100)         NULL COMMENT '创建人姓名',
     `updated_at`      TIMESTAMP(6)         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR (100) NULL COMMENT '更新人姓名',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY (16) NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted` TINYINT (1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
 ```

### <a id="sec-1-1"></a> 1.1 `sys_dict_type` — 字典类型

```sql
CREATE TABLE IF NOT EXISTS sys_dict_type
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    type_code          VARCHAR(64)     NOT NULL COMMENT '类型编码：小写蛇形，如 http_method',
    type_name          VARCHAR(200)    NOT NULL COMMENT '类型名称（人类可读）',
    description        VARCHAR(500)    NULL COMMENT '类型说明',
    allow_custom_items TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否允许自定义扩展项',
    is_system          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否系统内置（1=内置）',
    reserved_json      JSON            NULL COMMENT '扩展元数据（UI颜色/图标等）',

    record_remarks     JSON            NULL,
    created_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by         BIGINT UNSIGNED NULL,
    created_by_name    VARCHAR(100)    NULL,
    updated_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by         BIGINT UNSIGNED NULL,
    updated_by_name    VARCHAR(100)    NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address         VARBINARY(16)   NULL,
    deleted            TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type__code (type_code),
    KEY idx_sys_dict_type__deleted_is_system (deleted, is_system),
    CONSTRAINT chk_sys_dict_type__code_format CHECK (REGEXP_LIKE(type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-类型';
```

### <a id="sec-1-2"></a> 1.2 `sys_dict_item` — 字典项目

```sql
CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    type_id         BIGINT UNSIGNED NOT NULL COMMENT 'FK → sys_dict_type.id',
    item_code       VARCHAR(64)     NOT NULL COMMENT '项目编码：稳定键（全大写+下划线）',
    item_name       VARCHAR(200)    NOT NULL COMMENT '项目名称（默认语言）',
    short_name      VARCHAR(64)     NULL COMMENT '短名/缩写',
    description     VARCHAR(500)    NULL COMMENT '说明',
    display_order   INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT '显示顺序（小在前）',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认取值（同类型最多一条）',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    label_color     VARCHAR(32)     NULL COMMENT '标签颜色（如 #AABBCC 或语义色名）',
    icon_name       VARCHAR(64)     NULL COMMENT '图标名',
    attributes_json JSON            NULL COMMENT '扩展属性（业务自定义）',

    -- 生成列：仅当默认且启用且未删时等于 type_id；否则为 NULL
    default_key     BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN (is_default = 1 AND enabled = 1 AND deleted = 0) THEN type_id ELSE NULL END) STORED,

    record_remarks  JSON            NULL,
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      BIGINT UNSIGNED NULL,
    updated_by_name VARCHAR(100)    NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address      VARBINARY(16)   NULL,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item__type_code (type_id, item_code),
    UNIQUE KEY uk_sys_dict_item__default_per_type (default_key), -- 确保“同类型仅一个默认项”
    KEY idx_sys_dict_item__type_enabled (type_id, enabled, deleted, display_order),
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-项目';
```

### <a id="sec-1-3"></a> 1.3 `sys_dict_item_alias` — 外部映射（PubMed/Crossref/遗留）

```sql
CREATE TABLE IF NOT EXISTS sys_dict_item_alias
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id         BIGINT UNSIGNED NOT NULL COMMENT 'FK → sys_dict_item.id',
    source_system   VARCHAR(64)     NOT NULL COMMENT '来源系统：如 pubmed/crossref/legacy_v1',
    external_code   VARCHAR(128)    NOT NULL COMMENT '外部代码/取值',
    external_label  VARCHAR(200)    NULL COMMENT '外部名称（可空）',
    notes           VARCHAR(500)    NULL COMMENT '备注/映射说明',

    record_remarks  JSON            NULL,
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      BIGINT UNSIGNED NULL,
    updated_by_name VARCHAR(100)    NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address      VARBINARY(16)   NULL,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_alias__src_code (source_system, external_code),
    KEY idx_dict_alias__item (item_id, source_system),
    CONSTRAINT chk_dict_alias__src_format CHECK (REGEXP_LIKE(source_system, '^[a-z0-9_\\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-外部映射';
```

### <a id="sec-1-4"></a> 1.4 读侧视图 `v_sys_dict_item_enabled`

```sql
CREATE OR REPLACE VIEW v_sys_dict_item_enabled AS
SELECT di.id                                 AS item_id,
       dt.type_code                          AS type_code,
       di.item_code                          AS item_code,
       COALESCE(di.short_name, di.item_name) AS display_name,
       di.item_name,
       di.description,
       di.display_order,
       di.is_default,
       di.label_color,
       di.icon_name
FROM sys_dict_item di
         JOIN sys_dict_type dt ON dt.id = di.type_id
WHERE di.enabled = 1
  AND di.deleted = 0
  AND dt.deleted = 0;
```

---


## <a id="sec-2"></a> 2. 字典类型清单（覆盖原文所有枚举）

> 以下 **type_code** 与 **item_code** 为**建议标准**，可直接导入种子数据。

| type_code                   | 语义              | 典型 item_code                                                                      |
|-----------------------------|-----------------|-----------------------------------------------------------------------------------|
| `scope`                     | 作用域             | `SOURCE`, `TASK`                                                                  |
| `http_method`               | HTTP 方法         | `GET`,`POST`,`PUT`,`PATCH`,`DELETE`,`HEAD`,`OPTIONS`                              |
| `endpoint_usage`            | 端点用途            | `SEARCH`,`DETAIL`,`BATCH`,`AUTH`,`HEALTH`                                         |
| `pagination_mode`           | 分页模式            | `PAGE_NUMBER`,`CURSOR`,`TOKEN`,`SCROLL`                                           |
| `window_mode`               | 窗口模式            | `SLIDING`,`CALENDAR`                                                              |
| `time_unit`                 | 时间单位            | `SECOND`,`MINUTE`,`HOUR`,`DAY`                                                    |
| `offset_type`               | 增量指针类型          | `DATE`,`ID`,`COMPOSITE`                                                           |
| `reg_data_type`             | 统一字段数据类型       | `DATE`,`DATETIME`,`NUMBER`,`TEXT`,`KEYWORD`,`BOOLEAN`,`TOKEN`                     |
| `reg_cardinality`           | 字段基数            | `SINGLE`,`MULTI`                                                                  |
| `bucket_granularity_scope`  | 限流粒度            | `GLOBAL`,`PER_KEY`,`PER_ENDPOINT`,`PER_IP`,`PER_TASK`                             |
| `retry_after_policy`        | Retry-After 策略     | `IGNORE`,`RESPECT`,`CLAMP`                                                        |
| `backoff_policy_type`       | 退避策略            | `FIXED`,`EXP`,`EXP_JITTER`,`DECOR_JITTER`                                         |
| `backpressure_strategy`     | 背压策略            | `BLOCK`,`DROP`,`YIELD`                                                            |
| `reg_operation`             | 端点操作            | `SEARCH`,`DETAIL`,`LOOKUP`                                                         |
| `reg_expr_op`               | 表达式操作符          | `TERM`,`IN`,`RANGE`,`EXISTS`,`TOKEN`                                               |
| `reg_range_kind`            | 范围值类型            | `NONE`,`DATE`,`DATETIME`,`NUMBER`                                                  |
| `reg_match_type`            | 匹配策略            | `PHRASE`,`EXACT`,`ANY`                                                             |
| `reg_emit_type`             | 渲染产出类型          | `QUERY`,`PARAMS`                                                                   |
| `reg_transform`             | 渲染/取值转换函数       | `IDENTITY`,`TO_EXCLUSIVE_MINUS_1D`,`PUBMED_DATETYPE`                               |

---


## <a id="sec-3"></a> 3. 兼容性与别名映射（legacy 同义词）

为了平滑过渡历史文档与外部供应商的取值命名差异，建议通过 `sys_dict_item_alias` 维护同义词映射，读写层统一面向“规范 code”。

- 典型映射建议（source_system 建议使用 `legacy_v1` 作为内部遗留标识，或供应商名如 `pubmed`/`crossref`）：
  - endpoint_usage: `FETCH` → `DETAIL`
  - bucket_granularity_scope: `CREDENTIAL` → `PER_KEY`；`ENDPOINT` → `PER_ENDPOINT`；`IP` → `PER_IP`；`TASK` → `PER_TASK`
  - retry_after_policy: `NONE` → `IGNORE`；`RESPECT_HEADER` → `RESPECT`；`FIXED`/`EXP_BACKOFF` → `CLAMP`
  - lifecycle_status: （如有历史）`PUBLISHED` → `ACTIVE`

示例 SQL（以 endpoint_usage 为例，省略插入类型与项目的基础数据）：

```sql
-- 查询规范项 id（以 endpoint_usage.DETAIL 为例）
SELECT di.id INTO @detail_id
FROM sys_dict_item di
JOIN sys_dict_type dt ON dt.id = di.type_id
WHERE dt.type_code = 'endpoint_usage'
  AND di.item_code = 'DETAIL'
  AND di.deleted = 0;

-- 建立 legacy 同义映射：FETCH → DETAIL (DETAIL 为规范编码)
INSERT INTO sys_dict_item_alias (item_id, source_system, external_code, external_label, notes)
VALUES
  (@detail_id, 'legacy_v1', 'FETCH', 'fetch', 'legacy synonym of DETAIL');
```

注意事项：
- 别名表不改变业务表的取值，业务表一律存放规范 `item_code`；
- 别名用于对接/导入/兼容查询时的转换，避免把遗留值扩散到业务表；
- `(source_system, external_code)` 全局唯一，避免冲突。
| `payload_compress_strategy` | 压缩策略            | `NONE`,`GZIP`                                                                     |
| `inbound_location`          | 鉴权参数放置位置        | `HEADER`,`QUERY`,`BODY`                                                           |
| `lifecycle_status`          | 生命周期            | `DRAFT`,`ACTIVE`,`DEPRECATED`,`RETIRED`                                           |
| `param_merge_strategy`      | 参数合并策略（新增）      | `OVERRIDE`,`UNION`,`REMOVE_NULLS`                                                 |
| `oauth_grant_type`          | OAuth2 授权类型（新增） | `CLIENT_CREDENTIALS`,`PASSWORD`,`AUTHORIZATION_CODE`,`REFRESH_TOKEN`,`JWT_BEARER` |

> 如需额外枚举（例：`mime_type`/`tls_mode`），按相同模式扩展类型与项目即可。

---


## <a id="sec-3"></a> 3. 可执行种子数据（INSERT）

> 建议作为**迁移脚本**的一部分一次性导入；以后仅增量变更。

```sql
-- 类型
INSERT INTO sys_dict_type (type_code, type_name, description, allow_custom_items, is_system)
VALUES ('scope', '作用域', '配置维度作用域', 0, 1),
       ('http_method', 'HTTP 方法', 'HTTP verbs for REST', 0, 1),
       ('endpoint_usage', '端点用途', 'API 端点角色/用途', 1, 1),
       ('pagination_mode', '分页模式', '页码/游标/令牌/滚动', 1, 1),
       ('window_mode', '窗口模式', '滑动/日历对齐', 1, 1),
       ('time_unit', '时间单位', 'SECOND/MINUTE/HOUR/DAY', 1, 1),
       ('offset_type', '增量指针类型', '基于日期/ID/复合键', 1, 1),
       ('reg_data_type', '统一字段数据类型', 'DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN', 0, 1),
       ('reg_cardinality', '字段基数', 'SINGLE/MULTI', 0, 1),
       ('bucket_granularity_scope', '配额/限流粒度', 'GLOBAL/PER_KEY/PER_ENDPOINT/PER_IP/PER_TASK', 1, 1),
       ('retry_after_policy', 'Retry-After 策略', 'IGNORE/RESPECT/CLAMP', 1, 1),
       ('backoff_policy_type', '退避策略', '固定/指数/抖动', 1, 1),
       ('backpressure_strategy', '背压策略', '阻塞/丢弃/让出', 1, 1),
       ('reg_operation', '端点操作', 'SEARCH/DETAIL/LOOKUP', 0, 1),
       ('reg_expr_op', '表达式操作符', 'TERM/IN/RANGE/EXISTS/TOKEN', 0, 1),
       ('reg_range_kind', '范围值类型', 'NONE/DATE/DATETIME/NUMBER', 0, 1),
       ('reg_match_type', '匹配策略', 'PHRASE/EXACT/ANY', 0, 1),
       ('reg_emit_type', '渲染产出类型', 'QUERY/PARAMS', 0, 1),
       ('reg_transform', '渲染/取值转换', 'IDENTITY/TO_EXCLUSIVE_MINUS_1D/自定义', 1, 1),
       ('payload_compress_strategy', '压缩策略', 'NONE/GZIP', 1, 1),
       ('inbound_location', '鉴权参数放置位置', 'Header/Query/Body', 1, 1),
       ('lifecycle_status', '生命周期状态', '草稿/生效/弃用/下线', 0, 1),
       ('param_merge_strategy', '参数合并策略', '覆盖/并集/去空', 1, 1),
       ('oauth_grant_type', 'OAuth2 授权类型', '客户端凭证/密码/授权码/刷新/JWT', 0, 1);

-- 项目：以 SELECT-INTO 方式批量插入，确保 type_id 正确
-- scope
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SOURCE' code, '来源级', 10, 1 def
               UNION ALL
               SELECT 'TASK', '任务级', 20, 0) x
WHERE t.type_code = 'scope';

-- http_method
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'GET', 'GET', 10, 1
               UNION ALL
               SELECT 'POST', 'POST', 20, 0
               UNION ALL
               SELECT 'PUT', 'PUT', 30, 0
               UNION ALL
               SELECT 'PATCH', 'PATCH', 40, 0
               UNION ALL
               SELECT 'DELETE', 'DELETE', 50, 0
               UNION ALL
               SELECT 'HEAD', 'HEAD', 60, 0
               UNION ALL
               SELECT 'OPTIONS', 'OPTIONS', 70, 0) x
WHERE t.type_code = 'http_method';

-- endpoint_usage
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SEARCH', '搜索/检索', 10, 1
               UNION ALL
               SELECT 'DETAIL', '详情获取', 20, 0
               UNION ALL
               SELECT 'BATCH', '批量下载/导出', 30, 0
               UNION ALL
               SELECT 'AUTH', '鉴权/令牌', 40, 0
               UNION ALL
               SELECT 'HEALTH', '健康检查/配额探测', 50, 0) x
WHERE t.type_code = 'endpoint_usage';

-- pagination_mode
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'PAGE_NUMBER', '页码分页', 10, 0
               UNION ALL
               SELECT 'CURSOR', '游标分页', 20, 1
               UNION ALL
               SELECT 'TOKEN', '令牌分页', 30, 0
               UNION ALL
               SELECT 'SCROLL', '滚动分页', 40, 0) x
WHERE t.type_code = 'pagination_mode';

-- window_mode
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SLIDING', '滑动窗口', 10, 1
               UNION ALL
               SELECT 'CALENDAR', '日历对齐', 20, 0) x
WHERE t.type_code = 'window_mode';

-- time_unit
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SECOND', '秒', 10, 0
               UNION ALL
               SELECT 'MINUTE', '分', 20, 0
               UNION ALL
               SELECT 'HOUR', '时', 30, 0
               UNION ALL
               SELECT 'DAY', '天', 40, 1) x
WHERE t.type_code = 'time_unit';

-- offset_type
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'DATE', '日期型', 10, 1
               UNION ALL
               SELECT 'ID', '主键/序列', 20, 0
               UNION ALL
               SELECT 'COMPOSITE', '复合指针', 30, 0) x
WHERE t.type_code = 'offset_type';

-- reg_data_type
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'DATE', '日期', 10, 0
               UNION ALL
               SELECT 'DATETIME', '日期时间', 20, 0
               UNION ALL
               SELECT 'NUMBER', '数值', 30, 0
               UNION ALL
               SELECT 'TEXT', '长文本', 40, 0
               UNION ALL
               SELECT 'KEYWORD', '关键词', 50, 0
               UNION ALL
               SELECT 'BOOLEAN', '布尔', 60, 0
               UNION ALL
               SELECT 'TOKEN', '标记/令牌', 70, 1) x
WHERE t.type_code = 'reg_data_type';

-- reg_cardinality
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SINGLE', '单值', 10, 1
               UNION ALL
               SELECT 'MULTI', '多值', 20, 0) x
WHERE t.type_code = 'reg_cardinality';

-- bucket_granularity_scope
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'GLOBAL', '全局', 10, 1
               UNION ALL
               SELECT 'PER_KEY', '按凭证', 20, 0
               UNION ALL
               SELECT 'PER_ENDPOINT', '按端点', 30, 0
               UNION ALL
               SELECT 'PER_IP', '按来源IP', 40, 0
               UNION ALL
               SELECT 'PER_TASK', '按任务类型', 50, 0) x
WHERE t.type_code = 'bucket_granularity_scope';

-- retry_after_policy
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'IGNORE', '忽略响应头', 10, 0
               UNION ALL
               SELECT 'RESPECT', '完全遵循 Retry-After', 20, 1
               UNION ALL
               SELECT 'CLAMP', '遵循但设置上限', 30, 0) x
WHERE t.type_code = 'retry_after_policy';

-- backoff_policy_type
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'FIXED', '固定', 10, 1
               UNION ALL
               SELECT 'EXP', '指数', 20, 0
               UNION ALL
               SELECT 'EXP_JITTER', '指数+抖动', 30, 0
               UNION ALL
               SELECT 'DECOR_JITTER', '装饰性抖动', 40, 0) x
WHERE t.type_code = 'backoff_policy_type';

-- reg_operation
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'SEARCH', '检索/搜索', 10, 1
               UNION ALL
               SELECT 'DETAIL', '详情调用', 20, 0
               UNION ALL
               SELECT 'LOOKUP', '单条查询/查表', 30, 0) x
WHERE t.type_code = 'reg_operation';

-- reg_expr_op
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'TERM', '词条匹配', 10, 1
               UNION ALL
               SELECT 'IN', '集合包含', 20, 0
               UNION ALL
               SELECT 'RANGE', '范围', 30, 0
               UNION ALL
               SELECT 'EXISTS', '存在判断', 40, 0
               UNION ALL
               SELECT 'TOKEN', '令牌搜索', 50, 0) x
WHERE t.type_code = 'reg_expr_op';

-- reg_range_kind
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'NONE', '无范围', 10, 1
               UNION ALL
               SELECT 'DATE', '日期', 20, 0
               UNION ALL
               SELECT 'DATETIME', '日期时间', 30, 0
               UNION ALL
               SELECT 'NUMBER', '数字', 40, 0) x
WHERE t.type_code = 'reg_range_kind';

-- reg_match_type
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'PHRASE', '短语', 10, 1
               UNION ALL
               SELECT 'EXACT', '精确', 20, 0
               UNION ALL
               SELECT 'ANY', '不限', 30, 0) x
WHERE t.type_code = 'reg_match_type';

-- reg_emit_type
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'QUERY', '查询拼接', 10, 1
               UNION ALL
               SELECT 'PARAMS', '参数输出', 20, 0) x
WHERE t.type_code = 'reg_emit_type';

-- reg_transform
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'IDENTITY', '不变换', 10, 1
               UNION ALL
               SELECT 'TO_EXCLUSIVE_MINUS_1D', '闭区间-1天转开区间', 20, 0
               UNION ALL
               SELECT 'PUBMED_DATETYPE', 'PubMed DateType', 30, 0) x
WHERE t.type_code = 'reg_transform';

-- backpressure_strategy
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'BLOCK', '阻塞', 10, 1
               UNION ALL
               SELECT 'DROP', '丢弃', 20, 0
               UNION ALL
               SELECT 'YIELD', '让出', 30, 0) x
WHERE t.type_code = 'backpressure_strategy';

-- payload_compress_strategy
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'NONE', '不压缩', 10, 1
               UNION ALL
               SELECT 'GZIP', 'GZIP 压缩', 20, 0) x
WHERE t.type_code = 'payload_compress_strategy';

-- inbound_location
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'HEADER', '请求头', 10, 1
               UNION ALL
               SELECT 'QUERY', '查询参数', 20, 0
               UNION ALL
               SELECT 'BODY', '请求体', 30, 0) x
WHERE t.type_code = 'inbound_location';

-- lifecycle_status
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'DRAFT', '草稿', 10, 0
               UNION ALL
               SELECT 'ACTIVE', '生效', 20, 1
               UNION ALL
               SELECT 'DEPRECATED', '弃用中', 30, 0
               UNION ALL
               SELECT 'RETIRED', '已下线', 40, 0) x
WHERE t.type_code = 'lifecycle_status';

-- param_merge_strategy（新增）
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'OVERRIDE', '覆盖', 10, 1
               UNION ALL
               SELECT 'UNION', '并集', 20, 0
               UNION ALL
               SELECT 'REMOVE_NULLS', '去空/去 null', 30, 0) x
WHERE t.type_code = 'param_merge_strategy';

-- oauth_grant_type（新增）
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'CLIENT_CREDENTIALS', '客户端凭证', 10, 1
               UNION ALL
               SELECT 'PASSWORD', '密码模式', 20, 0
               UNION ALL
               SELECT 'AUTHORIZATION_CODE', '授权码', 30, 0
               UNION ALL
               SELECT 'REFRESH_TOKEN', '刷新令牌', 40, 0
               UNION ALL
               SELECT 'JWT_BEARER', 'JWT 断言', 50, 0) x
WHERE t.type_code = 'oauth_grant_type';
```

---
