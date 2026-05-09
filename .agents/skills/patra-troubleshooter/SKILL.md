---
name: "patra-troubleshooter"
description: "Patra 应用错误排查指南。分析异常堆栈、日志链路、Spring/JPA/RestClient/Hibernate/MapStruct 报错或应用启动失败时使用。"
---

# Patra 应用错误排查

## 排查流程

### 1. 分析异常堆栈

从用户提供的堆栈中提取：
- **异常类型**：`NullPointerException`、`IllegalStateException`、自定义 `DomainException` 等
- **根因位置**：`Caused by` 链中最底层的异常
- **业务代码位置**：`com.patra.*` 包下的调用栈行号

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
| 排查 com.patra 问题 | 默认 DEBUG 已开启 | - |
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
