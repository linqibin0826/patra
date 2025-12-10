# 测试策略

## 层级测试规则

| 层级 | 测试方式 | Mock 策略 | 文件后缀 |
|------|----------|-----------|---------|
| Domain | 纯单元测试 | 无 Mock | `*Test.java` |
| Application | 单元测试 | Mock Ports | `*Test.java` |
| Infrastructure | 集成测试优先 | TestContainers / WireMock | `*IT.java` |
| Adapter | 切片测试 | `@MockitoBean` | `*IT.java` |
| Boot | E2E 测试 | 真实中间件 | `*E2E.java` |

## 测试比例

单元测试 ≥75%，切片测试 ~20%，E2E <5%

## 覆盖率

行覆盖率 ≥80%，分支覆盖率 ≥70%，关键业务逻辑 100%
**排除项**：DTO getter/setter、配置类、启动类

## 超时配置

| 测试类型 | 超时限制 | 说明 |
|----------|----------|------|
| 单元测试 `*Test.java` | `@Timeout` ≤ 2s | 纯内存操作，无 I/O |
| 集成测试 `*IT.java` | `@Timeout` ≤ 30s | TestContainers 启动需要时间 |
| E2E 测试 `*E2E.java` | `@Timeout` ≤ 60s | 多服务协调，允许更长超时 |
| Awaitility 等待 | `atMost` ≤ 5s | 异步操作等待时间 |

## 注意事项

1. Spring Boot 3.4+ 使用 `@MockitoBean`，禁止废弃的 `@MockBean`
2. infra 层集成测试优先，单元测试价值有限（仅验证调用，无法验证 SQL）
3. 使用 TestContainers 模拟真实中间件，避免使用内存数据库
4. 切片测试仅测试 Adapter 层，禁止跨层调用
5. E2E 测试覆盖核心业务流程，避免冗余测试场景
6. 集成测试统一使用 `patra-spring-boot-starter-test` 提供的 `TestMybatisPlusAutoConfiguration`，禁止在各服务中重复定义测试配置
7. `@MybatisPlusTest` 切片测试中 `Db.saveBatch()` 与测试事务隔离，验证批量操作需使用 E2E 测试
