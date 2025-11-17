# 阶段 2：详细表设计 - Patra 出版物管理示例

> **设计目标**：详细定义每个表的字段、类型、约束、索引

---

## 📊 表 1：publication（出版物表）

**表说明：** 存储医学文献出版物的核心元数据
**记录数预估：** 初始 10万 / 年增长 50万 / 5年规模 260万
**主要查询场景：**
1. 按 PMID 精确查询（>1000次/天）
2. 全文检索标题和摘要（>1000次/天）
3. 按发表日期范围查询（100-1000次/天）

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键 | 聚簇索引 |
| pmid | VARCHAR | 20 | NULL, UNIQUE | NULL | PubMed ID | 唯一索引 |
| doi | VARCHAR | 200 | NULL | NULL | Digital Object Identifier | 普通索引 |
| title | VARCHAR | 1000 | NOT NULL | - | 文献标题 | 全文索引 |
| abstract | TEXT | - | NULL | NULL | 摘要 | 全文索引 |
| journal_name | VARCHAR | 500 | NULL | NULL | 期刊名称 | 否 |
| publication_date | DATE | - | NULL | NULL | 发表日期 | 普通索引 |
| publication_type | VARCHAR | 50 | NULL | NULL | 出版物类型（期刊文章、综述等） | 否 |
| language | VARCHAR | 10 | NULL | 'en' | 语言代码（ISO 639-1） | 否 |
| citation_count | INT UNSIGNED | - | NULL | 0 | 引用次数 | 否 |

**字段设计说明：**
1. **pmid**：VARCHAR(20) 而非 INT，因为 PMID 可能有前导零
2. **doi**：VARCHAR(200) 支持长 DOI（如 `10.1234/extremely.long.doi.identifier`）
3. **title**：VARCHAR(1000) 而非 TEXT，支持全文索引，大多数标题 < 500 字符
4. **abstract**：TEXT 类型，摘要可能很长（>2000 字符）
5. **publication_date**：DATE 类型，仅需日期不需时间
6. **citation_count**：INT UNSIGNED，引用数不会为负

### 审计字段（标准化）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| record_remarks | JSON | NULL | JSON 数组，备注/变更日志 |
| version | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 乐观锁版本号 |
| ip_address | VARBINARY(16) | NULL | 请求者 IP（二进制，支持 IPv4/IPv6） |
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间（UTC） |
| created_by | BIGINT UNSIGNED | NULL | 创建人 ID |
| created_by_name | VARCHAR(100) | NULL | 创建人姓名 |
| updated_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE | 更新时间（UTC） |
| updated_by | BIGINT UNSIGNED | NULL | 更新人 ID |
| updated_by_name | VARCHAR(100) | NULL | 更新人姓名 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除标志（0=正常，1=已删除） |

### 索引定义

```sql
-- 主键索引（自动创建）
PRIMARY KEY (`id`)

-- 唯一索引
UNIQUE INDEX `uk_pmid` (`pmid`) COMMENT 'PMID 唯一索引'

-- 普通索引
INDEX `idx_doi` (`doi`) COMMENT 'DOI 索引'
INDEX `idx_publication_date` (`publication_date`) COMMENT '发表日期索引'

-- 全文索引（MySQL 5.6+）
FULLTEXT INDEX `ft_title_abstract` (`title`, `abstract`) COMMENT '标题和摘要全文索引'

-- 复合索引（软删除 + 更新时间）
INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引'
```

**索引选择性分析：**
| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_pmid | 0.98 | 极高 ✅ | PMID 几乎唯一（部分为 NULL） |
| idx_doi | 0.95 | 极高 ✅ | DOI 高度唯一 |
| idx_publication_date | 0.75 | 高 ✅ | 日期范围查询频繁 |
| ft_title_abstract | N/A | 必需 ✅ | 全文检索需求 |
| idx_deleted_updated | N/A | 推荐 ✅ | 查询"未删除 + 最新更新"组合 |

---

## 📊 表 2：author（作者表）

**表说明：** 存储文献作者信息
**记录数预估：** 初始 5万 / 年增长 10万 / 5年规模 55万
**主要查询场景：**
1. 按 ORCID 查询（中频）
2. 按姓名模糊查询（中频）

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键 | 聚簇索引 |
| orcid | VARCHAR | 20 | NULL, UNIQUE | NULL | ORCID 标识符（如 0000-0001-2345-6789） | 唯一索引 |
| full_name | VARCHAR | 200 | NOT NULL | - | 作者全名 | 普通索引 |
| given_name | VARCHAR | 100 | NULL | NULL | 名（First Name） | 否 |
| family_name | VARCHAR | 100 | NULL | NULL | 姓（Last Name） | 否 |
| email | VARCHAR | 200 | NULL | NULL | 电子邮箱 | 普通索引 |
| affiliation | VARCHAR | 500 | NULL | NULL | 所属机构 | 否 |

**字段设计说明：**
1. **orcid**：VARCHAR(20) 存储格式为 `0000-0001-2345-6789`
2. **full_name**：NOT NULL，至少需要完整姓名
3. **given_name / family_name**：NULL，部分数据源未拆分
4. **affiliation**：VARCHAR(500) 机构名称可能较长

### 审计字段（简化）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间（UTC） |

**为什么 Author 表不包含完整审计字段？**
- 作者信息相对静态，很少更新
- 不涉及并发冲突（无需 version）
- 简化表结构，节省存储

### 索引定义

```sql
-- 主键索引
PRIMARY KEY (`id`)

-- 唯一索引
UNIQUE INDEX `uk_orcid` (`orcid`) COMMENT 'ORCID 唯一索引'

-- 普通索引
INDEX `idx_full_name` (`full_name`) COMMENT '全名索引'
INDEX `idx_email` (`email`) COMMENT '邮箱索引'
```

**索引选择性分析：**
| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_orcid | 0.99 | 极高 ✅ | ORCID 绝对唯一 |
| idx_full_name | 0.90 | 高 ✅ | 姓名重复率低 |
| idx_email | 0.85 | 高 ✅ | 邮箱几乎唯一 |

---

## 📊 表 3：publication_author（文献-作者关联表）

**表说明：** 文献与作者的多对多关系表
**记录数预估：** 初始 50万 / 年增长 250万 / 5年规模 1300万
**主要查询场景：**
1. 查询某出版物的所有作者（按顺序）
2. 查询某作者的所有出版物

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL | - | 文献 ID | 复合唯一索引 |
| author_id | BIGINT UNSIGNED | - | NOT NULL | - | 作者 ID | 普通索引 |
| author_order | INT UNSIGNED | - | NOT NULL | 1 | 作者排序（1=第一作者，2=第二作者...） | 复合索引 |
| is_corresponding | TINYINT(1) | - | NOT NULL | 0 | 是否通讯作者（0=否，1=是） | 否 |
| is_first_author | TINYINT(1) | - | NOT NULL | 0 | 是否第一作者（0=否，1=是） | 否 |

**字段设计说明：**
1. **author_order**：INT UNSIGNED，值越小排序越前
2. **is_corresponding / is_first_author**：TINYINT(1) 布尔字段
3. **为什么 is_first_author 和 author_order=1 都存在？**
   - `author_order=1` 表示排序第一
   - `is_first_author=1` 表示业务上的"第一作者"身份
   - 部分情况下两者不一致（如共同第一作者）

### 审计字段（简化）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间（UTC） |

### 索引定义

```sql
-- 主键索引
PRIMARY KEY (`id`)

-- 唯一索引（防止重复关联）
UNIQUE INDEX `uk_pub_author` (`publication_id`, `author_id`) COMMENT '防止同一作者在同一出版物重复关联'

-- 普通索引
INDEX `idx_author_id` (`author_id`) COMMENT '作者 ID 索引，支持查询某作者的出版物'

-- 复合索引（排序）
INDEX `idx_author_order` (`publication_id`, `author_order`) COMMENT '出版物 + 排序，支持按顺序获取作者'
```

**索引选择性分析：**
| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_pub_author | 1.00 | 极高 ✅ | 组合绝对唯一 |
| idx_author_id | 0.80 | 高 ✅ | 查询作者出版物 |
| idx_author_order | 0.95 | 极高 ✅ | 出版物内作者排序 |

---

## 🎯 设计检查清单

### 字段设计检查
- [x] 数据类型最优（VARCHAR 长度合理，TEXT 用于长文本）
- [x] 长度合理（pmid=20, doi=200, title=1000）
- [x] NOT NULL 约束已添加（title, full_name）
- [x] 默认值合理（language='en', citation_count=0, deleted=0）
- [x] 无遗漏业务字段

### 索引设计检查
- [x] 主键索引已定义（所有表）
- [x] 唯一约束已添加（pmid, orcid, uk_pub_author）
- [x] 外键字段已索引（publication_id, author_id）
- [x] 查询条件字段已索引（publication_date, full_name）
- [x] 复合索引顺序最优（deleted + updated_at）

### 性能优化检查
- [x] 全文索引已添加（title, abstract）
- [x] 软删除字段包含在索引中（idx_deleted_updated）
- [x] 无过多索引（每张表 3-5 个索引）

### 规范性检查
- [x] 包含标准审计字段（publication 完整，author/publication_author 简化）
- [x] 字段命名符合规范（小写 + 下划线）
- [x] 注释完整清晰（已在索引定义中添加 COMMENT）
- [x] 字符集 UTF8MB4（将在 DDL 中定义）

---

## 📝 特殊设计决策

### 决策 1：为什么 PMID 和 DOI 允许 NULL？
**原因：**
- 部分文献可能没有 PMID（非 PubMed 来源）
- 部分文献可能没有 DOI（旧文献）
- 允许 NULL 提高数据灵活性

**约束：**
- 虽然允许 NULL，但通过唯一索引保证有值时不重复

### 决策 2：为什么使用 VARBINARY(16) 存储 IP？
**原因：**
- IPv4：4 字节（如 192.168.1.1 → 0xC0A80101）
- IPv6：16 字节
- 二进制存储比字符串节省 50%+ 空间

**转换方式：**
```sql
-- 存储时
INSERT INTO publication (ip_address) VALUES (INET6_ATON('192.168.1.1'));

-- 查询时
SELECT INET6_NTOA(ip_address) FROM publication;
```

### 决策 3：为什么使用 JSON 存储 record_remarks？
**原因：**
- 灵活存储任意结构的备注/变更日志
- 支持 MySQL 5.7+ 的 JSON 函数查询
- 避免创建额外的变更日志表

**示例查询：**
```sql
-- 查询包含特定操作的记录
SELECT * FROM publication
WHERE JSON_CONTAINS(
  record_remarks,
  '{"action": "manual_correction"}',
  '$'
);
```

---

## 下一步

详细表设计完成，进入 **[阶段 3：SQL DDL 生成](3-sql-ddl.md)**
