# patra-registry-adapter

**角色**: 驱动适配器(六边形架构 - 适配器层)

本模块**仅包含驱动适配器**,用于接收外部触发并将其转换为用例调用。

## 架构契约

- **方向**: 外部世界 → 系统
- **职责**: 接收外部请求、验证输入、委托给应用编排器
- **禁止**: 直接调用外部资源(数据库、外部 API、MQ 发布者) - 这些属于 `patra-registry-infra`

## 模块分离

在 Papertrace 的六边形架构中:

```
patra-registry-adapter/     ← 驱动适配器(入站,接收外部触发)
patra-registry-infra/       ← 被驱动适配器(出站,访问外部资源)
```

这种模块级分离确保驱动适配器和被驱动适配器之间的清晰边界。

## 包组织

```
adapter/
└── rest/               - REST API 端点
    ├── ProvenanceEndpointImpl.java    - 数据源管理 API
    ├── ExprEndpointImpl.java          - 表达式编译 API
    └── converter/                     - API DTO 转换器
```

## 命名约定

- **控制器**: `*EndpointImpl`(REST 端点实现)
- **转换器**: `*ApiConverter`(API DTO 转换)

## 被驱动适配器(出站)

所有被驱动适配器都属于 `patra-registry-infra`,包括:
- 数据库访问 → `infra/repository/`
- 外部 API 客户端 → `infra/integration/`
- 缓存访问 → `infra/cache/`

**切勿向本模块添加被驱动适配器。**

## 相关文档

- 架构: `/docs/ARCHITECTURE.md`
- 开发指南: `/docs/DEV-GUIDE.md`
- Agent 指南: `/.claude/AGENTS-architecture.md`

## 作者

linqibin

## 起始版本

0.1.0
