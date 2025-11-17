# 阶段 3：SQL DDL 自动生成指南

> **生成说明**：此阶段由 Claude 根据阶段 2 的表设计自动生成 SQL DDL 语句

---

## 🎯 生成目标

根据用户提供的表结构设计，自动生成：
1. **标准化的 CREATE TABLE 语句**
2. **完整的索引定义**
3. **规范的审计字段**
4. **执行说明和测试 SQL**

---

## 📋 生成步骤

### 步骤 1：分析表设计

从阶段 2 的表设计中提取：
- 表名和表说明
- 业务字段（名称、类型、长度、约束、默认值）
- 索引需求（唯一索引、普通索引、全文索引）
- 关系定义（外键字段）

### 步骤 2：应用标准审计字段

根据表的特性，添加审计字段：

**完整审计字段**（主表、聚合根）：
```sql
`record_remarks`    JSON            NULL COMMENT 'JSON 数组，备注/变更日志',
`version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
`ip_address`        VARBINARY(16)   NULL COMMENT '请求者 IP（二进制，支持 IPv4/IPv6）',
`created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
`created_by`        BIGINT UNSIGNED NULL COMMENT '创建人 ID',
`created_by_name`   VARCHAR(100)    NULL COMMENT '创建人姓名',
`updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间（UTC）',
`updated_by`        BIGINT UNSIGNED NULL COMMENT '更新人 ID',
`updated_by_name`   VARCHAR(100)    NULL COMMENT '更新人姓名',
`deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志（0=正常，1=已删除）'
```

**简化审计字段**（关联表、字典表）：
```sql
`created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）'
```

### 步骤 3：生成索引定义

根据索引需求生成：

**主键索引**（自动创建）：
```sql
PRIMARY KEY (`id`)
```

**唯一索引**：
```sql
UNIQUE INDEX `uk_字段名` (`字段名`) COMMENT '说明'
```

**普通索引**：
```sql
INDEX `idx_字段名` (`字段名`) COMMENT '说明'
```

**复合索引**：
```sql
INDEX `idx_字段1_字段2` (`字段1`, `字段2`) COMMENT '说明'
```

**全文索引**：
```sql
FULLTEXT INDEX `ft_字段名` (`字段1`, `字段2`) COMMENT '全文索引'
```

**软删除复合索引**（推荐）：
```sql
INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引'
```

### 步骤 4：应用 DDL 模板

使用 `resources/templates/sql-ddl-template.sql` 模板生成最终 DDL：

```sql
-- =============================================================================
-- 表名：{{table_name}}
-- 描述：{{table_description}}
-- 创建时间：{{current_timestamp}}
-- =============================================================================

CREATE TABLE IF NOT EXISTS `{{table_name}}` (
    -- =========================================================================
    -- 业务字段
    -- =========================================================================
    {{business_fields}}

    -- =========================================================================
    -- 审计字段（标准化）
    -- =========================================================================
    {{audit_fields}}

    -- =========================================================================
    -- 索引定义
    -- =========================================================================
    {{indexes}}

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{{table_comment}}';
```

### 步骤 5：生成索引分析报告

为每个表生成索引分析表格：

| 索引名 | 类型 | 字段 | 选择性 | 建议 |
|-------|------|------|--------|------|
| PRIMARY KEY | 聚簇索引 | id | 1.00 | ✅ 必需 |
| uk_xxx | 唯一索引 | xxx | 0.98 | ✅ 推荐 |
| idx_xxx | 普通索引 | xxx | 0.85 | ✅ 推荐 |

**选择性评级标准**：
- `> 0.95`：极高 ✅ 强烈推荐
- `0.80-0.95`：高 ✅ 推荐
- `0.50-0.80`：中 ⚠️ 视情况
- `< 0.50`：低 ❌ 不推荐

### 步骤 6：生成执行说明

包含：
1. 创建数据库语句
2. 执行顺序说明（考虑表依赖关系）
3. 验证表结构 SQL
4. 测试插入语句
5. 测试查询语句

---

## 📐 生成规范

### 1. 命名规范

| 对象类型 | 命名规范 | 示例 |
|---------|---------|------|
| 表名 | 小写，下划线分隔 | `publication`, `user_profile` |
| 字段名 | 小写，下划线分隔 | `created_at`, `user_id` |
| 主键索引 | PRIMARY KEY | `PRIMARY KEY (id)` |
| 唯一索引 | `uk_` 前缀 | `uk_pmid`, `uk_user_email` |
| 普通索引 | `idx_` 前缀 | `idx_status`, `idx_created_at` |
| 全文索引 | `ft_` 前缀 | `ft_title_abstract` |

### 2. 数据类型规范

| 业务需求 | 推荐类型 | 说明 |
|---------|---------|------|
| 主键 | `BIGINT UNSIGNED` | 支持大数据量 |
| 外键 | `BIGINT UNSIGNED` | 与主键类型一致 |
| 业务编码 | `VARCHAR(32-64)` | 变长字符串 |
| 名称/标题 | `VARCHAR(100-500)` | 根据实际需求 |
| 长文本 | `TEXT` | >2000 字符 |
| 金额 | `DECIMAL(19,4)` | 精确计算 |
| 计数 | `INT UNSIGNED` | 正整数 |
| 布尔 | `TINYINT(1)` | 0/1 |
| 枚举/状态 | `VARCHAR(32)` | 字典代码 |
| 日期 | `DATE` | 仅日期 |
| 时间戳 | `TIMESTAMP(6)` | 微秒精度，UTC |
| JSON | `JSON` | 结构化数据 |

### 3. 约束规范

- **NOT NULL**：业务必需字段（如标题、状态）
- **NULL**：可选字段（如描述、备注）
- **DEFAULT**：提供合理默认值（如 `deleted = 0`, `version = 0`）
- **UNIQUE**：唯一约束（如 PMID、邮箱）
- **AUTO_INCREMENT**：主键自增

### 4. 注释规范

- 每个字段必须有 `COMMENT '说明'`
- 表必须有 `COMMENT='表说明'`
- 索引必须有 `COMMENT '索引说明'`

---

## ⚠️ 注意事项

### 强制规范
- ✅ **字符集**：必须使用 `utf8mb4`
- ✅ **排序规则**：必须使用 `utf8mb4_unicode_ci`
- ✅ **引擎**：必须使用 `InnoDB`
- ✅ **时区**：所有 TIMESTAMP 字段使用 UTC
- ✅ **审计字段**：根据表类型添加完整或简化审计字段

### 不推荐
- ❌ **ENUM 类型**：使用 VARCHAR + 字典表代替
- ❌ **物理外键**：使用应用层校验代替
- ❌ **NULL 默认值**：明确指定默认值
- ❌ **过多索引**：每个表 3-6 个索引为宜

---

## 📝 生成输出格式

生成的 DDL 应保存为独立文件，建议命名：
- 项目内：`resources/stages/stage-3-sql-ddl.md`
- 示例：`resources/examples/{project-name}/3-sql-ddl.md`

文件结构：
```markdown
# 阶段 3：SQL DDL 生成 - {项目名称}

## 📋 生成的 DDL 语句

### 表：{table_name}
```sql
CREATE TABLE ...
```

## 📊 索引分析报告
...

## 🔧 执行说明
...

## 🧪 测试 SQL
...

## ⚠️ 注意事项
...
```

---

## 🔗 相关资源

- **模板文件**：[sql-ddl-template.sql](../templates/sql-ddl-template.sql)
- **审计字段规范**：[standard-audit-fields.sql](../guides/standard-audit-fields.sql)
- **索引优化指南**：[index-optimization-guide.md](../guides/index-optimization-guide.md)

---

## 下一步

SQL DDL 生成完成后，可选：
- **[阶段 4：领域模型映射](stage-4-domain-model-generation.md)** - 生成 DDD 领域模型
- **[阶段 5：设计决策记录](stage-5-decisions.md)** - 记录关键设计决策
