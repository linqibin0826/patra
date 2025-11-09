# 测试覆盖率检查清单

**目的**: 验证测试覆盖率和测试质量
**创建日期**: [日期]
**功能**: [链接到 spec.md]

**说明**: 此检查清单基于 Patra 项目的分层测试策略，确保不同层次的代码都有适当的测试覆盖。

---

## 📊 测试覆盖率目标

### 覆盖率要求

- [ ] **CHK-COV-001**: Domain 层单元测试覆盖率 ≥ 80%
  - 当前覆盖率: [%]
  - 未覆盖的关键代码: [列出]

- [ ] **CHK-COV-002**: Application 层单元测试覆盖率 ≥ 70%
  - 当前覆盖率: [%]
  - 未覆盖的关键代码: [列出]

- [ ] **CHK-COV-003**: Infrastructure 层有 IT 集成测试
  - IT 测试数量: [数量]
  - 覆盖的 Repository: [列出]

- [ ] **CHK-COV-004**: Adapter 层有 E2E 测试
  - E2E 测试数量: [数量]
  - 覆盖的 API: [列出]

---

## 🏛️ Domain 层测试

### 聚合根测试

- [ ] **CHK-DOMAIN-001**: 所有聚合根有单元测试
  - 聚合根列表: [列出所有聚合根]
  - [ ] 测试状态转换
  - [ ] 测试业务规则验证
  - [ ] 测试领域事件发布

- [ ] **CHK-DOMAIN-002**: 聚合根测试无 Spring 依赖
  - [ ] 不使用 `@SpringBootTest`
  - [ ] 不使用 `@Autowired`
  - [ ] 纯 JUnit 5 + AssertJ

### 值对象测试

- [ ] **CHK-DOMAIN-003**: 所有值对象有单元测试
  - 值对象列表: [列出所有值对象]
  - [ ] 测试构造器验证
  - [ ] 测试相等性判断
  - [ ] 测试不可变性

### 领域服务测试

- [ ] **CHK-DOMAIN-004**: 所有领域服务有单元测试
  - 领域服务列表: [列出所有领域服务]
  - [ ] 测试业务逻辑
  - [ ] 测试跨聚合操作

### 测试命名

- [ ] **CHK-DOMAIN-005**: 测试方法命名清晰
  - [ ] 使用 `should_xxx_when_yyy` 格式
  - [ ] 例如: `should_emit_event_when_update_metadata()`

### 测试组织

- [ ] **CHK-DOMAIN-006**: 测试结构清晰
  - [ ] 使用 Given-When-Then 结构
  - [ ] 每个测试只验证一个场景
  - [ ] 避免测试之间的依赖

---

## 🎯 Application 层测试

### Orchestrator / Coordinator 测试

- [ ] **CHK-APP-001**: 所有 Orchestrator/Coordinator 有单元测试
  - Orchestrator 列表: [列出]
  - Coordinator 列表: [列出]
  - [ ] 测试用例编排逻辑
  - [ ] 测试事务边界

- [ ] **CHK-APP-002**: Mock 外部依赖
  - [ ] 使用 Mockito Mock Repository
  - [ ] 使用 @MockitoBean（不使用 @MockBean）
  - [ ] Mock 外部服务网关

- [ ] **CHK-APP-003**: 测试异常处理
  - [ ] 测试失败场景
  - [ ] 验证异常类型
  - [ ] 验证事务回滚

### Assembler 测试

- [ ] **CHK-APP-004**: DTO 组装器有单元测试
  - [ ] 测试 DTO 组装逻辑
  - [ ] 测试 null 值处理

---

## 🔧 Infrastructure 层测试

### Repository IT 测试

- [ ] **CHK-INFRA-001**: 所有 Repository 实现有 IT 测试
  - Repository 列表: [列出所有 Repository]
  - [ ] 使用 `@SpringBootTest`
  - [ ] 使用 TestContainers 启动真实数据库
  - [ ] 测试文件命名: `*RepositoryIT.java`

- [ ] **CHK-INFRA-002**: IT 测试覆盖 CRUD 操作
  - [ ] 测试 save() 方法
  - [ ] 测试 findById() 方法
  - [ ] 测试 findAll() / findByXxx() 方法
  - [ ] 测试 update() 方法
  - [ ] 测试 delete() 方法

- [ ] **CHK-INFRA-003**: IT 测试验证 SQL 正确性
  - [ ] 验证查询结果
  - [ ] 验证数据库状态
  - [ ] 验证关联关系

### Mapper 测试

- [ ] **CHK-INFRA-004**: MyBatis Mapper 有 IT 测试
  - [ ] 测试自定义 SQL
  - [ ] 验证返回结果
  - [ ] 验证参数绑定

### Converter 测试

- [ ] **CHK-INFRA-005**: MapStruct Converter 有单元测试
  - [ ] 测试映射完整性
  - [ ] 测试 null 值处理
  - [ ] 测试复杂映射规则

### TestContainers 配置

- [ ] **CHK-INFRA-006**: TestContainers 配置正确
  - [ ] 使用正确的数据库版本（如 MySQL 8）
  - [ ] 容器启动成功
  - [ ] 容器在测试结束后关闭

---

## 🔌 Adapter 层测试

### Controller E2E 测试

- [ ] **CHK-ADAPTER-001**: 所有 Controller 有 E2E 测试
  - Controller 列表: [列出所有 Controller]
  - [ ] 使用 MockMvc 测试 REST API
  - [ ] 测试文件命名: `*ControllerE2ETest.java` 或 `*ControllerIT.java`
  - [ ] 位置: `patra-{service}-boot/src/test/java/`

- [ ] **CHK-ADAPTER-002**: E2E 测试覆盖所有 API
  - [ ] 测试 GET 请求
  - [ ] 测试 POST 请求
  - [ ] 测试 PUT 请求
  - [ ] 测试 DELETE 请求

- [ ] **CHK-ADAPTER-003**: E2E 测试验证 HTTP 响应
  - [ ] 验证状态码（200/201/400/404/500）
  - [ ] 验证响应体结构
  - [ ] 验证错误消息

- [ ] **CHK-ADAPTER-004**: E2E 测试验证业务逻辑
  - [ ] 验证数据库状态
  - [ ] 验证领域事件发布（如需要）
  - [ ] 端到端验证完整流程

### Listener 测试

- [ ] **CHK-ADAPTER-005**: 事件监听器有集成测试
  - [ ] 测试消息消费
  - [ ] 验证业务逻辑执行
  - [ ] 测试异常处理

### Job 测试

- [ ] **CHK-ADAPTER-006**: 定时任务有集成测试
  - [ ] 测试任务执行逻辑
  - [ ] 验证任务结果
  - [ ] 测试异常处理

---

## 🧪 测试质量

### 测试独立性

- [ ] **CHK-QUALITY-001**: 测试之间独立
  - [ ] 测试可以单独运行
  - [ ] 测试顺序不影响结果
  - [ ] 使用 `@Transactional` 或手动清理数据

### 测试可读性

- [ ] **CHK-QUALITY-002**: 测试代码清晰易懂
  - [ ] 测试方法名描述测试场景
  - [ ] 使用 Given-When-Then 注释
  - [ ] 断言清晰明确

### 测试数据

- [ ] **CHK-QUALITY-003**: 测试数据合理
  - [ ] 使用测试数据构建器（Builder 模式）
  - [ ] 避免魔法数字
  - [ ] 测试数据有意义

### 断言

- [ ] **CHK-QUALITY-004**: 断言充分
  - [ ] 使用 AssertJ 流式断言
  - [ ] 验证关键属性
  - [ ] 验证异常消息

---

## 🔬 测试工具和框架

### JUnit 5

- [ ] **CHK-TOOL-001**: 使用 JUnit 5
  - [ ] 使用 `@Test` 注解（org.junit.jupiter.api）
  - [ ] 使用 `@BeforeEach` / `@AfterEach`
  - [ ] 使用 `@DisplayName` 描述测试（可选）

### Mockito

- [ ] **CHK-TOOL-002**: 正确使用 Mockito
  - [ ] 使用 `@MockitoBean` 替代 `@MockBean`
  - [ ] 使用 `when().thenReturn()` 模拟行为
  - [ ] 使用 `verify()` 验证交互

### AssertJ

- [ ] **CHK-TOOL-003**: 使用 AssertJ 断言
  - [ ] 使用流式断言（`assertThat()`）
  - [ ] 使用专用断言（如 `hasSize()`, `containsExactly()`）
  - [ ] 断言清晰易读

### TestContainers

- [ ] **CHK-TOOL-004**: 正确使用 TestContainers
  - [ ] 使用 `@Testcontainers` 注解
  - [ ] 使用 `@Container` 注解
  - [ ] 容器配置正确

### MockMvc

- [ ] **CHK-TOOL-005**: 正确使用 MockMvc
  - [ ] 使用 `@AutoConfigureMockMvc`
  - [ ] 构建清晰的请求
  - [ ] 验证响应状态和内容

---

## 📐 特殊测试场景

### 幂等性测试

- [ ] **CHK-SPECIAL-001**: 幂等操作有幂等性测试
  - [ ] 测试重复执行相同操作
  - [ ] 验证结果一致

### 乐观锁测试

- [ ] **CHK-SPECIAL-002**: 乐观锁机制有并发测试
  - [ ] 测试版本冲突
  - [ ] 验证异常处理

### 事务测试

- [ ] **CHK-SPECIAL-003**: 事务边界有测试
  - [ ] 测试事务提交
  - [ ] 测试事务回滚
  - [ ] 验证数据一致性

### 性能测试

- [ ] **CHK-SPECIAL-004**: 关键操作有性能测试（可选）
  - [ ] 测试响应时间
  - [ ] 测试并发处理能力

---

## 🏗️ 架构测试（ArchUnit）

### 依赖规则测试

- [ ] **CHK-ARCH-001**: 使用 ArchUnit 测试依赖规则
  - [ ] 测试文件: `ArchitectureTest.java`
  - [ ] 验证 Domain 层无框架依赖
  - [ ] 验证依赖方向: Adapter → App → Domain ← Infra

### 命名规范测试

- [ ] **CHK-ARCH-002**: 测试命名规范
  - [ ] Controller 以 `Controller` 结尾
  - [ ] Repository 实现以 `RepositoryImpl` 结尾
  - [ ] Mapper 以 `Mapper` 结尾

### 包结构测试

- [ ] **CHK-ARCH-003**: 测试包结构规范
  - [ ] Domain 层包结构正确
  - [ ] Application 层包结构正确
  - [ ] Infrastructure 层包结构正确

---

## 🚀 持续集成

### CI/CD 测试

- [ ] **CHK-CI-001**: 所有测试在 CI 中运行
  - [ ] 单元测试执行
  - [ ] IT 测试执行
  - [ ] E2E 测试执行

- [ ] **CHK-CI-002**: 测试失败阻止合并
  - [ ] CI 流程配置正确
  - [ ] 测试失败时构建失败

### 测试报告

- [ ] **CHK-CI-003**: 生成测试覆盖率报告
  - [ ] 使用 JaCoCo 生成报告
  - [ ] 报告可访问
  - [ ] 覆盖率趋势可追踪

---

## ✅ 检查清单摘要

**测试总数**: [数量]

**Domain 层测试**:
- 单元测试数量: [数量]
- 覆盖率: [%]

**Application 层测试**:
- 单元测试数量: [数量]
- 覆盖率: [%]

**Infrastructure 层测试**:
- IT 测试数量: [数量]

**Adapter 层测试**:
- E2E 测试数量: [数量]

**整体状态**: [PASS / FAIL / PARTIAL]

---

## 📋 备注

- 检查项标记为完成: `[x]`
- 添加测试覆盖缺口和改进建议
- 链接到具体的测试类
- 优先级：高（必须覆盖）、中（应该覆盖）、低（可选覆盖）
