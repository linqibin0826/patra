# CLAUDE.md

---

## 🎯 项目原则

### 项目特性
- **全新代码库（Greenfield）**：无历史包袱，无兼容性约束
- **单人开发**：技术决策自由，追求完美实现
- **质量至上**：不妥协，直接采用最优方案

### 实施要求
✅ **必须**：
- 一次性实现最终方案，不做渐进式开发
- 发现更好方案立即替换，不保留旧实现
- 以技术卓越为唯一决策标准

❌ **禁止**：
- 考虑向后兼容、数据迁移、版本管理
- 因时间或资源限制而采用次优方案
- 使用 deprecated 标记（直接删除或重写）

---

# 第一部分：核心规则

## 快速参考

### 你的角色

**角色：系统架构师/高级 Java 开发者**

技术能力：精通六边形架构 + DDD，熟练使用 Spring Boot/Cloud 技术栈。

### 核心原则

**✅ 应该做**

- 🚨 **[强制]** 用户不一定是对的, 用户也可能犯错, 要以实际的代码为准, 发现问题要主动指出并提供改进建议
- 🚨 **[强制]** 每个模块都有文档，你要阅读模块 README.md 与 包的 package-info.java
- 🚨 **[强制]** 信息不足时先查看相关代码/文档，没答案再提问（不要猜测，不要直接问）
- 🚨 **[强制]** 三次失败必须转换策略（启动subagents 进行调研，如web、context7等）
- 🚨 **[强制]** 处理任务时，必须主动加载对应的 Skills（使用 Skill 工具）
- [推荐] 主动使用 MCP 工具 (serena, sequential-thinking, context7)

**❌ 不应该做**

- 🚨 **[禁止]** 向 domain 层添加框架依赖

---

# 第二部分：TDD 开发流程

## 核心理念

**测试不是验证代码的工具，而是驱动设计的引擎**

### TDD 循环（Red-Green-Refactor）

```
🔴 Red    → 写一个失败的测试（定义期望行为）
🟢 Green  → 用最简单的方式让测试通过
🔵 Refactor → 在测试保护下优化代码
```

### 开发原则

✅ **必须**：
- 永远先写测试，再写实现
- 小步前进，每步都可验证
- 只写让测试通过的代码（YAGNI）

❌ **禁止**：
- 跳过 Red 阶段直接写实现
- 添加"可能用到"的功能

---

# 第三部分：六边形架构规范

## 层级职责与测试策略

### Domain 层（核心业务逻辑）
- **职责**: 纯业务逻辑，不依赖任何框架
- **测试**: 纯单元测试，无 Mock，毫秒级反馈
- **位置**: `patra-{service}-domain/src/test/java/`

### Application 层（编排逻辑）
- **职责**: 编排领域服务，管理事务边界
- **测试**: Mock 所有 Port 接口（Repository、EventPublisher）
- **位置**: `patra-{service}-app/src/test/java/`
- **注意**: @Transactional 只在此层

### Infrastructure 层（技术实现）
- **职责**: 实现领域端口（Repository、外部服务调用、消息发布）
- **测试**:
  - Repository: @MybatisPlusTest + TestContainers
  - Feign Client: 单元测试 + WireMock
  - Converter: 纯单元测试
- **位置**: `patra-{service}-infra/src/test/java/`

### Adapter 层（外部接口）
- **职责**: HTTP 接口、消息监听、定时任务
- **测试**:
  - Controller: @WebMvcTest + MockMvc
  - Listener/Job: Mock 业务层依赖
- **位置**: `patra-{service}-adapter/src/test/java/`

### Boot 层（应用启动）
- **职责**: 配置和启动应用
- **测试**: E2E 测试，真实中间件集成
- **位置**: `patra-{service}-boot/src/test/java/`

## 依赖规则

- **Domain 层不依赖任何层**
- Application 层只依赖 Domain 层
- Infrastructure 层实现 Domain 端口
- Adapter 层调用 Application 层

---

# 第四部分：项目信息

## 项目概览

**Patra** — 医学文献数据平台，采集 10+ 数据源 (PubMed, EPMC 等)。使用 `patra-registry` 作为 Provenance 配置、字典、元数据的单一事实来源 (SSOT)。
**架构**: SpringBoot 微服务 + 六边形架构 + DDD + domain 事件驱动
**技术栈**: Java 25 | Spring Boot 3.5.7 + Cloud 2025.0.0 | Maven | MyBatis-Plus + MapStruct | Nacos

## 代码库结构

**仓库**: `patra-parent`, `patra-common`, `patra-*`, `patra-spring-boot-starter-*`, `docker/`
**微服务模块**: `patra-{service}-boot` (入口), `-api` (对其他模块的契约和接口), `-domain` (纯 Java), `-app` (编排器), `-infra` (仓储), `-adapter` (控制器/定时任务)

## 资源

**文档**: 每个 `patra-*` 模块中的模块特定 `README.md` 文件, 包中存在 `package-info.java`

---

# 第五部分：开发实践

## 最佳实践

### DO 原则
✅ 永远先写测试（TDD Red-Green-Refactor）
✅ Domain 层保持纯 Java，无框架依赖
✅ Application 层管理事务边界（@Transactional）
✅ 使用 MapStruct 进行对象转换
✅ 通过 Port 和 Repository 接口定义依赖
✅ 使用 @Valid 验证输入

### DON'T 反模式
❌ 跳过测试直接写实现
❌ 在 Domain 层使用 Spring 注解
❌ 跨层直接调用（如 Controller 直接调用 Repository）
❌ 在 Controller 层处理业务逻辑
❌ 硬编码配置值

## Mock 策略

- **Domain 层**: 不 Mock（纯业务逻辑）
- **Application 层**: Mock 所有 Ports
- **Infrastructure 层**: 视情况（Repository 用真实数据库，Feign 用 WireMock）
- **Adapter 层**: Mock 业务层依赖
- **Boot 层**: 不 Mock（E2E 测试）

## 开发检查清单

### 开始新功能
```
[ ] 理解需求，明确期望行为
[ ] 确定功能层级（Domain/Application/Infrastructure/Adapter）
[ ] 规划测试顺序，从最简单开始
```

### TDD 循环
```
🔴 Red:
[ ] 写失败的测试
[ ] 运行确认失败

🟢 Green:
[ ] 最简实现让测试通过
[ ] 运行所有测试确保没破坏已有功能

🔵 Refactor:
[ ] 改善代码质量（DRY、命名、结构）
[ ] 运行测试确保重构正确
```

### 完成后
```
[ ] 架构合规检查（依赖方向、层次边界）
[ ] 补充边界条件测试
[ ] 运行所有测试最终验证
```

