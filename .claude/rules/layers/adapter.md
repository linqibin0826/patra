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

## API 路径约定

- `/_internal/` 前缀专用于微服务间内部 RPC（定义在 `patra-*-api` 模块的 HTTP Interface 契约中），不暴露给外部
- 面向前端消费的公开 API 使用无前缀路径（如 `/dictionaries/items`、`/venues`）
- 两者是不同消费者的独立 Controller，不要混淆

## 禁止行为

1. 禁止在 Controller 中处理业务逻辑
2. 禁止直接调用 Repository（写操作通过 CommandBus，读操作通过 QueryService）
3. 禁止在请求/响应对象中使用领域对象
4. 禁止直接注入 CommandHandler（应使用 CommandBus）
5. 禁止将面向前端的公开 API 放在 `/_internal/` 路径下

## Starter 依赖

- Web 层：`starter-web`
