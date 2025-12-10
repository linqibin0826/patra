---
paths: patra-*/*-app/**/*.java
---

# Application 层开发规范

## 核心职责

- 编排领域服务，管理事务边界（`@Transactional`）
- 转换数据格式：Command → Domain → Result

## 命名约定

- 用例接口：`{Feature}UseCase`
- 编排器：`{Feature}Orchestrator`
- 命令对象：`{Feature}Command`（含 `@Valid` 验证）
- 结果对象：`{Feature}Result`

## 事务管理

- Application 层是**唯一**管理事务的层级
- 禁止在 Domain/Infrastructure/Adapter 层使用 `@Transactional`

## 异常处理

- 使用 `ApplicationException` 包装领域异常
- 携带 `ErrorCodeLike` 错误码，格式：`{SERVICE}-{0xxx}`
