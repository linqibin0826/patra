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
