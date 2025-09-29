# Papertrace 文档索引

> 本目录聚合平台架构、流程、模块与运维知识。遵循“就近维护”原则：模块细节以对应 README/专题文档为准，专题内容沉淀于此处。

## 文档分类
- **overview/**：全局视角（业务背景、架构、技术栈、术语表）
- **process/**：端到端业务流程与作业链路
- **modules/**：各服务/组件的深入指南与 SQL/样例
- **operations/**：部署、监控、故障排查与回放
- **standards/**：跨服务规范（错误、命名、编码标准）
- **templates/**：文档模板与写作规范

## 快速入口
- 平台总览：`overview/architecture.md`
- 采集链路指南：`process/ingest-dataflow.md`
- Registry 专题：`modules/registry/README.md`、`modules/registry/deep-dive.md`
- Ingest 深入：`modules/ingest/deep-dive.md`
- 公共模块：`modules/common/deep-dive.md`、`modules/expr-kernel/deep-dive.md`
- Starter 深入：`modules/starters/core.md`、`modules/starters/web.md`、`modules/starters/expr.md`、`modules/starters/mybatis.md`、`modules/starters/rocketmq.md`、`modules/starters/feign.md`
- RocketMQ 规范速览：`modules/starters/rocketmq-usage.md`
- 错误处理总览：`standards/platform-error-handling.md`

## 维护指南
- 新增文档时确认归属目录，并在此索引补充链接
- 文档顶部注明状态（草案/待补充/已验证），提示阅读者可信度
- 引用代码请使用仓库相对路径或固定 commit 链接，避免链接失效
- 模块 README 与专题文档保持双向链接，减少信息孤岛
