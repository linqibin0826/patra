# Starters 模块文档索引

> 本目录包含各自研 Starter 的使用指南和最佳实践补充文档。

## Starter 列表

### 核心基础设施

- **[patra-spring-boot-starter-core](../../../patra-spring-boot-starter-core/README.md)**
  - 统一错误处理、TraceProvider SPI、基础工具

- **[patra-spring-boot-starter-web](../../../patra-spring-boot-starter-web/README.md)**
  - Web 层封装、统一响应格式、全局异常处理

- **[patra-spring-boot-starter-mybatis](../../../patra-spring-boot-starter-mybatis/README.md)**
  - MyBatis-Plus 配置、分页、审计字段

### 消息中间件

- **[patra-spring-boot-starter-rocketmq](../../../patra-spring-boot-starter-rocketmq/README.md)** 📖 [使用指南](./rocketmq-starter.md)
  - RocketMQ 统一封装、消息模型、发布/消费抽象
  - **重要更新（v0.1.0）**：支持直接使用 channel 字符串，简化消费者配置

### 表达式引擎

- **[patra-spring-boot-starter-expr](../../../patra-spring-boot-starter-expr/README.md)**
  - 表达式引擎集成、配置管理

### 服务间调用

- **[patra-spring-cloud-starter-feign](../../../patra-spring-cloud-starter-feign/README.md)**
  - Feign 客户端封装、错误处理、负载均衡

## 文档组织原则

1. **权威来源**：各 Starter 模块目录下的 README.md 是权威文档
2. **补充指南**：本目录（`docs/modules/starters/`）提供快速参考和最佳实践
3. **避免重复**：不在 docs 目录重复维护完整的 API 文档
4. **及时更新**：Starter 有重要变更时，同步更新对应的使用指南

## 最佳实践

### 何时创建使用指南？

在以下情况下，为 Starter 创建补充的使用指南文档（如 `rocketmq-starter.md`）：

1. **复杂配置场景**：需要展示多种配置组合和场景
2. **常见问题**：收集了足够的 FAQ 和排查经验
3. **版本变更**：有重要的 API 变更或使用方式变化
4. **最佳实践**：积累了足够的生产实践经验

### 使用指南模板

```markdown
# {Starter Name} 使用指南

> **状态**：{草案/已验证}  
> **权威文档**：[{path-to-readme}]

## 快速开始
- 引入依赖
- 基本配置
- 简单示例

## 核心特性
- 特性 1
- 特性 2

## 最佳实践
- 实践 1
- 实践 2

## 常见问题
- Q1
- Q2

## 版本变更
### vX.Y.Z
- 变更说明

## 参考资料
- 完整文档链接
```

## 维护建议

1. **保持简洁**：使用指南专注于"如何使用"，不重复 API 说明
2. **实例驱动**：多用代码示例，少用抽象描述
3. **及时更新**：Starter 有变更时，同步更新使用指南
4. **链接检查**：定期检查指向 Starter README 的链接是否有效
