# 响应已提交后出错时中止连接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（用户已定：会话内联执行，不派 subagent）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 响应已提交后再出错时，`patra-tool-web` 的全局处理器把原始异常抛回容器，由 Tomcat 中止连接，客户端不再拿到一个看起来完整的 200。

**Architecture:** 只改 `GlobalRestExceptionHandler` 的两个入口：本类的 `handleException`，以及覆写父类的 `handleExceptionInternal`。已提交时 `throw ex`，Spring MVC 把它当作未处理，异常传到 Tomcat，`StandardHostValve` 执行 `CLOSE_NOW`。网关用同一个 starter，自动获得同样的行为，只需让 advice 的签名跟上，并把钉住旧行为的 IT 翻过来。

**Tech Stack:** Java 25、Spring Boot 4.0.8 / Spring Framework 7.0.9、Tomcat 11.0.26、JUnit 5 + AssertJ + Mockito、JDK `HttpClient` 与裸 `Socket`（IT 客户端）。

**Spec:** `docs/patra/specs/2026-10-24-committed-response-abort-design.md`

## Global Constraints

- 只有「响应已提交」的分支行为改变；未提交时仍是先 `resetBuffer`、清掉 `Content-Length`，再渲染 ProblemDetail。
- 抛回的必须是**原始异常对象本身**（`throw ex`），不能包装：包装后的异常会让 `ExceptionHandlerExceptionResolver` 多打一条 `Failure in @ExceptionHandler` 告警。
- `renderThroughAdapter` 的三个覆写（404 两个、multipart 超限）不改（spec 4.1）。
- 全局处理器在已提交分支保留一行不带堆栈的 WARN，文案：`响应已提交，交给容器中断连接: request path [{}], exception={}: {}`（spec 4.3）。
- 凡是要写到 8 KB 以上的 IT，都用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 起真实 Tomcat、用真实客户端；每次写 32 KB（`"x".repeat(32 * 1024)`），因为 `StreamingResponseBody` 的 `flush()` 什么也不做。
- 代码规范：所有方法写 `///` Javadoc；测试方法 snake_case；不用全类名；注释、日志用中文。
- 提交：**没有用户明确同意不提交、不推送、不开 PR**。各 Task 末尾不设提交步骤，统一放在 Task 4。commit subject 用中文动词开头（commitlint 拒绝大写开头）。

## Review Focus

1. **客户端主动断开**（用户取消下载 → `ClientAbortException`）：期望被 `DisconnectedClientHelper` 安静地吞掉，不刷 ERROR、不循环分派。没有自动化测试；Task 1 Step 7 跑完 IT 后看日志确认，测试里的 JDK 客户端读到一半关闭连接时不出现循环。
2. **未提交时不回归**：小正文 `StreamingResponseBody` 抛异常时仍是完整的 ProblemDetail，由 Task 1 的 `should_render_problem_detail_when_streaming_fails_before_commit` 钉住。
3. **网关 advice 已提交时抛出的是包装后的异常**：期望客户端照样看到中止，只多一条告警。Task 3 跑网关 IT 时顺带确认日志。
4. （Task 2 撤销，本条作废）
5. **异步路径追加的 `/error` 尾巴**：IT 只断言「没有终止块 + 客户端抛 IOException」，不断言正文内容，避免把这段尾巴写进契约。

---

### Task 1: 全局处理器在已提交时抛回原始异常

**Files:**
- Create: `patra-tool/patra-tool-web/src/integrationTest/java/dev/patra/tool/web/error/probe/CommittedResponseProbeController.java`
- Create: `patra-tool/patra-tool-web/src/integrationTest/java/dev/patra/tool/web/error/CommittedResponseAbortIT.java`
- Modify: `patra-tool/patra-tool-web/src/integrationTest/resources/application.yml`
- Modify: `patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/error/handler/GlobalRestExceptionHandler.java`（`handleException`、`logResponseAlreadyCommitted`、类注释）
- Modify: `patra-tool/patra-tool-web/src/test/java/dev/patra/tool/web/error/handler/GlobalRestExceptionHandlerTest.java`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/error/GatewayProxyFailureAdvice.java`（只改签名）

**Interfaces:**
- Produces: `public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request, HttpServletResponse response) throws Exception`
- Produces（IT 内部，Task 2 复用）：`CommittedResponseAbortIT` 里的两个私有辅助方法 `String rawExchange(String path)`（裸 socket 发 `GET path HTTP/1.1`，读到 EOF、连接异常，或读到 `\r\n0\r\n\r\n` 为止，按 UTF-8 返回已收到的全部字节；`soTimeout` 10 秒）和 `void assertAborted(String path)`（断言见 Step 2）

- [ ] **Step 1: 搭 IT 骨架：排除数据源、写探针控制器**

`application.yml` 在现有 `patra.web.error.context-prefix: TEST` 之外加上：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
      - org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
```

`CommittedResponseProbeController`（`@RestController`，只在测试里存在），三个端点：
- `GET /probe/committed/streaming` → 返回 `StreamingResponseBody`：写 3 次 32 KB 的 `x`，然后抛 `IllegalStateException("写出中途失败")`
- `GET /probe/committed/sync`（参数 `HttpServletResponse response`）→ `response.setContentType("text/plain")`，往 `getOutputStream()` 写 32 KB 的 `x` 并 flush，然后抛 `IllegalStateException("写出中途失败")`
- `GET /probe/committed/streaming-small` → 返回 `StreamingResponseBody`：写 `"partial"`，然后抛 `IllegalStateException("写出中途失败")`

- [ ] **Step 2: 写失败测试 `CommittedResponseAbortIT`**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CommittedResponseProbeController.class)
@DisplayName("响应已提交后出错时中止连接")
class CommittedResponseAbortIT {
  @LocalServerPort private int port;

  @Test
  void should_abort_connection_when_streaming_body_fails_after_commit() throws Exception {
    assertAborted("/probe/committed/streaming");
  }

  @Test
  void should_abort_connection_when_synchronous_write_fails_after_commit() throws Exception {
    assertAborted("/probe/committed/sync");
  }

  @Test
  void should_render_problem_detail_when_streaming_fails_before_commit() throws Exception {
    // JDK HttpClient GET /probe/committed/streaming-small
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.body()).contains("\"code\":\"TEST-0422\"").doesNotContain("partial");
  }
}
```

`assertAborted(path)` 的断言：

```java
String raw = rawExchange(path);
assertThat(raw).startsWith("HTTP/1.1 200");          // 响应头已经发出
assertThat(raw).doesNotContain("\r\n0\r\n\r\n");     // 没有分块终止块
assertThatThrownBy(() -> httpClient.send(
        HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString()))
    .isInstanceOf(IOException.class);                // 客户端判定传输不完整
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :patra-tool:patra-tool-web:integrationTest --tests "*CommittedResponseAbortIT*"`
Expected：两个 `should_abort_*` 失败，失败点是 `doesNotContain("\r\n0\r\n\r\n")`（实测：现在拿到的是以终止块结束的完整 200）；`should_render_problem_detail_*` 通过。如果 `should_abort_*` 的失败点是 `startsWith("HTTP/1.1 200")`，说明响应没有提交，先检查写出的数据量。

- [ ] **Step 4: 改单测，钉住新契约**

`GlobalRestExceptionHandlerTest`：
- `shouldSkipRenderingWhenResponseIsCommitted` 改名为 `shouldRethrowOriginalExceptionWhenResponseIsCommitted`，DisplayName 改为「响应已提交时原样抛回原始异常，由容器中止连接」，断言改成：

```java
RuntimeException ex = new RuntimeException("下游中断");
assertThatThrownBy(() -> handler.handleException(ex, request, committed)).isSameAs(ex);
verifyNoInteractions(problemDetailAdapter);
```
- `handleException` 签名加了 `throws Exception` 之后，其他直接调用它的测试方法也要加 `throws Exception`。
- `shouldSkipNoResourceFoundRenderingWhenResponseIsCommitted` 不动。

Run: `./gradlew :patra-tool:patra-tool-web:test --tests "*GlobalRestExceptionHandlerTest*"`
Expected：编译失败，或新用例失败（还没抛出）。

- [ ] **Step 5: 实现**

`GlobalRestExceptionHandler.handleException`：签名加 `throws Exception`；`!prepareForProblemDetail(response)` 分支先调 `logResponseAlreadyCommitted(request, ex)`，再 `throw ex`。`logResponseAlreadyCommitted` 的文案改成 Global Constraints 里那句。方法 Javadoc（`@return` 和正文里「返回 null 交给容器收尾」）以及类注释同步改掉，加上 `@throws Exception 响应已提交时原样抛回的 ex`。

`GatewayProxyFailureAdvice.handleProxyFailure`：签名加 `throws Exception`，Javadoc 补 `@throws Exception 响应已提交时全局处理器原样抛回的异常`。方法体不改。

- [ ] **Step 6: 跑测试，确认通过**

Run: `./gradlew :patra-tool:patra-tool-web:test :patra-tool:patra-tool-web:integrationTest :patra-api:patra-gateway-boot:compileJava`
Expected：全部 PASS，网关编译通过。

- [ ] **Step 7: 看日志（Review Focus 1）**

在 Step 6 的 IT 输出里确认：每个 `should_abort_*` 用例对应一行 `响应已提交，交给容器中断连接` WARN，加上 Tomcat 的 `Servlet.service() ... threw exception` ERROR；没有 `Failure in @ExceptionHandler`；没有同一请求反复分派的日志。

---

### Task 2: Spring MVC 自带异常在已提交时同样抛回

> **已按 ledger 裁决撤销**（2026-10-24）：`DefaultHandlerExceptionResolver` 会再次忽略已提交的 MVC 异常，见 spec §10。

**Files:**
- Modify: `patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/error/handler/GlobalRestExceptionHandler.java`（新增 `handleExceptionInternal` 覆写）
- Modify: `patra-tool/patra-tool-web/src/integrationTest/java/dev/patra/tool/web/error/probe/CommittedResponseProbeController.java`
- Modify: `patra-tool/patra-tool-web/src/integrationTest/java/dev/patra/tool/web/error/CommittedResponseAbortIT.java`
- Modify: `patra-tool/patra-tool-web/src/test/java/dev/patra/tool/web/error/handler/GlobalRestExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 的 `assertAborted(String path)`
- Produces: `@Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request)`（`body` 和返回值按父类加 `@Nullable`）

- [ ] **Step 1: 探针加大 JSON 端点，写失败 IT**

`GET /probe/committed/json` 返回 `List<ProbeItem>`，共 2,000 项，每项带一个约 40 字符的字符串字段，正文远超 8 KB。`ProbeItem` 是带 getter 的普通类（不用 record）：最后一项的 getter 抛 `IllegalStateException("序列化中途失败")`。Jackson 写到最后一项时失败，Spring 抛 `HttpMessageNotWritableException`，交给父类的 `handleExceptionInternal` 处理。

```java
@Test
void should_abort_connection_when_json_serialization_fails_after_commit() throws Exception {
  assertAborted("/probe/committed/json");
}
```

Run: `./gradlew :patra-tool:patra-tool-web:integrationTest --tests "*CommittedResponseAbortIT*json*"`
Expected：FAIL，失败点是 `doesNotContain("\r\n0\r\n\r\n")`。日志里应能看到父类那行 `Response already committed. Ignoring: ...HttpMessageNotWritableException`。看不到的话，说明异常没走 `handleExceptionInternal`：停下，按实际异常类型调整探针后再继续。

- [ ] **Step 2: 写失败单测**

```java
@Test
@DisplayName("Spring MVC 自带的非受检异常在响应已提交时原样抛回")
void shouldRethrowUncheckedMvcExceptionWhenResponseIsCommitted() {
  // committed 响应；ex = new HttpMessageNotWritableException("写出失败")
  assertThatThrownBy(() -> handler.handleExceptionInternal(
          ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
          new ServletWebRequest(request, committed)))
      .isSameAs(ex);
}

@Test
@DisplayName("受检异常在响应已提交时保持父类的忽略行为")
void shouldIgnoreCheckedMvcExceptionWhenResponseIsCommitted() {
  // ex = new MissingServletRequestParameterException("q", "String")
  assertThat(handler.handleExceptionInternal(
          ex, null, new HttpHeaders(), HttpStatus.BAD_REQUEST,
          new ServletWebRequest(request, committed)))
      .isNull();
}
```

Run: `./gradlew :patra-tool:patra-tool-web:test --tests "*GlobalRestExceptionHandlerTest*"`
Expected：`shouldRethrowUnchecked...` FAIL（现在返回 null）；`shouldIgnoreChecked...` PASS。

- [ ] **Step 3: 实现 `handleExceptionInternal` 覆写**

`extractServletResponse(request)` 不为 null、`isCommitted()` 为真、且 `ex instanceof RuntimeException runtime` 时：调 `logResponseAlreadyCommitted(extractServletRequest(request), ex)`，然后 `throw runtime`。其他情况返回 `super.handleExceptionInternal(...)`。Javadoc 写明：受检异常只会在参数解析阶段出现，那时响应不可能已提交，父类签名也抛不出去。

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-tool:patra-tool-web:test :patra-tool:patra-tool-web:integrationTest`
Expected：全部 PASS（含 `ValidationFailureLeakIT`）。

---

### Task 3: 网关翻转「已提交时干净截断」的契约

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayStalledDownstreamIT.java`
- Modify: `patra-api/patra-gateway-boot/README.md:104`

**Interfaces:**
- Consumes: Task 1 改过的 `handleException`（网关 advice 已在 Task 1 改好签名）

- [ ] **Step 1: 改测试**

`should_end_committed_streaming_response_cleanly_when_downstream_stalls` 改名为 `should_abort_committed_streaming_response_when_downstream_stalls`。方法注释改成：流式响应已提交，下游停住超过读超时后，网关中止与客户端的连接，客户端拿不到正常结束的 200。断言改成：

```java
assertThatThrownBy(() -> client.send(
        HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/stalled-stream"))).build(),
        HttpResponse.BodyHandlers.ofString()))
    .isInstanceOf(IOException.class);
```

另外两个用例不动。

- [ ] **Step 2: 跑测试**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayStalledDownstreamIT*"`
Expected：三个用例都 PASS。Task 1 已经让网关获得新行为，所以改完断言直接是绿的。可以临时把 Step 1 的断言换回旧的 200，确认旧断言现在确实失败（失败点：`send` 抛 `IOException`），然后改回。顺带在日志里确认 Review Focus 3：抛回后没有出现 `Failure in @ExceptionHandler` 之外的异常日志。

- [ ] **Step 3: 改 README 失败场景表**

`README.md:104` 那一行「同上，响应已提交 | 200，半截响应体 | 无 |」改成「同上，响应已提交 | 连接中止（没有分块终止块）| 无 | 客户端报传输不完整；见 `2026-10-24-committed-response-abort-design.md`」，列数保持和表头一致。

---

### Task 4: 全量门控、交接笔记、提交

**Files:**
- Create: `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_c04766c5-6ac7-4771-97aa-718035535462/committed-response-abort.md`
- Modify: `docs/patra/specs/2026-10-24-committed-response-abort-design.md`（状态行）

- [ ] **Step 1: 格式化与全量门控**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected：BUILD SUCCESSFUL。spotless 改了文件的话，一起纳入后面的提交。

- [ ] **Step 2: 写交接笔记**

把 spec 第 8 节的三条写进共享笔记 `committed-response-abort.md`，给 catalog 会话和门户导出会话看，再加一行：门户那一跳在 PAP-114/115 接入时，用一次中途失败的导出核对浏览器显示「下载失败」。

- [ ] **Step 3: 更新 spec 状态行**

把 spec 的状态行改成「已实施（2026-10-24）」；实施中如有偏离，在 spec 末尾补一节「实施记录」。

- [ ] **Step 4: 提交（需用户明确同意）**

向用户报告改动和对 catalog 导出的影响，等用户明确同意后再执行。只暂存本计划涉及的路径（spec、plan、tool-web、gateway 的改动），先核对 `git diff --cached`：

```bash
git add docs/patra/specs/2026-10-24-committed-response-abort-design.md docs/patra/plans/2026-10-24-committed-response-abort.md patra-tool/patra-tool-web patra-api/patra-gateway-boot
git commit -m "fix(tool): 响应已提交后出错时抛回容器中止连接，不再返回截断的 200"
```
