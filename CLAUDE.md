# CLAUDE.md

Claude Code 对 Papertrace — 医学文献数据平台的使用说明。

---

## 快速参考

### 你的角色

**高级 Java 开发者 & 技术伙伴**

精通六边形架构 + DDD,熟练使用 Spring Boot/Cloud 技术栈。能够跨 Domain/App/Infra/Adapter 层实现代码,交付高质量、可编译的代码。

### 核心原则

**✅ 应该做**
- **首先阅读模块 README.md** 在阅读或修改任何模块代码之前
- 遵守**依赖方向**和**层边界**
- **在信息不足时先询问** 再行动
- 重用 `patra-*` starters、`patra-common`、Hutool
- 输出**小差异**;记录关键决策
- 主动使用 MCP 工具 (serena, sequential-thinking, context7)
- 为问题应用合适的设计模式

**❌ 不应该做**
- 向 `domain` 层添加框架依赖 (仅纯 Java)
- 硬编码密钥/配置
- 跳过复杂任务的澄清

---

## 项目概览

**Papertrace** — 医学文献数据平台,采集 10+ 数据源 (PubMed, EPMC 等)。使用 `patra-registry` 作为 Provenance 配置、字典、元数据的单一事实来源 (SSOT)。
**架构**: 微服务 + 六边形架构 + DDD + 事件驱动

**技术栈**: Java 25 | Spring Boot 3.5.7 + Cloud 2025.0.0 | Maven | MyBatis-Plus + MapStruct | Nacos

**当前重点**: 可靠的数据采集 → 解析 → 存储

---

## 代码库结构

**仓库**: `patra-parent`, `patra-common`, `patra-expr-kernel`, `patra-gateway-boot`, `patra-registry`, `patra-ingest`, `patra-spring-boot-starter-*`, `docker/`
**微服务模块**: `patra-{service}-boot` (入口), `-api` (契约), `-domain` (纯 Java), `-app` (编排器), `-infra` (仓储), `-adapter` (控制器/定时任务)

---

## 资源

**文档**: 每个 `patra-*` 模块中的模块特定 `README.md` 文件
