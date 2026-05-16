---
name: "patra-hexagonal"
description: "Patra 六边形架构组件创建与编排指南。创建 Controller、Handler、Port、Adapter、Repository、Gateway、QueryService、XXL-Job 或添加 Starter 依赖时使用。"
---

# Patra 六边形架构开发指南

在 Patra 项目中创建新组件或实现新功能时，按此指南确定**创建什么、放在哪里、怎么连接**。

架构约束和命名规范已在 `rules/` 中定义并自动加载，本技能聚焦于**实操指导**。

## 组件创建决策树

```
我要做什么？
│
├── 暴露一个 HTTP 接口（面向前端或外部）
│   ├── 写操作 → Controller + ApiConverter + Command + Handler
│   │   模块: adapter + app
│   │   参考: [controller-guide.md](resources/controller-guide.md)
│   │
│   └── 读操作（列表/详情查询）→ Controller + ApiConverter + QueryService + ReadPort
│       模块: adapter + app + domain(ReadPort) + infra(ReadAdapter)
│       参考: [controller-guide.md](resources/controller-guide.md) 的"分页列表查询"部分
│
├── 实现一个命令处理流程
│   └── Handler + Command + Result
│       模块: app
│       参考: [handler-guide.md](resources/handler-guide.md)
│
├── 定义领域层需要的外部能力
│   ├── 聚合根持久化 → `{Entity}Repository`（domain）→ `{Entity}RepositoryAdapter`（infra）
│   ├── 外部调用能力 → `{Function}Port`（domain）→ `{Function}Adapter`（infra）
│   ├── 实体 ID 查找（带缓存）→ `{Entity}LookupPort` → Default + Caching + Batch
│   ├── CQRS 读查询 → `{Entity}ReadPort`（domain）→ `{Entity}ReadAdapter`（infra）
│   └── 调用应用层服务 → `{Entity}Gateway`（domain）→ `{Entity}GatewayImpl`（app）
│
├── 创建定时任务
│   └── XXL-Job → 继承 `AbstractProvenanceScheduleJob`
│       模块: adapter
│       参考: [controller-guide.md](resources/controller-guide.md) 的"XXL-Job 模式"部分
│
└── 添加 Starter 依赖
    └── 参考: [patra-starters.md](resources/patra-starters.md)
```

## 层间数据流转

一个完整的写操作请求经过以下层转换：

```
[HTTP Request]
    ↓ @Valid 校验
[Adapter] CreateXxxRequest（record）
    ↓ ApiConverter.toCommand()      ← MapStruct 反腐层
[App]     CreateXxxCommand（record implements Command<R>）
    ↓ Handler.handle()              ← @Transactional 事务边界
[Domain]  XxxAggregate / DomainService
    ↓ Repository.save()
[Infra]   XxxEntity → JPA 持久化
    ↓ 返回
[App]     CreateXxxResult（record）
    ↓ ApiConverter.toResponse()
[Adapter] XxxResponse（record）
    ↓
[HTTP Response]
```

一个完整的读操作请求：

```
[HTTP Request]
    ↓
[Adapter] XxxListRequest（record）
    ↓ ApiConverter.toQuery()
[App]     XxxListQuery → QueryService.listXxx()
    ↓ ReadPort.findXxxPage()
[Infra]   XxxReadAdapter → JPA 查询
    ↓ 返回
[Domain]  PageResult<XxxSummaryReadModel>
    ↓ PageResult.map(converter::toItemResponse)
[Adapter] PageResult<XxxItemResponse>
    ↓
[HTTP Response]
```

## Starter 快速选择

```
判断模块类型：
├── adapter 层 → patra-spring-boot-starter-web
├── infra 层
│   ├── 需要数据库 → patra-spring-boot-starter-jpa
│   ├── 需要调用内部服务 → patra-spring-boot-starter-http-interface
│   ├── 需要调用外部 REST API → patra-spring-boot-starter-rest-client
│   ├── 需要对象存储 → patra-spring-boot-starter-object-storage
│   └── 需要分布式锁 → patra-spring-boot-starter-redisson
├── domain 层 → 不能添加任何 Starter
└── 详细指南 → [patra-starters.md](resources/patra-starters.md)
```

## 新功能开发 Checklist

开始一个新功能前，依次确认：

1. **确定涉及的模块**：adapter / app / domain / infra 分别需要什么
2. **检查 Starter 依赖**：是否已添加对应 Starter
3. **定义 Port 接口**（如需要）：在 domain 层定义，遵循命名规范
4. **从外向内开发**：
   - Controller/Job（adapter）→ Command + Handler（app）→ Domain Logic → Port 实现（infra）
   - 或者 TDD：测试先行，从 Handler 测试开始

## 联动技能

完整功能开发通常涉及多层代码，进入以下阶段时**必须加载对应技能**：

- **实现数据层**（Entity / Dao / JpaMapper / RepositoryAdapter / ReadAdapter）→ 加载 `patra-jpa`
- **实现事件通信**（领域事件 / Outbox / @TransactionalEventListener）→ 加载 `patra-events`

## 资源索引

| 场景 | 参考文档 | 何时阅读 |
|------|----------|----------|
| 创建 Controller / Job | [controller-guide.md](resources/controller-guide.md) | 需要写 adapter 层代码时 |
| 创建 Handler / 事务管理 | [handler-guide.md](resources/handler-guide.md) | 需要写 app 层命令处理时 |
| 选择/添加 Starter 依赖 | [patra-starters.md](resources/patra-starters.md) | 不确定该用哪个 Starter 时 |
| 配置管理 | [configuration.md](resources/configuration.md) | 需要添加配置属性时 |
| Port/Service 详细指南 | [port-service-guide.md](resources/port-service-guide.md) | 需要了解各 Port 类型的依赖方向、注入方式、目录结构时 |

## Codex 工具使用说明

原 Claude `allowed-tools` 已改写为 Codex 操作建议，不是权限边界。使用本技能时优先读取本地规则与代码、用 `rg`/文件检索定位上下文、通过 `apply_patch` 做小范围修改，并在必要时运行 shell 验证命令。
