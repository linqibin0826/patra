---
paths: patra-*/*-api/**/*.java
---

# API 模块开发规范

## 核心职责

- 定义服务契约：HTTP Interface 接口（@HttpExchange）、DTO、常量
- 跨服务共享的公共定义

## 禁止行为

1. 禁止包含 Controller 实现
2. 禁止包含业务逻辑
3. 禁止依赖 Domain/Application/Infrastructure 层
