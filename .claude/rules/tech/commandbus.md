---
paths: patra-*/patra-*-app/**/*.java, patra-*/patra-*-adapter/**/*.java
---

# CommandBus 使用规范

## 概述

CommandBus 是 Adapter 层与 Application 层之间的**统一分发中心**，替代直接注入多个 UseCase 接口的模式。

## 架构图

```
Adapter 层（Controller/Job/Listener）
    ↓ 只注入 CommandBus
CommandBus
    ↓ 拦截器链（Tracing → Logging → Metrics）
    ↓ 自动路由
CommandHandler（Application 层）
    ↓ 调用
Domain + Infrastructure
```

## 核心组件

| 组件 | 位置 | 说明 |
|------|------|------|
| `Command<R>` | patra-common-core | 命令标记接口，泛型 R 为返回类型 |
| `CommandHandler<C,R>` | patra-common-core | 命令处理器接口 |
| `CommandBus` | patra-common-core | 命令总线接口 |
| `SimpleCommandBus` | patra-spring-boot-starter-core | Spring 实现 |

## 命名约定

> 见 [application.md](../layers/application.md) 的命名约定表（Command/Handler/Result 命名规则）

## Void 返回类型

对于无返回值的命令，使用 `Void` 作为泛型参数，Handler 返回 `null`。

## Command 字段允许为 null 的场景

某些 Command 的字段允许为 null，由 Handler 内部回退到默认值（如配置覆盖场景）。
此时**必须在类 JavaDoc 中说明回退策略**。

## Query 端处理策略

**查询操作不通过 CommandBus**：

| 操作类型 | 路由方式 | 说明 |
|---------|---------|------|
| 写操作（Command） | `CommandBus.handle()` | 需要拦截器链 |
| 读操作（Query） | 直接注入 QueryService | 无副作用，不需要复杂横切关注点 |

## 禁止行为

1. 禁止在 Adapter 层直接注入 Handler（应使用 CommandBus）
2. 禁止在 Command 中包含业务逻辑（仅做数据载体）
3. 禁止在 Handler 中调用其他 Handler（使用事件驱动）
4. 禁止让 Handler 依赖框架特定类（保持可测试性）

## 与旧模式的关系

CommandBus 模式已完全取代 Orchestrator/UseCase 模式。项目中不再使用 Orchestrator 命名，Handler 内部可使用 `*Phase` 表示执行阶段。
