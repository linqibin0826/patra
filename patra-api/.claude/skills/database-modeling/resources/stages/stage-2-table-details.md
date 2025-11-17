# 阶段 2：详细表设计

## 📊 表结构设计模板

### 表设计文档格式

```markdown
## 表名：[table_name]

**表说明：** [业务用途描述]
**记录数预估：** [初始/年增长/5年规模]
**主要查询场景：** [列举 2-3 个]
```

---

## 📝 字段设计规范

### 业务字段设计表

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键 | 聚簇索引 |
| code | VARCHAR | 64 | NOT NULL, UNIQUE | - | 业务编码 | 唯一索引 |
| name | VARCHAR | 200 | NOT NULL | - | 名称 | 需要(查询) |
| type | VARCHAR | 32 | NOT NULL | 'DEFAULT' | 类型(字典) | 需要(过滤) |
| status | VARCHAR | 32 | NOT NULL | 'ACTIVE' | 状态(字典) | 需要(过滤) |
| config | JSON | - | NULL | NULL | 配置信息 | 否 |
| remarks | TEXT | - | NULL | NULL | 备注说明 | 否 |

### 审计字段（标准化）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| version | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 乐观锁版本号 |
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC) |
| created_by | BIGINT UNSIGNED | NULL | 创建人ID |
| created_by_name | VARCHAR(100) | NULL | 创建人姓名 |
| updated_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE | 更新时间(UTC) |
| updated_by | BIGINT UNSIGNED | NULL | 更新人ID |
| updated_by_name | VARCHAR(100) | NULL | 更新人姓名 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除标志 |

---

## 🔑 数据类型选择指南

### 常用类型对照表

| 业务需求 | 推荐类型 | 长度/精度 | 说明 | 示例 |
|---------|---------|-----------|------|------|
| 主键 | BIGINT UNSIGNED | - | 自增主键 | 1, 2, 3... |
| 哈希值 | CHAR | 64 | SHA-256 | 'e3b0c44298fc1c14...' |
| 业务编码 | VARCHAR | 32-64 | 变长字符串 | 'ORD-2025-001' |
| 名称/标题 | VARCHAR | 100-500 | 中文支持 | '医学文献标题' |
| 摘要/描述 | TEXT | - | 长文本 | '这是一段描述...' |
| 金额 | DECIMAL | (19,4) | 精确计算 | 12345.6789 |
| 比率 | DECIMAL | (5,4) | 百分比 | 0.9850 (98.5%) |
| 计数 | INT UNSIGNED | - | 正整数 | 100 |
| 布尔 | TINYINT | 1 | 0/1 | 0=false, 1=true |
| 枚举 | VARCHAR | 32 | 字典代码 | 'ACTIVE' |
| 日期 | DATE | - | 仅日期 | '2025-01-15' |
| 时间戳 | TIMESTAMP | 6 | 微秒精度 | '2025-01-15 10:30:45.123456' |
| JSON | JSON | - | 结构化数据 | '{"key": "value"}' |
| 二进制 | VARBINARY | 变长 | IP地址等 | 0x0A0B0C0D |

### 字段长度设计原则

```sql
-- 常见字段长度标准
VARCHAR(32)   -- 编码、状态、类型等枚举值
VARCHAR(64)   -- 业务编码、用户名
VARCHAR(100)  -- 姓名、简短标题
VARCHAR(200)  -- 标准标题、名称
VARCHAR(500)  -- 长标题、URL
VARCHAR(1000) -- 摘要、简介
VARCHAR(2000) -- 详细描述
TEXT          -- 正文、详细内容（>2000字符）
```

---

## 📈 索引设计策略

### 索引选择性分析

| 选择性范围 | 评级 | 是否建索引 | 说明 |
|-----------|------|------------|------|
| > 0.95 | 极高 ✅ | 强烈推荐 | 几乎唯一，查询效率最高 |
| 0.80-0.95 | 高 ✅ | 推荐 | 区分度好，值得建索引 |
| 0.50-0.80 | 中 ⚠️ | 视情况 | 结合查询频率决定 |
| 0.20-0.50 | 低 ⚠️ | 谨慎 | 仅在必要时建立 |
| < 0.20 | 极低 ❌ | 不推荐 | 效果差，反而增加开销 |

### 索引类型决策树

```
需要索引吗？
├─ 是主键？→ PRIMARY KEY (自动聚簇索引)
├─ 要求唯一？→ UNIQUE INDEX
├─ 高频等值查询？
│  ├─ 单字段？→ INDEX (B-Tree)
│  └─ 多字段？→ COMPOSITE INDEX
├─ 范围查询？
│  ├─ 时间范围？→ INDEX on timestamp
│  └─ 数值范围？→ INDEX on number
├─ 模糊查询？
│  ├─ 前缀匹配？→ INDEX (可用)
│  └─ 全文搜索？→ FULLTEXT INDEX
└─ 否 → 不建索引
```

### 索引定义示例

```sql
-- 主键索引（自动创建）
PRIMARY KEY (`id`)

-- 唯一索引
UNIQUE INDEX `uk_user_email` (`email`)

-- 单列索引
INDEX `idx_status` (`status`)

-- 复合索引（注意字段顺序）
INDEX `idx_user_date` (`user_id`, `created_at`)

-- 前缀索引（长字符串）
INDEX `idx_title` (`title`(50))

-- 全文索引（MySQL 5.6+）
FULLTEXT INDEX `ft_content` (`title`, `abstract`)
```

---

## 🎯 设计检查清单

### 字段设计检查
- [ ] 数据类型是否最优？
- [ ] 长度是否合理？
- [ ] 是否需要 NOT NULL 约束？
- [ ] 默认值是否合理？
- [ ] 是否遗漏业务字段？

### 索引设计检查
- [ ] 主键索引已定义？
- [ ] 唯一约束已添加？
- [ ] 外键字段已索引？
- [ ] 查询条件字段已索引？
- [ ] 复合索引顺序是否最优？

### 性能优化检查
- [ ] 是否需要冗余字段？
- [ ] 是否需要分区表？
- [ ] 大字段是否需要分离？
- [ ] 是否需要读写分离？

### 规范性检查
- [ ] 包含标准审计字段？
- [ ] 字段命名符合规范？
- [ ] 注释是否完整清晰？
- [ ] 字符集是 UTF8MB4？

---

## 💡 最佳实践提醒

### DO ✅
- 使用 BIGINT 作为主键（支持大数据量）
- 为外键字段建立索引
- 使用 VARCHAR 而非 CHAR（除非定长）
- JSON 字段用于灵活配置
- 时间字段使用 UTC 时区
- 金额使用 DECIMAL 而非 FLOAT

### DON'T ❌
- 避免使用 ENUM（使用 VARCHAR + 字典表）
- 避免 NULL 作为默认值（除非业务需要）
- 避免过多的索引（影响写入性能）
- 避免 SELECT * 查询
- 避免在 WHERE 子句对字段函数操作

---

## 参考资源

需要更详细的指导：
- [索引优化深度指南](../guides/index-optimization-guide.md)
- [标准审计字段 SQL](../guides/standard-audit-fields.sql)

---

## 下一步

表设计完成后，我会自动：
1. **生成 SQL DDL** - 创建可执行的建表语句
2. **生成领域模型** - 如需要 DDD 映射

或查看：**[阶段 5：设计决策记录](stage-4-decisions.md)**
