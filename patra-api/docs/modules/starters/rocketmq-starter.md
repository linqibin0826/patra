# patra-spring-boot-starter-rocketmq 使用指南

> **状态**：已验证（v0.1.0，2025-10-01）  
> **权威文档**：[patra-spring-boot-starter-rocketmq/README.md](../../../patra-spring-boot-starter-rocketmq/README.md)

本文档为 RocketMQ Starter 的快速参考和最佳实践补充。完整配置和 API 说明请参考模块 README。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
</dependency>
```

### 2. 配置

```yaml
patra:
  messaging:
    rocketmq:
      enabled: true

rocketmq:
  name-server: ${MQ_NAMESERVER}
```

### 3. 消费消息（推荐方式）

```java
@Component
@MessageListener(
    channel = "ingest.task.ready",  // 直接指定 channel 字符串
    consumer = "relay",
    mode = ConsumeMode.CONCURRENT,
    concurrency = 2
)
public class TaskReadyHandler implements MessageHandler<TaskReadyPayload> {
    @Override
    public void handle(Message<TaskReadyPayload> message) throws Exception {
        // 处理逻辑
    }
}
```

## 核心特性

### Channel 格式

- **格式**：`domain.resource.event`（至少 3 段，小写，点分段）
- **示例**：`ingest.task.ready`、`registry.config.updated`
- **自动映射**：
  - Topic：`{NAMESPACE}.{DOMAIN}.{RESOURCE}`（如 `DEV.INGEST.TASK`）
  - Tag：`{EVENT}`（如 `READY`）

### 消费组命名

自动生成格式：`svc-{service}-{consumer}-cg`

- `{service}`：来自 `spring.application.name`
- `{consumer}`：来自 `@MessageListener` 的 `consumer` 属性

示例：`svc-ingest-relay-cg`

## 最佳实践

### 1. Channel 常量定义（推荐）

虽然现在支持字符串形式的 channel，但仍建议定义常量避免拼写错误：

```java
// 在 domain 层或 api 层
public interface IngestChannels {
    String TASK_READY = "ingest.task.ready";
    String TASK_COMPLETED = "ingest.task.completed";
}

// 使用时
@MessageListener(channel = IngestChannels.TASK_READY, consumer = "relay")
```

### 2. 枚举方式（可选）

如果需要编译期类型检查和更多元数据：

```java
public enum IngestPublishingChannels implements ChannelKey {
    TASK_READY("ingest", "task", "ready", TaskReadyMessage.class);
    
    // ... 实现 ChannelKey 接口
}

// 使用时
@MessageListener(channel = TASK_READY.channel(), consumer = "relay")
```

### 3. 消费模式选择

| 场景 | 模式 | 并发度 | 说明 |
|------|------|--------|------|
| 高吞吐、无序 | `CONCURRENT` | 2-8 | 默认选择 |
| 严格顺序 | `ORDERLY` | 1 | 按 hashKey 顺序 |
| 低延迟 | `CONCURRENT` | 1-2 | 避免过度并发 |

### 4. 错误处理

```java
@Override
public void handle(Message<TaskReadyPayload> message) throws Exception {
    try {
        // 业务逻辑
    } catch (BusinessException e) {
        // 业务异常不重试
        log.error("业务处理失败，不重试: {}", e.getMessage());
        return; // 正常返回，ACK 消息
    } catch (Exception e) {
        // 系统异常重试
        log.error("系统异常，触发重试", e);
        throw e; // 抛出异常触发重试
    }
}
```

## 常见问题

### Q1: Channel 格式错误

**现象**：启动时抛出 `IllegalArgumentException: channel 格式错误`

**原因**：channel 不符合 `domain.resource.event` 格式

**解决**：检查 channel 字符串，确保至少 3 段，小写，点分段

### Q2: 消息无法消费

**排查步骤**：
1. 检查 RocketMQ Console，确认 Topic 和 Tag 正确
2. 检查消费组是否订阅了正确的 Topic
3. 查看日志中的 `[CONSUME][START]` 和 `[CONSUME][SUCCESS]` 记录
4. 确认发布方的 channel 与消费方的 channel 一致

### Q3: 是否必须使用枚举？

**答**：不必须。从 v0.1.0 开始支持直接使用字符串。推荐根据场景选择：
- **快速开发/跨服务消费**：使用字符串，简单快捷
- **强类型要求/内部消费**：使用枚举，提供编译期检查

## 版本变更

### v0.1.0 (2025-10-01)

- ✅ 简化消费者配置，支持直接使用 channel 字符串
- ✅ 移除枚举强制依赖，降低消费方与发布方耦合
- ✅ 删除白名单校验机制，Channel 现在只能通过 ChannelKey 创建，保证类型安全
- ✅ 添加启动时 channel 格式验证

## 参考资料

- 完整文档：[patra-spring-boot-starter-rocketmq/README.md](../../../patra-spring-boot-starter-rocketmq/README.md)
- 消息通道约定：[ChannelKey.java](../../../patra-common/src/main/java/com/patra/common/messaging/ChannelKey.java)
- Outbox 流程：[ingest/deep-dive.md](../ingest/deep-dive.md)
- 错误处理：[platform-error-handling.md](../../standards/platform-error-handling.md)
