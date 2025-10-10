# Papertrace 架构文档中心

> 医学文献数据平台 - 技术架构与数据模型文档  
> 最后更新: 2025-10-08

---

## 📚 文档导航

### 🏗️ 系统架构

- **[系统架构总览](./overview/architecture-diagrams.md)**
  - C4 Container 架构图（基础版 & 详细版）
  - 微服务交互序列图（采集任务执行流程）
  - 数据流部署视图（物理拓扑）
  - 技术栈与通信协议说明

### 🔷 模块文档

- **[模块文档索引](./modules/README.md)** - 所有微服务模块文档导航
- **[patra-ingest 六边形架构图](./modules/ingest/architecture-diagram.md)** - 采集引擎微服务
- **[patra-registry 六边形架构图](./modules/registry/architecture-diagram.md)** - 配置中心微服务
- **[patra-egress-gateway 深入指南](./modules/egress-gateway/deep-dive.md)** - 南向网关服务
- **[patra-gateway-boot 深入指南](./modules/gateway/deep-dive.md)** - API 网关服务

### 📋 业务流程

- **[业务流程索引](./process/README.md)** - 核心业务流程文档
- **[采集数据流](./process/ingest-dataflow.md)** - 采集任务执行流程
- **[Outbox 发布流程](./process/outbox-publishing.md)** - 事件发布可靠性保证
- **[Registry 配置生命周期](./process/registry-config-lifecycle.md)** - 配置生效流程

### 💾 数据库设计

- **[数据库文档索引](./database/README.md)** - 数据库设计与迁移文档
- **[核心数据模型 ER 图](./database/er-diagrams.md)** - 完整的表关系图
- **[window_spec JSON Schema](./database/window_spec_schema.md)** - Window boundary specification schema

### 🧬 领域模型

- **[WindowSpec 领域模型](./domain/WindowSpec.md)** - Window boundary specification value object
  - 5种切片策略（TIME, ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE）
  - Format B nested JSON serialization
  - Database virtual column integration

### 📡 API 文档

- **[API 文档索引](./api/README.md)** - API 设计规范与文档
- **[Feign API 设计指南](./standards/feign-api-design-guide.md)** - 内部 RPC 契约设计
- **[跨服务错误处理](./standards/cross-service-error-best-practices.md)** - 错误传播策略

### ⚙️ 运维文档

- **[运维文档索引](./operations/README.md)** - 部署、监控、配置管理
- Docker Compose 本地环境
- Nacos 配置管理
- SkyWalking 追踪
- XXL-Job 调度

### 🧩 Starters 文档

- **[Starters 索引](./starters/README.md)** - 自定义 Spring Boot/Cloud Starters
- patra-spring-boot-starter-core
- patra-spring-boot-starter-web
- patra-spring-boot-starter-mybatis
- patra-spring-cloud-starter-feign

### 📏 开发规范

- **[规范文档索引](./standards/README.md)** - 代码规范、设计模式、最佳实践
- **[平台错误处理规范](./standards/platform-error-handling.md)** - 统一错误模型
- **[日志规范](./standards/logging-convention.md)** - 日志格式与级别

---

## 🎯 快速开始

### 1. 了解系统全貌

```bash
# 推荐阅读顺序
1. docs/overview/architecture-diagrams.md       # 系统整体架构
2. docs/modules/ingest/architecture-diagram.md  # 采集引擎详解
3. docs/modules/registry/architecture-diagram.md # 配置中心详解
4. docs/database/er-diagrams.md                 # 数据模型深入
```

### 2. 渲染 Mermaid 图表

**在线渲染**:
- **Mermaid Live Editor**: https://mermaid.live
- **GitHub/GitLab**: Markdown 原生支持

**VS Code**:
```bash
# 安装插件
# 在 Extensions 中搜索: Markdown Preview Mermaid Support
# 安装后在 Markdown 文件中按 Ctrl+Shift+V 预览
```

**命令行导出**:
```bash
# 安装 Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# 导出为 PNG (透明背景)
mmdc -i docs/overview/architecture-diagrams.md -o architecture.png -b transparent

# 导出为 SVG (矢量图)
mmdc -i docs/modules/ingest/architecture-diagram.md -o ingest-hexagonal.svg

# 导出为 PDF
mmdc -i docs/database/er-diagrams.md -o er-diagrams.pdf
```

### 3. 文档贡献指南

**添加新图表**:
1. 选择合适的 Mermaid 图表类型（flowchart/sequence/erDiagram/C4/classDiagram）
2. 遵循现有命名规范和样式
3. 提供基础版和详细版两个视图
4. 添加图例说明和相关文档链接

**更新现有图表**:
1. 修改 Mermaid 源码
2. 更新文档中的"更新记录"表格
3. 验证渲染效果
4. 提交 PR 并说明变更原因

---

## 📊 图表类型说明

### C4 Container Diagram
- **用途**: 系统容器级架构视图
- **优势**: 清晰展示微服务边界、外部系统、技术栈
- **文件**: `overview/architecture-diagrams.md`

### Hexagonal Architecture Diagram
- **用途**: 六边形架构分层视图
- **优势**: 体现 Ports & Adapters 模式、依赖方向
- **文件**: `modules/*/architecture-diagram.md`

### ER Diagram
- **用途**: 数据库表关系
- **优势**: 展示主外键、索引、约束
- **文件**: `database/er-diagrams.md`

### Sequence Diagram
- **用途**: 时序交互流程
- **优势**: 展示微服务间调用顺序、消息传递
- **文件**: `overview/architecture-diagrams.md`

### Class Diagram
- **用途**: 领域模型与端口接口
- **优势**: 展示聚合根、值对象、端口实现
- **文件**: `modules/*/architecture-diagram.md`

---

## 🎨 样式规范

### 颜色编码

| 颜色 | 含义 | Hex Code |
|-----|------|----------|
| 🔵 蓝色 | Adapter 层（入站/出站适配器）| `#e1f5ff` |
| 🟡 黄色 | Application 层（用例编排） | `#fff3cd` |
| 🟢 绿色 | Domain 层（领域核心）| `#d4edda` |
| 🔴 红色虚线 | Port 接口（端口定义）| `#f8d7da` |
| ⚪ 灰色 | 外部系统 | `#e2e3e5` |
| 🟦 浅蓝 | Infrastructure 层（基础设施）| `#cfe2ff` |

### 命名约定

- **文件名**: `kebab-case.md` (全小写、连字符)
- **Mermaid ID**: `camelCase` (驼峰命名)
- **标题**: 中文标题，英文技术术语保持原文
- **注释**: 英文注释（代码层面），中文说明（文档层面）

---

## 🔗 相关资源

### 项目文档
- [Papertrace 项目 README](../README.md)
- [Agent 协作手册](../AGENTS.md)
- [Claude 开发规范](../CLAUDE.md)
- [任务执行流程](../TaskExecution-Flowchart.md)

### 技术参考
- [Mermaid 官方文档](https://mermaid.js.org/)
- [C4 Model 规范](https://c4model.com/)
- [六边形架构指南](https://alistair.cockburn.us/hexagonal-architecture/)
- [Spring Boot 3.x 文档](https://docs.spring.io/spring-boot/docs/3.2.4/reference/)

### 数据库设计
- [Flyway 迁移脚本](../patra-ingest/patra-ingest-infra/src/main/resources/db/migration/)
- [MyBatis-Plus 文档](https://baomidou.com/)

---

## 📝 更新记录

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.2 | 2025-10-10 | 新增领域模型章节，增加 WindowSpec 和 window_spec Schema 文档 | docs-engineer |
| 1.1 | 2025-10-08 | 新增模块深入指南、完善文档索引结构 | docs-engineer |
| 1.0 | 2025-10-08 | 初始版本：系统架构图、六边形架构图、ER 图 | System |

---

## 🤝 贡献者

感谢以下贡献者对 Papertrace 架构文档的贡献:

- **架构设计**: System
- **图表绘制**: Claude (Mermaid Expert)
- **文档维护**: Development Team

---

## 📄 许可证

本文档遵循项目主仓库的许可证。

---

**💡 提示**: 
- 所有 Mermaid 图表源码直接嵌入 Markdown，无需额外图片文件
- GitHub/GitLab 会自动渲染 Mermaid 代码块
- 建议使用 VS Code + Mermaid 插件进行本地编辑和预览
