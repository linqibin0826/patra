# 会话存储归 identity 独占 Implementation Plan

**Goal:** 网关不再读 Redis，改为每个请求调 identity 的 `POST /_internal/sessions/introspect` 查会话；`patra-identity-session` 并入 identity-infra 后删除（PAP-106）。

**Architecture:** identity-infra 独占 `RedisSessionStore`；domain `SessionStorePort.findAndTouch` → app `SessionIntrospectionService` → adapter `SessionEndpointImpl`；契约在新模块 `patra-identity-api`；网关经 `RestClientFactory` + LoadBalancer 调它，失败按 spec 第 6 节翻译。

**Spec:** `docs/patra/specs/2026-10-23-session-introspection-design.md`（下称 spec）

## Global Constraints

- 每个 Task 结束时 `./gradlew compileJava compileTestJava` 通过、受影响模块的 `test` 绿；涉及 Redis / WireMock 的再跑对应 `integrationTest`。
- 搬移只改包名和 import，不改行为；新增行为先写失败测试。
- 提交前 `./gradlew spotlessApply`，subject 用中文动词起头，带 `(PAP-106)`。
- 改了 `*.gradle.kts` / `settings.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。

### Task 1: 会话存储搬进 identity-infra

**Files:** `patra-identity-session/src/**` → `patra-identity-infra` 的 `dev.patra.identity.infra.adapter.session`（main / test / integrationTest，Lua 进 `src/main/resources/redis/`）；infra、boot 的 `build.gradle.kts`；infra 与 boot 里引用 `dev.patra.identity.session` 的类。

会话模块的目录与构建文件 Task 6 再删；网关依赖已清空的会话模块，编译在 Task 5 之前不过（实际按此执行）。infra 已有 `spring-boot-starter-data-redis`；integrationTest 用测试 starter 的 `RedisContainerInitializer`。

验证：`:patra-identity-infra:test :patra-identity-infra:integrationTest`（含搬来的 `RedisSessionStoreIT`）、`:patra-identity-boot:test`、boot 编译集成测试。

### Task 2: 端口加 findAndTouch

**Files:** `SessionStorePort`、`SessionStoreAdapter`、`SessionStoreAdapterTest`。

**Interfaces:** Produces `Optional<CurrentUser> findAndTouch(String token)`：令牌格式不对返回空且不调存储；查不到返回空；查到返回 `StoredSession.toCurrentUser()`。

TDD：先写三条适配器单测。

### Task 3: identity-api 与内部接口

**Files:** 新模块 `patra-identity-api`（`settings.gradle.kts` 加 `includeAt`）：`api/endpoint/SessionEndpoint`、`api/dto/SessionIntrospectionReq`、`api/dto/SessionIntrospectionResp`；app `usecase/session/introspect/SessionIntrospectionService` + 单测；adapter `rest/internal/SessionEndpointImpl` + `@WebMvcTest` 切片测试；adapter 依赖 api。

**Interfaces:** spec 第 4 节。

TDD：服务单测（委托端口）→ 切片测试（active 时 `userId` 为字符串、inactive 只有 `active:false`）→ 实现。

### Task 4: identity 端到端

**Files:** `SessionFlowIT`（或同目录新 IT）。

用例：登录 → introspect active 且用户 ID / 会话 ID 对得上 → 登出 → inactive；乱写的令牌 inactive。

### Task 5: 网关改调 identity

**Files:** gateway `build.gradle.kts`（去会话模块，加 `patra-tool-http-client`、`patra-identity-api`）、`IdentityClientConfiguration`、`SessionTokenAuthenticationConverter`、`GatewaySecurityConfiguration`（去 `RedisSessionStore` Bean）、`application.yml`、单测与全部集成测试。

TDD：转换器单测先改（mock `SessionEndpoint`：active、inactive、无头 / 多头 / 非 Bearer 不调、spec 第 6 节四类失败）→ 实现 → 集成测试换成 WireMock 桩 introspect，`GatewayRedisUnavailableIT` → `GatewayIdentityUnavailableIT`，拒绝名单补一条。

验证：`:patra-gateway-boot:test :patra-gateway-boot:integrationTest`；`dependencies --configuration runtimeClasspath` 里没有 `spring-data-redis`。

### Task 6: 删除会话模块，文档与基建同步

**Files:** 删 `patra-identity-session/`；`settings.gradle.kts`；`./gradlew dumpModuleGraph` 更新 `patra-infra/cd/module-graph.json`；`patra-infra` 的 Nacos gateway 配置、compose、README、`redis.env.example`、`CLAUDE.md`；spec 第 9 节列的文档。

### Task 7: 全量验证

`./gradlew spotlessApply && ./gradlew check`，再跑 identity 各模块与网关的 `integrationTest`。
