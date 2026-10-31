# 通用事件模块（patra-tool-events）

- 日期：2026-10-30
- 分支：`claude/v016-events-7dd164`（基线 `feat/v0.16-notification-api` @ `7a6825d00`，即 Boot 4.1.2 升级合入后变基的版本分支）
- Issue：PAP-137（v0.16 决策 C、D、E）
- 版本：Spring Modulith 2.1.2（与 Boot 4.1.2 配套；PAP-152 / #342 升级后落地，版本无关的部分先在 Modulith 2.0.8 + Boot 4.0.8 上实现并测过，见 4.12）、Spring AMQP（Boot BOM 管理）、RabbitMQ 4.4.0
- 范围：新建 `patra-tool-events`；`patra-tool-core` 加两个接口；`patra-tool-test` 加 RabbitMQ 测试容器；构建接线；`transaction.md` 与 `patra-events` 技能改写

## 1. 为什么做

v0.16 要让 interaction、identity 把「发生了什么」告诉新的通知服务。两个服务的库不能放进同一个事务，消息又必须可靠送到，所以要一套三家都能用的事件能力：发布端不丢、消费端不重复处理、处理失败有去处。release spec 决策 C（可靠发出）、D（消费端去重）、E（RabbitMQ 结构）定了方向，本模块把它们做成工具模块，具体服务的接入归 PAP-141（interaction）、PAP-104（identity）、PAP-143 / PAP-105（notification）。

## 2. 先讲清几个概念

### 2.1 事件发布登记（Outbox）

- **是什么**：Spring Modulith 的事件发布登记是一张 `event_publication` 表。业务代码在事务里 `publishEvent(事件)` 时，框架把「这个事件要交给哪个监听器」写成一行，和业务数据在**同一个事务**里提交；提交后再调监听器，监听器成功了才把这一行标成完成。
- **防什么**：防「双写」丢消息。写库和发消息是两个系统，做不到同成同败：先提交再发，进程在中间崩了消息就丢；先发再提交，回滚了消息却已经发出。登记行和业务数据一起提交，就保证「业务成功 ⇔ 有一行待发」，发不出去这一行就一直留着，可以重发。
- **业界**：Transactional Outbox 是解决双写的标准做法（microservices.io、Debezium）；Modulith 的登记表是 Spring 官方的实现。
- **我们选**：Modulith 2.1 的 JDBC 登记表，加官方的 `spring-modulith-events-amqp` 把事件外送到 RabbitMQ。
- **代价**：这是**至少一次**投递，重发时可能把已经送到的事件再发一遍，消费端必须去重（2.4）。

### 2.2 publisher confirm：等 RabbitMQ 说「收到了」

- **是什么**：RabbitMQ 把消息收下、写进队列后，给发送方回一个确认（ack）；收不下就回拒绝（nack）。这叫 publisher confirm，要在连接上打开才有。
- **防什么**：发送调用返回不代表 broker 收到了。网络断在半路、broker 内存满了，消息都可能没进队列。
- **Modulith 的缺口**：`spring-modulith-events-amqp` 发完 `convertAndSend` 就返回成功，登记行随即被标成完成，根本不等确认（2.0.8 与 2.1.2 的源码都是 `return CompletableFuture.completedFuture(null)`；同类问题见 Modulith issue #395）。broker 没收下，这条事件就永远丢了。
- **我们选**：每次发送同步等确认，等到 ack 才算成功；nack、超时、没有队列可投（见 2.3）都抛异常，登记行保持未完成，留给补投任务。
- **代价**：每次发送多等一次回执，平时一两毫秒；外送本来就在提交后的异步线程里，不拖慢用户请求。

### 2.3 mandatory 与退回：没有队列收也算失败

- **是什么**：交换机找不到任何匹配的队列时，消息默认被静默丢弃，而且 RabbitMQ **照样回 ack**。发送时带上 `mandatory` 标志，broker 会先把消息退回（return）再回 ack。
- **防什么**：通知服务还没部署、队列还没声明时，interaction 发出的事件会被悄悄丢掉。
- **我们选**：所有发送都带 `mandatory`；收到退回就当失败，登记行留着，等队列建好后补投送达。所以部署顺序无所谓：先上 interaction、后上通知服务，事件也不会丢。
- **代价**：外送的事件必须有人订阅，否则这类事件每分钟被补投一次、永远发不出去，登记表越积越多。对策：
  - 只外送有队列订阅的事件；每种外送事件都在 boot 层显式选出来（决策 B），上线前对应的路由键必须已有绑定；
  - 上线顺序：先部署通知服务（声明队列与绑定），再部署 interaction、identity（写进 v0.16 上线手册）；
  - 补投时，尝试 10 次以上（约 10 分钟没发出去）的登记行打 WARN 日志，列出事件 ID、类型、发布时间、尝试次数，日志里就能查到卡住的事件（4.6）。不设重试上限：broker 停几个小时恢复后照样能自动补上。

### 2.4 幂等消费：同一个事件只处理一次

- **是什么**：同一条消息处理多次，结果和处理一次一样。
- **防什么**：至少一次投递下，重复来自两处：补投把已经送到的事件再发一遍（确认在路上丢了）；消费方处理完、ack 之前崩了，RabbitMQ 重投。
- **我们选**：消费端建一张 `processed_event` 表，主键（消费方, 事件 ID）。业务事务里先插一行，插进去了才做业务；主键冲突说明处理过，直接结束。插入和业务写入同成同败，所以「记了已处理但业务没写」不会发生。
- **前提**：事件 ID 必须在事件**产生时**就定下来，补投时不变（4.5）。

### 2.5 ack、重试与死信

- **ack**：消费方处理完才回「收到」，RabbitMQ 这时才删掉消息；没回 ack 就断开，消息会重投。RabbitMQ 文档把这叫手动确认（manual acknowledgement），与「一投递就算收到」的自动确认相对。
- **Spring 的叫法容易混**：Spring AMQP 的 `AcknowledgeMode.AUTO` 就是 RabbitMQ 意义上的手动确认，由容器在监听方法正常返回后发 ack、抛异常时发 nack；Spring 的 `MANUAL` 则要每个监听方法自己调 `basicAck`，而且重试耗尽后的拒绝默认不生效，消息会卡在未确认状态。**我们用 `AUTO`**，满足决策 E 的「处理完才确认」。
- **重试**：失败先在本地按指数退避重试（间隔 1 秒、2 秒、4 秒，最多 3 次重试），处理的是数据库抖动、下游暂时不可用这类短暂故障。
- **死信**：重试仍失败的消息被拒绝且不回队列，RabbitMQ 按队列上配的死信交换机把它转进专门的死信队列（`<队列名>.dlq`）留存，不再堵住后面的消息。
- **prefetch**：一个消费者手里最多同时攥 10 条未确认的消息（Spring 默认 250）。设小是为了消费者崩溃时重投的量少，也让多个消费者分得更均匀。
- **代价**：死信队列里的消息不会自己回来，要人在 RabbitMQ 管理界面查看或重放；本版不加告警（release spec 决策 E）。

## 3. 目标、非目标、反目标

**目标**

1. 服务引入 `patra-tool-events`、建好表、在 boot 层写一个 `EventExternalizationConfiguration`，就能可靠地把领域事件外送到 RabbitMQ。
2. broker 没收下（不可达、nack、超时、没有队列）时登记行保持未完成；XXL-Job 补投任务把它们重新发出。
3. 消费端有去重表与一行调用的去重能力，有统一的队列声明（死信交换机 + 死信队列），有默认的 ack、重试、prefetch。
4. 用 Testcontainers 起真 RabbitMQ 证明 PAP-137 的 6 条验收。

**非目标**

- 不接入任何具体服务：interaction（PAP-141）、identity（PAP-104）、notification（PAP-139 / 143 / 105）各自引依赖、建表、写映射与监听器。
- 不做 compose 与 mini 上的 RabbitMQ（PAP-138）。
- 不做乱序处理（墓碑、版本号，决策 D 后半）：它们是各事件的业务规则，归 PAP-143。
- 不做死信告警、死信重放工具（见第 9 节）。
- 不做 Boot 4.1 升级本身（PAP-152 单独做、单独合入）。

**反目标**（功能都在也算失败）

- 登记行在 broker 没收下时被标成完成（正是要修的缺口）。
- 补投换了事件 ID，消费端去重失效。
- 引入模块后，服务里原有的同步 `@EventListener` 行为变了。
- 拉进 Modulith 的模块结构校验（`spring-modulith-core`、ArchUnit 扫描），和 Patra 自己的分层规则打架。

## 4. 设计

### 4.1 模块落点

新建工具模块 `patra-tool-events`（包 `dev.patra.tool.events`，配置前缀 `patra.events`），守 R1–R5：只依赖 `patra-tool-core`，bean 全部由 `autoconfigure` 包里的自动配置注册，每个自动配置类配同名 `ApplicationContextRunner` 契约测试。

没有放进现有模块：事件能力同时涉及 JDBC 登记表、RabbitMQ、XXL-Job，和 jpa、batch 都不是一回事；单独一个模块，服务按需引入，不用的服务不带 AMQP 依赖。

`patra-tool-core`（纯 Java）新增两个接口，让 `-api` 模块和 app 层不依赖框架就能用：

| 接口 | 谁用 | 说明 |
|---|---|---|
| `dev.patra.tool.core.event.IntegrationEvent` | 发布方 `-api` 模块里的集成事件 record 实现它 | 只有 `UUID eventId()`。模块据此给消息设 AMQP `messageId`，消费端据此去重 |
| `dev.patra.tool.core.event.ProcessedEventPort` | 消费方 app 层的 Handler 注入它 | `boolean markProcessed(String consumer, UUID eventId)`：首次处理返回 `true`，重复返回 `false`；必须在业务事务里调用。参照 `CurrentUserPort` 的做法：接口在 core，实现由工具模块注册 |

依赖：`spring-modulith-events-api`、`-events-core`、`-events-jdbc`、`-events-jackson`、`-events-amqp`、`spring-boot-starter-amqp`、`spring-boot-starter-jdbc`；`xxl-job-core` 为 `compileOnly`。不用 `spring-modulith-starter-jdbc`：它带进 `spring-modulith-starter-core`，后者含模块结构校验（反目标 4）。版本由 `spring-modulith-bom` 管理（落地 2.1 前是 2.0.8），在 `PatraDependencyManagement` 里导入。

### 4.2 发布端流程

```text
app Handler 的事务内
  ├─ 写业务数据
  └─ events.publishEvent(领域事件)            ← Modulith 的多播器同步写一行 event_publication
提交
  └─ 外送监听器（Modulith，@Async）
       ├─ 选择 / 映射 / 路由（服务的 EventExternalizationConfiguration）
       └─ RabbitMessageOperations.convertAndSend
            └─ ConfirmingRabbitMessagingTemplate（本模块）
                 ├─ 带 CorrelationData 发送，mandatory
                 ├─ 同步等确认（默认 5 秒）
                 └─ ack 且没被退回 → 返回；否则抛异常
       ├─ 返回 → 登记行完成（默认 DELETE：删除这一行）
       └─ 抛异常 → 登记行标 FAILED，留给补投
回滚 → 登记行跟着回滚，监听器不会被调用
```

### 4.3 在哪里等确认

Modulith 的外送器 bean（`rabbitEventExternalizer`）没有 `@ConditionalOnMissingBean`，不能替换；再注册一个自己的外送器会让每个事件发两遍。它往下调的是 `RabbitMessageOperations`，而 Boot 的 `RabbitMessagingTemplate` 带 `@ConditionalOnMissingBean(RabbitMessagingTemplate.class)`，这是 Boot 留的扩展点。所以本模块注册一个子类 `ConfirmingRabbitMessagingTemplate`，自动配置排在 `RabbitAutoConfiguration` 之前，Boot 看到已有就不再建。

子类覆盖 `doSend`（`AbstractMessagingTemplate` 留给子类的扩展方法）：

1. 新建 `CorrelationData`，关联 ID 每次发送都是新的随机 UUID：信道按关联 ID 记录待退回的消息，同一事件的两次发送并发时共用一个 ID 会互相覆盖，可能把被退回的消息当成成功。载荷实现了 `IntegrationEvent` 就把 `eventId` 写进消息头 `amqp_messageId`。
2. 把 `CorrelationData` 放进消息头 `AmqpHeaders.PUBLISH_CONFIRM_CORRELATION`，交给父类发送（父类按这个头调 `RabbitTemplate.send(..., correlationData)`）。
3. `correlationData.getFuture().get(confirmTimeout)`：
   - 超时 → `AmqpTimeoutException`；
   - nack → `AmqpException`（带 broker 给的原因）；
   - `getReturned() != null`（没有队列可投）→ `AmqpMessageReturnedException`；
   - 等待被中断 → 恢复中断标志后抛 `AmqpException`。

这样做的好处：继续用官方 `spring-modulith-events-amqp`，以后它自己修了确认问题，本模块这层仍然无害；服务里其他经 `RabbitMessageOperations` 的发送也一样有确认。不另写外送器，也避免了两个外送器的监听器 ID 相同导致登记表混乱。

确认依赖连接配置，模块出默认值（4.9）：`publisher-confirm-type=correlated`、`publisher-returns=true`。`mandatory` 由模块的 `RabbitTemplateCustomizer` 直接设成 `true`，不走属性：它是确认语义的一部分，不允许服务关掉。模板初始化时检查连接工厂确实开了确认与退回，没开就启动失败，报错写明缺哪个配置。

### 4.4 只登记要外送的事件：`registry-trigger-annotation`

- **问题**：Modulith 的多播器默认给服务里**每一个** `@TransactionalEventListener(AFTER_COMMIT)` 都写登记行，完成与失败也一并记录、补投。登记表应该只放要外送的事件：服务内部「提交后可丢」的副作用不该被补投，通知服务（PAP-145）还要用普通的提交后监听器做实时推送。
- **2.1 的做法**：`spring.modulith.events.registry-trigger-annotation` 填一个注解的全类名，只有带它的提交后监听器才写登记行。Modulith 的外送器方法带 `@ApplicationModuleListener`，所以本模块默认填 `org.springframework.modulith.events.ApplicationModuleListener`：登记表只有外送器的行，服务里普通的 `@TransactionalEventListener` 照 Spring 原来的行为走。
- **约束**：服务代码里不写 `@ApplicationModuleListener`（写了就会进登记表）。只消费、不外送的服务（notification）不会写登记行：没有 `EventExternalizationConfiguration` 时，Modulith 的默认外送配置只选带 `@Externalized` 的事件，什么都不会选中；但仍要建 `event_publication` 表，Modulith 关机时会查未完成的行，没有表会报 SQL 错。
- **版本**：这个属性在 Modulith 2.1.2 里才有（核对过 2.0.8、2.0.9、2.1.2 的配置元数据；相关类的 `@since` 都是 2.1.2，2.1.0 / 2.1.1 没有，所以版本下限是 2.1.2），2.1 配 Boot 4.1，所以随 PAP-152 一起落地（4.12）。仓库里现在没有一处真正的 `@TransactionalEventListener`（release spec 说的「7 处」是 Javadoc 注释和 #339 已删的两个 ingest 处理器），落地前没有实际影响。
- **备选（已放弃）**：留在 2.0.8 时，用 ArchUnit 规则禁止发事件的服务写 `@TransactionalEventListener`，并把模块拆成「发布」「消费」两块，让通知服务不带登记表。用户 10/30 决定先升 Boot 4.1，这条作废；PAP-152 探路失败时再回到它。

### 4.5 事件 ID：产生时定下，补投时不变

登记表存的是**领域事件**（序列化后的 JSON），映射成集成事件发生在每次外送时，补投会再映射一遍。所以去重用的 ID 不能在映射里生成（每次补投都会得到新 ID，去重形同虚设），必须：

1. 外送的领域事件带一个 `UUID eventId`，在领域事件创建时生成（`UUID.randomUUID()`，纯 Java，领域层可用）；
2. 映射到集成事件时原样带过去，集成事件实现 `IntegrationEvent`；
3. 本模块把它写进 AMQP `messageId`（确认的关联 ID 另用随机值，见 4.3）。

这是给 PAP-141、PAP-104 的硬约束，写进模块 README。

### 4.6 补投任务

**任务**：XXL-Job handler `eventPublicationResubmitJob`，由本模块注册（类路径上有 `xxl-job-core` 时）。服务只需在 xxl-job-admin 登记：每分钟一次（CRON `0 * * * * ?`），路由策略「第一个」，阻塞处理「丢弃后续调度」，超时 60 秒，不重试。逻辑放在 `EventPublicationResubmitter`，任务类只做 XXL 的成功 / 失败回写。

**每次执行**：

1. **解开卡住的补投**：上次补投 10 分钟前仍停在 `RESUBMITTED` 的行改回 `FAILED`。原因：Modulith 补投前先把行标成 `RESUBMITTED`，并且拒绝再补投已是 `RESUBMITTED` 的行（2.1 仍然如此）；如果补投途中进程崩了，这一行会永远卡住。Modulith 自带的「过期检查」（staleness）是 `SchedulingConfigurer`，要 `@EnableScheduling` 才会自己跑（Patra 用 XXL-Job，没开），所以在任务里主动调：
   `EventPublicationRegistry.markStalePublicationsFailed(只监控 RESUBMITTED、时长 10 分钟)`，2.1.2 按最后一次补投时间判断，正合适（2.0.8 按发布时间判断，当时用的是一条手写 UPDATE，已随 4.12 删除）。
2. **补投**：`IncompleteEventPublications.resubmitIncompletePublications(过滤)`，返回补投条数写进 XXL 回写消息。过滤条件：发布时间早于 `现在 - min-age`，且不是「还在发送」的行。`PROCESSING` 表示外送任务正在执行（发送或等确认；Spring 的异步拦截器在最外层，任务在线程里开始执行时才标 `PROCESSING`，还在执行器里排队的行仍是 `PUBLISHED` 或 `RESUBMITTED`），Modulith 的「未完成」查询和抢占都不排除它；不加这一条，broker 阻塞时每轮都会把同一行再排一份，恢复时冲出大量重复消息。所以 `PROCESSING` 且上次补投（没补投过就是发布时间）在 10 分钟内的行不派发；超过 10 分钟没动静的当作进程崩溃留下的，照常补投。`RESUBMITTED` 的行已被领走（可能在排队，也可能是别的实例领的），Modulith 不会再派发它，也不计入补投数、不打日志；超过 10 分钟的已在第 1 步改回 `FAILED`。
3. **卡住的事件打日志**：过滤时遇到 `completion_attempts` ≥ 10 的行（首次发送记 1，每补投一次加 1，约 10 分钟没发出去），打 WARN，列出登记行 ID、发布时间、尝试次数（类型按 ID 查表）。不读事件内容：反序列化失败的行会让异常在 Modulith 的处理之外抛出，整轮补投中断。不设上限，继续每分钟重试。
4. `min-age` 默认 1 分钟：刚提交的事件正在异步外送，不去抢；一次发送最多耗时「连接超时 5 秒 + 确认超时 5 秒」，1 分钟足够安全。

补投出去的事件在异步线程里发送，任务返回时可能还没发完，结果体现在下一轮：成功的行被删除，失败的又回到 `FAILED`。两台实例同时跑补投时，Modulith 用 `UPDATE ... WHERE status != 'RESUBMITTED'` 抢占，同一行只会被一边领走；同一实例里不重复派发靠上面的「还在发送」条件。

不打开 `republish-outstanding-events-on-restart`：重启后一分钟内补投任务就会捡起来，没必要在启动路径上加一次全表补投。

### 4.7 完成模式

Modulith 的 `spring.modulith.events.completion-mode` 决定发成功的登记行怎么处理：`UPDATE`（标完成、留着）、`DELETE`（删掉）、`ARCHIVE`（挪进 `event_publication_archive`）。

本模块默认 `DELETE`，服务可在自己的配置里改：

- identity 的安全邮件事件带一次性令牌链接，发完即删（决策 F），默认即满足；
- 表里只剩没发出去的事件，「表不空」就等于「有积压」，排查直观；
- 选 `UPDATE` 的服务要自己清理已完成的行（`CompletedEventPublications.deletePublicationsOlderThan`），选 `ARCHIVE` 要多建归档表（模板见 4.10）。

### 4.8 消费端

**默认配置**：见 4.9，Boot 的 `spring.rabbitmq.listener.simple.*` 属性实现 2.5 的 ack、重试、死信、prefetch。重试耗尽后由 Boot 默认的 `RejectAndDontRequeueRecoverer` 拒绝且不回队列，消息进死信。

**消息转换**：模块注册一个 `MessageConverter` bean（`JacksonJsonMessageConverter`，用应用里的 `JsonMapper`），Boot 把它同时装到 `RabbitTemplate` 和监听容器上，收发用同一份 JSON 配置；关掉 Modulith 自己的模板转换器定制（`spring.modulith.events.rabbitmq.enable-json=false`），避免两处各配一份。监听方法参数是具体类型时按参数类型反序列化；一个队列收多种事件、监听类上用多个 `@RabbitHandler` 时，要按消息头 `__TypeId__` 里的类型名反序列化，只认 `patra.events.consumer.trusted-packages` 里列出的包。Spring AMQP 4.0 按包名精确匹配、不支持通配；全部信任（`*`）会放大反序列化面，所以默认只信 Spring 默认的 `java.util`、`java.lang`，用到多类型监听的消费方在配置里写出发布方集成事件所在的包。

**队列声明**：`EventQueue.named("notification.inbox").bind("interaction.events", "comment.published", ...).declarables()` 返回一组 `Declarables`，服务把它注册成 bean，由 `RabbitAdmin` 在连接时声明：

| 声明 | 名字 | 说明 |
|---|---|---|
| 主题交换机 | 每个 `bind` 的交换机，如 `interaction.events` | 持久化；消费方也声明一遍，绑定不依赖发布方先部署 |
| 队列 | `notification.inbox` | 持久化；参数 `x-dead-letter-exchange=notification.inbox.dlx`、`x-dead-letter-routing-key=notification.inbox` |
| 死信交换机 | `notification.inbox.dlx` | 直连，持久化 |
| 死信队列 | `notification.inbox.dlq` | 持久化，用路由键 `notification.inbox` 绑到死信交换机 |
| 绑定 | 队列 ← 交换机 × 每个路由键 | |

每个队列各一对死信交换机与死信队列：不用在服务之间协调名字，看名字就知道死信来自哪个队列。

**去重**：`ProcessedEventPort` 的实现 `JdbcProcessedEventAdapter`（`@Transactional(propagation = MANDATORY)`，调用方没有事务就报错）：

```sql
INSERT INTO processed_event (consumer, event_id, processed_at) VALUES (?, ?, ?)
ON CONFLICT (consumer, event_id) DO NOTHING
```

影响行数为 1 返回 `true`。`consumer` 填队列名；同一个事件被同一服务的两个队列消费时，各记各的。用法（PAP-143 的 Handler）：

```java
transactionOperations.executeWithoutResult(status -> {
  if (!processedEvents.markProcessed("notification.inbox", event.eventId())) {
    return; // 处理过了
  }
  // 写信箱……
});
```

### 4.9 模块出的默认值

由 `EventsDefaultsPostProcessor` 并入 `defaultProperties`（同 observability 的做法），服务 yml、Nacos、环境变量都能覆盖。

| 键 | 默认值 | 原因 |
|---|---|---|
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `false` | 表由 Flyway 建（2.0.8 默认也是 false，显式写出） |
| `spring.modulith.events.completion-mode` | `delete` | 4.7 |
| `spring.modulith.events.registry-trigger-annotation` | `org.springframework.modulith.events.ApplicationModuleListener` | 4.4，只登记外送器（Modulith ≥ 2.1.2 才有，随 4.12 加） |
| `spring.modulith.events.rabbitmq.enable-json` | `false` | 转换器由本模块统一提供（4.8） |
| `spring.rabbitmq.publisher-confirm-type` | `correlated` | 2.2 |
| `spring.rabbitmq.publisher-returns` | `true` | 2.3 |
| `spring.rabbitmq.connection-timeout` | `5s` | broker 不可达时尽快失败（amqp-client 默认 60 秒） |
| `management.health.rabbit.enabled` | `false` | broker 停着时业务照常提交、事件留给补投，服务不该判成 DOWN：`deploy.sh` 与 compose 健康检查都看 `/actuator/health`，会回滚部署（同 identity 关掉邮件健康检查的先例） |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `auto` | 2.5（RabbitMQ 意义上的手动确认） |
| `spring.rabbitmq.listener.simple.prefetch` | `10` | 2.5 |
| `spring.rabbitmq.listener.simple.default-requeue-rejected` | `false` | 不经重试就失败的消息（如反序列化失败）也进死信，不在队列里打转 |
| `spring.rabbitmq.listener.simple.retry.enabled` | `true` | 2.5 |
| `spring.rabbitmq.listener.simple.retry.max-retries` | `3` | 首次 + 3 次重试 |
| `spring.rabbitmq.listener.simple.retry.initial-interval` | `1s` | |
| `spring.rabbitmq.listener.simple.retry.multiplier` | `2` | 1 秒、2 秒、4 秒 |
| `spring.rabbitmq.listener.simple.retry.max-interval` | `10s` | |

本模块自己的配置（`patra.events`，record）：

| 键 | 默认值 | 说明 |
|---|---|---|
| `publisher.confirm-timeout` | `5s` | 等确认的上限 |
| `resubmit.min-age` | `1m` | 只补投早于这个时长的登记行（「卡住」的判定固定为 10 分钟没动静，见 4.6） |
| `consumer.trusted-packages` | 空 | 按类型头反序列化时信任的包（精确匹配） |

### 4.10 Flyway 迁移写法

发布方（interaction、identity）照抄，表名和列是 Modulith JDBC 第二版结构（`schemas/v2/schema-postgresql.sql`，2.0.8 与 2.1.2 一致），不能改名：

```sql
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
```

选 `ARCHIVE` 的服务另建同结构的 `event_publication_archive`（含两个同名后缀的索引）。

消费方（notification）照抄：

```sql
CREATE TABLE processed_event
(
    consumer     TEXT                     NOT NULL,
    event_id     UUID                     NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
CREATE INDEX processed_event_processed_at_idx ON processed_event (processed_at);
```

模块的集成测试用的就是这两段（`src/integrationTest/resources/db/migration/`），README 指向它们。

### 4.11 测试容器

`patra-tool-test` 的 `ContainerType` 加 `RABBITMQ`，新增 `RabbitMQContainerInitializer`：镜像 `rabbitmq:4.4.0-management`（与 PAP-138 的 compose 一致），JVM 内单例，把 `spring.rabbitmq.host / port / username / password` 写进环境，并提供容器本身给测试做 `rabbitmqctl stop_app / start_app`（停应用不停容器，映射端口不变）。用 Testcontainers 1.21.4 的 `org.testcontainers:rabbitmq` 模块。

### 4.12 2.0.8 与 2.1 的差异，以及落到 2.1 的改动

核对 2.0.8 与 2.1.2 的源码 jar 得到：

| 项 | 2.0.8 | 2.1.2 | 对本模块 |
|---|---|---|---|
| Boot 版本 | 4.0.x | 4.1.x（events-amqp 编译依赖 spring-boot-amqp 4.1.2） | 随 PAP-152 一起升 |
| 只登记部分监听器 | 没有 | `registry-trigger-annotation` | 默认填 `ApplicationModuleListener`（4.4） |
| 外送模式 | 只有提交后监听器 | 新增 `externalization.mode`：`module-listener`（默认，同 2.0.8，不等回执）/ `outbox` | `outbox` 只认 nack（无超时、不查退回），且要 Namastack Outbox 或 JobRunr 驱动，不用；仍走 `module-listener` + 等确认的模板（4.3） |
| 外送器类 | `DelegatingEventExternalizer` | `EventExternalizerModuleListener` | 登记行的 `listener_id` 变了，IT 常量跟着改 |
| 表结构 | JDBC 第二版 | 同左，PostgreSQL 脚本逐字一致 | Flyway 模板不变 |
| 补投 API | `IncompleteEventPublications` | 同左 | 不变 |
| `markResubmitted` | 拒绝已是 `RESUBMITTED` 的行 | 同左，另加 `completion_date IS NULL` | 卡住问题仍在，仍要解卡 |
| 过期检查 | 按发布时间、所有状态都查 | 按最后一次补投时间、只查配置了的状态；仍要 `@EnableScheduling` | 解卡改用 `markStalePublicationsFailed`（4.6） |

PAP-152 合入（#342）、版本分支变基后，已在本分支上做完：

1. `gradle/libs.versions.toml` 的 `spring-modulith` 改为 2.1.2（与 Boot 4.1.2 配套；`registry-trigger-annotation` 与新的过期检查都从 2.1.2 起才有，不能降到 2.1.0 / 2.1.1）。
2. `EventsDefaultsPostProcessor` 加 `spring.modulith.events.registry-trigger-annotation=org.springframework.modulith.events.ApplicationModuleListener`，单测与 IT（普通提交后监听器不产生登记行）先红后绿。
3. `EventPublicationResubmitter` 的解卡改用 `EventPublicationRegistry.markStalePublicationsFailed`，删掉手写 UPDATE；卡住用例照旧要通过。
4. IT 里外送器 ID 常量改为 `EventExternalizerModuleListener.externalize(java.lang.Object)`。
5. README「发布方怎么接」加回「服务代码里不写 `@ApplicationModuleListener`，普通 `@TransactionalEventListener` 不进登记表」，默认值表加 trigger-annotation；`patra-events` 技能发布端要点同步。
6. 跑 `./gradlew check integrationTest`。

### 4.13 等 broker 确认时占不占数据库连接（10/31 补）

**先讲概念**：数据库连接池像一叠号码牌（identity、interaction 各 10 张）。请求要查库就领一张，用完还回去；牌发完了，后来的请求只能排队，排到超时就报错。所以有一条铁律：**拿着号码牌的时候不要去等网络**——等的那几秒里牌被白白占着，几个这样的等待就能把牌领光，登录、注册这些和事件毫不相干的请求跟着失败。

**疑虑从哪来**：PAP-104 的独立评审认为，外送在 Modulith 的 `REQUIRES_NEW` 事务里同步等 broker 确认；broker 卡住（端口通、就是不回确认）时，每个外送都拿着一个连接等满超时，Boot 默认 8 个异步线程能占掉 10 个连接里的 8 个。identity 为此在本地把异步并发压到 2。

**实际路径**（对照 Modulith 2.1.2 源码）：
- 外送器方法是 `@ApplicationModuleListener(propagation = SUPPORTS)`。`@ApplicationModuleListener` 默认是 `REQUIRES_NEW`，外送器特意改成了 `SUPPORTS`：当前没有事务就不开**物理事务**。但它仍会开一个「空的事务作用域」，事务同步是打开的：作用域里只要有代码取连接（查一次库），这条连接就绑在线程上，一直拿到外送方法返回，也就是跨过整个等确认。所以前提是**外送路径上没有任何代码查库**：外送配置的选择、路由（包括 SpEL 引用的 bean）、映射、消息转换都在这个作用域里。现在 identity、interaction 的映射都是纯构造，满足这一条。
- 进入时把登记行标成 `PROCESSING`、结束时标完成或失败，由完成登记拦截器做（顺序 `HIGHEST_PRECEDENCE + 10`，在事务拦截器外层）。这两步各是一个 `REQUIRES_NEW` 短事务，用完就还，不跨等待。
- 补投走同一个外送器方法，路径相同。

**证据**：`ExternalizationConnectionIT` 把连接池压到 2 张、取连接最多等 3 秒，让确认永远不回（`EventsITUnreachableConfirmConfig`）：首次外送 4 个、补投 5 个同时等确认期间，活动连接数一直是 0，业务写库照常成功；上下文里的事务管理器是 `JpaTransactionManager`，与服务一致。反证：临时加一个在 `REQUIRES_NEW` 事务里拿着连接睡眠的异步提交后监听器，同一个测试立即报 `Connection is not available … active=2`，说明测试确实能抓到占连接。再一个反证：在测试的映射函数里查一次库，等确认的外送把连接池占住，测试同样变红（这就是上面「外送路径不能查库」那条前提）。

**决定**：不加执行器上限，加两道护栏。
- 运行时：`ConfirmingRabbitMessagingTemplate` 发送前检查线程上有没有挂着事务资源（数据库连接、EntityManager 等），有就直接报错、不发也不等。外送路径里有人查了库、或有人在事务里经 `RabbitMessageOperations` 发送，测试里立刻暴露（报「线程上挂着事务资源…不能等 broker 确认」），而不是线上把连接池耗尽；事件留在登记表，等修好后补投。
- 测试：`ExternalizationConnectionIT` 守住「正常外送不占连接」，以后 Modulith 升级把外送器改回在事务里跑，它会变红。

identity 本地的 `spring.task.execution.pool.core-size: 2` 和它的配置测试删掉，interaction 也不用配。服务代码里没有别的异步提交后监听器（identity、interaction 都核过），而服务不写 `@ApplicationModuleListener`（4.4），自己写也会是默认的 `REQUIRES_NEW`，这类监听器才会在等待时占连接。

**三个备选为什么不需要**：
- 给外送单独一个有界执行器：限制的是同时等确认的线程数，可这些线程本来就不占连接，限了也护不住什么；
- 模块出默认的异步并发上限并校验它小于连接池：校验的这层关系并不存在；
- 等确认期间不持有连接：本来就没有持有。

**没管的残余风险**：
- broker 卡住期间，每个外送占一个异步线程，至少等满确认超时（5 秒）。真卡住时可能更久：等不到确认的信道不回缓存，后续发送要新开信道，开信道的 RPC 只受 `channel-rpc-timeout` 限制（Boot 默认 10 分钟），所以线程可能卡到分钟级。这只占线程、不占连接；补投会把排队太久的行再派一次，恢复后重复投递由消费端去重兜住。
- Boot 默认的执行器队列不设上限，长时间卡住时排队的外送任务堆在内存里（每个只带一个事件对象，按现在的量可忽略）。哪天要给执行器配队列容量，理由写内存，并且拒绝策略**不能用 `CallerRunsPolicy`**：提交后监听在业务事务的 `afterCompletion` 里执行，那时请求线程还拿着业务连接，队列满了让请求线程自己跑外送，等于原问题复活；默认的 `AbortPolicy` 只打 ERROR、事件留给补投。
- 去掉 identity 的并发上限后，8 个异步线程同时超时时会集中做几次毫秒级的短事务（标失败、标进行中），算不上饿死。

### 4.14 `processed_event` 的保留期清理（10/31 补）

**问题**：去重表每处理一个事件就多一行，只增不减。删早了会出错：同一个事件如果在行被删之后又来一次，`markProcessed` 会把它当成第一次。

**`processed_event` 是第一道去重，不是唯一一道**。现有消费方各自还有业务层的第二道，行删了再来的重复事件今天没有可见后果：
- 安全邮件：`SendSecurityMailHandler` 先按事件 ID 查 `mail.mail_delivery`（这张表不清理），查到就跳过，不会再发一封；
- 回复与 @ 提醒：插入带 `ON CONFLICT (recipient_id, comment_id) DO NOTHING`，不会多一条；
- 评论删除、经验删除：有墓碑，重复处理也是幂等的；
- 有帮助（PAP-144）：同一个人、同一时刻的重复点击被吸收，不重置已读。

所以保留期真正保护的是「没有第二道去重的副作用」。**以后新增的消费方**：副作用不幂等的，要么自己带一道业务层去重，要么接受「超过保留期才再到的重复会再处理一次」。

**同一个事件什么时候会再来**：
- **broker 重投**：消费方处理完、还没 ack 就断开（崩溃、重启），这条消息在连接恢复后立刻重投。间隔等于消费方停机多久，一般是分钟级，最坏是一次几天的故障。
- **发布方补投**：消息其实送到了、也处理了，但发布方没拿到 broker 的确认（超时、断线），登记行留着，补投任务每分钟再发一次，直到某次拿到确认。间隔等于 broker「收得下却回不了确认」持续多久，通常随 broker 恢复几分钟内结束。补投没有上限：极端情况下登记行卡很久（broker 半坏几天，或者长假后才有人处理），几天后才补投成功。还有一种 broker 本身健康的情形：同一个路由键绑了多个队列、其中一个拒收（比如超长度拒收），broker 回 nack，其他队列却已收下，于是每分钟送来一份副本、一直持续；现在每个路由键只绑一个消费队列，不会发生，以后多绑时要留意（这时补投 WARN 里「多半是路由键没有队列绑定」的提示会误导）。
- **死信重放**：死信里可能是已处理事件的重复副本。比如第一份处理成功、ack 前断开，重投的那份碰上数据库抖动、重试三次都失败进了死信；死信队列不设过期，超过保留期再重放，去重行已经没了，会被当成新事件。上线手册与 README 的重放步骤都写了：重放超过保留期的死信前，先按事件 ID 查业务表。

**选 30 天**：比前两种的现实间隔（分钟到小时，最坏几天）长一个数量级，也覆盖「出事后过一个长假才处理」。
- **代价**：多留 30 天的行。notification 每天处理的事件按百到千条计，30 天几万行、每行几十字节，几 MB；按 `processed_at` 加索引，清理只扫要删的行。与信箱的 5 个月对齐也只是几十 MB，选 30 天是因为现有消费方都有第二道去重，不必为它们多留。
- **残余风险**：超过 30 天才再到的重复会被再处理一次，现有消费方靠第二道去重挡住。补投那条路要登记行卡 30 天以上，这期间补投每分钟都会打 WARN，到不了 30 天；死信那条路靠重放前先查业务表。
- **为什么不按事件时间丢弃太旧的事件**：集成事件只约定了 `eventId`，没有统一的时间字段；而且按时间丢会把真正没处理过的晚到事件也丢掉。
- **配置**：`patra.events.consumer.processed-retention`，不写单位按天；必须为正，写 0 或负数启动失败（否则每天把去重表删光）。

**怎么触发**：和补投一样用 XXL-Job。模块注册 `processedEventPurgeJob`（`ProcessedEventPurger`），消费事件的服务在调度中心登记，每天一次（CRON `0 0 7 * * ?`）。调度中心看得到、可以手动执行；不用 `@Scheduled`（服务没开 `@EnableScheduling`），也不在 `markProcessed` 里顺手删（每次写入多一条删除，业务事务变长）。删除分批：每批一条语句、最多 1000 行、各自提交，删到某批为 0 才停（有并发清理时也不会提前收工），不开长事务、不一次锁住大量行。「各自提交」的前提是任务线程不带事务、连接池开着自动提交（Hikari 默认如此）；哪个服务关掉自动提交，连接归还时删除会被回滚，任务照样回写删了多少，行却还在。

**谁要登记**：现在只有 notification 消费事件；identity、interaction 也会注册这个任务入口（模块按有没有 `JdbcClient` 装配），但它们没有 `processed_event` 表，调度中心里不要给它们登记（误登记只会让任务报失败）。

## 5. 包结构

```text
patra-tool-core
  dev.patra.tool.core.event
    IntegrationEvent
    ProcessedEventPort

patra-tool-events
  dev.patra.tool.events
    autoconfigure
      EventsProperties                        record，前缀 patra.events
      EventsDefaultsPostProcessor             4.9 默认值
      EventsRabbitAutoConfiguration           转换器、mandatory 定制、ConfirmingRabbitMessagingTemplate
      EventsResubmitAutoConfiguration         EventPublicationResubmitter；有 xxl-job 时注册任务
      EventsConsumerAutoConfiguration         JdbcProcessedEventAdapter、ProcessedEventPurger；有 xxl-job 时注册清理任务
    publisher
      ConfirmingRabbitMessagingTemplate
    resubmit
      EventPublicationResubmitter
      EventPublicationResubmitJob
    consumer
      EventQueue
      JdbcProcessedEventAdapter
      ProcessedEventPurger
      ProcessedEventPurgeJob

patra-tool-test
  dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer
```

## 6. 测试

| 验收（PAP-137） | 测试 |
|---|---|
| 事务提交后事件送达消费者 | IT：事务里写一行业务数据并发布事件，消费者收到，业务效果表有 1 行，登记表为空（DELETE） |
| 事务回滚时不登记、不外送 | IT：发布后抛异常回滚，登记表为空，等待后消费者没收到 |
| broker 不可达时业务照常提交、登记行未完成；恢复后补投送达，业务只执行一次 | IT：`rabbitmqctl stop_app` 后发布，业务行在、登记行 `FAILED`；`start_app` 后跑补投，效果表 1 行，登记表为空 |
| 等不到 confirm 时登记行不被标为完成 | IT（真 broker 回 nack）：目标队列设 `x-max-length=0` + `x-overflow=reject-publish`，登记行留着；IT（超时）：单独上下文把 `confirm-timeout` 设成 1 纳秒，登记行留着、但消息实际到了队列，补投再发一遍后效果表仍只有 1 行（同时覆盖「至少一次 + 去重」）；单测：模拟 `RabbitTemplate`，覆盖 ack、nack、超时、退回、中断 |
| 同一事件投递两次，业务只执行一次 | IT：用同一个 `eventId` 直接发两条，效果表 1 行，`processed_event` 1 行 |
| 失败超过重试次数进死信队列 | IT：监听器对标记过的事件一直抛异常（测试里重试间隔调到毫秒级），消息出现在 `<队列>.dlq`，共被调用 1 + 3 次 |

另外：

- 没有队列可投（退回）：IT，路由到没有绑定的路由键，登记行留着。
- 卡在 `RESUBMITTED` 的行：IT，把一条 `FAILED` 行手工改成 `RESUBMITTED` 且补投时间在 2 分钟前，跑补投后送达。
- 启动检查：契约测试，连接工厂没开确认时启动失败、报错写明配置键。
- 自动配置：每个自动配置类的 `ApplicationContextRunner` 契约测试（R4）；默认值后处理器单测（不覆盖已有键）。
- `RabbitMQContainerInitializer` IT（握手 AMQP 协议头）。
- 补投时尝试 10 次以上的行打 WARN：单测断言日志里有登记行 ID 与尝试次数，且事件内容读不出来时补投不中断。
- 正在发送的行不重复派发：单测覆盖 `PROCESSING` 10 分钟内跳过、超过 10 分钟重投、`FAILED` 照常补投。
- 确认关联 ID：同一事件发两次，两次关联 ID 不同且都不等于 `eventId`，`messageId` 都是 `eventId`。
- 默认值：`management.health.rabbit.enabled=false`。
- 等确认时不占数据库连接：`ExternalizationConnectionIT`（4.13）。
- 线程上挂着事务资源时拒绝等确认：`ConfirmingRabbitMessagingTemplateTest` 绑一个资源后发送，断言报错（4.13）。
- 已处理事件清理：`ProcessedEventPurgerIT` 在真 PostgreSQL 上插 2500 条 40 天前的行和 3 条 29 天前的行，清理删掉 2500 条（跨三批）、留下 3 条；`ProcessedEventPurgerTest` 断言截止时间与「删到 0 才停」；装配契约测试覆盖注册条件、不写单位按天、0 天启动失败、配置值传到清理器；任务单测覆盖回写（4.14）。
- 只登记外送器（4.12 落地时加）：IT 里放一个普通的 `@TransactionalEventListener(AFTER_COMMIT)`，发布后登记表里只有外送器的行。
- 外送监听器的 ID：IT 断言登记行的 `listener_id`，防止以后误加第二个外送器。2.0.8 是 `DelegatingEventExternalizer.externalize(java.lang.Object)`，2.1 改为 `EventExternalizerModuleListener.externalize(java.lang.Object)`。

## 7. 文档与规则

- `.claude/rules/tech/transaction.md`：§1「提交后不能丢的副作用」改为「发领域事件，由 `patra-tool-events` 外送」，「提交后可丢的副作用」一行不动（2.1 用 trigger-annotation 后普通提交后监听器不进登记表）；§3 的 Outbox 中继一行改为消费端去重的写法。
- `patra-events` 技能：「其他微服务」分支改成本模块的用法（发布端四步、消费端两步、事件 ID 约束）。
- `.claude/rules/project-info.md` 的工具模块清单（12 → 13 个）、`patra-hexagonal` 技能的 `patra-starters.md` 加本模块。
- 新增 `patra-tool/patra-tool-events/README.md`：能力、配置、默认值、发布端与消费端接入步骤、Flyway 模板。
- `patra-infra/cd/module-graph.json`：加模块后重跑 `./gradlew dumpModuleGraph`。

## 8. 上线步骤

本 Issue 只加工具模块，不改任何服务的运行时行为，合入后 mini 上无需操作。接入的服务在各自 Issue 里做：

1. boot 模块引入 `patra-tool-events`，Flyway 加 4.10 的表；
2. boot 层写 `EventExternalizationConfiguration`（选哪些事件、交换机、路由键、映射）与交换机 bean；
3. Nacos 或服务配置里写 RabbitMQ 连接（地址、账号按 PAP-138 的密钥规则）；
4. 发布方在 xxl-job-admin 登记 `eventPublicationResubmitJob`（4.6 的参数）；
5. identity 保持默认 `DELETE`；其他服务若改 `UPDATE` 要自己清理已完成的行；
6. 部署顺序：先部署消费方（通知服务声明队列与绑定），再部署发布方（2.3）。条目已写进共享笔记 `pap-137-runbook-items.md`，由 v0.16 上线手册的维护者并入。

## 9. 留给后续 Issue

- 死信告警与重放工具（release spec 决策 E 的代价）。
- 无。落到 2.1 的改动属于本 Issue，见 4.12。
