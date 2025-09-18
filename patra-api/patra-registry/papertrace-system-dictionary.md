# Papertrace 系统字典（去 ENUM 化）设计与 SQL（MySQL 8.0）

> 版本：2025-09-18 · 适配 Papertrace Registry（采集数据源配置）  
> 目标：用**字典表**替换所有 ENUM，支持**零 DDL 演进**、**默认项唯一**、**多来源映射**、**完善审计**，并与 DDD/六边形架构落地对齐。

---

## 0. 摘要（Executive Summary）

- 在《Papertrace Registry Schema & SQL 指南》中广泛使用了枚举（如 `http_method`、`endpoint_usage`、`pagination_mode`、
  `window_mode`、`offset_type`、`retry_after_policy`、`bucket_granularity_scope`、`backoff_policy_type`、
  `backpressure_strategy`、`payload_compress_strategy`、`inbound_location`、`lifecycle_status` 等）。
- 为避免 **ENUM 演进困难** 与 **线上变更卡顿**，本方案提供：
    1) **字典类型表** `sys_dict_type`；
    2) **字典项目表** `sys_dict_item`（含“同类型仅一个默认项”的**强约束**）；
    3) **外部代码映射表** `sys_dict_item_alias`（可对接 PubMed/Crossref/遗留值）；
    4) **只读视图** `v_sys_dict_item_enabled`（读侧统一入口）。
- 不引入触发器；所有表**包含统一审计字段**（BaseDO）。
- 业务表通过 **FK → `sys_dict_item.id`** 引用字典项，实现新增取值 **零 DDL**。

---

## 1. 背景与总体设计

### 1.1 设计动机

- **ENUM 的问题**：新增取值需 `ALTER`，发版窗口受限；跨环境迁移复杂；与第三方值对表困难。
- **采集域的现实**：数据源差异大、演进快（端点用途、分页模型、限流粒度、鉴权模式等经常新增）。
- **DDD/六边形**：将**可枚举的业务语义**收敛到“**字典**”作为**对外契约**的一部分，读写解耦，发布可控。

### 1.2 设计目标

- **零 DDL 演进**：新增/废弃取值仅需 `INSERT/UPDATE`。
- **默认项唯一**：同一类型最多一个**启用且未删除**的默认项。
- **可观测/可治理**：带审计、软删、版本号；提供健康检查 SQL。
- **高可用**：适配 MySQL 8.0，避免触发器/存储过程，提高可移植性。

### 1.3 术语约定

- **类型（type）**：一组枚举的语义集合，如 `http_method`。
- **项目（item）**：类型下的某个取值，如 `GET`。键 `item_code` **稳定**且**全大写+下划线**。
- **启用/删除**：`enabled`=1 且 `deleted`=0 才参与业务选择。

---

## 2. 数据模型与约束（ER 抽象）

- `sys_dict_type (1) ——< sys_dict_item (N)`：类型与项目的父子关系。
- `sys_dict_item_alias (N)`：为一个项目提供多个外部平台/遗留系统的“别名/编码”。
- **默认唯一**：`sys_dict_item.default_key` 是一个**生成列**；当 `is_default=1` 且启用未删时取 `type_id`，否则为 `NULL`
  ；配合唯一键确保**同类型仅一条默认**。
- **读侧视图**：`v_sys_dict_item_enabled` 只暴露启用且未删的项目，减少 where 条件重复。

---

## 3. 表结构（包含统一审计字段 BaseDO）

BaseDO 字段（所有表均包含）：

 ```sql
     `record_remarks`  JSON                 NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
     `created_at`      TIMESTAMP(6)         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
     `created_by`      BIGINT UNSIGNED      NULL COMMENT '创建人ID',
     `created_by_name` VARCHAR(100)         NULL COMMENT '创建人姓名',
     `updated_at`      TIMESTAMP(6)         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON
UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR (100) NULL COMMENT '更新人姓名',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY (16) NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted` TINYINT (1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
 ```

### 3.1 `sys_dict_type` — 字典类型

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

### 3.2 `sys_dict_item` — 字典项目

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
    CONSTRAINT fk_sys_dict_item__type FOREIGN KEY (type_id) REFERENCES sys_dict_type (id),
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-项目';
```

### 3.3 `sys_dict_item_alias` — 外部映射（PubMed/Crossref/遗留）

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
    CONSTRAINT fk_dict_alias__item FOREIGN KEY (item_id) REFERENCES sys_dict_item (id),
    CONSTRAINT chk_dict_alias__src_format CHECK (REGEXP_LIKE(source_system, '^[a-z0-9_\\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-外部映射';
```

### 3.4 读侧视图 `v_sys_dict_item_enabled`

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

## 4. 字典类型清单（覆盖原文所有枚举）

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
| `bucket_granularity_scope`  | 限流粒度            | `GLOBAL`,`CREDENTIAL`,`ENDPOINT`,`IP`,`TASK`                                      |
| `retry_after_policy`        | 重试等待策略          | `NONE`,`RESPECT_HEADER`,`FIXED`,`EXP_BACKOFF`                                     |
| `backoff_policy_type`       | 退避策略            | `FIXED`,`EXP`,`EXP_JITTER`,`DECOR_JITTER`                                         |
| `backpressure_strategy`     | 背压策略            | `BLOCK`,`DROP`,`YIELD`                                                            |
| `payload_compress_strategy` | 压缩策略            | `NONE`,`GZIP`                                                                     |
| `inbound_location`          | 鉴权参数放置位置        | `HEADER`,`QUERY`,`BODY`                                                           |
| `lifecycle_status`          | 生命周期            | `DRAFT`,`ACTIVE`,`DEPRECATED`,`RETIRED`                                           |
| `param_merge_strategy`      | 参数合并策略（新增）      | `OVERRIDE`,`UNION`,`REMOVE_NULLS`                                                 |
| `oauth_grant_type`          | OAuth2 授权类型（新增） | `CLIENT_CREDENTIALS`,`PASSWORD`,`AUTHORIZATION_CODE`,`REFRESH_TOKEN`,`JWT_BEARER` |

> 如需额外枚举（例：`mime_type`/`tls_mode`），按相同模式扩展类型与项目即可。

---

## 5. 可执行种子数据（INSERT）

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
       ('bucket_granularity_scope', '配额/限流粒度', 'GLOBAL/CREDENTIAL/ENDPOINT/IP/TASK', 1, 1),
       ('retry_after_policy', '重试等待策略', 'Retry-After 解析与等待', 1, 1),
       ('backoff_policy_type', '退避策略', '固定/指数/抖动', 1, 1),
       ('backpressure_strategy', '背压策略', '阻塞/丢弃/让出', 1, 1),
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

-- bucket_granularity_scope
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'GLOBAL', '全局', 10, 1
               UNION ALL
               SELECT 'CREDENTIAL', '按凭证', 20, 0
               UNION ALL
               SELECT 'ENDPOINT', '按端点', 30, 0
               UNION ALL
               SELECT 'IP', '按来源IP', 40, 0
               UNION ALL
               SELECT 'TASK', '按任务类型', 50, 0) x
WHERE t.type_code = 'bucket_granularity_scope';

-- retry_after_policy
INSERT INTO sys_dict_item (type_id, item_code, item_name, display_order, is_default)
SELECT id, x.code, x.name, x.ord, x.def
FROM sys_dict_type t
         JOIN (SELECT 'NONE', '固定间隔/不读取头', 10, 0
               UNION ALL
               SELECT 'RESPECT_HEADER', '遵循 Retry-After 头', 20, 1
               UNION ALL
               SELECT 'FIXED', '固定退避', 30, 0
               UNION ALL
               SELECT 'EXP_BACKOFF', '指数退避', 40, 0) x
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

## 6. 在业务表中的引用方式（示例与规范）

### 6.1 字段命名规范

- 统一采用 `*_id`，FK → `sys_dict_item.id`，并在注释中标注对应 `type_code`：  
  例如 `http_method_id BIGINT UNSIGNED NOT NULL COMMENT 'FK: sys_dict_item.id (type=http_method)'`。

### 6.2 端点定义示例（替换 ENUM）

> 以 `reg_prov_endpoint_def` 的 `http_method`、`endpoint_usage` 两个枚举字段为例：

```sql
-- 示例：在新建表时直接使用 FK；（旧表迁移见 §8）
CREATE TABLE reg_prov_endpoint_def
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provenance_id     BIGINT UNSIGNED NOT NULL,
    scope             VARCHAR(16)     NOT NULL, -- 也可改为 scope_id（若希望彻底去枚举）
    task_type         VARCHAR(64)     NULL,
    task_type_key     VARCHAR(64)     NOT NULL DEFAULT 'ALL',
    endpoint_name     VARCHAR(64)     NOT NULL,
    http_method_id    BIGINT UNSIGNED NOT NULL COMMENT 'FK: sys_dict_item.id (type=http_method)',
    endpoint_usage_id BIGINT UNSIGNED NOT NULL COMMENT 'FK: sys_dict_item.id (type=endpoint_usage)',
    -- ... 其它字段略 ...
    record_remarks    JSON            NULL,
    created_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by        BIGINT UNSIGNED NULL,
    created_by_name   VARCHAR(100)    NULL,
    updated_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by        BIGINT UNSIGNED NULL,
    updated_by_name   VARCHAR(100)    NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address        VARBINARY(16)   NULL,
    deleted           TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ep_def__method FOREIGN KEY (http_method_id) REFERENCES sys_dict_item (id),
    CONSTRAINT fk_ep_def__usage FOREIGN KEY (endpoint_usage_id) REFERENCES sys_dict_item (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='端点定义（示例：去 ENUM 化）';
```

### 6.3 应用层校验（限制“类型匹配”）

- 纯 SQL 难以在 FK 上强约束“`http_method_id` 必属 `http_method` 类型”；MySQL 的 CHECK 不能引用子查询。
- **实践做法**：在 **应用层/仓储层** 统一校验：
    - 通过 `v_sys_dict_item_enabled` 反查 `type_code`，断言与预期一致；
    - 对外 REST DTO 使用 `(typeCode, itemCode)` 交互，入库前解析为 `item_id`。

---

## 7. 读写实践与健康检查

### 7.1 常用查询

```sql
-- 取指定类型 + 编码
SELECT item_id, type_code, item_code, display_name
FROM v_sys_dict_item_enabled
WHERE type_code = 'http_method'
  AND item_code = 'GET';

-- 取某类型默认项
SELECT item_id, item_code
FROM v_sys_dict_item_enabled
WHERE type_code = 'endpoint_usage'
  AND is_default = 1;

-- 列出可用项（按显示顺序）
SELECT item_id, item_code, display_name
FROM v_sys_dict_item_enabled
WHERE type_code = 'retry_after_policy'
ORDER BY display_order;
```

### 7.2 数据质量巡检（应返回 0 行）

```sql
-- A. 检查“默认项>1”的违规
SELECT dt.type_code, COUNT(*) AS defaults
FROM sys_dict_item di
         JOIN sys_dict_type dt ON dt.id = di.type_id
WHERE di.is_default = 1
  AND di.enabled = 1
  AND di.deleted = 0
  AND dt.deleted = 0
GROUP BY dt.type_code
HAVING COUNT(*) > 1;

-- B. 检查“同类型重复 item_code”（理论上被唯一键阻止，这里兜底）
SELECT dt.type_code, di.item_code, COUNT(*) c
FROM sys_dict_item di
         JOIN sys_dict_type dt ON dt.id = di.type_id
GROUP BY dt.type_code, di.item_code
HAVING c > 1;
```

---

## 8. 运维与治理（Runbook）

- **新增类型**：`sys_dict_type` 插入一行；尽量避免删除，使用 `deleted=1` 软删。
- **新增项目**：`sys_dict_item` 插入；如要设为默认，将其他项目的 `is_default` 更新为 0（唯一约束可防止并发冲突）。
- **废弃项目**：将 `enabled=0` 或 `deleted=1`；不建议修改 `item_code`（稳定键）。
- **外部映射**：在 `sys_dict_item_alias` 维护第三方值映射，避免在业务表“硬编码对照表”。
- **发布审计**：使用 `record_remarks` 记录变更原因（JSON 数组）。
- **定期巡检**：执行 §7.2 的健康检查 SQL。

---

## 9. FAQ 与取舍

- **问：为何不直接用 CHECK 强约束“类型匹配”？**  
  MySQL 8.0 的 CHECK 不能引用子查询，无法在 FK 层面保证 `*_id` 一定属于某个 `type_code`。实践采用**应用层校验 + 巡检 SQL
  **。
- **问：默认项如何防止并发写冲突？**  
  `default_key` + 唯一键天生防止；并发设置多个默认将失败，应用层捕获后重试或回滚。
