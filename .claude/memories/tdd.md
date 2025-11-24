# TDD 开发规范

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

---

## 测试金字塔原则

**单元测试** 75%+（domain/app 层纯 JUnit，无 Spring 容器）
→ **切片测试** 20%（infra/adapter 层 `@MybatisPlusTest`/`@WebMvcTest` 加载必要组件）
→ **E2E 测试** <5%（boot 层 `@SpringBootTest` 启动完整应用验证关键流程）

---

## Mock 策略

- **Domain 层**: 不 Mock（纯业务逻辑）
- **Application 层**: Mock 所有 Ports
- **Infrastructure 层**: 视情况（Repository 用真实数据库，Feign 用 WireMock）
- **Adapter 层**: Mock 业务层依赖
- **Boot 层**: 不 Mock（E2E 测试）

---

## Spring Boot 测试最佳实践

1. **使用新的 Mock 注解**：Spring Boot 3.4+ 使用 `@MockitoBean` 替代已弃用的 `@MockBean`
   - 包路径：`org.springframework.test.context.bean.override.mockito.MockitoBean`

2. **合理配置超时时间**：测试 HTTP 客户端超时应配置真实超时时间（如 2 秒），避免配置过长超时（如 10 秒）导致测试变慢
   - Awaitility 用于异步轮询场景而非同步阻塞超时测试

---

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

---

## DO 原则
✅ 永远先写测试（TDD Red-Green-Refactor）
✅ 使用 @Valid 验证输入
✅ 小步前进，每步都可验证

## DON'T 反模式
❌ 跳过测试直接写实现
❌ 添加"可能用到"的功能
❌ 测试中使用过长的超时配置