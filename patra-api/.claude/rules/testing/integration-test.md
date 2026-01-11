---
paths: patra-*/*-infra/**/src/test/**/*IT.java, patra-*/*-adapter/**/src/test/**/*IT.java
---

# 集成测试规范

## 适用范围

- Infrastructure 层：集成测试优先，使用 TestContainers/WireMock
- Adapter 层：切片测试，使用 `@MockitoBean`

## 文件命名

`*IT.java`

## 超时限制

`@Timeout` ≤ 30s（TestContainers 启动需要时间）

## 注意事项

1. 使用 `@MockitoBean` 进行 Mock 注入（`@MockBean` 已在 Spring Boot 4.0 中移除）
2. 统一使用 `patra-spring-boot-starter-test` 提供的测试自动配置
3. 使用 TestContainers 模拟真实中间件，避免使用内存数据库
4. `@DataJpaTest` 中使用 `TestEntityManager` 或 `JpaRepository` 进行数据准备

## 测试比例

切片测试 ~20%
