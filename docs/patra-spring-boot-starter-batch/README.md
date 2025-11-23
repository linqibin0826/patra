# patra-spring-boot-starter-batch 设计文档

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

2. **[迁移指南](./migration-guide.md)**
   - 从自定义批处理到 Spring Batch 的迁移步骤
   - 代码对比和重构建议
   - 数据迁移方案

3. **[模块结构对比](./module-comparison.md)**
   - 当前实现 vs 新方案对比
   - 删除的代码清单
   - 保留的代码清单

4. **[使用示例](./usage-examples.md)**
   - MeSH 数据导入完整示例
   - 文献数据导入示例
   - 常见问题 FAQ

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

### ✅ 已规划功能

- [x] Spring Batch 自动配置
- [x] 分布式锁支持（基于 Redisson）
- [x] 可观测性集成（SkyWalking、Micrometer、日志）
- [x] 断点续传
- [x] Chunk 批次处理
- [x] 重试和跳过策略
- [x] XXL-Job 集成
- [x] Job 编排 DSL

### 🚧 未来优化

- [ ] Spring Batch Admin UI
- [ ] 并行处理（Multi-threaded Step）
- [ ] 分区策略（Partitioning）
- [ ] 实时进度推送（WebSocket）

---

## 🏗️ 整体架构

```
业务服务 (patra-catalog)
├─ Adapter: XXL-Job Handler + REST API
├─ Application: Job 配置和编排
└─ Infrastructure: ItemReader/Writer/Processor
           ↓ 依赖
patra-spring-boot-starter-batch
├─ 自动配置: Spring Batch 核心组件
├─ 分布式锁: @DistributedJobLock 注解
├─ 可观测性: SkyWalking + Micrometer + 日志
└─ 基础组件: JobLauncherHelper 等
           ↓ 依赖
Spring Batch 5.2.x
```

---

## 📊 与现有方案对比

| 维度 | 自定义批处理 | Spring Batch |
|------|------------|-------------|
| **开发成本** | 高（需自己实现所有功能） | 低（开箱即用） |
| **维护成本** | 高（复杂状态机、进度跟踪） | 低（框架维护） |
| **可扩展性** | 低（并行、分区需大量开发） | 高（内置支持） |
| **标准化** | 低（团队特定框架） | 高（行业标准） |
| **可观测性** | 需自己实现 | 内置集成 |
| **学习曲线** | 中（需学习项目特定框架） | 中（需学习 Spring Batch） |

---

## 🚀 实施时间表

| 里程碑 | 预计完成时间 | 交付物 |
|--------|------------|--------|
| **M1: Starter 开发** | D+5 | patra-spring-boot-starter-batch 1.0.0 |
| **M2: MeSH 导入重构** | D+8 | patra-catalog 集成 Spring Batch |
| **M3: 验证和文档** | D+10 | 完整文档、示例代码 |

---

## 📖 相关资源

### 官方文档

- [Spring Batch 官方文档](https://docs.spring.io/spring-batch/docs/5.2.x/reference/html/)
- [Spring Batch GitHub](https://github.com/spring-projects/spring-batch)

### 学习资源

- [Spring Batch 快速入门](https://spring.io/guides/gs/batch-processing/)
- [Spring Batch 最佳实践](https://www.baeldung.com/spring-batch)

### 内部文档

- [Patra 项目架构宪章](../../.specify/memory/constitution.md)
- [Patra 最佳实践](../../troubleshooting-and-notes/best-practices.md)

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
