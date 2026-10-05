# 安全 starter 实现计划（PAP-62）

> **给执行计划的 agent：** 必须使用子技能 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐个实现本计划。步骤用复选框（`- [ ]`）语法跟踪进度。

**目标：** 交付一份纯 Java 的「当前用户」抽象和一个基于 Spring Security 的 servlet starter，让下游服务从网关传来的身份头得到当前用户，401 / 403 输出为现有的 ProblemDetail 格式，JPA 审计列自动填当前用户 ID。

**架构：** `patra-common-security` 放 domain 可依赖的抽象（`CurrentUser`、`CurrentUserPort`）。`patra-spring-boot-starter-security` 用 Spring Security 的通用认证过滤器加一个自己的请求头转换器建立认证，三个自动配置按「是否依赖 Web 环境」拆开。linqibin-commons 配套改两处：starter-jpa 新增「当前操作人」接口，starter-web 的全局异常处理器降一级优先级。

**技术栈：** Java 25、Spring Boot 4.0.8、Spring Security 7.0.7、Spring Framework 7.0.9、Jackson 3（`tools.jackson`）、Spring Data JPA / Hibernate 7、Gradle 9.5（Kotlin DSL + Convention Plugins）、JUnit 5 + AssertJ + Mockito、Testcontainers（PostgreSQL 17）。

**Spec：** `docs/patra/specs/2026-10-05-security-starter-design.md`。执行时两份都要读：本计划讲怎么做，spec 讲为什么这么做。

**Issue：** [PAP-62](https://linear.app/papertrace/issue/PAP-62)。分支 `feat/v0.8-accounts-api`（已存在，直接在上面工作）。

## Global Constraints

每个任务都隐含遵守本节。

**来自 spec 的硬约束**

- Spring Security 只经 `patra-spring-boot-starter-security` 进入 classpath。不把它加进 starter-web 或任何公共模块。
- linqibin-commons 的模块不依赖两个新模块（`checkBoundary` 验证）。
- `patra-common-security` 是纯 Java，项目依赖只有 `linqibin-commons-core`。
- 包名：`dev.linqibin.patra.common.security`、`dev.linqibin.patra.starter.security`。
- 五个请求头：`X-Patra-User-Id`、`X-Patra-Session-Id`、`X-Patra-Account-Type`、`X-Patra-Client-Type`、`X-Patra-Gateway-Token`。五个头都是单值，同一个头出现多次按不合法处理。
- 配置项只有一个：`patra.security.gateway-token`。只在 servlet 应用里必填。日志里不输出令牌的值。
- 错误码：`{PREFIX}-0401`、`{PREFIX}-0403`、`{PREFIX}-0500`、`{PREFIX}-0503`。`PREFIX` 来自 `linqibin.starter.core.error.context-prefix`，测试里统一设成 `TEST`。
- 安全过滤器链里输出给客户端的 `detail` 是固定短句，不带框架异常的原始消息：401 是 `Authentication required`，403 是 `Access denied`，其余用 HTTP 状态的标准短语（如 `Internal Server Error`）。
- 下游过滤器链：`STATELESS`、关 CSRF、关登出、匿名认证保持框架默认（开）。自定义的认证过滤器不声明成 Bean。
- 版本由 Spring Boot BOM 管理：不在 `gradle/libs.versions.toml` 里为 Spring Security 新增版本号。
- 不开 `@EnableMethodSecurity`，不做角色，不做续期和路径规则（那些属于 PAP-63 / 64 / 65）。

**来自仓库的硬约束**

- 文档、注释、commit message 用中文；代码标识符用英文。
- 所有方法（任何访问级别）写 `///` 风格的 Markdown JavaDoc。每行不超过 100 列，否则 google-java-format 会把它折成 `//`。
- 不写全类名，用 `import`。优先用 Lombok。参数不超过 4 个的 record 用静态工厂方法，不用 `@Builder`。
- 格式由 Spotless（google-java-format 1.29.0）决定：每个任务提交前跑一次 `spotlessApply`，以它的输出为准。
- SpotBugs 的设置是 `effort=MAX`、`reportLevel=LOW`、`ignoreFailures=false`：任何提示都会让 `check` 失败。本计划已经预防了三处，照着写即可：可序列化的普通类声明 `serialVersionUID`；构造器会抛异常或调用可重写方法的类声明为 `final`；`dev.linqibin.patra.starter.security` 包的 `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` 在任务 4 加进排除清单。出现其他提示时修代码，不要扩大排除范围。
- 测试位置与命名：单元测试 `src/test`、`*Test`；集成测试 `src/integrationTest`、`*IT`；测试方法用 snake_case（`should_<期望行为>`），配中文 `@DisplayName`。已有测试文件里加用例时沿用该文件自己的写法。
- 测试里禁止：反射访问私有成员、`@SuppressWarnings("unchecked")`、为了测试给生产类加 setter。
- TDD：先写失败的测试，看到它失败，再写让它通过的最少代码。
- 不写工时估算，不写「后续优化」「分阶段」。

**执行方式**

- 所有命令在仓库根目录执行（当前工作树是 `.claude/worktrees/v0.8-accounts-api`）。
- 集成测试用 Testcontainers 起 PostgreSQL 17，本机要有 Docker 在运行。先跑 `docker info` 确认。
- 提交：每个任务末尾有提交步骤。只有用户对本次执行明确授权可以提交时才执行（以用户原话为准）；没有授权就跳过提交，把改动留在工作区，并在汇报里列出待提交的文件。任何情况下都不 push、不开 PR。
- commit message 末尾按当次执行会话的署名规则追加 `Co-Authored-By` 行。subject 不能以大写字母开头，用中文动词起头。
- spec 第 14 节列了五个「来自读源码、没有运行验证」的结论。对应的测试在本计划里都标了「实测点」。任何一条的结果与预期不符：停下来，把现象报告给用户，不要自行改设计绕过去。
- 本计划里的代码对着 Spring Boot 4.0.8、Spring Security 7.0.7、Spring Framework 7.0.9 的源码核对过类名和方法签名，但没有编译和运行过。遇到编译错误时，按报错修正接口的用法（import 的包名、方法签名、泛型推断）即可，这类修正不需要报告。如果要改的是做法本身（换一种机制、删掉某个测试、放宽某个断言），停下来报告。

## Review Focus

spec 没有逐条写明、但使用这个模块的人最可能踩到的五种输入或故障。每条都已经在对应任务里加了测试。

1. **内部令牌带首尾空白、换行或非 ASCII 字符**（从 secret 文件读入时很常见）。HTTP 头在传输中会被去掉首尾空白，网关写出去的值和下游配置的值永远比不上，结果是所有请求静默变成匿名，没有任何报错。预期：应用启动时就失败，错误信息指明配置项。→ 任务 8 的 `PatraSecurityPropertiesTest`。
2. **同一个工作线程先后处理已登录请求和匿名请求**。预期：匿名请求看不到上一个用户；身份头不合法的请求之后也一样。→ 任务 12 的 `SecurityWebMvcSliceIT`。
3. **身份头里的数字写法不规范**：前导零、正负号、全角数字、超出 Long 范围、空串、带空格。预期：一律按不合法处理，不被宽松地解析成某个用户 ID。→ 任务 4 的 `IdentityHeadersTest`。
4. **`CurrentUserRunner` 嵌套调用，内层抛异常**。预期：内层结束后回到外层的身份，最外层结束后清空。→ 任务 5 的 `CurrentUserRunnerTest`。
5. **请求头的名字是全小写**（HTTP/2 下所有头名都是小写）。预期：与规范写法的结果相同。→ 任务 6 的 `GatewayHeaderAuthenticationConverterTest`。

## 与 spec 的出入

写计划时核对代码发现两处 spec 与实际不符，另有几处 spec 没写到的细节在这里定下。任务 13 会把前两处写回 spec。

1. **非 Web 用例的测试应用不扫描整个 `dev.linqibin`**（spec 第 13.2 节）。starter-web 的 `GlobalRestExceptionHandler` 带 `@RestControllerAdvice`，会被组件扫描注册；它依赖的 `ProblemDetailAdapter` 只在 servlet 环境下才有。所以「扫描整个 `dev.linqibin`」的应用以非 Web 方式启动时本来就起不来，这与安全模块无关。非 Web 用例的测试应用只用自动配置，不做大范围扫描。
2. **spec 第 14 节第 5 条在本 Issue 无法实测**。它问的是全局异常处理器降优先级后，网关代理失败时的表现。网关现在还是 WebFlux 版，classpath 上没有 starter-web，要等 PAP-69 切到 WebMVC 版之后才测得了。移交 PAP-69。
3. spec 没写到、计划里定下的细节：
   - `CurrentUser` 实现 `Serializable`。它是认证对象的主体，而 Spring Security 的认证对象是可序列化的；不这样写 SpotBugs 会报 `SE_BAD_FIELD`。
   - 解析身份头的三种结果用一个密封接口 `IdentityParseResult` 表示（`Identified` / `Absent` / `Malformed`）。
   - `IdentityHeaders` 多一个常量 `IDENTITY_HEADER_NAMES`（四个身份头的名字，不含内部令牌头）。
   - `PatraSecurityProperties` 除了拒绝空白令牌，还拒绝含空白或非可见 ASCII 字符的令牌（Review Focus 第 1 条）。
   - 默认过滤器链的 Bean 名是 `patraSecurityFilterChain`。

## 文件结构

### 新模块 `patra-common-security`

路径 `patra-api/patra-common/patra-common-security/`，Gradle 路径 `:patra-api:patra-common:patra-common-security`。

| 文件 | 职责 |
|---|---|
| `build.gradle.kts` | 模块构建脚本 |
| `src/main/java/dev/linqibin/patra/common/security/AccountType.java` | 账号类型枚举 |
| `src/main/java/dev/linqibin/patra/common/security/ClientType.java` | 客户端类型枚举 |
| `src/main/java/dev/linqibin/patra/common/security/CurrentUser.java` | 当前用户记录类型 |
| `src/main/java/dev/linqibin/patra/common/security/CurrentUserPort.java` | 取当前用户的端口 |
| `src/main/java/dev/linqibin/patra/common/security/AuthenticationRequiredException.java` | 需要登录但没登录时抛出的领域异常 |
| `src/test/java/dev/linqibin/patra/common/security/*Test.java` | 四个单元测试类 |

### 新模块 `patra-spring-boot-starter-security`

路径 `patra-starters/patra-spring-boot-starter-security/`，Gradle 路径 `:patra-starters:patra-spring-boot-starter-security`。下表的 Java 路径都省略前缀 `dev/linqibin/patra/starter/security/`。

| 文件 | 职责 |
|---|---|
| `build.gradle.kts`、`README.md` | 构建脚本、使用说明 |
| `src/main/java/…/header/IdentityHeaders.java` | 五个请求头的名字，身份头的解析与写入 |
| `src/main/java/…/header/IdentityParseResult.java` | 解析身份头的三种结果 |
| `src/main/java/…/authentication/CurrentUserAuthentication.java` | 认证对象，主体是 `CurrentUser` |
| `src/main/java/…/authentication/GatewayHeaderAuthenticationConverter.java` | 校验内部令牌，把身份头变成认证对象 |
| `src/main/java/…/authentication/MalformedIdentityException.java` | 身份头残缺或不合法 |
| `src/main/java/…/context/SecurityContextCurrentUserAdapter.java` | `CurrentUserPort` 的实现 |
| `src/main/java/…/context/CurrentUserRunner.java` | 以某个用户的身份执行 |
| `src/main/java/…/error/SecurityErrorMappingContributor.java` | 安全异常到错误码的映射 |
| `src/main/java/…/error/SecurityProblemWriter.java` | 在过滤器链里输出 ProblemDetail |
| `src/main/java/…/error/SecurityExceptionRethrowAdvice.java` | 把控制器里抛出的安全异常原样抛回 |
| `src/main/java/…/audit/CurrentUserAuditorProvider.java` | 用当前用户 ID 作审计人 |
| `src/main/java/…/config/PatraSecurityProperties.java` | 配置属性 |
| `src/main/java/…/config/StatelessSecurityDefaults.java` | 可复用的无状态默认配置 |
| `src/main/java/…/config/SecurityCoreAutoConfiguration.java` | 注册 `CurrentUserPort` |
| `src/main/java/…/config/SecurityAuditingAutoConfiguration.java` | 注册审计人提供者 |
| `src/main/java/…/config/SecurityServletAutoConfiguration.java` | 注册过滤器链和错误输出 |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 自动配置清单 |
| `src/testFixtures/java/…/test/TestIdentity.java` | 构造已登录请求的请求头 |
| `src/testFixtures/java/…/test/TestGatewayTokenEnvironmentPostProcessor.java` | 测试里自动设置内部令牌 |
| `src/testFixtures/resources/META-INF/spring.factories` | 登记上面的后处理器 |
| `src/test/…` | 单元测试与自动配置测试（classpath 上没有 starter-jpa） |
| `src/integrationTest/…` | 整应用测试、切片测试（classpath 上有 starter-jpa，用 PostgreSQL 容器） |

### 改动的现有文件

| 文件 | 改动 |
|---|---|
| `settings.gradle.kts` | 加两条 `includeAt` |
| `spotbugs-exclude.xml` | 加一条针对新 starter 包的排除 |
| `linqibin-commons/linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/audit/CurrentAuditorProvider.java` | 新增接口 |
| `linqibin-commons/linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/autoconfig/JpaAuditingConfig.java` | 默认审计人改成向接口要 |
| `linqibin-commons/linqibin-spring-boot-starter-jpa/README.md` | 改写「自定义审计用户」一节 |
| `linqibin-commons/linqibin-spring-boot-starter-jpa/src/test/…`、`src/integrationTest/…` | 新增单元测试和集成测试 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java` | 优先级降一级 |
| `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandlerTest.java` | 加一个用例 |
| `patra-infra/cd/module-graph.json` | 重新生成 |
| `docs/patra/specs/2026-10-05-security-starter-design.md` | 写回实测结果和两处出入 |

## 任务列表

| # | 任务 | 模块 |
|---|---|---|
| 1 | 当前用户的抽象 | `patra-common-security`（新） |
| 2 | 「当前操作人」接口与默认审计人 | starter-jpa |
| 3 | 全局异常处理器降一级优先级 | starter-web |
| 4 | 模块骨架与请求头约定 | 安全 starter（新） |
| 5 | 认证对象、取当前用户、以某个用户的身份执行 | 安全 starter |
| 6 | 从请求头建立认证 | 安全 starter |
| 7 | 错误输出 | 安全 starter |
| 8 | 配置属性与自动配置 | 安全 starter |
| 9 | 测试支持 | 安全 starter |
| 10 | 整应用集成测试 | 安全 starter |
| 11 | 审计人接入 | 安全 starter |
| 12 | 切片测试与 README | 安全 starter |
| 13 | 构建接入、回归、spec 收尾 | 全仓库 |

---

### 任务 1：当前用户的抽象（`patra-common-security`）

**Files:**

- Modify: `settings.gradle.kts`
- Create: `patra-api/patra-common/patra-common-security/build.gradle.kts`
- Create: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/AccountType.java`
- Create: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/ClientType.java`
- Create: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/CurrentUser.java`
- Create: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/CurrentUserPort.java`
- Create: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/AuthenticationRequiredException.java`
- Test: `patra-api/patra-common/patra-common-security/src/test/java/dev/linqibin/patra/common/security/AccountTypeTest.java`
- Test: `patra-api/patra-common/patra-common-security/src/test/java/dev/linqibin/patra/common/security/ClientTypeTest.java`
- Test: `patra-api/patra-common/patra-common-security/src/test/java/dev/linqibin/patra/common/security/CurrentUserTest.java`
- Test: `patra-api/patra-common/patra-common-security/src/test/java/dev/linqibin/patra/common/security/CurrentUserPortTest.java`

**Interfaces:**

- Consumes: `dev.linqibin.commons.error.DomainException`（构造器 `DomainException(String message, ErrorTrait... traits)`）、`dev.linqibin.commons.error.trait.StandardErrorTrait.UNAUTHORIZED`。
- Produces（后面的任务都用这些）：
  - `enum AccountType { PORTAL }`，`String getCode()`，`static Optional<AccountType> fromCode(String code)`。`PORTAL` 的 code 是 `portal`。
  - `enum ClientType { WEB }`，`String getCode()`，`static Optional<ClientType> fromCode(String code)`。`WEB` 的 code 是 `web`。
  - `record CurrentUser(long userId, long sessionId, AccountType accountType, ClientType clientType) implements Serializable`，`static CurrentUser of(long, long, AccountType, ClientType)`。
  - `interface CurrentUserPort { Optional<CurrentUser> current(); default CurrentUser require(); }`
  - `class AuthenticationRequiredException extends DomainException`，无参构造器，消息固定为 `Authentication required`。

- [ ] **步骤 1：把模块接进构建**

在 `settings.gradle.kts` 的 `patra-api / patra-common` 一段里，紧挨着 `patra-common-enums` 那一行的后面加一行（必须在 `mapParent(":patra-api:patra-common", …)` 之前）：

```kotlin
includeAt(":patra-api:patra-common:patra-common-security", "patra-api/patra-common/patra-common-security")
```

新建 `patra-api/patra-common/patra-common-security/build.gradle.kts`：

```kotlin
/**
 * Patra Common Security
 *
 * 当前用户的抽象（纯 Java，domain 层可以依赖）：
 * - CurrentUser / AccountType / ClientType
 * - CurrentUserPort
 * - AuthenticationRequiredException
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.java-library")
}

dependencies {
    // 只依赖 commons-core（DomainException / StandardErrorTrait）
    api(project(":linqibin-commons:linqibin-commons-core"))
}

// 覆盖率要求 75%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:compileJava`
预期：`BUILD SUCCESSFUL`（模块里还没有源码，`compileJava` 显示 `NO-SOURCE`）。

- [ ] **步骤 2：写 `AccountType` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/common/security/AccountTypeTest.java`：

```java
package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// AccountType 单元测试。
@DisplayName("AccountType 单元测试")
class AccountTypeTest {

  @Test
  @DisplayName("portal 对应门户用户")
  void should_return_portal_when_code_is_portal() {
    assertThat(AccountType.fromCode("portal")).contains(AccountType.PORTAL);
    assertThat(AccountType.PORTAL.getCode()).isEqualTo("portal");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "PORTAL", "admin", " portal"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(AccountType.fromCode(code)).isEmpty();
  }
}
```

- [ ] **步骤 3：运行，确认失败**

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*AccountTypeTest"`
预期：`compileTestJava` 失败，报 `cannot find symbol … class AccountType`。

- [ ] **步骤 4：实现 `AccountType`**

新建 `src/main/java/dev/linqibin/patra/common/security/AccountType.java`：

```java
package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 账号类型。
///
/// 本版只有门户用户。后台账号在做 admin 时加入。
@Getter
@RequiredArgsConstructor
public enum AccountType {
  /// 门户用户。
  PORTAL("portal");

  /// 写进请求头和会话里的字符串形式。
  private final String code;

  /// 按字符串查找账号类型。
  ///
  /// @param code 字符串形式，区分大小写
  /// @return 匹配的账号类型；不认识或为 `null` 时返回空
  public static Optional<AccountType> fromCode(String code) {
    for (AccountType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*AccountTypeTest"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。

- [ ] **步骤 5：写 `ClientType` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/common/security/ClientTypeTest.java`：

```java
package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// ClientType 单元测试。
@DisplayName("ClientType 单元测试")
class ClientTypeTest {

  @Test
  @DisplayName("web 对应网页端")
  void should_return_web_when_code_is_web() {
    assertThat(ClientType.fromCode("web")).contains(ClientType.WEB);
    assertThat(ClientType.WEB.getCode()).isEqualTo("web");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "WEB", "app", " web"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(ClientType.fromCode(code)).isEmpty();
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*ClientTypeTest"`
预期：编译失败，`cannot find symbol … class ClientType`。

- [ ] **步骤 6：实现 `ClientType`**

新建 `src/main/java/dev/linqibin/patra/common/security/ClientType.java`：

```java
package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 客户端类型。
///
/// 本版只有网页端。移动端、小程序端在接入时加入。
@Getter
@RequiredArgsConstructor
public enum ClientType {
  /// 网页端。
  WEB("web");

  /// 写进请求头和会话里的字符串形式。
  private final String code;

  /// 按字符串查找客户端类型。
  ///
  /// @param code 字符串形式，区分大小写
  /// @return 匹配的客户端类型；不认识或为 `null` 时返回空
  public static Optional<ClientType> fromCode(String code) {
    for (ClientType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*ClientTypeTest"`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 7：写 `CurrentUser` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/common/security/CurrentUserTest.java`：

```java
package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// CurrentUser 单元测试。
@DisplayName("CurrentUser 单元测试")
class CurrentUserTest {

  @Test
  @DisplayName("工厂方法创建的当前用户带齐四个字段")
  void should_expose_all_fields_when_created_by_factory() {
    CurrentUser user = CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

    assertThat(user.userId()).isEqualTo(1001L);
    assertThat(user.sessionId()).isEqualTo(2001L);
    assertThat(user.accountType()).isEqualTo(AccountType.PORTAL);
    assertThat(user.clientType()).isEqualTo(ClientType.WEB);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("用户 ID 不是正数时拒绝创建")
  void should_reject_non_positive_user_id(long userId) {
    assertThatThrownBy(() -> CurrentUser.of(userId, 2001L, AccountType.PORTAL, ClientType.WEB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId");
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("会话 ID 不是正数时拒绝创建")
  void should_reject_non_positive_session_id(long sessionId) {
    assertThatThrownBy(() -> CurrentUser.of(1001L, sessionId, AccountType.PORTAL, ClientType.WEB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sessionId");
  }

  @Test
  @DisplayName("账号类型为空时拒绝创建")
  void should_reject_null_account_type() {
    assertThatThrownBy(() -> CurrentUser.of(1001L, 2001L, null, ClientType.WEB))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("accountType");
  }

  @Test
  @DisplayName("客户端类型为空时拒绝创建")
  void should_reject_null_client_type() {
    assertThatThrownBy(() -> CurrentUser.of(1001L, 2001L, AccountType.PORTAL, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("clientType");
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*CurrentUserTest"`
预期：编译失败，`cannot find symbol … class CurrentUser`。

- [ ] **步骤 8：实现 `CurrentUser`**

新建 `src/main/java/dev/linqibin/patra/common/security/CurrentUser.java`：

```java
package dev.linqibin.patra.common.security;

import java.io.Serializable;
import java.util.Objects;

/// 当前用户：一次操作里「是谁在做」的最小描述。
///
/// 不放邮箱等个人信息。实现 `Serializable`，是因为它会作为 Spring Security 认证对象的主体，
/// 而认证对象本身是可序列化的。
///
/// @param userId 用户 ID（雪花 Long，正数）
/// @param sessionId 会话 ID（正数），登出时靠它定位删哪条会话
/// @param accountType 账号类型
/// @param clientType 客户端类型
public record CurrentUser(
    long userId, long sessionId, AccountType accountType, ClientType clientType)
    implements Serializable {

  /// 校验两个 ID 为正数、两个枚举非空。
  public CurrentUser {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
  }

  /// 创建当前用户。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param accountType 账号类型
  /// @param clientType 客户端类型
  /// @return 当前用户
  public static CurrentUser of(
      long userId, long sessionId, AccountType accountType, ClientType clientType) {
    return new CurrentUser(userId, sessionId, accountType, clientType);
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*CurrentUserTest"`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 9：写 `CurrentUserPort` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/common/security/CurrentUserPortTest.java`：

```java
package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// CurrentUserPort 单元测试：`require()` 的默认实现。
@DisplayName("CurrentUserPort 单元测试")
class CurrentUserPortTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  @Test
  @DisplayName("有当前用户时 require 返回它")
  void should_return_user_when_require_and_user_present() {
    CurrentUserPort port = () -> Optional.of(USER);

    assertThat(port.require()).isEqualTo(USER);
  }

  @Test
  @DisplayName("没有当前用户时 require 抛出带 UNAUTHORIZED 特征的异常")
  void should_throw_authentication_required_when_require_and_no_user() {
    CurrentUserPort port = Optional::empty;

    assertThatThrownBy(port::require)
        .isInstanceOfSatisfying(
            AuthenticationRequiredException.class,
            exception -> {
              assertThat(exception.getErrorTraits())
                  .containsExactly(StandardErrorTrait.UNAUTHORIZED);
              assertThat(exception).hasMessage("Authentication required");
            });
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests "*CurrentUserPortTest"`
预期：编译失败，`cannot find symbol … class CurrentUserPort`。

- [ ] **步骤 10：实现端口和异常**

新建 `src/main/java/dev/linqibin/patra/common/security/AuthenticationRequiredException.java`：

```java
package dev.linqibin.patra.common.security;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 需要登录但取不到当前用户时抛出。
///
/// 带 `UNAUTHORIZED` 特征，现有的错误解析引擎会把它解析成 `{PREFIX}-0401`。
public class AuthenticationRequiredException extends DomainException {

  /// 创建异常。消息是固定的英文短句，会作为错误响应的 `detail` 返回给客户端。
  public AuthenticationRequiredException() {
    super("Authentication required", StandardErrorTrait.UNAUTHORIZED);
  }
}
```

新建 `src/main/java/dev/linqibin/patra/common/security/CurrentUserPort.java`：

```java
package dev.linqibin.patra.common.security;

import java.util.Optional;

/// 取当前用户的端口。
///
/// domain 和 app 层通过它得到「是谁在操作」，不依赖任何 Web 或安全框架。
/// 实现由 `patra-spring-boot-starter-security` 提供。
public interface CurrentUserPort {

  /// 返回当前用户。
  ///
  /// @return 当前用户；匿名请求、不带身份的后台线程返回空
  Optional<CurrentUser> current();

  /// 返回当前用户，没有时抛异常。需要登录的业务入口用它。
  ///
  /// @return 当前用户
  /// @throws AuthenticationRequiredException 没有当前用户时
  default CurrentUser require() {
    return current().orElseThrow(AuthenticationRequiredException::new);
  }
}
```

运行：`./gradlew :patra-api:patra-common:patra-common-security:test`
预期：`BUILD SUCCESSFUL`，四个测试类全部通过。

- [ ] **步骤 11：格式化并跑模块的全部检查**

运行：

```bash
./gradlew :patra-api:patra-common:patra-common-security:spotlessApply
```

```bash
./gradlew :patra-api:patra-common:patra-common-security:check
```

预期：`BUILD SUCCESSFUL`（包含 `spotlessCheck`、`spotbugsMain`、`test`）。

确认模块的项目依赖只有 commons-core：

```bash
./gradlew -q :patra-api:patra-common:patra-common-security:dependencies --configuration runtimeClasspath | grep "project :"
```

预期：只输出一行，`project :linqibin-commons:linqibin-commons-core`。

- [ ] **步骤 12：提交**

```bash
git add settings.gradle.kts patra-api/patra-common/patra-common-security
git commit -m "feat(security): 新增当前用户的抽象模块 patra-common-security (PAP-62)"
```

---

### 任务 2：「当前操作人」接口与默认审计人（starter-jpa）

`created_by`、`updated_by` 由 starter-jpa 里名为 `auditorAware` 的 Bean 决定填什么，它现在永远返回空。本任务让它去问一个新接口 `CurrentAuditorProvider`：容器里有实现就用它的返回值，没有就保持为空。

为什么不让安全 starter 直接提供一个 `auditorAware` 把默认的换掉：各服务的启动类扫描整个 `dev.linqibin`，`JpaAuditingConfig` 是个普通的 `@Configuration`，会被提前扫到并先注册；后来者想靠 `@ConditionalOnMissingBean` 抢位置，抢不到，而且不报错。改成注入就与注册顺序无关。

starter-jpa 现在没有集成测试目录，本任务新建。

**Files:**

- Create: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/audit/CurrentAuditorProvider.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/autoconfig/JpaAuditingConfig.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-jpa/README.md`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/test/java/dev/linqibin/starter/jpa/autoconfig/JpaAuditingConfigTest.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/java/dev/linqibin/starter/jpa/JpaITBootstrap.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/java/dev/linqibin/starter/jpa/support/JpaITAuditedNoteEntity.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/java/dev/linqibin/starter/jpa/support/JpaITAuditedNoteDao.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/java/dev/linqibin/starter/jpa/audit/JpaAuditingIT.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/java/dev/linqibin/starter/jpa/audit/JpaAuditingWithProviderIT.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-jpa/src/integrationTest/resources/db/migration/V1__create_jpa_it_audited_note.sql`

**Interfaces:**

- Consumes: `dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer`（starter-test 提供，起一个 JVM 内共享的 PostgreSQL 17 容器并注入数据源配置）、`dev.linqibin.starter.jpa.entity.BaseJpaEntity`、`dev.linqibin.starter.jpa.id.SnowflakeIdGenerator.getId()`。
- Produces：
  - `dev.linqibin.starter.jpa.audit.CurrentAuditorProvider`：`Optional<Long> currentAuditorId()`。任务 11 实现它。
  - `JpaAuditingConfig.auditorAware(ObjectProvider<CurrentAuditorProvider>)`。Bean 名仍是 `auditorAware`，仍带 `@ConditionalOnMissingBean`。

- [ ] **步骤 1：搭集成测试的脚手架，先给现状拍一张基线**

确认 Docker 在运行：`docker info`（能输出 Server 信息即可）。

新建 `src/integrationTest/resources/db/migration/V1__create_jpa_it_audited_note.sql`：

```sql
-- starter-jpa 集成测试专用表：列与 BaseJpaEntity 一一对应
CREATE TABLE jpa_it_audited_note
(
    id              BIGINT         NOT NULL,
    content         VARCHAR(200)   NULL,
    record_remarks  jsonb          NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT         NULL,
    created_by_name VARCHAR(100)   NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NULL,
    updated_by_name VARCHAR(100)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    ip_address      bytea          NULL,
    PRIMARY KEY (id)
);
```

新建 `src/integrationTest/java/dev/linqibin/starter/jpa/JpaITBootstrap.java`：

```java
package dev.linqibin.starter.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的最小 Spring Boot 应用。
///
/// 默认扫描 `dev.linqibin.starter.jpa`，所以 `JpaAuditingConfig` 会像在真实服务里一样
/// 被组件扫描提前注册。
@SpringBootApplication
class JpaITBootstrap {}
```

新建 `src/integrationTest/java/dev/linqibin/starter/jpa/support/JpaITAuditedNoteEntity.java`：

```java
package dev.linqibin.starter.jpa.support;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：只为观察审计列，映射到表 `jpa_it_audited_note`。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_audited_note")
public class JpaITAuditedNoteEntity extends BaseJpaEntity {

  /// 任意内容。
  @Column(name = "content", length = 200)
  private String content;
}
```

新建 `src/integrationTest/java/dev/linqibin/starter/jpa/support/JpaITAuditedNoteDao.java`：

```java
package dev.linqibin.starter.jpa.support;

import org.springframework.data.jpa.repository.JpaRepository;

/// 集成测试专用实体的 JPA Repository。
public interface JpaITAuditedNoteDao extends JpaRepository<JpaITAuditedNoteEntity, Long> {}
```

新建 `src/integrationTest/java/dev/linqibin/starter/jpa/audit/JpaAuditingIT.java`：

```java
package dev.linqibin.starter.jpa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteDao;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteEntity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/// JPA 审计集成测试：容器里没有操作人提供者时的行为。
///
/// 这是「没引安全 starter 的服务行为不变」的回归基线。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("JPA 审计集成测试（没有操作人提供者）")
class JpaAuditingIT {

  @Autowired private JpaITAuditedNoteDao noteDao;

  @Test
  @DisplayName("没有操作人提供者时，操作人两列为空，时间两列照常填充")
  void should_leave_auditor_columns_empty_and_fill_timestamps_when_no_provider() {
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("no provider");

    noteDao.saveAndFlush(note);

    JpaITAuditedNoteEntity saved = noteDao.findById(note.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isNull();
    assertThat(saved.getUpdatedBy()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }
}
```

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:integrationTest --tests "*JpaAuditingIT"`
预期：`BUILD SUCCESSFUL`。这一步在改动之前就应该通过：它记录的是现状，后面的改动不能让它变红。

- [ ] **步骤 2：写「有操作人提供者」的集成测试**

新建 `src/integrationTest/java/dev/linqibin/starter/jpa/audit/JpaAuditingWithProviderIT.java`：

```java
package dev.linqibin.starter.jpa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteDao;
import dev.linqibin.starter.jpa.support.JpaITAuditedNoteEntity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

/// JPA 审计集成测试：容器里有操作人提供者时的行为。
///
/// 测试应用会像真实服务一样把 `JpaAuditingConfig` 提前扫描进来，
/// 这里验证后注册的提供者仍然生效。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("JPA 审计集成测试（有操作人提供者）")
class JpaAuditingWithProviderIT {

  private static final long AUDITOR_ID = 42L;

  @Autowired private JpaITAuditedNoteDao noteDao;

  @Test
  @DisplayName("有操作人提供者时，操作人两列是它返回的 ID")
  void should_fill_auditor_columns_from_provider() {
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("with provider");

    noteDao.saveAndFlush(note);

    JpaITAuditedNoteEntity saved = noteDao.findById(note.getId()).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(AUDITOR_ID);
    assertThat(saved.getUpdatedBy()).isEqualTo(AUDITOR_ID);
  }

  /// 提供一个固定的操作人。
  @TestConfiguration
  static class FixedAuditorConfig {

    /// 固定返回同一个操作人 ID 的提供者。
    ///
    /// @return 操作人提供者
    @Bean
    CurrentAuditorProvider fixedAuditorProvider() {
      return () -> Optional.of(AUDITOR_ID);
    }
  }
}
```

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:integrationTest --tests "*JpaAuditingWithProviderIT"`
预期：编译失败，`cannot find symbol … class CurrentAuditorProvider`。

- [ ] **步骤 3：只加接口，看到测试因为正确的原因失败**

新建 `src/main/java/dev/linqibin/starter/jpa/audit/CurrentAuditorProvider.java`：

```java
package dev.linqibin.starter.jpa.audit;

import java.util.Optional;

/// 当前操作人提供者：告诉 JPA 审计「这次写入是谁做的」。
///
/// 容器里有这个接口的实现时，`created_by` / `updated_by` 填它返回的 ID；
/// 没有实现时两列为空。实现方通常是安全模块，从当前登录用户取 ID。
@FunctionalInterface
public interface CurrentAuditorProvider {

  /// 返回当前操作人的 ID。
  ///
  /// @return 操作人 ID；匿名请求、不带身份的后台线程返回空
  Optional<Long> currentAuditorId();
}
```

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:integrationTest --tests "*JpaAuditingWithProviderIT"`
预期：测试失败，断言信息是 `createdBy` 期望 `42L`、实际 `null`。接口有了，但默认审计人还没去问它。

- [ ] **步骤 4：写默认审计人的单元测试**

新建 `src/test/java/dev/linqibin/starter/jpa/autoconfig/JpaAuditingConfigTest.java`：

```java
package dev.linqibin.starter.jpa.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.AuditorAware;

/// JpaAuditingConfig 单元测试：默认审计人怎么接入 CurrentAuditorProvider。
@DisplayName("JpaAuditingConfig 单元测试")
class JpaAuditingConfigTest {

  private final JpaAuditingConfig config = new JpaAuditingConfig();
  private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

  @Test
  @DisplayName("容器里没有操作人提供者时，审计人为空")
  void should_return_empty_auditor_when_no_provider() {
    AuditorAware<Long> auditorAware =
        config.auditorAware(beanFactory.getBeanProvider(CurrentAuditorProvider.class));

    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }

  @Test
  @DisplayName("容器里有操作人提供者时，每次都向它要当前操作人")
  void should_ask_provider_on_every_call_when_provider_present() {
    AtomicReference<Optional<Long>> currentAuditor = new AtomicReference<>(Optional.of(42L));
    CurrentAuditorProvider provider = currentAuditor::get;
    beanFactory.registerSingleton("currentAuditorProvider", provider);

    AuditorAware<Long> auditorAware =
        config.auditorAware(beanFactory.getBeanProvider(CurrentAuditorProvider.class));

    assertThat(auditorAware.getCurrentAuditor()).contains(42L);
    currentAuditor.set(Optional.empty());
    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }
}
```

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:test --tests "*JpaAuditingConfigTest"`
预期：编译失败，`method auditorAware in class JpaAuditingConfig cannot be applied to given types`（现在的方法没有参数）。

- [ ] **步骤 5：改默认审计人**

修改 `src/main/java/dev/linqibin/starter/jpa/autoconfig/JpaAuditingConfig.java`。

在 import 区加两行：

```java
import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import org.springframework.beans.factory.ObjectProvider;
```

把类注释里「扩展点」的第一条：

```java
/// - 应用可以自定义 `AuditorAware<Long>` Bean 来提供真实的用户 ID（如从 SecurityContext 获取）
```

换成：

```java
/// - 提供一个 {@link CurrentAuditorProvider} Bean 来告诉审计「当前操作人是谁」
///   （安全 starter 已经内置了实现）
```

把整个 `auditorAware()` 方法（连同它上面的注释和方法体里的 TODO 注释）：

```java
  /// 默认的审计用户提供者。
  ///
  /// 返回空 Optional，表示系统操作（无用户上下文）。
  /// 应用应该覆盖此 Bean 以从安全上下文获取实际用户 ID。
  ///
  /// @return 审计用户提供者
  @Bean
  @ConditionalOnMissingBean
  public AuditorAware<Long> auditorAware() {
    // TODO: 从安全上下文获取当前用户 ID
    // 示例: return () -> Optional.ofNullable(SecurityContextHolder.getContext())
    //           .map(SecurityContext::getAuthentication)
    //           .filter(Authentication::isAuthenticated)
    //           .map(auth -> ((UserDetails) auth.getPrincipal()).getId());
    return () -> Optional.empty();
  }
```

换成：

```java
  /// 默认的审计用户提供者。
  ///
  /// 容器里有 {@link CurrentAuditorProvider} 的实现就问它；没有就返回空，表示系统操作。
  /// 用注入来接入，而不是让别的模块提供同名 Bean 来替换，所以不受配置类加载顺序影响。
  ///
  /// @param currentAuditorProvider 当前操作人提供者（可能不存在）
  /// @return 审计用户提供者
  @Bean
  @ConditionalOnMissingBean
  public AuditorAware<Long> auditorAware(
      ObjectProvider<CurrentAuditorProvider> currentAuditorProvider) {
    CurrentAuditorProvider provider = currentAuditorProvider.getIfAvailable();
    if (provider == null) {
      return Optional::empty;
    }
    return provider::currentAuditorId;
  }
```

- [ ] **步骤 6：运行，确认三处都通过**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:test --tests "*JpaAuditingConfigTest"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:integrationTest`
预期：`BUILD SUCCESSFUL`。`JpaAuditingWithProviderIT` 变绿，`JpaAuditingIT`（基线）仍然是绿的。

- [ ] **步骤 7：更新 README**

修改 `linqibin-commons/linqibin-spring-boot-starter-jpa/README.md`，三处。

第一处，「组件说明」表格里，在 `JpaAuditingConfig` 那一行的下面加一行：

```markdown
| `CurrentAuditorProvider` | 当前操作人提供者接口，审计列的操作人 ID 从它来 |
```

第二处，把「### 自定义审计用户」整个小节（标题、一句说明、一个 java 代码块，到「### 自定义时钟（测试用）」之前为止）换成：

````markdown
### 接入当前操作人

默认的 `AuditorAware` 会向容器里的 `CurrentAuditorProvider` 要当前操作人。容器里没有这个接口的实现时返回空（系统操作），`created_by` / `updated_by` 两列留空。

引入了 `patra-spring-boot-starter-security` 的服务不用写任何代码：它自带一个实现，从当前登录用户取 ID。操作人来自别处时，自己提供一个 Bean：

```java
@Bean
public CurrentAuditorProvider currentAuditorProvider() {
    return () -> Optional.of(SYSTEM_OPERATOR_ID);
}
```

不要用「自己声明一个 `auditorAware` Bean」的办法来替换默认实现：各服务的启动类扫描整个 `dev.linqibin`，`JpaAuditingConfig` 会被提前扫到，默认的那个总是先注册。
````

第三处，「包结构」的目录树里，在 `├── autoconfig/` 这一组之前加：

```
├── audit/
│   └── CurrentAuditorProvider          # 当前操作人提供者接口
```

- [ ] **步骤 8：格式化并跑模块的全部检查**

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:spotlessApply
```

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:check :linqibin-commons:linqibin-spring-boot-starter-jpa:integrationTest :linqibin-commons:linqibin-spring-boot-starter-jpa:checkBoundary
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 9：提交**

```bash
git add linqibin-commons/linqibin-spring-boot-starter-jpa
git commit -m "feat(jpa): 新增当前操作人接口，默认审计人改为向它取值 (PAP-62)"
```

---

### 任务 3：全局异常处理器降一级优先级（starter-web）

`GlobalRestExceptionHandler` 现在以最高优先级兜住所有异常。控制器里抛出的 Spring Security 异常会被它接走，变成 500，到不了安全过滤器。任务 7 会加一个最高优先级的处理器把这类异常原样抛回；本任务先给它腾出位置。

仓库里只有这一个 `@ControllerAdvice`，降一级不会和别的处理器撞优先级。

**Files:**

- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandlerTest.java`

**Interfaces:**

- Consumes: 无。
- Produces: `GlobalRestExceptionHandler` 的优先级是 `Ordered.HIGHEST_PRECEDENCE + 1`。任务 7 的 `SecurityExceptionRethrowAdvice` 用 `Ordered.HIGHEST_PRECEDENCE` 排在它前面。

- [ ] **步骤 1：写失败的测试**

在 `GlobalRestExceptionHandlerTest.java` 里加一个用例（放在 `shouldHandleGenericException` 之前；沿用这个文件已有的 camelCase 方法名写法）：

```java
  @Test
  @DisplayName("优先级应比最高优先级低一级，给安全异常处理器留出位置")
  void shouldRankOneBelowHighestPrecedence() {
    Order order = AnnotationUtils.findAnnotation(GlobalRestExceptionHandler.class, Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
  }
```

在 import 区加：

```java
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test --tests "*GlobalRestExceptionHandlerTest"`
预期：`shouldRankOneBelowHighestPrecedence` 失败，期望 `-2147483647`，实际 `-2147483648`。

- [ ] **步骤 3：改优先级**

在 `GlobalRestExceptionHandler.java` 里，把类上的注解：

```java
@Order(Ordered.HIGHEST_PRECEDENCE)
```

换成：

```java
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
```

把类注释里的这一行：

```java
/// **优先级:** {@link Ordered#HIGHEST_PRECEDENCE},确保在其他异常处理器之前执行。
```

换成：

```java
/// **优先级:** 比 {@link Ordered#HIGHEST_PRECEDENCE} 低一级。最高的位置留给安全 starter
/// 的处理器：它只把 Spring Security 的异常原样抛回安全过滤器，其余异常仍由本类兜底。
```

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test --tests "*GlobalRestExceptionHandlerTest"`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：格式化并跑模块的全部检查**

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:spotlessApply
```

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:check :linqibin-commons:linqibin-spring-boot-starter-web:checkBoundary
```

预期：`BUILD SUCCESSFUL`。各服务的切片测试会在任务 13 的全量回归里一起跑。

- [ ] **步骤 6：提交**

```bash
git add linqibin-commons/linqibin-spring-boot-starter-web
git commit -m "refactor(web): 全局异常处理器降一级优先级，给安全异常处理器让位 (PAP-62)"
```

---

### 任务 4：模块骨架与请求头约定（安全 starter）

建立 `patra-spring-boot-starter-security` 模块，交付网关和下游共用的请求头约定：五个头的名字，四个身份头的解析与写入。

构建脚本一次写全，后面的任务不再改它。三个依赖安排要留意：

- `linqibin-spring-boot-starter-jpa` 在主代码里是 `compileOnly`：没有 starter-jpa 的服务也能用本 starter。
- 单元测试（`src/test`）的 classpath 上因此没有 starter-jpa，任务 11 正好用它验证「没有 starter-jpa 时能正常启动」。
- 集成测试（`src/integrationTest`）的 classpath 上显式加回 starter-jpa，用来测审计列。

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `spotbugs-exclude.xml`
- Create: `patra-starters/patra-spring-boot-starter-security/build.gradle.kts`
- Create: `patra-starters/patra-spring-boot-starter-security/src/test/resources/logback-test.xml`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/header/IdentityParseResult.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/header/IdentityHeaders.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/header/IdentityHeadersTest.java`

**Interfaces:**

- Consumes: 任务 1 的 `CurrentUser`、`AccountType`、`ClientType`；`org.springframework.http.HttpHeaders`（Spring 7 里的方法：`getOrEmpty(String)` 返回 `List<String>`，`getFirst(String)`，`set(String, String)` 覆盖，`add(String, String)` 追加，`remove(String)`）。
- Produces：
  - `IdentityHeaders` 的常量：`USER_ID`、`SESSION_ID`、`ACCOUNT_TYPE`、`CLIENT_TYPE`、`GATEWAY_TOKEN`（都是 `String`），`IDENTITY_HEADER_NAMES`（`List<String>`，前四个）。
  - `static IdentityParseResult IdentityHeaders.parse(HttpHeaders headers)`
  - `static void IdentityHeaders.write(HttpHeaders headers, CurrentUser user)`
  - `sealed interface IdentityParseResult`，三个实现：`record Identified(CurrentUser user)`、`record Absent()`、`record Malformed(String reason)`；三个静态工厂：`identified(CurrentUser)`、`absent()`、`malformed(String)`。

- [ ] **步骤 1：把模块接进构建**

在 `settings.gradle.kts` 的 `patra-starters` 一段里，紧挨着 `patra-spring-boot-starter-expr` 那一行的后面加一行（必须在 `mapParent(":patra-starters", …)` 之前）：

```kotlin
includeAt(":patra-starters:patra-spring-boot-starter-security", "patra-starters/patra-spring-boot-starter-security")
```

新建 `patra-starters/patra-spring-boot-starter-security/build.gradle.kts`：

```kotlin
/**
 * Patra Spring Boot Starter - Security
 *
 * 基于 Spring Security 的当前用户识别（只做 servlet 一套）：
 * - 从网关传来的身份头建立认证
 * - 401 / 403 输出为统一的 ProblemDetail
 * - JPA 审计人接入
 *
 * Spring Security 只经本模块进入 classpath：只有需要识别用户的服务才引它。
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // 当前用户的抽象
    api(project(":patra-api:patra-common:patra-common-security"))

    // 统一错误格式（ProblemDetailAdapter、GlobalRestExceptionHandler）
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    // Spring Security（版本由 Spring Boot BOM 管理）
    api("org.springframework.boot:spring-boot-starter-security")

    // 审计人接入：classpath 上有 starter-jpa 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))

    // 测试支持（testFixtures）：没有它，使用方的 @WebMvcTest 里没有安全过滤器
    "testFixturesApi"("org.springframework.boot:spring-boot-security-test")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))

    // 集成测试要测审计列，需要 starter-jpa；单元测试的 classpath 故意不带它
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
}
```

新建 `patra-starters/patra-spring-boot-starter-security/src/test/resources/logback-test.xml`（与其他 starter 的写法一致）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Property for application name -->
    <property name="appName" value="patra-spring-boot-starter-security"/>

    <!-- Console Appender: 测试专用配置,使用 %nopex 抑制堆栈跟踪 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <!-- %nopex: 不输出异常堆栈跟踪,只显示异常消息,避免测试输出被大量堆栈淹没 -->
            <pattern>%d{HH:mm:ss.SSS} %5p [${appName}] [%t] %-40.40logger{39} : %m%nopex%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- Root Logger: WARN level (减少测试输出) -->
    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>

    <!-- Application Logger: INFO level (only for dev.linqibin.* classes) -->
    <logger name="dev.linqibin" level="INFO" additivity="false">
        <appender-ref ref="CONSOLE"/>
    </logger>

</configuration>
```

在 `spotbugs-exclude.xml` 里，紧挨着「Expr 编译器内部类」那个 `<Match>` 块的后面加一块：

```xml
  <!-- Security starter 基础设施类：持有注入的依赖是设计意图 -->
  <Match>
    <Package name="~dev\.linqibin\.patra\.starter\.security(\..*)?"/>
    <Or>
      <Bug pattern="EI_EXPOSE_REP"/>
      <Bug pattern="EI_EXPOSE_REP2"/>
    </Or>
  </Match>
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:compileJava`
预期：`BUILD SUCCESSFUL`（模块里还没有源码，`compileJava` 显示 `NO-SOURCE`）。Spring Security 的依赖会在步骤 2 第一次编译时下载。

- [ ] **步骤 2：写「写入后能解析回来」的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/header/IdentityHeadersTest.java`：

```java
package dev.linqibin.patra.starter.security.header;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/// IdentityHeaders 单元测试：身份头的读写约定。
@DisplayName("IdentityHeaders 单元测试")
class IdentityHeadersTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  private static HttpHeaders validHeaders() {
    HttpHeaders headers = new HttpHeaders();
    IdentityHeaders.write(headers, USER);
    return headers;
  }

  @Test
  @DisplayName("写入的四个身份头能原样解析回当前用户")
  void should_parse_back_user_written_by_write() {
    HttpHeaders headers = new HttpHeaders();

    IdentityHeaders.write(headers, USER);

    assertThat(headers.getFirst(IdentityHeaders.USER_ID)).isEqualTo("1001");
    assertThat(headers.getFirst(IdentityHeaders.SESSION_ID)).isEqualTo("2001");
    assertThat(headers.getFirst(IdentityHeaders.ACCOUNT_TYPE)).isEqualTo("portal");
    assertThat(headers.getFirst(IdentityHeaders.CLIENT_TYPE)).isEqualTo("web");
    assertThat(IdentityHeaders.parse(headers)).isEqualTo(IdentityParseResult.identified(USER));
  }

  @Test
  @DisplayName("写入会覆盖已有的同名头，不是追加")
  void should_overwrite_existing_headers_when_write() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(IdentityHeaders.USER_ID, "999");
    headers.add(IdentityHeaders.USER_ID, "998");

    IdentityHeaders.write(headers, USER);

    assertThat(headers.getOrEmpty(IdentityHeaders.USER_ID)).containsExactly("1001");
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：编译失败，`cannot find symbol … class IdentityHeaders`。

- [ ] **步骤 3：实现请求头约定的主干**

新建 `src/main/java/dev/linqibin/patra/starter/security/header/IdentityParseResult.java`：

```java
package dev.linqibin.patra.starter.security.header;

import dev.linqibin.patra.common.security.CurrentUser;

/// 解析身份头的三种结果。
public sealed interface IdentityParseResult {

  /// 四个身份头齐全且合法。
  ///
  /// @param user 解析出的当前用户
  /// @return 结果
  static IdentityParseResult identified(CurrentUser user) {
    return new Identified(user);
  }

  /// 四个身份头都没有。
  ///
  /// @return 结果
  static IdentityParseResult absent() {
    return new Absent();
  }

  /// 身份头只有一部分、出现多次或格式不合法。
  ///
  /// @param reason 原因，只含头名，不含头的值
  /// @return 结果
  static IdentityParseResult malformed(String reason) {
    return new Malformed(reason);
  }

  /// 四个身份头齐全且合法。
  ///
  /// @param user 解析出的当前用户
  record Identified(CurrentUser user) implements IdentityParseResult {}

  /// 四个身份头都没有。
  record Absent() implements IdentityParseResult {}

  /// 身份头只有一部分、出现多次或格式不合法。
  ///
  /// @param reason 原因，只含头名，不含头的值
  record Malformed(String reason) implements IdentityParseResult {}
}
```

新建 `src/main/java/dev/linqibin/patra/starter/security/header/IdentityHeaders.java`（先只实现能让步骤 2 通过的部分：四个头齐全且合法的情况）：

```java
package dev.linqibin.patra.starter.security.header;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpHeaders;

/// 网关向下游传递身份的请求头约定。网关和下游用同一个类读写。
///
/// 五个头都是单值。身份头没有签名：安全性依赖服务不对外暴露和内部令牌不泄露。
public final class IdentityHeaders {

  /// 用户 ID，十进制字符串。
  public static final String USER_ID = "X-Patra-User-Id";

  /// 会话 ID，十进制字符串。
  public static final String SESSION_ID = "X-Patra-Session-Id";

  /// 账号类型，`AccountType` 的字符串形式。
  public static final String ACCOUNT_TYPE = "X-Patra-Account-Type";

  /// 客户端类型，`ClientType` 的字符串形式。
  public static final String CLIENT_TYPE = "X-Patra-Client-Type";

  /// 网关与下游共享的内部令牌。
  public static final String GATEWAY_TOKEN = "X-Patra-Gateway-Token";

  /// 四个身份头的名字（不含内部令牌头）。
  public static final List<String> IDENTITY_HEADER_NAMES =
      List.of(USER_ID, SESSION_ID, ACCOUNT_TYPE, CLIENT_TYPE);

  /// 工具类，不允许实例化。
  private IdentityHeaders() {}

  /// 从一组请求头解析身份。
  ///
  /// @param headers 请求头
  /// @return 三种结果之一：当前用户、没有身份、格式不合法
  public static IdentityParseResult parse(HttpHeaders headers) {
    return IdentityParseResult.identified(
        CurrentUser.of(
            Long.parseLong(headers.getFirst(USER_ID)),
            Long.parseLong(headers.getFirst(SESSION_ID)),
            AccountType.fromCode(headers.getFirst(ACCOUNT_TYPE)).orElseThrow(),
            ClientType.fromCode(headers.getFirst(CLIENT_TYPE)).orElseThrow()));
  }

  /// 把当前用户写成四个身份头。已有的同名头会被覆盖。
  ///
  /// @param headers 要写入的请求头
  /// @param user 当前用户
  public static void write(HttpHeaders headers, CurrentUser user) {
    headers.set(USER_ID, Long.toString(user.userId()));
    headers.set(SESSION_ID, Long.toString(user.sessionId()));
    headers.set(ACCOUNT_TYPE, user.accountType().getCode());
    headers.set(CLIENT_TYPE, user.clientType().getCode());
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

- [ ] **步骤 4：写「没有、残缺、重复」的失败测试**

在 `IdentityHeadersTest` 里追加三个用例，并在 import 区加 `org.junit.jupiter.params.ParameterizedTest` 和 `org.junit.jupiter.params.provider.ValueSource`：

```java
  @Test
  @DisplayName("四个身份头都没有时是「没有身份」")
  void should_be_absent_when_no_identity_headers() {
    assertThat(IdentityHeaders.parse(new HttpHeaders())).isEqualTo(IdentityParseResult.absent());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        IdentityHeaders.USER_ID,
        IdentityHeaders.SESSION_ID,
        IdentityHeaders.ACCOUNT_TYPE,
        IdentityHeaders.CLIENT_TYPE
      })
  @DisplayName("四个身份头缺任何一个都是不合法")
  void should_be_malformed_when_any_identity_header_missing(String missing) {
    HttpHeaders headers = validHeaders();
    headers.remove(missing);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        IdentityHeaders.USER_ID,
        IdentityHeaders.SESSION_ID,
        IdentityHeaders.ACCOUNT_TYPE,
        IdentityHeaders.CLIENT_TYPE
      })
  @DisplayName("同一个身份头出现多次是不合法")
  void should_be_malformed_when_identity_header_repeated(String repeated) {
    HttpHeaders headers = validHeaders();
    headers.add(repeated, headers.getFirst(repeated));

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：新加的用例失败。没有身份头和缺头的用例抛 `NumberFormatException` 或 `NoSuchElementException`；重复头的用例得到的是 `Identified`。

- [ ] **步骤 5：实现「没有、残缺、重复」**

把 `IdentityHeaders.parse` 换成：

```java
  public static IdentityParseResult parse(HttpHeaders headers) {
    int present = 0;
    for (String name : IDENTITY_HEADER_NAMES) {
      List<String> values = headers.getOrEmpty(name);
      if (values.size() > 1) {
        return IdentityParseResult.malformed(name + " 出现多次");
      }
      present += values.size();
    }
    if (present == 0) {
      return IdentityParseResult.absent();
    }
    if (present < IDENTITY_HEADER_NAMES.size()) {
      return IdentityParseResult.malformed("身份头不完整");
    }
    return IdentityParseResult.identified(
        CurrentUser.of(
            Long.parseLong(headers.getFirst(USER_ID)),
            Long.parseLong(headers.getFirst(SESSION_ID)),
            AccountType.fromCode(headers.getFirst(ACCOUNT_TYPE)).orElseThrow(),
            ClientType.fromCode(headers.getFirst(CLIENT_TYPE)).orElseThrow()));
  }
```

（方法上面的 `///` 注释保持不变。）

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：写「值不合法」的失败测试（Review Focus 第 3 条）**

在 `IdentityHeadersTest` 里追加：

```java
  @ParameterizedTest
  @ValueSource(
      strings = {
        "", "0", "-1", "+5", "007", "１２３", "1.0", "abc", " 12", "12 ", "9223372036854775808"
      })
  @DisplayName("用户 ID 不是规范写法的正整数时不合法")
  void should_be_malformed_when_user_id_is_not_canonical_positive_long(String userId) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, userId);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "0", "-1", "007", "abc", "9223372036854775808"})
  @DisplayName("会话 ID 不是规范写法的正整数时不合法")
  void should_be_malformed_when_session_id_is_not_canonical_positive_long(String sessionId) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.SESSION_ID, sessionId);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @Test
  @DisplayName("Long 的最大值是合法的 ID")
  void should_accept_long_max_value_as_id() {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, "9223372036854775807");

    assertThat(IdentityHeaders.parse(headers))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(Long.MAX_VALUE, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "PORTAL", "admin"})
  @DisplayName("账号类型不认识时不合法")
  void should_be_malformed_when_account_type_unknown(String accountType) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.ACCOUNT_TYPE, accountType);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "WEB", "app"})
  @DisplayName("客户端类型不认识时不合法")
  void should_be_malformed_when_client_type_unknown(String clientType) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.CLIENT_TYPE, clientType);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @Test
  @DisplayName("不合法的原因里只有头名，没有头的值")
  void should_not_leak_header_values_in_malformed_reason() {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, "not-a-number");

    assertThat(IdentityHeaders.parse(headers))
        .isInstanceOfSatisfying(
            IdentityParseResult.Malformed.class,
            malformed ->
                assertThat(malformed.reason())
                    .contains(IdentityHeaders.USER_ID)
                    .doesNotContain("not-a-number"));
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：新加的用例大多失败：非数字的值抛 `NumberFormatException`，不认识的枚举值抛 `NoSuchElementException`，`+5` 和 `007` 被宽松地解析成了用户 ID。

- [ ] **步骤 7：实现严格的值校验**

把 `IdentityHeaders` 改成最终形态。import 区最终是：

```java
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
```

在 `IDENTITY_HEADER_NAMES` 常量下面加一个常量：

```java
  /// 规范写法的正整数：没有符号、没有前导零、只有 ASCII 数字、最多 19 位。
  private static final Pattern POSITIVE_LONG = Pattern.compile("[1-9][0-9]{0,18}");
```

把 `parse` 方法里最后那个 `return IdentityParseResult.identified(…)` 语句换成：

```java
    OptionalLong userId = parsePositiveLong(headers.getFirst(USER_ID));
    if (userId.isEmpty()) {
      return IdentityParseResult.malformed(USER_ID + " 不是正整数");
    }
    OptionalLong sessionId = parsePositiveLong(headers.getFirst(SESSION_ID));
    if (sessionId.isEmpty()) {
      return IdentityParseResult.malformed(SESSION_ID + " 不是正整数");
    }
    Optional<AccountType> accountType = AccountType.fromCode(headers.getFirst(ACCOUNT_TYPE));
    if (accountType.isEmpty()) {
      return IdentityParseResult.malformed(ACCOUNT_TYPE + " 的值不认识");
    }
    Optional<ClientType> clientType = ClientType.fromCode(headers.getFirst(CLIENT_TYPE));
    if (clientType.isEmpty()) {
      return IdentityParseResult.malformed(CLIENT_TYPE + " 的值不认识");
    }
    return IdentityParseResult.identified(
        CurrentUser.of(
            userId.getAsLong(), sessionId.getAsLong(), accountType.get(), clientType.get()));
```

在 `parse` 和 `write` 之间加一个私有方法：

```java
  /// 把规范写法的正整数解析成 long。
  ///
  /// @param value 请求头的值，可能为 `null`
  /// @return 解析结果；写法不规范或超出 Long 范围时返回空
  private static OptionalLong parsePositiveLong(String value) {
    if (value == null || !POSITIVE_LONG.matcher(value).matches()) {
      return OptionalLong.empty();
    }
    try {
      return OptionalLong.of(Long.parseLong(value));
    } catch (NumberFormatException overflow) {
      return OptionalLong.empty();
    }
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityHeadersTest"`
预期：`BUILD SUCCESSFUL`，全部用例通过。

- [ ] **步骤 8：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 9：提交**

```bash
git add settings.gradle.kts spotbugs-exclude.xml patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 新增安全 starter 模块与身份请求头约定 (PAP-62)"
```

---

### 任务 5：认证对象、取当前用户、以某个用户的身份执行（安全 starter）

三个小类，都围绕 Spring Security 的安全上下文：

- `CurrentUserAuthentication`：放进安全上下文的认证对象。网关查到会话后构造它，下游从请求头解析后构造它。
- `SecurityContextCurrentUserAdapter`：`CurrentUserPort` 的实现，从安全上下文里取。
- `CurrentUserRunner`：给定时任务、消息消费用，在一段代码执行期间临时带上某个用户的身份。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthentication.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/context/SecurityContextCurrentUserAdapter.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/context/CurrentUserRunner.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthenticationTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/context/SecurityContextCurrentUserAdapterTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/context/CurrentUserRunnerTest.java`

**Interfaces:**

- Consumes: 任务 1 的 `CurrentUser`、`CurrentUserPort`。Spring Security 7.0.7 的 `AbstractAuthenticationToken`（构造器接收 `Collection<? extends GrantedAuthority>`）、`AuthorityUtils.NO_AUTHORITIES`、`SecurityContextHolder.getContextHolderStrategy()`。
- Produces：
  - `final class CurrentUserAuthentication extends AbstractAuthenticationToken`：构造器 `(CurrentUser)` 和 `(CurrentUser, Collection<? extends GrantedAuthority>)`；`CurrentUser getPrincipal()`；`getCredentials()` 返回 `null`；`getName()` 返回用户 ID 的字符串。
  - `class SecurityContextCurrentUserAdapter implements CurrentUserPort`，无参构造器。
  - `CurrentUserRunner.runAs(CurrentUser user, Runnable action)`、`<T> T CurrentUserRunner.callAs(CurrentUser user, Supplier<T> action)`。

- [ ] **步骤 1：写 `CurrentUserAuthentication` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthenticationTest.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/// CurrentUserAuthentication 单元测试。
@DisplayName("CurrentUserAuthentication 单元测试")
class CurrentUserAuthenticationTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  @Test
  @DisplayName("构造出来就是已认证状态，主体是当前用户，没有凭据和权限")
  void should_be_authenticated_with_user_as_principal() {
    CurrentUserAuthentication authentication = new CurrentUserAuthentication(USER);

    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.getAuthorities()).isEmpty();
  }

  @Test
  @DisplayName("名字只含用户 ID，不带整条记录")
  void should_use_user_id_as_name() {
    assertThat(new CurrentUserAuthentication(USER).getName()).isEqualTo("1001");
  }

  @Test
  @DisplayName("可以带上权限列表")
  void should_expose_given_authorities() {
    CurrentUserAuthentication authentication =
        new CurrentUserAuthentication(USER, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("主体为空时拒绝创建")
  void should_reject_null_principal() {
    assertThatThrownBy(() -> new CurrentUserAuthentication(null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserAuthenticationTest"`
预期：编译失败，`cannot find symbol … class CurrentUserAuthentication`。

- [ ] **步骤 2：实现 `CurrentUserAuthentication`**

新建 `src/main/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthentication.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.common.security.CurrentUser;
import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/// 主体是 `CurrentUser` 的认证对象，构造出来就是已认证状态。
///
/// 网关查到会话后构造它，下游从请求头解析后构造它，两边用同一个类型。
/// 本版没有角色，权限列表为空；做 admin 时由建立认证的一方把角色传进来。
public final class CurrentUserAuthentication extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final CurrentUser principal;

  /// 创建没有任何权限的认证对象。
  ///
  /// @param principal 当前用户
  public CurrentUserAuthentication(CurrentUser principal) {
    this(principal, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带权限列表的认证对象。
  ///
  /// @param principal 当前用户
  /// @param authorities 权限列表
  public CurrentUserAuthentication(
      CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = Objects.requireNonNull(principal, "principal 不能为 null");
    super.setAuthenticated(true);
  }

  /// 没有凭据：身份已经由网关验证过。
  ///
  /// @return 始终为 `null`
  @Override
  public Object getCredentials() {
    return null;
  }

  /// 返回当前用户。
  ///
  /// @return 当前用户
  @Override
  public CurrentUser getPrincipal() {
    return principal;
  }

  /// 只返回用户 ID。父类的默认实现会把整条记录打进日志。
  ///
  /// @return 用户 ID 的十进制字符串
  @Override
  public String getName() {
    return Long.toString(principal.userId());
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserAuthenticationTest"`
预期：`BUILD SUCCESSFUL`，4 个用例通过。

- [ ] **步骤 3：写 `SecurityContextCurrentUserAdapter` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/context/SecurityContextCurrentUserAdapterTest.java`：

```java
package dev.linqibin.patra.starter.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/// SecurityContextCurrentUserAdapter 单元测试。
@DisplayName("SecurityContextCurrentUserAdapter 单元测试")
class SecurityContextCurrentUserAdapterTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  private final SecurityContextCurrentUserAdapter adapter = new SecurityContextCurrentUserAdapter();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("安全上下文为空时没有当前用户")
  void should_be_empty_when_security_context_is_empty() {
    assertThat(adapter.current()).isEmpty();
  }

  @Test
  @DisplayName("认证对象的主体是 CurrentUser 时返回它")
  void should_return_user_when_principal_is_current_user() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(USER));

    assertThat(adapter.current()).contains(USER);
  }

  @Test
  @DisplayName("匿名认证对象不会被当成用户")
  void should_be_empty_when_authentication_is_anonymous() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(adapter.current()).isEmpty();
  }

  @Test
  @DisplayName("主体是别的类型时没有当前用户")
  void should_be_empty_when_principal_is_other_type() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("someone", "n/a"));

    assertThat(adapter.current()).isEmpty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityContextCurrentUserAdapterTest"`
预期：编译失败，`cannot find symbol … class SecurityContextCurrentUserAdapter`。

- [ ] **步骤 4：实现 `SecurityContextCurrentUserAdapter`**

新建 `src/main/java/dev/linqibin/patra/starter/security/context/SecurityContextCurrentUserAdapter.java`：

```java
package dev.linqibin.patra.starter.security.context;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/// `CurrentUserPort` 的实现：从 Spring Security 的安全上下文里取当前用户。
///
/// 线程上下文的设置和清理由 Spring Security 的过滤器负责，这里只读。
public class SecurityContextCurrentUserAdapter implements CurrentUserPort {

  /// 返回当前用户。
  ///
  /// 判断的是主体的类型，不是 `isAuthenticated()`：匿名认证对象的 `isAuthenticated()`
  /// 也是 true，但它的主体是一个字符串，按类型判断不会把它误认成用户。
  ///
  /// @return 当前用户；安全上下文为空、匿名、主体是别的类型时返回空
  @Override
  public Optional<CurrentUser> current() {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
      return Optional.of(user);
    }
    return Optional.empty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityContextCurrentUserAdapterTest"`
预期：`BUILD SUCCESSFUL`，4 个用例通过。

- [ ] **步骤 5：写 `CurrentUserRunner` 的失败测试（含 Review Focus 第 4 条）**

新建 `src/test/java/dev/linqibin/patra/starter/security/context/CurrentUserRunnerTest.java`：

```java
package dev.linqibin.patra.starter.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/// CurrentUserRunner 单元测试。
@DisplayName("CurrentUserRunner 单元测试")
class CurrentUserRunnerTest {

  private static final CurrentUser ALICE =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);
  private static final CurrentUser BOB =
      CurrentUser.of(1002L, 2002L, AccountType.PORTAL, ClientType.WEB);

  private final CurrentUserPort currentUserPort = new SecurityContextCurrentUserAdapter();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("执行期间能取到指定的用户")
  void should_expose_user_inside_run_as() {
    AtomicReference<Optional<CurrentUser>> seen = new AtomicReference<>();

    CurrentUserRunner.runAs(ALICE, () -> seen.set(currentUserPort.current()));

    assertThat(seen.get()).contains(ALICE);
  }

  @Test
  @DisplayName("callAs 返回动作的结果")
  void should_return_action_result_when_call_as() {
    long userId = CurrentUserRunner.callAs(ALICE, () -> currentUserPort.require().userId());

    assertThat(userId).isEqualTo(1001L);
  }

  @Test
  @DisplayName("原来没有身份时，执行结束后清空")
  void should_clear_context_after_run_when_no_previous_identity() {
    CurrentUserRunner.runAs(ALICE, () -> {});

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("原来有身份时，执行结束后恢复原来的身份")
  void should_restore_previous_identity_after_run() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(BOB));

    CurrentUserRunner.runAs(ALICE, () -> {});

    assertThat(currentUserPort.current()).contains(BOB);
  }

  @Test
  @DisplayName("动作抛异常时也恢复原状，异常原样抛出")
  void should_restore_context_when_action_throws() {
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                CurrentUserRunner.runAs(
                    ALICE,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("嵌套调用时，内层结束（包括抛异常）后回到外层的身份")
  void should_restore_outer_identity_after_nested_run() {
    List<Optional<CurrentUser>> seen = new ArrayList<>();

    CurrentUserRunner.runAs(
        ALICE,
        () -> {
          CurrentUserRunner.runAs(BOB, () -> seen.add(currentUserPort.current()));
          seen.add(currentUserPort.current());
          try {
            CurrentUserRunner.runAs(
                BOB,
                () -> {
                  throw new IllegalStateException("inner");
                });
          } catch (IllegalStateException expected) {
            seen.add(currentUserPort.current());
          }
        });

    assertThat(seen).containsExactly(Optional.of(BOB), Optional.of(ALICE), Optional.of(ALICE));
    assertThat(currentUserPort.current()).isEmpty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserRunnerTest"`
预期：编译失败，`cannot find symbol … class CurrentUserRunner`。

- [ ] **步骤 6：实现 `CurrentUserRunner`**

新建 `src/main/java/dev/linqibin/patra/starter/security/context/CurrentUserRunner.java`：

```java
package dev.linqibin.patra.starter.security.context;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/// 以某个用户的身份执行一段代码。
///
/// 定时任务、消息消费这些不在请求里的线程，需要带身份时用它。不调它时，
/// `CurrentUserPort.current()` 得到空结果，审计列留空。
public final class CurrentUserRunner {

  /// 工具类，不允许实例化。
  private CurrentUserRunner() {}

  /// 以指定用户的身份执行动作。
  ///
  /// @param user 用户
  /// @param action 要执行的动作
  public static void runAs(CurrentUser user, Runnable action) {
    Objects.requireNonNull(action, "action 不能为 null");
    callAs(
        user,
        () -> {
          action.run();
          return null;
        });
  }

  /// 以指定用户的身份执行动作并返回结果。
  ///
  /// 进去时把这个用户放进安全上下文；出来时恢复原来的上下文，原来是空的就清空。
  /// 动作抛异常时同样恢复。
  ///
  /// @param user 用户
  /// @param action 要执行的动作
  /// @param <T> 结果类型
  /// @return 动作的结果
  public static <T> T callAs(CurrentUser user, Supplier<T> action) {
    Objects.requireNonNull(user, "user 不能为 null");
    Objects.requireNonNull(action, "action 不能为 null");
    SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
    SecurityContext original = strategy.getContext();
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(new CurrentUserAuthentication(user));
    strategy.setContext(context);
    try {
      return action.get();
    } finally {
      if (strategy.createEmptyContext().equals(original)) {
        strategy.clearContext();
      } else {
        strategy.setContext(original);
      }
    }
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserRunnerTest"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。

- [ ] **步骤 7：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 新增认证对象、当前用户适配器与按用户身份执行的入口 (PAP-62)"
```

---

### 任务 6：从请求头建立认证（安全 starter）

下游用 Spring Security 的通用认证过滤器 `AuthenticationFilter`，配一个自己的转换器。转换器决定四种情况怎么处理（spec 第 8.2 节）：

| 收到的请求 | 转换器的结果 |
|---|---|
| 内部令牌正确，四个身份头齐全且合法 | 返回 `CurrentUserAuthentication` |
| 内部令牌正确，四个身份头都没有 | 返回 `null`（匿名） |
| 内部令牌缺失或不对 | 返回 `null`；请求里带了任何身份头时记一条警告日志 |
| 内部令牌正确，身份头只有一部分或格式不合法 | 抛 `MalformedIdentityException` |

内部令牌头出现多次按令牌不对处理。第四种只可能是网关自己的缺陷，所以后面会按服务端错误（500）输出。

本任务只做转换器和异常，把它们装进过滤器链是任务 8 的事。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/MalformedIdentityException.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverter.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverterTest.java`

**Interfaces:**

- Consumes: 任务 4 的 `IdentityHeaders`、`IdentityParseResult`；任务 5 的 `CurrentUserAuthentication`。Spring Security 的 `AuthenticationConverter`（方法 `Authentication convert(HttpServletRequest request)`，可以返回 `null`）、`AuthenticationServiceException`。
- Produces：
  - `class MalformedIdentityException extends AuthenticationServiceException`，构造器 `(String reason)`。
  - `final class GatewayHeaderAuthenticationConverter implements AuthenticationConverter`，构造器 `(String gatewayToken)`。调用方保证令牌非空白（任务 8 的 `PatraSecurityProperties` 负责校验）。

- [ ] **步骤 1：写「令牌正确」两种情况的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverterTest.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

/// GatewayHeaderAuthenticationConverter 单元测试：spec 第 8.2 节的四种情况。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GatewayHeaderAuthenticationConverter 单元测试")
class GatewayHeaderAuthenticationConverterTest {

  private static final String TOKEN = "test-gateway-token";
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  private final GatewayHeaderAuthenticationConverter converter =
      new GatewayHeaderAuthenticationConverter(TOKEN);

  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/probe/whoami");
  }

  private static void addIdentityHeaders(MockHttpServletRequest request) {
    request.addHeader(IdentityHeaders.USER_ID, "1001");
    request.addHeader(IdentityHeaders.SESSION_ID, "2001");
    request.addHeader(IdentityHeaders.ACCOUNT_TYPE, "portal");
    request.addHeader(IdentityHeaders.CLIENT_TYPE, "web");
  }

  @Test
  @DisplayName("内部令牌正确、身份头齐全且合法：得到认证对象")
  void should_authenticate_when_token_valid_and_identity_headers_complete() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    addIdentityHeaders(request);

    Authentication authentication = converter.convert(request);

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  @DisplayName("内部令牌正确、没有身份头：匿名")
  void should_return_null_when_token_valid_and_no_identity_headers() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);

    assertThat(converter.convert(request)).isNull();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：编译失败，`cannot find symbol … class GatewayHeaderAuthenticationConverter`。

- [ ] **步骤 2：实现转换器的主干**

新建 `src/main/java/dev/linqibin/patra/starter/security/authentication/MalformedIdentityException.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import org.springframework.security.authentication.AuthenticationServiceException;

/// 内部令牌正确、但身份头残缺或格式不合法时抛出。
///
/// 只可能是网关自己的缺陷，所以按服务端错误（500）处理，不伪装成 401。
public class MalformedIdentityException extends AuthenticationServiceException {

  /// 创建异常。
  ///
  /// @param reason 不合法的原因，只含头名，不含头的值
  public MalformedIdentityException(String reason) {
    super("网关传来的身份头不合法: " + reason);
  }
}
```

新建 `src/main/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverter.java`（先不校验令牌）：

```java
package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.header.IdentityParseResult;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把网关传来的身份头变成认证对象。
///
/// 只有带着正确内部令牌的请求，身份头才被采信。令牌缺失或不对时按匿名处理：
/// 服务之间直连的调用不带令牌，它们不应该因此被拒绝。
@Slf4j
public final class GatewayHeaderAuthenticationConverter implements AuthenticationConverter {

  private final byte[] expectedToken;

  /// 创建转换器。
  ///
  /// @param gatewayToken 网关与下游共享的内部令牌，调用方保证非空白
  public GatewayHeaderAuthenticationConverter(String gatewayToken) {
    this.expectedToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；应按匿名处理时返回 `null`
  /// @throws MalformedIdentityException 内部令牌正确但身份头残缺或不合法时
  @Override
  public Authentication convert(HttpServletRequest request) {
    HttpHeaders identityHeaders = identityHeadersOf(request);
    IdentityParseResult result = IdentityHeaders.parse(identityHeaders);
    if (result instanceof IdentityParseResult.Identified identified) {
      return new CurrentUserAuthentication(identified.user());
    }
    return null;
  }

  /// 把请求里的四个身份头收集成 `HttpHeaders`，保留重复出现的值。
  ///
  /// @param request 当前请求
  /// @return 只含身份头的请求头集合
  private static HttpHeaders identityHeadersOf(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    for (String name : IdentityHeaders.IDENTITY_HEADER_NAMES) {
      Enumeration<String> values = request.getHeaders(name);
      while (values.hasMoreElements()) {
        headers.add(name, values.nextElement());
      }
    }
    return headers;
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

- [ ] **步骤 3：写「令牌缺失或不对」的失败测试**

在测试类里追加五个用例，并在 import 区加 `org.springframework.boot.test.system.CapturedOutput`：

```java
  @Test
  @DisplayName("内部令牌缺失：身份头不被采信，并记一条警告")
  void should_ignore_identity_and_warn_when_token_missing(CapturedOutput output) {
    MockHttpServletRequest request = request();
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
    assertThat(output).contains("内部令牌");
  }

  @Test
  @DisplayName("内部令牌不对：身份头不被采信，警告里不出现令牌的值")
  void should_ignore_identity_and_warn_when_token_wrong(CapturedOutput output) {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
    assertThat(output).contains("内部令牌").doesNotContain("wrong-token").doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("内部令牌头出现多次：按令牌不对处理")
  void should_ignore_identity_when_token_repeated() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
  }

  @Test
  @DisplayName("内部令牌缺失、也没有身份头：匿名，不记警告")
  void should_not_warn_when_token_missing_and_no_identity_headers(CapturedOutput output) {
    assertThat(converter.convert(request())).isNull();
    assertThat(output).doesNotContain("内部令牌");
  }

  @Test
  @DisplayName("内部令牌不对时，即使身份头不合法也按匿名处理，不抛异常")
  void should_return_null_when_token_wrong_and_identity_headers_malformed() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
    request.addHeader(IdentityHeaders.USER_ID, "abc");

    assertThat(converter.convert(request)).isNull();
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：前三个新用例失败（得到的是认证对象而不是 `null`，或输出里没有「内部令牌」）。

- [ ] **步骤 4：实现令牌校验**

在 `GatewayHeaderAuthenticationConverter` 的 import 区加：

```java
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
```

把 `convert` 方法体换成：

```java
    HttpHeaders identityHeaders = identityHeadersOf(request);
    if (!hasValidGatewayToken(request)) {
      if (!identityHeaders.isEmpty()) {
        log.warn(
            "收到带身份头但内部令牌缺失或不正确的请求，身份头不予采信，按匿名处理: {} {}",
            request.getMethod(),
            request.getRequestURI());
      }
      return null;
    }
    IdentityParseResult result = IdentityHeaders.parse(identityHeaders);
    if (result instanceof IdentityParseResult.Identified identified) {
      return new CurrentUserAuthentication(identified.user());
    }
    return null;
```

在 `convert` 和 `identityHeadersOf` 之间加一个私有方法：

```java
  /// 判断请求是否带着正确的内部令牌。
  ///
  /// 令牌头必须恰好出现一次。比较用恒定时间算法，不因内容不同而提前返回。
  ///
  /// @param request 当前请求
  /// @return 令牌正确时为 true
  private boolean hasValidGatewayToken(HttpServletRequest request) {
    List<String> presented = Collections.list(request.getHeaders(IdentityHeaders.GATEWAY_TOKEN));
    return presented.size() == 1
        && MessageDigest.isEqual(
            expectedToken, presented.getFirst().getBytes(StandardCharsets.UTF_8));
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：`BUILD SUCCESSFUL`，7 个用例通过。

- [ ] **步骤 5：写「令牌正确但身份头不合法」和「头名全小写」的失败测试（含 Review Focus 第 5 条）**

在测试类里追加三个用例，并在 import 区加 `import static org.assertj.core.api.Assertions.assertThatThrownBy;`：

```java
  @Test
  @DisplayName("内部令牌正确、身份头只有一部分：抛出身份头不合法")
  void should_throw_malformed_identity_when_token_valid_and_identity_partial() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.USER_ID, "1001");

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(MalformedIdentityException.class);
  }

  @Test
  @DisplayName("内部令牌正确、用户 ID 不是正整数：抛出身份头不合法，消息里没有头的值")
  void should_throw_malformed_identity_when_token_valid_and_user_id_invalid() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.USER_ID, "not-a-number");
    request.addHeader(IdentityHeaders.SESSION_ID, "2001");
    request.addHeader(IdentityHeaders.ACCOUNT_TYPE, "portal");
    request.addHeader(IdentityHeaders.CLIENT_TYPE, "web");

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining(IdentityHeaders.USER_ID)
        .hasMessageNotContaining("not-a-number");
  }

  @Test
  @DisplayName("请求头的名字全小写时结果相同")
  void should_authenticate_when_header_names_are_lowercase() {
    MockHttpServletRequest request = request();
    request.addHeader("x-patra-gateway-token", TOKEN);
    request.addHeader("x-patra-user-id", "1001");
    request.addHeader("x-patra-session-id", "2001");
    request.addHeader("x-patra-account-type", "portal");
    request.addHeader("x-patra-client-type", "web");

    Authentication authentication = converter.convert(request);

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：前两个新用例失败（没有抛异常，得到的是 `null`）。小写头名的用例直接通过：servlet 的请求头查找本来就不区分大小写，这个用例是把这条性质钉住。

- [ ] **步骤 6：实现「身份头不合法就抛异常」**

把 `convert` 方法体最后的这几行：

```java
    IdentityParseResult result = IdentityHeaders.parse(identityHeaders);
    if (result instanceof IdentityParseResult.Identified identified) {
      return new CurrentUserAuthentication(identified.user());
    }
    return null;
```

换成：

```java
    IdentityParseResult result = IdentityHeaders.parse(identityHeaders);
    if (result instanceof IdentityParseResult.Identified identified) {
      return new CurrentUserAuthentication(identified.user());
    }
    if (result instanceof IdentityParseResult.Malformed malformed) {
      throw new MalformedIdentityException(malformed.reason());
    }
    return null;
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*GatewayHeaderAuthenticationConverterTest"`
预期：`BUILD SUCCESSFUL`，10 个用例通过。

- [ ] **步骤 7：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 新增从网关请求头建立认证的转换器 (PAP-62)"
```

---

### 任务 7：错误输出（安全 starter）

三样东西，让安全相关的失败都输出成现有的 ProblemDetail 格式：

- `SecurityErrorMappingContributor`：现有的错误解析引擎不认识 Spring Security 的异常，拒绝访问会被解析成 500，凭据错误会被解析成 422。这个映射把它们对到正确的错误码。
- `SecurityProblemWriter`：过滤器链里的失败到不了全局异常处理器，由它直接写响应。它同时充当「未登录的入口点」「拒绝访问的处理器」「认证失败的处理器」。
- `SecurityExceptionRethrowAdvice`：控制器里抛出的安全异常，以最高优先级接住并原样抛出。原样抛出的异常 Spring MVC 当作没处理，会一路回到安全过滤器，由它区分 401 和 403 后交给上面的写出器。

`SecurityProblemWriter` 有两点和 `GlobalRestExceptionHandler` 的路径不同，必须自己做：

- `instance` 字段。现有的 `ProblemDetailBuilder` 不设它；控制器路径上是 Spring MVC 写响应时补的（取 `request.getRequestURI()`）。过滤器里没有这一步，要显式设置。
- `detail` 字段。`ProblemDetailBuilder` 用的是异常的原始消息；这里换成固定短句，原始消息只进日志。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/error/SecurityErrorMappingContributor.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/error/SecurityProblemWriter.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/error/SecurityExceptionRethrowAdvice.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/error/SecurityErrorMappingContributorTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/error/SecurityProblemWriterTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/error/SecurityExceptionRethrowAdviceTest.java`

**Interfaces:**

- Consumes：
  - 任务 6 的 `MalformedIdentityException`。
  - starter-core：`ErrorMappingContributor`（方法 `Optional<ErrorCodeLike> mapException(Throwable exception)`）、`HttpStdErrors.Group`（方法 `UNAUTHORIZED()`、`FORBIDDEN()`、`INTERNAL_ERROR()`、`UNAVAILABLE()`，都返回 `ErrorCodeLike`；用 `HttpStdErrors.of("TEST")` 得到前缀为 `TEST` 的一组）。
  - starter-web：`ProblemDetailAdapter.adapt(Throwable exception, HttpServletRequest request)` 返回 `ProblemDetailResponse`，后者是 record，有 `problemDetail()`（可修改的 `ProblemDetail`）和 `httpStatus()`（`HttpStatus`）。
  - Jackson 3：`tools.jackson.databind.json.JsonMapper`，方法 `writeValue(OutputStream, Object)`。容器里那个由 Spring Boot 配好的 `JsonMapper` 已经带了 ProblemDetail 的序列化规则（扩展字段平铺在顶层）。
- Produces：
  - `class SecurityErrorMappingContributor implements ErrorMappingContributor`，构造器 `(HttpStdErrors.Group http)`。
  - `class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler`，构造器 `(ProblemDetailAdapter problemDetailAdapter, JsonMapper jsonMapper)`。
  - `class SecurityExceptionRethrowAdvice`，无参构造器，带 `@RestControllerAdvice` 和 `@Order(Ordered.HIGHEST_PRECEDENCE)`。

- [ ] **步骤 1：写错误码映射的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/error/SecurityErrorMappingContributorTest.java`：

```java
package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/// SecurityErrorMappingContributor 单元测试：spec 第 9.2 节的映射表。
@DisplayName("SecurityErrorMappingContributor 单元测试")
class SecurityErrorMappingContributorTest {

  private final SecurityErrorMappingContributor contributor =
      new SecurityErrorMappingContributor(HttpStdErrors.of("TEST"));

  private Optional<String> codeOf(Throwable exception) {
    return contributor.mapException(exception).map(ErrorCodeLike::code);
  }

  @Test
  @DisplayName("身份头不合法映射为 0500")
  void should_map_malformed_identity_to_internal_error() {
    assertThat(codeOf(new MalformedIdentityException("身份头不完整"))).contains("TEST-0500");
  }

  @Test
  @DisplayName("其他认证服务异常映射为 0503")
  void should_map_authentication_service_exception_to_unavailable() {
    assertThat(codeOf(new AuthenticationServiceException("session store down")))
        .contains("TEST-0503");
  }

  @Test
  @DisplayName("其他认证异常映射为 0401")
  void should_map_authentication_exception_to_unauthorized() {
    assertThat(codeOf(new InsufficientAuthenticationException("full authentication required")))
        .contains("TEST-0401");
    assertThat(codeOf(new BadCredentialsException("bad credentials"))).contains("TEST-0401");
  }

  @Test
  @DisplayName("拒绝访问映射为 0403")
  void should_map_access_denied_to_forbidden() {
    assertThat(codeOf(new AccessDeniedException("denied"))).contains("TEST-0403");
  }

  @Test
  @DisplayName("别的异常不归它管")
  void should_return_empty_for_unrelated_exception() {
    assertThat(codeOf(new IllegalStateException("other"))).isEmpty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityErrorMappingContributorTest"`
预期：编译失败，`cannot find symbol … class SecurityErrorMappingContributor`。

- [ ] **步骤 2：实现错误码映射**

新建 `src/main/java/dev/linqibin/patra/starter/security/error/SecurityErrorMappingContributor.java`：

```java
package dev.linqibin.patra.starter.security.error;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;

/// 把 Spring Security 的异常映射到统一错误码。
///
/// 没有它，错误解析引擎会把拒绝访问解析成 500、把凭据错误解析成 422。
public class SecurityErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 创建映射。
  ///
  /// @param http 带服务前缀的标准 HTTP 错误码组
  public SecurityErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /// 映射安全异常。判断顺序从最具体的类型到最宽的类型。
  ///
  /// @param exception 要映射的异常
  /// @return 错误码；不是安全异常时返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof MalformedIdentityException) {
      return Optional.of(http.INTERNAL_ERROR());
    }
    if (exception instanceof AuthenticationServiceException) {
      return Optional.of(http.UNAVAILABLE());
    }
    if (exception instanceof AuthenticationException) {
      return Optional.of(http.UNAUTHORIZED());
    }
    if (exception instanceof AccessDeniedException) {
      return Optional.of(http.FORBIDDEN());
    }
    return Optional.empty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityErrorMappingContributorTest"`
预期：`BUILD SUCCESSFUL`，5 个用例通过。

- [ ] **步骤 3：写写出器的失败测试**

这个测试不起 Spring，但用真实的错误解析引擎和 `ProblemDetailBuilder` 组装适配器，所以它同时验证了「映射 + 适配 + 写出」这条链。

新建 `src/test/java/dev/linqibin/patra/starter/security/error/SecurityProblemWriterTest.java`：

```java
package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import dev.linqibin.starter.core.error.config.ErrorProperties;
import dev.linqibin.starter.core.error.engine.DefaultErrorResolutionEngine;
import dev.linqibin.starter.core.error.pipeline.ErrorResolutionPipeline;
import dev.linqibin.starter.web.error.adapter.DefaultProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.builder.ProblemDetailBuilder;
import dev.linqibin.starter.web.error.config.WebErrorProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// SecurityProblemWriter 单元测试。
///
/// 不起 Spring，用真实的错误解析引擎和 ProblemDetailBuilder 组装适配器。
@DisplayName("SecurityProblemWriter 单元测试")
class SecurityProblemWriterTest {

  private final JsonMapper jsonMapper =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  private SecurityProblemWriter writer;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    ErrorProperties errorProperties = new ErrorProperties();
    errorProperties.setContextPrefix("TEST");
    DefaultErrorResolutionEngine engine =
        new DefaultErrorResolutionEngine(
            errorProperties,
            List.of(new SecurityErrorMappingContributor(HttpStdErrors.of("TEST"))));
    ProblemDetailBuilder builder =
        new ProblemDetailBuilder(
            errorProperties, new WebErrorProperties(), Optional::empty, List.of(), List.of());
    ProblemDetailAdapter adapter =
        new DefaultProblemDetailAdapter(new ErrorResolutionPipeline(engine, List.of()), builder);
    writer = new SecurityProblemWriter(adapter, jsonMapper);
    request = new MockHttpServletRequest("GET", "/probe/me");
    response = new MockHttpServletResponse();
  }

  private Map<String, Object> body() {
    return jsonMapper.readValue(
        response.getContentAsByteArray(), new TypeReference<Map<String, Object>>() {});
  }

  private String rawBody() {
    return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("未登录：401、Bearer 质询头、ProblemDetail，detail 是固定短句")
  void should_write_401_problem_when_commence() throws Exception {
    writer.commence(
        request,
        response,
        new InsufficientAuthenticationException("Full authentication is required"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(body())
        .containsEntry("status", 401)
        .containsEntry("code", "TEST-0401")
        .containsEntry("title", "TEST-0401")
        .containsEntry("detail", "Authentication required")
        .containsEntry("instance", "/probe/me")
        .containsEntry("path", "/probe/me")
        .containsKeys("type", "timestamp");
  }

  @Test
  @DisplayName("拒绝访问：403，没有质询头，不泄露异常的原始消息")
  void should_write_403_problem_when_access_denied() throws Exception {
    writer.handle(request, response, new AccessDeniedException("probe internal reason"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    assertThat(body())
        .containsEntry("status", 403)
        .containsEntry("code", "TEST-0403")
        .containsEntry("detail", "Access denied")
        .containsEntry("instance", "/probe/me");
    assertThat(rawBody()).doesNotContain("probe internal reason");
  }

  @Test
  @DisplayName("身份头不合法：500，detail 是 HTTP 状态的标准短语，不泄露头名")
  void should_write_500_problem_when_identity_malformed() throws Exception {
    writer.onAuthenticationFailure(
        request, response, new MalformedIdentityException("X-Patra-User-Id 不是正整数"));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(body())
        .containsEntry("status", 500)
        .containsEntry("code", "TEST-0500")
        .containsEntry("detail", "Internal Server Error")
        .containsEntry("instance", "/probe/me");
    assertThat(rawBody()).doesNotContain("X-Patra-User-Id");
  }

  @Test
  @DisplayName("认证服务不可用：503")
  void should_write_503_problem_when_authentication_service_unavailable() throws Exception {
    writer.onAuthenticationFailure(
        request, response, new AuthenticationServiceException("session store down"));

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(body())
        .containsEntry("status", 503)
        .containsEntry("code", "TEST-0503")
        .containsEntry("detail", "Service Unavailable");
    assertThat(rawBody()).doesNotContain("session store down");
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityProblemWriterTest"`
预期：编译失败，`cannot find symbol … class SecurityProblemWriter`。

- [ ] **步骤 4：实现写出器**

新建 `src/main/java/dev/linqibin/patra/starter/security/error/SecurityProblemWriter.java`：

```java
package dev.linqibin.patra.starter.security.error;

import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.json.JsonMapper;

/// 在安全过滤器链里把失败输出成统一的 ProblemDetail。
///
/// 过滤器链里的失败到不了全局异常处理器，由这个类直接写响应。它同时充当
/// 未登录的入口点、拒绝访问的处理器和认证失败的处理器。
@Slf4j
public class SecurityProblemWriter
    implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

  private static final String BEARER_CHALLENGE = "Bearer";

  private final ProblemDetailAdapter problemDetailAdapter;
  private final JsonMapper jsonMapper;

  /// 创建写出器。
  ///
  /// @param problemDetailAdapter 把异常变成 ProblemDetail 的适配器
  /// @param jsonMapper 容器里的 JSON 映射器（已带 ProblemDetail 的序列化规则）
  public SecurityProblemWriter(ProblemDetailAdapter problemDetailAdapter, JsonMapper jsonMapper) {
    this.problemDetailAdapter = problemDetailAdapter;
    this.jsonMapper = jsonMapper;
  }

  /// 需要登录但没登录时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param authException 触发的认证异常
  /// @throws IOException 写响应失败时
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(request, response, authException);
  }

  /// 登录了但不允许访问时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param accessDeniedException 触发的拒绝访问异常
  /// @throws IOException 写响应失败时
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(request, response, accessDeniedException);
  }

  /// 认证过滤器里建立认证失败时调用。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param exception 触发的认证异常
  /// @throws IOException 写响应失败时
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    write(request, response, exception);
  }

  /// 把异常写成 ProblemDetail 响应。
  ///
  /// `detail` 换成固定短句，`instance` 显式设成请求路径：这两个字段在控制器路径上
  /// 分别来自异常消息和 Spring MVC，过滤器里都得自己处理。
  ///
  /// @param request 当前请求
  /// @param response 当前响应
  /// @param exception 要输出的异常
  /// @throws IOException 写响应失败时
  private void write(
      HttpServletRequest request, HttpServletResponse response, RuntimeException exception)
      throws IOException {
    ProblemDetailResponse adapted = problemDetailAdapter.adapt(exception, request);
    HttpStatus status = adapted.httpStatus();
    ProblemDetail problemDetail = adapted.problemDetail();
    problemDetail.setDetail(clientDetailFor(status));
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    if (status.is5xxServerError()) {
      log.error(
          "安全过滤器链返回 {}: {} {}",
          status.value(),
          request.getMethod(),
          request.getRequestURI(),
          exception);
    } else {
      log.debug(
          "安全过滤器链返回 {}: {} {}，原因: {}",
          status.value(),
          request.getMethod(),
          request.getRequestURI(),
          exception.getMessage());
    }

    response.setStatus(status.value());
    if (status == HttpStatus.UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
    }
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problemDetail);
  }

  /// 给客户端看的固定短句，不带框架异常的原始消息。
  ///
  /// @param status 响应状态
  /// @return 固定短句
  private static String clientDetailFor(HttpStatus status) {
    return switch (status) {
      case UNAUTHORIZED -> "Authentication required";
      case FORBIDDEN -> "Access denied";
      default -> status.getReasonPhrase();
    };
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityProblemWriterTest"`
预期：`BUILD SUCCESSFUL`，4 个用例通过。

- [ ] **步骤 5：写重抛处理器的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/error/SecurityExceptionRethrowAdviceTest.java`：

```java
package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/// SecurityExceptionRethrowAdvice 单元测试。
///
/// 这里只验证「原样抛出」和优先级；异常是否真的回到安全过滤器，由任务 10 的
/// 整应用测试验证。
@DisplayName("SecurityExceptionRethrowAdvice 单元测试")
class SecurityExceptionRethrowAdviceTest {

  private final SecurityExceptionRethrowAdvice advice = new SecurityExceptionRethrowAdvice();

  @Test
  @DisplayName("拒绝访问异常被原样抛出")
  void should_rethrow_same_access_denied_exception() {
    AccessDeniedException exception = new AccessDeniedException("denied");

    assertThatThrownBy(() -> advice.rethrowAccessDenied(exception)).isSameAs(exception);
  }

  @Test
  @DisplayName("认证异常被原样抛出")
  void should_rethrow_same_authentication_exception() {
    AuthenticationException exception = new BadCredentialsException("bad credentials");

    assertThatThrownBy(() -> advice.rethrowAuthentication(exception)).isSameAs(exception);
  }

  @Test
  @DisplayName("优先级是最高的，排在全局异常处理器之前")
  void should_have_highest_precedence() {
    Order order = AnnotationUtils.findAnnotation(SecurityExceptionRethrowAdvice.class, Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityExceptionRethrowAdviceTest"`
预期：编译失败，`cannot find symbol … class SecurityExceptionRethrowAdvice`。

- [ ] **步骤 6：实现重抛处理器**

新建 `src/main/java/dev/linqibin/patra/starter/security/error/SecurityExceptionRethrowAdvice.java`：

```java
package dev.linqibin.patra.starter.security.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/// 把控制器或方法注解里抛出的 Spring Security 异常原样抛回安全过滤器。
///
/// 只有安全过滤器知道当前是匿名还是已登录，才能正确区分 401 和 403。全局异常处理器
/// 会兜住所有异常，所以这个类以最高优先级抢在它前面，只接安全异常并原样抛出。
/// 原样抛出的异常 Spring MVC 当作没处理，会回到安全过滤器，由 `SecurityProblemWriter` 输出。
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionRethrowAdvice {

  /// 原样抛出拒绝访问异常。
  ///
  /// @param exception 控制器里抛出的拒绝访问异常
  @ExceptionHandler(AccessDeniedException.class)
  public void rethrowAccessDenied(AccessDeniedException exception) {
    throw exception;
  }

  /// 原样抛出认证异常。
  ///
  /// @param exception 控制器里抛出的认证异常
  @ExceptionHandler(AuthenticationException.class)
  public void rethrowAuthentication(AuthenticationException exception) {
    throw exception;
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityExceptionRethrowAdviceTest"`
预期：`BUILD SUCCESSFUL`，3 个用例通过。

- [ ] **步骤 7：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 安全相关的失败输出为统一的 ProblemDetail (PAP-62)"
```

---

### 任务 8：配置属性与自动配置（安全 starter）

把前面几个任务的零件装起来。本任务注册两个自动配置，第三个（审计）在任务 11：

| 类 | 条件 | 注册的东西 |
|---|---|---|
| `SecurityCoreAutoConfiguration` | 无 | `CurrentUserPort` 的实现 |
| `SecurityServletAutoConfiguration` | servlet 应用 | 配置属性、`SecurityProblemWriter`、`SecurityErrorMappingContributor`、`SecurityExceptionRethrowAdvice`、拒绝一切的 `AuthenticationManager`、默认的 `SecurityFilterChain` |

`SecurityServletAutoConfiguration` 里有几处写法是有原因的，不要改：

- **排在 Spring Boot 的两个安全自动配置之前**（`before = …`）。Boot 的默认过滤器链和默认用户都是「容器里没有才注册」，我们的 Bean 必须先登记，它们才会让位。
- **认证过滤器在方法里直接 `new`，不声明成 Bean**。声明成 Bean 的话 Boot 会把它再注册成全局 servlet 过滤器，在别的链上也执行一遍。
- **成功处理器换成空实现**。默认的会先回一个 302 重定向，然后还继续执行过滤器链。
- **失败处理器换成 `SecurityProblemWriter`**。默认的遇到服务类异常会原样抛出，变成容器的 500 页面。
- **直通的认证管理器用显式类型的局部变量**。`AuthenticationFilter` 有两个构造器，第一个参数分别是 `AuthenticationManager` 和 `AuthenticationManagerResolver`，都是单方法接口，直接传 lambda 会有二义性，编译不过。
- **拒绝一切的 `AuthenticationManager` Bean**。Boot 检测不到任何认证相关的 Bean 时，会生成一个带随机密码的内存用户并把密码打进日志。应用里没有用户名密码登录，这个 Bean 不会被任何地方调用，它存在只是为了让 Boot 的默认用户让位。
- **不用嵌套的 `@Configuration` 类**。各服务扫描整个 `dev.linqibin`，嵌套的配置类会被组件扫描提前注册，绕过自动配置的顺序。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/PatraSecurityProperties.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/StatelessSecurityDefaults.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityCoreAutoConfiguration.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfiguration.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/PatraSecurityPropertiesTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityCoreAutoConfigurationTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfigurationTest.java`

**Interfaces:**

- Consumes：
  - 任务 5 的 `SecurityContextCurrentUserAdapter`；任务 6 的 `GatewayHeaderAuthenticationConverter`；任务 7 的三个类。
  - starter-web 的 `ProblemDetailAdapter` Bean（由 `WebErrorAutoConfiguration` 注册）；starter-core 的 `HttpStdErrors.Group` Bean（由 `CoreErrorAutoConfiguration` 注册）；Spring Boot 的 `JsonMapper` Bean（由 `org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration` 注册）。
  - Spring Boot 4.0.8 的安全自动配置，包名 `org.springframework.boot.security.autoconfigure`：`SecurityAutoConfiguration`、`UserDetailsServiceAutoConfiguration`、`web.servlet.ServletWebSecurityAutoConfiguration`。
  - Spring Security 7.0.7：`HttpSecurity` 的配置方法和 `build()` 都不再声明受检异常，所以下面的方法都不写 `throws Exception`。
- Produces：
  - `record PatraSecurityProperties(String gatewayToken)`，前缀 `patra.security`。
  - `StatelessSecurityDefaults.apply(HttpSecurity http, SecurityProblemWriter problemWriter)`：网关在 PAP-65 里写自己的过滤器链时调用。
  - 两个自动配置类和它们注册的 Bean。默认过滤器链的 Bean 名是 `patraSecurityFilterChain`。

- [ ] **步骤 1：写配置属性的失败测试（含 Review Focus 第 1 条）**

新建 `src/test/java/dev/linqibin/patra/starter/security/config/PatraSecurityPropertiesTest.java`：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraSecurityProperties 单元测试。
@DisplayName("PatraSecurityProperties 单元测试")
class PatraSecurityPropertiesTest {

  private static final String TOKEN = "test-gateway-token";

  @Test
  @DisplayName("合法的令牌原样保存")
  void should_keep_token_when_valid() {
    assertThat(new PatraSecurityProperties(TOKEN).gatewayToken()).isEqualTo(TOKEN);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("令牌为空或只有空白时拒绝，并指明配置项")
  void should_reject_blank_token(String token) {
    assertThatThrownBy(() -> new PatraSecurityProperties(token))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("patra.security.gateway-token");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {" test-gateway-token", "test-gateway-token\n", "test gateway token", "令牌"})
  @DisplayName("令牌带空白、换行或非 ASCII 字符时拒绝，并指明配置项")
  void should_reject_token_with_whitespace_or_non_ascii(String token) {
    assertThatThrownBy(() -> new PatraSecurityProperties(token))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("patra.security.gateway-token");
  }

  @Test
  @DisplayName("toString 不输出令牌的值")
  void should_mask_token_in_to_string() {
    assertThat(new PatraSecurityProperties(TOKEN).toString()).doesNotContain(TOKEN);
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*PatraSecurityPropertiesTest"`
预期：编译失败，`cannot find symbol … class PatraSecurityProperties`。

- [ ] **步骤 2：实现配置属性**

新建 `src/main/java/dev/linqibin/patra/starter/security/config/PatraSecurityProperties.java`：

```java
package dev.linqibin.patra.starter.security.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 安全 starter 的配置属性，前缀 `patra.security`。
///
/// 只在 servlet 应用里绑定，因为只有从请求头建立认证时才用到内部令牌。
///
/// @param gatewayToken 网关与下游共享的内部令牌，由部署时的 secret 注入
@ConfigurationProperties(prefix = "patra.security")
public record PatraSecurityProperties(String gatewayToken) {

  /// 可见 ASCII 字符（不含空格）。令牌要放进 HTTP 头里传输，首尾空白会被去掉。
  private static final Pattern VISIBLE_ASCII = Pattern.compile("[\\x21-\\x7E]+");

  /// 校验内部令牌。不合法时应用启动失败，错误信息指明配置项。
  public PatraSecurityProperties {
    if (gatewayToken == null || gatewayToken.isBlank()) {
      throw new IllegalArgumentException(
          "配置项 patra.security.gateway-token 不能为空：它是网关与下游共享的内部令牌");
    }
    if (!VISIBLE_ASCII.matcher(gatewayToken).matches()) {
      throw new IllegalArgumentException(
          "配置项 patra.security.gateway-token 只能包含可见 ASCII 字符，不能带空白或换行："
              + "它要放进 HTTP 头里传输，首尾空白会被去掉，两边永远比不上");
    }
  }

  /// 不输出令牌的值，避免它经由日志泄露。
  ///
  /// @return 掩码后的字符串
  @Override
  public String toString() {
    return "PatraSecurityProperties[gatewayToken=***]";
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*PatraSecurityPropertiesTest"`
预期：`BUILD SUCCESSFUL`，10 个用例通过。

- [ ] **步骤 3：写核心自动配置的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/config/SecurityCoreAutoConfigurationTest.java`：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.SecurityContextCurrentUserAdapter;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// SecurityCoreAutoConfiguration 自动配置测试。
@DisplayName("SecurityCoreAutoConfiguration 自动配置测试")
class SecurityCoreAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityCoreAutoConfiguration.class));

  @Test
  @DisplayName("非 Web 环境下也注册 CurrentUserPort")
  void should_register_current_user_port_without_web_environment() {
    contextRunner.run(
        context ->
            assertThat(context)
                .getBean(CurrentUserPort.class)
                .isInstanceOf(SecurityContextCurrentUserAdapter.class));
  }

  @Test
  @DisplayName("应用自己提供 CurrentUserPort 时让位")
  void should_back_off_when_application_provides_current_user_port() {
    CurrentUserPort custom = Optional::empty;

    contextRunner
        .withBean(CurrentUserPort.class, () -> custom)
        .run(context -> assertThat(context).getBean(CurrentUserPort.class).isSameAs(custom));
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains("dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration");
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityCoreAutoConfigurationTest"`
预期：编译失败，`cannot find symbol … class SecurityCoreAutoConfiguration`。

- [ ] **步骤 4：实现核心自动配置并登记 imports**

新建 `src/main/java/dev/linqibin/patra/starter/security/config/SecurityCoreAutoConfiguration.java`：

```java
package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.SecurityContextCurrentUserAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 安全 starter 的核心自动配置：注册取当前用户的端口实现。
///
/// 不带 Web 条件。安全上下文在没有 Web 环境的应用里同样可用，比如只跑定时任务的进程；
/// 这样依赖 `CurrentUserPort` 的审计配置在任何环境下都能装配。
@AutoConfiguration
public class SecurityCoreAutoConfiguration {

  /// 注册从安全上下文取当前用户的端口实现。
  ///
  /// @return 端口实现
  @Bean
  @ConditionalOnMissingBean
  public CurrentUserPort currentUserPort() {
    return new SecurityContextCurrentUserAdapter();
  }
}
```

新建 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（先只有一行，文件末尾留一个换行）：

```
dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration
```

这个文件里的每一行都必须指向已经存在的类：真实应用按名字加载它们，指向不存在的类会让应用起不来。所以每个自动配置类都是在它创建的那一步才登记进来。

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityCoreAutoConfigurationTest"`
预期：`BUILD SUCCESSFUL`，3 个用例通过。

- [ ] **步骤 5：写 servlet 自动配置的失败测试（第一组：过滤器链与配套 Bean）**

新建 `src/test/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfigurationTest.java`：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import dev.linqibin.patra.starter.security.error.SecurityExceptionRethrowAdvice;
import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/// SecurityServletAutoConfiguration 自动配置测试。
///
/// 把 Spring Boot 自己的安全自动配置一起放进来，验证我们的 Bean 能让它们让位。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SecurityServletAutoConfiguration 自动配置测试")
class SecurityServletAutoConfigurationTest {

  private static final String TOKEN_PROPERTY = "patra.security.gateway-token=test-gateway-token";

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  CoreErrorAutoConfiguration.class,
                  WebErrorAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  UserDetailsServiceAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  SecurityCoreAutoConfiguration.class,
                  SecurityServletAutoConfiguration.class))
          .withPropertyValues("linqibin.starter.core.error.context-prefix=TEST");

  @Test
  @DisplayName("没配内部令牌时启动失败，错误信息指明配置项")
  void should_fail_to_start_when_gateway_token_missing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .rootCause()
              .hasMessageContaining("patra.security.gateway-token");
        });
  }

  @Test
  @DisplayName("配了内部令牌时注册默认过滤器链和配套 Bean")
  void should_register_default_chain_and_companions_when_token_configured() {
    contextRunner
        .withPropertyValues(TOKEN_PROPERTY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SecurityProblemWriter.class);
              assertThat(context).hasSingleBean(SecurityErrorMappingContributor.class);
              assertThat(context).hasSingleBean(SecurityExceptionRethrowAdvice.class);
              assertThat(context.getBeansOfType(SecurityFilterChain.class))
                  .containsOnlyKeys("patraSecurityFilterChain");
            });
  }

  @Test
  @DisplayName("默认过滤器链：认证过滤器排在匿名过滤器之前，没有 CSRF 和登出过滤器")
  void should_build_stateless_chain_with_gateway_header_filter() {
    contextRunner
        .withPropertyValues(TOKEN_PROPERTY)
        .run(
            context -> {
              SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
              assertThat(chain.getFilters())
                  .extracting(filter -> filter.getClass().getSimpleName())
                  .containsSubsequence("AuthenticationFilter", "AnonymousAuthenticationFilter")
                  .doesNotContain("CsrfFilter", "LogoutFilter");
            });
  }

  @Test
  @DisplayName("应用自己声明过滤器链时默认链让位，无状态默认配置可以被复用")
  void should_back_off_default_chain_when_application_declares_one() {
    contextRunner
        .withPropertyValues(TOKEN_PROPERTY)
        .withUserConfiguration(CustomChainConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(SecurityFilterChain.class))
                  .containsOnlyKeys("customChain");
            });
  }

  @Test
  @DisplayName("非 Web 环境下不注册 servlet 相关的 Bean，也不要求内部令牌")
  void should_skip_servlet_beans_without_web_environment() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SecurityCoreAutoConfiguration.class, SecurityServletAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CurrentUserPort.class);
              assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
              assertThat(context).doesNotHaveBean(PatraSecurityProperties.class);
            });
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains("dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration");
  }

  /// 模拟网关：声明自己的过滤器链，复用无状态默认配置，再加自己的规则。
  @Configuration(proxyBeanMethods = false)
  static class CustomChainConfig {

    /// 自定义过滤器链。
    ///
    /// @param http Spring Security 的构建器
    /// @param problemWriter 统一的错误写出器
    /// @return 过滤器链
    @Bean
    SecurityFilterChain customChain(HttpSecurity http, SecurityProblemWriter problemWriter) {
      StatelessSecurityDefaults.apply(http, problemWriter);
      http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
      return http.build();
    }
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityServletAutoConfigurationTest"`
预期：编译失败，`cannot find symbol … class SecurityServletAutoConfiguration` 和 `class StatelessSecurityDefaults`。

- [ ] **步骤 6：实现无状态默认配置和 servlet 自动配置（先不含拒绝一切的认证管理器）**

新建 `src/main/java/dev/linqibin/patra/starter/security/config/StatelessSecurityDefaults.java`：

```java
package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/// Patra 各服务过滤器链共用的无状态默认配置。
///
/// 下游的默认过滤器链用它；网关写自己的过滤器链时调用同一个方法，再加自己的规则。
public final class StatelessSecurityDefaults {

  /// 工具类，不允许实例化。
  private StatelessSecurityDefaults() {}

  /// 应用无状态默认配置。
  ///
  /// - 不创建 HttpSession：安全上下文只放在请求属性里。
  /// - 关 CSRF：凭据在请求头里，不在 Cookie 里。
  /// - 关登出：不关的话 `/logout` 路径会被框架劫持并重定向。
  /// - 未登录输出 401、拒绝访问输出 403，都走统一的写出器。关掉表单登录后，
  ///   框架默认对未登录给的是 403，所以必须显式指定。
  ///
  /// 匿名认证保持框架默认（开）：框架靠匿名认证对象区分「没登录」和「登录了但不允许」。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  public static void apply(HttpSecurity http, SecurityProblemWriter problemWriter) {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(problemWriter)
                    .accessDeniedHandler(problemWriter));
  }
}
```

新建 `src/main/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfiguration.java`：

```java
package dev.linqibin.patra.starter.security.config;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.GatewayHeaderAuthenticationConverter;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import dev.linqibin.patra.starter.security.error.SecurityExceptionRethrowAdvice;
import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/// 安全 starter 的 servlet 自动配置：过滤器链、错误输出、内部令牌。
///
/// 排在 Spring Boot 的默认用户和默认过滤器链之前，它们检测到这里的 Bean 后会让位。
@AutoConfiguration(
    before = {UserDetailsServiceAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PatraSecurityProperties.class)
public class SecurityServletAutoConfiguration {

  /// 注册统一的错误写出器。
  ///
  /// @param problemDetailAdapter 把异常变成 ProblemDetail 的适配器
  /// @param jsonMapper 容器里的 JSON 映射器
  /// @return 错误写出器
  @Bean
  @ConditionalOnMissingBean
  public SecurityProblemWriter securityProblemWriter(
      ProblemDetailAdapter problemDetailAdapter, JsonMapper jsonMapper) {
    return new SecurityProblemWriter(problemDetailAdapter, jsonMapper);
  }

  /// 注册安全异常到错误码的映射。
  ///
  /// @param http 带服务前缀的标准 HTTP 错误码组
  /// @return 错误码映射
  @Bean
  @ConditionalOnMissingBean
  public SecurityErrorMappingContributor securityErrorMappingContributor(HttpStdErrors.Group http) {
    return new SecurityErrorMappingContributor(http);
  }

  /// 注册把控制器里的安全异常抛回过滤器的处理器。
  ///
  /// 服务扫描整个 `dev.linqibin` 时这个类会先被组件扫描注册，这里随之让位。
  ///
  /// @return 重抛处理器
  @Bean
  @ConditionalOnMissingBean
  public SecurityExceptionRethrowAdvice securityExceptionRethrowAdvice() {
    return new SecurityExceptionRethrowAdvice();
  }

  /// 下游的默认过滤器链：无会话，从网关请求头建立认证，所有路径放行。
  ///
  /// 下游不做路径级拦截，路由规则归网关。需要登录的接口由业务代码调
  /// `CurrentUserPort.require()` 来保证，这样服务之间直连的 `/_internal/**` 不受影响。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param properties 配置属性
  /// @return 过滤器链
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain patraSecurityFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter, PatraSecurityProperties properties) {
    StatelessSecurityDefaults.apply(http, problemWriter);

    // 转换器给出的已经是认证完成的对象，原样返回。
    // 必须用显式类型：AuthenticationFilter 的两个构造器对 lambda 有二义性。
    AuthenticationManager passThrough = authentication -> authentication;
    // 不声明成 Bean：否则 Boot 会把它再注册成全局 servlet 过滤器。
    AuthenticationFilter gatewayHeaderFilter =
        new AuthenticationFilter(
            passThrough, new GatewayHeaderAuthenticationConverter(properties.gatewayToken()));
    // 默认的成功处理器会回 302 再继续执行过滤器链，换成什么都不做。
    gatewayHeaderFilter.setSuccessHandler((request, response, authentication) -> {});
    // 默认的失败处理器遇到服务类异常会原样抛出，换成统一的写出器。
    gatewayHeaderFilter.setFailureHandler(problemWriter);

    http.addFilterBefore(gatewayHeaderFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
```

在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里追加一行：

```
dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityServletAutoConfigurationTest"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。

- [ ] **步骤 7：写「不生成默认用户」的失败测试（实测点：spec 第 14 节第 3 条）**

在 `SecurityServletAutoConfigurationTest` 里追加两个用例，并在 import 区加 `org.springframework.boot.test.system.CapturedOutput`、`org.springframework.security.authentication.AuthenticationManager`、`org.springframework.security.core.userdetails.UserDetailsService`：

```java
  @Test
  @DisplayName("不生成带随机密码的默认用户")
  void should_not_generate_default_user(CapturedOutput output) {
    contextRunner
        .withPropertyValues(TOKEN_PROPERTY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(UserDetailsService.class);
              assertThat(output).doesNotContain("Using generated security password");
            });
  }

  @Test
  @DisplayName("应用自己声明认证管理器时，拒绝一切的那个让位")
  void should_back_off_rejecting_manager_when_application_declares_one() {
    contextRunner
        .withPropertyValues(TOKEN_PROPERTY)
        .withUserConfiguration(CustomAuthenticationManagerConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(AuthenticationManager.class))
                  .containsOnlyKeys("customAuthenticationManager");
            });
  }
```

在测试类的末尾（`CustomChainConfig` 之后）加一个配置类：

```java
  /// 模拟网关：声明自己的认证管理器。
  @Configuration(proxyBeanMethods = false)
  static class CustomAuthenticationManagerConfig {

    /// 自定义认证管理器。
    ///
    /// @return 认证管理器
    @Bean
    AuthenticationManager customAuthenticationManager() {
      return authentication -> authentication;
    }
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityServletAutoConfigurationTest"`
预期：`should_not_generate_default_user` 失败：容器里有一个 `UserDetailsService`（Boot 生成的内存用户），输出里有 `Using generated security password`。另一个新用例通过。

如果 `should_not_generate_default_user` 在这一步就通过了，说明 Boot 在这个版本里的行为和 spec 的假设不同：停下来报告。

- [ ] **步骤 8：加上拒绝一切的认证管理器**

在 `SecurityServletAutoConfiguration` 的 import 区加：

```java
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
```

在 `securityExceptionRethrowAdvice()` 和 `patraSecurityFilterChain(…)` 之间加一个 Bean 方法：

```java
  /// 注册一个拒绝一切的认证管理器，让 Spring Boot 的默认用户让位。
  ///
  /// Boot 检测不到任何认证相关的 Bean 时，会生成一个带随机密码的内存用户并把密码
  /// 打进日志。应用里没有用户名密码登录，这个 Bean 不会被任何地方调用。
  /// 应用自己声明了这四类 Bean 中的任何一个时（比如网关的会话认证管理器），它让位。
  ///
  /// @return 拒绝一切的认证管理器
  @Bean
  @ConditionalOnMissingBean({
    AuthenticationManager.class,
    AuthenticationProvider.class,
    UserDetailsService.class,
    AuthenticationManagerResolver.class
  })
  public AuthenticationManager rejectingAuthenticationManager() {
    return authentication -> {
      throw new ProviderNotFoundException("本应用没有用户名密码登录");
    };
  }
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityServletAutoConfigurationTest"`
预期：`BUILD SUCCESSFUL`，8 个用例通过。

- [ ] **步骤 9：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 10：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 新增配置属性、无状态默认配置与两个自动配置 (PAP-62)"
```

---

### 任务 9：测试支持（安全 starter 的 `testFixtures`）

给使用方（本版是 identity）的测试提供两样东西：

- `TestIdentity`：返回一组请求头（四个身份头加测试用的内部令牌）。已登录的请求把它加到请求上，匿名的请求什么都不加。这种写法走的是真实的过滤器，切片测试和整应用测试用法相同。
- 一个环境后处理器：只要测试 classpath 上有本模块的 testFixtures，就自动把 `patra.security.gateway-token` 设成 `TestIdentity` 用的那个值。它放在最低优先级，测试里显式配置的值会覆盖它。

`spring-boot-security-test` 已经在任务 4 的构建脚本里作为 `testFixturesApi` 带给使用方。

testFixtures 源集没有配 Lombok，这里的类不用 Lombok。也不要在 testFixtures 里放 `logback-test.xml` 或 `application.yml` 这类文件：它们会进到使用方的测试 classpath，和使用方自己的同名文件冲突。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestIdentity.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessor.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/resources/META-INF/spring.factories`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessorTest.java`

**Interfaces:**

- Consumes: 任务 1 的 `CurrentUser`；任务 4 的 `IdentityHeaders`。Spring Boot 4 的 `org.springframework.boot.EnvironmentPostProcessor`（注意包名：4.0 起在 `org.springframework.boot` 下，`spring.factories` 里的键也是这个全名）。
- Produces：
  - `TestIdentity.GATEWAY_TOKEN`（`String`，值 `test-gateway-token`）、`TestIdentity.USER_ID`（`long`，`1001`）、`TestIdentity.SESSION_ID`（`long`，`2001`）。
  - `static CurrentUser TestIdentity.user()`、`user(long userId)`。
  - `static HttpHeaders TestIdentity.headers()`、`headers(long userId)`、`headers(CurrentUser user)`。
  - 默认用户是 `CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB)`。后面的测试里，探针接口对默认用户的输出是 `1001:2001:portal:web`。

- [ ] **步骤 1：写 `TestIdentity` 的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityTest.java`：

```java
package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.header.IdentityParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/// TestIdentity 单元测试。
@DisplayName("TestIdentity 单元测试")
class TestIdentityTest {

  @Test
  @DisplayName("默认的请求头带内部令牌，并能解析回默认用户")
  void should_produce_headers_that_parse_back_to_default_user() {
    HttpHeaders headers = TestIdentity.headers();

    assertThat(headers.getFirst(IdentityHeaders.GATEWAY_TOKEN))
        .isEqualTo(TestIdentity.GATEWAY_TOKEN);
    assertThat(IdentityHeaders.parse(headers))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @Test
  @DisplayName("可以只指定用户 ID")
  void should_use_given_user_id() {
    assertThat(IdentityHeaders.parse(TestIdentity.headers(42L)))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(42L, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @Test
  @DisplayName("可以指定整个用户")
  void should_use_given_user() {
    CurrentUser user = CurrentUser.of(7L, 8L, AccountType.PORTAL, ClientType.WEB);

    assertThat(IdentityHeaders.parse(TestIdentity.headers(user)))
        .isEqualTo(IdentityParseResult.identified(user));
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*TestIdentityTest"`
预期：编译失败，`cannot find symbol … class TestIdentity`。

- [ ] **步骤 2：实现 `TestIdentity`**

新建 `src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestIdentity.java`：

```java
package dev.linqibin.patra.starter.security.test;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import org.springframework.http.HttpHeaders;

/// 测试里构造「已登录请求」用的请求头。
///
/// 已登录的请求：把 `headers()` 返回的头加到请求上。匿名的请求：什么都不加。
/// 这种写法走的是真实的安全过滤器，切片测试和整应用测试用法相同。
public final class TestIdentity {

  /// 测试用的内部令牌。测试 classpath 上有本模块的 testFixtures 时，
  /// `patra.security.gateway-token` 会被自动设成这个值。
  public static final String GATEWAY_TOKEN = "test-gateway-token";

  /// 默认的用户 ID。
  public static final long USER_ID = 1001L;

  /// 默认的会话 ID。
  public static final long SESSION_ID = 2001L;

  /// 工具类，不允许实例化。
  private TestIdentity() {}

  /// 默认用户。
  ///
  /// @return 用户 ID 为 `USER_ID`、会话 ID 为 `SESSION_ID` 的门户网页端用户
  public static CurrentUser user() {
    return user(USER_ID);
  }

  /// 指定用户 ID、其余字段取默认值的用户。
  ///
  /// @param userId 用户 ID
  /// @return 用户
  public static CurrentUser user(long userId) {
    return CurrentUser.of(userId, SESSION_ID, AccountType.PORTAL, ClientType.WEB);
  }

  /// 默认用户的请求头。
  ///
  /// @return 四个身份头加内部令牌头
  public static HttpHeaders headers() {
    return headers(user());
  }

  /// 指定用户 ID 的请求头。
  ///
  /// @param userId 用户 ID
  /// @return 四个身份头加内部令牌头
  public static HttpHeaders headers(long userId) {
    return headers(user(userId));
  }

  /// 指定用户的请求头。
  ///
  /// @param user 用户
  /// @return 四个身份头加内部令牌头
  public static HttpHeaders headers(CurrentUser user) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(IdentityHeaders.GATEWAY_TOKEN, GATEWAY_TOKEN);
    IdentityHeaders.write(headers, user);
    return headers;
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*TestIdentityTest"`
预期：`BUILD SUCCESSFUL`，3 个用例通过。

- [ ] **步骤 3：写环境后处理器的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessorTest.java`：

```java
package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/// TestGatewayTokenEnvironmentPostProcessor 单元测试。
///
/// 这里只测它对环境做了什么；它是否真的被 Spring Boot 加载，由任务 10、12 的
/// 集成测试验证（没加载的话那些测试的应用会因为缺内部令牌而起不来）。
@DisplayName("TestGatewayTokenEnvironmentPostProcessor 单元测试")
class TestGatewayTokenEnvironmentPostProcessorTest {

  private static final String PROPERTY = "patra.security.gateway-token";

  private final TestGatewayTokenEnvironmentPostProcessor postProcessor =
      new TestGatewayTokenEnvironmentPostProcessor();

  @Test
  @DisplayName("没有配置时补上测试用的内部令牌")
  void should_add_test_token_when_absent() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo(TestIdentity.GATEWAY_TOKEN);
  }

  @Test
  @DisplayName("已有显式配置时不覆盖")
  void should_not_override_explicit_configuration() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("explicit", Map.of(PROPERTY, "explicit-token")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo("explicit-token");
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*TestGatewayTokenEnvironmentPostProcessorTest"`
预期：编译失败，`cannot find symbol … class TestGatewayTokenEnvironmentPostProcessor`。

- [ ] **步骤 4：实现环境后处理器并登记**

新建 `src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessor.java`：

```java
package dev.linqibin.patra.starter.security.test;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/// 测试里自动把 `patra.security.gateway-token` 设成 `TestIdentity.GATEWAY_TOKEN`。
///
/// 只要测试 classpath 上有本模块的 testFixtures 就生效。放在最低优先级，
/// 测试里显式配置的值会覆盖它。
public class TestGatewayTokenEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_SOURCE_NAME = "patraSecurityTestGatewayToken";

  /// 往环境里追加一个只含内部令牌的属性源。
  ///
  /// @param environment 应用的环境
  /// @param application 当前的 Spring 应用
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of("patra.security.gateway-token", TestIdentity.GATEWAY_TOKEN)));
  }
}
```

新建 `src/testFixtures/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.EnvironmentPostProcessor=\
dev.linqibin.patra.starter.security.test.TestGatewayTokenEnvironmentPostProcessor
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*TestGatewayTokenEnvironmentPostProcessorTest"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

- [ ] **步骤 5：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "test(security): 新增构造已登录请求的测试支持 (PAP-62)"
```

---

### 任务 10：整应用集成测试（安全 starter）

前面的任务都是零件级的测试。本任务起一个只存在于测试里的小应用，像真实服务一样扫描整个 `dev.linqibin`，用真实的 HTTP 请求验证整条链路。spec 第 14 节的第 1、2 条在这里实测。

本任务不新增生产代码。它的每个测试类第一次运行就应该通过：通过说明前面的零件装起来确实按 spec 工作；任何一个不通过，都说明前面某个任务的假设错了，按 Global Constraints 里的规定停下来报告。

几点安排：

- **真实端口**（`RANDOM_PORT`）：`RestTestClient` 会绑定到真实的内嵌 Tomcat。会话、Cookie、重定向这些行为在真实容器里验证最可靠。
- **需要数据库**：集成测试的 classpath 上有 starter-jpa，扫描整个 `dev.linqibin` 会把它的审计配置带进来，所以应用必须有数据源。用 starter-test 的 `PostgreSQLContainerInitializer` 起容器，表结构用 Flyway 脚本建。这也是真实服务的样子。
- **每个测试类的注解完全相同**，这样 Spring 的测试上下文缓存让它们共用一个应用实例。不要给单个测试类加额外的属性或 Mock，否则会多起一个应用。
- 内部令牌不用配：任务 9 的环境后处理器会自动设置。应用能起来，本身就验证了它被正确加载。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/resources/application.yml`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/resources/db/migration/V1__create_security_it_note.sql`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityITBootstrap.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITNoteEntity.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITNoteDao.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITForbiddenException.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITSessionCounter.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITProbeController.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/GatewayHeaderAuthenticationIT.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityErrorResponseIT.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/StatelessSessionIT.java`

**Interfaces:**

- Consumes: 任务 1 到 9 的全部产出。`RestTestClient`（`org.springframework.test.web.servlet.client.RestTestClient`，由 `@AutoConfigureRestTestClient` 注入）。
- Produces（任务 11 复用）：
  - `SecurityITBootstrap`：`@SpringBootApplication(scanBasePackages = "dev.linqibin")`。
  - `SecurityITNoteEntity`（表 `security_it_note`，继承 `BaseJpaEntity`，多一个 `content` 字段）、`SecurityITNoteDao extends JpaRepository<SecurityITNoteEntity, Long>`。
  - 探针接口：`GET /probe/whoami`（匿名返回 `anonymous`，已登录返回 `用户ID:会话ID:账号类型:客户端类型`）、`GET /probe/me`（需要登录）、`GET /probe/forbidden-by-domain`、`GET /probe/access-denied`、`GET /probe/boom`、`POST /probe/notes`（写一条记录，返回它的 ID）、`POST /logout`。

- [ ] **步骤 1：搭测试应用**

确认 Docker 在运行：`docker info`。

新建 `src/integrationTest/resources/application.yml`：

```yaml
# 安全 starter 集成测试配置
linqibin:
  starter:
    core:
      error:
        # 错误码前缀：断言里的 TEST-0401 / TEST-0403 / TEST-0500 由它决定
        context-prefix: TEST
```

新建 `src/integrationTest/resources/db/migration/V1__create_security_it_note.sql`：

```sql
-- 安全 starter 集成测试专用表：列与 BaseJpaEntity 一一对应
CREATE TABLE security_it_note
(
    id              BIGINT         NOT NULL,
    content         VARCHAR(200)   NULL,
    record_remarks  jsonb          NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT         NULL,
    created_by_name VARCHAR(100)   NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NULL,
    updated_by_name VARCHAR(100)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    ip_address      bytea          NULL,
    PRIMARY KEY (id)
);
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityITBootstrap.java`：

```java
package dev.linqibin.patra.starter.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的应用，只存在于测试里。
///
/// 和真实服务一样扫描整个 `dev.linqibin`：starter-web 的全局异常处理器、starter-jpa 的
/// 审计配置、本模块的重抛处理器都会被组件扫描提前注册。这正是要验证的启动方式。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class SecurityITBootstrap {}
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITNoteEntity.java`：

```java
package dev.linqibin.patra.starter.security.support;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：只为观察审计列，映射到表 `security_it_note`。
@Getter
@Setter
@Entity
@Table(name = "security_it_note")
public class SecurityITNoteEntity extends BaseJpaEntity {

  /// 任意内容。
  @Column(name = "content", length = 200)
  private String content;
}
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITNoteDao.java`：

```java
package dev.linqibin.patra.starter.security.support;

import org.springframework.data.jpa.repository.JpaRepository;

/// 集成测试专用实体的 JPA Repository。
public interface SecurityITNoteDao extends JpaRepository<SecurityITNoteEntity, Long> {}
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITForbiddenException.java`：

```java
package dev.linqibin.patra.starter.security.support;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 集成测试专用的领域异常：模拟业务代码判定「登录了但不允许」。
public class SecurityITForbiddenException extends DomainException {

  /// 创建带 `FORBIDDEN` 特征的异常。
  public SecurityITForbiddenException() {
    super("Probe forbidden", StandardErrorTrait.FORBIDDEN);
  }
}
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITSessionCounter.java`：

```java
package dev.linqibin.patra.starter.security.support;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/// 统计应用里创建过多少个 HttpSession。
///
/// Spring Boot 会把实现了 servlet 监听器接口的 Bean 自动注册到内嵌容器。
@Component
public class SecurityITSessionCounter implements HttpSessionListener {

  private final AtomicInteger created = new AtomicInteger();

  /// 每创建一个会话计数加一。
  ///
  /// @param event 会话创建事件
  @Override
  public void sessionCreated(HttpSessionEvent event) {
    created.incrementAndGet();
  }

  /// 返回至今创建过的会话数。
  ///
  /// @return 会话数
  public int createdCount() {
    return created.get();
  }
}
```

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITProbeController.java`：

```java
package dev.linqibin.patra.starter.security.support;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/// 集成测试专用的探针接口：把业务代码能观察到的东西原样返回。
@RestController
public class SecurityITProbeController {

  private final CurrentUserPort currentUserPort;
  private final SecurityITNoteDao noteDao;

  /// 创建探针控制器。
  ///
  /// @param currentUserPort 取当前用户的端口
  /// @param noteDao 测试实体的 Repository
  public SecurityITProbeController(CurrentUserPort currentUserPort, SecurityITNoteDao noteDao) {
    this.currentUserPort = currentUserPort;
    this.noteDao = noteDao;
  }

  /// 描述当前是谁。
  ///
  /// @return 匿名时是 `anonymous`，已登录时是 `用户ID:会话ID:账号类型:客户端类型`
  @GetMapping("/probe/whoami")
  public String whoAmI() {
    return currentUserPort.current().map(SecurityITProbeController::describe).orElse("anonymous");
  }

  /// 需要登录的接口。
  ///
  /// @return 当前用户的描述
  @GetMapping("/probe/me")
  public String me() {
    return describe(currentUserPort.require());
  }

  /// 业务代码判定「登录了但不允许」。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/forbidden-by-domain")
  public String forbiddenByDomain() {
    throw new SecurityITForbiddenException();
  }

  /// 控制器里直接抛出 Spring Security 的拒绝访问异常。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/access-denied")
  public String accessDenied() {
    throw new AccessDeniedException("probe: access denied");
  }

  /// 抛出一个与安全无关的异常，由全局异常处理器输出。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/boom")
  public String boom() {
    throw new UnsupportedOperationException("probe: boom");
  }

  /// 写一条记录，用来观察审计列。
  ///
  /// @return 新记录的 ID
  @PostMapping("/probe/notes")
  public String createNote() {
    SecurityITNoteEntity note = new SecurityITNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("probe");
    noteDao.save(note);
    return Long.toString(note.getId());
  }

  /// 业务自己的登出接口，用来确认 `/logout` 路径没有被框架劫持。
  ///
  /// @return 固定文本
  @PostMapping("/logout")
  public String logout() {
    return "logout handled by controller";
  }

  /// 把当前用户拼成一行文本。
  ///
  /// @param user 当前用户
  /// @return `用户ID:会话ID:账号类型:客户端类型`
  private static String describe(CurrentUser user) {
    return user.userId()
        + ":"
        + user.sessionId()
        + ":"
        + user.accountType().getCode()
        + ":"
        + user.clientType().getCode();
  }
}
```

`/probe/boom` 抛的是 `UnsupportedOperationException` 而不是 `IllegalStateException`：现有的错误解析引擎按类名做启发式判断，类名里带 `illegal` 的会被当成客户端错误解析成 422，这里要的是一个普通的 500。

- [ ] **步骤 2：写 spec 第 8.2 节四种情况的集成测试**

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/GatewayHeaderAuthenticationIT.java`：

```java
package dev.linqibin.patra.starter.security;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 从网关请求头建立认证的集成测试：spec 第 8.2 节的四种情况。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("从网关请求头建立认证 集成测试")
class GatewayHeaderAuthenticationIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("内部令牌正确、身份头齐全：已登录，四个字段都对，且不被重定向")
  void should_identify_user_when_token_and_identity_headers_valid() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.LOCATION)
        .expectBody(String.class)
        .isEqualTo("1001:2001:portal:web");
  }

  @Test
  @DisplayName("内部令牌正确、没有身份头：匿名")
  void should_be_anonymous_when_token_valid_and_no_identity_headers() {
    restClient
        .get()
        .uri("/probe/whoami")
        .header(IdentityHeaders.GATEWAY_TOKEN, TestIdentity.GATEWAY_TOKEN)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌缺失：身份头不被采信，按匿名处理")
  void should_be_anonymous_when_token_missing() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.GATEWAY_TOKEN);
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌不对：身份头不被采信，按匿名处理")
  void should_be_anonymous_when_token_wrong() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌正确、身份头残缺：500，ProblemDetail 格式")
  void should_reject_with_500_problem_when_identity_headers_partial() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.SESSION_ID);
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(500)
        .jsonPath("$.code")
        .isEqualTo("TEST-0500")
        .jsonPath("$.title")
        .isEqualTo("TEST-0500")
        .jsonPath("$.detail")
        .isEqualTo("Internal Server Error")
        .jsonPath("$.instance")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.path")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  @DisplayName("内部令牌正确、用户 ID 不是正整数：500")
  void should_reject_with_500_problem_when_user_id_invalid() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.USER_ID, "abc");
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0500");
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*GatewayHeaderAuthenticationIT"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。第一次运行会拉起 PostgreSQL 容器。

应用起不来时先看原因：如果是 `patra.security.gateway-token 不能为空`，说明任务 9 的环境后处理器没被加载，检查 `spring.factories` 的路径和键名。

- [ ] **步骤 3：写错误输出的集成测试（实测点：spec 第 14 节第 1、2 条）**

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityErrorResponseIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 错误输出的集成测试：spec 第 9 节的几条路径输出同一种格式。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("错误输出 集成测试")
class SecurityErrorResponseIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  /// 不带语义特征的异常输出的字段。带特征的领域异常会多一个 `traits`。
  private static final Set<String> BASE_FIELDS =
      Set.of("type", "title", "status", "detail", "instance", "code", "path", "timestamp");

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("需要登录但没登录：401，字段与现有错误格式一致")
  void should_return_401_problem_when_require_and_anonymous() {
    restClient
        .get()
        .uri("/probe/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/me")
        .jsonPath("$.traits[0]")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("需要登录且已登录：正常返回当前用户")
  void should_return_user_when_require_and_logged_in() {
    restClient
        .get()
        .uri("/probe/me")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("1001:2001:portal:web");
  }

  @Test
  @DisplayName("业务代码抛带 FORBIDDEN 特征的领域异常：403")
  void should_return_403_problem_when_domain_exception_is_forbidden() {
    restClient
        .get()
        .uri("/probe/forbidden-by-domain")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0403")
        .jsonPath("$.instance")
        .isEqualTo("/probe/forbidden-by-domain");
  }

  @Test
  @DisplayName("控制器里抛出拒绝访问、匿名：401，由安全过滤器输出，日志里不留多余的错误记录")
  void should_return_401_when_controller_throws_access_denied_and_anonymous(CapturedOutput output) {
    restClient
        .get()
        .uri("/probe/access-denied")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/access-denied");

    assertThat(output)
        .doesNotContain("Exception handled")
        .doesNotContain("Failure in @ExceptionHandler")
        .doesNotContain("threw exception");
  }

  @Test
  @DisplayName("控制器里抛出拒绝访问、已登录：403，不泄露异常的原始消息")
  void should_return_403_when_controller_throws_access_denied_and_logged_in() {
    restClient
        .get()
        .uri("/probe/access-denied")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0403")
        .jsonPath("$.detail")
        .isEqualTo("Access denied")
        .jsonPath("$.instance")
        .isEqualTo("/probe/access-denied");
  }

  @Test
  @DisplayName("过滤器里输出的和控制器里输出的错误响应，字段集合相同")
  void should_write_same_field_set_in_filter_and_controller() {
    Map<String, Object> fromController =
        restClient
            .get()
            .uri("/probe/boom")
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .expectBody(JSON_OBJECT)
            .returnResult()
            .getResponseBody();

    Map<String, Object> fromFilter =
        restClient
            .get()
            .uri("/probe/whoami")
            .headers(
                headers -> {
                  headers.addAll(TestIdentity.headers());
                  headers.remove(IdentityHeaders.SESSION_ID);
                })
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .expectBody(JSON_OBJECT)
            .returnResult()
            .getResponseBody();

    assertThat(fromController).containsOnlyKeys(BASE_FIELDS);
    assertThat(fromFilter).containsOnlyKeys(BASE_FIELDS);
  }
}
```

关于「字段集合相同」这个用例：两边都选了不带语义特征的异常来比。`require()` 抛的领域异常会多输出一个 `traits` 字段，这是现有错误格式对带特征异常的既有行为，不是两条路径的差别。

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*SecurityErrorResponseIT"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。

如果 `should_write_same_field_set_in_filter_and_controller` 失败（第 14 节第 1 条），或 `should_return_401_when_controller_throws_access_denied_and_anonymous` 失败（第 14 节第 2 条：没有回到安全过滤器，或日志里出现了多余的错误记录）：停下来报告，附上实际的响应体或日志行。

- [ ] **步骤 4：写无会话行为的集成测试**

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/StatelessSessionIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.support.SecurityITSessionCounter;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 无会话行为的集成测试：不建会话、不拦 POST、不劫持 `/logout`、不生成默认用户。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("无会话行为 集成测试")
class StatelessSessionIT {

  @Autowired private RestTestClient restClient;
  @Autowired private SecurityITSessionCounter sessionCounter;
  @Autowired private ApplicationContext applicationContext;

  private void getWithoutCookie(String uri, Consumer<HttpHeaders> headers) {
    restClient
        .get()
        .uri(uri)
        .headers(headers)
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  @Test
  @DisplayName("匿名、已登录、401、403、500 的请求都不创建会话，也不下发 Cookie")
  void should_never_create_session_or_set_cookie() {
    getWithoutCookie("/probe/whoami", headers -> {});
    getWithoutCookie("/probe/whoami", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie("/probe/me", headers -> {});
    getWithoutCookie("/probe/access-denied", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie(
        "/probe/whoami",
        headers -> {
          headers.addAll(TestIdentity.headers());
          headers.remove(IdentityHeaders.SESSION_ID);
        });

    assertThat(sessionCounter.createdCount()).isZero();
  }

  @Test
  @DisplayName("POST 请求不被 CSRF 拦截")
  void should_accept_post_without_csrf_token() {
    restClient.post().uri("/probe/notes").exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("/logout 路径由业务控制器处理，不被框架劫持")
  void should_route_logout_path_to_controller() {
    restClient
        .post()
        .uri("/logout")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("logout handled by controller");
  }

  @Test
  @DisplayName("容器里没有带随机密码的默认用户")
  void should_not_register_default_user() {
    assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*StatelessSessionIT"`
预期：`BUILD SUCCESSFUL`，4 个用例通过。

- [ ] **步骤 5：确认这些测试真的会失败**

三个测试类都是第一次运行就通过的，需要确认它们不是「怎么都会过」。做一次临时改动：在 `SecurityServletAutoConfiguration.patraSecurityFilterChain` 里把这一行注释掉：

```java
    gatewayHeaderFilter.setSuccessHandler((request, response, authentication) -> {});
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*GatewayHeaderAuthenticationIT"`
预期：`should_identify_user_when_token_and_identity_headers_valid` 失败（默认的成功处理器回了重定向，拿不到 `1001:2001:portal:web`）。

把那一行恢复原样，再跑一次同样的命令，确认回到全部通过。用 `git diff patra-starters/patra-spring-boot-starter-security/src/main` 确认主代码没有残留改动。

- [ ] **步骤 6：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check :patra-starters:patra-spring-boot-starter-security:integrationTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 7：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "test(security): 新增安全 starter 的整应用集成测试 (PAP-62)"
```

---

### 任务 11：审计人接入（安全 starter）

让引了安全 starter 的服务，登录用户写入的记录自动带上他的用户 ID。做法是实现任务 2 的 `CurrentAuditorProvider`，从 `CurrentUserPort` 取用户 ID，并用第三个自动配置把它注册进容器。

三种情况都要验证：

| 情况 | 预期 | 测试 |
|---|---|---|
| Web 应用（扫描整个 `dev.linqibin`），登录用户写入 | `created_by` 是他的用户 ID | `SecurityAuditingIT` |
| 同上，匿名写入 | `created_by` 为空 | `SecurityAuditingIT` |
| 两个 starter 都在、以非 Web 方式启动，用 `CurrentUserRunner` 带身份写入 | `created_by` 是指定的用户 ID | `NonWebStartupIT` |
| classpath 上没有 starter-jpa | 应用正常启动，不注册审计人提供者 | `SecurityAuditingWithoutJpaTest` |

关于最后一条为什么放在 `src/test`、为什么按名字导入：单元测试的 classpath 上没有 starter-jpa（它在主代码里是 `compileOnly`），正好是要验证的环境。但在这个环境里不能用 `AutoConfigurations.of(SecurityAuditingAutoConfiguration.class)` 按类注册：按类注册时 Spring 用反射读注解，读不到指向缺失类的 `@ConditionalOnClass`，会把整个条件当作不存在，接着在检查 Bean 方法时因为找不到返回类型而失败。真实应用是从 imports 文件按名字加载自动配置的，那条路径用字节码读注解，不会加载类。测试里用一个 `ImportSelector` 走同样的按名字导入的路径。

**Files:**

- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/audit/CurrentUserAuditorProvider.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityAuditingAutoConfiguration.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityAuditingIT.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/NonWebStartupIT.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityAuditingWithoutJpaTest.java`

**Interfaces:**

- Consumes: 任务 2 的 `CurrentAuditorProvider`（`Optional<Long> currentAuditorId()`）；任务 1 的 `CurrentUserPort`；任务 5 的 `CurrentUserRunner`；任务 8 的 `SecurityCoreAutoConfiguration`；任务 9 的 `TestIdentity`；任务 10 的 `SecurityITBootstrap`、`SecurityITNoteEntity`、`SecurityITNoteDao`、`POST /probe/notes`。
- Produces：
  - `class CurrentUserAuditorProvider implements CurrentAuditorProvider`，构造器 `(CurrentUserPort currentUserPort)`。
  - `SecurityAuditingAutoConfiguration`：classpath 上有 `CurrentAuditorProvider` 时注册名为 `currentAuditorProvider` 的 Bean。

- [ ] **步骤 1：写 Web 应用下审计列的失败测试**

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityAuditingIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.support.SecurityITNoteDao;
import dev.linqibin.patra.starter.security.support.SecurityITNoteEntity;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 审计列的集成测试：在「扫描整个 dev.linqibin」的启动方式下，登录用户写入的记录带上他的用户 ID。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("审计列 集成测试")
class SecurityAuditingIT {

  @Autowired private RestTestClient restClient;
  @Autowired private SecurityITNoteDao noteDao;

  private SecurityITNoteEntity createNote(Consumer<HttpHeaders> headers) {
    String noteId =
        restClient
            .post()
            .uri("/probe/notes")
            .headers(headers)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return noteDao.findById(Long.valueOf(noteId)).orElseThrow();
  }

  @Test
  @DisplayName("登录用户写入的记录，创建人和更新人是他的用户 ID")
  void should_fill_auditor_columns_with_user_id_when_logged_in() {
    SecurityITNoteEntity note =
        createNote(headers -> headers.addAll(TestIdentity.headers(4242L)));

    assertThat(note.getCreatedBy()).isEqualTo(4242L);
    assertThat(note.getUpdatedBy()).isEqualTo(4242L);
  }

  @Test
  @DisplayName("匿名写入的记录，创建人和更新人为空")
  void should_leave_auditor_columns_empty_when_anonymous() {
    SecurityITNoteEntity note = createNote(headers -> {});

    assertThat(note.getCreatedBy()).isNull();
    assertThat(note.getUpdatedBy()).isNull();
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*SecurityAuditingIT"`
预期：`should_fill_auditor_columns_with_user_id_when_logged_in` 失败，`createdBy` 期望 `4242L`、实际 `null`。匿名的用例通过。

- [ ] **步骤 2：写「没有 starter-jpa」的失败测试**

新建 `src/test/java/dev/linqibin/patra/starter/security/config/SecurityAuditingWithoutJpaTest.java`：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/// 没有 starter-jpa 时的自动配置测试。
///
/// 单元测试的 classpath 上没有 starter-jpa（它在主代码里是 compileOnly），正好是要验证的环境。
/// 审计自动配置必须按名字导入：真实应用从 imports 文件按名字加载它，那条路径不会加载类。
@DisplayName("没有 starter-jpa 时的自动配置测试")
class SecurityAuditingWithoutJpaTest {

  private static final String AUDITING_AUTO_CONFIGURATION =
      "dev.linqibin.patra.starter.security.config.SecurityAuditingAutoConfiguration";

  @Test
  @DisplayName("审计自动配置登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(AUDITING_AUTO_CONFIGURATION);
  }

  @Test
  @DisplayName("classpath 上没有 starter-jpa：应用正常启动，不注册审计人提供者")
  void should_start_without_auditor_provider_when_starter_jpa_absent() {
    assertThat(
            ClassUtils.isPresent(
                "dev.linqibin.starter.jpa.audit.CurrentAuditorProvider",
                getClass().getClassLoader()))
        .as("前提：单元测试的 classpath 上没有 starter-jpa")
        .isFalse();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecurityCoreAutoConfiguration.class))
        .withUserConfiguration(ImportAuditingByName.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CurrentUserPort.class);
              assertThat(context).doesNotHaveBean("currentAuditorProvider");
            });
  }

  /// 像 Spring Boot 加载 imports 文件那样，按名字导入审计自动配置。
  @Configuration(proxyBeanMethods = false)
  @Import(AuditingByNameSelector.class)
  static class ImportAuditingByName {}

  /// 只返回类名，不加载类。
  static class AuditingByNameSelector implements ImportSelector {

    /// 返回要导入的配置类的名字。
    ///
    /// @param importingClassMetadata 发起导入的配置类的元数据
    /// @return 审计自动配置的类名
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
      return new String[] {AUDITING_AUTO_CONFIGURATION};
    }
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityAuditingWithoutJpaTest"`
预期：两个用例都失败。第一个：imports 文件里没有这个类名。第二个：应用启动失败，因为要导入的类还不存在。

- [ ] **步骤 3：实现审计人提供者和审计自动配置**

新建 `src/main/java/dev/linqibin/patra/starter/security/audit/CurrentUserAuditorProvider.java`：

```java
package dev.linqibin.patra.starter.security.audit;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import java.util.Optional;

/// 用当前用户的 ID 作 JPA 审计的操作人。
///
/// 匿名请求、不带身份的后台线程没有当前用户，审计列留空。
public class CurrentUserAuditorProvider implements CurrentAuditorProvider {

  private final CurrentUserPort currentUserPort;

  /// 创建提供者。
  ///
  /// @param currentUserPort 取当前用户的端口
  public CurrentUserAuditorProvider(CurrentUserPort currentUserPort) {
    this.currentUserPort = currentUserPort;
  }

  /// 返回当前用户的 ID。
  ///
  /// @return 当前用户的 ID；没有当前用户时返回空
  @Override
  public Optional<Long> currentAuditorId() {
    return currentUserPort.current().map(CurrentUser::userId);
  }
}
```

新建 `src/main/java/dev/linqibin/patra/starter/security/config/SecurityAuditingAutoConfiguration.java`：

```java
package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.audit.CurrentUserAuditorProvider;
import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 安全 starter 的审计自动配置：把当前用户接到 JPA 审计上。
///
/// classpath 上有 starter-jpa 时才生效。不带 Web 条件：只跑定时任务的进程里，
/// 用 `CurrentUserRunner` 带上身份写入的记录同样会填上用户 ID。
@AutoConfiguration(after = SecurityCoreAutoConfiguration.class)
@ConditionalOnClass(CurrentAuditorProvider.class)
public class SecurityAuditingAutoConfiguration {

  /// 注册用当前用户 ID 作操作人的提供者。应用自己提供了就让位。
  ///
  /// @param currentUserPort 取当前用户的端口
  /// @return 操作人提供者
  @Bean
  @ConditionalOnMissingBean
  public CurrentAuditorProvider currentAuditorProvider(CurrentUserPort currentUserPort) {
    return new CurrentUserAuditorProvider(currentUserPort);
  }
}
```

在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里，在 `SecurityCoreAutoConfiguration` 那一行的后面插入一行，文件最终是三行：

```
dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration
dev.linqibin.patra.starter.security.config.SecurityAuditingAutoConfiguration
dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration
```

- [ ] **步骤 4：运行，确认两处都通过**

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test --tests "*SecurityAuditingWithoutJpaTest"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*SecurityAuditingIT"`
预期：`BUILD SUCCESSFUL`，2 个用例通过。

- [ ] **步骤 5：写非 Web 方式启动的集成测试**

这个测试的应用是测试类里的嵌套类，只用自动配置、不扫描整个 `dev.linqibin`（原因见「与 spec 的出入」第 1 条）。嵌套在测试类里的配置类不会被 `SecurityITBootstrap` 的组件扫描捡到。实体和 Repository 靠自动配置的包扫描发现，所以测试类必须放在 `dev.linqibin.patra.starter.security` 这个包里。

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/NonWebStartupIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.CurrentUserRunner;
import dev.linqibin.patra.starter.security.support.SecurityITNoteDao;
import dev.linqibin.patra.starter.security.support.SecurityITNoteEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;

/// 非 Web 方式启动的集成测试：安全 starter 加 starter-jpa，没有 Web 环境。
///
/// 对应只跑定时任务的进程。这里没有过滤器链，身份靠 `CurrentUserRunner` 带上。
@SpringBootTest(
    classes = NonWebStartupIT.NonWebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("非 Web 方式启动 集成测试")
class NonWebStartupIT {

  @Autowired private SecurityITNoteDao noteDao;
  @Autowired private CurrentUserPort currentUserPort;
  @Autowired private ApplicationContext applicationContext;

  private static SecurityITNoteEntity newNote() {
    SecurityITNoteEntity note = new SecurityITNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("non-web");
    return note;
  }

  @Test
  @DisplayName("应用正常启动，没有安全过滤器链")
  void should_start_without_security_filter_chain() {
    assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("以某个用户的身份执行时，写入的记录带上他的用户 ID")
  void should_fill_auditor_columns_when_write_runs_as_user() {
    CurrentUser user = CurrentUser.of(4343L, 9001L, AccountType.PORTAL, ClientType.WEB);

    Long noteId = CurrentUserRunner.callAs(user, () -> noteDao.save(newNote()).getId());

    SecurityITNoteEntity saved = noteDao.findById(noteId).orElseThrow();
    assertThat(saved.getCreatedBy()).isEqualTo(4343L);
    assertThat(saved.getUpdatedBy()).isEqualTo(4343L);
    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("不带身份的线程写入时，创建人为空")
  void should_leave_auditor_columns_empty_without_identity() {
    Long noteId = noteDao.save(newNote()).getId();

    assertThat(noteDao.findById(noteId).orElseThrow().getCreatedBy()).isNull();
  }

  /// 只用自动配置的最小应用，不做大范围的组件扫描。
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class NonWebApplication {}
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*NonWebStartupIT"`
预期：`BUILD SUCCESSFUL`，3 个用例通过。这个测试第一次运行就应该通过：它验证的是步骤 3 的同一份实现在另一种启动方式下的表现。

- [ ] **步骤 6：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check :patra-starters:patra-spring-boot-starter-security:integrationTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 7：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "feat(security): 登录用户写入的记录自动带上用户 ID (PAP-62)"
```

---

### 任务 12：切片测试与 README（安全 starter）

使用方（identity 的 adapter 层）会用 `@WebMvcTest` 加 `RestTestClient` 写切片测试。本任务用同样的方式写一个切片测试，确认安全 starter 在切片里能正常工作（spec 第 14 节第 4 条），然后把这套用法写进 README。

切片测试里请求是在测试线程上同步执行的，正好用来验证 Review Focus 第 2 条：同一个线程先后处理两个请求，后一个看不到前一个的身份。

切片测试的配置根类写成测试类里的嵌套类。如果写成顶层类，会被任务 10 的 `SecurityITBootstrap` 扫描进去。

**Files:**

- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/slice/SecurityWebMvcSliceIT.java`
- Create: `patra-starters/patra-spring-boot-starter-security/README.md`

**Interfaces:**

- Consumes: 任务 8 的两个自动配置；任务 9 的 `TestIdentity` 和环境后处理器；starter-core 的 `CoreErrorAutoConfiguration`、`dev.linqibin.starter.core.json.autoconfig.JacksonAutoConfiguration`；starter-web 的 `WebErrorAutoConfiguration`。
- Produces: README；一份可以照抄的切片测试配置。

- [ ] **步骤 1：写切片测试（实测点：spec 第 14 节第 4 条；Review Focus 第 2 条）**

新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/slice/SecurityWebMvcSliceIT.java`：

```java
package dev.linqibin.patra.starter.security.slice;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration;
import dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.core.json.autoconfig.JacksonAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// 切片测试：安全 starter 在 `@WebMvcTest` 加 `RestTestClient` 里的表现。
///
/// 使用方的切片测试照这个类的配置写：配置根类多导入两个安全自动配置，
/// 测试依赖里加上本模块的 testFixtures。
@WebMvcTest
@AutoConfigureRestTestClient
@Import(SecurityWebMvcSliceIT.SliceProbeController.class)
@DisplayName("安全 starter 切片测试")
class SecurityWebMvcSliceIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("带上 TestIdentity 的请求头：已登录")
  void should_identify_user_when_test_identity_headers_present() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("1001");
  }

  @Test
  @DisplayName("什么头都不带：匿名")
  void should_be_anonymous_when_no_headers() {
    restClient
        .get()
        .uri("/slice/whoami")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("需要登录但没登录：401，ProblemDetail 格式")
  void should_return_401_problem_when_require_and_anonymous() {
    restClient
        .get()
        .uri("/slice/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.instance")
        .isEqualTo("/slice/me");
  }

  @Test
  @DisplayName("身份头残缺：500，由安全过滤器输出")
  void should_return_500_problem_when_identity_headers_partial() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.SESSION_ID);
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0500")
        .jsonPath("$.instance")
        .isEqualTo("/slice/whoami");
  }

  @Test
  @DisplayName("同一线程上，已登录请求之后的匿名请求看不到上一个用户")
  void should_not_leak_identity_to_next_request_on_same_thread() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectBody(String.class)
        .isEqualTo("1001");

    restClient.get().uri("/slice/whoami").exchange().expectBody(String.class).isEqualTo("anonymous");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("同一线程上，身份头不合法的请求之后，匿名请求仍是匿名")
  void should_stay_anonymous_after_malformed_request_on_same_thread() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.USER_ID, "abc");
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500);

    restClient.get().uri("/slice/whoami").exchange().expectBody(String.class).isEqualTo("anonymous");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  /// 切片测试的配置根类：在现有的三个自动配置之外，多导入两个安全自动配置。
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ImportAutoConfiguration({
    CoreErrorAutoConfiguration.class,
    WebErrorAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    SecurityCoreAutoConfiguration.class,
    SecurityServletAutoConfiguration.class
  })
  static class SliceConfig {}

  /// 切片测试专用的探针接口。
  @RestController
  static class SliceProbeController {

    private final CurrentUserPort currentUserPort;

    /// 创建探针控制器。
    ///
    /// @param currentUserPort 取当前用户的端口
    SliceProbeController(CurrentUserPort currentUserPort) {
      this.currentUserPort = currentUserPort;
    }

    /// 描述当前是谁。
    ///
    /// @return 匿名时是 `anonymous`，已登录时是用户 ID
    @GetMapping("/slice/whoami")
    String whoAmI() {
      return currentUserPort
          .current()
          .map(user -> Long.toString(user.userId()))
          .orElse("anonymous");
    }

    /// 需要登录的接口。
    ///
    /// @return 当前用户的 ID
    @GetMapping("/slice/me")
    String me() {
      return Long.toString(currentUserPort.require().userId());
    }
  }
}
```

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*SecurityWebMvcSliceIT"`
预期：`BUILD SUCCESSFUL`，6 个用例通过。这个测试不需要数据库。

不通过时按 Global Constraints 的规定停下来报告（spec 第 14 节第 4 条）。两个接线问题先自查：应用起不来、报缺内部令牌，是任务 9 的 `spring.factories` 没生效；应用起不来、报找不到 `HttpSecurity`，是 testFixtures 没把 `spring-boot-security-test` 带进来，切片里没有 Spring Security 的基础设施。

- [ ] **步骤 2：确认切片测试真的经过了安全过滤器**

确认这个测试用到的是我们的过滤器链，而不是碰巧通过。做一次临时改动：把 `SliceConfig` 上 `@ImportAutoConfiguration` 里的 `SecurityServletAutoConfiguration.class` 一项删掉。

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*SecurityWebMvcSliceIT"`
预期：失败。没有我们的过滤器链时，生效的是 Spring Boot 自己的默认链，它要求所有请求登录，匿名的用例不再返回 200。

把那一项加回去，再跑一次，确认回到 6 个用例全部通过。

- [ ] **步骤 3：写 README**

新建 `patra-starters/patra-spring-boot-starter-security/README.md`：

````markdown
# patra-spring-boot-starter-security

基于 Spring Security 的安全 starter。它让服务从网关传来的身份头得到「当前用户」，并把 401 / 403 输出成统一的 ProblemDetail 格式。只做 servlet 一套。

工程设计：`docs/patra/specs/2026-10-05-security-starter-design.md`。

## 1. 怎么引入

只有需要识别用户的服务才引。Spring Security 一进 classpath，所有请求都会经过它的过滤器链。

boot 模块依赖本 starter：

```kotlin
implementation(project(":patra-starters:patra-spring-boot-starter-security"))
```

domain 或 app 层需要「当前用户」时，依赖纯 Java 的抽象模块，不依赖本 starter：

```kotlin
api(project(":patra-api:patra-common:patra-common-security"))
```

## 2. 要配什么

只有一个配置项，由部署时的 secret 注入，网关和下游必须拿到同一个值（下面的环境变量名只是示例）：

```yaml
patra:
  security:
    gateway-token: ${PATRA_GATEWAY_TOKEN}
```

没配、为空、带空白或换行、含非 ASCII 字符时，应用启动失败。它只在 servlet 应用里必填。

## 3. 业务代码怎么取当前用户

注入 `CurrentUserPort`：

```java
// 可以匿名访问的地方
Optional<CurrentUser> user = currentUserPort.current();

// 需要登录的地方：没登录时抛 AuthenticationRequiredException，输出为 {PREFIX}-0401
CurrentUser user = currentUserPort.require();
```

下游不做路径级的拦截，路由规则归网关。需要登录的接口由业务代码调 `require()` 来保证。

`CurrentUser` 里有用户 ID、会话 ID、账号类型、客户端类型，没有邮箱等个人信息。

引了 starter-jpa 的服务，登录用户写入的记录会自动填上 `created_by` / `updated_by`，不需要额外配置。

## 4. 定时任务、消息消费里怎么带身份

不在请求里的线程没有当前用户，`current()` 返回空，审计列留空。需要带身份时：

```java
CurrentUserRunner.runAs(user, () -> service.doSomething());

Result result = CurrentUserRunner.callAs(user, () -> service.compute());
```

执行结束（包括抛异常）后会恢复原来的身份。

## 5. 测试怎么写

测试依赖里加上本模块的 testFixtures：

```kotlin
testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
```

它带来三样东西：`TestIdentity`、自动设置的测试用内部令牌、`spring-boot-security-test`。

已登录的请求，把 `TestIdentity` 的请求头加上；匿名的请求，什么都不加：

```java
restClient
    .get()
    .uri("/me")
    .headers(headers -> headers.addAll(TestIdentity.headers()))
    .exchange()
    .expectStatus()
    .isOk();
```

`TestIdentity.headers(42L)` 指定用户 ID，`TestIdentity.headers(CurrentUser.of(…))` 指定全部字段。

`@WebMvcTest` 切片测试的配置根类，要在原有的三个自动配置之外多导入两个：

```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
  CoreErrorAutoConfiguration.class,
  WebErrorAutoConfiguration.class,
  JacksonAutoConfiguration.class,
  SecurityCoreAutoConfiguration.class,
  SecurityServletAutoConfiguration.class
})
public class IdentityAdapterITWebMvcConfig {}
```

两件事都不能省。少了那两个自动配置，生效的是 Spring Boot 自己的默认规则（所有请求都要求登录）。少了 testFixtures，切片里没有 Spring Security 的基础设施，测试应用起不来。

## 6. 信任前提

身份头没有签名。下游只认带着正确内部令牌的身份头，安全性依赖两件事：

- 服务不对外暴露，外部请求只能经过网关。
- 内部令牌不泄露。

内部令牌缺失或不对的请求按匿名处理，不会被拒绝：服务之间直连的调用（`/_internal/**`）不带令牌。

## 给网关用的部分

网关（PAP-65）声明自己的过滤器链，本 starter 的默认链随之让位。网关复用这几样：

| 类 | 用途 |
|---|---|
| `StatelessSecurityDefaults.apply(http, problemWriter)` | 无会话、关 CSRF、关登出、401 / 403 走统一写出器 |
| `CurrentUserAuthentication` | 查到会话后构造的认证对象 |
| `IdentityHeaders.write(headers, user)` | 往转发给下游的请求里写身份头 |
| `SecurityProblemWriter` | 过滤器链里的失败输出 |
````

- [ ] **步骤 4：格式化并跑模块的全部检查**

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:spotlessApply
```

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:check :patra-starters:patra-spring-boot-starter-security:integrationTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
git add patra-starters/patra-spring-boot-starter-security
git commit -m "test(security): 新增切片测试并补充模块说明 (PAP-62)"
```

---

### 任务 13：构建接入、回归、spec 收尾

**Files:**

- Modify: `patra-infra/cd/module-graph.json`（由 Gradle 任务生成，不手改）
- Modify: `docs/patra/specs/2026-10-05-security-starter-design.md`

**Interfaces:**

- Consumes: 前 12 个任务的全部产出。
- Produces: 通过全量检查的分支；与实现一致的 spec。

- [ ] **步骤 1：重新生成模块依赖图**

CI 用 `patra-infra/cd/module-graph.json` 决定每个单元跑哪些任务，并会检查它是否与构建一致。

```bash
./gradlew dumpModuleGraph
```

```bash
git diff --stat patra-infra/cd/module-graph.json
```

预期：文件有改动。用 `git diff patra-infra/cd/module-graph.json` 确认只有三处变化：

- 新增 `patra-api/patra-common/patra-common-security`，`unit` 是 `foundation`，`tasks` 是 `["check"]`。
- 新增 `patra-starters/patra-spring-boot-starter-security`，`unit` 是 `foundation`，`tasks` 是 `["check", "integrationTest"]`。
- `linqibin-commons/linqibin-spring-boot-starter-jpa` 的 `tasks` 多了 `integrationTest`。

- [ ] **步骤 2：检查模块边界**

linqibin-commons 的模块不能依赖 `:patra-*`：

```bash
./gradlew :linqibin-commons:linqibin-spring-boot-starter-jpa:checkBoundary :linqibin-commons:linqibin-spring-boot-starter-web:checkBoundary :linqibin-commons:linqibin-commons-core:checkBoundary
```

预期：`BUILD SUCCESSFUL`。

没引安全 starter 的服务，classpath 上不能有 Spring Security 的 web / config / core（`spring-security-crypto` 是 Spring Cloud 带来的，原来就有，不算）：

```bash
for service in catalog ingest registry object-storage; do
  echo "== ${service}"
  ./gradlew -q ":patra-api:patra-${service}:patra-${service}-boot:dependencies" --configuration runtimeClasspath \
    | grep -E "spring-security-(core|web|config)|spring-boot-security|spring-boot-starter-security" || echo "无"
done
```

预期：四个服务都输出 `无`。

- [ ] **步骤 3：全量回归**

```bash
./gradlew check
```

预期：`BUILD SUCCESSFUL`。它包含所有模块的单元测试、Spotless、SpotBugs，以及各服务 domain 模块的纯净性检查。

```bash
./gradlew integrationTest
```

预期：`BUILD SUCCESSFUL`。这一步覆盖 spec 的两条回归要求：starter-web 降优先级之后，各服务 adapter 层的切片测试全部通过；starter-jpa 改动之后，各服务 infra 层的持久化测试全部通过。

不要以「太慢」为由跳过这两条命令。端到端测试（`e2eTest`）不在本地门控里，开 PR 之后由 CI 对所有受影响的单元执行。

- [ ] **步骤 4：把实测结果和两处出入写回 spec**

修改 `docs/patra/specs/2026-10-05-security-starter-design.md`，四处。

第一处，文件开头的状态行：

```markdown
> **状态**：待评审
```

换成：

```markdown
> **状态**：已实现
```

第二处，第 13.2 节里的这一条：

```markdown
- 两个 starter 都在、以非 Web 方式启动的应用能正常启动，`CurrentUserRunner` 里写入的记录带上用户 ID。
```

换成：

```markdown
- 两个 starter 都在、以非 Web 方式启动的应用能正常启动，`CurrentUserRunner` 里写入的记录带上用户 ID。这个用例的测试应用只用自动配置，不扫描整个 `dev.linqibin`：starter-web 的全局异常处理器会被组件扫描注册，而它依赖的 Bean 只在 servlet 环境下才有，所以大范围扫描的应用本来就不能以非 Web 方式启动，这与本模块无关。
```

第三处，把第 14 节整节（从 `## 14. 待实测的点` 到 `## 15. README` 之前）换成下面的内容。「结果」一列按实际情况填写，下面写的是预期：

```markdown
## 14. 实测结果

设计阶段有五个结论来自阅读源码，实现时逐个用测试确认。

| # | 结论 | 结果 | 对应测试 |
|---|---|---|---|
| 1 | `SecurityProblemWriter` 补上 `instance` 之后，输出与全局异常处理器的字段集合一致 | 成立 | `SecurityErrorResponseIT` |
| 2 | 安全异常重抛处理器原样抛出后回到安全过滤器，日志里不留多余的错误记录 | 成立 | `SecurityErrorResponseIT` |
| 3 | 拒绝所有请求的 `AuthenticationManager` Bean 足以让 Boot 不生成随机密码用户 | 成立 | `SecurityServletAutoConfigurationTest`、`StatelessSessionIT` |
| 4 | `STATELESS` 加自定义认证过滤器在 `@WebMvcTest` 加 `RestTestClient` 的切片测试里正常工作 | 成立 | `SecurityWebMvcSliceIT` |
| 5 | `GlobalRestExceptionHandler` 降优先级后，对网关代理失败的异常表现 | 本 Issue 无法实测：网关还是 WebFlux 版，classpath 上没有 starter-web。移交 PAP-69，见第 16 节 | 无 |
```

第四处，第 16 节的表格末尾加一行：

```markdown
| 网关切到 WebMVC 版并引入 starter-web 之后，实测全局异常处理器（已降一级优先级）对代理失败异常的表现（原第 14 节第 5 条） | PAP-69 |
```

- [ ] **步骤 5：把移交的那一条记到 PAP-69 上**

用 Linear 的工具在 PAP-69 的 To-Do 末尾加一条：

```markdown
- [ ] 引入 starter-web 之后，实测全局异常处理器（已在 PAP-62 降一级优先级）对代理失败异常的表现；结果写回 `docs/patra/specs/2026-10-05-security-starter-design.md` 第 14 节
```

当前会话没有 Linear 工具时，把这一条写进汇报，交给用户处理。

- [ ] **步骤 6：对照 PAP-62 的验收条目做最后确认**

逐条确认，每条都能指到一个已经通过的测试或命令：

| 验收条目 | 证据 |
|---|---|
| 带合法身份头和正确内部令牌的请求能拿到四个字段；不带身份头的是匿名 | `GatewayHeaderAuthenticationIT` |
| 内部令牌缺失或不对时身份头不被采信 | `GatewayHeaderAuthenticationIT`、`GatewayHeaderAuthenticationConverterTest` |
| 身份头残缺或不合法时返回 500 | `GatewayHeaderAuthenticationIT`、`IdentityHeadersTest` |
| 取不到当前用户时 401；控制器里抛拒绝访问时匿名 401、已登录 403；ProblemDetail 格式；两条路径字段集合相同 | `SecurityErrorResponseIT` |
| 不创建 HttpSession；POST 不被 CSRF 拦截；不被重定向；没有随机密码 | `StatelessSessionIT`、`GatewayHeaderAuthenticationIT`、`SecurityServletAutoConfigurationTest` |
| 没配内部令牌时启动失败；非 Web 方式能正常启动 | `SecurityServletAutoConfigurationTest`、`NonWebStartupIT` |
| 登录用户写入的记录 `created_by` 是他的用户 ID，匿名为空，在扫描整个 `dev.linqibin` 时也成立 | `SecurityAuditingIT` |
| 没引安全 starter 的服务没有 Spring Security，审计列和错误响应不变 | 步骤 2 的依赖检查、`JpaAuditingIT`、步骤 3 的全量回归 |
| domain 没有 Spring Security 依赖；linqibin-commons 不依赖新模块 | 步骤 3 的 `check`、步骤 2 的 `checkBoundary` |
| 相关模块 Gradle check 通过 | 步骤 3 |

- [ ] **步骤 7：提交**

```bash
git add patra-infra/cd/module-graph.json docs/patra/specs/2026-10-05-security-starter-design.md
git commit -m "docs(accounts): 写回安全 starter 的实测结果并更新模块依赖图 (PAP-62)"
```

到这里 PAP-62 的代码完成。不 push、不开 PR：这个分支还要承载 PAP-63 到 PAP-66，PR 的时机和评审节奏按根 `CLAUDE.md` 的「PR 与代码评审」一节，由用户决定。

---

## Spec 覆盖对照

| spec 章节 | 对应任务 |
|---|---|
| 4 模块 | 任务 1、4（模块与依赖）；任务 13（依赖图、边界检查） |
| 5 当前用户的抽象 | 任务 1 |
| 6 请求头约定 | 任务 4 |
| 7 认证对象 | 任务 5 |
| 8.1 过滤器链 | 任务 8（配置）；任务 10（行为验证） |
| 8.2 从请求头建立认证 | 任务 6（转换器）；任务 8（装进过滤器）；任务 10（四种情况） |
| 8.3 内部令牌 | 任务 6（比较）；任务 8（配置项与启动失败） |
| 8.4 取当前用户 | 任务 5 |
| 9.1 `SecurityProblemWriter` | 任务 7 |
| 9.2 `SecurityErrorMappingContributor` | 任务 7 |
| 9.3 控制器里抛出的安全异常 | 任务 3（starter-web）；任务 7（重抛处理器）；任务 10（行为验证） |
| 10 JPA 审计列 | 任务 2（starter-jpa）；任务 11（安全 starter） |
| 11 以某个用户的身份执行 | 任务 5；任务 11（非 Web 下写入带用户 ID） |
| 12 自动配置 | 任务 8（核心、servlet）；任务 11（审计） |
| 13.1 测试支持 | 任务 9；任务 4（`spring-boot-security-test` 的依赖） |
| 13.2 测试策略 | 各任务的测试；回归与构建层面在任务 13 |
| 14 待实测的点 | 第 1、2 条任务 10；第 3 条任务 8；第 4 条任务 12；第 5 条移交 PAP-69 |
| 15 README | 任务 12 |
| 16 交给其他 Issue 的约束 | 不在本计划内实现；任务 13 追加一行 |
