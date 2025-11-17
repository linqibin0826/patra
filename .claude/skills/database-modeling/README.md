# Database Modeling Skill（MySQL + MyBatis-Plus 版）

## 📖 概述

这是一个专为 **Patra 项目**优化的数据库设计 Skill，采用**渐进式加载**和**动态生成**策略，支持 MySQL 8.0 + MyBatis-Plus 3.5.x + 六边形架构 + DDD。

## 🎯 技术栈

- **数据库**：MySQL 8.0+（InnoDB 引擎）
- **持久化框架**：MyBatis-Plus 3.5.x
- **架构风格**：六边形架构（Hexagonal Architecture）+ DDD
- **Spring Boot**：3.5.7
- **Java 版本**：Java 25
- **ER 图工具**：Mermaid

## 🌟 核心特性

### 1. 渐进式资源加载
- **分阶段设计**：6 个独立阶段（需求分析 → ER 图 → 表设计 → SQL 生成 → 领域模型 → 决策记录）
- **按需加载**：Claude 只在需要时读取相关阶段的文件
- **上下文优化**：从 390 行大文件拆分为多个 50-150 行的小文件

### 2. 动态内容生成
- **SQL DDL 自动生成**：根据表设计自动生成标准化 CREATE TABLE 语句
- **领域模型映射**：生成符合六边形架构的 Domain 层和 Infra 层代码
- **智能模板引擎**：使用模板生成标准化代码

### 3. 技术栈适配
- **纯 Domain 层**：聚合根和实体不包含任何框架注解（JPA/MyBatis）
- **MyBatis-Plus 集成**：Infra 层使用 `@TableName`、`@TableId`、`@TableLogic` 等注解
- **转换器模式**：通过 Converter 实现 Entity ↔ PO 转换

## 📁 文件结构

```
database-modeling/
├── SKILL.md                            # 主入口：智能路由和阶段控制
├── README.md                           # 本文件：使用说明
└── resources/
    ├── stages/                         # 阶段化资源（核心）
    │   ├── stage-0-requirements.md     # 需求分析模板
    │   ├── stage-1-er-design.md        # ER 图设计指南
    │   ├── stage-2-table-details.md    # 表结构设计指南
    │   └── stage-5-decisions.md        # 设计决策记录（ADR）
    │   # stage-3 和 stage-4 动态生成
    ├── templates/                      # 生成模板
    │   ├── sql-ddl-template.sql        # SQL 生成模板
    │   └── domain-model-template.md    # 领域模型模板（MyBatis 版）
    ├── guides/                         # 深度指南（可选引用）
    │   ├── index-optimization-guide.md # 索引优化指南
    │   ├── mermaid-er-examples.md      # Mermaid 示例库
    │   └── standard-audit-fields.sql   # 审计字段规范
    └── examples/                       # 完整示例
        └── patra-publication/          # Patra 出版物管理案例
            ├── 0-requirements.md       # 阶段 0：需求分析
            ├── 1-er-design.md          # 阶段 1：ER 图设计
            ├── 2-table-details.md      # 阶段 2：详细表设计
            ├── 3-sql-ddl.md            # 阶段 3：SQL DDL
            └── 5-decisions.md          # 阶段 5：设计决策记录
```

## 🚀 使用流程

### 场景 1：新项目从零开始

```
用户：我要设计一个电商订单系统的数据库
Claude：[加载 stage-0] 让我们先分析需求...
      → 识别实体：订单、订单项、商品、用户
      → 预估规模：初始 1000 订单/月，年增长 200%
```

### 场景 2：已有需求设计 ER 图

```
用户：帮我设计订单和商品的关系图
Claude：[加载 stage-1] 使用 Mermaid ER 图...
      → 一对多关系：ORDER ||--o{ ORDER_ITEM
      → 多对一关系：ORDER_ITEM }o--|| PRODUCT
```

### 场景 3：生成 SQL DDL

```
用户：我已经设计好表结构，帮我生成 SQL
Claude：[分析设计 → 生成 DDL → 保存到 stage-3-sql-ddl.md]
      → 自动添加审计字段
      → 优化索引定义
      → 生成符合 MyBatis-Plus 的 DDL
```

### 场景 4：生成领域模型

```
用户：帮我生成 DDD 领域模型
Claude：[分析聚合边界 → 生成代码]
      ✅ Domain 层：Publication.java（纯 POJO，无注解）
      ✅ Infra 层：PublicationPO.java（MyBatis 注解）
      ✅ Converter：Entity ↔ PO 转换
      ✅ Repository：接口定义 + 实现
```

## 💡 设计原则

### 1. 分层架构

```
┌─────────────────────────────────────┐
│ Domain 层（纯 Java）                 │
│ ✅ 无框架依赖                        │
│ ✅ 聚合根、实体、值对象              │
│ ✅ 仓储接口（端口）                  │
└─────────────────────────────────────┘
              ↑ 依赖倒置
┌─────────────────────────────────────┐
│ Infra 层（MyBatis-Plus）            │
│ ✅ PO（带 @TableName 等注解）        │
│ ✅ Mapper（BaseMapper）              │
│ ✅ RepositoryImpl（适配器）          │
│ ✅ Converter（Entity ↔ PO）         │
└─────────────────────────────────────┘
```

### 2. 强制规范

| 规范项 | 要求 | 说明 |
|-------|------|------|
| 字符集 | `utf8mb4` | 支持 Emoji 和特殊字符 |
| 排序规则 | `utf8mb4_unicode_ci` | 不区分大小写 |
| 时区 | UTC | 所有 TIMESTAMP 字段 |
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT` | 支持大数据量 |
| 软删除 | `deleted TINYINT(1)` | 配合 `@TableLogic` |
| 乐观锁 | `version BIGINT UNSIGNED` | 配合 `@Version` |
| 审计字段 | 标准 9 个字段 | 见审计字段规范 |

### 3. 命名约定

- **表名**：小写，下划线分隔（`user_profile`）
- **字段名**：小写，下划线分隔（`created_at`）
- **索引名**：`uk_` 唯一索引，`idx_` 普通索引
- **外键字段**：`关联表名_id`（如 `user_id`）

## 📊 性能对比

| 指标 | 优化前 | 优化后 | 改进 |
|------|--------|--------|------|
| 初始加载 | 390 行 | 200 行 | -49% |
| 平均上下文 | 390 行 | 250 行 | -36% |
| 文件数量 | 4 个 | 12 个 | 模块化 |
| 响应精准度 | 中等 | 高 | ⬆️ |
| 维护性 | 低 | 高 | ⬆️ |

## 🛠️ 扩展指南

### 添加新阶段

1. 在 `resources/stages/` 创建新文件
2. 在 `SKILL.md` 添加引用逻辑
3. 更新阶段识别逻辑

### 添加新模板

1. 在 `resources/templates/` 创建模板文件
2. 在动态生成逻辑中引用模板
3. 测试生成效果

## 🎓 最佳实践

### DO ✅
- 保持每个阶段文件在 50-150 行
- 使用清晰的阶段命名
- 在 SKILL.md 中提供快速预览
- 动态生成重复性内容
- Domain 层保持纯 Java（无注解）
- 使用 MyBatis-Plus 的增强功能（`@TableLogic`、`@Version`）

### DON'T ❌
- 避免在 SKILL.md 包含完整内容
- 不要强制加载所有阶段
- 避免循环引用
- 不要在 Domain 层使用 JPA/MyBatis 注解
- 不要硬编码生成逻辑

## 📚 相关资源

### 官方文档
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/)
- [Mermaid ER 图语法](https://mermaid.js.org/syntax/entityRelationshipDiagram.html)

### 架构设计
- [六边形架构（Hexagonal Architecture）](https://alistair.cockburn.us/hexagonal-architecture/)
- [DDD 战术设计](https://www.domainlanguage.com/ddd/)
- [端口与适配器模式](https://herbertograca.com/2017/09/14/ports-adapters-architecture/)

### Patra 项目
- [Patra 出版物管理完整案例](resources/examples/patra-publication/)
  - [阶段 0：需求分析](resources/examples/0-requirements.md)
  - [阶段 1：ER 图设计](resources/examples/1-er-design.md)
  - [阶段 2：详细表设计](resources/examples/2-table-details.md)
  - [阶段 3：SQL DDL](resources/examples/3-sql-ddl.md)
  - [阶段 5：设计决策记录](resources/examples/5-decisions.md)
- [标准审计字段规范](resources/guides/standard-audit-fields.sql)
- [索引优化指南](resources/guides/index-optimization-guide.md)

## 🤝 贡献指南

欢迎改进此 Skill：
1. 优化阶段划分
2. 增强动态生成能力
3. 添加更多模板
4. 改进智能路由逻辑
5. 提供更多 MyBatis-Plus 最佳实践

## 📝 更新日志

### v2.2.0 (2025-01-17)
- ✨ 拆分示例文件：将单一 stage-3-sql-ddl-example.md 拆分为 5 个独立的阶段示例
- 📂 新增 examples/patra-publication/ 目录，包含完整的 Patra 出版物管理案例
- 📝 每个阶段都有独立的示例文件（0-requirements.md, 1-er-design.md, 2-table-details.md, 3-sql-ddl.md, 5-decisions.md）
- 🔧 更新 README.md 文件结构说明和示例链接

### v2.1.0 (2025-01-17)
- 🔧 修正 stage-0 技术栈选项，明确为 MySQL + MyBatis-Plus
- 🔧 修正领域模型模板，移除 JPA 注解
- 📝 更新 README，强调技术栈适配

### v2.0.0 (2025-01-17)
- ✨ 实现渐进式加载
- ✨ 添加动态生成功能
- 📝 重构文件结构
- 🎯 优化上下文使用

### v1.0.0 (2025-01-15)
- 🎉 初始版本
- 📚 基础功能实现

## 📞 支持

如有问题，请：
1. 检查文件路径是否正确
2. 确认 Claude Code 版本 ≥ 1.0
3. 确认项目使用 MySQL 8.0 + MyBatis-Plus 3.5.x
4. 查看 [官方文档](https://docs.claude.com/en/docs/agents-and-tools/agent-skills)

---

**技术栈**：MySQL 8.0 | MyBatis-Plus 3.5.x | 六边形架构 | DDD | Spring Boot 3.5.7 | Java 25

*Optimized for Patra Project with Progressive Disclosure Pattern*
