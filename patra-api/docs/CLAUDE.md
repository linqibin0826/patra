# docs 目录使用指南

本目录是项目的知识管理中心，使用 Obsidian 管理，采用"文档即代码"理念。

## 核心设计原则

| 原则 | 说明 |
|------|------|
| **原子性** | 一条笔记解决一个问题，避免"上帝对象"式长文档 |
| **强类型** | 使用 YAML Frontmatter 定义笔记"类型"，支持 Dataview 查询 |
| **领域驱动** | 目录基于知识领域（devlog/bugs/learning）而非文件格式 |
| **可观测性** | 通过 Dataview 监控文档健康状态（过期、未决 ADR 等） |

---

## 目录结构

```
docs/
├── devlog/           # 开发日志
│   ├── daily/        # 每日日志
│   ├── weekly/       # 周报
│   └── monthly/      # 月报
├── bugs/             # Bug 记录
│   └── {YYYY}/{MM}/  # 按年月组织
├── learning/         # 详细学习材料（教程式）
│   └── {topic}/      # 按主题组织
├── til/              # 每日学习总结（TIL）
│   └── {YYYY}/{MM}/  # 按年月组织
├── decisions/        # ADR 架构决策记录
├── designs/          # 设计文档（架构设计方案）
│   └── {module}/     # 按模块组织
├── templates/        # 模板文件（参考用，不要修改）
└── _MOC.md           # 主索引（Dataview 仪表盘）
```

## 文档类型说明

| 类型 | 目录 | 触发时机 | 粒度 |
|------|------|----------|------|
| **Learning** | `learning/{topic}/` | 学完一个章节/知识点 | 完整教程 |
| **TIL** | `til/{YYYY}/{MM}/` | 一天学习结束后 | 每日汇总 |
| **Bug** | `bugs/{YYYY}/{MM}/` | 修复非平凡 Bug | 单个问题 |
| **ADR** | `decisions/` | 做出架构决策 | 单个决策 |
| **Design** | `designs/{module}/` | 开发复杂功能前或归档时 | 单个功能/模块 |
| **Devlog** | `devlog/daily/` | 每天开发结束 | 每日记录 |

---

## 文件命名规范

**强制使用 Kebab-case**（短横线命名法，全小写）：

| 命名风格 | 示例 | 推荐度 | 理由 |
|----------|------|--------|------|
| **Kebab-case** | `spring-boot-startup.md` | **强制** | URL 友好、跨平台兼容、CLI 无需转义 |
| PascalCase | `SpringBootStartup.md` | 受限 | 仅用于对应 Java 类名的源码分析笔记 |
| Snake_case | `spring_boot_startup.md` | **禁止** | 下划线可能被解析为斜体标记 |
| 自然语言 | `Spring Boot Startup.md` | **禁止** | 空格导致路径问题 |

**语义化命名**：
- `adr-001-microservice-split.md` - 架构决策（保留编号）
- `bug-redis-connection-timeout.md` - Bug 记录
- `til-2025-11-28.md` - 每日学习

**附件命名**：`{note-name}-{diagram-type}.png`，如 `java-concurrent-hashmap-structure.png`

---

## Dataview 仪表盘

`_MOC.md` 是主索引页，包含多个 Dataview 查询，自动生成：
- 最近更新的文档
- 未解决的 Bug
- 本周学习内容
- 最近开发日志

> [!tip] 扩展仪表盘
> 可在 `_MOC.md` 中添加自定义查询，如追踪过期文档：
> ```dataview
> TABLE file.mtime as "最后更新"
> FROM "learning"
> WHERE (date(today) - file.mtime) > dur(180 days)
> ```

---

## 代码与文档互链

### IDE → Obsidian

在 JavaDoc 中使用 Obsidian URI 链接到设计文档：

```java
///
/// 核心计费逻辑
///
/// 详见设计文档: [ADR-005 Billing Logic](obsidian://open?vault=docs&file=decisions/adr-005-billing-logic)
///
public class BillingService { }
```

### Obsidian → IDE

在 Obsidian 中链接到 Git 仓库源码：

```markdown
源码: [BillingService.java](https://github.com/user/repo/blob/main/src/BillingService.java#L45)
```

---

## 语法规范

文档编写涉及多种语法，按用途分为以下几类：

- **Obsidian 原生语法**：包括内部链接、Callout 提示框、YAML 元数据和 Dataview 查询，详见 @.claude/memories/obsidian.md
- **Mermaid 图表**：用于绘制流程图、时序图、状态图和甘特图，详见 @.claude/memories/mermaid.md
- **Charts 插件**：用于绘制柱状图、折线图、饼图和雷达图等统计图表，详见 @.claude/memories/charts.md
- **D2 声明式图表**：用于绘制复杂架构图、ERD 数据库建模、UML 类图和嵌套容器拓扑，详见 @.claude/memories/d2.md

---

## 注意事项

1. **不要修改 `templates/` 目录下的文件**，它们是模板参考
2. **不要手动修改 `_MOC.md` 文件**，它们包含 Dataview 查询，会自动生成索引
3. **使用中文编写内容**，但文件名使用英文
4. **Learning vs TIL**：Learning 是详细教程（学完一章节保存），TIL 是每日汇总（一天结束后创建）

## 图表规范使用指南

| 需求 | 工具 | 关键要点 |
|------|------|----------|
| 流程图、时序图、状态图 | **Mermaid** | 使用 `flowchart` 而非 `graph`；必须注入初始化指令确保暗色模式兼容 |
| 柱状图、折线图、饼图 | **Charts** | 使用 ` ```chart ` 代码块；必须有 `title` 字段 |
| 复杂架构图、ERD、UML | **D2** | ID 与 Label 分离；使用 `classes` 管理样式，禁止内联硬编码 |

### 工具选型决策

```
简单流程/时序 → Mermaid
统计数据可视化 → Charts
嵌套容器/数据库建模 → D2
```

### 禁止行为

1. **禁止 ASCII 艺术**：不要使用 `┌─┐│└┘▼█▓░` 等字符绘制图表
2. **禁止混用工具**：同一张图不要混合 Mermaid 和 D2 语法
3. **禁止硬编码颜色**：使用 `classes`（D2）或 `classDef`（Mermaid）集中管理样式
