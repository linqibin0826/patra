# patra_catalog 数据库设计文档

> 版本：v2.0
> 更新日期：2025-01-18
> 状态：ER 图设计阶段完成 ✅

## 📋 文档导航

### 01. 需求分析阶段

| 文档 | 说明 | 状态 |
|------|------|------|
| [01-requirements-analysis.md](./01-requirements/01-requirements-analysis.md) | 业务需求、数据模型分析、设计决策 | ✅ v1.4 |

### 02. ER 图设计阶段

| 序号 | 文档 | 表数量 | 说明 | 状态 |
|------|------|--------|------|------|
| 01 | [core-entities.md](./02-er-diagrams/01-core-entities.md) | 6张 | 核心实体表（publication、venue、author等） | ✅ v1.5 |
| 02 | [classification-index.md](./02-er-diagrams/02-classification-index.md) | 13张 | 分类与索引表（MeSH、关键词、物质等） | ✅ v1.0 |
| 03 | [personnel-organization.md](./02-er-diagrams/03-personnel-organization.md) | 6张 | 人员与机构表（作者、机构、研究者等） | ✅ v1.0 |
| 04 | [related-information.md](./02-er-diagrams/04-related-information.md) | 7张 | 关联信息表（资助、引用、补充材料等） | ✅ v1.0 |
| 05 | [auxiliary-management.md](./02-er-diagrams/05-auxiliary-management.md) | 5张 | 辅助管理表（日期、元数据、OA位置等） | ✅ v1.0 |
| **总计** | | **42张** | | **100% 完成** |

### 变更记录

- [CHANGELOG.md](./CHANGELOG.md) - 设计变更和版本历史

## 🎯 设计概览

### 数据库架构

```
patra_catalog（42 张表）
├── 核心实体（6张）        - 文献、载体、作者、摘要等
├── 分类索引（13张）       - MeSH、关键词、出版类型、物质
├── 人员机构（6张）        - 作者、机构、研究者关联
├── 关联信息（7张）        - 资助、引用、补充材料、历史
└── 辅助管理（5张）        - 日期、元数据、语言、OA
```

### 设计亮点

1. **性能优化**
   - 策略性字段冗余（pmid、doi、venue_id、publication_year）
   - 预设计索引策略
   - 大表分区建议

2. **数据质量**
   - 完整的约束体系
   - 去重策略（作者、机构、资助）
   - 数据验证机制

3. **灵活扩展**
   - JSON 扩展字段
   - 多态设计模式
   - 动态映射表

### 数据规模预估

- **文献记录**：200万+
- **作者记录**：500万+
- **机构记录**：10万+
- **MeSH 关联**：2000万+
- **参考文献**：4000万+
- **总存储**：约 100GB（含索引）

## 🚀 下一步工作

- [ ] SQL DDL 生成
- [ ] 领域模型映射（Java 实体类）
- [ ] 索引优化实施
- [ ] 数据导入脚本
- [ ] 性能测试

## 📖 快速开始

1. **了解业务需求**：从 [需求分析](./01-requirements/01-requirements-analysis.md) 开始
2. **查看核心表设计**：阅读 [核心实体表](./02-er-diagrams/01-core-entities.md)
3. **理解表关系**：查看各 ER 图的 Mermaid 图表
4. **查看设计决策**：参考每个文档的"设计要点"章节

## 📞 联系方式

**作者**：Patra Lin
**项目**：Patra 医学文献数据平台
**模块**：patra-catalog（目录服务）

---

*本文档体系遵循六边形架构 + DDD 原则，确保数据模型与领域模型的一致性。*