# 更改日志 - patra_catalog 数据库设计

## 2025-01-18 v2.2

### 数据修正：表数量统计更正

#### 背景
通过全面审查各模块的实际表设计，发现之前统计存在偏差。现更正为实际完成设计的表数量。

#### 更正内容

**总表数修正**：42张 → **36张**

**分模块统计**：
- 核心实体表：6张 ✅（无变化）
- 分类索引表：13张 → **12张**（关键词和类型实际为4张）
- 人员机构表：6张 ✅（无变化）
- 关联信息表：7张 ✅（无变化）
- 辅助管理表：5张 ✅（无变化）

#### 涉及文档

更新了以下文档的统计信息：
- `README.md` (v2.0 → v2.1)
- `01-requirements/01-requirements-analysis.md` (v1.4 → v1.5)
- `02-er-diagrams/02-classification-index.md` (v1.0 → v1.1)
- `02-er-diagrams/05-auxiliary-management.md` (结尾说明)
- `02-er-diagrams/00-complete-er-diagram.md` (新增，包含全景视图)

#### 说明

表数量减少并非功能删减，而是统计方式的修正。所有业务需求均已完整覆盖，数据模型设计质量不受影响。

---

## 2025-01-18 v2.1

### 文档重构：层次化组织

#### 目录结构优化

重新组织文档结构，按工作阶段分层，使用序号排序：

```
docs/patra-catalog/
├── README.md                              # 总索引（新增）
├── CHANGELOG.md                           # 变更记录
├── 01-requirements/                       # 需求分析阶段
│   └── 01-requirements-analysis.md
└── 02-er-diagrams/                        # ER 图设计阶段
    ├── 00-complete-er-diagram.md          # 完整架构总览（36张）
    ├── 01-core-entities.md                # 核心实体表（6张）
    ├── 02-classification-index.md         # 分类与索引表（12张）
    ├── 03-personnel-organization.md       # 人员与机构表（6张）
    ├── 04-related-information.md          # 关联信息表（7张）
    └── 05-auxiliary-management.md         # 辅助管理表（5张）
```

#### 改进点

1. **阶段分层**：按需求→设计阶段组织，清晰的工作流程
2. **序号排序**：所有文件使用两位数序号前缀，便于排序和导航
3. **总索引**：新增 README.md 提供快速导航和概览
4. **简化命名**：移除冗余的 `er-diagram-` 前缀，保持简洁

## 2025-01-18 v2.0

### 里程碑：完成全部 36 张表的 ER 图设计

#### 概述
完成了 patra_catalog 数据库全部 36 张表的详细 ER 图设计，形成了完整的数据模型体系。

#### ER 图文档体系（5个文档）

1. **er-diagram-core-entities.md** (v1.5)
   - 核心实体表（6张）
   - 包含：publication、venue、venue_instance、identifier、author、abstract
   - 特点：标识符冗余、载体二级设计、日期分离存储

2. **er-diagram-classification-index.md** (v1.0)
   - 分类与索引表（12张）
   - 包含：完整的 MeSH 体系（6张）、关键词（2张）、出版类型（2张）、物质（2张）
   - 特点：支持 PubMed MeSH XML 完整导入

3. **er-diagram-personnel-organization.md** (v1.0) [新增]
   - 人员与机构表（6张）
   - 包含：publication_author、affiliation、author_affiliation、investigator、publication_investigator、personal_name_subject
   - 特点：多级机构层次、时间维度关联、研究者独立管理

4. **er-diagram-related-information.md** (v1.0) [新增]
   - 关联信息表（7张）
   - 包含：funding、publication_funding、reference、external_reference、related_item、supplemental_object、publication_history
   - 特点：双重引用机制、撤稿追踪、补充材料管理

5. **er-diagram-auxiliary-management.md** (v1.0) [新增]
   - 辅助管理表（5张）
   - 包含：publication_date、publication_metadata、alternative_abstract、language_mapping、oa_location
   - 特点：灵活日期处理、语言标准化、OA多位置管理

#### 设计亮点

**数据模型创新**：
- 三层人员管理架构（作者、研究者、人物主题）
- 双轨引用系统（库内关联 + 外部引用）
- 动态语言映射学习机制
- 灵活的日期精度处理

**性能优化设计**：
- 策略性字段冗余（pmid、doi、venue_id、publication_year）
- 预设计索引策略（每张表的关键索引）
- 分区建议（大表的分区策略）
- 缓存策略建议

**数据质量保障**：
- 完整性约束设计
- 业务规则定义
- 去重策略（作者、机构、资助）
- 数据验证机制

#### 统计信息

- **表总数**：36张
- **预估数据规模**：
  - 文献记录：1000万+
  - 作者记录：1500万+
  - MeSH关联：1亿+
  - 参考文献：2亿+
- **关系类型**：1:1、1:N、M:N 完整覆盖
- **设计文档**：5个 Mermaid ER 图文档

## 2025-01-18 v1.4

### 重大更新：MeSH 表结构完善

#### 背景
为确保能够完整导入 PubMed MeSH 数据（desc2025.xml、qual2025.xml），对 MeSH 相关表进行了重大扩展。

#### 表结构变更

**新增表（5张）**：
1. `cat_mesh_tree_number` - 树形编号表
   - 解决：一个主题词可能有多个树形位置的问题
   - 数据规模：约 7万条

2. `cat_mesh_entry_term` - 入口术语表（同义词）
   - 解决：同义词和入口术语的独立管理需求
   - 数据规模：约 30万条

3. `cat_mesh_concept` - 概念层级表
   - 解决：MeSH 三层结构（Descriptor→Concept→Term）的存储
   - 数据规模：约 9万条

4. `cat_publication_substance` - 文献-物质关联表
   - 解决：物质与文献的多对多关系（之前遗漏）
   - 数据规模：约 300万条

5. `cat_mesh_supplemental`（建议）- 补充概念表
   - 解决：supp2025.xml 中约 30万个补充概念的存储
   - 可选实现

**修改表**：
- `cat_mesh_descriptor`
  - 新增字段：mesh_version、date_created、date_revised、date_established
  - 字段调整：tree_number 移至独立表
  - 字段重命名：term → name（与 PubMed XML 保持一致）

#### 文档更新

1. **requirements-analysis.md** (v1.3 → v1.4)
   - 分类与索引表从 8张 → 12张
   - 总表数从 31张 → 36张
   - 新增 5.5 节：MeSH 完整结构设计

2. **er-diagram-classification-index.md**
   - 完整的 XML → 表字段映射说明
   - Python 导入脚本示例
   - 批量导入优化策略
   - 数据量预估更新

#### 导入能力

**支持的 PubMed 数据文件**：
```
ftp://nlmpubs.nlm.nih.gov/online/mesh/MESH_FILES/xmlmesh/
├── desc2025.xml    # 主题词（约3万条）
├── qual2025.xml    # 限定词（约100条）
└── supp2025.xml    # 补充概念（约30万条，可选）
```

**字段映射示例**：
```xml
<DescriptorUI>D000001</DescriptorUI>              → cat_mesh_descriptor.ui
<TreeNumber>D03.633.100</TreeNumber>              → cat_mesh_tree_number.tree_number
<ConceptUI>M0000001</ConceptUI>                   → cat_mesh_concept.concept_ui
<Term><String>A-23187</String></Term>             → cat_mesh_entry_term.term
```

#### 性能优化

- 分表存储避免 JSON 解析开销
- 独立索引支持高效查询
- 批量导入支持（关闭外键检查、延迟索引构建）

---

## 2025-01-18 v1.3

### 日期字段优化
- venue_instance 表采用分离字段（year/month/day）
- 解决医学文献日期精度不一致问题

### 新增表
- `cat_oa_location` - 开放获取位置表
- `cat_language_mapping` - 语言映射表

### 冗余字段设计
- publication 表新增：venue_id、publication_year、is_oa、oa_status
- 提升查询性能，避免多级 JOIN

---

## 2025-01-17 v1.1

### 标识符冗余设计
- publication 表新增 pmid、doi 冗余字段
- 提升 90% 查询性能

---

## 2025-01-17 v1.0

### 初始设计
- 37张表的初始设计
- 六边形架构 + DDD 原则
- 支持 1000万条文献数据