# 阶段 3：SQL DDL 生成 - Patra 出版物管理示例

> **生成说明**：根据阶段 2 的详细表设计，生成可执行的 SQL DDL 语句

---

## 📋 生成的 DDL 语句

### 表：publication（出版物表）

```sql
-- =============================================================================
-- 表名：publication
-- 描述：存储医学文献出版物的核心元数据
-- 创建时间：2025-01-17 19:30:00
-- =============================================================================

CREATE TABLE IF NOT EXISTS `publication` (
    -- =========================================================================
    -- 业务字段
    -- =========================================================================
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pmid`              VARCHAR(20)     NULL UNIQUE COMMENT 'PubMed ID',
    `doi`               VARCHAR(200)    NULL COMMENT 'Digital Object Identifier',
    `title`             VARCHAR(1000)   NOT NULL COMMENT '文献标题',
    `abstract`          TEXT            NULL COMMENT '摘要',
    `journal_name`      VARCHAR(500)    NULL COMMENT '期刊名称',
    `publication_date`  DATE            NULL COMMENT '发表日期',
    `publication_type`  VARCHAR(50)     NULL COMMENT '出版物类型',
    `language`          VARCHAR(10)     NULL DEFAULT 'en' COMMENT '语言代码',
    `citation_count`    INT UNSIGNED    NULL DEFAULT 0 COMMENT '引用次数',

    -- =========================================================================
    -- 审计字段（标准化）
    -- =========================================================================
    `record_remarks`    JSON            NULL COMMENT 'JSON 数组，备注/变更日志',
    `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`        VARBINARY(16)   NULL COMMENT '请求者 IP（二进制，支持 IPv4/IPv6）',
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`        BIGINT UNSIGNED NULL COMMENT '创建人 ID',
    `created_by_name`   VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间（UTC）',
    `updated_by`        BIGINT UNSIGNED NULL COMMENT '更新人 ID',
    `updated_by_name`   VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志（0=正常，1=已删除）',

    -- =========================================================================
    -- 索引定义
    -- =========================================================================
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pmid` (`pmid`) COMMENT 'PMID 唯一索引',
    INDEX `idx_doi` (`doi`) COMMENT 'DOI 索引',
    INDEX `idx_publication_date` (`publication_date`) COMMENT '发表日期索引',
    FULLTEXT INDEX `ft_title_abstract` (`title`, `abstract`) COMMENT '全文索引',
    INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医学文献出版物表';
```

### 表：author（作者表）

```sql
-- =============================================================================
-- 表名：author
-- 描述：存储文献作者信息
-- 创建时间：2025-01-17 19:30:00
-- =============================================================================

CREATE TABLE IF NOT EXISTS `author` (
    -- =========================================================================
    -- 业务字段
    -- =========================================================================
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `orcid`             VARCHAR(20)     NULL UNIQUE COMMENT 'ORCID 标识符',
    `full_name`         VARCHAR(200)    NOT NULL COMMENT '作者全名',
    `given_name`        VARCHAR(100)    NULL COMMENT '名',
    `family_name`       VARCHAR(100)    NULL COMMENT '姓',
    `email`             VARCHAR(200)    NULL COMMENT '电子邮箱',
    `affiliation`       VARCHAR(500)    NULL COMMENT '所属机构',

    -- =========================================================================
    -- 审计字段（简化）
    -- =========================================================================
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',

    -- =========================================================================
    -- 索引定义
    -- =========================================================================
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_orcid` (`orcid`) COMMENT 'ORCID 唯一索引',
    INDEX `idx_full_name` (`full_name`) COMMENT '全名索引',
    INDEX `idx_email` (`email`) COMMENT '邮箱索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文献作者表';
```

### 表：publication_author（文献-作者关联表）

```sql
-- =============================================================================
-- 表名：publication_author
-- 描述：文献与作者的多对多关系表
-- 创建时间：2025-01-17 19:30:00
-- =============================================================================

CREATE TABLE IF NOT EXISTS `publication_author` (
    -- =========================================================================
    -- 业务字段
    -- =========================================================================
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `publication_id`    BIGINT UNSIGNED NOT NULL COMMENT '文献 ID',
    `author_id`         BIGINT UNSIGNED NOT NULL COMMENT '作者 ID',
    `author_order`      INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '作者排序',
    `is_corresponding`  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否通讯作者',
    `is_first_author`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否第一作者',

    -- =========================================================================
    -- 审计字段（简化）
    -- =========================================================================
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',

    -- =========================================================================
    -- 索引定义
    -- =========================================================================
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pub_author` (`publication_id`, `author_id`) COMMENT '防止重复关联',
    INDEX `idx_author_id` (`author_id`) COMMENT '作者 ID 索引',
    INDEX `idx_author_order` (`publication_id`, `author_order`) COMMENT '排序索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文献-作者关联表';
```

---

## 📊 索引分析报告

### publication 表索引分析

| 索引名 | 类型 | 字段 | 选择性 | 建议 |
|-------|------|------|--------|------|
| PRIMARY KEY | 聚簇索引 | id | 1.00 | ✅ 必需 |
| uk_pmid | 唯一索引 | pmid | 0.98 | ✅ 推荐 |
| idx_doi | 普通索引 | doi | 0.95 | ✅ 推荐 |
| idx_publication_date | 普通索引 | publication_date | 0.75 | ⚠️ 考虑查询频率 |
| ft_title_abstract | 全文索引 | title, abstract | N/A | ✅ 搜索必需 |
| idx_deleted_updated | 复合索引 | deleted, updated_at | N/A | ✅ 软删除过滤 |

**索引总数：6 个（合理范围）**

### author 表索引分析

| 索引名 | 类型 | 字段 | 选择性 | 建议 |
|-------|------|------|--------|------|
| PRIMARY KEY | 聚簇索引 | id | 1.00 | ✅ 必需 |
| uk_orcid | 唯一索引 | orcid | 0.99 | ✅ 推荐 |
| idx_full_name | 普通索引 | full_name | 0.90 | ✅ 推荐 |
| idx_email | 普通索引 | email | 0.85 | ✅ 推荐 |

**索引总数：4 个（轻量）**

### publication_author 表索引分析

| 索引名 | 类型 | 字段 | 选择性 | 建议 |
|-------|------|------|--------|------|
| PRIMARY KEY | 聚簇索引 | id | 1.00 | ✅ 必需 |
| uk_pub_author | 唯一索引 | publication_id, author_id | 1.00 | ✅ 必需 |
| idx_author_id | 普通索引 | author_id | 0.80 | ✅ 推荐 |
| idx_author_order | 复合索引 | publication_id, author_order | 0.95 | ✅ 推荐 |

**索引总数：4 个（高效）**

---

## 🔧 执行说明

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS `patra_db`
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE `patra_db`;
```

### 2. 执行建表语句

按以下顺序执行：
1. `publication` 表
2. `author` 表
3. `publication_author` 表（依赖前两个表）

### 3. 验证表结构

```sql
-- 查看表结构
SHOW CREATE TABLE publication\G
SHOW CREATE TABLE author\G
SHOW CREATE TABLE publication_author\G

-- 查看索引
SHOW INDEX FROM publication;
SHOW INDEX FROM author;
SHOW INDEX FROM publication_author;
```

### 4. 验证字符集和排序规则

```sql
SELECT
    TABLE_NAME,
    TABLE_COLLATION,
    ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'patra_db'
  AND TABLE_NAME IN ('publication', 'author', 'publication_author');
```

**预期结果：**
- `TABLE_COLLATION`: utf8mb4_unicode_ci
- `ENGINE`: InnoDB

---

## 🧪 测试 SQL

### 插入测试数据

```sql
-- 1. 插入出版物
INSERT INTO publication (
    pmid, doi, title, abstract, journal_name,
    publication_date, publication_type, language, citation_count
) VALUES (
    '12345678',
    '10.1234/example.2025',
    'Example Medical Research Paper',
    'This is an example abstract for testing purposes.',
    'Nature Medicine',
    '2025-01-15',
    'Journal Article',
    'en',
    0
);

-- 2. 插入作者
INSERT INTO author (orcid, full_name, given_name, family_name, email, affiliation)
VALUES
    ('0000-0001-2345-6789', 'John Doe', 'John', 'Doe', 'john.doe@example.com', 'Stanford University'),
    ('0000-0002-3456-7890', 'Jane Smith', 'Jane', 'Smith', 'jane.smith@example.com', 'MIT');

-- 3. 关联出版物和作者
INSERT INTO publication_author (publication_id, author_id, author_order, is_first_author, is_corresponding)
VALUES
    (1, 1, 1, 1, 0),  -- John Doe，第一作者
    (1, 2, 2, 0, 1);  -- Jane Smith，第二作者，通讯作者
```

### 查询测试

```sql
-- 测试 1：按 PMID 查询
SELECT * FROM publication WHERE pmid = '12345678' AND deleted = 0;

-- 测试 2：全文检索
SELECT * FROM publication
WHERE MATCH(title, abstract) AGAINST ('medical research' IN NATURAL LANGUAGE MODE)
  AND deleted = 0;

-- 测试 3：查询出版物的所有作者（按顺序）
SELECT
    p.title,
    a.full_name,
    pa.author_order,
    pa.is_first_author,
    pa.is_corresponding
FROM publication p
JOIN publication_author pa ON p.id = pa.publication_id
JOIN author a ON pa.author_id = a.id
WHERE p.id = 1
ORDER BY pa.author_order;

-- 测试 4：查询某作者的所有出版物
SELECT
    p.pmid,
    p.title,
    p.publication_date
FROM publication p
JOIN publication_author pa ON p.id = pa.publication_id
WHERE pa.author_id = 1
  AND p.deleted = 0
ORDER BY p.publication_date DESC;
```

---

## ⚠️ 注意事项

### 1. 字符集
- **强制要求**：`utf8mb4` + `utf8mb4_unicode_ci`
- **原因**：支持 Emoji、特殊字符和多语言
- **验证**：执行"验证字符集和排序规则"SQL

### 2. 时区
- **所有时间字段使用 UTC**
- **应用层转换**：显示时转换为用户时区
- **存储示例**：
  ```sql
  -- 存储时（应用层转为 UTC）
  INSERT INTO publication (created_at) VALUES (UTC_TIMESTAMP(6));

  -- 查询时（应用层转为本地时区）
  SELECT CONVERT_TZ(created_at, '+00:00', '+08:00') FROM publication;
  ```

### 3. 外键约束
- **本设计未使用物理外键**
- **原因**：
  - 避免锁竞争
  - 提高并发性能
  - 由应用层保证参照完整性
- **MyBatis-Plus 处理**：Repository 层校验关联关系

### 4. 软删除
- **查询时必须加 `WHERE deleted = 0`**
- **MyBatis-Plus 配置**：
  ```java
  @TableLogic
  private Boolean deleted;
  ```
- **自动处理**：MyBatis-Plus 自动在查询中添加 `deleted = 0` 条件

### 5. 乐观锁
- **更新时检查 `version` 字段**
- **MyBatis-Plus 配置**：
  ```java
  @Version
  private Long version;
  ```
- **冲突处理**：
  ```java
  // 更新时自动检查版本号
  int rows = publicationMapper.updateById(publication);
  if (rows == 0) {
      throw new OptimisticLockingFailureException("数据已被其他用户修改");
  }
  ```

### 6. IP 地址存储
- **存储时转换**：
  ```sql
  INSERT INTO publication (ip_address) VALUES (INET6_ATON('192.168.1.1'));
  ```
- **查询时转换**：
  ```sql
  SELECT INET6_NTOA(ip_address) FROM publication;
  ```

---

## 📈 性能优化建议

### 1. 索引优化
- **定期分析索引使用情况**：
  ```sql
  SELECT * FROM sys.schema_unused_indexes WHERE object_schema = 'patra_db';
  ```
- **考虑删除未使用索引**

### 2. 查询优化
- **避免 SELECT \***：只查询需要的字段
- **使用覆盖索引**：将常用查询字段加入索引
- **LIMIT 分页**：大数据量查询使用 LIMIT

### 3. 全文索引优化
- **调整全文索引参数**：
  ```sql
  -- 设置最小词长度
  SET GLOBAL innodb_ft_min_token_size = 2;

  -- 重建全文索引
  ALTER TABLE publication DROP INDEX ft_title_abstract;
  ALTER TABLE publication ADD FULLTEXT INDEX ft_title_abstract (title, abstract);
  ```

### 4. 分区策略（可选）
如果数据量达到千万级，考虑按 `publication_date` 分区：
```sql
ALTER TABLE publication
PARTITION BY RANGE (YEAR(publication_date)) (
    PARTITION p2020 VALUES LESS THAN (2021),
    PARTITION p2021 VALUES LESS THAN (2022),
    PARTITION p2022 VALUES LESS THAN (2023),
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

---

## 📋 后续步骤

- ✅ DDL 已生成完成
- ⬜ 如需生成领域模型，请告诉我 → **[阶段 4：领域模型生成](#)**
- ⬜ 如需记录设计决策，请告诉我 → **[阶段 5：设计决策记录](5-decisions.md)**
- ⬜ 如需调整索引策略，请提供查询模式
- ⬜ 如需添加分区策略，请提供数据增长预估
