# identity 账号实现计划（PAP-63）

> **给执行计划的 agent：** 必须使用子技能 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐个实现本计划。步骤用复选框（`- [ ]`）语法跟踪进度。

**目标：** 新建六边形微服务 `patra-identity`，交付前台用户的注册、登录校验、登录失败限制、封禁与解封，并补齐 linqibin-commons 统一错误格式的三处缺口。

**架构：** 五个模块（domain / app / infra / adapter / boot）。领域层两个聚合根 `User` 和 `UserPasswordCredential`，密码规则、失败限制规则在领域层，哈希（Argon2id）、常见密码名单、失败计数（Redis + Lua）、持久化（JPA + Flyway）在基础设施层，对外是前台的 `AuthController` 和后台的 `AdminUserController`，写操作都走 CommandBus。linqibin-commons 新增「字段错误带原因码」「429 带剩余时间」两个接口，并把 4xx 的日志降到 WARN、修掉参数校验失败时带出字段原始值的问题。

**技术栈：** Java 25、Spring Boot 4.0.8、Spring Framework 7.0.9、Spring Security Crypto 7.0.7（Argon2）、BouncyCastle 1.86、Spring Data JPA / Hibernate 7、Spring Data Redis（Lettuce）、Flyway、Jackson 3（`tools.jackson`）、Gradle 9.5（Kotlin DSL + Convention Plugins）、JUnit 5 + AssertJ + Mockito、Testcontainers（PostgreSQL 17、Redis 7.0.15）。

**Spec：** `docs/patra/specs/2026-10-05-identity-account-design.md`。执行时两份都要读：本计划讲怎么做，spec 讲为什么这么做。

**Issue：** [PAP-63](https://linear.app/papertrace/issue/PAP-63)。分支 `feat/v0.8-accounts-api`（已存在，直接在上面工作）。

## Global Constraints

每个任务都隐含遵守本节。

**来自 spec 的硬约束**

- 服务名 `patra-identity`，端口 `6400`，库 `patra_identity`，错误码前缀 `IDN`，包名根 `dev.linqibin.patra.identity`。
- 五个模块：`patra-identity-domain`、`-app`、`-infra`、`-adapter`、`-boot`，Gradle 路径都在 `:patra-api:patra-identity` 下。不建 `-api` 模块。
- 账号类型：前台用户 `USER`（code `user`）。本版不加 `STAFF`。
- 表：`idn_user`（`id`、`email VARCHAR(254)`、`status VARCHAR(16)`、`banned_at`）、`idn_user_password_credential`（`id`、`user_id`、`password_hash VARCHAR(255)`），都带 `BaseJpaEntity` 的标准审计列。约束：`uk_idn_user_email`、`ck_idn_user_email_lowercase`（`email = lower(email)`）、`ck_idn_user_status`（`ACTIVE`、`BANNED`）、`ck_idn_user_banned_at`（状态为 `BANNED` 时 `banned_at` 非空，否则为空）、`uk_idn_user_password_credential_user`、`fk_idn_user_password_credential_user`。
- 接口：`POST /auth/register` 成功 `201`；`POST /auth/login` 成功 `200`；两者的响应体都是 `{ "userId": "…", "email": "…" }`（`userId` 按全局设置输出成字符串）。`POST /admin/users/{userId}/ban`、`POST /admin/users/{userId}/unban` 成功 `204`，没有请求体。
- 字段名 `email`、`password`。原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`。
- 邮箱：先 `strip()`，为空报 `REQUIRED`，超过 254 个字符报 `TOO_LONG`，再用 Zod 4.6.5 默认 `z.email()` 的正则校验格式，通过后转小写（`Locale.ROOT`）保存。
- 密码：不去空格；空串或 `null` 报 `REQUIRED`；含孤立的 UTF-16 代理项报 `INVALID_CHARACTER`；按码点计长度，少于 8 报 `TOO_SHORT`，多于 64 报 `TOO_LONG`；在常见名单里报 `TOO_COMMON`。登录只检查 `REQUIRED` 和 `INVALID_CHARACTER`。
- 常见密码名单：Django 6.0 标签下的 `django/contrib/auth/common-passwords.txt.gz`，整份加载，不按长度过滤。比较键：NFKC → `toLowerCase(Locale.ROOT)` → `strip()`。
- 哈希：`new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`，哈希前和校验前都对密码做 NFKC。同时进行的哈希计算最多 4 个，排队超过 3 秒抛 `TemporarilyUnavailableException`。
- 失败限制：计数窗口 15 分钟（从窗口内第一次失败算起），失败 5 次锁 15 分钟，在途登记 30 秒过期。三个 Redis 键：`idn:login-failures:{账号类型 code}:{邮箱 SHA-256 十六进制}`、`idn:login-inflight:…`、`idn:login-lock:…`。「失败次数 + 在途次数」到上限时返回 429，`retryAfterSeconds` 为 1，不上锁。只有确认的失败才计数、才上锁。
- 错误码与 `detail`（固定文案，不带邮箱和用户输入）：

  | 异常 | 特征 | 状态 | detail |
  |---|---|---|---|
  | `InvalidUserFieldsException` | `RULE_VIOLATION` | 422 | 请求参数不合法 |
  | `EmailAlreadyRegisteredException` | `CONFLICT` | 409 | 该邮箱已注册 |
  | `InvalidCredentialsException` | `UNAUTHORIZED` | 401 | 邮箱或密码错误 |
  | `LoginTemporarilyLockedException` | `QUOTA_EXCEEDED` | 429 | 尝试次数过多，请稍后再试 |
  | `UserBannedException` | `FORBIDDEN` | 403 | 该账号已被封禁 |
  | `UserNotFoundException` | `NOT_FOUND` | 404 | 用户不存在 |
  | `TemporarilyUnavailableException` | `DEP_UNAVAILABLE` | 503 | 服务暂时不可用 |

- 429 同时输出响应头 `Retry-After`（秒，向上取整，最小 1）和响应体字段 `retryAfterSeconds`（同值）。
- 字段错误输出在 `errors[]`，每项 `{ "field", "code", "rejectedValue", "message" }`，领域层报的错误 `rejectedValue` 一律为 `null`。
- 4xx 记 WARN、不带堆栈；5xx 记 ERROR、带堆栈。参数校验失败的日志只记「参数校验失败」加字段名和原因码，`detail` 固定为「请求参数不合法」。
- 只引 `spring-security-crypto`，不引 `spring-boot-starter-security`。安全 starter 留到 PAP-64 再接。
- 版本都由 BOM 和依赖管理决定：不在 `gradle/libs.versions.toml` 里新增版本号。

**来自仓库的硬约束**

- 文档、注释、commit message 用中文；代码标识符用英文。
- 所有方法（任何访问级别）写 `///` 风格的 Markdown JavaDoc，每行不超过 100 列，否则 google-java-format 会把它折成 `//`。测试类写 `///`，测试方法用中文 `@DisplayName`，不再单独写 `///`（沿用 PAP-62 的写法）。
- 不写全类名，用 `import`。优先用 Lombok。参数不超过 4 个的 record 用静态工厂方法 `of()`，不用 `@Builder`。改已有文件时，不顺手清理文件里原有的全类名。
- 格式由 Spotless（google-java-format）决定：每个任务提交前跑一次 `./gradlew spotlessApply`，以它的输出为准。
- SpotBugs 是 `effort=MAX`、`reportLevel=LOW`、`ignoreFailures=false`，任何提示都会让 `check` 失败。本计划已经预防的写法：构造器可能抛异常的类声明为 `final`；返回集合字段时返回 `List.copyOf(...)`。出现其他提示时修代码，不要扩大 `spotbugs-exclude.xml` 的范围。
- 测试位置与命名：单元测试 `src/test`、`*Test`；集成测试 `src/integrationTest`、`*IT`；测试方法 snake_case（`should_<期望行为>`）。
- 测试里禁止：反射访问私有成员、`@SuppressWarnings("unchecked")`、为了测试给生产类加 setter。
- 重命名符号一律用 JetBrains MCP 的 `rename_refactoring`（先用 ToolSearch 加载）；只有加载后实际调用失败（IDEA 没开、MCP 无响应）才退回文本替换。
- TDD：先写失败的测试，看到它失败，再写让它通过的最少代码。
- 不写工时估算，不写「后续优化」「分阶段」。

**执行方式**

- 所有命令在工作树根目录执行：`/Users/linqibin/Projects/Products/patra/.claude/worktrees/v0.8-accounts-api`。不要 `cd` 到主检出目录，主检出里有别的会话没提交的改动。
- 集成测试用 Testcontainers 起 PostgreSQL 17 和 Redis 7.0.15，先跑 `docker info` 确认 Docker 在运行。
- **`check` 不包含集成测试**：`integrationTest` 测试套件只设了 `shouldRunAfter(test)`。验证时两个任务都写上，比如 `./gradlew :patra-api:patra-identity:patra-identity-infra:check :patra-api:patra-identity:patra-identity-infra:integrationTest`。
- 任务 12 要从 GitHub 下载 Django 的两个文件。下载前先向用户说明文件名、来源和大小，得到同意后再执行。
- 提交：每个任务末尾有提交步骤。只有用户对本次执行明确授权可以提交时才执行（以用户原话为准）；没有授权就跳过提交，把改动留在工作区，并在汇报里列出待提交的文件。任何情况下都不 push、不开 PR。
- 共享工作区：提交前用 `git status --short` 和 `git diff --cached --stat` 核对，只提交本任务的文件。
- commit message 末尾按当次执行会话的署名规则追加 `Co-Authored-By` 行。subject 用中文动词起头，不能以大写字母开头。
- spec 第 14 节列了五个「来自读源码、没有运行验证」的结论，本计划里对应的测试都标了「实测点」。任何一条的结果与预期不符：停下来，把现象报告给用户，不要自行改设计绕过去。
- 本计划里的代码对着 Spring Boot 4.0.8、Spring Framework 7.0.9、Spring Security 7.0.7 的源码和本仓库现有写法核对过类名和方法签名，但没有编译和运行过。遇到编译错误时，按报错修正用法（import 的包名、方法签名、泛型推断）即可，这类修正不需要报告。如果要改的是做法本身（换一种机制、删掉某个测试、放宽某个断言），停下来报告。

## Review Focus

spec 没有逐条写明、但使用这个服务的人最可能踩到的五种输入。每条都已经在对应任务里加了测试。

1. **同一个邮箱换了大小写或带首尾空白**：用 `Chen.Yu@Example.com ` 注册之后，用 `chen.yu@example.com` 再注册应返回 409，用任意大小写组合登录都应成功。→ 任务 18 的 `AccountFlowIT`。
2. **请求体缺字段或字段为 `null`**（`{}`、`{"email": null}`）：应返回 422 和 `REQUIRED`，不是 500。→ 任务 14、15 的处理器单元测试，任务 17 的 `AuthControllerIT`。
3. **密码首尾带空格**：注册时原样保存，登录时少了空格就是错密码，带上空格才对。→ 任务 18 的 `AccountFlowIT`。
4. **超长输入**（1 MB 的邮箱或密码）：注册在长度校验处拒绝，不做哈希；登录按普通的凭据错误处理，不出 500。→ 任务 14、15 的处理器单元测试。
5. **请求体里夹带多余字段**（`id`、`status`、`role`）：这些字段被忽略，注册出来的账号状态是正常、ID 由服务端分配。这正是调研里合表系统出过事故的批量赋值路径。→ 任务 18 的 `AccountFlowIT`。

## 与 spec 的出入

写计划时有几处 spec 没写到的细节在这里定下。任务 18 把需要的写回 spec。

1. **注册时的哈希放在事务外。** 处理器用 `TransactionOperations` 只把「存用户、存凭据」两步包进事务。如果给整个 `handle()` 加 `@Transactional`，JPA 在事务开始时就占住一个数据库连接，Argon2 最长排队 3 秒，这期间连接一直被占着；Hikari 连接池只有 10 个。spec 第 7.2 节「同一个事务里先存用户再存凭据」不变。
2. **在途登记的过期时间做成配置项** `patra.identity.login-throttle.in-flight-ttl`，默认 30 秒，和 spec 一致。做成配置是为了集成测试能用很短的时间验证过期。`LoginThrottlePolicy` 因此有四个字段。
3. **`PasswordHashingAdapter` 的编码器由构造器传入**，生产代码用工厂方法 `PasswordHashingAdapter.argon2(maxConcurrent, waitTimeout)`。测试用一个可以阻塞的假编码器验证并发上限和排队超时，不依赖真实哈希的耗时。
4. **Redis 不可用的端到端测试**把 Redis 指向一个没人监听的端口，不去停共享的 Redis 容器（同一个 JVM 里多个测试类共用它）。
5. **邮箱格式的对照用例**是在门户 `node_modules` 里实际运行 Zod 4.6.5 的 `z.email()` 得到的 28 条结果，原样写进 `EmailAddressTest`。
6. **`HasRetryAfter` 带默认方法 `getRetryAfterSeconds()`**，响应头和响应体字段都用它算，两边数值一定一致。
7. **`ValidationError` 的新字段 `code` 放在第二位**：`(field, code, rejectedValue, message)`。
8. **失败限制端口的返回方式**：`begin` 被拒时直接抛 `LoginTemporarilyLockedException`；`recordFailure` 返回 `Optional<Duration>`，有值表示账号处于锁定期（这次失败上锁，或者结算时已经是锁定期）。
9. **模块图加入 `identity` 单元之后**，`patra-infra/cd/detect-changes.test.sh` 里某些公共模块的扇出期望值可能多出 `identity`。按 `module-graph.json` 的实际扇出更新期望值即可，这是依赖关系的真实变化。

## 文件结构

### linqibin-commons（改）

| 文件 | 职责 |
|---|---|
| `linqibin-commons-core/.../commons/error/field/FieldViolation.java`（新） | 一条字段错误：字段名、原因码、文案 |
| `linqibin-commons-core/.../commons/error/field/HasFieldViolations.java`（新） | 异常实现它就能带字段错误 |
| `linqibin-commons-core/.../commons/error/retry/HasRetryAfter.java`（新） | 异常实现它就能带剩余等待时间 |
| `linqibin-commons-core/.../commons/error/problem/ErrorKeys.java`（改） | 新增 `RETRY_AFTER_SECONDS` |
| `linqibin-spring-boot-starter-web/.../web/error/model/ValidationError.java`（改） | 增加 `code` |
| `linqibin-spring-boot-starter-web/.../web/error/formatter/DefaultValidationErrorsFormatter.java`（改） | Bean Validation 错误的原因码 |
| `linqibin-spring-boot-starter-web/.../web/error/builder/ProblemDetailBuilder.java`（改） | 输出 `errors[]`、`retryAfterSeconds` |
| `linqibin-spring-boot-starter-web/.../web/error/handler/GlobalRestExceptionHandler.java`（改） | `Retry-After` 响应头、日志分级、参数校验失败不带原始值 |
| `linqibin-spring-boot-starter-core/.../core/cqrs/interceptor/LoggingCommandInterceptor.java`（改） | 领域异常记 WARN |
| `linqibin-spring-boot-starter-test/.../test/container/initializer/RedisContainerInitializer.java`（新） | Redis 测试容器 |

### patra-common-security / 安全 starter（改）

`AccountType.PORTAL("portal")` 改名为 `USER("user")`，以及引用它的测试、`TestIdentity`、PAP-62 工程设计。

### patra-identity（新）

```
patra-api/patra-identity/
├── README.md
├── patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/
│   ├── exception/      七个领域异常
│   ├── model/aggregate/User.java、UserPasswordCredential.java
│   ├── model/enums/UserStatus.java
│   ├── model/vo/EmailAddress.java、PlainPassword.java、PasswordHash.java、UserFieldViolations.java
│   ├── policy/PasswordPolicy.java、LoginThrottlePolicy.java
│   └── port/
│       ├── repository/UserRepository.java、UserPasswordCredentialRepository.java
│       ├── hashing/PasswordHashingPort.java
│       ├── password/CommonPasswordPort.java
│       └── throttle/LoginThrottlePort.java、LoginAttempt.java
├── patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/
│   ├── register/RegisterUserCommand.java、RegisterUserHandler.java、RegisterUserResult.java
│   ├── authenticate/AuthenticateUserCommand.java、AuthenticateUserHandler.java、AuthenticateUserResult.java
│   └── ban/BanUserCommand.java、BanUserHandler.java、UnbanUserCommand.java、UnbanUserHandler.java
├── patra-identity-infra/src/main/
│   ├── java/dev/linqibin/patra/identity/infra/adapter/
│   │   ├── persistence/UserRepositoryAdapter.java、UserPasswordCredentialRepositoryAdapter.java
│   │   ├── persistence/entity/UserEntity.java、UserPasswordCredentialEntity.java
│   │   ├── persistence/dao/UserDao.java、UserPasswordCredentialDao.java
│   │   ├── persistence/converter/mapper/UserJpaMapper.java、UserPasswordCredentialJpaMapper.java
│   │   ├── hashing/PasswordHashingAdapter.java
│   │   ├── password/CommonPasswordAdapter.java
│   │   └── throttle/LoginThrottleAdapter.java
│   └── resources/
│       ├── db/migration/V1__init_identity_schema.sql
│       ├── password/common-passwords.txt.gz、password/common-passwords.LICENSE
│       └── redis/login-throttle-begin.lua、redis/login-throttle-settle.lua
├── patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/
│   ├── auth/AuthController.java、auth/request/RegisterRequest.java、auth/request/LoginRequest.java
│   ├── auth/response/UserAccountResponse.java
│   └── admin/AdminUserController.java
└── patra-identity-boot/src/main/
    ├── java/dev/linqibin/patra/identity/PatraIdentityApplication.java
    ├── java/dev/linqibin/patra/identity/config/IdentityProperties.java、IdentityConfiguration.java
    └── resources/application.yml、application-dev.yml、application-container.yml
```

另外：`settings.gradle.kts`、根 `build.gradle.kts` 的 `dumpModuleGraph`、`patra-infra/cd/module-graph.json`、spec 第 14 节。

## 任务列表

| # | 任务 | 依赖 |
|---|---|---|
| 1 | 账号类型改名 `PORTAL` → `USER` | — |
| 2 | commons-core：字段错误与剩余等待时间的接口 | — |
| 3 | starter-web：输出 `errors[]`、原因码、`retryAfterSeconds` 和 `Retry-After` | 2 |
| 4 | starter-web：日志分级，参数校验失败不带原始值 | 3 |
| 5 | starter-core：命令拦截器的日志级别 | — |
| 6 | starter-test：Redis 测试容器 | — |
| 7 | identity 服务骨架与建表脚本 | 6 |
| 8 | domain：值对象与领域异常 | 2、7 |
| 9 | domain：聚合、规则与端口 | 1、8 |
| 10 | infra：持久化 | 9 |
| 11 | infra：密码哈希 | 9 |
| 12 | infra：常见密码名单 | 9 |
| 13 | infra：登录失败限制（Redis），并接进 boot 配置 | 6、9、11、12 |
| 14 | app：注册 | 9 |
| 15 | app：登录校验 | 9 |
| 16 | app：封禁与解封 | 9 |
| 17 | adapter：两个 Controller 与错误契约 | 3、14、15、16 |
| 18 | boot：端到端测试、README、spec 收尾 | 全部 |

---

### 任务 1：账号类型改名 `PORTAL` → `USER`

**Files:**

- Modify: `patra-api/patra-common/patra-common-security/src/main/java/dev/linqibin/patra/common/security/AccountType.java`
- Modify: `patra-api/patra-common/patra-common-security/src/test/java/dev/linqibin/patra/common/security/AccountTypeTest.java`
- Modify（随重命名自动更新）: `patra-common-security` 的 `CurrentUserTest`、`CurrentUserPortTest`；安全 starter 的 `TestIdentity`（testFixtures）、`TestIdentityTest`、`CurrentUserRunnerTest`、`SecurityContextCurrentUserAdapterTest`、`CurrentUserAuthenticationTest`、`GatewayHeaderAuthenticationConverterTest`、`IdentityHeadersTest`、`NonWebStartupIT`
- Modify: `docs/patra/specs/2026-10-05-security-starter-design.md`

**Interfaces:**

- Produces：`AccountType.USER`，`getCode()` 返回 `"user"`；`AccountType.fromCode("user")` 返回 `USER`。请求头 `X-Patra-Account-Type` 的值随之变成 `user`。任务 9、13、15 用 `AccountType.USER`。

- [ ] **步骤 1：把 `AccountTypeTest` 改成新名字（先让它失败）**

整个文件替换为：

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
  @DisplayName("user 对应前台用户")
  void should_return_user_when_code_is_user() {
    assertThat(AccountType.fromCode("user")).contains(AccountType.USER);
    assertThat(AccountType.USER.getCode()).isEqualTo("user");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "USER", "portal", "staff", " user"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(AccountType.fromCode(code)).isEmpty();
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :patra-api:patra-common:patra-common-security:test --tests '*AccountTypeTest'`
预期：编译失败，报 `cannot find symbol: variable USER`。

- [ ] **步骤 3：重命名枚举常量**

先用 ToolSearch 加载 `mcp__jetbrains__rename_refactoring`，把 `AccountType.PORTAL` 重命名为 `USER`（IDEA 会同步改掉所有 Java 引用，包括安全 starter 的测试、`TestIdentity` 和 `NonWebStartupIT`）。只有加载后实际调用失败时，才改用文本替换把这些文件里的 `AccountType.PORTAL` 换成 `AccountType.USER`。

然后手工改 `AccountType.java` 的字符串和注释，改完是：

```java
package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 账号类型。
///
/// 本版只有前台用户。后台账号（`STAFF`）在做后台时加入。
@Getter
@RequiredArgsConstructor
public enum AccountType {
  /// 前台用户：自己注册的外部用户，门户 Web、App、小程序共用这一类账号。
  USER("user");

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

- [ ] **步骤 4：改测试里写死的请求头值**

重命名只改符号，不改字符串。手工改这两个文件：

- `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverterTest.java`：三处 `"portal"` 都改成 `"user"`（`IdentityHeaders.ACCOUNT_TYPE` 的值、全小写头名 `x-patra-account-type` 的值）。
- `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/header/IdentityHeadersTest.java`：
  - `assertThat(headers.getFirst(IdentityHeaders.ACCOUNT_TYPE)).isEqualTo("portal");` 改为 `isEqualTo("user")`。
  - 账号类型不认识的用例 `@ValueSource(strings = {"", "PORTAL", "admin"})` 改为 `@ValueSource(strings = {"", "USER", "portal", "admin"})`，把旧值 `portal` 也列为不认识。

核对：

```bash
grep -rn 'PORTAL\|"portal"' patra-api/patra-common/patra-common-security patra-starters/patra-spring-boot-starter-security --include='*.java'
```

预期：只剩两个测试里故意列为「不认识」的值（`AccountTypeTest` 的 `"portal"` 和 `IdentityHeadersTest` 的 `"portal"`），没有别的。

- [ ] **步骤 5：运行两个模块的全部测试**

运行：`./gradlew :patra-api:patra-common:patra-common-security:test :patra-starters:patra-spring-boot-starter-security:test :patra-starters:patra-spring-boot-starter-security:integrationTest`
预期：`BUILD SUCCESSFUL`，全部通过。

- [ ] **步骤 6：改 PAP-62 工程设计里的两处**

`docs/patra/specs/2026-10-05-security-starter-design.md`：

- 第 3 节表格里的 `| 用户 ID 是雪花 Long；账号分门户用户和后台账号两类 | release spec 决策 A、D |`，「门户用户」改为「前台用户」。
- 第 5 节表格里的 `` | `AccountType` | 枚举。本版只有 `PORTAL`，字符串 `portal`。`fromCode(String)` 对不认识的值返回空结果 | ``，改为 `` | `AccountType` | 枚举。本版只有 `USER`（前台用户），字符串 `user`。`fromCode(String)` 对不认识的值返回空结果 | ``。

- [ ] **步骤 7：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-common/patra-common-security patra-starters/patra-spring-boot-starter-security docs/patra/specs/2026-10-05-security-starter-design.md
git diff --cached --stat
git commit -m "refactor(security): 账号类型 PORTAL 改名为 USER (PAP-63)"
```

---

### 任务 2：commons-core：字段错误与剩余等待时间的接口

**Files:**

- Create: `linqibin-commons/linqibin-commons-core/src/main/java/dev/linqibin/commons/error/field/FieldViolation.java`
- Create: `linqibin-commons/linqibin-commons-core/src/main/java/dev/linqibin/commons/error/field/HasFieldViolations.java`
- Create: `linqibin-commons/linqibin-commons-core/src/main/java/dev/linqibin/commons/error/retry/HasRetryAfter.java`
- Modify: `linqibin-commons/linqibin-commons-core/src/main/java/dev/linqibin/commons/error/problem/ErrorKeys.java`
- Test: `linqibin-commons/linqibin-commons-core/src/test/java/dev/linqibin/commons/error/field/FieldViolationTest.java`
- Test: `linqibin-commons/linqibin-commons-core/src/test/java/dev/linqibin/commons/error/retry/HasRetryAfterTest.java`

**Interfaces:**

- Produces：
  - `record FieldViolation(String field, String code, String message) implements Serializable`，`static FieldViolation of(String field, String code, String message)`。`field`、`code` 不能为 `null`，`message` 可以。
  - `interface HasFieldViolations { List<FieldViolation> getFieldViolations(); }`
  - `interface HasRetryAfter { Duration getRetryAfter(); default long getRetryAfterSeconds(); }`：毫秒向上取整到秒，最小为 1。
  - `ErrorKeys.RETRY_AFTER_SECONDS = "retryAfterSeconds"`。

- [ ] **步骤 1：写 `FieldViolation` 的失败测试**

新建 `FieldViolationTest.java`：

```java
package dev.linqibin.commons.error.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// FieldViolation 单元测试。
@DisplayName("FieldViolation 单元测试")
class FieldViolationTest {

  @Test
  @DisplayName("按原样保存字段名、原因码和文案")
  void should_keep_field_code_and_message() {
    FieldViolation violation = FieldViolation.of("password", "TOO_SHORT", "密码至少 8 位");

    assertThat(violation.field()).isEqualTo("password");
    assertThat(violation.code()).isEqualTo("TOO_SHORT");
    assertThat(violation.message()).isEqualTo("密码至少 8 位");
  }

  @Test
  @DisplayName("字段名和原因码不能为 null")
  void should_reject_null_field_or_code() {
    assertThatThrownBy(() -> FieldViolation.of(null, "REQUIRED", "请输入邮箱"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FieldViolation.of("email", null, "请输入邮箱"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("文案可以为 null")
  void should_allow_null_message() {
    assertThat(FieldViolation.of("email", "REQUIRED", null).message()).isNull();
  }
}
```

- [ ] **步骤 2：写 `HasRetryAfter` 的失败测试**

新建 `HasRetryAfterTest.java`：

```java
package dev.linqibin.commons.error.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// HasRetryAfter 单元测试。
@DisplayName("HasRetryAfter 单元测试")
class HasRetryAfterTest {

  @ParameterizedTest
  @CsvSource({"0, 1", "1, 1", "999, 1", "1000, 1", "1001, 2", "900000, 900", "-5, 1"})
  @DisplayName("剩余秒数向上取整，最小为 1")
  void should_round_up_to_whole_seconds_with_minimum_one(long millis, long expected) {
    HasRetryAfter retryAfter = () -> Duration.ofMillis(millis);

    assertThat(retryAfter.getRetryAfterSeconds()).isEqualTo(expected);
  }
}
```

- [ ] **步骤 3：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-commons-core:test --tests '*FieldViolationTest' --tests '*HasRetryAfterTest'`
预期：编译失败，找不到 `FieldViolation` 和 `HasRetryAfter`。

- [ ] **步骤 4：实现三个类型**

`FieldViolation.java`：

```java
package dev.linqibin.commons.error.field;

import java.io.Serializable;
import java.util.Objects;

/// 一条字段错误：哪个字段、什么原因、给人看的文案。
///
/// 原因码是机器可读的固定字符串（如 `TOO_SHORT`），前端按它选文案，不解析 `message`。
///
/// @param field 字段名，和请求体里的字段名一致
/// @param code 原因码，大写下划线
/// @param message 默认文案，可以为 `null`
public record FieldViolation(String field, String code, String message) implements Serializable {

  /// 校验必填项。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案
  public FieldViolation {
    Objects.requireNonNull(field, "field 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
  }

  /// 创建一条字段错误。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案，可以为 `null`
  /// @return 字段错误
  public static FieldViolation of(String field, String code, String message) {
    return new FieldViolation(field, code, message);
  }
}
```

`HasFieldViolations.java`：

```java
package dev.linqibin.commons.error.field;

import java.util.List;

/// 带字段错误的异常实现此接口，统一错误格式会把它们输出成 `errors[]`。
public interface HasFieldViolations {

  /// 返回这个异常携带的字段错误。
  ///
  /// @return 字段错误列表，不为 `null`
  List<FieldViolation> getFieldViolations();
}
```

`HasRetryAfter.java`：

```java
package dev.linqibin.commons.error.retry;

import java.time.Duration;

/// 需要告诉调用方「多久之后再试」的异常实现此接口。
///
/// 统一错误格式据此输出响应头 `Retry-After` 和响应体字段 `retryAfterSeconds`，两者数值相同。
public interface HasRetryAfter {

  /// 返回还要等多久。
  ///
  /// @return 剩余等待时间
  Duration getRetryAfter();

  /// 返回剩余等待的秒数：毫秒向上取整到秒，最小为 1。
  ///
  /// @return 秒数
  default long getRetryAfterSeconds() {
    long millis = Math.max(0, getRetryAfter().toMillis());
    return Math.max(1, (millis + 999) / 1000);
  }
}
```

`ErrorKeys.java`：在 `TRAITS` 常量后面加：

```java
  /// 剩余等待秒数字段键（429 时输出）
  public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";
```

- [ ] **步骤 5：运行，确认通过**

运行：`./gradlew :linqibin-commons:linqibin-commons-core:test`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：提交**

```bash
./gradlew spotlessApply
git add linqibin-commons/linqibin-commons-core
git diff --cached --stat
git commit -m "feat(commons): 新增字段错误与剩余等待时间两个异常接口 (PAP-63)"
```

---

### 任务 3：starter-web：输出 `errors[]`、原因码、`retryAfterSeconds` 和 `Retry-After`

**Files:**

- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/model/ValidationError.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/formatter/DefaultValidationErrorsFormatter.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/builder/ProblemDetailBuilder.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java`
- Test: 同目录结构下的 `DefaultValidationErrorsFormatterTest`、`ProblemDetailBuilderTest`、`GlobalRestExceptionHandlerTest`（都是已有文件，加用例）

**Interfaces:**

- Consumes：任务 2 的 `FieldViolation`、`HasFieldViolations`、`HasRetryAfter`、`ErrorKeys.RETRY_AFTER_SECONDS`。
- Produces：
  - `record ValidationError(String field, String code, Object rejectedValue, String message)`。
  - 异常实现 `HasFieldViolations` 时，`ProblemDetail` 带 `errors`：`List<ValidationError>`，每项 `rejectedValue` 为 `null`。
  - 异常实现 `HasRetryAfter` 时，`ProblemDetail` 带 `retryAfterSeconds`（`long`），`GlobalRestExceptionHandler.handleException` 的响应带 `Retry-After` 头，值相同。
  - Bean Validation 错误的 `code`：取 `ObjectError.getCode()`（Spring 给的最后一个、最通用的错误码，即约束名），驼峰转大写下划线，比如 `NotBlank` → `NOT_BLANK`、`typeMismatch` → `TYPE_MISMATCH`；没有错误码时为 `null`。

- [ ] **步骤 1：给格式化器加原因码的失败测试**

在 `DefaultValidationErrorsFormatterTest` 末尾加三个用例：

```java
  @Test
  @DisplayName("原因码取约束名，转成大写下划线")
  void should_use_constraint_name_in_constant_case_as_code() {
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError(
            "userRequest",
            "email",
            "",
            false,
            new String[] {
              "NotBlank.userRequest.email", "NotBlank.email", "NotBlank.java.lang.String", "NotBlank"
            },
            null,
            "不能为空");
    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    assertThat(result.get(0).code()).isEqualTo("NOT_BLANK");
  }

  @Test
  @DisplayName("类型转换失败的原因码是 TYPE_MISMATCH")
  void should_convert_type_mismatch_code() {
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError(
            "userRequest",
            "age",
            "abc",
            true,
            new String[] {
              "typeMismatch.userRequest.age",
              "typeMismatch.age",
              "typeMismatch.java.lang.Integer",
              "typeMismatch"
            },
            null,
            "类型不对");
    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    assertThat(result.get(0).code()).isEqualTo("TYPE_MISMATCH");
  }

  @Test
  @DisplayName("没有错误码时原因码为 null")
  void should_leave_code_null_when_error_has_no_codes() {
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("userRequest", "email", "x", false, null, null, "必须是有效的邮箱");
    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    assertThat(result.get(0).code()).isNull();
  }
```

- [ ] **步骤 2：给构建器加 `errors[]` 和 `retryAfterSeconds` 的失败测试**

在 `ProblemDetailBuilderTest` 里加 import（`dev.linqibin.commons.error.field.FieldViolation`、`dev.linqibin.commons.error.field.HasFieldViolations`、`dev.linqibin.commons.error.retry.HasRetryAfter`、`dev.linqibin.starter.web.error.model.ValidationError`、`java.time.Duration`），再在类末尾加：

```java
  @Test
  @DisplayName("异常带字段错误时输出 errors，且不回显原始值")
  void should_add_errors_when_exception_has_field_violations() {
    ErrorResolution resolution = resolution("TEST-0422", 422);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/auth/register");
    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());
    Throwable exception =
        new FieldViolationsException(
            List.of(FieldViolation.of("password", "TOO_COMMON", "密码太常见")));

    ProblemDetail result = builder.build(resolution, exception, request);

    assertThat(result.getProperties())
        .containsEntry(
            ErrorKeys.ERRORS,
            List.of(new ValidationError("password", "TOO_COMMON", null, "密码太常见")));
  }

  @Test
  @DisplayName("异常带剩余等待时间时输出 retryAfterSeconds")
  void should_add_retry_after_seconds_when_exception_has_retry_after() {
    ErrorResolution resolution = resolution("TEST-0429", 429);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/auth/login");
    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());
    Throwable exception = new RetryAfterException(Duration.ofSeconds(899).plusMillis(1));

    ProblemDetail result = builder.build(resolution, exception, request);

    assertThat(result.getProperties()).containsEntry(ErrorKeys.RETRY_AFTER_SECONDS, 900L);
  }

  @Test
  @DisplayName("普通异常不输出 errors 和 retryAfterSeconds")
  void should_not_add_extension_fields_for_plain_exception() {
    ErrorResolution resolution = resolution("TEST-0500", 500);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/x");
    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    ProblemDetail result = builder.build(resolution, new RuntimeException("x"), request);

    assertThat(result.getProperties())
        .doesNotContainKeys(ErrorKeys.ERRORS, ErrorKeys.RETRY_AFTER_SECONDS);
  }

  /// 构造一个指定错误码和状态的解析结果。
  ///
  /// @param code 错误码
  /// @param status HTTP 状态
  /// @return 解析结果
  private static ErrorResolution resolution(String code, int status) {
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn(code);
    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(status);
    return resolution;
  }

  /// 带字段错误的测试异常。
  private static final class FieldViolationsException extends RuntimeException
      implements HasFieldViolations {

    private final List<FieldViolation> violations;

    /// 创建测试异常。
    ///
    /// @param violations 字段错误
    FieldViolationsException(List<FieldViolation> violations) {
      super("字段不合法");
      this.violations = List.copyOf(violations);
    }

    /// 返回字段错误。
    ///
    /// @return 字段错误
    @Override
    public List<FieldViolation> getFieldViolations() {
      return violations;
    }
  }

  /// 带剩余等待时间的测试异常。
  private static final class RetryAfterException extends RuntimeException
      implements HasRetryAfter {

    private final Duration retryAfter;

    /// 创建测试异常。
    ///
    /// @param retryAfter 剩余等待时间
    RetryAfterException(Duration retryAfter) {
      super("请稍后再试");
      this.retryAfter = retryAfter;
    }

    /// 返回剩余等待时间。
    ///
    /// @return 剩余等待时间
    @Override
    public Duration getRetryAfter() {
      return retryAfter;
    }
  }
```

如果 `ProblemDetailBuilderTest` 里 `ErrorResolution` 等的 mock 写法和这里的 `resolution(...)` 冲突（比如已有同名方法），把新方法改个名字即可。

- [ ] **步骤 3：给处理器加 `Retry-After` 的失败测试，并把已有用例改成四个参数**

`GlobalRestExceptionHandlerTest`：

1. 已有的三处 `new ValidationError(...)` 改成四个参数，`code` 放第二位：
   - `new ValidationError("email", "invalid", "必须是有效的邮箱")` → `new ValidationError("email", "EMAIL", "invalid", "必须是有效的邮箱")`
   - `new ValidationError("field" + i, "value" + i, "message" + i)` → `new ValidationError("field" + i, "SIZE", "value" + i, "message" + i)`
   - 第三处同理，`code` 填 `"SIZE"`。
2. 加 import：`dev.linqibin.commons.error.codes.ErrorCodeLike`、`dev.linqibin.commons.error.retry.HasRetryAfter`、`java.time.Duration`、`org.springframework.http.HttpHeaders`。
3. 在类末尾加：

```java
  @Test
  @DisplayName("异常带剩余等待时间时加上 Retry-After 响应头")
  void should_add_retry_after_header_when_exception_has_retry_after() {
    Exception exception = new RetryAfterException(Duration.ofMinutes(15));
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(problemDetailAdapter.adapt(exception, request))
        .thenReturn(response(HttpStatus.TOO_MANY_REQUESTS, "TEST-0429"));

    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
  }

  @Test
  @DisplayName("普通异常不加 Retry-After 响应头")
  void should_not_add_retry_after_header_for_plain_exception() {
    Exception exception = new IllegalStateException("x");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(problemDetailAdapter.adapt(exception, request))
        .thenReturn(response(HttpStatus.CONFLICT, "TEST-0409"));

    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
  }

  /// 构造一个指定状态和错误码的适配结果。
  ///
  /// @param status HTTP 状态
  /// @param code 错误码
  /// @return 适配结果
  private static ProblemDetailResponse response(HttpStatus status, String code) {
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn(code);
    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);
    return new ProblemDetailResponse(ProblemDetail.forStatus(status), status, errorResolution);
  }

  /// 带剩余等待时间的测试异常。
  private static final class RetryAfterException extends RuntimeException
      implements HasRetryAfter {

    private final Duration retryAfter;

    /// 创建测试异常。
    ///
    /// @param retryAfter 剩余等待时间
    RetryAfterException(Duration retryAfter) {
      super("请稍后再试");
      this.retryAfter = retryAfter;
    }

    /// 返回剩余等待时间。
    ///
    /// @return 剩余等待时间
    @Override
    public Duration getRetryAfter() {
      return retryAfter;
    }
  }
```

- [ ] **步骤 4：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test`
预期：编译失败，`ValidationError` 没有四个参数的构造器、没有 `code()`。

- [ ] **步骤 5：`ValidationError` 加 `code`**

整个文件替换为：

```java
package dev.linqibin.starter.web.error.model;

/// 通过 ProblemDetail 扩展暴露的验证错误条目的不可变表示。敏感值会预先掩码，以避免泄露机密数据。
///
/// @param field 逻辑字段名
/// @param code 原因码，大写下划线（如 `NOT_BLANK`、`TOO_COMMON`）；没有时为 `null`
/// @param rejectedValue 已清理的被拒绝值；领域层报的字段错误一律为 `null`
/// @param message 人类可读的验证消息
/// @author linqibin
/// @since 0.1.0
public record ValidationError(String field, String code, Object rejectedValue, String message) {}
```

- [ ] **步骤 6：格式化器计算原因码**

`DefaultValidationErrorsFormatter.java`：

1. 加 import `java.util.regex.Pattern`。
2. 在 `SENSITIVE_PATTERNS` 后面加常量：

```java
  /// 驼峰边界：小写字母或数字后面紧跟大写字母的位置。
  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
```

3. `mapToValidationError` 改为：

```java
  /// 将 Spring 的 {@link ObjectError} 映射到 {@link ValidationError} 并掩盖敏感值。
  ///
  /// @param error Spring 验证报告的绑定错误
  /// @return 清理后的验证错误
  private ValidationError mapToValidationError(ObjectError error) {
    String code = toConstantCase(error.getCode());
    if (error instanceof FieldError fieldError) {
      String fieldName = fieldError.getField();
      Object rejectedValue = maskSensitiveValue(fieldName, fieldError.getRejectedValue());
      String message = fieldError.getDefaultMessage();

      return new ValidationError(fieldName, code, rejectedValue, message);
    } else {
      // 全局错误（非字段特定）
      return new ValidationError(error.getObjectName(), code, null, error.getDefaultMessage());
    }
  }

  /// 把 Spring 的错误码（约束名，如 `NotBlank`）转成大写下划线（`NOT_BLANK`）。
  ///
  /// @param code Spring 给的错误码，可以为 `null`
  /// @return 大写下划线形式；输入为 `null` 时返回 `null`
  static String toConstantCase(String code) {
    if (code == null) {
      return null;
    }
    return CAMEL_BOUNDARY.matcher(code).replaceAll("$1_$2").toUpperCase(Locale.ROOT);
  }
```

`ObjectError.getCode()` 返回错误码数组的最后一个，也就是最通用的那个（约束名本身）。

- [ ] **步骤 7：构建器输出 `errors[]` 和 `retryAfterSeconds`**

`ProblemDetailBuilder.java`：

1. 加 import：`dev.linqibin.commons.error.field.FieldViolation`、`dev.linqibin.commons.error.field.HasFieldViolations`、`dev.linqibin.commons.error.retry.HasRetryAfter`、`dev.linqibin.starter.web.error.model.ValidationError`。
2. `build(...)` 里，在 `setupStandardFields(problemDetail, resolution, exception, request);` 之后加两行：

```java
    addFieldViolationsIfPresent(problemDetail, exception);
    addRetryAfterIfPresent(problemDetail, exception);
```

3. 在 `addErrorTraitsIfPresent` 方法后面加：

```java
  /// 异常带字段错误时，输出 `errors[]`。领域层报的错误不回显用户填的值。
  ///
  /// @param problemDetail 目标问题详情实例
  /// @param exception 源异常
  private void addFieldViolationsIfPresent(ProblemDetail problemDetail, Throwable exception) {
    if (exception instanceof HasFieldViolations hasViolations) {
      List<ValidationError> errors =
          hasViolations.getFieldViolations().stream().map(this::toValidationError).toList();
      problemDetail.setProperty(ErrorKeys.ERRORS, errors);
    }
  }

  /// 把领域层的字段错误转成响应里的验证错误条目。
  ///
  /// @param violation 字段错误
  /// @return 验证错误条目，`rejectedValue` 为 `null`
  private ValidationError toValidationError(FieldViolation violation) {
    return new ValidationError(violation.field(), violation.code(), null, violation.message());
  }

  /// 异常带剩余等待时间时，输出 `retryAfterSeconds`。
  ///
  /// @param problemDetail 目标问题详情实例
  /// @param exception 源异常
  private void addRetryAfterIfPresent(ProblemDetail problemDetail, Throwable exception) {
    if (exception instanceof HasRetryAfter hasRetryAfter) {
      problemDetail.setProperty(
          ErrorKeys.RETRY_AFTER_SECONDS, hasRetryAfter.getRetryAfterSeconds());
    }
  }
```

- [ ] **步骤 8：处理器加 `Retry-After` 响应头**

`GlobalRestExceptionHandler.java`：

1. 加 import：`dev.linqibin.commons.error.retry.HasRetryAfter`、`org.springframework.http.HttpHeaders`。
2. `handleException` 改为：

```java
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);

    logExceptionHandled(response, ex);

    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(response.httpStatus())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (ex instanceof HasRetryAfter hasRetryAfter) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(hasRetryAfter.getRetryAfterSeconds()));
    }
    return builder.body(response.problemDetail());
  }
```

方法上原有的 `///` 注释保留，末尾补一句：「异常实现 {@link HasRetryAfter} 时加上 `Retry-After` 响应头。」

- [ ] **步骤 9：运行，确认通过**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test`
预期：`BUILD SUCCESSFUL`，新旧用例全部通过。

- [ ] **步骤 10：确认其他用到统一错误格式的模块不受影响**

运行：`./gradlew :patra-starters:patra-spring-boot-starter-security:test :patra-starters:patra-spring-boot-starter-security:integrationTest :patra-api:patra-object-storage:patra-object-storage-adapter:integrationTest`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 11：提交**

```bash
./gradlew spotlessApply
git add linqibin-commons/linqibin-spring-boot-starter-web
git diff --cached --stat
git commit -m "feat(commons): 统一错误格式输出字段原因码和剩余等待时间 (PAP-63)"
```

---

### 任务 4：starter-web：日志分级，参数校验失败不带原始值

**Files:**

- Modify: `linqibin-commons/linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandler.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-web/src/test/java/dev/linqibin/starter/web/error/handler/GlobalRestExceptionHandlerTest.java`（加用例）
- Create: `linqibin-commons/linqibin-spring-boot-starter-web/src/integrationTest/java/dev/linqibin/starter/web/WebErrorITWebMvcConfig.java`
- Create: `linqibin-commons/linqibin-spring-boot-starter-web/src/integrationTest/java/dev/linqibin/starter/web/error/probe/ValidationProbeController.java`
- Create: `linqibin-commons/linqibin-spring-boot-starter-web/src/integrationTest/java/dev/linqibin/starter/web/error/ValidationFailureLeakIT.java`
- Create: `linqibin-commons/linqibin-spring-boot-starter-web/src/integrationTest/resources/application.yml`

**Interfaces:**

- Consumes：任务 3 的 `ValidationError(field, code, rejectedValue, message)`。
- Produces：4xx 记 WARN（一行，不带堆栈）；5xx 记 ERROR（带堆栈）；参数校验失败时 `detail` 固定为 `请求参数不合法`，日志只有「参数校验失败」和 `字段名:原因码` 列表。

- [ ] **步骤 1：写日志分级和校验失败的失败测试**

`GlobalRestExceptionHandlerTest`：

1. 加 import：`ch.qos.logback.classic.Level`、`ch.qos.logback.classic.Logger`、`ch.qos.logback.classic.spi.ILoggingEvent`、`ch.qos.logback.core.read.ListAppender`、`java.lang.reflect.Method`、`org.junit.jupiter.api.AfterEach`、`org.slf4j.LoggerFactory`、`org.springframework.core.MethodParameter`。
2. 加两个字段，`setUp()` 末尾挂上日志收集器，再加一个 `@AfterEach`：

```java
  private Logger handlerLogger;
  private ListAppender<ILoggingEvent> logAppender;

  // setUp() 里，创建 handler 之后加：
    handlerLogger = (Logger) LoggerFactory.getLogger(GlobalRestExceptionHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);

  /// 摘掉日志收集器，避免影响别的测试。
  @AfterEach
  void detachLogAppender() {
    handlerLogger.detachAppender(logAppender);
  }
```

3. 在类末尾加三个用例（`response(...)` 是任务 3 加的辅助方法，`dummyMethod` 是文件里已有的占位方法）：

```java
  @Test
  @DisplayName("4xx 记 WARN，不带堆栈")
  void should_log_client_error_at_warn_without_stack_trace() {
    Exception exception = new IllegalStateException("邮箱或密码错误");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(problemDetailAdapter.adapt(exception, request))
        .thenReturn(response(HttpStatus.UNAUTHORIZED, "TEST-0401"));

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage()).contains("TEST-0401").contains("邮箱或密码错误");
            });
  }

  @Test
  @DisplayName("5xx 记 ERROR，带堆栈")
  void should_log_server_error_at_error_with_stack_trace() {
    Exception exception = new IllegalStateException("连接池耗尽");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(problemDetailAdapter.adapt(exception, request))
        .thenReturn(response(HttpStatus.INTERNAL_SERVER_ERROR, "TEST-0500"));

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getThrowableProxy()).isNotNull();
            });
  }

  @Test
  @DisplayName("参数校验失败：detail 用固定文案，日志只记字段名和原因码")
  void should_hide_rejected_values_when_validation_fails() throws Exception {
    BindingResult bindingResult = mock(BindingResult.class);
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    ServletWebRequest webRequest = new ServletWebRequest(mock(HttpServletRequest.class));
    ProblemDetailResponse response = response(HttpStatus.valueOf(422), "TEST-0422");
    response.problemDetail().setDetail("rejected value [Leaky-Secret-123]");
    when(problemDetailAdapter.adapt(eq(exception), any(HttpServletRequest.class)))
        .thenReturn(response);
    when(validationErrorsFormatter.formatWithMasking(bindingResult))
        .thenReturn(List.of(new ValidationError("password", "SIZE", "***", "长度不对")));

    ResponseEntity<Object> result =
        handler.handleMethodArgumentNotValid(
            exception, null, HttpStatus.valueOf(422), webRequest);

    ProblemDetail body = (ProblemDetail) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo("请求参数不合法");
    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage())
                  .contains("参数校验失败")
                  .contains("password:SIZE")
                  .doesNotContain("Leaky-Secret-123");
            });
  }
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test --tests '*GlobalRestExceptionHandlerTest'`
预期：三个新用例失败：4xx 现在记的是 ERROR 且带堆栈；校验失败的 `detail` 仍是原始消息、日志是 ERROR。

- [ ] **步骤 3：改处理器**

`GlobalRestExceptionHandler.java`：

1. 在 `MAX_VALIDATION_ERRORS` 后面加常量：

```java
  /// 参数校验失败时返回给客户端的固定文案。异常自身的消息里带着字段原始值，不能外泄。
  private static final String VALIDATION_FAILED_DETAIL = "请求参数不合法";
```

2. `handleMethodArgumentNotValid` 的方法体改为：

```java
    HttpServletRequest servletRequest = extractServletRequest(request);
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, servletRequest);

    List<ValidationError> errors = formatAndTruncateValidationErrors(ex);
    response.problemDetail().setDetail(VALIDATION_FAILED_DETAIL);
    response.problemDetail().setProperty(ErrorKeys.ERRORS, errors);

    logValidationExceptionHandled(response, errors);

    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
```

方法注释补一句：「`detail` 固定为「请求参数不合法」：异常消息里带着字段原始值（`rejected value [...]`），现有掩码拦不住。」

3. `logExceptionHandled` 整个替换为：

```java
  /// 记录通用异常处理。4xx 是调用方的问题，记 WARN、不带堆栈；5xx 记 ERROR、带堆栈。
  ///
  /// @param response 包含错误元数据的问题详情响应
  /// @param ex 被处理的异常
  private void logExceptionHandled(ProblemDetailResponse response, Exception ex) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    String code = response.errorResolution().errorCode().code();
    int status = response.httpStatus().value();
    if (response.httpStatus().is5xxServerError()) {
      log.error(
          "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}",
          code,
          status,
          path,
          ex.getClass().getSimpleName(),
          ex);
      return;
    }
    log.warn(
        "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}: {}",
        code,
        status,
        path,
        ex.getClass().getSimpleName(),
        ex.getMessage());
  }
```

4. `logValidationExceptionHandled` 整个替换为（去掉了 `Exception ex` 参数）：

```java
  /// 记录参数校验失败。只记字段名和原因码，不记异常消息和堆栈：异常消息里带着字段原始值。
  ///
  /// @param response 问题详情响应
  /// @param errors 响应中包含的验证错误
  private void logValidationExceptionHandled(
      ProblemDetailResponse response, List<ValidationError> errors) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    List<String> fieldCodes =
        errors.stream().map(error -> error.field() + ":" + error.code()).toList();
    log.warn(
        "参数校验失败: error code [{}], HTTP status {}, request path [{}], errors={}",
        response.errorResolution().errorCode().code(),
        response.httpStatus().value(),
        path,
        fieldCodes);
  }
```

类注释里「掩码敏感字段」那一行后面补一行：`///   - 4xx 记 WARN、不带堆栈，5xx 记 ERROR、带堆栈；参数校验失败不记字段原始值`。

- [ ] **步骤 4：运行，确认单元测试通过**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:test`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：写真实 Bean Validation 路径的集成测试**

`src/integrationTest/resources/application.yml`：

```yaml
linqibin:
  starter:
    core:
      error:
        context-prefix: TEST
```

`WebErrorITWebMvcConfig.java`：

```java
package dev.linqibin.starter.web;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/// starter-web 切片测试的配置根，导入统一错误格式的两个自动配置。
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({CoreErrorAutoConfiguration.class, WebErrorAutoConfiguration.class})
public class WebErrorITWebMvcConfig {}
```

`ValidationProbeController.java`：

```java
package dev.linqibin.starter.web.error.probe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/// 只在测试里存在的接口，用来走一遍真实的 Bean Validation 失败路径。
@RestController
public class ValidationProbeController {

  /// 校验通过时什么都不做。
  ///
  /// @param request 带校验注解的请求体
  @PostMapping("/probe/validation")
  public void probe(@Valid @RequestBody ProbeRequest request) {
    // 只用来触发校验
  }

  /// 带校验注解的请求体。
  ///
  /// @param email 不能为空
  /// @param password 最长 8 个字符
  public record ProbeRequest(@NotBlank String email, @Size(max = 8) String password) {}
}
```

`ValidationFailureLeakIT.java`：

```java
package dev.linqibin.starter.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.web.error.probe.ValidationProbeController;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 参数校验失败时，响应和日志里都不能出现字段的原始值。
@WebMvcTest(controllers = ValidationProbeController.class)
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("参数校验失败时不泄露字段原始值")
class ValidationFailureLeakIT {

  private static final String SECRET = "Leaky-Secret-Value-20261006";

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("响应和日志里都没有被拒绝的原始值")
  void should_not_leak_rejected_value_into_response_or_log(CapturedOutput output) {
    String body =
        restClient
            .post()
            .uri("/probe/validation")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "", "password", SECRET))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JsonMapper.builder().build().readTree(body);
    List<String> fieldCodes = new ArrayList<>();
    json.get("errors")
        .forEach(error -> fieldCodes.add(error.get("field").asString() + ":" + error.get("code").asString()));
    assertThat(json.get("detail").asString()).isEqualTo("请求参数不合法");
    assertThat(fieldCodes).containsExactlyInAnyOrder("email:NOT_BLANK", "password:SIZE");
    assertThat(body).doesNotContain(SECRET);
    assertThat(output.getAll()).contains("参数校验失败").doesNotContain(SECRET);
  }
}
```

Jackson 3 里取字符串值的方法叫 `asString()`；如果编译报找不到，按报错换成该版本的等价方法。

- [ ] **步骤 6：运行集成测试**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-web:integrationTest`
预期：`BUILD SUCCESSFUL`。如果把步骤 3 第 2 条的 `setDetail` 临时删掉再跑，`detail` 和响应体的两个断言会失败，说明测试确实覆盖了原来的泄露（确认后恢复）。

- [ ] **步骤 7：提交**

```bash
./gradlew spotlessApply
git add linqibin-commons/linqibin-spring-boot-starter-web
git diff --cached --stat
git commit -m "fix(commons): 4xx 日志降到 WARN，参数校验失败不再带出字段原始值 (PAP-63)"
```

---

### 任务 5：starter-core：命令拦截器的日志级别

**Files:**

- Modify: `linqibin-commons/linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/interceptor/LoggingCommandInterceptor.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-core/src/test/java/dev/linqibin/starter/core/cqrs/interceptor/LoggingCommandInterceptorTest.java`

**Interfaces:**

- Produces：命令抛 `DomainException`（业务上的拒绝）时，「命令失败」记 WARN；其他异常仍记 ERROR。异常照常抛出。

- [ ] **步骤 1：写失败的测试**

```java
package dev.linqibin.starter.core.cqrs.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/// LoggingCommandInterceptor 单元测试。
@DisplayName("LoggingCommandInterceptor 单元测试")
class LoggingCommandInterceptorTest {

  private final LoggingCommandInterceptor interceptor = new LoggingCommandInterceptor();
  private Logger interceptorLogger;
  private ListAppender<ILoggingEvent> logAppender;

  /// 挂上日志收集器。
  @BeforeEach
  void attachLogAppender() {
    interceptorLogger = (Logger) LoggerFactory.getLogger(LoggingCommandInterceptor.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    interceptorLogger.addAppender(logAppender);
  }

  /// 摘掉日志收集器。
  @AfterEach
  void detachLogAppender() {
    interceptorLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("领域异常记 WARN，异常照常抛出")
  void should_log_domain_exception_at_warn() {
    assertThatThrownBy(
            () ->
                interceptor.intercept(
                    new ProbeCommand(),
                    command -> {
                      throw new ProbeDomainException();
                    }))
        .isInstanceOf(ProbeDomainException.class);

    assertThat(failureEvent().getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  @DisplayName("其他异常仍记 ERROR")
  void should_log_unexpected_exception_at_error() {
    assertThatThrownBy(
            () ->
                interceptor.intercept(
                    new ProbeCommand(),
                    command -> {
                      throw new IllegalStateException("意外");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(failureEvent().getLevel()).isEqualTo(Level.ERROR);
  }

  /// 取出「命令失败」那条日志。
  ///
  /// @return 日志事件
  private ILoggingEvent failureEvent() {
    return logAppender.list.stream()
        .filter(event -> event.getFormattedMessage().contains("命令失败"))
        .findFirst()
        .orElseThrow();
  }

  /// 测试用命令。
  private record ProbeCommand() implements Command<Void> {}

  /// 测试用领域异常。
  private static final class ProbeDomainException extends DomainException {

    /// 创建测试异常。
    ProbeDomainException() {
      super("业务上拒绝", StandardErrorTrait.CONFLICT);
    }
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-core:test --tests '*LoggingCommandInterceptorTest'`
预期：`should_log_domain_exception_at_warn` 失败（实际是 ERROR），另一个通过。

- [ ] **步骤 3：改拦截器**

加 import `dev.linqibin.commons.error.DomainException`，`catch` 块改为：

```java
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      if (e instanceof DomainException) {
        // 业务上的拒绝（如凭据错误、邮箱已注册），不是故障
        log.warn("<<< 命令失败: {} ({}ms) - {}", cmdName, duration, e.getMessage());
      } else {
        log.error("<<< 命令失败: {} ({}ms) - {}", cmdName, duration, e.getMessage());
      }
      throw e;
    }
```

`intercept` 方法的注释补一句：「领域异常记 WARN，其他异常记 ERROR。」

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-core:test`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add linqibin-commons/linqibin-spring-boot-starter-core
git diff --cached --stat
git commit -m "fix(commons): 命令拦截器把领域异常的失败日志降到 WARN (PAP-63)"
```

---

### 任务 6：starter-test：Redis 测试容器

**Files:**

- Create: `linqibin-commons/linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/RedisContainerInitializer.java`
- Test: `linqibin-commons/linqibin-spring-boot-starter-test/src/integrationTest/java/dev/linqibin/starter/test/container/initializer/RedisContainerInitializerIT.java`

**Interfaces:**

- Consumes：`ContainerRegistry.register(ContainerType, Startable)`、`ContainerRegistry.isRegistered(ContainerType)`、`ContainerRegistry.get(ContainerType, Class<T>)`、`ContainerType.REDIS`（已有，一直没有实现）。
- Produces：`RedisContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>`。镜像 `redis:7.0.15`，同一个 JVM 里只启动一次。往环境里写 `spring.data.redis.host`、`spring.data.redis.port`、`spring.data.redis.url`（三个都写：`url` 的优先级高于 `host`/`port`，dev 配置里写的是 `url`）。静态方法 `GenericContainer<?> getRedisContainer()` 返回已启动的容器。任务 7、13、18 用它。

- [ ] **步骤 1：写失败的集成测试**

```java
package dev.linqibin.starter.test.container.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// RedisContainerInitializer 集成测试，需要本机 Docker。
@DisplayName("RedisContainerInitializer 集成测试")
class RedisContainerInitializerIT {

  @Test
  @DisplayName("启动 Redis 容器，并把连接参数写进环境")
  void should_start_redis_and_publish_connection_properties() throws IOException {
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new RedisContainerInitializer().initialize(context);

      ConfigurableEnvironment environment = context.getEnvironment();
      String host = environment.getProperty("spring.data.redis.host");
      Integer port = environment.getProperty("spring.data.redis.port", Integer.class);
      assertThat(environment.getProperty("spring.data.redis.url"))
          .isEqualTo("redis://" + host + ":" + port);
      try (Socket socket = new Socket(host, port)) {
        socket.getOutputStream().write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        assertThat(reader.readLine()).isEqualTo("+PONG");
      }
    }
  }

  @Test
  @DisplayName("多次获取拿到的是同一个容器")
  void should_reuse_the_same_container() {
    assertThat(RedisContainerInitializer.getRedisContainer())
        .isSameAs(RedisContainerInitializer.getRedisContainer());
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :linqibin-commons:linqibin-spring-boot-starter-test:integrationTest`
预期：编译失败，找不到 `RedisContainerInitializer`。

- [ ] **步骤 3：实现**

```java
package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.ContainerRegistry;
import dev.linqibin.starter.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/// Redis 测试容器初始化器。
///
/// 同一个 JVM 里只启动一个容器，注册到 {@link ContainerRegistry}，多个测试类共用。
/// 镜像版本与 mini 上的 Redis 一致。
///
/// 用法：`@ContextConfiguration(initializers = RedisContainerInitializer.class)`。
public class RedisContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(RedisContainerInitializer.class);

  private static final String REDIS_IMAGE = "redis:7.0.15";

  private static final int REDIS_PORT = 6379;

  private static final Object LOCK = new Object();

  /// 返回已启动的 Redis 容器，第一次调用时启动。
  ///
  /// @return Redis 容器
  public static GenericContainer<?> getRedisContainer() {
    synchronized (LOCK) {
      if (!ContainerRegistry.isRegistered(ContainerType.REDIS)) {
        GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);
        redis.start();
        ContainerRegistry.register(ContainerType.REDIS, redis);
        log.info("Redis 容器已启动: {}:{}", redis.getHost(), redis.getMappedPort(REDIS_PORT));
      }
      return ContainerRegistry.get(ContainerType.REDIS, GenericContainer.class);
    }
  }

  /// 把容器的连接参数写进 Spring 环境。
  ///
  /// `url` 的优先级高于 `host`/`port`，三个都写，避免被 dev 配置里的 `url` 盖掉。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    GenericContainer<?> redis = getRedisContainer();
    String host = redis.getHost();
    int port = redis.getMappedPort(REDIS_PORT);
    TestPropertyValues.of(
            "spring.data.redis.host=" + host,
            "spring.data.redis.port=" + port,
            "spring.data.redis.url=redis://" + host + ":" + port)
        .applyTo(applicationContext.getEnvironment());
  }
}
```

- [ ] **步骤 4：运行，确认通过**

运行：`docker info > /dev/null && ./gradlew :linqibin-commons:linqibin-spring-boot-starter-test:check :linqibin-commons:linqibin-spring-boot-starter-test:integrationTest`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add linqibin-commons/linqibin-spring-boot-starter-test
git diff --cached --stat
git commit -m "feat(commons): 测试 starter 新增 Redis 容器初始化器 (PAP-63)"
```

---

### 任务 7：identity 服务骨架与建表脚本

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`（根目录，`dumpModuleGraph` 任务）
- Modify: `patra-infra/cd/module-graph.json`（重新生成）
- Create: `patra-api/patra-identity/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-domain/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-app/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-infra/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-adapter/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-boot/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/PatraIdentityApplication.java`
- Create: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`、`application-dev.yml`、`application-container.yml`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`
- Test: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/PatraIdentityApplicationIT.java`
- Test: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/config/IdentityITPostgreSQLContainerInitializer.java`
- Test: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/resources/application-test.yml`

**Interfaces:**

- Consumes：任务 6 的 `RedisContainerInitializer`；任务 1 的 `patra-common-security`。
- Produces：五个模块的 Gradle 路径 `:patra-api:patra-identity:patra-identity-{domain,app,infra,adapter,boot}`；两张表和六个约束（名字见 Global Constraints）；boot 集成测试的公共写法：`@SpringBootTest` + `@ContextConfiguration(initializers = {IdentityITPostgreSQLContainerInitializer.class, RedisContainerInitializer.class})` + `@ActiveProfiles("test")`，任务 18 沿用。

- [ ] **步骤 1：接进构建**

`settings.gradle.kts`：在 `patra-object-storage` 那一组的 `mapParent(":patra-api:patra-object-storage", …)` 之后、`// ==================== patra-api / Gateway` 之前加：

```kotlin
includeAt(":patra-api:patra-identity:patra-identity-domain", "patra-api/patra-identity/patra-identity-domain")
includeAt(":patra-api:patra-identity:patra-identity-app", "patra-api/patra-identity/patra-identity-app")
includeAt(":patra-api:patra-identity:patra-identity-infra", "patra-api/patra-identity/patra-identity-infra")
includeAt(":patra-api:patra-identity:patra-identity-adapter", "patra-api/patra-identity/patra-identity-adapter")
includeAt(":patra-api:patra-identity:patra-identity-boot", "patra-api/patra-identity/patra-identity-boot")
mapParent(":patra-api:patra-identity", "patra-api/patra-identity")
```

`patra-api/patra-identity/build.gradle.kts`：

```kotlin
/**
 * Patra Identity - 账号、凭据、会话
 *
 * 聚合模块，管理六边形架构各层子模块
 */

// 聚合模块不需要任何插件和依赖
```

`patra-identity-domain/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-domain")
}

dependencies {
    // 账号类型 AccountType（纯 Java）
    api(project(":patra-api:patra-common:patra-common-security"))
}
```

`patra-identity-app/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-app")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":linqibin-commons:linqibin-commons-core"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    api("org.springframework:spring-tx")
}
```

`patra-identity-infra/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Infrastructure
 *
 * 基础设施层 - 持久化、密码哈希、常见密码名单、登录失败限制
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))

    // 登录失败限制存在 Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Argon2 只需要 crypto 模块：不带 Spring Security 的过滤器链和自动配置
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.bouncycastle:bcprov-jdk18on")

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
```

`patra-identity-adapter/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Adapter
 *
 * 适配器层 - 前台和后台的 REST 接口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-app"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
```

`patra-identity-boot/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.identity.PatraIdentityApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-identity:patra-identity-adapter"))
    implementation(project(":patra-api:patra-identity:patra-identity-infra"))

    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-observability"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-openapi"))

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-boot:compileJava`
预期：`BUILD SUCCESSFUL`（还没有源码，各模块 `NO-SOURCE`）。

- [ ] **步骤 2：写启动测试（先让它失败）**

`src/integrationTest/resources/application-test.yml`：

```yaml
# 测试里不连 Nacos
spring:
  cloud:
    discovery:
      enabled: false
    nacos:
      discovery:
        enabled: false
    service-registry:
      auto-registration:
        enabled: false
```

`config/IdentityITPostgreSQLContainerInitializer.java`：

```java
package dev.linqibin.patra.identity.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// identity 集成测试用的 PostgreSQL 容器，库名 `patra_identity`。
public class IdentityITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回库名。
  ///
  /// @return 库名
  @Override
  protected String getDatabaseName() {
    return "patra_identity";
  }
}
```

`PatraIdentityApplicationIT.java`：

```java
package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// identity 应用能启动，Flyway 建好表。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@DisplayName("identity 应用启动")
class PatraIdentityApplicationIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("启动时 Flyway 建好两张表")
  void should_create_identity_tables_on_startup() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name LIKE 'idn\\_%' "
                + "ORDER BY table_name",
            String.class);

    assertThat(tables).containsExactly("idn_user", "idn_user_password_credential");
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
预期：失败，找不到带 `@SpringBootConfiguration` 的类（还没有启动类）。

- [ ] **步骤 3：启动类和配置**

`PatraIdentityApplication.java`：

```java
package dev.linqibin.patra.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// identity 服务的启动入口：前台用户的账号、凭据，以及以后的会话。
///
/// 没有指定 profile 时默认用 `dev`。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraIdentityApplication {

  /// 启动应用。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIdentityApplication.class, args);
  }
}
```

`application.yml`：

```yaml
server:
  port: 6400
  # 解析网关注入的 X-Forwarded-*，让 OpenAPI 文档里的地址指向网关
  forward-headers-strategy: framework

spring:
  application:
    name: patra-identity
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        # 显式 false：SCA 2025.1.0.0 默认 true 会让 nacos 3.x gRPC 异步握手未完成时
        # 同步 register 的 -401 Client-not-connected 直接 abort 整个应用启动
        fail-fast: false
  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  flyway:
    enabled: true
    locations: classpath:db/migration

linqibin:
  starter:
    core:
      error:
        context-prefix: IDN

logging:
  file:
    path: ${PATRA_LOG_DIR:logs}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

`application-dev.yml`：

```yaml
spring:
  config:
    activate:
      on-profile: dev
  # dev 场景：用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
  cloud:
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
  datasource:
    url: ${IDENTITY_DB_URL:jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_identity}
    username: ${IDENTITY_DB_USERNAME:postgres}
    password: ${IDENTITY_DB_PASSWORD:123456}
  data:
    redis:
      url: ${IDENTITY_REDIS_URL:redis://${PATRA_INFRA_HOST:127.0.0.1}:16379}
      connect-timeout: 10s
      timeout: 5s

logging:
  file:
    path: ${PATRA_LOG_DIR:./logs}/patra-identity
  level:
    dev.linqibin.patra.identity: INFO
```

`application-container.yml`：

```yaml
# 容器部署：所有连接信息都从环境变量读，没有默认值。变量名由 PAP-66 定稿。
spring:
  config:
    activate:
      on-profile: container
  datasource:
    url: ${IDENTITY_DB_URL}
    username: ${IDENTITY_DB_USERNAME}
    password: ${IDENTITY_DB_PASSWORD}
  data:
    redis:
      url: ${IDENTITY_REDIS_URL}
```

- [ ] **步骤 4：建表脚本**

`patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`：

```sql
-- set_updated_at() 触发器函数（每个服务首个脚本独立定义，CREATE OR REPLACE 保证幂等）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- idn_user：前台用户
-- ============================================================
CREATE TABLE idn_user
(
    id              BIGINT          NOT NULL,
    email           VARCHAR(254)    NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    banned_at       timestamptz(6)  NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_email UNIQUE (email),
    CONSTRAINT ck_idn_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_idn_user_status CHECK (status IN ('ACTIVE', 'BANNED')),
    CONSTRAINT ck_idn_user_banned_at CHECK ((status = 'BANNED') = (banned_at IS NOT NULL))
);

COMMENT ON TABLE idn_user IS 'Front-end user account (account type USER)';
COMMENT ON COLUMN idn_user.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user.email IS 'Normalized email: trimmed and lower-cased';
COMMENT ON COLUMN idn_user.status IS 'ACTIVE or BANNED';
COMMENT ON COLUMN idn_user.banned_at IS 'Set when BANNED, null otherwise';
COMMENT ON COLUMN idn_user.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_updated_at
    BEFORE UPDATE ON idn_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_password_credential：前台用户的密码凭据
-- ============================================================
CREATE TABLE idn_user_password_credential
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_password_credential_user UNIQUE (user_id),
    CONSTRAINT fk_idn_user_password_credential_user FOREIGN KEY (user_id) REFERENCES idn_user (id)
);

COMMENT ON TABLE idn_user_password_credential IS 'Password credential of a front-end user';
COMMENT ON COLUMN idn_user_password_credential.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_password_credential.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_password_credential.password_hash IS 'Argon2id encoded hash (PHC string)';
COMMENT ON COLUMN idn_user_password_credential.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_password_credential.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_password_credential.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_password_credential.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_password_credential.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_password_credential.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_password_credential.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_password_credential_updated_at
    BEFORE UPDATE ON idn_user_password_credential
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

- [ ] **步骤 5：运行，确认启动测试通过**

运行：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
预期：`BUILD SUCCESSFUL`。如果应用因为 Nacos、可观测性 starter 或 OpenAPI starter 起不来，看报错补测试配置（只改 `application-test.yml`），不要改主配置。

- [ ] **步骤 6：模块图加 `identity` 单元**

根目录 `build.gradle.kts` 的 `dumpModuleGraph` 任务里：

1. `unitOf` 的 `when` 里，在 `"patra-api/patra-gateway-boot" -> "gateway"` 之前加一行：

```kotlin
            dir == "patra-api/patra-identity" || dir.startsWith("patra-api/patra-identity/") -> "identity"
```

2. `units` 列表改为：

```kotlin
            "units" to listOf("registry", "object-storage", "catalog", "ingest", "identity", "gateway", "foundation"),
```

运行：`./gradlew dumpModuleGraph && git diff --stat patra-infra/cd/module-graph.json`
预期：`module-graph.json` 里多出五个 `unit` 为 `identity` 的模块；boot、infra 的 `tasks` 含 `integrationTest`；被 identity 依赖的公共模块，`impacts` 里多出 `identity`。

运行：`bash patra-infra/cd/detect-changes.test.sh`
预期：全部 ✓。如果有场景因为扇出多了 `identity` 而失败，按 `module-graph.json` 的实际扇出改那条期望值（见「与 spec 的出入」第 9 条），不要改脚本逻辑。

CI 的后端矩阵直接从 `module-graph.json` 的单元生成，不用改 CI；CD 的 `services.json` 由 PAP-66 加 identity 的条目。

- [ ] **步骤 7：提交**

```bash
./gradlew spotlessApply
git add settings.gradle.kts build.gradle.kts patra-infra/cd/module-graph.json patra-infra/cd/detect-changes.test.sh patra-api/patra-identity
git diff --cached --stat
git commit -m "feat(identity): 新建 identity 服务骨架与建表脚本 (PAP-63)"
```

`detect-changes.test.sh` 没改动时，`git add` 它不会有任何效果。

---

### 任务 8：domain：值对象与领域异常

**Files:**

- Create（`patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/` 下）：
  - `model/vo/UserFieldViolations.java`、`model/vo/EmailAddress.java`、`model/vo/PlainPassword.java`、`model/vo/PasswordHash.java`
  - `exception/InvalidUserFieldsException.java`、`exception/EmailAlreadyRegisteredException.java`、`exception/InvalidCredentialsException.java`、`exception/LoginTemporarilyLockedException.java`、`exception/UserBannedException.java`、`exception/UserNotFoundException.java`、`exception/TemporarilyUnavailableException.java`
- Test（`patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/` 下）：
  - `model/vo/EmailAddressTest.java`、`model/vo/PlainPasswordTest.java`、`model/vo/PasswordHashTest.java`
  - `exception/InvalidUserFieldsExceptionTest.java`、`exception/LoginTemporarilyLockedExceptionTest.java`、`exception/DomainExceptionTraitsTest.java`

**Interfaces:**

- Consumes：任务 2 的 `FieldViolation`、`HasFieldViolations`、`HasRetryAfter`。
- Produces：
  - `final class UserFieldViolations`：常量 `EMAIL`、`PASSWORD`、`REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`TOO_SHORT`、`TOO_COMMON`、`INVALID_CHARACTER`；工厂方法 `emailRequired()`、`emailTooLong()`、`emailInvalidFormat()`、`passwordRequired()`、`passwordInvalidCharacter()`、`passwordTooShort()`、`passwordTooLong()`、`passwordTooCommon()`，都返回 `FieldViolation`。
  - `record EmailAddress(String value)`：`static Optional<FieldViolation> validate(String raw)`、`static EmailAddress of(String raw)`（不合法抛 `InvalidUserFieldsException`），`MAX_LENGTH = 254`。规范构造器只接受已规范化的合法值，用于从库里恢复。
  - `final class PlainPassword`：`static Optional<FieldViolation> validate(String raw)`（只查 `REQUIRED`、`INVALID_CHARACTER`）、`static PlainPassword of(String raw)`、`String value()`、`int length()`（码点数）、`String normalized()`（NFKC）、`static String comparisonKeyOf(String raw)`（NFKC → 小写 → `strip()`），`toString()` 返回 `***`。
  - `record PasswordHash(String value)`：`static PasswordHash of(String value)`，`toString()` 不含哈希。
  - 七个异常，全部 `final`，消息和特征见 Global Constraints 的表。`InvalidUserFieldsException(List<FieldViolation>)` 实现 `HasFieldViolations`；`LoginTemporarilyLockedException(Duration retryAfter)` 实现 `HasRetryAfter`；`TemporarilyUnavailableException` 有无参和 `(Throwable cause)` 两个构造器；其余都是无参构造器。

- [ ] **步骤 1：写 `EmailAddress` 的失败测试**

```java
package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// EmailAddress 单元测试。
@DisplayName("EmailAddress 单元测试")
class EmailAddressTest {

  /// 在门户 `node_modules` 里实际运行 Zod 4.6.5 `z.email()` 得到的结果（2026-10-06）。
  ///
  /// @return 邮箱和 Zod 是否接受
  static Stream<Arguments> zodEmailVectors() {
    return Stream.of(
        Arguments.of("chen.yu@example.com", true),
        Arguments.of(
            "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org",
            true),
        Arguments.of("2024cohort@example.org", true),
        Arguments.of("o'brien@example.ie", true),
        Arguments.of("user+tag@example.com", true),
        Arguments.of("a_b-c@sub.example.co", true),
        Arguments.of("UPPER@EXAMPLE.COM", true),
        Arguments.of("-a@example.com", true),
        Arguments.of("a+@example.com", true),
        Arguments.of("_@example.com", true),
        Arguments.of("a@xn--fiqs8s.cn", true),
        Arguments.of("a@example-.com", true),
        Arguments.of("a@b.c", false),
        Arguments.of("a@b", false),
        Arguments.of(".a@example.com", false),
        Arguments.of("a.@example.com", false),
        Arguments.of("a..b@example.com", false),
        Arguments.of("a'@example.com", false),
        Arguments.of("a@-example.com", false),
        Arguments.of("a@example.c0m", false),
        Arguments.of("a b@example.com", false),
        Arguments.of("张三@example.com", false),
        Arguments.of("a@example.com.", false),
        Arguments.of("a@@example.com", false),
        Arguments.of("\"q\"@example.com", false),
        Arguments.of("a@[127.0.0.1]", false),
        Arguments.of("a@localhost", false),
        Arguments.of("a@example..com", false));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("zodEmailVectors")
  @DisplayName("格式规则和 Zod 默认的 z.email() 一致（实测点 2）")
  void should_match_zod_default_email_rule(String raw, boolean accepted) {
    assertThat(EmailAddress.validate(raw).isEmpty()).isEqualTo(accepted);
  }

  @Test
  @DisplayName("去掉首尾空白并转小写")
  void should_strip_and_lowercase() {
    assertThat(EmailAddress.of("  Chen.Yu@Example.COM \t").value())
        .isEqualTo("chen.yu@example.com");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t\n"})
  @DisplayName("空的报 REQUIRED")
  void should_require_email(String raw) {
    assertThat(EmailAddress.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("email");
              assertThat(violation.code()).isEqualTo("REQUIRED");
            });
  }

  @Test
  @DisplayName("254 个字符可以，255 个报 TOO_LONG")
  void should_limit_length_to_254() {
    String atLimit = "a".repeat(242) + "@example.com";
    String overLimit = "a".repeat(243) + "@example.com";

    assertThat(atLimit).hasSize(254);
    assertThat(EmailAddress.validate(atLimit)).isEmpty();
    assertThat(EmailAddress.validate(overLimit))
        .hasValueSatisfying(violation -> assertThat(violation.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("1 MB 的输入在长度校验处就被拒绝")
  void should_reject_huge_input_by_length() {
    String huge = "a".repeat(1_000_000) + "@example.com";

    assertThat(EmailAddress.validate(huge))
        .hasValueSatisfying(violation -> assertThat(violation.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("格式不对报 INVALID_FORMAT")
  void should_report_invalid_format() {
    assertThat(EmailAddress.validate("not-an-email"))
        .hasValueSatisfying(
            violation -> assertThat(violation.code()).isEqualTo("INVALID_FORMAT"));
  }

  @Test
  @DisplayName("of 遇到不合法的输入抛 InvalidUserFieldsException")
  void should_throw_when_creating_from_invalid_input() {
    assertThatThrownBy(() -> EmailAddress.of("not-an-email"))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            exception ->
                assertThat(((InvalidUserFieldsException) exception).getFieldViolations())
                    .extracting("code")
                    .containsExactly("INVALID_FORMAT"));
  }

  @Test
  @DisplayName("规范构造器只接受规范化之后的值")
  void should_reject_non_normalized_value_in_canonical_constructor() {
    assertThatThrownBy(() -> new EmailAddress("Chen@Example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailAddress(" chen@example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new EmailAddress("chen@example.com").value()).isEqualTo("chen@example.com");
  }
}
```

- [ ] **步骤 2：写 `PlainPassword`、`PasswordHash` 的失败测试**

`PlainPasswordTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PlainPassword 单元测试。
@DisplayName("PlainPassword 单元测试")
class PlainPasswordTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {""})
  @DisplayName("null 和空串报 REQUIRED")
  void should_require_password(String raw) {
    assertThat(PlainPassword.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("password");
              assertThat(violation.code()).isEqualTo("REQUIRED");
            });
  }

  @Test
  @DisplayName("只有空格不算空，原样保留")
  void should_keep_whitespace_as_is() {
    assertThat(PlainPassword.validate("        ")).isEmpty();
    assertThat(PlainPassword.of(" secret ").value()).isEqualTo(" secret ");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc\uD800def", "\uDC00abcdefgh", "abcdefgh\uD83D"})
  @DisplayName("含孤立代理项报 INVALID_CHARACTER")
  void should_reject_unpaired_surrogates(String raw) {
    assertThat(PlainPassword.validate(raw))
        .hasValueSatisfying(
            violation -> assertThat(violation.code()).isEqualTo("INVALID_CHARACTER"));
  }

  @Test
  @DisplayName("成对的代理项（表情）是合法的")
  void should_accept_paired_surrogates() {
    assertThat(PlainPassword.validate("😀😀😀😀")).isEmpty();
  }

  @Test
  @DisplayName("长度按码点计")
  void should_count_code_points() {
    assertThat(PlainPassword.of("密码密码密码密码").length()).isEqualTo(8);
    assertThat(PlainPassword.of("😀".repeat(8)).length()).isEqualTo(8);
  }

  @Test
  @DisplayName("normalized 做 NFKC")
  void should_normalize_with_nfkc() {
    assertThat(PlainPassword.of("ｐａｓｓ１２３").normalized()).isEqualTo("pass123");
  }

  @Test
  @DisplayName("比较键：NFKC、小写、去掉首尾空白")
  void should_build_comparison_key() {
    assertThat(PlainPassword.comparisonKeyOf("  Ｐａｓｓｗｏｒｄ１２３ ")).isEqualTo("password123");
  }

  @Test
  @DisplayName("toString 不输出密码")
  void should_hide_password_in_to_string() {
    assertThat(PlainPassword.of("Secret-Value-1").toString())
        .isEqualTo("***")
        .doesNotContain("Secret");
  }
}
```

`PasswordHashTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordHash 单元测试。
@DisplayName("PasswordHash 单元测试")
class PasswordHashTest {

  @Test
  @DisplayName("toString 不输出哈希")
  void should_hide_hash_in_to_string() {
    PasswordHash hash = PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");

    assertThat(hash.toString()).doesNotContain("argon2id").doesNotContain("aGFzaA");
  }

  @Test
  @DisplayName("空白的哈希不合法")
  void should_reject_blank_hash() {
    assertThatThrownBy(() -> PasswordHash.of(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordHash.of(null)).isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **步骤 3：写异常的失败测试**

`InvalidUserFieldsExceptionTest.java`：

```java
package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// InvalidUserFieldsException 单元测试。
@DisplayName("InvalidUserFieldsException 单元测试")
class InvalidUserFieldsExceptionTest {

  @Test
  @DisplayName("带上全部字段错误，外部改原列表不影响异常")
  void should_carry_a_copy_of_all_violations() {
    List<FieldViolation> violations = new ArrayList<>();
    violations.add(FieldViolation.of("email", "REQUIRED", "请输入邮箱"));
    violations.add(FieldViolation.of("password", "TOO_SHORT", "密码至少 8 位"));

    InvalidUserFieldsException exception = new InvalidUserFieldsException(violations);
    violations.clear();

    assertThat(exception.getFieldViolations()).extracting("field").containsExactly("email", "password");
  }

  @Test
  @DisplayName("至少要有一条字段错误")
  void should_require_at_least_one_violation() {
    assertThatThrownBy(() -> new InvalidUserFieldsException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

`LoginTemporarilyLockedExceptionTest.java`：

```java
package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginTemporarilyLockedException 单元测试。
@DisplayName("LoginTemporarilyLockedException 单元测试")
class LoginTemporarilyLockedExceptionTest {

  @Test
  @DisplayName("带上剩余等待时间")
  void should_carry_retry_after() {
    LoginTemporarilyLockedException exception =
        new LoginTemporarilyLockedException(Duration.ofSeconds(899).plusMillis(1));

    assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(899).plusMillis(1));
    assertThat(exception.getRetryAfterSeconds()).isEqualTo(900);
  }
}
```

`DomainExceptionTraitsTest.java`：

```java
package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// identity 领域异常的文案和语义特征。文案会原样出现在响应的 `detail` 里，必须是固定的。
@DisplayName("identity 领域异常的文案和特征")
class DomainExceptionTraitsTest {

  /// 七个异常及其预期的文案和特征。
  ///
  /// @return 异常、文案、特征
  static Stream<Arguments> exceptions() {
    return Stream.of(
        Arguments.of(
            new InvalidUserFieldsException(
                List.of(FieldViolation.of("email", "REQUIRED", "请输入邮箱"))),
            "请求参数不合法",
            StandardErrorTrait.RULE_VIOLATION),
        Arguments.of(
            new EmailAlreadyRegisteredException(), "该邮箱已注册", StandardErrorTrait.CONFLICT),
        Arguments.of(
            new InvalidCredentialsException(), "邮箱或密码错误", StandardErrorTrait.UNAUTHORIZED),
        Arguments.of(
            new LoginTemporarilyLockedException(Duration.ofMinutes(15)),
            "尝试次数过多，请稍后再试",
            StandardErrorTrait.QUOTA_EXCEEDED),
        Arguments.of(new UserBannedException(), "该账号已被封禁", StandardErrorTrait.FORBIDDEN),
        Arguments.of(new UserNotFoundException(), "用户不存在", StandardErrorTrait.NOT_FOUND),
        Arguments.of(
            new TemporarilyUnavailableException(),
            "服务暂时不可用",
            StandardErrorTrait.DEP_UNAVAILABLE));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("exceptions")
  @DisplayName("文案固定，特征决定状态码")
  void should_have_fixed_message_and_trait(
      DomainException exception, String message, StandardErrorTrait trait) {
    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getErrorTraits()).containsExactly(trait);
  }
}
```

- [ ] **步骤 4：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-domain:test`
预期：编译失败，找不到 `EmailAddress` 等类。

- [ ] **步骤 5：实现字段错误工厂和三个值对象**

`UserFieldViolations.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;

/// 前台用户注册、登录时会报的字段错误。前端按原因码选文案，`message` 只是默认文案。
public final class UserFieldViolations {

  /// 邮箱字段名，和请求体一致。
  public static final String EMAIL = "email";

  /// 密码字段名，和请求体一致。
  public static final String PASSWORD = "password";

  /// 为空。
  public static final String REQUIRED = "REQUIRED";

  /// 太长。
  public static final String TOO_LONG = "TOO_LONG";

  /// 格式不对。
  public static final String INVALID_FORMAT = "INVALID_FORMAT";

  /// 太短。
  public static final String TOO_SHORT = "TOO_SHORT";

  /// 在常见密码名单里。
  public static final String TOO_COMMON = "TOO_COMMON";

  /// 含不合法的 Unicode（孤立的代理项）。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  /// 只有静态方法。
  private UserFieldViolations() {}

  /// 邮箱为空。
  ///
  /// @return 字段错误
  public static FieldViolation emailRequired() {
    return FieldViolation.of(EMAIL, REQUIRED, "请输入邮箱");
  }

  /// 邮箱太长。
  ///
  /// @return 字段错误
  public static FieldViolation emailTooLong() {
    return FieldViolation.of(EMAIL, TOO_LONG, "邮箱最长 254 个字符");
  }

  /// 邮箱格式不对。
  ///
  /// @return 字段错误
  public static FieldViolation emailInvalidFormat() {
    return FieldViolation.of(EMAIL, INVALID_FORMAT, "邮箱格式不正确");
  }

  /// 密码为空。
  ///
  /// @return 字段错误
  public static FieldViolation passwordRequired() {
    return FieldViolation.of(PASSWORD, REQUIRED, "请输入密码");
  }

  /// 密码含不合法的 Unicode。
  ///
  /// @return 字段错误
  public static FieldViolation passwordInvalidCharacter() {
    return FieldViolation.of(PASSWORD, INVALID_CHARACTER, "密码包含无效字符");
  }

  /// 密码太短。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooShort() {
    return FieldViolation.of(PASSWORD, TOO_SHORT, "密码至少 8 位");
  }

  /// 密码太长。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooLong() {
    return FieldViolation.of(PASSWORD, TOO_LONG, "密码最长 64 位");
  }

  /// 密码太常见。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooCommon() {
    return FieldViolation.of(PASSWORD, TOO_COMMON, "这个密码太常见，容易被猜到，请换一个");
  }
}
```

`EmailAddress.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 规范化之后的邮箱：去掉首尾空白、转成小写。
///
/// 格式规则和门户用的 Zod 4.6.5 默认 `z.email()` 是同一条正则，只认 ASCII。
///
/// @param value 规范化之后的邮箱
public record EmailAddress(String value) {

  /// 最长 254 个字符。
  public static final int MAX_LENGTH = 254;

  /// 抄自 `zod/v4/core/regexes.js` 的 `email`（Zod 4.6.5）。
  private static final Pattern FORMAT =
      Pattern.compile(
          "^(?:[A-Za-z0-9_'+\\-]+\\.)*[A-Za-z0-9_'+\\-]*[A-Za-z0-9_+-]"
              + "@(?:[A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$");

  /// 只接受已经规范化的合法值，用于从库里恢复。
  ///
  /// @param value 规范化之后的邮箱
  public EmailAddress {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!value.equals(normalize(value)) || validate(value).isPresent()) {
      throw new IllegalArgumentException("邮箱必须是规范化之后的合法值");
    }
  }

  /// 从用户输入创建。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的邮箱
  /// @throws InvalidUserFieldsException 输入不合法
  public static EmailAddress of(String raw) {
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new EmailAddress(normalize(raw));
  }

  /// 校验用户输入。先去掉首尾空白，再依次查空、长度、格式。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null) {
      return Optional.of(UserFieldViolations.emailRequired());
    }
    String stripped = raw.strip();
    if (stripped.isEmpty()) {
      return Optional.of(UserFieldViolations.emailRequired());
    }
    if (stripped.length() > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.emailTooLong());
    }
    if (!FORMAT.matcher(stripped).matches()) {
      return Optional.of(UserFieldViolations.emailInvalidFormat());
    }
    return Optional.empty();
  }

  /// 去掉首尾空白、转成小写。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值
  private static String normalize(String raw) {
    return raw.strip().toLowerCase(Locale.ROOT);
  }
}
```

`PlainPassword.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// 用户输入的明文密码。不去空格，`toString()` 不输出内容。
///
/// 长度按码点计，和前端 `[...password].length` 一致。哈希和校验前都做 NFKC，
/// 同一个密码在全角、半角或不同输入法下得到同样的结果。
public final class PlainPassword {

  private final String value;

  /// 只能经 {@link #of(String)} 创建。
  ///
  /// @param value 已经校验过的密码
  private PlainPassword(String value) {
    this.value = value;
  }

  /// 从用户输入创建。只查空值和不合法的 Unicode，长度和常见名单由 `PasswordPolicy` 查。
  ///
  /// @param raw 用户输入
  /// @return 明文密码
  /// @throws InvalidUserFieldsException 输入为空或含孤立的代理项
  public static PlainPassword of(String raw) {
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new PlainPassword(raw);
  }

  /// 校验用户输入：不能为空，不能含孤立的 UTF-16 代理项。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null || raw.isEmpty()) {
      return Optional.of(UserFieldViolations.passwordRequired());
    }
    // codePoints() 会把成对的代理项合成一个码点，孤立的代理项则原样给出，落在这个区间里
    if (raw.codePoints().anyMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
      return Optional.of(UserFieldViolations.passwordInvalidCharacter());
    }
    return Optional.empty();
  }

  /// 查常见密码名单用的比较键：NFKC、转小写、去掉首尾空白。名单条目也按同样的方式处理。
  ///
  /// @param raw 原始字符串
  /// @return 比较键
  public static String comparisonKeyOf(String raw) {
    Objects.requireNonNull(raw, "raw 不能为 null");
    return Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
  }

  /// 返回原始输入。
  ///
  /// @return 原始输入
  public String value() {
    return value;
  }

  /// 返回码点数。
  ///
  /// @return 长度
  public int length() {
    return value.codePointCount(0, value.length());
  }

  /// 返回 NFKC 之后的形式，哈希和校验都用它。
  ///
  /// @return NFKC 之后的字符串
  public String normalized() {
    return Normalizer.normalize(value, Normalizer.Form.NFKC);
  }

  /// 不输出密码。
  ///
  /// @return `***`
  @Override
  public String toString() {
    return "***";
  }
}
```

`PasswordHash.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import java.util.Objects;

/// 密码哈希的编码串（Argon2id 的 PHC 格式，自带参数和盐）。`toString()` 不输出内容。
///
/// @param value 编码串
public record PasswordHash(String value) {

  /// 校验非空。
  ///
  /// @param value 编码串
  public PasswordHash {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("密码哈希不能为空白");
    }
  }

  /// 创建密码哈希。
  ///
  /// @param value 编码串
  /// @return 密码哈希
  public static PasswordHash of(String value) {
    return new PasswordHash(value);
  }

  /// 不输出哈希。
  ///
  /// @return 固定文本
  @Override
  public String toString() {
    return "PasswordHash[***]";
  }
}
```

- [ ] **步骤 6：实现七个异常**

都放在 `dev.linqibin.patra.identity.domain.exception` 包下，都是 `final`。

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.field.HasFieldViolations;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.List;

/// 注册或登录的字段不合法，返回 422，`errors[]` 里列出全部字段错误。
public final class InvalidUserFieldsException extends DomainException
    implements HasFieldViolations {

  private final List<FieldViolation> fieldViolations;

  /// 创建异常。
  ///
  /// @param fieldViolations 字段错误，至少一条
  public InvalidUserFieldsException(List<FieldViolation> fieldViolations) {
    super("请求参数不合法", StandardErrorTrait.RULE_VIOLATION);
    if (fieldViolations == null || fieldViolations.isEmpty()) {
      throw new IllegalArgumentException("至少要有一条字段错误");
    }
    this.fieldViolations = List.copyOf(fieldViolations);
  }

  /// 返回字段错误。
  ///
  /// @return 字段错误的副本
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.copyOf(fieldViolations);
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 邮箱已注册，返回 409。并发注册撞上唯一约束时也抛它。
public final class EmailAlreadyRegisteredException extends DomainException {

  /// 创建异常。文案固定，不带邮箱。
  public EmailAlreadyRegisteredException() {
    super("该邮箱已注册", StandardErrorTrait.CONFLICT);
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 邮箱或密码错误，返回 401。邮箱不存在和密码错误都抛它，从响应上区分不出来。
public final class InvalidCredentialsException extends DomainException {

  /// 创建异常。
  public InvalidCredentialsException() {
    super("邮箱或密码错误", StandardErrorTrait.UNAUTHORIZED);
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.retry.HasRetryAfter;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;

/// 登录被暂时限制，返回 429，带上还要等多久。
public final class LoginTemporarilyLockedException extends DomainException
    implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param retryAfter 剩余等待时间
  public LoginTemporarilyLockedException(Duration retryAfter) {
    super("尝试次数过多，请稍后再试", StandardErrorTrait.QUOTA_EXCEEDED);
    this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter 不能为 null");
  }

  /// 返回剩余等待时间。
  ///
  /// @return 剩余等待时间
  @Override
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 账号已被封禁，返回 403。只在邮箱和密码都对时抛出。
public final class UserBannedException extends DomainException {

  /// 创建异常。
  public UserBannedException() {
    super("该账号已被封禁", StandardErrorTrait.FORBIDDEN);
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 用户不存在，返回 404。只用于后台接口。
public final class UserNotFoundException extends DomainException {

  /// 创建异常。
  public UserNotFoundException() {
    super("用户不存在", StandardErrorTrait.NOT_FOUND);
  }
}
```

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 依赖暂时不可用（Redis 连不上、密码哈希排队超时），返回 503。
public final class TemporarilyUnavailableException extends DomainException {

  /// 创建异常。
  public TemporarilyUnavailableException() {
    super("服务暂时不可用", StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public TemporarilyUnavailableException(Throwable cause) {
    super("服务暂时不可用", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
```

- [ ] **步骤 7：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-domain:check`
预期：`BUILD SUCCESSFUL`，包括 `enforceDomainPurity` 和 SpotBugs。如果 SpotBugs 对 `InvalidUserFieldsException.fieldViolations` 报 `SE_BAD_FIELD`，把该字段声明为 `transient`，不要扩大排除清单。

- [ ] **步骤 8：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-domain
git diff --cached --stat
git commit -m "feat(identity): 新增邮箱、密码值对象和领域异常 (PAP-63)"
```

---

### 任务 9：domain：聚合、规则与端口

**Files:**

- Create（`patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/` 下）：
  - `model/enums/UserStatus.java`、`model/aggregate/User.java`、`model/aggregate/UserPasswordCredential.java`
  - `policy/PasswordPolicy.java`、`policy/LoginThrottlePolicy.java`
  - `port/repository/UserRepository.java`、`port/repository/UserPasswordCredentialRepository.java`
  - `port/hashing/PasswordHashingPort.java`、`port/password/CommonPasswordPort.java`
  - `port/throttle/LoginThrottlePort.java`、`port/throttle/LoginAttempt.java`
- Test（`patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/` 下）：
  - `model/aggregate/UserTest.java`、`model/aggregate/UserPasswordCredentialTest.java`
  - `policy/PasswordPolicyTest.java`、`policy/LoginThrottlePolicyTest.java`

**Interfaces:**

- Consumes：任务 8 的值对象和异常；任务 1 的 `AccountType`。
- Produces：
  - `enum UserStatus { ACTIVE, BANNED }`
  - `final class User`（Lombok `@Getter`）：字段 `Long id`、`EmailAddress email`、`UserStatus status`、`Instant bannedAt`、`Long version`、`Instant createdAt`、`Instant updatedAt`；`static User register(EmailAddress email)`；`static User restore(Long id, EmailAddress email, UserStatus status, Instant bannedAt, Long version, Instant createdAt, Instant updatedAt)`；`void ban(Instant now)`；`void unban()`；`boolean isBanned()`。
  - `final class UserPasswordCredential`（`@Getter`）：字段 `Long id`、`Long userId`、`PasswordHash passwordHash`、`Long version`；`static create(long userId, PasswordHash passwordHash)`；`static restore(Long id, Long userId, PasswordHash passwordHash, Long version)`。
  - `final class PasswordPolicy`：常量 `MIN_LENGTH = 8`、`MAX_LENGTH = 64`；构造器 `PasswordPolicy(CommonPasswordPort)`；`Optional<FieldViolation> validateForRegistration(String raw)`。
  - `record LoginThrottlePolicy(int maxFailures, Duration window, Duration lockDuration, Duration inFlightTtl)`，`static of(...)`。
  - `interface UserRepository`：`Optional<User> findById(long id)`、`Optional<User> findByEmail(EmailAddress email)`、`boolean existsByEmail(EmailAddress email)`、`User save(User user)`（撞上邮箱唯一约束时抛 `EmailAlreadyRegisteredException`）。
  - `interface UserPasswordCredentialRepository`：`Optional<UserPasswordCredential> findByUserId(long userId)`、`UserPasswordCredential save(UserPasswordCredential credential)`。
  - `interface PasswordHashingPort`：`PasswordHash hash(PlainPassword password)`、`boolean matches(PlainPassword password, PasswordHash hash)`、`void verifyAgainstDummy(PlainPassword password)`。都可能抛 `TemporarilyUnavailableException`。
  - `@FunctionalInterface interface CommonPasswordPort { boolean isCommon(String comparisonKey); }`
  - `interface LoginThrottlePort`：`LoginAttempt begin(AccountType accountType, EmailAddress email)`（锁定或在途已满时抛 `LoginTemporarilyLockedException`）、`void recordSuccess(LoginAttempt attempt)`、`Optional<Duration> recordFailure(LoginAttempt attempt)`（处于锁定期时返回剩余时间）、`void cancel(LoginAttempt attempt)`。Redis 不可用时都抛 `TemporarilyUnavailableException`。
  - `record LoginAttempt(AccountType accountType, EmailAddress email, String ticketId)`，`static of(...)`。

- [ ] **步骤 1：写聚合的失败测试**

`UserTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// User 单元测试。
@DisplayName("User 单元测试")
class UserTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Instant FIRST_BAN = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant SECOND_BAN = Instant.parse("2026-10-06T09:00:00Z");

  @Test
  @DisplayName("注册出来的用户是正常状态，还没有 ID")
  void should_register_active_user_without_id() {
    User user = User.register(EMAIL);

    assertThat(user.getId()).isNull();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getBannedAt()).isNull();
    assertThat(user.isBanned()).isFalse();
  }

  @Test
  @DisplayName("封禁后状态是封禁，记下封禁时间")
  void should_ban_user() {
    User user = User.register(EMAIL);

    user.ban(FIRST_BAN);

    assertThat(user.isBanned()).isTrue();
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
  }

  @Test
  @DisplayName("重复封禁不改原来的封禁时间")
  void should_keep_first_ban_time_when_banned_twice() {
    User user = User.register(EMAIL);
    user.ban(FIRST_BAN);

    user.ban(SECOND_BAN);

    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
  }

  @Test
  @DisplayName("解封后回到正常，清掉封禁时间")
  void should_unban_user() {
    User user = User.register(EMAIL);
    user.ban(FIRST_BAN);

    user.unban();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getBannedAt()).isNull();
  }

  @Test
  @DisplayName("对正常用户解封什么都不变")
  void should_do_nothing_when_unbanning_active_user() {
    User user = User.register(EMAIL);

    user.unban();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("从库里恢复时保留全部字段")
  void should_restore_all_fields() {
    Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
    Instant updatedAt = Instant.parse("2026-10-02T00:00:00Z");

    User user =
        User.restore(42L, EMAIL, UserStatus.BANNED, FIRST_BAN, 3L, createdAt, updatedAt);

    assertThat(user.getId()).isEqualTo(42L);
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
    assertThat(user.getVersion()).isEqualTo(3L);
    assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
  }
}
```

`UserPasswordCredentialTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserPasswordCredential 单元测试。
@DisplayName("UserPasswordCredential 单元测试")
class UserPasswordCredentialTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");

  @Test
  @DisplayName("新建的凭据还没有 ID，属于指定用户")
  void should_create_credential_for_user() {
    UserPasswordCredential credential = UserPasswordCredential.create(42L, HASH);

    assertThat(credential.getId()).isNull();
    assertThat(credential.getUserId()).isEqualTo(42L);
    assertThat(credential.getPasswordHash()).isEqualTo(HASH);
  }

  @Test
  @DisplayName("toString 不输出哈希")
  void should_hide_hash_in_to_string() {
    UserPasswordCredential credential = UserPasswordCredential.restore(7L, 42L, HASH, 0L);

    assertThat(credential.toString()).doesNotContain("argon2id").contains("42");
  }
}
```

- [ ] **步骤 2：写规则的失败测试**

`PasswordPolicyTest.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PasswordPolicy 单元测试。
@DisplayName("PasswordPolicy 单元测试")
class PasswordPolicyTest {

  /// 名单里只放几条，条目已经是比较键的形式。
  private final PasswordPolicy policy =
      new PasswordPolicy(Set.of("123456", "password123", "12345678")::contains);

  @Test
  @DisplayName("7 个码点太短，8 个可以（汉字）")
  void should_check_min_length_in_code_points_with_cjk() {
    assertThat(policy.validateForRegistration("密码密码密码密"))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SHORT"));
    assertThat(policy.validateForRegistration("密码密码密码密码")).isEmpty();
  }

  @Test
  @DisplayName("64 个码点可以，65 个太长（表情，每个占两个 UTF-16 单元）")
  void should_check_max_length_in_code_points_with_emoji() {
    assertThat(policy.validateForRegistration("😀".repeat(64))).isEmpty();
    assertThat(policy.validateForRegistration("😀".repeat(65)))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"password123", "Password123", "PASSWORD123", "ｐａｓｓｗｏｒｄ１２３", " password123 "})
  @DisplayName("常见名单不分大小写、全半角和首尾空白")
  void should_reject_common_password_variants(String raw) {
    assertThat(policy.validateForRegistration(raw))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("原始长度够 8 位、规范化后变短的输入也能命中名单")
  void should_hit_list_when_normalized_key_is_shorter_than_min_length() {
    assertThat(policy.validateForRegistration(" 123456 "))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("空值和孤立代理项沿用 PlainPassword 的规则")
  void should_delegate_basic_checks_to_plain_password() {
    assertThat(policy.validateForRegistration(null))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("REQUIRED"));
    assertThat(policy.validateForRegistration("abcdefgh\uD800"))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("INVALID_CHARACTER"));
  }

  @Test
  @DisplayName("1 MB 的密码在长度校验处拒绝，不去查名单")
  void should_reject_huge_password_before_checking_list() {
    PasswordPolicy strict =
        new PasswordPolicy(
            key -> {
              throw new AssertionError("超长输入不该查名单");
            });

    assertThat(strict.validateForRegistration("a".repeat(1_000_000)))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("不在名单里、长度合适的密码通过")
  void should_accept_good_password() {
    assertThat(policy.validateForRegistration("correct horse battery")).isEmpty();
  }
}
```

`LoginThrottlePolicyTest.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginThrottlePolicy 单元测试。
@DisplayName("LoginThrottlePolicy 单元测试")
class LoginThrottlePolicyTest {

  private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

  @Test
  @DisplayName("保存四个参数")
  void should_keep_parameters() {
    LoginThrottlePolicy policy =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofSeconds(30));

    assertThat(policy.maxFailures()).isEqualTo(5);
    assertThat(policy.inFlightTtl()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("上限至少为 1，时长必须为正")
  void should_reject_invalid_parameters() {
    assertThatThrownBy(
            () -> LoginThrottlePolicy.of(0, FIFTEEN_MINUTES, FIFTEEN_MINUTES, FIFTEEN_MINUTES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> LoginThrottlePolicy.of(5, Duration.ZERO, FIFTEEN_MINUTES, FIFTEEN_MINUTES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, null, FIFTEEN_MINUTES))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **步骤 3：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-domain:test`
预期：编译失败，找不到 `User`、`PasswordPolicy` 等。

- [ ] **步骤 4：实现聚合**

`UserStatus.java`：

```java
package dev.linqibin.patra.identity.domain.model.enums;

/// 前台用户的状态。
public enum UserStatus {
  /// 正常。
  ACTIVE,
  /// 已封禁：邮箱和密码都对时登录返回 403。
  BANNED
}
```

`User.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 前台用户聚合根：账号本身和封禁。密码在 {@link UserPasswordCredential} 里，不跟着用户走。
@Getter
public final class User {

  private Long id;
  private EmailAddress email;
  private UserStatus status;
  private Instant bannedAt;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private User() {}

  /// 注册一个新用户，状态为正常。ID 由仓储在保存时分配。
  ///
  /// @param email 规范化之后的邮箱
  /// @return 新用户
  public static User register(EmailAddress email) {
    User user = new User();
    user.email = Objects.requireNonNull(email, "email 不能为 null");
    user.status = UserStatus.ACTIVE;
    return user;
  }

  /// 从库里恢复。
  ///
  /// @param id 用户 ID
  /// @param email 邮箱
  /// @param status 状态
  /// @param bannedAt 封禁时间，未封禁时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  /// @return 用户
  public static User restore(
      Long id,
      EmailAddress email,
      UserStatus status,
      Instant bannedAt,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    User user = new User();
    user.id = Objects.requireNonNull(id, "id 不能为 null");
    user.email = Objects.requireNonNull(email, "email 不能为 null");
    user.status = Objects.requireNonNull(status, "status 不能为 null");
    user.bannedAt = bannedAt;
    user.version = version;
    user.createdAt = createdAt;
    user.updatedAt = updatedAt;
    return user;
  }

  /// 封禁。已经封禁时什么都不做，保留原来的封禁时间。
  ///
  /// @param now 当前时间
  public void ban(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    if (status == UserStatus.BANNED) {
      return;
    }
    status = UserStatus.BANNED;
    bannedAt = now;
  }

  /// 解封。已经正常时什么都不做。
  public void unban() {
    if (status == UserStatus.ACTIVE) {
      return;
    }
    status = UserStatus.ACTIVE;
    bannedAt = null;
  }

  /// 是否已封禁。
  ///
  /// @return 封禁时为 `true`
  public boolean isBanned() {
    return status == UserStatus.BANNED;
  }

  /// 只输出 ID 和状态，不输出邮箱。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "User[id=" + id + ", status=" + status + "]";
  }
}
```

`UserPasswordCredential.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import java.util.Objects;
import lombok.Getter;

/// 前台用户的密码凭据聚合根。只在注册和登录两条路径上加载。
@Getter
public final class UserPasswordCredential {

  private Long id;
  private Long userId;
  private PasswordHash passwordHash;
  private Long version;

  /// 只能经工厂方法创建。
  private UserPasswordCredential() {}

  /// 给用户新建一份密码凭据。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @param passwordHash 密码哈希
  /// @return 凭据
  public static UserPasswordCredential create(long userId, PasswordHash passwordHash) {
    UserPasswordCredential credential = new UserPasswordCredential();
    credential.userId = userId;
    credential.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash 不能为 null");
    return credential;
  }

  /// 从库里恢复。
  ///
  /// @param id 凭据 ID
  /// @param userId 用户 ID
  /// @param passwordHash 密码哈希
  /// @param version 乐观锁版本
  /// @return 凭据
  public static UserPasswordCredential restore(
      Long id, Long userId, PasswordHash passwordHash, Long version) {
    UserPasswordCredential credential = new UserPasswordCredential();
    credential.id = Objects.requireNonNull(id, "id 不能为 null");
    credential.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    credential.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash 不能为 null");
    credential.version = version;
    return credential;
  }

  /// 不输出哈希。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserPasswordCredential[id=" + id + ", userId=" + userId + "]";
  }
}
```

- [ ] **步骤 5：实现规则**

`PasswordPolicy.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.model.vo.UserFieldViolations;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.util.Objects;
import java.util.Optional;

/// 注册时的密码规则：不为空、Unicode 合法、8 到 64 个码点、不在常见密码名单里。
///
/// 登录不用它：登录只要求非空，密码规则以后调整时旧密码不该被拦住。
public final class PasswordPolicy {

  /// 最短 8 个码点。
  public static final int MIN_LENGTH = 8;

  /// 最长 64 个码点。
  public static final int MAX_LENGTH = 64;

  private final CommonPasswordPort commonPasswords;

  /// 创建规则。
  ///
  /// @param commonPasswords 常见密码名单
  public PasswordPolicy(CommonPasswordPort commonPasswords) {
    this.commonPasswords = Objects.requireNonNull(commonPasswords, "commonPasswords 不能为 null");
  }

  /// 按注册规则校验。长度按原始输入算，名单按比较键查；超长的输入不做规范化、不查名单。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validateForRegistration(String raw) {
    Optional<FieldViolation> basic = PlainPassword.validate(raw);
    if (basic.isPresent()) {
      return basic;
    }
    int length = raw.codePointCount(0, raw.length());
    if (length < MIN_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooShort());
    }
    if (length > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooLong());
    }
    if (commonPasswords.isCommon(PlainPassword.comparisonKeyOf(raw))) {
      return Optional.of(UserFieldViolations.passwordTooCommon());
    }
    return Optional.empty();
  }
}
```

`LoginThrottlePolicy.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import java.time.Duration;
import java.util.Objects;

/// 登录失败限制的参数。
///
/// @param maxFailures 计数窗口内允许的失败次数，达到就上锁
/// @param window 计数窗口，从窗口内第一次失败算起
/// @param lockDuration 锁定时长
/// @param inFlightTtl 在途登记的过期时间，进程崩溃没来得及结算时用它兜底
public record LoginThrottlePolicy(
    int maxFailures, Duration window, Duration lockDuration, Duration inFlightTtl) {

  /// 校验参数。
  ///
  /// @param maxFailures 失败次数上限
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  public LoginThrottlePolicy {
    if (maxFailures < 1) {
      throw new IllegalArgumentException("maxFailures 至少为 1");
    }
    requirePositive(window, "window");
    requirePositive(lockDuration, "lockDuration");
    requirePositive(inFlightTtl, "inFlightTtl");
  }

  /// 创建参数。
  ///
  /// @param maxFailures 失败次数上限
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  /// @return 参数
  public static LoginThrottlePolicy of(
      int maxFailures, Duration window, Duration lockDuration, Duration inFlightTtl) {
    return new LoginThrottlePolicy(maxFailures, window, lockDuration, inFlightTtl);
  }

  /// 校验时长为正。
  ///
  /// @param duration 时长
  /// @param name 参数名
  private static void requirePositive(Duration duration, String name) {
    Objects.requireNonNull(duration, name + " 不能为 null");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " 必须为正");
    }
  }
}
```

- [ ] **步骤 6：实现端口**

`port/repository/UserRepository.java`：

```java
package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.Optional;

/// 前台用户仓储。
public interface UserRepository {

  /// 按 ID 查用户。
  ///
  /// @param id 用户 ID
  /// @return 用户；不存在时为空
  Optional<User> findById(long id);

  /// 按规范化之后的邮箱查用户。
  ///
  /// @param email 邮箱
  /// @return 用户；不存在时为空
  Optional<User> findByEmail(EmailAddress email);

  /// 邮箱是否已注册。
  ///
  /// @param email 邮箱
  /// @return 已注册时为 `true`
  boolean existsByEmail(EmailAddress email);

  /// 保存用户。新用户在这里分配雪花 ID。
  ///
  /// @param user 用户
  /// @return 保存后的用户，带 ID 和版本
  /// @throws EmailAlreadyRegisteredException 撞上邮箱唯一约束（并发注册同一个邮箱）
  User save(User user);
}
```

`port/repository/UserPasswordCredentialRepository.java`：

```java
package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import java.util.Optional;

/// 前台用户密码凭据的仓储。
public interface UserPasswordCredentialRepository {

  /// 按用户 ID 查凭据。
  ///
  /// @param userId 用户 ID
  /// @return 凭据；不存在时为空
  Optional<UserPasswordCredential> findByUserId(long userId);

  /// 保存凭据。新凭据在这里分配雪花 ID。
  ///
  /// @param credential 凭据
  /// @return 保存后的凭据
  UserPasswordCredential save(UserPasswordCredential credential);
}
```

`port/hashing/PasswordHashingPort.java`：

```java
package dev.linqibin.patra.identity.domain.port.hashing;

import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;

/// 密码哈希。同时进行的计算有上限，排队超时抛 {@link TemporarilyUnavailableException}。
public interface PasswordHashingPort {

  /// 计算哈希。
  ///
  /// @param password 明文密码
  /// @return 哈希
  PasswordHash hash(PlainPassword password);

  /// 校验密码。
  ///
  /// @param password 明文密码
  /// @param hash 存储的哈希
  /// @return 匹配时为 `true`
  boolean matches(PlainPassword password, PasswordHash hash);

  /// 拿一个固定的假哈希做一次校验，结果丢弃。用户不存在时调用，让耗时和「密码错」一样。
  ///
  /// @param password 明文密码
  void verifyAgainstDummy(PlainPassword password);
}
```

`port/password/CommonPasswordPort.java`：

```java
package dev.linqibin.patra.identity.domain.port.password;

/// 常见密码名单。
@FunctionalInterface
public interface CommonPasswordPort {

  /// 比较键是否在名单里。比较键的算法见 `PlainPassword.comparisonKeyOf`。
  ///
  /// @param comparisonKey 比较键
  /// @return 在名单里时为 `true`
  boolean isCommon(String comparisonKey);
}
```

`port/throttle/LoginAttempt.java`：

```java
package dev.linqibin.patra.identity.domain.port.throttle;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.Objects;

/// 一次被放行的登录尝试。结算时凭它找到自己的在途登记。
///
/// @param accountType 账号类型
/// @param email 邮箱
/// @param ticketId 在途登记的随机 ID
public record LoginAttempt(AccountType accountType, EmailAddress email, String ticketId) {

  /// 校验非空。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @param ticketId 在途登记的随机 ID
  public LoginAttempt {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(ticketId, "ticketId 不能为 null");
  }

  /// 创建登录尝试。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @param ticketId 在途登记的随机 ID
  /// @return 登录尝试
  public static LoginAttempt of(AccountType accountType, EmailAddress email, String ticketId) {
    return new LoginAttempt(accountType, email, ticketId);
  }
}
```

`port/throttle/LoginThrottlePort.java`：

```java
package dev.linqibin.patra.identity.domain.port.throttle;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.time.Duration;
import java.util.Optional;

/// 登录失败限制。只有确认的失败才计数、才上锁；正在校验的尝试单独登记，只用来限制并发。
///
/// 每次尝试先 {@link #begin}，校验之后必定结算一次：成功、失败或取消。
/// Redis 不可用时，四个方法都抛 {@link TemporarilyUnavailableException}。
public interface LoginThrottlePort {

  /// 开始一次尝试，在校验密码之前调用。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 被放行的尝试
  /// @throws LoginTemporarilyLockedException 处于锁定期，或「失败次数 + 在途次数」已到上限
  LoginAttempt begin(AccountType accountType, EmailAddress email);

  /// 按成功结算：删掉在途登记，清零失败计数，不动锁。
  ///
  /// @param attempt 尝试
  void recordSuccess(LoginAttempt attempt);

  /// 按失败结算：删掉在途登记，处于锁定期就不计数，否则失败计数加一，达到上限就上锁。
  ///
  /// @param attempt 尝试
  /// @return 处于锁定期时（包括这次刚上锁）返回剩余时间，否则为空
  Optional<Duration> recordFailure(LoginAttempt attempt);

  /// 按取消结算：只删掉在途登记，不计失败。用于中途出错（哈希排队超时、数据库异常等）。
  ///
  /// @param attempt 尝试
  void cancel(LoginAttempt attempt);
}
```

- [ ] **步骤 7：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-domain:check`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-domain
git diff --cached --stat
git commit -m "feat(identity): 新增用户与密码凭据聚合、密码规则和端口 (PAP-63)"
```

---

### 任务 10：infra：持久化

**Files:**

- Create（`patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/` 下）：
  - `entity/UserEntity.java`、`entity/UserPasswordCredentialEntity.java`
  - `dao/UserDao.java`、`dao/UserPasswordCredentialDao.java`
  - `converter/mapper/UserJpaMapper.java`、`converter/mapper/UserPasswordCredentialJpaMapper.java`
  - `UserRepositoryAdapter.java`、`UserPasswordCredentialRepositoryAdapter.java`
- Test（`patra-identity-infra/src/integrationTest/java/dev/linqibin/patra/identity/infra/` 下）：
  - `IdentityITBootstrap.java`、`config/IdentityITPostgreSQLContainerInitializer.java`
  - `adapter/persistence/UserRepositoryAdapterIT.java`、`adapter/persistence/UserPasswordCredentialRepositoryAdapterIT.java`、`adapter/persistence/IdentitySchemaConstraintsIT.java`

**Interfaces:**

- Consumes：任务 9 的 `User`、`UserPasswordCredential`、`UserRepository`、`UserPasswordCredentialRepository`；任务 8 的 `EmailAlreadyRegisteredException`；任务 7 的建表脚本。
- Produces：`UserRepositoryAdapter implements UserRepository`、`UserPasswordCredentialRepositoryAdapter implements UserPasswordCredentialRepository`，都是 `@Repository`。保存用 `saveAndFlush`，让唯一约束和乐观锁的冲突在适配器里就抛出来；邮箱唯一约束（`uk_idn_user_email`）转成 `EmailAlreadyRegisteredException`，其他数据完整性异常原样抛出（由 starter-jpa 映射成 409）。

- [ ] **步骤 1：写集成测试（先让它失败）**

`IdentityITBootstrap.java`：

```java
package dev.linqibin.patra.identity.infra;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// infra 集成测试的启动类，只在测试里存在。
@SpringBootApplication
class IdentityITBootstrap {}
```

`config/IdentityITPostgreSQLContainerInitializer.java`：

```java
package dev.linqibin.patra.identity.infra.config;

import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;

/// infra 集成测试用的 PostgreSQL 容器，库名 `patra_identity`。
public class IdentityITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {

  /// 返回库名。
  ///
  /// @return 库名
  @Override
  protected String getDatabaseName() {
    return "patra_identity";
  }
}
```

`UserRepositoryAdapterIT.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({UserRepositoryAdapter.class, JpaAuditingConfig.class, HibernatePropertiesCustomizer.class})
@ComponentScan(basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserRepositoryAdapter 集成测试")
class UserRepositoryAdapterIT {

  @Autowired private UserRepositoryAdapter repository;

  @Test
  @DisplayName("保存新用户时分配雪花 ID，能按邮箱和 ID 查回")
  void should_assign_id_and_find_saved_user() {
    EmailAddress email = EmailAddress.of("save.find@example.com");

    User saved = repository.save(User.register(email));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getVersion()).isNotNull();
    assertThat(repository.findByEmail(email)).get().extracting(User::getId).isEqualTo(saved.getId());
    assertThat(repository.findById(saved.getId())).isPresent();
    assertThat(repository.existsByEmail(email)).isTrue();
    assertThat(repository.existsByEmail(EmailAddress.of("nobody@example.com"))).isFalse();
  }

  @Test
  @DisplayName("邮箱撞上唯一约束时抛 EmailAlreadyRegisteredException")
  void should_translate_email_unique_violation() {
    EmailAddress email = EmailAddress.of("duplicate@example.com");
    repository.save(User.register(email));

    assertThatThrownBy(() -> repository.save(User.register(email)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  @DisplayName("封禁后保存，查回来是封禁状态")
  void should_persist_ban() {
    User saved = repository.save(User.register(EmailAddress.of("ban.me@example.com")));
    Instant now = Instant.parse("2026-10-06T08:00:00Z");

    saved.ban(now);
    repository.save(saved);

    User reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(reloaded.getBannedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("用过期的版本保存时报乐观锁冲突")
  void should_reject_stale_version() {
    User saved = repository.save(User.register(EmailAddress.of("stale@example.com")));
    User stale = repository.findById(saved.getId()).orElseThrow();
    saved.ban(Instant.parse("2026-10-06T08:00:00Z"));
    repository.save(saved);

    stale.ban(Instant.parse("2026-10-06T09:00:00Z"));

    assertThatThrownBy(() -> repository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }
}
```

`UserPasswordCredentialRepositoryAdapterIT.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserPasswordCredentialRepositoryAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  UserRepositoryAdapter.class,
  UserPasswordCredentialRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserPasswordCredentialRepositoryAdapter 集成测试")
class UserPasswordCredentialRepositoryAdapterIT {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHQ$aGFzaGhhc2g");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserPasswordCredentialRepositoryAdapter credentials;

  @Test
  @DisplayName("保存后按用户 ID 查回哈希")
  void should_save_and_find_by_user_id() {
    User user = users.save(User.register(EmailAddress.of("credential@example.com")));

    UserPasswordCredential saved = credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThat(saved.getId()).isPositive();
    assertThat(credentials.findByUserId(user.getId()))
        .get()
        .extracting(UserPasswordCredential::getPasswordHash)
        .isEqualTo(HASH);
    assertThat(credentials.findByUserId(-1L)).isEmpty();
  }

  @Test
  @DisplayName("同一个用户不能有两份密码凭据")
  void should_reject_second_credential_for_same_user() {
    User user = users.save(User.register(EmailAddress.of("twice@example.com")));
    credentials.save(UserPasswordCredential.create(user.getId(), HASH));

    assertThatThrownBy(() -> credentials.save(UserPasswordCredential.create(user.getId(), HASH)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
```

`IdentitySchemaConstraintsIT.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 建表脚本里的检查约束和外键：绕过应用层直接写库，数据库自己也要拦住不合法的数据。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("identity 表约束")
class IdentitySchemaConstraintsIT {

  private static final String INSERT_USER =
      "INSERT INTO idn_user (id, email, status, banned_at) VALUES (?, ?, ?, ?)";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("邮箱必须是小写")
  void should_reject_uppercase_email() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_USER, 1L, "Upper@Example.com", "ACTIVE", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_lowercase");
  }

  @Test
  @DisplayName("状态只能是 ACTIVE 或 BANNED")
  void should_reject_unknown_status() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_USER, 2L, "a@example.com", "DELETED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_status");
  }

  @Test
  @DisplayName("封禁状态必须有封禁时间")
  void should_require_banned_at_when_banned() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_USER, 3L, "b@example.com", "BANNED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("正常状态不能有封禁时间")
  void should_reject_banned_at_when_active() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    INSERT_USER,
                    4L,
                    "c@example.com",
                    "ACTIVE",
                    Timestamp.valueOf("2026-10-06 08:00:00")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("凭据必须指向存在的用户")
  void should_reject_credential_of_missing_user() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO idn_user_password_credential (id, user_id, password_hash) "
                        + "VALUES (?, ?, ?)",
                    5L,
                    999_999L,
                    "$argon2id$x"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_password_credential_user");
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest`
预期：编译失败，找不到 `UserRepositoryAdapter` 等类。

- [ ] **步骤 2：实体和 DAO**

`entity/UserEntity.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// `idn_user` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user")
public class UserEntity extends BaseJpaEntity {

  /// 规范化之后的邮箱
  @Column(name = "email", nullable = false, length = 254)
  private String email;

  /// 状态：ACTIVE / BANNED
  @Column(name = "status", nullable = false, length = 16)
  private String status;

  /// 封禁时间，未封禁时为 null
  @Column(name = "banned_at")
  private Instant bannedAt;
}
```

`entity/UserPasswordCredentialEntity.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// `idn_user_password_credential` 表的 JPA 实体。`toString()` 不输出哈希。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_password_credential")
public class UserPasswordCredentialEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// Argon2id 编码串
  @ToString.Exclude
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;
}
```

`dao/UserDao.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user` 的 Spring Data 仓库。
public interface UserDao extends JpaRepository<UserEntity, Long> {

  /// 按规范化之后的邮箱查。
  ///
  /// @param email 邮箱
  /// @return 实体
  Optional<UserEntity> findByEmail(String email);

  /// 邮箱是否存在。
  ///
  /// @param email 邮箱
  /// @return 存在时为 `true`
  boolean existsByEmail(String email);
}
```

`dao/UserPasswordCredentialDao.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserPasswordCredentialEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_password_credential` 的 Spring Data 仓库。
public interface UserPasswordCredentialDao
    extends JpaRepository<UserPasswordCredentialEntity, Long> {

  /// 按用户 ID 查。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  Optional<UserPasswordCredentialEntity> findByUserId(Long userId);
}
```

- [ ] **步骤 3：MapStruct 转换器**

`converter/mapper/UserJpaMapper.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// User 与 UserEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface UserJpaMapper {

  /// 聚合转实体。
  ///
  /// @param user 用户
  /// @return 实体
  @Mapping(target = "email", expression = "java(user.getEmail().value())")
  @Mapping(target = "status", expression = "java(user.getStatus().name())")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserEntity toEntity(User user);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 用户；实体为 `null` 时返回 `null`
  default User toAggregate(UserEntity entity) {
    if (entity == null) {
      return null;
    }
    return User.restore(
        entity.getId(),
        new EmailAddress(entity.getEmail()),
        UserStatus.valueOf(entity.getStatus()),
        entity.getBannedAt(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
```

`converter/mapper/UserPasswordCredentialJpaMapper.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserPasswordCredentialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// UserPasswordCredential 与 UserPasswordCredentialEntity 的转换。
@Mapper(componentModel = "spring")
public interface UserPasswordCredentialJpaMapper {

  /// 聚合转实体。
  ///
  /// @param credential 凭据
  /// @return 实体
  @Mapping(target = "passwordHash", expression = "java(credential.getPasswordHash().value())")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserPasswordCredentialEntity toEntity(UserPasswordCredential credential);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 凭据；实体为 `null` 时返回 `null`
  default UserPasswordCredential toAggregate(UserPasswordCredentialEntity entity) {
    if (entity == null) {
      return null;
    }
    return UserPasswordCredential.restore(
        entity.getId(),
        entity.getUserId(),
        PasswordHash.of(entity.getPasswordHash()),
        entity.getVersion());
  }
}
```

- [ ] **步骤 4：两个仓储适配器**

`UserRepositoryAdapter.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/// 前台用户仓储的 JPA 实现。
///
/// 保存用 `saveAndFlush`：唯一约束和乐观锁的冲突在这里就抛出来，
/// 邮箱唯一约束转成 {@link EmailAlreadyRegisteredException}，不把数据库的报错带进响应。
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

  /// 邮箱唯一约束的名字，见建表脚本。
  static final String EMAIL_UNIQUE_CONSTRAINT = "uk_idn_user_email";

  private final UserDao dao;
  private final UserJpaMapper mapper;

  /// 按 ID 查用户。
  ///
  /// @param id 用户 ID
  /// @return 用户
  @Override
  public Optional<User> findById(long id) {
    return dao.findById(id).map(mapper::toAggregate);
  }

  /// 按邮箱查用户。
  ///
  /// @param email 邮箱
  /// @return 用户
  @Override
  public Optional<User> findByEmail(EmailAddress email) {
    return dao.findByEmail(email.value()).map(mapper::toAggregate);
  }

  /// 邮箱是否已注册。
  ///
  /// @param email 邮箱
  /// @return 已注册时为 `true`
  @Override
  public boolean existsByEmail(EmailAddress email) {
    return dao.existsByEmail(email.value());
  }

  /// 保存用户，新用户分配雪花 ID。
  ///
  /// @param user 用户
  /// @return 保存后的用户
  @Override
  public User save(User user) {
    UserEntity entity = mapper.toEntity(user);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (DataIntegrityViolationException ex) {
      if (violates(ex, EMAIL_UNIQUE_CONSTRAINT)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw ex;
    }
  }

  /// 判断异常是否由指定约束引起。
  ///
  /// @param ex 数据完整性异常
  /// @param constraint 约束名
  /// @return 是该约束时为 `true`
  private static boolean violates(DataIntegrityViolationException ex, String constraint) {
    return ex.getCause() instanceof ConstraintViolationException violation
        && constraint.equalsIgnoreCase(violation.getConstraintName());
  }
}
```

`UserPasswordCredentialRepositoryAdapter.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserPasswordCredentialJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserPasswordCredentialDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserPasswordCredentialEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 前台用户密码凭据仓储的 JPA 实现。
@Repository
@RequiredArgsConstructor
public class UserPasswordCredentialRepositoryAdapter implements UserPasswordCredentialRepository {

  private final UserPasswordCredentialDao dao;
  private final UserPasswordCredentialJpaMapper mapper;

  /// 按用户 ID 查凭据。
  ///
  /// @param userId 用户 ID
  /// @return 凭据
  @Override
  public Optional<UserPasswordCredential> findByUserId(long userId) {
    return dao.findByUserId(userId).map(mapper::toAggregate);
  }

  /// 保存凭据，新凭据分配雪花 ID。
  ///
  /// @param credential 凭据
  /// @return 保存后的凭据
  @Override
  public UserPasswordCredential save(UserPasswordCredential credential) {
    UserPasswordCredentialEntity entity = mapper.toEntity(credential);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    return mapper.toAggregate(dao.saveAndFlush(entity));
  }
}
```

- [ ] **步骤 5：运行，确认通过**

运行：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-infra:check :patra-api:patra-identity:patra-identity-infra:integrationTest`
预期：`BUILD SUCCESSFUL`。如果唯一约束的转换没生效（`violation.getConstraintName()` 拿到的名字带引号、带 schema 前缀或为空），看异常链里的实际值调整比较方式，不要改成「凡是数据完整性异常都转成邮箱已注册」。

- [ ] **步骤 6：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-infra
git diff --cached --stat
git commit -m "feat(identity): 新增用户与密码凭据的持久化 (PAP-63)"
```

---

### 任务 11：infra：密码哈希

**Files:**

- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/hashing/PasswordHashingAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/linqibin/patra/identity/infra/adapter/hashing/PasswordHashingAdapterTest.java`

**Interfaces:**

- Consumes：任务 9 的 `PasswordHashingPort`；任务 8 的 `PlainPassword`、`PasswordHash`、`TemporarilyUnavailableException`。
- Produces：`final class PasswordHashingAdapter implements PasswordHashingPort`。构造器 `PasswordHashingAdapter(PasswordEncoder encoder, int maxConcurrent, Duration waitTimeout)`；工厂方法 `static PasswordHashingAdapter argon2(int maxConcurrent, Duration waitTimeout)` 用 `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`。不是 `@Component`，由任务 13 的配置类创建。

- [ ] **步骤 1：写失败的测试**

```java
package dev.linqibin.patra.identity.infra.adapter.hashing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/// PasswordHashingAdapter 单元测试。
@DisplayName("PasswordHashingAdapter 单元测试")
class PasswordHashingAdapterTest {

  private final PasswordHashingAdapter argon2 =
      PasswordHashingAdapter.argon2(4, Duration.ofSeconds(3));

  @Test
  @DisplayName("输出 Argon2id 编码串，参数是 OWASP 的最低配置（实测点 1）")
  void should_produce_argon2id_hash_with_owasp_parameters() {
    PasswordHash hash = argon2.hash(PlainPassword.of("correct horse battery"));

    assertThat(hash.value()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
  }

  @Test
  @DisplayName("同一个密码能校验通过，别的密码不行")
  void should_match_only_the_same_password() {
    PasswordHash hash = argon2.hash(PlainPassword.of("correct horse battery"));

    assertThat(argon2.matches(PlainPassword.of("correct horse battery"), hash)).isTrue();
    assertThat(argon2.matches(PlainPassword.of("correct horse battery "), hash)).isFalse();
    assertThat(argon2.matches(PlainPassword.of("Correct horse battery"), hash)).isFalse();
  }

  @Test
  @DisplayName("NFKC 前后等价的两个密码互相能校验通过（全角注册、半角登录）")
  void should_treat_nfkc_equivalent_passwords_as_same() {
    PasswordHash hash = argon2.hash(PlainPassword.of("ｐａｓｓｗｏｒｄ－２０２６"));

    assertThat(argon2.matches(PlainPassword.of("password-2026"), hash)).isTrue();
  }

  @Test
  @DisplayName("假哈希校验能正常完成")
  void should_complete_dummy_verification() {
    argon2.verifyAgainstDummy(PlainPassword.of("whatever-password"));
  }

  @Test
  @DisplayName("同时进行的计算到上限时，排队超时抛 TemporarilyUnavailableException")
  void should_reject_when_queue_wait_times_out() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    PasswordHashingAdapter adapter =
        new PasswordHashingAdapter(
            new BlockingPasswordEncoder(entered, release), 1, Duration.ofMillis(100));
    PasswordHash anyHash = PasswordHash.of("{fake}x");
    CompletableFuture<Boolean> holder =
        CompletableFuture.supplyAsync(() -> adapter.matches(PlainPassword.of("holder"), anyHash));
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> adapter.hash(PlainPassword.of("waiting-one")))
        .isInstanceOf(TemporarilyUnavailableException.class);

    release.countDown();
    assertThat(holder.get(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("计算完成后归还名额")
  void should_release_permit_after_work() {
    PasswordHashingAdapter adapter =
        new PasswordHashingAdapter(
            new BlockingPasswordEncoder(new CountDownLatch(1), new CountDownLatch(0)),
            1,
            Duration.ofMillis(100));

    adapter.hash(PlainPassword.of("first-call"));
    adapter.hash(PlainPassword.of("second-call"));
  }

  @Test
  @DisplayName("并发上限至少为 1")
  void should_require_positive_max_concurrent() {
    assertThatThrownBy(() -> PasswordHashingAdapter.argon2(0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 校验时阻塞的假编码器：进入 `matches` 后先发信号，再等放行。
  private static final class BlockingPasswordEncoder implements PasswordEncoder {

    private final CountDownLatch entered;
    private final CountDownLatch release;

    /// 创建假编码器。
    ///
    /// @param entered 进入 `matches` 时计数
    /// @param release 放行信号
    BlockingPasswordEncoder(CountDownLatch entered, CountDownLatch release) {
      this.entered = entered;
      this.release = release;
    }

    /// 返回一个假的编码串，不阻塞。
    ///
    /// @param rawPassword 明文
    /// @return 假编码串
    @Override
    public String encode(CharSequence rawPassword) {
      return "{fake}" + rawPassword;
    }

    /// 阻塞直到放行，然后返回 `true`。
    ///
    /// @param rawPassword 明文
    /// @param encodedPassword 编码串
    /// @return `true`
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      entered.countDown();
      try {
        return release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*PasswordHashingAdapterTest'`
预期：编译失败，找不到 `PasswordHashingAdapter`。

- [ ] **步骤 3：实现**

```java
package dev.linqibin.patra.identity.infra.adapter.hashing;

import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/// 密码哈希：Argon2id，参数取 OWASP Password Storage Cheat Sheet 的最低配置。
///
/// 每次计算要占 19 MiB 内存，同时进行的计算有上限；拿不到名额的排队，超时抛
/// {@link TemporarilyUnavailableException}，返回 503。哈希和校验都用 NFKC 之后的密码。
public final class PasswordHashingAdapter implements PasswordHashingPort {

  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_KIB = 19_456;
  private static final int ITERATIONS = 2;

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final Duration waitTimeout;
  private final String dummyHash;

  /// 创建适配器，并用同样的参数生成一个假哈希，供「用户不存在」时校验。
  ///
  /// @param encoder 编码器
  /// @param maxConcurrent 同时进行的计算上限，至少为 1
  /// @param waitTimeout 排队等待的最长时间
  public PasswordHashingAdapter(PasswordEncoder encoder, int maxConcurrent, Duration waitTimeout) {
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("maxConcurrent 至少为 1");
    }
    this.encoder = Objects.requireNonNull(encoder, "encoder 不能为 null");
    this.permits = new Semaphore(maxConcurrent, true);
    this.waitTimeout = Objects.requireNonNull(waitTimeout, "waitTimeout 不能为 null");
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  /// 用 Argon2id 创建适配器。
  ///
  /// @param maxConcurrent 同时进行的计算上限
  /// @param waitTimeout 排队等待的最长时间
  /// @return 适配器
  public static PasswordHashingAdapter argon2(int maxConcurrent, Duration waitTimeout) {
    return new PasswordHashingAdapter(
        new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS),
        maxConcurrent,
        waitTimeout);
  }

  /// 计算哈希。
  ///
  /// @param password 明文密码
  /// @return 哈希
  @Override
  public PasswordHash hash(PlainPassword password) {
    return withPermit(() -> PasswordHash.of(encoder.encode(password.normalized())));
  }

  /// 校验密码。
  ///
  /// @param password 明文密码
  /// @param hash 存储的哈希
  /// @return 匹配时为 `true`
  @Override
  public boolean matches(PlainPassword password, PasswordHash hash) {
    return withPermit(() -> encoder.matches(password.normalized(), hash.value()));
  }

  /// 拿假哈希做一次校验，结果丢弃。
  ///
  /// @param password 明文密码
  @Override
  public void verifyAgainstDummy(PlainPassword password) {
    withPermit(() -> encoder.matches(password.normalized(), dummyHash));
  }

  /// 拿到名额后执行计算，执行完归还。
  ///
  /// @param work 计算
  /// @param <T> 结果类型
  /// @return 结果
  private <T> T withPermit(Supplier<T> work) {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TemporarilyUnavailableException(e);
    }
    if (!acquired) {
      throw new TemporarilyUnavailableException();
    }
    try {
      return work.get();
    } finally {
      permits.release();
    }
  }
}
```

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*PasswordHashingAdapterTest'`
预期：全部通过。记下测试报告（`build/reports/tests/test/index.html`）里 `should_produce_argon2id_hash_with_owasp_parameters` 的耗时，任务 18 写回 spec 第 14 节第 1 条。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-infra
git diff --cached --stat
git commit -m "feat(identity): 新增 Argon2id 密码哈希，限制同时进行的计算数 (PAP-63)"
```

---

### 任务 12：infra：常见密码名单

**Files:**

- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/password/common-passwords.txt.gz`（下载）
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/password/common-passwords.LICENSE`（下载）
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/password/CommonPasswordAdapter.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/test/resources/password/test-common-passwords.txt.gz`（生成）
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/linqibin/patra/identity/infra/adapter/password/CommonPasswordAdapterTest.java`

**Interfaces:**

- Consumes：任务 9 的 `CommonPasswordPort`；任务 8 的 `PlainPassword.comparisonKeyOf`。
- Produces：`@Component final class CommonPasswordAdapter implements CommonPasswordPort`。公开无参构造器读 `password/common-passwords.txt.gz`；包内可见的构造器 `CommonPasswordAdapter(String classpathLocation)` 读指定位置。整份加载，每条去掉首尾空白、跳过空行，再转成比较键。

- [ ] **步骤 1：下载名单和许可证（先征得用户同意）**

向用户说明要下载的两个文件，得到同意后再执行：

- `common-passwords.txt.gz`：Django 6.0 标签下的 `django/contrib/auth/common-passwords.txt.gz`，约 80 KB（2026-10-06 核对为 80228 字节）。
- `common-passwords.LICENSE`：同一标签下的 Django `LICENSE`（BSD 3-Clause），约 1.5 KB。

```bash
DIR=patra-api/patra-identity/patra-identity-infra/src/main/resources/password
mkdir -p "$DIR"
curl -fsSL -o "$DIR/common-passwords.txt.gz" https://raw.githubusercontent.com/django/django/6.0/django/contrib/auth/common-passwords.txt.gz
curl -fsSL -o "$DIR/common-passwords.LICENSE" https://raw.githubusercontent.com/django/django/6.0/LICENSE
ls -l "$DIR"
shasum -a 256 "$DIR/common-passwords.txt.gz"
gzip -dc "$DIR/common-passwords.txt.gz" | wc -l
gzip -dc "$DIR/common-passwords.txt.gz" | head -5
```

预期：`.gz` 约 80 KB；解压后约 2 万行，每行一个小写密码。记下 SHA-256 和行数，任务 18 写进 README。

- [ ] **步骤 2：生成测试用的小名单**

```bash
mkdir -p patra-api/patra-identity/patra-identity-infra/src/test/resources/password
printf 'Abc12345\n123456\n  spaced-entry  \n\nｆｕｌｌｗｉｄｔｈ\n' | gzip -c > patra-api/patra-identity/patra-identity-infra/src/test/resources/password/test-common-passwords.txt.gz
gzip -dc patra-api/patra-identity/patra-identity-infra/src/test/resources/password/test-common-passwords.txt.gz
```

五行分别验证：大写转小写、8 位以下的条目也保留、首尾空白去掉、空行跳过、全角转半角。

- [ ] **步骤 3：写失败的测试**

```java
package dev.linqibin.patra.identity.infra.adapter.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// CommonPasswordAdapter 单元测试。
@DisplayName("CommonPasswordAdapter 单元测试")
class CommonPasswordAdapterTest {

  private final CommonPasswordAdapter testList =
      new CommonPasswordAdapter("password/test-common-passwords.txt.gz");

  @ParameterizedTest
  @ValueSource(strings = {"abc12345", "123456", "spaced-entry", "fullwidth"})
  @DisplayName("条目按比较键加载：转小写、去空白、全角转半角，短条目也保留")
  void should_load_entries_as_comparison_keys(String key) {
    assertThat(testList.isCommon(key)).isTrue();
  }

  @Test
  @DisplayName("空行不会变成一个空的条目")
  void should_skip_blank_lines() {
    assertThat(testList.isCommon("")).isFalse();
  }

  @Test
  @DisplayName("不在名单里的返回 false")
  void should_return_false_for_unknown_key() {
    assertThat(testList.isCommon("correct horse battery")).isFalse();
  }

  @Test
  @DisplayName("默认加载 Django 的名单")
  void should_load_django_list_by_default() {
    CommonPasswordAdapter django = new CommonPasswordAdapter();

    assertThat(django.isCommon(PlainPassword.comparisonKeyOf("PASSWORD"))).isTrue();
    assertThat(django.isCommon("123456")).isTrue();
    assertThat(django.isCommon("qwerty")).isTrue();
    assertThat(django.isCommon("correct horse battery staple 2026")).isFalse();
  }

  @Test
  @DisplayName("名单文件不存在时启动失败")
  void should_fail_when_list_is_missing() {
    assertThatThrownBy(() -> new CommonPasswordAdapter("password/missing.txt.gz"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("password/missing.txt.gz");
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*CommonPasswordAdapterTest'`
预期：编译失败，找不到 `CommonPasswordAdapter`。

- [ ] **步骤 4：实现**

```java
package dev.linqibin.patra.identity.infra.adapter.password;

import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/// 常见密码名单：Django `CommonPasswordValidator` 自带的 2 万条（Royce Williams 整理，BSD 许可）。
///
/// 启动时整份读进内存，每条转成比较键。不按长度过滤：长度按原始输入算，比较前却要先规范化，
/// ` 123456 ` 这类输入的比较键会比 8 位短。
@Component
public final class CommonPasswordAdapter implements CommonPasswordPort {

  /// 默认名单的位置。
  static final String DEFAULT_LOCATION = "password/common-passwords.txt.gz";

  private final Set<String> comparisonKeys;

  /// 加载默认名单。
  public CommonPasswordAdapter() {
    this(DEFAULT_LOCATION);
  }

  /// 加载指定位置的名单（gzip，一行一条）。
  ///
  /// @param classpathLocation classpath 上的位置
  CommonPasswordAdapter(String classpathLocation) {
    this.comparisonKeys = load(classpathLocation);
  }

  /// 比较键是否在名单里。
  ///
  /// @param comparisonKey 比较键
  /// @return 在名单里时为 `true`
  @Override
  public boolean isCommon(String comparisonKey) {
    return comparisonKeys.contains(comparisonKey);
  }

  /// 读名单：去掉首尾空白、跳过空行、转成比较键。
  ///
  /// @param location classpath 上的位置
  /// @return 比较键集合
  private static Set<String> load(String location) {
    try (InputStream raw = new ClassPathResource(location).getInputStream();
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty())
          .map(PlainPassword::comparisonKeyOf)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException("读不到常见密码名单: " + location, e);
    }
  }
}
```

- [ ] **步骤 5：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*CommonPasswordAdapterTest'`
预期：全部通过。

- [ ] **步骤 6：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-infra
git diff --cached --stat
git commit -m "feat(identity): 新增常见密码名单（取自 Django 6.0） (PAP-63)"
```

如果 pre-commit 的 gitleaks 把名单文件当成密钥拦下，停下来报告用户，不要加忽略规则绕过。

---

### 任务 13：infra：登录失败限制（Redis），并接进 boot 配置

**Files:**

- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/redis/login-throttle-begin.lua`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/redis/login-throttle-settle.lua`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/throttle/LoginThrottleAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/linqibin/patra/identity/infra/adapter/throttle/LoginThrottleAdapterTest.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/linqibin/patra/identity/infra/adapter/throttle/LoginThrottleAdapterIT.java`
- Create: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/config/IdentityProperties.java`
- Create: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/config/IdentityConfiguration.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`
- Test: `patra-api/patra-identity/patra-identity-boot/src/test/java/dev/linqibin/patra/identity/config/IdentityConfigurationTest.java`

**Interfaces:**

- Consumes：任务 9 的 `LoginThrottlePort`、`LoginAttempt`、`LoginThrottlePolicy`、`PasswordPolicy`；任务 8 的 `LoginTemporarilyLockedException`、`TemporarilyUnavailableException`；任务 6 的 `RedisContainerInitializer`；任务 11 的 `PasswordHashingAdapter`；任务 12 的 `CommonPasswordAdapter`。
- Produces：`@Component final class LoginThrottleAdapter implements LoginThrottlePort`，构造器 `(StringRedisTemplate redis, LoginThrottlePolicy policy)`。包内可见的静态方法 `List<String> keys(AccountType, EmailAddress)` 返回 `[失败计数键, 在途键, 锁键]`。
- Produces（boot）：配置项 `patra.identity.login-throttle.{max-failures, window, lock-duration, in-flight-ttl}`、`patra.identity.password-hashing.{max-concurrent, wait-timeout}`；三个 Bean：`LoginThrottlePolicy`、`PasswordHashingPort`（`PasswordHashingAdapter.argon2(...)`）、`PasswordPolicy`。放在这个任务里，是因为 `LoginThrottleAdapter` 是第一个需要它们的组件，任务 14 到 16 的处理器也要用。

脚本的返回值：

- 开始：`0` 放行；`-1` 失败数加在途数已到上限；正数是锁的剩余毫秒。
- 结算：`0` 没有锁；正数是锁的剩余毫秒（这次刚上锁时等于锁定时长）。

- [ ] **步骤 1：写两段 Lua 脚本**

`login-throttle-begin.lua`：

```lua
-- 开始一次登录尝试（在校验密码之前）。
-- KEYS[1] 失败计数  KEYS[2] 在途登记（有序集合，分数是过期时间）  KEYS[3] 锁
-- ARGV[1] 失败次数上限  ARGV[2] 在途登记 ID  ARGV[3] 在途登记的过期毫秒
-- 返回：0 放行；-1 失败数加在途数已到上限；正数是锁的剩余毫秒
local lockTtl = redis.call('PTTL', KEYS[3])
if lockTtl > 0 then
  return lockTtl
end
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
local failures = tonumber(redis.call('GET', KEYS[1]) or '0')
local inflight = redis.call('ZCARD', KEYS[2])
if failures + inflight >= tonumber(ARGV[1]) then
  return -1
end
local ttl = tonumber(ARGV[3])
redis.call('ZADD', KEYS[2], now + ttl, ARGV[2])
redis.call('PEXPIRE', KEYS[2], ttl)
return 0
```

`login-throttle-settle.lua`：

```lua
-- 结算一次登录尝试（在校验密码之后，必定执行一次）。
-- KEYS[1] 失败计数  KEYS[2] 在途登记  KEYS[3] 锁
-- ARGV[1] 在途登记 ID  ARGV[2] 结果：SUCCESS / FAILURE / CANCEL
-- ARGV[3] 失败次数上限  ARGV[4] 计数窗口毫秒  ARGV[5] 锁定毫秒
-- 返回：0 没有锁；正数是锁的剩余毫秒
redis.call('ZREM', KEYS[2], ARGV[1])
local outcome = ARGV[2]
if outcome == 'SUCCESS' then
  redis.call('DEL', KEYS[1])
  return 0
end
if outcome ~= 'FAILURE' then
  return 0
end
local lockTtl = redis.call('PTTL', KEYS[3])
if lockTtl > 0 then
  return lockTtl
end
local failures = redis.call('INCR', KEYS[1])
if failures == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[4])
end
if failures >= tonumber(ARGV[3]) then
  redis.call('SET', KEYS[3], '1', 'PX', ARGV[5])
  redis.call('DEL', KEYS[1])
  return tonumber(ARGV[5])
end
return 0
```

- [ ] **步骤 2：写键名的失败单元测试**

`LoginThrottleAdapterTest.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginThrottleAdapter 单元测试：只测键名。
@DisplayName("LoginThrottleAdapter 键名")
class LoginThrottleAdapterTest {

  @Test
  @DisplayName("三个键带账号类型和邮箱的 SHA-256，不含明文邮箱")
  void should_build_keys_with_account_type_and_email_digest() {
    List<String> keys = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("chen.yu@example.com"));

    assertThat(keys).hasSize(3);
    assertThat(keys.get(0)).matches("idn:login-failures:user:[0-9a-f]{64}");
    assertThat(keys.get(1)).matches("idn:login-inflight:user:[0-9a-f]{64}");
    assertThat(keys.get(2)).matches("idn:login-lock:user:[0-9a-f]{64}");
    assertThat(String.join(",", keys)).doesNotContain("chen.yu");
  }

  @Test
  @DisplayName("同一个邮箱的三个键后缀相同，不同邮箱不同")
  void should_share_suffix_for_same_email_only() {
    List<String> a = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("a@example.com"));
    List<String> b = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("b@example.com"));

    assertThat(suffix(a.get(0))).isEqualTo(suffix(a.get(2)));
    assertThat(suffix(a.get(0))).isNotEqualTo(suffix(b.get(0)));
  }

  /// 取键名最后一段。
  ///
  /// @param key 键名
  /// @return 最后一个冒号之后的部分
  private static String suffix(String key) {
    return key.substring(key.lastIndexOf(':') + 1);
  }
}
```

- [ ] **步骤 3：写交错场景的失败集成测试**

`LoginThrottleAdapterIT.java`（不起 Spring 上下文，直接连共享的 Redis 测试容器；每个用例用不同的邮箱，互不干扰）：

```java
package dev.linqibin.patra.identity.infra.adapter.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// LoginThrottleAdapter 集成测试：两段 Lua 脚本在 redis:7.0.15 上的行为（实测点 4）。
@DisplayName("LoginThrottleAdapter 集成测试")
class LoginThrottleAdapterIT {

  private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
  private static final LoginThrottlePolicy DEFAULT_POLICY =
      LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofSeconds(30));

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  /// 连上共享的 Redis 测试容器。
  @BeforeAll
  static void connect() {
    GenericContainer<?> container = RedisContainerInitializer.getRedisContainer();
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redis = new StringRedisTemplate(connectionFactory);
  }

  /// 断开连接。
  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("连续失败 5 次，第 5 次上锁并返回锁定时长，之后的尝试被拒")
  void should_lock_on_fifth_failure() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();

    for (int i = 0; i < 4; i++) {
      assertThat(adapter.recordFailure(adapter.begin(AccountType.USER, email))).isEmpty();
    }
    Optional<Duration> lock = adapter.recordFailure(adapter.begin(AccountType.USER, email));

    assertThat(lock).contains(FIFTEEN_MINUTES);
    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class)
        .satisfies(
            e ->
                assertThat(((LoginTemporarilyLockedException) e).getRetryAfter())
                    .isPositive()
                    .isLessThanOrEqualTo(FIFTEEN_MINUTES));
  }

  @Test
  @DisplayName("中间成功一次，失败计数清零")
  void should_reset_failures_on_success() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 4);

    adapter.recordSuccess(adapter.begin(AccountType.USER, email));

    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("并发 20 个错误尝试：只放行 5 个，其余拿到 1 秒的 429；5 个都失败后上锁")
  void should_admit_at_most_five_concurrent_attempts() throws Exception {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Object>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
      for (int i = 0; i < 20; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return adapter.begin(AccountType.USER, email);
                  } catch (LoginTemporarilyLockedException e) {
                    return e;
                  }
                }));
      }
      start.countDown();
      List<LoginAttempt> admitted = new ArrayList<>();
      List<LoginTemporarilyLockedException> rejected = new ArrayList<>();
      for (Future<Object> result : results) {
        Object value = result.get();
        if (value instanceof LoginAttempt attempt) {
          admitted.add(attempt);
        } else {
          rejected.add((LoginTemporarilyLockedException) value);
        }
      }

      assertThat(admitted).hasSize(5);
      assertThat(rejected)
          .hasSize(15)
          .allSatisfy(e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
      long locks = admitted.stream().filter(a -> adapter.recordFailure(a).isPresent()).count();
      assertThat(locks).isEqualTo(1);
    }
    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);
  }

  @Test
  @DisplayName("同时提交 6 次正确密码：至多一次拿到 429，不上锁")
  void should_not_lock_on_concurrent_successful_attempts() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    List<LoginAttempt> inFlight = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inFlight.add(adapter.begin(AccountType.USER, email));
    }

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);

    inFlight.forEach(adapter::recordSuccess);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("5 次取消之后，下一次尝试照常放行")
  void should_release_slots_on_cancel() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    List<LoginAttempt> inFlight = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inFlight.add(adapter.begin(AccountType.USER, email));
    }

    inFlight.forEach(adapter::cancel);

    LoginAttempt next = adapter.begin(AccountType.USER, email);
    adapter.recordSuccess(next);
    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("先放行 A，B 成功之后 A 才按失败结算：只计 1 次，不凭旧计数上锁")
  void should_count_late_failure_in_current_window() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 3);
    LoginAttempt late = adapter.begin(AccountType.USER, email);
    LoginAttempt success = adapter.begin(AccountType.USER, email);

    adapter.recordSuccess(success);

    assertThat(adapter.recordFailure(late)).isEmpty();
    failTimes(adapter, email, 3);
    assertThat(adapter.recordFailure(adapter.begin(AccountType.USER, email))).isPresent();
  }

  @Test
  @DisplayName("锁定期间结算的失败不计数")
  void should_not_count_failure_settled_during_lock() {
    LoginThrottlePolicy shortLock =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, Duration.ofSeconds(1), Duration.ofSeconds(30));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortLock);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 5);

    Optional<Duration> during =
        adapter.recordFailure(LoginAttempt.of(AccountType.USER, email, "stale-ticket"));

    assertThat(during).isPresent();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> adapter.cancel(adapter.begin(AccountType.USER, email)));
    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("没结算的在途登记过期后，名额自动释放")
  void should_expire_in_flight_registrations() {
    LoginThrottlePolicy shortTtl =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofMillis(300));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortTtl);
    EmailAddress email = uniqueEmail();
    for (int i = 0; i < 5; i++) {
      adapter.begin(AccountType.USER, email);
    }

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(adapter.begin(AccountType.USER, email)).isNotNull());
  }

  @Test
  @DisplayName("计数窗口过期后，失败次数重新算")
  void should_expire_failure_window() {
    LoginThrottlePolicy shortWindow =
        LoginThrottlePolicy.of(5, Duration.ofMillis(500), FIFTEEN_MINUTES, Duration.ofSeconds(30));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortWindow);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 4);

    await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2)).until(() -> true);

    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("Redis 连不上时抛 TemporarilyUnavailableException（实测点 3 的一部分）")
  void should_translate_connection_failure() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    LettuceConnectionFactory deadFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", closedPort),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
    deadFactory.afterPropertiesSet();
    deadFactory.start();
    try {
      LoginThrottleAdapter adapter =
          new LoginThrottleAdapter(new StringRedisTemplate(deadFactory), DEFAULT_POLICY);

      assertThatThrownBy(() -> adapter.begin(AccountType.USER, uniqueEmail()))
          .isInstanceOf(TemporarilyUnavailableException.class);
    } finally {
      deadFactory.destroy();
    }
  }

  /// 每个用例用不同的邮箱，互不干扰。
  ///
  /// @return 随机邮箱
  private static EmailAddress uniqueEmail() {
    return EmailAddress.of("throttle-" + UUID.randomUUID() + "@example.com");
  }

  /// 连续失败若干次，每次都没有上锁。
  ///
  /// @param adapter 适配器
  /// @param email 邮箱
  /// @param times 次数
  private static void failTimes(LoginThrottleAdapter adapter, EmailAddress email, int times) {
    for (int i = 0; i < times; i++) {
      adapter.recordFailure(adapter.begin(AccountType.USER, email));
    }
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*LoginThrottleAdapterTest'`
预期：编译失败，找不到 `LoginThrottleAdapter`。

- [ ] **步骤 4：实现**

```java
package dev.linqibin.patra.identity.infra.adapter.throttle;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/// 登录失败限制的 Redis 实现。
///
/// 失败次数、在途登记、锁分三个键，键名带账号类型和邮箱的 SHA-256，不放明文邮箱。
/// 「开始」和「结算」各是一段 Lua 脚本，原子执行。Redis 连不上或超时，转成
/// {@link TemporarilyUnavailableException}；其他 Redis 异常（比如脚本写错）是程序缺陷，原样抛出。
@Component
public final class LoginThrottleAdapter implements LoginThrottlePort {

  private static final RedisScript<Long> BEGIN_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/login-throttle-begin.lua"), Long.class);
  private static final RedisScript<Long> SETTLE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/login-throttle-settle.lua"), Long.class);

  /// 「失败数 + 在途数」到上限时告诉调用方的等待时间。
  private static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(1);

  private final StringRedisTemplate redis;
  private final LoginThrottlePolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 失败限制的参数
  public LoginThrottleAdapter(StringRedisTemplate redis, LoginThrottlePolicy policy) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 开始一次尝试。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 被放行的尝试
  @Override
  public LoginAttempt begin(AccountType accountType, EmailAddress email) {
    String ticketId = UUID.randomUUID().toString();
    long result =
        execute(
            () ->
                redis.execute(
                    BEGIN_SCRIPT,
                    keys(accountType, email),
                    String.valueOf(policy.maxFailures()),
                    ticketId,
                    String.valueOf(policy.inFlightTtl().toMillis())));
    if (result > 0) {
      throw new LoginTemporarilyLockedException(Duration.ofMillis(result));
    }
    if (result < 0) {
      throw new LoginTemporarilyLockedException(BUSY_RETRY_AFTER);
    }
    return LoginAttempt.of(accountType, email, ticketId);
  }

  /// 按成功结算。
  ///
  /// @param attempt 尝试
  @Override
  public void recordSuccess(LoginAttempt attempt) {
    settle(attempt, "SUCCESS");
  }

  /// 按失败结算。
  ///
  /// @param attempt 尝试
  /// @return 处于锁定期时返回剩余时间
  @Override
  public Optional<Duration> recordFailure(LoginAttempt attempt) {
    long lockMillis = settle(attempt, "FAILURE");
    return lockMillis > 0 ? Optional.of(Duration.ofMillis(lockMillis)) : Optional.empty();
  }

  /// 按取消结算。
  ///
  /// @param attempt 尝试
  @Override
  public void cancel(LoginAttempt attempt) {
    settle(attempt, "CANCEL");
  }

  /// 执行结算脚本。
  ///
  /// @param attempt 尝试
  /// @param outcome 结果
  /// @return 锁的剩余毫秒，没有锁时为 0
  private long settle(LoginAttempt attempt, String outcome) {
    return execute(
        () ->
            redis.execute(
                SETTLE_SCRIPT,
                keys(attempt.accountType(), attempt.email()),
                attempt.ticketId(),
                outcome,
                String.valueOf(policy.maxFailures()),
                String.valueOf(policy.window().toMillis()),
                String.valueOf(policy.lockDuration().toMillis())));
  }

  /// 三个键：失败计数、在途登记、锁。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 键名
  static List<String> keys(AccountType accountType, EmailAddress email) {
    String suffix = accountType.getCode() + ":" + sha256Hex(email.value());
    return List.of(
        "idn:login-failures:" + suffix, "idn:login-inflight:" + suffix, "idn:login-lock:" + suffix);
  }

  /// 计算 SHA-256 的十六进制形式。
  ///
  /// @param value 输入
  /// @return 64 位十六进制
  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 执行 Redis 调用，把连不上和超时转成 503。
  ///
  /// @param call 调用
  /// @return 脚本返回值，`null` 按 0 处理
  private static long execute(Supplier<Long> call) {
    try {
      Long result = call.get();
      return result == null ? 0 : result;
    } catch (DataAccessResourceFailureException | QueryTimeoutException e) {
      throw new TemporarilyUnavailableException(e);
    }
  }
}
```

- [ ] **步骤 5：运行，确认通过**

运行：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-infra:check :patra-api:patra-identity:patra-identity-infra:integrationTest`
预期：`BUILD SUCCESSFUL`。实测点 3、4 的结果记下来，任务 18 写回 spec。任何一个交错场景的结果和断言不符：停下来报告，不要改断言迁就脚本。

- [ ] **步骤 6：接进 boot：写配置的失败测试**

```java
package dev.linqibin.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// IdentityConfiguration 单元测试。
@DisplayName("IdentityConfiguration 单元测试")
class IdentityConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(IdentityConfiguration.class)
          .withBean(CommonPasswordPort.class, () -> key -> false);

  @Test
  @DisplayName("不配置时用默认值：5 次、15 分钟窗口、锁 15 分钟、在途 30 秒")
  void should_use_defaults() {
    runner.run(
        context -> {
          assertThat(context.getBean(LoginThrottlePolicy.class))
              .isEqualTo(
                  LoginThrottlePolicy.of(
                      5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30)));
          assertThat(context).hasSingleBean(PasswordHashingPort.class);
          assertThat(context).hasSingleBean(PasswordPolicy.class);
        });
  }

  @Test
  @DisplayName("配置项能覆盖默认值")
  void should_bind_overrides() {
    runner
        .withPropertyValues(
            "patra.identity.login-throttle.max-failures=3",
            "patra.identity.login-throttle.lock-duration=2s")
        .run(
            context -> {
              LoginThrottlePolicy policy = context.getBean(LoginThrottlePolicy.class);
              assertThat(policy.maxFailures()).isEqualTo(3);
              assertThat(policy.lockDuration()).isEqualTo(Duration.ofSeconds(2));
            });
  }

  @Test
  @DisplayName("非法配置让应用启动失败")
  void should_fail_on_invalid_configuration() {
    runner
        .withPropertyValues("patra.identity.login-throttle.max-failures=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests '*IdentityConfigurationTest'`
预期：编译失败，找不到 `IdentityConfiguration`。

- [ ] **步骤 7：接进 boot：实现配置**

`IdentityProperties.java`：

```java
package dev.linqibin.patra.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// identity 的配置项，前缀 `patra.identity`。
///
/// @param loginThrottle 登录失败限制
/// @param passwordHashing 密码哈希
@ConfigurationProperties(prefix = "patra.identity")
public record IdentityProperties(
    @DefaultValue LoginThrottle loginThrottle, @DefaultValue PasswordHashing passwordHashing) {

  /// 登录失败限制。
  ///
  /// @param maxFailures 计数窗口内允许的失败次数
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  public record LoginThrottle(
      @DefaultValue("5") int maxFailures,
      @DefaultValue("15m") Duration window,
      @DefaultValue("15m") Duration lockDuration,
      @DefaultValue("30s") Duration inFlightTtl) {}

  /// 密码哈希。
  ///
  /// @param maxConcurrent 同时进行的计算上限
  /// @param waitTimeout 排队等待的最长时间
  public record PasswordHashing(
      @DefaultValue("4") int maxConcurrent, @DefaultValue("3s") Duration waitTimeout) {}
}
```

`IdentityConfiguration.java`：

```java
package dev.linqibin.patra.identity.config;

import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import dev.linqibin.patra.identity.infra.adapter.hashing.PasswordHashingAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// identity 的装配：把配置项变成领域层的规则对象，创建密码哈希适配器。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfiguration {

  /// 登录失败限制的参数。
  ///
  /// @param properties 配置
  /// @return 参数
  @Bean
  public LoginThrottlePolicy loginThrottlePolicy(IdentityProperties properties) {
    IdentityProperties.LoginThrottle throttle = properties.loginThrottle();
    return LoginThrottlePolicy.of(
        throttle.maxFailures(), throttle.window(), throttle.lockDuration(), throttle.inFlightTtl());
  }

  /// 密码哈希：Argon2id。
  ///
  /// @param properties 配置
  /// @return 哈希端口
  @Bean
  public PasswordHashingPort passwordHashingPort(IdentityProperties properties) {
    IdentityProperties.PasswordHashing hashing = properties.passwordHashing();
    return PasswordHashingAdapter.argon2(hashing.maxConcurrent(), hashing.waitTimeout());
  }

  /// 注册时的密码规则。
  ///
  /// @param commonPasswordPort 常见密码名单
  /// @return 规则
  @Bean
  public PasswordPolicy passwordPolicy(CommonPasswordPort commonPasswordPort) {
    return new PasswordPolicy(commonPasswordPort);
  }
}
```

`application.yml` 末尾加：

```yaml
patra:
  identity:
    login-throttle:
      max-failures: 5
      window: 15m
      lock-duration: 15m
      in-flight-ttl: 30s
    password-hashing:
      max-concurrent: 4
      wait-timeout: 3s
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-boot:test`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：确认应用还能启动**

`LoginThrottleAdapter` 是第一个需要配置 Bean 的组件，从这里开始应用的启动依赖这份配置。

运行：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:test :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*PatraIdentityApplicationIT'`
预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 9：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-infra patra-api/patra-identity/patra-identity-boot
git diff --cached --stat
git commit -m "feat(identity): 新增登录失败限制并接进 boot 配置，失败与在途尝试分开计数 (PAP-63)"
```

---

### 任务 14：app：注册

**Files:**

- Create（`patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/register/` 下）：`RegisterUserCommand.java`、`RegisterUserResult.java`、`RegisterUserHandler.java`
- Test: `patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/register/RegisterUserHandlerTest.java`

**Interfaces:**

- Consumes：任务 9 的 `User`、`UserPasswordCredential`、`PasswordPolicy`、两个仓储端口、`PasswordHashingPort`；任务 8 的值对象和异常。
- Produces：
  - `record RegisterUserCommand(String email, String password) implements Command<RegisterUserResult>`，`static of(String, String)`，`toString()` 遮掉密码。
  - `record RegisterUserResult(long userId, String email)`，`static of(long, String)`。
  - `@Component RegisterUserHandler`，构造器参数 `(UserRepository, UserPasswordCredentialRepository, PasswordHashingPort, PasswordPolicy, TransactionOperations)`。

- [ ] **步骤 1：写失败的测试**

```java
package dev.linqibin.patra.identity.app.usecase.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// RegisterUserHandler 单元测试。
@DisplayName("RegisterUserHandler 单元测试")
class RegisterUserHandlerTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final User SAVED_USER =
      User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null);

  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final PasswordPolicy passwordPolicy =
      new PasswordPolicy(Set.of("password123")::contains);
  private final RegisterUserHandler handler =
      new RegisterUserHandler(
          users,
          credentials,
          passwordHashing,
          passwordPolicy,
          TransactionOperations.withoutTransaction());

  @Test
  @DisplayName("注册成功：存用户、存凭据，返回用户 ID 和规范化之后的邮箱")
  void should_register_user_with_password_credential() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenReturn(SAVED_USER);
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    RegisterUserResult result =
        handler.handle(RegisterUserCommand.of(" Chen.Yu@Example.com ", "correct horse battery"));

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    verify(users).existsByEmail(EMAIL);
    ArgumentCaptor<UserPasswordCredential> credential =
        ArgumentCaptor.forClass(UserPasswordCredential.class);
    verify(credentials).save(credential.capture());
    assertThat(credential.getValue().getUserId()).isEqualTo(42L);
    assertThat(credential.getValue().getPasswordHash()).isEqualTo(HASH);
  }

  @Test
  @DisplayName("两个字段的错误一次报全，不查库也不做哈希")
  void should_report_all_field_violations_at_once() {
    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of("", "short")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:TOO_SHORT"));
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("字段为 null 时按 REQUIRED 处理，不出空指针")
  void should_treat_null_fields_as_required() {
    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of(null, null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:REQUIRED"));
  }

  @Test
  @DisplayName("常见密码报 TOO_COMMON")
  void should_reject_common_password() {
    assertThatThrownBy(
            () -> handler.handle(RegisterUserCommand.of("chen.yu@example.com", "Password123")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.code())
                    .containsExactly("TOO_COMMON"));
  }

  @Test
  @DisplayName("1 MB 的邮箱和密码在长度校验处拒绝，不做哈希")
  void should_reject_huge_input_without_hashing() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";
    String hugePassword = "b".repeat(1_000_000);

    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of(hugeEmail, hugePassword)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:TOO_LONG", "password:TOO_LONG"));
    verifyNoInteractions(passwordHashing);
  }

  @Test
  @DisplayName("邮箱已注册时返回 409，不做哈希")
  void should_reject_registered_email_before_hashing() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(
            () ->
                handler.handle(
                    RegisterUserCommand.of("CHEN.YU@example.com", "correct horse battery")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verifyNoInteractions(passwordHashing);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("哈希在事务外做，两次保存在同一个事务里")
  void should_hash_outside_transaction_and_save_inside() {
    AtomicBoolean inTransaction = new AtomicBoolean(false);
    TransactionOperations recording =
        new TransactionOperations() {
          /// 标记事务边界后执行回调。
          ///
          /// @param action 回调
          /// @param <T> 结果类型
          /// @return 回调结果
          @Override
          public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            inTransaction.set(true);
            try {
              return action.doInTransaction(new SimpleTransactionStatus());
            } finally {
              inTransaction.set(false);
            }
          }
        };
    RegisterUserHandler transactional =
        new RegisterUserHandler(users, credentials, passwordHashing, passwordPolicy, recording);
    when(passwordHashing.hash(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return HASH;
            });
    when(users.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return SAVED_USER;
            });
    when(credentials.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return invocation.getArgument(0);
            });

    transactional.handle(RegisterUserCommand.of("chen.yu@example.com", "correct horse battery"));

    verify(credentials).save(any());
  }

  @Test
  @DisplayName("并发注册撞上唯一约束时，仓储抛出的 409 原样传出")
  void should_propagate_unique_violation() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenThrow(new EmailAlreadyRegisteredException());

    assertThatThrownBy(
            () ->
                handler.handle(
                    RegisterUserCommand.of("chen.yu@example.com", "correct horse battery")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(credentials, never()).save(any());
  }

  @Test
  @DisplayName("命令的 toString 不输出密码")
  void should_hide_password_in_command_to_string() {
    assertThat(RegisterUserCommand.of("chen.yu@example.com", "Secret-Value-1").toString())
        .doesNotContain("Secret-Value-1");
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*RegisterUserHandlerTest'`
预期：编译失败，找不到 `RegisterUserHandler` 等。

- [ ] **步骤 3：实现**

`RegisterUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.Command;

/// 注册前台用户。字段是用户的原始输入，校验在处理器里做。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
public record RegisterUserCommand(String email, String password)
    implements Command<RegisterUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 命令
  public static RegisterUserCommand of(String email, String password) {
    return new RegisterUserCommand(email, password);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserCommand[email=" + email + ", password=***]";
  }
}
```

`RegisterUserResult.java`：

```java
package dev.linqibin.patra.identity.app.usecase.register;

/// 注册结果。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record RegisterUserResult(long userId, String email) {

  /// 创建结果。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static RegisterUserResult of(long userId, String email) {
    return new RegisterUserResult(userId, email);
  }
}
```

`RegisterUserHandler.java`：

```java
package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注册前台用户。
///
/// 顺序：两个字段一起校验（有错一次报全）→ 查邮箱是否已注册 → 哈希 → 在一个事务里存用户和凭据。
/// 哈希放在事务外：事务开始时就会占住数据库连接，而哈希可能要排队几秒。
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUserHandler implements CommandHandler<RegisterUserCommand, RegisterUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final PasswordPolicy passwordPolicy;
  private final TransactionOperations transactions;

  /// 注册。
  ///
  /// @param command 命令
  /// @return 新用户的 ID 和邮箱
  @Override
  public RegisterUserResult handle(RegisterUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    passwordPolicy.validateForRegistration(command.password()).ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }
    PasswordHash hash = passwordHashing.hash(PlainPassword.of(command.password()));
    User user =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User saved = users.save(User.register(email));
                  credentials.save(UserPasswordCredential.create(saved.getId(), hash));
                  return saved;
                }));
    log.info("前台用户已注册: userId={}", user.getId());
    return RegisterUserResult.of(user.getId(), user.getEmail().value());
  }
}
```

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*RegisterUserHandlerTest'`
预期：全部通过。

顺带确认应用还能启动（新组件都接上了依赖）：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*PatraIdentityApplicationIT'`，预期 `BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-app
git diff --cached --stat
git commit -m "feat(identity): 新增注册用例 (PAP-63)"
```

---

### 任务 15：app：登录校验

**Files:**

- Create（`patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/authenticate/` 下）：`AuthenticateUserCommand.java`、`AuthenticateUserResult.java`、`AuthenticateUserHandler.java`
- Test: `patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/authenticate/AuthenticateUserHandlerTest.java`

**Interfaces:**

- Consumes：任务 9 的 `LoginThrottlePort`、`LoginAttempt`、`PasswordHashingPort`、两个仓储端口；任务 1 的 `AccountType.USER`。
- Produces：
  - `record AuthenticateUserCommand(String email, String password) implements Command<AuthenticateUserResult>`，`static of`，`toString()` 遮掉密码。
  - `record AuthenticateUserResult(long userId, String email)`，`static of`。PAP-64 拿它建会话。
  - `@Component AuthenticateUserHandler`，构造器参数 `(UserRepository, UserPasswordCredentialRepository, PasswordHashingPort, LoginThrottlePort)`。不开事务。

- [ ] **步骤 1：写失败的测试**

```java
package dev.linqibin.patra.identity.app.usecase.authenticate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// AuthenticateUserHandler 单元测试。
@DisplayName("AuthenticateUserHandler 单元测试")
class AuthenticateUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final User ACTIVE_USER =
      User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null);
  private static final User BANNED_USER =
      User.restore(
          42L, EMAIL, UserStatus.BANNED, Instant.parse("2026-10-06T08:00:00Z"), 1L, null, null);
  private static final UserPasswordCredential CREDENTIAL =
      UserPasswordCredential.restore(7L, 42L, HASH, 0L);
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");

  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final AuthenticateUserHandler handler =
      new AuthenticateUserHandler(users, credentials, passwordHashing, loginThrottle);

  /// 默认放行，用户和凭据都存在。
  @BeforeEach
  void stubHappyPath() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ACTIVE_USER));
    when(credentials.findByUserId(42L)).thenReturn(Optional.of(CREDENTIAL));
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("密码正确：按成功结算，返回用户 ID 和邮箱")
  void should_authenticate_with_correct_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    AuthenticateUserResult result =
        handler.handle(AuthenticateUserCommand.of("Chen.Yu@Example.com", "correct horse"));

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("密码错误：按失败结算，返回 401")
  void should_reject_wrong_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("这次失败触发上锁：返回 429 和锁定时长")
  void should_return_429_when_failure_triggers_lock() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(LoginTemporarilyLockedException.class)
        .satisfies(
            e ->
                assertThat(((LoginTemporarilyLockedException) e).getRetryAfter())
                    .isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("邮箱不存在：照样做一次假哈希校验，按失败结算，返回和密码错一样的 401")
  void should_treat_unknown_email_like_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
    verify(passwordHashing, never()).matches(any(), any());
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("用户在但凭据不在：也按密码错处理")
  void should_treat_missing_credential_like_wrong_password() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
  }

  @Test
  @DisplayName("锁定期间直接返回 429，不查库、不做哈希")
  void should_reject_when_locked_without_touching_storage() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("封禁的账号：密码对返回 403")
  void should_return_403_for_banned_user_with_correct_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "right")))
        .isInstanceOf(UserBannedException.class);
    verify(loginThrottle).recordSuccess(ATTEMPT);
  }

  @Test
  @DisplayName("封禁的账号：密码错仍是 401，不暴露封禁")
  void should_return_401_for_banned_user_with_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("哈希排队超时：按取消结算，不计失败，503 原样传出")
  void should_cancel_attempt_when_hashing_is_unavailable() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isSameAs(busy);
    verify(loginThrottle).cancel(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
  }

  @Test
  @DisplayName("取消本身也失败时，原来的错误不被盖掉")
  void should_keep_original_error_when_cancel_fails() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);
    doThrow(new TemporarilyUnavailableException()).when(loginThrottle).cancel(ATTEMPT);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isSameAs(busy);
  }

  @Test
  @DisplayName("字段为 null 时报 REQUIRED，不进失败限制")
  void should_validate_fields_before_throttling() {
    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of(null, null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:REQUIRED"));
    verifyNoInteractions(loginThrottle);
  }

  @Test
  @DisplayName("登录不查密码长度：短密码、1 MB 的密码都按凭据错误处理")
  void should_not_check_password_length_on_login() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "abc")))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(
            () ->
                handler.handle(
                    AuthenticateUserCommand.of("chen.yu@example.com", "x".repeat(1_000_000))))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*AuthenticateUserHandlerTest'`
预期：编译失败。

- [ ] **步骤 3：实现**

`AuthenticateUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.authenticate;

import dev.linqibin.commons.cqrs.Command;

/// 校验前台用户的登录凭据。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
public record AuthenticateUserCommand(String email, String password)
    implements Command<AuthenticateUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 命令
  public static AuthenticateUserCommand of(String email, String password) {
    return new AuthenticateUserCommand(email, password);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticateUserCommand[email=" + email + ", password=***]";
  }
}
```

`AuthenticateUserResult.java`：

```java
package dev.linqibin.patra.identity.app.usecase.authenticate;

/// 凭据校验通过后的结果。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record AuthenticateUserResult(long userId, String email) {

  /// 创建结果。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static AuthenticateUserResult of(long userId, String email) {
    return new AuthenticateUserResult(userId, email);
  }
}
```

`AuthenticateUserHandler.java`：

```java
package dev.linqibin.patra.identity.app.usecase.authenticate;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 校验前台用户的登录凭据。
///
/// 1. 校验字段：邮箱用注册时的规则，密码只查非空和 Unicode 合法。
/// 2. 开始一次尝试：锁定期内或在途已满直接 429，不查库、不做哈希。
/// 3. 查用户和凭据。查不到时拿假哈希做一次校验，耗时和「密码错」一样。
/// 4. 密码错按失败结算（可能触发 429）；密码对按成功结算，封禁的账号返回 403。
/// 5. 中途出错按取消结算，不计失败，原来的错误照常抛出。
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticateUserHandler
    implements CommandHandler<AuthenticateUserCommand, AuthenticateUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;

  /// 校验凭据。
  ///
  /// @param command 命令
  /// @return 用户 ID 和邮箱
  @Override
  public AuthenticateUserResult handle(AuthenticateUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    PlainPassword.validate(command.password()).ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    PlainPassword password = PlainPassword.of(command.password());

    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    Optional<User> user;
    boolean matches;
    try {
      user = users.findByEmail(email);
      matches = verify(user, password);
    } catch (RuntimeException e) {
      cancelQuietly(attempt);
      throw e;
    }
    if (!matches) {
      Optional<Duration> lock = loginThrottle.recordFailure(attempt);
      if (lock.isPresent()) {
        throw new LoginTemporarilyLockedException(lock.get());
      }
      throw new InvalidCredentialsException();
    }
    loginThrottle.recordSuccess(attempt);
    User verified = user.orElseThrow();
    if (verified.isBanned()) {
      throw new UserBannedException();
    }
    return AuthenticateUserResult.of(verified.getId(), verified.getEmail().value());
  }

  /// 校验密码。用户或凭据不存在时拿假哈希校验一次，然后返回 `false`。
  ///
  /// @param user 按邮箱查到的用户
  /// @param password 明文密码
  /// @return 匹配时为 `true`
  private boolean verify(Optional<User> user, PlainPassword password) {
    Optional<UserPasswordCredential> credential =
        user.flatMap(found -> credentials.findByUserId(found.getId()));
    if (credential.isEmpty()) {
      passwordHashing.verifyAgainstDummy(password);
      return false;
    }
    return passwordHashing.matches(password, credential.get().getPasswordHash());
  }

  /// 按取消结算。取消失败只记日志：在途登记会自动过期，不能盖掉原来的错误。
  ///
  /// @param attempt 尝试
  private void cancelQuietly(LoginAttempt attempt) {
    try {
      loginThrottle.cancel(attempt);
    } catch (RuntimeException e) {
      log.warn("取消登录尝试失败，在途登记会自动过期: {}", e.getMessage());
    }
  }
}
```

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*AuthenticateUserHandlerTest'`
预期：全部通过。

顺带确认应用还能启动（新组件都接上了依赖）：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*PatraIdentityApplicationIT'`，预期 `BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-app
git diff --cached --stat
git commit -m "feat(identity): 新增登录凭据校验用例 (PAP-63)"
```

---

### 任务 16：app：封禁与解封

**Files:**

- Create（`patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/ban/` 下）：`BanUserCommand.java`、`BanUserHandler.java`、`UnbanUserCommand.java`、`UnbanUserHandler.java`
- Test: `patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/ban/BanUserHandlerTest.java`、`UnbanUserHandlerTest.java`

**Interfaces:**

- Consumes：任务 9 的 `User`、`UserRepository`；任务 8 的 `UserNotFoundException`；starter-core 提供的 `Clock` Bean。
- Produces：`record BanUserCommand(long userId) implements Command<Void>`、`record UnbanUserCommand(long userId) implements Command<Void>`，都有 `static of(long)`；两个 `@Component` 处理器，`handle()` 上 `@Transactional`。

- [ ] **步骤 1：写失败的测试**

`BanUserHandlerTest.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// BanUserHandler 单元测试。
@DisplayName("BanUserHandler 单元测试")
class BanUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final BanUserHandler handler =
      new BanUserHandler(users, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("封禁正常用户：状态改为封禁，封禁时间取时钟")
  void should_ban_active_user() {
    when(users.findById(42L))
        .thenReturn(Optional.of(User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(saved.getValue().getBannedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("重复封禁保留原来的封禁时间")
  void should_keep_original_ban_time() {
    Instant earlier = Instant.parse("2026-10-01T00:00:00Z");
    when(users.findById(42L))
        .thenReturn(
            Optional.of(User.restore(42L, EMAIL, UserStatus.BANNED, earlier, 1L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getBannedAt()).isEqualTo(earlier);
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_throw_when_user_not_found() {
    when(users.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(BanUserCommand.of(404L)))
        .isInstanceOf(UserNotFoundException.class);
  }
}
```

`UnbanUserHandlerTest.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// UnbanUserHandler 单元测试。
@DisplayName("UnbanUserHandler 单元测试")
class UnbanUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final UnbanUserHandler handler = new UnbanUserHandler(users);

  @Test
  @DisplayName("解封已封禁的用户：回到正常，清掉封禁时间")
  void should_unban_banned_user() {
    when(users.findById(42L))
        .thenReturn(
            Optional.of(
                User.restore(
                    42L,
                    EMAIL,
                    UserStatus.BANNED,
                    Instant.parse("2026-10-06T08:00:00Z"),
                    1L,
                    null,
                    null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(UnbanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getValue().getBannedAt()).isNull();
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_throw_when_user_not_found() {
    when(users.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(UnbanUserCommand.of(404L)))
        .isInstanceOf(UserNotFoundException.class);
  }
}
```

- [ ] **步骤 2：运行，确认失败**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*BanUserHandlerTest'`
预期：编译失败。

- [ ] **步骤 3：实现**

`BanUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.Command;

/// 封禁前台用户。
///
/// @param userId 用户 ID
public record BanUserCommand(long userId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param userId 用户 ID
  /// @return 命令
  public static BanUserCommand of(long userId) {
    return new BanUserCommand(userId);
  }
}
```

`UnbanUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.Command;

/// 解封前台用户。
///
/// @param userId 用户 ID
public record UnbanUserCommand(long userId) implements Command<Void> {

  /// 创建命令。
  ///
  /// @param userId 用户 ID
  /// @return 命令
  public static UnbanUserCommand of(long userId) {
    return new UnbanUserCommand(userId);
  }
}
```

`BanUserHandler.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 封禁前台用户。幂等：已经封禁时保留原来的封禁时间。
///
/// 封禁生效后要删掉该用户的全部会话、结束对应的登录记录，这一步由 PAP-64 加在保存之后。
@Slf4j
@Component
@RequiredArgsConstructor
public class BanUserHandler implements CommandHandler<BanUserCommand, Void> {

  private final UserRepository users;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(BanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    user.ban(clock.instant());
    users.save(user);
    log.info("前台用户已封禁: userId={}", command.userId());
    return null;
  }
}
```

`UnbanUserHandler.java`：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 解封前台用户。幂等：已经正常时什么都不变。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnbanUserHandler implements CommandHandler<UnbanUserCommand, Void> {

  private final UserRepository users;

  /// 解封。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(UnbanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    user.unban();
    users.save(user);
    log.info("前台用户已解封: userId={}", command.userId());
    return null;
  }
}
```

- [ ] **步骤 4：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-app:check`
预期：`BUILD SUCCESSFUL`，三个处理器的测试全部通过。

顺带确认应用还能启动（新组件都接上了依赖）：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*PatraIdentityApplicationIT'`，预期 `BUILD SUCCESSFUL`。

- [ ] **步骤 5：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-app
git diff --cached --stat
git commit -m "feat(identity): 新增封禁与解封用例 (PAP-63)"
```

---

### 任务 17：adapter：两个 Controller 与错误契约

**Files:**

- Create（`patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/` 下）：
  - `auth/AuthController.java`、`auth/request/RegisterRequest.java`、`auth/request/LoginRequest.java`、`auth/response/UserAccountResponse.java`
  - `admin/AdminUserController.java`
- Test（`patra-identity-adapter/src/integrationTest/` 下）：
  - `java/dev/linqibin/patra/identity/IdentityITWebMvcConfig.java`
  - `java/dev/linqibin/patra/identity/adapter/rest/auth/AuthControllerIT.java`
  - `java/dev/linqibin/patra/identity/adapter/rest/admin/AdminUserControllerIT.java`
  - `resources/application.yml`

**Interfaces:**

- Consumes：任务 14、15、16 的命令和结果；任务 3、4 的统一错误格式。
- Produces：`POST /auth/register`（201）、`POST /auth/login`（200），响应体 `UserAccountResponse(Long userId, String email)`；`POST /admin/users/{userId}/ban`、`/unban`（204）。Controller 只把请求转成命令交给 `CommandBus`，不做校验。

- [ ] **步骤 1：写切片测试（先让它失败）**

`resources/application.yml`：

```yaml
linqibin:
  starter:
    core:
      error:
        context-prefix: IDN
```

`IdentityITWebMvcConfig.java`：

```java
package dev.linqibin.patra.identity;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.core.json.autoconfig.JacksonAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/// identity adapter 切片测试的配置根：统一错误格式，加上 starter-core 的 Jackson 配置（Long 输出成字符串）。
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
  CoreErrorAutoConfiguration.class,
  WebErrorAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
public class IdentityITWebMvcConfig {}
```

`AuthControllerIT.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserCommand;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserResult;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserResult;
import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.vo.UserFieldViolations;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// AuthController 切片测试：请求怎么转成命令，领域异常怎么变成统一错误格式。
@WebMvcTest
@Import(AuthController.class)
@AutoConfigureRestTestClient
@DisplayName("AuthController 切片测试")
class AuthControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("注册成功返回 201，userId 是字符串；原始输入原样交给命令")
  void should_register_and_return_201() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenReturn(RegisterUserResult.of(352303128713027974L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", " Chen.Yu@Example.com ", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.userId")
        .isEqualTo("352303128713027974")
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo(" Chen.Yu@Example.com ");
    assertThat(command.getValue().password()).isEqualTo("correct horse battery");
  }

  @Test
  @DisplayName("登录成功返回 200")
  void should_login_and_return_200() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenReturn(AuthenticateUserResult.of(42L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.userId")
        .isEqualTo("42");
  }

  @Test
  @DisplayName("空请求体 {} 交给命令时两个字段都是 null，返回 422")
  void should_pass_nulls_for_empty_body() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(
                List.of(
                    UserFieldViolations.emailRequired(), UserFieldViolations.passwordRequired())));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isNull();
    assertThat(command.getValue().password()).isNull();
  }

  @Test
  @DisplayName("字段不合法：422，errors 带字段名和原因码，不回显原始值（实测点 5）")
  void should_render_field_violations() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.passwordTooCommon())));

    String body =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "chen.yu@example.com", "password", "Password123"))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("detail").asString()).isEqualTo("请求参数不合法");
    JsonNode error = json.get("errors").get(0);
    assertThat(error.get("field").asString()).isEqualTo("password");
    assertThat(error.get("code").asString()).isEqualTo("TOO_COMMON");
    assertThat(error.path("rejectedValue").isNull() || error.path("rejectedValue").isMissingNode())
        .isTrue();
    assertThat(body).doesNotContain("Password123");
  }

  @Test
  @DisplayName("邮箱已注册：409")
  void should_render_conflict() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(new EmailAlreadyRegisteredException());

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0409")
        .jsonPath("$.detail")
        .isEqualTo("该邮箱已注册");
  }

  @Test
  @DisplayName("邮箱或密码错误：401")
  void should_render_unauthorized() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenThrow(new InvalidCredentialsException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "wrong"))
        .exchange()
        .expectStatus()
        .isEqualTo(401)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0401")
        .jsonPath("$.detail")
        .isEqualTo("邮箱或密码错误");
  }

  @Test
  @DisplayName("被暂时限制：429，带 Retry-After 响应头和 retryAfterSeconds")
  void should_render_too_many_requests() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(15)));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "any"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "900")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0429")
        .jsonPath("$.retryAfterSeconds")
        .isEqualTo(900);
  }

  @Test
  @DisplayName("账号已被封禁：403")
  void should_render_forbidden() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenThrow(new UserBannedException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "right"))
        .exchange()
        .expectStatus()
        .isEqualTo(403)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0403")
        .jsonPath("$.detail")
        .isEqualTo("该账号已被封禁");
  }

  @Test
  @DisplayName("依赖暂时不可用：503")
  void should_render_service_unavailable() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenThrow(new TemporarilyUnavailableException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "any"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0503")
        .jsonPath("$.detail")
        .isEqualTo("服务暂时不可用");
  }
}
```

`AdminUserControllerIT.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.ban.BanUserCommand;
import dev.linqibin.patra.identity.app.usecase.ban.UnbanUserCommand;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// AdminUserController 切片测试。
@WebMvcTest
@Import(AdminUserController.class)
@AutoConfigureRestTestClient
@DisplayName("AdminUserController 切片测试")
class AdminUserControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("封禁返回 204")
  void should_ban_and_return_204() {
    restClient.post().uri("/admin/users/42/ban").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(BanUserCommand.of(42L));
  }

  @Test
  @DisplayName("解封返回 204")
  void should_unban_and_return_204() {
    restClient.post().uri("/admin/users/42/unban").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(UnbanUserCommand.of(42L));
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_render_not_found() {
    when(commandBus.handle(any(BanUserCommand.class))).thenThrow(new UserNotFoundException());

    restClient
        .post()
        .uri("/admin/users/404/ban")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0404")
        .jsonPath("$.detail")
        .isEqualTo("用户不存在");
  }
}
```

运行：`./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest`
预期：编译失败，找不到两个 Controller。

- [ ] **步骤 2：实现请求、响应和两个 Controller**

`auth/request/RegisterRequest.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 注册请求体。确认密码只在前端校验，不传给后端。
///
/// @param email 邮箱
/// @param password 密码
public record RegisterRequest(String email, String password) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterRequest[email=" + email + ", password=***]";
  }
}
```

`auth/request/LoginRequest.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 登录请求体。
///
/// @param email 邮箱
/// @param password 密码
public record LoginRequest(String email, String password) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=***]";
  }
}
```

`auth/response/UserAccountResponse.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 注册、登录成功的响应体。`userId` 按全局 Jackson 设置输出成字符串。PAP-64 再加上会话令牌。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record UserAccountResponse(Long userId, String email) {

  /// 创建响应。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 响应
  public static UserAccountResponse of(long userId, String email) {
    return new UserAccountResponse(userId, email);
  }
}
```

`auth/AuthController.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.response.UserAccountResponse;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserCommand;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserResult;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 前台用户的注册与登录。字段校验在应用层做，这里只把请求转成命令。
@Tag(name = "Auth", description = "前台用户的注册与登录")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final CommandBus commandBus;

  /// 注册。成功返回 201。
  ///
  /// @param request 请求体
  /// @return 新用户的 ID 和邮箱
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserAccountResponse register(@RequestBody RegisterRequest request) {
    RegisterUserResult result =
        commandBus.handle(RegisterUserCommand.of(request.email(), request.password()));
    return UserAccountResponse.of(result.userId(), result.email());
  }

  /// 登录（本 Issue 只做到凭据校验通过）。成功返回 200。
  ///
  /// @param request 请求体
  /// @return 用户 ID 和邮箱
  @PostMapping("/login")
  public UserAccountResponse login(@RequestBody LoginRequest request) {
    AuthenticateUserResult result =
        commandBus.handle(AuthenticateUserCommand.of(request.email(), request.password()));
    return UserAccountResponse.of(result.userId(), result.email());
  }
}
```

`admin/AdminUserController.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.admin;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.ban.BanUserCommand;
import dev.linqibin.patra.identity.app.usecase.ban.UnbanUserCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 后台接口：封禁与解封前台用户。
///
/// 本版没有后台账号，接口本身不做身份校验；网关拒绝外部访问 `/admin/**`（PAP-65），只能在内网直连调用。
@Tag(name = "Admin", description = "后台接口：封禁与解封前台用户")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final CommandBus commandBus;

  /// 封禁。幂等，成功返回 204。
  ///
  /// @param userId 用户 ID
  @PostMapping("/{userId}/ban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ban(@PathVariable long userId) {
    commandBus.handle(BanUserCommand.of(userId));
  }

  /// 解封。幂等，成功返回 204。
  ///
  /// @param userId 用户 ID
  @PostMapping("/{userId}/unban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unban(@PathVariable long userId) {
    commandBus.handle(UnbanUserCommand.of(userId));
  }
}
```

- [ ] **步骤 3：运行，确认通过**

运行：`./gradlew :patra-api:patra-identity:patra-identity-adapter:check :patra-api:patra-identity:patra-identity-adapter:integrationTest`
预期：`BUILD SUCCESSFUL`。实测点 5：记下 `rejectedValue` 在 JSON 里是 `null` 还是没有这个键，任务 18 写回 spec。

顺带确认应用还能启动（新组件都接上了依赖）：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*PatraIdentityApplicationIT'`，预期 `BUILD SUCCESSFUL`。

- [ ] **步骤 4：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-adapter
git diff --cached --stat
git commit -m "feat(identity): 新增注册、登录和封禁解封接口 (PAP-63)"
```

---

### 任务 18：boot：端到端测试、README、spec 收尾

**Files:**

- Test: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/AccountFlowIT.java`
- Test: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/RedisUnavailableIT.java`
- Create: `patra-api/patra-identity/README.md`
- Modify: `docs/patra/specs/2026-10-05-identity-account-design.md`

**Interfaces:**

- Consumes：前面所有任务（配置项和三个 Bean 在任务 13 已经接好）。
- Produces：端到端测试、README、spec 第 14 节的实测结果。

- [ ] **步骤 1：写端到端测试**

`AccountFlowIT.java`（锁定时长在测试里改成 2 秒，好验证「到期后恢复」；每个用例用不同的邮箱）：

```java
package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 前台用户账号的完整流程：真实的 PostgreSQL、Redis、Argon2。
@SpringBootTest(properties = "patra.identity.login-throttle.lock-duration=2s")
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("前台用户账号的完整流程")
class AccountFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("邮箱的大小写和首尾空白不影响：注册后用任意大小写登录，重复注册返回 409")
  void should_treat_email_case_and_whitespace_as_same_account() {
    EntityExchangeResult<String> registered = register("  Case.User@Example.COM ", "Correct-Horse-01");

    assertThat(registered.getStatus().value()).isEqualTo(201);
    JsonNode body = JSON.readTree(registered.getResponseBody());
    assertThat(body.get("email").asString()).isEqualTo("case.user@example.com");
    assertThat(body.get("userId").isString()).isTrue();
    assertThat(login("case.user@example.com", "Correct-Horse-01").getStatus().value()).isEqualTo(200);
    assertThat(login("CASE.USER@EXAMPLE.COM", "Correct-Horse-01").getStatus().value()).isEqualTo(200);
    EntityExchangeResult<String> duplicate = register("case.user@example.com", "Another-Pass-02");
    assertThat(duplicate.getStatus().value()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0409");
  }

  @Test
  @DisplayName("密码首尾的空格原样保存：少了空格就是错密码")
  void should_keep_leading_and_trailing_spaces_in_password() {
    register("spaces@example.com", "  padded-secret  ");

    assertThat(login("spaces@example.com", "padded-secret").getStatus().value()).isEqualTo(401);
    assertThat(login("spaces@example.com", "  padded-secret  ").getStatus().value())
        .isEqualTo(200);
  }

  @Test
  @DisplayName("请求体里夹带的 id、status、role 被忽略")
  void should_ignore_extra_fields_in_request_body() {
    EntityExchangeResult<String> result =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "email", "mass@example.com",
                    "password", "Mass-Assign-03",
                    "id", 1,
                    "userId", "1",
                    "status", "BANNED",
                    "role", "ADMIN"))
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getStatus().value()).isEqualTo(201);
    assertThat(JSON.readTree(result.getResponseBody()).get("userId").asString()).isNotEqualTo("1");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM idn_user WHERE email = ?", String.class, "mass@example.com"))
        .isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("邮箱不存在和密码错误的响应体，除时间戳和 traceId 外完全相同")
  void should_return_identical_401_for_unknown_email_and_wrong_password() {
    register("victim@example.com", "Victim-Pass-04");

    EntityExchangeResult<String> unknown = login("nobody-here@example.com", "Whatever-Pass-05");
    EntityExchangeResult<String> wrong = login("victim@example.com", "Wrong-Pass-06");

    assertThat(unknown.getStatus().value()).isEqualTo(401);
    assertThat(wrong.getStatus().value()).isEqualTo(401);
    assertThat(comparable(unknown.getResponseBody())).isEqualTo(comparable(wrong.getResponseBody()));
  }

  @Test
  @DisplayName("连续失败 5 次后被锁，正确密码也返回 429；锁到期后恢复")
  void should_lock_after_five_failures_and_recover() {
    register("locked@example.com", "Locked-Pass-07");
    for (int i = 0; i < 4; i++) {
      assertThat(login("locked@example.com", "Wrong-" + i).getStatus().value()).isEqualTo(401);
    }

    EntityExchangeResult<String> fifth = login("locked@example.com", "Wrong-5");
    assertThat(fifth.getStatus().value()).isEqualTo(429);
    assertThat(fifth.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
    assertThat(JSON.readTree(fifth.getResponseBody()).get("retryAfterSeconds").asLong())
        .isEqualTo(2);
    assertThat(login("locked@example.com", "Locked-Pass-07").getStatus().value()).isEqualTo(429);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(login("locked@example.com", "Locked-Pass-07").getStatus().value())
                    .isEqualTo(200));
  }

  @Test
  @DisplayName("封禁后密码对返回 403、密码错返回 401；解封后能登录；用户不存在返回 404")
  void should_ban_and_unban() {
    String userId =
        JSON.readTree(register("banned@example.com", "Banned-Pass-08").getResponseBody())
            .get("userId")
            .asString();

    restClient.post().uri("/admin/users/" + userId + "/ban").exchange().expectStatus().isNoContent();
    assertThat(login("banned@example.com", "Banned-Pass-08").getStatus().value()).isEqualTo(403);
    assertThat(login("banned@example.com", "Wrong-Pass-08").getStatus().value()).isEqualTo(401);
    restClient.post().uri("/admin/users/" + userId + "/ban").exchange().expectStatus().isNoContent();

    restClient
        .post()
        .uri("/admin/users/" + userId + "/unban")
        .exchange()
        .expectStatus()
        .isNoContent();
    assertThat(login("banned@example.com", "Banned-Pass-08").getStatus().value()).isEqualTo(200);
    restClient.post().uri("/admin/users/1/ban").exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("库里只有哈希；日志和响应里都找不到明文密码")
  void should_never_store_or_echo_plain_password(CapturedOutput output) {
    List<String> passwords =
        List.of(
            "Leak-Probe-Register-09",
            "Leak-Probe-Wrong-10",
            "Leak-Probe-Dup-11",
            "password123",
            "Lp-12");
    List<String> bodies = new ArrayList<>();
    bodies.add(register("leak@example.com", passwords.get(0)).getResponseBody());
    bodies.add(login("leak@example.com", passwords.get(1)).getResponseBody());
    bodies.add(register("leak@example.com", passwords.get(2)).getResponseBody());
    EntityExchangeResult<String> common = register("leak-common@example.com", passwords.get(3));
    EntityExchangeResult<String> tooShort = register("leak-short@example.com", passwords.get(4));
    bodies.add(common.getResponseBody());
    bodies.add(tooShort.getResponseBody());

    assertThat(common.getStatus().value()).isEqualTo(422);
    assertThat(tooShort.getStatus().value()).isEqualTo(422);
    String stored =
        jdbcTemplate.queryForObject(
            "SELECT c.password_hash FROM idn_user_password_credential c "
                + "JOIN idn_user u ON u.id = c.user_id WHERE u.email = ?",
            String.class,
            "leak@example.com");
    assertThat(stored).startsWith("$argon2id$").doesNotContain(passwords.get(0));
    for (String password : passwords) {
      assertThat(bodies).allSatisfy(body -> assertThat(body).doesNotContain(password));
      assertThat(output.getAll()).doesNotContain(password);
    }
  }

  /// 注册。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> register(String email, String password) {
    return post("/auth/register", email, password);
  }

  /// 登录。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> login(String email, String password) {
    return post("/auth/login", email, password);
  }

  /// 发一个带邮箱和密码的 POST。
  ///
  /// @param uri 路径
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> post(String uri, String email, String password) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "password", password))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 去掉每次都不同的字段，留下可比较的部分。
  ///
  /// @param body 响应体
  /// @return 去掉 `timestamp`、`traceId` 之后的 JSON
  private static JsonNode comparable(String body) {
    ObjectNode node = (ObjectNode) JSON.readTree(body);
    node.remove("timestamp");
    node.remove("traceId");
    return node;
  }
}
```

`RedisUnavailableIT.java`：

```java
package dev.linqibin.patra.identity;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// Redis 连不上时，登录返回 503，不在没有限制的情况下放行（实测点 3）。
///
/// 把 Redis 指向一个没人监听的端口，不去停共享的 Redis 测试容器。
@SpringBootTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@DisplayName("Redis 不可用时登录返回 503")
class RedisUnavailableIT {

  @Autowired private RestTestClient restClient;

  /// 让 Redis 指向一个没人监听的端口，超时设短。
  ///
  /// @param registry 动态属性
  @DynamicPropertySource
  static void unreachableRedis(DynamicPropertyRegistry registry) {
    int closedPort = closedPort();
    registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + closedPort);
    registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    registry.add("spring.data.redis.timeout", () -> "500ms");
  }

  @Test
  @DisplayName("注册不依赖 Redis；登录返回 503 和 IDN-0503")
  void should_return_503_when_redis_is_unreachable() {
    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
        .exchange()
        .expectStatus()
        .isCreated();

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0503")
        .jsonPath("$.detail")
        .isEqualTo("服务暂时不可用");
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

- [ ] **步骤 2：运行端到端测试**

运行：`docker info > /dev/null && ./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
预期：`BUILD SUCCESSFUL`。

- `should_ignore_extra_fields_in_request_body` 如果返回 400（Jackson 拒绝未知字段），说明多余字段同样进不来，安全上没问题，但和预期不同：停下来报告用户，不要直接改断言。
- `should_return_identical_401_for_unknown_email_and_wrong_password` 如果两边只差 `path` 以外的某个字段，停下来报告：那个字段可能泄露了邮箱是否存在。

- [ ] **步骤 3：写 README**

`patra-api/patra-identity/README.md`（SHA-256 和行数填任务 12 步骤 1 的输出，Argon2 耗时填任务 11 步骤 4 记下的值）：

````markdown
# patra-identity

前台用户的账号服务：注册、登录时的凭据校验、登录失败限制、封禁与解封。会话令牌、登录记录、登出、「当前用户」接口在 PAP-64。

工程设计：`docs/patra/specs/2026-10-05-identity-account-design.md`。

## 1. 模块

| 模块 | 内容 |
|---|---|
| `patra-identity-domain` | `User`、`UserPasswordCredential` 两个聚合，邮箱、密码值对象，密码规则、失败限制规则，端口，领域异常 |
| `patra-identity-app` | 注册、登录校验、封禁、解封四个处理器，走 CommandBus |
| `patra-identity-infra` | JPA 持久化、Argon2id 哈希、常见密码名单、Redis 失败限制、Flyway 脚本 |
| `patra-identity-adapter` | 前台的 `AuthController`，后台的 `AdminUserController` |
| `patra-identity-boot` | 启动类、配置 |

## 2. 接口

| 路径 | 成功 | 网关规则（PAP-65） |
|---|---|---|
| `POST /auth/register` | 201 `{ userId, email }` | 公开 |
| `POST /auth/login` | 200 `{ userId, email }` | 公开 |
| `POST /admin/users/{userId}/ban` | 204 | 拒绝外部访问 |
| `POST /admin/users/{userId}/unban` | 204 | 拒绝外部访问 |

`/admin/**` 本版没有身份校验，只能在内网直连调用。

## 3. 账号模型

账号分前台用户（`USER`）和后台账号（`STAFF`，以后建）两类，分开建表；密码和用户也分开，各一张表。理由和调研见工程设计第 4 节。

| 表 | 内容 |
|---|---|
| `idn_user` | ID、规范化之后的邮箱（唯一、只能小写）、状态（`ACTIVE` / `BANNED`）、封禁时间 |
| `idn_user_password_credential` | 用户 ID（唯一）、Argon2id 编码串 |

## 4. 密码

- 规则：8 到 64 个码点，不去空格，不能在常见密码名单里。登录只要求非空。
- 哈希：Argon2id，`m=19456 KiB, t=2, p=1`，盐 16 字节、输出 32 字节；哈希和校验前都做 NFKC。开发机上单次约 <填任务 11 记下的耗时>。
- 并发：同时进行的哈希计算最多 4 个，排队超过 3 秒返回 503。
- 常见密码名单：`infra/src/main/resources/password/common-passwords.txt.gz`，取自 Django 6.0 的 `django/contrib/auth/common-passwords.txt.gz`（Royce Williams 整理，<填行数> 行），SHA-256 `<填任务 12 记下的值>`。许可证见同目录的 `common-passwords.LICENSE`（BSD 3-Clause）。比较时对密码做 NFKC、转小写、去掉首尾空白。

## 5. 登录失败限制

- 按「账号类型 + 邮箱」计数，没注册过的邮箱也一样。15 分钟内失败 5 次锁 15 分钟，成功一次清零。
- 只有确认的失败才计数；正在校验的尝试单独登记，「失败数 + 在途数」到上限时返回 429（1 秒），不上锁。
- Redis 键：`idn:login-failures:user:{邮箱 SHA-256}`、`idn:login-inflight:…`、`idn:login-lock:…`，脚本在 `infra/src/main/resources/redis/`。
- 配置：

```yaml
patra:
  identity:
    login-throttle:
      max-failures: 5
      window: 15m
      lock-duration: 15m
      in-flight-ttl: 30s
    password-hashing:
      max-concurrent: 4
      wait-timeout: 3s
```

## 6. 错误码

| 场景 | 状态 | 错误码 |
|---|---|---|
| 字段不合法（`errors[]` 带字段名和原因码） | 422 | `IDN-0422` |
| 邮箱已注册 | 409 | `IDN-0409` |
| 邮箱或密码错误 | 401 | `IDN-0401` |
| 被暂时限制（`Retry-After`、`retryAfterSeconds`） | 429 | `IDN-0429` |
| 账号已被封禁 | 403 | `IDN-0403` |
| 用户不存在（后台接口） | 404 | `IDN-0404` |
| Redis 不可用、哈希排队超时 | 503 | `IDN-0503` |

原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`。

## 7. 本地运行和测试

- dev 配置连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`）。库由 PAP-66 建，建好之前本地起不来。
- 测试全部用 Testcontainers（PostgreSQL 17、Redis 7.0.15），本机要有 Docker：

```bash
./gradlew :patra-api:patra-identity:patra-identity-boot:check :patra-api:patra-identity:patra-identity-boot:integrationTest
```

`check` 不包含集成测试，两个任务都要写。
````

- [ ] **步骤 4：写回 spec**

`docs/patra/specs/2026-10-05-identity-account-design.md`：

1. 开头的 `> **状态**：待评审` 改为 `> **状态**：已实现`。
2. 第 7.2 节第 3、4 条之间补一句：「哈希在事务外做：事务开始时就会占住数据库连接，而哈希可能排队几秒；只有两次保存在事务里。」
3. 第 8.2 节表格「阈值」那一行末尾补：「在途登记的过期时间也可配置：`in-flight-ttl`，默认 30 秒。」
4. 第 14 节标题改为「## 14. 实测结果」，表格改成四列（# / 结论 / 结果 / 对应测试），按实际结果填「成立」或现象。第 1 条附上任务 11 记下的耗时；第 5 条写明 `rejectedValue` 实际是 `null` 还是没有这个键。

- [ ] **步骤 5：全量回归**

日志写到本计划的工作目录（git 忽略的 `.superpowers/sdd/2026-10-06-identity-account/`），只看末尾：

```bash
docker info > /dev/null
LOG_DIR=.superpowers/sdd/2026-10-06-identity-account
mkdir -p "$LOG_DIR"
./gradlew check --continue > "$LOG_DIR/check.log" 2>&1; tail -30 "$LOG_DIR/check.log"
./gradlew integrationTest --continue > "$LOG_DIR/integration-test.log" 2>&1; tail -30 "$LOG_DIR/integration-test.log"
./gradlew dumpModuleGraph && git diff --exit-code patra-infra/cd/module-graph.json
bash patra-infra/cd/detect-changes.test.sh
```

预期：两次 Gradle 都是 `BUILD SUCCESSFUL`；`module-graph.json` 没有变化；`detect-changes.test.sh` 全部 ✓。有失败时先看是不是本 Issue 引起的（linqibin-commons 的改动会影响所有服务），是就修，不是就报告。

- [ ] **步骤 6：提交**

```bash
./gradlew spotlessApply
git add patra-api/patra-identity/patra-identity-boot patra-api/patra-identity/README.md
git diff --cached --stat
git commit -m "test(identity): 补端到端测试和服务 README (PAP-63)"
git add docs/patra/specs/2026-10-05-identity-account-design.md
git diff --cached --stat
git commit -m "docs(accounts): 写回 identity 账号的实测结果 (PAP-63)"
```

---

## Spec 覆盖对照

| spec 章节 | 任务 |
|---|---|
| 第 4 节 账号模型（分表、命名） | 1、7、9、10 |
| 第 5 节 模块 | 7 |
| 第 6.1 节 聚合与值对象 | 8、9 |
| 第 6.2 节 端口 | 9 |
| 第 6.3 节 表 | 7、10 |
| 第 7 节 注册（字段规则、常见名单、哈希） | 8、9、11、12、14、17 |
| 第 8.1 节 登录处理顺序 | 15、17 |
| 第 8.2 节 失败限制 | 13 |
| 第 8.3 节 耗时一致 | 11、15 |
| 第 9 节 封禁与解封 | 16、17 |
| 第 10.1 节 错误码 | 8、17、18 |
| 第 10.2 节 字段错误带原因码 | 2、3 |
| 第 10.3 节 日志分级、参数校验失败不带原始值 | 4、5 |
| 第 10.4 节 剩余等待时间 | 2、3 |
| 第 10.5 节 明文密码的防线 | 4、8、14、15、17、18 |
| 第 11 节 服务骨架与配置 | 7、13 |
| 第 12 节 账号类型改名 | 1 |
| 第 13 节 测试策略 | 各任务的测试；Redis 测试容器在 6 |
| 第 14 节 待实测点 | 11（1）、8（2）、13 和 18（3）、13（4）、17（5），结果在 18 写回 |
| 第 15 节 README | 18 |
| 第 16、17 节 交给其他 Issue、同步改的文档 | 已在 spec 定稿时完成；本计划不涉及 |
