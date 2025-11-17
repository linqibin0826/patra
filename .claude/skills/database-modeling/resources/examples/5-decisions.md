# 阶段 5：设计决策记录 - Patra 出版物管理示例

> **ADR（Architecture Decision Record）**：记录数据库设计过程中的关键决策、背景、方案对比和最终选择

---

## 📋 决策记录索引

1. [ADR-001：使用中间表实现多对多关系](#adr-001使用中间表实现多对多关系)
2. [ADR-002：Author 表采用简化审计字段](#adr-002author-表采用简化审计字段)
3. [ADR-003：PMID 和 DOI 的唯一性约束策略](#adr-003pmid-和-doi-的唯一性约束策略)
4. [ADR-004：使用 VARBINARY 存储 IP 地址](#adr-004使用-varbinary-存储-ip-地址)
5. [ADR-005：使用 JSON 存储变更日志](#adr-005使用-json-存储变更日志)
6. [ADR-006：不使用物理外键约束](#adr-006不使用物理外键约束)
7. [ADR-007：全文索引策略](#adr-007全文索引策略)

---

## ADR-001：使用中间表实现多对多关系

### 状态
✅ **已采纳**（2025-01-17）

### 背景
出版物（Publication）和作者（Author）之间存在多对多关系：
- 一篇出版物可以有多个作者
- 一个作者可以发表多篇出版物
- 需要记录作者排序、第一作者、通讯作者等额外信息

### 方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **方案 A：JSON 数组存储作者 ID** | • 单表查询简单<br>• 不需要额外表 | • 无法高效查询"某作者的出版物"<br>• 无法建立外键约束<br>• 数据冗余（作者信息重复） | ❌ 2/10 |
| **方案 B：中间表** | • 支持双向查询<br>• 可存储额外属性<br>• 符合数据库范式 | • 增加一张表<br>• JOIN 查询稍复杂 | ✅ 9/10 |
| **方案 C：在 Publication 表冗余作者信息** | • 查询最简单 | • 严重违反范式<br>• 数据一致性难保证<br>• 存储浪费 | ❌ 1/10 |

### 决策
**采用方案 B：创建 `publication_author` 中间表**

### 理由
1. **业务需求**：需要记录作者排序、是否第一作者、是否通讯作者等信息
2. **查询需求**：高频查询"某作者的所有出版物"（需要反向查询）
3. **数据一致性**：作者信息统一管理，避免冗余和不一致
4. **扩展性**：未来可能增加作者贡献度、利益冲突声明等字段

### 影响
- ✅ **正面**：数据结构清晰，查询灵活，易于维护
- ⚠️ **负面**：部分查询需要 JOIN，略微增加复杂度（但 MySQL 优化器会处理）

### 实施细节
```sql
CREATE TABLE publication_author (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    publication_id    BIGINT UNSIGNED NOT NULL,
    author_id         BIGINT UNSIGNED NOT NULL,
    author_order      INT UNSIGNED NOT NULL DEFAULT 1,
    is_corresponding  TINYINT(1) NOT NULL DEFAULT 0,
    is_first_author   TINYINT(1) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_pub_author (publication_id, author_id),
    KEY idx_author_id (author_id),
    KEY idx_author_order (publication_id, author_order)
);
```

---

## ADR-002：Author 表采用简化审计字段

### 状态
✅ **已采纳**（2025-01-17）

### 背景
标准审计字段包括：
- `version`（乐观锁）
- `created_at`, `created_by`, `created_by_name`
- `updated_at`, `updated_by`, `updated_by_name`
- `deleted`（软删除）

问题：Author 表是否需要完整的审计字段？

### 方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **方案 A：完整审计字段** | • 符合规范<br>• 可追踪所有变更 | • 字段冗余<br>• 作者信息很少更新 | ⚠️ 5/10 |
| **方案 B：仅保留 created_at** | • 简化结构<br>• 节省存储空间<br>• 作者信息相对静态 | • 无法追踪更新历史<br>• 无乐观锁保护 | ✅ 8/10 |

### 决策
**采用方案 B：Author 表仅保留 `created_at` 字段**

### 理由
1. **业务特性**：作者信息（姓名、ORCID、邮箱）创建后很少修改
2. **并发场景少**：不存在多个用户同时修改同一作者信息的场景
3. **存储优化**：预估 55万作者记录，简化字段可节省约 50MB 存储
4. **可扩展性**：如未来需要，可通过迁移脚本添加完整审计字段

### 影响
- ✅ **正面**：表结构简洁，存储开销小
- ⚠️ **负面**：无法追踪作者信息修改历史（可通过应用层日志补充）

### 实施细节
```sql
CREATE TABLE author (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    orcid        VARCHAR(20) UNIQUE,
    full_name    VARCHAR(200) NOT NULL,
    -- ... 其他业务字段
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

### 后续考虑
如果未来出现以下情况，需要重新评估：
- 作者信息频繁更新（如机构变更、邮箱更新）
- 需要审计作者信息修改历史
- 出现并发修改冲突

---

## ADR-003：PMID 和 DOI 的唯一性约束策略

### 状态
✅ **已采纳**（2025-01-17）

### 背景
- **PMID**：PubMed 唯一标识符，理论上绝对唯一
- **DOI**：数字对象标识符，理论上唯一，但实际可能有脏数据或缺失

问题：如何设计唯一性约束？

### 方案对比

| 字段 | 方案 | 约束类型 | NULL 允许 | 理由 |
|------|------|---------|-----------|------|
| **PMID** | UNIQUE INDEX | 唯一索引 | ✅ 允许 | PubMed 来源必有 PMID，其他来源可能无 PMID |
| **DOI** | INDEX | 普通索引 | ✅ 允许 | DOI 可能缺失或脏数据，不强制唯一 |

### 决策
- **PMID**：UNIQUE INDEX + NULL
- **DOI**：普通 INDEX + NULL

### 理由

#### PMID 为什么用 UNIQUE INDEX？
1. **数据源保证**：PubMed 系统保证 PMID 绝对唯一
2. **防止重复采集**：避免同一文献重复入库
3. **高频查询**：按 PMID 查询是最常见的操作

#### PMID 为什么允许 NULL？
- 非 PubMed 来源的文献（如 EPMC、arXiv）没有 PMID

#### DOI 为什么不用 UNIQUE INDEX？
1. **数据质量问题**：部分数据源的 DOI 可能有错误或重复
2. **历史数据**：旧文献可能没有 DOI
3. **灵活性**：允许先入库，后续清洗去重

### 影响
- ✅ **正面**：防止 PMID 重复，同时保留数据灵活性
- ⚠️ **负面**：DOI 可能有极少量重复，需要应用层校验

### 实施细节
```sql
CREATE TABLE publication (
    -- ...
    pmid  VARCHAR(20) NULL,
    doi   VARCHAR(200) NULL,
    -- ...
    UNIQUE KEY uk_pmid (pmid),
    KEY idx_doi (doi)
);
```

### 后续处理
- **数据清洗任务**：定期检查 DOI 重复情况
  ```sql
  SELECT doi, COUNT(*) FROM publication
  WHERE doi IS NOT NULL
  GROUP BY doi HAVING COUNT(*) > 1;
  ```

---

## ADR-004：使用 VARBINARY 存储 IP 地址

### 状态
✅ **已采纳**（2025-01-17）

### 背景
需要记录数据操作的来源 IP 地址，支持 IPv4 和 IPv6。

### 方案对比

| 方案 | 类型 | 存储大小 | 优点 | 缺点 | 评分 |
|------|------|---------|------|------|------|
| **A：VARCHAR** | VARCHAR(45) | 45 字节 | 人类可读 | 浪费空间 | ❌ 4/10 |
| **B：VARBINARY** | VARBINARY(16) | 4-16 字节 | 节省 60%+ 空间 | 需要转换函数 | ✅ 9/10 |
| **C：两个字段** | VARCHAR(15) + VARCHAR(45) | 60 字节 | 分离 IPv4/v6 | 更复杂 | ❌ 3/10 |

### 决策
**采用方案 B：VARBINARY(16)**

### 理由
1. **存储效率**：
   - IPv4：4 字节（vs VARCHAR 15 字节）
   - IPv6：16 字节（vs VARCHAR 45 字节）
   - 节省约 60% 存储空间
2. **性能**：二进制比较比字符串比较更快
3. **MySQL 原生支持**：`INET6_ATON()` 和 `INET6_NTOA()` 函数

### 影响
- ✅ **正面**：存储空间节省显著（260万条记录约节省 200MB）
- ⚠️ **负面**：查询时需要转换函数（但 MyBatis-Plus 可自动处理）

### 实施细节

**DDL 定义**：
```sql
ip_address VARBINARY(16) NULL COMMENT '请求者 IP（二进制，支持 IPv4/IPv6）'
```

**存储时转换**：
```sql
INSERT INTO publication (ip_address)
VALUES (INET6_ATON('192.168.1.1'));
```

**查询时转换**：
```sql
SELECT INET6_NTOA(ip_address) AS ip FROM publication;
```

**MyBatis-Plus 自动转换**：
```java
@TableField(typeHandler = IpAddressTypeHandler.class)
private String ipAddress;

// 自定义 TypeHandler
public class IpAddressTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 调用 INET6_ATON()
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 调用 INET6_NTOA()
    }
}
```

---

## ADR-005：使用 JSON 存储变更日志

### 状态
✅ **已采纳**（2025-01-17）

### 背景
需要记录数据的备注、变更历史、数据来源等信息。

### 方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **A：单独的变更日志表** | • 符合范式<br>• 查询灵活 | • 增加表和 JOIN<br>• 存储开销大 | ⚠️ 6/10 |
| **B：JSON 字段** | • 灵活存储任意结构<br>• 单表查询<br>• MySQL 5.7+ 支持 JSON 函数 | • 查询稍复杂<br>• 不适合大量查询 | ✅ 8/10 |
| **C：TEXT 纯文本** | • 简单 | • 无结构<br>• 无法查询 | ❌ 2/10 |

### 决策
**采用方案 B：JSON 字段 `record_remarks`**

### 理由
1. **灵活性**：可存储任意结构的备注、变更日志、数据来源
2. **单表查询**：无需 JOIN，性能更好
3. **MySQL 支持**：MySQL 5.7+ 提供丰富的 JSON 函数
4. **存储效率**：JSON 压缩存储，比单独表节省空间

### 影响
- ✅ **正面**：结构灵活，易于扩展
- ⚠️ **负面**：不适合频繁基于 JSON 内容查询（但本场景查询频率低）

### 实施细节

**DDL 定义**：
```sql
record_remarks JSON NULL COMMENT 'JSON 数组，备注/变更日志'
```

**数据示例**：
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

**查询示例**：
```sql
-- 查询包含特定操作的记录
SELECT * FROM publication
WHERE JSON_CONTAINS(
  record_remarks,
  '{"action": "manual_correction"}',
  '$'
);

-- 提取最后一次变更时间
SELECT
  id,
  JSON_EXTRACT(record_remarks, '$[0].timestamp') AS last_change
FROM publication;
```

### 后续考虑
如果变更日志查询需求增加，考虑：
- 使用虚拟列（Generated Column）提取常用字段
- 或迁移到单独的变更日志表

---

## ADR-006：不使用物理外键约束

### 状态
✅ **已采纳**（2025-01-17）

### 背景
`publication_author` 表引用 `publication.id` 和 `author.id`，是否使用外键约束？

### 方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **A：物理外键** | • 数据库层面保证一致性 | • 锁竞争<br>• 降低并发性能<br>• 影响批量操作 | ❌ 3/10 |
| **B：应用层校验** | • 高并发性能<br>• 灵活性高<br>• 易于分库分表 | • 需要应用层保证 | ✅ 9/10 |

### 决策
**采用方案 B：不使用物理外键，由应用层保证参照完整性**

### 理由
1. **性能考虑**：
   - 避免外键锁竞争
   - 提高并发写入性能
   - 批量操作更快
2. **扩展性**：
   - 便于未来分库分表
   - 跨库关联更灵活
3. **行业实践**：大多数互联网公司不使用物理外键
4. **MyBatis-Plus 支持**：Repository 层可自动校验关联

### 影响
- ✅ **正面**：高并发性能，易于扩展
- ⚠️ **负面**：需要应用层严格控制数据一致性

### 实施细节

**应用层校验示例**：
```java
@Transactional
public void createPublicationWithAuthors(Publication publication, List<Long> authorIds) {
    // 1. 校验作者是否存在
    List<Author> authors = authorRepository.findByIds(authorIds);
    if (authors.size() != authorIds.size()) {
        throw new BusinessException("部分作者不存在");
    }

    // 2. 保存出版物
    publicationRepository.save(publication);

    // 3. 创建关联关系
    List<PublicationAuthor> relations = authorIds.stream()
        .map(authorId -> PublicationAuthor.create(publication.getId(), authorId))
        .collect(Collectors.toList());
    publicationAuthorRepository.saveBatch(relations);
}
```

**数据一致性保证**：
- 使用 `@Transactional` 保证原子性
- 定期运行数据一致性检查任务
  ```sql
  -- 检查孤立的关联记录
  SELECT * FROM publication_author pa
  WHERE NOT EXISTS (SELECT 1 FROM publication p WHERE p.id = pa.publication_id)
     OR NOT EXISTS (SELECT 1 FROM author a WHERE a.id = pa.author_id);
  ```

---

## ADR-007：全文索引策略

### 状态
✅ **已采纳**（2025-01-17）

### 背景
需要支持标题和摘要的全文检索，预计查询频率 >1000次/天。

### 方案对比

| 方案 | 优点 | 缺点 | 评分 |
|------|------|------|------|
| **A：MySQL FULLTEXT** | • 内置支持<br>• 配置简单<br>• 适合中等规模 | • 功能有限<br>• 中文分词支持弱 | ✅ 7/10 |
| **B：Elasticsearch** | • 功能强大<br>• 中文分词好<br>• 高级搜索 | • 额外组件<br>• 运维成本高<br>• 数据同步复杂 | ⚠️ 8/10 |
| **C：LIKE 模糊查询** | • 无需配置 | • 性能极差<br>• 无法支持高频查询 | ❌ 1/10 |

### 决策
**阶段 1：采用方案 A（MySQL FULLTEXT）**
**阶段 2（数据量 >500万）：迁移到 Elasticsearch**

### 理由
1. **初期需求**：
   - 数据量 260万（5年），FULLTEXT 可满足
   - 简化技术栈，减少运维复杂度
2. **性能可接受**：
   - FULLTEXT 索引支持自然语言搜索
   - 响应时间 <200ms（95th percentile）
3. **渐进式演进**：
   - 先用 FULLTEXT 验证需求
   - 数据量大时再迁移到 ES

### 影响
- ✅ **正面**：实现简单，无额外组件
- ⚠️ **负面**：中文分词效果一般（后续可优化）

### 实施细节

**DDL 定义**：
```sql
FULLTEXT INDEX ft_title_abstract (title, abstract) COMMENT '标题和摘要全文索引'
```

**查询示例**：
```sql
-- 自然语言搜索
SELECT * FROM publication
WHERE MATCH(title, abstract) AGAINST ('machine learning' IN NATURAL LANGUAGE MODE)
  AND deleted = 0;

-- 布尔模式（支持 +、-、*）
SELECT * FROM publication
WHERE MATCH(title, abstract) AGAINST ('+cancer -treatment' IN BOOLEAN MODE)
  AND deleted = 0;
```

**性能优化**：
```sql
-- 调整最小词长度（默认 4，调整为 2）
SET GLOBAL innodb_ft_min_token_size = 2;

-- 重建索引
ALTER TABLE publication DROP INDEX ft_title_abstract;
ALTER TABLE publication ADD FULLTEXT INDEX ft_title_abstract (title, abstract);
```

### 迁移计划（阶段 2）
当数据量 >500万 或 查询需求变复杂时：
1. 部署 Elasticsearch 集群
2. 使用 Logstash/Canal 同步数据
3. 应用层查询切换到 ES
4. 保留 MySQL FULLTEXT 作为备用

---

## 📊 决策总结

| 决策编号 | 决策内容 | 状态 | 影响范围 |
|---------|---------|------|---------|
| ADR-001 | 使用中间表实现多对多关系 | ✅ 已采纳 | publication_author 表 |
| ADR-002 | Author 表采用简化审计字段 | ✅ 已采纳 | author 表 |
| ADR-003 | PMID 唯一索引，DOI 普通索引 | ✅ 已采纳 | publication 表 |
| ADR-004 | 使用 VARBINARY 存储 IP | ✅ 已采纳 | publication 表 |
| ADR-005 | 使用 JSON 存储变更日志 | ✅ 已采纳 | publication 表 |
| ADR-006 | 不使用物理外键约束 | ✅ 已采纳 | 所有表 |
| ADR-007 | MySQL FULLTEXT → ES 渐进式演进 | ✅ 已采纳 | publication 表 |

---

## ✅ 设计验证

### 性能验证
- [x] 索引选择性 >0.8（关键索引）
- [x] 全文索引响应时间 <200ms（预估）
- [x] JOIN 查询响应时间 <50ms（预估）

### 扩展性验证
- [x] 支持 1000 QPS 并发查询
- [x] 支持未来分库分表（无物理外键）
- [x] 支持历史数据归档（按 publication_date 分区）

### 规范性验证
- [x] 符合 Patra 项目规范
- [x] 符合六边形架构 + DDD
- [x] 符合 MyBatis-Plus 最佳实践

---

## 📝 遗留问题

### 待观察
1. **DOI 重复问题**：运行 3 个月后评估重复率，决定是否升级为唯一索引
2. **全文索引性能**：数据量达到 200万 时评估响应时间，决定是否迁移到 ES
3. **IP 地址使用率**：评估 ip_address 字段的实际使用情况，决定是否保留

### 待优化
1. **中文分词**：如需支持中文全文搜索，考虑集成 jieba 或迁移到 ES
2. **数据归档**：数据量达到 500万 时，考虑按年份分区或归档
3. **查询缓存**：高频查询（按 PMID）考虑引入 Redis 缓存

---

## 📌 回顾机制

**定期回顾**：每季度回顾一次 ADR，评估决策是否需要调整

**触发条件**：
- 数据量增长 >50%
- 查询性能下降 >20%
- 新业务需求出现

**回顾内容**：
- 决策是否仍然有效？
- 是否出现预期外的问题？
- 是否需要调整或撤销决策？

---

**示例完成！** 🎉

完整的 Patra 出版物管理数据库设计示例（5 个阶段）已全部完成。
