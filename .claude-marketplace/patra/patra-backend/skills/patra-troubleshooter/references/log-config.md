# Patra 日志配置参考

## 日志目录

```
/Users/linqibin/Desktop/Patra/patra-api/logs/
├── patra-catalog.log
├── patra-ingest.log
├── patra-registry.log
└── patra-gateway.log
```

## 日志格式

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${appName}] [%traceId/%spanId] [%t] %-40.40logger{39} : %m%n
```

**字段说明**：
| 字段 | 格式 | 示例 |
|------|------|------|
| 时间戳 | `yyyy-MM-dd HH:mm:ss.SSS` | `2025-12-18 11:30:45.123` |
| 日志级别 | 5字符右对齐 | `INFO`, `DEBUG`, `ERROR` |
| 应用名称 | `[appName]` | `[patra-catalog]` |
| 链路信息 | `[traceId/spanId]` | `[abc123/def456]` |
| 线程名 | `[thread]` | `[http-nio-8080-exec-1]` |
| Logger | 40字符截断 | `c.p.catalog.app.ArticleApplicationServi` |
| 消息 | 原始消息 | `处理文章: 12345` |

## 日志级别配置

| 包路径 | 级别 | 说明 |
|--------|------|------|
| root | INFO | 默认级别，所有第三方库 |
| dev.linqibin | DEBUG | 业务代码，DEBUG 级别 |

**重要**：FILE appender 有 `ThresholdFilter` 过滤 INFO 以下，所以：
- **控制台**：可见 dev.linqibin 的 DEBUG 日志
- **文件**：只有 INFO 及以上，DEBUG 日志不会写入文件

## 滚动策略

- 单文件最大：100MB
- 总大小上限：10GB
- 保留天数：30天
- 归档格式：`${appName}.%d{yyyy-MM-dd}.%i.log`

## 链路追踪

使用 OpenTelemetry 自动注入：
- `traceId`：分布式追踪 ID，跨服务唯一
- `spanId`：当前操作 ID

**按链路追踪 grep 示例**：
```bash
grep "abc123def456" /Users/linqibin/Desktop/Patra/patra-api/logs/*.log
```

## 配置文件位置

```
/Users/linqibin/Desktop/Patra/patra-api/patra-spring-boot-starter-core/src/main/resources/logback-spring.xml
```

## 临时开启第三方 DEBUG 日志

在 `logback-spring.xml` 中添加（排查完成后删除）：

```xml
<!-- 临时开启 Spring 框架 DEBUG -->
<logger name="org.springframework" level="DEBUG" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<!-- 临时开启 JPA/Hibernate DEBUG -->
<logger name="org.hibernate" level="DEBUG" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<!-- 临时开启 HTTP Interface/RestClient DEBUG -->
<logger name="org.springframework.web.client" level="DEBUG" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>
```
