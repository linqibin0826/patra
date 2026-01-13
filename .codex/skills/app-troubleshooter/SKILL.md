---
name: app-troubleshooter
description: Patra 应用错误排查专家：分析 Java 异常堆栈、Spring/JPA/HTTP Interface 等框架错误，以及本仓库 logs 目录日志，定位根因并给出修复与验证步骤。
metadata:
  short-description: 运行时错误定位与修复流程
---

# Patra 应用错误排查

本技能用于处理“应用运行时报错/行为异常/接口失败/数据不一致”等问题，目标是用**可复现、可验证**的方式定位根因并修复。

## 0. 先收集最小信息（不猜）

- 现象：发生时间、影响范围、是否必现、触发入口（HTTP/Job/Listener）
- 证据：异常堆栈（从 `Caused by` 链条最底部开始）、关键日志片段、相关请求参数（脱敏）
- 环境：服务名/模块、分支、配置差异（本地/测试/生产）

## 1. 堆栈分析（从根因开始）

1. 锁定根因异常：优先看最底层 `Caused by`。
2. 识别业务落点：优先关注 `com.patra.*` 包下的类与行号。
3. 分类处理：
   - Spring 启动/注入：Bean 冲突、条件装配、配置绑定
   - JPA/Hibernate：映射、事务、SQL、乐观锁/软删除
   - HTTP Interface/远程调用：统一按 `RemoteCallException` + `ErrorTrait` 语义判断

## 2. 日志与链路（TraceId 贯穿）

- 日志目录：`./logs/`
- 若日志包含 `traceId/spanId`：优先用它做跨线程/跨组件关联，围绕同一 `traceId` 抽取前后文。

## 3. 源码定位与验证

1. 基于堆栈行号定位到具体方法，读上下文确认输入、状态与边界条件。
2. 复现优先级：
   - 能写测试复现 → 先写测试（回归保护）
   - 不能写测试复现 → 最小化复现步骤 + 日志增强（仅临时）
3. 修复后验证：
   - 针对性测试通过
   - 关键路径日志/指标无异常
   - 移除临时 DEBUG/临时探针（避免污染长期运行）

## 4. 日志级别调整策略（临时）

| 场景 | 操作 |
|------|------|
| 排查业务代码 | 只对 `com.patra` 提升到 DEBUG（临时） |
| 排查 Spring | 临时开启 `org.springframework` DEBUG |
| 排查 JPA/Hibernate | 临时开启 `org.hibernate` DEBUG |
| 排查 HTTP Interface | 临时开启 `org.springframework.web.client` DEBUG |

参考：`references/log-config.md`（修复后务必回滚临时日志级别）
