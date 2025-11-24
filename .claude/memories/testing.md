# 测试相关

1. Spring Boot 3.4+ 使用 `@MockitoBean` 替代已弃用的 `@MockBean`（包路径：`org.springframework.test.context.bean.override.mockito.MockitoBean`）
2. 测试 HTTP 客户端超时应配置真实超时时间（如 2 秒），避免配置过长超时（如 10 秒）导致测试变慢；Awaitility 用于异步轮询场景而非同步阻塞超时测试
3. 测试遵循金字塔原则：**单元测试** 75%+（domain/app 层纯 JUnit，无 Spring 容器）→ **切片测试** 20%（infra/adapter 层 `@MybatisPlusTest`/`@WebMvcTest` 加载必要组件）→ **E2E 测试** <5%（boot 层 `@SpringBootTest` 启动完整应用验证关键流程）
