# 通知服务实时推送工程设计（PAP-145）

> 状态：设计定稿（2026-10-31），待实施
> 日期：2026-10-31
> Issue：PAP-145（GitHub #332），Linear Project P-PAP-13 v0.16 Notification，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1「推送什么」「后台与断线」、第 3 节 B / C、决策 H、决策 I、Done D3 / D8；PAP-143 信箱契约（`docs/patra/specs/2026-10-31-notification-inbox-design.md` 第 10 节）
> 拍板：用户睡前授权编排会话「按建议推进整个版本」，本文的选择由本会话按最优方案定，理由写在各节，汇总在第 13 节

## 1. 要解决的问题

门户现在每 60 秒问一次未读数。别人回复了你，铃铛最多要等一分钟才变；人多时，绝大多数轮询问到的都是「没变」。本 Issue 让通知服务在未读数变了的那一刻主动告诉浏览器：

1. 通知服务提供一个 SSE 接口，浏览器连上后，先收到当前三个未读数，之后每次变化收到新的三个数。
2. 只在信箱写入的事务**提交后**推，回滚了不推。
3. 连接能稳稳地挂着：每 25 秒一次心跳，挂满 30 分钟由服务端主动结束、浏览器自动重连。
4. 网关给推送单独一个连接池，推送连接再多也挤不到普通请求。
5. 推送连接在线，算这个用户「在站上」（决策 H），离站邮件（PAP-146）据此不发。

门户怎么接（Route Handler 透传、`EventSource`、切到后台断开、回退轮询、处理 401）归 PAP-149，本文第 5 节是给它的契约。

## 2. 先讲清几个概念

### 2.1 SSE：服务器往浏览器单向「念」消息

**是什么**：浏览器发一个普通的 GET，服务器回 `Content-Type: text/event-stream`，然后**不结束响应**，有事就往里写一段文本：

```
event: unread-count
data: {"total":3,"replies":2,"mentions":1,"helpful":0}

```

每段以空行结尾。以冒号开头的行是注释，浏览器收到后什么也不做，正好拿来当心跳。浏览器端用标准的 `EventSource` 接收，连接断了它会自己隔几秒重连（间隔由服务器发的 `retry:` 字段决定）。

**防什么**：轮询的两个毛病，一是慢（最多慢一个轮询周期），二是浪费（大多数请求的答案都是「没变」）。

**业界**：WebSocket 是双向的，能做聊天、「正在输入」；SSE 只能服务器说、浏览器听，但它就是普通 HTTP，过代理、过 Cloudflare、带令牌都和普通请求一样。Mastodon 的推送、GitHub 的部分实时更新用的都是这种「服务器推、浏览器听」的模式。

**我们**：只要告诉浏览器「数字变了」，用 SSE（第 3 节 B 已定不做 WebSocket）。

### 2.2 异步请求与它的超时

**是什么**：普通请求是「一个线程处理到底、写完响应、线程还回去」。SSE 的响应要挂几十分钟，不能一直占着一个线程。Servlet 的**异步请求**允许处理方法先返回、把线程还给 Tomcat，响应留着不关；之后任何线程都能往里写。Spring MVC 的 `SseEmitter` 就是这样一个「留着的响应」。

**防什么**：每条连接占一个线程，几百条连接就把线程池占满。

**坑**：异步请求有超时。不显式设置时用 Tomcat 的默认值 **30 秒**，到点连接被掐断，浏览器重连，再 30 秒又断，日志里全是超时。

**我们**：给 `SseEmitter` 显式设 30 分钟超时，并把「超时」当成「连接寿命到了」：到点时服务端正常结束响应，不报错（第 6.4 节）。一个机制同时满足 release spec 的「显式设置异步超时」和「连接最长 30 分钟」。

### 2.3 空闲超时与心跳

**是什么**：浏览器到通知服务之间隔着好几层（门户、网关，公网再加 Cloudflare），每一层都有「连续多久没数据就当连接死了、断掉」的**空闲超时**：网关读超时 60 秒，Cloudflare 125 秒。SSE 在没有新提醒时可能几十分钟一个字节都不发。

**防什么**：中间某一层把一条正常但安静的连接当成死连接断掉。

**我们**：每 25 秒往每条连接写一行注释 `:ping`，比最短的 60 秒还短一半以上。浏览器看不到注释，各层代理都看到「有数据在流」。

### 2.4 连接池，以及为什么要隔离

**是什么**：网关转发请求时，到下游的 TCP 连接是从**连接池**里借的，用完还回去给下一个请求用，省去每次握手。池子有上限，借不到就排队。

**防什么**：普通请求几毫秒就还；SSE 一借就是 30 分钟。两种请求共用一个池子，推送连接一多，普通请求就借不到连接、排队、超时。

**实测的数**（读 Reactor Netty 1.3.8 源码，`TcpResources.create`）：网关用的全局池是**每个下游地址 500 条**、最多 1000 个排队、排队最多等 45 秒。release spec 决策 I 写的「max(CPU, 8) × 2 条」是 `ConnectionProvider.create(name)` 这种自建池的默认值，网关没用它；网关 WebMVC spec 第 6 节的 500 是对的（第 12 节更正）。通知服务只有一个实例，也就是一个地址：推送连接和通知服务的普通接口（列表、已读、偏好）共用这 500 条。

**我们**：网关给推送路由一个自己的池子（第 7 节）：上限单独算，满了**立刻**回 503、不排队，前端退回轮询；普通请求的池子完全不受影响。

### 2.5 提交后再推

**是什么**：信箱写提醒是一个数据库事务。如果在事务里就把「数字变了」推出去，浏览器马上来拉列表，事务可能还没提交，拉到的是旧数据；事务要是回滚了，推出去的数字根本不存在。

**我们**：信箱在事务里发布领域事件 `UnreadCountsChangedEvent`，推送这边用 `@TransactionalEventListener(AFTER_COMMIT)` 订阅：Spring 只在提交成功后才把事件交给它，回滚时直接丢掉（PAP-143 契约第 3 节）。

## 3. 目标、非目标、反目标

**目标**

- 别人回复后 3 秒内，收件人开着的每个标签页都收到新的未读数。
- 连接在没有提醒的情况下挂满 30 分钟不被任何一层掐断；到点服务端正常结束、浏览器 3 秒后自动重连，网关借机重新查会话。
- 推送连接满了，经网关的普通请求照常；多出来的推送连接立即 503。
- 登出（会话失效）后，重连被网关以 401 拒绝。
- 推送连接在线期间，`kernel.presence` 每 25 秒更新一次。

**非目标**

- 推提醒内容（只推三个数，2.1 产品定义）。
- 断点续传（`Last-Event-ID`）：连上先推当前值，丢了中间的变化也无所谓。
- 多实例（第 3 节 C）。
- 已建好的连接在会话失效时立刻断开：网关只在建立时查会话，最晚 30 分钟后重连时拦住（2.1「后台与断线」已接受）。

**反目标**（技术上能跑，但算失败）

- 推送失败让「写提醒」或「标已读」失败、回滚或变慢。推送是锦上添花，信箱是本体。
- 推送把数字推错且停在错的值上（并发时旧值覆盖新值）。
- 部署通知服务或网关时，挂着的推送连接拖住优雅停机（第 6.6、7.6 节）。
- 事件攒在中间某一层、不及时到达浏览器（第 7.5 节：网关默认就会这样）。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 I | SSE 放在通知服务；提交后推；只推三个数；连上先推一次当前值；25 秒心跳；30 分钟寿命；显式异步超时；网关单独连接池 |
| 决策 H | 推送连接在线算在站 |
| 第 3 节 C | 只跑一个实例，连接表在进程内存里 |
| PAP-143 契约 | `UnreadCountsChangedEvent(Set<Long> recipientIds)`（`domain.inbox.event`）在信箱写入事务里发布，不带数字；`InboxReadPort#unreadCounts(long)` → `UnreadCounts(replies, mentions, helpful)`，`total()` = replies + mentions；`PresenceGateway#markSeen(long)`（`domain.kernel.port.gateway`，时刻取 `Clock`）；SSE 数据与 `GET /me/notifications/unread-count` 的响应体同形 |
| 骨架 spec | 子域 `realtime`；计划中的依赖边 `realtime → inbox` 由本 Issue 在架构测试里声明；推送路由要排在通用 `/patra-notification/**` 前面 |

## 5. 接口契约（给 PAP-149）

同一份摘要放在共享笔记 `PAP-145-sse-contract.md`。

**请求**

```
GET /patra-notification/me/notifications/stream      （经网关；通知服务自己的路径是 /me/notifications/stream）
Accept: text/event-stream
Authorization: Bearer <会话令牌>                       （由门户 Route Handler 加，网关查会话后换成身份断言）
```

**成功**：`200`，`Content-Type: text/event-stream`，`Cache-Control: no-cache, no-transform`。第一条事件在通知服务返回响应之前就写好了，和响应头一起到达：`EventSource` 触发 `open` 时数据已经在路上。响应不结束，依次收到：

1. **立刻**一条当前值，带重连间隔：

   ```
   retry: 3000
   event: unread-count
   data: {"total":3,"replies":2,"mentions":1,"helpful":0}

   ```

2. 之后**每次变化**一条（同名事件、同形数据，不带 `retry`）：写入提醒、单条已读、全部已读、删除联动删了提醒。数据就是推送那一刻读到的三个数，可能连着两条一样（两次变化挨得很近时），前端照收即可。
3. **每 25 秒**一行注释 `:ping`（`EventSource` 不触发任何事件）。
4. **第 30 分钟**服务端正常结束响应。`EventSource` 按 `retry` 3 秒后自动重连，重连时网关重新查会话。

字段：`total` = `replies` + `mentions`（铃铛），不含 `helpful`；`helpful` 是「有帮助」标签自己的数，PAP-144 合入前恒为 0。没有 `id:` 行，不支持 `Last-Event-ID`：重连后第一条就是当前值。

**失败**（都是 `application/problem+json`，不是事件流；`EventSource` 收到非 200 会触发 `error` 并**不再自动重连**）：

| 情况 | 状态码 | 错误码 | 前端怎么办 |
|---|---|---|---|
| 没带令牌、令牌无效 / 过期 / 已登出 | 401 | `GW-0401` | 当作已登出：关掉推送、不回退轮询（轮询也会 401） |
| 通知服务挂了、没有实例；推送连接池满了 | 503 | `GW-0503` | 退回 60 秒轮询，稍后（如下次切回前台）再试推送 |
| identity 查不了会话 | 503 | `GW-0503` | 同上 |
| 通知服务 60 秒没回响应头 | 504 | `GW-0504` | 同上 |

**断线**：响应中途断开（部署通知服务或网关、网络抖动）时，`EventSource` 进入重连，3 秒后再连；重连拿到非 200 就按上表处理。

**给 Route Handler 的提醒**：上游状态码原样回给浏览器（非 200 时浏览器才会停止重连）；不缓冲、不压缩（上游带了 `no-transform`）；浏览器断开时中止上游请求，网关和通知服务才能及时释放连接。

## 6. 通知服务侧

### 6.1 分包

realtime 子域只依赖 inbox 的公开包（`model.read.UnreadCounts`、`port.read.InboxReadPort`、`event.UnreadCountsChangedEvent`）和底座的 `PresenceGateway`。架构测试加 `allowingDependency("realtime", "inbox")`。

| 层 | 类 | 职责 |
|---|---|---|
| domain | — | 没有业务规则需要放这里：推送是信箱状态的一种投递方式，规则（谁的数、怎么算）都在 inbox。domain 里只有骨架的 `package-info` |
| app | `service.UnreadCountStream`（接口） | 一条已打开的推送连接：推数字、推心跳、结束。不认识 SSE，由 adapter 实现 |
| app | `service.UnreadCountStreamRegistry` | 「用户 → 连接」表：登记（并推当前值、记在站）、注销、按用户推、心跳一轮、全部结束 |
| app | `listener.UnreadCountPushListener` | 订阅 `UnreadCountsChangedEvent`：提交后、换线程，交给表按用户推 |
| adapter | `rest.UnreadCountStreamController` | `GET /me/notifications/stream`：要登录，建 `SseEmitter`，登记 |
| adapter | `rest.SseUnreadCountStream` | 用 `SseEmitter` 实现 `UnreadCountStream`，并把 emitter 的完成 / 超时 / 出错接回表里注销 |
| adapter | `rest.response.UnreadCountsEventResponse` | 事件数据：`total`、`replies`、`mentions`、`helpful`，与 inbox 的未读数响应同形 |
| adapter | `scheduler.UnreadCountStreamHeartbeatScheduleJob` | 每 25 秒调表的「心跳一轮」 |
| adapter | `config.RealtimeProperties` | `patra.notification.realtime.heartbeat-interval`（25s）、`max-connection-age`（30m） |

**为什么连接表在 app、接口由 adapter 实现**：表里的逻辑（并发、顺序、心跳、在站、失败剔除）和 SSE 无关，放 app 可以纯单元测试；SSE 是 Web 技术，只能在 adapter。adapter 依赖 app 本来就允许，app 定义接口、adapter 实现，方向不变。不放 domain 的 `port` 包：项目规则要求 domain 端口的实现在 infra（Gateway 除外），而 `SseEmitter` 只能在 adapter 创建。

### 6.2 建立连接

1. 控制器 `CurrentUserPort.require()` 取当前用户；没有身份断言时抛出，安全 starter 回 401 `NOTIFICATION-0401`（正常经网关时网关已先拦住，这里是第二道）。
2. 建 `SseEmitter(max-connection-age)`，包成 `SseUnreadCountStream`，交给表登记。
3. 登记时在该用户的锁里：放进表 → 读当前三个数 → 推给这条连接（带 `retry: 3000`）。先放进表再读，读之后发生的变化一定会再推一次，不会漏。
4. 锁外记一次在站。
5. 返回 `ResponseEntity<SseEmitter>`，响应头带 `Cache-Control: no-cache, no-transform`：`no-transform` 让 Next.js 的压缩中间件和 Cloudflare 不对事件流做 gzip（压缩会攒满一块才发，事件就卡住了）。

读当前值失败（数据库挂了）时从表里撤下这条连接，异常照常抛出，回 500 / 503 的 ProblemDetail，不返回事件流。

### 6.3 提交后推

`UnreadCountPushListener` 用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 加 `@Async` 订阅 `UnreadCountsChangedEvent`：

- **提交后**：回滚时 Spring 不会调用它。
- **换线程**（`@Async`，用 Boot 自动配置的 `applicationTaskExecutor`；boot 层加 `@EnableAsync`）：提交事务的线程是 RabbitMQ 消费线程或门户「标已读」的请求线程，推送的慢、出错都不能落到它们头上（第 3 节反目标）。另一个原因：提交后的回调里事务资源还挂在线程上，在原线程里查库会「参与」一个已提交的事务；换到新线程就是干净的上下文。
- 新线程里对每个收件人：没有连接就跳过（不查库）；有连接时，在该用户的锁里读三个数、推给他的每条连接。

**顺序**：同一个用户的两次变化可能在两个线程里同时推。读数放在锁**里面**：后拿到锁的那个，读到的一定是两次提交之后的状态，最后一条推出去的总是最新值。不在锁外读，否则先读的旧值可能后发，铃铛停在错的数上。

**取数的事务**：按 inbox 读端口实现的约定；它若要求已有事务，就在推送线程里开只读事务（实现时看 PAP-143 的 `InboxReadAdapter`）。

**失败**：推某条连接出错（浏览器已走、写不进去）→ 从表里剔除这条、结束它，别的连接照推；读数出错 → 记 WARN，这一轮不推，等下一次变化或下一次重连。监听器不往外抛异常。

### 6.4 连接寿命 30 分钟

`SseEmitter` 的超时就是寿命：到第 30 分钟 Servlet 容器触发超时，`onTimeout` 里调 `complete()` 正常结束响应（不是报错），emitter 的完成回调把它从表里注销。浏览器看到流正常结束，按 `retry` 3 秒后重连。

为什么不另起定时器数 30 分钟：异步超时本来就必须显式设（不设是 30 秒），设成 30 分钟后它自己就是那个定时器，少一套机制、少一个「两个计时不一致」的可能。

### 6.5 心跳与在站

`UnreadCountStreamHeartbeatScheduleJob` 用 Spring 的 `@Scheduled(fixedRateString = "${patra.notification.realtime.heartbeat-interval}")`（boot 层加 `@EnableScheduling`），每 25 秒调一次表的「心跳一轮」：

- 对每个有连接的用户，在他的锁里给每条连接写 `:ping`；写失败的剔除。
- 一轮里每个仍有连接的用户记一次在站（`markSeen`），同一用户多条连接只记一次。

为什么用进程内定时器、不用 XXL-Job：连接只存在于这个进程的内存里，心跳必须由持有连接的进程自己发；XXL-Job 是集群调度，粒度和语义都不合适。

一轮心跳是所有连接一起发，所以一条连接的第一次心跳在建立后 0 ~ 25 秒内到达，之后间隔 25 秒；任何时刻的最长静默是 25 秒。

### 6.6 停机时先结束所有连接

Boot 默认优雅停机：停机时等在途请求结束，最多等 30 秒再强关。挂着的推送连接永远不会自己结束，每次部署都会白等 30 秒、然后被强关。

表监听 `ContextClosedEvent`（它先于优雅停机的等待发出）：给每条连接调 `complete()`，正常结束响应、清空表。浏览器随即重连，新实例起来之前拿到 503，前端退回轮询（决策 I 的代价：部署时所有推送连接断开、重连并重拉一次）。

### 6.7 配置

`application.yml`（随代码，不进 Nacos）：

```yaml
patra:
  notification:
    realtime:
      # 心跳间隔：短于网关读超时 60 秒、Cloudflare 空闲超时 125 秒
      heartbeat-interval: 25s
      # 一条推送连接最长活多久，也是 SseEmitter 的异步超时；到点服务端正常结束，浏览器重连、网关重新查会话
      max-connection-age: 30m
```

集成测试把两个值压短（如 200ms、2s），用例才跑得快。

## 7. 网关侧

### 7.1 推送路由

`application.yml` 在通用 notification 路由**之前**加一条（Gateway 按列表顺序匹配，先到先得）：

```yaml
- id: patra-notification-stream
  uri: lb-push://patra-notification
  predicates:
    - Path=/patra-notification/me/notifications/stream
  filters:
    - StripPrefix=1
```

`lb-push` 是自定义的 scheme。Gateway Server MVC 5.0.4 从配置建路由时，按 scheme 找名为 `{scheme}HandlerFunctionDefinition` 的 Bean 来决定「用什么处理器转发」（`RouterFunctionHolderFactory`），自带的 `lb://` 就是这样接上负载均衡的（`lbHandlerFunctionDefinition`）。我们照它注册一个 `lb-pushHandlerFunctionDefinition`：负载均衡过滤器照用自带的 `LoadBalancerFilterFunctions.lb(服务名)`，处理器换成一个用推送专用 `RestClient` 的 `ProxyExchangeHandlerFunction`。

这样做的好处：路由仍然写在配置里、和其他路由一个样子；出站头过滤器（剥外部 `Authorization`、写身份断言、`X-Forwarded-*`）是同一批 Bean，处理器在容器刷新时自动收集，行为和普通路由一致；只有「连接从哪个池子借」不同。

**实现要点**：

- 推送用的代理交换（`PushProxyExchange`，继承 `RestClientProxyExchange`，见 7.5）和 `ProxyExchangeHandlerFunction` 都**不能**声明成 Bean。自带的 `restClientProxyExchange` 是 `@ConditionalOnMissingBean(ProxyExchange.class)`、自带的处理器注入「唯一的」`ProxyExchange`：多注册一个，所有 `lb://` 路由会悄悄换到推送池上；处理器同理，`lb://` 路由按类型查找它会查到两个。两者都在 `lb-pushHandlerFunctionDefinition` 这个 Bean 内部建（`@Bean(name = "lb-pushHandlerFunctionDefinition")`，横线在 Bean 名和 URI scheme 里都合法）。
- 内部那个处理器要收到容器刷新事件才会收集出站头过滤器（`init()`）；收不到时它**原样转发全部请求头**，浏览器带的会话令牌会直达通知服务。所以把它包在一个小 Bean 里，由这个 Bean 实现刷新事件监听并转交。路由测试要断言下游**收不到**会话令牌（第 9 节）。

### 7.2 推送专用连接池

- 一个 Reactor Netty `ConnectionProvider`（Bean，停机时 `dispose`）：最多 `patra.gateway.push.max-connections` 条（默认 500），**不排队**（`pendingAcquireMaxCount(0)`：借不到立即失败）。
- 推送用的 HTTP 客户端从 Boot 装好的那个 `ClientHttpRequestFactoryBuilder` 派生，只换掉「从哪个池子建 `HttpClient`」（`withHttpClientFactory(() -> HttpClient.create(推送池))`），所以连接超时 5 秒、读超时 60 秒（空闲）、不跟随重定向、不谈压缩、写超时处理器都和普通路由一样。
- 推送用的 `RestClient` 由容器里的 `RestClient.Builder` **先 `clone()`** 再换请求工厂：这个 Builder 是 `patra-tool-http-client` 注册的单例（`@Primary`），自带的代理交换也用它，直接改它，普通路由会跟着换到推送池上（实施时被池满用例抓到：普通请求也 503 了）。
- 读超时仍是 60 秒：通知服务每 25 秒一次心跳，正常连接不会撞上；通知服务卡死时 60 秒后断开。

**为什么上限 500**：和普通池同一个量级；现在用户是个位数，远远够；它的意义是「有上限、满了立刻失败」，而不是精确的容量规划。网关这边每条推送连接占一个 Tomcat 连接、一个虚拟线程、一条 Netty 连接，通知服务那边占一个 Tomcat 连接和一个 emitter，两边 Tomcat 默认都允许 8192 条连接，500 条离哪个上限都很远。

**满了为什么不排队**：排队的请求最多等 45 秒，浏览器那边就是 45 秒白屏转圈，最后还是失败。立刻 503，前端马上退回轮询，体验更好。

**池满时回什么**：借连接失败在代理调用里表现为 I/O 异常，按现有分类规则归「到不了」→ 503 `GW-0503`。实施时用测试确认；分类不对就补上这一类。

### 7.3 鉴权：推送路径要登录

`GatewaySecurityConfiguration` 第二条链加一条规则：`/patra-notification/me/notifications/stream` 需登录，匿名 401 `GW-0401`，放在 `anyRequest().permitAll()` 之前。

为什么网关就拦、不全交给通知服务：

- 没登录或已登出的请求根本不借推送池的连接，也不到达通知服务。
- 401 由网关的 `SecurityProblemWriter` 直接写出，不受请求 `Accept: text/event-stream` 影响，前端拿到的总是 ProblemDetail。
- 「登出后重连被网关拒绝」是 AC 原文。

通知服务的普通接口仍按骨架 spec 的约定由它自己的安全 starter 判定，不改。

### 7.4 配置

`patra.gateway.push.max-connections` 放 `application.yml`（默认 500），不进 Nacos：只有调容量时才改。集成测试设成 2。

### 7.5 逐条事件刷出（实施时发现）

**问题**：Gateway 对 `text/event-stream` 的做法是「读一块、往浏览器写、刷一次」（`AbstractProxyExchange.copyResponseBodyWithFlushing`，缓冲 16 KB）。这对 JDK 客户端成立：它的响应体输入流有多少给多少。但网关用的是 Reactor Netty，Spring 7.0.10 给它的响应体输入流（`SubscriberInputStream`）一次读**要读满整个缓冲区或读到流结束才返回**。几十字节的事件和 7 字节的心跳会一直攒在网关里，30 分钟后流结束才一起出来，推送等于没有。

**实测**：下游每 300 毫秒滴一条心跳，第一条到浏览器用了约 5 秒，正是流结束的时刻（`GatewayPushRouteIT.should_deliver_each_event_as_soon_as_it_arrives` 先红）。网关 WebMVC spec 和 release spec 调研里「网关对事件流逐块 flush」的说法，对 Reactor 客户端不成立；普通路由目前没有事件流，不受影响。

**做法**：推送路由的代理交换 `PushProxyExchange` 覆盖 `copyResponseBody`：逐字节读（单字节读只等「有数据」，不等凑满），写进 Tomcat 的响应缓冲（写本身不出网），读到空行（一条 SSE 事件的结尾）就刷出去。通知服务的事件都以 `\n\n` 结尾，不处理 `\r`。

**代价**：每个字节一次方法调用和一次锁（`SubscriberInputStream` 内部）。推送流量是每条连接每 25 秒几个字节加偶尔一条事件，可以忽略。

### 7.6 网关停机时结束推送流（评审补充）

推送流在网关上是一个正在进行的阻塞代理请求，要挂 30 分钟。Boot 的优雅停机会等它，最多 30 秒；compose 没设 `stop_grace_period`，docker 10 秒后直接强杀。结果是每次部署网关，只要有人开着门户，都要白等 10 秒再被强杀。

做法同通知服务（6.6）：推送处理器监听 `ContextClosedEvent`（先于优雅停机的等待），关掉所有正在转发的下游响应体（`PushProxyExchange.closeOpenStreams()`）。卡在读上的线程被唤醒、读到结尾，转发正常结束，浏览器 3 秒后重连。推送连接池在 Bean 销毁时 `dispose`，那已经在优雅停机之后，挡不住这件事，所以要这一步。

## 8. 两条网络路径与各层空闲超时

| 层 | tailnet 直连（HTTP/1.1） | 公网（Cloudflare Tunnel，浏览器侧 HTTP/2） | 空闲超时 | 25 秒心跳够不够 |
|---|---|---|---|---|
| 浏览器 ↔ 边缘 | 直连门户 | 浏览器 ↔ Cloudflare | Cloudflare 125 秒 | 够 |
| 边缘 ↔ 门户 | — | Tunnel → 门户；Tunnel 对 `text/event-stream` 不缓冲 | 同上 | 够 |
| 门户 ↔ 网关 | Route Handler 发起的上游请求 | 同左 | 无（Node fetch 不设读超时） | — |
| 网关 ↔ 通知服务 | 推送专用池 | 同左 | 读超时 60 秒 | 够 |
| 通知服务 | `SseEmitter` | 同左 | 异步超时 = 寿命 30 分钟 | 到点正常结束 |

HTTP/1.1 下浏览器对同一站点最多 6 条连接，所以门户在标签页隐藏时关推送（PAP-149）；HTTP/2 没有这个限制，但关掉后台连接对两条路径都一样省资源、也和决策 H 的「后台超过 10 分钟算离站」一致。

## 9. 测试

| AC / 目标 | 测试 | 位置 |
|---|---|---|
| 别人回复后 3 秒内推到收件人的连接 | 起 PG + RabbitMQ，开一条推送连接（测试私钥签断言），向 `interaction.events` 发一条回复了他的 `comment.published`，3 秒内收到 `replies` +1 的 `unread-count` 事件；同一用户两条连接都收到；别人的连接收不到 | boot `integrationTest`（依赖 PAP-143 的消费者与测试支撑） |
| 事务回滚时不推送 | 在事务里发布 `UnreadCountsChangedEvent` 后回滚：连接上 1.5 秒内没有新事件；同样的操作提交：收到 | boot `integrationTest` |
| 闲置时连接不断（心跳） | 心跳间隔压到 200ms：闲置期间按间隔收到 `:ping`，连接一直开着；`kernel.presence` 的时刻随心跳前进 | boot `integrationTest` |
| 30 分钟后服务端结束 | 寿命压到 2 秒：约 2 秒后流**正常**结束（读到 EOF，不是 I/O 错误） | boot `integrationTest` |
| 显式异步超时、心跳 25 秒、寿命 30 分钟 | 读 jar 里的 `application.yml`，断言三个值 | boot `ApplicationConfigTest` |
| 推送长连接占满时普通请求不受影响 | 推送池设 2；WireMock 顶替通知服务、推送路径回一个持续滴数据的事件流；占满 2 条后：第 3 条推送立即 503 `GW-0503`，同时 `/patra-notification/me/notifications/unread-count` 照常 200 | 网关 `integrationTest` |
| 登出后重连被网关拒绝 | 有效会话能连上；吊销会话后再连 401 `GW-0401`，WireMock 收不到这次请求；不带令牌同样 401 | 网关 `integrationTest` |
| 推送路由转发正确 | 带会话连上，下游收到剥了前缀的路径与网关签的断言、**收不到**浏览器带的会话令牌，事件流原样透传 | 网关 `integrationTest` |
| 每条事件一到就到浏览器 | 下游每 600ms 滴一条心跳、共 6 秒，第一条 3 秒内到达客户端（7.5） | 网关 `integrationTest` |
| 网关停机不等推送流 | 卡在读上的转发，`closeOpenStreams()` 后 1 秒内结束 | 网关单元测试 `PushProxyExchangeTest` |
| 推送不进事件登记表 | 一次提交后推送之后，`event_publication` 里没有 `UnreadCountsChangedEvent` 的行（登记表只认 `@ApplicationModuleListener`，`patra-tool-events` 的默认值） | boot `integrationTest`（随回滚用例） |
| 表的行为 | 登记推当前值并记在站；按用户推到他的全部连接、不推别人；没连接不查库；发送失败的被剔除；心跳一轮每人记一次在站；读数失败不抛；全部结束 | app 单元测试 |
| 监听器 | 收到事件后对每个收件人调表的推送 | app 单元测试 |
| 架构 | `realtime → inbox` 声明后子域规则通过 | `NotificationArchitectureTest` |

未登录直连通知服务回 401 `NOTIFICATION-0401` 也写一条集成测试（第二道防线）。

真实 60 秒闲置、真实 30 分钟、Cloudflare 那条路径在 mini 上验（第 10 节与 D8）。

## 10. 上线步骤

追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` 第 7 步：

1. 没有新的 Nacos 配置、XXL 任务和密钥，默认值都在 jar 里。
2. 后端 PR 合进 main、CD 结束后，经 tailnet 直连网关验一次（测试账号的令牌只在本机 shell 变量里，不写进文件）：`curl -N -H "Authorization: Bearer $TOKEN" http://100.103.73.27:9528/patra-notification/me/notifications/stream`，立刻收到 `unread-count`，之后每 25 秒一行 `:ping`，挂满 30 分钟正常结束；不带令牌回 401 `GW-0401`。
3. 公网路径（Cloudflare）只开放了门户，随 PAP-149 联调时验（D8）。

## 11. 文档同步

- notification README：子域表里 realtime 一行补上接口与配置，加一节「实时推送」。
- 网关 README：路由清单、路径规则表加推送路径；「HTTP 客户端、超时与线程」表加推送池一行。
- release spec 决策 I 第 4 点的默认连接池数要更正（第 2.4 节）。release spec 由编排会话统一改，本分支不动它，更正文字随验收汇报交过去。
- 两份 README 都只追加，不重排已有段落（PAP-142 / 143 / 144 / 146 也在改 notification README）。

## 12. 考虑过、没选的做法

| 做法 | 为什么没选 |
|---|---|
| 推送路由用 Java DSL 写一条 `RouterFunction` | 能做到同样的事，但路由会分成「配置里的」和「代码里的」两处，排序也要另外处理；按 scheme 注册处理器是框架自带 `lb://` 用的同一个扩展点 |
| 包一层 `ClientHttpRequestFactory`，按下游路径选池子 | 「哪个路径用哪个池」藏在客户端里、和路由配置脱节，换路径时容易漏改 |
| 推送连接池满时排队 | 见 7.2：白等 45 秒再失败，不如立刻回退轮询 |
| 推送路由改用 JDK HTTP 客户端（它的输入流有多少给多少，Gateway 原本的「读一块刷一次」就成立） | JDK 客户端的超时是「从发请求到收到响应头」的总时长，响应开始后没有空闲超时，通知服务卡死时网关这条连接永远不断；连接数也要另外用信号量限。网关从 JDK 换到 Reactor 正是为了空闲超时（网关 WebMVC spec 第 6 节） |
| 把 `streaming-buffer-size` 设成 1 | 全局配置，所有事件流类型都按字节刷，每个字节一个 HTTP 分块；只改推送路由的拷贝更准 |
| 在 AFTER_COMMIT 的原线程里同步推 | 推送的慢和错会落到 RabbitMQ 消费线程和「标已读」请求上；原线程查库还会碰到已提交事务的残留资源 |
| `@ApplicationModuleListener`（Modulith 的「提交后、异步」） | 本服务的登记表触发注解就是它（决策 C），用它会把每次推送都记进 `event_publication` 并在重启后补推过期的数字 |
| 另起定时器数 30 分钟 | 见 6.4：异步超时本身就是这个定时器 |
| 每条连接一个心跳定时器 | 一个全局 25 秒的轮次更简单，效果一样（最长静默 25 秒） |
| 连接表放 adapter | adapter 的分包白名单里没有放这种组件的包，且 app 的监听器调不到 adapter |
| 事件里带 `id:`、支持 `Last-Event-ID` | 连上先推当前值就够了（决策 I） |

## 13. 拍板记录

本会话按授权自定、需要用户读时留意的几条：

1. 路径 `GET /me/notifications/stream`，事件名 `unread-count`，首条带 `retry: 3000`。
2. 网关对推送路径要求登录（7.3），通知服务自己再判一次。
3. 推送池上限 500、不排队，满了 503（7.2）。
4. 寿命就是异步超时（6.4）；停机前主动结束所有连接（6.6）。
5. 推送换线程、读数放在每用户的锁里（6.3）。
6. 响应头带 `Cache-Control: no-cache, no-transform`（6.2）。
7. release spec 决策 I 里连接池默认值的说法有误（2.4），交编排会话更正。
8. 推送路由逐字节读、按事件刷（7.5）；网关停机时主动结束推送流（7.6）。
