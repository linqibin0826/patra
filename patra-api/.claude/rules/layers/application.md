---
paths: patra-*/*-app/**/*.java
---

# Application 层开发规范

## 核心职责

- 编排领域服务，管理事务边界（`@Transactional`）
- 转换数据格式：Command → Domain → Result

## 命名约定

| 组件 | 命名规则 | 示例 |
|------|---------|------|
| 命令对象 | `{Action}{Entity}Command` | `CreateUserCommand` |
| 处理器 | `{Action}{Entity}Handler` | `CreateUserHandler` |
| 结果对象 | `{Action}{Entity}Result` | `CreateUserResult` |

## 事务管理

- Application 层是**唯一**管理事务的层级
- 在 Handler 的 `handle()` 方法上使用 `@Transactional`
- 禁止在 Domain/Infrastructure/Adapter 层使用 `@Transactional`

## 异常处理

- 使用 `ApplicationException` 包装领域异常
- 携带 `ErrorCodeLike` 错误码，格式：`{SERVICE}-{0xxx}`

## Gateway 实现规范

当 Infra 层组件需要调用涉及业务逻辑的服务时，使用 Gateway 模式：

| 组件 | 位置 | 说明 |
|------|------|------|
| `{Entity}Gateway` | Domain 层 `port/` | 接口定义 |
| `{Entity}GatewayImpl` | App 层 `usecase/{domain}/service/` | 实现类 |

**使用场景**：
- Spring Batch Processor 需要 findOrCreate 语义
- 需要独立事务管理的跨聚合协调

> 详细规范参见 [port-service.md](../tech/port-service.md)
