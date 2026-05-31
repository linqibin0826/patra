---
name: patra-troubleshooter
description: |
  Patra 应用程序错误排查专家。分析异常堆栈、追踪日志链路、定位问题根因。
  触发场景：用户贴出 Java 异常堆栈或错误日志、描述应用运行时错误、请求排查某个服务的问题、
  出现 Spring/JPA/RestClient/Hibernate/MapStruct 等框架异常、应用启动失败、
  数据库连接异常、Nacos 服务发现问题、HTTP Interface 调用超时或返回错误。
  即使用户只说"报错了"或贴出一段日志，也应使用此技能进行系统化排查。
  提供日志分析、链路追踪、代码定位、第三方文档查询、联网搜索解决方案。
---

# Patra 应用错误排查

> **方法论纪律**：本技能是 `patra:systematic-debugging` 在 Patra 后端的落地执行，下面的「排查流程 1–5」对应其**第一阶段（根因调查）**。排查全程遵循其根因优先纪律：
>
> - **不做根因调查，不许提修复方案**——定位到根因前，禁止给出任何 fix。
> - **单一假设、最小验证**——一次只改一个变量，不捆绑"顺便改改"的优化与重构。
> - **修复前先写失败测试**——走 `patra:test-driven-development`，用最简复现锁定 bug。
> - **3 次修复失败 → 停止打补丁，质疑架构**（见 systematic-debugging 第四阶段第 5 步），而不是发起第 4 次猜测式修复。
>
> 即定位故障源后，回到 systematic-debugging 的第二～四阶段（模式对比 → 假设验证 → 实施）完成修复。

## 排查流程

### 1. 分析异常堆栈

从用户提供的堆栈中提取：
- **异常类型**：`NullPointerException`、`IllegalStateException`、自定义 `DomainException` 等
- **根因位置**：`Caused by` 链中最底层的异常
- **业务代码位置**：`dev.linqibin.*` 包下的调用栈行号

### 2. 提取链路信息

从日志中提取 `[traceId/spanId]`，用于跨服务追踪：
```bash
grep "traceId值" /Users/linqibin/Desktop/Patra/patra-api/logs/*.log
```

### 3. 查看应用日志

日志目录：`/Users/linqibin/Desktop/Patra/patra-api/logs/`

**时间范围过滤**（使用 Read 工具或 grep）：
```bash
grep "2025-12-18 11:30" /Users/linqibin/Desktop/Patra/patra-api/logs/patra-catalog.log
```

### 4. 定位源码

使用 IDEA MCP 的符号搜索能力或 `rg` 定位异常发生的类和方法，阅读上下文代码理解逻辑。

### 5. 查找解决方案

**第三方框架问题**：
1. 使用 `mcp__context7__resolve-library-id` + `mcp__context7__get-library-docs` 查询官方文档
2. 使用 `WebSearch` 搜索 stackoverflow/github issues

**业务代码问题**：
1. 检查是否缺少 DEBUG 日志，需要时在代码中添加
2. 分析领域逻辑，检查边界条件

## 日志级别调整策略

| 场景 | 操作 | 配置位置 |
|------|------|----------|
| 排查 dev.linqibin 问题 | 默认 DEBUG 已开启 | - |
| 排查 Spring 问题 | 临时开启 `org.springframework` DEBUG | logback-spring.xml |
| 排查 JPA/Hibernate 问题 | 临时开启 `org.hibernate` DEBUG | logback-spring.xml |
| 排查 HTTP Interface/RestClient 问题 | 临时开启 `org.springframework.web.client` DEBUG | logback-spring.xml |
| 业务逻辑缺日志 | 在代码中添加 log.debug() | 对应 Java 文件 |

**重要**：修复后必须关闭临时 DEBUG 日志！

详细配置参考：[log-config.md](references/log-config.md)

## 常见异常模式

### RemoteCallException（HTTP Interface 调用失败）
```java
// 正确处理方式：基于 ErrorTrait 语义判断
catch (RemoteCallException ex) {
    if (ex.hasErrorTrait(StandardErrorTrait.NOT_FOUND)) {
        // 处理资源不存在
    }
}
```

### DomainException（领域层异常）
检查 `StandardErrorTrait` 语义特征，追溯领域逻辑。

### ApplicationException（应用层异常）
检查 `ErrorCodeLike` 错误码，格式：`{SERVICE}-{0xxx}`

## 联网搜索关键词模板

```
{异常类名} {框架名} {版本} site:stackoverflow.com
{异常消息关键词} spring boot 3.x
{错误码} github issues
```
