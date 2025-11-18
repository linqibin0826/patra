# 更改日志 - patra_catalog 数据库设计

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