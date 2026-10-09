# gateway 鉴权 实施计划（PAP-65）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 网关按会话令牌查 Redis 会话并续期，按路径规则放行或拒绝，通过后把网关签的身份断言写进转发请求；外部请求到不了各服务的 `/_internal/**`、`/admin/**`、actuator。

**Architecture:** 两条 Spring Security 过滤器链：第一条只匹配拒绝名单、对所有人回 403；第二条用 `AuthenticationFilter` 加自己的 `SessionTokenAuthenticationConverter` 查会话，`/patra-identity/**` 默认需登录、公开名单放行、其余路由全放行。出站改头用 Gateway MVC 的 `RequestHttpHeadersFilter` Bean：一个剥外部转发头，一个剥外部 `Authorization` 并写入 `IdentityAssertionSigner` 现签的断言。私钥按字符串绑定、建签名器时解析并自签自验。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Security 7.0.7、Spring Cloud Gateway Server WebMVC 5.0.3、Spring Data Redis 4.0 + Lettuce 6.8.2、Nimbus JOSE + JWT（经 `spring-security-oauth2-jose`）、WireMock 3.10、Testcontainers（`RedisContainerInitializer`）、JUnit 5 + AssertJ + Mockito、`RestTestClient`。

**Spec:** `docs/patra/specs/2026-10-09-gateway-auth-design.md`

## Global Constraints

- 拒绝名单 `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` 统一 403 `GW-0403`，detail `Access denied`，不查 Redis、不带 `WWW-Authenticate`（spec 第 3、6.1 节）。
- `/patra-identity/**` 默认需登录；公开名单只有 `/patra-identity/auth/register`、`/auth/login`、`/auth/logout`、`/v3/api-docs`、`/v3/api-docs/**`；catalog、registry、ingest 全放行（spec 第 6.2 节）。
- 令牌不是「恰好一个 `Bearer` 头 + 会话令牌格式 + Redis 里有」的一律按匿名；`SessionStoreUnavailableException` → `AuthenticationServiceException`（503）；其他运行时异常 → `SessionLookupFailedException`（500）（spec 第 7.1 节）。
- 出站：剥外部 `Authorization` 和 `X-Forwarded-*` / `Forwarded`，已登录写入现签断言；两个过滤器 order 分别为 -200、-100，返回新建的可变 `HttpHeaders`（spec 第 8 节）。
- 私钥配置项 `patra.gateway.identity-assertion.private-key` 只按字符串绑定，记录构造器不校验；校验、解析、自检都在 `GatewaySecurityConfiguration.createSigner`；错误信息只出现 `kid`，不出现密钥内容（spec 第 9 节）。
- Redis：`spring.data.redis.connect-timeout: 5s`、`timeout: 2s`；dev 地址 `${GATEWAY_REDIS_URL:redis://${PATRA_INFRA_HOST:127.0.0.1}:16379}`，container `${GATEWAY_REDIS_URL}`（spec 第 7.2 节）。
- 错误码由 `HttpStdErrors.Group` 生成，不手写字符串；`SecurityServletAutoConfiguration` 全部保留，不排除。
- 密钥不进仓库：测试用 `TestSigningKey` 现生成；本地运行用 `generateIdentityAssertionKey` 生成、环境变量注入。
- 代码规范：Google Java Format（spotless）、`///` Javadoc 用 Markdown、禁止全类名、测试方法 snake_case、禁止反射做白盒测试（`.claude/rules/code-style.md`、`.claude/rules/testing/conventions.md`）。
- 提交：`type(scope): 中文主题 (PAP-65)`，subject 用中文起头；正文每行不超过 100 字符；末尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。改了 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。
- Gradle 命令都在主检出根目录跑；`check` 不含 `integrationTest`，两个都要跑；集成测试需要本机 Docker（Redis 容器）。

## Review Focus

1. 一条用网关公钥能验的断言从外面直接送进来（泄露后被重放）：下游必须拿不到它，当匿名 → Task 6 `should_strip_external_authorization_that_is_not_a_session_token`。
2. `%5Finternal` 这类编码过的拒绝路径：解码后同样 403，不能经编码绕过 → Task 5 `should_block_encoded_and_bare_internal_paths`。
3. `bearer` 小写、令牌两边带空白：仍识别为会话令牌 → Task 4 `should_accept_lowercase_scheme_and_surrounding_whitespace`。
4. 网关自己的 `/actuator/health` 不带服务前缀，compose 健康检查要照常拿到 200 → Task 5 `should_keep_the_gateway_own_actuator_reachable`。
5. 客户端伪造 `X-Forwarded-Host` / `Prefix` / `For`、`Forwarded`：下游只能看到网关写的值 → Task 6 `should_replace_client_supplied_forwarded_headers_with_gateway_values`。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/TransientRedisFailures.java` | 连接层 `RedisException` 归入暂时失败 | 1 |
| `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/TransientRedisFailuresTest.java` | 新规则的用例 | 1 |
| `patra-api/patra-identity/patra-identity-session/README.md` | 判定规则一句话 | 1 |
| `patra-api/patra-gateway-boot/build.gradle.kts` | 加 security starter、session 模块、testFixtures | 2 |
| `patra-api/patra-gateway-boot/src/main/resources/application.yml` | identity 路由、Scalar 源、Redis 超时 | 2 |
| `patra-api/patra-gateway-boot/src/main/resources/application-dev.yml` / `application-container.yml` | Redis 地址、两个密钥占位 | 2 |
| `spotbugs-exclude.xml` | `gateway.security` 包排除 EI_EXPOSE_REP | 2 |
| `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java` | 会话存储 Bean（2）、签名器与自检（3）、映射与头过滤器 Bean（4、6）、两条链（5） | 2 → 6 |
| `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayITSigningKeyInitializer.java` | 测试私钥写进配置 | 2 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java` | 会话存储、签名器、两条链的断言 | 2、3、5 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/GatewayRoutingIT.java`、`GatewayFailureIT.java` | 挂 Redis 与密钥初始化器；删「Authorization 透传」用例 | 2 |
| `.../main/java/dev/linqibin/patra/gateway/security/GatewayIdentityAssertionProperties.java` | 私钥配置项 | 3 |
| `.../test/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfigurationTest.java` | `createSigner` 四种失败与一种成功 | 3 |
| `.../main/java/dev/linqibin/patra/gateway/security/SessionTokenAuthenticationConverter.java` | 取令牌、查会话、建认证 | 4 |
| `.../main/java/dev/linqibin/patra/gateway/security/SessionLookupFailedException.java` | 非暂时失败 → 500 | 4 |
| `.../main/java/dev/linqibin/patra/gateway/security/GatewaySecurityErrorMappingContributor.java` | 加一行映射 | 4 |
| `.../test/java/dev/linqibin/patra/gateway/security/SessionTokenAuthenticationConverterTest.java`、`GatewaySecurityErrorMappingContributorTest.java` | 单元测试 | 4 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/GatewayITSessions.java` | 集成测试建会话的帮助方法 | 5 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/GatewayBlockedPathsIT.java` | 拒绝名单 | 5 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/GatewayAuthenticationIT.java` | 认证、规则（5）；断言与出站头（6） | 5、6 |
| `.../main/java/dev/linqibin/patra/gateway/security/IdentityAssertionRequestHeadersFilter.java`、`ExternalForwardedHeadersFilter.java` | 出站改头 | 6 |
| `.../test/java/dev/linqibin/patra/gateway/security/IdentityAssertionRequestHeadersFilterTest.java`、`ExternalForwardedHeadersFilterTest.java` | 单元测试 | 6 |
| `.../integrationTest/java/dev/linqibin/patra/gateway/GatewayRedisUnavailableIT.java` | Redis 不可用 → 503 | 7 |
| `docs/patra/specs/2026-10-09-gateway-auth-design.md` 第 14 节 | 实测结果 | 8 |
| `patra-api/patra-gateway-boot/README.md`、`patra-infra/cd/module-graph.json`、Linear PAP-65 / PAP-66 | 文档、模块图、Issue | 9 |

---

### Task 1: 会话模块：连接层的 RedisException 归入暂时失败

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/TransientRedisFailures.java`
- Modify: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/TransientRedisFailuresTest.java`
- Modify: `patra-api/patra-identity/patra-identity-session/README.md`（第 54 到 56 行那段）

**Interfaces:**
- Consumes: 现有 `TransientRedisFailures.isTransient(RuntimeException)`；Spring Data Redis 把 Lettuce 的 `RedisException` 包成 `RedisSystemException`，原因是 Lettuce 异常本身。
- Produces: `isTransient` 对「原因是 `io.lettuce.core.RedisException` 且不是 `RedisCommandExecutionException`」返回 `true`。网关的转换器（Task 4）靠 `RedisSessionStore` 用它把这类失败转成 `SessionStoreUnavailableException`。

- [ ] **Step 1: 写失败的测试**

在 `TransientRedisFailuresTest` 里加一个用例，并补两个 import（`io.lettuce.core.RedisCommandInterruptedException`、`io.lettuce.core.RedisException`）：

```java
  @Test
  @DisplayName("连接层的 RedisException（连接关闭、命令被中断）是暂时失败")
  void should_treat_connection_level_redis_exceptions_as_transient() {
    assertThat(
            TransientRedisFailures.isTransient(
                new RedisSystemException("Redis exception", new RedisException("Connection closed"))))
        .isTrue();
    assertThat(
            TransientRedisFailures.isTransient(
                new RedisSystemException(
                    "Redis command interrupted",
                    new RedisCommandInterruptedException(new InterruptedException()))))
        .isTrue();
  }
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*TransientRedisFailuresTest*"`
Expected: FAIL，`should_treat_connection_level_redis_exceptions_as_transient` 的第一个断言 `Expecting value to be true but was false`；`should_not_treat_defects_as_transient` 仍 PASS。

- [ ] **Step 3: 改判定**

`TransientRedisFailures.isTransient` 的 `RedisSystemException` 分支改成：

```java
    Throwable cause = failure.getCause();
    if (cause instanceof RedisLoadingException
        || cause instanceof RedisReadOnlyException
        || cause instanceof RedisBusyException) {
      return true;
    }
    if (cause instanceof RedisCommandExecutionException) {
      // 服务端回了错误：除了 MASTERDOWN，其余（NOAUTH、WRONGTYPE、NOSCRIPT）是缺陷或配置错
      return cause.getMessage() != null && cause.getMessage().startsWith(MASTER_DOWN_PREFIX);
    }
    // 其余直接继承 RedisException 的都是连接层的事：连接关闭、命令被中断
    return cause instanceof RedisException;
```

加 import `io.lettuce.core.RedisCommandExecutionException`、`io.lettuce.core.RedisException`。类 Javadoc 第二段改成：

```java
/// 暂时失败：连不上、超时，Redis 处于 `LOADING`、`READONLY`、`BUSY`、`MASTERDOWN` 状态，
/// 以及连接层的 `RedisException`（Redis 重启瞬间在途命令的「Connection closed」、命令被中断）。
/// 其余服务端错误回复（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，不算暂时，调用方原样抛出成 500。
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test`
Expected: PASS，`TransientRedisFailuresTest` 5 个用例全绿（含原有的 NOSCRIPT / WRONGTYPE / NOAUTH 仍为 `false`）。

- [ ] **Step 5: README 补一句**

`patra-api/patra-identity/patra-identity-session/README.md` 第 54 到 56 行那段改成：

```markdown
Redis 连不上、超时、`LOADING` / `READONLY` / `BUSY` / `MASTERDOWN`，以及连接层的 `RedisException`
（Redis 重启瞬间在途命令的「Connection closed」、命令被中断）转成
`SessionStoreUnavailableException`（`DEP_UNAVAILABLE`，503）。判定在 `TransientRedisFailures`，
identity 的登录限流和网关的查会话都用它。其他服务端错误回复（脚本写错、`WRONGTYPE`、`NOAUTH`）
是缺陷或配置错，原样抛出。
```

- [ ] **Step 6: 提交**

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -F - <<'MSG'
fix(identity): Redis 连接层异常归入会话存储的暂时失败 (PAP-65)

Lettuce 直接继承 RedisException 的异常（Connection closed、命令被中断）被 Spring 包成
RedisSystemException 后原来判成 500；现在除 RedisCommandExecutionException（服务端错误回复）
之外都算暂时失败，identity 的登录限流和网关的查会话一起受益。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 2: 网关接入安全 starter 与会话模块，集成测试带上 Redis 与测试密钥

**Files:**
- Modify: `patra-api/patra-gateway-boot/build.gradle.kts`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application-container.yml`
- Modify: `spotbugs-exclude.xml`（第 378 到 385 行那段之后）
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayITSigningKeyInitializer.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayRoutingIT.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayFailureIT.java`

**Interfaces:**
- Consumes: `RedisSessionStore(StringRedisTemplate, Clock)`（会话模块）；starter-core 自动配置的 `Clock` Bean；`TestSigningKey.key()`（security starter 的 testFixtures）返回含私钥的 `ECKey`；`RedisContainerInitializer`（starter-test）。
- Produces: 网关 classpath 上有 Spring Security 与会话模块；Bean `redisSessionStore`；集成测试上下文里 `patra.gateway.identity-assertion.private-key` 是测试私钥、`patra.security.identity-assertion.public-keys` 是测试公钥（后者由 testFixtures 的 `TestIdentityAssertionEnvironmentPostProcessor` 自动注入）；类 `GatewaySecurityConfiguration`（后续任务往里加 Bean）；集成测试类都挂 `@ContextConfiguration(initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})`。

- [ ] **Step 1: 加依赖**

`patra-api/patra-gateway-boot/build.gradle.kts` 的 `dependencies` 里，`libs.springdoc.openapi.scalar` 那行之后、`testImplementation(...)` 之前加：

```kotlin
    // 鉴权：Spring Security、无状态默认配置、ProblemDetail 写出器、验签器、签名器、认证对象
    implementation(project(":patra-starters:patra-spring-boot-starter-security"))

    // 会话存储契约（RedisSessionStore、SessionToken）；它用 api 带进 spring-boot-starter-data-redis
    implementation(project(":patra-api:patra-identity:patra-identity-session"))
```

`testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))` 之后加：

```kotlin
    // 测试里的签名密钥、自动注入测试公钥的环境后处理器、spring-boot-security-test
    testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
```

文件头的注释块第三行改成 `* API 网关 - Spring Cloud Gateway（WebMVC 版，servlet 栈）+ Spring Security 鉴权`。提醒用户在 IDEA 里点「加载 Gradle 更改」。

- [ ] **Step 2: 改配置**

`application.yml`：

1. 文件头注释的 `# 设计：` 行下面加一行 `# 鉴权：docs/patra/specs/2026-10-09-gateway-auth-design.md`。
2. `spring.cloud` 块之前（`spring.http` 块之后）加：

```yaml
  # 会话存储：地址按 profile 给（dev 默认本机 16379，container 由环境变量注入）。
  # 命令超时 2 秒：带令牌的请求每个都要等这一跳，Redis 卡住时几秒内变成 503；建连耗时另计。
  data:
    redis:
      connect-timeout: 5s
      timeout: 2s
```

3. 路由列表最后（catalog 路由之后）加第四条：

```yaml
            # Identity Service Route（注册、登录、登出、当前用户）
            # Example: /patra-identity/auth/me -> lb://patra-identity/auth/me
            - id: patra-identity
              uri: lb://patra-identity
              predicates:
                - Path=/patra-identity/**
              filters:
                - StripPrefix=1
```

4. `scalar.sources` 加：

```yaml
    - url: /patra-identity/v3/api-docs
      title: Identity Service
      slug: identity
```

`application-dev.yml`：`spring.cloud` 块之后加 `data`，文件末尾加 `patra`：

```yaml
  data:
    redis:
      url: ${GATEWAY_REDIS_URL:redis://${PATRA_INFRA_HOST:127.0.0.1}:16379}
```

```yaml
patra:
  gateway:
    identity-assertion:
      # 含私钥的 EC P-256 JWK JSON，带 kid。本地先用 generateIdentityAssertionKey 生成，见 README；没配启动失败
      private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
  security:
    identity-assertion:
      # 网关自己那把公钥（JWK Set JSON）：安全 starter 的验签器和启动自检用
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

`application-container.yml` 整个替换成：

```yaml
# ============================================================================
# Patra API Gateway - Container Deployment Configuration
# ============================================================================
# 容器部署：Redis 地址和两把密钥都从环境变量读，没有默认值。变量名由 PAP-66 定稿。
# ============================================================================

spring:
  config:
    activate:
      on-profile: container
  data:
    redis:
      url: ${GATEWAY_REDIS_URL}

patra:
  gateway:
    identity-assertion:
      private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
  security:
    identity-assertion:
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

- [ ] **Step 3: SpotBugs 排除**

`spotbugs-exclude.xml` 第 385 行（gateway.error 那个 `</Match>`）之后加：

```xml

  <!-- Gateway 鉴权：配置类、转换器、头过滤器持有注入的会话存储、签名器、错误码组是设计意图 -->
  <Match>
    <Package name="~dev\.linqibin\.patra\.gateway\.security(\..*)?"/>
    <Or>
      <Bug pattern="EI_EXPOSE_REP"/>
      <Bug pattern="EI_EXPOSE_REP2"/>
    </Or>
  </Match>
```

- [ ] **Step 4: 测试密钥初始化器，三个现有 IT 挂上 Redis 与密钥**

新建 `src/integrationTest/java/dev/linqibin/patra/gateway/GatewayITSigningKeyInitializer.java`：

```java
package dev.linqibin.patra.gateway;

import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.util.Map;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// 把测试私钥写进网关的私钥配置项。
///
/// 公钥由安全 starter 测试支持的环境后处理器自动注入，两边是同一对密钥，启动自检才能过。
/// 用法：`@ContextConfiguration(initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})`。
public class GatewayITSigningKeyInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  /// 私钥配置项的全名。
  static final String PRIVATE_KEY_PROPERTY = "patra.gateway.identity-assertion.private-key";

  /// 写入私钥 JWK JSON。用 `Map` 重载：JSON 里有冒号，字符串重载会在第一个分隔符处切开。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    TestPropertyValues.of(Map.of(PRIVATE_KEY_PROPERTY, TestSigningKey.key().toJSONString()))
        .applyTo(applicationContext.getEnvironment());
  }
}
```

`PatraGatewayApplicationIT`、`GatewayRoutingIT`、`GatewayFailureIT` 三个类的注解都加：

```java
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
```

并加 import `dev.linqibin.starter.test.container.initializer.RedisContainerInitializer`、`org.springframework.test.context.ContextConfiguration`。

`GatewayRoutingIT`：删掉 `should_forward_authorization_header_unchanged` 整个方法（设计反转了它：外部 `Authorization` 从此到不了下游，Task 5、6 的用例取代它）；类 Javadoc 里「`Authorization` 透传、」四个字删掉。

- [ ] **Step 5: 写失败的集成测试**

`PatraGatewayApplicationIT` 加字段和用例：

```java
  @Autowired private StringRedisTemplate redis;
```

```java
  @Test
  void should_have_the_session_store_ready() {
    assertThat(context.getBean(RedisSessionStore.class)).isNotNull();
    assertThat(redis.getRequiredConnectionFactory().getConnection().ping()).isEqualTo("PONG");
  }
```

import：`dev.linqibin.patra.identity.session.RedisSessionStore`、`org.springframework.data.redis.core.StringRedisTemplate`。

- [ ] **Step 6: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*PatraGatewayApplicationIT*"`
Expected: FAIL，`should_have_the_session_store_ready` 抛 `NoSuchBeanDefinitionException: No qualifying bean of type 'dev.linqibin.patra.identity.session.RedisSessionStore'`；其余三个用例 PASS（Redis 容器已起、测试公钥已注入、starter 的默认过滤器链全放行）。

- [ ] **Step 7: 配置类，先只放会话存储**

新建 `src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`：

```java
package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/// 网关鉴权的装配：会话存储、签名器、两条过滤器链、出站头过滤器。
///
/// 设计：`docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfiguration {

  /// Redis 里的会话存储，和 identity 共用同一份契约；网关只查和续期。
  ///
  /// @param redis Redis 模板，Boot 按 `spring.data.redis.*` 装配
  /// @param clock 容器里的时钟（starter-core 提供）
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }
}
```

- [ ] **Step 8: 跑全部集成测试与单元测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test :patra-api:patra-gateway-boot:integrationTest`
Expected: PASS；`PatraGatewayApplicationIT` 4 个、`GatewayRoutingIT` 11 个、`GatewayFailureIT` 6 个全绿。日志里出现 `Redis 容器已启动`。

- [ ] **Step 9: 提交**

```bash
git add patra-api/patra-gateway-boot spotbugs-exclude.xml
git commit -F - <<'MSG'
feat(gateway): 接入安全 starter 与会话模块，集成测试带上 Redis 与测试密钥 (PAP-65)

identity 路由与 Scalar 源、Redis 超时与各 profile 的地址、两把密钥的占位；RedisSessionStore Bean。
集成测试挂 Redis 容器和测试私钥初始化器；「Authorization 原样透传」用例按设计删除，
后续任务用剥头与写断言的用例取代。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 3: 私钥配置项、签名器 Bean 与启动自检

**Files:**
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewayIdentityAssertionProperties.java`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfigurationTest.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java`

**Interfaces:**
- Consumes: `IdentityAssertionSigner(ECKey, Clock)`（构造器检查含私钥、带 `kid`、P-256，不满足抛 `IllegalArgumentException`）、`IdentityAssertionSigner.sign(CurrentUser)`；starter 注册的 Bean `identityAssertionDecoder`（`JwtDecoder`，验不过抛 `JwtException`）；`CurrentUser.of(long, long, AccountType, ClientType)`。
- Produces: 记录 `GatewayIdentityAssertionProperties(String privateKey)`，常量 `PRIVATE_KEY_PROPERTY`；包级静态方法 `GatewaySecurityConfiguration.createSigner(String privateKey, Clock clock, JwtDecoder decoder)` 返回 `IdentityAssertionSigner`，失败抛 `IllegalStateException`；Bean `identityAssertionSigner`（Task 6 的头过滤器用）。

- [ ] **Step 1: 写失败的单元测试**

新建 `src/test/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfigurationTest.java`：

```java
package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/// `createSigner`：四种启动失败和一种成功；错误信息点名配置项、不带密钥内容。
class GatewaySecurityConfigurationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC);
  private static final ECKey KEY = TestSigningKey.key();
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final JwtDecoder decoder =
      IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK);

  @Test
  void should_fail_when_private_key_is_missing() {
    for (String missing : Arrays.asList(null, "", "   ")) {
      assertThatThrownBy(() -> GatewaySecurityConfiguration.createSigner(missing, CLOCK, decoder))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY);
    }
  }

  @Test
  void should_fail_when_value_is_not_a_jwk() {
    assertThatThrownBy(
            () -> GatewaySecurityConfiguration.createSigner("not-a-jwk", CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY)
        .hasMessageContaining("JWK");
  }

  @Test
  void should_fail_when_key_has_no_private_part() {
    String publicOnly = KEY.toPublicJWK().toJSONString();

    assertThatThrownBy(() -> GatewaySecurityConfiguration.createSigner(publicOnly, CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY)
        .hasMessageContaining("私钥");
  }

  @Test
  void should_fail_when_key_does_not_match_configured_public_keys() throws JOSEException {
    ECKey other = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(
            () -> GatewaySecurityConfiguration.createSigner(other.toJSONString(), CLOCK, decoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不配对")
        .hasMessageContaining(other.getKeyID())
        .hasMessageNotContaining(other.getD().toString())
        .hasMessageNotContaining(other.getX().toString());
  }

  @Test
  void should_sign_assertions_the_configured_decoder_accepts() {
    IdentityAssertionSigner signer =
        GatewaySecurityConfiguration.createSigner(KEY.toJSONString(), CLOCK, decoder);

    Jwt jwt = decoder.decode(signer.sign(USER));

    assertThat(IdentityAssertionClaims.toCurrentUser(jwt)).isEqualTo(USER);
    assertThat(jwt.getHeaders()).containsEntry("kid", KEY.getKeyID());
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*GatewaySecurityConfigurationTest*"`
Expected: 编译失败，`cannot find symbol: class GatewayIdentityAssertionProperties` 与 `method createSigner`。

- [ ] **Step 3: 配置项记录**

新建 `src/main/java/dev/linqibin/patra/gateway/security/GatewayIdentityAssertionProperties.java`：

```java
package dev.linqibin.patra.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// 网关签身份断言用的配置，前缀 `patra.gateway.identity-assertion`。
///
/// 只按字符串绑定、构造器不做任何校验：绑定阶段一旦抛异常，Boot 的失败报告会把配置值原样打进
/// 日志，私钥就泄露在日志里了。解析和校验在 `GatewaySecurityConfiguration.createSigner`。
///
/// @param privateKey 含私钥的 EC P-256 JWK JSON，带 `kid`；没配时为 `null`，启动失败
@ConfigurationProperties(prefix = "patra.gateway.identity-assertion")
public record GatewayIdentityAssertionProperties(String privateKey) {

  /// 私钥配置项的全名，用在错误信息里。
  public static final String PRIVATE_KEY_PROPERTY =
      "patra.gateway.identity-assertion.private-key";
}
```

- [ ] **Step 4: 签名器工厂方法与 Bean**

`GatewaySecurityConfiguration` 加注解 `@EnableConfigurationProperties(GatewayIdentityAssertionProperties.class)`、`@Slf4j`，加两个方法：

```java
  /// 身份断言的签名器：解析私钥并自签自验一次，密钥配错在启动时就暴露。
  ///
  /// @param properties 私钥配置
  /// @param clock 容器里的时钟
  /// @param identityAssertionDecoder starter 用网关公钥建的验签器
  /// @return 签名器
  @Bean
  public IdentityAssertionSigner identityAssertionSigner(
      GatewayIdentityAssertionProperties properties,
      Clock clock,
      @Qualifier("identityAssertionDecoder") JwtDecoder identityAssertionDecoder) {
    return createSigner(properties.privateKey(), clock, identityAssertionDecoder);
  }

  /// 解析私钥、构造签名器、自检。
  ///
  /// 错误信息只出现配置项名和 `kid`，不出现密钥内容。
  ///
  /// @param privateKey 配置值，含私钥的 EC P-256 JWK JSON
  /// @param clock 时钟
  /// @param decoder 用网关公钥建的验签器
  /// @return 签名器
  /// @throws IllegalStateException 没配、不是 JWK、密钥不可用、和公钥不配对时
  static IdentityAssertionSigner createSigner(String privateKey, Clock clock, JwtDecoder decoder) {
    String property = GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY;
    if (privateKey == null || privateKey.isBlank()) {
      throw new IllegalStateException(
          "配置项 " + property + " 不能为空：它是网关签身份断言的私钥（含私钥的 EC P-256 JWK JSON）");
    }
    ECKey key;
    try {
      key = ECKey.parse(privateKey);
    } catch (ParseException e) {
      throw new IllegalStateException("配置项 " + property + " 不是合法的 EC JWK JSON", e);
    }
    IdentityAssertionSigner signer;
    try {
      signer = new IdentityAssertionSigner(key, clock);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("配置项 " + property + " 不可用：" + e.getMessage(), e);
    }
    CurrentUser probe = CurrentUser.of(1L, 1L, AccountType.USER, ClientType.WEB);
    try {
      decoder.decode(signer.sign(probe));
    } catch (JwtException e) {
      throw new IllegalStateException(
          "配置项 "
              + property
              + " 的私钥与 patra.security.identity-assertion.public-keys 里的公钥不配对或 kid 不一致（kid="
              + key.getKeyID()
              + "）",
          e);
    }
    log.info("身份断言签名密钥就绪，kid={}", key.getKeyID());
    return signer;
  }
```

import：`com.nimbusds.jose.jwk.ECKey`、`dev.linqibin.patra.common.security.AccountType`、`dev.linqibin.patra.common.security.ClientType`、`dev.linqibin.patra.common.security.CurrentUser`、`dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner`、`java.text.ParseException`、`lombok.extern.slf4j.Slf4j`、`org.springframework.beans.factory.annotation.Qualifier`、`org.springframework.boot.context.properties.EnableConfigurationProperties`、`org.springframework.security.oauth2.jwt.JwtDecoder`、`org.springframework.security.oauth2.jwt.JwtException`。

- [ ] **Step 5: 跑单元测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*GatewaySecurityConfigurationTest*"`
Expected: PASS，5 个用例全绿。

- [ ] **Step 6: 集成测试补断言**

`PatraGatewayApplicationIT` 加：

```java
  @Test
  void should_have_the_assertion_signer_ready() {
    assertThat(context.getBean(IdentityAssertionSigner.class)).isNotNull();
  }
```

import `dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner`。

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*PatraGatewayApplicationIT*"`
Expected: PASS，5 个用例；启动日志出现 `身份断言签名密钥就绪，kid=`。

- [ ] **Step 7: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -F - <<'MSG'
feat(gateway): 私钥配置项与身份断言签名器，启动时自签自验 (PAP-65)

私钥只按字符串绑定，解析和校验放在建签名器的地方，绑定失败报告不会打出密钥；
用 starter 的验签器验一条探针断言，公私钥不成对或 kid 不一致时启动失败，错误信息只带 kid。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 4: 会话令牌认证转换器，暂时失败与缺陷分流

**Files:**
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/SessionTokenAuthenticationConverter.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/SessionLookupFailedException.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityErrorMappingContributor.java`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/security/SessionTokenAuthenticationConverterTest.java`
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/security/GatewaySecurityErrorMappingContributorTest.java`

**Interfaces:**
- Consumes: `RedisSessionStore.findAndTouch(SessionToken) → Optional<StoredSession>`（Redis 暂时不可用抛 `SessionStoreUnavailableException`）；`SessionToken.parse(String) → Optional<SessionToken>`；`StoredSession.toCurrentUser()`；`CurrentUserAuthentication(CurrentUser)`；`SecurityErrorMappingContributor(HttpStdErrors.Group)` 的 `mapException(Throwable) → Optional<ErrorCodeLike>`。
- Produces: `SessionTokenAuthenticationConverter(RedisSessionStore)` 实现 `AuthenticationConverter`，`convert(HttpServletRequest)` 返回 `CurrentUserAuthentication` 或 `null`，抛 `AuthenticationServiceException` / `SessionLookupFailedException`；`SessionLookupFailedException(Throwable cause)` 继承 `AuthenticationServiceException`；Bean `GatewaySecurityErrorMappingContributor`（starter 的同类 Bean 让位）。Task 5 的第二条链用转换器。

- [ ] **Step 1: 写失败的转换器测试**

新建 `src/test/java/dev/linqibin/patra/gateway/security/SessionTokenAuthenticationConverterTest.java`：

```java
package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionStoreUnavailableException;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.identity.session.StoredSession;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import io.lettuce.core.RedisCommandExecutionException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;

/// 设计第 7.1 节那张表逐行：前六行当匿名，后两行分别是 503 与 500 对应的异常。
class SessionTokenAuthenticationConverterTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final RedisSessionStore sessions = mock(RedisSessionStore.class);
  private final SessionTokenAuthenticationConverter converter =
      new SessionTokenAuthenticationConverter(sessions);
  private final SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());

  @Test
  void should_return_null_without_authorization_header() {
    assertThat(converter.convert(request())).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_multiple_authorization_headers() {
    assertThat(converter.convert(request("Bearer " + token.value(), "Bearer " + token.value())))
        .isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_non_bearer_scheme() {
    assertThat(converter.convert(request("Basic dXNlcjpwYXNz"))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_when_bearer_value_is_not_a_session_token() {
    assertThat(converter.convert(request("Bearer not-a-session-token"))).isNull();
    assertThat(converter.convert(request("Bearer eyJhbGciOiJFUzI1NiJ9.e30.c2ln"))).isNull();
    assertThat(converter.convert(request("Bearer"))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_when_session_is_missing() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.empty());

    assertThat(converter.convert(request("Bearer " + token.value()))).isNull();
  }

  @Test
  void should_authenticate_when_session_exists() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.of(storedSession()));

    Authentication authentication = converter.convert(request("Bearer " + token.value()));

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.isAuthenticated()).isTrue();
  }

  @Test
  void should_accept_lowercase_scheme_and_surrounding_whitespace() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.of(storedSession()));

    Authentication authentication = converter.convert(request(" bearer  " + token.value() + " "));

    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  void should_translate_store_unavailable_into_authentication_service_exception() {
    when(sessions.findAndTouch(token))
        .thenThrow(
            new SessionStoreUnavailableException(new RedisConnectionFailureException("refused")));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + token.value())))
        .isInstanceOf(AuthenticationServiceException.class)
        .isNotInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(SessionStoreUnavailableException.class)
        .hasMessageNotContaining(token.value());
  }

  @Test
  void should_wrap_other_store_failures_as_lookup_failed() {
    when(sessions.findAndTouch(token))
        .thenThrow(
            new RedisSystemException(
                "Error in execution",
                new RedisCommandExecutionException("NOAUTH Authentication required.")));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + token.value())))
        .isInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(RedisSystemException.class)
        .hasMessageNotContaining(token.value());
  }

  private static MockHttpServletRequest request(String... authorizations) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/patra-identity/auth/me");
    for (String value : authorizations) {
      request.addHeader(HttpHeaders.AUTHORIZATION, value);
    }
    return request;
  }

  private static StoredSession storedSession() {
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    return StoredSession.builder()
        .userId(USER.userId())
        .sessionId(USER.sessionId())
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(now)
        .lastActiveAt(now)
        .expiresAt(now.plus(Duration.ofDays(180)))
        .build();
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*SessionTokenAuthenticationConverterTest*"`
Expected: 编译失败，`cannot find symbol: class SessionTokenAuthenticationConverter`、`SessionLookupFailedException`。

- [ ] **Step 3: 异常与转换器**

新建 `SessionLookupFailedException.java`：

```java
package dev.linqibin.patra.gateway.security;

import java.io.Serial;
import org.springframework.security.authentication.AuthenticationServiceException;

/// 查会话时的非暂时失败：Redis 的配置错（`NOAUTH`）、键被写坏（`WRONGTYPE`、字段解析不了）。
///
/// 是缺陷不是「稍后再试」，按服务端错误（500）输出，不伪装成 503 或 401。文案固定，不含令牌。
public final class SessionLookupFailedException extends AuthenticationServiceException {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public SessionLookupFailedException(Throwable cause) {
    super("查会话失败", cause);
  }
}
```

新建 `SessionTokenAuthenticationConverter.java`：

```java
package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionStoreUnavailableException;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.identity.session.StoredSession;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把 `Authorization: Bearer` 里的会话令牌变成认证对象。
///
/// 不是「恰好一个 Bearer 头 + 会话令牌格式 + Redis 里有」的一律按匿名返回 `null`，是否放行交给
/// 路径规则；查会话顺手续期。Redis 暂时不可用抛 `AuthenticationServiceException`（503），
/// 其他失败抛 `SessionLookupFailedException`（500）。不打印请求头，异常文案不含令牌。
public final class SessionTokenAuthenticationConverter implements AuthenticationConverter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final RedisSessionStore sessions;

  /// 创建转换器。
  ///
  /// @param sessions 会话存储
  public SessionTokenAuthenticationConverter(RedisSessionStore sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；按匿名处理时返回 `null`
  /// @throws AuthenticationServiceException 会话存储暂时不可用时
  /// @throws SessionLookupFailedException 查会话遇到非暂时失败时
  @Override
  public Authentication convert(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (values.size() != 1) {
      return null;
    }
    String value = values.getFirst().strip();
    if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    Optional<SessionToken> token = SessionToken.parse(value.substring(BEARER_PREFIX.length()).strip());
    if (token.isEmpty()) {
      return null;
    }
    Optional<StoredSession> session;
    try {
      session = sessions.findAndTouch(token.get());
    } catch (SessionStoreUnavailableException e) {
      throw new AuthenticationServiceException("会话存储暂时不可用", e);
    } catch (RuntimeException e) {
      throw new SessionLookupFailedException(e);
    }
    return session.map(found -> new CurrentUserAuthentication(found.toCurrentUser())).orElse(null);
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*SessionTokenAuthenticationConverterTest*"`
Expected: PASS，9 个用例全绿。

- [ ] **Step 5: 写失败的映射测试**

新建 `src/test/java/dev/linqibin/patra/gateway/security/GatewaySecurityErrorMappingContributorTest.java`：

```java
package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/// 在 starter 的映射之上加一行：查会话的非暂时失败是 0500，不是 0503。
class GatewaySecurityErrorMappingContributorTest {

  private final GatewaySecurityErrorMappingContributor contributor =
      new GatewaySecurityErrorMappingContributor(HttpStdErrors.of("GW"));

  @Test
  void should_map_lookup_failure_to_internal_error() {
    assertThat(codeOf(new SessionLookupFailedException(new IllegalStateException("corrupt"))))
        .contains("GW-0500");
  }

  @Test
  void should_keep_the_parent_mappings() {
    assertThat(codeOf(new AuthenticationServiceException("redis down"))).contains("GW-0503");
    assertThat(codeOf(new InsufficientAuthenticationException("login"))).contains("GW-0401");
    assertThat(codeOf(new AccessDeniedException("denied"))).contains("GW-0403");
    assertThat(codeOf(new IllegalStateException("other"))).isEmpty();
  }

  private Optional<String> codeOf(Throwable exception) {
    return contributor.mapException(exception).map(ErrorCodeLike::code);
  }
}
```

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*GatewaySecurityErrorMappingContributorTest*"`
Expected: 编译失败，`cannot find symbol: class GatewaySecurityErrorMappingContributor`。

- [ ] **Step 6: 映射类与 Bean**

新建 `GatewaySecurityErrorMappingContributor.java`：

```java
package dev.linqibin.patra.gateway.security;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import java.util.Optional;

/// 网关的安全异常映射：在 starter 的表上加一行，`SessionLookupFailedException` → 0500。
///
/// 声明成 Bean 后 starter 那个 `@ConditionalOnMissingBean` 的让位，不存在两个映射谁先谁后的问题。
public final class GatewaySecurityErrorMappingContributor extends SecurityErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 创建映射。
  ///
  /// @param http 按网关前缀生成错误码的组
  public GatewaySecurityErrorMappingContributor(HttpStdErrors.Group http) {
    super(http);
    this.http = http;
  }

  /// 先判网关自己的异常，其余交给 starter 的表。
  ///
  /// @param exception 要映射的异常
  /// @return 错误码；不是安全异常时返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof SessionLookupFailedException) {
      return Optional.of(http.INTERNAL_ERROR());
    }
    return super.mapException(exception);
  }
}
```

`GatewaySecurityConfiguration` 加 Bean：

```java
  /// 安全异常到错误码的映射：starter 的表加一行查会话失败 → 0500。starter 的同类 Bean 随之让位。
  ///
  /// @param http 按网关前缀生成错误码的组
  /// @return 映射
  @Bean
  public GatewaySecurityErrorMappingContributor gatewaySecurityErrorMappingContributor(
      HttpStdErrors.Group http) {
    return new GatewaySecurityErrorMappingContributor(http);
  }
```

import `dev.linqibin.commons.error.codes.HttpStdErrors`。

- [ ] **Step 7: 跑单元测试与集成测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test :patra-api:patra-gateway-boot:integrationTest`
Expected: PASS；单元测试新增 11 个全绿；集成测试不变（容器里只剩网关这一个 `SecurityErrorMappingContributor` Bean，上下文能起）。

- [ ] **Step 8: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -F - <<'MSG'
feat(gateway): 会话令牌认证转换器，暂时失败与缺陷分流 (PAP-65)

恰好一个 Bearer 头、会话令牌格式、Redis 里有，三者缺一按匿名；查会话顺手续期。
Redis 暂时不可用包成 AuthenticationServiceException（503），其他失败包成
SessionLookupFailedException 并在 starter 的映射上加一行到 0500。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 5: 两条安全过滤器链：拒绝名单 403，identity 默认需登录

**Files:**
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayITSessions.java`
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayBlockedPathsIT.java`
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayAuthenticationIT.java`（本任务放认证与规则的用例，Task 6 再加断言与出站头的用例）
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java`

**Interfaces:**
- Consumes: `SessionTokenAuthenticationConverter(RedisSessionStore)`（Task 4）；`StatelessSecurityDefaults.apply(HttpSecurity, SecurityProblemWriter)`；`SecurityProblemWriter.handle(HttpServletRequest, HttpServletResponse, AccessDeniedException)`（输出 403）；`RedisSessionStore.create(NewSession) → IssuedSession`、`.delete(AccountType, long, long) → boolean`；`SessionToken.generate(AccountType, SecureRandom)`、`.hash()`、`.value()`。
- Produces: 常量 `GatewaySecurityConfiguration.BLOCKED_PATHS`、`IDENTITY_PUBLIC_PATHS`、`IDENTITY_PATHS`；Bean `blockedPathsFilterChain`（`@Order(1)`）、`gatewayFilterChain`（`@Order(2)`）；测试帮助类 `GatewayITSessions.session(userId, sessionId)`（返回 `NewSession.NewSessionBuilder`，180 天绝对期、30 天不活跃）、`GatewayITSessions.issue(store, userId, sessionId)`（返回令牌字符串）。Task 6 往 `GatewayAuthenticationIT` 里加用例。

- [ ] **Step 1: 集成测试的建会话帮助类**

新建 `src/integrationTest/java/dev/linqibin/patra/gateway/GatewayITSessions.java`：

```java
package dev.linqibin.patra.gateway;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Duration;
import java.time.Instant;

/// 集成测试里直接往 Redis 写会话、拿令牌：不经过 identity，网关只认 Redis 里有没有。
final class GatewayITSessions {

  /// 不活跃过期，和 identity 的默认配置一样。
  static final Duration IDLE = Duration.ofDays(30);

  /// 绝对过期，和 identity 的默认配置一样。
  static final Duration ABSOLUTE = Duration.ofDays(180);

  /// 工具类，不允许实例化。
  private GatewayITSessions() {}

  /// 一条现在创建、前台网页端的会话，字段可以再改。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 构建器
  static NewSession.NewSessionBuilder session(long userId, long sessionId) {
    Instant now = Instant.now();
    return NewSession.builder()
        .userId(userId)
        .sessionId(sessionId)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(now)
        .expiresAt(now.plus(ABSOLUTE))
        .idleTimeout(IDLE)
        .maxSessionsPerUser(10);
  }

  /// 建会话并返回令牌原文。
  ///
  /// @param sessions 会话存储
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 令牌，形如 `patra_user_…`
  static String issue(RedisSessionStore sessions, long userId, long sessionId) {
    return sessions.create(session(userId, sessionId).build()).token().value();
  }
}
```

- [ ] **Step 2: 写失败的拒绝名单测试**

新建 `src/integrationTest/java/dev/linqibin/patra/gateway/GatewayBlockedPathsIT.java`：

```java
package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 拒绝名单（设计第 6.1 节）：`/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` 对任何人 403、
/// 下游收不到请求；网关自己的 actuator 不受影响；畸形路径由框架防火墙 400。
///
/// registry 这条路由故意不配实例：要是规则没拦住，得到的是 503 而不是 403，一样能分辨。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
class GatewayBlockedPathsIT {

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;
  @Autowired private RedisSessionStore sessions;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    catalog.resetAll();
    identity.resetAll();
  }

  @Test
  void should_answer_403_for_blocked_paths_whether_anonymous_or_logged_in() {
    String token = GatewayITSessions.issue(sessions, 4301L, 9301L);
    List<String> blocked =
        List.of(
            "/patra-registry/_internal/provenances",
            "/patra-identity/admin/users/1/ban",
            "/patra-catalog/actuator/health",
            "/patra-identity/actuator/env");

    for (String path : blocked) {
      expectForbidden(restClient.get().uri(path));
      expectForbidden(
          restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
    expectForbidden(
        restClient
            .post()
            .uri("/patra-identity/admin/users/1/ban")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    catalog.verify(0, anyRequestedFor(anyUrl()));
    identity.verify(0, anyRequestedFor(anyUrl()));
  }

  /// 匹配的是解码后的路径：`%5F` 就是 `_`；`/**` 也匹配零个尾段。
  @Test
  void should_block_encoded_and_bare_internal_paths() {
    for (String path : List.of("/patra-catalog/_internal", "/patra-catalog/%5Finternal/x")) {
      // 用绝对 URI 绕过客户端的模板编码，百分号才能原样发出去
      expectForbidden(restClient.get().uri(URI.create(gatewayUrl(path))));
    }

    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void should_keep_the_gateway_own_actuator_reachable() {
    restClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  /// 双斜杠和编码过的点由 Spring Security 的 `StrictHttpFirewall` 拒绝，到不了规则和路由。
  @Test
  void should_let_the_firewall_reject_abnormal_paths() {
    for (String path : List.of("/patra-catalog//_internal/x", "/patra-catalog/%2e%2e/_internal/x")) {
      restClient
          .get()
          .uri(URI.create(gatewayUrl(path)))
          .exchange()
          .expectStatus()
          .isBadRequest();
    }

    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  private static void expectForbidden(RestTestClient.RequestHeadersSpec<?> request) {
    request
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0403")
        .jsonPath("$.detail")
        .isEqualTo("Access denied");
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
```

- [ ] **Step 3: 写失败的认证与规则测试**

新建 `src/integrationTest/java/dev/linqibin/patra/gateway/GatewayAuthenticationIT.java`（下面的 import 只含本任务用到的，提交时 spotless 会删掉没用到的；Task 6 加用例时再补它们的 import）：

```java
package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 会话认证与路径规则（设计第 6.2、6.3、7.1 节）、出站的断言与头（第 8 节）。
/// identity 和 catalog 都由 WireMock 顶替；会话直接写进 Redis 测试容器。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
class GatewayAuthenticationIT {

  private static final String BEARER = "Bearer ";

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;
  @Autowired private RedisSessionStore sessions;
  @Autowired private StringRedisTemplate redis;

  @Autowired
  @Qualifier("identityAssertionDecoder")
  private JwtDecoder decoder;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    identity.resetAll();
    catalog.resetAll();
  }

  @Test
  void should_let_anonymous_requests_reach_public_routes_without_authorization() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/portal/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/portal/venues")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void should_answer_401_problem_detail_when_anonymous_hits_identity_me() {
    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/patra-identity/auth/me");

    identity.verify(0, getRequestedFor(urlPathEqualTo("/auth/me")));
  }

  @Test
  void should_treat_a_well_formed_unknown_token_as_anonymous() {
    String unknown = SessionToken.generate(AccountType.USER, new SecureRandom()).value();
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + unknown)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401");
    restClient
        .get()
        .uri("/patra-catalog/portal/venues")
        .header(HttpHeaders.AUTHORIZATION, BEARER + unknown)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void should_let_a_valid_session_reach_identity_without_cookies_or_redirects() {
    String token = GatewayITSessions.issue(sessions, 4201L, 9201L);
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{\"userId\":\"4201\"}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectHeader()
        .doesNotExist(HttpHeaders.LOCATION)
        .expectBody()
        .json("{\"userId\":\"4201\"}");

    identity.verify(1, getRequestedFor(urlPathEqualTo("/auth/me")));
  }

  @Test
  void should_reject_the_token_once_the_session_is_deleted_but_still_let_logout_through() {
    String token = GatewayITSessions.issue(sessions, 4203L, 9203L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));
    assertThat(sessions.delete(AccountType.USER, 4203L, 9203L)).isTrue();

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isUnauthorized();
    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/logout")));
  }

  /// 会话模块满 60 秒才写回：建一条最后活跃时间在两分钟前的会话，一次请求后它被写回、TTL 重算，
  /// 且不超过绝对过期剩下的十分钟。
  @Test
  void should_renew_the_session_on_request_without_passing_absolute_expiry() {
    Instant twoMinutesAgo = Instant.now().minus(Duration.ofMinutes(2));
    Duration remaining = Duration.ofMinutes(10);
    NewSession stale =
        GatewayITSessions.session(4204L, 9204L)
            .createdAt(twoMinutesAgo)
            .expiresAt(Instant.now().plus(remaining))
            .build();
    SessionToken token = sessions.create(stale).token();
    String key = "idn:session:user:" + token.hash();
    assertThat(redis.opsForHash().get(key, "last_active_at"))
        .isEqualTo(Long.toString(twoMinutesAgo.toEpochMilli()));
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token.value())
        .exchange()
        .expectStatus()
        .isOk();

    long lastActiveAt = Long.parseLong((String) redis.opsForHash().get(key, "last_active_at"));
    assertThat(lastActiveAt).isGreaterThan(twoMinutesAgo.toEpochMilli());
    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
        .isPositive()
        .isLessThanOrEqualTo(remaining.toMillis());
  }

  @Test
  void should_require_login_for_unlisted_identity_paths_and_let_the_public_list_through() {
    identity.stubFor(any(urlPathMatching("/.*")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(sessions, 4205L, 9205L);

    restClient.get().uri("/patra-identity/devices").exchange().expectStatus().isUnauthorized();
    restClient
        .get()
        .uri("/patra-identity/devices")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    restClient.post().uri("/patra-identity/auth/register").exchange().expectStatus().isOk();
    restClient.post().uri("/patra-identity/auth/login").exchange().expectStatus().isOk();
    restClient.post().uri("/patra-identity/auth/logout").exchange().expectStatus().isOk();
    restClient.get().uri("/patra-identity/v3/api-docs").exchange().expectStatus().isOk();

    identity.verify(1, getRequestedFor(urlPathEqualTo("/devices")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/register")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/login")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/logout")));
    identity.verify(1, getRequestedFor(urlPathEqualTo("/v3/api-docs")));
  }
}
```

- [ ] **Step 4: 跑两个新 IT，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT*" --tests "*GatewayAuthenticationIT*"`
Expected: FAIL。此时生效的还是 starter 的默认过滤器链（全放行、把 Bearer 当断言验）：拒绝名单用例拿到 404 / 503 而不是 403；匿名访问 `/auth/me` 拿到 WireMock 的 404 而不是 401；带会话令牌的请求被 starter 的转换器判成「断言无效」401；只有 `should_keep_the_gateway_own_actuator_reachable`、`should_let_the_firewall_reject_abnormal_paths` 和 `should_let_anonymous_requests_reach_public_routes_without_authorization` PASS。

- [ ] **Step 5: 两条过滤器链**

`GatewaySecurityConfiguration` 加常量和两个 Bean：

```java
  /// 拒绝名单：各服务的内部接口、后台接口、actuator。通配符只放第一段（服务前缀），
  /// 这三组路径在各服务里都挂在根上。
  static final String[] BLOCKED_PATHS = {"/*/_internal/**", "/*/admin/**", "/*/actuator/**"};

  /// identity 上匿名也能访问的路径：注册、登录、登出（幂等，会话已失效也要能到达）、OpenAPI 文档（Scalar 聚合要拉）。
  static final String[] IDENTITY_PUBLIC_PATHS = {
    "/patra-identity/auth/register",
    "/patra-identity/auth/login",
    "/patra-identity/auth/logout",
    "/patra-identity/v3/api-docs",
    "/patra-identity/v3/api-docs/**"
  };

  /// identity 的全部路径，公开名单之外默认需登录。
  static final String IDENTITY_PATHS = "/patra-identity/**";
```

```java
  /// 第一条链：拒绝名单，对任何人 403。
  ///
  /// 链里没有认证过滤器，所有人在这条链上都是匿名，撞上 `denyAll` 时框架走的是未登录入口点；
  /// 把入口点换成拒绝访问的输出，匿名和已登录就得到同一个 403，不读令牌、不查 Redis。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @return 过滤器链
  @Bean
  @Order(1)
  public SecurityFilterChain blockedPathsFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter) {
    http.securityMatcher(BLOCKED_PATHS);
    StatelessSecurityDefaults.apply(http, problemWriter);
    http.exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    (request, response, exception) ->
                        problemWriter.handle(
                            request, response, new AccessDeniedException("拒绝名单内的路径"))))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
    return http.build();
  }

  /// 第二条链：其余全部。从 `Authorization: Bearer` 里的会话令牌建立认证，identity 默认需登录，
  /// 公开名单和其他路由全放行。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param sessions 会话存储
  /// @return 过滤器链
  @Bean
  @Order(2)
  public SecurityFilterChain gatewayFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter, RedisSessionStore sessions) {
    StatelessSecurityDefaults.apply(http, problemWriter);

    // 转换器给出的已经是认证完成的对象，原样返回；不经过 ProviderManager。
    // 必须用显式类型：AuthenticationFilter 的两个构造器对 lambda 有二义性。
    AuthenticationManager passThrough = authentication -> authentication;
    // 不声明成 Bean：否则 Boot 会把它再注册成全局 servlet 过滤器。
    AuthenticationFilter sessionFilter =
        new AuthenticationFilter(passThrough, new SessionTokenAuthenticationConverter(sessions));
    // 默认的成功处理器会回 302 再继续执行过滤器链，换成什么都不做。
    sessionFilter.setSuccessHandler((request, response, authentication) -> {});
    // 默认的失败处理器遇到服务类异常会原样抛出，换成统一的写出器（503 / 500）。
    sessionFilter.setFailureHandler(problemWriter);

    http.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers(IDENTITY_PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(IDENTITY_PATHS)
                    .authenticated()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
```

import：`dev.linqibin.patra.starter.security.config.StatelessSecurityDefaults`、`dev.linqibin.patra.starter.security.error.SecurityProblemWriter`、`jakarta.servlet.DispatcherType`、`org.springframework.core.annotation.Order`、`org.springframework.security.access.AccessDeniedException`、`org.springframework.security.authentication.AuthenticationManager`、`org.springframework.security.config.annotation.web.builders.HttpSecurity`、`org.springframework.security.web.SecurityFilterChain`、`org.springframework.security.web.authentication.AnonymousAuthenticationFilter`、`org.springframework.security.web.authentication.AuthenticationFilter`。

类 Javadoc 补一段：

```java
/// 两条过滤器链：第一条只匹配拒绝名单、对所有人 403；第二条查会话、identity 默认需登录、其余放行。
/// starter 的默认过滤器链因为这里声明了而让位，它的写出器、错误映射、验签器照常生效。
```

- [ ] **Step 6: 跑两个新 IT，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT*" --tests "*GatewayAuthenticationIT*"`
Expected: PASS，4 + 7 个用例全绿。要是 `should_let_the_firewall_reject_abnormal_paths` 的 `//` 那条拿到 403 而不是 400，说明 Tomcat 先把 `//` 合并成 `/` 再给了规则，同样是被拒绝：把该条断言改成 `isIn(400, 403)`，并在 spec 第 14 节第 5 条记下实际状态码；`%2e%2e` 那条必须是 400。

- [ ] **Step 7: 应用 IT 补两条链的断言**

`PatraGatewayApplicationIT` 加：

```java
  @Test
  void should_declare_exactly_two_security_filter_chains() {
    assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(2);
    // starter 的默认链让位
    assertThat(context.containsBean("patraSecurityFilterChain")).isFalse();
  }
```

import `org.springframework.security.web.SecurityFilterChain`。

- [ ] **Step 8: 跑网关全部测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test :patra-api:patra-gateway-boot:integrationTest`
Expected: PASS。`GatewayRoutingIT`、`GatewayFailureIT` 不受影响（它们的路径都在 `anyRequest().permitAll()` 里）；`PatraGatewayApplicationIT` 6 个用例。

- [ ] **Step 9: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -F - <<'MSG'
feat(gateway): 两条安全过滤器链，拒绝名单 403、identity 默认需登录 (PAP-65)

第一条链只匹配 /*/_internal/**、/*/admin/**、/*/actuator/**，没有认证过滤器，入口点改成
拒绝访问的输出，匿名和已登录都是 403 且不查 Redis；第二条链用 AuthenticationFilter 加会话令牌
转换器，identity 除注册、登录、登出、api-docs 外需登录，其他路由全放行。集成测试钉住
401 / 403 的 ProblemDetail、公开名单、失效会话、续期与绝对过期、无 Cookie 无重定向。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 6: 出站剥外部 Authorization 与转发头，已登录写入断言

**Files:**
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/ExternalForwardedHeadersFilter.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/IdentityAssertionRequestHeadersFilter.java`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/security/GatewaySecurityConfiguration.java`
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/security/ExternalForwardedHeadersFilterTest.java`
- Create: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/security/IdentityAssertionRequestHeadersFilterTest.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayAuthenticationIT.java`

**Interfaces:**
- Consumes: Gateway MVC 的 `HttpHeadersFilter.RequestHttpHeadersFilter`（`HttpHeaders apply(HttpHeaders, ServerRequest)`），`ProxyExchangeHandlerFunction` 按 `orderedStream` 收集容器里所有实现、依次调用；框架的 X-Forwarded / Forwarded 过滤器 order 0。`IdentityAssertionSigner.sign(CurrentUser)`（Task 3 的 Bean）；`CurrentUserAuthentication.getPrincipal()`；Spring 7 的 `HttpHeaders.copyOf(HttpHeaders)`（可变副本）、`headerNames()`、`remove(String)`、`setBearerAuth(String)`。
- Produces: 两个 Bean；`ExternalForwardedHeadersFilter.ORDER = -200`、`IdentityAssertionRequestHeadersFilter.ORDER = -100`。

- [ ] **Step 1: 写失败的单元测试（转发头）**

新建 `src/test/java/dev/linqibin/patra/gateway/security/ExternalForwardedHeadersFilterTest.java`：

```java
package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

/// 外部自带的 `X-Forwarded-*` 和 `Forwarded` 到不了下游，其他头原样；排在框架追加自己值的过滤器之前。
class ExternalForwardedHeadersFilterTest {

  private final ExternalForwardedHeadersFilter filter = new ExternalForwardedHeadersFilter();
  private final ServerRequest request = mock(ServerRequest.class);

  @Test
  void should_drop_client_supplied_forwarded_headers_only() {
    HttpHeaders input = new HttpHeaders();
    input.add("X-Forwarded-Host", "evil.example");
    input.add("x-forwarded-prefix", "/evil");
    input.add("X-FORWARDED-FOR", "203.0.113.9");
    input.add("Forwarded", "for=203.0.113.9;host=evil.example");
    input.add("Host", "gateway.example");
    input.add("Accept", "application/json");

    HttpHeaders output = filter.apply(HttpHeaders.readOnlyHttpHeaders(input), request);

    assertThat(output.headerNames())
        .allSatisfy(name -> assertThat(name.toLowerCase()).doesNotStartWith("x-forwarded-"))
        .noneSatisfy(name -> assertThat(name).isEqualToIgnoringCase("Forwarded"));
    assertThat(output.getFirst("Host")).isEqualTo("gateway.example");
    assertThat(output.getFirst("Accept")).isEqualTo("application/json");
    // 传进来的只读视图没被碰
    assertThat(input.getFirst("X-Forwarded-Host")).isEqualTo("evil.example");
  }

  @Test
  void should_run_before_the_framework_forwarded_filters() {
    assertThat(filter.getOrder()).isLessThan(0);
  }
}
```

- [ ] **Step 2: 写失败的单元测试（Authorization 与断言）**

新建 `src/test/java/dev/linqibin/patra/gateway/security/IdentityAssertionRequestHeadersFilterTest.java`：

```java
package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.function.ServerRequest;

/// 出站的 `Authorization`：外部的一律剥掉；已登录时写入现签的断言，匿名什么都不写。
class IdentityAssertionRequestHeadersFilterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC);
  private static final ECKey KEY = TestSigningKey.key();
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final IdentityAssertionRequestHeadersFilter filter =
      new IdentityAssertionRequestHeadersFilter(new IdentityAssertionSigner(KEY, CLOCK));
  private final ServerRequest request = mock(ServerRequest.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_strip_every_external_authorization_header_when_anonymous() {
    HttpHeaders input = new HttpHeaders();
    input.add(HttpHeaders.AUTHORIZATION, "Bearer patra_user_external");
    input.add(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
    input.add("X-Request-Id", "r1");

    HttpHeaders output = filter.apply(HttpHeaders.readOnlyHttpHeaders(input), request);

    assertThat(output.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    assertThat(output.getFirst("X-Request-Id")).isEqualTo("r1");
    assertThat(input.get(HttpHeaders.AUTHORIZATION)).hasSize(2);
  }

  @Test
  void should_write_a_fresh_assertion_for_the_logged_in_user() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(USER));
    HttpHeaders input = new HttpHeaders();
    input.setBearerAuth("patra_user_external");

    HttpHeaders output = filter.apply(input, request);

    String value = output.getFirst(HttpHeaders.AUTHORIZATION);
    assertThat(output.get(HttpHeaders.AUTHORIZATION)).hasSize(1);
    assertThat(value).startsWith("Bearer ").doesNotContain("patra_user_external");
    Jwt jwt =
        IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK)
            .decode(value.substring("Bearer ".length()));
    assertThat(IdentityAssertionClaims.toCurrentUser(jwt)).isEqualTo(USER);
  }

  @Test
  void should_return_a_mutable_copy() {
    HttpHeaders output =
        filter.apply(HttpHeaders.readOnlyHttpHeaders(new HttpHeaders()), request);

    output.add("X-Later", "ok");

    assertThat(output.getFirst("X-Later")).isEqualTo("ok");
  }

  @Test
  void should_run_before_the_framework_forwarded_filters() {
    assertThat(filter.getOrder()).isLessThan(0);
  }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*HeadersFilterTest*"`
Expected: 编译失败，`cannot find symbol: class ExternalForwardedHeadersFilter`、`IdentityAssertionRequestHeadersFilter`。

- [ ] **Step 4: 两个过滤器与 Bean**

新建 `ExternalForwardedHeadersFilter.java`：

```java
package dev.linqibin.patra.gateway.security;

import java.util.List;
import java.util.Locale;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

/// 剥掉外部请求自带的 `X-Forwarded-*` 和 `Forwarded`。
///
/// 网关前面没有任何反向代理，这些头从外面进来一律不合法。排在框架的 X-Forwarded / Forwarded
/// 过滤器（order 0）之前，它们随后在干净的头上追加网关自己的值，下游拿到的转发头只可能是网关写的。
/// 将来网关前面放了反向代理：删掉这个类，把 `trusted-proxies` 改成代理的地址。
public final class ExternalForwardedHeadersFilter
    implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

  /// 排在框架的转发头过滤器之前。
  static final int ORDER = -200;

  private static final String X_FORWARDED_PREFIX = "x-forwarded-";
  private static final String FORWARDED = "forwarded";

  /// 返回去掉转发头的新副本；传进来的是 `ServerRequest` 的只读视图，不能原地改。
  ///
  /// @param input 当前的出站请求头
  /// @param request 当前请求
  /// @return 新的可变请求头
  @Override
  public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
    HttpHeaders output = HttpHeaders.copyOf(input);
    for (String name : List.copyOf(output.headerNames())) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.startsWith(X_FORWARDED_PREFIX) || lower.equals(FORWARDED)) {
        output.remove(name);
      }
    }
    return output;
  }

  /// 顺序。
  ///
  /// @return {@link #ORDER}
  @Override
  public int getOrder() {
    return ORDER;
  }
}
```

新建 `IdentityAssertionRequestHeadersFilter.java`：

```java
package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.Objects;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

/// 出站的 `Authorization`：外部的一律剥掉；已登录时写入网关现签的身份断言，匿名什么都不写。
///
/// 下游收到的 `Authorization` 只可能是网关签的断言，或者没有。每个请求现签、不缓存，
/// 登出和封禁删掉会话后，下一个请求就签不出断言。安全过滤器链放进线程上下文的认证对象，
/// 在 `DispatcherServlet` 调用本过滤器时还在。
public final class IdentityAssertionRequestHeadersFilter
    implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

  /// 排在框架的转发头过滤器之前；和 `Authorization` 无关的过滤器不会再碰这个头。
  static final int ORDER = -100;

  private final IdentityAssertionSigner signer;

  /// 创建过滤器。
  ///
  /// @param signer 用网关私钥构造的签名器
  public IdentityAssertionRequestHeadersFilter(IdentityAssertionSigner signer) {
    this.signer = Objects.requireNonNull(signer, "signer 不能为 null");
  }

  /// 返回换过 `Authorization` 的新副本。
  ///
  /// @param input 当前的出站请求头（只读视图）
  /// @param request 当前请求
  /// @return 新的可变请求头
  @Override
  public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
    HttpHeaders output = HttpHeaders.copyOf(input);
    output.remove(HttpHeaders.AUTHORIZATION);
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication instanceof CurrentUserAuthentication current) {
      output.setBearerAuth(signer.sign(current.getPrincipal()));
    }
    return output;
  }

  /// 顺序。
  ///
  /// @return {@link #ORDER}
  @Override
  public int getOrder() {
    return ORDER;
  }
}
```

`GatewaySecurityConfiguration` 加两个 Bean：

```java
  /// 出站：剥外部自带的转发头，让框架在干净的头上追加网关自己的值。
  ///
  /// @return 过滤器
  @Bean
  public ExternalForwardedHeadersFilter externalForwardedHeadersFilter() {
    return new ExternalForwardedHeadersFilter();
  }

  /// 出站：剥外部 `Authorization`，已登录写入现签的断言。
  ///
  /// @param signer 签名器
  /// @return 过滤器
  @Bean
  public IdentityAssertionRequestHeadersFilter identityAssertionRequestHeadersFilter(
      IdentityAssertionSigner signer) {
    return new IdentityAssertionRequestHeadersFilter(signer);
  }
```

- [ ] **Step 5: 跑单元测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*HeadersFilterTest*"`
Expected: PASS，2 + 4 个用例全绿。

- [ ] **Step 6: 集成测试补断言与出站头的用例**

`GatewayAuthenticationIT` 加五个用例，并补 import：`com.github.tomakehurst.wiremock.verification.LoggedRequest`、`dev.linqibin.patra.common.security.ClientType`、`dev.linqibin.patra.common.security.CurrentUser`、`dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims`、`dev.linqibin.patra.starter.security.test.TestIdentity`、`java.util.List`、`org.springframework.security.oauth2.jwt.Jwt`：

```java
  @Test
  void should_hand_identity_a_signed_assertion_for_the_session_user() {
    String token = GatewayITSessions.issue(sessions, 4211L, 9211L);
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();

    List<LoggedRequest> received = identity.findAll(getRequestedFor(urlPathEqualTo("/auth/me")));
    assertThat(received).hasSize(1);
    String authorization = received.getFirst().getHeader(HttpHeaders.AUTHORIZATION);
    assertThat(authorization).startsWith(BEARER).doesNotContain(token);
    Jwt jwt = decoder.decode(authorization.substring(BEARER.length()));
    assertThat(IdentityAssertionClaims.toCurrentUser(jwt))
        .isEqualTo(CurrentUser.of(4211L, 9211L, AccountType.USER, ClientType.WEB));
  }

  /// `Basic`、随手写的 Bearer、甚至一条用网关公钥能验的断言（泄露后被重放），都到不了下游：
  /// 网关只认会话令牌，其余一律剥掉、当匿名。
  @Test
  void should_strip_external_authorization_that_is_not_a_session_token() {
    catalog.stubFor(get(urlPathEqualTo("/portal/publications")).willReturn(okJson("{}")));
    List<String> external =
        List.of("Basic dXNlcjpwYXNz", BEARER + "not-a-session-token", BEARER + TestIdentity.assertion());

    for (String value : external) {
      restClient
          .get()
          .uri("/patra-catalog/portal/publications")
          .header(HttpHeaders.AUTHORIZATION, value)
          .exchange()
          .expectStatus()
          .isOk();
    }

    List<LoggedRequest> received =
        catalog.findAll(getRequestedFor(urlPathEqualTo("/portal/publications")));
    assertThat(received)
        .hasSize(3)
        .allSatisfy(request -> assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isNull());
  }

  @Test
  void should_treat_multiple_authorization_headers_as_anonymous_and_strip_them_all() {
    String token = GatewayITSessions.issue(sessions, 4212L, 9212L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));

    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(
        postRequestedFor(urlPathEqualTo("/auth/logout")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  /// 会话已失效时拿旧令牌登出：以匿名身份到达 identity，不带任何 `Authorization`，identity 回 204。
  @Test
  void should_let_logout_with_a_dead_token_reach_identity_anonymously() {
    String token = GatewayITSessions.issue(sessions, 4213L, 9213L);
    sessions.delete(AccountType.USER, 4213L, 9213L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));

    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(
        postRequestedFor(urlPathEqualTo("/auth/logout")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void should_replace_client_supplied_forwarded_headers_with_gateway_values() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-catalog/portal/venues")
        .header("X-Forwarded-Host", "evil.example")
        .header("X-Forwarded-Prefix", "/evil")
        .header("X-Forwarded-For", "203.0.113.9")
        .header("X-Forwarded-Proto", "https")
        .header("Forwarded", "for=203.0.113.9;host=evil.example;proto=https")
        .exchange()
        .expectStatus()
        .isOk();

    LoggedRequest received =
        catalog.findAll(getRequestedFor(urlPathEqualTo("/portal/venues"))).getFirst();
    assertThat(received.getHeader("X-Forwarded-Host")).matches("localhost(:\\d+)?");
    assertThat(received.getHeader("X-Forwarded-Prefix")).isEqualTo("/patra-catalog");
    assertThat(received.getHeader("X-Forwarded-Proto")).isEqualTo("http");
    assertThat(received.getHeader("X-Forwarded-For")).doesNotContain("203.0.113.9");
    assertThat(received.getHeader("Forwarded")).doesNotContain("evil").doesNotContain("203.0.113.9");
  }
```

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayAuthenticationIT*"`
Expected: PASS，12 个用例全绿（Task 5 的 7 个加这 5 个）。

- [ ] **Step 7: 跑网关全部测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test :patra-api:patra-gateway-boot:integrationTest`
Expected: PASS。`GatewayRoutingIT.should_send_x_forwarded_headers_to_downstream` 仍然通过：剥的是外部带进来的，框架追加的照旧。

- [ ] **Step 8: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -F - <<'MSG'
feat(gateway): 出站剥外部 Authorization 与转发头，已登录写入断言 (PAP-65)

两个 RequestHttpHeadersFilter Bean 排在框架的转发头过滤器之前：一个剥外部自带的 X-Forwarded-*
和 Forwarded，让框架在干净的头上追加网关自己的值；一个剥外部 Authorization，已登录时写入
用网关私钥现签的 60 秒断言。集成测试钉住下游解出的四个字段、泄露断言重放到不了下游、
多个 Authorization 头当匿名、失效令牌登出以匿名到达、伪造转发头被替换。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 7: Redis 不可用时带令牌的请求返回 503

**Files:**
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayRedisUnavailableIT.java`

**Interfaces:**
- Consumes: Task 4 的转换器把 `SessionStoreUnavailableException` 包成 `AuthenticationServiceException`，Task 5 的第二条链用 `SecurityProblemWriter` 输出 503；`SessionToken.generate(AccountType, SecureRandom)`。
- Produces: 无新接口。这是行为的钉子：公开路由、登出、需登录路径带令牌都是 503，不带令牌不碰 Redis。

- [ ] **Step 1: 写集成测试**

和 identity 的 `RedisUnavailableIT` 同一写法：Redis 指向一个没人监听的端口，不停共享的测试容器，所以这个类**不挂** `RedisContainerInitializer`。新建 `GatewayRedisUnavailableIT.java`：

```java
package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.session.SessionToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// Redis 连不上（设计第 7.1、11 节）：带令牌的请求不管打哪条路由都是 503，不是 401 也不是 500；
/// 不带令牌的请求不碰 Redis，照常放行。
///
/// Redis 指向一个没人监听的端口，不停共享的 Redis 测试容器，所以不挂 `RedisContainerInitializer`。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayRedisUnavailableIT {

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;

  @DynamicPropertySource
  static void unreachableRedisAndDownstreams(DynamicPropertyRegistry registry) {
    int closedPort = closedPort();
    registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + closedPort);
    registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    registry.add("spring.data.redis.timeout", () -> "500ms");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @Test
  void should_answer_503_for_any_route_when_a_token_is_present() {
    String token = SessionToken.generate(AccountType.USER, new SecureRandom()).value();

    expectUnavailable(HttpMethod.GET, "/patra-identity/auth/me", token);
    expectUnavailable(HttpMethod.GET, "/patra-catalog/portal/venues", token);
    expectUnavailable(HttpMethod.POST, "/patra-identity/auth/logout", token);

    identity.verify(0, anyRequestedFor(anyUrl()));
    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void should_not_touch_redis_for_requests_without_a_token() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/portal/venues").exchange().expectStatus().isOk();
  }

  private void expectUnavailable(HttpMethod method, String path, String token) {
    restClient
        .method(method)
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503")
        .jsonPath("$.detail")
        .isEqualTo("Service Unavailable");
  }

  /// 拿一个刚释放、没人监听的端口。
  ///
  /// @return 端口
  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
```

- [ ] **Step 2: 跑测试，确认通过（这是对 Task 4、5 行为的钉子，写完即应通过）**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayRedisUnavailableIT*"`
Expected: PASS，2 个用例。日志里每个 503 各有一条 ERROR（写出器对 5xx 记 ERROR 带堆栈），原因链里是 `RedisConnectionFailureException`。要是拿到的是 500 而不是 503，说明连不上的异常没被 `TransientRedisFailures` 认出来：把日志里的异常类记下来，回 Task 1 的判定里补，不要在网关这边兜。

- [ ] **Step 3: 提交**

```bash
git add patra-api/patra-gateway-boot
git commit -F - <<'MSG'
test(gateway): Redis 不可用时带令牌的请求任何路由都返回 503 (PAP-65)

Redis 指向没人监听的端口：需登录路径、公开路径、登出带令牌都是 GW-0503 的 ProblemDetail，
下游零请求；不带令牌的请求不碰 Redis，照常放行。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 8: 本地实测，结果写回设计文档

**Files:**
- Modify: `docs/patra/specs/2026-10-09-gateway-auth-design.md`（第 14 节）

**Interfaces:**
- Consumes: Task 1 到 7 全部完成；本机 Docker 可用；tailnet 在线（只有第 4 步要）。
- Produces: 第 14 节五条各带「实测：」结果；第 3 步可能回头改 Task 1 的判定。

基础设施容器都在 Mac mini 上，本机不跑 compose；本地实测用两个一次性容器，identity 的 dev 默认值正好指向它们（`127.0.0.1:15432/patra_identity`、`postgres/123456`、`127.0.0.1:16379`）。

- [ ] **Step 1: 一次性的 Redis、PostgreSQL 和一对密钥**

```bash
docker run -d --name patra-redis-local -p 16379:6379 redis:7.0.15
docker run -d --name patra-pg-local -p 15432:5432 -e POSTGRES_PASSWORD=123456 -e POSTGRES_DB=patra_identity postgres:17
K=/private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/5cf40ed0-3140-4e91-bd51-9c11d12cb541/scratchpad/keys
mkdir -p "$K"
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K/private.jwk" > "$K/public.jwks"
```

Expected：两个容器 `docker ps` 可见；`$K/private.jwk` 权限 0600；`$K/public.jwks` 是 `{"keys":[{...}]}`。**不要 `cat`、`echo` 私钥文件和两个环境变量**；两个文件只存在于 scratchpad，实测完 `rm -rf "$K"`。

- [ ] **Step 2: 起 identity 和网关，走一遍注册、查当前用户、登出、再查**

两个终端。关掉 Nacos 的发现与注册，网关用 simple discovery 指到本机 identity：

```bash
export PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS="$(cat "$K/public.jwks")"
./gradlew :patra-api:patra-identity:patra-identity-boot:bootRun --args='--spring.cloud.nacos.discovery.enabled=false --spring.cloud.service-registry.auto-registration.enabled=false'
```

```bash
export PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS="$(cat "$K/public.jwks")"
export PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY="$(cat "$K/private.jwk")"
./gradlew :patra-api:patra-gateway-boot:bootRun --args='--spring.cloud.nacos.discovery.enabled=false --spring.cloud.service-registry.auto-registration.enabled=false --spring.cloud.discovery.client.simple.instances.patra-identity[0].uri=http://127.0.0.1:6400'
```

Expected：identity 日志 `Tomcat started on port 6400`、Flyway 迁移成功；网关日志 `Tomcat started on port 9528` 和 `身份断言签名密钥就绪，kid=`。

然后（令牌只留在 shell 变量里，不写进任何文档）：

```bash
B=http://localhost:9528
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' "$B/patra-identity/auth/me"
curl -s --noproxy '*' -X POST "$B/patra-identity/auth/register" -H 'Content-Type: application/json' -d '{"email":"pap65@example.com","password":"Gateway-Pass-65"}'
T='<上一步响应里的 sessionToken>'
curl -s --noproxy '*' -w '\n%{http_code}\n' -H "Authorization: Bearer $T" "$B/patra-identity/auth/me"
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' -X POST -H "Authorization: Bearer $T" "$B/patra-identity/auth/logout"
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $T" "$B/patra-identity/auth/me"
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' -X POST "$B/patra-identity/admin/users/1/ban"
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' "$B/patra-identity/actuator/health"
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' "$B/actuator/health"
grep -rl 'patra_user_' logs/ patra-api/patra-gateway-boot/logs/ 2>/dev/null; grep -rc '"d":' logs/ patra-api/patra-gateway-boot/logs/ 2>/dev/null | grep -v ':0$'
```

Expected：401 → 201（响应含 `sessionToken`、`userId`、`email`）→ 200（`userId`、`email`、`accountType: user`）→ 204 → 401 → 403 → 403 → 200；最后两条 grep 没有输出（日志里既没有令牌也没有私钥的 `d` 字段）。

- [ ] **Step 3: Redis 重启瞬间**

网关还在跑。再注册一个账号拿到令牌 `T2`，然后一个终端循环打、另一个终端重启 Redis：

```bash
for i in $(seq 1 60); do curl -s --noproxy '*' -o /dev/null -w '%{http_code} ' -H "Authorization: Bearer $T2" "$B/patra-identity/auth/me"; sleep 0.2; done; echo
```

```bash
docker stop patra-redis-local && sleep 3 && docker start patra-redis-local
```

Expected：状态码序列里只有 200 和 503，没有 500；网关日志里 503 对应的异常链是 `AuthenticationServiceException` ← `SessionStoreUnavailableException` ← Spring 的 `RedisConnectionFailureException` / `QueryTimeoutException` / `RedisSystemException(RedisException: Connection closed)`。把实际看到的类名记下来。出现 500 就把它的异常类补进 Task 1 的判定、补用例、重跑 Task 1 的测试，再回到这里。

- [ ] **Step 4: 门户读接口回归（指向 mini 的 Nacos，Redis 用本机）**

停掉第 2 步的网关，按 PAP-69 的办法起一个连 mini 发现服务的网关（Nacos 账号密码从 `.env.common` 导出，不打印）：

```bash
set -a; source <(grep -E '^NACOS_(USERNAME|PASSWORD)=' patra-infra/docker/.env.common); set +a
NP='localhost|127.*|100.*|192.168.*|172.*|10.*'
JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$NP -Dhttps.nonProxyHosts=$NP -Dsocks.nonProxyHosts=$NP" \
PATRA_INFRA_HOST=100.103.73.27 TAILSCALE_IP=100.70.109.37 GATEWAY_REDIS_URL=redis://127.0.0.1:16379 \
SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED=false \
./gradlew :patra-api:patra-gateway-boot:bootRun
```

```bash
PATRA_GATEWAY_BASE_URL=http://localhost:9528 pnpm --dir patra-portal test:e2e --workers=1
```

Expected：Playwright 全部通过（上次是 14/14），门户读接口行为与改动前一致。

- [ ] **Step 5: 配错私钥启动一次**

另生成一对密钥到 `$K/other`，把网关的公钥换成它的、私钥仍用第 1 步的，起网关：

```bash
mkdir -p "$K/other"
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K/other/private.jwk" > "$K/other/public.jwks"
PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS="$(cat "$K/other/public.jwks")" \
PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY="$(cat "$K/private.jwk")" \
./gradlew :patra-api:patra-gateway-boot:bootRun --args='--spring.cloud.nacos.discovery.enabled=false --spring.cloud.service-registry.auto-registration.enabled=false' 2>&1 | tee "$K/wrong-key.log" | grep -E '不配对|kid=' | head -3
grep -c '"d":' "$K/wrong-key.log"
```

Expected：启动失败，提示里有「不配对或 kid 不一致（kid=…）」；最后的 grep 输出 `0`。

- [ ] **Step 6: 收尾与写回**

```bash
docker rm -f patra-redis-local patra-pg-local
rm -rf "$K"
```

spec 第 14 节五条各补一行「实测：」，写观察到的状态码序列、异常类名、日志检查结果、`//` 实际拿到的状态码；第 7.3 节如果因第 3 步改了判定，同步改。

- [ ] **Step 7: 提交**

```bash
git add docs/patra/specs/2026-10-09-gateway-auth-design.md
git commit -F - <<'MSG'
docs(gateway): 网关鉴权本地实测结果写回设计文档 (PAP-65)

注册、查当前用户、登出、再查与拒绝名单的状态码；Redis 重启瞬间的异常形状；门户读接口回归；
配错私钥的启动提示不含密钥；防火墙对畸形路径的实际状态码。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 9: README 重写、模块图、全量门控、Linear

**Files:**
- Modify: `patra-api/patra-gateway-boot/README.md`（整个替换）
- Modify: `patra-infra/cd/module-graph.json`（`dumpModuleGraph` 重新生成）
- Linear：PAP-65、PAP-66 的描述

**Interfaces:**
- Consumes: Task 1 到 8 的全部行为与实测结果。
- Produces: 与实际行为一致的 README；模块图含网关到 security starter、session 模块的边。

- [ ] **Step 1: README 整个替换**

````markdown
# patra-gateway-boot

Patra 的 API 网关：所有外部请求的统一入口，按路径前缀路由到各微服务，并负责**鉴权**：按会话令牌查 Redis 会话并续期，按路径规则放行或拒绝，通过后把网关签的身份断言写进转发请求。Spring Cloud Gateway 的 **WebMVC 版**（servlet 栈）+ Spring Security。

设计：[gateway 切换到 WebMVC 版（PAP-69）](../../docs/patra/specs/2026-10-09-gateway-webmvc-design.md)、[gateway 鉴权（PAP-65）](../../docs/patra/specs/2026-10-09-gateway-auth-design.md)。

## 职责

- **路由**：`/patra-catalog/**`、`/patra-registry/**`、`/patra-ingest/**`、`/patra-identity/**` 剥掉第一段后转给对应服务，经 Nacos 发现、Spring Cloud LoadBalancer 选实例。
- **鉴权**：`Authorization: Bearer <会话令牌>` → 查 Redis 会话（顺手续期）→ 建认证对象；令牌不是「恰好一个 Bearer 头 + 会话令牌格式 + Redis 里有」的一律按匿名，是否放行由下面的路径规则决定。
- **出站请求头**：剥掉外部自带的 `Authorization`、`X-Forwarded-*`、`Forwarded`；已登录时写入网关现签的 60 秒身份断言（`Authorization: Bearer <JWT>`），匿名不写；再由框架追加网关自己的 `X-Forwarded-*` / `Forwarded`，剥逐跳头。下游收到的 `Authorization` 只可能是网关签的断言，或者没有。
- **透传**：下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx / 3xx；不跟随重定向，不替任何一方谈压缩。
- **文档聚合**：`/scalar` 聚合四个服务的 OpenAPI 文档。
- **可观测性**：OTel Agent + Micrometer，actuator 暴露 `health` / `info` / `metrics`。

## 路径规则

规则看到的是剥前缀**之前**的路径，写在 `GatewaySecurityConfiguration` 里，两条过滤器链：

| 顺序 | 路径 | 结果 |
|---|---|---|
| 第一条链 | `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` | 任何人 403 `GW-0403`；不读令牌、不查 Redis。服务之间的直连调用不过网关，不受影响 |
| 第二条链 | `/patra-identity/auth/register`、`/auth/login`、`/auth/logout`、`/v3/api-docs`、`/v3/api-docs/**` | 放行（登出公开是为了幂等：会话已失效也要能到达 identity） |
| 第二条链 | `/patra-identity/**` 其余 | 需登录；匿名 401 `GW-0401` |
| 第二条链 | 其他一切（catalog、registry、ingest、网关自己的 `/actuator/**` 与 `/scalar`） | 放行 |

规则只看路径不看方法。网关自己的 `/actuator/health` 没有服务前缀，compose 健康检查照常。`//`、`%2F`、`;`、`..` 这类路径由 Spring Security 的防火墙以 400 拒绝（框架默认，不是 ProblemDetail）。

## 模块结构

```
patra-gateway-boot/
├── src/main/java/dev/linqibin/patra/gateway/
│   ├── PatraGatewayApplication.java              # 启动类
│   ├── config/GatewayConfiguration.java          # HTTP 客户端不谈压缩
│   ├── error/                                    # 代理失败 → 网关自己的应用异常（503 / 504，固定文案）
│   └── security/
│       ├── GatewaySecurityConfiguration.java     # 会话存储、签名器（启动自检）、两条过滤器链、出站头过滤器、错误映射
│       ├── GatewayIdentityAssertionProperties.java   # patra.gateway.identity-assertion.private-key（只按字符串绑定）
│       ├── SessionTokenAuthenticationConverter.java  # 取令牌、查会话、建认证；503 / 500 分流
│       ├── SessionLookupFailedException.java     # 查会话的非暂时失败 → 500
│       ├── GatewaySecurityErrorMappingContributor.java # starter 的映射表加一行
│       ├── IdentityAssertionRequestHeadersFilter.java  # 出站：剥外部 Authorization，已登录写入断言
│       └── ExternalForwardedHeadersFilter.java   # 出站：剥外部 X-Forwarded-* / Forwarded
├── src/main/resources/
│   ├── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、Redis 超时、错误前缀
│   ├── application-dev.yml                       # dev：Redis 地址、两把密钥的环境变量、DEBUG 日志
│   └── application-container.yml                 # 容器：同一组环境变量，无默认值
└── src/integrationTest/java/dev/linqibin/patra/gateway/
    ├── PatraGatewayApplicationIT.java            # servlet 启动、JDK 客户端、虚拟线程、两条链、签名器
    ├── GatewayRoutingIT.java                     # 转发行为（WireMock 顶替下游）
    ├── GatewayFailureIT.java                     # 代理失败的状态码
    ├── GatewayAuthenticationIT.java              # 认证、规则、断言、出站头
    ├── GatewayBlockedPathsIT.java                # 拒绝名单
    └── GatewayRedisUnavailableIT.java            # Redis 不可用 → 503
```

## 会话与断言

- 会话契约在 `patra-identity-session`（`RedisSessionStore`、`SessionToken`）：网关只查和续期，不配有效期；续期按会话里的 `idle_timeout_ms` 和 `expires_at`，距上次写入满 60 秒才写回。
- 断言格式、签名器、验签器在 `patra-spring-boot-starter-security`：`ES256`、`typ: patra-identity+jwt`、`iss: patra-gateway`、`aud: patra`、60 秒有效期，每个请求现签、不缓存。
- 私钥按字符串绑定，建签名器时才解析；启动时用配置的公钥自签自验一次，公私钥不成对或 `kid` 不一致直接启动失败，错误信息只带 `kid`。
- 网关也要配 `patra.security.identity-assertion.public-keys`（自己那把公钥）：安全 starter 的验签器和启动自检用它，不要为了绕过它排除 starter 的自动配置。

## HTTP 客户端、超时与线程

| 项 | 值 | 说明 |
|---|---|---|
| 客户端 | JDK HttpClient | `spring.http.clients.imperative.factory: jdk`，显式指定 |
| 连接超时 | 5 秒 | `spring.http.clients.connect-timeout` |
| 读超时 | 60 秒 | `spring.http.clients.read-timeout`，从请求发出起算的总时长，流式响应同样受限 |
| 重定向 | 不跟随 | `spring.http.clients.redirects: dont-follow` |
| 压缩 | 关 | `GatewayConfiguration` 关掉 JDK 客户端的透明压缩 |
| 线程 | 虚拟线程 | `spring.threads.virtual.enabled: true` |
| Redis | 连接 5 秒、命令 2 秒 | `spring.data.redis.connect-timeout` / `timeout`。命令超时是单条命令的上限，建连耗时另计 |

## 错误

网关自身产生的错误都是 `application/problem+json`，错误码前缀 `GW`。下游自己的错误原样透传。

| 场景 | 状态码 | 错误码 | detail |
|---|---|---|---|
| 匿名（含无效、过期、不存在的令牌）访问需登录路径 | 401 | `GW-0401` | Authentication required（带 `WWW-Authenticate: Bearer`） |
| 任何人访问拒绝名单 | 403 | `GW-0403` | Access denied |
| 带会话令牌且 Redis 暂时不可用（任何路由） | 503 | `GW-0503` | Service Unavailable |
| 带会话令牌且查会话遇到非暂时失败（`NOAUTH`、键被写坏） | 500 | `GW-0500` | Internal Server Error |
| 未匹配任何路由 | 404 | `GW-0404` | |
| `lb://` 找不到实例；连接被拒、连接超时、域名解析失败 | 503 | `GW-0503` | 下游服务暂时不可用 |
| 响应头到达前读超时 | 504 | `GW-0504` | 下游服务响应超时 |
| 响应头已到、响应体读超时，响应还没提交 | 500 | `GW-0500` | |
| 同上，响应已提交 | 200，半截响应体 | 无 | |
| 防火墙拒绝的畸形路径 | 400 | 无 | 框架默认 |

identity 自己的 `IDN-0401` 仍会出现：带有效断言但用户已封禁的 60 秒窗口里由 identity 回 401。

## 配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile | `dev` |
| `NACOS_HOST` / `NACOS_PORT` / `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos | 跟随 `PATRA_INFRA_HOST`、`8848`、`nacos` / `nacos` |
| `TAILSCALE_IP` | dev 下向 Nacos 注册的 IP | 空 |
| `GATEWAY_REDIS_URL` | 会话所在的 Redis | dev：`redis://${PATRA_INFRA_HOST:127.0.0.1}:16379`；container 无默认值 |
| `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY` | 签断言的私钥，含私钥的 EC P-256 JWK JSON，带 `kid` | 无，缺了启动失败 |
| `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` | 网关自己那把公钥，JWK Set JSON | 无，缺了启动失败 |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |

端口 9528。容器里的变量名由 PAP-66 定稿并注入；**带本版本的网关镜像没有上面三样就起不来**，部署前先把它们放进网关的环境。

本地跑网关前生成一对密钥（私钥给网关，公钥给网关和 identity，用完删掉文件，不要 `cat` 到终端或粘进任何文档）：

```bash
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut=/tmp/gateway-private.jwk > /tmp/gateway-public.jwks
export PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY="$(cat /tmp/gateway-private.jwk)"
export PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS="$(cat /tmp/gateway-public.jwks)"
```

**反向代理**：网关前面现在没有任何反向代理，所以外部自带的 `X-Forwarded-*` / `Forwarded` 一律剥掉（`ExternalForwardedHeadersFilter`）。将来放了反向代理：删掉这个过滤器，把 `spring.cloud.gateway.server.webmvc.trusted-proxies` 从 `.*` 改成代理的地址。

## 测试

```bash
./gradlew :patra-api:patra-gateway-boot:test              # 单元测试
./gradlew :patra-api:patra-gateway-boot:integrationTest   # 集成测试（WireMock 顶替下游，Redis 用 Testcontainers，不连 Nacos）
```

集成测试需要本机 Docker。`test` profile 只关掉 Nacos 的发现与注册；私钥由 `GatewayITSigningKeyInitializer` 写进配置，公钥由安全 starter 测试支持的环境后处理器自动注入；会话由测试直接写进 Redis 容器。

## 技术栈

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.0.8 |
| Spring Security | 7.0.7 |
| Spring Cloud | 2025.1.3（gateway-server-webmvc 5.0.3） |
| Spring Data Redis / Lettuce | 4.0 / 6.8.2 |
| Nacos Discovery | spring-cloud-alibaba 2025.1.0.0 |
| springdoc（webmvc Scalar） | 3.0.1 |
| Java | 25 |
````

- [ ] **Step 2: 模块图**

Run: `./gradlew dumpModuleGraph --no-configuration-cache && git diff --stat patra-infra/cd/module-graph.json`
Expected: `module-graph.json` 有改动，网关条目新增到 `patra-starters/patra-spring-boot-starter-security` 和 `patra-api/patra-identity/patra-identity-session` 的依赖边。

- [ ] **Step 3: 全量门控**

Run: `./gradlew check --no-configuration-cache`
Expected: BUILD SUCCESSFUL（含 spotless、SpotBugs、各模块单元测试）。

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: BUILD SUCCESSFUL；网关 IT 约 40 个用例，会话模块和 identity 的 IT 不受 Task 1 影响。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-gateway-boot/README.md patra-infra/cd/module-graph.json
git commit -F - <<'MSG'
docs(gateway): 重写网关 README，模块图随依赖更新 (PAP-65)

路径规则表、两条链、会话与断言、出站改头、错误表加鉴权四行、三个新环境变量与本地生成密钥的步骤、
反向代理的注意事项；模块图新增网关到 security starter 与 session 模块的边。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

- [ ] **Step 5: Linear**

用 Linear MCP 的 `save_issue` 更新两个 Issue 的描述（不动状态，状态由用户定）：

- PAP-65：To-Do 第一项「Tech Design」后面加「→ `docs/patra/specs/2026-10-09-gateway-auth-design.md`」；描述里加一节：

```markdown
## 工程设计里定下的三件事（2026-10-09）

- 拒绝名单（`/*/_internal/**`、`/*/admin/**`、`/*/actuator/**`）统一 403 `GW-0403`，detail「Access denied」，不查 Redis。
- `/patra-identity/**` 默认需登录，只有注册、登录、登出和 `/v3/api-docs` 公开。
- 外部自带的 `X-Forwarded-*` / `Forwarded` 在网关剥掉，下游只看到网关写的值。
```

  To-Do 按实际完成情况勾掉；AC 留给用户按第 14 节的实测自行勾。

- PAP-66：描述里加一段：

```markdown
## 来自 PAP-65 设计的部署顺序约束（2026-10-09）

带网关鉴权的镜像没有 `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`、`GATEWAY_REDIS_URL` 三样就起不来。mini 上要先把它们放进网关的环境（`.env.gateway` / `.env.gateway.secret`），再让 CD 部署带 PAP-65 的网关镜像，否则 mini 上的网关和门户一起不可用。变量名由本 Issue 定稿，网关的 `application-container.yml` 里是这三个占位。
```

Expected：两个 Issue 的描述更新成功；在最终汇报里提醒用户这条顺序约束。

---

## 执行后的收尾

- 全部任务完成后按 `superpowers:executing-plans` 做最终的全分支评审（最强模型），再走 `superpowers:finishing-a-development-branch`：按本仓库约定本地快进合并到 `main`、保留版本分支、推送等用户开口。
- 向用户汇报时单独点出：PAP-66 必须先在 mini 上放好三样环境变量再合并带本 Issue 的 main，否则 mini 上的网关起不来。
