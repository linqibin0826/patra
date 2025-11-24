# Claude Code 用户提示词模板

> 用于启动可观测性架构实施任务的标准提示词

---

## 📋 基础提示词（完整版）

```
你好 Jobs！

我需要你帮我实施 Patra 可观测性架构方案。

**项目背景**：
- 项目：Patra 医学文献数据平台
- 架构：微服务 + 六边形架构 + DDD
- 项目类型：绿地项目（无历史包袱，可破坏性重构）
- 开发模式：单人开发，质量优先，无时间压力

**实施计划位置**：
请先阅读 `/Users/linqibin/Desktop/Patra-api/docs/observability/implementation-plan.md`

**当前状态**：
- 设计方案已完成（docs/observability 目录下有完整文档）
- 实施计划已制定（v1.1.0，实施中）
- 已完成工作：阶段 0（先决条件验证）、阶段 1（PoC 性能测试）
- 当前阶段：阶段 1 - 创建 patra-starter-observability（进行中）
- 整体进度：17% (3.5/20.5 天)

**你的任务**：
根据实施计划文档，帮我执行可观测性架构的实施工作。

**执行要求**：
1. 严格遵循 CLAUDE.md 和 best-practices.md 中的规则
2. 每次执行前先阅读实施计划，了解当前进度
3. 每次完成任务后更新实施计划中的检查清单
4. 遇到问题先查看 troubleshooting-and-notes/ 目录
5. 遵循三次原则：同一问题尝试 3 次后必须改变策略

**请从以下内容开始**：
1. 阅读实施计划文档
2. 告诉我当前应该执行哪个阶段的哪些任务
3. 等待我确认后开始执行
```

---

## 📋 简化提示词（快速版）

如果你已经确认了实施方案，可以使用这个简化版本：

```
Jobs，继续实施 Patra 可观测性架构。

请查看 `/Users/linqibin/Desktop/Patra-api/docs/observability/implementation-plan.md`，告诉我当前进度和下一步任务。
```

---

## 📋 阶段性提示词（按阶段使用）

> **注**：阶段 0（先决条件验证）和原阶段 1（PoC 性能测试）已完成，以下为剩余阶段。

### 阶段 1：创建 observability starter

```
Jobs，开始执行阶段 1：创建 patra-spring-boot-starter-observability。

请查看实施计划的"阶段 1"部分，按照任务清单逐步创建可观测性 Starter 模块。

重点任务：
- 实现 SensitiveDataObservationFilter（P0-5，敏感数据脱敏）
- 实现 ObservationResolutionInterceptor（适配现有的 ResolutionInterceptor）
```

### 阶段 2：重构现有 Starters

```
Jobs，开始执行阶段 2：重构现有 Starters。

请查看实施计划的"阶段 2"部分，从以下模块中移除可观测性代码：
1. patra-starter-core（保留 ResolutionInterceptor 接口）
2. patra-starter-rest-client（新增 ClientInterceptor 扩展点）
3. patra-starter-batch

关键原则：
- 保留扩展点接口
- 删除 Micrometer 依赖
- 验证依赖方向正确
```

### 阶段 3：安全加固

```
Jobs，开始执行阶段 3：安全加固。

这是 P0 任务的最后阶段。请查看实施计划的"阶段 3"部分，完成：
1. P0-3：实现 ArchUnit 架构测试
2. P0-6：配置 Actuator 访问控制
3. SkyWalking Agent 安全配置

所有 P0 任务完成后才能进入生产环境。
```

### 阶段 4：集成与验证

```
Jobs，开始执行阶段 4：集成与验证。

请查看实施计划的"阶段 4"部分，将 observability starter 集成到各服务并验证功能。

验证清单：
- 追踪链路正常
- 指标收集正常
- 敏感数据脱敏生效
- 性能符合预期
```

### 阶段 5：文档更新

```
Jobs，开始执行阶段 5：文档更新。

请查看实施计划的"阶段 5"部分，更新所有相关文档：
- 模块 README.md
- package-info.java
- 配置示例
- 故障排查指南
```

### 阶段 6：性能优化与最终验证

```
Jobs，开始执行阶段 6：性能优化与最终验证。

这是最后阶段。请查看实施计划的"阶段 6"部分，完成：
1. 性能调优
2. 生产就绪检查清单验证
3. 实施总结报告

完成后项目即可进入生产环境。
```

---

## 📋 问题排查提示词

如果在实施过程中遇到问题：

```
Jobs，我在执行 [具体任务] 时遇到问题：

[描述问题]

请帮我：
1. 先查看 /Users/linqibin/Desktop/Patra-api/troubleshooting-and-notes/ 是否有类似问题
2. 如果没有，分析问题原因
3. 提供解决方案（遵循三次原则）
4. 如果问题解决，将解决方案记录到 troubleshooting-and-notes/
```

---

## 📋 进度查询提示词

查询当前实施进度：

```
Jobs，请查看实施计划，告诉我：
1. 当前完成到哪个阶段
2. 当前阶段的完成度
3. 下一步应该做什么
4. 是否有阻塞问题
```

---

## 📋 更新进度提示词

手动更新进度（如果 Jobs 忘记更新）：

```
Jobs，我已经完成了 [具体任务]，请更新实施计划中的对应检查清单。

完成的任务：
- [ ] 任务 1
- [ ] 任务 2
- [ ] 任务 3
```

---

## 🎯 使用建议

### 最佳实践

1. **每次新会话开始时**：
   - 使用"基础提示词（完整版）"
   - 让 Jobs 先阅读实施计划
   - 确认当前进度后再开始

2. **继续之前的工作时**：
   - 使用"简化提示词（快速版）"
   - 或直接使用对应阶段的提示词

3. **遇到问题时**：
   - 使用"问题排查提示词"
   - 遵循三次原则
   - 记录解决方案到 troubleshooting-and-notes/

4. **定期检查进度**：
   - 使用"进度查询提示词"
   - 确保实施计划与实际进度同步

### 注意事项

⚠️ **每次使用提示词前**：
- 确保实施计划文档是最新的
- 如果有手动修改代码，先告知 Jobs
- 如果有外部因素变化（如依赖版本升级），及时更新实施计划

⚠️ **与 Jobs 沟通时**：
- 明确指出当前所在阶段
- 如果跳过某些步骤，说明原因
- 如果调整实施顺序，更新实施计划

⚠️ **关键决策点**：
- 阶段 3 结束时：P0 任务完成检查（所有 P0 任务必须完成）
- 阶段 6 结束时：生产就绪最终验证

---

## 📝 提示词模板变量说明

在使用提示词时，替换以下占位符：

- `[具体任务]`：你正在执行的具体任务名称
- `[描述问题]`：详细描述遇到的问题
- `[阶段编号]`：当前所在阶段（1-6）

---

## 🔗 相关文档

- 实施计划：`/Users/linqibin/Desktop/Patra-api/docs/observability/implementation-plan.md`
- 设计方案：`/Users/linqibin/Desktop/Patra-api/docs/observability/observability-starter-design.md`
- 重构指南：`/Users/linqibin/Desktop/Patra-api/docs/observability/plugin-architecture-refactoring-guide.md`
- 配置示例：`/Users/linqibin/Desktop/Patra-api/docs/observability/observability-config-examples.yaml`

---

**祝实施顺利！** 🚀
