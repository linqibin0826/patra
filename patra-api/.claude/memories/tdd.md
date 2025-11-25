# TDD 开发规范

## 核心原则

1. 遵循 Red-Green-Refactor 循环：先写失败测试，再写最少代码使测试通过，最后重构优化
2. 先写测试，小步前进，只写满足测试的必要代码
3. 禁止跳过测试直接编写实现代码，禁止过度设计

## 层级测试策略

| 层级 | 测试方式 | Mock 策略 |
|------|----------|-----------|
| Domain | 纯单元测试 | 无 Mock |
| Application | 单元测试 | Mock 所有 Ports |
| Infrastructure | `@MybatisPlusTest`（Repository）、WireMock（Feign） | 真实数据库（TestContainers）、Mock 外部 API |
| Adapter | `@WebMvcTest`（Controller）、单元测试（Listener/Job） | Mock 业务层 |
| Boot | `@SpringBootTest`（E2E） | 真实中间件 |

## 测试金字塔

1. 单元测试占比 75% 以上，切片测试占比约 20%，E2E 测试占比不超过 5%
2. 优先编写单元测试，仅在必要时编写集成测试和 E2E 测试

## 注意事项

1. Spring Boot 3.4+ 使用 `@MockitoBean`（包路径：`org.springframework.test.context.bean.override.mockito`），禁止使用已废弃的 `@MockBean`
2. 测试超时配置使用真实值（如 2s），禁止配置过长超时以避免拖慢测试执行
