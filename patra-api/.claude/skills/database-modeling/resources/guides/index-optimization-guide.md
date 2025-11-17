# 索引优化详细指南

## 1. 索引选择性（Index Selectivity）

### 1.1 定义

```sql
索引选择性 = COUNT(DISTINCT column_name) / COUNT(*)
```

选择性值范围：0.0 ~ 1.0
- **1.0** = 所有值都不同（如主键）
- **0.5** = 一半的值不同
- **0.01** = 100 条记录中只有 1 个不同值

### 1.2 实际计算

```sql
-- 查询单个字段的选择性
SELECT
  COUNT(DISTINCT username) / COUNT(*) AS selectivity,
  COUNT(DISTINCT username) AS distinct_values,
  COUNT(*) AS total_rows
FROM user;

-- 输出示例:
-- selectivity: 0.95
-- distinct_values: 9500
-- total_rows: 10000
```

```sql
-- 批量查询多个字段的选择性
SELECT
  'username' AS column_name,
  COUNT(DISTINCT username) / COUNT(*) AS selectivity
FROM user
UNION ALL
SELECT
  'email',
  COUNT(DISTINCT email) / COUNT(*)
FROM user
UNION ALL
SELECT
  'status',
  COUNT(DISTINCT status) / COUNT(*)
FROM user;

-- 输出示例:
-- column_name  | selectivity
-- username     | 0.95
-- email        | 0.98
-- status       | 0.05
```

### 1.3 选择性分级标准

| 选择性范围 | 索引建议 | 典型字段 | 示例 |
|-----------|---------|---------|------|
| **> 0.8** | ✅ 强烈推荐建立单列索引 | 主键、唯一标识符 | id, email, pmid, doi |
| **0.3 - 0.8** | ✅ 可以考虑单列索引 | 高区分度字段 | username, phone, order_no |
| **0.1 - 0.3** | ⚠️ 需要评估查询模式 | 中区分度字段 | publication_year, category |
| **< 0.1** | ❌ 不推荐单列索引 | 低区分度字段 | status, gender, deleted |

---

## 2. 低选择性字段的索引策略

### 2.1 问题：为什么不为低选择性字段建索引？

**示例场景：** `user` 表有 100 万条记录，`status` 字段只有 2 个值（0=禁用, 1=启用）。

```sql
-- 查询活跃用户
SELECT * FROM user WHERE status = 1;
```

**如果为 `status` 建立索引：**
1. 索引选择性 = 2 / 1,000,000 = 0.000002（极低）
2. MySQL 优化器会发现：扫描索引 + 回表查询的成本 **高于** 直接全表扫描
3. 结果：**索引被忽略，执行全表扫描**

**验证：**

```sql
EXPLAIN SELECT * FROM user WHERE status = 1;

-- 输出:
-- type: ALL (全表扫描)
-- key: NULL (未使用索引)
-- rows: 1000000 (扫描全部记录)
```

**结论：** 为低选择性字段建索引是**浪费存储空间**且**无性能提升**。

---

### 2.2 策略 1: 不建索引

**适用场景：** 低选择性字段 + 其他高选择性条件

```sql
-- 查询: 活跃用户中用户名为 'john' 的记录
SELECT * FROM user
WHERE status = 1 AND username = 'john';

-- MySQL 优化器会:
-- 1. 使用 uk_username 索引快速定位到 'john'
-- 2. 然后检查 status = 1 条件（在内存中过滤）
-- 3. 不需要 status 索引

EXPLAIN 输出:
-- type: const
-- key: uk_username (使用用户名唯一索引)
-- rows: 1 (只扫描 1 条记录)
```

**适用条件：**
- 低选择性字段的查询总是与高选择性字段组合
- 未删除记录占 > 95%（deleted = 0 的记录占绝大多数）

---

### 2.3 策略 2: 组合索引（高选择性字段在前）

**适用场景：** 需要同时过滤多个条件，且低选择性字段的过滤很重要

```sql
-- 查询: 2025 年创建的活跃用户
SELECT * FROM user
WHERE created_at >= '2025-01-01'
  AND created_at < '2026-01-01'
  AND deleted = 0;

-- 组合索引设计:
KEY `idx_created_at_deleted` (`created_at`, `deleted`)

-- MySQL 优化器会:
-- 1. 使用 created_at 快速定位到时间范围内的记录
-- 2. 在索引中直接过滤 deleted = 0（不需要回表）
```

**组合索引顺序原则：**

```sql
-- ✅ 正确: 高选择性字段在前
KEY `idx_created_at_deleted` (`created_at`, `deleted`)
-- created_at 选择性 0.3, deleted 选择性 0.05

-- ❌ 错误: 低选择性字段在前
KEY `idx_deleted_created_at` (`deleted`, `created_at`)
-- deleted 只有 2 个值，索引树的第一层几乎无区分度
```

**EXPLAIN 分析：**

```sql
-- 使用正确的索引
EXPLAIN SELECT * FROM user
WHERE created_at >= '2025-01-01' AND deleted = 0;

-- 输出:
-- type: range
-- key: idx_created_at_deleted
-- rows: 5000 (只扫描时间范围内的记录)

-- 使用错误的索引
EXPLAIN SELECT * FROM user
WHERE created_at >= '2025-01-01' AND deleted = 0;

-- 输出:
-- type: index (索引全扫描)
-- key: idx_deleted_created_at
-- rows: 950000 (扫描所有 deleted=0 的记录)
```

---

### 2.4 策略 3: 查询改写

**场景：** 避免在低选择性字段上建索引，通过改写查询优化性能

```sql
-- ❌ 原查询: 直接过滤 deleted = 0
SELECT * FROM publication
WHERE deleted = 0
ORDER BY created_at DESC
LIMIT 10;

-- 问题: deleted 字段选择性低，无法有效使用索引

-- ✅ 改写查询: 利用 created_at 索引 + 应用层过滤
SELECT * FROM publication
WHERE created_at >= '2020-01-01'  -- 假设所有数据都在 2020 年后
  AND deleted = 0
ORDER BY created_at DESC
LIMIT 10;

-- MySQL 优化器会使用 idx_created_at 索引
```

**另一个例子：分页查询**

```sql
-- ❌ 原查询: 深度分页 + 低选择性过滤
SELECT * FROM publication
WHERE deleted = 0
LIMIT 10000, 10;

-- 问题: 需要扫描 10010 条记录

-- ✅ 改写查询: 使用游标分页
SELECT * FROM publication
WHERE id > 10000 AND deleted = 0
LIMIT 10;

-- 使用主键索引，性能更好
```

---

## 3. 常见低选择性字段及其处理

### 3.1 软删除字段 `deleted`

| 取值 | 占比 | 说明 |
|------|------|------|
| 0 (活动) | 95% - 99% | 绝大多数记录 |
| 1 (已删除) | 1% - 5% | 极少数记录 |

**索引策略：**

```sql
-- ❌ 不要单独为 deleted 建索引
KEY `idx_deleted` (`deleted`)  -- 错误！

-- ✅ 将 deleted 放在组合索引末尾
KEY `idx_username_deleted` (`username`, `deleted`)
KEY `idx_created_at_deleted` (`created_at`, `deleted`)
```

---

### 3.2 状态字段 `status`

| 取值 | 占比 | 说明 |
|------|------|------|
| 0 (禁用) | 10% | 少数记录 |
| 1 (启用) | 90% | 绝大多数记录 |

**索引策略：**

```sql
-- 场景 1: 查询启用用户（占 90%）
SELECT * FROM user WHERE status = 1;
-- 不需要索引，全表扫描更快

-- 场景 2: 查询禁用用户（占 10%）
SELECT * FROM user WHERE status = 0;
-- 可以考虑部分索引（Partial Index）- MySQL 8.0.13+

-- 部分索引（Functional Index）
CREATE INDEX idx_disabled_users ON user ((CASE WHEN status = 0 THEN 1 ELSE NULL END));
-- 只为禁用用户建索引，节省空间
```

---

### 3.3 性别字段 `gender`

| 取值 | 占比 | 说明 |
|------|------|------|
| M (男性) | 50% | 一半记录 |
| F (女性) | 50% | 一半记录 |

**索引策略：**

```sql
-- ❌ 永远不要为 gender 建索引
-- 选择性 = 2 / 总数 ≈ 0.00002

-- ✅ 如果需要按性别统计，使用物化视图或汇总表
CREATE TABLE user_gender_stats (
  gender CHAR(1) PRIMARY KEY,
  count BIGINT,
  updated_at TIMESTAMP
);

-- 定期更新统计表
INSERT INTO user_gender_stats (gender, count, updated_at)
SELECT gender, COUNT(*), NOW()
FROM user
GROUP BY gender
ON DUPLICATE KEY UPDATE count = VALUES(count), updated_at = NOW();
```

---

## 4. 高级技巧：覆盖索引（Covering Index）

### 4.1 定义

**覆盖索引：** 查询所需的所有字段都包含在索引中，不需要回表查询。

```sql
-- 查询: 获取用户名和邮箱
SELECT username, email FROM user WHERE username = 'john';

-- 如果有覆盖索引:
KEY `idx_username_email` (`username`, `email`)

-- MySQL 优化器会:
-- 1. 在索引树中找到 username = 'john'
-- 2. 直接从索引中读取 email（不需要回表到主键索引）
-- 3. 性能提升明显
```

**EXPLAIN 验证：**

```sql
EXPLAIN SELECT username, email FROM user WHERE username = 'john';

-- 输出:
-- type: ref
-- key: idx_username_email
-- Extra: Using index (覆盖索引)
```

### 4.2 覆盖索引 + 软删除

```sql
-- 查询: 获取活跃用户的用户名列表
SELECT username FROM user WHERE deleted = 0;

-- 覆盖索引设计:
KEY `idx_deleted_username` (`deleted`, `username`)

-- 尽管 deleted 选择性低，但作为覆盖索引的一部分:
-- 1. deleted = 0 过滤在索引中完成
-- 2. username 也在索引中，不需要回表
-- 3. 性能优于全表扫描
```

---

## 5. 索引监控与分析

### 5.1 查看未使用的索引

```sql
-- MySQL 8.0+ 支持索引统计
SELECT
  object_schema AS database_name,
  object_name AS table_name,
  index_name,
  COUNT_STAR AS total_access,
  COUNT_READ AS read_access,
  COUNT_WRITE AS write_access
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE object_schema = 'your_database'
  AND index_name IS NOT NULL
  AND index_name != 'PRIMARY'
  AND COUNT_STAR = 0  -- 从未使用的索引
ORDER BY object_name, index_name;
```

### 5.2 分析慢查询

```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;  -- 记录执行时间 > 1 秒的查询

-- 查看慢查询日志
SHOW VARIABLES LIKE 'slow_query_log_file';

-- 使用 mysqldumpslow 分析
-- mysqldumpslow -s t -t 10 /path/to/slow-query.log
```

### 5.3 EXPLAIN 分析关键指标

| 列名 | 最佳值 | 说明 |
|-----|--------|------|
| **type** | const > eq_ref > ref > range | 查询类型，越靠前性能越好 |
| **key** | 预期的索引名 | 实际使用的索引 |
| **rows** | 越小越好 | 预计扫描的行数 |
| **Extra** | Using index | 使用了覆盖索引（最佳） |

```sql
EXPLAIN SELECT * FROM user WHERE username = 'john';

-- 理想输出:
-- type: const (常量查询，最快)
-- key: uk_username (使用了唯一索引)
-- rows: 1 (只扫描 1 行)
-- Extra: NULL 或 Using index
```

---

## 6. 索引设计检查清单

### 设计阶段

- [ ] **计算索引选择性** - 使用 `COUNT(DISTINCT column) / COUNT(*)` 计算
- [ ] **优先为高选择性字段建索引** - 选择性 > 0.8
- [ ] **避免为低选择性字段建单列索引** - 选择性 < 0.1
- [ ] **组合索引顺序** - 高选择性字段在前
- [ ] **考虑覆盖索引** - 将常用查询字段组合成索引

### 实施阶段

- [ ] **使用 EXPLAIN 验证** - 确保索引被正确使用
- [ ] **监控索引大小** - 每个表不超过 5-6 个索引
- [ ] **检查索引碎片** - 定期 `OPTIMIZE TABLE`

### 维护阶段

- [ ] **监控未使用的索引** - 使用 `performance_schema` 查询
- [ ] **分析慢查询日志** - 识别缺失的索引
- [ ] **定期重新计算选择性** - 数据分布可能会变化

---

## 7. 实战案例

### 案例 1: Publication 表的索引优化

**场景：** 查询 2025 年发表的未删除文献，按发表日期倒序排列。

```sql
-- 原查询
SELECT * FROM publication
WHERE publication_date >= '2025-01-01'
  AND publication_date < '2026-01-01'
  AND deleted = 0
ORDER BY publication_date DESC
LIMIT 10;

-- 分析索引选择性
SELECT
  COUNT(DISTINCT publication_date) / COUNT(*) AS pub_date_selectivity,
  COUNT(DISTINCT deleted) / COUNT(*) AS deleted_selectivity
FROM publication;

-- 结果:
-- pub_date_selectivity: 0.15 (中等)
-- deleted_selectivity: 0.05 (低)

-- 索引设计:
KEY `idx_publication_date_deleted` (`publication_date`, `deleted`)

-- EXPLAIN 分析:
-- type: range
-- key: idx_publication_date_deleted
-- rows: 5000 (只扫描 2025 年的记录)
-- Extra: Using index condition
```

### 案例 2: User 表的组合索引优化

**场景：** 查询活跃用户中，用户名以 'john' 开头的记录。

```sql
-- 原查询
SELECT * FROM user
WHERE username LIKE 'john%'
  AND deleted = 0;

-- 索引设计:
KEY `idx_username_deleted` (`username`, `deleted`)

-- EXPLAIN 分析:
-- type: range
-- key: idx_username_deleted
-- rows: 100 (只扫描 username 前缀匹配的记录)
-- Extra: Using index condition

-- 如果顺序反过来:
KEY `idx_deleted_username` (`deleted`, `username`)
-- type: ref (只能使用 deleted = 0 过滤)
-- rows: 950000 (扫描所有活跃用户，然后过滤 username)
```

---

## 总结

1. **优先为高选择性字段建索引** (> 0.8)
2. **低选择性字段不单独建索引** (< 0.1)
3. **组合索引遵循"高选择性在前"原则**
4. **利用覆盖索引减少回表查询**
5. **定期监控索引使用情况，删除无用索引**
6. **使用 EXPLAIN 验证索引效果**
