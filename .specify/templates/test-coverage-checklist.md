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

- [ ] **CHK-COV-003**: Infrastructure 层有单元测试和集成测试
  - 单元测试数量: [数量]
  - 集成测试数量: [数量]
  - 覆盖的 Repository: [列出]
  - 覆盖的 Feign Client: [列出]
  - 覆盖的 MQ Publisher: [列出]

- [ ] **CHK-COV-004**: Adapter 层有单元测试和切片测试
  - 单元测试数量: [数量]
  - 切片测试数量: [数量]
  - 覆盖的 Controller: [列出]
  - 覆盖的 Listener: [列出]
  - 覆盖的 Job: [列出]

- [ ] **CHK-COV-005**: Boot 层有 E2E 端到端测试
  - E2E 测试数量: [数量]
  - 覆盖的业务流程: [列出]

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

### Repository 集成测试

- [ ] **CHK-INFRA-001**: 所有 Repository 实现有集成测试
  - Repository 列表: [列出所有 Repository]
  - [ ] 使用 `@MybatisPlusTest` 切片测试
  - [ ] 使用 TestContainers 启动真实数据库（MySQL）
  - [ ] 测试文件命名: `*RepositoryIT.java`
  - [ ] 测试位置: `patra-{service}-infra/src/test/java/`

- [ ] **CHK-INFRA-002**: Repository 测试覆盖 CRUD 操作
  - [ ] 测试 save() 方法
  - [ ] 测试 findById() 方法
  - [ ] 测试 findAll() / findByXxx() 方法
  - [ ] 测试 update() 方法
  - [ ] 测试 delete() 方法

- [ ] **CHK-INFRA-003**: Repository 测试验证 SQL 正确性
  - [ ] 验证查询结果
  - [ ] 验证数据库状态
  - [ ] 验证关联关系

### Feign Client 测试

- [ ] **CHK-INFRA-004**: Feign Client 有单元测试和集成测试
  - Feign Client 列表: [列出所有 Feign Client]
  - [ ] **单元测试**: Mock HTTP 响应，验证请求构建
  - [ ] **集成测试**: 使用 WireMock 模拟外部服务
  - [ ] 测试文件命名: `*ClientTest.java` (单元), `*ClientIT.java` (集成)
  - [ ] 测试位置: `patra-{service}-infra/src/test/java/`

### MQ Publisher 测试

- [ ] **CHK-INFRA-005**: MQ Publisher 有单元测试和集成测试
  - Publisher 列表: [列出所有 Publisher]
  - [ ] **单元测试**: Mock MQ Template，验证消息构建
  - [ ] **集成测试**: 使用 TestContainers 启动真实 RocketMQ
  - [ ] 测试文件命名: `*PublisherTest.java` (单元), `*PublisherIT.java` (集成)
  - [ ] 测试位置: `patra-{service}-infra/src/test/java/`

### Elasticsearch Client 测试

- [ ] **CHK-INFRA-006**: ES Client 有集成测试（如使用 ES）
  - ES Client 列表: [列出所有 ES Client]
  - [ ] 使用 `@DataElasticsearchTest` 切片测试
  - [ ] 使用 TestContainers 启动真实 Elasticsearch
  - [ ] 测试文件命名: `*ClientIT.java`
  - [ ] 测试位置: `patra-{service}-infra/src/test/java/`

### Converter 单元测试

- [ ] **CHK-INFRA-007**: MapStruct Converter 有单元测试
  - Converter 列表: [列出所有 Converter]
  - [ ] 测试映射完整性
  - [ ] 测试 null 值处理
  - [ ] 测试复杂映射规则
  - [ ] 测试位置: `patra-{service}-infra/src/test/java/`

### TestContainers 配置

- [ ] **CHK-INFRA-008**: TestContainers 配置正确
  - [ ] 使用正确的数据库版本（如 MySQL 8）
  - [ ] 使用正确的 RocketMQ 版本
  - [ ] 使用正确的 Elasticsearch 版本
  - [ ] 容器启动成功
  - [ ] 容器在测试结束后关闭

---

## 🔌 Adapter 层测试

### Controller 切片测试

- [ ] **CHK-ADAPTER-001**: 所有 Controller 有单元测试和切片测试
  - Controller 列表: [列出所有 Controller]
  - [ ] **单元测试**: 使用 `@WebMvcTest` 切片测试
  - [ ] Mock 业务层依赖（UseCase/Orchestrator）
  - [ ] 测试文件命名: `*ControllerTest.java`
  - [ ] 测试位置: `patra-{service}-adapter/src/test/java/`

- [ ] **CHK-ADAPTER-002**: Controller 测试覆盖所有 HTTP 方法
  - [ ] 测试 GET 请求
  - [ ] 测试 POST 请求
  - [ ] 测试 PUT 请求
  - [ ] 测试 DELETE 请求

- [ ] **CHK-ADAPTER-003**: Controller 测试验证 HTTP 请求/响应
  - [ ] 验证状态码（200/201/400/404/500）
  - [ ] 验证响应体结构
  - [ ] 验证错误消息
  - [ ] 验证 Content-Type 头

- [ ] **CHK-ADAPTER-004**: Controller 测试验证参数校验
  - [ ] 测试 @Valid 参数校验
  - [ ] 测试路径参数验证
  - [ ] 测试查询参数验证
  - [ ] 测试请求体验证

- [ ] **CHK-ADAPTER-005**: Controller 测试验证异常处理
  - [ ] 测试业务异常转换为 HTTP 错误
  - [ ] 测试全局异常处理器
  - [ ] 验证错误响应格式

### Listener 单元测试

- [ ] **CHK-ADAPTER-006**: 事件监听器有单元测试
  - Listener 列表: [列出所有 Listener]
  - [ ] Mock 业务层依赖
  - [ ] 测试消息反序列化
  - [ ] 测试消息处理逻辑
  - [ ] 测试异常处理
  - [ ] 测试位置: `patra-{service}-adapter/src/test/java/`

### Job 单元测试

- [ ] **CHK-ADAPTER-007**: 定时任务有单元测试
  - Job 列表: [列出所有 Job]
  - [ ] Mock 业务层依赖
  - [ ] 测试任务执行逻辑
  - [ ] 验证任务结果
  - [ ] 测试异常处理
  - [ ] 测试位置: `patra-{service}-adapter/src/test/java/`

---

## 🚀 Boot 层测试

### E2E 端到端测试

- [ ] **CHK-BOOT-001**: 所有关键业务流程有 E2E 测试
  - 业务流程列表: [列出所有关键流程]
  - [ ] 使用 `@SpringBootTest` 完整上下文测试
  - [ ] 测试文件命名: `*E2ETest.java` 或 `*IT.java`
  - [ ] 测试位置: `patra-{service}-boot/src/test/java/`

- [ ] **CHK-BOOT-002**: E2E 测试验证完整业务流程
  - [ ] 验证 HTTP → 业务逻辑 → 数据库 → MQ → ES 完整链路
  - [ ] 使用 TestContainers 启动所有依赖（MySQL + RocketMQ + ES）
  - [ ] 使用 Awaitility 进行异步断言

- [ ] **CHK-BOOT-003**: E2E 测试验证数据一致性
  - [ ] 验证数据库状态
  - [ ] 验证消息发布
  - [ ] 验证事件传播
  - [ ] 验证最终一致性

- [ ] **CHK-BOOT-004**: E2E 测试覆盖异常场景
  - [ ] 测试系统故障恢复
  - [ ] 测试事务回滚
  - [ ] 测试幂等性保证

### 架构规则测试

- [ ] **CHK-BOOT-005**: 使用 ArchUnit 验证架构规则
  - [ ] 测试文件: `ArchitectureTest.java`
  - [ ] 验证六边形架构依赖方向
  - [ ] 验证 Domain 层纯净性
  - [ ] 验证命名规范
  - [ ] 验证包结构规范

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

### Spring Test 切片注解

- [ ] **CHK-TOOL-005**: 正确使用 @WebMvcTest
  - [ ] 用于 Controller 层切片测试
  - [ ] Mock 业务层依赖（@MockBean）
  - [ ] 自动配置 MockMvc
  - [ ] 只加载 Web 层组件

- [ ] **CHK-TOOL-006**: 正确使用 @MybatisPlusTest
  - [ ] 用于 Repository 层集成测试
  - [ ] 结合 TestContainers 使用
  - [ ] 只加载 MyBatis 相关组件
  - [ ] 验证 SQL 和数据库操作

- [ ] **CHK-TOOL-007**: 正确使用 @DataElasticsearchTest（如使用 ES）
  - [ ] 用于 ES Client 层集成测试
  - [ ] 结合 TestContainers 使用
  - [ ] 只加载 Elasticsearch 相关组件

### WireMock（HTTP Mock）

- [ ] **CHK-TOOL-008**: 正确使用 WireMock（如有 Feign Client）
  - [ ] 用于 Feign Client 集成测试
  - [ ] 模拟外部 HTTP 服务
  - [ ] 验证 HTTP 请求构建
  - [ ] 验证响应处理

### Awaitility（异步断言）

- [ ] **CHK-TOOL-009**: 正确使用 Awaitility（如有异步场景）
  - [ ] 用于 E2E 测试中的异步断言
  - [ ] 等待消息消费完成
  - [ ] 等待事件传播完成
  - [ ] 设置合理的超时时间

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
- 覆盖率: [%] (目标 ≥ 80%)
- 测试位置: `patra-{service}-domain/src/test/java/`

**Application 层测试**:
- 单元测试数量: [数量]
- 覆盖率: [%] (目标 ≥ 70%)
- 测试位置: `patra-{service}-app/src/test/java/`

**Infrastructure 层测试**:
- 单元测试数量: [数量]
- 集成测试数量: [数量]
- 测试位置: `patra-{service}-infra/src/test/java/`

**Adapter 层测试**:
- 单元测试数量: [数量]
- 切片测试数量: [数量]
- 测试位置: `patra-{service}-adapter/src/test/java/`

**Boot 层测试**:
- E2E 测试数量: [数量]
- 架构测试数量: [数量]
- 测试位置: `patra-{service}-boot/src/test/java/`

**整体状态**: [PASS / FAIL / PARTIAL]

---

## 📋 备注

- 检查项标记为完成: `[x]`
- 添加测试覆盖缺口和改进建议
- 链接到具体的测试类
- 优先级：高（必须覆盖）、中（应该覆盖）、低（可选覆盖）
