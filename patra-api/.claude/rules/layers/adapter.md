---
paths: patra-*/*-adapter/**/*.java
---

# Adapter 层开发规范

## 核心职责

- 入站适配：Controller、Job、Listener
- 协议转换：HTTP/消息 → Command → CommandBus（写操作）
- 协议转换：HTTP → Query → QueryService（读操作）

## 查询 Controller 约定

查询 Controller 注入 `QueryService` + `ApiConverter`（不注入 CommandBus）：
- 使用 `PageResult.map()` 进行读模型 → 响应 DTO 的跨层转换
- 返回 `PageResult<{Entity}ItemResponse>`（不使用 ResponseEntity）

## Controller 子包结构

```
adapter/rest/{entity}/
├── {Entity}Controller.java
├── request/{Entity}ListRequest.java      # 查询参数 DTO
├── response/{Entity}ItemResponse.java    # 列表项响应 DTO
└── mapper/{Entity}ApiConverter.java      # MapStruct 转换器
```

## 禁止行为

1. 禁止在 Controller 中处理业务逻辑
2. 禁止直接调用 Repository（写操作通过 CommandBus，读操作通过 QueryService）
3. 禁止在请求/响应对象中使用领域对象
4. 禁止直接注入 CommandHandler（应使用 CommandBus）

## Starter 依赖

- Web 层：`starter-web`
