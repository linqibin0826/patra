# gateway 切换到 WebMVC 版 实施计划（PAP-69）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `patra-gateway-boot` 从 Spring Cloud Gateway 的 WebFlux 版换成 WebMVC 版，对外行为不变，下游失败给 503 / 504，网关自身错误输出带错误码的 ProblemDetail。

**Architecture:** 原地替换：模块、三条 YAML 路由、Nacos、LoadBalancer 都保留，只换依赖、配置键、HTTP 客户端和错误输出。网关的 RestClient 由 Boot 按 `spring.http.clients.*` 装配（JDK HttpClient，5 秒连接、60 秒读超时、不跟随重定向），Tomcat 跑虚拟线程。网关自身错误走 starter-web 的全局处理器，新增一个 `ErrorMappingContributor` 把到不了下游和无实例映射成 503 / 504；starter-web 补上 Spring MVC 自带 404 的错误码渲染。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Cloud 2025.1.3（`spring-cloud-gateway-server-webmvc` 5.0.3）、Spring Cloud LoadBalancer、springdoc 3.0.1（webmvc Scalar）、WireMock 3.10（经 `linqibin-spring-boot-starter-test` 传递）、JUnit 5 + AssertJ + Mockito、`RestTestClient`。

**Spec:** `docs/patra/specs/2026-10-09-gateway-webmvc-design.md`

## Global Constraints

- 只换技术栈，不加鉴权、不加熔断重试限流、不碰其他服务、不改 mini 上的 compose 与 `.env.gateway`（spec 第 2 节）。
- 三条路由的 id、`lb://` 地址、`Path` 谓词、`StripPrefix=1` 一字不改（spec 第 5 节）。
- HTTP 客户端只由 `spring.http.clients.imperative.factory: jdk` 决定；不声明任何 `ClientHttpRequestFactory` Bean（WebMVC 版网关会优先用容器里的这个 Bean）。
- 超时：`connect-timeout: 5s`、`read-timeout: 60s`、`redirects: dont-follow`；读超时是总时长，流式响应同样受限（spec 第 6 节）。
- 错误码前缀 `GW`：`GW-0404` / `GW-0503` / `GW-0504`，由 `HttpStdErrors.Group` 生成，不手写字符串。
- 集成测试的 `test` profile 只关 `spring.cloud.nacos.discovery.enabled` 和 `spring.cloud.service-registry.auto-registration.enabled`，`spring.cloud.discovery.enabled` 保持默认开启（spec 第 10 节）。
- 代码规范：Google Java Format（spotless）、`///` Javadoc 用 Markdown、禁止全类名、测试方法 snake_case、禁止反射做白盒测试（`.claude/rules/code-style.md`、`.claude/rules/testing/conventions.md`）。
- 提交：`type(scope): 中文主题 (PAP-69)`，subject 用中文起头；正文每行不超过 100 字符；末尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。改了 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」（`.claude/rules/superpowers.md` 第 5 条）。
- Gradle 命令都在主检出根目录跑：`./gradlew ...`；`check` 不含 `integrationTest`，两个都要跑。

## Review Focus

1. 门户以后的注册、登录是 POST，请求体必须原样到达下游，JSON 一字不差 → Task 4 的 `should_forward_post_body_unchanged`。
2. 检索词里有中文和空格，查询串里的百分号编码不能被网关改写或二次编码 → Task 4 的 `should_keep_encoded_query_string`。
3. 下游回 204 无响应体、或带 `Cache-Control` 等响应头时，客户端拿到的要和直连一样 → Task 4 的 `should_pass_204_and_response_headers_through`。
4. 下游收到的 `Host` 头应是下游自己的地址，否则按虚拟主机分发的下游会错 → Task 4 的 `should_send_downstream_host_header`（实测值写回 spec 第 12 节第 6 条）。
5. 读超时期间持续有数据的流式响应会被切断，客户端拿到的是半截响应 → Task 5 的 `should_cut_streaming_response_at_read_timeout`（钉住 spec 第 6 节接受的限制）。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `gradle/libs.versions.toml` | 别名 `spring-cloud-starter-gateway` 改指 webmvc；删 `springdoc-openapi-webflux-scalar` | 1 |
| `patra-api/patra-gateway-boot/build.gradle.kts` | 依赖换成 webmvc 版、加 starter-web | 1 |
| `patra-api/patra-gateway-boot/src/main/resources/application.yml` | 配置键挪到 `server.webmvc`，HTTP 客户端、超时、虚拟线程、错误前缀 | 1 |
| `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java` | Javadoc 改成 WebMVC 版 | 1 |
| `patra-api/patra-gateway-boot/src/integrationTest/resources/application-test.yml` | 关 Nacos、读超时 1 秒 | 1 |
| `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java` | servlet 启动、JDK 客户端、虚拟线程 | 1 |
| `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributor.java` | 到不了下游 / 无实例 → 503 / 504 | 2 |
| `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/config/GatewayConfiguration.java` | 声明 contributor Bean | 2 |
| `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributorTest.java` | contributor 单元测试 | 2 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/WebMvcErrorMappingContributor.java` | Spring MVC 自带 404 → `NOT_FOUND` | 3 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java` | 覆写两个 404 处理方法改走适配器 | 3 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/config/WebErrorAutoConfiguration.java` | 声明 contributor Bean | 3 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/WebMvcErrorMappingContributorTest.java` | contributor 单元测试 | 3 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandlerTest.java` | 新增两个 404 用例 | 3 |
| `linqibin-commons/linqibin-spring-boot-starter-web/README.md` | 补一句 404 带错误码 | 3 |
| `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayRoutingIT.java` | 转发行为 | 4 |
| `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayFailureIT.java` | 失败状态码 | 5 |
| `docs/patra/specs/2026-10-09-gateway-webmvc-design.md` 第 12 节 | 实测结果 | 6 |
| `docs/patra/specs/2026-10-05-security-starter-design.md` 第 14 节 | 全局处理器对代理失败的实测 | 6 |
| `patra-api/patra-gateway-boot/README.md` | 重写 | 7 |
| `patra-infra/cd/module-graph.json` | `dumpModuleGraph` 重新生成 | 7 |

---

### Task 1: 依赖与配置切到 WebMVC 版

**Files:**
- Modify: `gradle/libs.versions.toml:120`、`gradle/libs.versions.toml:125-126`
- Modify: `patra-api/patra-gateway-boot/build.gradle.kts`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java`
- Create: `patra-api/patra-gateway-boot/src/integrationTest/resources/application-test.yml`
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java`

**Interfaces:**
- Consumes: 无。
- Produces: `test` profile（`application-test.yml`）供 Task 4、5 的集成测试复用；`linqibin.starter.core.error.context-prefix: GW` 决定 Task 2、5 断言的错误码。

- [ ] **Step 1: 写失败的集成测试**

`patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/PatraGatewayApplicationIT.java`：

```java
package dev.linqibin.patra.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;

/// 网关以 servlet 应用启动，HTTP 客户端是显式指定的 JDK HttpClient，请求跑在虚拟线程上。
///
/// 用真实端口：虚拟线程只有真的经过 Tomcat 才能观察到。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
class PatraGatewayApplicationIT {

  @Autowired private ApplicationContext context;
  @Autowired private RestTestClient restClient;
  @Autowired private RequestThreadProbe probe;

  @Test
  void should_start_as_servlet_application_without_webflux_gateway() {
    assertThat(context).isInstanceOf(WebApplicationContext.class);
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration",
                null))
        .isTrue();
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.gateway.config.GatewayAutoConfiguration", null))
        .isFalse();
  }

  @Test
  void should_use_explicitly_configured_jdk_http_client() {
    assertThat(context.getBean(ClientHttpRequestFactoryBuilder.class))
        .isInstanceOf(JdkClientHttpRequestFactoryBuilder.class);
    // 网关会优先用容器里的 ClientHttpRequestFactory Bean；没有这个 Bean，Boot 按配置装的 builder 才算数
    assertThat(context.getBeanProvider(ClientHttpRequestFactory.class).getIfAvailable()).isNull();
  }

  @Test
  void should_handle_requests_on_virtual_threads() {
    restClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

    assertThat(probe.lastRequestOnVirtualThread()).isTrue();
  }

  /// 记录最近一次请求是否跑在虚拟线程上。
  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {

    /// 注册探针过滤器；Boot 会把容器里的 Filter Bean 挂到 servlet 容器上。
    ///
    /// @return 探针
    @Bean
    RequestThreadProbe requestThreadProbe() {
      return new RequestThreadProbe();
    }
  }

  /// 探针过滤器。
  static class RequestThreadProbe extends OncePerRequestFilter {

    private final AtomicReference<Boolean> virtual = new AtomicReference<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      virtual.set(Thread.currentThread().isVirtual());
      chain.doFilter(request, response);
    }

    /// 最近一次请求是否在虚拟线程上。
    ///
    /// @return 是则 true
    boolean lastRequestOnVirtualThread() {
      return Boolean.TRUE.equals(virtual.get());
    }
  }
}
```

`patra-api/patra-gateway-boot/src/integrationTest/resources/application-test.yml`：

```yaml
# 集成测试：不连 Nacos，但不要关 spring.cloud.discovery.enabled——
# LoadBalancerClientConfiguration 整个挂在它下面，关了 lb:// 就没有实例来源（测试用 SimpleDiscoveryClient 给实例）。
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
    service-registry:
      auto-registration:
        enabled: false
  http:
    clients:
      # 把读超时压短，Task 5 的超时与截断用例才跑得快
      read-timeout: 1s
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*PatraGatewayApplicationIT*"`
Expected: FAIL。编译就不过：`ClientHttpRequestFactoryBuilder`、`RestTestClient`、`OncePerRequestFilter` 在 WebFlux 版的 classpath 上不存在（没有 `spring-boot-starter-web`）。

- [ ] **Step 3: 改版本目录**

`gradle/libs.versions.toml` 第 120 行改为：

```toml
spring-cloud-starter-gateway = { module = "org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc" }
```

删掉第 126 行 `springdoc-openapi-webflux-scalar = { module = "org.springdoc:springdoc-openapi-starter-webflux-scalar", version.ref = "springdoc" }`。第 125 行的 `springdoc-openapi-scalar`（webmvc 版）保留。

确认别名只有网关在用：

Run: `grep -rn -E 'spring\.cloud\.starter\.gateway|springdoc\.openapi\.webflux\.scalar' --include='*.kts' . | grep -v -E '/build/|\.claude/worktrees/'`
Expected: 只有 `patra-api/patra-gateway-boot/build.gradle.kts` 一行（`springdoc.openapi.webflux.scalar`，下一步改掉）。

- [ ] **Step 4: 改网关构建文件**

`patra-api/patra-gateway-boot/build.gradle.kts` 整体替换为：

```kotlin
/**
 * Patra API Gateway Boot
 *
 * API 网关 - Spring Cloud Gateway（WebMVC 版，servlet 栈）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.gateway.PatraGatewayApplication"
}

dependencies {
    // Patra Starter：错误引擎、ProblemDetail 全局处理器、可观测性
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-web"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-observability"))

    // Spring Cloud Gateway（WebMVC 版）：代理走 Boot 的 RestClient，HTTP 客户端由 spring.http.clients.* 决定
    implementation(libs.spring.cloud.starter.gateway)

    // LoadBalancer：lb:// 路由
    implementation(libs.spring.cloud.starter.loadbalancer)

    // API 文档聚合（WebMVC 版 Scalar UI）
    implementation(libs.springdoc.openapi.scalar)

    // 测试依赖（含 RestTestClient 与 WireMock）
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
```

改完提醒用户：**在 IDEA 里点「加载 Gradle 更改」**（自动同步已关）。

- [ ] **Step 5: 改配置**

`patra-api/patra-gateway-boot/src/main/resources/application.yml` 整体替换为：

```yaml
# ============================================================================
# Patra API Gateway - Main Configuration
# ============================================================================
# 统一入口：按路径前缀把请求路由到各微服务（Nacos 发现 + LoadBalancer）。
# Spring Cloud Gateway 的 WebMVC 版（servlet 栈），代理走 Boot 的 RestClient。
# 设计：docs/patra/specs/2026-10-09-gateway-webmvc-design.md
# ============================================================================

server:
  port: 9528

spring:
  application:
    name: patra-gateway
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # 每个请求一个虚拟线程：等下游的时间不占平台线程
  threads:
    virtual:
      enabled: true

  # 网关唯一的 HTTP 客户端：显式用 JDK HttpClient，不靠 classpath 自动探测。
  # 读超时是从请求发出起算的总时长，流式响应同样受限（设计第 6 节）。
  http:
    clients:
      imperative:
        factory: jdk
      connect-timeout: 5s
      read-timeout: 60s
      # 网关不替客户端跟随下游的 3xx
      redirects: dont-follow

  cloud:
    # Nacos Service Discovery
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        # 显式 false：SCA 2025.1.0.0 默认 true 会让 nacos 3.x gRPC 异步握手未完成时
        # 同步 register 的 -401 Client-not-connected 直接 abort 整个应用启动
        fail-fast: false

    # Gateway Route Configuration（WebMVC 版）
    gateway:
      server:
        webmvc:
          # 设了 trusted-proxies 才激活 X-Forwarded-* 头（Host/Port/Proto/Prefix）；
          # 缺它则下游 springdoc 的 servers 还原失败、停在实例地址。patra 仅 tailscale 内网暴露，dev 信任所有上游。
          trusted-proxies: ".*"
          routes:
            # Ingest Service Route
            # Example: /patra-ingest/plans -> lb://patra-ingest/plans
            - id: patra-ingest
              uri: lb://patra-ingest
              predicates:
                - Path=/patra-ingest/**
              filters:
                - StripPrefix=1

            # Registry Service Route
            # Example: /patra-registry/provenance -> lb://patra-registry/provenance
            - id: patra-registry
              uri: lb://patra-registry
              predicates:
                - Path=/patra-registry/**
              filters:
                - StripPrefix=1

            # Catalog Service Route
            # Example: /patra-catalog/venues -> lb://patra-catalog/venues
            - id: patra-catalog
              uri: lb://patra-catalog
              predicates:
                - Path=/patra-catalog/**
              filters:
                - StripPrefix=1

# 网关自身错误的错误码前缀：GW-0404 / GW-0503 / GW-0504
linqibin:
  starter:
    core:
      error:
        context-prefix: GW

# ----------------------------------------------------------------------------
# API Documentation Aggregation
# ----------------------------------------------------------------------------
# 通过 Gateway 聚合各微服务的 OpenAPI 文档；各服务的 /v3/api-docs 经已有路由代理到下游
scalar:
  sources:
    - url: /patra-catalog/v3/api-docs
      title: Catalog Service
      slug: catalog
    - url: /patra-registry/v3/api-docs
      title: Registry Service
      slug: registry
    - url: /patra-ingest/v3/api-docs
      title: Ingest Service
      slug: ingest

# ----------------------------------------------------------------------------
# Logging Configuration
# ----------------------------------------------------------------------------
logging:
  file:
    # Centralized log directory in project root
    path: ${PATRA_LOG_DIR:logs}
  level:
    org.springframework.cloud.gateway: INFO
    org.springframework.cloud.loadbalancer: INFO

# ----------------------------------------------------------------------------
# Actuator & Monitoring Configuration
# ----------------------------------------------------------------------------
# 注意：Metrics 通过 OTel Agent + Micrometer Bridge 导出到 OTel Collector
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

`application-dev.yml`、`application-container.yml` 不动。

- [ ] **Step 6: 改启动类 Javadoc**

`PatraGatewayApplication.java` 类注释替换为（代码不变）：

```java
/// Patra API 网关主入口。
///
/// Spring Cloud Gateway 的 WebMVC 版（servlet 栈），为所有 Patra 微服务提供统一入口：
///
/// - 按路径前缀把请求路由到下游微服务（patra-catalog、patra-registry、patra-ingest），经 Nacos 发现、LoadBalancer 选实例
/// - 代理走 Boot 的 `RestClient`（JDK HttpClient），每个请求一个虚拟线程
/// - 网关自身的错误输出 ProblemDetail（前缀 `GW`）；下游的响应原样透传
/// - 聚合各服务的 OpenAPI 文档（Scalar UI）
///
/// 默认端口 9528。路由与客户端配置见 `application.yml`，设计见
/// `docs/patra/specs/2026-10-09-gateway-webmvc-design.md`。
```

把原注释里的 `@see org.springframework.cloud.gateway.route.RouteDefinition` 删掉（那是 WebFlux 版的类）。

- [ ] **Step 7: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*PatraGatewayApplicationIT*"`
Expected: PASS，3 个用例。

若 `should_handle_requests_on_virtual_threads` 失败，先确认 `application.yml` 里 `spring.threads.virtual.enabled: true` 没被 `test` profile 覆盖；若 `should_use_explicitly_configured_jdk_http_client` 失败于 `getIfAvailable()` 非空，查是谁声明了 `ClientHttpRequestFactory` Bean（`./gradlew :patra-api:patra-gateway-boot:dependencies --configuration runtimeClasspath` 看是否混入了别的 starter），不要为了通过而删断言。

- [ ] **Step 8: 编译全部 source set 并提交**

Run: `./gradlew :patra-api:patra-gateway-boot:compileJava :patra-api:patra-gateway-boot:compileTestJava :patra-api:patra-gateway-boot:compileIntegrationTestJava :patra-api:patra-gateway-boot:spotlessApply`
Expected: BUILD SUCCESSFUL。spotless 若改了文件，`git add -u` 后再提交。

```bash
git add gradle/libs.versions.toml patra-api/patra-gateway-boot/build.gradle.kts \
  patra-api/patra-gateway-boot/src/main/resources/application.yml \
  patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java \
  patra-api/patra-gateway-boot/src/integrationTest
git commit -F - <<'MSG'
build(gateway): 网关切到 Spring Cloud Gateway 的 WebMVC 版 (PAP-69)

依赖、路由配置键和 Scalar 换成 webmvc 版，引入 starter-web；HTTP 客户端显式指定为 JDK
HttpClient，5 秒连接、60 秒读超时、不跟随重定向；网关开虚拟线程；错误码前缀 GW。
集成测试钉住 servlet 启动、客户端类型与虚拟线程。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 2: 到不了下游与无实例映射成 503 / 504

**Files:**
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributor.java`
- Create: `patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/config/GatewayConfiguration.java`
- Test: `patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributorTest.java`

**Interfaces:**
- Consumes: starter-core 的 `dev.linqibin.starter.core.error.spi.ErrorMappingContributor`（`Optional<ErrorCodeLike> mapException(Throwable)`）；commons-core 的 `HttpStdErrors.Group`（`UNAVAILABLE()` → 0503、`GATEWAY_TIMEOUT()` → 0504），由 starter-core 按 `context-prefix` 注册为 Bean。
- Produces: `GatewayErrorMappingContributor(HttpStdErrors.Group)`；Task 5 的集成测试依赖它产生 `GW-0503` / `GW-0504`。

- [ ] **Step 1: 写失败的单元测试**

`patra-api/patra-gateway-boot/src/test/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributorTest.java`：

```java
package dev.linqibin.patra.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class GatewayErrorMappingContributorTest {

  private final GatewayErrorMappingContributor contributor =
      new GatewayErrorMappingContributor(HttpStdErrors.of("GW"));

  @Test
  void should_map_gateway_service_unavailable_to_503() {
    HttpServerErrorException noInstance =
        new HttpServerErrorException(
            HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for patra-ingest");

    assertThat(codeOf(contributor.mapException(noInstance))).isEqualTo("GW-0503");
  }

  @Test
  void should_ignore_other_http_server_errors() {
    assertThat(contributor.mapException(new HttpServerErrorException(HttpStatus.BAD_GATEWAY)))
        .isEmpty();
  }

  @Test
  void should_map_connection_refused_to_503() {
    ResourceAccessException refused =
        new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

    assertThat(codeOf(contributor.mapException(refused))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_connect_timeout_to_503_not_504() {
    ResourceAccessException connectTimeout =
        new ResourceAccessException(
            "I/O error", new HttpConnectTimeoutException("HTTP connect timed out"));

    assertThat(codeOf(contributor.mapException(connectTimeout))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_unknown_host_and_unresolved_address_to_503() {
    ResourceAccessException unknownHost =
        new ResourceAccessException("I/O error", new UnknownHostException("nowhere"));
    ResourceAccessException unresolved =
        new ResourceAccessException("I/O error", new UnresolvedAddressException());

    assertThat(codeOf(contributor.mapException(unknownHost))).isEqualTo("GW-0503");
    assertThat(codeOf(contributor.mapException(unresolved))).isEqualTo("GW-0503");
  }

  @Test
  void should_map_read_timeout_to_504() {
    ResourceAccessException httpTimeout =
        new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"));
    ResourceAccessException socketTimeout =
        new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

    assertThat(codeOf(contributor.mapException(httpTimeout))).isEqualTo("GW-0504");
    assertThat(codeOf(contributor.mapException(socketTimeout))).isEqualTo("GW-0504");
  }

  @Test
  void should_map_nested_timeout_cause_to_504() {
    ResourceAccessException nested =
        new ResourceAccessException(
            "I/O error", new RuntimeException("wrapped", new HttpTimeoutException("timed out")));

    assertThat(codeOf(contributor.mapException(nested))).isEqualTo("GW-0504");
  }

  @Test
  void should_map_io_failure_without_known_cause_to_503() {
    assertThat(codeOf(contributor.mapException(new ResourceAccessException("I/O error"))))
        .isEqualTo("GW-0503");
  }

  @Test
  void should_ignore_unrelated_exceptions() {
    assertThat(contributor.mapException(new IllegalStateException("boom"))).isEmpty();
  }

  private static String codeOf(Optional<ErrorCodeLike> resolved) {
    return resolved.orElseThrow().code();
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*GatewayErrorMappingContributorTest*"`
Expected: FAIL，编译错误 `cannot find symbol: class GatewayErrorMappingContributor`。

- [ ] **Step 3: 写最小实现**

`patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/error/GatewayErrorMappingContributor.java`：

```java
package dev.linqibin.patra.gateway.error;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/// 网关到不了下游时的错误码：到不了（连接被拒、连接超时、域名解析失败、无实例）是 503，等不到（读超时）是 504。
///
/// 下游自己的状态码经 `RestClient.exchange(…, false)` 原样成为响应、不抛异常，所以这里看到的
/// `HttpServerErrorException` 只会是 LoadBalancer 过滤器在找不到实例时抛的那个。
public class GatewayErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造。
  ///
  /// @param http 按网关前缀生成错误码的组
  public GatewayErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof HttpServerErrorException serverError) {
      return serverError.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
          ? Optional.of(http.UNAVAILABLE())
          : Optional.empty();
    }
    if (exception instanceof ResourceAccessException ioFailure) {
      return Optional.of(isReadTimeout(ioFailure) ? http.GATEWAY_TIMEOUT() : http.UNAVAILABLE());
    }
    return Optional.empty();
  }

  /// 沿原因链判断是不是读超时。连接阶段的失败（含 `HttpConnectTimeoutException`，它是
  /// `HttpTimeoutException` 的子类）先于读超时判定，归 503。
  ///
  /// @param exception RestClient 抛出的 I/O 异常
  /// @return 读超时为 true
  private static boolean isReadTimeout(ResourceAccessException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof HttpConnectTimeoutException
          || cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof UnresolvedAddressException) {
        return false;
      }
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
```

`patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/config/GatewayConfiguration.java`：

```java
package dev.linqibin.patra.gateway.config;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.gateway.error.GatewayErrorMappingContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 网关自己的装配：目前只有错误映射。
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

  /// 到不了下游与无实例的错误映射，由 starter-core 的错误引擎收集。
  ///
  /// @param http 按 `linqibin.starter.core.error.context-prefix` 生成错误码的组
  /// @return contributor
  @Bean
  public GatewayErrorMappingContributor gatewayErrorMappingContributor(HttpStdErrors.Group http) {
    return new GatewayErrorMappingContributor(http);
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:test --tests "*GatewayErrorMappingContributorTest*"`
Expected: PASS，9 个用例。

- [ ] **Step 5: 提交**

```bash
./gradlew :patra-api:patra-gateway-boot:spotlessApply
git add patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/error \
  patra-api/patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/config \
  patra-api/patra-gateway-boot/src/test
git commit -F - <<'MSG'
feat(gateway): 到不了下游与无实例映射成 503 和 504 的 ProblemDetail (PAP-69)

RestClient 的 ResourceAccessException 沿原因链分类：连接被拒、连接超时、域名解析失败归 503，
读超时归 504；LoadBalancer 找不到实例抛的 HttpServerErrorException(503) 归 503。没有这条映射，
错误引擎会把它们都判成 500。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 3: starter-web 让 Spring MVC 自带的 404 也带错误码

**Files:**
- Create: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/WebMvcErrorMappingContributor.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/config/WebErrorAutoConfiguration.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/README.md`
- Test: `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/WebMvcErrorMappingContributorTest.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ProblemDetailAdapter.adapt(Exception, HttpServletRequest) → ProblemDetailResponse`（字段 `problemDetail()`、`httpStatus()`、`errorResolution()`）；`HttpStdErrors.Group.NOT_FOUND()`。
- Produces: 任何引 starter-web 的服务，未匹配路径的 404 响应带 `code`（`{前缀}-0404`）。Task 5 断言网关的 `GW-0404`。

背景：`GlobalRestExceptionHandler` 继承 `ResponseEntityExceptionHandler`，父类对 Spring MVC 自带异常有专门的 `@ExceptionHandler`，比本类的 `handleException(Exception)` 更具体、优先命中，渲染出的 ProblemDetail 不经错误引擎，没有 `code`。

- [ ] **Step 1: 写失败的单元测试（contributor）**

`linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/WebMvcErrorMappingContributorTest.java`：

```java
package dev.linqibin.starter.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class WebMvcErrorMappingContributorTest {

  private final WebMvcErrorMappingContributor contributor =
      new WebMvcErrorMappingContributor(HttpStdErrors.of("T"));

  @Test
  void should_map_no_resource_found_to_404() {
    NoResourceFoundException noResource = new NoResourceFoundException(HttpMethod.GET, "/nowhere");

    assertThat(contributor.mapException(noResource).orElseThrow().code()).isEqualTo("T-0404");
  }

  @Test
  void should_map_no_handler_found_to_404() {
    NoHandlerFoundException noHandler =
        new NoHandlerFoundException("GET", "/nowhere", new HttpHeaders());

    assertThat(contributor.mapException(noHandler).orElseThrow().code()).isEqualTo("T-0404");
  }

  @Test
  void should_ignore_other_exceptions() {
    assertThat(contributor.mapException(new IllegalStateException("boom"))).isEmpty();
  }
}
```

- [ ] **Step 2: 写失败的单元测试（处理器覆写）**

在 `GlobalRestExceptionHandlerTest` 里追加两个用例（放在 `shouldHandleGenericException` 之后，沿用该类的 mock 风格；需要的新 import：`org.springframework.http.HttpMethod`、`org.springframework.web.context.request.ServletWebRequest`、`org.springframework.web.servlet.NoHandlerFoundException`、`org.springframework.web.servlet.resource.NoResourceFoundException`、`org.springframework.http.HttpHeaders`、`org.springframework.http.MediaType`）：

```java
  @Test
  @DisplayName("未匹配路径的 NoResourceFoundException 应经适配器渲染，带错误码")
  void shouldRenderNoResourceFoundThroughAdapter() {
    NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/nowhere");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problemDetail.setProperty("code", "T-0404");
    ErrorResolution errorResolution = mock(ErrorResolution.class);
    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("T-0404");
    when(errorResolution.errorCode()).thenReturn(errorCode);
    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.NOT_FOUND, errorResolution);
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    ResponseEntity<Object> result =
        handler.handleNoResourceFoundException(
            exception, new HttpHeaders(), HttpStatus.NOT_FOUND, new ServletWebRequest(request));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result.getBody()).isSameAs(problemDetail);
  }

  @Test
  @DisplayName("NoHandlerFoundException 应经适配器渲染，带错误码")
  void shouldRenderNoHandlerFoundThroughAdapter() {
    NoHandlerFoundException exception =
        new NoHandlerFoundException("GET", "/nowhere", new HttpHeaders());
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    ErrorResolution errorResolution = mock(ErrorResolution.class);
    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("T-0404");
    when(errorResolution.errorCode()).thenReturn(errorCode);
    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.NOT_FOUND, errorResolution);
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    ResponseEntity<Object> result =
        handler.handleNoHandlerFoundException(
            exception, new HttpHeaders(), HttpStatus.NOT_FOUND, new ServletWebRequest(request));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getBody()).isSameAs(problemDetail);
  }
```

（该测试类现有代码就用了 `dev.linqibin.commons.error.codes.ErrorCodeLike` 的全类名，新用例保持同一写法；不顺手重构旧用例。）

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test --tests "*WebMvcErrorMappingContributorTest*" --tests "*GlobalRestExceptionHandlerTest*"`
Expected: FAIL。contributor 类不存在编译失败；若先只编处理器用例，`handleNoResourceFoundException` 是父类 `protected` 方法，测试同包能调到，但返回的是父类渲染的 ProblemDetail，`isSameAs(problemDetail)` 失败。

- [ ] **Step 4: 写最小实现**

`WebMvcErrorMappingContributor.java`：

```java
package dev.linqibin.starter.web.error.handler;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/// Spring MVC 自带异常的错误码：未匹配路径（静态资源处理器的 `NoResourceFoundException`、
/// 没有处理器的 `NoHandlerFoundException`）映射为 404。
public class WebMvcErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造。
  ///
  /// @param http 按服务前缀生成错误码的组
  public WebMvcErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof NoResourceFoundException
        || exception instanceof NoHandlerFoundException) {
      return Optional.of(http.NOT_FOUND());
    }
    return Optional.empty();
  }
}
```

`GlobalRestExceptionHandler.java` 增加两个覆写和一个私有方法（放在 `handleMethodArgumentNotValid` 之后；新增 import：`org.springframework.http.HttpStatusCode`、`org.springframework.web.context.request.WebRequest`、`org.springframework.web.servlet.NoHandlerFoundException`、`org.springframework.web.servlet.resource.NoResourceFoundException`）：

```java
  /// 未匹配路径：不用父类的渲染，走适配器，让 ProblemDetail 带错误码。
  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return renderThroughAdapter(ex, request);
  }

  /// 没有处理器：同上。
  @Override
  protected ResponseEntity<Object> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return renderThroughAdapter(ex, request);
  }

  /// 把父类接住的 Spring MVC 异常交回适配器渲染。
  ///
  /// @param ex 异常
  /// @param request 请求
  /// @return ProblemDetail 响应
  private ResponseEntity<Object> renderThroughAdapter(Exception ex, WebRequest request) {
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, extractServletRequest(request));
    logExceptionHandled(response, ex);
    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
  }
```

`WebErrorAutoConfiguration.java` 增加 Bean（放在 `globalRestExceptionHandler` 之前；import `dev.linqibin.commons.error.codes.HttpStdErrors` 与 `dev.linqibin.starter.web.error.handler.WebMvcErrorMappingContributor`）：

```java
  @Bean
  @ConditionalOnMissingBean
  public WebMvcErrorMappingContributor webMvcErrorMappingContributor(HttpStdErrors.Group http) {
    log.debug("正在注册 Spring MVC 自带异常的错误映射(WebMvcErrorMappingContributor)：未匹配路径 -> 404");
    return new WebMvcErrorMappingContributor(http);
  }
```

- [ ] **Step 5: 跑测试，确认通过**

Run: `./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test`
Expected: PASS，含新增 5 个用例，旧用例全绿。

- [ ] **Step 6: README 补一句**

`linqibin-commons/linqibin-spring-boot-starter-web/README.md`「异常处理流程」小节末尾加：

```markdown
Spring MVC 自带的未匹配路径异常（`NoResourceFoundException` / `NoHandlerFoundException`）同样经错误引擎渲染，响应带 `code`（`{前缀}-0404`）；其余由 `ResponseEntityExceptionHandler` 接住的框架异常（405、415 等）仍是 Spring 默认的 ProblemDetail。
```

- [ ] **Step 7: 提交**

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:spotlessApply
git add linqibin-commons/linqibin-spring-boot-starter-web
git commit -F - <<'MSG'
feat(starter-web): Spring MVC 自带的 404 也经错误引擎渲染并带错误码 (PAP-69)

GlobalRestExceptionHandler 继承的 ResponseEntityExceptionHandler 会先接住未匹配路径的异常，
绕过错误引擎，ProblemDetail 没有 code。覆写两个 404 处理方法改走 ProblemDetailAdapter，
并新增 WebMvcErrorMappingContributor 把它们映射成 NOT_FOUND。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 4: 集成测试钉住转发行为

**Files:**
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayRoutingIT.java`

**Interfaces:**
- Consumes: Task 1 的 `test` profile；生产 `application.yml` 的三条路由；`spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri` 由测试动态指到 WireMock。
- Produces: 无新生产代码。期望全部用例一次通过；任何失败都是迁移改变了行为，修配置而不是改断言（除第 8 条 `Host` 实测写回）。

- [ ] **Step 1: 写集成测试**

`patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayRoutingIT.java`：

```java
package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 迁移前后必须一致的转发行为（设计第 7 节）：剥前缀、查询串、方法与请求体、响应原样、转发头、
/// `Authorization` 透传、不跟随重定向。下游用 WireMock 顶替，`lb://patra-catalog` 经
/// SimpleDiscoveryClient 指到它，LoadBalancer 这条路真的走到。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
class GatewayRoutingIT {

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @Test
  void should_strip_prefix_and_forward_query_string() {
    catalog.stubFor(
        get(urlPathEqualTo("/venues"))
            .withQueryParam("page", equalTo("0"))
            .withQueryParam("size", equalTo("20"))
            .willReturn(okJson("{\"items\":[]}").withHeader("X-Downstream", "catalog")));

    restClient
        .get()
        .uri("/patra-catalog/venues?page=0&size=20")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectHeader()
        .valueEquals("X-Downstream", "catalog")
        .expectBody()
        .json("{\"items\":[]}");
  }

  @Test
  void should_keep_encoded_query_string() {
    catalog.stubFor(get(urlPathEqualTo("/publications/search")).willReturn(okJson("{}")));

    // 用 uriBuilder 让客户端按模板编码（空格 -> %20，中文 -> UTF-8 百分号编码），网关必须原样转发
    restClient
        .get()
        .uri(builder -> builder.path("/patra-catalog/publications/search").queryParam("q", "GLP-1 肠道").build())
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/publications/search"))
            .withQueryParam("q", equalTo("GLP-1 肠道")));
  }

  @Test
  void should_forward_post_body_unchanged() {
    catalog.stubFor(post(urlPathEqualTo("/auth/login")).willReturn(okJson("{\"ok\":true}")));

    restClient
        .post()
        .uri("/patra-catalog/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"email\":\"a@example.com\",\"password\":\"p\"}")
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        postRequestedFor(urlPathEqualTo("/auth/login"))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/json"))
            .withRequestBody(equalToJson("{\"email\":\"a@example.com\",\"password\":\"p\"}")));
  }

  @Test
  void should_pass_204_and_response_headers_through() {
    catalog.stubFor(
        get(urlPathEqualTo("/venues/1"))
            .willReturn(aResponse().withStatus(204).withHeader("Cache-Control", "no-store")));

    restClient
        .get()
        .uri("/patra-catalog/venues/1")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectHeader()
        .valueEquals("Cache-Control", "no-store")
        .expectBody()
        .isEmpty();
  }

  @Test
  void should_send_x_forwarded_headers_to_downstream() {
    catalog.stubFor(get(urlPathEqualTo("/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues"))
            .withHeader("X-Forwarded-Host", matching("localhost(:\\d+)?"))
            .withHeader("X-Forwarded-Port", equalTo(gatewayPort()))
            .withHeader("X-Forwarded-Proto", equalTo("http"))
            .withHeader("X-Forwarded-Prefix", equalTo("/patra-catalog"))
            .withHeader("Forwarded", containing("proto=http")));
  }

  @Test
  void should_send_downstream_host_header() {
    catalog.stubFor(get(urlPathEqualTo("/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues"))
            .withHeader("Host", equalTo("localhost:" + catalog.getPort())));
  }

  @Test
  void should_forward_authorization_header_unchanged() {
    catalog.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-catalog/auth/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer patra_user_opaque")
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/auth/me"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer patra_user_opaque")));
  }

  @Test
  void should_pass_downstream_problem_detail_through() {
    String body = "{\"type\":\"about:blank\",\"status\":422,\"code\":\"CATALOG-0422\"}";
    catalog.stubFor(
        get(urlPathEqualTo("/venues/bad"))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                    .withBody(body)));

    restClient
        .get()
        .uri("/patra-catalog/venues/bad")
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(body);
  }

  @Test
  void should_not_follow_downstream_redirects() {
    catalog.stubFor(
        get(urlPathEqualTo("/old"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", "/new")));

    restClient
        .get()
        .uri("/patra-catalog/old")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals("Location", "/new");
  }

  @Test
  void should_serve_scalar_page_itself() {
    restClient
        .get()
        .uri("/scalar.html")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML);
  }

  private String gatewayPort() {
    return environment.getRequiredProperty("local.server.port");
  }
}
```

- [ ] **Step 2: 跑测试**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayRoutingIT*"`
Expected: PASS，10 个用例。

可能的失败与处理：
- `should_send_downstream_host_header`：这是实测点（spec 第 12 节第 6 条）。若下游收到的 `Host` 是网关地址或别的值，把实测值记下、按实测改断言，并在 Task 6 写回 spec。不是缺陷。
- `should_send_x_forwarded_headers_to_downstream` 缺 `X-Forwarded-Prefix`：确认 `trusted-proxies` 写在 `spring.cloud.gateway.server.webmvc` 下；`RestTestClient` 的请求来自 `127.0.0.1`，`".*"` 必然信任。
- `should_not_follow_downstream_redirects` 收到 200 或 404：`spring.http.clients.redirects: dont-follow` 没生效，核对键名拼写（是 `clients` 不是 `client`）。
- `should_serve_scalar_page_itself` 404：确认依赖是 `springdoc-openapi-starter-webmvc-scalar` 且版本目录别名改对。

- [ ] **Step 3: 提交**

```bash
./gradlew :patra-api:patra-gateway-boot:spotlessApply
git add patra-api/patra-gateway-boot/src/integrationTest
git commit -F - <<'MSG'
test(gateway): 集成测试钉住转发行为、转发头与透传 (PAP-69)

WireMock 顶替下游，lb://patra-catalog 经 SimpleDiscoveryClient 指到它：剥前缀与查询串、
编码不被改写、POST 请求体原样、204 与响应头原样、X-Forwarded-* 与 Host、Authorization 透传、
下游 ProblemDetail 原样、不跟随重定向、Scalar 页由网关自己响应。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 5: 集成测试钉住失败状态码

**Files:**
- Test: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayFailureIT.java`

**Interfaces:**
- Consumes: Task 1 的 `test` profile（读超时 1 秒、前缀 `GW`）；Task 2 的 `GatewayErrorMappingContributor`；Task 3 的 starter-web 404 渲染。
- Produces: 无新生产代码。

- [ ] **Step 1: 写集成测试**

`patra-api/patra-gateway-boot/src/integrationTest/java/dev/linqibin/patra/gateway/GatewayFailureIT.java`：

```java
package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 网关自身产生的错误（设计第 8 节）：未知路由 404、无实例 503、连接被拒 503、读超时 504，
/// 都是带错误码的 ProblemDetail；以及读超时是总时长这一接受的限制（第 6 节）。
///
/// `test` profile 把读超时压到 1 秒。三条路由各扮一个角色：catalog 指向 WireMock，
/// registry 指向没人监听的端口，ingest 不配实例。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
class GatewayFailureIT {

  private static final int STREAM_BODY_LENGTH = 10_000;

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-registry[0].uri",
        () -> "http://127.0.0.1:1");
  }

  @Test
  void should_return_problem_detail_404_for_unknown_route() {
    restClient
        .get()
        .uri("/nowhere")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0404")
        .jsonPath("$.status")
        .isEqualTo(404);
  }

  @Test
  void should_return_503_when_no_instance_is_available() {
    restClient
        .get()
        .uri("/patra-ingest/plans")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503");
  }

  @Test
  void should_return_503_when_connection_is_refused() {
    restClient
        .get()
        .uri("/patra-registry/provenance/pubmed")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503");
  }

  @Test
  void should_return_504_when_downstream_does_not_answer_in_time() {
    catalog.stubFor(
        get(urlPathEqualTo("/slow")).willReturn(okJson("{}").withFixedDelay(2_000)));

    restClient
        .get()
        .uri("/patra-catalog/slow")
        .exchange()
        .expectStatus()
        .isEqualTo(504)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0504");
  }

  /// 钉住接受的限制：读超时从请求发出起算、持续有数据也不重置，响应体超过 1 秒还没发完就被切断。
  /// 此时状态码已经发出，客户端拿到的是半截响应或连接异常，不会是 504。框架行为变了这条会先知道。
  @Test
  void should_cut_streaming_response_at_read_timeout() throws Exception {
    catalog.stubFor(
        get(urlPathEqualTo("/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("x".repeat(STREAM_BODY_LENGTH))
                    .withChunkedDribbleDelay(10, 3_000)));

    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getRequiredProperty("local.server.port")
                        + "/patra-catalog/stream"))
            .build();
    int received;
    try (HttpClient client = HttpClient.newHttpClient()) {
      received = client.send(request, HttpResponse.BodyHandlers.ofString()).body().length();
    } catch (IOException truncated) {
      received = -1;
    }

    assertThat(received)
        .as("响应体应被读超时切断：要么读到一半抛 IOException，要么长度不足 %d", STREAM_BODY_LENGTH)
        .isLessThan(STREAM_BODY_LENGTH);
  }
}
```

- [ ] **Step 2: 跑测试**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayFailureIT*"`
Expected: PASS，5 个用例。整类约 5 秒（固定延迟 2 秒 + 分块 3 秒，各被 1 秒读超时截断）。

可能的失败与处理：
- 无实例回 500 `GW-0500`：Task 2 的 contributor 没被收集，确认 `GatewayConfiguration` 在 `dev.linqibin.patra.gateway` 包下被扫描到。
- 404 没有 `code`：Task 3 的 starter-web 改动没生效，确认网关依赖的是本仓库的 `:linqibin-commons:linqibin-spring-boot-starter-web` 且已重新编译。
- 连接被拒回 504：`isReadTimeout` 的原因链里先出现了 `HttpTimeoutException`。把实际的原因链（`ex.getCause()` 逐层类名）记下来，按实测调整 `GatewayErrorMappingContributor` 的判断顺序，并同步更新 Task 2 的单元测试与 spec 第 8 节、第 12 节第 2 条。
- 截断用例拿到完整 10000 字节：JDK 客户端的读超时语义变了（spec 第 6 节的前提不再成立），这是重要发现，停下来报告而不是改断言。

- [ ] **Step 3: 提交**

```bash
./gradlew :patra-api:patra-gateway-boot:spotlessApply
git add patra-api/patra-gateway-boot/src/integrationTest
git commit -F - <<'MSG'
test(gateway): 集成测试钉住未知路由、无实例、连不上与超时的状态码 (PAP-69)

未知路由 404 GW-0404，无实例与连接被拒 503 GW-0503，响应头到达前超时 504 GW-0504，
都是 ProblemDetail；另钉住读超时是总时长、流式响应会被切断这一接受的限制。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 6: 回归对照 mini，实测写回两份设计文档

**Files:**
- Modify: `docs/patra/specs/2026-10-09-gateway-webmvc-design.md`（第 12 节）
- Modify: `docs/patra/specs/2026-10-05-security-starter-design.md`（第 14 节末尾）

**Interfaces:**
- Consumes: Task 1 到 5 全部完成；mini 上的网关（迁移前）在 `http://100.103.73.27:9528`；本机能连 mini 的 Nacos（`PATRA_INFRA_HOST=100.103.73.27`，tailnet 在线）。
- Produces: 两份文档的实测段落。

- [ ] **Step 1: 本机起迁移后的网关**

另开一个终端跑（不注册到 mini 的 Nacos，只做发现）：

```bash
PATRA_INFRA_HOST=100.103.73.27 TAILSCALE_IP=100.70.109.37 \
SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED=false \
./gradlew :patra-api:patra-gateway-boot:bootRun
```

Expected: 日志出现 `Tomcat started on port 9528`，且没有 `WebFlux`、`Netty` 字样；`curl -s http://localhost:9528/actuator/health` 返回 `{"status":"UP"}`。

- [ ] **Step 2: 对照 API**

门户用到的 catalog 读接口（来自 `patra-portal/src/lib/portal-api/*.ts`）加三份文档、Scalar 页、一个不存在的路径。两个详情接口的 id 先从列表响应里取第一条（字段名以实际响应为准）：

```bash
B=http://100.103.73.27:9528
curl -s "$B/patra-catalog/portal/publications?tab=latest&page=1&pageSize=1" | python3 -I -m json.tool | head -30   # 记下第一条的 id -> PID
curl -s "$B/patra-catalog/portal/venues?sort=impactFactor&pageSize=1" | python3 -I -m json.tool | head -30          # 记下第一条的 id -> VID
```

```bash
B=http://100.103.73.27:9528   # 迁移前（mini）
L=http://localhost:9528       # 迁移后（本机）
PID=<上面记下的文献 id>; VID=<上面记下的期刊 id>
D=/private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/5cf40ed0-3140-4e91-bd51-9c11d12cb541/scratchpad/regression; mkdir -p "$D"
for p in \
  /patra-catalog/v3/api-docs /patra-registry/v3/api-docs /patra-ingest/v3/api-docs \
  /scalar.html /nowhere /actuator/health \
  "/patra-catalog/portal/publications?tab=latest&page=1&pageSize=14" \
  "/patra-catalog/portal/publications/search?q=GLP-1&page=1&pageSize=14" \
  "/patra-catalog/portal/publications/search/facets?q=GLP-1" \
  "/patra-catalog/portal/publications/$PID" \
  "/patra-catalog/portal/venues?sort=impactFactor&pageSize=12" \
  "/patra-catalog/portal/venues/facets" \
  "/patra-catalog/portal/venues/$VID"; do
  n=$(echo "$p" | tr '/?&=' '____')
  for tag in before after; do
    base=$B; [ "$tag" = after ] && base=$L
    curl -s -o "$D/$tag$n.body" -D "$D/$tag$n.headers" -w '%{http_code} %{content_type}\n' "$base$p" > "$D/$tag$n.status"
  done
  echo "== $p"; paste "$D/before$n.status" "$D/after$n.status"
  diff <(grep -i -E '^(content-type|cache-control|location):' "$D/before$n.headers" | sort) \
       <(grep -i -E '^(content-type|cache-control|location):' "$D/after$n.headers" | sort) && echo "headers same"
  if grep -q json "$D/before$n.status"; then
    diff <(python3 -I -m json.tool "$D/before$n.body") <(python3 -I -m json.tool "$D/after$n.body") > /dev/null && echo "body same" || echo "body differs"
  else
    cmp -s "$D/before$n.body" "$D/after$n.body" && echo "body same" || echo "body differs"
  fi
done
```

Expected：
- 三份 `/v3/api-docs` 状态码与响应体一致，且 `servers[0].url` 分别是各自网关地址加服务前缀（迁移前 `http://100.103.73.27:9528/patra-catalog`，迁移后 `http://localhost:9528/patra-catalog`）——地址不同是因为打的网关不同，形状一致即可。
- `/scalar.html` 两边都是 200 的 HTML。
- `/nowhere`：迁移前 404 的 Spring 默认 JSON，迁移后 404 的 `application/problem+json` 带 `"code":"GW-0404"`——这是 Issue 要的差别。
- 门户读接口：状态码、`Content-Type`、响应体一致（响应体里若有 `traceId` 一类每次不同的字段，忽略）。

有不一致就停下来查，先怀疑配置键再怀疑框架。

- [ ] **Step 3: 对照门户**

```bash
PATRA_GATEWAY_BASE_URL=http://localhost:9528 pnpm --dir patra-portal test:e2e
```

Expected: 现有 e2e 全部通过（catalog 可达，不会 skip）。再本机起门户 `PATRA_GATEWAY_BASE_URL=http://localhost:9528 pnpm --dir patra-portal dev -p 4000`，用浏览器过首页、`/papers`、`/papers/[id]`、`/journals`、`/journals/[id]`，与 `http://100.103.73.27:4000` 上的同一页面对照，内容与加载正常。

- [ ] **Step 4: 写回 spec 第 12 节**

把第 12 节「结果写回本节」下的八（九）条逐条补上实测结果，形式照 `2026-10-09-identity-session-design.md` 第 15 节：每条以「实测：」开头，写观察到的类名、状态码、头的值。必填的事实：

1. 无实例：响应 503、`application/problem+json`、`GW-0503`（Task 5）。
2. JDK 客户端下连接被拒与读超时的原因链：从 Task 5 失败时或加一次临时日志得到的类名（例如 `ResourceAccessException → ConnectException`、`ResourceAccessException → HttpTimeoutException`）。
3. 全局处理器对代理失败的表现：代理失败以异常形式到达处理器，由 contributor 决定状态码；写明没有 contributor 时的默认是 500。
4. `X-Forwarded-Prefix` 的值与 springdoc servers 地址（Step 2）。
5. 重定向：`dont-follow` 下 302 原样到客户端（Task 4）。
6. 下游收到的 `Host`（Task 4 的实测值）。
7. 虚拟线程下 Nacos、OTel、Micrometer：Step 1 启动日志有无告警，`/actuator/metrics` 可读。
8. Apache HttpClient 5 是否在 classpath：`./gradlew :patra-api:patra-gateway-boot:dependencies --configuration runtimeClasspath | grep -i httpclient5`，写明结果与来源。
9. 流式响应被切断时客户端看到什么（Task 5 的 `received` 值或 IOException 文案）。

- [ ] **Step 5: 写回安全 starter 设计第 14 节**

在 `docs/patra/specs/2026-10-05-security-starter-design.md` 第 14 节末尾追加一段：

```markdown
**网关切到 WebMVC 版后对全局异常处理器的实测（2026-10-09，PAP-69）**：网关引入 starter-web 后，代理失败（`RestClient` 的 `ResourceAccessException`、LoadBalancer 无实例的 `HttpServerErrorException(503)`）都以异常形式到达 `GlobalRestExceptionHandler`，经错误引擎解析；引擎没有专门规则时判成 500，网关用 `GatewayErrorMappingContributor` 映射成 503 / 504。另发现 `ResponseEntityExceptionHandler` 会先接住 Spring MVC 自带的异常、绕过引擎，未匹配路径的 404 原本没有错误码，已在 starter-web 里覆写两个 404 处理方法改走适配器。响应体已经开始转发后再出错（读超时切断流式响应），状态码无法再改。
```

- [ ] **Step 6: 提交**

```bash
git add docs/patra/specs/2026-10-09-gateway-webmvc-design.md docs/patra/specs/2026-10-05-security-starter-design.md
git commit -F - <<'MSG'
docs(gateway): 回归对照与实测结果写回设计文档 (PAP-69)

与 mini 上迁移前的网关逐项对照门户读接口、三份 api-docs、Scalar 页与未知路径；
实测点逐条写回；安全 starter 设计第 14 节补上全局处理器对代理失败的表现。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

---

### Task 7: README 重写、模块图、全量门控、Linear

**Files:**
- Modify: `patra-api/patra-gateway-boot/README.md`
- Modify: `patra-infra/cd/module-graph.json`（生成）

**Interfaces:**
- Consumes: Task 1 到 6。
- Produces: 无。

- [ ] **Step 1: 重写 README**

`patra-api/patra-gateway-boot/README.md` 整体替换为：

```markdown
# patra-gateway-boot

Patra 的 API 网关：所有外部请求的统一入口，按路径前缀把请求路由到各微服务。Spring Cloud Gateway 的 **WebMVC 版**（servlet 栈），和仓库里其他服务同一套 Web 栈。

设计：[gateway 切换到 WebMVC 版工程设计（PAP-69）](../../docs/patra/specs/2026-10-09-gateway-webmvc-design.md)。鉴权在 PAP-65 加。

## 职责

- **路由**：`/patra-catalog/**`、`/patra-registry/**`、`/patra-ingest/**` 剥掉第一段后转给对应服务，经 Nacos 发现、Spring Cloud LoadBalancer 选实例。
- **转发头**：给下游加 `X-Forwarded-Host` / `Port` / `Proto` / `Prefix` 和 `Forwarded`，下游 springdoc 据此把 servers 还原成网关地址。逐跳头剥掉，`Authorization` 等其余请求头原样到达下游。
- **透传**：下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx / 3xx；网关不跟随重定向。
- **文档聚合**：`/scalar.html` 聚合三个服务的 OpenAPI 文档。
- **可观测性**：OTel Agent + Micrometer，actuator 暴露 `health` / `info` / `metrics`。

## 模块结构

```
patra-gateway-boot/
├── src/main/java/dev/linqibin/patra/gateway/
│   ├── PatraGatewayApplication.java              # 启动类
│   ├── config/GatewayConfiguration.java          # 网关自己的装配（错误映射）
│   └── error/GatewayErrorMappingContributor.java # 到不了下游 / 无实例 → 503 / 504
├── src/main/resources/
│   ├── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、错误前缀
│   ├── application-dev.yml                       # dev：Nacos 用 TAILSCALE_IP 注册，DEBUG 日志
│   └── application-container.yml                 # 容器部署
└── src/integrationTest/java/dev/linqibin/patra/gateway/
    ├── PatraGatewayApplicationIT.java            # servlet 启动、JDK 客户端、虚拟线程
    ├── GatewayRoutingIT.java                     # 转发行为（WireMock 顶替下游）
    └── GatewayFailureIT.java                     # 失败状态码
```

## 路由

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          trusted-proxies: ".*"      # 激活 X-Forwarded-* 头；patra 仅 tailscale 内网暴露
          routes:
            - id: patra-catalog
              uri: lb://patra-catalog
              predicates:
                - Path=/patra-catalog/**
              filters:
                - StripPrefix=1
```

三条路由同形。例：`GET /patra-catalog/venues?page=0` → `GET http://<catalog 实例>/venues?page=0`，下游另收到 `X-Forwarded-Prefix: /patra-catalog`。

## HTTP 客户端、超时与线程

| 项 | 值 | 说明 |
|---|---|---|
| 客户端 | JDK HttpClient | `spring.http.clients.imperative.factory: jdk`，显式指定，不靠 classpath 自动探测 |
| 连接超时 | 5 秒 | `spring.http.clients.connect-timeout` |
| 读超时 | 60 秒 | `spring.http.clients.read-timeout`。**从请求发出起算的总时长**，持续收到数据也不重置：任何响应（含流式）要在 60 秒内读完，超过则正在转发的响应体被切断 |
| 重定向 | 不跟随 | `spring.http.clients.redirects: dont-follow` |
| 线程 | 虚拟线程 | `spring.threads.virtual.enabled: true`，每个请求一个虚拟线程 |

WebMVC 版网关自己不带 HTTP 客户端实现，代理走 Boot 的 `RestClient`，上面这组键就是客户端的唯一来源。

## 错误

网关自身产生的错误走 starter-web 的全局处理器，`application/problem+json`，错误码前缀 `GW`（`linqibin.starter.core.error.context-prefix`）。下游自己的错误原样透传、不改写。

| 场景 | 状态码 | 错误码 |
|---|---|---|
| 未匹配任何路由 | 404 | `GW-0404` |
| `lb://` 找不到实例 | 503 | `GW-0503` |
| 连接被拒、连接超时、域名解析失败 | 503 | `GW-0503` |
| 响应头到达前读超时 | 504 | `GW-0504` |

映射逻辑在 `GatewayErrorMappingContributor`；响应体已开始转发后再出错，状态码无法再改。

## API 文档聚合

`http://<gateway>:9528/scalar.html`。`scalar.sources` 列出三个服务的 `/v3/api-docs`，经各自路由代理到下游；各服务需引入 `linqibin-spring-boot-starter-openapi`。

## 配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile | `dev` |
| `NACOS_HOST` | Nacos 地址 | 跟随 `PATRA_INFRA_HOST`，再缺省为 `127.0.0.1` |
| `NACOS_PORT` | Nacos 端口 | `8848` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 凭据 | `nacos` / `nacos` |
| `TAILSCALE_IP` | dev 下向 Nacos 注册的 IP | 空 |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |

端口 9528。超时与虚拟线程在 `application.yml` 里有值，不需要环境变量。

## 测试

```bash
./gradlew :patra-api:patra-gateway-boot:test              # 单元测试
./gradlew :patra-api:patra-gateway-boot:integrationTest   # 集成测试（WireMock 顶替下游，不连 Nacos）
```

集成测试的 `test` profile 只关掉 Nacos 的发现与注册，`spring.cloud.discovery.enabled` 保持开启，`lb://` 经 `spring.cloud.discovery.client.simple.instances.*` 拿到 WireMock 的地址。

## 技术栈

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3（gateway-server-webmvc 5.0.3） |
| Spring Cloud LoadBalancer | 随 Spring Cloud |
| Nacos Discovery | spring-cloud-alibaba 2025.1.0.0 |
| springdoc（webmvc Scalar） | 3.0.1 |
| Java | 25 |
```

- [ ] **Step 2: 重新生成模块图并跑脚本测试**

Run: `./gradlew dumpModuleGraph --no-configuration-cache && bash patra-infra/cd/detect-changes.test.sh`
Expected: `patra-infra/cd/module-graph.json` 有 diff（网关新增 `linqibin-spring-boot-starter-web` 的影响边）；脚本测试全部通过。

- [ ] **Step 3: 全量门控**

Run: `./gradlew check --no-configuration-cache && ./gradlew :patra-api:patra-gateway-boot:integrationTest :linqibin-commons:linqibin-spring-boot-starter-web:test`
Expected: 两条都 BUILD SUCCESSFUL（`check` 不含集成测试，所以分开跑）。

- [ ] **Step 4: 提交**

```bash
git add patra-api/patra-gateway-boot/README.md patra-infra/cd/module-graph.json
git commit -F - <<'MSG'
docs(gateway): 重写网关 README，模块图随依赖更新 (PAP-69)

README 按 WebMVC 版的实际行为重写：路由、转发头、HTTP 客户端与超时、虚拟线程、错误码表、
测试方式。网关新增 starter-web 依赖，dumpModuleGraph 重新生成模块图。

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
```

- [ ] **Step 5: Linear**

用 Linear 工具更新 PAP-69：To-Do 九条按实际完成勾掉；Acceptance Criteria 留给用户验收；状态改为 Review。PAP-65 描述里「网关已在 PAP-69 切到 WebMVC 版」现在是事实，不用改。

---

## 执行后的收尾

按 `superpowers:finishing-a-development-branch`：分支 `feat/v0.8-accounts-api` 停在本地等用户验收；合并与推送由用户决定。
