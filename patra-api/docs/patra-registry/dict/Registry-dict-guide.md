# Papertrace Registry · 字典（Dict）Guide

导航： [体系总览](../README.md) ｜ 同域： [字典 Reference](Registry-dict-reference.md) ｜ [字典 Ops](Registry-dict-ops.md)

## 目录
- [1. 摘要（Executive Summary）](#sec-1)
- [2. 背景与总体设计](#sec-2)
  - [2.1 设计动机](#sec-2-1)
  - [2.2 设计目标](#sec-2-2)
  - [2.3 术语约定](#sec-2-3)
- [3. 数据模型与约束（ER 抽象）](#sec-3)
- [4. 在业务表中的引用方式（示例与规范）](#sec-4)
  - [4.1 字段命名规范（以字典编码关联）](#sec-4-1)
  - [4.2 端点定义示例（以编码关联字典）](#sec-4-2)
  - [4.3 应用层校验（保证“类型匹配 + 有效性”）](#sec-4-3)


## <a id="sec-1"></a> 1. 摘要（Executive Summary）

- 在《Papertrace Registry Schema & SQL 指南》中广泛使用了枚举（如 `http_method`、`endpoint_usage`、`pagination_mode`、
  `window_mode`、`offset_type`、`retry_after_policy`、`bucket_granularity_scope`、`backoff_policy_type`、
  `backpressure_strategy`、`payload_compress_strategy`、`inbound_location`、`lifecycle_status` 等）。
- 为避免 **ENUM 演进困难** 与 **线上变更卡顿**，本方案提供：
    1) **字典类型表** `sys_dict_type`；
    2) **字典项目表** `sys_dict_item`（含“同类型仅一个默认项”的**强约束**）；
    3) **外部代码映射表** `sys_dict_item_alias`（可对接 PubMed/Crossref/遗留值）；
    4) **只读视图** `v_sys_dict_item_enabled`（读侧统一入口）。
- 不引入触发器；所有表**包含统一审计字段**（BaseDO）。
- 业务表通过**字典编码（`item_code`）关联**字典项（不使用 `id` 外键），实现新增取值 **零 DDL**，由应用层保证类型匹配与有效性。

---


## <a id="sec-2"></a> 2. 背景与总体设计

### <a id="sec-2-1"></a> 2.1 设计动机

- **ENUM 的问题**：新增取值需 `ALTER`，发版窗口受限；跨环境迁移复杂；与第三方值对表困难。
- **采集域的现实**：数据源差异大、演进快（端点用途、分页模型、限流粒度、鉴权模式等经常新增）。
- **DDD/六边形**：将**可枚举的业务语义**收敛到“**字典**”作为**对外契约**的一部分，读写解耦，发布可控。

### <a id="sec-2-2"></a> 2.2 设计目标

- **零 DDL 演进**：新增/废弃取值仅需 `INSERT/UPDATE`。
- **默认项唯一**：同一类型最多一个**启用且未删除**的默认项。
- **可观测/可治理**：带审计、软删、版本号；提供健康检查 SQL。
- **高可用**：适配 MySQL 8.0，避免触发器/存储过程，提高可移植性。

### <a id="sec-2-3"></a> 2.3 术语约定

- **类型（type）**：一组枚举的语义集合，如 `http_method`。
- **项目（item）**：类型下的某个取值，如 `GET`。键 `item_code` **稳定**且**全大写+下划线**。
- **启用/删除**：`enabled`=1 且 `deleted`=0 才参与业务选择。

---


## <a id="sec-3"></a> 3. 数据模型与约束（ER 抽象）

- `sys_dict_type (1) ——< sys_dict_item (N)`：类型与项目的父子关系。
- `sys_dict_item_alias (N)`：为一个项目提供多个外部平台/遗留系统的“别名/编码”。
- **默认唯一**：`sys_dict_item.default_key` 是一个**生成列**；当 `is_default=1` 且启用未删时取 `type_id`，否则为 `NULL`
  ；配合唯一键确保**同类型仅一条默认**。
- **读侧视图**：`v_sys_dict_item_enabled` 只暴露启用且未删的项目，减少 where 条件重复。

---


## <a id="sec-4"></a> 4. 在业务表中的引用方式（示例与规范）

### <a id="sec-4-1"></a> 4.1 字段命名规范（以字典编码关联）

- 业务表字段直接使用语义字段名并保存“字典编码”（`item_code`），不保存 `*_id`；并在字段名上以 `_code` 后缀明确这是“字典编码”：
  - 例如 `http_method_code VARCHAR(32) NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=http_method)'`。
  - 例如 `endpoint_usage_code VARCHAR(32) NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=endpoint_usage)'`。
  - 例如 `scope_code VARCHAR(16) NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=scope)'`。
  - 例如 `lifecycle_status_code VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE: sys_dict_item.item_code (type=lifecycle_status)'`。

- 不与 `sys_dict_*` 建立物理外键；由应用层在写入/变更时校验“编码存在且类型匹配”。

### <a id="sec-4-2"></a> 4.2 端点定义示例（以编码关联字典）

> 以 `reg_prov_endpoint_def` 的 `http_method_code`、`endpoint_usage_code` 两个字段为例：

```sql
-- 示例：在新建表时直接保存“字典编码”，不使用 *_id；（旧表迁移见 §8）
CREATE TABLE reg_prov_endpoint_def
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provenance_id   BIGINT UNSIGNED NOT NULL,
  scope_code      VARCHAR(16)     NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=scope)',
    task_type       VARCHAR(64)     NULL,
    task_type_key   VARCHAR(64)     NOT NULL DEFAULT 'ALL',
    endpoint_name   VARCHAR(64)     NOT NULL,
  http_method_code     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=http_method)',
  endpoint_usage_code  VARCHAR(32)     NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=endpoint_usage)',
  lifecycle_status_code VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE: sys_dict_item.item_code (type=lifecycle_status)',
    -- ... 其它字段略 ...
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
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='端点定义（示例：以编码关联字典）';
```

### <a id="sec-4-3"></a> 4.3 应用层校验（保证“类型匹配 + 有效性”）

- MySQL 的 CHECK 不能引用子查询，难以在数据库层强约束“`http_method` 一定属于 `http_method` 类型”。
- 实践做法（写路径）：
  - 入参以 `(typeCode, itemCode)` 或直接 `itemCode` 形式传入；
  - 应用层通过 `v_sys_dict_item_enabled(type_code, item_code)` 校验存在性与启用状态；
  - 校验通过后直接持久化 `item_code` 到业务表；无需也不生成/保存 `item_id`。
  - 读路径可按需联查 `v_sys_dict_item_enabled` 以获得展示名（display_name）。

---
