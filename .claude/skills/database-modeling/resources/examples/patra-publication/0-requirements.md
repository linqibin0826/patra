# 阶段 0：需求分析 - Patra 出版物管理示例

> **项目背景**：Patra 医学文献数据平台需要存储和管理从 PubMed、EPMC 等数据源采集的医学出版物信息

---

## 📋 业务需求描述

### 核心功能
存储医学文献出版物的核心元数据，包括：
- 出版物基本信息（标题、摘要、发表日期、期刊）
- 唯一标识符（PMID、DOI）
- 作者信息及其所属机构
- 引用统计和出版类型

### 关键业务规则
1. 每个出版物必须有唯一的 PMID（PubMed ID）
2. 一篇出版物可以有多个作者（多对多关系）
3. 需要记录作者顺序、第一作者、通讯作者
4. 支持全文检索（标题和摘要）
5. 需要追踪引用次数的变化

---

## 🎯 核心实体识别

### 主实体
1. **Publication（出版物）**
   - 医学文献的核心元数据
   - 聚合根：负责管理出版物生命周期

2. **Author（作者）**
   - 文献作者信息
   - 可被多个出版物共享

3. **PublicationAuthor（出版物-作者关联）**
   - 多对多关系表
   - 包含作者排序、角色等额外信息

### 实体关系
- Publication ↔ Author：多对多（通过 PublicationAuthor）
- PublicationAuthor 包含：author_order, is_first_author, is_corresponding

---

## 📊 数据量预估

| 实体 | 初始规模 | 年增长量 | 5年规模 | 说明 |
|------|---------|---------|---------|------|
| Publication | 10万条 | 50万条/年 | 260万条 | 采集历史数据 + 新增 |
| Author | 5万人 | 10万人/年 | 55万人 | 去重后的作者 |
| PublicationAuthor | 50万条 | 250万条/年 | 1300万条 | 平均每篇 5 个作者 |

**存储预估**：
- Publication 表：约 2GB（5年）
- Author 表：约 200MB（5年）
- PublicationAuthor 表：约 800MB（5年）

---

## 🔍 查询场景分析

### 高频查询（>1000次/天）
1. **按 PMID 精确查询**
   ```sql
   SELECT * FROM publication WHERE pmid = ? AND deleted = 0
   ```
   **索引需求**：UNIQUE INDEX on pmid

2. **按 DOI 查询**
   ```sql
   SELECT * FROM publication WHERE doi = ? AND deleted = 0
   ```
   **索引需求**：INDEX on doi

3. **全文检索**
   ```sql
   SELECT * FROM publication
   WHERE MATCH(title, abstract) AGAINST (? IN NATURAL LANGUAGE MODE)
     AND deleted = 0
   ```
   **索引需求**：FULLTEXT INDEX on (title, abstract)

### 中频查询（100-1000次/天）
4. **按发表日期范围查询**
   ```sql
   SELECT * FROM publication
   WHERE publication_date BETWEEN ? AND ?
     AND deleted = 0
   ORDER BY publication_date DESC
   ```
   **索引需求**：INDEX on publication_date

5. **查询作者的所有出版物**
   ```sql
   SELECT p.* FROM publication p
   JOIN publication_author pa ON p.id = pa.publication_id
   WHERE pa.author_id = ? AND p.deleted = 0
   ORDER BY p.publication_date DESC
   ```
   **索引需求**：INDEX on author_id, INDEX on publication_id

### 低频查询（<100次/天）
6. **统计作者出版物数量**
7. **按期刊名称查询**
8. **按出版类型过滤**

---

## ⚙️ 技术栈确认

**确定的技术栈（Patra 项目标准）：**
- ✅ **数据库**：MySQL 8.0+（InnoDB 引擎）
- ✅ **持久化框架**：Spring Data JPA + Hibernate 6.6
- ✅ **架构风格**：六边形架构 + DDD
- ✅ **Spring Boot**：3.5.7
- ✅ **Java 版本**：Java 25

**强制规范：**
- **字符集**：`utf8mb4` + `utf8mb4_unicode_ci` 排序规则
- **时区**：所有 TIMESTAMP 字段使用 UTC
- **主键**：BIGINT（应用层雪花 ID 预分配）
- **软删除**：使用 `deleted_at` 字段（TIMESTAMP），配合 JPA `@SQLRestriction`
- **乐观锁**：使用 `version` 字段（BIGINT），配合 JPA `@Version`
- **审计字段**：标准审计字段（id, version, created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted_at）

---

## 🎯 非功能需求

### 性能要求
- 全文检索响应时间 < 200ms（95th percentile）
- 按 PMID/DOI 查询响应时间 < 50ms
- 支持 1000 QPS 查询并发

### 可用性要求
- 服务可用性 99.9%
- 支持读写分离
- 定期数据备份

### 扩展性要求
- 支持水平扩展（分库分表预案）
- 支持历史数据归档

---

## 📝 特殊字段说明

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
      "details": "Updated citation count"
    }
  ]
  ```

### ip_address（请求者 IP）
- **类型**：VARBINARY(16)
- **用途**：记录数据操作的来源 IP（支持 IPv4/IPv6）
- **存储格式**：二进制存储，节省空间

---

## ✅ 需求确认检查清单

- [x] 核心业务需求已明确
- [x] 实体和关系已识别
- [x] 数据量级已预估
- [x] 查询场景已分析
- [x] 技术栈已确定
- [x] 非功能需求已定义

---

## 下一步

需求分析完成，进入 **[阶段 1：ER 图设计](1-er-design.md)**
