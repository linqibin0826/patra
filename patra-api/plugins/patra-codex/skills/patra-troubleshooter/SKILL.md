---
name: patra-troubleshooter
description: Patra 应用错误排查技能。用户贴出异常堆栈、错误日志、运行时故障、启动失败、数据库连接问题、HTTP Interface 调用错误或让你定位根因时使用。
---

# Patra 故障排查

用于对异常堆栈、日志链路、启动失败、JPA 问题、HTTP Interface 调用错误和运行时故障做系统化排查。

## 何时使用

- 用户贴出 Java 异常堆栈
- 用户给出日志片段、traceId、spanId
- 服务启动失败、数据库连接失败、调用下游失败
- 需要从日志和源码一起定位根因

## 排查顺序

1. 先找根因异常
   - 识别最底层 `Caused by`
   - 抓取 `com.patra.*` 相关调用栈与行号
2. 提取链路信息
   - 从日志中找 `traceId` / `spanId`
   - 同时间窗内串起上下游日志
3. 检查本地日志
   - 日志目录：`logs/`
   - 先按时间、traceId、类名过滤，再扩大范围
4. 定位源码
   - 用 `rg` 找类、方法、异常消息、错误码
   - 对照 `.claude/rules/**` 判断是否违反分层或异常规范
5. 框架问题再查官方文档或联网资料

## 日志策略

- 默认先用现有日志，不要一上来加日志
- 必要时可临时加 `DEBUG` 日志，但修复后要说明并清理
- 排查 Spring / Hibernate / RestClient 时，只临时开启对应包的 DEBUG

## 常见异常方向

- `RemoteCallException`
  - 优先看 `ErrorTrait`
  - 判断是 `NOT_FOUND`、`CONFLICT` 还是服务端错误
- `DomainException`
  - 检查携带的 `StandardErrorTrait`
  - 回溯对应领域约束
- `ApplicationException`
  - 看错误码格式是否符合 `{SERVICE}-{0xxx}`
- JPA / Hibernate
  - 重点看事务边界、实体映射、延迟加载、乐观锁

## 关键约束

- 先确认问题属于业务逻辑、分层违规还是框架配置
- 不要只盯第一层异常消息，必须追到底层 `Caused by`
- 任何“临时调试改动”都必须可回收

## 参考文件

- `.claude/rules/tech/error-handling.md`
- `.claude/rules/layers/*.md`
