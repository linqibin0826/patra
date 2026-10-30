# 通用事件模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，按项目约定不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `patra-tool-events`，让服务可靠地把领域事件经 Modulith 登记表外送到 RabbitMQ（等 publisher confirm），并给消费端去重、死信与默认配置。

**Architecture:** Modulith 2.0.8 JDBC 登记表当 Outbox，官方 `spring-modulith-events-amqp` 外送；在它下面用 `RabbitMessagingTemplate` 子类同步等确认；XXL-Job 任务补投；消费端 `processed_event` 表 + `ProcessedEventPort`，`EventQueue` 声明死信对。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Modulith 2.0.8、Spring AMQP 4.0.5、RabbitMQ 4.4.0、PostgreSQL 17、Testcontainers 1.21.4、XXL-Job 3.2.0

**Spec:** `docs/patra/specs/2026-10-30-event-module-design.md`

## Global Constraints

- Modulith 经 `spring-modulith-bom` 导入：PAP-152（Boot 4.1）合入前是 `2.0.8`，之后按 Task 10 升到 2.1.x；不引 `spring-modulith-starter-*`、`spring-modulith-core`
- 工具模块规则 R1–R5：只依赖 `patra-tool-core`；bean 只在 `dev.patra.tool.events.autoconfigure` 的 `Events<Aspect>AutoConfiguration`（`public final class`）里注册并登记 imports；每个自动配置类有同名 `ApplicationContextRunner` 测试；配置类是 record、前缀 `patra.events`；无 `@Component` / `@Service` / `@Repository`
- 所有方法写 `///` Javadoc；不写全类名；测试方法 snake_case
- 测试镜像 `rabbitmq:4.4.0-management`
- 外送监听器 ID：`org.springframework.modulith.events.support.DelegatingEventExternalizer.externalize(java.lang.Object)`
- 提交：全部完成、编排会话同意后一次提交到 `claude/v016-events-7dd164`（用户规则：做完先验收再提交），因此各任务不单独 commit；改了 `*.gradle.kts` 要在汇报里提醒用户在 IDEA 点「加载 Gradle 更改」

## Review Focus

1. **补投换 ID**：领域事件反序列化后再映射，`eventId` 必须不变 → Task 7 的超时 IT 断言两次投递同一 `messageId`、效果表 1 行。
2. **重试时去重记录被提前写入**：失败的那次事务回滚后 `processed_event` 不能留行，否则重试被当成重复跳过 → Task 7 的死信 IT 断言 `processed_event` 为 0 行、监听器被调 4 次。
3. **broker 恢复后连接自动恢复**：`stop_app` / `start_app` 之后发布与消费都要恢复，不需要重启应用 → Task 7 的 broker 不可达 IT。
4. **服务关掉确认配置**：服务 yml 覆盖 `publisher-confirm-type` 时要启动失败而不是静默不等确认 → Task 3 的启动失败契约测试。
5. **无事务调用去重**：`markProcessed` 在事务外调用必须报错，不能自己开事务 → Task 5 的 IT。

---

### Task 1: RabbitMQ 测试容器

**Files:**
- Modify: `gradle/libs.versions.toml`（`[libraries]` 的 Testcontainers 段加 `testcontainers-rabbitmq = { module = "org.testcontainers:rabbitmq" }`）
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`api(libs.testcontainers.rabbitmq)`）
- Modify: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/container/ContainerType.java`（加 `RABBITMQ`，注释写镜像）
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/container/initializer/RabbitMQContainerInitializer.java`
- Test: `patra-tool/patra-tool-test/src/integrationTest/java/dev/patra/tool/test/container/initializer/RabbitMQContainerInitializerIT.java`

**Interfaces:**
- Produces: `RabbitMQContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>`；`public static RabbitMQContainer getRabbitMQContainer()`（JVM 内单例，注册到 `ContainerRegistry`）；`initialize` 写 `spring.rabbitmq.host / port / username / password`

- [ ] **Step 1: 写失败的 IT**（照 `RedisContainerInitializerIT`）
  - `should_start_rabbitmq_and_publish_connection_properties`：`initialize` 后从环境读 host、port，`Socket` 写协议头 `"AMQP\0\0\u0009\u0001"`（字节 `65 77 81 80 0 0 9 1`），读回第一个字节 `== 1`（METHOD 帧，即 `connection.start`）；`username`、`password` 非空
  - `should_reuse_the_same_container`：两次 `getRabbitMQContainer()` 是同一个实例
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-test:integrationTest --tests "*RabbitMQContainerInitializerIT"` → 编译失败（类不存在）
- [ ] **Step 3:** 实现初始化器：`new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.4.0-management"))`，属性取 `getAmqpPort()`、`getAdminUsername()`、`getAdminPassword()`
- [ ] **Step 4:** 同一命令 → PASS

### Task 2: 模块骨架、构建接线与默认值

**Files:**
- Modify: `gradle/libs.versions.toml`（`[versions]` 加 `spring-modulith = "2.0.8"`）
- Modify: `build-logic/src/main/kotlin/PatraDependencyManagement.kt`（`imports` 里加 `mavenBom("org.springframework.modulith:spring-modulith-bom:${version("spring-modulith")}")`）
- Modify: `settings.gradle.kts`（`includeAt(":patra-tool:patra-tool-events", "patra-tool/patra-tool-events")`，放在 patra-tool 段）
- Create: `patra-tool/patra-tool-events/build.gradle.kts`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/autoconfigure/EventsDefaultsPostProcessor.java`
- Create: `patra-tool/patra-tool-events/src/main/resources/META-INF/spring.factories`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/autoconfigure/EventsDefaultsPostProcessorTest.java`

**Interfaces:**
- Produces: `EventsDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered`，`static final Map<String, Object> DEFAULTS`（spec 4.9 的 14 个键，用 `Map.ofEntries`），`getOrder()` 返回 `PatraOrders.DEFAULTS_POST_PROCESSOR`

build 依赖：`api(project(":patra-tool:patra-tool-core"))`、`api("org.springframework.boot:spring-boot-starter-amqp")`、`api("org.springframework.boot:spring-boot-starter-jdbc")`、`api("org.springframework.modulith:spring-modulith-events-api")`、`implementation("org.springframework.modulith:spring-modulith-events-core")`、`runtimeOnly` 三个：`-events-jdbc`、`-events-jackson`、`-events-amqp`、`api("tools.jackson.core:jackson-databind")`、`compileOnly(libs.xxl.job)`；测试：`testImplementation(project(":patra-tool:patra-tool-test"))`、`testImplementation(libs.xxl.job)`、`testImplementation("org.springframework.boot:spring-boot-starter-flyway")`、`testImplementation("org.springframework.boot:spring-boot-starter-jackson")`。编译到哪个类缺依赖，就把对应 runtimeOnly 提成 implementation。

- [ ] **Step 1: 写失败的单测**（照 `ObservabilityDefaultsPostProcessorTest`）
  - `should_add_defaults_at_lowest_priority`：空环境处理后 `spring.rabbitmq.publisher-confirm-type == "correlated"`、`spring.modulith.events.completion-mode == "delete"`、`spring.rabbitmq.listener.simple.retry.max-retries == "3"`、`spring.rabbitmq.listener.simple.prefetch == "10"`
  - `should_let_application_properties_override_defaults`：先放一个 `MapPropertySource` 写 `prefetch=50`，处理后读到 `50`
  - `should_keep_existing_default_properties`：`defaultProperties` 里已有 `spring.modulith.events.completion-mode=update`，处理后仍是 `update`
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-events:test --tests "*EventsDefaultsPostProcessorTest"` → FAIL（类不存在）
- [ ] **Step 3:** 实现：照 `ObservabilityDefaultsPostProcessor` 先剔除 `defaultProperties` 已有的键再 `addOrMerge`；`spring.factories` 登记
- [ ] **Step 4:** 同一命令 → PASS；`./gradlew :patra-tool:patra-tool-events:checkToolBoundary :patra-tool:patra-tool-events:checkToolDependencies` → PASS

### Task 3: 等确认的发送模板与自动配置

**Files:**
- Create: `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/event/IntegrationEvent.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/publisher/ConfirmingRabbitMessagingTemplate.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/autoconfigure/EventsProperties.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/autoconfigure/EventsRabbitAutoConfiguration.java`
- Create: `patra-tool/patra-tool-events/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/publisher/ConfirmingRabbitMessagingTemplateTest.java`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/autoconfigure/EventsRabbitAutoConfigurationTest.java`

**Interfaces:**
- Produces: `public interface IntegrationEvent { UUID eventId(); }`
- Produces: `public class ConfirmingRabbitMessagingTemplate extends RabbitMessagingTemplate`，构造 `(RabbitTemplate rabbitTemplate, Duration confirmTimeout)`；覆盖 `afterPropertiesSet()`、`doSend(String exchange, String routingKey, Message<?> message)`、`doSend(String destination, Message<?> message)`
- Produces: `@ConfigurationProperties("patra.events") public record EventsProperties(@DefaultValue Publisher publisher, @DefaultValue Resubmit resubmit)`，嵌套 `record Publisher(@DefaultValue("5s") Duration confirmTimeout)`、`record Resubmit(@DefaultValue("1m") Duration minAge)`
- Produces: `EventsRabbitAutoConfiguration`（`@AutoConfiguration(before = RabbitAutoConfiguration.class)`、`@ConditionalOnClass(RabbitMessagingTemplate.class)`、`@EnableConfigurationProperties(EventsProperties.class)`），bean：`JacksonJsonMessageConverter eventsMessageConverter(JsonMapper)`（`@ConditionalOnMissingBean(MessageConverter.class)`，可信包 `dev.patra.*`）、`RabbitTemplateCustomizer eventsMandatoryCustomizer()`（`setMandatory(true)`）、`ConfirmingRabbitMessagingTemplate confirmingRabbitMessagingTemplate(RabbitTemplate, EventsProperties)`

- [ ] **Step 1: 写模板的失败单测**（模拟 `RabbitTemplate`：`getMessageConverter()` 返回 `SimpleMessageConverter`，`getConnectionFactory()` 返回开了确认与退回的模拟连接工厂；`doAnswer` 捕获 `send(exchange, rk, Message, CorrelationData)` 的 `CorrelationData` 并按用例完成它的 future；调公开的 `convertAndSend("ex", "rk", payload)`）
  - `should_return_when_broker_acks`：future 完成 `new Confirm(true, null)` → 不抛
  - `should_use_event_id_as_message_id_and_correlation_id`：载荷是实现 `IntegrationEvent` 的测试 record → 捕获的 AMQP 消息 `getMessageProperties().getMessageId()` 与 `CorrelationData.getId()` 都等于 `eventId.toString()`
  - `should_throw_when_broker_nacks`：`new Confirm(false, "reject-publish")` → `AmqpException`，消息含 `reject-publish`
  - `should_throw_when_message_is_returned`：先 `setReturned(new ReturnedMessage(...))` 再 ack → `AmqpMessageReturnedException`
  - `should_throw_when_confirm_times_out`：超时 20ms、future 不完成 → `AmqpTimeoutException`
  - `should_restore_interrupt_flag_when_interrupted`：调用前 `Thread.currentThread().interrupt()`、future 不完成 → `AmqpException`，之后 `Thread.interrupted()` 为 `true`
  - `should_fail_fast_without_publisher_confirms`：连接工厂 `isPublisherConfirms()` 为 false → `afterPropertiesSet()` 抛 `IllegalStateException`，消息含 `spring.rabbitmq.publisher-confirm-type=correlated`
  - `should_fail_fast_without_publisher_returns`：`isPublisherReturns()` 为 false → 消息含 `spring.rabbitmq.publisher-returns=true`
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-events:test --tests "*ConfirmingRabbitMessagingTemplateTest"` → FAIL
- [ ] **Step 3:** 实现模板：`doSend` 用 `MessageBuilder.fromMessage(message)` 加 `AmqpHeaders.MESSAGE_ID`（仅 `IntegrationEvent`）与 `AmqpHeaders.PUBLISH_CONFIRM_CORRELATION`，调 `super.doSend`，再 `getFuture().get(timeout, MILLISECONDS)`；先查 `getReturned()` 再查 `isAck()`
- [ ] **Step 4:** 同一命令 → PASS
- [ ] **Step 5: 写自动配置的失败契约测试**（`ApplicationContextRunner` + `AutoConfigurations.of(EventsRabbitAutoConfiguration, RabbitAutoConfiguration, JacksonAutoConfiguration)`，属性 `spring.rabbitmq.publisher-confirm-type=correlated`、`spring.rabbitmq.publisher-returns=true`；不连 broker）
  - `should_replace_boot_messaging_template`：`getBean(RabbitMessageOperations.class)` 是 `ConfirmingRabbitMessagingTemplate`，`RabbitMessagingTemplate` 只有一个 bean
  - `should_make_rabbit_template_mandatory`：取 `eventsMandatoryCustomizer` 应用到模拟 `RabbitTemplate`，`verify(template).setMandatory(true)`
  - `should_convert_json_of_patra_types_by_type_header`：从上下文取 `MessageConverter`，`toMessage(dev.patra 包下的测试 record)` 后 `fromMessage` 得到同类实例
  - `should_fail_to_start_without_publisher_confirms`：去掉两个属性 → `getStartupFailure()` 的根因消息含 `publisher-confirm-type`
  - `should_bind_properties_defaults`：`EventsProperties.publisher().confirmTimeout() == 5s`、`resubmit().minAge() == 1m`
- [ ] **Step 6:** `./gradlew :patra-tool:patra-tool-events:test --tests "*EventsRabbitAutoConfigurationTest"` → FAIL；实现自动配置并登记 imports；再跑 → PASS；`./gradlew :patra-tool:patra-tool-events:checkAutoConfiguration` → PASS

### Task 4: 补投

**Files:**
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/resubmit/EventPublicationResubmitter.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/resubmit/EventPublicationResubmitJob.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/autoconfigure/EventsResubmitAutoConfiguration.java`（登记 imports）
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/resubmit/EventPublicationResubmitterTest.java`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/resubmit/EventPublicationResubmitJobTest.java`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/autoconfigure/EventsResubmitAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `EventsProperties.resubmit().minAge()`
- Produces: `EventPublicationResubmitter(IncompleteEventPublications publications, JdbcClient jdbcClient, Clock clock, Duration minAge)`，`public int resubmit()`：先执行 `UPDATE event_publication SET status = 'FAILED' WHERE status = 'RESUBMITTED' AND last_resubmission_date < ?`（参数 `Timestamp.from(now - minAge)`），再 `publications.resubmitIncompletePublications(p -> p.getPublicationDate().isBefore(cutoff))` 并用过滤器计数返回
- Produces: `EventPublicationResubmitJob(EventPublicationResubmitter)`，`@XxlJob("eventPublicationResubmitJob") public void execute()`：成功 `XxlJobHelper.handleSuccess("补投 %d 条".formatted(n))`，`RuntimeException` 时 `handleFail("执行失败：" + ex.getMessage())`
- Produces: `EventsResubmitAutoConfiguration`（`@AutoConfiguration(after = {EventPublicationAutoConfiguration.class, JdbcClientAutoConfiguration.class})`、`@ConditionalOnBean({IncompleteEventPublications.class, JdbcClient.class})`、`@EnableConfigurationProperties(EventsProperties.class)`）：bean `eventPublicationResubmitter`（`Clock` 取 `ObjectProvider.getIfAvailable(Clock::systemUTC)`）；嵌套 `@ConditionalOnClass(XxlJob.class)` 配置注册 `eventPublicationResubmitJob`

- [ ] **Step 1: 写失败单测**
  - Resubmitter 另加 `should_warn_about_publications_stuck_for_ten_attempts`（10/30 编排会话要求：尝试 10 次以上的行打 WARN，带事件 ID 与尝试次数；不设上限）
  - Resubmitter（`MutableClock` 固定在 `2026-10-30T00:10:00Z`，minAge 1 分钟，`JdbcClient` 用 `RETURNS_DEEP_STUBS`）：`should_only_resubmit_publications_older_than_min_age`（捕获过滤器：发布于 `00:08` 的为 true、`00:09:30` 的为 false）；`should_return_number_of_resubmitted`（捕获过滤器后对 3 个旧发布调用，返回 3）；`should_reset_stuck_resubmissions_first`（`InOrder`：先 `jdbcClient.sql(...)`，后 `resubmitIncompletePublications`）
  - Job（`mockStatic(XxlJobHelper.class)`）：`should_report_count_on_success`（`handleSuccess("补投 2 条")`）；`should_report_failure_message`（resubmitter 抛 `IllegalStateException("boom")` → `handleFail(contains("boom"))`）
  - 自动配置：`should_register_resubmitter_and_job`（`withBean` 模拟的 `IncompleteEventPublications` 与 `JdbcClient`）；`should_back_off_without_event_registry`；`should_skip_job_without_xxl_job`（`FilteredClassLoader(XxlJob.class)` → 有 resubmitter、无 job）
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-events:test --tests "*Resubmit*"` → FAIL
- [ ] **Step 3:** 实现三个类，登记 imports
- [ ] **Step 4:** 同一命令 → PASS；`checkAutoConfiguration` → PASS

### Task 5: 消费端：去重端口与队列声明

**Files:**
- Create: `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/event/ProcessedEventPort.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/consumer/JdbcProcessedEventAdapter.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/consumer/EventQueue.java`
- Create: `patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/autoconfigure/EventsConsumerAutoConfiguration.java`（登记 imports）
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/consumer/EventQueueTest.java`
- Test: `patra-tool/patra-tool-events/src/test/java/dev/patra/tool/events/autoconfigure/EventsConsumerAutoConfigurationTest.java`
- Test: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/consumer/JdbcProcessedEventAdapterIT.java`
- Create: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/EventsITBootstrap.java`（`@SpringBootApplication`）
- Create: `patra-tool/patra-tool-events/src/integrationTest/resources/db/migration/V1__create_event_publication.sql`、`V2__create_processed_event.sql`（spec 4.10 原文）
- Create: `patra-tool/patra-tool-events/src/integrationTest/resources/application.yml`（`spring.application.name: events-it`、`patra.events.resubmit.min-age: 0s`、监听重试 `initial-interval: 10ms`、`max-interval: 50ms`、`spring.flyway.enabled: true`）

**Interfaces:**
- Produces: `public interface ProcessedEventPort { boolean markProcessed(String consumer, UUID eventId); }`
- Produces: `JdbcProcessedEventAdapter implements ProcessedEventPort`，构造 `(JdbcClient jdbcClient, Clock clock)`，方法上 `@Transactional(propagation = Propagation.MANDATORY)`，SQL 见 spec 4.8
- Produces: `EventQueue`：`public static EventQueue named(String queue)`、`public EventQueue bind(String exchange, String... routingKeys)`（返回新实例；`routingKeys` 为空抛 `IllegalArgumentException`）、`public Declarables declarables()`；名字规则 `<queue>.dlx`、`<queue>.dlq`
- Produces: `EventsConsumerAutoConfiguration`（`@AutoConfiguration(after = JdbcClientAutoConfiguration.class)`、`@ConditionalOnBean(JdbcClient.class)`），bean `processedEventPort`（`@ConditionalOnMissingBean(ProcessedEventPort.class)`）

- [ ] **Step 1: 写失败测试**
  - `EventQueueTest.should_declare_queue_with_dead_letter_pair`：`named("notification.inbox").bind("interaction.events", "comment.published", "comment.deleted").declarables()` 里有：持久化队列 `notification.inbox`，参数 `x-dead-letter-exchange=notification.inbox.dlx`、`x-dead-letter-routing-key=notification.inbox`；`DirectExchange notification.inbox.dlx`；队列 `notification.inbox.dlq`；`notification.inbox.dlq ← notification.inbox.dlx` 键 `notification.inbox`；持久化 `TopicExchange interaction.events`；两条队列绑定，键 `comment.published`、`comment.deleted`
  - `EventQueueTest.should_reject_binding_without_routing_keys`
  - `EventsConsumerAutoConfigurationTest`：`should_register_processed_event_port_with_jdbc_client`；`should_back_off_without_jdbc_client`；`should_back_off_when_service_defines_its_own_port`
  - `JdbcProcessedEventAdapterIT`（`@SpringBootTest(webEnvironment = NONE)`，初始化器 PG + RabbitMQ）：`should_return_true_only_the_first_time`（同一事务模板里两次 → true、false；不同 consumer 同 ID → true）；`should_require_existing_transaction`（事务外调用抛 `IllegalTransactionStateException`）；`should_forget_mark_when_business_transaction_rolls_back`（事务里 mark 后抛异常，之后再 mark 返回 true）
- [ ] **Step 2:** 跑 `test` 与 `integrationTest --tests "*JdbcProcessedEventAdapterIT"` → FAIL
- [ ] **Step 3:** 实现四个类，登记 imports
- [ ] **Step 4:** 同两条命令 → PASS

### Task 6: ArchUnit 规则「发事件的服务不写 @TransactionalEventListener」（已作废）

> 10/30 用户决定先升 Boot 4.1，改用 Modulith 2.1 的 `registry-trigger-annotation`（Task 10）。本任务实现过后撤掉，补丁留在执行工作区 `archunit-rule-fallback.patch`，PAP-152 探路失败时恢复。

**Files:**
- Modify: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/HexagonalArchitectureRules.java`（事务规则段后加；不进 `allRules()`）
- Create: 夹具 `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/fixtures/events/listener/AfterCommitListener.java`（方法带 `@TransactionalEventListener`）、`fixtures/events/compliant/SyncListener.java`（方法带 `@EventListener`）
- Test: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/EventRulesTest.java`

**Interfaces:**
- Produces: `public ArchRule noTransactionalEventListeners()`：`noMethods().should().beMetaAnnotatedWith(TransactionalEventListener.class)`，`because` 写「Modulith 2.0.8 会把提交后监听器写进事件发布登记表，见 .claude/rules/tech/transaction.md」，`allowEmptyShould(true)`

- [ ] **Step 1:** 写 `should_reject_transactional_event_listener`、`should_accept_synchronous_event_listener`（照 `TransactionRulesTest` 的 `violates` 写法）
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-test:test --tests "*EventRulesTest"` → FAIL
- [ ] **Step 3:** 实现规则
- [ ] **Step 4:** 同一命令 → PASS

### Task 7: 真 RabbitMQ 的端到端集成测试（PAP-137 验收）

**Files:**
- Create: `patra-tool/patra-tool-events/src/integrationTest/resources/db/migration/V3__create_events_it_tables.sql`（`events_it_order(order_no TEXT PRIMARY KEY)`、`events_it_effect(event_id UUID PRIMARY KEY, order_no TEXT NOT NULL)`）
- Create: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/support/`：`EventsITOrderPlaced`（领域事件 record：`UUID eventId, String orderNo, String routingKey, boolean poison`）、`EventsITOrderPlacedMessage`（集成事件 record，实现 `IntegrationEvent`：`eventId, orderNo, poison`）、`EventsITSpringConfig`（`EventExternalizationConfiguration`：`externalizing().selectByType(EventsITOrderPlaced.class).route(EventsITOrderPlaced.class, e -> RoutingTarget.forTarget("events-it.events").andKey(e.routingKey())).mapping(EventsITOrderPlaced.class, e -> new EventsITOrderPlacedMessage(...)).build()`；`EventQueue.named("events-it.orders").bind("events-it.events", "order.placed")`；队列 `events-it.full`（`x-max-length=0`、`x-overflow=reject-publish`）绑键 `order.rejected`）、`EventsITOrderService`（事务模板里插 `events_it_order` 并 `publishEvent`，`fail` 为真时抛异常回滚）、`EventsITOrderListener`（`@RabbitListener(queues = "events-it.orders")`，事务模板里 `markProcessed("events-it.orders", eventId)` 后插 `events_it_effect`；`poison` 为真时抛异常；用 `AtomicInteger` 记调用次数）
- Test: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/EventPublicationIT.java`
- Test: `patra-tool/patra-tool-events/src/integrationTest/java/dev/patra/tool/events/EventPublicationConfirmTimeoutIT.java`（`@TestPropertySource(properties = "patra.events.publisher.confirm-timeout=1ns")`）

每个用例前清空 `event_publication`、`processed_event`、`events_it_*`，`AmqpAdmin.purgeQueue` 三个队列，监听计数归零；异步结果一律用 Awaitility（最长 10 秒）。

- [ ] **Step 1: 写 `EventPublicationIT`**
  - `should_deliver_event_after_commit`：下单 → 效果表 1 行、`event_publication` 为空
  - `should_neither_register_nor_send_when_transaction_rolls_back`：`fail=true` → 订单表与登记表都空；等 2 秒监听计数仍为 0
  - `should_keep_publication_incomplete_while_broker_is_down_and_deliver_once_after_resubmit`：`execInContainer("rabbitmqctl", "stop_app")` → 下单成功、订单表 1 行；等登记行 `status = 'FAILED'`，`listener_id` 等于全局约束里的外送监听器 ID；`start_app`（`finally` 里也保证启动）→ 等监听容器恢复后调 `resubmitter.resubmit()`（返回 1）→ 效果表 1 行、登记表为空
  - `should_keep_publication_incomplete_when_broker_nacks`：`routingKey = "order.rejected"` → 登记行 `FAILED`
  - `should_keep_publication_incomplete_when_no_queue_is_bound`：`routingKey = "order.nowhere"` → 登记行 `FAILED`
  - `should_resubmit_publication_stuck_in_resubmitted`：broker 停着时下单得到 `FAILED` 行，启动 broker，SQL 把它改成 `RESUBMITTED` 且 `last_resubmission_date = now() - interval '2 minutes'` → `resubmit()` 后送达、登记表为空
  - `should_process_duplicate_delivery_only_once`：`RabbitTemplate.convertAndSend("events-it.events", "order.placed", 同一个 message)` 两次 → 监听调用 2 次、效果表 1 行、`processed_event` 1 行
  - `should_dead_letter_after_retries_are_exhausted`：`poison=true` → `receive("events-it.orders.dlq", 10000)` 非空；监听调用 4 次；效果表与 `processed_event` 都为 0
- [ ] **Step 2: 写 `EventPublicationConfirmTimeoutIT`**
  - `should_keep_publication_incomplete_when_confirm_times_out_and_dedup_resubmission`：下单 → 登记行 `FAILED`，且消息仍被消费（效果表 1 行）；`resubmit()` → 监听调用 2 次，两次收到的 `messageId` 相同（监听里记录 `@Header(AmqpHeaders.MESSAGE_ID)`），效果表仍 1 行
- [ ] **Step 3:** `./gradlew :patra-tool:patra-tool-events:integrationTest` → 先确认新用例按预期失败或通过；出现与 spec 不符的行为按 systematic-debugging 排查（不改断言迁就实现）
- [ ] **Step 4:** 全部 PASS

### Task 8: 文档与规则

**Files:**
- Create: `patra-tool/patra-tool-events/README.md`（能力；`patra.events` 配置表；出的默认值表；发布端接入 6 步（spec 第 8 节）含 `EventExternalizationConfiguration` 与交换机 bean 示例、事件 ID 约束；消费端：`EventQueue` 示例、`markProcessed` 用法；Flyway 模板指向 `src/integrationTest/resources/db/migration/`）
- Modify: `.claude/rules/tech/transaction.md`（§1 两行、§3 Outbox 中继一行，按 spec 第 7 节；保持短）
- Modify: `.claude/skills/patra-backend/skills/patra-events/SKILL.md`（「其他微服务」分支与常见陷阱改成本模块用法）
- Modify: `.claude/rules/project-info.md`（工具模块 12 → 13，列 `patra-tool-events`）
- Modify: `.claude/skills/patra-backend/skills/patra-hexagonal/resources/patra-starters.md`（层表 boot 行加本模块、加一节）
- Modify: `patra-infra/cd/module-graph.json`（`./gradlew dumpModuleGraph` 生成）

- [ ] **Step 1:** 写文档；`.claude/` 下的改动若被钩子拒绝，生成补丁放到共享笔记目录交编排会话
- [ ] **Step 2:** `./gradlew dumpModuleGraph`，`git diff patra-infra/cd/module-graph.json` 只多出 `patra-tool-events` 一项（以及依赖它的模块 impacts 不变）

### Task 10: 落到 Modulith 2.1（等 PAP-152 合入、版本分支变基后做）

**Files:**
- Modify: `gradle/libs.versions.toml`（`spring-modulith` 改为与 Boot 4.1 配套的 2.1.x 最新补丁版）
- Modify: `EventsDefaultsPostProcessor`（加 `spring.modulith.events.registry-trigger-annotation=org.springframework.modulith.events.ApplicationModuleListener`）与其单测
- Modify: `EventPublicationResubmitter`（解卡改用 `EventPublicationRegistry.markStalePublicationsFailed(只监控 RESUBMITTED、时长固定 10 分钟)`，删手写 UPDATE；`RESUBMITTED` 的行视为已被领走，不计入补投数）与其单测
- Modify: `EventPublicationIT`（外送器 ID 常量改为 `org.springframework.modulith.events.support.EventExternalizerModuleListener.externalize(java.lang.Object)`）
- Create: IT `should_not_register_plain_after_commit_listeners`：`support` 里加一个普通 `@TransactionalEventListener(AFTER_COMMIT)` 监听 `EventsITOrderPlaced`，订单号带失败前缀时它抛异常；下单后消费方收到、普通监听器被调 1 次、登记表为空（没默认值时会留下它的 `FAILED` 行）
- Modify: README 默认值表加 trigger-annotation 一行；「发布方怎么接」加回「服务代码里不写 `@ApplicationModuleListener`、普通 `@TransactionalEventListener` 不进登记表」；`patra-events` 技能发布端要点同步

- [x] **Step 1:** 先写 trigger-annotation 的单测与 IT，在 2.1 上跑 → FAIL
- [x] **Step 2:** 加默认值 → PASS
- [x] **Step 3:** 解卡改用 Modulith API（阈值固定 10 分钟，与「还在发送」共用；IT 把卡住时间改为 11 分钟前），`should_resubmit_publication_stuck_in_resubmitted` 仍 PASS
- [ ] **Step 4:** `./gradlew check integrationTest` → BUILD SUCCESSFUL

### Task 9: 全量验证

- [ ] **Step 1:** `./gradlew spotlessApply`，再 `git diff --stat` 确认只动了本 Issue 的路径
- [ ] **Step 2:** `./gradlew check`（含各模块 `test` 与 `integrationTest`）→ BUILD SUCCESSFUL
- [ ] **Step 3:** `./gradlew :patra-tool:patra-tool-events:integrationTest :patra-tool:patra-tool-test:integrationTest` 单独再跑一次，确认新 IT 都被执行（看报告里的用例数）
- [ ] **Step 4:** 向编排会话汇报，等同意后提交
