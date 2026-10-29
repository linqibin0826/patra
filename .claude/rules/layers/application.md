---
paths: patra-api/*/*-app/**/*.java
---

# Application 层开发规范

## 核心职责

- 编排领域服务，管理事务边界
- 转换数据格式：Command → Domain → Result

## 命名约定

| 组件 | 命名规则 | 示例 |
|------|---------|------|
| 命令对象 | `{Action}{Entity}Command` | `CreateUserCommand` |
| 处理器 | `{Action}{Entity}Handler` | `CreateUserHandler` |
| 结果对象 | `{Action}{Entity}Result` | `CreateUserResult` |

三者放同一个包 `app.{子域}.usecase.{用例族}`，不再分 `command/`、`dto/` 子包（见 [package-layout.md](../tech/package-layout.md)）。

## 事务管理

- Application 层是**唯一**划定事务边界的层级（infra 只允许 `MANDATORY` 守卫）
- 写命令 Handler 注入 `TransactionOperations`，只包住写库那一段，`handle()` 上不加 `@Transactional`
- 各场景的写法、事务里禁止的操作、加锁约定见 [transaction.md](../tech/transaction.md)

## 异常处理

- 领域异常（`DomainException`）携带语义特征应**直接传播**，由 `DefaultErrorResolutionEngine` 自动映射为 HTTP 状态码
- 仅对**意外异常**（`RuntimeException`、`Exception`）使用 `ApplicationException` 包装，携带 `ErrorCodeLike` 错误码

## Gateway 实现规范

当 Infra 层组件需要调用涉及业务逻辑的服务时，使用 Gateway 模式：

| 组件 | 位置 | 说明 |
|------|------|------|
| `{Entity}Gateway` | Domain 层 `port/` | 接口定义 |
| `{Entity}GatewayImpl` | `app.{子域}.service` | 实现类 |

**使用场景**：
- Spring Batch Processor 需要 findOrCreate 语义
- 需要独立事务管理的跨聚合协调

> 详细规范参见 [port-service.md](../tech/port-service.md)

## QueryService 规范

CQRS 读端查询服务，不走 CommandBus，直接被 Controller 注入。

| 组件 | 位置 | 说明 |
|------|------|------|
| `{Domain}QueryService` | `app.{子域}.usecase.query` | 具体类，无接口 |
| `{Domain}ListQuery` | `app.{子域}.usecase.query` | 查询参数 DTO，与 QueryService 同包 |

**特点**：
- 默认不加事务；需要同一会话多次读 JPA 实体时才加 `@Transactional(readOnly = true)`（见 [transaction.md](../tech/transaction.md)）
- 无接口定义（CQRS 读端不需要抽象）
- 使用 `PagingParams.normalize()` 统一归一化分页参数
- 返回 `PageResult<{Entity}SummaryReadModel>`

> 详细规范参见 [port-service.md](../tech/port-service.md) 和 [commandbus.md](../tech/commandbus.md) 的 Query 端章节
