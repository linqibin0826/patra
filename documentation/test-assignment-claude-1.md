# Claude Code #1 - 测试任务分配清单

> **负责范围**: patra-ingest 微服务全栈测试
> **并行策略**: 同时调度 3 个 test-architect subagents
> **预计工作量**: 8-12 小时
> **优先级**: P1（最高）

---

## 📋 任务概览

你负责完成 **patra-ingest（采集服务）** 的全栈单元测试，包括 Domain、Infrastructure、Application 三个层级。这是项目的核心服务，优先级最高。

### 当前状态

| 层级 | 当前覆盖率 | 目标覆盖率 | 差距 | 状态 |
|------|-----------|-----------|------|------|
| **Domain** | 76% | 80% | +4% | 🟡 接近目标 |
| **Infrastructure** | 6% | 60% | +54% | 🔴 急需提升 |
| **Application** | 0% | 70% | +70% | 🔴 未开始 |

---

## 🎯 阶段 1：完成 Domain 层（1-2 小时）

### 目标
patra-ingest-domain: **76% → 85%**

### 待测试包（按优先级排序）

#### Batch 1A - 高优先级（并行生成）
1. **factory 包** (189 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/factory`
   - 预计测试: ~30 个
   - 预期覆盖: 80%+

2. **policy 包** (117 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/policy`
   - 预计测试: ~20 个
   - 预期覆盖: 80%+

3. **service 包** (84 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/service`
   - 预计测试: ~15 个
   - 预期覆盖: 70%+

#### Batch 1B - 中优先级（并行生成）
4. **expression 包** (101 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/expression`
   - 预计测试: ~18 个
   - 预期覆盖: 70%+

5. **snapshot 包提升** (17% → 60%)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/snapshot`
   - 预计测试: ~25 个
   - 预期覆盖: 60%+

6. **messaging 包提升** (43% → 70%)
   - 路径: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/messaging`
   - 预计测试: ~15 个
   - 预期覆盖: 70%+

### 执行指令

```bash
# 阶段 1：使用 Task tool 并行生成 Batch 1A
Task(subagent_type=test-architect,
     description="生成 factory 包测试",
     prompt="为 patra-ingest-domain/factory 包生成全面单元测试...")

Task(subagent_type=test-architect,
     description="生成 policy 包测试",
     prompt="为 patra-ingest-domain/policy 包生成全面单元测试...")

Task(subagent_type=test-architect,
     description="生成 service 包测试",
     prompt="为 patra-ingest-domain/service 包生成全面单元测试...")

# 验证
mvn clean test jacoco:report -q
# 查看覆盖率报告，确认达到 80%+

# 阶段 2：并行生成 Batch 1B
# 重复上述步骤，生成 expression、snapshot、messaging 包测试
```

---

## 🎯 阶段 2：Infrastructure 层（3-5 小时）

### 目标
patra-ingest-infra: **6% → 65%**

### 待测试包（按优先级排序）

#### Batch 2A - 最高优先级（并行生成）
1. **persistence.repository 包** (2,156 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository`
   - 文件: TaskAggregateRepository, PlanAggregateRepository 等 10 个仓储
   - 预计测试: ~150 个
   - 预期覆盖: 70%+
   - **说明**: 使用 @DataJpaTest 或 @MybatisTest 进行仓储测试

2. **integration.pubmed 包** (613 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/pubmed`
   - 预计测试: ~40 个
   - 预期覆盖: 60%+
   - **说明**: 使用 WireMock 或 MockRestServiceServer 测试 HTTP 集成

3. **integration.registry 包** (275 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/registry`
   - 预计测试: ~30 个
   - 预期覆盖: 60%+
   - **说明**: 测试 Feign 客户端集成

#### Batch 2B - 高优先级（并行生成）
4. **persistence.converter 包** (2,243 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/converter`
   - 预计测试: ~100 个
   - 预期覆盖: 80%+
   - **说明**: MyBatis TypeHandler 测试，纯 Java 单元测试即可

5. **integration.storage 包** (452 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/integration/storage`
   - 预计测试: ~35 个
   - 预期覆盖: 60%+

6. **compiler 包** (184 条指令，0% 覆盖率)
   - 路径: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/compiler`
   - 预计测试: ~25 个
   - 预期覆盖: 70%+

#### Batch 2C - 中优先级（可选）
7. **integration.registry.converter 包** (295 条指令)
8. **config 包** (187 条指令)
9. **integration.storage.acl 包** (158 条指令)

### 执行指令

```bash
# 阶段 1：repository 层测试（最重要）
Task(subagent_type=test-architect,
     description="生成 TaskAggregateRepository 测试",
     prompt="为 TaskAggregateRepository 生成 Spring Data/MyBatis 仓储测试...")

Task(subagent_type=test-architect,
     description="生成 PlanAggregateRepository 测试",
     prompt="为 PlanAggregateRepository 生成仓储测试...")

Task(subagent_type=test-architect,
     description="生成其他 Repository 测试",
     prompt="为剩余 8 个 Repository 生成测试...")

# 阶段 2：integration 层测试
Task(subagent_type=test-architect,
     description="生成 PubMed 集成测试",
     prompt="为 PubMedClient 生成 HTTP 集成测试，使用 WireMock...")

Task(subagent_type=test-architect,
     description="生成 Registry 集成测试",
     prompt="为 RegistryClient 生成 Feign 集成测试...")

Task(subagent_type=test-architect,
     description="生成 Storage 集成测试",
     prompt="为 StorageClient 生成集成测试...")

# 阶段 3：converter 层测试
Task(subagent_type=test-architect,
     description="生成 TypeHandler 测试",
     prompt="为 MyBatis TypeHandler 生成转换器测试...")
```

---

## 🎯 阶段 3：Application 层（3-4 小时）

### 目标
patra-ingest-app: **0% → 70%**

### 待测试包

#### Batch 3A - Orchestrator 测试（并行生成）
1. **任务编排器**
   - 路径: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/orchestrator`
   - 预计文件: TaskOrchestrator, PlanOrchestrator 等
   - 预计测试: ~80 个
   - 预期覆盖: 70%+
   - **说明**: 测试编排逻辑，使用 Mock 仓储和领域服务

2. **命令处理器**
   - 路径: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/command`
   - 预计测试: ~50 个
   - 预期覆盖: 70%+

3. **查询处理器**
   - 路径: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/query`
   - 预计测试: ~40 个
   - 预期覆盖: 70%+

#### Batch 3B - 事件处理器（并行生成）
4. **事件处理器**
   - 路径: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/eventhandler`
   - 预计测试: ~30 个
   - 预期覆盖: 60%+

5. **DTO 转换器**
   - 路径: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/assembler`
   - 预计测试: ~25 个
   - 预期覆盖: 80%+

### 执行指令

```bash
# 先探索 app 层结构
Task(subagent_type=Explore,
     description="探索 patra-ingest-app 结构",
     prompt="分析 patra-ingest-app 包结构，识别 Orchestrator、Command、Query 等...")

# 并行生成 Orchestrator 测试
Task(subagent_type=test-architect,
     description="生成 TaskOrchestrator 测试",
     prompt="为 TaskOrchestrator 生成编排器测试，Mock 仓储和领域服务...")

Task(subagent_type=test-architect,
     description="生成 PlanOrchestrator 测试",
     prompt="为 PlanOrchestrator 生成编排器测试...")

Task(subagent_type=test-architect,
     description="生成 Command 处理器测试",
     prompt="为 Command 处理器生成测试...")
```

---

## ✅ 测试规范要求

### 必须遵循的规范
1. **Domain 层**: 纯 Java 单元测试，不使用 @SpringBootTest
2. **Infrastructure 层**:
   - Repository: 使用 @DataJpaTest 或 @MybatisTest
   - Integration: 使用 WireMock 或 TestContainers
   - Converter: 纯 Java 单元测试
3. **Application 层**: 使用 @ExtendWith(MockitoExtension.class)，Mock 仓储和端口

### 测试模式
- JUnit 5 + AssertJ
- @Nested 分组 + 中文 @DisplayName
- Given-When-Then 结构
- 边界条件 + 业务场景测试

---

## 📊 验证与报告

### 每个阶段完成后

```bash
# 1. 运行测试
cd patra-ingest/patra-ingest-[domain|infra|app]
mvn clean test jacoco:report -q

# 2. 查看覆盖率
cat target/site/jacoco/index.html

# 3. 确认达到目标覆盖率
# Domain: ≥ 80%
# Infra: ≥ 60%
# App: ≥ 70%
```

### 最终报告

完成所有阶段后，生成覆盖率总结报告，提交到 Git。

---

## 🎯 预期成果

### 覆盖率目标
- **patra-ingest-domain**: 76% → **85%** ✅
- **patra-ingest-infra**: 6% → **65%** ✅
- **patra-ingest-app**: 0% → **70%** ✅

### 测试数量预估
- Domain 层新增: ~120 个测试
- Infra 层新增: ~400 个测试
- App 层新增: ~200 个测试
- **总计**: ~720 个测试

### 时间规划
- 阶段 1 (Domain): 1-2 小时
- 阶段 2 (Infra): 3-5 小时
- 阶段 3 (App): 3-4 小时
- **总计**: 8-12 小时

---

## 📝 注意事项

1. **并行执行**: 每个阶段的 Batch 使用 3 个 test-architect 并行生成
2. **增量验证**: 每个 Batch 完成后立即运行测试验证
3. **问题修复**: 遇到编译或测试失败，立即修复后再继续
4. **覆盖率监控**: 实时监控覆盖率增长，确保达标

祝顺利完成任务！🚀
