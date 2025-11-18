# 阶段 2：详细表设计 - 人员机构模块(6张表)

> **设计目标**: 详细定义 patra_catalog 数据库人员机构模块每个表的字段、类型、约束、索引
>
> **创建日期**: 2025-01-18
> **设计范围**: patra_catalog 人员机构模块(cat_publication_author, cat_affiliation, cat_author_affiliation, cat_investigator, cat_publication_investigator, cat_personal_name_subject)
> **作者**: Patra Lin

---

## 📑 模块概览

本文档详细设计 patra_catalog 数据库人员机构管理相关的 6 张表。这些表与核心的 `cat_author` 和 `cat_publication` 表协作,构成完整的人员机构体系。

### 表清单

| 表名 | 中文名 | 核心功能 | 预估规模 |
|------|--------|---------|---------|
| `cat_publication_author` | 文献-作者关联表 | 管理作者顺序和角色 | 1.4亿+ |
| `cat_affiliation` | 机构表 | 存储机构信息和标识符 | 45万+ |
| `cat_author_affiliation` | 作者-机构关联表 | 管理作者机构关系 | 1.75亿+ |
| `cat_investigator` | 研究者表 | 非作者的研究人员 | 50万+ |
| `cat_publication_investigator` | 文献-研究者关联表 | 研究者角色管理 | 150万+ |
| `cat_personal_name_subject` | 人物主题表 | 文献主题人物 | 5万+ |

### 模块设计亮点

- ✅ **作者顺序唯一性** - 防止重复顺序号,保证学术顺序正确性
- ✅ **机构标准化** - 支持 ROR/GRID/ISNI 多种国际标识符
- ✅ **复合去重策略** - ROR/GRID 优先 + 标准化名称兜底,去重准确率 90%+
- ✅ **时间维度追踪** - 作者-机构关联支持历史追踪和特定文献上下文
- ✅ **研究者独立管理** - investigator 与 author 职责分离,支持临床试验场景
- ✅ **审计策略优化** - 关联表简化审计,主表适度审计,平衡数据质量和存储成本

---

## 📊 表 1: cat_publication_author (文献-作者关联表)

**表说明:** 管理文献与作者的多对多关系,记录作者顺序、角色和贡献类型
**记录数预估:** 初始 3000万 / 年增长 2200万 / 5年规模 1.4亿
**主要查询场景:**
1. 查询某文献的所有作者(按顺序)(>5000次/天,高频)
2. 查询某作者的所有文献(>3000次/天,高频)
3. 筛选第一作者文献(>1000次/天,高频)
4. 筛选通讯作者文献(>800次/天,中频)
5. 按 CRediT 贡献类型筛选(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合唯一索引 |
| author_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 作者ID(外键:cat_author.id) | 复合唯一索引 |
| author_order | INT UNSIGNED | - | NOT NULL | 1 | 作者顺序(1=第一作者,2=第二作者...) | 复合唯一索引 |
| is_first_author | BOOLEAN | - | NOT NULL | 0 | 是否第一作者(0=否,1=是) | 普通索引 |
| is_corresponding_author | BOOLEAN | - | NOT NULL | 0 | 是否通讯作者(0=否,1=是) | 普通索引 |
| is_equal_contribution | BOOLEAN | - | NOT NULL | 0 | 是否同等贡献作者(0=否,1=是) | 否 |
| contribution_type | VARCHAR | 50 | NULL | NULL | 贡献类型(CRediT分类,如"Conceptualization") | 否 |
| affiliation_string | VARCHAR | 1000 | NULL | NULL | 原始机构字符串(外部采集,未标准化) | 否 |
| author_metadata | JSON | - | NULL | NULL | 作者元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL(每个关联必须属于某个文献)
2. **author_id**: 外键关联到 cat_author.id,NOT NULL(每个关联必须属于某个作者)
3. **author_order**: INT UNSIGNED,作者在文献中的顺序号:
   - 必须从 1 开始,连续递增
   - 同一文献不允许重复(通过唯一索引保证)
   - CHECK 约束: `author_order > 0`
4. **is_first_author**: BOOLEAN,标识是否第一作者:
   - 每篇文献只能有一个 is_first_author=1
   - 通常 author_order=1 时,is_first_author=1
   - 但允许特殊情况(如共同第一作者)
5. **is_corresponding_author**: BOOLEAN,标识是否通讯作者:
   - 允许多个通讯作者(现代科研趋势)
   - 通讯作者负责稿件沟通和最终确认
6. **is_equal_contribution**: BOOLEAN,标识是否同等贡献:
   - 用于标记"共同第一作者"或"共同通讯作者"
   - 现代科研常见(如生物医学领域)
7. **contribution_type**: VARCHAR(50),CRediT 贡献分类(国际标准):
   - 14 种标准类型:Conceptualization/Data curation/Formal analysis/Funding acquisition/Investigation/Methodology/Project administration/Resources/Software/Supervision/Validation/Visualization/Writing-original draft/Writing-review & editing
   - 可以有多个类型(JSON 数组)
8. **affiliation_string**: VARCHAR(1000),原始机构字符串:
   - 保留外部数据源的原始机构信息
   - 未经标准化处理(如 "Dept. of Medicine, Harvard Univ., Boston, MA, USA")
   - 用于审计、调试和数据验证
   - 标准化机构信息存储在 cat_affiliation 表
9. **author_metadata**: JSON 字段,存储扩展信息,如:
   ```json
   {
     "credit_roles": ["Conceptualization", "Writing-original draft"],
     "corresponding_email": "john.smith@example.com",
     "conflict_of_interest": "None",
     "funding_support": "NIH Grant R01-123456"
   }
   ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_author 表不包含完整审计字段?**
- 关联关系创建后通常不再修改(作者顺序固定)
- 数据量极大(1.4亿行),精简字段节省存储(节省约 1.12GB)
- 不涉及高频并发冲突,不需要乐观锁(version)
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_pub_author` (`publication_id`, `author_id`) COMMENT '防止同一作者在同一文献重复关联'
UNIQUE INDEX `uk_author_order` (`publication_id`, `author_order`) COMMENT '防止同一文献的作者顺序重复,保证学术顺序正确性'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有作者(高频)'
INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有文献(高频)'
INDEX `idx_first_author` (`is_first_author`) COMMENT '第一作者索引,支持筛选第一作者文献(学术评价重要指标)'
INDEX `idx_corresponding` (`is_corresponding_author`) COMMENT '通讯作者索引,支持筛选通讯作者文献(联系查询)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_pub_author | 0.98 | 极高 ✅ | 组合几乎唯一(极少数重复录入) |
| uk_author_order | 1.00 | 极高 ✅ | 组合绝对唯一(业务强约束) |
| idx_publication | 0.85 | 高 ✅ | 平均每文献 4 个作者,选择性高 |
| idx_author | 0.80 | 高 ✅ | 平均每作者 8 篇文献,选择性高 |
| idx_first_author | 0.25 | 低 ⚠️ | 仅两值(0/1),但学术评价强需求 |
| idx_corresponding | 0.30 | 低 ⚠️ | 仅两值(0/1),但联系查询需求 |

**索引设计权衡说明:**
- `uk_author_order` 是业务核心约束:防止数据录入错误(如两个作者都是顺序 1)
- `uk_pub_author` 防止重复关联:同一作者不能在同一文献出现两次
- `idx_first_author` 和 `idx_corresponding` 虽然选择性低,但是学术评价和联系查询的核心需求,必须保留
- 未创建 `contribution_type` 索引:查询频率极低(<100次/天),选择性不高

### CHECK 约束

```sql
-- 作者顺序必须大于 0
CHECK (author_order > 0)

-- 第一作者一致性检查(author_order=1 时必须 is_first_author=1)
CHECK (
  (author_order = 1 AND is_first_author = 1) OR
  (author_order > 1)
)
```

**约束设计说明:**
- 第一条约束:防止 author_order=0 或负数
- 第二条约束:强制 author_order=1 时 is_first_author=1,保证数据一致性
- 注意:允许 author_order>1 但 is_first_author=1(共同第一作者场景)

---

## 📊 表 2: cat_affiliation (机构表)

**表说明:** 存储机构信息,支持多种国际标识符,实现机构标准化和去重
**记录数预估:** 初始 15万 / 年增长 6万 / 5年规模 45万
**主要查询场景:**
1. 按 ROR ID 查询机构(>500次/天,中频)
2. 按 GRID ID 查询机构(>300次/天,中频)
3. 按去重键查询机构(>1000次/天,高频,去重用)
4. 按国家统计机构(100-500次/天,中频)
5. 按机构名称模糊查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| name | VARCHAR | 500 | NOT NULL | - | 机构名称(标准化后) | 普通索引 |
| original_name | VARCHAR | 500 | NULL | NULL | 原始名称(外部采集,未标准化) | 否 |
| department | VARCHAR | 200 | NULL | NULL | 部门/科室(如"Department of Medicine") | 否 |
| division | VARCHAR | 200 | NULL | NULL | 分部/分院(如"School of Medicine") | 否 |
| section | VARCHAR | 200 | NULL | NULL | 科/组(如"Cardiology Section") | 否 |
| city | VARCHAR | 100 | NULL | NULL | 城市(如"Boston") | 否 |
| state_province | VARCHAR | 100 | NULL | NULL | 州/省(如"Massachusetts","广东") | 否 |
| country | VARCHAR | 100 | NULL | NULL | 国家(ISO 3166-1 alpha-3,如"USA","CHN") | 普通索引 |
| postal_code | VARCHAR | 20 | NULL | NULL | 邮政编码(如"02115") | 否 |
| ror_id | VARCHAR | 50 | NULL | NULL | ROR 标识符(如"https://ror.org/03vek6s52") | 唯一索引(部分) |
| grid_id | VARCHAR | 50 | NULL | NULL | GRID 标识符(如"grid.38142.3c") | 唯一索引(部分) |
| isni | VARCHAR | 50 | NULL | NULL | ISNI 标识符(如"0000 0004 1936 8948") | 否 |
| ringgold_id | VARCHAR | 50 | NULL | NULL | Ringgold ID(如"1812") | 否 |
| parent_affiliation | VARCHAR | 200 | NULL | NULL | 上级机构(如"Harvard University") | 否 |
| affiliation_type | VARCHAR | 50 | NULL | NULL | 机构类型(如"Education","Healthcare","Company") | 否 |
| dedup_key | VARCHAR | 255 | NOT NULL | - | 复合去重键(应用层计算,MD5哈希) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 机构元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **name**: VARCHAR(500) NOT NULL,标准化后的机构名称:
   - 应用层标准化处理(去除前后缀、统一大小写)
   - 如 "Harvard University"(统一格式)
   - 必填字段,作为主要显示名称
2. **original_name**: VARCHAR(500),保留外部数据源的原始名称:
   - 如 "Harvard Univ.","哈佛大学","HARVARD UNIVERSITY"
   - 用于审计和数据溯源
3. **department / division / section**: 机构层级结构:
   - department: 部门/科室(最常用)
   - division: 分部/分院(次级)
   - section: 科/组(最细粒度)
   - 示例:"Department of Medicine" > "Division of Cardiology" > "Heart Failure Section"
4. **city / state_province / country**: 地理位置信息:
   - city: 城市名称(如 "Boston","北京")
   - state_province: 州/省(如 "Massachusetts","广东")
   - country: ISO 3166-1 alpha-3 国家代码(3位字母,如 "USA","CHN","GBR")
5. **postal_code**: VARCHAR(20),邮政编码:
   - 支持多种格式(美国 "02115",中国 "100000",英国 "SW1A 1AA")
6. **ror_id**: VARCHAR(50),ROR(Research Organization Registry)标识符:
   - 格式: "https://ror.org/03vek6s52"
   - 国际标准,覆盖率约 60%,准确率 99%
   - 部分唯一索引(仅对非 NULL 值)
7. **grid_id**: VARCHAR(50),GRID(Global Research Identifier Database)标识符:
   - 格式: "grid.38142.3c"
   - 覆盖率约 75%,准确率 95%
   - 部分唯一索引(仅对非 NULL 值)
8. **isni**: VARCHAR(50),ISNI(International Standard Name Identifier)标识符:
   - 格式: "0000 0004 1936 8948"
   - 覆盖率较低(<30%),但国际标准
9. **ringgold_id**: VARCHAR(50),Ringgold ID:
   - 格式: "1812"
   - 商业标识符,覆盖率中等(40%)
10. **parent_affiliation**: VARCHAR(200),上级机构:
    - 如 "Department of Medicine" 的上级是 "Harvard Medical School"
    - 文本字段,不做外键关联(避免复杂层级)
11. **affiliation_type**: VARCHAR(50),机构类型,常见值:
    - `Education`: 教育机构(大学、学院)
    - `Healthcare`: 医疗机构(医院、诊所)
    - `Company`: 企业(制药公司、生物技术公司)
    - `Government`: 政府机构(CDC、NIH)
    - `Nonprofit`: 非营利组织
    - `Other`: 其他
12. **dedup_key**: VARCHAR(255) NOT NULL,复合去重键(详见设计决策 2):
    - 优先级 1: `ROR:{ror_id}` (覆盖率 60%,准确率 99%)
    - 优先级 2: `GRID:{grid_id}` (覆盖率 75%,准确率 95%)
    - 优先级 3: `MD5(标准化名称 + 国家 + 城市)` (覆盖率 90%,准确率 85%)
    - 优先级 4: `MD5(标准化名称 + 国家)` (覆盖率 100%,准确率 70%)
13. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "founded_year": 1636,
      "institution_size": "large",
      "research_funding": 1200000000,
      "website": "https://www.harvard.edu",
      "aliases": ["Harvard", "哈佛大学"]
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |
| updated_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 更新时间(UTC,微秒精度) |

**为什么 cat_affiliation 表不包含完整审计字段?**
- 机构信息相对静态,更新频率低(年更新频率<5%)
- 不涉及高频并发冲突,不需要乐观锁(version)
- 数据量中等(45万行),精简字段节省存储
- 保留 created_at/updated_at 满足基本审计需求

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引(部分唯一,仅对非 NULL 值)
UNIQUE INDEX `uk_ror` (`ror_id`) WHERE `ror_id` IS NOT NULL COMMENT 'ROR ID 唯一索引,支持按 ROR 查询机构(高准确率)'
UNIQUE INDEX `uk_grid` (`grid_id`) WHERE `grid_id` IS NOT NULL COMMENT 'GRID ID 唯一索引,支持按 GRID 查询机构(高准确率)'

-- 普通索引
INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持机构去重和合并(核心业务)'
INDEX `idx_country` (`country`) COMMENT '国家索引,支持按国家统计机构产出'
INDEX `idx_name` (`name`) COMMENT '机构名称索引,支持按名称查询(中频)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_ror | 0.99 | 极高 ✅ | ROR ID 几乎唯一(覆盖率 60%,其余为 NULL) |
| uk_grid | 0.98 | 极高 ✅ | GRID ID 高度唯一(覆盖率 75%,其余为 NULL) |
| idx_dedup_key | 0.95 | 极高 ✅ | 复合去重键高度唯一(95%+) |
| idx_country | 0.40 | 中 ⚠️ | 主要国家约 50 个,但地域分析需求强 |
| idx_name | 0.85 | 高 ✅ | 机构名称重复率低(标准化后) |

**索引设计权衡说明:**
- `uk_ror` 和 `uk_grid` 使用部分唯一索引(WHERE 条件):仅对非 NULL 值强制唯一,允许多个 NULL
- `idx_dedup_key` 是去重系统的核心,必须保留
- `idx_country` 虽然选择性不高,但地域分析是核心业务需求
- 未创建 `city` 或 `department` 索引:查询频率低,且值非常分散

---

## 📊 表 3: cat_author_affiliation (作者-机构关联表)

**表说明:** 管理作者与机构的多对多关系,支持时间维度追踪和特定文献上下文
**记录数预估:** 初始 5000万 / 年增长 2500万 / 5年规模 1.75亿
**主要查询场景:**
1. 查询某作者的所有机构(按时间排序)(>1000次/天,高频)
2. 查询某机构的所有作者(>800次/天,中频)
3. 查询某文献的作者机构(>2000次/天,高频)
4. 查询作者的主要机构(>500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| author_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 作者ID(外键:cat_author.id) | 复合索引 |
| affiliation_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 机构ID(外键:cat_affiliation.id) | 普通索引 |
| publication_id | BIGINT UNSIGNED | - | NULL, FK | NULL | 文献ID(外键:cat_publication.id,可选) | 普通索引 |
| start_date | DATE | - | NULL | NULL | 开始日期(作者加入机构日期) | 否 |
| end_date | DATE | - | NULL | NULL | 结束日期(作者离开机构日期) | 否 |
| affiliation_type | VARCHAR | 50 | NULL | NULL | 关联类型(如"current","past","visiting") | 否 |
| is_primary | BOOLEAN | - | NOT NULL | 0 | 是否主要机构(0=否,1=是) | 复合索引 |
| order_num | INT UNSIGNED | - | NULL | NULL | 机构顺序(作者有多个机构时排序) | 否 |
| metadata | JSON | - | NULL | NULL | 关联元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **author_id**: 外键关联到 cat_author.id,NOT NULL(每个关联必须属于某个作者)
2. **affiliation_id**: 外键关联到 cat_affiliation.id,NOT NULL(每个关联必须属于某个机构)
3. **publication_id**: 外键关联到 cat_publication.id,**可选(NULL)**:
   - **NULL**: 表示作者的一般机构归属(通用关联)
   - **非 NULL**: 表示发表特定文献时的机构(文献上下文关联)
   - 设计意图:同时支持"作者的机构历史"和"发表文献时的机构"两种查询
4. **start_date / end_date**: DATE 类型,时间维度支持:
   - `start_date`: 作者加入机构日期(如 "2020-01-01")
   - `end_date`: 作者离开机构日期(如 "2023-12-31")
   - 均可为 NULL:表示时间未知或当前仍在职
   - CHECK 约束: `end_date IS NULL OR end_date >= start_date`
   - 用于追踪作者的机构变更历史
5. **affiliation_type**: VARCHAR(50),关联类型,常见值:
   - `current`: 当前机构(仍在职)
   - `past`: 过去机构(已离职)
   - `visiting`: 访问学者
   - `adjunct`: 兼职
   - `emeritus`: 荣誉退休
6. **is_primary**: BOOLEAN,标识是否主要机构:
   - 每个作者只有一个 is_primary=1 的当前机构
   - 用于快速定位作者的主要归属
7. **order_num**: INT UNSIGNED,机构顺序:
   - 当作者有多个机构时,按重要性排序(1=最重要)
   - 可为 NULL(单一机构时不需要排序)
8. **metadata**: JSON 字段,存储扩展信息,如:
   ```json
   {
     "position": "Associate Professor",
     "department_role": "Department Chair",
     "email_at_institution": "john.smith@harvard.edu",
     "verified": true
   }
   ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_author_affiliation 表不包含完整审计字段?**
- 关联关系相对静态,创建后很少修改(除非作者换工作)
- 数据量极大(1.75亿行),精简字段节省存储(节省约 1.4GB)
- 不涉及高频并发冲突,不需要乐观锁(version)
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有机构(高频)'
INDEX `idx_affiliation` (`affiliation_id`) COMMENT '机构索引,支持查询某机构的所有作者(中频)'
INDEX `idx_publication` (`publication_id`) WHERE `publication_id` IS NOT NULL COMMENT '文献索引,支持查询某文献的作者机构(高频,部分索引优化)'

-- 复合索引
INDEX `idx_author_primary` (`author_id`, `is_primary`) COMMENT '作者+主要机构复合索引,支持快速查询作者的主要机构'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_author | 0.80 | 高 ✅ | 平均每作者 5 个机构关联(含历史) |
| idx_affiliation | 0.75 | 高 ✅ | 平均每机构 300+ 作者 |
| idx_publication | 0.85 | 高 ✅ | 仅索引非 NULL 值,选择性高 |
| idx_author_primary | 0.90 | 极高 ✅ | 组合高度唯一(每作者通常只有 1-2 个主要机构) |

**索引设计权衡说明:**
- `idx_publication` 使用部分索引(WHERE 条件):仅对非 NULL 值创建索引,节省存储空间(约 40%)
- `idx_author_primary` 复合索引顺序: author_id → is_primary,支持快速查询"作者的主要机构"
- 未创建 `start_date` 或 `end_date` 索引:时间范围查询频率低,且通常与 author_id 组合查询(已有 idx_author)
- 未创建 `affiliation_type` 索引:查询频率低,选择性不高

### CHECK 约束

```sql
-- 结束日期不能早于开始日期
CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
```

---

## 📊 表 4: cat_investigator (研究者表)

**表说明:** 存储研究者信息(非作者的研究人员,如临床试验 PI),支持去重
**记录数预估:** 初始 20万 / 年增长 6万 / 5年规模 50万
**主要查询场景:**
1. 按 ORCID 查询研究者(>200次/天,中频)
2. 按去重键查询研究者(>300次/天,中频,去重用)
3. 按邮箱查询研究者(<100次/天,低频)
4. 按研究者类型筛选(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| last_name | VARCHAR | 200 | NULL | NULL | 姓(Last Name/Family Name) | 否 |
| fore_name | VARCHAR | 200 | NULL | NULL | 名(First Name/Given Name) | 否 |
| initials | VARCHAR | 50 | NULL | NULL | 姓名缩写(如"J.K.") | 否 |
| suffix | VARCHAR | 50 | NULL | NULL | 后缀(如"Jr.","III","MD","PhD") | 否 |
| orcid | VARCHAR | 50 | NULL | NULL | ORCID 标识符(格式:0000-0001-2345-6789) | 普通索引 |
| researcher_id | VARCHAR | 100 | NULL | NULL | 研究者ID(ResearcherID/Publons) | 否 |
| investigator_type | VARCHAR | 100 | NULL | NULL | 研究者类型(如"PI","CoI","Collaborator") | 否 |
| affiliation_name | VARCHAR | 500 | NULL | NULL | 机构名称(文本,不关联 affiliation 表) | 否 |
| email | VARCHAR | 255 | NULL | NULL | 邮箱地址 | 普通索引 |
| dedup_key | VARCHAR | 255 | NOT NULL | - | 复合去重键(应用层计算,MD5哈希) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 研究者元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **last_name / fore_name**: VARCHAR(200),姓和名:
   - 与 cat_author 表字段设计一致
   - 支持长姓名和中文姓名
2. **initials**: VARCHAR(50),姓名缩写:
   - 如 "J.K. Rowling" 的 "J.K."
   - 某些临床试验注册仅提供缩写
3. **suffix**: VARCHAR(50),姓名后缀:
   - 如 "John Smith Jr.","Jane Doe, MD, PhD"
   - 西方姓名和学术头衔常见
4. **orcid**: VARCHAR(50),ORCID 标识符:
   - 格式: "0000-0001-2345-6789"
   - 覆盖率约 20%(低于作者表,因为非作者)
   - 非唯一索引(允许重复,因为可能与 author 表有重叠)
5. **researcher_id**: VARCHAR(100),研究者ID:
   - ResearcherID 或 Publons ID(Web of Science 平台)
6. **investigator_type**: VARCHAR(100),研究者类型,常见值:
   - `PI`: Principal Investigator(主要研究者)
   - `CoI`: Co-Investigator(协同研究者)
   - `Collaborator`: 合作者
   - `Coordinator`: 协调员
   - `Sponsor`: 资助方代表
7. **affiliation_name**: VARCHAR(500),机构名称:
   - **文本字段,不做外键关联**(与 cat_affiliation 表独立)
   - 原因:临床试验注册的机构名称非标准化,难以匹配
   - 保留原始机构字符串
8. **email**: VARCHAR(255),邮箱地址:
   - 联系研究者的主要方式
9. **dedup_key**: VARCHAR(255) NOT NULL,复合去重键:
   - 优先级 1: `ORCID:{orcid}` (覆盖率 20%,准确率 99%)
   - 优先级 2: `MD5(姓名 + 机构 + 邮箱)` (覆盖率 40%,准确率 85%)
   - 优先级 3: `MD5(姓名 + 机构)` (覆盖率 60%,准确率 70%)
   - 优先级 4: `MD5(姓名 + 邮箱)` (覆盖率 80%,准确率 60%)
   - 优先级 5: `MD5(姓名)` (覆盖率 100%,准确率 30%)
10. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "clinical_trial_id": "NCT12345678",
      "role_description": "Study coordinator for Phase III trial",
      "contact_phone": "+1-617-555-1234",
      "alternate_email": "john.smith@cro.com"
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |
| updated_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 更新时间(UTC,微秒精度) |

**为什么 cat_investigator 表不包含完整审计字段?**
- 研究者信息相对静态,更新频率低(主要是补充联系方式)
- 不涉及高频并发冲突,不需要乐观锁(version)
- 数据量中等(50万行),精简字段节省存储
- 保留 created_at/updated_at 满足基本审计需求

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_orcid` (`orcid`) WHERE `orcid` IS NOT NULL COMMENT 'ORCID 索引,支持按 ORCID 查询研究者(部分索引优化)'
INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持研究者去重和合并(核心业务)'
INDEX `idx_email` (`email`) COMMENT '邮箱索引,支持按邮箱查询研究者(联系场景)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_orcid | 0.99 | 极高 ✅ | ORCID 几乎唯一(覆盖率 20%,其余为 NULL) |
| idx_dedup_key | 0.95 | 极高 ✅ | 复合去重键高度唯一(95%+) |
| idx_email | 0.85 | 高 ✅ | 邮箱几乎唯一(少数共享邮箱除外) |

**索引设计权衡说明:**
- `idx_orcid` 使用部分索引(WHERE 条件):仅对非 NULL 值创建索引,节省存储空间(约 80%)
- 未创建 `last_name` 或 `fore_name` 索引:姓名重复率高,查询频率低
- 未创建 `investigator_type` 索引:查询频率低,选择性不高

---

## 📊 表 5: cat_publication_investigator (文献-研究者关联表)

**表说明:** 管理文献与研究者的多对多关系,记录研究者角色和职责
**记录数预估:** 初始 50万 / 年增长 20万 / 5年规模 150万
**主要查询场景:**
1. 查询某文献的所有研究者(>300次/天,中频)
2. 查询某研究者的所有文献(>200次/天,中频)
3. 筛选 PI(主要研究者)文献(100-200次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合唯一索引 |
| investigator_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 研究者ID(外键:cat_investigator.id) | 复合唯一索引 |
| role | VARCHAR | 100 | NULL | NULL | 角色(如"principal","co-investigator","coordinator") | 普通索引 |
| is_contact | BOOLEAN | - | NOT NULL | 0 | 是否联系人(0=否,1=是) | 否 |
| order_num | INT UNSIGNED | - | NULL | NULL | 顺序号(多个研究者时排序) | 否 |
| responsibility | VARCHAR | 1000 | NULL | NULL | 职责描述(文本) | 否 |
| metadata | JSON | - | NULL | NULL | 关联元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL(每个关联必须属于某个文献)
2. **investigator_id**: 外键关联到 cat_investigator.id,NOT NULL(每个关联必须属于某个研究者)
3. **role**: VARCHAR(100),研究者角色,常见值:
   - `principal`: 主要研究者(PI,Principal Investigator)
   - `co-investigator`: 协同研究者(Co-I)
   - `coordinator`: 研究协调员
   - `sponsor`: 资助方代表
   - `collaborator`: 合作者
4. **is_contact**: BOOLEAN,标识是否联系人:
   - 联系人负责项目沟通和协调
   - 通常 PI 是联系人,但也可能是协调员
5. **order_num**: INT UNSIGNED,顺序号:
   - 当有多个研究者时,按重要性排序(1=最重要)
   - 可为 NULL(单一研究者时不需要排序)
6. **responsibility**: VARCHAR(1000),职责描述:
   - 文本字段,描述研究者的具体职责
   - 如 "Responsible for patient recruitment and data collection"
7. **metadata**: JSON 字段,存储扩展信息,如:
   ```json
   {
     "funding_amount": 500000,
     "grant_number": "R01-CA123456",
     "institution_role": "site_lead",
     "subinvestigator_count": 5
   }
   ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_investigator 表不包含完整审计字段?**
- 关联关系创建后通常不再修改(研究团队固定)
- 数据量中等(150万行),精简字段节省存储
- 不涉及高频并发冲突,不需要乐观锁(version)
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_pub_investigator` (`publication_id`, `investigator_id`) COMMENT '防止同一研究者在同一文献重复关联'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有研究者(中频)'
INDEX `idx_investigator` (`investigator_id`) COMMENT '研究者索引,支持查询某研究者的所有文献(中频)'
INDEX `idx_role` (`role`) COMMENT '角色索引,支持按角色筛选(如查询 PI 的所有文献)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_pub_investigator | 0.98 | 极高 ✅ | 组合几乎唯一(极少数重复录入) |
| idx_publication | 0.85 | 高 ✅ | 平均每文献 3 个研究者 |
| idx_investigator | 0.80 | 高 ✅ | 平均每研究者 3 个项目 |
| idx_role | 0.30 | 低 ⚠️ | 5-6 个枚举值,但角色筛选是业务需求 |

**索引设计权衡说明:**
- `uk_pub_investigator` 防止重复关联:同一研究者不能在同一文献出现两次
- `idx_role` 虽然选择性低,但按角色筛选是业务需求(如"查询所有 PI 的文献")
- 未创建 `order_num` 索引:查询频率低,且通常与 publication_id 组合查询(已有 idx_publication)

---

## 📊 表 6: cat_personal_name_subject (人物主题表)

**表说明:** 存储文献的主题人物信息(传记类、历史类、纪念类文献)
**记录数预估:** 初始 2万 / 年增长 0.6万 / 5年规模 5万
**主要查询场景:**
1. 查询某文献的主题人物(>100次/天,中频)
2. 按主题类型筛选(如查询所有传记类文献)(<100次/天,低频)
3. 按姓氏查询历史人物(<50次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 普通索引 |
| last_name | VARCHAR | 200 | NULL | NULL | 姓(Last Name/Family Name) | 普通索引 |
| fore_name | VARCHAR | 200 | NULL | NULL | 名(First Name/Given Name) | 否 |
| initials | VARCHAR | 50 | NULL | NULL | 姓名缩写(如"J.K.") | 否 |
| suffix | VARCHAR | 100 | NULL | NULL | 后缀/头衔(如"Jr.","King","Emperor") | 否 |
| dates | VARCHAR | 100 | NULL | NULL | 生卒年代(如"1820-1910","c. 460 BC - c. 370 BC") | 否 |
| description | VARCHAR | 500 | NULL | NULL | 人物描述(简短介绍) | 否 |
| subject_type | VARCHAR | 50 | NULL | NULL | 主题类型(如"biography","history","memorial") | 普通索引 |
| identifier | VARCHAR | 100 | NULL | NULL | 人物标识符(如 VIAF ID, Wikidata ID) | 否 |
| order_num | INT UNSIGNED | - | NULL | NULL | 顺序号(多个主题人物时排序) | 否 |
| metadata | JSON | - | NULL | NULL | 人物元数据(灵活扩展) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL(每个主题人物必须属于某个文献)
2. **last_name / fore_name**: VARCHAR(200),姓和名:
   - 与 cat_author 表字段设计一致
   - 支持长姓名和历史人物名称(如 "Hippocrates of Kos")
3. **initials**: VARCHAR(50),姓名缩写:
   - 某些历史文献仅提供缩写
4. **suffix**: VARCHAR(100),后缀/头衔:
   - 如 "Louis XIV","Charlemagne the Great"
   - 历史人物的头衔和尊称
5. **dates**: VARCHAR(100),生卒年代:
   - **文本字段,不做日期类型**(原因:历史人物年代不精确)
   - 格式示例:
     - "1820-1910"(现代人物)
     - "c. 460 BC - c. 370 BC"(古代人物,约数)
     - "1564 - April 23, 1616"(部分精确)
     - "fl. 5th century BC"(活跃年代)
6. **description**: VARCHAR(500),人物描述:
   - 简短介绍(如 "French chemist and microbiologist")
   - 用于上下文展示
7. **subject_type**: VARCHAR(50),主题类型,常见值:
   - `biography`: 传记类(如 "The Life of Louis Pasteur")
   - `history`: 历史研究(如 "Hippocrates and Ancient Medicine")
   - `memorial`: 纪念文章(如 "In Memory of Dr. Watson")
   - `case_report`: 病例报告(匿名化患者,如 "Patient A.B.")
8. **identifier**: VARCHAR(100),人物标识符:
   - VIAF ID(Virtual International Authority File,如 "27063124")
   - Wikidata ID(如 "Q529")
   - LCCN(Library of Congress Control Number)
   - 用于关联外部人物数据库
9. **order_num**: INT UNSIGNED,顺序号:
   - 当有多个主题人物时,按重要性排序(1=最重要)
   - 可为 NULL(单一主题人物时不需要排序)
10. **metadata**: JSON 字段,存储扩展信息,如:
    ```json
    {
      "occupation": "Chemist, Microbiologist",
      "nationality": "French",
      "notable_works": ["Germ theory", "Pasteurization"],
      "wikipedia_url": "https://en.wikipedia.org/wiki/Louis_Pasteur"
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_personal_name_subject 表不包含完整审计字段?**
- 主题人物信息创建后通常不再修改(历史人物信息固定)
- 数据量小(5万行),精简字段节省存储
- 不涉及并发冲突,不需要乐观锁(version)
- 仅保留 created_at 记录创建时间即可

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的主题人物(中频)'
INDEX `idx_subject_type` (`subject_type`) COMMENT '主题类型索引,支持按类型筛选(如查询所有传记类文献)'
INDEX `idx_last_name` (`last_name`) COMMENT '姓氏索引,支持按姓氏查询历史人物(低频但有需求)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_publication | 0.90 | 极高 ✅ | 平均每文献 1-2 个主题人物 |
| idx_subject_type | 0.40 | 中 ⚠️ | 4-5 个枚举值,但类型筛选是业务需求 |
| idx_last_name | 0.60 | 中 ✅ | 历史人物姓氏重复率中等 |

**索引设计权衡说明:**
- `idx_subject_type` 虽然选择性不高,但按类型筛选是业务需求(如"查询所有传记类文献")
- `idx_last_name` 支持历史研究场景(如"查询所有关于 Pasteur 的文献")
- 未创建 `identifier` 索引:查询频率极低,且值非常分散

---

## 🎯 特殊设计决策汇总

本节汇总人员机构模块的关键设计决策,详细说明参见 **[ER 图文档](../02-er-diagrams/3-personnel-organization.md) 第四章**。

### 决策 1: 作者顺序唯一性约束

**问题**: 如何防止作者顺序重复或跳跃?

**决定**: 采用数据库级唯一索引 + CHECK 约束

**约束设计**:
```sql
-- 防止同一文献的作者顺序重复
CREATE UNIQUE INDEX uk_author_order
ON cat_publication_author(publication_id, author_order);

-- 防止同一文献的作者重复关联
CREATE UNIQUE INDEX uk_pub_author
ON cat_publication_author(publication_id, author_id);

-- 作者顺序必须从 1 开始
CHECK (author_order > 0)

-- 第一作者检查(author_order=1 必须是 is_first_author=true)
CHECK ((author_order = 1 AND is_first_author = 1) OR author_order > 1)
```

**业务规则**:
1. **顺序唯一性**: 同一文献不能有两个 author_order=1
2. **作者唯一性**: 同一作者不能在同一文献出现两次
3. **第一作者一致性**: author_order=1 必须标记 is_first_author=1
4. **顺序连续性**: 应用层确保 1,2,3... 连续(数据库不强制)

---

### 决策 2: 机构去重策略 - ROR/GRID/标准化名称

**问题**: 机构名称有多种写法,如何有效去重?

**实际数据问题**:
- "Harvard University" vs "Harvard Univ." vs "哈佛大学"
- 机构更名、合并、拆分
- 部门层级不一致

**决定**: 采用分级去重策略,优先级如下:

**去重优先级**:
```
1. ROR ID (如果存在)                → 覆盖率 60%, 准确率 99%
2. GRID ID (如果存在)               → 覆盖率 75%, 准确率 95%
3. 标准化名称 + 国家 + 城市          → 覆盖率 90%, 准确率 85%
4. 仅标准化名称(降级策略)            → 覆盖率 100%, 准确率 70%
```

**实现方式**:
- `dedup_key` VARCHAR(255) 字段,由应用层计算生成
- 定期同步 ROR 数据库更新机构信息
- 保留 `original_name` 便于追溯和验证

**标准化处理**:
```java
// 优先级 1: ROR ID
if (rorId != null) {
    dedupKey = "ROR:" + rorId;
}
// 优先级 2: GRID ID
else if (gridId != null) {
    dedupKey = "GRID:" + gridId;
}
// 优先级 3: 标准化名称
else {
    String normalized = normalizeName(name); // 去除前缀、统一大小写
    dedupKey = MD5(normalized + country + city);
}
```

---

### 决策 3: 作者-机构关联的时间维度

**问题**: 如何表达作者在不同时期的机构归属?

**决定**: 采用时间维度 + 文献上下文的组合策略

**两种关联模式**:

1. **通用关联**(不带 publication_id):
   ```sql
   -- 作者的一般机构归属
   author_id=123, affiliation_id=456, publication_id=NULL,
   start_date='2020-01-01', end_date='2023-12-31'
   ```

2. **特定文献关联**(带 publication_id):
   ```sql
   -- 发表特定文献时的机构
   author_id=123, affiliation_id=789, publication_id=1001,
   start_date=NULL, end_date=NULL
   ```

**使用场景**:
- 查询作者的所有机构历史
- 查询发表某文献时的机构信息
- 统计机构的研究产出
- 追踪作者的职业轨迹

---

### 决策 4: Investigator vs Author 的区别

**问题**: 为什么需要独立的 investigator 表?与 author 有何不同?

**决定**: 研究者(investigator)独立于作者(author)存在

**根本区别**:

| 维度 | Author(作者) | Investigator(研究者) |
|------|-------------|---------------------|
| 定义 | 撰写并发表文献的人员 | 参与研究项目但不一定是论文作者 |
| 典型角色 | 第一作者、通讯作者、共同作者 | PI(主要研究者)、Co-I(协同研究者)、Coordinator |
| 数据来源 | 文献署名 | 临床试验注册、项目申请 |
| 关联方式 | publication_author 表 | publication_investigator 表 |

**实际场景**:
```
临床试验 NCT12345678:
- Principal Investigator: Dr. Smith (可能不是论文作者)
- Co-Investigators: Dr. Johnson, Dr. Lee (可能不是论文作者)
- 文献作者: Dr. Wang(第一作者), Dr. Smith(通讯作者)

在系统中:
- Dr. Smith 既是 investigator 又是 author (两个独立记录)
- Dr. Johnson, Dr. Lee 仅是 investigator
- Dr. Wang 仅是 author
```

**业务价值**:
- 完整记录临床试验团队
- 支持研究者的项目管理
- 区分研究贡献和学术贡献

---

### 决策 5: 审计字段策略优化

**问题**: 不同类型的表应该使用什么级别的审计字段?

**决定**: 采用分层审计策略

**审计字段策略**:

| 表类型 | 审计级别 | 包含字段 | 理由 |
|--------|---------|---------|------|
| **关联表**(publication_author等) | 简化 | created_at | 关联关系静态,很少更新 |
| **主表**(affiliation/investigator) | 简化 | created_at, updated_at | 更新频率低,不需要完整审计 |
| **核心主表**(publication/author) | 完整 | 全部审计字段 + version | 高频更新,需要完整审计 |

**存储成本节省**:
- 关联表(1.4亿行): 节省约 1.12GB (80 字节/行 × 1.4亿)
- 主表(45万行): 节省约 36MB (80 字节/行 × 45万)
- 总计节省: 约 1.16GB

**业务权衡**:
- 关联关系创建后通常不变(作者顺序固定、机构归属固定)
- 即使更新,也可通过 created_at 和应用层日志追溯
- 精简字段显著降低存储成本和写入开销

---

## ✅ 设计检查清单

### 字段设计检查
- [x] 数据类型最优(BIGINT/VARCHAR/DATE/BOOLEAN/JSON 合理选择)
- [x] 长度合理(author_order=INT, affiliation_name=500, dedup_key=255)
- [x] NOT NULL 约束已添加(publication_id, author_id, affiliation_id, dedup_key 等关键字段)
- [x] 默认值合理(is_first_author=0, is_corresponding_author=0, is_primary=0)
- [x] CHECK 约束完整(author_order > 0, end_date >= start_date)
- [x] 无遗漏业务字段(已包含所有 ER 图定义的字段)

### 索引设计检查
- [x] 主键索引已定义(所有表)
- [x] 唯一约束已添加(uk_pub_author, uk_author_order, uk_ror, uk_grid)
- [x] 外键字段已索引(publication_id, author_id, affiliation_id, investigator_id)
- [x] 查询条件字段已索引(is_first_author, is_corresponding_author, country, subject_type)
- [x] 复合索引顺序最优(author_id + is_primary, publication_id + author_order)
- [x] 部分索引优化(ror_id/grid_id/orcid/publication_id 的 WHERE IS NOT NULL)
- [x] 避免过多索引(每表 3-5 个业务索引,总计 23 个索引)

### 性能优化检查
- [x] 高频查询有索引支持(查询作者的文献、查询文献的作者、查询作者的主要机构)
- [x] 唯一性约束防止重复数据(uk_pub_author, uk_author_order, uk_pub_investigator)
- [x] 软删除字段不需要(关联表和主表均不涉及软删除场景)
- [x] JSON 扩展字段已包含(author_metadata, metadata 等)
- [x] 部分索引节省存储(约 40%-80% 空间节省)

### 规范性检查
- [x] 包含标准审计字段(分层策略:关联表简化,主表适度,核心表完整)
- [x] 字段命名符合规范(小写 + 下划线,英文标识符)
- [x] 注释完整清晰(已在索引定义中添加 COMMENT)
- [x] 字符集 UTF8MB4(将在 DDL 中定义)
- [x] 时间字段使用 TIMESTAMP(6)(微秒精度,UTC 时区)

### 数据质量检查
- [x] 唯一性约束(uk_pub_author, uk_author_order, uk_ror, uk_grid, uk_pub_investigator)
- [x] 检查约束(author_order > 0, 第一作者一致性, end_date >= start_date)
- [x] 外键约束(所有 FK 字段,在 DDL 中定义)
- [x] 非空约束(publication_id, author_id, affiliation_id, name, dedup_key 等关键字段)

### 业务逻辑检查
- [x] 作者顺序唯一性策略明确(唯一索引 + CHECK 约束)
- [x] 机构去重策略完整(ROR/GRID 优先 + 标准化名称兜底)
- [x] 时间维度逻辑清晰(start_date/end_date 支持历史追踪)
- [x] 文献上下文支持(publication_id 可选,支持两种关联模式)
- [x] 研究者独立管理(investigator 与 author 职责分离)
- [x] 人物主题场景明确(biography/history/memorial 分类)

---

## 📝 总结

人员机构模块的 6 张表详细设计已完成,关键成果:

### 设计亮点

1. **数据完整性**: 通过唯一索引和 CHECK 约束保证作者顺序正确性,防止数据错误
2. **机构标准化**: 支持 ROR/GRID/ISNI 多种国际标识符,去重准确率 90%+
3. **时间维度**: 作者-机构关联支持历史追踪和特定文献上下文,灵活性高
4. **职责分离**: investigator 与 author 独立管理,支持临床试验等复杂场景
5. **索引优化**: 23 个精心设计的索引,包含部分索引优化,覆盖所有高频查询
6. **审计策略**: 分层审计策略,平衡数据质量和存储成本,节省 1.16GB+

### 数据规模预估(5年)

| 表名 | 5年规模 | 单行大小 | 存储预估 | 索引预估 | 总计 |
|------|---------|---------|---------|---------|------|
| cat_publication_author | 1.4亿行 | 0.4 KB | 56 GB | 22 GB | 78 GB |
| cat_affiliation | 45万行 | 1.2 KB | 540 MB | 180 MB | 720 MB |
| cat_author_affiliation | 1.75亿行 | 0.3 KB | 52.5 GB | 18 GB | 70.5 GB |
| cat_investigator | 50万行 | 0.8 KB | 400 MB | 120 MB | 520 MB |
| cat_publication_investigator | 150万行 | 0.3 KB | 450 MB | 150 MB | 600 MB |
| cat_personal_name_subject | 5万行 | 0.6 KB | 30 MB | 10 MB | 40 MB |
| **总计** | **3.155亿行** | - | **109.9 GB** | **40.5 GB** | **150.4 GB** |

**说明**:
- 单行大小包含业务字段+审计字段
- 索引预估约为数据大小的 30%-40%
- 实际存储需考虑 InnoDB 页填充率(通常 70%-80%)
- 建议预留 2 倍空间(300 GB+)用于索引膨胀和临时表

### 与核心表的协作

人员机构模块与核心表紧密协作:
- `cat_publication_author` 关联 `cat_publication` 和 `cat_author`
- `cat_author_affiliation` 关联 `cat_author` 和 `cat_affiliation`
- `cat_publication_investigator` 关联 `cat_publication` 和 `cat_investigator`
- `cat_personal_name_subject` 关联 `cat_publication`

---

## 下一步

人员机构表详细设计完成,进入 **[阶段 3: SQL DDL 生成](../04-sql-ddl/3-personnel-organization.sql)**

下一阶段将生成:
- 完整的 CREATE TABLE 语句(包含字符集、引擎、注释)
- 索引定义 SQL(包含所有索引和约束)
- CHECK 约束 SQL
- 外键约束 SQL(如果启用外键)
- 表结构验证脚本

---

*本文档是 patra_catalog 数据库人员机构模块的详细表设计,是 SQL DDL 生成的输入基础。*
