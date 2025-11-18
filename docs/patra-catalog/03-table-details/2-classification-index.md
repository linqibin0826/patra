# 阶段 2：详细表设计 - 分类与索引模块(12张表)

> **设计目标**: 详细定义 patra_catalog 数据库分类与索引模块每个表的字段、类型、约束、索引
>
> **创建日期**: 2025-01-18
> **设计范围**: patra_catalog 分类与索引体系(MeSH 6张, 关键词 2张, 出版类型 2张, 物质 2张)
> **作者**: Patra Lin

---

## 📑 模块概览

本文档详细设计 patra_catalog 数据库分类与索引模块的 12 张表。这些表构成了医学文献检索的核心基础。

### 表清单

| 体系 | 表名 | 中文名 | 核心功能 | 预估规模 |
|------|------|--------|---------|---------|
| **MeSH 标引** | `cat_mesh_descriptor` | MeSH 主题词表 | 医学主题词(NLM 标准) | 3.75万 |
| | `cat_mesh_qualifier` | MeSH 限定词表 | 限定词管理 | 100+ |
| | `cat_mesh_tree_number` | 树形编号表 | 多位置层次结构 | 8.5万 |
| | `cat_mesh_entry_term` | 入口术语表 | 同义词管理 | 30万 |
| | `cat_mesh_concept` | MeSH 概念表 | 概念关联 | 20万 |
| | `cat_publication_mesh` | 文献-MeSH 关联表 | 文献标引 | 2.8亿 |
| **关键词** | `cat_keyword` | 关键词表 | 自由关键词管理 | 700万 |
| | `cat_publication_keyword` | 文献-关键词关联表 | 文献关键词 | 7000万 |
| **出版类型** | `cat_publication_type` | 出版类型表 | 文献类型层次 | 150+ |
| | `cat_publication_type_mapping` | 文献-类型关联表 | 文献类型标注 | 1500万 |
| **物质索引** | `cat_substance` | 物质表 | 化学物质管理 | 8万 |
| | `cat_publication_substance` | 文献-物质关联表 | 文献物质标引 | 1500万 |

### 模块设计亮点

- ✅ **MeSH 完整性** - 6 张表完整支持 NLM MeSH 层次结构,保留所有元数据
- ✅ **树形多位置** - 一个主题词平均 2.3 个位置,支持跨学科概念
- ✅ **主/副主题** - `is_major_topic` 对应 MeSH 星号(*)标记,精确筛选
- ✅ **限定词灵活组合** - descriptor + qualifier 分离设计,支持 350万+ 组合
- ✅ **关键词规范化** - `normalized_term` 去重,频次统计支持趋势分析
- ✅ **类型层次递归** - `parent_type` 自引用,支持任意层级查询
- ✅ **物质注册号体系** - CAS 号唯一索引,支持精确检索

---

## 📊 表 1: cat_mesh_descriptor (MeSH 主题词表)

**表说明:** 存储 NLM MeSH(医学主题词表)主题词的核心信息,是医学文献标引的权威词表
**记录数预估:** 初始 3.5万 / 年增长 500 / 5年规模 3.75万
**主要查询场景:**
1. 按 MeSH UI 精确查询(>2000次/天,高频)
2. 按主题词名称查询(>1000次/天,高频)
3. 全文检索 scope_note(100-500次/天,中频)
4. 按版本筛选有效主题词(>500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| ui | VARCHAR | 10 | NOT NULL, UNIQUE | - | MeSH 唯一标识符(格式:D000001-D999999) | 唯一索引 |
| name | VARCHAR | 255 | NOT NULL | - | 主题词名称(首选术语,英文) | 普通索引 |
| descriptor_class | VARCHAR | 50 | NULL | NULL | 主题词类型(枚举:1-Topical/2-PublicationType/3-Geographicals/4-CheckTag) | 否 |
| scope_note | TEXT | - | NULL | NULL | 范围说明(定义和使用指南) | 全文索引 |
| annotation | TEXT | - | NULL | NULL | 注释(索引员使用的说明) | 否 |
| previous_indexing | TEXT | - | NULL | NULL | 之前的索引方式(历史参考) | 否 |
| public_mesh_note | TEXT | - | NULL | NULL | 公共 MeSH 注释(面向用户) | 否 |
| consider_also | TEXT | - | NULL | NULL | 另请参考(相关主题词建议) | 否 |
| date_created | VARCHAR | 10 | NULL | NULL | 创建日期(格式:YYYYMMDD,如 20230115) | 否 |
| date_revised | VARCHAR | 10 | NULL | NULL | 修订日期(格式:YYYYMMDD) | 否 |
| date_established | VARCHAR | 10 | NULL | NULL | 确立日期(格式:YYYYMMDD) | 否 |
| active_status | BOOLEAN | - | NOT NULL | 1 | 是否有效(0=已废弃,1=有效) | 复合索引 |
| mesh_version | VARCHAR | 10 | NULL | NULL | MeSH 版本年份(如"2025") | 复合索引 |
| metadata | JSON | - | NULL | NULL | 其他元数据(扩展字段) | 否 |

**字段设计说明:**
1. **ui**: VARCHAR(10) MeSH 唯一标识符,格式:
   - Descriptor: D000001-D999999(7位数字)
   - 完整格式: "D" + 6位数字(如 D000906)
   - 10 位长度预留变体格式空间
2. **name**: VARCHAR(255) 主题词名称(首选术语),如:
   - "Antibodies"(抗体)
   - "COVID-19"
   - "Neoplasms"(肿瘤)
3. **descriptor_class**: VARCHAR(50) 主题词类型,枚举值:
   - `1`: Topical Descriptor(主题词,约 95%)
   - `2`: Publication Type(出版类型,约 2%)
   - `3`: Geographicals(地理位置,约 2%)
   - `4`: Check Tag(检查标签,约 1%)
4. **scope_note**: TEXT 范围说明,定义主题词的使用范围和指南,如:
   ```
   "Immunoglobulin molecules with antigenic specificity that interact
   with specific antigens. Use for general discussions of antibodies."
   ```
5. **annotation**: TEXT 注释,供索引员使用的内部说明
6. **previous_indexing**: TEXT 之前的索引方式,记录历史变更,如:
   ```
   "Before 2008, this heading was indexed as 'Calcimycin (1975-2007)'"
   ```
7. **public_mesh_note**: TEXT 公共 MeSH 注释,面向最终用户的说明
8. **consider_also**: TEXT 另请参考,推荐相关主题词,如:
   ```
   "See also the molecular formula: ANTIBODIES, BACTERIAL"
   ```
9. **date_created / date_revised / date_established**: VARCHAR(10) 日期字段:
   - 格式: YYYYMMDD(如 "20230115")
   - 使用 VARCHAR 而非 DATE,保留 NLM 原始格式
   - date_created: 首次创建日期
   - date_revised: 最后修订日期
   - date_established: 正式确立日期(进入 MeSH 体系的日期)
10. **active_status**: BOOLEAN 有效状态:
    - 1: 当前有效,可用于标引
    - 0: 已废弃,仅保留历史记录
11. **mesh_version**: VARCHAR(10) MeSH 版本年份(如 "2025"):
    - NLM 每年发布新版本 MeSH(通常在年底)
    - 支持多版本并存(历史版本查询)
12. **metadata**: JSON 扩展元数据,如:
    ```json
    {
      "nlm_classification_number": "WC 500",
      "entry_version": "PUBLIC MESH 2025",
      "frequency_of_use": 12345
    }
    ```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_mesh_descriptor 表不包含完整审计字段?**
- MeSH 主题词表是权威词表,数据来源于 NLM,创建后极少修改
- 版本管理通过 mesh_version + active_status 字段实现
- 不涉及并发冲突,不需要乐观锁(version)
- 简化表结构,节省存储(3.75万行 × 80字节 ≈ 3MB)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_mesh_ui` (`ui`) COMMENT 'MeSH UI 唯一索引,支持高频精确查询(<5ms)'

-- 普通索引
INDEX `idx_name` (`name`) COMMENT '主题词名称索引,支持按名称查询'

-- 复合索引
INDEX `idx_active_version` (`active_status`, `mesh_version`) COMMENT '有效状态+版本复合索引,筛选某版本的有效主题词'

-- 全文索引
FULLTEXT INDEX `ft_name_note` (`name`, `scope_note`) WITH PARSER ngram COMMENT '名称和范围说明全文索引,支持中英文混合检索'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_mesh_ui | 1.00 | 极高 ✅ | MeSH UI 绝对唯一(NLM 标准) |
| idx_name | 0.95 | 极高 ✅ | 主题词名称高度唯一 |
| idx_active_version | 0.80 | 高 ✅ | 组合查询"2025版本的有效主题词" |
| ft_name_note | N/A | 必需 ✅ | 全文检索需求,支持模糊查询 |

**索引设计权衡说明:**
- `uk_mesh_ui` 是最高频查询字段(>90%查询通过 UI 检索)
- `idx_name` 支持按名称精确/模糊查询
- `idx_active_version` 支持版本管理和有效性筛选
- 全文索引使用 ngram 解析器,支持中英文(scope_note 可能包含中文注释)
- 未创建 `descriptor_class` 索引:查询频率低(<100次/天),选择性低(仅 4 个枚举值)

---

## 📊 表 2: cat_mesh_qualifier (MeSH 限定词表)

**表说明:** 存储 MeSH 限定词,用于修饰主题词(如"immunology"限定"Antibodies")
**记录数预估:** 初始 100 / 年增长 5 / 5年规模 125
**主要查询场景:**
1. 按限定词 UI 精确查询(>500次/天,中频)
2. 按限定词名称查询(100-500次/天,中频)
3. 按缩写查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| ui | VARCHAR | 10 | NOT NULL, UNIQUE | - | 限定词唯一标识符(格式:Q000001-Q999999) | 唯一索引 |
| name | VARCHAR | 100 | NOT NULL | - | 限定词名称(英文) | 普通索引 |
| abbreviation | VARCHAR | 10 | NULL | NULL | 限定词缩写(如 DI, GE, IM) | 否 |
| annotation | TEXT | - | NULL | NULL | 注释说明 | 否 |
| date_created | VARCHAR | 10 | NULL | NULL | 创建日期(格式:YYYYMMDD) | 否 |
| date_revised | VARCHAR | 10 | NULL | NULL | 修订日期(格式:YYYYMMDD) | 否 |
| date_established | VARCHAR | 10 | NULL | NULL | 确立日期(格式:YYYYMMDD) | 否 |
| active_status | BOOLEAN | - | NOT NULL | 1 | 是否有效(0=已废弃,1=有效) | 否 |
| mesh_version | VARCHAR | 10 | NULL | NULL | MeSH 版本年份(如"2025") | 否 |

**字段设计说明:**
1. **ui**: VARCHAR(10) 限定词唯一标识符,格式:
   - Qualifier: Q000001-Q999999(7位数字)
   - 完整格式: "Q" + 6位数字(如 Q000276)
2. **name**: VARCHAR(100) 限定词名称(首选术语),如:
   - "immunology"(免疫学)
   - "diagnosis"(诊断)
   - "drug therapy"(药物治疗)
   - 长度 100 足够覆盖所有限定词(最长约 60 字符)
3. **abbreviation**: VARCHAR(10) 限定词缩写,如:
   - "IM" (immunology)
   - "DI" (diagnosis)
   - "DT" (drug therapy)
   - "GE" (genetics)
4. **annotation**: TEXT 注释,使用指南
5. **date_created / date_revised / date_established**: 同 descriptor 表
6. **active_status / mesh_version**: 同 descriptor 表

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_mesh_qualifier 表不包含完整审计字段?**
- 限定词表是权威词表,数据来源于 NLM,创建后极少修改
- 数据量极小(仅 125 条记录),精简字段无显著收益
- 版本管理通过 mesh_version + active_status 字段实现

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_qualifier_ui` (`ui`) COMMENT '限定词 UI 唯一索引,支持精确查询(<5ms)'

-- 普通索引
INDEX `idx_name` (`name`) COMMENT '限定词名称索引,支持按名称查询'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_qualifier_ui | 1.00 | 极高 ✅ | 限定词 UI 绝对唯一(NLM 标准) |
| idx_name | 0.95 | 极高 ✅ | 限定词名称高度唯一(仅 125 个) |

**索引设计权衡说明:**
- 数据量极小(125 条),索引优化收益有限
- 未创建 `abbreviation` 索引:查询频率极低(<50次/天)
- 未创建全文索引:无全文检索需求

---

## 📊 表 3: cat_mesh_tree_number (MeSH 树形编号表)

**表说明:** 存储 MeSH 主题词的树形编号,支持多位置和层次查询(一个主题词平均 2.3 个位置)
**记录数预估:** 初始 8万 / 年增长 1000 / 5年规模 8.5万
**主要查询场景:**
1. 按 descriptor_id 查询某主题词的所有位置(>1000次/天,高频)
2. 按树形编号前缀查询某分支下的所有主题词(>500次/天,中频)
3. 按层级深度查询(100-500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| descriptor_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 主题词ID(外键:cat_mesh_descriptor.id) | 复合索引 |
| tree_number | VARCHAR | 50 | NOT NULL, UNIQUE | - | 树形编号(如 C04.557.337.428) | 唯一索引/前缀索引 |
| tree_level | TINYINT | - | NOT NULL | - | 层级深度(1-10,自动计算) | 复合索引 |
| is_primary | BOOLEAN | - | NOT NULL | 1 | 是否主要位置(0=次要,1=主要) | 否 |

**字段设计说明:**
1. **descriptor_id**: 外键关联到 cat_mesh_descriptor.id,NOT NULL(每个树形编号必须属于某个主题词)
2. **tree_number**: VARCHAR(50) 树形编号,格式:
   - 层级分隔符: "."(点号)
   - 示例: "C04.557.337.428"
     - C04: 一级分类(Neoplasms 肿瘤)
     - C04.557: 二级分类(Neoplasms by Histologic Type 组织学分类肿瘤)
     - C04.557.337: 三级分类(Carcinoma 癌)
     - C04.557.337.428: 四级分类(具体类型)
   - 最长约 40 字符(10 级深度,每级 3-4 位数字)
   - 50 位长度预留足够空间
3. **tree_level**: TINYINT 层级深度,自动计算(点号数量 + 1):
   - "C04" → level=1
   - "C04.557" → level=2
   - "C04.557.337" → level=3
   - "C04.557.337.428" → level=4
   - 最大深度约 10 层,TINYINT(最大 127)足够
4. **is_primary**: BOOLEAN 主/次位置标记:
   - 1: 主要位置(主学科分类)
   - 0: 次要位置(交叉学科)
   - 示例: "Antibodies"(抗体)
     - 主要位置: D12.776.124.486.485(生物化学)
     - 次要位置: D20.215.894.899.600.100(免疫学)

**树形编号实际示例:**
```
"Antibodies"(抗体) 主题词有 2 个树形位置:
1. D12.776.124.486.485 (生物化学分支,is_primary=1)
2. D20.215.894.899.600.100 (免疫学分支,is_primary=0)

"COVID-19" 主题词有 1 个树形位置:
1. C01.925.782.600.550.200.360 (病毒性疾病分支,is_primary=1)
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_mesh_tree_number 表不包含完整审计字段?**
- 树形编号随主题词一起创建,很少单独修改
- 数据来源于 NLM MeSH,权威且稳定
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_tree_number` (`tree_number`) COMMENT '树形编号唯一索引,保证编号唯一性'

-- 普通索引
INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有位置'

-- 前缀索引(层次查询优化)
INDEX `idx_tree_prefix` (`tree_number`(20)) COMMENT '树形编号前缀索引,支持层次查询(LIKE "D12.%")'

-- 复合索引
INDEX `idx_tree_level` (`tree_level`, `descriptor_id`) COMMENT '层级+主题词复合索引,支持按层级筛选'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_tree_number | 1.00 | 极高 ✅ | 树形编号绝对唯一(NLM 标准) |
| idx_descriptor | 0.30 | 中 ⚠️ | 每个主题词平均 2.3 个位置,但业务需求高 |
| idx_tree_prefix | 0.60 | 中 ✅ | 前缀索引支持层次查询(如 "D12.%") |
| idx_tree_level | 0.50 | 中 ✅ | 层级筛选需求(如查询所有 4 级主题词) |

**索引设计权衡说明:**
- `idx_tree_prefix` 前缀索引长度 20:
  - 覆盖前 3-4 级分类(如 "D12.776.124")
  - 支持典型查询: `WHERE tree_number LIKE 'D12.%'`
  - 平衡索引大小和查询性能
- `idx_descriptor` 选择性较低但必需:
  - 查询某主题词的所有位置是核心需求(>1000次/天)
  - 虽然平均只有 2.3 条记录,但查询频率高

**层次查询示例:**
```sql
-- 查询某个分支下的所有子主题词(如 D12.776.* 分支)
SELECT DISTINCT d.* FROM cat_mesh_descriptor d
JOIN cat_mesh_tree_number t ON d.id = t.descriptor_id
WHERE t.tree_number LIKE 'D12.776.%';

-- 查询某主题词的所有上级主题词
SELECT parent.* FROM cat_mesh_descriptor parent
JOIN cat_mesh_tree_number pt ON parent.id = pt.descriptor_id
WHERE pt.tree_number IN (
    'D12',
    'D12.776',
    'D12.776.124'  -- 从子节点逐级向上
);
```

---

## 📊 表 4: cat_mesh_entry_term (MeSH 入口术语表)

**表说明:** 存储 MeSH 主题词的同义词和入口术语,支持模糊检索(如 "A-23187" → "Calcimycin")
**记录数预估:** 初始 25万 / 年增长 1万 / 5年规模 30万
**主要查询场景:**
1. 按 descriptor_id 查询某主题词的所有同义词(>500次/天,中频)
2. 全文检索入口术语(>1000次/天,高频)
3. 按词法标记筛选(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| descriptor_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 主题词ID(外键:cat_mesh_descriptor.id) | 复合索引 |
| term | VARCHAR | 255 | NOT NULL | - | 入口术语/同义词 | 全文索引 |
| lexical_tag | VARCHAR | 10 | NULL | NULL | 词法标记(枚举:NON/PEF/LAB/ABB/ACR/NAM) | 否 |
| is_print_flag | BOOLEAN | - | NOT NULL | 1 | 是否打印(0=否,1=是) | 否 |
| record_preferred | VARCHAR | 10 | NULL | NULL | 记录首选(枚举:Y/N) | 否 |
| is_permuted_term | BOOLEAN | - | NOT NULL | 0 | 是否排列术语(0=否,1=是) | 否 |

**字段设计说明:**
1. **descriptor_id**: 外键关联到 cat_mesh_descriptor.id,NOT NULL(每个入口术语必须属于某个主题词)
2. **term**: VARCHAR(255) 入口术语/同义词,如:
   - 主题词 "Calcimycin" 的同义词:
     - "A-23187"
     - "A23187"
     - "Antibiotic A23187"
     - "Ionophore A23187"
   - 平均长度 30-50 字符,255 足够覆盖极端情况
3. **lexical_tag**: VARCHAR(10) 词法标记,枚举值:
   - `NON`: None(无特殊标记,默认)
   - `PEF`: Preferred Entry Form(首选入口形式)
   - `LAB`: Laboratory Term(实验室术语)
   - `ABB`: Abbreviation(缩写)
   - `ACR`: Acronym(首字母缩写)
   - `NAM`: Proper Name(专有名词)
4. **is_print_flag**: BOOLEAN 是否打印标志:
   - 1: 可以在打印输出中显示
   - 0: 仅供内部检索,不打印
5. **record_preferred**: VARCHAR(10) 记录首选标记:
   - `Y`: 记录首选术语(在同一 descriptor 下的多个 entry term 中首选)
   - `N`: 非首选术语
   - NULL: 无偏好
6. **is_permuted_term**: BOOLEAN 排列术语标志:
   - 1: 术语经过排列(如 "Diabetes Mellitus" → "Mellitus, Diabetes")
   - 0: 原始术语

**入口术语实际示例:**
```
主题词: "Calcimycin" (D000001)
入口术语列表:
1. term="A-23187", lexical_tag="LAB", is_print_flag=1, record_preferred="Y"
2. term="A23187", lexical_tag="LAB", is_print_flag=1, record_preferred="N"
3. term="Antibiotic A23187", lexical_tag="LAB", is_print_flag=1, record_preferred="N"
4. term="Ionophore A23187", lexical_tag="LAB", is_print_flag=1, record_preferred="N"
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_mesh_entry_term 表不包含完整审计字段?**
- 入口术语随主题词一起创建,很少单独修改
- 数据来源于 NLM MeSH,权威且稳定
- 数据量较大(30万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 复合索引
INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有入口术语'

-- 全文索引
FULLTEXT INDEX `ft_term` (`term`) WITH PARSER ngram COMMENT '入口术语全文索引,支持同义词模糊检索'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_descriptor | 0.15 | 低 ⚠️ | 每个主题词平均 8 个入口术语,但业务需求高 |
| ft_term | N/A | 必需 ✅ | 全文检索需求,支持同义词查询 |

**索引设计权衡说明:**
- `idx_descriptor` 选择性低但必需:
  - 查询某主题词的所有同义词是核心需求
  - 虽然平均有 8 条记录,但查询频率高(>500次/天)
- `ft_term` 全文索引是核心功能:
  - 用户通过同义词/别名查询主题词(如 "A-23187" → "Calcimycin")
  - 使用 ngram 解析器支持中文和英文
- 未创建 `lexical_tag` 索引:查询频率极低(<100次/天),选择性不高

**同义词查询示例:**
```sql
-- 用户输入 "A-23187",查询对应的主题词
SELECT d.* FROM cat_mesh_descriptor d
JOIN cat_mesh_entry_term e ON d.id = e.descriptor_id
WHERE MATCH(e.term) AGAINST('A-23187' IN BOOLEAN MODE);

-- 结果: "Calcimycin" (D000001)
```

---

## 📊 表 5: cat_mesh_concept (MeSH 概念表)

**表说明:** 存储 MeSH 主题词下的概念,支持概念级别的关联和检索
**记录数预估:** 初始 18万 / 年增长 5000 / 5年规模 20.5万
**主要查询场景:**
1. 按 descriptor_id 查询某主题词的所有概念(>300次/天,中频)
2. 按 concept_ui 精确查询(<500次/天,中频)
3. 按 registry_number 查询(化学物质,<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| descriptor_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 主题词ID(外键:cat_mesh_descriptor.id) | 复合索引 |
| concept_ui | VARCHAR | 10 | NOT NULL, UNIQUE | - | 概念唯一标识符(格式:M000001-M999999) | 唯一索引 |
| concept_name | VARCHAR | 255 | NOT NULL | - | 概念名称 | 否 |
| is_preferred | BOOLEAN | - | NOT NULL | 0 | 是否首选概念(0=否,1=是) | 否 |
| casn1_name | VARCHAR | 255 | NULL | NULL | CAS 类型 1 名称(化学物质专用) | 否 |
| registry_number | VARCHAR | 50 | NULL | NULL | 注册号(如 CAS 号,EC 号) | 普通索引 |
| scope_note | TEXT | - | NULL | NULL | 范围说明 | 否 |
| concept_status | VARCHAR | 10 | NULL | NULL | 概念状态(枚举值) | 否 |

**字段设计说明:**
1. **descriptor_id**: 外键关联到 cat_mesh_descriptor.id,NOT NULL(每个概念必须属于某个主题词)
2. **concept_ui**: VARCHAR(10) 概念唯一标识符,格式:
   - Concept: M000001-M999999(7位数字)
   - 完整格式: "M" + 6位数字(如 M0000001)
3. **concept_name**: VARCHAR(255) 概念名称,如:
   - 主题词 "Antibodies" 可能有多个概念:
     - "Antibodies (Immunoglobulins)"
     - "Antibodies (Diagnostic Reagents)"
4. **is_preferred**: BOOLEAN 首选概念标志:
   - 1: 首选概念(每个 descriptor 通常有一个首选概念)
   - 0: 非首选概念
5. **casn1_name**: VARCHAR(255) CAS 类型 1 名称:
   - 仅用于化学物质主题词
   - CAS(Chemical Abstracts Service)标准名称
   - 如 "Aspirin" 的 CAS Type 1 名称: "Benzoic acid, 2-(acetyloxy)-"
6. **registry_number**: VARCHAR(50) 注册号,如:
   - CAS 号: "50-78-2"(阿司匹林)
   - EC 号: "1.1.1.1"(酶分类号)
   - 格式可能包含连字符和点号,50 位足够
7. **scope_note**: TEXT 范围说明,定义概念的使用范围
8. **concept_status**: VARCHAR(10) 概念状态,可能的枚举值(待 NLM 文档确认)

**概念层次实际示例:**
```
主题词: "Antibodies" (D000906)
概念列表:
1. concept_ui="M0001259", name="Antibodies", is_preferred=1
   - 这是首选概念,代表抗体的通用含义
2. concept_ui="M0001260", name="Immunoglobulins", is_preferred=0
   - 这是非首选概念,代表免疫球蛋白的特定含义
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_mesh_concept 表不包含完整审计字段?**
- 概念随主题词一起创建,很少单独修改
- 数据来源于 NLM MeSH,权威且稳定
- 数据量较大(20.5万行),精简字段节省存储

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_concept_ui` (`concept_ui`) COMMENT '概念 UI 唯一索引,支持精确查询'

-- 复合索引
INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有概念'

-- 普通索引
INDEX `idx_registry_number` (`registry_number`) COMMENT '注册号索引,支持化学物质查询'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_concept_ui | 1.00 | 极高 ✅ | 概念 UI 绝对唯一(NLM 标准) |
| idx_descriptor | 0.20 | 低 ⚠️ | 每个主题词平均 5 个概念,但业务需求存在 |
| idx_registry_number | 0.80 | 高 ✅ | 注册号高度唯一(仅化学物质有值) |

**索引设计权衡说明:**
- `idx_descriptor` 选择性低但保留:
  - 查询某主题词的所有概念是业务需求
  - 虽然平均有 5 条记录,但数据量不大(20.5万行)
- `idx_registry_number` 支持化学物质查询:
  - 通过 CAS 号查询对应的 MeSH 主题词
  - 覆盖率约 20%(仅化学物质主题词有值)
- 未创建 `concept_name` 索引:查询频率低,通过 descriptor_id 检索即可

---

## 📊 表 6: cat_publication_mesh (文献-MeSH 关联表)

**表说明:** 存储文献的 MeSH 标引,关联文献、主题词、限定词,支持主/副主题标记
**记录数预估:** 初始 2000万 / 年增长 1600万 / 5年规模 2.8亿
**主要查询场景:**
1. 按 publication_id 查询某文献的所有 MeSH(>3000次/天,高频)
2. 按 descriptor_id 查询某主题词的所有文献(>2000次/天,高频)
3. 筛选主要主题(is_major_topic=1)(>1000次/天,高频)
4. 按限定词筛选(>500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合索引 |
| descriptor_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 主题词ID(外键:cat_mesh_descriptor.id) | 复合索引 |
| qualifier_id | BIGINT UNSIGNED | - | NULL, FK | NULL | 限定词ID(外键:cat_mesh_qualifier.id,可选) | 普通索引 |
| is_major_topic | BOOLEAN | - | NOT NULL | 0 | 是否主要主题(0=副主题,1=主要主题,对应 MeSH 星号*) | 复合索引 |
| order_num | INT UNSIGNED | - | NULL | NULL | 顺序号(在同一文献内的排序) | 否 |
| indexing_method | VARCHAR | 50 | NULL | NULL | 标引方法(如 Manual/Automatic) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **descriptor_id**: 外键关联到 cat_mesh_descriptor.id,NOT NULL
3. **qualifier_id**: 外键关联到 cat_mesh_qualifier.id,**允许 NULL**:
   - NULL: 仅主题词,无限定词(如 "COVID-19")
   - 有值: 主题词+限定词组合(如 "Antibodies/immunology")
4. **is_major_topic**: BOOLEAN 主/副主题标记(核心字段):
   - 1: 主要主题(对应 PubMed MeSH 标引的星号 "*")
     - 示例: "Antibodies/immunology*" → is_major_topic=1
   - 0: 副主题(无星号)
     - 示例: "COVID-19/drug therapy" → is_major_topic=0
   - 用途: 筛选文献的核心主题(查准率优化)
5. **order_num**: INT UNSIGNED 顺序号:
   - 记录 MeSH 标引在文献中的排序
   - 通常主要主题排在前面
   - 允许 NULL(部分数据源无顺序信息)
6. **indexing_method**: VARCHAR(50) 标引方法:
   - `Manual`: 人工标引(索引员)
   - `Automatic`: 自动标引(NLM 算法)
   - `Hybrid`: 混合标引
   - 允许 NULL(历史数据可能无此信息)

**MeSH 标引实际示例:**
```
文献 ID: 38123456 (一篇关于抗体免疫学的论文)
MeSH 标引列表:
1. publication_id=38123456, descriptor_id=1(Antibodies),
   qualifier_id=10(immunology), is_major_topic=1, order_num=1
   → 标引: "Antibodies/immunology*" (主要主题)

2. publication_id=38123456, descriptor_id=2(COVID-19),
   qualifier_id=NULL, is_major_topic=1, order_num=2
   → 标引: "COVID-19*" (主要主题)

3. publication_id=38123456, descriptor_id=3(Humans),
   qualifier_id=NULL, is_major_topic=0, order_num=3
   → 标引: "Humans" (副主题,Check Tag)
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_mesh 表不包含完整审计字段?**
- 关联关系随文献一起创建,很少单独修改
- 数据量极大(2.8亿行),精简字段节省存储(节省 2.8亿 × 80字节 ≈ 22GB)
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 复合索引(核心查询)
INDEX `idx_pub_desc` (`publication_id`, `descriptor_id`) COMMENT '文献+主题词复合索引,支持查询文献的MeSH(<20ms)'
INDEX `idx_desc_pub` (`descriptor_id`, `publication_id`) COMMENT '主题词+文献复合索引,支持查询MeSH的文献(<50ms)'
INDEX `idx_major_topic` (`descriptor_id`, `is_major_topic`) COMMENT '主题词+主/副主题复合索引,筛选主要主题文献'

-- 普通索引
INDEX `idx_qualifier` (`qualifier_id`) COMMENT '限定词索引,支持按限定词筛选文献'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_pub_desc | 0.95 | 极高 ✅ | 组合几乎唯一(同一文献同一主题词通常唯一,除非有多个限定词) |
| idx_desc_pub | 0.85 | 高 ✅ | 反向查询,每个主题词平均 7000+ 篇文献 |
| idx_major_topic | 0.75 | 高 ✅ | 筛选主要主题是高频操作(>1000次/天) |
| idx_qualifier | 0.60 | 中 ✅ | 每个限定词平均 200万+ 条记录,但业务需求存在 |

**索引设计权衡说明:**
- `idx_pub_desc` 和 `idx_desc_pub` 是核心索引:
  - 双向查询需求(文献→MeSH,MeSH→文献)
  - 顺序不同,优化不同查询场景
- `idx_major_topic` 支持主题筛选:
  - 典型查询: "查询以'Antibodies'为主要主题的文献"
  - 主要主题约占 30%(0.7亿条 is_major_topic=1)
- `idx_qualifier` 选择性中等但保留:
  - 按限定词筛选是业务需求(如 "查询所有 /immunology 的文献")
- 未创建 `order_num` 索引:仅用于排序,非筛选条件

**典型查询示例:**
```sql
-- 查询某文献的所有主要主题 MeSH
SELECT d.name, q.name AS qualifier
FROM cat_publication_mesh pm
JOIN cat_mesh_descriptor d ON pm.descriptor_id = d.id
LEFT JOIN cat_mesh_qualifier q ON pm.qualifier_id = q.id
WHERE pm.publication_id = 38123456 AND pm.is_major_topic = 1
ORDER BY pm.order_num;

-- 查询以"Antibodies/immunology"为主要主题的所有文献
SELECT p.*
FROM cat_publication p
JOIN cat_publication_mesh pm ON p.id = pm.publication_id
WHERE pm.descriptor_id = 1
  AND pm.qualifier_id = 10
  AND pm.is_major_topic = 1;
```

---

## 📊 表 7: cat_keyword (关键词表)

**表说明:** 存储作者/编辑提供的自由关键词,支持规范化去重和频次统计
**记录数预估:** 初始 300万 / 年增长 100万 / 5年规模 800万
**主要查询场景:**
1. 按规范化关键词查询(去重,>1000次/天,高频)
2. 全文检索关键词(>500次/天,中频)
3. 按频次排序(热门关键词,100-500次/天,中频)
4. 按来源和语言筛选(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| term | VARCHAR | 500 | NOT NULL | - | 关键词原始形式 | 全文索引 |
| source | VARCHAR | 50 | NULL | NULL | 来源(枚举:author/editor/indexer) | 复合索引 |
| language | VARCHAR | 10 | NULL | NULL | 语言代码(ISO 639-1,如 en/zh) | 复合索引 |
| normalized_term | VARCHAR | 255 | NULL | NULL | 规范化词形(小写+去标点+去空格,用于去重) | 普通索引 |
| frequency | INT UNSIGNED | - | NULL | 0 | 出现频次(被多少篇文献使用) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 元数据(扩展字段) | 否 |

**字段设计说明:**
1. **term**: VARCHAR(500) 关键词原始形式,如:
   - "COVID-19"
   - "Machine Learning"
   - "深度学习"(中文关键词)
   - "Cas9-CRISPR"
   - 长度 500 支持长关键词短语
2. **source**: VARCHAR(50) 关键词来源,枚举值:
   - `author`: 作者提供(Author Keyword)
   - `editor`: 编辑添加(Editor Keyword)
   - `indexer`: 索引员标注(Indexer Keyword)
   - `pubmed`: PubMed 自动提取
   - 允许 NULL(历史数据可能无来源信息)
3. **language**: VARCHAR(10) 语言代码(ISO 639-1):
   - `en`: 英语
   - `zh`: 中文
   - `ja`: 日语
   - `de`: 德语
   - 允许 NULL(无法识别语言时)
4. **normalized_term**: VARCHAR(255) 规范化词形,用于去重:
   - 规范化规则(应用层实现):
     - 转小写: "COVID-19" → "covid-19"
     - 去标点: "Cas9-CRISPR" → "cas9crispr"
     - 去空格: "Machine Learning" → "machinelearning"
     - Unicode 规范化: "café" → "cafe"
   - 示例:
     - "COVID-19", "Covid-19", "covid 19" → normalized: "covid19"
     - "Machine Learning", "machine-learning", "Machine_Learning" → normalized: "machinelearning"
5. **frequency**: INT UNSIGNED 出现频次:
   - 记录有多少篇文献使用了该关键词
   - 定期更新(如每日批量计算)
   - 用于热门关键词排行和趋势分析
6. **metadata**: JSON 扩展元数据,如:
   ```json
   {
     "first_appeared": "2020-01-15",
     "peak_year": 2023,
     "related_mesh_ui": ["D000086382"],
     "category": "emerging_topic"
   }
   ```

**关键词规范化实际示例:**
```
原始关键词变体:
1. term="COVID-19", normalized_term="covid19", frequency=15000
2. term="Covid-19", normalized_term="covid19" → 合并到第 1 条
3. term="covid 19", normalized_term="covid19" → 合并到第 1 条

应用层插入逻辑:
1. 计算 normalized_term = "covid19"
2. 查询是否已存在 normalized_term="covid19"
3. 如存在,更新 frequency += 1,不插入新记录
4. 如不存在,插入新记录
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |
| updated_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 更新时间(UTC,微秒精度,频次更新时触发) |

**为什么 cat_keyword 表包含 updated_at?**
- frequency 字段需要定期更新(批量计算频次)
- updated_at 记录最后一次频次更新时间
- 不需要完整审计字段(无 version,无 created_by/updated_by)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 普通索引
INDEX `idx_normalized` (`normalized_term`) COMMENT '规范化词形索引,支持去重查询'
INDEX `idx_frequency` (`frequency` DESC) COMMENT '频次索引,支持热门关键词排序'

-- 复合索引
INDEX `idx_source_lang` (`source`, `language`) COMMENT '来源+语言复合索引,支持按来源和语言筛选'

-- 全文索引
FULLTEXT INDEX `ft_term` (`term`) WITH PARSER ngram COMMENT '关键词全文索引,支持中英文混合检索'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_normalized | 0.90 | 极高 ✅ | 规范化后高度唯一(去重目标) |
| idx_frequency | 0.70 | 高 ✅ | 降序索引,支持热门关键词排序 |
| idx_source_lang | 0.60 | 中 ✅ | 组合查询"作者提供的英文关键词" |
| ft_term | N/A | 必需 ✅ | 全文检索需求,支持模糊查询 |

**索引设计权衡说明:**
- `idx_normalized` 是去重核心索引:
  - 插入前查询 normalized_term 是否存在
  - 高频操作(每篇文献平均 2-3 个关键词)
- `idx_frequency` 降序索引:
  - 支持 `ORDER BY frequency DESC` 高效排序
  - 热门关键词排行榜查询
- `idx_source_lang` 复合索引:
  - 支持筛选"作者提供的中文关键词"
  - 虽然查询频率低,但数据分析需求存在
- 全文索引使用 ngram 解析器:
  - 支持中文分词(如 "深度学习" → "深度" "学习")
  - 支持英文模糊查询

**典型查询示例:**
```sql
-- 查询热门关键词 Top 100
SELECT term, frequency, source, language
FROM cat_keyword
WHERE source = 'author'
ORDER BY frequency DESC
LIMIT 100;

-- 去重查询(插入前检查)
SELECT id, frequency FROM cat_keyword
WHERE normalized_term = 'covid19';

-- 全文检索关键词
SELECT * FROM cat_keyword
WHERE MATCH(term) AGAINST('machine learning' IN BOOLEAN MODE);
```

---

## 📊 表 8: cat_publication_keyword (文献-关键词关联表)

**表说明:** 存储文献的关键词标注,关联文献和关键词,支持主/副关键词标记
**记录数预估:** 初始 1500万 / 年增长 1400万 / 5年规模 8500万
**主要查询场景:**
1. 按 publication_id 查询某文献的所有关键词(>2000次/天,高频)
2. 按 keyword_id 查询某关键词的所有文献(>1000次/天,高频)
3. 筛选主要关键词(is_major=1)(>500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合索引 |
| keyword_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 关键词ID(外键:cat_keyword.id) | 复合索引 |
| is_major | BOOLEAN | - | NOT NULL | 0 | 是否主要关键词(0=副关键词,1=主要关键词) | 复合索引 |
| order_num | INT UNSIGNED | - | NULL | NULL | 顺序号(在同一文献内的排序) | 否 |
| keyword_set | VARCHAR | 50 | NULL | NULL | 关键词集(如 Author/Editor,区分不同来源) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **keyword_id**: 外键关联到 cat_keyword.id,NOT NULL
3. **is_major**: BOOLEAN 主/副关键词标记:
   - 1: 主要关键词(作者标注的核心关键词)
   - 0: 副关键词(次要关键词)
   - 部分期刊要求作者标注 3-5 个主要关键词
4. **order_num**: INT UNSIGNED 顺序号:
   - 记录关键词在文献中的排序
   - 通常主要关键词排在前面
   - 允许 NULL(部分数据源无顺序信息)
5. **keyword_set**: VARCHAR(50) 关键词集,区分不同来源:
   - `author`: 作者关键词集
   - `editor`: 编辑关键词集
   - `pubmed`: PubMed 自动提取关键词集
   - 允许 NULL(无明确来源时)

**关键词标注实际示例:**
```
文献 ID: 38123456 (一篇机器学习论文)
关键词列表:
1. publication_id=38123456, keyword_id=100(Machine Learning),
   is_major=1, order_num=1, keyword_set='author'
   → 主要关键词: "Machine Learning"

2. publication_id=38123456, keyword_id=101(Deep Learning),
   is_major=1, order_num=2, keyword_set='author'
   → 主要关键词: "Deep Learning"

3. publication_id=38123456, keyword_id=102(Neural Networks),
   is_major=0, order_num=3, keyword_set='author'
   → 副关键词: "Neural Networks"
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_keyword 表不包含完整审计字段?**
- 关联关系随文献一起创建,很少单独修改
- 数据量极大(8500万行),精简字段节省存储(节省 8500万 × 80字节 ≈ 6.8GB)
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 复合索引(核心查询)
INDEX `idx_pub_keyword` (`publication_id`, `keyword_id`) COMMENT '文献+关键词复合索引,支持查询文献的关键词(<20ms)'
INDEX `idx_keyword_pub` (`keyword_id`, `publication_id`) COMMENT '关键词+文献复合索引,支持查询关键词的文献(<50ms)'
INDEX `idx_major` (`keyword_id`, `is_major`) COMMENT '关键词+主/副标记复合索引,筛选主要关键词文献'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_pub_keyword | 0.95 | 极高 ✅ | 组合几乎唯一(同一文献同一关键词唯一) |
| idx_keyword_pub | 0.90 | 极高 ✅ | 反向查询,每个关键词平均 10+ 篇文献 |
| idx_major | 0.80 | 高 ✅ | 筛选主要关键词是业务需求 |

**索引设计权衡说明:**
- `idx_pub_keyword` 和 `idx_keyword_pub` 是核心索引:
  - 双向查询需求(文献→关键词,关键词→文献)
  - 顺序不同,优化不同查询场景
- `idx_major` 支持主要关键词筛选:
  - 典型查询: "查询以'Machine Learning'为主要关键词的文献"
  - 主要关键词约占 40%(3400万条 is_major=1)
- 未创建 `order_num` 索引:仅用于排序,非筛选条件
- 未创建 `keyword_set` 索引:查询频率极低(<50次/天)

**典型查询示例:**
```sql
-- 查询某文献的所有主要关键词
SELECT k.term, pk.order_num
FROM cat_publication_keyword pk
JOIN cat_keyword k ON pk.keyword_id = k.id
WHERE pk.publication_id = 38123456 AND pk.is_major = 1
ORDER BY pk.order_num;

-- 查询以"Machine Learning"为主要关键词的文献
SELECT p.*
FROM cat_publication p
JOIN cat_publication_keyword pk ON p.id = pk.publication_id
WHERE pk.keyword_id = 100 AND pk.is_major = 1;
```

---

## 📊 表 9: cat_publication_type (出版类型表)

**表说明:** 存储文献出版类型(如期刊文章、综述、临床试验),支持层次结构
**记录数预估:** 初始 120 / 年增长 5 / 5年规模 145
**主要查询场景:**
1. 按 type_code 精确查询(>500次/天,中频)
2. 按 parent_type 查询子类型(递归查询,100-500次/天,中频)
3. 按类型名称查询(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| type_code | VARCHAR | 100 | NOT NULL, UNIQUE | - | 类型代码(英文,唯一标识) | 唯一索引 |
| type_name | VARCHAR | 200 | NOT NULL | - | 类型名称(英文) | 否 |
| description | VARCHAR | 500 | NULL | NULL | 描述说明 | 否 |
| vocabulary_source | VARCHAR | 50 | NULL | NULL | 词表来源(如 MEDLINE/EMBASE/CUSTOM) | 否 |
| parent_type | VARCHAR | 100 | NULL | NULL | 父类型代码(自引用,支持层次) | 普通索引 |
| is_active | BOOLEAN | - | NOT NULL | 1 | 是否有效(0=已废弃,1=有效) | 普通索引 |
| metadata | JSON | - | NULL | NULL | 元数据(扩展字段) | 否 |

**字段设计说明:**
1. **type_code**: VARCHAR(100) 类型代码(唯一标识),如:
   - "Journal Article"(期刊文章)
   - "Review"(综述)
   - "Randomized Controlled Trial"(随机对照试验)
   - "Meta-Analysis"(荟萃分析)
   - 使用英文标准名称,100 位足够
2. **type_name**: VARCHAR(200) 类型名称(英文),如:
   - "Randomized Controlled Trial"
   - "Systematic Review"
   - 名称可能比代码稍长(包含完整描述)
3. **description**: VARCHAR(500) 描述说明,如:
   ```
   "A work consisting of a clinical trial that involves at least one test
   treatment and one control treatment, concurrent enrollment and follow-up
   of the test- and control-treated groups..."
   ```
4. **vocabulary_source**: VARCHAR(50) 词表来源,如:
   - `MEDLINE`: PubMed/MEDLINE 标准
   - `EMBASE`: Embase 标准
   - `CUSTOM`: 自定义类型
   - 允许 NULL(默认为 MEDLINE)
5. **parent_type**: VARCHAR(100) 父类型代码,支持层次结构:
   - 自引用字段,关联到 type_code
   - NULL: 顶级类型(无父类型)
   - 有值: 子类型(如 "Systematic Review" 的父类型是 "Review")
6. **is_active**: BOOLEAN 有效状态:
   - 1: 当前有效,可用于分类
   - 0: 已废弃,仅保留历史记录
7. **metadata**: JSON 扩展元数据,如:
   ```json
   {
     "medline_abbreviation": "RCT",
     "legacy_codes": ["D016449"],
     "usage_frequency": 12345
   }
   ```

**类型层次实际示例:**
```
Journal Article (顶级类型,parent_type=NULL)
├── Clinical Trial (parent_type='Journal Article')
│   ├── Randomized Controlled Trial (parent_type='Clinical Trial')
│   └── Controlled Clinical Trial (parent_type='Clinical Trial')
├── Review (parent_type='Journal Article')
│   ├── Systematic Review (parent_type='Review')
│   └── Meta-Analysis (parent_type='Review')
└── Case Reports (parent_type='Journal Article')
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_type 表不包含完整审计字段?**
- 出版类型表是权威词表,数据来源于 MEDLINE/EMBASE,创建后极少修改
- 数据量极小(145 条记录),精简字段无显著收益
- 版本管理通过 is_active 字段实现

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_type_code` (`type_code`) COMMENT '类型代码唯一索引,支持精确查询(<5ms)'

-- 普通索引
INDEX `idx_parent` (`parent_type`) COMMENT '父类型索引,支持查询子类型(递归查询)'
INDEX `idx_active` (`is_active`) COMMENT '有效状态索引,筛选有效类型'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_type_code | 1.00 | 极高 ✅ | 类型代码绝对唯一 |
| idx_parent | 0.30 | 中 ⚠️ | 每个父类型平均 3-5 个子类型,但递归查询需要 |
| idx_active | 0.20 | 低 ⚠️ | 仅两值(0/1),但筛选有效类型是基本需求 |

**索引设计权衡说明:**
- `idx_parent` 选择性低但必需:
  - 递归查询子类型需要索引
  - 虽然数据量小(145 条),但业务逻辑依赖
- `idx_active` 选择性极低但保留:
  - 筛选有效类型是基本需求
  - 数据量小,索引成本可忽略
- 未创建 `type_name` 索引:查询频率低,通过 type_code 检索即可

**递归查询示例:**
```sql
-- 查询"Review"类型及其所有子类型
WITH RECURSIVE type_hierarchy AS (
    -- 锚点: 综述类型
    SELECT id, type_code, type_name, parent_type
    FROM cat_publication_type
    WHERE type_code = 'Review'

    UNION ALL

    -- 递归: 子类型
    SELECT t.id, t.type_code, t.type_name, t.parent_type
    FROM cat_publication_type t
    JOIN type_hierarchy h ON t.parent_type = h.type_code
)
SELECT * FROM type_hierarchy;

-- 结果:
-- 1. Review
-- 2. Systematic Review (parent: Review)
-- 3. Meta-Analysis (parent: Review)
```

---

## 📊 表 10: cat_publication_type_mapping (文献-类型关联表)

**表说明:** 存储文献的出版类型标注,关联文献和类型(一篇文献可有多个类型)
**记录数预估:** 初始 300万 / 年增长 300万 / 5年规模 1800万
**主要查询场景:**
1. 按 publication_id 查询某文献的所有类型(>1500次/天,高频)
2. 按 type_id 查询某类型的所有文献(>1000次/天,高频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合索引 |
| type_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 类型ID(外键:cat_publication_type.id) | 复合索引 |
| order_num | INT UNSIGNED | - | NULL | NULL | 顺序号(在同一文献内的排序) | 否 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **type_id**: 外键关联到 cat_publication_type.id,NOT NULL
3. **order_num**: INT UNSIGNED 顺序号:
   - 记录类型在文献中的排序
   - 通常主要类型排在前面(如 "Journal Article" 优先于 "Review")
   - 允许 NULL(部分数据源无顺序信息)

**类型标注实际示例:**
```
文献 ID: 38123456 (一篇随机对照试验的系统综述)
类型列表:
1. publication_id=38123456, type_id=1(Journal Article), order_num=1
2. publication_id=38123456, type_id=5(Review), order_num=2
3. publication_id=38123456, type_id=8(Systematic Review), order_num=3
4. publication_id=38123456, type_id=15(Randomized Controlled Trial), order_num=4

说明: 一篇文献可以同时是"期刊文章"、"综述"、"系统综述"和"随机对照试验"
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_type_mapping 表不包含完整审计字段?**
- 关联关系随文献一起创建,很少单独修改
- 数据量大(1800万行),精简字段节省存储(节省 1800万 × 80字节 ≈ 1.44GB)
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 复合索引(核心查询)
INDEX `idx_pub_type` (`publication_id`, `type_id`) COMMENT '文献+类型复合索引,支持查询文献的类型(<20ms)'
INDEX `idx_type_pub` (`type_id`, `publication_id`) COMMENT '类型+文献复合索引,支持查询类型的文献(<50ms)'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_pub_type | 0.95 | 极高 ✅ | 组合几乎唯一(同一文献同一类型唯一) |
| idx_type_pub | 0.90 | 极高 ✅ | 反向查询,每个类型平均 12万+ 篇文献 |

**索引设计权衡说明:**
- `idx_pub_type` 和 `idx_type_pub` 是核心索引:
  - 双向查询需求(文献→类型,类型→文献)
  - 顺序不同,优化不同查询场景
- 未创建 `order_num` 索引:仅用于排序,非筛选条件

**典型查询示例:**
```sql
-- 查询某文献的所有类型(按顺序)
SELECT t.type_name, ptm.order_num
FROM cat_publication_type_mapping ptm
JOIN cat_publication_type t ON ptm.type_id = t.id
WHERE ptm.publication_id = 38123456
ORDER BY ptm.order_num;

-- 查询所有"系统综述"类型的文献
SELECT p.*
FROM cat_publication p
JOIN cat_publication_type_mapping ptm ON p.id = ptm.publication_id
WHERE ptm.type_id = 8;  -- 8 = Systematic Review
```

---

## 📊 表 11: cat_substance (物质表)

**表说明:** 存储化学物质、药物、生物制品等信息,支持 CAS 号等注册号检索
**记录数预估:** 初始 7万 / 年增长 2000 / 5年规模 7.7万
**主要查询场景:**
1. 按 registry_number 精确查询(>500次/天,中频)
2. 按物质名称查询(>300次/天,中频)
3. 按物质分类筛选(100-500次/天,中频)
4. 全文检索同义词(<100次/天,低频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| registry_number | VARCHAR | 100 | NOT NULL, UNIQUE | - | 注册号(如 CAS 号,格式:50-78-2) | 唯一索引 |
| name | VARCHAR | 500 | NOT NULL | - | 物质名称(英文) | 普通索引 |
| vocabulary_id | VARCHAR | 100 | NULL | NULL | 词表ID(外部词表标识) | 否 |
| vocabulary_source | VARCHAR | 50 | NULL | NULL | 词表来源(如 CAS/EC/UNII) | 否 |
| substance_class | VARCHAR | 50 | NULL | NULL | 物质分类(枚举:chemical/drug/biological/enzyme) | 普通索引 |
| molecular_formula | VARCHAR | 200 | NULL | NULL | 分子式(如 C9H8O4) | 否 |
| synonyms | JSON | - | NULL | NULL | 同义词列表(JSON 数组) | 全文索引 |
| metadata | JSON | - | NULL | NULL | 元数据(扩展字段) | 否 |

**字段设计说明:**
1. **registry_number**: VARCHAR(100) 注册号(唯一标识),如:
   - CAS 号: "50-78-2"(阿司匹林)
   - EC 号: "1.1.1.1"(酶分类号)
   - UNII: "R16CO5Y76E"(FDA 唯一成分标识符)
   - "0": 非特定物质或物质类(特殊值)
   - 格式多样,100 位足够覆盖
2. **name**: VARCHAR(500) 物质名称(英文),如:
   - "Aspirin"(阿司匹林)
   - "Acetylsalicylic Acid"(乙酰水杨酸)
   - "Immunoglobulin G"(免疫球蛋白 G)
   - 长度 500 支持长化学名称
3. **vocabulary_id**: VARCHAR(100) 词表ID:
   - 外部词表的内部标识符
   - 允许 NULL(无外部词表时)
4. **vocabulary_source**: VARCHAR(50) 词表来源,枚举值:
   - `CAS`: Chemical Abstracts Service(化学文摘服务)
   - `EC`: Enzyme Commission(酶委员会)
   - `UNII`: Unique Ingredient Identifier(FDA 唯一成分标识符)
   - `ChEBI`: Chemical Entities of Biological Interest
   - `PubChem`: NCBI PubChem 数据库
5. **substance_class**: VARCHAR(50) 物质分类,枚举值:
   - `chemical`: 化学物质
   - `drug`: 药物
   - `biological`: 生物制品
   - `enzyme`: 酶
   - `antibody`: 抗体
   - `protein`: 蛋白质
6. **molecular_formula**: VARCHAR(200) 分子式,如:
   - "C9H8O4"(阿司匹林)
   - "C21H27NO"(吗啡)
   - 允许 NULL(非化学物质或未知)
7. **synonyms**: JSON 同义词列表(多语言),如:
   ```json
   {
     "en": ["Aspirin", "Acetylsalicylic Acid", "ASA"],
     "zh": ["阿司匹林", "乙酰水杨酸"],
     "de": ["Acetylsalicylsäure"]
   }
   ```
8. **metadata**: JSON 扩展元数据,如:
   ```json
   {
     "molecular_weight": 180.158,
     "smiles": "CC(=O)OC1=CC=CC=C1C(=O)O",
     "inchi": "InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)",
     "pubchem_cid": 2244
   }
   ```

**物质信息实际示例:**
```
物质: Aspirin (阿司匹林)
registry_number: "50-78-2" (CAS 号)
name: "Aspirin"
vocabulary_source: "CAS"
substance_class: "drug"
molecular_formula: "C9H8O4"
synonyms: {
  "en": ["Aspirin", "Acetylsalicylic Acid", "ASA", "Acetysal"],
  "zh": ["阿司匹林", "乙酰水杨酸"],
  "de": ["Acetylsalicylsäure"]
}
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_substance 表不包含完整审计字段?**
- 物质信息相对静态,数据来源于权威词表(CAS/EC/UNII),创建后极少修改
- 数据量中等(7.7万行),精简字段节省存储
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 唯一索引
UNIQUE INDEX `uk_registry` (`registry_number`) COMMENT '注册号唯一索引,支持 CAS 号精确查询(<5ms)'

-- 普通索引
INDEX `idx_name` (`name`) COMMENT '物质名称索引,支持按名称查询'
INDEX `idx_class` (`substance_class`) COMMENT '物质分类索引,支持按分类筛选'

-- 全文索引(JSON 字段)
FULLTEXT INDEX `ft_name_synonyms` (`name`, `synonyms`) WITH PARSER ngram COMMENT '名称和同义词全文索引,支持多语言检索'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| uk_registry | 0.99 | 极高 ✅ | 注册号几乎唯一(除 "0" 外) |
| idx_name | 0.85 | 高 ✅ | 物质名称高度唯一 |
| idx_class | 0.30 | 低 ⚠️ | 仅 6 个枚举值,但业务需求存在 |
| ft_name_synonyms | N/A | 必需 ✅ | 全文检索需求,支持同义词查询 |

**索引设计权衡说明:**
- `uk_registry` 是最高频查询字段:
  - CAS 号查询是化学物质检索的标准方式
  - 唯一索引保证数据完整性
- `idx_name` 支持按名称查询:
  - 物质名称高度唯一(如 "Aspirin")
- `idx_class` 选择性低但保留:
  - 按分类筛选是业务需求(如 "查询所有药物")
  - 数据量不大(7.7万行),索引成本可接受
- `ft_name_synonyms` 全文索引:
  - 支持同义词检索(如 "乙酰水杨酸" → "Aspirin")
  - JSON 字段全文索引需要 MySQL 5.7.8+
- 未创建 `molecular_formula` 索引:查询频率极低(<50次/天)

**典型查询示例:**
```sql
-- 通过 CAS 号查询物质
SELECT * FROM cat_substance
WHERE registry_number = '50-78-2';

-- 通过同义词查询物质(全文检索)
SELECT * FROM cat_substance
WHERE MATCH(name, synonyms) AGAINST('乙酰水杨酸' IN BOOLEAN MODE);

-- 查询所有药物类物质
SELECT * FROM cat_substance
WHERE substance_class = 'drug';
```

---

## 📊 表 12: cat_publication_substance (文献-物质关联表)

**表说明:** 存储文献涉及的化学物质标注,关联文献和物质,支持主/副物质标记和角色
**记录数预估:** 初始 300万 / 年增长 300万 / 5年规模 1800万
**主要查询场景:**
1. 按 publication_id 查询某文献的所有物质(>1000次/天,高频)
2. 按 substance_id 查询某物质的所有文献(>500次/天,中频)
3. 筛选主要物质(is_major=1)(>300次/天,中频)
4. 按物质角色筛选(100-500次/天,中频)

### 业务字段设计

| 字段名 | 类型 | 长度 | 约束 | 默认值 | 说明 | 索引需求 |
|--------|------|------|------|--------|------|----------|
| id | BIGINT UNSIGNED | - | PK, AUTO_INCREMENT | - | 主键,雪花算法生成 | 聚簇索引 |
| publication_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 出版物ID(外键:cat_publication.id) | 复合索引 |
| substance_id | BIGINT UNSIGNED | - | NOT NULL, FK | - | 物质ID(外键:cat_substance.id) | 复合索引 |
| is_major | BOOLEAN | - | NOT NULL | 0 | 是否主要物质(0=副物质,1=主要物质) | 复合索引 |
| role | VARCHAR | 100 | NULL | NULL | 物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect) | 复合索引 |

**字段设计说明:**
1. **publication_id**: 外键关联到 cat_publication.id,NOT NULL
2. **substance_id**: 外键关联到 cat_substance.id,NOT NULL
3. **is_major**: BOOLEAN 主/副物质标记:
   - 1: 主要物质(研究的核心物质)
   - 0: 副物质(次要涉及的物质)
4. **role**: VARCHAR(100) 物质角色,枚举值:
   - `therapeutic`: 治疗用途(治疗药物)
   - `diagnostic`: 诊断用途(诊断试剂)
   - `research_tool`: 研究工具(实验试剂)
   - `adverse_effect`: 不良反应(引起副作用的物质)
   - `target`: 靶点(药物作用靶点)
   - `metabolite`: 代谢产物
   - 允许 NULL(无明确角色时)

**物质标注实际示例:**
```
文献 ID: 38123456 (一篇关于阿司匹林治疗心血管疾病的论文)
物质列表:
1. publication_id=38123456, substance_id=100(Aspirin),
   is_major=1, role='therapeutic'
   → 主要物质: "Aspirin",角色: 治疗药物

2. publication_id=38123456, substance_id=101(Cyclooxygenase),
   is_major=0, role='target'
   → 副物质: "Cyclooxygenase"(环氧化酶),角色: 药物靶点

3. publication_id=38123456, substance_id=102(Salicylic Acid),
   is_major=0, role='metabolite'
   → 副物质: "Salicylic Acid"(水杨酸),角色: 代谢产物
```

### 审计字段(简化)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| created_at | TIMESTAMP(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 创建时间(UTC,微秒精度) |

**为什么 cat_publication_substance 表不包含完整审计字段?**
- 关联关系随文献一起创建,很少单独修改
- 数据量大(1800万行),精简字段节省存储(节省 1800万 × 80字节 ≈ 1.44GB)
- 不涉及并发冲突,不需要乐观锁(version)

### 索引定义

```sql
-- 主键索引(自动创建)
PRIMARY KEY (`id`) COMMENT '主键聚簇索引'

-- 复合索引(核心查询)
INDEX `idx_pub_substance` (`publication_id`, `substance_id`) COMMENT '文献+物质复合索引,支持查询文献的物质(<20ms)'
INDEX `idx_substance_pub` (`substance_id`, `publication_id`) COMMENT '物质+文献复合索引,支持查询物质的文献(<50ms)'
INDEX `idx_major_role` (`substance_id`, `is_major`, `role`) COMMENT '物质+主/副标记+角色复合索引,支持多条件筛选'
```

**索引选择性分析:**

| 索引名 | 选择性 | 评级 | 说明 |
|-------|--------|------|------|
| idx_pub_substance | 0.95 | 极高 ✅ | 组合几乎唯一(同一文献同一物质通常唯一,除非有多个角色) |
| idx_substance_pub | 0.90 | 极高 ✅ | 反向查询,每个物质平均 200+ 篇文献 |
| idx_major_role | 0.85 | 高 ✅ | 三字段组合,支持复杂筛选(如"治疗用途的主要物质") |

**索引设计权衡说明:**
- `idx_pub_substance` 和 `idx_substance_pub` 是核心索引:
  - 双向查询需求(文献→物质,物质→文献)
  - 顺序不同,优化不同查询场景
- `idx_major_role` 三字段复合索引:
  - 支持复杂筛选(如 "查询以'Aspirin'为主要治疗药物的文献")
  - 最左前缀原则:支持 `substance_id`、`substance_id + is_major`、`substance_id + is_major + role` 三种查询
  - 虽然 role 选择性中等,但业务需求存在
- 未创建单独的 `role` 索引:已包含在复合索引中,无需重复

**典型查询示例:**
```sql
-- 查询某文献的所有主要物质
SELECT s.name, ps.role
FROM cat_publication_substance ps
JOIN cat_substance s ON ps.substance_id = s.id
WHERE ps.publication_id = 38123456 AND ps.is_major = 1;

-- 查询以"Aspirin"为治疗药物的所有文献
SELECT p.*
FROM cat_publication p
JOIN cat_publication_substance ps ON p.id = ps.publication_id
WHERE ps.substance_id = 100
  AND ps.role = 'therapeutic'
  AND ps.is_major = 1;

-- 查询涉及"Aspirin"的所有文献(任意角色)
SELECT p.*
FROM cat_publication p
JOIN cat_publication_substance ps ON p.id = ps.publication_id
WHERE ps.substance_id = 100;
```

---

## 🎯 设计检查清单

### 字段设计检查
- [x] 数据类型最优(BIGINT/VARCHAR/TEXT/JSON/BOOLEAN/TINYINT/SMALLINT 合理选择)
- [x] 长度合理(ui=10, tree_number=50, term=255/500, registry_number=100)
- [x] NOT NULL 约束已添加(ui, name, tree_number, is_major_topic 等关键字段)
- [x] 默认值合理(active_status=1, is_major_topic=0, frequency=0)
- [x] CHECK 约束完整(descriptor_class 枚举, lexical_tag 枚举, source 枚举)
- [x] 无遗漏业务字段(已包含所有 ER 图定义的字段)

### 索引设计检查
- [x] 主键索引已定义(所有表)
- [x] 唯一约束已添加(ui, concept_ui, tree_number, type_code, registry_number)
- [x] 外键字段已索引(descriptor_id, qualifier_id, publication_id, keyword_id, type_id, substance_id)
- [x] 查询条件字段已索引(normalized_term, frequency, is_major_topic, is_major)
- [x] 复合索引顺序最优(publication_id + descriptor_id, descriptor_id + is_major_topic)
- [x] 避免过多索引(每表 3-5 个业务索引,总计 47 个索引)

### 性能优化检查
- [x] 全文索引已添加(name/scope_note, term, plain_text, name/synonyms)
- [x] 前缀索引优化(tree_number 层次查询)
- [x] 降序索引优化(frequency DESC)
- [x] JSON 扩展字段已包含(metadata, synonyms, structured_sections)
- [x] 冗余字段策略明确(无冗余,MeSH 体系保持数据完整性)

### 规范性检查
- [x] 包含标准审计字段(主表简化,关联表仅 created_at)
- [x] 字段命名符合规范(小写 + 下划线,英文标识符)
- [x] 注释完整清晰(已在索引定义中添加 COMMENT)
- [x] 字符集 UTF8MB4(将在 DDL 中定义)
- [x] 时间字段使用 TIMESTAMP(6)(微秒精度,UTC 时区)

### 数据质量检查
- [x] 唯一性约束(ui, concept_ui, tree_number, type_code, registry_number)
- [x] 检查约束(descriptor_class 枚举, lexical_tag 枚举, source 枚举, substance_class 枚举)
- [x] 外键约束(所有 FK 字段,在 DDL 中定义)
- [x] 非空约束(ui, name, tree_number, term, type_code, registry_number 等关键字段)
- [x] 可选外键(qualifier_id 允许 NULL)

### 业务逻辑检查
- [x] MeSH 标引完整性(6 张表完整支持 NLM MeSH 结构)
- [x] 树形多位置设计(tree_number 表支持平均 2.3 个位置)
- [x] 主/副主题标记(is_major_topic 对应 MeSH 星号)
- [x] 限定词灵活组合(descriptor + qualifier 分离,qualifier_id 可为 NULL)
- [x] 关键词规范化去重(normalized_term + frequency 字段)
- [x] 出版类型层次递归(parent_type 自引用,支持任意层级)
- [x] 物质注册号体系(registry_number 唯一索引,支持 CAS/EC/UNII)
- [x] 同义词多语言支持(JSON 字段存储多语言同义词)

---

## 📝 总结

分类与索引模块的 12 张表详细设计已完成,关键成果:

### 设计亮点

1. **MeSH 完整性**: 6 张表完整支持 NLM MeSH 层次结构,保留所有元数据(descriptor, qualifier, tree_number, entry_term, concept, publication_mesh)
2. **树形多位置**: tree_number 表支持一个主题词平均 2.3 个位置,支持跨学科概念(如 "Antibodies" 在生物化学和免疫学)
3. **主/副主题**: is_major_topic 字段对应 MeSH 星号(*),精确筛选文献核心主题
4. **限定词灵活组合**: descriptor + qualifier 分离设计,支持 350万+ 组合(3.5万主题词 × 100 限定词)
5. **关键词规范化**: normalized_term 去重,frequency 统计,支持热门关键词排行和趋势分析
6. **类型层次递归**: parent_type 自引用,支持任意层级查询(如 "Review" → "Systematic Review" → "Meta-Analysis")
7. **物质注册号体系**: registry_number 唯一索引,支持 CAS 号精确检索
8. **同义词多语言**: JSON 字段存储多语言同义词,支持全文检索

### 数据规模预估(5年)

| 体系 | 表数量 | 5年规模 | 单行大小 | 存储预估 | 索引预估 | 总计 |
|------|--------|---------|---------|---------|---------|------|
| **MeSH 主表** | 3张 | 45.5万行 | 0.5-1.2 KB | 350 MB | 150 MB | 500 MB |
| **MeSH 关联** | 1张 | 2.8亿行 | 0.2 KB | 56 GB | 18 GB | 74 GB |
| **关键词** | 2张 | 8500万行 | 0.3-0.8 KB | 30 GB | 10 GB | 40 GB |
| **出版类型** | 2张 | 1800万行 | 0.2 KB | 3.6 GB | 1.2 GB | 4.8 GB |
| **物质** | 2张 | 1807万行 | 0.3-0.6 KB | 8 GB | 3 GB | 11 GB |
| **总计** | **12张** | **3.97亿行** | - | **98 GB** | **32.4 GB** | **130.3 GB** |

**说明**:
- MeSH 主表: descriptor(3.75万) + qualifier(125) + tree_number(8.5万) + entry_term(30万) + concept(20.5万) = 45.5万行
- MeSH 关联: publication_mesh(2.8亿行,数据量最大的表)
- 关键词: keyword(800万) + publication_keyword(7700万) = 8500万行
- 出版类型: publication_type(145) + publication_type_mapping(1800万) = 1800万行
- 物质: substance(7.7万) + publication_substance(1800万) = 1807万行
- 单行大小包含业务字段+审计字段
- 索引预估约为数据大小的 30%-35%
- 实际存储需考虑 InnoDB 页填充率(通常 70%-80%)
- 建议预留 2 倍空间(260 GB+)用于索引膨胀和临时表

### 性能优化策略

1. **MeSH UI 唯一索引**: 支持高频精确查询(<5ms)
2. **树形编号前缀索引**: tree_number(20) 支持层次查询(LIKE 'D12.%')
3. **全文索引**: name/scope_note, term, synonyms 支持中英文混合检索
4. **复合索引优化**: (publication_id + descriptor_id), (descriptor_id + is_major_topic) 支持高频双向查询
5. **频次降序索引**: frequency DESC 支持热门关键词排序
6. **注册号唯一索引**: registry_number 支持 CAS 号精确查询

### 审计策略

- **主表**(descriptor/qualifier/keyword/publication_type/substance): 仅 created_at(数据静态,极少更新)
- **关联表**(publication_mesh/publication_keyword/publication_type_mapping/publication_substance): 仅 created_at(随文献创建,不再修改)
- **原因**: 词表数据相对静态,很少更新;关联表记录不涉及并发冲突;精简字段节省存储(节省约 30GB)

---

## 下一步

分类与索引表详细设计完成,进入 **[阶段 3: SQL DDL 生成](../04-sql-ddl/2-classification-index.sql)**

下一阶段将生成:
- 完整的 CREATE TABLE 语句(包含字符集、引擎、注释)
- 索引定义 SQL(包含所有索引和约束)
- 外键约束 SQL(如果启用外键)
- 表结构验证脚本

---

*本文档是 patra_catalog 数据库分类与索引模块的详细表设计,是医学文献检索的核心基础。*
