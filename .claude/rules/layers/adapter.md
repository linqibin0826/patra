---
paths: patra-*/*-adapter/**/*.java
---

# Adapter 层开发规范

## 核心职责

- 入站适配：Controller、Job、Listener
- 协议转换：HTTP/消息 → Command → UseCase

## 禁止行为

1. 禁止在 Controller 中处理业务逻辑
2. 禁止直接调用 Repository（必须通过 UseCase）
3. 禁止在请求/响应对象中使用领域对象

## Feign 错误处理

1. 捕获 `RemoteCallException`，基于 `getErrorTraits()` 判断错误类型
2. 备选：使用 `RemoteErrorHelper` 工具类
3. 禁止直接捕获 `FeignException`

## Starter 依赖

- Web 层：`starter-web`
- Feign：`cloud-starter-feign`
