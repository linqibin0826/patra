# 更改日志 - patra_catalog 数据库设计

## 2025-01-18 v2.0

### 里程碑：完成全部 42 张表的 ER 图设计

#### 概述
完成了 patra_catalog 数据库全部 42 张表的详细 ER 图设计，形成了完整的数据模型体系。

#### ER 图文档体系（5个文档）

1. **er-diagram-core-entities.md** (v1.5)
   - 核心实体表（6张）
   - 包含：publication、venue、venue_instance、identifier、author、abstract
   - 特点：标识符冗余、载体二级设计、日期分离存储

2. **er-diagram-classification-index.md** (v1.0)
   - 分类与索引表（13张）
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

- **表总数**：42张
- **预估数据规模**：
  - 文献记录：200万+
  - 作者记录：500万+
  - MeSH关联：2000万+
  - 参考文献：4000万+
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
   - 分类与索引表从 8张 → 13张
   - 总表数从 37张 → 42张
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
- 支持 200万条文献数据