# patra-storage-adapter

**角色**: 驱动适配器(六边形架构 - 适配器层)

本模块**仅包含驱动适配器**,用于接收外部触发并将其转换为用例调用。

## 架构契约

- **方向**: 外部世界 → 系统
- **职责**: 接收外部请求、验证输入、委托给应用编排器
- **禁止**: 直接调用外部资源(数据库、外部 API、对象存储) - 这些属于 `patra-storage-infra`

## 模块分离

在 Papertrace 的六边形架构中:

```
patra-storage-adapter/     ← 驱动适配器(入站,接收外部触发)
patra-storage-infra/       ← 被驱动适配器(出站,访问外部资源)
```

这种模块级分离确保驱动适配器和被驱动适配器之间的清晰边界。

## 包组织

```
adapter/
└── rest/                 - REST API 端点
    └── internal/         - 微服务间内部 API
        └── StorageEndpointImpl.java
```

### API 受众组织

`rest/` 包可按 API 受众组织:
- **`internal/`**: 微服务间通信(Feign 客户端)
- **`public/`**: 面向外部的公共 API(未来)

这种组织方式清楚地说明了每个 API 的预期消费者。

## 命名约定

- **控制器**: `*EndpointImpl`(REST 端点实现)

## 被驱动适配器(出站)

所有被驱动适配器都属于 `patra-storage-infra`,包括:
- 数据库访问 → `infra/repository/`
- 对象存储(S3/MinIO) → `infra/storage/`
- 外部服务客户端 → `infra/integration/`

**切勿向本模块添加被驱动适配器。**

## 相关文档

- 架构: `/docs/ARCHITECTURE.md`
- 开发指南: `/docs/DEV-GUIDE.md`
- Agent 指南: `/.claude/AGENTS-architecture.md`

## 作者

linqibin

## 起始版本

0.1.0
