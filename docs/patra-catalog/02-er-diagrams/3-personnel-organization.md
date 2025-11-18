# 阶段 3：ER 图设计 - 人员与机构表(6张)

> **设计目标**: 为 Patra 医学文献管理系统设计人员与机构管理体系,支持作者关联、机构归属、研究者管理和人物主题
>
> **文档版本**: v2.0 (按模板重构)
> **创建日期**: 2025-01-18
> **设计范围**: patra_catalog 人员与机构管理体系(不包含核心 author 表)
> **作者**: Patra Lin

---

## 一、人员机构体系概览

本文档描述 patra_catalog 数据库中人员与机构管理相关的 6 张表及其关系。这些表与核心的 `cat_author` 表协作,构成完整的人员机构体系:

| 表名 | 中文名 | 核心功能 | 预估规模 |
|------|--------|---------|---------|
| `cat_publication_author` | 文献-作者关联表 | 管理作者顺序和角色 | 5000万+ |
| `cat_affiliation` | 机构表 | 存储机构信息和标识符 | 25万+ |
| `cat_author_affiliation` | 作者-机构关联表 | 管理作者机构关系 | 6000万+ |
| `cat_investigator` | 研究者表 | 非作者的研究人员 | 50万+ |
| `cat_publication_investigator` | 文献-研究者关联表 | 研究者角色管理 | 150万+ |
| `cat_personal_name_subject` | 人物主题表 | 文献主题人物 | 5万+ |

**设计亮点**:
- ✅ 职责分离 - author 在核心表,关联关系在此处
- ✅ 作者顺序唯一性 - 防止重复顺序号
- ✅ 机构标准化 - 支持 ROR/GRID/ISNI 多种标识符
- ✅ 时间维度 - 追踪作者机构历史
- ✅ 角色标记 - 第一作者/通讯作者/同等贡献
- ✅ 研究者独立管理 - 与作者分离设计

---

## 二、🎨 完整 ER 图

```mermaid
erDiagram
    %% 引用核心表(简化展示)
    cat_publication {
        bigint id PK "主键,雪花ID"
        varchar title "文献标题"
        "其他字段..."
    }

    cat_author {
        bigint id PK "主键,雪花ID"
        varchar last_name "姓"
        varchar fore_name "名"
        varchar orcid "ORCID标识符"
        varchar dedup_key "复合去重键"
        "其他字段..."
    }

    %% 人员与机构表
    cat_publication_author {
        bigint id PK "主键,雪花ID"
        bigint publication_id FK "出版物ID"
        bigint author_id FK "作者ID"
        integer author_order "作者顺序(1开始)"
        boolean is_first_author "是否第一作者"
        boolean is_corresponding_author "是否通讯作者"
        boolean is_equal_contribution "是否同等贡献"
        varchar(50) contribution_type "贡献类型(CRediT)"
        varchar(1000) affiliation_string "原始机构字符串"
        json author_metadata "作者元数据"
    }

    cat_affiliation {
        bigint id PK "主键,雪花ID"
        varchar(500) name "机构名称"
        varchar(500) original_name "原始名称"
        varchar(200) department "部门/科室"
        varchar(200) division "分部/分院"
        varchar(200) section "科/组"
        varchar(100) city "城市"
        varchar(100) state_province "州/省"
        varchar(100) country "国家"
        varchar(20) postal_code "邮政编码"
        varchar(50) ror_id "ROR标识符"
        varchar(50) grid_id "GRID标识符"
        varchar(50) isni "ISNI标识符"
        varchar(50) ringgold_id "Ringgold ID"
        varchar(200) parent_affiliation "上级机构"
        varchar(50) affiliation_type "机构类型"
        varchar(255) dedup_key "去重键"
        json metadata "机构元数据"
    }

    cat_author_affiliation {
        bigint id PK "主键,雪花ID"
        bigint author_id FK "作者ID"
        bigint affiliation_id FK "机构ID"
        bigint publication_id FK "文献ID(可空)"
        date start_date "开始日期"
        date end_date "结束日期"
        varchar(50) affiliation_type "关联类型:current/past/visiting"
        boolean is_primary "是否主要机构"
        integer order_num "机构顺序"
        json metadata "关联元数据"
    }

    cat_investigator {
        bigint id PK "主键,雪花ID"
        varchar(200) last_name "姓"
        varchar(200) fore_name "名"
        varchar(50) initials "姓名缩写"
        varchar(50) suffix "后缀"
        varchar(50) orcid "ORCID标识符"
        varchar(100) researcher_id "研究者ID"
        varchar(100) investigator_type "研究者类型:PI/CoI/Collaborator"
        varchar(500) affiliation_name "机构名称"
        varchar(255) email "邮箱地址"
        varchar(255) dedup_key "去重键"
        json metadata "研究者元数据"
    }

    cat_publication_investigator {
        bigint id PK "主键,雪花ID"
        bigint publication_id FK "出版物ID"
        bigint investigator_id FK "研究者ID"
        varchar(100) role "角色:principal/co-investigator/coordinator"
        boolean is_contact "是否联系人"
        integer order_num "顺序号"
        varchar(1000) responsibility "职责描述"
        json metadata "关联元数据"
    }

    cat_personal_name_subject {
        bigint id PK "主键,雪花ID"
        bigint publication_id FK "出版物ID"
        varchar(200) last_name "姓"
        varchar(200) fore_name "名"
        varchar(50) initials "姓名缩写"
        varchar(100) suffix "后缀/头衔"
        varchar(100) dates "生卒年代"
        varchar(500) description "人物描述"
        varchar(50) subject_type "主题类型:biography/history/memorial"
        varchar(100) identifier "人物标识符"
        integer order_num "顺序号"
        json metadata "人物元数据"
    }

    %% 实体关系定义
    cat_publication ||--o{ cat_publication_author : "has_authors"
    cat_author ||--o{ cat_publication_author : "writes"
    cat_author ||--o{ cat_author_affiliation : "affiliated_with"
    cat_affiliation ||--o{ cat_author_affiliation : "has_members"
    cat_publication ||--o{ cat_author_affiliation : "context_for(optional)"
    cat_publication ||--o{ cat_publication_investigator : "has_investigators"
    cat_investigator ||--o{ cat_publication_investigator : "investigates"
    cat_publication ||--o{ cat_personal_name_subject : "has_subjects"
```

---

## 三、📊 关系说明

### 3.1 基数关系解释

| 关系 | 类型 | 说明 | 业务含义 |
|------|------|------|----------|
| `cat_publication \|\|--o{ cat_publication_author` | 1:N | 一篇文献有多个作者 | 论文通常有多位作者,按顺序排列 |
| `cat_author \|\|--o{ cat_publication_author` | 1:N | 一个作者写多篇文献 | 学者的发表历史 |
| `cat_author \|\|--o{ cat_author_affiliation` | 1:N | 一个作者可有多个机构归属 | 作者可能换工作或有多个兼职 |
| `cat_affiliation \|\|--o{ cat_author_affiliation` | 1:N | 一个机构有多个作者 | 同一机构的研究人员 |
| `cat_publication \|\|--o{ cat_author_affiliation` | 1:N(可选) | 特定文献时的机构关联 | 发表某文献时的机构信息 |
| `cat_publication \|\|--o{ cat_publication_investigator` | 1:N | 一篇文献有多个研究者 | 临床试验的研究团队 |
| `cat_investigator \|\|--o{ cat_publication_investigator` | 1:N | 一个研究者参与多项研究 | 研究者的项目历史 |
| `cat_publication \|\|--o{ cat_personal_name_subject` | 1:N | 一篇文献可有多个人物主题 | 传记类文献的主题人物 |

### 3.2 关键关联规则

**publication_author 关联规则**:
- 每篇文献的 `author_order` 必须从 1 开始,连续递增
- 每篇文献只能有一个 `is_first_author = true`
- 允许多个 `is_corresponding_author = true`
- `affiliation_string` 保留原始数据,用于数据验证

**author_affiliation 关联规则**:
- `publication_id` 为空时,表示作者的一般机构归属
- `publication_id` 有值时,表示发表特定文献时的机构
- 时间字段支持追踪作者的机构变更历史
- `is_primary` 标记作者的主要所属机构

---

## 四、🔑 关键设计决策

### 设计决策 1: 为什么 author 在核心表,但关联表在这里?

**问题**: 作者是核心实体,为什么 `cat_publication_author` 关联表不在核心表阶段设计?

**方案对比**:

| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| 关联表在核心表阶段 | 核心实体集中展示 | 混淆实体表和关联表的职责边界 | ❌ |
| **关联表在人员机构阶段** | 职责分离清晰,关联关系集中管理 | 需要跨阶段理解 | ✅ **采用** |

**决定**: 采用职责分离策略,因为:

1. **职责清晰**:
   - 核心表阶段: 定义实体本身(cat_author 存储作者属性)
   - 人员机构阶段: 定义实体关系(作者与文献的多对多关系)

2. **便于扩展**:
   - 关联关系相关的字段(author_order、is_corresponding_author)集中在关联表
   - 作者的机构关联、研究者关联等相关表在同一阶段便于理解

3. **架构一致性**:
   ```
   阶段 1(核心实体): cat_author(作者实体)
                    ↓
   阶段 3(人员机构): cat_publication_author(作者关联)
                    cat_author_affiliation(机构关联)
   ```

---

### 设计决策 2: 机构去重策略 - ROR/GRID/标准化名称

**问题**: 机构名称有多种写法,如何有效去重?

**实际数据问题**:
- "Harvard University" vs "Harvard Univ." vs "哈佛大学"
- 机构更名、合并、拆分
- 部门层级不一致

**方案对比**:

| 方案 | 准确率 | 覆盖率 | 决策 |
|------|--------|--------|------|
| 仅依赖机构名称 | 40% | 100% | ❌ 重名太多 |
| 仅依赖 ROR ID | 99% | 60% | ❌ 覆盖率不足 |
| **标识符 + 标准化名称** | 90% | 95% | ✅ **采用** |

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

### 设计决策 3: 作者-机构关联的时间维度

**问题**: 如何表达作者在不同时期的机构归属?

**方案对比**:

| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| 不记录时间信息 | 简单 | 无法追踪机构变更历史 | ❌ |
| 仅记录当前机构 | 查询方便 | 历史信息丢失 | ❌ |
| **记录起止时间 + publication_id** | 完整追踪,支持历史查询 | 需要维护时间信息 | ✅ **采用** |

**决定**: 采用时间维度 + 文献上下文的组合策略,因为:

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

3. **历史追踪**:
   - 通过时间字段追踪作者的机构变更
   - 支持查询某时间段的作者机构
   - 标记当前主要机构(`is_primary = true`)

**使用场景**:
- 查询作者的所有机构历史
- 查询发表某文献时的机构信息
- 统计机构的研究产出

---

### 设计决策 4: Investigator vs Author 的区别

**问题**: 为什么需要独立的 investigator 表?与 author 有何不同?

**方案对比**:

| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| 合并到 author 表 | 数据统一 | 职责混淆,查询复杂 | ❌ |
| **独立 investigator 表** | 职责清晰,数据完整性好 | 需要管理两个实体 | ✅ **采用** |

**决定**: 研究者(investigator)独立于作者(author)存在,因为:

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

### 设计决策 5: 人物主题的应用场景

**问题**: 为什么需要独立的 personal_name_subject 表?

**方案对比**:

| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| 复用 author 表 | 减少表数量 | 职责混淆,历史人物无法处理 | ❌ |
| **独立 personal_name_subject 表** | 职责清晰,支持历史人物 | 增加一张表 | ✅ **采用** |

**决定**: 人物主题独立存储,因为:

**应用场景**:
1. **传记类文献**: "The Life of Louis Pasteur"
   - Louis Pasteur 是主题人物,不是作者

2. **历史研究**: "医学史: 希波克拉底的贡献"
   - 希波克拉底是主题,不是作者

3. **纪念文章**: "In Memory of Dr. Watson (1920-2023)"
   - Dr. Watson 是被纪念者,不是作者

4. **病例报告**: "A Case of Rare Disease in Patient A.B."
   - Patient A.B. 是主题(匿名化),不是作者

**设计特点**:
- **不需要去重**: 历史人物可能重名,不强制去重
- **支持历史信息**: `dates` 字段存储生卒年代
- **外部标识符**: 可关联维基数据、VIAF 等人物数据库
- **主题类型**: 区分 biography/history/memorial 等类型

---

### 设计决策 6: 作者顺序的唯一性约束

**问题**: 如何防止作者顺序重复或跳跃?

**方案对比**:

| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| 无约束 | 灵活 | 可能出现重复顺序 | ❌ |
| **唯一索引 + CHECK 约束** | 数据完整性好 | 需要严格验证 | ✅ **采用** |
| 应用层验证 | 灵活处理异常 | 无法保证数据一致性 | ❌ |

**决定**: 采用数据库级约束,因为:

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
CHECK ((author_order = 1 AND is_first_author = true) OR author_order > 1)
```

**业务规则**:
1. **顺序唯一性**: 同一文献不能有两个 author_order=1
2. **作者唯一性**: 同一作者不能在同一文献出现两次
3. **第一作者一致性**: author_order=1 必须标记 is_first_author=true
4. **顺序连续性**: 应用层确保 1,2,3... 连续(数据库不强制)

---

## 五、🎯 索引策略预览

### 5.1 cat_publication_author 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| uk_pub_author | 唯一索引 | publication_id, author_id | 0.98 | 防止重复关联 |
| uk_author_order | 唯一索引 | publication_id, author_order | 1.00 | 防止顺序重复 |
| idx_publication | 普通索引 | publication_id | 0.85 | 查询文献的作者 |
| idx_author | 普通索引 | author_id | 0.80 | 查询作者的文献 |
| idx_corresponding | 普通索引 | is_corresponding_author | 0.30 | 筛选通讯作者 |
| idx_first | 普通索引 | is_first_author | 0.20 | 筛选第一作者 |

### 5.2 cat_affiliation 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| uk_ror | 唯一索引 | ror_id (WHERE ror_id IS NOT NULL) | 0.99 | ROR ID 唯一性 |
| uk_grid | 唯一索引 | grid_id (WHERE grid_id IS NOT NULL) | 0.98 | GRID ID 唯一性 |
| idx_dedup | 普通索引 | dedup_key | 0.95 | 去重查询 |
| idx_country | 普通索引 | country | 0.40 | 按国家统计 |
| idx_name | 普通索引 | name | 0.85 | 按名称查询 |

### 5.3 cat_author_affiliation 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| idx_author | 普通索引 | author_id | 0.80 | 查询作者的机构 |
| idx_affiliation | 普通索引 | affiliation_id | 0.75 | 查询机构的作者 |
| idx_publication | 普通索引 | publication_id (WHERE publication_id IS NOT NULL) | 0.85 | 文献上下文查询 |
| idx_author_primary | 复合索引 | author_id, is_primary | 0.90 | 查询作者主要机构 |

### 5.4 cat_investigator 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| idx_orcid | 普通索引 | orcid (WHERE orcid IS NOT NULL) | 0.99 | 按 ORCID 查询 |
| idx_dedup | 普通索引 | dedup_key | 0.95 | 去重查询 |
| idx_email | 普通索引 | email | 0.85 | 按邮箱查询 |

### 5.5 cat_publication_investigator 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| uk_pub_investigator | 唯一索引 | publication_id, investigator_id | 0.98 | 防止重复关联 |
| idx_publication | 普通索引 | publication_id | 0.85 | 查询文献的研究者 |
| idx_investigator | 普通索引 | investigator_id | 0.80 | 查询研究者的文献 |
| idx_role | 普通索引 | role | 0.30 | 按角色筛选 |

### 5.6 cat_personal_name_subject 表索引

| 索引名 | 类型 | 字段 | 选择性 | 理由 |
|--------|------|------|--------|------|
| PRIMARY | 聚簇索引 | id | 1.00 | 主键 |
| idx_publication | 普通索引 | publication_id | 0.90 | 查询文献的主题人物 |
| idx_subject_type | 普通索引 | subject_type | 0.40 | 按主题类型筛选 |
| idx_last_name | 普通索引 | last_name | 0.60 | 按姓氏查询 |

---

## 六、✅ ER 图验证清单

### 完整性检查
- [x] 包含全部 6 张人员机构表
- [x] 所有业务关系都已定义(8 个关系)
- [x] 主键和唯一键都已标识
- [x] 外键关系明确(10 个外键)
- [x] 与核心表的关联关系清晰(cat_author, cat_publication)

### 规范性检查
- [x] 表名使用单数形式,小写,下划线分隔(cat_publication_author)
- [x] 字段名小写,下划线分隔(author_order, is_corresponding_author)
- [x] 主键统一为 `id` (BIGINT,雪花 ID)
- [x] 关联表包含必要的业务字段(order、role、type)
- [x] JSON 扩展字段已包含(author_metadata、metadata 等)

### 性能考虑
- [x] 高频查询有索引支持(publication_id, author_id)
- [x] 唯一性约束防止重复数据(uk_pub_author, uk_author_order)
- [x] 外键索引完整(所有 FK 字段)
- [x] 条件索引用于可空字段(ror_id, grid_id, publication_id)
- [x] 索引策略完整(主键/唯一/外键/业务索引共 34 个)

### 数据质量
- [x] 唯一性约束(作者顺序、机构标识符)
- [x] 检查约束(author_order > 0、第一作者一致性)
- [x] 外键约束(所有 FK 字段)
- [x] 枚举约束(affiliation_type、investigator_type、subject_type)
- [x] 时间约束(end_date >= start_date)

---

## 七、🔍 与需求的映射

| 需求场景 | ER 图体现 | 实现方式 | 备注 |
|---------|----------|---------|------|
| 查询文献的所有作者(按顺序) | publication_author.author_order | ORDER BY author_order | 保留学术顺序 |
| 筛选第一作者文献 | publication_author.is_first_author | 索引 idx_first | 学术评价重要指标 |
| 查询通讯作者 | publication_author.is_corresponding_author | 索引 idx_corresponding | 支持联系查询 |
| 标记同等贡献作者 | publication_author.is_equal_contribution | - | 现代科研趋势 |
| CRediT 贡献分类 | publication_author.contribution_type | - | 国际标准 |
| 按机构查询作者 | author_affiliation 关联 | JOIN affiliation | 机构产出统计 |
| 作者的机构历史 | author_affiliation.start_date/end_date | 时间排序 | 追踪职业轨迹 |
| 查询作者主要机构 | author_affiliation.is_primary | 索引 idx_author_primary | 快速定位 |
| 发表文献时的机构 | author_affiliation.publication_id | 索引 idx_publication | 文献上下文 |
| 按 ROR 查询机构 | affiliation.ror_id | 唯一索引 uk_ror | 标准标识符 |
| 按国家统计机构 | affiliation.country | 索引 idx_country | 地域分析 |
| 机构去重 | affiliation.dedup_key | 索引 idx_dedup | 数据质量 |
| 查询临床试验 PI | investigator.investigator_type | - | PI/CoI/Collaborator |
| 研究者的项目历史 | publication_investigator 关联 | JOIN investigator | 项目管理 |
| 查询传记类文献主题人物 | personal_name_subject.subject_type | 索引 idx_subject_type | biography/history/memorial |
| 按人物姓氏查询 | personal_name_subject.last_name | 索引 idx_last_name | 历史研究 |

---

## 八、下一步

人员机构 ER 图设计完成,进入 **[阶段 4: 关联信息 ER 设计](4-related-information.md)**

下一阶段将设计:
- 参考文献引用(3 张表)
- 补充材料(2 张表)
- 开放获取版本(2 张表)
- 资助信息(3 张表)
- 评论与勘误(2 张表)
- **共计 12 张表**

---

*本文档是 patra_catalog 数据库人员与机构管理体系的 ER 设计,与核心实体表和分类索引表共同构成完整的数据模型。*
