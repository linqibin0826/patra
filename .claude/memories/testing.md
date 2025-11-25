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

## 注意事项

1. Spring Boot 3.4+ 使用 `@MockitoBean`，禁止废弃的 `@MockBean`
2. 测试超时 ≤2s，避免拖慢测试执行
3. Infrastructure 层集成测试优先，单元测试价值有限（仅验证调用，无法验证 SQL）
