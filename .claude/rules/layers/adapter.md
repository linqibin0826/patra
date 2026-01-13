---
paths: patra-*/*-adapter/**/*.java
---

# Adapter 层开发规范

## 核心职责

- 入站适配：Controller、Job、Listener
- 协议转换：HTTP/消息 → Command → CommandBus

## 禁止行为

1. 禁止在 Controller 中处理业务逻辑
2. 禁止直接调用 Repository（必须通过 CommandBus）
3. 禁止在请求/响应对象中使用领域对象
4. 禁止直接注入 CommandHandler（应使用 CommandBus）

## Starter 依赖

- Web 层：`starter-web`
