# Papertrace · Patra Registry 文档体系总览

本目录汇集 Registry 相关的设计与 SQL，已按“字典 → 表达式（Expr）→ 来源配置（Provenance Config）→ SQL 脚本”的脉络组织，支持顺序阅读与交叉跳转。

## 导航（推荐阅读顺序）

- 1) 字典 Schema（统一枚举与编码）
  - 目录索引：`dict/Registry-dict-schema-design.md`
  - 分篇：`dict/Registry-dict-guide.md` / `dict/Registry-dict-reference.md` / `dict/Registry-dict-ops.md`
- 2) Expr Schema（检索表达抽象与渲染）
  - 目录索引：`expr/Registry-expr-schema-design.md`
  - 分篇：`expr/Registry-expr-guide.md` / `expr/Registry-expr-reference.md` / `expr/Registry-expr-usage.md`
- 3) Provenance Config Schema（采集来源配置）
  - 目录索引：`prov-config/Registry-prov-config-schema-design.md`
  - 分篇：`prov-config/Registry-prov-config-guide.md` / `prov-config/Registry-prov-config-reference.md` / `prov-config/Registry-prov-config-ops.md` / `prov-config/Registry-prov-config-examples.md`
- 4) 建表 SQL（整套 DDL + 索引）
  - 文件：`patra-registry.sql`
  - 作用：包含字典、Expr、Provenance Config 等全部表结构定义与索引。

## 关系图（高层）

- 字典（Dict）提供稳定的 `*_code`，被 Expr 与 Provenance Config 共同引用。
- Expr 抽象负责“查询语义 → 参数/模板渲染”。
- Provenance Config 负责“执行合同”（端点/窗口/分页/HTTP/重试/限流/凭证）。
- SQL 脚本是可执行的落地实现（MySQL 8.0）。

## 快速入口

- 字典：`dict/Registry-dict-schema-design.md`
- Expr：`expr/Registry-expr-schema-design.md`
- 来源配置（索引）：`prov-config/Registry-prov-config-schema-design.md`
- 全部 DDL：`patra-registry.sql`

---

提示：各文档首页已统一“体系总览 + 同域导航”；每个子域仅保留数篇高内聚长文，避免碎片化。
