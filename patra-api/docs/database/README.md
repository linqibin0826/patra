# 数据库文档索引

> Papertrace 数据库设计、ER 图、迁移脚本文档

---

## 📄 文档列表

### ER 图（Entity-Relationship Diagrams）
- **[核心数据模型 ER 图](./er-diagrams.md)** - 完整的数据库表关系图
  - **patra-ingest 数据模型**：Schedule → Plan → Slice → Task → Run → Batch → Cursor
  - **patra-registry 数据模型**：Provenance + 多维配置 + 表达式
  - 包含表关系说明、索引设计、约束规则

---

## 🔗 相关文档

### 数据迁移
- **Flyway 迁移脚本**：
  - [patra-ingest 迁移脚本](../../patra-ingest/patra-ingest-infra/src/main/resources/db/migration/)
  - [patra-registry 迁移脚本](../../patra-registry/patra-registry-infra/src/main/resources/db/migration/)
  - 命名规范：`V{version}__{description}.sql`（如：`V001__create_plan_table.sql`）

### 数据字典
- **审计字段**：
  - `created_at`：创建时间（TIMESTAMP，默认 CURRENT_TIMESTAMP）
  - `updated_at`：更新时间（TIMESTAMP，ON UPDATE CURRENT_TIMESTAMP）
  - `created_by`：创建人（VARCHAR，可选）
  - `updated_by`：更新人（VARCHAR，可选）

- **字典码规范**：
  - 枚举类型统一使用 `VARCHAR(50)`
  - 状态字段：`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`
  - 来源类型：`PUBMED`, `PMC`, `CROSSREF`, `EMBASE`

- **JSON 字段**：
  - 使用 `JSON` 或 `LONGTEXT` 类型
  - 代码中使用 `JsonNode`（Jackson）处理
  - 示例：`filter_config`（过滤器配置）、`metadata`（元数据）

### 索引设计
- **主键**：`id BIGINT AUTO_INCREMENT PRIMARY KEY`
- **唯一索引**：幂等键（`uk_batch_id`、`uk_plan_slice_num`）
- **复合索引**：查询优化（`idx_plan_status_updated`、`idx_task_plan_state`）
- **外键**：显式定义关系（`fk_task_plan_id`）

---

## 📝 贡献指南

### 添加新表
1. 在对应模块的 `db/migration/` 目录创建迁移脚本
2. 遵循命名规范：`V{version}__{description}.sql`
3. 包含表结构、索引、约束、注释
4. 更新 ER 图文档

### 迁移脚本模板
```sql
-- V001__create_example_table.sql
CREATE TABLE IF NOT EXISTS example_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '名称',
    status VARCHAR(50) NOT NULL COMMENT '状态：PENDING/ACTIVE/INACTIVE',
    metadata JSON COMMENT '元数据（JSON）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='示例表';
```

### 修改表结构
```sql
-- V002__add_column_to_example_table.sql
ALTER TABLE example_table
    ADD COLUMN description TEXT COMMENT '描述',
    ADD INDEX idx_name (name);
```

### 数据初始化
```sql
-- V003__init_example_data.sql
INSERT INTO example_table (name, status, metadata)
VALUES 
    ('Example 1', 'ACTIVE', '{"key": "value"}'),
    ('Example 2', 'PENDING', '{"key": "value"}');
```

---

## 🗂️ 数据库规范

### 表命名规范
- 使用下划线分隔的小写字母：`plan_task`、`provenance_source`
- 前缀表示模块：`ingest_plan`、`registry_provenance`
- 关联表：`{entity1}_{entity2}`（如：`plan_slice`）

### 字段命名规范
- 使用下划线分隔的小写字母：`plan_id`、`source_code`
- 布尔字段：`is_*`、`has_*`、`can_*`（如：`is_active`）
- 时间字段：`*_at`、`*_time`（如：`created_at`、`start_time`）

### 索引命名规范
- 主键：无需命名（默认 `PRIMARY`）
- 唯一索引：`uk_{column_name}`（如：`uk_batch_id`）
- 普通索引：`idx_{column1}__{column2}`（如：`idx_plan_status_updated`）
- 外键：`fk_{table}_{column}`（如：`fk_task_plan_id`）

### 数据类型选择
| 用途 | 数据类型 | 说明 |
|------|---------|------|
| 主键 | `BIGINT AUTO_INCREMENT` | 支持大数据量 |
| 字符串（短） | `VARCHAR(50/100/200)` | 根据实际长度选择 |
| 字符串（长） | `TEXT` / `LONGTEXT` | 不确定长度时使用 |
| JSON | `JSON` 或 `LONGTEXT` | JSON 类型支持查询优化 |
| 枚举 | `VARCHAR(50)` | 避免使用 ENUM（不灵活） |
| 布尔 | `TINYINT(1)` | 0/1 表示 false/true |
| 时间戳 | `TIMESTAMP` | 支持时区转换 |
| 日期时间 | `DATETIME` | 不涉及时区 |
| 整数 | `INT` / `BIGINT` | 根据范围选择 |
| 小数 | `DECIMAL(10, 2)` | 精确计算（如金额） |

---

## 📊 数据库统计

### 表数量统计
```sql
-- 统计各模块的表数量
SELECT 
    SUBSTRING_INDEX(table_name, '_', 1) AS module,
    COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'papertrace'
GROUP BY module
ORDER BY table_count DESC;
```

### 索引使用情况
```sql
-- 查看未使用的索引
SELECT 
    table_schema AS database_name,
    table_name,
    index_name
FROM information_schema.statistics
WHERE table_schema = 'papertrace'
  AND index_name NOT IN (
      SELECT DISTINCT index_name 
      FROM information_schema.statistics 
      WHERE table_schema = 'papertrace'
  );
```

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：数据库文档索引 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
