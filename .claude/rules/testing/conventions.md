# 后端测试约定

patra-api 是 Java 25 + Spring Boot 4 + Gradle 项目，TDD / 验证时遵循以下约定：

| 维度 | 规范 |
|------|------|
| 单元测试位置 | `src/test/java/{对应包路径}/` ——跟生产代码 `src/main/java/` 镜像 |
| 集成测试位置 | `src/integrationTest/java/{对应包路径}/` |
| 端到端测试位置 | `src/e2eTest/java/{对应包路径}/` |
| 共享 fixture/资源 | `src/testFixtures/java/{对应包路径}/` + `src/testFixtures/resources/` ——跨 sourceSet 或跨模块共享的 builder / generator / application yaml |
| 单元测试命名 | `{被测类}Test`（例：`ProvenanceCode` → `ProvenanceCodeTest`） |
| 集成测试命名 | `{被测类或场景}IT`（例：`OutboxRelayIT`） |
| 端到端测试命名 | `{被测场景}E2E`（例：`CatalogIngestionE2E`） |
| IT Bootstrap | `{Module}ITBootstrap`（@DataJpaTest / @SpringBootTest placeholder Application） |
| IT WebMvc Config | `{Module}ITWebMvcConfig`（@WebMvcTest 切片的 @SpringBootConfiguration 根） |
| IT Spring Config | `*ITSpringConfig`（@Configuration / @TestConfiguration 内嵌 bean 装配） |
| IT Container Init | `{Module}IT{Container}ContainerInitializer`；E2E 同类型用 `{Module}E2E{Container}ContainerInitializer` 前缀区分 |
| Fixture 命名 | `*Fixture`（不带 Test 前缀，source set 路径已表达语义） |
| 测试方法命名 | snake_case，`should_<期望行为>` 或 `<行为>_when_<条件>`（直接读得通） |
| 单元测试栈 | JUnit 5（`@Test`）+ AssertJ（`assertThat(...)`）+ Mockito（`mock(X.class)` / `when(...).thenReturn(...)`） |
| 集成测试栈 | `@SpringBootTest` 或 slice test（`@WebMvcTest` / `@DataJpaTest`）+ TestContainers（真实 PostgreSQL / Redis 等） |
| 时间相关 | 注入 `java.time.Clock` 而不是直接调 `LocalDateTime.now()` / `Instant.now()`——可被测试替换为固定时钟 |
| 运行命令 | `./gradlew :module-name:test` / `:integrationTest` / `:e2eTest`（按 source set 跑），或 `--tests "*ClassName*"` 精确 |

**禁止：** 用反射访问私有方法做"白盒测试"；用 `@SuppressWarnings("unchecked")` 绕过类型检查；为了让测试通过在生产类加 `setXxx()` setter。

## 验证门控

1. **编译**：`./gradlew compileJava compileTestJava`——IDE 不标红不等于编译通过（IDE 可能用增量缓存）
2. **六边形纯净性**：domain 层由 convention plugin（`build-logic/`）禁用框架依赖，`./gradlew :<service>-domain:check` 通过才算纯净
3. **全栈门控（PR 前必跑）**：`./gradlew check`，包含各模块 `test` 与 `integrationTest`（TestContainers 起 PG17），不得以"太慢"为由跳过
