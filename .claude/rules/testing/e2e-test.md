---
paths: patra-*/*-boot/**/src/test/**/*E2E.java
---

# E2E 测试规范

## 适用范围

Boot 模块：端到端测试，使用真实中间件（TestContainers）

## 文件命名

`*E2E.java`

## 超时限制

`@Timeout` ≤ 60s（多服务协调）
Awaitility 等待：`atMost` ≤ 5s

## 测试比例

E2E < 5%

## 注意事项

- `Db.saveBatch()` 验证需在 E2E 测试中进行（切片测试存在事务隔离问题）
- 覆盖核心业务流程，避免冗余测试场景
