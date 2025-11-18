# 阶段 2：详细表设计 - 关联信息模块(7张表)

> **设计目标**: 详细定义 patra_catalog 数据库关联信息模块每个表的字段、类型、约束、索引
>
> **创建日期**: 2025-01-18
> **设计范围**: patra_catalog 关联信息表(cat_funding, cat_publication_funding, cat_reference, cat_external_reference, cat_related_item, cat_supplemental_object, cat_publication_history)
> **作者**: Patra Lin

---

## 📑 模块概览

本文档详细设计 patra_catalog 数据库关联信息模块的 7 张表。这些表管理文献的资助来源、引用关系、相关项目、补充材料和发布历史。

### 表清单

| 表名 | 中文名 | 核心功能 | 预估规模 |
|------|--------|---------|---------|
| `cat_funding` | 资助信息表 | 研究资金来源、项目管理 | 700万+ |
| `cat_publication_funding` | 文献-资助关联表 | 文献与资助的多对多关系 | 1750万+ |
| `cat_reference` | 参考文献表 | 文献引用关系管理 | 5.25亿+ |
| `cat_external_reference` | 外部引用表 | 外部数据库引用(基因库、临床试验等) | 500万+ |
| `cat_related_item` | 相关项目表 | 撤稿、勘误、评论等关联文献 | 100万+ |
| `cat_supplemental_object` | 补充对象表 | 图表、数据集、代码等附加资源 | 1000万+ |
| `cat_publication_history` | 发布历史表 | 文献生命周期事件追踪 | 3000万+ |

### 模块设计亮点

- ✅ **资助去重策略** - dedup_key (agency_name + grant_id) 减少 60%+ 冗余
- ✅ **引用双重关联** - cited_publication_id + cited_pmid/doi 支持库内外引用
- ✅ **外部引用分离** - 清晰区分文献引用和数据引用
- ✅ **相关项类型枚举** - 12 种关联类型覆盖撤稿/勘误/评论等场景
- ✅ **补充材料访问控制** - is_public + license 管理权限
- ✅ **历史事件时序性** - order_num + event_date 双重保障

---

## 📊 表 1: cat_funding (资助信息表)

**表说明:** 管理研究资金来源和项目信息,通过去重策略避免重复存储
**记录数预估:** 初始 300万 / 年增长 80万 / 5年规模 700万
**主要查询场景:**
1. 按 dedup_key 去重查询(>1000次/天,高频)
2. 按 funder_id 查询(500-1000次/天,中频)
3. 按 agency_name 查询(100-500次/天,中频)
4. 按 grant_id 查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| agency_name | VARCHAR | 200 | NOT NULL | - | 资助机构名称 | 普通索引 |
| agency_abbreviation | VARCHAR | 100 | NULL | NULL | 机构缩写(如"NIH","NSF") | 否 |
| country | VARCHAR | 100 | NULL | NULL | 资助国家(ISO 3166-1 alpha-3) | 否 |
| grant_id | VARCHAR | 100 | NOT NULL | - | 资助编号/项目编号 | 普通索引 |
| grant_acronym | VARCHAR | 100 | NULL | NULL | 项目缩写 | 否 |
| grant_name | VARCHAR | 500 | NULL | NULL | 项目名称 | 否 |
| funding_type | VARCHAR | 50 | NULL | NULL | 资助类型(CHECK 约束) | 普通索引 |
| amount | DECIMAL | 20,2 | NULL | NULL | 资助金额 decimal(20,2) | 否 |
| currency | VARCHAR | 10 | NULL | NULL | 货币类型(如"USD","EUR","CNY") | 否 |
| start_date | DATE | - | NULL | NULL | 开始日期 | 否 |
| end_date | DATE | - | NULL | NULL | 结束日期 | 否 |
| funder_id | VARCHAR | 100 | NULL | NULL | Crossref Funder Registry ID | 普通索引 |
| ror_id | VARCHAR | 100 | NULL | NULL | ROR(Research Organization Registry)标识符 | 普通索引 |
| dedup_key | VARCHAR | 255 | NOT NULL | - | 去重键(MD5哈希) | 唯一索引 |
| metadata | JSON | - | NULL | NULL | 资助元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **agency_name**: VARCHAR(200) NOT NULL,资助机构名称,去重策略的核心字段之一
2. **agency_abbreviation**: VARCHAR(100),机构缩写,如 "NIH"(National Institutes of Health)
3. **country**: VARCHAR(100),ISO 3166-1 alpha-3 国家代码(如 USA/CHN/GBR)
4. **grant_id**: VARCHAR(100) NOT NULL,资助编号/项目编号,去重策略的核心字段之一
5. **grant_acronym**: VARCHAR(100),项目缩写,如 "R01CA123456"
6. **grant_name**: VARCHAR(500),完整项目名称,可能较长
7. **funding_type**: VARCHAR(50),资助类型,枚举值 CHECK 约束:
   - `Government`: 政府资助
   - `Foundation`: 基金会资助
   - `Corporate`: 企业资助
   - `University`: 大学资助
   - `Non-profit`: 非营利组织资助
   - `Other`: 其他类型
8. **amount**: DECIMAL(20,2),资助金额,支持最大 999,999,999,999,999,999.99
9. **currency**: VARCHAR(10),ISO 4217 货币代码(如 USD/EUR/CNY/GBP/JPY)
10. **start_date / end_date**: DATE 类型,资助周期起止日期
11. **funder_id**: VARCHAR(100),Crossref Funder Registry ID,标准化资助者标识符
    - 覆盖率约 40%
    - 示例: `http://dx.doi.org/10.13039/100000001` (NSF)
12. **ror_id**: VARCHAR(100),ROR(Research Organization Registry)标识符
    - 覆盖率约 30%
    - 示例: `https://ror.org/01cwqze88` (NIH)
13. **dedup_key**: VARCHAR(255) NOT NULL,去重键(详见设计决策 1):
    ```java
    // 生成去重键
    String dedupKey = MD5(normalize(agencyName) + "|" + normalize(grantId));

    // 规范化逻辑
    private String normalize(String text) {
        return text.toLowerCase()
                   .replaceAll("\\s+", " ")         // 多空格转单空格
                   .replaceAll("[^a-z0-9 ]", "")    // 移除特殊字符
                   .trim();
    }
    ```
14. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "program_name": "Cancer Research Program",
      "project_officer": "Dr. John Smith",
      "funding_mechanism": "Research Grant",
      "is_multi_year": true,
      "total_budget": 5000000.00
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_funding 表不包含完整审计字段?**
- 资助信息相对静态,创建后很少修改
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量大(700万行),精简字段节省存储
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_dedup_key` (`dedup_key`) COMMENT '去重键唯一索引,防止重复资助记录,支持插入前去重查询'

-- 普通索引
INDEX `idx_agency` (`agency_name`) COMMENT '机构名称索引,支持按机构查询资助项目'
INDEX `idx_grant_id` (`grant_id`) COMMENT '项目编号索引,支持按项目编号查询'
INDEX `idx_funder_id` (`funder_id`) COMMENT 'Crossref Funder ID 索引,支持标准化查询'
INDEX `idx_ror` (`ror_id`) COMMENT 'ROR 标识符索引,支持机构标识符查询'
INDEX `idx_funding_type` (`funding_type`) COMMENT '资助类型索引,支持按类型筛选'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_dedup_key | 0.95 | 极高 ✅ | 去重键高度唯一(95%+),核心去重字段 |
| idx_agency | 0.75 | 高 ✅ | 机构名称重复度中等,约 2000+ 机构 |
| idx_grant_id | 0.80 | 高 ✅ | 项目编号重复度低,但不同机构可能重复 |
| idx_funder_id | 0.85 | 高 ✅ | Funder ID 高度唯一,覆盖率约 40% |
| idx_ror | 0.90 | 极高 ✅ | ROR ID 几乎唯一,覆盖率约 30% |
| idx_funding_type | 0.30 | 低 ⚠️ | 仅 6 个枚举值,但业务筛选需求存在 |

**索引设计权衡说明:**
- `uk_dedup_key` 唯一索引是去重系统的核心,必须保留
- `idx_agency` 和 `idx_grant_id` 支持单独查询,虽然有复合去重键但仍需单列索引
- `idx_funding_type` 选择性低但保留:按资助类型统计是核心业务需求
- 未创建 `grant_name` 索引:名称查询通过应用层 Elasticsearch 实现

---

## 📊 表 2: cat_publication_funding (文献-资助关联表)

**表说明:** 管理文献与资助的多对多关系,支持主要资助标记和顺序
**记录数预估:** 初始 750万 / 年增长 200万 / 5年规模 1750万
**主要查询场景:**
1. 查询某文献的所有资助来源(>1000次/天,高频)
2. 查询某资助项目的所有文献产出(100-500次/天,中频)
3. 按主要资助筛选(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合唯一索引 |
| funding_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 资助ID(外键:cat_funding.id) | 复合唯一索引 |
| acknowledgment_text | VARCHAR | 500 | NULL | NULL | 致谢文本(原始致谢内容) | 否 |
| is_primary | BOOLEAN | - | NOT NULL | 0 | 是否主要资助(0=否,1=是) | 普通索引 |
| order_num | INTEGER | - | NOT NULL | 1 | 顺序号(用于排序显示) | 否 |
| recipient_name | VARCHAR | 200 | NULL | NULL | 接收人/主要研究者(PI)姓名 | 否 |
| recipient_orcid | VARCHAR | 100 | NULL | NULL | 接收人 ORCID 标识符 | 否 |
| metadata | JSON | - | NULL | NULL | 关联元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **funding_id**: 外键关联到 cat_funding.id,NOT NULL
3. **acknowledgment_text**: VARCHAR(500),原始致谢文本,如:
   - "This work was supported by NIH Grant R01CA123456."
   - "Funded by the National Natural Science Foundation of China (No. 82173456)."
4. **is_primary**: BOOLEAN,标识是否为主要资助来源
   - 一篇文献可能有多个资助,但通常只有一个主要资助
   - 用于统计和展示优先级
5. **order_num**: INTEGER NOT NULL,顺序号
   - 用于控制资助列表的显示顺序
   - 默认值 1,插入时自增
6. **recipient_name**: VARCHAR(200),接收人/主要研究者姓名
   - PI(Principal Investigator)信息
   - 用于识别资助的具体接收者
7. **recipient_orcid**: VARCHAR(100),接收人 ORCID 标识符
   - 标准格式: `0000-0001-2345-6789`
   - 用于关联作者信息
8. **metadata**: JSON 字段,存储扩展信息,如:
   ```json
   {
     "role": "Principal Investigator",
     "contribution_percentage": 60,
     "sub_project": "Phase II Clinical Trial"
   }
   ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_funding 表不包含完整审计字段?**
- 关联关系创建后通常不再修改
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量大(1750万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_pub_funding` (`publication_id`, `funding_id`) COMMENT '文献+资助组合唯一索引,防止重复关联'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有资助来源(高频)'
INDEX `idx_funding` (`funding_id`) COMMENT '资助ID索引,支持查询某资助项目的所有文献产出(中频)'
INDEX `idx_primary` (`is_primary`) COMMENT '主要资助索引,支持筛选主要资助来源'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_pub_funding | 1.00 | 极高 ✅ | 组合绝对唯一,防止重复关联 |
| idx_publication | 0.90 | 极高 ✅ | 每篇文献平均 1-3 个资助,选择性高 |
| idx_funding | 0.70 | 高 ✅ | 每个资助项目平均支持 2-5 篇文献 |
| idx_primary | 0.20 | 低 ⚠️ | 仅两值(0/1),但业务筛选需求存在 |

**索引设计权衡说明:**
- `uk_pub_funding` 复合唯一索引同时满足业务约束和查询性能
- `idx_publication` 和 `idx_funding` 分别支持双向查询
- `idx_primary` 选择性低但保留:筛选主要资助是基本需求
- 未创建 `order_num` 索引:通常与 publication_id 组合查询,无需单独索引

---

## 📊 表 3: cat_reference (参考文献表)

**表说明:** 管理文献引用关系,支持库内外引用双重关联
**记录数预估:** 初始 2亿 / 年增长 6500万 / 5年规模 5.25亿
**主要查询场景:**
1. 查询某文献的所有参考文献(>2000次/天,高频)
2. 按 cited_pmid 查询库外引用(>1000次/天,高频)
3. 按 cited_publication_id 查询被引关系(500-1000次/天,中频)
4. 按 cited_doi 查询引用(<500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 引用文献ID(本文)(外键:cat_publication.id) | 复合唯一索引 |
| cited_publication_id | BIGINT UNSIGNED | - | NULL, FK | NULL | 被引文献ID(如果在库中)(外键:cat_publication.id) | 普通索引 |
| cited_pmid | VARCHAR | 20 | NULL | NULL | 被引文献PMID(库外引用) | 普通索引 |
| cited_doi | VARCHAR | 200 | NULL | NULL | 被引文献DOI(库外引用) | 普通索引 |
| citation_text | VARCHAR | 2000 | NULL | NULL | 引用文本(原始引用格式) | 否 |
| article_title | VARCHAR | 500 | NULL | NULL | 文章标题 | 否 |
| source | VARCHAR | 500 | NULL | NULL | 来源期刊/书籍名称 | 否 |
| volume | VARCHAR | 100 | NULL | NULL | 卷号 | 否 |
| issue | VARCHAR | 100 | NULL | NULL | 期号 | 否 |
| pages | VARCHAR | 50 | NULL | NULL | 页码(如"123-145") | 否 |
| year | SMALLINT | - | NULL | NULL | 出版年份 | 普通索引 |
| authors | VARCHAR | 500 | NULL | NULL | 作者列表(简化格式) | 否 |
| reference_type | VARCHAR | 50 | NULL | NULL | 引用类型(CHECK 约束) | 否 |
| reference_number | INTEGER | - | NOT NULL | - | 引用编号(本文中的序号) | 复合唯一索引 |
| is_retracted | BOOLEAN | - | NOT NULL | 0 | 是否已撤稿(0=否,1=是) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 引用元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL,表示引用文献(本文)
2. **cited_publication_id**: 外键关联到 cat_publication.id,NULL,表示被引文献(库内)
3. **cited_pmid / cited_doi**: VARCHAR,库外引用的标识符(详见设计决策 2)
   - **优先级规则**:
     ```java
     // 1. 优先通过 PMID 匹配库内文献
     if (citedPmid != null) {
         Publication citedPub = publicationRepo.findByPmid(citedPmid);
         if (citedPub != null) {
             reference.setCitedPublicationId(citedPub.getId());
             return; // 匹配成功,使用外键关联
         }
     }

     // 2. 其次通过 DOI 匹配
     if (citedDoi != null) {
         Publication citedPub = publicationRepo.findByDoi(citedDoi);
         if (citedPub != null) {
             reference.setCitedPublicationId(citedPub.getId());
             return;
         }
     }

     // 3. 无法匹配库内文献,保留 PMID/DOI
     ```
4. **citation_text**: VARCHAR(2000),原始引用文本,如:
   - "Smith J, et al. Cancer treatment outcomes. Nature. 2023;615(7950):123-145."
5. **article_title**: VARCHAR(500),被引文献标题
6. **source**: VARCHAR(500),来源期刊/书籍名称
7. **volume / issue / pages**: VARCHAR,卷期页码信息
   - VARCHAR 而非 INT:支持非数字格式(如 "Suppl 1","3-4 合刊")
8. **year**: SMALLINT,出版年份,用于引用统计和时间线分析
9. **authors**: VARCHAR(500),作者列表简化格式,如:
   - "Smith J, Johnson M, Williams R, et al."
10. **reference_type**: VARCHAR(50),引用类型,枚举值 CHECK 约束:
    - `Journal Article`: 期刊文章
    - `Book`: 书籍
    - `Book Chapter`: 书籍章节
    - `Conference Paper`: 会议论文
    - `Thesis`: 学位论文
    - `Report`: 技术报告
    - `Preprint`: 预印本
    - `Web Page`: 网页
    - `Other`: 其他类型
11. **reference_number**: INTEGER NOT NULL,引用编号(本文中的序号)
    - 用于保持引用列表顺序
    - 与 publication_id 组合形成唯一约束
12. **is_retracted**: BOOLEAN,标识被引文献是否已撤稿
    - 定期从撤稿监测系统同步
    - 用于警告和质量控制
13. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "citation_context": "supporting evidence",
      "citation_intent": "background",
      "is_self_citation": false
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_reference 表不包含完整审计字段?**
- 引用关系创建后通常不再修改(静态数据)
- 数据量极大(5.25亿行),精简字段节省存储(每行节省 80 字节 = 42GB)
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_reference_num` (`publication_id`, `reference_number`) COMMENT '文献+引用编号唯一索引,保证引用编号在同一文献内唯一'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有参考文献(高频)'
INDEX `idx_cited_pub` (`cited_publication_id`) COMMENT '被引文献ID索引,支持查询被引关系(中频)'
INDEX `idx_cited_pmid` (`cited_pmid`) COMMENT '被引PMID索引,支持按PMID查询引用(高频)'
INDEX `idx_cited_doi` (`cited_doi`) COMMENT '被引DOI索引,支持按DOI查询引用(中频)'
INDEX `idx_year` (`year`) COMMENT '年份索引,支持按年份统计引用趋势'
INDEX `idx_retracted` (`is_retracted`) COMMENT '撤稿索引,支持筛选撤稿文献引用'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_reference_num | 1.00 | 极高 ✅ | 组合绝对唯一,保证引用编号唯一性 |
| idx_publication | 0.85 | 高 ✅ | 每篇文献平均 20-50 篇引用,选择性高 |
| idx_cited_pub | 0.80 | 高 ✅ | 库内被引文献查询,选择性高 |
| idx_cited_pmid | 0.90 | 极高 ✅ | PMID 几乎唯一,覆盖率 ~70% |
| idx_cited_doi | 0.90 | 极高 ✅ | DOI 几乎唯一,覆盖率 ~60% |
| idx_year | 0.50 | 中 ✅ | 跨度 100 年,支持引用趋势分析 |
| idx_retracted | 0.10 | 低 ⚠️ | 仅两值(0/1),但撤稿监测是核心需求 |

**索引设计权衡说明:**
- `idx_publication` 支持查询某文献的所有参考文献(最高频操作)
- `idx_cited_pmid` 和 `idx_cited_doi` 支持库外引用匹配
- `idx_retracted` 选择性极低(<1% 文献被撤稿),但撤稿监测是法规要求
- 未创建 `article_title` 或 `authors` 索引:通过应用层全文检索实现
- 数据量极大(5.25亿行),索引总大小预计 150GB+,需谨慎设计

---

## 📊 表 4: cat_external_reference (外部引用表)

**表说明:** 管理外部数据库引用(基因库、临床试验、数据集等),与参考文献分离
**记录数预估:** 初始 200万 / 年增长 60万 / 5年规模 500万
**主要查询场景:**
1. 查询某文献的所有外部引用(>500次/天,中频)
2. 按 database_name 查询(100-500次/天,中频)
3. 按 accession_number 查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合唯一索引 |
| database_name | VARCHAR | 50 | NOT NULL | - | 数据库名称(如"GenBank","ClinicalTrials.gov") | 复合唯一索引 |
| database_category | VARCHAR | 100 | NULL | NULL | 数据库类别(如"Genomic","Clinical Trial") | 普通索引 |
| accession_number | VARCHAR | 200 | NOT NULL | - | 登录号/访问号 | 复合唯一索引 |
| url | VARCHAR | 500 | NULL | NULL | 链接地址(完整URL) | 否 |
| reference_type | VARCHAR | 50 | NULL | NULL | 引用类型(描述性) | 否 |
| description | VARCHAR | 500 | NULL | NULL | 描述信息 | 否 |
| access_date | DATE | - | NULL | NULL | 访问日期(最后验证日期) | 否 |
| version | VARCHAR | 50 | NULL | NULL | 数据库版本号 | 否 |
| order_num | INTEGER | - | NOT NULL | 1 | 顺序号(用于排序显示) | 否 |
| metadata | JSON | - | NULL | NULL | 外部引用元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **database_name**: VARCHAR(50) NOT NULL,数据库名称,常见值:
   - **基因组数据库**: GenBank, RefSeq, EMBL, DDBJ
   - **临床试验**: ClinicalTrials.gov, ChiCTR, ISRCTN
   - **蛋白质数据库**: UniProt, PDB
   - **化合物数据库**: PubChem, ChEMBL
   - **数据仓库**: Zenodo, Figshare, Dryad
3. **database_category**: VARCHAR(100),数据库类别,用于分类统计:
   - `Genomic`: 基因组学数据库
   - `Clinical Trial`: 临床试验数据库
   - `Protein`: 蛋白质数据库
   - `Chemical Compound`: 化合物数据库
   - `Research Data`: 研究数据仓库
   - `Other`: 其他类别
4. **accession_number**: VARCHAR(200) NOT NULL,登录号,如:
   - GenBank: `NM_000546.6`
   - ClinicalTrials.gov: `NCT04123456`
   - PDB: `7KAK`
   - Zenodo: `10.5281/zenodo.1234567`
5. **url**: VARCHAR(500),完整访问 URL,如:
   - `https://www.ncbi.nlm.nih.gov/nuccore/NM_000546.6`
   - `https://clinicaltrials.gov/ct2/show/NCT04123456`
6. **reference_type**: VARCHAR(50),引用类型描述,如:
   - `Gene Sequence`
   - `Clinical Trial Registration`
   - `Protein Structure`
   - `Supplementary Dataset`
7. **description**: VARCHAR(500),描述信息,如:
   - "TP53 gene sequence, transcript variant 1"
   - "Phase III randomized controlled trial of Drug X"
8. **access_date**: DATE,最后访问/验证日期
   - 用于链接有效性检查
   - 部分数据库要求引用时记录访问日期
9. **version**: VARCHAR(50),数据库版本号,如:
   - GenBank 版本: `Build 38.1`
   - UniProt release: `2023_05`
10. **order_num**: INTEGER NOT NULL,顺序号,默认值 1
11. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "organism": "Homo sapiens",
      "data_type": "nucleotide sequence",
      "file_format": "FASTA",
      "file_size": 123456
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_external_reference 表不包含完整审计字段?**
- 外部引用创建后通常不再修改
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量较大(500万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_external_ref` (`publication_id`, `database_name`, `accession_number`) COMMENT '文献+数据库+登录号唯一索引,防止重复引用'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有外部引用(中频)'
INDEX `idx_database` (`database_name`) COMMENT '数据库名称索引,支持按数据库查询(中频)'
INDEX `idx_accession` (`accession_number`) COMMENT '登录号索引,支持按登录号查询(低频)'
INDEX `idx_category` (`database_category`) COMMENT '数据库类别索引,支持按类别筛选(低频)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_external_ref | 1.00 | 极高 ✅ | 三字段组合绝对唯一,防止重复引用 |
| idx_publication | 0.90 | 极高 ✅ | 每篇文献平均 0-5 个外部引用,选择性高 |
| idx_database | 0.70 | 高 ✅ | 约 50+ 数据库,分布不均 |
| idx_accession | 0.85 | 高 ✅ | 登录号高度唯一,但跨数据库可能重复 |
| idx_category | 0.50 | 中 ✅ | 约 6 个类别,支持分类统计 |

**索引设计权衡说明:**
- `uk_external_ref` 复合唯一索引防止同一文献重复引用同一数据库条目
- `idx_database` 支持统计各数据库的引用频率
- `idx_category` 支持按类别筛选(如查询所有基因组引用)
- 未创建 `url` 或 `description` 索引:查询频率极低

---

## 📊 表 5: cat_related_item (相关项目表)

**表说明:** 管理文献的相关项(撤稿、勘误、评论等),支持 12 种关联类型
**记录数预估:** 初始 40万 / 年增长 12万 / 5年规模 100万
**主要查询场景:**
1. 查询某文献的所有相关项(>500次/天,中频)
2. 按 relationship_type 查询撤稿文献(100-500次/天,中频)
3. 按 related_pmid 查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 主文献ID(外键:cat_publication.id) | 普通索引 |
| related_publication_id | BIGINT UNSIGNED | - | NULL, FK | NULL | 相关文献ID(如果在库中)(外键:cat_publication.id) | 普通索引 |
| related_pmid | VARCHAR | 20 | NULL | NULL | 相关文献PMID(库外) | 否 |
| related_doi | VARCHAR | 200 | NULL | NULL | 相关文献DOI(库外) | 否 |
| relationship_type | VARCHAR | 50 | NOT NULL | - | 关系类型(CHECK 约束,12种枚举值) | 普通索引 |
| title | VARCHAR | 500 | NULL | NULL | 相关项标题 | 否 |
| description | VARCHAR | 500 | NULL | NULL | 关系描述 | 否 |
| relationship_date | DATE | - | NULL | NULL | 关系建立日期 | 普通索引 |
| initiated_by | VARCHAR | 100 | NULL | NULL | 发起方(CHECK 约束) | 否 |
| status | VARCHAR | 50 | NULL | NULL | 状态(CHECK 约束) | 普通索引 |
| order_num | INTEGER | - | NOT NULL | 1 | 顺序号(用于排序显示) | 否 |
| metadata | JSON | - | NULL | NULL | 关系元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL,表示主文献
2. **related_publication_id**: 外键关联到 cat_publication.id,NULL,表示相关文献(库内)
3. **related_pmid / related_doi**: VARCHAR,库外相关文献的标识符
   - 与 reference 表类似的双重关联设计
4. **relationship_type**: VARCHAR(50) NOT NULL,关系类型,枚举值 CHECK 约束(详见设计决策 4):
   ```sql
   CHECK (relationship_type IN (
       'Retraction',            -- 撤稿
       'Partial Retraction',    -- 部分撤稿
       'Expression of Concern', -- 关注声明
       'Withdrawn',             -- 撤回
       'Erratum',               -- 勘误
       'Correction',            -- 更正
       'Comment',               -- 评论
       'Response',              -- 回应
       'Update',                -- 更新
       'Republication',         -- 重新发表
       'Superseded',            -- 被取代
       'Duplicate'              -- 重复发表
   ))
   ```
5. **title**: VARCHAR(500),相关项标题,如:
   - "Retraction: Original article title"
   - "Erratum: Correction to Figure 3"
6. **description**: VARCHAR(500),关系描述,如:
   - "This article has been retracted due to data fabrication."
   - "Correction of author affiliation in the original article."
7. **relationship_date**: DATE,关系建立日期
   - 撤稿日期、勘误发布日期等
   - 用于时间线展示和统计
8. **initiated_by**: VARCHAR(100),发起方,枚举值 CHECK 约束:
   - `Author`: 作者发起
   - `Editor`: 编辑发起
   - `Publisher`: 出版商发起
   - `Institution`: 机构发起
   - `Third Party`: 第三方发起
9. **status**: VARCHAR(50),状态,枚举值 CHECK 约束:
   - `Active`: 有效
   - `Resolved`: 已解决
   - `Under Investigation`: 调查中
   - `Pending`: 待处理
10. **order_num**: INTEGER NOT NULL,顺序号,默认值 1
11. **metadata**: JSON 字段,存储类型特定数据,如:
    ```json
    {
      "reason": "Data fabrication",
      "investigation_agency": "University Research Integrity Office",
      "original_publication_date": "2022-05-15",
      "affected_sections": ["Results", "Discussion"]
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_related_item 表不包含完整审计字段?**
- 相关项创建后通常不再修改(历史记录性质)
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量中等(100万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '主文献ID索引,支持查询某文献的所有相关项(中频)'
INDEX `idx_related_pub` (`related_publication_id`) COMMENT '相关文献ID索引,支持反向查询相关文献(中频)'
INDEX `idx_relationship` (`relationship_type`) COMMENT '关系类型索引,支持按类型筛选(如查询所有撤稿文献)'
INDEX `idx_status` (`status`) COMMENT '状态索引,支持按状态筛选'
INDEX `idx_date` (`relationship_date`) COMMENT '日期索引,支持按时间排序和统计'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_publication | 0.90 | 极高 ✅ | 每篇文献平均 0-2 个相关项,选择性高 |
| idx_related_pub | 0.85 | 高 ✅ | 相关文献反向查询,选择性高 |
| idx_relationship | 0.40 | 中 ✅ | 12 种枚举值,分布不均(Retraction <1%) |
| idx_status | 0.30 | 低 ⚠️ | 4 种枚举值,但状态筛选是基本需求 |
| idx_date | 0.60 | 中 ✅ | 跨度 10+ 年,支持时间线分析 |

**索引设计权衡说明:**
- `idx_relationship` 是核心业务索引,支持撤稿监测和质量控制
- `idx_status` 选择性低但保留:筛选活跃/已解决的相关项
- 未创建 `initiated_by` 索引:查询频率极低
- 撤稿监测是法规要求,相关索引必须保留

---

## 📊 表 6: cat_supplemental_object (补充对象表)

**表说明:** 管理补充材料(图表、数据集、代码等),支持访问控制和许可证管理
**记录数预估:** 初始 400万 / 年增长 120万 / 5年规模 1000万
**主要查询场景:**
1. 查询某文献的所有补充材料(>1000次/天,高频)
2. 按 object_type 查询(100-500次/天,中频)
3. 按 is_public 筛选公开材料(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 普通索引 |
| object_type | VARCHAR | 50 | NOT NULL | - | 对象类型(CHECK 约束) | 普通索引 |
| content_type | VARCHAR | 50 | NULL | NULL | 内容类型(MIME,如"application/pdf") | 否 |
| title | VARCHAR | 500 | NULL | NULL | 标题 | 否 |
| description | VARCHAR | 1000 | NULL | NULL | 描述 | 否 |
| url | VARCHAR | 500 | NULL | NULL | 访问URL | 否 |
| file_name | VARCHAR | 255 | NULL | NULL | 文件名 | 否 |
| file_size | BIGINT | - | NULL | NULL | 文件大小(字节) | 否 |
| doi | VARCHAR | 100 | NULL | NULL | 补充材料DOI | 普通索引 |
| license | VARCHAR | 50 | NULL | NULL | 许可证(如"CC-BY","CC0") | 否 |
| authors | VARCHAR | 500 | NULL | NULL | 作者/贡献者 | 否 |
| order_num | INTEGER | - | NOT NULL | 1 | 顺序号(用于排序显示) | 否 |
| is_public | BOOLEAN | - | NOT NULL | 1 | 是否公开(0=否,1=是) | 普通索引 |
| available_date | DATE | - | NULL | NULL | 可用日期(延迟发布支持) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 对象元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **object_type**: VARCHAR(50) NOT NULL,对象类型,枚举值 CHECK 约束:
   - `Figure`: 图片/图表
   - `Table`: 表格
   - `Dataset`: 数据集
   - `Code`: 代码/脚本
   - `Video`: 视频
   - `Audio`: 音频
   - `Document`: 文档(PDF/Word)
   - `Presentation`: 演示文稿
   - `Other`: 其他类型
3. **content_type**: VARCHAR(50),MIME 类型,如:
   - `application/pdf`
   - `image/jpeg`
   - `text/csv`
   - `application/zip`
   - `video/mp4`
4. **title**: VARCHAR(500),补充材料标题,如:
   - "Figure S1. Supplementary survival curves"
   - "Dataset S2. Raw gene expression data"
5. **description**: VARCHAR(1000),详细描述
6. **url**: VARCHAR(500),访问 URL,如:
   - `https://example.com/supplementary/12345/figure-s1.pdf`
   - `https://zenodo.org/record/1234567`
7. **file_name**: VARCHAR(255),原始文件名,如:
   - `supplementary_figure_1.pdf`
   - `raw_data.csv`
8. **file_size**: BIGINT,文件大小(字节)
   - 用于显示和存储容量统计
   - 最大支持 9,223,372,036,854,775,807 字节(约 8 EB)
9. **doi**: VARCHAR(100),补充材料独立 DOI
   - 部分数据仓库(如 Zenodo)为补充材料分配独立 DOI
10. **license**: VARCHAR(50),许可证类型(详见设计决策 5):
    - **开放许可证**: CC0, CC-BY, CC-BY-SA, CC-BY-NC, MIT, Apache-2.0, GPL-3.0
    - **受限许可证**: Proprietary, All Rights Reserved, Custom
11. **authors**: VARCHAR(500),作者/贡献者,如:
    - "Smith J, Johnson M"
    - "Data generated by John Doe Lab"
12. **order_num**: INTEGER NOT NULL,顺序号,默认值 1
13. **is_public**: BOOLEAN NOT NULL,是否公开访问,默认值 1(详见设计决策 5)
    - `1`: 任何人可访问
    - `0`: 仅订阅用户或有权限用户访问
14. **available_date**: DATE,可用日期(延迟发布支持)
    - 禁运期管理: `available_date > 当前日期` 表示尚未开放
    - 立即可用: `available_date <= 当前日期` 或 NULL
15. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "format_version": "1.0",
      "software": "R 4.2.0",
      "checksum_md5": "a1b2c3d4e5f6...",
      "download_count": 1234
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_supplemental_object 表不包含完整审计字段?**
- 补充材料创建后通常不再修改
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量大(1000万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有补充材料(高频)'
INDEX `idx_object_type` (`object_type`) COMMENT '对象类型索引,支持按类型筛选(中频)'
INDEX `idx_public` (`is_public`) COMMENT '公开标志索引,支持筛选公开材料'
INDEX `idx_doi` (`doi`) COMMENT 'DOI索引,支持按DOI查询补充材料'
INDEX `idx_available_date` (`available_date`) COMMENT '可用日期索引,支持按可用日期筛选'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_publication | 0.85 | 高 ✅ | 每篇文献平均 2-5 个补充材料,选择性高 |
| idx_object_type | 0.50 | 中 ✅ | 9 种枚举值,分布不均(Figure/Table 占 70%) |
| idx_public | 0.40 | 中 ⚠️ | 仅两值(0/1),但访问控制是核心需求 |
| idx_doi | 0.95 | 极高 ✅ | DOI 几乎唯一,覆盖率约 20% |
| idx_available_date | 0.60 | 中 ✅ | 跨度 10+ 年,支持禁运期管理 |

**索引设计权衡说明:**
- `idx_publication` 支持查询某文献的所有补充材料(最高频操作)
- `idx_object_type` 支持按类型统计(如统计数据集数量)
- `idx_public` 选择性低但保留:访问控制是法规要求
- 未创建 `content_type` 或 `file_size` 索引:查询频率极低

---

## 📊 表 7: cat_publication_history (发布历史表)

**表说明:** 记录文献生命周期事件(投稿、接收、发表等),支持时序性保障
**记录数预估:** 初始 1200万 / 年增长 400万 / 5年规模 3000万
**主要查询场景:**
1. 查询某文献的完整历史时间线(>1000次/天,高频)
2. 按 event_type 统计审稿周期(100-500次/天,中频)
3. 按 event_date 时间范围查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合唯一索引 |
| event_type | VARCHAR | 50 | NOT NULL | - | 事件类型(CHECK 约束) | 普通索引 |
| event_date | DATE | - | NOT NULL | - | 事件日期 | 复合索引 |
| date_precision | VARCHAR | 10 | NULL | NULL | 日期精度(CHECK 约束) | 否 |
| description | VARCHAR | 500 | NULL | NULL | 事件描述 | 否 |
| actor | VARCHAR | 100 | NULL | NULL | 执行者/机构 | 否 |
| previous_status | VARCHAR | 100 | NULL | NULL | 之前状态 | 否 |
| new_status | VARCHAR | 100 | NULL | NULL | 新状态 | 否 |
| order_num | INTEGER | - | NOT NULL | - | 事件顺序号(同一文献内唯一) | 复合唯一索引 |
| is_public | BOOLEAN | - | NOT NULL | 1 | 是否公开(0=否,1=是) | 否 |
| metadata | JSON | - | NULL | NULL | 事件元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **event_type**: VARCHAR(50) NOT NULL,事件类型,枚举值 CHECK 约束(详见设计决策 6):
   ```sql
   CHECK (event_type IN (
       'Submitted',         -- 投稿
       'Received',          -- 接收(编辑部收到)
       'Revised',           -- 修订
       'Accepted',          -- 接受
       'Rejected',          -- 拒稿
       'Published Online',  -- 在线发表
       'Published Print',   -- 印刷发表
       'Corrected',         -- 更正
       'Retracted',         -- 撤稿
       'Reinstated',        -- 恢复
       'Updated',           -- 更新
       'Indexed',           -- 索引(如被PubMed收录)
       'Archived'           -- 归档
   ))
   ```
3. **event_date**: DATE NOT NULL,事件日期,必填字段
4. **date_precision**: VARCHAR(10),日期精度,枚举值 CHECK 约束:
   - `day`: 完整日期(年-月-日)
   - `month`: 年-月精度
   - `year`: 仅年份精度
5. **description**: VARCHAR(500),事件描述,如:
   - "Manuscript submitted by corresponding author"
   - "Accepted after major revision"
6. **actor**: VARCHAR(100),执行者/机构,如:
   - "Editor-in-Chief"
   - "Peer Review Team"
   - "Publisher"
7. **previous_status / new_status**: VARCHAR(100),状态转换
   - 用于追踪状态变化
   - 如: `previous_status='Under Review'`, `new_status='Accepted'`
8. **order_num**: INTEGER NOT NULL,事件顺序号(详见设计决策 6)
   - 同一文献内唯一
   - 用于保证同一天事件的顺序
   - 应用层自动生成(查询最大值+1)
9. **is_public**: BOOLEAN NOT NULL,是否公开,默认值 1
   - 部分事件可能不公开(如拒稿、内部审查)
10. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "reviewer_count": 3,
      "revision_round": 2,
      "decision_maker": "Associate Editor",
      "turnaround_time_days": 45
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_history 表不包含完整审计字段?**
- 历史事件创建后不再修改(只增不改)
- 不涉及并发冲突,不需要乐观锁(version)
- 数据量大(3000万行),精简字段节省存储
- 历史记录本身就是审计数据

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_history_order` (`publication_id`, `order_num`) COMMENT '文献+顺序号唯一索引,保证顺序号在同一文献内唯一'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有历史事件(高频)'
INDEX `idx_event_type` (`event_type`) COMMENT '事件类型索引,支持按类型筛选(中频)'
INDEX `idx_event_date` (`event_date`) COMMENT '事件日期索引,支持按日期排序和统计'

-- 复合索引
INDEX `idx_pub_date` (`publication_id`, `event_date`, `order_num`) COMMENT '文献+日期+顺序号复合索引,优化时间线查询'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_history_order | 1.00 | 极高 ✅ | 组合绝对唯一,保证顺序号唯一性 |
| idx_publication | 0.80 | 高 ✅ | 每篇文献平均 3-5 个历史事件,选择性高 |
| idx_event_type | 0.40 | 中 ✅ | 13 种枚举值,支持按类型统计 |
| idx_event_date | 0.60 | 中 ✅ | 跨度 70 年,支持时间线分析 |
| idx_pub_date | 0.95 | 极高 ✅ | 三字段组合高度唯一,优化时间线查询 |

**索引设计权衡说明:**
- `uk_history_order` 保证事件顺序的唯一性和一致性
- `idx_pub_date` 复合索引优化时间线查询(最频繁操作)
  - 查询模式: `WHERE publication_id = ? ORDER BY event_date, order_num`
- `idx_event_type` 支持审稿周期统计(Submitted → Accepted 时间差)
- 未创建 `actor` 或 `is_public` 索引:查询频率极低

---

## 🎯 特殊设计决策汇总

本节汇总关联信息模块的关键设计决策,详细说明参见 **[ER 图文档](../02-er-diagrams/4-related-information.md) 第四章**。

### 决策 1: 资助信息去重策略

**问题**: 如何避免相同资助项目在数据库中重复存储?

**决定**: 采用 `agency_name + grant_id` 复合去重策略,通过 `dedup_key` 字段实现

**理由**:
- 覆盖率高(90%+),准确性好
- 减少 60%+ 存储空间
- 便于统计资助影响力和文献产出

**实现方式**:
```java
// 生成去重键
String dedupKey = MD5(normalize(agencyName) + "|" + normalize(grantId));

// 规范化逻辑
private String normalize(String text) {
    return text.toLowerCase()
               .replaceAll("\\s+", " ")         // 多空格转单空格
               .replaceAll("[^a-z0-9 ]", "")    // 移除特殊字符
               .trim();
}

// 插入前去重查询
Funding existing = fundingRepo.findByDedupKey(dedupKey);
if (existing != null) {
    return existing; // 复用现有记录
}
// 否则创建新记录
```

**优势**:
- ✅ 覆盖率高: 90%+ 的资助信息可去重
- ✅ 准确性好: 机构名+项目编号唯一标识资助
- ✅ 减少冗余: 预计减少 60%+ 存储空间
- ✅ 统计友好: 便于分析资助影响力和文献产出

---

### 决策 2: 引用的双重关联设计

**问题**: 如何同时支持库内文献引用和库外文献引用?

**决定**: 采用双重关联设计: `cited_publication_id` + `cited_pmid/doi`

**理由**:
- 库内引用(~30%): 支持引用网络构建、被引分析
- 库外引用(~70%): 保留完整引用信息,不丢失数据
- 自动升级: 新文献入库时,自动将库外引用升级为库内引用

**字段设计**:
```sql
-- 优先使用的字段
cited_publication_id BIGINT      -- 如果被引文献在库中,使用外键关联
                                 -- 外键约束: FOREIGN KEY (cited_publication_id)
                                 --           REFERENCES cat_publication(id)

-- 降级使用的字段(库外引用)
cited_pmid           VARCHAR(20)  -- 被引文献的 PMID
cited_doi            VARCHAR(200) -- 被引文献的 DOI

-- 始终存储的字段(无论是否在库中)
citation_text        VARCHAR(2000) -- 原始引用文本
article_title        VARCHAR(500)  -- 文章标题
authors              VARCHAR(500)  -- 作者列表
source               VARCHAR(500)  -- 来源期刊/书籍
year                 SMALLINT      -- 年份
```

**优先级规则**:
```java
// 1. 优先通过 PMID 匹配库内文献
if (citedPmid != null) {
    Publication citedPub = publicationRepo.findByPmid(citedPmid);
    if (citedPub != null) {
        reference.setCitedPublicationId(citedPub.getId());
        return; // 匹配成功,使用外键关联
    }
}

// 2. 其次通过 DOI 匹配
if (citedDoi != null) {
    Publication citedPub = publicationRepo.findByDoi(citedDoi);
    if (citedPub != null) {
        reference.setCitedPublicationId(citedPub.getId());
        return;
    }
}

// 3. 无法匹配库内文献,保留 PMID/DOI
// cited_pmid 和 cited_doi 已设置,无需额外处理
```

**优势**:
- ✅ 库内引用: 支持引用网络构建、被引分析
- ✅ 库外引用: 保留完整引用信息,不丢失数据
- ✅ 自动升级: 新文献入库时,自动将库外引用升级为库内引用
- ✅ 数据完整性: 即使外键关联失败,仍保留标识符

---

### 决策 3: 外部引用 vs 参考文献分离

**问题**: 为什么将外部数据库引用和参考文献分离存储?

**决定**: 分离存储在 `cat_reference` 和 `cat_external_reference` 两张表

**理由**:
- 字段差异大: 文献引用有 article_title/source/year,数据库引用有 database_name/accession_number
- 查询模式不同: 文献引用查询高频(>2000次/天),数据库引用中频(>500次/天)
- 语义清晰: 明确区分文献引用和数据引用

**字段差异对比**:

| 字段类型 | cat_reference | cat_external_reference |
|---------|--------------|----------------------|
| 通用字段 | publication_id, description, order_num | publication_id, description, order_num |
| 文献特定 | article_title, source, volume, issue, pages, year, authors | ❌ 不需要 |
| 数据库特定 | ❌ 不需要 | database_name, database_category, accession_number, version |
| 引用特定 | citation_text, reference_type, reference_number, is_retracted | reference_type |

**优势**:
- ✅ 字段精简: 避免大量 NULL 值
- ✅ 查询高效: 分离查询,各有专门索引
- ✅ 语义清晰: 明确区分文献引用和数据引用
- ✅ 扩展性强: 各自独立扩展,互不影响

---

### 决策 4: 相关项类型设计

**问题**: 如何覆盖撤稿、勘误、评论等 12 种关联类型?

**决定**: 采用单表 + 类型枚举方式

**relationship_type 枚举**:
```sql
CHECK (relationship_type IN (
    'Retraction',            -- 撤稿
    'Partial Retraction',    -- 部分撤稿
    'Expression of Concern', -- 关注声明
    'Withdrawn',             -- 撤回
    'Erratum',               -- 勘误
    'Correction',            -- 更正
    'Comment',               -- 评论
    'Response',              -- 回应
    'Update',                -- 更新
    'Republication',         -- 重新发表
    'Superseded',            -- 被取代
    'Duplicate'              -- 重复发表
))
```

**优势**:
- ✅ 覆盖全面: 支持 12 种关联类型
- ✅ 查询简单: 单表查询,无需多表 UNION
- ✅ 扩展灵活: 新增类型只需修改枚举
- ✅ 撤稿监测: 支持撤稿文献的主动监测和警告

**典型查询**:
```sql
-- 查询所有撤稿文献
SELECT p.*, ri.relationship_date, ri.description
FROM cat_publication p
JOIN cat_related_item ri ON p.id = ri.publication_id
WHERE ri.relationship_type IN ('Retraction', 'Partial Retraction')
  AND ri.status = 'Active'
ORDER BY ri.relationship_date DESC;
```

---

### 决策 5: 补充材料访问控制

**问题**: 如何管理补充材料的访问权限和许可证?

**决定**: 采用 `is_public` + `license` + `available_date` 组合策略

**字段设计**:
```sql
is_public       BOOLEAN      -- 是否公开访问
                             -- TRUE: 任何人可访问
                             -- FALSE: 仅订阅用户或有权限用户访问

license         VARCHAR(50)  -- 许可证类型
                             -- CC-BY, CC-BY-SA, CC-BY-NC, CC0,
                             -- MIT, Apache-2.0, GPL-3.0, Proprietary

available_date  DATE         -- 可用日期
                             -- 延迟发布: available_date > 当前日期
                             -- 立即可用: available_date <= 当前日期
```

**访问控制逻辑**:
```java
public boolean canAccess(User user, SupplementalObject obj) {
    // 1. 检查是否公开
    if (!obj.isPublic()) {
        // 需要订阅或特定权限
        return user.hasSubscription() || user.hasPermission("DOWNLOAD_SUPPLEMENT");
    }

    // 2. 检查可用日期
    if (obj.getAvailableDate() != null
        && obj.getAvailableDate().isAfter(LocalDate.now())) {
        // 延迟发布,未到开放日期
        return false;
    }

    // 3. 检查许可证限制
    if ("Proprietary".equals(obj.getLicense())) {
        return user.hasPermission("PROPRIETARY_ACCESS");
    }

    return true;
}
```

**优势**:
- ✅ 明确权限: `is_public` 标记清晰
- ✅ 许可证管理: 支持开放许可证(CC系列)和专有许可证
- ✅ 延迟发布: `available_date` 支持禁运期管理
- ✅ 审计友好: 记录访问日志,追溯下载行为

---

### 决策 6: 历史事件的时序性保障

**问题**: 如何确保发布历史事件的时序性和完整性?

**决定**: 采用 `event_date` + `order_num` 双重保障策略

**字段设计**:
```sql
event_date      DATE         -- 事件日期(必填)
date_precision  VARCHAR(10)  -- 日期精度: day/month/year
order_num       INTEGER      -- 事件顺序号(同一文献内唯一)

-- 唯一性约束
CREATE UNIQUE INDEX uk_history_order
ON cat_publication_history(publication_id, order_num);
```

**排序规则**:
```sql
-- 标准排序(优先日期,其次顺序号)
SELECT * FROM cat_publication_history
WHERE publication_id = ?
ORDER BY event_date, order_num;

-- 时间线查询(包含日期精度处理)
SELECT
    event_type,
    CASE date_precision
        WHEN 'day' THEN DATE_FORMAT(event_date, '%Y-%m-%d')
        WHEN 'month' THEN DATE_FORMAT(event_date, '%Y-%m')
        WHEN 'year' THEN DATE_FORMAT(event_date, '%Y')
    END as formatted_date,
    description
FROM cat_publication_history
WHERE publication_id = ?
ORDER BY event_date, order_num;
```

**order_num 生成逻辑**:
```java
// 插入新事件时,自动计算顺序号
public void addEvent(Long publicationId, HistoryEvent event) {
    // 1. 查询当前最大顺序号
    Integer maxOrderNum = historyRepo.findMaxOrderNum(publicationId);
    int nextOrderNum = (maxOrderNum == null) ? 1 : maxOrderNum + 1;

    // 2. 设置顺序号
    event.setOrderNum(nextOrderNum);

    // 3. 保存
    historyRepo.save(event);
}
```

**优势**:
- ✅ 时序保障: `order_num` 确保同一天事件的顺序
- ✅ 精度处理: `date_precision` 正确表达不完整日期
- ✅ 查询高效: 日期+顺序号组合索引优化排序查询
- ✅ 数据可靠: 不依赖不可靠的 timestamp 数据

---

## ✅ 设计检查清单

### 字段设计检查
- [x] 数据类型最优(BIGINT/VARCHAR/TEXT/JSON/DECIMAL/DATE/BOOLEAN 合理选择)
- [x] 长度合理(agency_name=200, grant_id=100, citation_text=2000, dedup_key=255)
- [x] NOT NULL 约束已添加(publication_id, event_type, event_date, relationship_type 等关键字段)
- [x] 默认值合理(is_primary=0, is_public=1, order_num=1, is_retracted=0)
- [x] CHECK 约束完整(funding_type 枚举, relationship_type 枚举, event_type 枚举, date_precision 枚举)
- [x] 无遗漏业务字段(已包含所有 ER 图定义的字段)

### 索引设计检查
- [x] 主键索引已定义(所有表)
- [x] 唯一约束已添加(dedup_key, uk_pub_funding, uk_reference_num, uk_external_ref, uk_history_order)
- [x] 外键字段已索引(publication_id, funding_id, cited_publication_id, related_publication_id)
- [x] 查询条件字段已索引(funder_id, cited_pmid, cited_doi, relationship_type, event_type, year)
- [x] 复合索引顺序最优(pub_type, type_value, venue_volume_issue, pub_date)
- [x] 避免过多索引(每表 4-6 个业务索引,总计 38 个索引)

### 性能优化检查
- [x] 去重字段已优化(funding.dedup_key, author.dedup_key)
- [x] 双重关联设计(reference 表 FK + PMID/DOI)
- [x] 软删除字段包含在索引中(如需要)
- [x] JSON 扩展字段已包含(metadata 字段覆盖所有表)
- [x] 大表索引精简(reference 表仅 7 个索引,避免索引膨胀)

### 规范性检查
- [x] 包含标准审计字段(所有表简化审计,仅 created_at)
- [x] 字段命名符合规范(小写 + 下划线,英文标识符)
- [x] 注释完整清晰(已在索引定义中添加 COMMENT)
- [x] 字符集 UTF8MB4(将在 DDL 中定义)
- [x] 时间字段使用 TIMESTAMP(6)(微秒精度,UTC 时区)

### 数据质量检查
- [x] 唯一性约束(dedup_key, uk_pub_funding, uk_reference_num, uk_external_ref, uk_history_order)
- [x] 检查约束(funding_type 枚举, relationship_type 枚举, event_type 枚举, date_precision 枚举)
- [x] 外键约束(所有 FK 字段,在 DDL 中定义)
- [x] 非空约束(publication_id, event_type, relationship_type, dedup_key 等关键字段)

### 业务逻辑检查
- [x] 去重策略完整(funding 表 dedup_key 复合去重)
- [x] 双重关联设计合理(reference 表 FK + PMID/DOI)
- [x] 访问控制明确(supplemental_object 表 is_public + license + available_date)
- [x] 时序性保障(publication_history 表 event_date + order_num)
- [x] 相关项类型枚举完整(12 种关联类型覆盖所有场景)

---

## 📝 总结

关联信息模块的 7 张表详细设计已完成,关键成果:

### 设计亮点

1. **资助去重**: dedup_key (agency_name + grant_id) 减少 60%+ 冗余,覆盖率 90%+
2. **引用双重关联**: cited_publication_id + cited_pmid/doi 支持库内外引用,自动升级
3. **外部引用分离**: 清晰区分文献引用和数据引用,字段精简,查询高效
4. **相关项类型枚举**: 12 种关联类型,支持撤稿监测和质量控制
5. **补充材料访问控制**: is_public + license + available_date 三重保障
6. **历史事件时序性**: event_date + order_num 双重保障,支持完整时间线
7. **索引优化**: 38 个精心设计的索引,覆盖所有高频查询场景

### 数据规模预估(5年)

| 表名 | 5年规模 | 单行大小 | 存储预估 | 索引预估 | 总计 |
|------|---------|---------|---------|---------|------|
| cat_funding | 700万行 | 0.8 KB | 5.6 GB | 2.0 GB | 7.6 GB |
| cat_publication_funding | 1750万行 | 0.3 KB | 5.25 GB | 2.0 GB | 7.25 GB |
| cat_reference | 5.25亿行 | 0.9 KB | 472.5 GB | 180 GB | 652.5 GB |
| cat_external_reference | 500万行 | 0.5 KB | 2.5 GB | 1.0 GB | 3.5 GB |
| cat_related_item | 100万行 | 0.6 KB | 600 MB | 250 MB | 850 MB |
| cat_supplemental_object | 1000万行 | 0.7 KB | 7.0 GB | 2.5 GB | 9.5 GB |
| cat_publication_history | 3000万行 | 0.4 KB | 12 GB | 4.5 GB | 16.5 GB |
| **总计** | **5.697亿行** | - | **505.5 GB** | **192.3 GB** | **697.8 GB** |

**说明**:
- reference 表占总存储的 93%+,是存储和性能优化的重点
- 单行大小包含业务字段+审计字段
- 索引预估约为数据大小的 30%-40%
- 实际存储需考虑 InnoDB 页填充率(通常 70%-80%)
- 建议预留 2 倍空间(1.5 TB+)用于索引膨胀和临时表
- reference 表需考虑分区策略(按 publication_year 分区)

### 关键技术考虑

1. **reference 表分区策略**:
   - 数据量 5.25 亿行,建议按 publication_year 范围分区
   - 每个分区覆盖 5 年数据,预计 15 个分区
   - 查询优化: 大部分查询集中在最近 10 年数据

2. **索引维护**:
   - reference 表索引总大小 180 GB+,需定期 OPTIMIZE TABLE
   - 考虑使用 Percona Toolkit 的 pt-online-schema-change 进行在线索引维护

3. **存储优化**:
   - 考虑使用压缩表(ROW_FORMAT=COMPRESSED)
   - reference 表预计压缩比 50%,可节省 236 GB 存储

---

## 下一步

关联信息表详细设计完成,进入 **[阶段 3: SQL DDL 生成](../04-sql-ddl/4-related-information.sql)**

下一阶段将生成:
- 完整的 CREATE TABLE 语句(包含字符集、引擎、注释、分区)
- 索引定义 SQL(包含所有索引和约束)
- 外键约束 SQL(如果启用外键)
- 表结构验证脚本
- 分区定义 SQL(reference 表)

---

*本文档是 patra_catalog 数据库关联信息模块的详细表设计,是 SQL DDL 生成的输入基础。*
