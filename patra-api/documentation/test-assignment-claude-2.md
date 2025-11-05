# Claude Code #2 - 测试任务分配清单

> **负责范围**: patra-registry 微服务全栈测试
> **并行策略**: 同时调度 3 个 test-architect subagents
> **预计工作量**: 6-10 小时
> **优先级**: P2（高）

---

## 📋 任务概览

你负责完成 **patra-registry（注册中心服务）** 的全栈单元测试，包括 Infrastructure、Application、Adapter 三个层级。Domain 层已达标（87%），无需额外测试。

### 当前状态

| 层级 | 当前覆盖率 | 目标覆盖率 | 差距 | 状态 |
|------|-----------|-----------|------|------|
| **Domain** | 87% | 80% | - | ✅ 已达标 |
| **Infrastructure** | 0% | 60% | +60% | 🔴 未开始 |
| **Application** | 0% | 70% | +70% | 🔴 未开始 |
| **Adapter** | 0% | 50% | +50% | 🔴 未开始 |

---

## 🎯 阶段 1：Infrastructure 层（2-4 小时）

### 目标
patra-registry-infra: **0% → 65%**

### 待测试包（按优先级排序）

#### Batch 1A - Repository 层（并行生成）
1. **persistence.repository 包**
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/persistence/repository`
   - 预计文件: ProvenanceConfigurationRepository, ExprTemplateRepository 等
   - 预计测试: ~100 个
   - 预期覆盖: 70%+
   - **说明**: MyBatis 仓储测试，使用 @MybatisTest

2. **persistence.mapper 包**
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/persistence/mapper`
   - 预计文件: ProvenanceMapper, ExprMapper 等
   - 预计测试: ~80 个
   - 预期覆盖: 80%+
   - **说明**: MyBatis Mapper 接口测试

3. **persistence.converter 包**
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/persistence/converter`
   - 预计测试: ~60 个
   - 预期覆盖: 80%+
   - **说明**: TypeHandler 类型转换器测试，纯 Java 单元测试

#### Batch 1B - Integration 层（并行生成）
4. **integration.cache 包**（如果存在）
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/integration/cache`
   - 预计测试: ~30 个
   - 预期覆盖: 60%+
   - **说明**: Redis 缓存集成测试，使用 Embedded Redis 或 TestContainers

5. **config 包**
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/config`
   - 预计测试: ~20 个
   - 预期覆盖: 50%+
   - **说明**: 配置类测试，验证 Bean 加载

6. **acl 包**（防腐层，如果存在）
   - 路径: `patra-registry/patra-registry-infra/src/main/java/com/patra/registry/infra/acl`
   - 预计测试: ~25 个
   - 预期覆盖: 60%+

### 执行指令

```bash
# 先探索 infra 层结构
Task(subagent_type=Explore,
     description="探索 patra-registry-infra 结构",
     prompt="分析 patra-registry-infra 包结构，识别 Repository、Mapper、Converter...")

# 阶段 1：并行生成 Repository 测试
Task(subagent_type=test-architect,
     description="生成 ProvenanceConfigurationRepository 测试",
     prompt="为 ProvenanceConfigurationRepository 生成 MyBatis 仓储测试，使用 @MybatisTest...")

Task(subagent_type=test-architect,
     description="生成 ExprTemplateRepository 测试",
     prompt="为 ExprTemplateRepository 生成仓储测试...")

Task(subagent_type=test-architect,
     description="生成 Mapper 接口测试",
     prompt="为 MyBatis Mapper 接口生成 SQL 映射测试...")

# 阶段 2：并行生成 Converter 和 Integration 测试
Task(subagent_type=test-architect,
     description="生成 TypeHandler 测试",
     prompt="为 MyBatis TypeHandler 生成类型转换测试...")

Task(subagent_type=test-architect,
     description="生成 Cache 集成测试",
     prompt="为 Redis 缓存生成集成测试，使用 TestContainers...")

Task(subagent_type=test-architect,
     description="生成 Config 测试",
     prompt="为配置类生成 Bean 加载测试...")

# 验证
cd patra-registry/patra-registry-infra
mvn clean test jacoco:report -q
```

---

## 🎯 阶段 2：Application 层（2-3 小时）

### 目标
patra-registry-app: **0% → 70%**

### 待测试包

#### Batch 2A - Service 层（并行生成）
1. **应用服务**
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/service`
   - 预计文件: ProvenanceConfigService, ExprTemplateService 等
   - 预计测试: ~80 个
   - 预期覆盖: 70%+
   - **说明**: 应用服务测试，Mock 仓储和领域对象

2. **命令处理器**
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/command`
   - 预计测试: ~50 个
   - 预期覆盖: 70%+
   - **说明**: CQRS 命令处理器测试

3. **查询处理器**
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/query`
   - 预计测试: ~60 个
   - 预期覆盖: 75%+
   - **说明**: CQRS 查询处理器测试

#### Batch 2B - Assembler 和 Validator（并行生成）
4. **DTO 组装器**
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/assembler`
   - 预计测试: ~40 个
   - 预期覆盖: 80%+
   - **说明**: DTO 转换器测试，纯 Java 单元测试

5. **校验器**
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/validator`
   - 预计测试: ~35 个
   - 预期覆盖: 80%+
   - **说明**: 业务规则校验器测试

6. **事件处理器**（如果存在）
   - 路径: `patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/eventhandler`
   - 预计测试: ~25 个
   - 预期覆盖: 60%+

### 执行指令

```bash
# 探索 app 层结构
Task(subagent_type=Explore,
     description="探索 patra-registry-app 结构",
     prompt="分析 patra-registry-app 包结构，识别 Service、Command、Query...")

# 并行生成 Service 层测试
Task(subagent_type=test-architect,
     description="生成 ProvenanceConfigService 测试",
     prompt="为 ProvenanceConfigService 生成应用服务测试，Mock 仓储...")

Task(subagent_type=test-architect,
     description="生成 ExprTemplateService 测试",
     prompt="为 ExprTemplateService 生成应用服务测试...")

Task(subagent_type=test-architect,
     description="生成 Command/Query 处理器测试",
     prompt="为 CQRS 处理器生成测试...")

# 并行生成 Assembler 测试
Task(subagent_type=test-architect,
     description="生成 DTO Assembler 测试",
     prompt="为 DTO 组装器生成转换测试...")

Task(subagent_type=test-architect,
     description="生成 Validator 测试",
     prompt="为业务校验器生成测试...")

# 验证
cd patra-registry/patra-registry-app
mvn clean test jacoco:report -q
```

---

## 🎯 阶段 3：Adapter 层（2-3 小时）

### 目标
patra-registry-adapter: **0% → 55%**

### 待测试包

#### Batch 3A - REST Controller（并行生成）
1. **REST 控制器**
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/controller`
   - 预计文件: ProvenanceConfigController, ExprTemplateController 等
   - 预计测试: ~80 个
   - 预期覆盖: 60%+
   - **说明**: 使用 @WebMvcTest 测试 REST API

2. **Feign 客户端**（如果存在）
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/feign`
   - 预计测试: ~30 个
   - 预期覆盖: 50%+
   - **说明**: Feign Client 测试

3. **DTO 和转换器**
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/dto`
   - 预计测试: ~40 个
   - 预期覆盖: 60%+
   - **说明**: API DTO 转换测试

#### Batch 3B - Event Listener（并行生成，如果存在）
4. **事件监听器**
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/listener`
   - 预计测试: ~25 个
   - 预期覆盖: 50%+
   - **说明**: RocketMQ/Kafka 监听器测试

5. **定时任务**（如果存在）
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/scheduler`
   - 预计测试: ~20 个
   - 预期覆盖: 40%+

6. **异常处理器**
   - 路径: `patra-registry/patra-registry-adapter/src/main/java/com/patra/registry/adapter/exception`
   - 预计测试: ~15 个
   - 预期覆盖: 60%+

### 执行指令

```bash
# 探索 adapter 层结构
Task(subagent_type=Explore,
     description="探索 patra-registry-adapter 结构",
     prompt="分析 patra-registry-adapter 包结构，识别 Controller、Feign、Listener...")

# 并行生成 Controller 测试
Task(subagent_type=test-architect,
     description="生成 ProvenanceConfigController 测试",
     prompt="为 ProvenanceConfigController 生成 @WebMvcTest REST API 测试...")

Task(subagent_type=test-architect,
     description="生成 ExprTemplateController 测试",
     prompt="为 ExprTemplateController 生成 REST API 测试...")

Task(subagent_type=test-architect,
     description="生成 DTO 转换器测试",
     prompt="为 API DTO 转换器生成测试...")

# 并行生成 Feign 和 Listener 测试
Task(subagent_type=test-architect,
     description="生成 Feign Client 测试",
     prompt="为 Feign 客户端生成测试...")

Task(subagent_type=test-architect,
     description="生成 Event Listener 测试",
     prompt="为事件监听器生成测试...")

# 验证
cd patra-registry/patra-registry-adapter
mvn clean test jacoco:report -q
```

---

## ✅ 测试规范要求

### 测试类型
1. **Infrastructure 层**:
   - Repository/Mapper: @MybatisTest
   - Converter: 纯 Java 单元测试
   - Cache: TestContainers + Embedded Redis
   - Config: @SpringBootTest(classes = {ConfigClass.class})

2. **Application 层**:
   - Service: @ExtendWith(MockitoExtension.class)
   - Command/Query: Mock 仓储
   - Assembler/Validator: 纯 Java 单元测试

3. **Adapter 层**:
   - Controller: @WebMvcTest
   - Feign: @FeignClient 测试
   - Listener: @SpringBootTest + MockMessageProducer
   - DTO: 纯 Java 单元测试

### 测试模式
- JUnit 5 + AssertJ + Mockito
- @Nested 分组 + 中文 @DisplayName
- Given-When-Then 结构

---

## 📊 验证与报告

### 每个阶段完成后

```bash
# 1. 运行测试
cd patra-registry/patra-registry-[infra|app|adapter]
mvn clean test jacoco:report -q

# 2. 查看覆盖率
cat target/site/jacoco/index.html

# 3. 确认达到目标覆盖率
# Infra: ≥ 60%
# App: ≥ 70%
# Adapter: ≥ 50%
```

---

## 🎯 预期成果

### 覆盖率目标
- **patra-registry-infra**: 0% → **65%** ✅
- **patra-registry-app**: 0% → **70%** ✅
- **patra-registry-adapter**: 0% → **55%** ✅

### 测试数量预估
- Infra 层新增: ~300 个测试
- App 层新增: ~250 个测试
- Adapter 层新增: ~200 个测试
- **总计**: ~750 个测试

### 时间规划
- 阶段 1 (Infra): 2-4 小时
- 阶段 2 (App): 2-3 小时
- 阶段 3 (Adapter): 2-3 小时
- **总计**: 6-10 小时

---

## 📝 注意事项

1. **Domain 层已达标**: 无需额外测试，聚焦 Infra/App/Adapter
2. **并行执行**: 每个 Batch 使用 3 个 test-architect 并行生成
3. **增量验证**: 每个 Batch 完成后立即运行测试
4. **依赖管理**: 注意 Spring Test 依赖版本

祝顺利完成任务！🚀
