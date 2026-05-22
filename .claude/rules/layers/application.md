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

- 领域异常（`DomainException`）携带语义特征应**直接传播**，由 `DefaultErrorResolutionEngine` 自动映射为 HTTP 状态码
- 仅对**意外异常**（`RuntimeException`、`Exception`）使用 `ApplicationException` 包装，携带 `ErrorCodeLike` 错误码

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

## QueryService 规范

CQRS 读端查询服务，不走 CommandBus，直接被 Controller 注入。

| 组件 | 位置 | 说明 |
|------|------|------|
| `{Domain}QueryService` | `usecase/{domain}/query/` | 具体类，无接口 |
| `{Domain}ListQuery` | `usecase/{domain}/query/dto/` | 查询参数 DTO |

**特点**：
- 无 `@Transactional`（只读操作，JPA 默认读事务即可）
- 无接口定义（CQRS 读端不需要抽象）
- 使用 `PagingParams.normalize()` 统一归一化分页参数
- 返回 `PageResult<{Entity}SummaryReadModel>`

> 详细规范参见 [port-service.md](../tech/port-service.md) 和 [commandbus.md](../tech/commandbus.md) 的 Query 端章节
