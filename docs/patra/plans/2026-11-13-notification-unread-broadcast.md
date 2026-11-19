# 通知服务多实例推送未读数实施计划（#406）

> **For agentic workers:** 本计划在当前会话内按 superpowers:executing-plans 内联执行（用户约定，不用 subagent-driven），严格 TDD。步骤用复选框（`- [ ]`）跟踪。

**Goal：** 通知服务跑多台时，未读数变化能推到任一实例上的连接；信箱队列跨实例仍只有一个消费者；单实例行为不变。

**Architecture：** 提交后本机照旧直推，再经领域端口把「哪些用户变了」交给一个 1 线程的专用线程池，由它发到 RabbitMQ fanout 交换机；每个实例一条匿名独占队列，收到别的实例发的广播就推自己手上的连接。信箱队列用 `x-single-active-consumer` 保住单消费者，开关加在事件工具库的 `EventQueue` 上。

**Tech Stack：** Java 25、Spring Boot 4.1.2、Spring AMQP 4.1.2（`RabbitTemplate`、`AnonymousQueue`、`@RabbitListener`）、RabbitMQ 4.4、JUnit 5 + AssertJ + Mockito + Awaitility、Testcontainers（PG、RabbitMQ）。

**Spec：** `docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md`

## Global Constraints

- 交换机 `notification.unread.broadcast`：fanout、持久化。每实例一条 `AnonymousQueue`，名字前缀 `notification.unread.`（`Base64UrlNamingStrategy`）。
- 广播消息 JSON 两个字段：`origin`（发送实例 ID）、`userIds`。不带数字。
- 线程池 bean 名 `unreadBroadcastExecutor`：核心 1、最大 1、队列 500、线程名前缀 `unread-broadcast-`；满了丢最旧的一条；停机不等队列里的任务。数字写死，不做配置项。
- 丢弃时打 WARN，60 秒内最多一条，带这段时间丢了几条。
- 广播发送用 Boot 装配的普通 `RabbitTemplate`，不用 `ConfirmingRabbitMessagingTemplate`，不经 Modulith 登记表；通知服务的 `RabbitTemplate` 打开 `usePublisherConnection`。
- `application.yml` 设 `spring.task.execution.mode: force`；推送的 `@Async` 写明 `applicationTaskExecutor`。
- 信箱队列 `notification.inbox` 带 `x-single-active-consumer = true`，`InboxEventListener` 的 `concurrency = "1"` 保留。
- 广播收发两头的异常都接住、打 WARN、不重试、不往外抛。
- 实例 ID 每个 Spring 上下文一个随机值，不取主机名、进程号、静态字段。
- 不改：SSE 协议、网关、`patra-infra/docker/docker-compose.apps.yaml`、提醒邮件与其他 XXL 任务、旧 spec 与 v0.16 release spec、`patra.events.consumer.trusted-packages`。
- 方法一律 `///` Javadoc，注释按 `.claude/rules/code-style.md` 的「注释标准」；测试方法 snake_case。
- 命令都从工作树根目录执行，一律写绝对路径（`<根>/gradlew -p <根> …`），不 `cd`。`<根>` = `/Users/linqibin/Projects/Products/patra/.claude/worktrees/feat-406-notification-broadcast`。
- 每个任务提交前先跑该模块的 `spotlessApply`；提交 message 用 `type(scope): 中文动词开头`，body 每行 ≤ 100 字符，结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；只 `git add` 自己的路径，提交前核对 `git diff --cached`；不 push。
- #405 的会话在主检出改 `patra-tool-events` 的 `EventsDefaultsPostProcessor`（及其测试），本计划不碰这两个文件。

## 对 spec 的细化（实施者定的部分，本计划定下）

1. 广播消息 record 和实例 ID 放 domain（`domain.realtime.model.vo`），infra 发、adapter 收共用同一个类型。
2. 发送实现照 `SmtpMailDeliveryAdapter` 的写法：infra 里的普通类，由 boot 的配置类用 `@Bean` 装配；线程池、交换机、队列、绑定、实例 ID、发布连接定制都放 boot 的 `UnreadBroadcastConfiguration`。
3. 本机推送抛异常时（读库失败）照样发广播（`try / finally`）：别的实例上的连接不该因为这台读库失败而收不到信号。
4. 广播监听带 `id = "unreadBroadcast"`，测试用它停启监听容器。
5. 「通知服务确实打开了单活跃消费者」的断言：用同样的参数再声明一次 `notification.inbox`，参数不一致时 RabbitMQ 会拒绝。

## Review Focus

1. **RabbitMQ 卡住**（发送阻塞）：`broadcast` 立刻返回，本机推送不被拖住；排队满了丢最旧的 → Task 3 的 `should_return_immediately_while_sending_is_stuck`、`should_drop_oldest_and_keep_newest_when_queue_is_full`。
2. **广播队列里来了坏消息**（不是合法 JSON）：这条被丢掉，监听不死，后面的广播照常 → Task 6 的 `should_keep_listening_after_malformed_broadcast`。
3. **广播监听重启之后**（断线重连、测试上下文暂停再恢复）：匿名队列重建，照常收到 → Task 6 的 `should_receive_broadcast_again_after_listener_restart`。
4. **发起变化的实例自己不重复推** → Task 5 的 `should_ignore_broadcast_sent_by_this_instance`、Task 6 的 `should_push_once_on_the_instance_that_made_the_change`。
5. **停机后还有推送任务进来**（线程池已关）：不抛异常 → Task 3 的 `should_not_throw_after_executor_shutdown`。

---

### Task 1: `EventQueue` 的单活跃消费者开关（patra-tool-events）

**Files:**
- Modify: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/consumer/EventQueue.java`
- Modify: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/consumer/EventQueueTest.java`
- Create: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/consumer/EventQueueSingleActiveConsumerIT.java`
- Modify: `patra-tool/patra-tool-events/README.md`（「消费方怎么接」一节）

**Interfaces:**
- Produces: `EventQueue#singleActiveConsumer()`，返回打开开关的新声明；`named(...)` 默认关；`bind(...)` 保留开关状态。record 多一个组件 `boolean singleActiveConsumer`。

- [ ] **Step 1: 先把 spec 和本计划单独提交**

```bash
git add docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md docs/patra/plans/2026-11-13-notification-unread-broadcast.md
git commit -m "docs(notification): 多实例推送未读数的设计与实施计划"
```

- [ ] **Step 2: 写失败的单测**（加进 `EventQueueTest`）

```java
@Test
@DisplayName("打开单活跃消费者：主队列带 x-single-active-consumer，死信队列不带")
void should_mark_only_main_queue_single_active_consumer_when_enabled() {
  Declarables declared =
      EventQueue.named("q").bind("x", "k").singleActiveConsumer().declarables();
  assertThat(queueOf(declared, "q").getArguments())
      .containsEntry("x-single-active-consumer", true)
      .containsEntry("x-dead-letter-exchange", "q.dlx");
  assertThat(queueOf(declared, "q.dlq").getArguments())
      .doesNotContainKey("x-single-active-consumer");
}

@Test
@DisplayName("默认不带 x-single-active-consumer")
void should_not_set_single_active_consumer_by_default() {
  assertThat(queue("notification.inbox").getArguments())
      .doesNotContainKey("x-single-active-consumer");
}

@Test
@DisplayName("先开开关再绑定，开关不丢")
void should_keep_single_active_consumer_when_binding_afterwards() {
  Declarables declared =
      EventQueue.named("q").singleActiveConsumer().bind("x", "k").declarables();
  assertThat(queueOf(declared, "q").getArguments())
      .containsEntry("x-single-active-consumer", true);
}
```

`queueOf(Declarables, String)` 是新加的测试私有方法：按名字从声明里取 `Queue`。

- [ ] **Step 3: 跑，确认失败**

Run: `<根>/gradlew -p <根> :patra-tool:patra-tool-events:test --tests '*EventQueueTest*'`
Expected: 编译失败（`singleActiveConsumer` 不存在）。

- [ ] **Step 4: 实现**

`EventQueue` 改成 `record EventQueue(String queue, Map<String, List<String>> bindings, boolean singleActiveConsumer)`；加 `public EventQueue singleActiveConsumer()`；`declarables()` 里开关打开时对主队列的 `QueueBuilder` 调 `.singleActiveConsumer()`。类注释补一段：这个参数做什么（多台都订阅、同一时刻只有一台收）、已存在的队列不能原地加、要删掉重建。

- [ ] **Step 5: 跑，确认通过**

Run: 同 Step 3。Expected: PASS。

- [ ] **Step 6: 写集成测试 `EventQueueSingleActiveConsumerIT`**

不起 Spring 上下文：用 `RabbitMQContainerInitializer.getRabbitMQContainer()` 的地址建 `CachingConnectionFactory` 和 `RabbitAdmin`，逐个声明 `EventQueue.named("events.it.sac").bind("events.it.sac.x", "k").singleActiveConsumer().declarables()`；两个 `SimpleMessageListenerContainer` 各把收到的消息体记进自己的线程安全列表。`@AfterEach` 停容器、删队列（含 `.dlq`）与两个交换机、销毁连接工厂。

```java
@Test
@DisplayName("两个消费者只有先订阅的收到；它停了以后换另一个收")
void should_deliver_to_first_consumer_only_then_fail_over() {
  first.start();   // start() 返回时消费者已订阅
  second.start();
  for (int i = 0; i < 20; i++) {
    template.convertAndSend("events.it.sac.x", "k", "m" + i);
  }
  await().atMost(WAIT).untilAsserted(() -> assertThat(firstReceived).hasSize(20));
  assertThat(secondReceived).isEmpty();

  first.stop();
  for (int i = 20; i < 25; i++) {
    template.convertAndSend("events.it.sac.x", "k", "m" + i);
  }
  await().atMost(WAIT).untilAsserted(() -> assertThat(secondReceived).hasSize(5));
}
```

`WAIT` = 10 秒。这条测试直接验证 RabbitMQ 的行为，写完就应该通过；如果先订阅的没有独占消息，说明开关没生效，回到 Step 4。

- [ ] **Step 7: 跑集成测试**

Run: `<根>/gradlew -p <根> :patra-tool:patra-tool-events:integrationTest --tests '*EventQueueSingleActiveConsumerIT*'`
Expected: PASS（需本机 Docker）。

- [ ] **Step 8: README**

「消费方怎么接」的示例后加一小段：`.singleActiveConsumer()` 的用途（靠顺序处理保证正确、又要跑多台的队列）、已存在的队列要先停消费者、删队列、再启动。

- [ ] **Step 9: 提交**

```bash
<根>/gradlew -p <根> :patra-tool:patra-tool-events:spotlessApply
git add patra-tool/patra-tool-events
git commit -m "feat(events): 消费队列声明支持单活跃消费者"
```

---

### Task 2: 信箱队列打开单活跃消费者

**Files:**
- Modify: `patra-api/patra-notification/patra-notification-boot/src/main/java/dev/patra/notification/config/InboxQueueConfiguration.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/test/java/dev/patra/notification/config/InboxQueueConfigurationTest.java`
- Modify: `patra-api/patra-notification/patra-notification-adapter/src/main/java/dev/patra/notification/adapter/inbox/mq/InboxEventListener.java`（只改注释）

**Interfaces:**
- Consumes: Task 1 的 `EventQueue#singleActiveConsumer()`。

- [ ] **Step 1: 写失败的单测**

```java
@Test
void should_declare_inbox_queue_as_single_active_consumer_with_five_bindings() {
  List<Declarable> declared =
      List.copyOf(new InboxQueueConfiguration().inboxQueue().getDeclarables());
  Queue inbox =
      declared.stream()
          .filter(Queue.class::isInstance)
          .map(Queue.class::cast)
          .filter(queue -> queue.getName().equals(InboxConsumer.NAME))
          .findFirst()
          .orElseThrow();
  assertThat(inbox.getArguments()).containsEntry("x-single-active-consumer", true);
  assertThat(declared)
      .filteredOn(Binding.class::isInstance)
      .map(Binding.class::cast)
      .filteredOn(binding -> binding.getDestination().equals(InboxConsumer.NAME))
      .hasSize(5);
}
```

- [ ] **Step 2: 跑，确认失败**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:test --tests '*InboxQueueConfigurationTest*'`
Expected: FAIL（参数里没有 `x-single-active-consumer`）。

- [ ] **Step 3: 实现**

`inboxQueue()` 的链上加 `.singleActiveConsumer()`；类注释补一句为什么开。`InboxEventListener` 的注释改成：单实例内靠 `concurrency = "1"`，跨实例靠队列的单活跃消费者；并如实写交接窗口（spec 7.3：活跃实例优雅停机时，它已预取的几条和新活跃实例收到的可能短暂并行）。写之前对照 RabbitMQ 的 single active consumer 文档和 Spring AMQP 监听容器的停机行为核对这段描述，按核对结果写。

- [ ] **Step 4: 跑，确认通过；再跑一条流程测试确认上下文能起、信箱照常消费**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:test --tests '*InboxQueueConfigurationTest*'`
Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:integrationTest --tests '*UnreadCountStreamFlowIT*'`
Expected: 都 PASS。

- [ ] **Step 5: 提交**

```bash
<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:spotlessApply :patra-api:patra-notification:patra-notification-adapter:spotlessApply
git add patra-api/patra-notification/patra-notification-boot patra-api/patra-notification/patra-notification-adapter
git commit -m "feat(notification): 信箱队列改为单活跃消费者，多台时只有一台在收"
```

---

### Task 3: 广播的领域类型、端口与发送实现

**Files:**
- Create: `patra-api/patra-notification/patra-notification-domain/src/main/java/dev/patra/notification/domain/realtime/model/vo/InstanceId.java`
- Create: `…/domain/realtime/model/vo/UnreadCountBroadcast.java`
- Create: `…/domain/realtime/port/broadcast/UnreadCountBroadcastPort.java`
- Create: `patra-api/patra-notification/patra-notification-domain/src/test/java/dev/patra/notification/domain/realtime/model/vo/UnreadCountBroadcastTest.java`、`InstanceIdTest.java`
- Modify: `patra-api/patra-notification/patra-notification-infra/build.gradle.kts`（加 `implementation("org.springframework.boot:spring-boot-starter-amqp")`，注释写用途）
- Create: `patra-api/patra-notification/patra-notification-infra/src/main/java/dev/patra/notification/infra/realtime/broadcast/UnreadCountBroadcastAdapter.java`
- Create: `…/infra/realtime/broadcast/DiscardOldestBroadcastPolicy.java`
- Create: `patra-api/patra-notification/patra-notification-infra/src/test/java/dev/patra/notification/infra/realtime/broadcast/UnreadCountBroadcastAdapterTest.java`、`DiscardOldestBroadcastPolicyTest.java`

**Interfaces:**
- Produces:
  - `record InstanceId(String value)`：`static InstanceId random()`（UUID 字符串）；空白值抛 `IllegalArgumentException`。
  - `record UnreadCountBroadcast(String origin, Set<Long> userIds)`：紧凑构造器 `Set.copyOf`；`static UnreadCountBroadcast of(InstanceId origin, Collection<Long> userIds)`；`boolean sentBy(InstanceId instance)`。
  - `interface UnreadCountBroadcastPort { void broadcast(Set<Long> userIds); }`：契约是不阻塞、不抛异常、尽力而为。
  - `final class UnreadCountBroadcastAdapter implements UnreadCountBroadcastPort`：常量 `EXCHANGE = "notification.unread.broadcast"`；构造器 `(RabbitTemplate rabbitTemplate, InstanceId instanceId, Executor executor)`。
  - `final class DiscardOldestBroadcastPolicy implements RejectedExecutionHandler`：构造器 `(Clock clock)`；常量 `WARN_INTERVAL = Duration.ofSeconds(60)`。

按 TDD 一个用例一个用例来；下面按类列出用例和断言。

- [ ] **Step 1: domain 两个 record 与端口**

`UnreadCountBroadcastTest`：

```java
@Test
void should_copy_user_ids_and_carry_origin_value() {
  Set<Long> ids = new HashSet<>(Set.of(7L, 8L));
  UnreadCountBroadcast broadcast = UnreadCountBroadcast.of(new InstanceId("a"), ids);
  ids.add(9L);
  assertThat(broadcast.origin()).isEqualTo("a");
  assertThat(broadcast.userIds()).containsExactlyInAnyOrder(7L, 8L);
}

@Test
void should_tell_whether_it_was_sent_by_an_instance() {
  UnreadCountBroadcast broadcast = UnreadCountBroadcast.of(new InstanceId("a"), Set.of(7L));
  assertThat(broadcast.sentBy(new InstanceId("a"))).isTrue();
  assertThat(broadcast.sentBy(new InstanceId("b"))).isFalse();
}
```

`InstanceIdTest`：`should_generate_different_values_each_time`（两次 `random()` 不等）、`should_reject_blank_value`。

Run（先红后绿）: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-domain:test --tests '*realtime*'`

- [ ] **Step 2: `DiscardOldestBroadcastPolicyTest`**

夹具：`new ThreadPoolExecutor(1, 1, 0, SECONDS, new ArrayBlockingQueue<>(2), policy)`；第一个任务卡在一个 `CountDownLatch` 上占住线程。时钟用一个可拨的 `Clock`（测试里的小内部类，`instant()` 读一个 `AtomicReference<Instant>`）。日志用 `OutputCaptureExtension` 取（infra 测试类路径上没有它时改用 Logback 的 `ListAppender`）。

```java
@Test
void should_drop_oldest_and_keep_newest_when_queue_is_full() {
  // 占住线程后依次提交 A、B、C（队列容量 2）→ 放开 → 实际执行的是 B、C
  assertThat(ran).containsExactly("B", "C");
}

@Test
void should_warn_once_per_minute_with_dropped_count(CapturedOutput output) {
  // 同一时刻连丢 3 条 → 只有 1 行 WARN，含「1」
  // 时钟拨快 61 秒再丢 1 条 → 第 2 行 WARN，含「3」（上次打日志之后一共丢了 3 条）
}

@Test
void should_discard_quietly_after_shutdown() {
  executor.shutdownNow();
  assertThatCode(() -> policy.rejectedExecution(task, executor)).doesNotThrowAnyException();
  assertThat(ran).isEmpty();
}
```

WARN 的文字固定为：`未读数广播排队已满，丢弃最旧的 {} 条（RabbitMQ 发送变慢或不可用）`。

实现：没关时 `executor.getQueue().poll()` 再 `executor.execute(task)`；计数用 `AtomicLong`，上次打日志的时刻用 `AtomicReference<Instant>` 做 CAS，保证并发下也不多打。

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-infra:test --tests '*DiscardOldestBroadcastPolicyTest*'`

- [ ] **Step 3: `UnreadCountBroadcastAdapterTest`**

```java
private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
private final InstanceId self = new InstanceId("a");

@Test
void should_send_origin_and_user_ids_to_fanout_exchange() {
  new UnreadCountBroadcastAdapter(rabbit, self, Runnable::run).broadcast(Set.of(7L, 8L));
  verify(rabbit)
      .convertAndSend(
          "notification.unread.broadcast", "", UnreadCountBroadcast.of(self, Set.of(7L, 8L)));
}

@Test
void should_skip_empty_user_ids() { /* verifyNoInteractions(rabbit) */ }

@Test
void should_not_throw_when_sending_fails() {
  // rabbit 抛 AmqpException，执行器 Runnable::run → broadcast 不抛
}

@Test
void should_return_immediately_while_sending_is_stuck() {
  // 真线程池（1 线程、队列 500、DiscardOldestBroadcastPolicy）；rabbit 的 convertAndSend 卡在闩上
  // assertTimeoutPreemptively(1 秒)：连续 broadcast 3 次都返回；最后放开闩、关线程池
}

@Test
void should_not_throw_after_executor_shutdown() {
  // 线程池 shutdown 之后 broadcast → 不抛，rabbit 没被调用
}
```

实现：`broadcast` 里空集合直接返回；`executor.execute(...)` 外面接住 `RuntimeException`（线程池已关时 Spring 抛 `TaskRejectedException`）打 WARN；任务里 `convertAndSend` 的 `RuntimeException` 接住打 WARN，只带异常的 `getMessage()`。类注释讲清：为什么隔一层线程池（spec 5.2）、为什么不等确认。

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-infra:test --tests '*UnreadCountBroadcastAdapterTest*'`

- [ ] **Step 4: 整模块的单测与架构规则**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-domain:check :patra-api:patra-notification:patra-notification-infra:test :patra-api:patra-notification:patra-notification-boot:test --tests '*NotificationArchitectureTest*'`
Expected: PASS（domain 的 `check` 守领域层纯净；架构测试守分包）。

- [ ] **Step 5: 模块图与提交**

```bash
<根>/gradlew -p <根> dumpModuleGraph
<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-domain:spotlessApply :patra-api:patra-notification:patra-notification-infra:spotlessApply
git add patra-api/patra-notification/patra-notification-domain patra-api/patra-notification/patra-notification-infra
git status --short   # module-graph.json 有变化就一起 add
git commit -m "feat(notification): 新增未读数广播端口与发送实现，发送隔离在专用线程池"
```

---

### Task 4: 装配：线程池、交换机、队列、发布连接

**Files:**
- Create: `patra-api/patra-notification/patra-notification-boot/src/main/java/dev/patra/notification/config/UnreadBroadcastConfiguration.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/test/java/dev/patra/notification/config/UnreadBroadcastConfigurationTest.java`
- Modify: `patra-api/patra-notification/patra-notification-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-notification/patra-notification-boot/src/test/java/dev/patra/notification/ApplicationConfigTest.java`
- Modify: `patra-api/patra-notification/patra-notification-boot/src/main/java/dev/patra/notification/config/RealtimeConfiguration.java`（只改注释）
- Modify: `patra-api/patra-notification/patra-notification-app/src/main/java/dev/patra/notification/app/realtime/listener/UnreadCountPushListener.java`（只把 `@Async` 改成 `@Async("applicationTaskExecutor")`，注释同步）

**Interfaces:**
- Consumes: Task 3 的全部类型。
- Produces（bean 名即方法名）:
  - `InstanceId realtimeInstanceId()`
  - `ThreadPoolTaskExecutor unreadBroadcastExecutor(Clock clock)`；常量 `EXECUTOR = "unreadBroadcastExecutor"`
  - `FanoutExchange unreadBroadcastExchange()`
  - `AnonymousQueue unreadBroadcastQueue()`（Task 5 的监听用 `#{unreadBroadcastQueue.name}` 引用）
  - `Binding unreadBroadcastBinding(AnonymousQueue unreadBroadcastQueue, FanoutExchange unreadBroadcastExchange)`
  - `RabbitTemplateCustomizer publisherConnectionCustomizer()`
  - `UnreadCountBroadcastPort unreadCountBroadcastPort(RabbitTemplate rabbitTemplate, InstanceId realtimeInstanceId, @Qualifier(EXECUTOR) Executor executor)`

- [ ] **Step 1: 写失败的单测 `UnreadBroadcastConfigurationTest`**（直接调配置类的方法，不起上下文）

```java
private final UnreadBroadcastConfiguration config = new UnreadBroadcastConfiguration();

@Test
void should_build_single_thread_executor_with_bounded_queue() {
  ThreadPoolTaskExecutor executor = config.unreadBroadcastExecutor(Clock.systemUTC());
  executor.initialize();
  try {
    assertThat(executor.getCorePoolSize()).isEqualTo(1);
    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("unread-broadcast-");
    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(500);
    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(DiscardOldestBroadcastPolicy.class);
  } finally {
    executor.shutdown();
  }
}

@Test
void should_declare_durable_fanout_exchange_and_private_queue_bound_to_it() {
  FanoutExchange exchange = config.unreadBroadcastExchange();
  AnonymousQueue queue = config.unreadBroadcastQueue();
  assertThat(exchange.getName()).isEqualTo("notification.unread.broadcast");
  assertThat(exchange.isDurable()).isTrue();
  assertThat(queue.getName()).startsWith("notification.unread.");
  assertThat(queue.isDurable()).isFalse();
  assertThat(queue.isExclusive()).isTrue();
  assertThat(queue.isAutoDelete()).isTrue();
  Binding binding = config.unreadBroadcastBinding(queue, exchange);
  assertThat(binding.getDestination()).isEqualTo(queue.getName());
  assertThat(binding.getExchange()).isEqualTo("notification.unread.broadcast");
}

@Test
void should_give_each_call_its_own_instance_id() {
  assertThat(config.realtimeInstanceId()).isNotEqualTo(config.realtimeInstanceId());
}

@Test
void should_publish_on_a_separate_connection() {
  RabbitTemplate template = mock(RabbitTemplate.class);
  config.publisherConnectionCustomizer().customize(template);
  verify(template).setUsePublisherConnection(true);
}
```

`ApplicationConfigTest` 加：

```java
@Test
void should_force_boot_task_executor_beside_broadcast_executor() throws IOException {
  assertThat(property("spring.task.execution.mode")).isEqualTo("force");
}
```

（`property(...)` 用该测试类里现有的读 `application.yml` 的辅助方法。）

- [ ] **Step 2: 跑，确认失败**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:test --tests '*UnreadBroadcastConfigurationTest*' --tests '*ApplicationConfigTest*'`
Expected: 编译失败（配置类不存在）。

- [ ] **Step 3: 实现**

- `UnreadBroadcastConfiguration`：`@Configuration(proxyBeanMethods = false)`，按 Interfaces 的签名写。线程池不设 `waitForTasksToCompleteOnShutdown`（默认不等）。类注释讲清这几样东西各自为什么存在（spec 5.2、5.3、6.1、6.2），以及监听按 bean 名 `unreadBroadcastQueue` 引用队列。
- `application.yml`：`spring.task.execution.mode: force`，注释写原因：广播另有一个线程池 bean，有别的 `Executor` bean 时 Boot 默认不再建 `applicationTaskExecutor`；推送的 `@Async` 和 MVC 的异步请求都用它。
- `UnreadCountPushListener`：`@Async("applicationTaskExecutor")`。容器里现在有两个线程池，不写名字时 Spring 选哪个不确定。
- `RealtimeConfiguration` 注释里「服务里没有别的 `Executor` Bean，Boot 的那个才不会让位」改成现在的事实。

- [ ] **Step 4: 跑，确认通过；再确认上下文能起、推送还在 Boot 的线程池上**

Run: 同 Step 2，Expected: PASS。
Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:integrationTest --tests '*UnreadCountStreamFlowIT*' --tests '*PatraNotificationApplicationIT*'`
Expected: PASS。`applicationTaskExecutor` 不存在时 `@Async("applicationTaskExecutor")` 调用会失败、推送用例跟着红，所以这条通过就说明 Boot 的线程池还在。

- [ ] **Step 5: 提交**

```bash
<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:spotlessApply :patra-api:patra-notification:patra-notification-app:spotlessApply
git add patra-api/patra-notification/patra-notification-boot patra-api/patra-notification/patra-notification-app
git commit -m "feat(notification): 装配未读数广播的线程池、交换机与每实例的临时队列"
```

---

### Task 5: 广播监听

**Files:**
- Create: `patra-api/patra-notification/patra-notification-adapter/src/main/java/dev/patra/notification/adapter/realtime/mq/UnreadCountBroadcastListener.java`
- Create: `patra-api/patra-notification/patra-notification-adapter/src/test/java/dev/patra/notification/adapter/realtime/mq/UnreadCountBroadcastListenerTest.java`

**Interfaces:**
- Consumes: `UnreadCountBroadcast`、`InstanceId`（Task 3）；bean `unreadBroadcastQueue`（Task 4）；`UnreadCountStreamRegistry#push(Collection<Long>)`（现有）。
- Produces: `@Component UnreadCountBroadcastListener`，构造器 `(UnreadCountStreamRegistry registry, InstanceId instanceId)`；方法 `@RabbitListener(id = "unreadBroadcast", queues = "#{unreadBroadcastQueue.name}") public void on(UnreadCountBroadcast broadcast)`。

- [ ] **Step 1: 写失败的单测**

```java
private final UnreadCountStreamRegistry registry = mock(UnreadCountStreamRegistry.class);
private final InstanceId self = new InstanceId("a");
private final UnreadCountBroadcastListener listener =
    new UnreadCountBroadcastListener(registry, self);

@Test
void should_ignore_broadcast_sent_by_this_instance() {
  listener.on(UnreadCountBroadcast.of(self, Set.of(7L)));
  verifyNoInteractions(registry);
}

@Test
void should_push_users_from_another_instance() {
  listener.on(UnreadCountBroadcast.of(new InstanceId("b"), Set.of(7L, 8L)));
  verify(registry).push(Set.of(7L, 8L));
}

@Test
void should_not_throw_when_pushing_fails() {
  doThrow(new IllegalStateException("读库失败")).when(registry).push(any());
  assertThatCode(() -> listener.on(UnreadCountBroadcast.of(new InstanceId("b"), Set.of(7L))))
      .doesNotThrowAnyException();
}
```

- [ ] **Step 2: 跑，确认失败**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-adapter:test --tests '*UnreadCountBroadcastListenerTest*'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

按 Interfaces 的签名；`push` 的 `RuntimeException` 接住打 WARN。类注释讲清：每个实例一条自己的队列、为什么跳过自己发的、为什么不让异常触发监听重试（重试会拖住这条队列，丢一次推送可以接受）。`adapter.realtime` 的 `package-info` 里如果列了本包的内容，同步补上 `mq`。

- [ ] **Step 4: 跑，确认通过；架构规则与上下文**

Run: 同 Step 2，Expected: PASS。
Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:test --tests '*NotificationArchitectureTest*'`
Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:integrationTest --tests '*UnreadCountStreamFlowIT*'`
Expected: 都 PASS。

- [ ] **Step 5: 提交**

```bash
<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-adapter:spotlessApply
git add patra-api/patra-notification/patra-notification-adapter
git commit -m "feat(notification): 监听别的实例发来的未读数广播并推给本机连接"
```

---

### Task 6: 接通发送，并用两个实例验证

**Files:**
- Create: `patra-api/patra-notification/patra-notification-boot/src/integrationTest/java/dev/patra/notification/support/SecondNotificationInstance.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/integrationTest/java/dev/patra/notification/realtime/UnreadCountBroadcastFlowIT.java`
- Modify: `patra-api/patra-notification/patra-notification-app/src/main/java/dev/patra/notification/app/realtime/listener/UnreadCountPushListener.java`
- Modify: `patra-api/patra-notification/patra-notification-app/src/test/java/dev/patra/notification/app/realtime/listener/UnreadCountPushListenerTest.java`

**Interfaces:**
- Consumes: `UnreadCountBroadcastPort#broadcast(Set<Long>)`（Task 3）；广播监听的 id `unreadBroadcast`（Task 5）。
- Produces: `UnreadCountPushListener` 的构造器变成 `(UnreadCountStreamRegistry registry, UnreadCountBroadcastPort broadcast)`。

- [ ] **Step 1: 第二个实例的测试支撑 `SecondNotificationInstance`**

```java
/// 同一个 JVM 里的第二个通知服务实例：和测试类的上下文共用 PostgreSQL、RabbitMQ 容器与假 identity / interaction。
public final class SecondNotificationInstance implements AutoCloseable {
  public static SecondNotificationInstance start(String identityBaseUrl, String interactionBaseUrl);
  public int port();                    // local.server.port
  public <T> T bean(Class<T> type);
  @Override public void close();
}
```

用 `new SpringApplicationBuilder(PatraNotificationApplication.class).profiles("test").initializers(new NotificationITPostgreSQLContainerInitializer(), new RabbitMQContainerInitializer()).properties(...)` 启动，属性：

- `server.port=0`
- `spring.cloud.discovery.enabled=true`
- `spring.rabbitmq.listener.simple.auto-startup=true`（测试配置默认关监听；不打开的话广播监听和信箱监听都不起）
- `spring.cloud.discovery.client.simple.instances.patra-identity[0].uri` / `patra-interaction[0].uri` = 传进来的两个地址
- `spring.datasource.hikari.maximum-pool-size=4`、`spring.datasource.hikari.minimum-idle=1`

测试密钥不用管：`patra-tool-security` 的 testFixtures 经 `spring.factories` 里的 `EnvironmentPostProcessor` 装进每个 `SpringApplication`。

- [ ] **Step 2: 写失败的流程测试 `UnreadCountBroadcastFlowIT`**

`extends NotificationFlowITSupport`，不加 `@TestPropertySource`（共用缓存的上下文，它就是实例 A）。实例 B 是静态字段：在 `@BeforeEach` 里第一次用到时启动（此时 A 已经起好并订阅了信箱，B 后订阅，不会成为信箱的活跃消费者），`@AfterAll` 关闭。`openOnA(userId)` / `openOnB(userId)` 照 `UnreadCountStreamFlowIT#open` 的写法，端口分别取 A 的 `local.server.port` 和 `b.port()`。`replyTo(userId)` 照 `UnreadCountStreamFlowIT` 的同名方法。每条连接先 `nextEvent(2 秒)` 读掉连上时的当前值。

```java
/// 验收 1：连接在 B，消息被别的实例消费（B 后订阅，不是信箱的活跃消费者）。
@Test
void should_push_to_stream_on_instance_that_did_not_consume_the_message() throws Exception {
  try (SseTestConnection onB = openOnB(USER)) {
    onB.nextEvent(Duration.ofSeconds(2)).orElseThrow();
    rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));
    assertThat(replies(onB.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);
  }
}

/// 验收 2：同一用户在两个实例上都有连接，两边都收到。
@Test
void should_push_to_streams_on_both_instances() throws Exception { /* onA、onB 都收到 replies = 1 */ }

/// 请求线程触发的路径：在 B 上全部标已读，A 上的连接收到 replies = 0。
@Test
void should_push_across_instances_when_marked_read_on_the_other_one() throws Exception {
  // 先发一条回复，等 onA 收到 replies = 1；再用 JDK HttpClient 对 B 发 PUT /me/notifications/read
  // （带 TestIdentity.headers(USER) 的 Authorization）；onA 3 秒内收到 replies = 0
}

/// 反目标：发起变化的实例只推一次（自己的广播被跳过）。
@Test
void should_push_once_on_the_instance_that_made_the_change() throws Exception {
  // 先发一条回复，等 onA 收到 replies = 1；对 A 发 PUT /me/notifications/read
  // onA 收到 replies = 0；之后 1 秒内 onA.nextEvent 为空
}

/// 通知服务确实把信箱队列声明成了单活跃消费者，而且两个实例都订阅着。
@Test
void should_declare_inbox_as_single_active_consumer_with_both_instances_subscribed() {
  AMQP.Queue.DeclareOk ok =
      rabbit.execute(
          channel ->
              channel.queueDeclare(
                  "notification.inbox", true, false, false,
                  Map.of(
                      "x-dead-letter-exchange", "notification.inbox.dlx",
                      "x-dead-letter-routing-key", "notification.inbox",
                      "x-single-active-consumer", true)));
  assertThat(ok.getConsumerCount()).isGreaterThanOrEqualTo(2);
}

/// Review Focus 2：坏消息被丢掉，监听不死。
@Test
void should_keep_listening_after_malformed_broadcast() throws Exception {
  // rabbit.send("notification.unread.broadcast", "", 一条 body 为 "{oops" 的 application/json Message)
  // 之后照 should_push_across_instances_when_marked_read_on_the_other_one 的流程，onA 仍然收到
}

/// Review Focus 3：广播监听重启后（匿名队列随消费者取消被删），重建并照常收到。
@Test
void should_receive_broadcast_again_after_listener_restart() throws Exception {
  // A 的 RabbitListenerEndpointRegistry.getListenerContainer("unreadBroadcast")：stop() 再 start()
  // 之后照 should_push_across_instances_when_marked_read_on_the_other_one 的流程，onA 仍然收到
}

/// spec 5.4：没有队列可投的广播被退回时，发送方不抛异常。
@Test
void should_not_throw_when_broadcast_is_returned() {
  // 用 AmqpAdmin 声明一个没有任何绑定的临时 fanout 交换机，rabbit.convertAndSend 发一条
  // assertThatCode(...).doesNotThrowAnyException()；最后删掉这个交换机
}

/// spec 5.2：两个线程池都在，广播线程池带着监控指标。
@Test
void should_keep_boot_executor_and_expose_broadcast_executor_metrics() {
  assertThat(context.getBean("applicationTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
  assertThat(context.getBean("unreadBroadcastExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
  assertThat(
          meters.find("executor.queued").tag("name", "unreadBroadcastExecutor").gauge())
      .isNotNull();
}
```

`replies(event)` = `JSON.readTree(SseTestConnection.data(event)).get("replies").asInt()`。`context` 是注入的 `ApplicationContext`，`meters` 是注入的 `MeterRegistry`。

指标那条如果因为通知服务没有自动绑定线程池指标而失败：不为它加依赖，删掉这条断言里的指标部分，并把 spec 5.2 和 README 里「自动带监控指标」的说法改成事实，在交付时告诉用户。

- [ ] **Step 3: 跑，确认失败**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:integrationTest --tests '*UnreadCountBroadcastFlowIT*'`
Expected: 跨实例的几条 FAIL（B 上 3 秒内没有事件：还没有人发广播）；单活跃消费者、退回、线程池三条应当已经 PASS。

- [ ] **Step 4: 写失败的单测**（改 `UnreadCountPushListenerTest`）

```java
private final UnreadCountStreamRegistry registry = mock(UnreadCountStreamRegistry.class);
private final UnreadCountBroadcastPort broadcast = mock(UnreadCountBroadcastPort.class);
private final UnreadCountPushListener listener = new UnreadCountPushListener(registry, broadcast);

@Test
void should_push_locally_then_broadcast_to_other_instances() {
  listener.on(UnreadCountsChangedEvent.of(Set.of(7L, 8L)));
  InOrder order = inOrder(registry, broadcast);
  order.verify(registry).push(Set.of(7L, 8L));
  order.verify(broadcast).broadcast(Set.of(7L, 8L));
}

@Test
void should_still_broadcast_when_local_push_fails() {
  doThrow(new IllegalStateException("读库失败")).when(registry).push(any());
  assertThatThrownBy(() -> listener.on(UnreadCountsChangedEvent.of(Set.of(7L))))
      .isInstanceOf(IllegalStateException.class);
  verify(broadcast).broadcast(Set.of(7L));
}
```

原来的 `should_push_to_every_recipient_of_the_event` 被第一条取代，删掉。

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-app:test --tests '*UnreadCountPushListenerTest*'`
Expected: 编译失败（构造器只有一个参数）。

- [ ] **Step 5: 实现**

`on()`：`try { registry.push(ids); } finally { broadcast.broadcast(ids); }`。类注释补上：本机直推、广播只给别的实例、为什么先推后播（spec 2.2、5.1），以及广播端口不阻塞、不抛异常。

- [ ] **Step 6: 跑，确认全部通过**

Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-app:test --tests '*UnreadCountPushListenerTest*'`
Run: `<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-boot:integrationTest --tests '*UnreadCountBroadcastFlowIT*' --tests '*UnreadCountStreamFlowIT*'`
Expected: 都 PASS。再翻一遍这次运行的测试日志：广播相关的 WARN 只应出现在故意制造失败的用例里（坏消息那条），没有成串的退回或连接错误。

- [ ] **Step 7: 提交**

```bash
<根>/gradlew -p <根> :patra-api:patra-notification:patra-notification-app:spotlessApply :patra-api:patra-notification:patra-notification-boot:spotlessApply
git add patra-api/patra-notification/patra-notification-app patra-api/patra-notification/patra-notification-boot
git commit -m "feat(notification): 未读数变化广播给其他实例，连接在任一实例都能收到"
```

---

### Task 7: 文档同步与全量门控

**Files:**
- Modify: `patra-api/patra-notification/README.md`（「realtime：实时推送」一节后加「多实例」小节；「4. 运行」里如有单实例的说法同步）
- Modify: `patra-api/patra-notification/patra-notification-app/src/main/java/dev/patra/notification/app/realtime/service/UnreadCountStreamRegistry.java`（只改注释里与单实例有关的说法）
- Modify: `docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md`（状态行改「已实施」；有偏离时在文末加「实施记录」一节）

- [ ] **Step 1: notification README 的「多实例」小节**

写四件事，每件两三句：广播怎么走（fanout、每实例一条临时队列、本机直推、专用线程池满了就丢）；信箱靠单活跃消费者，以及交接窗口；提醒邮件为什么不用改（spec 第 8 节的结论：路由「第一个」加数据层的认领、唯一索引、幂等键）；上线时改队列参数要先停、再删、再起，以及 CD 自动回滚后的处理（spec 第 12 节）。README 只讲当前事实，不写推导。

- [ ] **Step 2: 对照新增文件核对文档**

Run: `git diff --stat main...HEAD`
逐个新增文件对照：README、类注释、`package-info` 里有没有该提到而没提的。

- [ ] **Step 3: 全量门控**

Run: `<根>/gradlew -p <根> spotlessApply`
Run: `<根>/gradlew -p <根> check integrationTest`
Expected: BUILD SUCCESSFUL。跑之前先看 `uptime`：别的会话也在跑 Gradle、负载很高时，网关的超时类集成测试和 `JpaAutoConfigurationTest` 会抖，等负载降下来单独重跑失败的任务，不当成回归。
Run: `<根>/gradlew -p <根> dumpModuleGraph`，`git status --short` 确认 `module-graph.json` 没有未提交的变化。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-notification/README.md patra-api/patra-notification/patra-notification-app docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md
git commit -m "docs(notification): 补充多实例推送、信箱单活跃消费者与上线步骤的说明"
```

- [ ] **Step 5: 停下来等用户验收**

把验收证据交给用户：`UnreadCountBroadcastFlowIT` 各用例的结果、全量门控的输出、与 spec 的偏离（如有）。用户确认后再走收尾：派 `comment-reviewer` 审本分支改动的注释，开 draft PR（描述里写 `Closes #406` 和 spec 第 12 节的上线步骤），转 ready，绑定 PR 监控。上线步骤里「停服务、删队列」要在合并前由用户在 mini 上做，PR 描述里写醒目。
