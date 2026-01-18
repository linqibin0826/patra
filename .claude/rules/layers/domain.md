---
paths: patra-*/*-domain/**/*.java
---

# Domain 层开发规范

## 核心原则

- **纯 Java 实现**：禁止依赖任何框架（Spring、JPA 等），Lombok 除外
- **业务逻辑内聚**：所有业务规则封装在领域对象内部
- **依赖倒置**：通过 Port/Repository 接口定义外部依赖

## 禁止行为

1. 禁止使用 Spring 注解（`@Service`、`@Autowired` 等）
2. 禁止依赖 Infrastructure 层的任何类
3. 禁止在 Domain 层添加可观测性代码

## 异常处理

- 继承 `DomainException`，携带 `StandardErrorTrait` 语义特征

## 聚合设计

1. **聚合即整体**：子实体/值对象变更 = 聚合根状态变更
2. **聚合根版本锁**：修改聚合内任何数据必须递增 `version` 字段
3. 通过 `Repository.save(aggregateRoot)` 持久化整个聚合

## Port 命名规范

| 类型 | 接口命名 | 包位置 | 实现层 |
|------|----------|--------|--------|
| Repository | `{Entity}Repository` | `port/repository/` | Infra |
| Driven Port | `{Function}Port` | `port/{function}/` | Infra |
| Driving Port | `{Entity}Gateway` | `port/gateway/` | App |

> 详细规范参见 [port-service.md](../tech/port-service.md)
