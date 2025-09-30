# 文档体系结构建议

```
docs/
├─ README.md                 # 文档索引与导航
├─ overview/                 # 全局视角：业务、架构、技术栈
│   ├─ architecture.md
│   ├─ tech-stack.md
│   └─ glossary.md
├─ process/                  # 端到端业务流程 / 作业链路
│   ├─ ingest-dataflow.md
│   ├─ registry-config-lifecycle.md
│   └─ error-handling.md
├─ modules/                  # 按模块拆分的专题文档（deep-dive），不再维护模块 README
│   ├─ ingest/
│   │   └─ deep-dive.md
│   ├─ registry/
│   │   └─ deep-dive.md
│   └─ starters/
│       ├─ core.md
│       └─ feign.md
├─ operations/               # 运维、观测、部署、回放
│   ├─ runbook.md
│   ├─ monitoring.md
│   └─ troubleshooting.md
├─ standards/                # 编码规范、错误码策略、命名约定
│   └─ error-best-practices.md
└─ templates/                # 文档模板（当前目录）
```

> 可按优先级逐步补全，未实现的文档可创建占位符并标注 TODO。
