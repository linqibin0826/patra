# patra-ingest-adapter

**角色**: 驱动适配器(六边形架构 - 适配器层)

本模块**仅包含驱动适配器**,用于接收外部触发并将其转换为用例调用。

## 架构契约

- **方向**: 外部世界 → 系统
- **职责**: 接收外部请求、验证输入、委托给应用编排器
- **禁止**: 直接调用外部资源(数据库、外部 API、MQ 发布者) - 这些属于 `patra-ingest-infra`

## 模块分离

在 Papertrace 的六边形架构中:

```
patra-ingest-adapter/     ← 驱动适配器(入站,接收外部触发)
patra-ingest-infra/       ← 被驱动适配器(出站,访问外部资源)
```

这种模块级分离确保驱动适配器和被驱动适配器之间的清晰边界。

## 包组织

```
adapter/
├── scheduler/        - XXL-Job 定时任务
│   ├── config/       - XXL-Job 执行器配置
│   ├── job/          - 定时任务实现
│   └── param/        - 任务参数 DTO
└── stream/          - RocketMQ 消息消费者
    ├── IngestStreamConsumers.java
    └── dto/          - 消息负载 DTO
```

## 命名约定

- **任务**: `*Job`(例如 `PubmedHarvestJob`)
- **消费者**: `*Consumers`(例如 `IngestStreamConsumers`)
- **控制器**: `*Controller`(未来的 REST API)

## 被驱动适配器(出站)

所有被驱动适配器都属于 `patra-ingest-infra`,包括:
- 数据库访问 → `infra/repository/`
- 外部 API 客户端 → `infra/integration/pubmed/`, `infra/integration/registry/`
- MQ 发布者 → `infra/messaging/`
- 未来: Webhook、事件发布者、监控客户端

**切勿向本模块添加被驱动适配器。**

## 相关文档

- 架构: `/docs/ARCHITECTURE.md`
- 开发指南: `/docs/DEV-GUIDE.md`
- Agent 指南: `/.claude/AGENTS-architecture.md`

## 作者

linqibin

## 起始版本

0.1.0
