# 响应已提交后出错时中止连接工程设计

> 状态：已实施（2026-10-24）；实施中的偏离见第 11 节
> 日期：2026-10-24
> Issue：暂无。由 PAP-110（catalog 引用导出）引出，修复点在公共模块 `patra-tool-web`，超出 PAP-110 的范围
> 依据：catalog 工程 spec `docs/patra/specs/2026-10-24-catalog-citation-data-export-design.md` §6.2；网关工程 spec `docs/patra/specs/2026-10-09-gateway-webmvc-design.md` §8 第 9 条
> 计划：`docs/patra/plans/2026-10-24-committed-response-abort.md`

## 1. 要解决的问题

PAP-110 新增的 `POST /portal/publications/export` 用 `StreamingResponseBody` 流式写出最多 5,000 篇文献。只要写出的字节超过 Tomcat 的响应缓冲（默认 8 KB），状态行和响应头就已经发给了客户端（响应「已提交」）。如果之后出错（比如某一批查询失败），用户拿到的是一个**截断的文件，状态码却是 200，而且分块传输正常结束**。浏览器会把它当作下载成功，用户手里是一个少了后半截、看起来却完好的 RIS / BibTeX / CSV 文件。

本设计要做到：**响应已提交后再出错时，客户端一定能识别出这次传输失败**，不会拿到一个看起来完整的 200。

## 2. 现状与根因（2026-10-24 实测）

### 2.1 现在走的路径

`GlobalRestExceptionHandler` 有两个入口，处理响应已提交的方式相同，都是「放弃、返回 null」：

| 入口 | 接住的异常 | 已提交时的做法 |
|---|---|---|
| 本类 `handleException`（`@ExceptionHandler(Exception.class)`） | 所有业务异常、`IOException` 等 | `prepareForProblemDetail` 返回 false → 打一行 WARN → `return null` |
| 父类 `ResponseEntityExceptionHandler.handleExceptionInternal` | Spring MVC 自带异常，其中 `HttpMessageNotWritableException`、`AsyncRequestTimeoutException` 会在写出过程中发生 | Spring 原生实现：WARN「Response already committed. Ignoring」→ `return null` |

`@ExceptionHandler` 返回 null 时，`HttpEntityMethodProcessor` 把请求标记为「已处理」，`ExceptionHandlerExceptionResolver` 返回一个空的 `ModelAndView`，Spring MVC 认为异常**已经解决**。分派正常结束，Tomcat 照常写出分块终止块 `0\r\n\r\n`，客户端看到的是一次正常结束的 200。

### 2.2 实测结果

用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 起真实 Tomcat 11.0.26，客户端用 JDK `HttpClient` 和裸 socket。实验代码已删除，计划里的 Red 测试会照着重写：

| 场景 | 现在的结果 | 改成「已提交时原样抛回」后的结果 |
|---|---|---|
| `StreamingResponseBody` 写出 3 × 32 KB 后抛异常 | 200，正文 98 KB，以 `0\r\n\r\n` 结束；JDK 客户端正常返回 | JDK 客户端抛 `IOException: chunked transfer encoding, state: READING_LENGTH`；没有终止块，连接被关闭 |
| 控制器同步写 `HttpServletResponse` 并 flush 后抛异常（网关代理就是这种形态） | 200，正文完整结束 | 同上：`IOException`，没有终止块 |
| `StreamingResponseBody` 只写几十字节、flush 后抛异常 | 422 ProblemDetail（响应还没提交） | 不变 |

还有一个顺带的发现：`StreamingResponseBody.writeTo` 拿到的流是 Spring 包过的 `StreamUtils$NonFlushingOutputStream`，**`flush()` 什么也不做**。字节要等 Tomcat 缓冲写满才发出去，响应也是在那一刻才提交的。所以「每批之后 flush」在 catalog 里实际不起作用；不过 500 条记录远超 8 KB，第一批写完响应就已经提交了。

## 3. 业界做法

1. **Servlet 容器的约定：已提交后的错误交给容器中止连接。** Tomcat 的 `StandardHostValve.custom()` 在响应已提交时，先 include 错误页（源码注释：「including the error page is the best we can do」），再执行 `ActionCode.CLOSE_NOW`（注释：「close immediately as an additional signal to the client that something went wrong」）。连接一断就没有分块终止块，HTTP/1.1 客户端据此判定传输不完整；HTTP/2 下发的是 `RST_STREAM`。浏览器下载会显示「失败 - 网络错误」，`fetch` 读正文时 reject。
2. **Spring MVC 自己不吞异常。** 没有兜底的 `@ExceptionHandler(Exception.class)` 时，`StreamingResponseBody` 里抛出的异常在异步分派里找不到解析器，最后传到容器，结果就是上面的中止。截断 200 是**兜底处理器把异常吞掉**造成的：Patra 的全局处理器，以及 Spring 的 `handleExceptionInternal`，都只打一行「已提交、忽略」的日志。
3. **在 `@ExceptionHandler` 里原样抛出**，就是告诉 Spring「这个异常我不处理」：`ExceptionHandlerExceptionResolver` 发现抛出的是原始异常，不打 `Failure in @ExceptionHandler` 告警，返回 null，异常继续向外传播。仓库里已有同样的写法：`SecurityExceptionRethrowAdvice` 就是这样把安全异常还给安全过滤器的。

评估过、没采用的做法：

| 做法 | 不采用的原因 |
|---|---|
| catalog 先把整个文件写进内存，写完才提交 | 内存占用由 catalog 决定，不属于本修复；而且只救得了导出这一个接口，网关那一跳和其他流式接口照样会截断成 200 |
| 用 HTTP trailer 带上出错信号 | 浏览器下载不认 trailer |
| 先发 `Content-Length`，靠长度不符暴露截断 | 流式写出时事先不知道总长度 |
| 处理器里直接调 Tomcat 内部 API 关连接 | 让工具模块绑死 Tomcat 内部类；而原样抛回已经会触发 `CLOSE_NOW` |

## 4. 方案

**响应已提交时，全局处理器原样抛回原始异常，交给容器中止连接；响应未提交时行为不变。**

### 4.1 `GlobalRestExceptionHandler`

- `handleException`：签名加上 `throws Exception`。`prepareForProblemDetail` 返回 false（已提交）时，先打日志，再 `throw ex`。未提交的路径不变：先 `resetBuffer`、清掉 `Content-Length`，再渲染 ProblemDetail。
- 父类 `handleExceptionInternal`（Spring MVC 自带异常的出口）**不改**，原因见 §10。
- `renderThroughAdapter` 负责的三个覆写（`handleNoResourceFoundException`、`handleNoHandlerFoundException`、`handleMaxUploadSizeExceededException`）**不改**：这几个异常都发生在请求映射或 multipart 解析阶段，响应不可能已经提交；而且前两个是受检的 `ServletException`，父类签名抛不出去。现有的「已提交返回 null」保护保留，作为不可能分支的兜底。
- 类注释和方法 Javadoc 里「返回 null 交给容器收尾」的说法改成「原样抛回，由容器中止连接」。

### 4.2 网关

- `GatewayProxyFailureAdvice.handleProxyFailure` 直接返回 `globalHandler.handleException(...)` 的结果，签名要跟着加 `throws Exception`，否则编译不过。
- 别的不动。已提交时它交给全局处理器的是包装后的异常，不是原始异常，所以 Spring 会多打一条 `Failure in @ExceptionHandler` 告警，但原始异常照样传到容器、连接照样被中止。这条路径很少走到：网关 spec §8 第 9 条实测过，响应体转发到一半断开时，网关侧拿到的是裸 `IOException`，直接进全局处理器，不经过这个 advice。（我的判断，见第 9 节第 3 条）

### 4.3 日志

全局处理器自己保留一行 WARN、不带堆栈，文案是「响应已提交，交给容器中断连接」，带上请求路径和异常类型。这样日志里能看出这次中止是「已提交后出错」，而不是普通的 500。（我的判断，见第 9 节第 3 条）

每次中止，日志里实际会出现这几条（2026-10-24 实测，都去不掉）：

- 全局处理器那一行 WARN。
- Tomcat 带堆栈的 ERROR（`Servlet.service() for servlet [dispatcherServlet] ... threw exception`）。异步分派（`StreamingResponseBody`）会打两条，同步写出打一条。
- 响应的 `Content-Type` 是 Boot 错误控制器写不出的类型时（text/plain、text/event-stream、CSV、RIS、PDF 等），还会多一条 WARN「Response already committed. Ignoring: HttpMessageNotWritableException: No converter for [class java.util.LinkedHashMap] with preset Content-Type '…'」。它来自 Tomcat include `/error` 时错误控制器写不出 JSON、被父类 `ResponseEntityExceptionHandler` 忽略。logger 名同样是 `GlobalRestExceptionHandler`（父类按 `getClass()` 取 logger）。这是中止过程的副产物，不是另一个缺陷。

用户中途取消下载（客户端断开）时，全局处理器照样抛回，但**不打**这行 WARN：Spring 的 `ExceptionHandlerExceptionResolver` 会认出客户端断开、安静处理，异常到不了容器（§5.3）。

## 5. 改动后的行为

### 5.1 三跳链路

catalog 导出的完整链路是：浏览器 → 门户 → 网关 → catalog。

1. **catalog**：某一批写出失败 → 异常进入全局处理器 → 已提交 → 抛回 → Tomcat 中止与网关之间的连接。
2. **网关**：网关的 `RestClient`（Reactor Netty）读下游正文时拿到 `IOException`。此时网关对外的响应也已经提交（已转发的字节超过 8 KB）→ 同一个 starter 的全局处理器同样抛回 → 网关中止与门户（或浏览器）之间的连接。**只修 catalog 一端不够：网关用的是同一个 `patra-tool-web`，所以这一处修复两跳都覆盖到了。**
3. **门户**：现有的文件代理 `proxyGatewayFile` 把 `upstream.body` 原样交给 `new Response(...)`。上游中止时这个流会出错，Next 应该随之中止对浏览器的响应。但导出的门户接口（PAP-114 / 115）还没落地，**这一跳在门户导出接入时核对**（release spec D6 的手工导出顺带验证），本设计不做断言。

### 5.2 客户端看到的两种形态

Tomcat 关连接之前，不论同步还是异步分派，都会先把 Boot 的 `/error` 输出 include 到已提交的响应里，然后才 `CLOSE_NOW`（`StandardHostValve.custom()` 只看响应是否已提交）。尾巴有没有，取决于响应的 `Content-Type` 能不能写出 Boot 的错误 JSON：

- **写不出来**（text/plain、text/event-stream、CSV、RIS、BibTeX、PDF 等）：已写出的字节之后连接直接关闭，没有尾巴，日志多一条 WARN（§4.3）。
- **写得出来**（没设 `Content-Type`、`application/json`、CSL-JSON 这种 `+json`）：正文后面多出约 90 字节 JSON（`{"timestamp":…,"status":200,"error":"OK","path":…}`），然后连接关闭。

这段尾巴**可以接受**：任何合规客户端都会因为缺少终止块而判定传输失败，这份半截文件本来就不会被当作成功的下载。所以不为抹掉它去改 `ErrorMvcAutoConfiguration` 或错误控制器。测试只断言「客户端判定失败」，不断言正文内容。

### 5.3 客户端主动断开保持安静

用户中途取消下载时，写出抛 `ClientAbortException`，进全局处理器后同样被抛回。`ExceptionHandlerExceptionResolver` 接住抛回的异常后，先用 `DisconnectedClientHelper` 判断是不是客户端断开：是的话直接当作已处理，只打低级别日志，不会进容器错误页，也不会刷 ERROR。这同时绕开了 spring-framework#32042 那种「客户端断开 → 处理器再次分派 → 循环」的问题。

### 5.4 影响面

这次改动是全局的，不止影响导出。凡是「已提交后出错」的响应，都从「干净截断的 200」变成「连接被中止」：

| 接口 | 写出方式 | 以前 | 以后 |
|---|---|---|---|
| catalog 引用导出 | `StreamingResponseBody` | 截断 200，看起来完整 | 中止，浏览器下载失败 |
| catalog 全文 PDF / 正文图片 | `InputStreamResource` 同步写，MinIO 中途失败 | 截断 200 | 中止 |
| identity 头像 | `InputStreamResource` 同步写 | 截断 200 | 中止 |
| 网关转发的流式响应 / 大文件，下游中途停住 | 代理同步写 | 截断 200（PAP-69 有意的选择） | 中止 |

**这推翻了网关 spec（2026-10-09）§8 第 9 条的结论。** 当时比较的是「把 ProblemDetail 拼在半截正文后面」和「干净地截断」两种做法，选了后者，没有考虑中止连接。那份 spec 属于已完成的版本，不改；本设计取代它的这条结论，同时改网关 README 的失败场景表和对应的集成测试（第 7 节）。

## 6. 测试

都按 TDD 先写失败测试。会写到 8 KB 之后的场景，必须用真实 Tomcat（`RANDOM_PORT`）加真实客户端：MockMvc 和 `RestTestClient` 看不到 TCP 层面的中止。

断言写法：先用裸 socket 读到状态行 `HTTP/1.1 200`，证明响应头已经发出；再断言正文里**没有**分块终止块 `0\r\n\r\n`，并且 JDK `HttpClient.send(..., ofString())` 抛 `IOException`。正文必须写到 8 KB 以上（用 32 KB 块），否则 `NonFlushingOutputStream` 让响应根本不提交，走的是未提交的路径。

| # | 位置 | 场景 | 现在（Red） | 期望 |
|---|---|---|---|---|
| 1 | tool-web integrationTest | `StreamingResponseBody` 写 > 8 KB 后抛异常 | 完整的 200 | 200 状态行 + 无终止块 + JDK 客户端抛 `IOException` |
| 2 | tool-web integrationTest | 控制器同步写响应 > 8 KB 后抛异常 | 完整的 200 | 同上 |
| 4 | tool-web integrationTest | `StreamingResponseBody` 只写几十字节就抛异常（响应未提交） | ProblemDetail | 不变：完整的 ProblemDetail（防回归） |
| 5 | tool-web 单测 `GlobalRestExceptionHandlerTest` | `shouldSkipRenderingWhenResponseIsCommitted` | `isNull()` | 改成断言抛出的是同一个异常（`isSameAs(ex)`），且不调用适配器 |
| 7 | 网关 integrationTest `GatewayStalledDownstreamIT` | `should_end_committed_streaming_response_cleanly_when_downstream_stalls` | 断言 200 + `x+` | 改名为 `should_abort_committed_streaming_response_when_downstream_stalls`，断言 JDK 客户端抛 `IOException` |
| 8 | 网关 integrationTest `GatewayStalledDownstreamIT` | 新增 `should_abort_committed_response_when_downstream_aborts_mid_body`：下游写 32 KB 后中途断开 | 完整的 200 | JDK 客户端抛 `IOException` |
| 9 | tool-web 单测 | 新增 `should_rethrow_without_warn_when_client_disconnected_after_commit`：已提交 + 客户端断开 | 打 WARN | 照样抛回，不打 WARN |

`父类 404 在已提交时不再渲染` 这条单测保持不变（见 4.1）。

tool-web 的 integrationTest 类路径会被 `patra-tool-test` 带进 JPA、Flyway、Hikari，用 `RANDOM_PORT` 起完整上下文时会因为没有数据源而失败。在 `src/integrationTest/resources/application.yml` 里用 `spring.autoconfigure.exclude` 排除数据源、Hibernate、Flyway 三个自动配置，不改构建脚本。现有的 `@WebMvcTest` 本来就不加载这三个，不受影响。

## 7. 文档同步

- `patra-tool-web` 的 README 没有写已提交时的行为（2026-10-24 核对），不需要改。
- `patra-api/patra-gateway-boot/README.md` 失败场景表第 104 行：「同上，响应已提交 | 200，半截响应体」改成「连接中止（没有分块终止块），客户端报传输不完整」。
- catalog 工程 spec §6.2：不在这里改，交给 catalog 会话（见第 8 节）。

## 8. 交给 catalog 会话的事项

写进共享笔记目录，不在本设计里改 catalog 的 spec：

- §6.2 和共享笔记里「已知限制：响应头发出后中途失败，客户端拿到截断的 200」，在本修复合入后作废，改成「中途失败时连接被中止，浏览器下载显示失败」。
- §6.2「每批写完即刷出」对 `StreamingResponseBody` 不成立：`TextExportWriter.flush()` 调的 `out.flush()` 落在 `NonFlushingOutputStream` 上，什么也不做，字节靠 Tomcat 8 KB 缓冲写满才发出。不影响正确性，只是表述不准。
- catalog 已配置 `spring.mvc.async.request-timeout: 10m`（9971335c9 核对）。导出超过 10 分钟时抛 `AsyncRequestTimeoutException`；这类 Spring MVC 自带异常不在本修复范围内（§10），仍是看起来完整的 200。10 分钟远超 5,000 条的实际耗时，碰不到。
- §6.2 里「导出每批写完即刷出，批间隔远小于 60 秒」这条网关空闲超时的论证，依据的是 flush。flush 本身不起作用，但每批 500 条远超 8 KB，字节照样逐批发出，结论成立。只需把依据改成「每批写满缓冲」。

## 9. 决定

### 9.1 需要用户确认的

1. **网关行为翻转**：网关代理 SSE、大文件时，下游中途停住，客户端以后看到的是「连接被切断」，不再是「干净的 200 + 半截正文」。这推翻了 PAP-69 的选择（第 5.4 节）。本设计按「翻转」写。
2. **Spring MVC 自带异常不纳入修复（实施中实测后改变）**：最初的版本默认「纳入」，打算覆写 `handleExceptionInternal`。实测发现 Spring 有两层都会忽略已提交的这类异常，要覆盖就得替换 Spring 的基础解析器，所以改成不纳入，详见 §10。代价：大 JSON 写到一半失败、异步请求超时这两种情况，仍是看起来完整的 200。
3. **日志**：全局处理器自己保留一行 WARN、不带堆栈，Tomcat 那条带堆栈的 ERROR 照常输出（第 4.3 节）。另一种做法是删掉自己这行，只留 Tomcat 的。

### 9.2 我的判断（可以推翻）

- 修复只在全局处理器做：已提交时原样抛回，不调用 Tomcat 内部 API，也不在 catalog 里先缓冲整份文件。
- `renderThroughAdapter` 的三个覆写不改（第 4.1 节）。
- 网关 advice 只补 `throws Exception`；已提交时多出的那条告警可以接受（第 4.2 节）。
- 异步路径 include 进来的 `/error` 尾巴可以接受，不去处理（第 5.2 节）。
- integrationTest 排除数据源的方式：用 `application.yml`，不改构建脚本（第 6 节）。

## 10. 不做的事

- 不改 catalog 导出的写法（缓冲大小、批次、异步超时都不动）。
- 不改门户。门户那一跳在导出接入时核对（第 5.1 节）。
- 不处理异步路径 include 进来的错误页尾巴。
- 不改网关 2026-10-09 的 spec 原文。
- **不处理 Spring MVC 自带异常在已提交后的情况**（`HttpMessageNotWritableException`：大 JSON 序列化写到一半失败；`AsyncRequestTimeoutException`：异步请求超时）。2026-10-24 实施时实测：
  - 第一层：`ResponseEntityExceptionHandler.handleExceptionInternal` 在已提交时打 WARN、返回 null。
  - 第二层：即使覆写这一层、把异常抛回去，紧接着的 `DefaultHandlerExceptionResolver` 也会再次忽略它。`handleHttpMessageNotWritable` 与 ErrorResponse 系共用的 `handleErrorResponse`，在已提交时都是「Ignoring exception, response committed already」，返回空的 `ModelAndView`。
  - 所以要做到中止，必须用 `WebMvcConfigurer.extendHandlerExceptionResolvers` 把 Spring 的这个基础解析器换成子类，这违背「主流做法优先、不改框架基础组件」。
  - 实际风险低：Patra 返回 JSON 的接口都是 DTO / record，序列化到一半抛异常几乎不会发生；catalog 导出的异步超时是 10 分钟，远超 5,000 条的实际耗时。
  - 接受的代价要说清楚：大 JSON 写到一半失败时，Jackson 会把出错那项写成 `{}` 并自动闭合数组，客户端拿到的是**语法合法、但少数据的 200**，比坏 JSON 更隐蔽。
  - 哪天真要做，正路是子类化 `DefaultHandlerExceptionResolver` 换进解析器链（Spring 在 `handleHttpMessageNotWritable` 的 Javadoc 里写了 the exception "could be rethrown as-is"），而不是再改全局处理器。

## 11. 实施记录（2026-10-24）

- **Spring MVC 自带异常不纳入（推翻 §9.1 第 2 项最初的默认值）**：实施中实测发现 `DefaultHandlerExceptionResolver` 会再次忽略已提交的 MVC 异常，原计划的 Task 2 整体撤销。原因与代价见 §10。
- **日志方法分开**：`renderThroughAdapter` 仍走「已提交返回 null」的兜底，沿用旧方法 `logResponseAlreadyCommitted`（文案「无法再写 ProblemDetail」）；抛回路径用新方法 `logAbortingCommittedResponse`（文案「响应已提交，交给容器中断连接」）。
- **网关实测**：两种场景下，异常都直接进入全局处理器，不经过 `GatewayProxyFailureAdvice`，也没有出现 `Failure in @ExceptionHandler` 告警。下游停住时，异常是 `ServletException` 包着 Netty 的 `ReadTimeoutException`；下游写到一半断开时，异常是 `ServletException` 包着 `ReactiveException(PrematureCloseException)`。后者没有被 `DisconnectedClientHelper` 误认成客户端断开，网关照样中止连接。由新增的 `GatewayStalledDownstreamIT.should_abort_committed_response_when_downstream_aborts_mid_body` 钉住，去掉修复时这条用例失败。
- **最终评审后的修复**：客户端断开时不打 WARN（单测 `should_rethrow_without_warn_when_client_disconnected_after_commit`）；补上「下游中途断开 → 网关中止」的 IT；改正 §4.3、§5.2 里与实测不符的描述。
