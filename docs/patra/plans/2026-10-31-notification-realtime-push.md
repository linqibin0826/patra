# 通知服务实时推送实施计划（PAP-145）

> **For agentic workers:** 本计划在当前会话内按 superpowers:executing-plans 内联执行（用户约定，不用 subagent-driven），严格 TDD。步骤用复选框（`- [ ]`）跟踪。

**Goal：** 通知服务提供只推三个未读数的 SSE 接口（提交后推、25 秒心跳、30 分钟寿命），网关给推送路由单独的连接池并要求登录。

**Architecture：** 网关用自定义 scheme `lb-push://` 注册一个使用独立 Reactor Netty 连接池的代理处理器；通知服务 app 层的连接表管「用户 → 连接」、推送顺序、心跳与在站，adapter 层用 `SseEmitter` 实现连接；信箱的 `UnreadCountsChangedEvent` 经 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 触发推送。

**Tech Stack：** Java 25、Spring Boot 4.1.2、Spring MVC `SseEmitter`、Spring Cloud Gateway Server MVC 5.0.4、Reactor Netty 1.3.8、JUnit 5 + AssertJ + Mockito、Testcontainers（PG、RabbitMQ）、WireMock。

**Spec：** `docs/patra/specs/2026-10-31-notification-realtime-push-design.md`

## Global Constraints

- 推送路径：经网关 `/patra-notification/me/notifications/stream`，通知服务 `/me/notifications/stream`，`produces = text/event-stream`。
- 事件名 `unread-count`；数据 `{"total","replies","mentions","helpful"}`（数字），`total` = replies + mentions；每条连接的第一条带 `retry: 3000`；心跳是注释 `:ping`。
- 响应头 `Cache-Control: no-cache, no-transform`。
- `patra.notification.realtime.heartbeat-interval: 25s`、`patra.notification.realtime.max-connection-age: 30m`（`SseEmitter` 超时 = 寿命）。
- 网关 `patra.gateway.push.max-connections: 500`，`pendingAcquireMaxCount(0)`；推送路由排在通用 `/patra-notification/**` 之前；推送路径需登录（401 `GW-0401`）。
- 推送用的 `RestClientProxyExchange`、`ProxyExchangeHandlerFunction`、`ConnectionProvider` 都不是 Bean。
- 方法一律 `///` Javadoc；测试方法 snake_case；不改 release spec（更正交编排会话）；README 只追加。
- 提交 message 中文、subject 中文动词开头，不以类名或 Issue key 开头；只 `git add` 自己的路径，提交前核对 `git diff --cached`；不 push。

## Review Focus

1. **浏览器悄悄走了**（关标签页、断网）：下一轮心跳写失败，连接被剔除，在站时刻不再前进 → Task 5 的 `should_stop_marking_presence_after_client_disconnects`。
2. **两次变化挨得很近、在两个线程里推**：最后一条必须是最新值 → Task 3 的 `should_end_with_latest_counts_when_two_pushes_race`。
3. **推送处理器没收到容器刷新事件**：会话令牌原样直达下游 → Task 2 断言下游收不到令牌。
4. **推送池满**：普通请求照常、多出的推送立即 503 → Task 2。
5. **部署时挂着的连接**：停机先结束所有连接，不拖住优雅停机 → Task 3 的 `should_close_every_stream_on_shutdown`。

---

## 第一段：网关（不依赖 PAP-143，现在做）

### Task 1：推送路径要登录

**Files：**
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayPushRouteIT.java`（新建，Task 2 继续往里加）

**Interfaces：**
- Produces: `GatewaySecurityConfiguration.PUSH_STREAM_PATH = "/patra-notification/me/notifications/stream"`

- [ ] **Step 1：写失败的测试**。`GatewayPushRouteIT`：`@SpringBootTest(RANDOM_PORT)`、`@ActiveProfiles("test")`、`@AutoConfigureRestTestClient`、`@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)`、`properties = "patra.gateway.push.max-connections=2"`；WireMock 顶替 identity 与 notification（同 `GatewayAuthenticationIT` 的注册与 `@BeforeEach` 重置 + `GatewayITSessions.inactiveByDefault`）。
  - `should_reject_anonymous_stream_request_at_gateway`：不带令牌 GET 推送路径 → 401、`$.code == "GW-0401"`；`notification.verify(0, anyRequestedFor(anyUrl()))`。
  - `should_reject_stream_reconnect_after_logout`：`issue` 一个令牌，打桩下游 `/me/notifications/stream` 回 200；第一次 GET → 200；`revoke` 后再 GET → 401 `GW-0401`，下游只收到 1 次。
- [ ] **Step 2：跑，确认失败**：`./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayPushRouteIT*"` → 匿名用例拿到 200（现在全放行）。
- [ ] **Step 3：实现**：第二条链 `.requestMatchers(PUSH_STREAM_PATH).authenticated()`，放在 `IDENTITY_PATHS` 之后、`anyRequest().permitAll()` 之前；类 Javadoc 与常量注释写明为什么网关就拦（spec 7.3）。
- [ ] **Step 4：跑，确认通过**（同上命令）。
- [ ] **Step 5：提交**：`feat(gateway): 要求推送连接登录`。

### Task 2：推送路由与独立连接池

**Files：**
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/config/GatewayPushProperties.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/config/PushProxyHandlerFunction.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/config/PushRouteConfiguration.java`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`（路由、`patra.gateway.push.max-connections: 500`）
- Modify（如测试证明需要）: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/error/ProxyFailureClassifier.java`
- Modify: `patra-api/patra-gateway-boot/README.md`、`PatraGatewayApplication.java` 的 Javadoc（如列了路由）
- Test: `GatewayPushRouteIT`（续）、`src/test/java/dev/patra/gateway/ApplicationConfigTest.java`

**Interfaces：**
- `@ConfigurationProperties("patra.gateway.push") record GatewayPushProperties(int maxConnections)`
- `PushProxyHandlerFunction implements HandlerFunction<ServerResponse>, ApplicationListener<ContextRefreshedEvent>, DisposableBean`：构造 `(ProxyExchangeHandlerFunction delegate, ConnectionProvider pool)`；`handle` 转交；`onApplicationEvent` 转交；`destroy` → `pool.dispose()`
- `PushRouteConfiguration`（`@EnableConfigurationProperties(GatewayPushProperties.class)`）：
  - `@Bean PushProxyHandlerFunction pushProxyHandlerFunction(ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder, HttpClientSettings httpClientSettings, RestClient.Builder restClientBuilder, GatewayMvcProperties gatewayProperties, ObjectProvider<RequestHttpHeadersFilter> requestFilters, ObjectProvider<ResponseHttpHeadersFilter> responseFilters, GatewayPushProperties push)`：池 `ConnectionProvider.builder("patra-push").maxConnections(push.maxConnections()).pendingAcquireMaxCount(0).build()`；`requestFactoryBuilder` 不是 `ReactorClientHttpRequestFactoryBuilder` 时启动失败；`withHttpClientFactory(() -> HttpClient.create(pool)).build(httpClientSettings)` → `restClientBuilder.requestFactory(...).build()` → `new RestClientProxyExchange(...)` → `new ProxyExchangeHandlerFunction(...)`
  - `@Bean(name = "lb-pushHandlerFunctionDefinition") Function<RouteProperties, HandlerFunctionDefinition> lbPushHandlerFunctionDefinition(PushProxyHandlerFunction handler)` → `new HandlerFunctionDefinition.Default("lb-push", handler, List.of(), List.of(LoadBalancerFilterFunctions.lb(uri.getHost())))`
- 路由 `id: patra-notification-stream`、`uri: lb-push://patra-notification`、`Path=/patra-notification/me/notifications/stream`、`StripPrefix=1`，排在 `patra-notification` 之前

- [ ] **Step 1：写失败的测试**（`GatewayPushRouteIT` 续）：
  - `should_forward_stream_with_assertion_and_without_session_token`：下游桩回 `200 text/event-stream`、体 `"event: unread-count\ndata: {\"total\":1}\n\n"`；带令牌 GET → 200、体原样；`notification.verify(getRequestedFor(urlPathEqualTo("/me/notifications/stream")).withHeader("Authorization", notMatching(".*" + token + ".*")))`，并取下游收到的 `Authorization` 用 `identityAssertionDecoder` 解出 `sub` = 用户 ID。
  - `should_reject_extra_stream_at_once_when_push_pool_is_full_while_other_requests_pass`：下游推送桩 `withChunkedDribbleDelay(10, 4_000)`（每块 400ms，短于测试读超时 1s）；用 JDK `HttpClient`（HTTP/1.1）`sendAsync` 两条推送并 `join` 到拿到响应头（200）；第三条推送 → 503、`$.code == "GW-0503"`、耗时 < 1s；同时 `/patra-notification/me/notifications/unread-count`（桩回 `okJson`）→ 200。
- [ ] **Step 2：写失败的测试**（`ApplicationConfigTest`）：`should_route_push_stream_before_general_notification_route`：读 jar 的 `application.yml`，`spring.cloud.gateway.server.webmvc.routes[i].id` 中 `patra-notification-stream` 的下标小于 `patra-notification`，其 `uri` 为 `lb-push://patra-notification`；`patra.gateway.push.max-connections` = `500`。
- [ ] **Step 3：跑，确认失败**：`./gradlew :patra-api:patra-gateway-boot:test :patra-api:patra-gateway-boot:integrationTest --tests "*ApplicationConfigTest*" --tests "*GatewayPushRouteIT*"` → 配置用例失败；池满用例第三条拿到 200（走普通池）。
- [ ] **Step 4：实现**上面 Interfaces 里的三个类与 yml。
- [ ] **Step 5：跑**同上。若第三条推送不是 503 而是 500：在 `ProxyFailureClassifier` 把借连接失败（原因链上的 `reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException` 或实测到的类型）归「到不了」，先在 `ProxyFailureClassifierTest` 写单元用例再改。
- [ ] **Step 6：全网关回归**：`./gradlew :patra-api:patra-gateway-boot:check :patra-api:patra-gateway-boot:integrationTest` → 全绿（`GatewayRoutingIT` 证明普通路由没被换到推送池）。
- [ ] **Step 7：README**：路由清单、路径规则表加推送路径；「HTTP 客户端、超时与线程」表加「推送连接池」一行；模块结构加三个新类与 `GatewayPushRouteIT`；只追加。
- [ ] **Step 8：提交**：subject「新增推送专用路由与独立连接池」。

---

## 第二段：通知服务（等编排会话给出含 PAP-143 的 SHA，`git reset --hard <SHA>` 对齐后做）

开工前先读：PAP-143 的 `InboxReadPort` 实现是否要求已有事务（决定 Task 3 读数是否包 `TransactionTemplate`）、`PresenceGateway` 的包与签名、`UnreadCountsChangedEvent` 的包、`NotificationFlowITSupport` 里 RabbitMQ 容器与发事件的辅助方法、`UnreadCountsResponse` 的字段顺序。与本计划不符处按实际代码改，偏离记进 spec 末尾「实施记录」。

### Task 3：连接表

**Files：**
- Create: `patra-api/patra-notification/patra-notification-app/src/main/java/dev/patra/notification/app/realtime/service/UnreadCountStream.java`
- Create: `.../app/realtime/service/UnreadCountStreamRegistry.java`
- Modify: `patra-notification-boot/src/test/java/dev/patra/notification/architecture/NotificationArchitectureTest.java`（`.allowingDependency("realtime", "inbox")`）
- Test: `patra-notification-app/src/test/java/dev/patra/notification/app/realtime/service/UnreadCountStreamRegistryTest.java`

**Interfaces：**
- Consumes：`InboxReadPort#unreadCounts(long) → UnreadCounts`、`PresenceGateway#markSeen(long)`
- Produces：
  - `interface UnreadCountStream { void sendCounts(UnreadCounts counts); void sendHeartbeat(); void close(); }`：发送失败抛 `RuntimeException`（adapter 包 `UncheckedIOException`）
  - `@Component class UnreadCountStreamRegistry`：`void open(long userId, UnreadCountStream stream)`、`void close(long userId, UnreadCountStream stream)`（幂等）、`void push(Collection<Long> userIds)`、`void heartbeat()`、`@EventListener(ContextClosedEvent.class) void closeAll()`
  - 结构：`ConcurrentHashMap<Long, Set<UnreadCountStream>>`（`ConcurrentHashMap.newKeySet()`），加删用 `compute` / `computeIfPresent`（空集合移除）；推与心跳在该用户的集合上 `synchronized`，遍历快照 `List.copyOf(set)`；读数在锁里

- [ ] **Step 1**（每个用例一轮红绿，按下列顺序）。测试用 Mockito 的 `InboxReadPort`、`PresenceGateway`，以及一个记录收到内容的假 `UnreadCountStream`（测试内部类，可配置「下次发送抛异常」）：
  1. `should_send_current_counts_and_mark_seen_on_open`：`open(7, s)` → `s` 收到 `UnreadCounts.of(2, 1, 0)`，`markSeen(7)` 一次。
  2. `should_withdraw_stream_and_rethrow_when_initial_counts_cannot_be_read`：读数抛 → `open` 抛同一异常；之后 `push(Set.of(7))` 不再读数。
  3. `should_push_fresh_counts_to_every_stream_of_recipient_only`：7 有两条、8 有一条；`push(Set.of(7))` → 7 的两条都收到新值，8 的只有初始值。
  4. `should_not_read_counts_for_users_without_streams`：`push(Set.of(9))` → `verifyNoInteractions(inboxReadPort)`。
  5. `should_drop_and_close_stream_whose_send_fails_and_keep_others`：7 的一条发送抛 → 被 `close()`，另一条照收；再推一次，坏的那条不再被调用。
  6. `should_not_throw_when_counts_cannot_be_read_during_push`：`push` 时读数抛 → 不抛，连接不受影响。
  7. `should_stop_pushing_to_closed_stream`：`close(7, s)` 后 `push` → `s` 不再收到；再 `close` 一次不抛。
  8. `should_send_heartbeat_to_all_streams_and_mark_each_user_seen_once`：7 两条、8 一条 → 三条都 `sendHeartbeat`，`markSeen(7)`、`markSeen(8)` 各一次（不算 `open` 时那次，用 `clearInvocations`）；心跳失败的被剔除且这一轮不为只剩它的用户记在站。
  9. `should_end_with_latest_counts_when_two_pushes_race`：读端口第一次调用阻塞在 `CountDownLatch` 上并返回旧值、之后返回新值；线程 A `push`，等它进入读数后线程 B `push`；放开闩，`join` 两线程（超时 5s）→ 该连接最后收到的是新值。
  10. `should_close_every_stream_on_shutdown`：`closeAll()` → 每条 `close()`，之后 `push` 不读数。
- [ ] **Step 2**：每个用例 `./gradlew :patra-api:patra-notification:patra-notification-app:test --tests "*UnreadCountStreamRegistryTest*"` 先红后绿。
- [ ] **Step 3**：加 `allowingDependency("realtime", "inbox")`，跑 `./gradlew :patra-api:patra-notification:patra-notification-boot:test --tests "*NotificationArchitectureTest*"` → 绿。
- [ ] **Step 4：提交**：subject「新增推送连接表」。

### Task 4：提交后推

**Files：**
- Create: `.../app/realtime/listener/UnreadCountPushListener.java`
- Create: `patra-notification-boot/src/main/java/dev/patra/notification/config/RealtimeConfiguration.java`（`@EnableAsync`、`@EnableScheduling`，Javadoc 写清是给推送用的）
- Test: `patra-notification-app/src/test/java/dev/patra/notification/app/realtime/listener/UnreadCountPushListenerTest.java`

**Interfaces：**
- `@Async @TransactionalEventListener(phase = AFTER_COMMIT) void on(UnreadCountsChangedEvent event)` → `registry.push(event.recipientIds())`

- [ ] **Step 1**：`should_push_to_every_recipient_of_the_event`（Mockito 注册表，直接调 `on`）→ 红 → 实现 → 绿。
- [ ] **Step 2**：提交并入 Task 5 的提交（监听器要到集成测试里才证明「提交后」「换线程」）。

### Task 5：SSE 接口、心跳任务与集成测试

**Files：**
- Create: `patra-notification-adapter/src/main/java/dev/patra/notification/adapter/realtime/config/RealtimeProperties.java`：`@ConfigurationProperties("patra.notification.realtime") record RealtimeProperties(Duration heartbeatInterval, Duration maxConnectionAge)`（由 `RealtimeConfiguration` 启用）
- Create: `.../adapter/realtime/rest/response/UnreadCountsEventResponse.java`：`record (int total, int replies, int mentions, int helpful)` + `static from(UnreadCounts)`
- Create: `.../adapter/realtime/rest/SseUnreadCountStream.java`：构造 `(SseEmitter emitter)`；第一次 `sendCounts` 加 `reconnectTime(3000)`；事件名常量 `"unread-count"`；`sendHeartbeat` → `comment("ping")`；`close` → `emitter.complete()`；`IOException` → `UncheckedIOException`
- Create: `.../adapter/realtime/rest/UnreadCountStreamController.java`：`@GetMapping(path = "/me/notifications/stream", produces = TEXT_EVENT_STREAM_VALUE) ResponseEntity<SseEmitter> stream()`；`CurrentUserPort.require()`；`new SseEmitter(maxConnectionAge.toMillis())`；`onTimeout(emitter::complete)`、`onCompletion` / `onError` → `registry.close(userId, stream)`；`registry.open(...)`；头 `Cache-Control: no-cache, no-transform`
- Create: `.../adapter/realtime/scheduler/UnreadCountStreamHeartbeatScheduleJob.java`：`@Scheduled(fixedRateString = "${patra.notification.realtime.heartbeat-interval}") void heartbeat()` → `registry.heartbeat()`
- Modify: `patra-notification-boot/src/main/resources/application.yml`（spec 6.7 的两项与注释）
- Test: `patra-notification-boot/src/test/java/dev/patra/notification/ApplicationConfigTest.java`
- Test: `patra-notification-boot/src/integrationTest/java/dev/patra/notification/realtime/UnreadCountStreamFlowIT.java`（继承 `NotificationFlowITSupport`，`properties = {"patra.notification.realtime.heartbeat-interval=200ms", "patra.notification.realtime.max-connection-age=3s"}`）
- Create: `patra-notification-boot/src/integrationTest/java/dev/patra/notification/support/SseTestConnection.java`：JDK `HttpClient` 开流，后台线程逐行读进 `BlockingQueue<String>`；`nextEvent(Duration)` 返回下一条完整事件（行列表）或空、`awaitComment(Duration)`、`awaitEnd(Duration)`（EOF 返回 true、I/O 错误抛出）、`close()`

- [ ] **Step 1：配置**：`ApplicationConfigTest` 加 `should_set_realtime_heartbeat_and_connection_age`：jar 的 yml 里两项为 `25s`、`30m` → 红 → 改 yml → 绿。
- [ ] **Step 2：集成测试**（一个一个红绿）：
  1. `should_send_current_counts_with_retry_first`：用户 7 连上 → 200、`Content-Type` 是 `text/event-stream`、`Cache-Control` 含 `no-transform`；第一条事件含 `retry: 3000`、`event: unread-count`、`data:` 解析为 `total/replies/mentions/helpful` 全 0。
  2. `should_reject_anonymous_stream_with_problem_detail`：不带断言、`Accept: text/event-stream` → 401、`$.code == "NOTIFICATION-0401"`。
  3. `should_keep_idle_stream_open_with_heartbeats_and_mark_presence`：1 秒内收到至少 3 行 `:ping`；`kernel.presence` 里 7 的 `last_seen_at` 在这期间前进。
  4. `should_stop_marking_presence_after_client_disconnects`：连上后关客户端，等 1 秒（5 轮心跳）后记下 `last_seen_at`，再等 1 秒不变。
  5. `should_end_stream_normally_at_max_age`：`awaitEnd(5s)` 为 true，且距连上 ≥ 3s。
  6. `should_push_only_after_commit_and_not_record_publication`：`TransactionTemplate` 里发布 `UnreadCountsChangedEvent(Set.of(7))` 后 `setRollbackOnly` → 1 秒内没有新事件；同样发布并提交 → 1 秒内收到；`SELECT count(*) FROM event_publication`（按 PAP-137 建表的实际 schema）为 0。
- [ ] **Step 3**：每例 `./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest --tests "*UnreadCountStreamFlowIT*"` 先红后绿。
- [ ] **Step 4：提交**：subject「新增未读数推送接口与心跳」（含 Task 4 的监听器与 `RealtimeConfiguration`）。

### Task 6：别人回复后 3 秒内推到

**Files：**
- Test: `UnreadCountStreamFlowIT`（续；若 RabbitMQ 容器只在另一个基类里，单独建 `UnreadCountPushFlowIT` 继承它）

- [ ] **Step 1**：`should_push_reply_to_recipient_within_three_seconds`：用户 7 开两条连接、用户 8 开一条；按 PAP-143 的测试辅助向 `interaction.events` 发一条「8 回复了 7 的评论」的 `comment.published`；3 秒内 7 的两条都收到 `replies` = 1 的事件，8 的连接 1 秒内没有新事件。
- [ ] **Step 2**：跑 → 绿（逻辑已在 Task 3 ~ 5 完成；若红，按 systematic-debugging 查）。
- [ ] **Step 3：提交**：subject「补充回复后推送的端到端测试」。

### Task 7：文档、全量检查与独立评审

**Files：**
- Modify: `patra-api/patra-notification/README.md`（子域表 realtime 行补接口；新增「实时推送」一节：接口、配置、心跳与寿命、停机；只追加）
- Modify: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` 第 7 步（spec 第 10 节三条）
- Modify: spec 头部状态 → 已实施，末尾加「实施记录」（偏离、实测结论）

- [ ] **Step 1**：写文档。
- [ ] **Step 2**：派独立评审子代理（`model: "opus"`，只给 spec 路径与本分支 diff 范围，不给本会话结论）；重要问题先写复现测试再修。
- [ ] **Step 3**：`./gradlew check integrationTest --continue`（后台跑）→ 全绿；Gradle 锁或个别 IT 超时先单独重跑。
- [ ] **Step 4**：提交文档；核对 `git diff --cached` 只含本 Issue 路径；给编排会话发验收汇报（SHA、AC ↔ 测试、全量结果、评审发现与处理、留下的小问题、上线步骤、release spec 决策 I 的更正文字）。
