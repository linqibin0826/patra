# patra-spring-boot-starter-redisson 设计文档

**版本**: v1.0.0
**状态**: 设计阶段
**最后更新**: 2025-11-23

---

## 📚 文档导航

### 核心文档

1. **[架构设计文档](./architecture-design.md)** ⭐ **必读**
   - 完整的技术架构设计
   - 核心功能详细设计
   - 实施计划和时间表
   - 风险评估

2. **[使用示例](./usage-examples.md)**
   - 基本用法示例
   - 高级用法示例
   - 最佳实践

3. **[与 Batch Starter 的集成](./batch-integration.md)**
   - 依赖关系调整
   - 迁移指南
   - 对比说明

---

## 🎯 快速开始

### 1. 了解背景

阅读 [架构设计文档 - 一、概述](./architecture-design.md#一概述)，了解为什么要创建这个 Starter。

### 2. 理解设计

阅读 [架构设计文档 - 五、架构设计](./architecture-design.md#五架构设计)，理解整体架构和包结构。

### 3. 查看示例

阅读 [架构设计文档 - 八、使用示例](./architecture-design.md#八使用示例)，了解如何使用 Starter。

### 4. 开始实施

参考 [架构设计文档 - 九、实施计划](./architecture-design.md#九实施计划)，按阶段开发。

---

## 📋 核心特性

### ✅ 已规划功能（P0）

- [x] RedissonClient 自动配置
- [x] @DistributedLock 声明式锁注解
- [x] 锁 AOP 自动拦截
- [x] SpEL 表达式支持
- [x] SkyWalking 追踪集成
- [x] Micrometer 指标集成
- [x] 多种锁类型（可重入、公平、读写）
- [x] 统一异常处理

### 🚧 未来优化（P1-P2）

- [ ] 分布式缓存（Spring Cache 集成）
- [ ] 分布式限流（@RateLimit）
- [ ] 分布式计数器
- [ ] 锁降级（Redis 不可用时）

---

## 🏗️ 整体架构

```
业务服务 (patra-catalog, patra-ingest)
├─ 使用 @DistributedLock 注解
└─ 业务逻辑受锁保护
           ↓ 依赖
patra-spring-boot-starter-redisson
├─ 自动配置: RedissonClient
├─ AOP 拦截: LockAspect
├─ 键生成: LockKeyGenerator (SpEL 支持)
└─ 可观测性: SkyWalking + Micrometer + 日志
           ↓ 依赖
Redisson 3.36.0 + Redis 6.0+
```

---

## 💡 设计亮点

### 1. 开箱即用

```java
// 只需一个注解，零代码获取/释放锁
@DistributedLock(key = "user:#{#userId}", leaseTime = 30)
public void updateUser(Long userId) {
    // 业务逻辑
}
```

### 2. SpEL 表达式支持

```java
// 动态生成锁键
@DistributedLock(key = "order:#{#order.id}:#{T(java.time.LocalDate).now()}")
public void processOrder(Order order) {
    // 业务逻辑
}
```

### 3. 多种锁类型

```java
// 读锁（允许并发）
@DistributedLock(key = "config:#{#key}", type = LockType.READ)
public String getConfig(String key) { ... }

// 写锁（独占）
@DistributedLock(key = "config:#{#key}", type = LockType.WRITE)
public void updateConfig(String key, String value) { ... }
```

### 4. 完整的可观测性

- **SkyWalking 追踪**：每次锁操作自动创建 Span
- **Micrometer 指标**：锁等待时间、持有时间、成功/失败率
- **日志记录**：DEBUG 级别记录获取/释放，WARN 级别记录超时

---

## 📊 与自定义实现对比

| 维度 | 自定义实现 | Redisson Starter |
|------|-----------|-----------------|
| **开发成本** | 高（每个服务都需实现） | 低（一次开发，全局复用） |
| **代码量** | ~500 行/服务 | 0 行（注解使用） |
| **可维护性** | 低（重复代码） | 高（统一维护） |
| **可观测性** | 需自己实现 | 内置集成 |
| **安全性** | 需自己保证 | 框架保证 |
| **学习曲线** | 中（需学习项目特定实现） | 低（行业标准） |

---

## 🚀 实施时间表

| 里程碑 | 预计完成时间 | 交付物 |
|--------|------------|--------|
| **M1: Starter 开发** | D+4 | patra-spring-boot-starter-redisson 1.0.0 |
| **M2: 业务服务集成** | D+5 | patra-catalog、patra-ingest 使用 |
| **M3: 文档完善** | D+5 | 完整文档、示例代码 |

---

## 📖 相关资源

### 官方文档

- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Redisson Spring Boot Starter](https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter)

### 学习资源

- [分布式锁最佳实践](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Redisson vs Jedis vs Lettuce](https://www.baeldung.com/java-redis-redisson)

### 内部文档

- [Patra 项目架构宪章](../../.specify/memory/constitution.md)
- [Patra 最佳实践](../../.claude/memories/best-practices.md)

---

## 🤝 贡献指南

1. 阅读 [架构设计文档](./architecture-design.md)
2. 按照 [实施计划](./architecture-design.md#九实施计划) 分阶段开发
3. 编写单元测试和集成测试
4. 更新相关文档

---

## 📝 变更日志

### v1.0.0 (2025-11-23)

- ✅ 完成架构设计文档
- ✅ 确定技术选型和实施计划
- 🚧 开始 Starter 模块开发

---

**维护者**: Patra Team
**联系方式**: [项目 Issues](https://github.com/patra/patra-api/issues)
