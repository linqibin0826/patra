# 阶段 0:需求分析 - patra_catalog 数据库设计

> **项目背景**:Patra 医学文献数据平台目录服务,负责存储和管理从 PubMed、EPMC 等数据源采集的医学出版物信息

> **作者**:Patra Lin
> **创建日期**:2025-01-18

---

## 📋 业务需求描述

### 核心功能

**数据库名称**：`patra_catalog`
**服务模块**：`/Users/linqibin/Desktop/Patra-api/patra-catalog`

存储医学文献出版物的完整元数据，包括：

1. **出版物核心信息**
   - 基础元数据（标题、摘要、语言、出版日期）
   - 唯一标识符（PMID、DOI、PMC、PII 等）
   - 出版载体信息（期刊、书籍、会议）

2. **医学领域专有数据**
   - MeSH 主题词标引（医学主题词表）
   - 物质信息（化学物质、药物、生物制品）
   - 研究者和人物主题
   - 基因、临床试验等外部引用

3. **学术关系网络**
   - 作者及其机构关联
   - 资助信息（基金、项目）
   - 参考文献和引用关系
   - 相关项目（撤稿、勘误、评论）

4. **开放获取管理**
   - OA 状态跟踪
   - 多来源全文链接
   - 版本类型管理

### 关键业务规则

1. **唯一标识策略**
   - 主键使用雪花算法生成 19 位 BIGINT
   - PMID、DOI 允许为 NULL（部分文献可能缺失）
   - PMID 和 DOI 在主表冗余以优化高频查询

2. **多对多关系管理**
   - 一篇文献可以有多个作者（保留作者顺序）
   - 一个作者可以有多个机构（记录主要机构）
   - 一篇文献可以有多个 MeSH 主题词（记录主/副主题）
   - 一篇文献可以有多个 OA 来源（记录最佳 OA 状态）

3. **版本管理规则**
   - 不保留历史版本，始终存储最新数据
   - 使用乐观锁（version 字段）处理并发更新
   - 软删除支持数据恢复（deleted 字段）

4. **数据来源**
   - 从消息队列接收 `CanonicalPublication` 模型数据
   - 支持多数据源（PubMed、EPMC、EMBASE 等）
   - 定期更新补充 MeSH 词表和文献数据

5. **检索策略**
   - 数据库存储结构化数据
   - Elasticsearch 负责全文检索
   - 数据库提供精确查询和关联查询

---

## 🎯 核心实体识别

### 主实体

#### 1. 核心实体（6个）

**Publication（出版物）**
- 医学文献的核心元数据
- 聚合根：负责管理出版物生命周期
- 冗余 PMID/DOI/publication_year 优化查询

**Venue（出版载体）**
- 期刊、书籍、会议等载体信息
- 多态设计：通过 venue_type 区分类型
- 存储 ISSN/ISBN、出版商等共同属性

**VenueInstance（载体实例）**
- 具体的卷/期/版次信息
- 存储不完整日期（年/月/日分离字段）

**Identifier（标识符）**
- 存储所有类型标识符（PMID/DOI/PMC/PII/arXiv等）
- 支持扩展新标识符类型

**Author（作者）**
- 作者基本信息
- 支持 ORCID 等作者标识符

**Abstract（摘要）**
- 结构化/非结构化摘要
- 独立表存储，避免影响主表查询性能

#### 2. 医学领域实体（8个）

**MeSH相关（6个）**
- MeshDescriptor（主题词）
- MeshQualifier（限定词）
- MeshTreeNumber（树形编号）
- MeshEntryTerm（入口术语/同义词）
- MeshConcept（概念）
- PublicationMesh（文献-MeSH关联）

**其他医学实体（2个）**
- Substance（物质：化学物质、药物）
- PersonalNameSubject（人物主题）

#### 3. 关联实体（14个）

**人员机构（6个）**
- PublicationAuthor（文献-作者关联，含顺序）
- Affiliation（机构）
- AuthorAffiliation（作者-机构关联）
- Investigator（研究者）
- PublicationInvestigator（文献-研究者关联）

**分类索引（4个）**
- Keyword（关键词）
- PublicationKeyword（文献-关键词关联）
- PublicationType（出版类型）
- PublicationTypeMapping（文献-类型关联）

**其他关联（4个）**
- Funding（资助信息）
- PublicationFunding（文献-资助关联）
- Reference（参考文献）
- ExternalReference（外部引用：基因库、临床试验）

#### 4. 辅助实体（8个）

- RelatedItem（相关项目：撤稿、勘误）
- SupplementalObject（补充对象：图表、数据集）
- PublicationHistory（发布历史）
- PublicationDate（日期信息）
- PublicationMetadata（元数据：索引方法、状态）
- AlternativeAbstract（其他语言摘要）
- LanguageMapping（语言映射表）
- OaLocation（开放获取位置）

### 实体关系概览

```
Publication (聚合根)
├── 1:1 → Abstract
├── 1:1 → PublicationMetadata
├── N:1 → VenueInstance → Venue
├── 1:N → Identifier
├── 1:N → PublicationDate
├── M:N → Author (通过 PublicationAuthor)
│   └── M:N → Affiliation (通过 AuthorAffiliation)
├── M:N → MeshDescriptor (通过 PublicationMesh)
├── M:N → Keyword (通过 PublicationKeyword)
├── M:N → PublicationType (通过 PublicationTypeMapping)
├── M:N → Substance (通过 PublicationSubstance)
├── M:N → Funding (通过 PublicationFunding)
├── 1:N → Reference
├── 1:N → ExternalReference
├── 1:N → RelatedItem
├── 1:N → SupplementalObject
├── 1:N → PublicationHistory
└── 1:N → OaLocation
```

**总计：36张表**
- 核心实体表：6张
- 分类与索引表：12张（MeSH相关6张 + 关键词类型4张 + 物质2张）
- 人员与机构表：6张
- 关联信息表：7张
- 辅助管理表：5张

---

## 📊 数据量预估

| 实体类别 | 表名 | 初始规模 | 年增长量 | 5年规模 | 说明 |
|---------|------|---------|---------|---------|------|
| **核心实体** |
| | cat_publication | 1000万 | 500万/年 | 3500万 | 文献主表 |
| | cat_venue | 3万 | 5000/年 | 5.5万 | 期刊/书籍/会议 |
| | cat_venue_instance | 100万 | 20万/年 | 200万 | 卷期信息 |
| | cat_identifier | 3000万 | 1000万/年 | 8000万 | 所有标识符类型 |
| | cat_author | 1000万 | 500万/年 | 3500万 | 去重后的作者 |
| | cat_abstract | 800万 | 400万/年 | 2800万 | 约80%文献有摘要 |
| **MeSH体系** |
| | cat_mesh_descriptor | 3.5万 | 500/年 | 3.75万 | MeSH主题词 |
| | cat_mesh_qualifier | 100 | 5/年 | 125 | MeSH限定词 |
| | cat_mesh_tree_number | 8万 | 1000/年 | 8.5万 | 树形编号 |
| | cat_mesh_entry_term | 35万 | 5000/年 | 37.5万 | 同义词 |
| | cat_mesh_concept | 10万 | 2000/年 | 11万 | 概念 |
| | cat_publication_mesh | 8000万 | 4000万/年 | 2.8亿 | 平均每篇8个MeSH |
| **关联关系** |
| | cat_publication_author | 4000万 | 2000万/年 | 1.4亿 | 平均每篇4个作者 |
| | cat_affiliation | 20万 | 5万/年 | 45万 | 研究机构 |
| | cat_author_affiliation | 5000万 | 2500万/年 | 1.75亿 | 作者-机构 |
| | cat_keyword | 200万 | 100万/年 | 700万 | 关键词 |
| | cat_publication_keyword | 2000万 | 1000万/年 | 7000万 | 平均每篇2个 |
| | cat_funding | 200万 | 100万/年 | 700万 | 资助项目 |
| | cat_publication_funding | 500万 | 250万/年 | 1750万 | 约50%有资助 |
| **其他** |
| | cat_reference | 1.5亿 | 7500万/年 | 5.25亿 | 平均每篇15条引用 |
| | cat_oa_location | 1000万 | 500万/年 | 3500万 | OA链接 |

**存储预估（5年）**：
- 核心实体表：约 50GB
- MeSH 关联表：约 100GB
- 作者关联表：约 200GB
- 参考文献表：约 300GB
- 其他辅助表：约 50GB
- **总计**：约 700GB（未压缩，未含索引）

---

## 🔍 查询场景分析

### 高频查询（>1000次/天）

#### 1. 按 PMID 精确查询
```sql
SELECT * FROM cat_publication
WHERE pmid = ? AND deleted = 0;
```
**索引需求**：UNIQUE INDEX on `pmid`
**性能目标**：< 10ms（主表冗余字段，直接查询）

#### 2. 按 DOI 精确查询
```sql
SELECT * FROM cat_publication
WHERE doi = ? AND deleted = 0;
```
**索引需求**：UNIQUE INDEX on `doi`
**性能目标**：< 10ms

#### 3. 按年份范围查询
```sql
SELECT * FROM cat_publication
WHERE publication_year BETWEEN ? AND ?
  AND deleted = 0
ORDER BY publication_year DESC
LIMIT 100;
```
**索引需求**：INDEX on `publication_year`
**性能目标**：< 100ms（主表冗余字段，避免 JOIN）

#### 4. 按标题关键词检索（转发到 ES）
```sql
-- 数据库仅用于 ID 精确查询，全文检索由 Elasticsearch 处理
SELECT * FROM cat_publication
WHERE id IN (?, ?, ...) AND deleted = 0;
```
**索引需求**：主键索引（自动）
**性能目标**：< 50ms

### 中频查询（100-1000次/天）

#### 5. 查询作者的所有出版物
```sql
SELECT p.* FROM cat_publication p
JOIN cat_publication_author pa ON p.id = pa.publication_id
WHERE pa.author_id = ? AND p.deleted = 0
ORDER BY p.publication_year DESC
LIMIT 100;
```
**索引需求**：
- INDEX on `cat_publication_author(author_id, publication_id)`
- INDEX on `cat_publication(id, publication_year)`
**性能目标**：< 200ms

#### 6. 查询 MeSH 主题词相关文献
```sql
SELECT p.* FROM cat_publication p
JOIN cat_publication_mesh pm ON p.id = pm.publication_id
WHERE pm.mesh_descriptor_id = ? AND p.deleted = 0
ORDER BY p.publication_year DESC
LIMIT 100;
```
**索引需求**：
- INDEX on `cat_publication_mesh(mesh_descriptor_id, publication_id)`
- 复合索引 on `cat_publication_mesh(mesh_descriptor_id, is_major_topic)`
**性能目标**：< 300ms

#### 7. 按机构查询文献
```sql
SELECT DISTINCT p.* FROM cat_publication p
JOIN cat_publication_author pa ON p.id = pa.publication_id
JOIN cat_author_affiliation aa ON pa.author_id = aa.author_id
WHERE aa.affiliation_id = ? AND p.deleted = 0
ORDER BY p.publication_year DESC
LIMIT 100;
```
**索引需求**：
- INDEX on `cat_author_affiliation(affiliation_id, author_id)`
- INDEX on `cat_publication_author(author_id, publication_id)`
**性能目标**：< 500ms

#### 8. 查询开放获取文献
```sql
SELECT * FROM cat_publication
WHERE is_oa = 1 AND oa_status = 'gold'
  AND deleted = 0
ORDER BY publication_year DESC
LIMIT 100;
```
**索引需求**：INDEX on `(is_oa, oa_status, publication_year)`
**性能目标**：< 200ms

### 低频查询（<100次/天）

#### 9. 统计作者出版物数量
```sql
SELECT author_id, COUNT(*) as pub_count
FROM cat_publication_author pa
JOIN cat_publication p ON pa.publication_id = p.id
WHERE p.deleted = 0
GROUP BY author_id
ORDER BY pub_count DESC
LIMIT 100;
```

#### 10. 按资助机构统计
```sql
SELECT f.agency, COUNT(DISTINCT pf.publication_id) as pub_count
FROM cat_funding f
JOIN cat_publication_funding pf ON f.id = pf.funding_id
GROUP BY f.agency
ORDER BY pub_count DESC;
```

#### 11. 查询引用关系
```sql
SELECT * FROM cat_reference
WHERE publication_id = ?
ORDER BY reference_order;
```

#### 12. 按出版类型筛选
```sql
SELECT p.* FROM cat_publication p
JOIN cat_publication_type_mapping ptm ON p.id = ptm.publication_id
JOIN cat_publication_type pt ON ptm.type_id = pt.id
WHERE pt.type_name = 'Clinical Trial' AND p.deleted = 0;
```

---

## ⚙️ 技术栈确认

**确定的技术栈（Patra 项目标准）：**

- ✅ **数据库**：MySQL 8.0+（InnoDB 引擎）
- ✅ **持久化框架**：MyBatis-Plus 3.5.x
- ✅ **架构风格**：六边形架构 + DDD
- ✅ **Spring Boot**：3.5.7
- ✅ **Spring Cloud**：2025.0.0
- ✅ **Java 版本**：Java 25
- ✅ **构建工具**：Maven
- ✅ **全文检索**：Elasticsearch（用于标题、摘要检索）
- ✅ **消息队列**：用于接收采集数据
- ✅ **注册中心**：Nacos

**强制规范：**

**数据库规范**
- **表前缀**：`cat_`（catalog 缩写）
- **字符集**：`utf8mb4` + `utf8mb4_unicode_ci` 排序规则
- **时区**：所有 TIMESTAMP 字段使用 UTC
- **主键**：BIGINT（雪花算法生成 19 位数字 ID）
- **软删除**：使用 `deleted` 字段（TINYINT(1)），配合 MyBatis-Plus `@TableLogic`
- **乐观锁**：使用 `version` 字段（BIGINT UNSIGNED），配合 MyBatis-Plus `@Version`

**审计字段（标准9个字段）**
```sql
id              BIGINT PRIMARY KEY           -- 主键
version         BIGINT UNSIGNED NOT NULL     -- 乐观锁版本
created_at      TIMESTAMP(6) NOT NULL        -- 创建时间（UTC）
created_by      BIGINT                       -- 创建人ID
created_by_name VARCHAR(100)                 -- 创建人姓名
updated_at      TIMESTAMP(6) NOT NULL        -- 更新时间（UTC）
updated_by      BIGINT                       -- 更新人ID
updated_by_name VARCHAR(100)                 -- 更新人姓名
deleted         TINYINT(1) NOT NULL          -- 软删除标记
```

**命名规范**
- 表名：小写 + 下划线（cat_publication）
- 字段名：小写 + 下划线（publication_year）
- 外键字段：主表名单数 + _id（author_id）
- 布尔字段：is_ 前缀（is_oa）
- 日期字段：_at 后缀（created_at）

---

## 🎯 非功能需求

### 性能要求

**查询性能**
- PMID/DOI 精确查询响应时间：< 10ms（95th percentile）
- 按年份范围查询响应时间：< 100ms
- MeSH 关联查询响应时间：< 300ms
- 作者/机构查询响应时间：< 500ms
- 支持查询并发：500-1000 QPS

**写入性能**
- 批量插入吞吐量：10000 条/秒
- 单条更新响应时间：< 50ms
- 支持写入并发：100-200 TPS

**全文检索**
- Elasticsearch 索引延迟：< 5 秒
- 全文检索响应时间：< 200ms（由 ES 保证）

### 可用性要求

- **服务可用性**：99.9%（年停机时间 < 8.76 小时）
- **读写分离**：支持主从复制，读操作分流到从库
- **数据备份**：
  - 每日增量备份
  - 每周全量备份
  - 保留最近 30 天备份
  - 跨地域备份存储
- **故障恢复**：
  - RPO（恢复点目标）：< 1 小时
  - RTO（恢复时间目标）：< 4 小时

### 扩展性要求

**数据规模扩展**
- 初始规模：1000 万文献
- 年增长：500 万文献/年
- 5 年目标：3500 万文献
- 10 年预案：7000 万文献

**水平扩展支持**
- 支持分库分表预案（按年份分表）
- 支持历史数据归档（3年前数据归档到冷存储）
- 支持冷热数据分离

**索引存储扩展**
- Elasticsearch 集群支持水平扩展
- 分片策略：按年份分片
- 副本策略：至少 1 个副本

### 数据质量要求

**完整性保证**
- 外键约束：所有关联表设置外键（开发环境）
- 应用层校验：生产环境由应用层保证引用完整性
- 级联策略：软删除（置 deleted=1），不物理删除

**唯一性保证**
- PMID 唯一性：UNIQUE INDEX（允许 NULL）
- DOI 唯一性：UNIQUE INDEX（允许 NULL）
- 雪花 ID 全局唯一

**一致性保证**
- 冗余字段同步：应用层事务保证
- 定期数据校验：每周校验冗余字段一致性
- 审计日志：记录所有数据变更

---

## 📝 关键设计决策

### 5.1 标识符冗余设计

**决策**：主表冗余高频标识符 + 独立标识符表存储全量

- `cat_publication` 表冗余字段：
  - `pmid` VARCHAR(20) - PubMed ID（医学文献最常用）
  - `doi` VARCHAR(255) - 数字对象标识符（跨学科通用）
- `cat_identifier` 表存储所有标识符类型

**理由**：
- 避免 90% 以上的 JOIN 操作
- 响应时间从 ms 级降到 μs 级
- 其他标识符（PMC、PII、arXiv 等）仍存储在标识符表

### 5.2 出版载体多态设计

**决策**：采用单表继承模式

- `cat_venue` 表包含所有载体类型的共同字段
- 使用 `venue_type` 枚举区分类型（JOURNAL/BOOK/CONFERENCE/OTHER）
- 特定类型的专有字段使用 JSON 扩展字段存储

**理由**：
- 避免多表联接的性能开销
- 便于统一管理和查询
- 支持未来新载体类型的扩展

### 5.3 日期字段设计

**决策**：分离字段存储 + publication_year 冗余

#### 不完整日期的处理

医学文献的出版日期并非总是完整的年月日：
- 只有年份：约 30% 的文献
- 年+月：约 40% 的文献
- 完整日期：约 30% 的文献

`cat_venue_instance` 表采用分离字段存储原始日期精度：

```sql
publication_year  SMALLINT NOT NULL   -- 出版年份（必填）
publication_month TINYINT NULL        -- 出版月份 1-12（可选）
publication_day   TINYINT NULL        -- 出版日期 1-31（可选）
```

**优势**：
- 精确表达不完整性（NULL 表示"不存在此精度"）
- 避免虚假精度（不会将 "2023-06" 存为 "2023-06-01"）
- 数值类型索引效率高，存储紧凑

#### publication_year 冗余设计

`cat_publication` 主表冗余 `publication_year` 字段：

**理由**：
- 按年份筛选是最高频操作（>60% 查询）
- 避免每次查询都需要 JOIN venue_instance
- 存储成本低（仅 2 字节/行）

### 5.4 MeSH 完整结构设计

**决策**：扩展 MeSH 表结构以完整支持 PubMed 数据导入

- 从 3 张表扩展到 6 张核心表
- 独立存储树形编号（支持多位置）
- 分离概念层级和入口术语

**理由**：
- 数据完整性：PubMed MeSH XML 包含复杂的层次结构
- 导入效率：分表存储便于批量导入
- 查询优化：树形编号独立索引，支持层次查询

### 5.5 语言字段三层设计

**决策**：原始值保留 + 映射表标准化 + 生成列基础语种

- `cat_publication` 表语言字段（3 个）：
  - `language_raw` VARCHAR(50) - 原始语言值（如 "eng", "Chinese"）
  - `language_code` VARCHAR(10) - 标准语言代码（如 "en", "zh-CN"）
  - `language_base` VARCHAR(5) - 基础语种（生成列，如 "en", "zh"）
- `cat_language_mapping` 表维护映射关系

**理由**：
- 保留原始值，永不丢失外部采集的原始数据
- 应对数据不规范（ISO 639-2、全称、中文描述等）
- 生成列自动提取基础语种，支持按基础语种统计

### 5.6 开放获取（OA）状态设计

**决策**：主表精简冗余 + 独立 OA 位置表存储详细信息

- `cat_publication` 表冗余字段（仅 2 个）：
  - `is_oa` BOOLEAN - 是否有任何形式的开放获取
  - `oa_status` VARCHAR(20) - 最佳 OA 状态（gold/green/hybrid/bronze/closed）
- `cat_oa_location` 表存储所有 OA 位置详情

**理由**：
- 主表仅保留最关键的两个字段，避免臃肿
- 一篇文献可能有多个 OA 来源（出版商、PMC、机构仓储等）
- 记录每个位置的版本类型和许可证信息

---

## 🔧 特殊字段说明

### ext_data（扩展数据）

- **类型**：JSON
- **用途**：存储非结构化的附加信息，支持未来扩展
- **示例**：
  ```json
  {
    "custom_field1": "value1",
    "legacy_data": {
      "old_system_id": "12345"
    }
  }
  ```

### record_remarks（记录备注）

- **类型**：JSON 数组
- **用途**：存储变更日志、审计记录、数据来源说明
- **示例**：
  ```json
  [
    {
      "timestamp": "2025-01-15T10:30:00Z",
      "operator": "sync_service",
      "action": "data_sync",
      "details": "Synced from PubMed API"
    },
    {
      "timestamp": "2025-01-16T14:20:00Z",
      "operator": "admin",
      "action": "manual_correction",
      "details": "Updated MeSH terms"
    }
  ]
  ```

### ip_address（请求者 IP）

- **类型**：VARBINARY(16)
- **用途**：记录数据操作的来源 IP（支持 IPv4/IPv6）
- **存储格式**：二进制存储，节省空间
- **示例**：
  ```sql
  -- 存储 IPv4
  INSERT INTO ... (ip_address) VALUES (INET6_ATON('192.168.1.1'));

  -- 存储 IPv6
  INSERT INTO ... (ip_address) VALUES (INET6_ATON('2001:db8::1'));

  -- 查询
  SELECT INET6_NTOA(ip_address) FROM ...;
  ```

---

## ✅ 需求确认检查清单

- [x] 核心业务需求已明确
  - [x] 数据来源明确（消息队列 + CanonicalPublication 模型）
  - [x] 数据规模明确（初始 1000 万，年增长 500 万）
  - [x] 更新策略明确（不保留历史版本，始终最新）

- [x] 实体和关系已识别
  - [x] 36 张表分类清晰（核心 6 + 分类 12 + 人员 6 + 关联 7 + 辅助 5）
  - [x] 主外键关系已梳理
  - [x] 多对多关系处理明确

- [x] 数据量级已预估
  - [x] 各表初始规模和增长量已评估
  - [x] 5 年存储预估约 700GB

- [x] 查询场景已分析
  - [x] 高频查询（4 个）- 精确查询和年份筛选
  - [x] 中频查询（4 个）- 关联查询
  - [x] 低频查询（4 个）- 统计分析
  - [x] 索引需求已识别

- [x] 技术栈已确定
  - [x] 数据库：MySQL 8.0+
  - [x] 持久化：MyBatis-Plus 3.5.x
  - [x] 架构：六边形架构 + DDD
  - [x] 检索：Elasticsearch
  - [x] 消息队列：已确认

- [x] 非功能需求已定义
  - [x] 性能要求（查询 < 500ms，并发 500-1000 QPS）
  - [x] 可用性要求（99.9%，读写分离，备份策略）
  - [x] 扩展性要求（分库分表预案，数据归档）
  - [x] 数据质量要求（完整性、唯一性、一致性）

- [x] 关键设计决策已记录
  - [x] 标识符冗余设计
  - [x] 出版载体多态设计
  - [x] 日期字段设计
  - [x] MeSH 完整结构设计
  - [x] 语言字段三层设计
  - [x] OA 状态设计

---

## 📌 下一步

需求分析完成，进入 **阶段 1：ER 图设计**

### 待完成任务

1. **ER 图设计**
   - 绘制完整的实体关系图
   - 明确主外键关系
   - 标注基数关系（1:1, 1:N, M:N）

2. **表结构详细设计**
   - 定义每个表的具体字段
   - 确定数据类型和长度
   - 设置约束条件（NOT NULL, UNIQUE, CHECK）

3. **SQL DDL 生成**
   - 生成建表语句
   - 包含注释说明
   - 提供示例数据

4. **领域模型映射**
   - 设计 Java 实体类
   - 定义仓储接口
   - 提取值对象

5. **架构决策记录**
   - 记录设计决策（ADR）
   - 评估风险点
   - 制定优化计划

---

## 附录A：数据源说明

主要数据源包括：
- **PubMed/MEDLINE**：美国国家医学图书馆（NLM）维护的生物医学文献数据库
- **Europe PMC**：欧洲生物医学文献数据库
- **EMBASE**：Elsevier 生物医学数据库
- **其他医学数据库**：根据需求扩展

---

## 附录B：参考标准

- **PubMed/MEDLINE 元数据标准**：https://www.nlm.nih.gov/bsd/licensee/elements_descriptions.html
- **MeSH 医学主题词表**：https://www.nlm.nih.gov/mesh/
- **Dublin Core 元数据标准**：https://www.dublincore.org/specifications/dublin-core/
- **Schema.org ScholarlyArticle 规范**：https://schema.org/ScholarlyArticle

---

## 附录C：术语表

| 术语 | 英文全称 | 说明 |
|------|---------|------|
| MeSH | Medical Subject Headings | 医学主题词表，NLM 维护的受控词表 |
| PMID | PubMed Identifier | PubMed 唯一标识符（8位数字） |
| DOI | Digital Object Identifier | 数字对象标识符（跨学科通用） |
| PMC | PubMed Central | 生物医学全文数据库（开放获取） |
| PII | Publisher Item Identifier | 出版商项目标识符 |
| ORCID | Open Researcher and Contributor ID | 研究者唯一标识符 |
| Venue | - | 出版载体，包括期刊、书籍、会议等 |
| Instance | - | 载体实例，如具体的卷期、版次 |
| OA | Open Access | 开放获取（免费公开获取全文） |
| CanonicalPublication | - | 标准化出版物模型（patra-common-model） |

---

*本文档为 patra_catalog 数据库设计的需求分析阶段成果，后续将根据需求变化持续更新。*
