# identity 会话实施计划（PAP-64）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录、注册签发不透明会话令牌并把会话写进 Redis，记登录记录，提供登出、封禁清会话和「当前用户」接口；同时交付 identity 与网关共用的会话存储模块。

**Architecture:** 新模块 `patra-identity-session`（令牌、Redis 键、四段 Lua、`RedisSessionStore`）只给 identity 和网关引。identity 的 domain 通过 `SessionStorePort` 用它；登录记录是 PostgreSQL 里的聚合 `UserLoginRecord`，ID 就是会话 ID；建会话的三步由领域服务 `SessionIssuer` 完成，登录和注册两个处理器各自包事务。identity 接入安全 starter，从网关签的断言取当前用户。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Security 7.0.7（经 `patra-spring-boot-starter-security`）、Spring Data Redis + Lettuce + Lua、Spring Data JPA + Flyway、PostgreSQL 17 与 Redis 7.0.15 的 Testcontainers。

**Spec:** `docs/patra/specs/2026-10-09-identity-session-design.md`

## Global Constraints

- 所有命令在工作树根目录执行：`/Users/linqibin/Projects/Products/patra/.claude/worktrees/v0.8-accounts-api`。本会话不 `cd`，用 `"$W/gradlew" -p "$W" …` 和 `git -C "$W" …`。
- 令牌格式：`patra_user_` + 43 个 `[A-Za-z0-9_-]`（32 字节 `SecureRandom` 的 base64url 不带填充），总长 54；哈希是整个令牌 UTF-8 的 SHA-256 小写十六进制 64 个字符（spec §6.1）。
- Redis 键：`idn:session:user:<哈希>`（HASH，字段 `user_id`、`session_id`、`account_type`、`client_type`、`device_id`（可选）、`created_at`、`last_active_at`、`expires_at`、`idle_timeout_ms`）；`idn:user-sessions:user:<userId>`（HASH，`session_id → 哈希`）。时间是 epoch 毫秒的十进制字符串（spec §6.2）。
- 策略：不活跃 30 天、绝对 180 天、每用户 10 条、续期间隔 60 秒（常量 `RedisSessionStore.RENEW_INTERVAL`）；只在 identity 配置，键名 `patra.identity.session.max-sessions-per-user` 和 `patra.identity.session.lifetime.user.web.{idle,absolute}`（spec §6.3）。
- 脚本只支持单机 Redis；时间由 Java 按注入的 `Clock` 传进 `ARGV`，脚本不调 `TIME`（spec §6.4、§6.5）。
- 错误：`SessionStoreUnavailableException`、`TemporarilyUnavailableException` 特征 `DEP_UNAVAILABLE`（503）；`UserModifiedConcurrentlyException` 特征 `CONFLICT`（409，文案「用户正被其他操作修改，请重试」）；`/auth/me` 的三种情况都是 `AuthenticationRequiredException`（401）。字段原因码只用 `INVALID_FORMAT`、`TOO_LONG`（spec §11）。
- 令牌不进日志：结果、响应、`SessionToken` 的 `toString()` 遮掩；会话模块的日志只有用户 ID 和会话 ID（spec §13）。
- 代码规范：Google Java 格式（spotless）、`///` Javadoc、不用全类名、record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`，集合用 `List.of` / `List.copyOf`。
- 测试规范：单测在 `src/test`，集成测试在 `src/integrationTest`，方法名 snake_case；`./gradlew check` 不含 `integrationTest`，两个都要跑；Testcontainers 要本机 Docker（OrbStack）在线。
- 提交：每个任务至少一次提交，格式 `type(scope): 中文 subject (PAP-64)`，subject 用中文动词起头，结尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；只 `git add` 本任务的路径，提交前看 `git diff --cached --stat`。
- Task 9 和 Task 12 开工前加载 `patra-backend:patra-jpa` 技能；它们写 Entity / Dao / Mapper / Adapter。
- Gradle 的 `-q` 会让 `task-done` 读不到输出，不要加。

## Review Focus

1. Cookie 转请求头时令牌末尾带了换行或空格：解析必须按匿名，不能 500（Task 1 的 `should_reject_tokens_with_surrounding_whitespace`）。
2. `deviceId` 的 128 个字符按码点数，表情算一个（Task 7 的 `should_count_device_id_length_by_code_points`）。
3. 同一用户并发登录超过上限，索引里仍然不超过 10 条（Task 5 的 `should_keep_cap_under_concurrent_logins`）。
4. 绝对过期比不活跃过期先到时，TTL 取绝对过期（Task 4 的 `should_cap_ttl_by_absolute_expiry`）。
5. 拿别的用户 ID 配上真实的会话 ID 登出，不能删掉那条会话（Task 6 的 `should_not_delete_session_of_another_user`）。

---

### Task 1: 会话模块骨架与 `SessionToken`

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/build.gradle.kts`
- Modify: `settings.gradle.kts`（identity 的 `includeAt` 块）
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/SessionToken.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/SessionTokenTest.java`
- Modify: `patra-infra/cd/module-graph.json`（由 `dumpModuleGraph` 重新生成）

**Interfaces:**
- Consumes: `AccountType`（`patra-common-security`，`getCode()` 返回 `user`）。
- Produces: `record SessionToken(String value)`：`static SessionToken generate(AccountType, SecureRandom)`、`static Optional<SessionToken> parse(String)`、`AccountType accountType()`、`String hash()`、`static String prefixOf(AccountType)`（包内可见）。

- [ ] **Step 1: 建模块并注册**

`patra-api/patra-identity/patra-identity-session/build.gradle.kts`：

```kotlin
/**
 * Patra Identity Session
 *
 * identity 与网关共用的会话存储契约：令牌、Redis 键、Lua 脚本、RedisSessionStore。
 * 只给 identity（infra）和网关引；其他服务只认网关签的断言。
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.java-library")
}

dependencies {
    // AccountType / ClientType / CurrentUser
    api(project(":patra-api:patra-common:patra-common-security"))

    // DomainException / StandardErrorTrait
    api(project(":linqibin-commons:linqibin-commons-core"))

    // StringRedisTemplate、RedisScript、Lettuce；版本由 Boot BOM 管理
    api("org.springframework.boot:spring-boot-starter-data-redis")

    // 集成测试的 Redis 容器（integrationTest 继承 test 的依赖）
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
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

`settings.gradle.kts`，在 `includeAt(":patra-api:patra-identity:patra-identity-boot", …)` 这一行后面加：

```kotlin
includeAt(":patra-api:patra-identity:patra-identity-session", "patra-api/patra-identity/patra-identity-session")
```

- [ ] **Step 2: 写失败的测试**

`src/test/java/dev/linqibin/patra/identity/session/SessionTokenTest.java`：

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// SessionToken 单元测试。
@DisplayName("SessionToken 单元测试")
class SessionTokenTest {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  @DisplayName("生成的令牌：前缀 patra_user_，随机部分 43 个 base64url 字符，总长 54")
  void should_generate_prefixed_base64url_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(token.value()).matches("^patra_user_[A-Za-z0-9_-]{43}$").hasSize(54);
    assertThat(token.accountType()).isEqualTo(AccountType.USER);
    assertThat(SessionToken.generate(AccountType.USER, RANDOM).value()).isNotEqualTo(token.value());
  }

  @Test
  @DisplayName("生成的令牌能被解析回来")
  void should_parse_generated_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(SessionToken.parse(token.value())).contains(token);
  }

  @ParameterizedTest(name = "拒绝: [{0}]")
  @ValueSource(
      strings = {
        "",
        "patra_user_",
        "patra_staff_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "PATRA_USER_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+/",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      })
  @DisplayName("格式不对的值解析为空，不抛异常")
  void should_reject_malformed_values(String raw) {
    assertThat(SessionToken.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("null 解析为空")
  void should_reject_null() {
    assertThat(SessionToken.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("首尾带空白或换行的令牌解析为空（Cookie 转头时常见）")
  void should_reject_tokens_with_surrounding_whitespace() {
    String value = SessionToken.generate(AccountType.USER, RANDOM).value();

    assertThat(SessionToken.parse(value + "\n")).isEmpty();
    assertThat(SessionToken.parse(" " + value)).isEmpty();
    assertThat(SessionToken.parse(value + " ")).isEmpty();
  }

  @Test
  @DisplayName("直接构造格式不对的值抛 IllegalArgumentException")
  void should_reject_malformed_value_in_constructor() {
    assertThatThrownBy(() -> new SessionToken("patra_user_short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("哈希是整个令牌 UTF-8 的 SHA-256，小写十六进制 64 位，同一令牌稳定")
  void should_hash_whole_token_with_sha256() throws Exception {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);
    String expected =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(token.value().getBytes(StandardCharsets.UTF_8)));

    assertThat(token.hash()).isEqualTo(expected).matches("^[0-9a-f]{64}$");
    assertThat(token.hash()).isEqualTo(token.hash());
    assertThat(SessionToken.generate(AccountType.USER, RANDOM).hash()).isNotEqualTo(token.hash());
  }

  @Test
  @DisplayName("toString 只有前缀，不带随机部分")
  void should_mask_random_part_in_to_string() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(token.toString()).isEqualTo("SessionToken[patra_user_***]");
    assertThat(token.toString()).doesNotContain(token.value().substring(11));
  }
}
```

- [ ] **Step 3: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*SessionTokenTest*"`
Expected: FAIL，`SessionToken` 找不到（编译错误）。

- [ ] **Step 4: 实现 `SessionToken`**

```java
package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 会话令牌：`<账号类型前缀><43 个 base64url 字符>`，随机部分 32 字节。
///
/// 前缀只说明令牌属于哪类账号，不含用户信息；网关靠它决定查哪个键空间。
/// Redis 里只存 `hash()`，令牌本身不落任何存储，也不进日志。
///
/// @param value 令牌原文
public record SessionToken(String value) {

  /// 随机部分的字节数。
  static final int RANDOM_BYTES = 32;

  /// 各账号类型的前缀。新账号类型在这里加一行。
  private static final Map<AccountType, String> PREFIXES = Map.of(AccountType.USER, "patra_user_");

  /// 随机部分：32 字节的 base64url 不带填充，恰好 43 个字符。
  private static final Pattern RANDOM_PART = Pattern.compile("[A-Za-z0-9_-]{43}");

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  /// 只接受格式合法的值。
  public SessionToken {
    Objects.requireNonNull(value, "value 不能为 null");
    if (accountTypeOf(value).isEmpty()) {
      throw new IllegalArgumentException("会话令牌的格式不合法");
    }
  }

  /// 生成一个新令牌。
  ///
  /// @param accountType 账号类型，决定前缀
  /// @param random 随机源
  /// @return 新令牌
  public static SessionToken generate(AccountType accountType, SecureRandom random) {
    Objects.requireNonNull(random, "random 不能为 null");
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return new SessionToken(prefixOf(accountType) + ENCODER.encodeToString(bytes));
  }

  /// 解析客户端送来的令牌。不合法返回空，调用方按匿名处理；不做任何裁剪。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 令牌；格式不对时为空
  public static Optional<SessionToken> parse(String raw) {
    if (raw == null || accountTypeOf(raw).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SessionToken(raw));
  }

  /// 令牌属于哪类账号，按前缀判断。
  ///
  /// @return 账号类型
  public AccountType accountType() {
    return accountTypeOf(value).orElseThrow();
  }

  /// 整个令牌 UTF-8 字节的 SHA-256，小写十六进制 64 个字符。Redis 键里用它。
  ///
  /// @return 哈希
  public String hash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 账号类型的前缀。
  ///
  /// @param accountType 账号类型
  /// @return 前缀
  static String prefixOf(AccountType accountType) {
    String prefix = PREFIXES.get(Objects.requireNonNull(accountType, "accountType 不能为 null"));
    if (prefix == null) {
      throw new IllegalArgumentException("没有为 " + accountType + " 定义令牌前缀");
    }
    return prefix;
  }

  /// 按前缀和随机部分的格式判断账号类型。
  ///
  /// @param raw 原始值
  /// @return 账号类型；格式不对时为空
  private static Optional<AccountType> accountTypeOf(String raw) {
    for (Map.Entry<AccountType, String> entry : PREFIXES.entrySet()) {
      String prefix = entry.getValue();
      if (raw.startsWith(prefix)
          && RANDOM_PART.matcher(raw.substring(prefix.length())).matches()) {
        return Optional.of(entry.getKey());
      }
    }
    return Optional.empty();
  }

  /// 只输出前缀。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SessionToken[" + prefixOf(accountType()) + "***]";
  }
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*SessionTokenTest*"`
Expected: PASS，8 个用例（参数化算一个方法）。

- [ ] **Step 6: 重新生成模块图并核对 CD 脚本**

Run: `./gradlew dumpModuleGraph`（报 configuration cache 错误就加 `--no-configuration-cache`）
Expected: `patra-infra/cd/module-graph.json` 里出现 `:patra-api:patra-identity:patra-identity-session`，归 `identity` 单元。

Run: `bash patra-infra/cd/detect-changes.test.sh`
Expected: 退出码 0。

- [ ] **Step 7: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:spotlessApply`

```bash
git add settings.gradle.kts patra-api/patra-identity/patra-identity-session patra-infra/cd/module-graph.json
git commit -m "feat(identity): 新建会话模块，定义会话令牌的格式与哈希 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 2: Redis 暂时不可用的判定与异常

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/TransientRedisFailures.java`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/SessionStoreUnavailableException.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/TransientRedisFailuresTest.java`

**Interfaces:**
- Consumes: `DomainException(String message, Throwable cause, StandardErrorTrait trait)`、`StandardErrorTrait.DEP_UNAVAILABLE`（commons-core）。
- Produces: `static boolean TransientRedisFailures.isTransient(RuntimeException)`；`SessionStoreUnavailableException(Throwable cause)`，消息「服务暂时不可用」。

- [ ] **Step 1: 写失败的测试**

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.trait.StandardErrorTrait;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisLoadingException;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.RedisReadOnlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/// TransientRedisFailures 单元测试。
@DisplayName("TransientRedisFailures 单元测试")
class TransientRedisFailuresTest {

  @Test
  @DisplayName("连不上和超时是暂时失败")
  void should_treat_connection_failure_and_timeout_as_transient() {
    assertThat(TransientRedisFailures.isTransient(new RedisConnectionFailureException("refused")))
        .isTrue();
    assertThat(TransientRedisFailures.isTransient(new QueryTimeoutException("timeout"))).isTrue();
  }

  @Test
  @DisplayName("LOADING、READONLY、BUSY、MASTERDOWN 是暂时失败")
  void should_treat_transient_server_states_as_transient() {
    assertThat(wrapped(new RedisLoadingException("LOADING Redis is loading the dataset in memory")))
        .isTrue();
    assertThat(wrapped(new RedisReadOnlyException("READONLY You can't write against a read only replica.")))
        .isTrue();
    assertThat(wrapped(new RedisBusyException("BUSY Redis is busy running a script."))).isTrue();
    assertThat(wrapped(new RedisCommandExecutionException("MASTERDOWN Link with MASTER is down")))
        .isTrue();
  }

  @Test
  @DisplayName("脚本错误、WRONGTYPE、NOAUTH、没有原因的系统异常、其他异常都不是暂时失败")
  void should_not_treat_defects_as_transient() {
    assertThat(wrapped(new RedisNoScriptException("NOSCRIPT No matching script."))).isFalse();
    assertThat(wrapped(new RedisCommandExecutionException("WRONGTYPE Operation against a key")))
        .isFalse();
    assertThat(wrapped(new RedisCommandExecutionException("NOAUTH Authentication required.")))
        .isFalse();
    assertThat(TransientRedisFailures.isTransient(new RedisSystemException("x", null))).isFalse();
    assertThat(TransientRedisFailures.isTransient(new IllegalStateException("x"))).isFalse();
  }

  @Test
  @DisplayName("SessionStoreUnavailableException 带 DEP_UNAVAILABLE 特征和固定文案")
  void should_carry_dep_unavailable_trait() {
    SessionStoreUnavailableException e =
        new SessionStoreUnavailableException(new RedisConnectionFailureException("refused"));

    assertThat(e.getMessage()).isEqualTo("服务暂时不可用");
    assertThat(e.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(e.getCause()).isInstanceOf(RedisConnectionFailureException.class);
  }

  /// Spring Data Redis 把 Lettuce 的命令错误包成 RedisSystemException，原因是 Lettuce 异常本身。
  ///
  /// @param cause Lettuce 异常
  /// @return 判定结果
  private static boolean wrapped(RuntimeException cause) {
    return TransientRedisFailures.isTransient(new RedisSystemException("Error in execution", cause));
  }
}
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*TransientRedisFailuresTest*"`
Expected: FAIL，两个类找不到（`getErrorTraits()` 是 `HasErrorTraits` 接口的方法，`DomainException` 实现了它）。

- [ ] **Step 3: 实现两个类**

`TransientRedisFailures.java`：

```java
package dev.linqibin.patra.identity.session;

import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisLoadingException;
import io.lettuce.core.RedisReadOnlyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

/// 判断一个 Redis 异常是不是「过一会儿再试就好」的暂时失败。
///
/// 暂时失败：连不上、超时，以及 Redis 处于 `LOADING`、`READONLY`、`BUSY`、`MASTERDOWN` 状态。
/// 其余（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，不算暂时，调用方原样抛出成 500。
public final class TransientRedisFailures {

  /// 没有专门异常类的状态，按回复的前缀识别。
  private static final String MASTER_DOWN_PREFIX = "MASTERDOWN";

  /// 工具类，不允许实例化。
  private TransientRedisFailures() {}

  /// 判断是否暂时失败。
  ///
  /// @param failure Spring Data Redis 抛出的异常
  /// @return 暂时失败时为 `true`
  public static boolean isTransient(RuntimeException failure) {
    if (failure instanceof DataAccessResourceFailureException
        || failure instanceof QueryTimeoutException) {
      return true;
    }
    if (!(failure instanceof RedisSystemException)) {
      return false;
    }
    Throwable cause = failure.getCause();
    if (cause instanceof RedisLoadingException
        || cause instanceof RedisReadOnlyException
        || cause instanceof RedisBusyException) {
      return true;
    }
    return cause != null
        && cause.getMessage() != null
        && cause.getMessage().startsWith(MASTER_DOWN_PREFIX);
  }
}
```

`SessionStoreUnavailableException.java`：

```java
package dev.linqibin.patra.identity.session;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 会话存储暂时不可用（Redis 连不上、超时、正在加载等），返回 503。
public final class SessionStoreUnavailableException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public SessionStoreUnavailableException(Throwable cause) {
    super("服务暂时不可用", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*TransientRedisFailuresTest*"`
Expected: PASS，4 个用例。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): 会话模块统一判定 Redis 的暂时不可用 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3: 会话模块的三个记录

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/NewSession.java`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/IssuedSession.java`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/StoredSession.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/NewSessionTest.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/StoredSessionTest.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/test/java/dev/linqibin/patra/identity/session/IssuedSessionTest.java`

**Interfaces:**
- Consumes: Task 1 的 `SessionToken`；`CurrentUser.of(long, long, AccountType, ClientType)`。
- Produces: `record NewSession(long userId, long sessionId, AccountType accountType, ClientType clientType, String deviceId, Instant createdAt, Instant expiresAt, Duration idleTimeout, int maxSessionsPerUser)`（`@Builder`）；`record IssuedSession(SessionToken token, List<Long> replacedSessionIds)`（`of`）；`record StoredSession(long userId, long sessionId, AccountType accountType, ClientType clientType, String deviceId, Instant createdAt, Instant lastActiveAt, Instant expiresAt)`（`@Builder`，`CurrentUser toCurrentUser()`，`Optional<String> device()`）。

- [ ] **Step 1: 写失败的测试**

`NewSessionTest.java`：

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// NewSession 单元测试。
@DisplayName("NewSession 单元测试")
class NewSessionTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

  /// 一个合法的会话，各用例在它上面改一个字段。
  ///
  /// @return 建造器
  private static NewSession.NewSessionBuilder valid() {
    return NewSession.builder()
        .userId(42L)
        .sessionId(7001L)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(NOW)
        .expiresAt(NOW.plus(Duration.ofDays(180)))
        .idleTimeout(Duration.ofDays(30))
        .maxSessionsPerUser(10);
  }

  @Test
  @DisplayName("合法的会话能建出来，空白的设备标识按没有")
  void should_build_valid_session_and_blank_device_as_absent() {
    assertThat(valid().build().deviceId()).isNull();
    assertThat(valid().deviceId("  ").build().deviceId()).isNull();
    assertThat(valid().deviceId("mac-safari").build().deviceId()).isEqualTo("mac-safari");
  }

  @Test
  @DisplayName("非正数的 ID、上限小于 1、过期不晚于创建、不活跃过期不是正数都被拒绝")
  void should_reject_invalid_values() {
    assertThatThrownBy(() -> valid().userId(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().sessionId(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().maxSessionsPerUser(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().expiresAt(NOW).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().idleTimeout(Duration.ZERO).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().accountType(null).build())
        .isInstanceOf(NullPointerException.class);
  }
}
```

`StoredSessionTest.java`：

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// StoredSession 单元测试。
@DisplayName("StoredSession 单元测试")
class StoredSessionTest {

  @Test
  @DisplayName("转成当前用户：四个身份字段原样带过去")
  void should_convert_to_current_user() {
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    StoredSession session =
        StoredSession.builder()
            .userId(42L)
            .sessionId(7001L)
            .accountType(AccountType.USER)
            .clientType(ClientType.WEB)
            .createdAt(now)
            .lastActiveAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();

    assertThat(session.toCurrentUser())
        .isEqualTo(CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB));
    assertThat(session.device()).isEmpty();
  }
}
```

`IssuedSessionTest.java`：

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IssuedSession 单元测试。
@DisplayName("IssuedSession 单元测试")
class IssuedSessionTest {

  @Test
  @DisplayName("被挤掉的 ID 列表是防御性拷贝，toString 不带令牌原文")
  void should_copy_replaced_ids_and_mask_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    List<Long> replaced = new ArrayList<>(List.of(1L, 2L));

    IssuedSession issued = IssuedSession.of(token, replaced);
    replaced.add(3L);

    assertThat(issued.replacedSessionIds()).containsExactly(1L, 2L);
    assertThat(IssuedSession.of(token, null).replacedSessionIds()).isEmpty();
    assertThat(issued.toString()).doesNotContain(token.value()).contains("patra_user_***");
  }
}
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test --tests "*SessionTest*"`
Expected: FAIL，三个记录找不到。

- [ ] **Step 3: 实现三个记录**

`NewSession.java`：

```java
package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 要建的会话：调用方算好时间后交给 `RedisSessionStore.create`。
///
/// `createdAt` 和 `expiresAt` 由调用方按同一个 `now` 算出，Redis 和登录记录的过期时间才一致。
///
/// @param userId 用户 ID，正数
/// @param sessionId 会话 ID，正数，等于登录记录的 ID
/// @param accountType 账号类型
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`；空白按 `null`
/// @param createdAt 创建时间
/// @param expiresAt 绝对过期时间，必须晚于创建时间
/// @param idleTimeout 不活跃过期，正数
/// @param maxSessionsPerUser 每用户的会话上限，至少 1
@Builder
public record NewSession(
    long userId,
    long sessionId,
    AccountType accountType,
    ClientType clientType,
    String deviceId,
    Instant createdAt,
    Instant expiresAt,
    Duration idleTimeout,
    int maxSessionsPerUser) {

  /// 校验各字段。
  public NewSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    Objects.requireNonNull(idleTimeout, "idleTimeout 不能为 null");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt 必须晚于 createdAt");
    }
    if (idleTimeout.isZero() || idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout 必须是正数");
    }
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
    if (deviceId != null && deviceId.isBlank()) {
      deviceId = null;
    }
  }
}
```

`IssuedSession.java`：

```java
package dev.linqibin.patra.identity.session;

import java.util.List;
import java.util.Objects;

/// 建会话的结果：新令牌，以及因超过上限被挤掉的会话 ID。
///
/// @param token 新令牌，只在这里出现一次，交给客户端后不再保存
/// @param replacedSessionIds 被挤掉的会话 ID，可能为空
public record IssuedSession(SessionToken token, List<Long> replacedSessionIds) {

  /// 校验令牌，拷贝列表。
  public IssuedSession {
    Objects.requireNonNull(token, "token 不能为 null");
    replacedSessionIds = replacedSessionIds == null ? List.of() : List.copyOf(replacedSessionIds);
  }

  /// 创建结果。
  ///
  /// @param token 新令牌
  /// @param replacedSessionIds 被挤掉的会话 ID，可以为 `null`
  /// @return 结果
  public static IssuedSession of(SessionToken token, List<Long> replacedSessionIds) {
    return new IssuedSession(token, replacedSessionIds);
  }

  /// 令牌按 `SessionToken.toString()` 遮掩。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "IssuedSession[token=" + token + ", replacedSessionIds=" + replacedSessionIds + "]";
  }
}
```

`StoredSession.java`：

```java
package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/// Redis 里查到的会话。
///
/// @param userId 用户 ID
/// @param sessionId 会话 ID
/// @param accountType 账号类型
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`
/// @param createdAt 创建时间
/// @param lastActiveAt 最后活跃时间，最多落后真实活跃时间一个续期间隔
/// @param expiresAt 绝对过期时间
@Builder
public record StoredSession(
    long userId,
    long sessionId,
    AccountType accountType,
    ClientType clientType,
    String deviceId,
    Instant createdAt,
    Instant lastActiveAt,
    Instant expiresAt) {

  /// 校验各字段。
  public StoredSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    Objects.requireNonNull(lastActiveAt, "lastActiveAt 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }

  /// 转成当前用户，网关据此建认证对象。
  ///
  /// @return 当前用户
  public CurrentUser toCurrentUser() {
    return CurrentUser.of(userId, sessionId, accountType, clientType);
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<String> device() {
    return Optional.ofNullable(deviceId);
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:test`
Expected: PASS，全部用例。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): 会话模块的三个记录：新会话、签发结果、已存会话 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4: `RedisSessionStore` 建会话、查会话并节流续期

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-create.lua`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-touch.lua`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/RedisSessionStore.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/linqibin/patra/identity/session/AdjustableClock.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/linqibin/patra/identity/session/RedisSessionStoreIT.java`

**Interfaces:**
- Consumes: Task 1–3 的类型；`RedisContainerInitializer.getRedisContainer()`（测试 starter）。
- Produces: `RedisSessionStore(StringRedisTemplate redis, Clock clock)`；`static final Duration RENEW_INTERVAL = 60s`；`IssuedSession create(NewSession)`；`Optional<StoredSession> findAndTouch(SessionToken)`；包内静态 `sessionKey(AccountType, String hash)`、`indexKey(AccountType, long userId)`、`sessionKeyPrefix(AccountType)`。本任务的 `session-create.lua` 还不清悬空条目、不限上限，Task 5 补。

- [ ] **Step 1: 写测试用的可调时钟**

`src/integrationTest/java/dev/linqibin/patra/identity/session/AdjustableClock.java`：

```java
package dev.linqibin.patra.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/// 测试用的时钟：从固定时刻开始，手动往前拨。
final class AdjustableClock extends Clock {

  private Instant now;

  /// 从指定时刻开始。
  ///
  /// @param start 起始时刻
  AdjustableClock(Instant start) {
    this.now = start;
  }

  /// 往前拨。
  ///
  /// @param duration 拨多久
  void advance(Duration duration) {
    now = now.plus(duration);
  }

  /// 固定 UTC。
  ///
  /// @return UTC
  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  /// 不支持换时区，原样返回。
  ///
  /// @param zone 忽略
  /// @return 自己
  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  /// 当前时刻。
  ///
  /// @return 当前时刻
  @Override
  public Instant instant() {
    return now;
  }
}
```

- [ ] **Step 2: 写失败的集成测试**

`RedisSessionStoreIT.java`：

```java
package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// RedisSessionStore 集成测试：四段 Lua 在 redis:7.0.15 上的行为。
@DisplayName("RedisSessionStore 集成测试")
class RedisSessionStoreIT {

  static final Instant START = Instant.parse("2026-10-09T08:00:00Z");
  static final Duration IDLE = Duration.ofDays(30);
  static final Duration ABSOLUTE = Duration.ofDays(180);
  /// PTTL 的断言容差：脚本执行和断言之间会过去几毫秒。
  static final long TOLERANCE_MILLIS = 5_000;

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redis;

  final AdjustableClock clock = new AdjustableClock(START);
  final RedisSessionStore store = new RedisSessionStore(redis, clock);

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

  /// 每个用例从空库开始。
  @BeforeEach
  void flush() {
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushAll();
              return null;
            });
  }

  @Test
  @DisplayName("建会话后能按令牌查到；Redis 里只有哈希，字段齐全，两个键的 TTL 各按规则")
  void should_create_session_and_find_it_by_token() {
    IssuedSession issued = store.create(newSession(42L, 7001L).build());
    SessionToken token = issued.token();

    assertThat(token.value()).startsWith("patra_user_");
    assertThat(issued.replacedSessionIds()).isEmpty();
    StoredSession found = store.findAndTouch(token).orElseThrow();
    assertThat(found.userId()).isEqualTo(42L);
    assertThat(found.sessionId()).isEqualTo(7001L);
    assertThat(found.accountType()).isEqualTo(AccountType.USER);
    assertThat(found.clientType()).isEqualTo(ClientType.WEB);
    assertThat(found.device()).isEmpty();
    assertThat(found.createdAt()).isEqualTo(START);
    assertThat(found.lastActiveAt()).isEqualTo(START);
    assertThat(found.expiresAt()).isEqualTo(START.plus(ABSOLUTE));

    String sessionKey = "idn:session:user:" + token.hash();
    Map<Object, Object> stored = redis.opsForHash().entries(sessionKey);
    assertThat(stored)
        .containsEntry("user_id", "42")
        .containsEntry("session_id", "7001")
        .containsEntry("account_type", "user")
        .containsEntry("client_type", "web")
        .containsEntry("created_at", Long.toString(START.toEpochMilli()))
        .containsEntry("last_active_at", Long.toString(START.toEpochMilli()))
        .containsEntry("expires_at", Long.toString(START.plus(ABSOLUTE).toEpochMilli()))
        .containsEntry("idle_timeout_ms", Long.toString(IDLE.toMillis()))
        .doesNotContainKey("device_id");
    assertThat(redis.opsForHash().get("idn:user-sessions:user:42", "7001")).isEqualTo(token.hash());
    assertThat(redis.keys("*")).allSatisfy(key -> assertThat(key).doesNotContain(token.value()));
    assertThat(ttl(sessionKey)).isBetween(IDLE.toMillis() - TOLERANCE_MILLIS, IDLE.toMillis());
    assertThat(ttl("idn:user-sessions:user:42"))
        .isBetween(ABSOLUTE.toMillis() - TOLERANCE_MILLIS, ABSOLUTE.toMillis());
  }

  @Test
  @DisplayName("给了设备标识就存进去并能读回")
  void should_store_device_id_when_given() {
    IssuedSession issued = store.create(newSession(42L, 7001L).deviceId("mac-safari").build());

    assertThat(store.findAndTouch(issued.token()).orElseThrow().device()).contains("mac-safari");
    assertThat(redis.opsForHash().get("idn:session:user:" + issued.token().hash(), "device_id"))
        .isEqualTo("mac-safari");
  }

  @Test
  @DisplayName("距上次写入不满 60 秒：只读，不写回最后活跃时间，TTL 继续走")
  void should_not_write_back_within_renew_interval() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();
    clock.advance(Duration.ofSeconds(59));

    StoredSession found = store.findAndTouch(token).orElseThrow();

    assertThat(found.lastActiveAt()).isEqualTo(START);
    long expected = IDLE.toMillis() - Duration.ofSeconds(59).toMillis();
    assertThat(ttl("idn:session:user:" + token.hash()))
        .isLessThan(IDLE.toMillis() - TOLERANCE_MILLIS)
        .isGreaterThan(expected - TOLERANCE_MILLIS - 60_000);
  }

  @Test
  @DisplayName("满 60 秒：写回最后活跃时间，TTL 重新按不活跃过期算")
  void should_renew_after_interval() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();
    clock.advance(Duration.ofSeconds(60));

    StoredSession found = store.findAndTouch(token).orElseThrow();

    assertThat(found.lastActiveAt()).isEqualTo(START.plusSeconds(60));
    assertThat(redis.opsForHash().get("idn:session:user:" + token.hash(), "last_active_at"))
        .isEqualTo(Long.toString(START.plusSeconds(60).toEpochMilli()));
    assertThat(ttl("idn:session:user:" + token.hash()))
        .isBetween(IDLE.toMillis() - TOLERANCE_MILLIS, IDLE.toMillis());
  }

  @Test
  @DisplayName("绝对过期比不活跃过期先到：建会话和续期的 TTL 都取绝对过期")
  void should_cap_ttl_by_absolute_expiry() {
    Duration oneHour = Duration.ofHours(1);
    SessionToken token =
        store.create(newSession(42L, 7001L).expiresAt(START.plus(oneHour)).build()).token();
    String sessionKey = "idn:session:user:" + token.hash();
    assertThat(ttl(sessionKey)).isBetween(oneHour.toMillis() - TOLERANCE_MILLIS, oneHour.toMillis());

    clock.advance(Duration.ofSeconds(61));
    store.findAndTouch(token).orElseThrow();

    long remaining = oneHour.toMillis() - Duration.ofSeconds(61).toMillis();
    assertThat(ttl(sessionKey)).isBetween(remaining - TOLERANCE_MILLIS, remaining);
  }

  @Test
  @DisplayName("到了绝对过期时间：查不到，键被删掉")
  void should_expire_at_absolute_time() {
    SessionToken token =
        store.create(newSession(42L, 7001L).expiresAt(START.plus(Duration.ofHours(1))).build())
            .token();
    clock.advance(Duration.ofHours(1));

    assertThat(store.findAndTouch(token)).isEmpty();
    assertThat(redis.hasKey("idn:session:user:" + token.hash())).isFalse();
  }

  @Test
  @DisplayName("不存在的令牌查到空")
  void should_return_empty_for_unknown_token() {
    SessionToken unknown = SessionToken.generate(AccountType.USER, new java.security.SecureRandom());

    assertThat(store.findAndTouch(unknown)).isEmpty();
  }

  /// 一个合法会话的建造器，用当前时钟算时间。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 建造器
  NewSession.NewSessionBuilder newSession(long userId, long sessionId) {
    return NewSession.builder()
        .userId(userId)
        .sessionId(sessionId)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(clock.instant())
        .expiresAt(clock.instant().plus(ABSOLUTE))
        .idleTimeout(IDLE)
        .maxSessionsPerUser(10);
  }

  /// 键的剩余毫秒。
  ///
  /// @param key 键
  /// @return 剩余毫秒
  static long ttl(String key) {
    Long millis = redis.getExpire(key, TimeUnit.MILLISECONDS);
    return millis == null ? -2 : millis;
  }
}
```

把 `new java.security.SecureRandom()` 改成 import 后的 `new SecureRandom()`（规范禁止全类名）。

- [ ] **Step 3: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: FAIL，`RedisSessionStore` 找不到。先确认 `docker info` 能通，不然 Testcontainers 直接报找不到 Docker。

- [ ] **Step 4: 写两段 Lua**

`src/main/resources/redis/session-create.lua`（本任务的版本，Task 5 加清理和上限）：

```lua
-- 建一条会话。
-- KEYS[1] 用户的会话索引（HASH：session_id → 令牌哈希）  KEYS[2] 新会话的键（HASH）
-- ARGV[1] 会话键前缀（拼其他会话的键用）  ARGV[2] 新会话的令牌哈希  ARGV[3] 会话 ID
-- ARGV[4] 用户 ID  ARGV[5] 账号类型  ARGV[6] 客户端类型  ARGV[7] 设备标识（空串表示没有）
-- ARGV[8] 当前时间（epoch 毫秒）  ARGV[9] 不活跃过期毫秒  ARGV[10] 绝对过期时间（epoch 毫秒）
-- ARGV[11] 每用户会话上限
-- 返回：被挤掉的会话 ID 列表
local index = KEYS[1]
local sessionKey = KEYS[2]
local now = tonumber(ARGV[8])
local idle = tonumber(ARGV[9])
local expiresAt = tonumber(ARGV[10])
local evicted = {}

-- 写新会话
local fields = {
  'user_id', ARGV[4], 'session_id', ARGV[3], 'account_type', ARGV[5], 'client_type', ARGV[6],
  'created_at', ARGV[8], 'last_active_at', ARGV[8], 'expires_at', ARGV[10], 'idle_timeout_ms', ARGV[9]
}
if ARGV[7] ~= '' then
  fields[#fields + 1] = 'device_id'
  fields[#fields + 1] = ARGV[7]
end
redis.call('HSET', sessionKey, unpack(fields))
redis.call('PEXPIRE', sessionKey, math.min(idle, expiresAt - now))

-- 写索引，TTL 不小于新会话的绝对有效期
redis.call('HSET', index, ARGV[3], ARGV[2])
local absoluteTtl = expiresAt - now
if redis.call('PTTL', index) < absoluteTtl then
  redis.call('PEXPIRE', index, absoluteTtl)
end
return evicted
```

`src/main/resources/redis/session-touch.lua`：

```lua
-- 按令牌哈希读会话，距上次写入满一个续期间隔才续期。
-- KEYS[1] 会话键
-- ARGV[1] 当前时间（epoch 毫秒）  ARGV[2] 续期间隔毫秒
-- 返回：会话的全部字段（HGETALL 的扁平数组）；没有会话或已过绝对期返回空
local fields = redis.call('HGETALL', KEYS[1])
if #fields == 0 then
  return false
end
local session = {}
for i = 1, #fields, 2 do
  session[fields[i]] = fields[i + 1]
end
local now = tonumber(ARGV[1])
local expiresAt = tonumber(session['expires_at'])
if now >= expiresAt then
  redis.call('DEL', KEYS[1])
  return false
end
if now - tonumber(session['last_active_at']) >= tonumber(ARGV[2]) then
  redis.call('HSET', KEYS[1], 'last_active_at', ARGV[1])
  redis.call('PEXPIRE', KEYS[1], math.min(tonumber(session['idle_timeout_ms']), expiresAt - now))
  for i = 1, #fields, 2 do
    if fields[i] == 'last_active_at' then
      fields[i + 1] = ARGV[1]
    end
  end
end
return fields
```

- [ ] **Step 5: 实现 `RedisSessionStore`（建与查）**

```java
package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/// Redis 里的会话存储：identity 建和删，网关查和续期。
///
/// 键 1 `idn:session:<账号类型>:<令牌哈希>` 是一条会话；键 2 `idn:user-sessions:<账号类型>:<用户 ID>`
/// 是该用户全部会话的索引（会话 ID → 令牌哈希）。每个操作是一段 Lua，原子执行。
/// 会话键在脚本里由前缀拼出来，所以只支持单机 Redis。
///
/// Redis 暂时不可用时抛 {@link SessionStoreUnavailableException}；其他 Redis 异常是缺陷，原样抛出。
public final class RedisSessionStore {

  /// 续期间隔：距上次写入满这么久，查会话时才写回最后活跃时间和 TTL。
  public static final Duration RENEW_INTERVAL = Duration.ofSeconds(60);

  static final String SESSION_KEY_PREFIX = "idn:session:";
  static final String INDEX_KEY_PREFIX = "idn:user-sessions:";

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> CREATE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-create.lua"), List.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> TOUCH_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-touch.lua"), List.class);

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 创建存储。
  ///
  /// @param redis Redis 模板
  /// @param clock 判断过期和续期用的时钟
  public RedisSessionStore(StringRedisTemplate redis, Clock clock) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 建一条会话：生成令牌，写键 1 和键 2。
  ///
  /// @param session 要建的会话
  /// @return 新令牌和被挤掉的会话 ID
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public IssuedSession create(NewSession session) {
    Objects.requireNonNull(session, "session 不能为 null");
    SessionToken token = SessionToken.generate(session.accountType(), random);
    List<?> replaced =
        execute(
            () ->
                redis.execute(
                    CREATE_SCRIPT,
                    List.of(
                        indexKey(session.accountType(), session.userId()),
                        sessionKey(session.accountType(), token.hash())),
                    sessionKeyPrefix(session.accountType()),
                    token.hash(),
                    Long.toString(session.sessionId()),
                    Long.toString(session.userId()),
                    session.accountType().getCode(),
                    session.clientType().getCode(),
                    session.deviceId() == null ? "" : session.deviceId(),
                    Long.toString(session.createdAt().toEpochMilli()),
                    Long.toString(session.idleTimeout().toMillis()),
                    Long.toString(session.expiresAt().toEpochMilli()),
                    Integer.toString(session.maxSessionsPerUser())));
    return IssuedSession.of(token, toSessionIds(replaced));
  }

  /// 按令牌查会话，距上次写入满 {@link #RENEW_INTERVAL} 就续期。
  ///
  /// @param token 令牌
  /// @return 会话；不存在或已过绝对期时为空
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public Optional<StoredSession> findAndTouch(SessionToken token) {
    Objects.requireNonNull(token, "token 不能为 null");
    List<?> fields =
        execute(
            () ->
                redis.execute(
                    TOUCH_SCRIPT,
                    List.of(sessionKey(token.accountType(), token.hash())),
                    Long.toString(clock.millis()),
                    Long.toString(RENEW_INTERVAL.toMillis())));
    if (fields == null || fields.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toStoredSession(fields));
  }

  /// 某账号类型的会话键前缀。
  ///
  /// @param accountType 账号类型
  /// @return 前缀，以冒号结尾
  static String sessionKeyPrefix(AccountType accountType) {
    return SESSION_KEY_PREFIX + accountType.getCode() + ":";
  }

  /// 会话键。
  ///
  /// @param accountType 账号类型
  /// @param hash 令牌哈希
  /// @return 键
  static String sessionKey(AccountType accountType, String hash) {
    return sessionKeyPrefix(accountType) + hash;
  }

  /// 用户的会话索引键。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 键
  static String indexKey(AccountType accountType, long userId) {
    return INDEX_KEY_PREFIX + accountType.getCode() + ":" + userId;
  }

  /// 执行 Redis 调用，把暂时失败转成 503 的异常。
  ///
  /// @param call 调用
  /// @param <T> 结果类型
  /// @return 结果
  private static <T> T execute(Supplier<T> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new SessionStoreUnavailableException(e);
      }
      throw e;
    }
  }

  /// 脚本返回的 ID 列表转成 Long。
  ///
  /// @param raw 脚本返回值，可能为 `null`
  /// @return ID 列表
  private static List<Long> toSessionIds(List<?> raw) {
    if (raw == null) {
      return List.of();
    }
    return raw.stream().map(value -> Long.parseLong(String.valueOf(value))).toList();
  }

  /// `HGETALL` 的扁平数组转成会话。
  ///
  /// @param fields 字段名和值交替的列表
  /// @return 会话
  private static StoredSession toStoredSession(List<?> fields) {
    Map<String, String> values = new HashMap<>();
    for (int i = 0; i + 1 < fields.size(); i += 2) {
      values.put(String.valueOf(fields.get(i)), String.valueOf(fields.get(i + 1)));
    }
    return StoredSession.builder()
        .userId(Long.parseLong(values.get("user_id")))
        .sessionId(Long.parseLong(values.get("session_id")))
        .accountType(
            AccountType.fromCode(values.get("account_type"))
                .orElseThrow(() -> new IllegalStateException("会话里的 account_type 不认识")))
        .clientType(
            ClientType.fromCode(values.get("client_type"))
                .orElseThrow(() -> new IllegalStateException("会话里的 client_type 不认识")))
        .deviceId(values.get("device_id"))
        .createdAt(Instant.ofEpochMilli(Long.parseLong(values.get("created_at"))))
        .lastActiveAt(Instant.ofEpochMilli(Long.parseLong(values.get("last_active_at"))))
        .expiresAt(Instant.ofEpochMilli(Long.parseLong(values.get("expires_at"))))
        .build();
  }
}
```

- [ ] **Step 6: 跑集成测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: PASS，7 个用例。两处可能要按实测调整并写回 spec 第 15 节第 2 条：`touch` 返回 `false` 时 `redis.execute` 给的是 `null` 还是空列表（两种代码都处理了）；`HGETALL` 回来的元素是 `String`（`StringRedisTemplate` 的值序列化器）。

- [ ] **Step 7: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): RedisSessionStore 建会话、查会话并节流续期 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 5: 建会话时清悬空条目、超上限挤掉最老的会话

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-create.lua`
- Test: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/linqibin/patra/identity/session/RedisSessionStoreIT.java`（加用例）

**Interfaces:**
- Consumes: Task 4 的 `RedisSessionStore.create`、`newSession(...)` 建造器、`ttl(...)`。
- Produces: `create` 返回的 `replacedSessionIds` 是被挤掉的会话 ID，按「字符串长度，再字典序」从小到大挤。Java 代码不变。

- [ ] **Step 1: 加失败的用例**

在 `RedisSessionStoreIT` 里加（import `java.util.ArrayList`、`java.util.List`、`java.util.Set`、`java.util.concurrent.CountDownLatch`、`java.util.concurrent.ExecutorService`、`java.util.concurrent.Executors`、`java.util.concurrent.Future`）：

```java
  @Test
  @DisplayName("第 11 次登录挤掉最老的一条：返回它的 ID，它的键和索引条目都没了")
  void should_evict_oldest_session_when_over_cap() {
    List<SessionToken> tokens = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tokens.add(store.create(newSession(42L, 7001L + i).build()).token());
    }

    IssuedSession eleventh = store.create(newSession(42L, 7011L).build());

    assertThat(eleventh.replacedSessionIds()).containsExactly(7001L);
    assertThat(redis.hasKey("idn:session:user:" + tokens.get(0).hash())).isFalse();
    assertThat(redis.hasKey("idn:session:user:" + tokens.get(1).hash())).isTrue();
    assertThat(redis.opsForHash().keys("idn:user-sessions:user:42"))
        .hasSize(10)
        .doesNotContain("7001")
        .contains("7002", "7011");
    assertThat(store.findAndTouch(tokens.get(0))).isEmpty();
  }

  @Test
  @DisplayName("建会话时清掉指向不存在会话的悬空条目")
  void should_prune_dangling_index_entries_on_create() {
    SessionToken first = store.create(newSession(42L, 7001L).build()).token();
    store.create(newSession(42L, 7002L).build());
    redis.delete("idn:session:user:" + first.hash());

    IssuedSession third = store.create(newSession(42L, 7003L).build());

    assertThat(third.replacedSessionIds()).isEmpty();
    assertThat(redis.opsForHash().keys("idn:user-sessions:user:42"))
        .containsExactlyInAnyOrder("7002", "7003");
  }

  @Test
  @DisplayName("同一用户并发登录 20 次，索引里仍然只有 10 条、Redis 里只有 10 个会话键")
  void should_keep_cap_under_concurrent_logins() throws Exception {
    int logins = 20;
    ExecutorService pool = Executors.newFixedThreadPool(logins);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<IssuedSession>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < logins; i++) {
        long sessionId = 8001L + i;
        futures.add(
            pool.submit(
                () -> {
                  go.await();
                  return store.create(newSession(42L, sessionId).build());
                }));
      }
      go.countDown();
      int evicted = 0;
      for (Future<IssuedSession> future : futures) {
        evicted += future.get().replacedSessionIds().size();
      }
      assertThat(evicted).isEqualTo(10);
    } finally {
      pool.shutdownNow();
    }

    assertThat(redis.opsForHash().size("idn:user-sessions:user:42")).isEqualTo(10);
    Set<String> sessionKeys = redis.keys("idn:session:user:*");
    assertThat(sessionKeys).hasSize(10);
  }

  @Test
  @DisplayName("会话 ID 先比位数再比字典序：18 位的比 19 位的老，先被挤掉")
  void should_order_sessions_by_id_length_then_lexicographically() {
    long nineteenDigits = 1_000_000_000_000_000_000L;
    long eighteenDigits = 999_999_999_999_999_999L;
    SessionToken newer = store.create(newSession(42L, nineteenDigits).maxSessionsPerUser(2).build()).token();
    SessionToken older = store.create(newSession(42L, eighteenDigits).maxSessionsPerUser(2).build()).token();

    IssuedSession third = store.create(newSession(42L, 7003L).maxSessionsPerUser(2).build());

    assertThat(third.replacedSessionIds()).containsExactly(eighteenDigits);
    assertThat(store.findAndTouch(older)).isEmpty();
    assertThat(store.findAndTouch(newer)).isPresent();
  }
```

- [ ] **Step 2: 跑测试，确认这四个失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: 前四个新用例 FAIL（没有挤掉、悬空条目还在、索引 20 条、挤错了那条），Task 4 的 7 个仍 PASS。

- [ ] **Step 3: 补全 `session-create.lua`**

整个文件换成：

```lua
-- 建一条会话。
-- KEYS[1] 用户的会话索引（HASH：session_id → 令牌哈希）  KEYS[2] 新会话的键（HASH）
-- ARGV[1] 会话键前缀（拼其他会话的键用）  ARGV[2] 新会话的令牌哈希  ARGV[3] 会话 ID
-- ARGV[4] 用户 ID  ARGV[5] 账号类型  ARGV[6] 客户端类型  ARGV[7] 设备标识（空串表示没有）
-- ARGV[8] 当前时间（epoch 毫秒）  ARGV[9] 不活跃过期毫秒  ARGV[10] 绝对过期时间（epoch 毫秒）
-- ARGV[11] 每用户会话上限
-- 返回：被挤掉的会话 ID 列表
local index = KEYS[1]
local sessionKey = KEYS[2]
local prefix = ARGV[1]
local now = tonumber(ARGV[8])
local idle = tonumber(ARGV[9])
local expiresAt = tonumber(ARGV[10])
local limit = tonumber(ARGV[11])

-- 1. 清掉指向不存在会话的悬空条目，留下还活着的
local entries = redis.call('HGETALL', index)
local live = {}
local hashOf = {}
for i = 1, #entries, 2 do
  local sid = entries[i]
  local hash = entries[i + 1]
  if redis.call('EXISTS', prefix .. hash) == 1 then
    live[#live + 1] = sid
    hashOf[sid] = hash
  else
    redis.call('HDEL', index, sid)
  end
end

-- 2. 到上限就按会话 ID 从小到大挤掉多出来的。
--    雪花 ID 按时间递增，最小的最老；先比位数再比字典序，转成 double 会丢精度。
local function older(a, b)
  if #a ~= #b then
    return #a < #b
  end
  return a < b
end
table.sort(live, older)
local evicted = {}
local excess = #live - limit + 1
for i = 1, excess do
  local sid = live[i]
  redis.call('DEL', prefix .. hashOf[sid])
  redis.call('HDEL', index, sid)
  evicted[#evicted + 1] = sid
end

-- 3. 写新会话
local fields = {
  'user_id', ARGV[4], 'session_id', ARGV[3], 'account_type', ARGV[5], 'client_type', ARGV[6],
  'created_at', ARGV[8], 'last_active_at', ARGV[8], 'expires_at', ARGV[10], 'idle_timeout_ms', ARGV[9]
}
if ARGV[7] ~= '' then
  fields[#fields + 1] = 'device_id'
  fields[#fields + 1] = ARGV[7]
end
redis.call('HSET', sessionKey, unpack(fields))
redis.call('PEXPIRE', sessionKey, math.min(idle, expiresAt - now))

-- 4. 写索引，TTL 不小于新会话的绝对有效期
redis.call('HSET', index, ARGV[3], ARGV[2])
local absoluteTtl = expiresAt - now
if redis.call('PTTL', index) < absoluteTtl then
  redis.call('PEXPIRE', index, absoluteTtl)
end
return evicted
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: PASS，11 个用例。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): 建会话时清悬空条目、超上限挤掉最老会话 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6: `RedisSessionStore` 按会话 ID 删除、按用户删全部，不可用转 503

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-delete.lua`
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-delete-all.lua`
- Modify: `patra-api/patra-identity/patra-identity-session/src/main/java/dev/linqibin/patra/identity/session/RedisSessionStore.java`
- Test: `patra-api/patra-identity/patra-identity-session/src/integrationTest/java/dev/linqibin/patra/identity/session/RedisSessionStoreIT.java`（加用例）

**Interfaces:**
- Consumes: Task 4、5。
- Produces: `boolean delete(AccountType, long userId, long sessionId)`；`List<Long> deleteAll(AccountType, long userId)`。四个方法在 Redis 暂时不可用时都抛 `SessionStoreUnavailableException`。

- [ ] **Step 1: 加失败的用例**

在 `RedisSessionStoreIT` 里加（import `java.io.IOException`、`java.io.UncheckedIOException`、`java.net.ServerSocket`、`org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration`、`static org.assertj.core.api.Assertions.assertThatThrownBy`）：

```java
  @Test
  @DisplayName("按用户 ID 和会话 ID 删：返回 true，键和索引条目都没了")
  void should_delete_session_by_user_and_session_id() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();

    assertThat(store.delete(AccountType.USER, 42L, 7001L)).isTrue();

    assertThat(redis.hasKey("idn:session:user:" + token.hash())).isFalse();
    assertThat(redis.opsForHash().hasKey("idn:user-sessions:user:42", "7001")).isFalse();
    assertThat(store.findAndTouch(token)).isEmpty();
  }

  @Test
  @DisplayName("拿别的用户 ID 配真实的会话 ID 删：返回 false，会话还在")
  void should_not_delete_session_of_another_user() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();

    assertThat(store.delete(AccountType.USER, 43L, 7001L)).isFalse();

    assertThat(store.findAndTouch(token)).isPresent();
  }

  @Test
  @DisplayName("会话已经没了再删：返回 false")
  void should_return_false_when_session_is_already_gone() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();
    redis.delete("idn:session:user:" + token.hash());

    assertThat(store.delete(AccountType.USER, 42L, 7001L)).isFalse();
    assertThat(store.delete(AccountType.USER, 42L, 7999L)).isFalse();
    assertThat(redis.opsForHash().hasKey("idn:user-sessions:user:42", "7001")).isFalse();
  }

  @Test
  @DisplayName("删全部：只返回真正删掉的会话 ID，索引键整个没了")
  void should_delete_all_sessions_of_user_and_report_only_existing() {
    SessionToken first = store.create(newSession(42L, 7001L).build()).token();
    store.create(newSession(42L, 7002L).build());
    store.create(newSession(42L, 7003L).build());
    SessionToken other = store.create(newSession(43L, 7004L).build()).token();
    redis.delete("idn:session:user:" + first.hash());

    List<Long> deleted = store.deleteAll(AccountType.USER, 42L);

    assertThat(deleted).containsExactlyInAnyOrder(7002L, 7003L);
    assertThat(redis.hasKey("idn:user-sessions:user:42")).isFalse();
    assertThat(redis.keys("idn:session:user:*")).containsExactly("idn:session:user:" + other.hash());
    assertThat(store.deleteAll(AccountType.USER, 42L)).isEmpty();
  }

  @Test
  @DisplayName("Redis 连不上：四个操作都抛 SessionStoreUnavailableException")
  void should_translate_connection_failure_to_unavailable() {
    LettuceConnectionFactory unreachable =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", closedPort()),
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .shutdownTimeout(Duration.ZERO)
                .build());
    unreachable.afterPropertiesSet();
    unreachable.start();
    try {
      RedisSessionStore broken = new RedisSessionStore(new StringRedisTemplate(unreachable), clock);
      SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());

      assertThatThrownBy(() -> broken.create(newSession(42L, 7001L).build()))
          .isInstanceOf(SessionStoreUnavailableException.class);
      assertThatThrownBy(() -> broken.findAndTouch(token))
          .isInstanceOf(SessionStoreUnavailableException.class);
      assertThatThrownBy(() -> broken.delete(AccountType.USER, 42L, 7001L))
          .isInstanceOf(SessionStoreUnavailableException.class);
      assertThatThrownBy(() -> broken.deleteAll(AccountType.USER, 42L))
          .isInstanceOf(SessionStoreUnavailableException.class);
    } finally {
      unreachable.destroy();
    }
  }

  /// 找一个当前没人监听的端口。
  ///
  /// @return 端口号
  static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: FAIL，`delete` / `deleteAll` 方法不存在。

- [ ] **Step 3: 写两段 Lua**

`session-delete.lua`：

```lua
-- 按会话 ID 删一条会话。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀  ARGV[2] 会话 ID
-- 返回：会话键真正删掉了返回 1，否则 0
local hash = redis.call('HGET', KEYS[1], ARGV[2])
if not hash then
  return 0
end
local deleted = redis.call('DEL', ARGV[1] .. hash)
redis.call('HDEL', KEYS[1], ARGV[2])
return deleted
```

`session-delete-all.lua`：

```lua
-- 删掉一个用户的全部会话。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀
-- 返回：会话键真正删掉了的会话 ID 列表
local entries = redis.call('HGETALL', KEYS[1])
local deleted = {}
for i = 1, #entries, 2 do
  if redis.call('DEL', ARGV[1] .. entries[i + 1]) == 1 then
    deleted[#deleted + 1] = entries[i]
  end
end
redis.call('DEL', KEYS[1])
return deleted
```

- [ ] **Step 4: 给 `RedisSessionStore` 加两个方法**

加两个脚本常量：

```java
  private static final RedisScript<Long> DELETE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-delete.lua"), Long.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> DELETE_ALL_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-delete-all.lua"), List.class);
```

在 `findAndTouch` 后面加：

```java
  /// 按用户 ID 和会话 ID 删一条会话。登出用：identity 手里只有断言里的 `sub` 和 `sid`。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 会话键真正删掉了返回 `true`；会话已经不在或不属于这个用户返回 `false`
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public boolean delete(AccountType accountType, long userId, long sessionId) {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Long deleted =
        execute(
            () ->
                redis.execute(
                    DELETE_SCRIPT,
                    List.of(indexKey(accountType, userId)),
                    sessionKeyPrefix(accountType),
                    Long.toString(sessionId)));
    return deleted != null && deleted == 1;
  }

  /// 删掉一个用户的全部会话。封禁用。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 会话键真正删掉了的会话 ID，调用方据此结束登录记录
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public List<Long> deleteAll(AccountType accountType, long userId) {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    List<?> deleted =
        execute(
            () ->
                redis.execute(
                    DELETE_ALL_SCRIPT,
                    List.of(indexKey(accountType, userId)),
                    sessionKeyPrefix(accountType)));
    return toSessionIds(deleted);
  }
```

- [ ] **Step 5: 跑集成测试和单测，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest --tests "*RedisSessionStoreIT*"`
Expected: PASS，16 个用例。

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:check`
Expected: BUILD SUCCESSFUL（单测、SpotBugs、覆盖率）。

- [ ] **Step 6: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-session
git commit -m "feat(identity): RedisSessionStore 按会话 ID 删除、按用户删全部，不可用转 503 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 7: 登录记录聚合与客户端信息值对象

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/vo/UserFieldViolations.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/vo/DeviceId.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/vo/LoginClient.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/enums/LoginEndReason.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/aggregate/UserLoginRecord.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/model/vo/DeviceIdTest.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/model/vo/LoginClientTest.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/model/aggregate/UserLoginRecordTest.java`

**Interfaces:**
- Consumes: `FieldViolation.of(field, code, message)`、`InvalidUserFieldsException(List<FieldViolation>)`、`ClientType.fromCode`（区分大小写）。
- Produces: `UserFieldViolations.CLIENT_TYPE = "clientType"`、`DEVICE_ID = "deviceId"`、`clientTypeInvalidFormat()`、`deviceIdTooLong()`；`record DeviceId(String value)`：`MAX_LENGTH = 128`、`static Optional<FieldViolation> validate(String)`、`static Optional<DeviceId> of(String)`；`record LoginClient(ClientType clientType, DeviceId deviceId)`：`static List<FieldViolation> validate(String, String)`、`static LoginClient of(String, String)`、`static LoginClient web()`、`Optional<DeviceId> device()`；`enum LoginEndReason { LOGOUT, BANNED, REPLACED }`；`UserLoginRecord`：`static start(long userId, LoginClient, Instant expiresAt)`、`static restore(Long id, Long userId, ClientType, DeviceId, Instant expiresAt, Instant endedAt, LoginEndReason, Long version, Instant createdAt, Instant updatedAt)`、`void end(LoginEndReason, Instant)`、`boolean isEnded()`、`Optional<DeviceId> getDeviceId()`，其余 Lombok getter。

- [ ] **Step 1: 写失败的测试**

`DeviceIdTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// DeviceId 单元测试。
@DisplayName("DeviceId 单元测试")
class DeviceIdTest {

  @Test
  @DisplayName("null 和空白按没有设备标识：不算错，也不建对象")
  void should_treat_blank_as_absent() {
    assertThat(DeviceId.validate(null)).isEmpty();
    assertThat(DeviceId.validate("   ")).isEmpty();
    assertThat(DeviceId.of(null)).isEmpty();
    assertThat(DeviceId.of("  ")).isEmpty();
  }

  @Test
  @DisplayName("去掉首尾空白后保存")
  void should_strip_surrounding_whitespace() {
    assertThat(DeviceId.of("  mac-safari ")).map(DeviceId::value).contains("mac-safari");
  }

  @Test
  @DisplayName("长度按码点数：128 个表情通过，129 个报 TOO_LONG")
  void should_count_device_id_length_by_code_points() {
    String okay = "😀".repeat(128);
    String tooLong = "😀".repeat(129);

    assertThat(DeviceId.validate(okay)).isEmpty();
    assertThat(DeviceId.of(okay)).map(DeviceId::value).contains(okay);
    assertThat(DeviceId.validate(tooLong))
        .get()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("deviceId");
              assertThat(violation.code()).isEqualTo("TOO_LONG");
            });
    assertThatThrownBy(() -> DeviceId.of(tooLong)).isInstanceOf(InvalidUserFieldsException.class);
  }

  @Test
  @DisplayName("直接构造只接受规范化之后的值")
  void should_reject_unnormalized_value_in_constructor() {
    assertThatThrownBy(() -> new DeviceId(" x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DeviceId("")).isInstanceOf(IllegalArgumentException.class);
  }
}
```

`LoginClientTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginClient 单元测试。
@DisplayName("LoginClient 单元测试")
class LoginClientTest {

  @Test
  @DisplayName("客户端类型不传或为空按 web；传了 web（允许首尾空白）也是 web")
  void should_default_client_type_to_web() {
    assertThat(LoginClient.validate(null, null)).isEmpty();
    assertThat(LoginClient.of(null, null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of("", null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of(" web ", null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of(null, null).device()).isEmpty();
    assertThat(LoginClient.web()).isEqualTo(LoginClient.of(null, null));
  }

  @Test
  @DisplayName("不认识的客户端类型报 INVALID_FORMAT；大小写不同也不认识")
  void should_reject_unknown_client_type() {
    assertThat(LoginClient.validate("app", null))
        .singleElement()
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly("clientType", "INVALID_FORMAT");
    assertThat(LoginClient.validate("WEB", null)).hasSize(1);
  }

  @Test
  @DisplayName("两个字段的错误一次报全；of 抛 InvalidUserFieldsException")
  void should_report_both_violations_at_once() {
    String tooLong = "x".repeat(129);

    assertThat(LoginClient.validate("app", tooLong))
        .extracting(FieldViolation::field)
        .containsExactly("clientType", "deviceId");
    assertThatThrownBy(() -> LoginClient.of("app", tooLong))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations()).hasSize(2));
  }

  @Test
  @DisplayName("设备标识原样带过去")
  void should_carry_device_id() {
    LoginClient client = LoginClient.of("web", " mac-safari ");

    assertThat(client.device()).map(DeviceId::value).contains("mac-safari");
  }
}
```

`UserLoginRecordTest.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserLoginRecord 单元测试。
@DisplayName("UserLoginRecord 单元测试")
class UserLoginRecordTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");

  @Test
  @DisplayName("开始一条记录：未结束，带客户端类型、设备标识和绝对过期时间，ID 留给仓储")
  void should_start_unended_record() {
    UserLoginRecord login =
        UserLoginRecord.start(42L, LoginClient.of("web", "mac-safari"), EXPIRES_AT);

    assertThat(login.getId()).isNull();
    assertThat(login.getUserId()).isEqualTo(42L);
    assertThat(login.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(login.getDeviceId()).map(DeviceId::value).contains("mac-safari");
    assertThat(login.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(login.isEnded()).isFalse();
    assertThat(login.getEndedAt()).isNull();
    assertThat(login.getEndReason()).isNull();
  }

  @Test
  @DisplayName("结束一次记下时间和原因；再结束保留第一次")
  void should_keep_first_end() {
    UserLoginRecord login = UserLoginRecord.start(42L, LoginClient.web(), EXPIRES_AT);

    login.end(LoginEndReason.LOGOUT, NOW);
    login.end(LoginEndReason.BANNED, NOW.plusSeconds(60));

    assertThat(login.isEnded()).isTrue();
    assertThat(login.getEndedAt()).isEqualTo(NOW);
    assertThat(login.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
  }

  @Test
  @DisplayName("恢复时结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together_on_restore() {
    assertThatThrownBy(
            () ->
                UserLoginRecord.restore(
                    7001L, 42L, ClientType.WEB, null, EXPIRES_AT, NOW, null, 0L, NOW, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    UserLoginRecord ended =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, EXPIRES_AT, NOW, LoginEndReason.BANNED, 1L, NOW, NOW);
    assertThat(ended.isEnded()).isTrue();
    assertThat(ended.getDeviceId()).isEmpty();
    assertThat(ended.toString()).contains("7001").doesNotContain("mac");
  }
}
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*DeviceIdTest*" --tests "*LoginClientTest*" --tests "*UserLoginRecordTest*"`
Expected: FAIL，四个新类型找不到。

- [ ] **Step 3: 实现**

`UserFieldViolations.java` 加两个字段名常量和两个工厂（放在 `PASSWORD` 常量后面、`passwordTooCommon()` 后面）：

```java
  /// 客户端类型字段名，和请求体一致。
  public static final String CLIENT_TYPE = "clientType";

  /// 设备标识字段名，和请求体一致。
  public static final String DEVICE_ID = "deviceId";
```

```java
  /// 客户端类型不认识。
  ///
  /// @return 字段错误
  public static FieldViolation clientTypeInvalidFormat() {
    return FieldViolation.of(CLIENT_TYPE, INVALID_FORMAT, "不支持的客户端类型");
  }

  /// 设备标识太长。
  ///
  /// @return 字段错误
  public static FieldViolation deviceIdTooLong() {
    return FieldViolation.of(DEVICE_ID, TOO_LONG, "设备标识最长 128 个字符");
  }
```

`DeviceId.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 客户端自报的设备标识，去掉首尾空白后最多 128 个字符（按码点数）。
///
/// @param value 规范化之后的值
public record DeviceId(String value) {

  /// 最多 128 个字符。
  public static final int MAX_LENGTH = 128;

  /// 只接受规范化之后的合法值。
  public DeviceId {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty() || !value.equals(value.strip()) || codePoints(value) > MAX_LENGTH) {
      throw new IllegalArgumentException("设备标识必须是去掉首尾空白、不超过 128 个字符的非空值");
    }
  }

  /// 校验用户输入。`null` 和空白按没有设备标识，不算错。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 不满足的规则；合法或没有时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    if (codePoints(raw.strip()) > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.deviceIdTooLong());
    }
    return Optional.empty();
  }

  /// 从用户输入创建。`null` 和空白返回空。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 设备标识；没有时为空
  /// @throws InvalidUserFieldsException 太长时
  public static Optional<DeviceId> of(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return Optional.of(new DeviceId(raw.strip()));
  }

  /// 码点数。
  ///
  /// @param value 字符串
  /// @return 码点数
  private static int codePoints(String value) {
    return value.codePointCount(0, value.length());
  }
}
```

`LoginClient.java`：

```java
package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 登录或注册时客户端报的自己：客户端类型和可选的设备标识。
///
/// 客户端类型用 `ClientType.code`（区分大小写），不传按网页端。
///
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`
public record LoginClient(ClientType clientType, DeviceId deviceId) {

  /// 客户端类型不能为空。
  public LoginClient {
    Objects.requireNonNull(clientType, "clientType 不能为 null");
  }

  /// 校验两个字段，错误一次报全。
  ///
  /// @param rawClientType 客户端类型的原始输入，可以为 `null`
  /// @param rawDeviceId 设备标识的原始输入，可以为 `null`
  /// @return 字段错误，合法时为空列表
  public static List<FieldViolation> validate(String rawClientType, String rawDeviceId) {
    List<FieldViolation> violations = new ArrayList<>();
    if (rawClientType != null
        && !rawClientType.isBlank()
        && ClientType.fromCode(rawClientType.strip()).isEmpty()) {
      violations.add(UserFieldViolations.clientTypeInvalidFormat());
    }
    DeviceId.validate(rawDeviceId).ifPresent(violations::add);
    return violations;
  }

  /// 从用户输入创建。
  ///
  /// @param rawClientType 客户端类型的原始输入，`null` 或空白按 `web`
  /// @param rawDeviceId 设备标识的原始输入，`null` 或空白按没有
  /// @return 客户端信息
  /// @throws InvalidUserFieldsException 任一字段不合法
  public static LoginClient of(String rawClientType, String rawDeviceId) {
    List<FieldViolation> violations = validate(rawClientType, rawDeviceId);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    ClientType clientType =
        rawClientType == null || rawClientType.isBlank()
            ? ClientType.WEB
            : ClientType.fromCode(rawClientType.strip()).orElseThrow();
    return new LoginClient(clientType, DeviceId.of(rawDeviceId).orElse(null));
  }

  /// 没有设备标识的网页端。
  ///
  /// @return 客户端信息
  public static LoginClient web() {
    return new LoginClient(ClientType.WEB, null);
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<DeviceId> device() {
    return Optional.ofNullable(deviceId);
  }
}
```

`LoginEndReason.java`：

```java
package dev.linqibin.patra.identity.domain.model.enums;

/// 登录记录的结束原因。过期不记：Redis 过期不通知 identity。
public enum LoginEndReason {
  /// 用户自己登出。
  LOGOUT,
  /// 账号被封禁，会话被删。
  BANNED,
  /// 超过每用户的会话上限，被新登录挤掉。
  REPLACED
}
```

`UserLoginRecord.java`：

```java
package dev.linqibin.patra.identity.domain.model.aggregate;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;

/// 一次登录的记录：ID 就是会话 ID。登录时间是审计列 `createdAt`。
///
/// 只用于审计和查看登录设备，不在请求路径上。会话过期不回写这里。
@Getter
public final class UserLoginRecord {

  private Long id;
  private Long userId;
  private ClientType clientType;

  @Getter(AccessLevel.NONE)
  private DeviceId deviceId;

  private Instant expiresAt;
  private Instant endedAt;
  private LoginEndReason endReason;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private UserLoginRecord() {}

  /// 开始一条记录。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param expiresAt 会话的绝对过期时间
  /// @return 未结束的记录
  public static UserLoginRecord start(long userId, LoginClient client, Instant expiresAt) {
    Objects.requireNonNull(client, "client 不能为 null");
    UserLoginRecord login = new UserLoginRecord();
    login.userId = userId;
    login.clientType = client.clientType();
    login.deviceId = client.deviceId();
    login.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    return login;
  }

  /// 从库里恢复。
  ///
  /// @param id 记录 ID
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param deviceId 设备标识，可以为 `null`
  /// @param expiresAt 绝对过期时间
  /// @param endedAt 结束时间，未结束时为 `null`
  /// @param endReason 结束原因，未结束时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间，也是登录时间
  /// @param updatedAt 更新时间
  /// @return 记录
  public static UserLoginRecord restore(
      Long id,
      Long userId,
      ClientType clientType,
      DeviceId deviceId,
      Instant expiresAt,
      Instant endedAt,
      LoginEndReason endReason,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    if ((endedAt == null) != (endReason == null)) {
      throw new IllegalArgumentException("结束时间和结束原因要么都有，要么都没有");
    }
    UserLoginRecord login = new UserLoginRecord();
    login.id = Objects.requireNonNull(id, "id 不能为 null");
    login.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    login.clientType = Objects.requireNonNull(clientType, "clientType 不能为 null");
    login.deviceId = deviceId;
    login.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    login.endedAt = endedAt;
    login.endReason = endReason;
    login.version = version;
    login.createdAt = createdAt;
    login.updatedAt = updatedAt;
    return login;
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<DeviceId> getDeviceId() {
    return Optional.ofNullable(deviceId);
  }

  /// 结束这条记录。已经结束时什么都不做，保留第一次的时间和原因。
  ///
  /// @param reason 结束原因
  /// @param now 当前时间
  public void end(LoginEndReason reason, Instant now) {
    Objects.requireNonNull(reason, "reason 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    if (endedAt != null) {
      return;
    }
    endedAt = now;
    endReason = reason;
  }

  /// 是否已结束。
  ///
  /// @return 已结束时为 `true`
  public boolean isEnded() {
    return endedAt != null;
  }

  /// 不输出设备标识。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserLoginRecord[id="
        + id
        + ", userId="
        + userId
        + ", clientType="
        + clientType
        + ", endReason="
        + endReason
        + "]";
  }
}
```

- [ ] **Step 4: 跑 domain 的全部测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:check`
Expected: BUILD SUCCESSFUL，新旧用例全绿，领域层纯净性检查通过。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-domain
git commit -m "feat(identity): 登录记录聚合与客户端信息值对象 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8: 有效期策略、会话端口与签发领域服务

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/policy/SessionLifetime.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/policy/SessionLifetimePolicy.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/port/session/NewUserSession.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/port/session/IssuedUserSession.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/port/session/SessionStorePort.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/port/repository/UserLoginRecordRepository.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/service/SessionIssuer.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/policy/SessionLifetimePolicyTest.java`
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/service/SessionIssuerTest.java`

**Interfaces:**
- Consumes: Task 7 的 `UserLoginRecord`、`LoginClient`、`LoginEndReason`；`AccountType.USER`。
- Produces: `record SessionLifetime(Duration idle, Duration absolute)`（`of`）；`record SessionLifetimePolicy(Map<ClientType, SessionLifetime> lifetimes, int maxSessionsPerUser)`（`of`、`lifetimeFor(ClientType)`）；`record NewUserSession(long userId, long sessionId, AccountType accountType, LoginClient client, Instant now, SessionLifetime lifetime, int maxSessionsPerUser)`（`@Builder`，`Instant expiresAt()`）；`record IssuedUserSession(String token, List<Long> replacedSessionIds)`（`of`）；`interface SessionStorePort { IssuedUserSession issue(NewUserSession); boolean revoke(AccountType, long userId, long sessionId); List<Long> revokeAll(AccountType, long userId); }`；`interface UserLoginRecordRepository { UserLoginRecord save(UserLoginRecord); Optional<UserLoginRecord> findById(long); }`；`SessionIssuer(UserLoginRecordRepository, SessionStorePort, SessionLifetimePolicy, Clock)`，`IssuedUserSession issue(long userId, LoginClient client)`。

- [ ] **Step 1: 写失败的测试**

`SessionLifetimePolicyTest.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SessionLifetimePolicy 与 SessionLifetime 单元测试。
@DisplayName("SessionLifetimePolicy 单元测试")
class SessionLifetimePolicyTest {

  private static final SessionLifetime WEB =
      SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180));

  @Test
  @DisplayName("有效期必须是正数，且不活跃过期不长于绝对过期")
  void should_validate_lifetime() {
    assertThat(WEB.idle()).isEqualTo(Duration.ofDays(30));
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ZERO, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ofDays(1), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ofDays(2), Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SessionLifetime.of(Duration.ofDays(1), Duration.ofDays(1))).isNotNull();
  }

  @Test
  @DisplayName("按客户端类型取有效期；没配的类型抛 IllegalStateException")
  void should_look_up_lifetime_by_client_type() {
    SessionLifetimePolicy policy = SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 10);

    assertThat(policy.lifetimeFor(ClientType.WEB)).isEqualTo(WEB);
    assertThat(policy.maxSessionsPerUser()).isEqualTo(10);
  }

  @Test
  @DisplayName("至少一行有效期，上限至少 1")
  void should_reject_empty_policy_or_bad_cap() {
    assertThatThrownBy(() -> SessionLifetimePolicy.of(Map.of(), 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

`SessionIssuerTest.java`：

```java
package dev.linqibin.patra.identity.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// SessionIssuer 单元测试。
@DisplayName("SessionIssuer 单元测试")
class SessionIssuerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final SessionLifetime WEB =
      SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180));
  private static final SessionLifetimePolicy POLICY =
      SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 10);

  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final SessionIssuer issuer =
      new SessionIssuer(records, sessions, POLICY, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("先存登录记录拿 ID，再用它建会话，返回令牌")
  void should_save_record_then_issue_session() {
    when(records.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(sessions.issue(any())).thenReturn(IssuedUserSession.of("patra_user_x", List.of()));

    IssuedUserSession issued = issuer.issue(42L, LoginClient.of("web", "mac-safari"));

    assertThat(issued.token()).isEqualTo("patra_user_x");
    ArgumentCaptor<UserLoginRecord> saved = ArgumentCaptor.forClass(UserLoginRecord.class);
    ArgumentCaptor<NewUserSession> requested = ArgumentCaptor.forClass(NewUserSession.class);
    InOrder order = inOrder(records, sessions);
    order.verify(records).save(saved.capture());
    order.verify(sessions).issue(requested.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(42L);
    assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    NewUserSession session = requested.getValue();
    assertThat(session.userId()).isEqualTo(42L);
    assertThat(session.sessionId()).isEqualTo(7001L);
    assertThat(session.accountType()).isEqualTo(AccountType.USER);
    assertThat(session.client().device()).isPresent();
    assertThat(session.now()).isEqualTo(NOW);
    assertThat(session.lifetime()).isEqualTo(WEB);
    assertThat(session.maxSessionsPerUser()).isEqualTo(10);
    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
  }

  @Test
  @DisplayName("被挤掉的会话：对应记录标成 REPLACED 并保存；找不到的跳过")
  void should_end_replaced_records() {
    when(records.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(sessions.issue(any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of(6001L, 6002L)));
    UserLoginRecord replaced =
        UserLoginRecord.restore(
            6001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(6001L)).thenReturn(Optional.of(replaced));
    when(records.findById(6002L)).thenReturn(Optional.empty());

    issuer.issue(42L, LoginClient.web());

    assertThat(replaced.getEndReason()).isEqualTo(LoginEndReason.REPLACED);
    assertThat(replaced.getEndedAt()).isEqualTo(NOW);
    verify(records).save(replaced);
  }

  @Test
  @DisplayName("客户端类型没配有效期：抛 IllegalStateException，什么都不写")
  void should_fail_before_writing_when_lifetime_is_missing() {
    SessionLifetimePolicy policy = mock(SessionLifetimePolicy.class);
    when(policy.lifetimeFor(ClientType.WEB)).thenThrow(new IllegalStateException("没配"));
    SessionIssuer broken =
        new SessionIssuer(records, sessions, policy, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> broken.issue(42L, LoginClient.web()))
        .isInstanceOf(IllegalStateException.class);
    verify(records, never()).save(any());
    verify(sessions, never()).issue(any());
  }

  /// 模拟仓储分配 ID：按原记录的内容恢复一条带 ID 的。
  ///
  /// @param login 未保存的记录
  /// @param id 分配的 ID
  /// @return 带 ID 的记录
  private static UserLoginRecord withId(UserLoginRecord login, long id) {
    return UserLoginRecord.restore(
        id,
        login.getUserId(),
        login.getClientType(),
        login.getDeviceId().orElse(null),
        login.getExpiresAt(),
        login.getEndedAt(),
        login.getEndReason(),
        0L,
        NOW,
        NOW);
  }
}
```

第三个用例 mock 了 record 类型的策略：本版只有一个客户端类型，缺行只能这样触发；Mockito 5 默认的 inline mock maker 能模拟 final 类。

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*SessionLifetimePolicyTest*" --tests "*SessionIssuerTest*"`
Expected: FAIL，类型找不到。

- [ ] **Step 3: 实现**

`SessionLifetime.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import java.time.Duration;
import java.util.Objects;

/// 一种客户端的会话有效期：不活跃过期和绝对过期。
///
/// @param idle 不活跃多久过期，正数
/// @param absolute 从登录起多久必定过期，不短于 `idle`
public record SessionLifetime(Duration idle, Duration absolute) {

  /// 校验两个时长。
  public SessionLifetime {
    Objects.requireNonNull(idle, "idle 不能为 null");
    Objects.requireNonNull(absolute, "absolute 不能为 null");
    if (idle.isZero() || idle.isNegative()) {
      throw new IllegalArgumentException("idle 必须是正数");
    }
    if (absolute.isZero() || absolute.isNegative()) {
      throw new IllegalArgumentException("absolute 必须是正数");
    }
    if (idle.compareTo(absolute) > 0) {
      throw new IllegalArgumentException("idle 不能长于 absolute");
    }
  }

  /// 创建有效期。
  ///
  /// @param idle 不活跃过期
  /// @param absolute 绝对过期
  /// @return 有效期
  public static SessionLifetime of(Duration idle, Duration absolute) {
    return new SessionLifetime(idle, absolute);
  }
}
```

`SessionLifetimePolicy.java`：

```java
package dev.linqibin.patra.identity.domain.policy;

import dev.linqibin.patra.common.security.ClientType;
import java.util.Map;
import java.util.Objects;

/// 会话策略：每种客户端的有效期，以及每用户的会话上限。只在 identity 配置，网关不配。
///
/// @param lifetimes 按客户端类型的有效期，至少一行
/// @param maxSessionsPerUser 每用户的会话上限，至少 1
public record SessionLifetimePolicy(
    Map<ClientType, SessionLifetime> lifetimes, int maxSessionsPerUser) {

  /// 拷贝并校验。
  public SessionLifetimePolicy {
    lifetimes = Map.copyOf(Objects.requireNonNull(lifetimes, "lifetimes 不能为 null"));
    if (lifetimes.isEmpty()) {
      throw new IllegalArgumentException("至少要配一种客户端类型的会话有效期");
    }
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
  }

  /// 创建策略。
  ///
  /// @param lifetimes 按客户端类型的有效期
  /// @param maxSessionsPerUser 每用户的会话上限
  /// @return 策略
  public static SessionLifetimePolicy of(
      Map<ClientType, SessionLifetime> lifetimes, int maxSessionsPerUser) {
    return new SessionLifetimePolicy(lifetimes, maxSessionsPerUser);
  }

  /// 某种客户端的有效期。
  ///
  /// @param clientType 客户端类型
  /// @return 有效期
  /// @throws IllegalStateException 没有为这种客户端配置时；启动校验保证本版不会发生
  public SessionLifetime lifetimeFor(ClientType clientType) {
    SessionLifetime lifetime = lifetimes.get(Objects.requireNonNull(clientType, "clientType 不能为 null"));
    if (lifetime == null) {
      throw new IllegalStateException("没有为 " + clientType + " 配置会话有效期");
    }
    return lifetime;
  }
}
```

`NewUserSession.java`：

```java
package dev.linqibin.patra.identity.domain.port.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 要签发的会话：domain 交给会话存储端口的输入。
///
/// @param userId 用户 ID，正数
/// @param sessionId 会话 ID，正数，等于登录记录的 ID
/// @param accountType 账号类型
/// @param client 客户端信息
/// @param now 签发时间，Redis 和登录记录都按它算过期
/// @param lifetime 有效期
/// @param maxSessionsPerUser 每用户的会话上限
@Builder
public record NewUserSession(
    long userId,
    long sessionId,
    AccountType accountType,
    LoginClient client,
    Instant now,
    SessionLifetime lifetime,
    int maxSessionsPerUser) {

  /// 校验各字段。
  public NewUserSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(client, "client 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    Objects.requireNonNull(lifetime, "lifetime 不能为 null");
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
  }

  /// 绝对过期时间。
  ///
  /// @return `now + lifetime.absolute()`
  public Instant expiresAt() {
    return now.plus(lifetime.absolute());
  }
}
```

`IssuedUserSession.java`：

```java
package dev.linqibin.patra.identity.domain.port.session;

import java.util.List;
import java.util.Objects;

/// 签发结果：令牌原文和被挤掉的会话 ID。
///
/// @param token 令牌原文，只交给客户端，不保存
/// @param replacedSessionIds 被挤掉的会话 ID，可能为空
public record IssuedUserSession(String token, List<Long> replacedSessionIds) {

  /// 校验令牌，拷贝列表。
  public IssuedUserSession {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token 不能为空");
    }
    replacedSessionIds = replacedSessionIds == null ? List.of() : List.copyOf(replacedSessionIds);
  }

  /// 创建结果。
  ///
  /// @param token 令牌原文
  /// @param replacedSessionIds 被挤掉的会话 ID，可以为 `null`
  /// @return 结果
  public static IssuedUserSession of(String token, List<Long> replacedSessionIds) {
    return new IssuedUserSession(token, replacedSessionIds);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "IssuedUserSession[token=***, replacedSessionIds=" + replacedSessionIds + "]";
  }
}
```

`SessionStorePort.java`：

```java
package dev.linqibin.patra.identity.domain.port.session;

import dev.linqibin.patra.common.security.AccountType;
import java.util.List;

/// 会话存储：建会话、按会话 ID 删、按用户删全部。
///
/// Redis 暂时不可用时，三个方法都抛带 `DEP_UNAVAILABLE` 特征的异常（由适配器原样传出，映射成 503）。
public interface SessionStorePort {

  /// 建一条会话，返回令牌。超过上限时挤掉最老的，返回被挤掉的会话 ID。
  ///
  /// @param session 要签发的会话
  /// @return 签发结果
  IssuedUserSession issue(NewUserSession session);

  /// 按用户 ID 和会话 ID 删一条会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 会话真的被删掉时为 `true`；已经不在或不属于这个用户时为 `false`
  boolean revoke(AccountType accountType, long userId, long sessionId);

  /// 删掉一个用户的全部会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 真的被删掉的会话 ID
  List<Long> revokeAll(AccountType accountType, long userId);
}
```

`UserLoginRecordRepository.java`：

```java
package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import java.util.Optional;

/// 登录记录仓储。
public interface UserLoginRecordRepository {

  /// 保存。新记录分配雪花 ID。
  ///
  /// @param login 记录
  /// @return 保存后的记录，带 ID 和版本
  UserLoginRecord save(UserLoginRecord login);

  /// 按 ID 查，ID 就是会话 ID。
  ///
  /// @param id 记录 ID
  /// @return 记录
  Optional<UserLoginRecord> findById(long id);
}
```

`SessionIssuer.java`：

```java
package dev.linqibin.patra.identity.domain.service;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// 给前台用户建会话：存登录记录拿 ID，用它建 Redis 会话，收尾被挤掉的记录。
///
/// 不管事务：登录和注册的处理器各自包事务，Redis 写在事务里，失败就回滚记录。
public final class SessionIssuer {

  private final UserLoginRecordRepository records;
  private final SessionStorePort sessions;
  private final SessionLifetimePolicy policy;
  private final Clock clock;

  /// 创建领域服务。
  ///
  /// @param records 登录记录仓储
  /// @param sessions 会话存储端口
  /// @param policy 会话策略
  /// @param clock 时钟
  public SessionIssuer(
      UserLoginRecordRepository records,
      SessionStorePort sessions,
      SessionLifetimePolicy policy,
      Clock clock) {
    this.records = Objects.requireNonNull(records, "records 不能为 null");
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 建会话。
  ///
  /// 1. 按客户端类型取有效期；2. 存登录记录拿到 ID；3. 以记录 ID 为会话 ID 建 Redis 会话；
  /// 4. 被挤掉的会话对应的记录标成 `REPLACED`。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @return 令牌和被挤掉的会话 ID
  public IssuedUserSession issue(long userId, LoginClient client) {
    Objects.requireNonNull(client, "client 不能为 null");
    Instant now = clock.instant();
    SessionLifetime lifetime = policy.lifetimeFor(client.clientType());
    UserLoginRecord login =
        records.save(UserLoginRecord.start(userId, client, now.plus(lifetime.absolute())));
    IssuedUserSession issued =
        sessions.issue(
            NewUserSession.builder()
                .userId(userId)
                .sessionId(login.getId())
                .accountType(AccountType.USER)
                .client(client)
                .now(now)
                .lifetime(lifetime)
                .maxSessionsPerUser(policy.maxSessionsPerUser())
                .build());
    for (Long replaced : issued.replacedSessionIds()) {
      records
          .findById(replaced)
          .ifPresent(
              old -> {
                old.end(LoginEndReason.REPLACED, now);
                records.save(old);
              });
    }
    return issued;
  }
}
```

- [ ] **Step 4: 跑 domain 的全部测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:check`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-domain
git commit -m "feat(identity): 有效期策略、会话端口与签发领域服务 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 9: 登录记录建表与仓储

开工前加载 `patra-backend:patra-jpa` 技能。

**Files:**
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/migration/V2__add_user_login_record.sql`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/entity/UserLoginRecordEntity.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/dao/UserLoginRecordDao.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/converter/mapper/UserLoginRecordJpaMapper.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/UserLoginRecordRepositoryAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/linqibin/patra/identity/infra/adapter/persistence/UserLoginRecordRepositoryAdapterIT.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/PatraIdentityApplicationIT.java`（建表数从两张改三张）

**Interfaces:**
- Consumes: Task 7、8 的 `UserLoginRecord`、`UserLoginRecordRepository`、`DeviceId`、`LoginEndReason`；`BaseJpaEntity`、`SnowflakeIdGenerator.getId()`、`UserRepositoryAdapter`（测试里建用户）。
- Produces: `UserLoginRecordRepositoryAdapter implements UserLoginRecordRepository`（`@Repository`）。

- [ ] **Step 1: 写失败的集成测试**

`UserLoginRecordRepositoryAdapterIT.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserLoginRecordRepositoryAdapter 集成测试：保存、查询、结束，以及 V2 的检查约束。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  UserRepositoryAdapter.class,
  UserLoginRecordRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserLoginRecordRepositoryAdapter 集成测试")
class UserLoginRecordRepositoryAdapterIT {

  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserLoginRecordRepositoryAdapter records;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("保存时分配雪花 ID，能按 ID 查回，登录时间取审计列")
  void should_assign_id_and_find_saved_record() {
    User user = users.save(User.register(EmailAddress.of("login.record@example.com")));

    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(user.getId(), LoginClient.of("web", "mac-safari"), EXPIRES_AT));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getVersion()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getUserId()).isEqualTo(user.getId());
    assertThat(reloaded.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(reloaded.getDeviceId()).map(DeviceId::value).contains("mac-safari");
    assertThat(reloaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(reloaded.isEnded()).isFalse();
    assertThat(records.findById(1L)).isEmpty();
  }

  @Test
  @DisplayName("结束后保存，查回来有结束时间和原因，版本加一")
  void should_persist_end() {
    User user = users.save(User.register(EmailAddress.of("end.record@example.com")));
    UserLoginRecord saved =
        records.save(UserLoginRecord.start(user.getId(), LoginClient.web(), EXPIRES_AT));
    Instant endedAt = Instant.parse("2026-10-09T09:00:00Z");

    saved.end(LoginEndReason.LOGOUT, endedAt);
    UserLoginRecord ended = records.save(saved);

    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.isEnded()).isTrue();
    assertThat(reloaded.getEndedAt()).isEqualTo(endedAt);
    assertThat(reloaded.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
    assertThat(reloaded.getDeviceId()).isEmpty();
    assertThat(ended.getVersion()).isEqualTo(saved.getVersion() + 1);
  }

  @Test
  @DisplayName("检查约束：客户端类型只能是 WEB")
  void should_reject_unknown_client_type() {
    User user = users.save(User.register(EmailAddress.of("ck.client@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "APP", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_client_type");
  }

  @Test
  @DisplayName("检查约束：结束原因只能是 LOGOUT、BANNED、REPLACED")
  void should_reject_unknown_end_reason() {
    User user = users.save(User.register(EmailAddress.of("ck.reason@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, "EXPIRED"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_end_reason");
  }

  @Test
  @DisplayName("检查约束：结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together() {
    User user = users.save(User.register(EmailAddress.of("ck.pair@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_ended");
  }

  /// 绕过聚合直接插一行，用来触发检查约束。
  ///
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param endedAt 结束时间，可为 `null`
  /// @param endReason 结束原因，可为 `null`
  private void insertRaw(long userId, String clientType, Instant endedAt, String endReason) {
    jdbcTemplate.update(
        "INSERT INTO idn_user_login_record (id, user_id, client_type, expires_at, ended_at, end_reason)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        dev.linqibin.starter.jpa.id.SnowflakeIdGenerator.getId(),
        userId,
        clientType,
        java.sql.Timestamp.from(EXPIRES_AT),
        endedAt == null ? null : java.sql.Timestamp.from(endedAt),
        endReason);
  }
}
```

把两处全类名改成 import（`SnowflakeIdGenerator`、`java.sql.Timestamp`）。

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserLoginRecordRepositoryAdapterIT*"`
Expected: FAIL，适配器类找不到。

- [ ] **Step 3: 写 Flyway V2**

`V2__add_user_login_record.sql`：

```sql
-- ============================================================
-- idn_user_login_record：前台用户的登录记录，ID 就是会话 ID
-- ============================================================
CREATE TABLE idn_user_login_record
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    client_type     VARCHAR(16)     NOT NULL,
    device_id       VARCHAR(128)    NULL,
    expires_at      timestamptz(6)  NOT NULL,
    ended_at        timestamptz(6)  NULL,
    end_reason      VARCHAR(16)     NULL,
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
    CONSTRAINT fk_idn_user_login_record_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_login_record_client_type CHECK (client_type IN ('WEB')),
    CONSTRAINT ck_idn_user_login_record_end_reason CHECK (end_reason IN ('LOGOUT', 'BANNED', 'REPLACED')),
    CONSTRAINT ck_idn_user_login_record_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX idx_idn_user_login_record_user_id ON idn_user_login_record (user_id);

COMMENT ON TABLE idn_user_login_record IS 'Login record of a front-end user; id equals the session id';
COMMENT ON COLUMN idn_user_login_record.id IS 'PK (snowflake), same as the Redis session id';
COMMENT ON COLUMN idn_user_login_record.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_login_record.client_type IS 'Client type: WEB';
COMMENT ON COLUMN idn_user_login_record.device_id IS 'Client-reported device identifier, optional';
COMMENT ON COLUMN idn_user_login_record.expires_at IS 'Absolute expiry of the session (UTC)';
COMMENT ON COLUMN idn_user_login_record.ended_at IS 'When the session ended explicitly; null while open or after a silent expiry';
COMMENT ON COLUMN idn_user_login_record.end_reason IS 'LOGOUT, BANNED or REPLACED';
COMMENT ON COLUMN idn_user_login_record.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_login_record.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_login_record.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_login_record.created_at IS 'Creation time (UTC), also the login time';
COMMENT ON COLUMN idn_user_login_record.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_login_record.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_login_record.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_login_record.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_login_record.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_login_record_updated_at
    BEFORE UPDATE ON idn_user_login_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

- [ ] **Step 4: 写实体、DAO、映射器、适配器**

`UserLoginRecordEntity.java`：

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
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// `idn_user_login_record` 表的 JPA 实体。`toString()` 不输出设备标识。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_login_record")
public class UserLoginRecordEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 客户端类型：WEB
  @Column(name = "client_type", nullable = false, length = 16)
  private String clientType;

  /// 设备标识，可空
  @ToString.Exclude
  @Column(name = "device_id", length = 128)
  private String deviceId;

  /// 会话的绝对过期时间
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /// 结束时间，未结束时为 null
  @Column(name = "ended_at")
  private Instant endedAt;

  /// 结束原因：LOGOUT / BANNED / REPLACED，未结束时为 null
  @Column(name = "end_reason", length = 16)
  private String endReason;
}
```

`UserLoginRecordDao.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.dao;

import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/// `idn_user_login_record` 的 Spring Data 仓库。
public interface UserLoginRecordDao extends JpaRepository<UserLoginRecordEntity, Long> {}
```

`UserLoginRecordJpaMapper.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// UserLoginRecord 与 UserLoginRecordEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface UserLoginRecordJpaMapper {

  /// 聚合转实体。
  ///
  /// @param login 登录记录
  /// @return 实体
  @Mapping(target = "clientType", expression = "java(login.getClientType().name())")
  @Mapping(
      target = "deviceId",
      expression = "java(login.getDeviceId().map(device -> device.value()).orElse(null))")
  @Mapping(
      target = "endReason",
      expression = "java(login.getEndReason() == null ? null : login.getEndReason().name())")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserLoginRecordEntity toEntity(UserLoginRecord login);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 登录记录；实体为 `null` 时返回 `null`
  default UserLoginRecord toAggregate(UserLoginRecordEntity entity) {
    if (entity == null) {
      return null;
    }
    return UserLoginRecord.restore(
        entity.getId(),
        entity.getUserId(),
        ClientType.valueOf(entity.getClientType()),
        entity.getDeviceId() == null ? null : new DeviceId(entity.getDeviceId()),
        entity.getExpiresAt(),
        entity.getEndedAt(),
        entity.getEndReason() == null ? null : LoginEndReason.valueOf(entity.getEndReason()),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
```

`UserLoginRecordRepositoryAdapter.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.persistence;

import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper.UserLoginRecordJpaMapper;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserLoginRecordDao;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserLoginRecordEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 登录记录仓储的 JPA 实现。保存用 `saveAndFlush`，乐观锁冲突在这里就抛出来。
@Repository
@RequiredArgsConstructor
public class UserLoginRecordRepositoryAdapter implements UserLoginRecordRepository {

  private final UserLoginRecordDao dao;
  private final UserLoginRecordJpaMapper mapper;

  /// 保存记录，新记录分配雪花 ID。
  ///
  /// @param login 记录
  /// @return 保存后的记录
  @Override
  public UserLoginRecord save(UserLoginRecord login) {
    UserLoginRecordEntity entity = mapper.toEntity(login);
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
    return mapper.toAggregate(dao.saveAndFlush(entity));
  }

  /// 按 ID 查记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  @Override
  public Optional<UserLoginRecord> findById(long id) {
    return dao.findById(id).map(mapper::toAggregate);
  }
}
```

- [ ] **Step 5: 跑集成测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserLoginRecordRepositoryAdapterIT*"`
Expected: PASS，5 个用例。

- [ ] **Step 6: 改启动测试的建表数**

`PatraIdentityApplicationIT.should_create_identity_tables_on_startup` 的断言改成：

```java
    assertThat(tables)
        .containsExactly("idn_user", "idn_user_login_record", "idn_user_password_credential");
```

`@DisplayName` 改成「启动时 Flyway 建好三张表」。

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*PatraIdentityApplicationIT*"`
Expected: PASS。

- [ ] **Step 7: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:spotlessApply :patra-api:patra-identity:patra-identity-boot:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-infra patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/PatraIdentityApplicationIT.java
git commit -m "feat(identity): 登录记录建表与仓储 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 10: 会话存储适配器，登录限流改用统一的不可用判定

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-infra/build.gradle.kts`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/session/SessionStoreAdapter.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/throttle/LoginThrottleAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/linqibin/patra/identity/infra/adapter/session/SessionStoreAdapterTest.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/linqibin/patra/identity/infra/adapter/throttle/LoginThrottleAdapterTest.java`（加两个用例）

**Interfaces:**
- Consumes: Task 3–6 的 `RedisSessionStore`、`NewSession`、`IssuedSession`、`TransientRedisFailures`；Task 8 的 `SessionStorePort`、`NewUserSession`、`IssuedUserSession`。
- Produces: `SessionStoreAdapter implements SessionStorePort`（`@Component`，构造参数 `RedisSessionStore`）。`LoginThrottleAdapter` 对 `LOADING` 等状态也抛 `TemporarilyUnavailableException`。

- [ ] **Step 1: 加依赖**

`patra-identity-infra/build.gradle.kts` 的 `dependencies` 里，`spring-boot-starter-data-redis` 那行后面加：

```kotlin
    // identity 与网关共用的会话存储
    implementation(project(":patra-api:patra-identity:patra-identity-session"))
```

- [ ] **Step 2: 写失败的测试**

`SessionStoreAdapterTest.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.session.IssuedSession;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionToken;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// SessionStoreAdapter 单元测试：domain 的记录怎么转成会话模块的类型。
@DisplayName("SessionStoreAdapter 单元测试")
class SessionStoreAdapterTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

  private final RedisSessionStore store = mock(RedisSessionStore.class);
  private final SessionStoreAdapter adapter = new SessionStoreAdapter(store);

  @Test
  @DisplayName("签发：时间按 now 算，设备标识、有效期、上限原样带过去；返回令牌原文和被挤掉的 ID")
  void should_translate_new_session_and_result() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    when(store.create(any())).thenReturn(IssuedSession.of(token, List.of(6001L)));

    IssuedUserSession issued =
        adapter.issue(
            NewUserSession.builder()
                .userId(42L)
                .sessionId(7001L)
                .accountType(AccountType.USER)
                .client(LoginClient.of("web", "mac-safari"))
                .now(NOW)
                .lifetime(SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180)))
                .maxSessionsPerUser(10)
                .build());

    assertThat(issued.token()).isEqualTo(token.value());
    assertThat(issued.replacedSessionIds()).containsExactly(6001L);
    ArgumentCaptor<NewSession> created = ArgumentCaptor.forClass(NewSession.class);
    verify(store).create(created.capture());
    NewSession session = created.getValue();
    assertThat(session.userId()).isEqualTo(42L);
    assertThat(session.sessionId()).isEqualTo(7001L);
    assertThat(session.accountType()).isEqualTo(AccountType.USER);
    assertThat(session.clientType()).isEqualTo(ClientType.WEB);
    assertThat(session.deviceId()).isEqualTo("mac-safari");
    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    assertThat(session.idleTimeout()).isEqualTo(Duration.ofDays(30));
    assertThat(session.maxSessionsPerUser()).isEqualTo(10);
  }

  @Test
  @DisplayName("撤销一条和撤销全部直接转给存储")
  void should_delegate_revocations() {
    when(store.delete(AccountType.USER, 42L, 7001L)).thenReturn(true);
    when(store.deleteAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));

    assertThat(adapter.revoke(AccountType.USER, 42L, 7001L)).isTrue();
    assertThat(adapter.revokeAll(AccountType.USER, 42L)).containsExactly(7001L, 7002L);
  }
}
```

在 `LoginThrottleAdapterTest` 里加（import `java.util.List` 已有；加 `io.lettuce.core.RedisLoadingException`、`io.lettuce.core.RedisNoScriptException`、`org.springframework.data.redis.RedisSystemException`、`org.springframework.data.redis.core.script.RedisScript`）：

```java
  private static final LoginThrottlePolicy POLICY =
      LoginThrottlePolicy.of(5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30));

  @Test
  @DisplayName("Redis 正在加载数据（LOADING）：按依赖不可用处理，返回 503")
  void should_treat_loading_as_unavailable() {
    LoginThrottleAdapter adapter =
        new LoginThrottleAdapter(
            failingWith(
                new RedisSystemException(
                    "Error in execution",
                    new RedisLoadingException("LOADING Redis is loading the dataset in memory"))),
            POLICY);

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, EmailAddress.of("a@example.com")))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("脚本本身出错（NOSCRIPT）：是缺陷，原样抛出")
  void should_rethrow_script_defects() {
    LoginThrottleAdapter adapter =
        new LoginThrottleAdapter(
            failingWith(
                new RedisSystemException(
                    "Error in execution", new RedisNoScriptException("NOSCRIPT No matching script."))),
            POLICY);

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, EmailAddress.of("a@example.com")))
        .isInstanceOf(RedisSystemException.class);
  }

  /// 一个执行脚本就抛指定异常的模板。不 mock 可变参数的方法，直接覆盖。
  ///
  /// @param failure 要抛的异常
  /// @return 模板
  private static StringRedisTemplate failingWith(RuntimeException failure) {
    return new StringRedisTemplate() {
      @Override
      public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        throw failure;
      }
    };
  }
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests "*SessionStoreAdapterTest*" --tests "*LoginThrottleAdapterTest*"`
Expected: `SessionStoreAdapterTest` 编译失败（类不存在）；先把它放一边跑 `LoginThrottleAdapterTest` 的话，`should_treat_loading_as_unavailable` FAIL（抛的是 `RedisSystemException`）。两个一起跑看到编译错误即可。

- [ ] **Step 4: 实现适配器，改限流适配器**

`SessionStoreAdapter.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import dev.linqibin.patra.identity.session.IssuedSession;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 会话存储端口的实现：把 domain 的记录转成会话模块的类型，交给 `RedisSessionStore`。
///
/// `SessionStoreUnavailableException` 原样穿过，错误引擎按 `DEP_UNAVAILABLE` 映射成 503。
@Component
@RequiredArgsConstructor
public class SessionStoreAdapter implements SessionStorePort {

  private final RedisSessionStore store;

  /// 建会话。
  ///
  /// @param session 要签发的会话
  /// @return 令牌原文和被挤掉的会话 ID
  @Override
  public IssuedUserSession issue(NewUserSession session) {
    IssuedSession issued =
        store.create(
            NewSession.builder()
                .userId(session.userId())
                .sessionId(session.sessionId())
                .accountType(session.accountType())
                .clientType(session.client().clientType())
                .deviceId(session.client().device().map(DeviceId::value).orElse(null))
                .createdAt(session.now())
                .expiresAt(session.expiresAt())
                .idleTimeout(session.lifetime().idle())
                .maxSessionsPerUser(session.maxSessionsPerUser())
                .build());
    return IssuedUserSession.of(issued.token().value(), issued.replacedSessionIds());
  }

  /// 删一条会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 真的删掉了返回 `true`
  @Override
  public boolean revoke(AccountType accountType, long userId, long sessionId) {
    return store.delete(accountType, userId, sessionId);
  }

  /// 删一个用户的全部会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 真的删掉了的会话 ID
  @Override
  public List<Long> revokeAll(AccountType accountType, long userId) {
    return store.deleteAll(accountType, userId);
  }
}
```

`LoginThrottleAdapter.java`：去掉 `DataAccessResourceFailureException`、`QueryTimeoutException` 两个 import，加 `import dev.linqibin.patra.identity.session.TransientRedisFailures;`；类 Javadoc 里「Redis 连不上或超时，转成 … 其他 Redis 异常（比如脚本写错）是程序缺陷，原样抛出」改成「Redis 暂时不可用（连不上、超时、`LOADING` 等，由会话模块的 `TransientRedisFailures` 判定）转成 {@link TemporarilyUnavailableException}；其他 Redis 异常（比如脚本写错）是程序缺陷，原样抛出」；`execute` 改成：

```java
  /// 执行 Redis 调用，把暂时失败转成 503。
  ///
  /// @param call 调用
  /// @return 脚本返回值；脚本没有真正执行时为 `null`，由调用方决定怎么处理
  private static Long execute(Supplier<Long> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
  }
```

- [ ] **Step 5: 跑 infra 的单测和限流 IT，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:test`
Expected: PASS。

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*LoginThrottleAdapterIT*"`
Expected: PASS，连不上的那条用例仍是 503。

- [ ] **Step 6: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-infra:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-infra
git commit -m "feat(identity): 会话存储适配器，登录限流改用统一的不可用判定 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 11: 封禁解封的乐观锁冲突转成固定文案的 409

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/exception/UserModifiedConcurrentlyException.java`
- Modify: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/linqibin/patra/identity/domain/exception/DomainExceptionTraitsTest.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/persistence/UserRepositoryAdapter.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/linqibin/patra/identity/infra/adapter/persistence/UserRepositoryAdapterIT.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/linqibin/patra/identity/adapter/rest/admin/AdminUserControllerIT.java`

**Interfaces:**
- Consumes: `DomainException(String, StandardErrorTrait)`、`StandardErrorTrait.CONFLICT`、Spring 的 `OptimisticLockingFailureException`。
- Produces: `UserModifiedConcurrentlyException()`，文案「用户正被其他操作修改，请重试」，错误码 `IDN-0409`。

- [ ] **Step 1: 写失败的测试**

`DomainExceptionTraitsTest.exceptions()` 的 `Stream.of(...)` 里加一行，Javadoc 的「七个」改「八个」：

```java
        Arguments.of(
            new UserModifiedConcurrentlyException(),
            "用户正被其他操作修改，请重试",
            StandardErrorTrait.CONFLICT),
```

`UserRepositoryAdapterIT.should_reject_stale_version` 的断言改成期望新异常（import 改为 `dev.linqibin.patra.identity.domain.exception.UserModifiedConcurrentlyException`，删掉 `OptimisticLockingFailureException` 的 import）：

```java
    assertThatThrownBy(() -> repository.save(stale))
        .isInstanceOf(UserModifiedConcurrentlyException.class);
```

`AdminUserControllerIT` 加（import `dev.linqibin.patra.identity.domain.exception.UserModifiedConcurrentlyException`）：

```java
  @Test
  @DisplayName("乐观锁冲突：409，固定文案，不带实体类名")
  void should_return_409_with_fixed_detail_on_concurrent_modification() {
    when(commandBus.handle(any(BanUserCommand.class)))
        .thenThrow(new UserModifiedConcurrentlyException());

    restClient
        .post()
        .uri("/admin/users/42/ban")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0409")
        .jsonPath("$.detail")
        .isEqualTo("用户正被其他操作修改，请重试");
  }
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*DomainExceptionTraitsTest*"`
Expected: 编译失败，异常类不存在。

- [ ] **Step 3: 实现**

`UserModifiedConcurrentlyException.java`：

```java
package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 保存用户时撞上乐观锁：封禁、解封并发时后到的一方收到 409，重试即可。
public final class UserModifiedConcurrentlyException extends DomainException {

  /// 创建异常。
  public UserModifiedConcurrentlyException() {
    super("用户正被其他操作修改，请重试", StandardErrorTrait.CONFLICT);
  }
}
```

`UserRepositoryAdapter.save` 加一个 `catch`（import `org.springframework.dao.OptimisticLockingFailureException` 和新异常）：

```java
    try {
      return mapper.toAggregate(dao.saveAndFlush(entity));
    } catch (DataIntegrityViolationException ex) {
      if (violates(ex, EMAIL_UNIQUE_CONSTRAINT)) {
        throw new EmailAlreadyRegisteredException();
      }
      throw ex;
    } catch (OptimisticLockingFailureException ex) {
      throw new UserModifiedConcurrentlyException();
    }
```

类 Javadoc 补一句：「乐观锁冲突转成 {@link UserModifiedConcurrentlyException}，`detail` 是固定文案，不带实体类名和 ID。」

- [ ] **Step 4: 跑三处测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests "*DomainExceptionTraitsTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserRepositoryAdapterIT*" :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AdminUserControllerIT*"`
Expected: 全部 PASS。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:spotlessApply :patra-api:patra-identity:patra-identity-infra:spotlessApply :patra-api:patra-identity:patra-identity-adapter:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra patra-api/patra-identity/patra-identity-adapter
git commit -m "fix(identity): 封禁解封的乐观锁冲突转成固定文案的 409 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 12: 当前用户的读端口与查询服务

开工前加载 `patra-backend:patra-jpa` 技能。

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/model/read/UserAccountReadModel.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/linqibin/patra/identity/domain/port/read/UserReadPort.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/linqibin/patra/identity/infra/adapter/read/UserReadAdapter.java`
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/user/query/UserQueryService.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/linqibin/patra/identity/infra/adapter/read/UserReadAdapterIT.java`
- Test: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/user/query/UserQueryServiceTest.java`

**Interfaces:**
- Consumes: `CurrentUserPort.require()`、`AuthenticationRequiredException`（`patra-common-security`）；`UserDao.findById`；`UserStatus`。
- Produces: `record UserAccountReadModel(long userId, String email, UserStatus status)`（`of`）；`interface UserReadPort { Optional<UserAccountReadModel> findAccount(long userId); }`；`UserReadAdapter implements UserReadPort`（`@Component`）；`UserQueryService`（`@Service`，构造参数 `CurrentUserPort`、`UserReadPort`），`UserAccountReadModel currentAccount()`。

- [ ] **Step 1: 写失败的测试**

`UserQueryServiceTest.java`：

```java
package dev.linqibin.patra.identity.app.usecase.user.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.AuthenticationRequiredException;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserQueryService 单元测试。
@DisplayName("UserQueryService 单元测试")
class UserQueryServiceTest {

  private static final CurrentUser USER =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserQueryService service = new UserQueryService(currentUserPort, userReadPort);

  @Test
  @DisplayName("有当前用户且账号正常：返回 ID、邮箱、状态")
  void should_return_current_account() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.ACTIVE)));

    UserAccountReadModel account = service.currentAccount();

    assertThat(account.userId()).isEqualTo(42L);
    assertThat(account.email()).isEqualTo("chen.yu@example.com");
  }

  @Test
  @DisplayName("没有当前用户：401，不查库")
  void should_require_authentication() {
    when(currentUserPort.require()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(userReadPort);
  }

  @Test
  @DisplayName("会话指向的用户不存在：401")
  void should_reject_missing_user() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("用户已封禁但断言还没过期：401")
  void should_reject_banned_user() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.BANNED)));

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }
}
```

`UserReadAdapterIT.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.infra.adapter.persistence.UserRepositoryAdapter;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserReadAdapter 集成测试。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  UserRepositoryAdapter.class,
  UserReadAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserReadAdapter 集成测试")
class UserReadAdapterIT {

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserReadAdapter reader;

  @Test
  @DisplayName("按 ID 读账号：邮箱和状态；封禁后状态跟着变；不存在返回空")
  void should_read_account_by_id() {
    User saved = users.save(User.register(EmailAddress.of("read.me@example.com")));

    assertThat(reader.findAccount(saved.getId()))
        .contains(UserAccountReadModel.of(saved.getId(), "read.me@example.com", UserStatus.ACTIVE));

    saved.ban(Instant.parse("2026-10-09T08:00:00Z"));
    users.save(saved);
    assertThat(reader.findAccount(saved.getId()))
        .map(UserAccountReadModel::status)
        .contains(UserStatus.BANNED);
    assertThat(reader.findAccount(1L)).isEmpty();
  }
}
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*UserQueryServiceTest*"`
Expected: 编译失败，类型不存在。

- [ ] **Step 3: 实现**

`UserAccountReadModel.java`：

```java
package dev.linqibin.patra.identity.domain.model.read;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import java.util.Objects;

/// 「当前用户」接口要的账号信息。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
/// @param status 状态
public record UserAccountReadModel(long userId, String email, UserStatus status) {

  /// 校验非空。
  public UserAccountReadModel {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
  }

  /// 创建读模型。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @param status 状态
  /// @return 读模型
  public static UserAccountReadModel of(long userId, String email, UserStatus status) {
    return new UserAccountReadModel(userId, email, status);
  }
}
```

`UserReadPort.java`：

```java
package dev.linqibin.patra.identity.domain.port.read;

import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import java.util.Optional;

/// 前台用户的读端口。
public interface UserReadPort {

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息；不存在时为空
  Optional<UserAccountReadModel> findAccount(long userId);
}
```

`UserReadAdapter.java`：

```java
package dev.linqibin.patra.identity.infra.adapter.read;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserDao;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 前台用户读端口的 JPA 实现。
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

  private final UserDao dao;

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息
  @Override
  public Optional<UserAccountReadModel> findAccount(long userId) {
    return dao.findById(userId)
        .map(
            entity ->
                UserAccountReadModel.of(
                    entity.getId(), entity.getEmail(), UserStatus.valueOf(entity.getStatus())));
  }
}
```

`UserQueryService.java`：

```java
package dev.linqibin.patra.identity.app.usecase.user.query;

import dev.linqibin.patra.common.security.AuthenticationRequiredException;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 「当前用户」的查询。只读，不经 CommandBus，没有事务。
///
/// 没有当前用户、会话指向的用户不存在、用户已封禁，三种情况都抛
/// {@link AuthenticationRequiredException}（401）：对客户端来说都是「这个会话不再有效」。
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

  private final CurrentUserPort currentUserPort;
  private final UserReadPort userReadPort;

  /// 当前登录用户的账号信息。
  ///
  /// @return 账号信息
  /// @throws AuthenticationRequiredException 没有当前用户、用户不存在或已封禁
  public UserAccountReadModel currentAccount() {
    CurrentUser current = currentUserPort.require();
    UserAccountReadModel account =
        userReadPort
            .findAccount(current.userId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "会话指向的用户不存在: userId={}, sessionId={}",
                      current.userId(),
                      current.sessionId());
                  return new AuthenticationRequiredException();
                });
    if (account.status() == UserStatus.BANNED) {
      log.warn(
          "已封禁用户的断言仍在有效期内: userId={}, sessionId={}",
          current.userId(),
          current.sessionId());
      throw new AuthenticationRequiredException();
    }
    return account;
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*UserQueryServiceTest*" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*UserReadAdapterIT*"`
Expected: 全部 PASS。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-domain:spotlessApply :patra-api:patra-identity:patra-identity-infra:spotlessApply :patra-api:patra-identity:patra-identity-app:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-domain patra-api/patra-identity/patra-identity-infra patra-api/patra-identity/patra-identity-app
git commit -m "feat(identity): 当前用户的读端口与查询服务 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 13: 登录和注册签发会话令牌，请求体接客户端类型与设备标识

**Files:**
- Rename（`git mv`）: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/authenticate/{AuthenticateUserCommand,AuthenticateUserHandler,AuthenticateUserResult}.java` → `.../app/usecase/login/{LoginUserCommand,LoginUserHandler,LoginUserResult}.java`
- Rename（`git mv`）: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/authenticate/AuthenticateUserHandlerTest.java` → `.../app/usecase/login/LoginUserHandlerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/register/{RegisterUserCommand,RegisterUserHandler,RegisterUserResult}.java`
- Modify: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/register/RegisterUserHandlerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/request/{RegisterRequest,LoginRequest}.java`
- Create: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/response/AuthenticatedUserResponse.java`
- Delete: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/response/UserAccountResponse.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/AuthController.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/test/java/dev/linqibin/patra/identity/adapter/rest/auth/request/{RegisterRequestTest,LoginRequestTest}.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/linqibin/patra/identity/adapter/rest/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: Task 7 的 `LoginClient`；Task 8 的 `SessionIssuer`、`IssuedUserSession`；现有的 `TransactionOperations`。
- Produces: `record LoginUserCommand(String email, String password, String clientType, String deviceId) implements Command<LoginUserResult>`（`of`）；`record LoginUserResult(String sessionToken, long userId, String email)`（`of`）；`LoginUserHandler(UserRepository, UserPasswordCredentialRepository, PasswordHashingPort, LoginThrottlePort, SessionIssuer, TransactionOperations)`；`record RegisterUserCommand(String email, String password, String clientType, String deviceId)`；`record RegisterUserResult(String sessionToken, long userId, String email)`；`RegisterUserHandler(UserRepository, UserPasswordCredentialRepository, PasswordHashingPort, PasswordPolicy, SessionIssuer, TransactionOperations)`；`record RegisterRequest(String email, String password, String clientType, String deviceId)`、`LoginRequest` 同；`record AuthenticatedUserResponse(String sessionToken, Long userId, String email)`（`of(String, long, String)`）。

- [ ] **Step 1: 改名**

```bash
git mv patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/authenticate patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login
git mv patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/AuthenticateUserCommand.java patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/LoginUserCommand.java
git mv patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/AuthenticateUserHandler.java patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/LoginUserHandler.java
git mv patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/AuthenticateUserResult.java patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/login/LoginUserResult.java
git mv patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/authenticate patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/login
git mv patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/login/AuthenticateUserHandlerTest.java patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/login/LoginUserHandlerTest.java
```

- [ ] **Step 2: 改登录处理器的测试（失败的测试）**

`LoginUserHandlerTest.java`：包名改 `…app.usecase.login`，类名、Javadoc、`@DisplayName` 改成 `LoginUserHandler`；所有 `AuthenticateUserCommand.of(a, b)` 改成 `LoginUserCommand.of(a, b, null, null)`，`AuthenticateUserResult` 改 `LoginUserResult`；字段和构造改成：

```java
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final LoginUserHandler handler =
      new LoginUserHandler(
          users,
          credentials,
          passwordHashing,
          loginThrottle,
          sessionIssuer,
          TransactionOperations.withoutTransaction());
```

`stubHappyPath()` 里加：

```java
    when(sessionIssuer.issue(anyLong(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));
```

「密码正确」那个用例的断言加一行 `assertThat(result.sessionToken()).isEqualTo("patra_user_x");`。再加四个用例（import `static org.mockito.ArgumentMatchers.anyLong`、`static org.mockito.ArgumentMatchers.eq`、`dev.linqibin.commons.error.field.FieldViolation`、`dev.linqibin.patra.identity.domain.model.vo.DeviceId`、`dev.linqibin.patra.identity.domain.model.vo.LoginClient`、`dev.linqibin.patra.identity.domain.port.session.IssuedUserSession`、`dev.linqibin.patra.identity.domain.service.SessionIssuer`、`java.util.List`、`org.mockito.ArgumentCaptor`、`org.springframework.transaction.support.TransactionOperations`）：

```java
  @Test
  @DisplayName("凭据正确：在结算成功之后、以已校验的用户 ID 和客户端信息建会话")
  void should_issue_session_after_successful_authentication() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    LoginUserResult result =
        handler.handle(LoginUserCommand.of("chen.yu@example.com", "correct horse", "web", " mac "));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    ArgumentCaptor<LoginClient> client = ArgumentCaptor.forClass(LoginClient.class);
    verify(sessionIssuer).issue(eq(42L), client.capture());
    assertThat(client.getValue().device()).map(DeviceId::value).contains("mac");
    var order = inOrder(loginThrottle, sessionIssuer);
    order.verify(loginThrottle).recordSuccess(ATTEMPT);
    order.verify(sessionIssuer).issue(anyLong(), any());
  }

  @Test
  @DisplayName("密码错误、账号被封禁：都不建会话")
  void should_not_issue_session_when_rejected() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);

    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "correct", null, null)))
        .isInstanceOf(UserBannedException.class);

    verifyNoInteractions(sessionIssuer);
  }

  @Test
  @DisplayName("客户端类型和设备标识的错误和邮箱、密码一起报，不碰限流")
  void should_report_client_violations_with_other_fields() {
    assertThatThrownBy(() -> handler.handle(LoginUserCommand.of("", "", "app", "x".repeat(129))))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly("email", "password", "clientType", "deviceId"));
    verifyNoInteractions(loginThrottle, sessionIssuer);
  }

  @Test
  @DisplayName("命令的 toString 不带密码")
  void should_mask_password_in_command() {
    assertThat(LoginUserCommand.of("a@example.com", "secret", "web", null).toString())
        .doesNotContain("secret")
        .contains("a@example.com");
    assertThat(LoginUserResult.of("patra_user_x", 42L, "a@example.com").toString())
        .doesNotContain("patra_user_x")
        .contains("42");
  }
```

`inOrder` 从 `org.mockito.Mockito` 静态导入。

- [ ] **Step 3: 改注册处理器的测试（失败的测试）**

`RegisterUserHandlerTest.java`：构造改成六个参数（`sessionIssuer` 放在 `passwordPolicy` 之后）；`RegisterUserCommand.of(a, b)` 改成 `of(a, b, null, null)`；注册成功的用例里加 `when(sessionIssuer.issue(anyLong(), any())).thenReturn(IssuedUserSession.of("patra_user_x", List.of()));` 和断言 `assertThat(result.sessionToken()).isEqualTo("patra_user_x");`；再加两个用例：

```java
  @Test
  @DisplayName("注册成功后以新用户的 ID 和客户端信息建会话")
  void should_issue_session_for_new_user() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenReturn(SAVED_USER);
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionIssuer.issue(anyLong(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));

    RegisterUserResult result =
        handler.handle(
            RegisterUserCommand.of("chen.yu@example.com", "correct horse battery", "web", "mac"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    ArgumentCaptor<LoginClient> client = ArgumentCaptor.forClass(LoginClient.class);
    verify(sessionIssuer).issue(eq(42L), client.capture());
    assertThat(client.getValue().device()).map(DeviceId::value).contains("mac");
  }

  @Test
  @DisplayName("邮箱已注册或字段不合法：不建会话")
  void should_not_issue_session_when_registration_is_rejected() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);
    assertThatThrownBy(
            () ->
                handler.handle(
                    RegisterUserCommand.of("chen.yu@example.com", "correct horse battery", null, null)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    assertThatThrownBy(
            () -> handler.handle(RegisterUserCommand.of("chen.yu@example.com", "short", "app", null)))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(sessionIssuer);
  }
```

- [ ] **Step 4: 跑 app 的测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test`
Expected: 编译失败（`LoginUserCommand` 没有四参 `of`、`SessionIssuer` 不是构造参数）。

- [ ] **Step 5: 实现 app 层**

`LoginUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.login;

import dev.linqibin.commons.cqrs.Command;

/// 前台用户登录：校验凭据并建会话。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
public record LoginUserCommand(String email, String password, String clientType, String deviceId)
    implements Command<LoginUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型，可以为 `null`
  /// @param deviceId 设备标识，可以为 `null`
  /// @return 命令
  public static LoginUserCommand of(
      String email, String password, String clientType, String deviceId) {
    return new LoginUserCommand(email, password, clientType, deviceId);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginUserCommand[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
```

`LoginUserResult.java`：

```java
package dev.linqibin.patra.identity.app.usecase.login;

/// 登录结果：会话令牌、用户 ID、邮箱。
///
/// @param sessionToken 会话令牌原文，只出现在响应里
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record LoginUserResult(String sessionToken, long userId, String email) {

  /// 创建结果。
  ///
  /// @param sessionToken 会话令牌
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static LoginUserResult of(String sessionToken, long userId, String email) {
    return new LoginUserResult(sessionToken, userId, email);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginUserResult[sessionToken=***, userId=" + userId + ", email=" + email + "]";
  }
}
```

`LoginUserHandler.java`（整个文件）：

```java
package dev.linqibin.patra.identity.app.usecase.login;

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
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 前台用户登录：校验凭据，通过后建会话。
///
/// 1. 校验字段：邮箱用注册时的规则，密码只查非空和 Unicode 合法，客户端类型和设备标识一起报。
/// 2. 开始一次尝试：锁定期内或在途已满直接 429，不查库、不做哈希。密码超过登录长度上限时
///    也不查库、不做哈希，直接按失败结算。
/// 3. 查用户和凭据。查不到时拿假哈希做一次校验，耗时和「密码错」一样。
/// 4. 密码错按失败结算（可能触发 429）；密码对按成功结算，封禁的账号返回 403。
/// 5. 中途出错按取消结算，不计失败，原来的错误照常抛出。
/// 6. 校验通过后在一个事务里建会话：存登录记录、写 Redis、收尾被挤掉的记录。哈希在事务外，
///    事务不占着连接等排队。Redis 写失败回滚记录，返回 503。
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserHandler implements CommandHandler<LoginUserCommand, LoginUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;
  private final SessionIssuer sessionIssuer;
  private final TransactionOperations transactions;

  /// 登录。
  ///
  /// @param command 命令
  /// @return 会话令牌、用户 ID 和邮箱
  @Override
  public LoginUserResult handle(LoginUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    PlainPassword.validate(command.password()).ifPresent(violations::add);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    PlainPassword password = PlainPassword.of(command.password());
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());

    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    if (password.length() > PasswordPolicy.MAX_LOGIN_LENGTH) {
      // 不可能是合法密码：不查库、不做哈希，按密码错误结算
      throw failure(attempt);
    }
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
      throw failure(attempt);
    }
    loginThrottle.recordSuccess(attempt);
    User verified = user.orElseThrow();
    if (verified.isBanned()) {
      throw new UserBannedException();
    }
    IssuedUserSession session =
        Objects.requireNonNull(
            transactions.execute(status -> sessionIssuer.issue(verified.getId(), client)));
    log.info(
        "前台用户已登录: userId={}, replacedSessions={}",
        verified.getId(),
        session.replacedSessionIds().size());
    return LoginUserResult.of(session.token(), verified.getId(), verified.getEmail().value());
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

  /// 按失败结算。这次失败让账号处于锁定期时是 429 的异常，否则是 401 的异常。
  ///
  /// @param attempt 尝试
  /// @return 要抛出的异常
  private RuntimeException failure(LoginAttempt attempt) {
    Optional<Duration> lock = loginThrottle.recordFailure(attempt);
    if (lock.isPresent()) {
      return new LoginTemporarilyLockedException(lock.get());
    }
    return new InvalidCredentialsException();
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

`RegisterUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.Command;

/// 注册前台用户，成功后直接建会话（注册即登录）。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
public record RegisterUserCommand(
    String email, String password, String clientType, String deviceId)
    implements Command<RegisterUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型，可以为 `null`
  /// @param deviceId 设备标识，可以为 `null`
  /// @return 命令
  public static RegisterUserCommand of(
      String email, String password, String clientType, String deviceId) {
    return new RegisterUserCommand(email, password, clientType, deviceId);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserCommand[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
```

`RegisterUserResult.java`：

```java
package dev.linqibin.patra.identity.app.usecase.register;

/// 注册结果：会话令牌、用户 ID、邮箱。
///
/// @param sessionToken 会话令牌原文，只出现在响应里
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record RegisterUserResult(String sessionToken, long userId, String email) {

  /// 创建结果。
  ///
  /// @param sessionToken 会话令牌
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static RegisterUserResult of(String sessionToken, long userId, String email) {
    return new RegisterUserResult(sessionToken, userId, email);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserResult[sessionToken=***, userId=" + userId + ", email=" + email + "]";
  }
}
```

`RegisterUserHandler.java`（整个文件）：

```java
package dev.linqibin.patra.identity.app.usecase.register;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注册前台用户，成功后直接建会话。
///
/// 顺序：字段一起校验（有错一次报全）→ 查邮箱是否已注册 → 哈希 → 在一个事务里存用户、存凭据、建会话。
/// 哈希放在事务外：事务开始时就会占住数据库连接，而哈希可能要排队几秒。
/// 建会话放在事务里：Redis 写失败整个注册回滚，用户重试不会撞 409。
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUserHandler
    implements CommandHandler<RegisterUserCommand, RegisterUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final PasswordPolicy passwordPolicy;
  private final SessionIssuer sessionIssuer;
  private final TransactionOperations transactions;

  /// 注册并登录。
  ///
  /// @param command 命令
  /// @return 会话令牌、新用户的 ID 和邮箱
  @Override
  public RegisterUserResult handle(RegisterUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    passwordPolicy.validateForRegistration(command.password()).ifPresent(violations::add);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }
    PasswordHash hash = passwordHashing.hash(PlainPassword.of(command.password()));
    Registration registration =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User saved = users.save(User.register(email));
                  credentials.save(UserPasswordCredential.create(saved.getId(), hash));
                  return new Registration(saved, sessionIssuer.issue(saved.getId(), client));
                }));
    log.info("前台用户已注册并登录: userId={}", registration.user().getId());
    return RegisterUserResult.of(
        registration.session().token(),
        registration.user().getId(),
        registration.user().getEmail().value());
  }

  /// 事务里一起产出的用户和会话。
  ///
  /// @param user 新用户
  /// @param session 新会话
  private record Registration(User user, IssuedUserSession session) {}
}
```

- [ ] **Step 6: 跑 app 的测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test`
Expected: PASS，含新加的六个用例。

- [ ] **Step 7: 改 adapter（先改测试）**

`RegisterRequestTest` 和 `LoginRequestTest` 里的构造改成四个参数（后两个传 `null`），并加断言 `toString()` 含 `clientType=`。`AuthControllerIT`：import 从 `…usecase.authenticate.AuthenticateUserCommand` / `AuthenticateUserResult` 改为 `…usecase.login.LoginUserCommand` / `LoginUserResult`，全文替换类名；两个成功用例改成：

```java
  @Test
  @DisplayName("注册成功返回 201，带会话令牌，userId 是字符串；原始输入原样交给命令")
  void should_register_and_return_201() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenReturn(
            RegisterUserResult.of("patra_user_x", 352303128713027974L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", " Chen.Yu@Example.com ", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_x")
        .jsonPath("$.userId")
        .value(
            userId -> assertThat(userId).isInstanceOf(String.class).isEqualTo("352303128713027974"))
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo(" Chen.Yu@Example.com ");
    assertThat(command.getValue().password()).isEqualTo("correct horse battery");
    assertThat(command.getValue().clientType()).isNull();
    assertThat(command.getValue().deviceId()).isNull();
  }

  @Test
  @DisplayName("登录成功返回 200，带会话令牌；clientType 和 deviceId 原样交给命令")
  void should_login_and_return_200() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenReturn(LoginUserResult.of("patra_user_x", 42L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "email", "chen.yu@example.com",
                "password", "correct horse battery",
                "clientType", "web",
                "deviceId", "mac-safari"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_x")
        .jsonPath("$.userId")
        .isEqualTo("42")
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<LoginUserCommand> command = ArgumentCaptor.forClass(LoginUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().clientType()).isEqualTo("web");
    assertThat(command.getValue().deviceId()).isEqualTo("mac-safari");
  }
```

其余用例里 `AuthenticateUserCommand` 改 `LoginUserCommand` 即可，断言不变。

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:compileIntegrationTestJava`
Expected: 编译失败（响应类和命令工厂还没改）。

- [ ] **Step 8: 实现 adapter**

`RegisterRequest.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 注册请求体。确认密码只在前端校验，不传给后端。
///
/// @param email 邮箱
/// @param password 密码
/// @param clientType 客户端类型，不传按 `web`
/// @param deviceId 设备标识，可不传
public record RegisterRequest(String email, String password, String clientType, String deviceId) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterRequest[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}
```

`LoginRequest.java` 同样加两个字段，Javadoc「登录请求体」，`toString()` 同上把类名换成 `LoginRequest`。

`AuthenticatedUserResponse.java`（删掉 `UserAccountResponse.java`）：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 注册、登录成功的响应体。`userId` 按全局 Jackson 设置输出成字符串。
///
/// @param sessionToken 会话令牌，客户端原样保存，之后放进 `Authorization: Bearer`
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record AuthenticatedUserResponse(String sessionToken, Long userId, String email) {

  /// 创建响应。
  ///
  /// @param sessionToken 会话令牌
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 响应
  public static AuthenticatedUserResponse of(String sessionToken, long userId, String email) {
    return new AuthenticatedUserResponse(sessionToken, userId, email);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticatedUserResponse[sessionToken=***, userId=" + userId + ", email=" + email + "]";
  }
}
```

`AuthController.java` 的两个方法改成：

```java
  /// 注册并登录。成功返回 201。
  ///
  /// @param request 请求体
  /// @return 会话令牌、新用户的 ID 和邮箱
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse register(@RequestBody RegisterRequest request) {
    RegisterUserResult result =
        commandBus.handle(
            RegisterUserCommand.of(
                request.email(), request.password(), request.clientType(), request.deviceId()));
    return AuthenticatedUserResponse.of(result.sessionToken(), result.userId(), result.email());
  }

  /// 登录。成功返回 200。
  ///
  /// @param request 请求体
  /// @return 会话令牌、用户 ID 和邮箱
  @PostMapping("/login")
  public AuthenticatedUserResponse login(@RequestBody LoginRequest request) {
    LoginUserResult result =
        commandBus.handle(
            LoginUserCommand.of(
                request.email(), request.password(), request.clientType(), request.deviceId()));
    return AuthenticatedUserResponse.of(result.sessionToken(), result.userId(), result.email());
  }
```

import 改成 `…usecase.login.LoginUserCommand` / `LoginUserResult` 和 `…response.AuthenticatedUserResponse`。

- [ ] **Step 9: 跑 adapter 的测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:test :patra-api:patra-identity:patra-identity-adapter:integrationTest`
Expected: PASS。

- [ ] **Step 10: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:spotlessApply :patra-api:patra-identity:patra-identity-adapter:spotlessApply`

```bash
git add -A patra-api/patra-identity/patra-identity-app patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 登录和注册签发会话令牌，请求体接客户端类型与设备标识 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 14: 登出删会话，封禁删该用户全部会话并结束登录记录

**Files:**
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/logout/LogoutUserCommand.java`
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/logout/LogoutUserHandler.java`
- Modify: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/linqibin/patra/identity/app/usecase/ban/BanUserHandler.java`
- Test: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/logout/LogoutUserHandlerTest.java`
- Modify: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/linqibin/patra/identity/app/usecase/ban/BanUserHandlerTest.java`

**Interfaces:**
- Consumes: `CurrentUserPort.current()`；Task 8 的 `SessionStorePort`、`UserLoginRecordRepository`；Task 7 的 `LoginEndReason`。
- Produces: `record LogoutUserCommand() implements Command<Void>`（`of()`）；`LogoutUserHandler(CurrentUserPort, SessionStorePort, UserLoginRecordRepository, Clock, TransactionOperations)`；`BanUserHandler(UserRepository, SessionStorePort, UserLoginRecordRepository, Clock)`。

- [ ] **Step 1: 写失败的测试**

`LogoutUserHandlerTest.java`：

```java
package dev.linqibin.patra.identity.app.usecase.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/// LogoutUserHandler 单元测试。
@DisplayName("LogoutUserHandler 单元测试")
class LogoutUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final CurrentUser USER =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final LogoutUserHandler handler =
      new LogoutUserHandler(
          currentUserPort,
          sessions,
          records,
          Clock.fixed(NOW, ZoneOffset.UTC),
          TransactionOperations.withoutTransaction());

  @Test
  @DisplayName("没有当前用户：什么都不做，正常返回")
  void should_do_nothing_without_current_user() {
    when(currentUserPort.current()).thenReturn(Optional.empty());

    handler.handle(LogoutUserCommand.of());

    verifyNoInteractions(sessions, records);
  }

  @Test
  @DisplayName("有当前用户：先删会话，再把登录记录标成 LOGOUT")
  void should_revoke_session_and_end_record() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(true);
    UserLoginRecord login =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(7001L)).thenReturn(Optional.of(login));

    handler.handle(LogoutUserCommand.of());

    assertThat(login.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
    assertThat(login.getEndedAt()).isEqualTo(NOW);
    verify(records).save(login);
  }

  @Test
  @DisplayName("会话已经没了（过期、封禁、被挤掉）：不动记录")
  void should_leave_record_when_session_is_already_gone() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(false);

    handler.handle(LogoutUserCommand.of());

    verifyNoInteractions(records);
  }

  @Test
  @DisplayName("会话删了但记录找不到：记日志放过，不报错")
  void should_tolerate_missing_record() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(true);
    when(records.findById(7001L)).thenReturn(Optional.empty());

    handler.handle(LogoutUserCommand.of());

    verify(records, never()).save(any());
  }
}
```

`BanUserHandlerTest.java`：字段和构造改成

```java
  private final UserRepository users = mock(UserRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final BanUserHandler handler =
      new BanUserHandler(users, sessions, records, Clock.fixed(NOW, ZoneOffset.UTC));
```

并加两个用例（import `dev.linqibin.patra.common.security.AccountType`、`dev.linqibin.patra.common.security.ClientType`、`dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord`、`dev.linqibin.patra.identity.domain.model.enums.LoginEndReason`、`dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository`、`dev.linqibin.patra.identity.domain.port.session.SessionStorePort`、`java.util.List`、`static org.mockito.Mockito.inOrder`、`static org.mockito.Mockito.verifyNoInteractions`）：

```java
  @Test
  @DisplayName("封禁后删掉该用户的全部会话，被删会话的记录标成 BANNED；找不到的记录跳过")
  void should_revoke_all_sessions_and_end_records() {
    when(users.findById(42L))
        .thenReturn(Optional.of(User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));
    UserLoginRecord first =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(7001L)).thenReturn(Optional.of(first));
    when(records.findById(7002L)).thenReturn(Optional.empty());

    handler.handle(BanUserCommand.of(42L));

    var order = inOrder(users, sessions);
    order.verify(users).save(any());
    order.verify(sessions).revokeAll(AccountType.USER, 42L);
    assertThat(first.getEndReason()).isEqualTo(LoginEndReason.BANNED);
    assertThat(first.getEndedAt()).isEqualTo(NOW);
    verify(records).save(first);
  }

  @Test
  @DisplayName("用户不存在：404，不碰会话")
  void should_not_touch_sessions_when_user_is_missing() {
    when(users.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(BanUserCommand.of(42L)))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(sessions, records);
  }
```

原来「用户不存在返回 404」的用例如果已存在，合并进上面这条，别重复。

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*LogoutUserHandlerTest*" --tests "*BanUserHandlerTest*"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`LogoutUserCommand.java`：

```java
package dev.linqibin.patra.identity.app.usecase.logout;

import dev.linqibin.commons.cqrs.Command;

/// 登出当前用户。没有字段：当前用户从 `CurrentUserPort` 取。
public record LogoutUserCommand() implements Command<Void> {

  /// 创建命令。
  ///
  /// @return 命令
  public static LogoutUserCommand of() {
    return new LogoutUserCommand();
  }
}
```

`LogoutUserHandler.java`：

```java
package dev.linqibin.patra.identity.app.usecase.logout;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 登出：有当前用户就删掉它的会话、把登录记录标成 `LOGOUT`；没有就什么都不做。
///
/// 先删 Redis 再改记录：删会话才是登出的实质，记录没改成也不影响用户已经登出。
/// 会话已经不在（过期、封禁、被挤掉）时不动记录：结束原因以先发生的为准。
/// 登出在网关上是公开路由，所以这里用 `current()` 不用 `require()`。
@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutUserHandler implements CommandHandler<LogoutUserCommand, Void> {

  private final CurrentUserPort currentUserPort;
  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;
  private final Clock clock;
  private final TransactionOperations transactions;

  /// 登出。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(LogoutUserCommand command) {
    Optional<CurrentUser> current = currentUserPort.current();
    if (current.isEmpty()) {
      return null;
    }
    CurrentUser user = current.get();
    boolean revoked = sessions.revoke(user.accountType(), user.userId(), user.sessionId());
    if (!revoked) {
      log.info("登出时会话已不存在: userId={}, sessionId={}", user.userId(), user.sessionId());
      return null;
    }
    Instant now = clock.instant();
    transactions.executeWithoutResult(
        status ->
            records
                .findById(user.sessionId())
                .ifPresentOrElse(
                    login -> {
                      login.end(LoginEndReason.LOGOUT, now);
                      records.save(login);
                    },
                    () ->
                        log.info(
                            "登出时找不到登录记录: userId={}, sessionId={}",
                            user.userId(),
                            user.sessionId())));
    log.info("前台用户已登出: userId={}, sessionId={}", user.userId(), user.sessionId());
    return null;
  }
}
```

`BanUserHandler.java`（整个文件）：

```java
package dev.linqibin.patra.identity.app.usecase.ban;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 封禁前台用户。幂等：已经封禁时保留原来的封禁时间。
///
/// 保存之后删掉该用户的全部会话，被删会话的登录记录标成 `BANNED`。Redis 失败整个事务回滚，
/// 返回 503，管理员重试。
@Slf4j
@Component
@RequiredArgsConstructor
public class BanUserHandler implements CommandHandler<BanUserCommand, Void> {

  private final UserRepository users;
  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;
  private final Clock clock;

  /// 封禁。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(BanUserCommand command) {
    User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
    Instant now = clock.instant();
    user.ban(now);
    users.save(user);
    List<Long> revoked = sessions.revokeAll(AccountType.USER, command.userId());
    for (Long sessionId : revoked) {
      records
          .findById(sessionId)
          .ifPresent(
              login -> {
                login.end(LoginEndReason.BANNED, now);
                records.save(login);
              });
    }
    log.info("前台用户已封禁: userId={}, revokedSessions={}", command.userId(), revoked.size());
    return null;
  }
}
```

- [ ] **Step 4: 跑 app 的测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:check`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-app:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-app
git commit -m "feat(identity): 登出删会话，封禁删该用户全部会话并结束登录记录 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 15: 登出与当前用户接口

**Files:**
- Create: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/response/CurrentUserResponse.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/linqibin/patra/identity/adapter/rest/auth/AuthController.java`
- Modify: `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/linqibin/patra/identity/adapter/rest/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: Task 12 的 `UserQueryService.currentAccount()`、`UserAccountReadModel`；Task 14 的 `LogoutUserCommand`；`AuthenticationRequiredException`。
- Produces: `POST /auth/logout` → 204；`GET /auth/me` → `CurrentUserResponse(Long userId, String email, String accountType)`（`of(long, String, String)`）。

- [ ] **Step 1: 加失败的切片用例**

`AuthControllerIT` 加字段 `@MockitoBean private UserQueryService userQueryService;`（import `dev.linqibin.patra.identity.app.usecase.user.query.UserQueryService`、`dev.linqibin.patra.identity.app.usecase.logout.LogoutUserCommand`、`dev.linqibin.patra.common.security.AuthenticationRequiredException`、`dev.linqibin.patra.identity.domain.model.enums.UserStatus`、`dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel`），再加三个用例：

```java
  @Test
  @DisplayName("登出返回 204，没有响应体；命令没有字段")
  void should_logout_and_return_204() {
    restClient.post().uri("/auth/logout").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(LogoutUserCommand.of());
  }

  @Test
  @DisplayName("当前用户：200，带 ID、邮箱、账号类型")
  void should_return_current_user() {
    when(userQueryService.currentAccount())
        .thenReturn(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.ACTIVE));

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.userId")
        .isEqualTo("42")
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.accountType")
        .isEqualTo("user");
  }

  @Test
  @DisplayName("没有当前用户：401，IDN-0401")
  void should_return_401_without_current_user() {
    when(userQueryService.currentAccount()).thenThrow(new AuthenticationRequiredException());

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0401");
  }
```

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests "*AuthControllerIT*"`
Expected: 编译失败（`CurrentUserResponse` 不存在、控制器没有这两个方法）。

- [ ] **Step 2: 实现**

`CurrentUserResponse.java`：

```java
package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 「当前用户」接口的响应体。`userId` 按全局 Jackson 设置输出成字符串。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
/// @param accountType 账号类型的 `code`，本版是 `user`
public record CurrentUserResponse(Long userId, String email, String accountType) {

  /// 创建响应。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @param accountType 账号类型的 `code`
  /// @return 响应
  public static CurrentUserResponse of(long userId, String email, String accountType) {
    return new CurrentUserResponse(userId, email, accountType);
  }
}
```

`AuthController.java`：加字段 `private final UserQueryService userQueryService;`（`@RequiredArgsConstructor` 会把它放进构造），类 Javadoc 改成「前台用户的注册、登录、登出与当前用户。字段校验在应用层做，这里只把请求转成命令；读操作直接走查询服务」，`@Tag` 的 description 改「前台用户的注册、登录、登出与当前用户」，加两个方法（import `GetMapping`、`AccountType`、`UserAccountReadModel`、`UserQueryService`、`LogoutUserCommand`、`CurrentUserResponse`）：

```java
  /// 登出。有当前用户就删会话，没有也返回 204：这条路由在网关上是公开的。
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    commandBus.handle(LogoutUserCommand.of());
  }

  /// 当前登录用户。没有当前用户、用户不存在或已封禁都是 401。
  ///
  /// @return 用户 ID、邮箱、账号类型
  @GetMapping("/me")
  public CurrentUserResponse me() {
    UserAccountReadModel account = userQueryService.currentAccount();
    return CurrentUserResponse.of(account.userId(), account.email(), AccountType.USER.getCode());
  }
```

- [ ] **Step 3: 跑 adapter 的测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:check :patra-api:patra-identity:patra-identity-adapter:integrationTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-adapter:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-adapter
git commit -m "feat(identity): 登出与当前用户接口 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 16: 会话策略配置、接入安全 starter 与装配

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-boot/build.gradle.kts`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/config/IdentityProperties.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/linqibin/patra/identity/config/IdentityConfiguration.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-container.yml`
- Test: `patra-api/patra-identity/patra-identity-boot/src/test/java/dev/linqibin/patra/identity/config/IdentityConfigurationTest.java`

**Interfaces:**
- Consumes: Task 4 的 `RedisSessionStore`；Task 8 的 `SessionLifetime`、`SessionLifetimePolicy`、`SessionIssuer`、`SessionStorePort`、`UserLoginRecordRepository`；starter-core 的 `Clock` Bean；Boot 自动配置的 `StringRedisTemplate`。
- Produces: 配置项 `patra.identity.session.max-sessions-per-user`、`patra.identity.session.lifetime.<account>.<client>.{idle,absolute}`；Bean `SessionLifetimePolicy`、`RedisSessionStore`、`SessionIssuer`；identity 带上安全 starter，`patra.security.identity-assertion.public-keys` 在 dev / container 配置里是 `${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}`。

- [ ] **Step 1: 加依赖**

`patra-identity-boot/build.gradle.kts` 的 `dependencies`：

```kotlin
    // 从网关签的断言取当前用户：过滤器链、CurrentUserPort、401 / 403 输出、JPA 审计人
    implementation(project(":patra-starters:patra-spring-boot-starter-security"))

    // 装配 RedisSessionStore
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    // 测试里自动注入测试公钥，并能签出带身份的请求头
    testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
```

- [ ] **Step 2: 改配置测试（失败的测试）**

`IdentityConfigurationTest.java` 整个文件换成：

```java
package dev.linqibin.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/// IdentityConfiguration 单元测试。
@DisplayName("IdentityConfiguration 单元测试")
class IdentityConfigurationTest {

  private static final String[] WEB_LIFETIME = {
    "patra.identity.session.lifetime.user.web.idle=30d",
    "patra.identity.session.lifetime.user.web.absolute=180d"
  };

  private final ApplicationContextRunner runner = bare().withPropertyValues(WEB_LIFETIME);

  /// 只有 IdentityConfiguration 和它依赖的 Bean，没有会话有效期的配置。
  ///
  /// @return 运行器
  private static ApplicationContextRunner bare() {
    return new ApplicationContextRunner()
        .withUserConfiguration(IdentityConfiguration.class)
        .withBean(CommonPasswordPort.class, () -> key -> false)
        .withBean(StringRedisTemplate.class, StringRedisTemplate::new)
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(UserLoginRecordRepository.class, () -> mock(UserLoginRecordRepository.class))
        .withBean(SessionStorePort.class, () -> mock(SessionStorePort.class))
        .withBean(CurrentUserPort.class, () -> mock(CurrentUserPort.class));
  }

  @Test
  @DisplayName("不配置时用默认值：5 次、15 分钟窗口、锁 15 分钟、在途 30 秒；每用户 10 条会话")
  void should_use_defaults() {
    runner.run(
        context -> {
          assertThat(context.getBean(LoginThrottlePolicy.class))
              .isEqualTo(
                  LoginThrottlePolicy.of(
                      5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30)));
          assertThat(context).hasSingleBean(PasswordHashingPort.class);
          assertThat(context).hasSingleBean(PasswordPolicy.class);
          SessionLifetimePolicy policy = context.getBean(SessionLifetimePolicy.class);
          assertThat(policy.maxSessionsPerUser()).isEqualTo(10);
          assertThat(policy.lifetimeFor(ClientType.WEB))
              .isEqualTo(SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180)));
          assertThat(context).hasSingleBean(RedisSessionStore.class);
          assertThat(context).hasSingleBean(SessionIssuer.class);
        });
  }

  @Test
  @DisplayName("配置项能覆盖默认值")
  void should_bind_overrides() {
    runner
        .withPropertyValues(
            "patra.identity.login-throttle.max-failures=3",
            "patra.identity.login-throttle.lock-duration=2s",
            "patra.identity.session.max-sessions-per-user=3",
            "patra.identity.session.lifetime.user.web.idle=7d")
        .run(
            context -> {
              LoginThrottlePolicy throttle = context.getBean(LoginThrottlePolicy.class);
              assertThat(throttle.maxFailures()).isEqualTo(3);
              assertThat(throttle.lockDuration()).isEqualTo(Duration.ofSeconds(2));
              SessionLifetimePolicy session = context.getBean(SessionLifetimePolicy.class);
              assertThat(session.maxSessionsPerUser()).isEqualTo(3);
              assertThat(session.lifetimeFor(ClientType.WEB).idle()).isEqualTo(Duration.ofDays(7));
            });
  }

  @Test
  @DisplayName("非法的限流配置让应用启动失败")
  void should_fail_on_invalid_throttle_configuration() {
    runner
        .withPropertyValues("patra.identity.login-throttle.max-failures=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("缺 user.web 那一行：启动失败")
  void should_fail_without_web_lifetime() {
    bare().run(context -> assertThat(context).hasFailed());
    bare()
        .withPropertyValues("patra.identity.session.lifetime.user.web.idle=30d")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("不认识的账号类型或客户端类型：启动失败")
  void should_fail_on_unknown_keys() {
    runner
        .withPropertyValues(
            "patra.identity.session.lifetime.user.app.idle=1d",
            "patra.identity.session.lifetime.user.app.absolute=2d")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues(
            "patra.identity.session.lifetime.staff.web.idle=1d",
            "patra.identity.session.lifetime.staff.web.absolute=2d")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("不活跃过期长于绝对过期、上限小于 1：启动失败")
  void should_fail_on_invalid_session_values() {
    runner
        .withPropertyValues("patra.identity.session.lifetime.user.web.idle=200d")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("patra.identity.session.max-sessions-per-user=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
```

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*IdentityConfigurationTest*"`
Expected: 编译失败（`IdentityProperties` 没有 `session`，配置类没有新 Bean）。

- [ ] **Step 3: 实现配置项和装配**

`IdentityProperties.java` 整个文件换成：

```java
package dev.linqibin.patra.identity.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// identity 的配置项，前缀 `patra.identity`。
///
/// @param loginThrottle 登录失败限制
/// @param passwordHashing 密码哈希
/// @param session 会话策略
@ConfigurationProperties(prefix = "patra.identity")
public record IdentityProperties(
    @DefaultValue LoginThrottle loginThrottle,
    @DefaultValue PasswordHashing passwordHashing,
    @DefaultValue Session session) {

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

  /// 会话策略。只在 identity 配，网关不配。
  ///
  /// @param maxSessionsPerUser 每用户的会话上限，所有账号类型一个数
  /// @param lifetime 有效期，两层键分别是账号类型和客户端类型的 `code`，比如 `user.web`
  public record Session(
      @DefaultValue("10") int maxSessionsPerUser, Map<String, Map<String, Lifetime>> lifetime) {}

  /// 一种客户端的会话有效期。
  ///
  /// @param idle 不活跃过期
  /// @param absolute 绝对过期
  public record Lifetime(Duration idle, Duration absolute) {}
}
```

`IdentityConfiguration.java` 加三个 Bean（import `AccountType`、`ClientType`、`SessionLifetime`、`SessionLifetimePolicy`、`UserLoginRecordRepository`、`SessionStorePort`、`SessionIssuer`、`RedisSessionStore`、`StringRedisTemplate`、`java.time.Clock`、`java.util.EnumMap`、`java.util.Map`），类 Javadoc 补「会话策略、Redis 会话存储、签发领域服务」：

```java
  /// 会话策略：把 `patra.identity.session.*` 变成领域的策略对象。
  ///
  /// 缺本服务要签发的那一行（`user.web`）、不认识的键、有效期不合法，都在这里让启动失败，
  /// 不等到第一次登录才报错。
  ///
  /// @param properties 配置
  /// @return 策略
  @Bean
  public SessionLifetimePolicy sessionLifetimePolicy(IdentityProperties properties) {
    IdentityProperties.Session session = properties.session();
    Map<String, Map<String, IdentityProperties.Lifetime>> configured =
        session.lifetime() == null ? Map.of() : session.lifetime();
    Map<ClientType, SessionLifetime> lifetimes = new EnumMap<>(ClientType.class);
    for (Map.Entry<String, Map<String, IdentityProperties.Lifetime>> byAccount :
        configured.entrySet()) {
      AccountType accountType =
          AccountType.fromCode(byAccount.getKey())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "patra.identity.session.lifetime 里的账号类型不认识: " + byAccount.getKey()));
      for (Map.Entry<String, IdentityProperties.Lifetime> byClient :
          byAccount.getValue().entrySet()) {
        ClientType clientType =
            ClientType.fromCode(byClient.getKey())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "patra.identity.session.lifetime."
                                + accountType.getCode()
                                + " 里的客户端类型不认识: "
                                + byClient.getKey()));
        IdentityProperties.Lifetime lifetime = byClient.getValue();
        if (lifetime == null || lifetime.idle() == null || lifetime.absolute() == null) {
          throw new IllegalStateException(
              "patra.identity.session.lifetime."
                  + accountType.getCode()
                  + "."
                  + clientType.getCode()
                  + " 要同时配 idle 和 absolute");
        }
        lifetimes.put(clientType, SessionLifetime.of(lifetime.idle(), lifetime.absolute()));
      }
    }
    if (!lifetimes.containsKey(ClientType.WEB)) {
      throw new IllegalStateException("缺少 patra.identity.session.lifetime.user.web");
    }
    return SessionLifetimePolicy.of(lifetimes, session.maxSessionsPerUser());
  }

  /// Redis 里的会话存储，和网关共用同一份契约。
  ///
  /// @param redis Redis 模板
  /// @param clock 时钟
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }

  /// 建会话的领域服务。
  ///
  /// @param records 登录记录仓储
  /// @param sessionStore 会话存储端口
  /// @param policy 会话策略
  /// @param clock 时钟
  /// @return 领域服务
  @Bean
  public SessionIssuer sessionIssuer(
      UserLoginRecordRepository records,
      SessionStorePort sessionStore,
      SessionLifetimePolicy policy,
      Clock clock) {
    return new SessionIssuer(records, sessionStore, policy, clock);
  }
```

`application.yml` 的 `patra.identity` 块加：

```yaml
    session:
      max-sessions-per-user: 10
      lifetime:
        user:
          web:
            idle: 30d
            absolute: 180d
```

`application-dev.yml` 末尾加（没有默认值：本地要先生成密钥，见 README）：

```yaml
patra:
  security:
    identity-assertion:
      # 网关的公钥（JWK Set JSON）。本地先用 generateIdentityAssertionKey 生成，见 README
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

`application-container.yml` 加同样的四行（注释改成「由 PAP-66 注入」）。

- [ ] **Step 4: 跑配置测试，确认通过**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:test`
Expected: PASS，6 个用例。

- [ ] **Step 5: 跑 boot 的全部集成测试，确认带着安全 starter 也能启动**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: PASS。`RedisUnavailableIT` 的「注册不依赖 Redis」在这一步会 FAIL（注册现在也建会话），这是预期的：Task 17 改写它。其余（启动、账号流程、哈希连接）必须 PASS；公钥由 testFixtures 的 `TestIdentityAssertionEnvironmentPostProcessor` 注入，不需要在测试配置里写。

- [ ] **Step 6: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-boot
git commit -m "feat(identity): 会话策略配置、接入安全 starter 与装配 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 17: 会话完整流程的端到端测试，Redis 不可用时注册回滚

**Files:**
- Create: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/SessionFlowIT.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/linqibin/patra/identity/RedisUnavailableIT.java`

**Interfaces:**
- Consumes: 全部前面的任务；安全 starter 测试支持的 `TestIdentity.headers(CurrentUser)`；`SessionToken.parse`。
- Produces: 无新代码；是 Done 判定 D2–D4 在 identity 一侧的证据。

- [ ] **Step 1: 写端到端测试**

`SessionFlowIT.java`：

```java
package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 会话的完整流程：真实的 PostgreSQL、Redis、Argon2 和安全过滤器链；身份断言用测试私钥签出。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("前台用户会话的完整流程")
class SessionFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Session-Pass-01";

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redis;

  @Test
  @DisplayName("注册即登录：响应带令牌，Redis 里只有哈希，登录记录和会话共用一个 ID")
  void should_register_with_session_token_stored_as_hash() {
    EntityExchangeResult<String> registered = register("flow.register@example.com");

    assertThat(registered.getStatus().value()).isEqualTo(201);
    String token = tokenOf(registered);
    long userId = userIdOf(registered);
    assertThat(token).matches("^patra_user_[A-Za-z0-9_-]{43}$");
    String hash = SessionToken.parse(token).orElseThrow().hash();
    Map<Object, Object> session = redis.opsForHash().entries("idn:session:user:" + hash);
    assertThat(session)
        .containsEntry("user_id", Long.toString(userId))
        .containsEntry("account_type", "user")
        .containsEntry("client_type", "web")
        .doesNotContainKey("device_id");
    assertThat(redis.keys("*")).allSatisfy(key -> assertThat(key).doesNotContain(token));
    long sessionId = Long.parseLong((String) session.get("session_id"));
    assertThat(redis.opsForHash().get("idn:user-sessions:user:" + userId, Long.toString(sessionId)))
        .isEqualTo(hash);
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT user_id, client_type, device_id, ended_at FROM idn_user_login_record WHERE id = ?",
            sessionId);
    assertThat(row)
        .containsEntry("user_id", userId)
        .containsEntry("client_type", "WEB")
        .containsEntry("device_id", null)
        .containsEntry("ended_at", null);
  }

  @Test
  @DisplayName("登录带客户端类型和设备标识；当前用户接口带断言 200、不带 401")
  void should_login_with_client_fields_and_read_current_user() {
    long userId = userIdOf(register("flow.login@example.com"));

    EntityExchangeResult<String> login =
        post(
            "/auth/login",
            Map.of(
                "email", "flow.login@example.com",
                "password", PASSWORD,
                "clientType", "web",
                "deviceId", " mac-safari "));

    assertThat(login.getStatus().value()).isEqualTo(200);
    String hash = SessionToken.parse(tokenOf(login)).orElseThrow().hash();
    assertThat(redis.opsForHash().get("idn:session:user:" + hash, "device_id"))
        .isEqualTo("mac-safari");
    long sessionId = latestSessionIdOf(userId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT device_id FROM idn_user_login_record WHERE id = ?", String.class, sessionId))
        .isEqualTo("mac-safari");

    EntityExchangeResult<String> me = me(assertionFor(userId, sessionId));
    assertThat(me.getStatus().value()).isEqualTo(200);
    JsonNode body = JSON.readTree(me.getResponseBody());
    assertThat(body.get("userId").asString()).isEqualTo(Long.toString(userId));
    assertThat(body.get("email").asString()).isEqualTo("flow.login@example.com");
    assertThat(body.get("accountType").asString()).isEqualTo("user");

    EntityExchangeResult<String> anonymous = me(new HttpHeaders());
    assertThat(anonymous.getStatus().value()).isEqualTo(401);
    assertThat(JSON.readTree(anonymous.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0401");
  }

  @Test
  @DisplayName("客户端类型不认识、设备标识太长：422 带原因码")
  void should_reject_invalid_client_fields() {
    register("flow.invalid@example.com");

    EntityExchangeResult<String> badType =
        post(
            "/auth/login",
            Map.of("email", "flow.invalid@example.com", "password", PASSWORD, "clientType", "app"));
    EntityExchangeResult<String> badDevice =
        post(
            "/auth/register",
            Map.of(
                "email", "flow.invalid2@example.com",
                "password", PASSWORD,
                "deviceId", "x".repeat(129)));

    assertThat(badType.getStatus().value()).isEqualTo(422);
    JsonNode typeError = JSON.readTree(badType.getResponseBody()).get("errors").get(0);
    assertThat(typeError.get("field").asString()).isEqualTo("clientType");
    assertThat(typeError.get("code").asString()).isEqualTo("INVALID_FORMAT");
    assertThat(badDevice.getStatus().value()).isEqualTo(422);
    JsonNode deviceError = JSON.readTree(badDevice.getResponseBody()).get("errors").get(0);
    assertThat(deviceError.get("field").asString()).isEqualTo("deviceId");
    assertThat(deviceError.get("code").asString()).isEqualTo("TOO_LONG");
  }

  @Test
  @DisplayName("登出后会话消失、记录标 LOGOUT；重复登出和不带身份登出都 204")
  void should_logout_idempotently() {
    EntityExchangeResult<String> registered = register("flow.logout@example.com");
    long userId = userIdOf(registered);
    String hash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders assertion = assertionFor(userId, sessionId);

    assertThat(logout(assertion).getStatus().value()).isEqualTo(204);

    assertThat(redis.hasKey("idn:session:user:" + hash)).isFalse();
    assertThat(redis.opsForHash().hasKey("idn:user-sessions:user:" + userId, Long.toString(sessionId)))
        .isFalse();
    assertThat(endReasonOf(sessionId)).isEqualTo("LOGOUT");
    assertThat(logout(assertion).getStatus().value()).isEqualTo(204);
    assertThat(logout(new HttpHeaders()).getStatus().value()).isEqualTo(204);
    assertThat(endReasonOf(sessionId)).isEqualTo("LOGOUT");
  }

  @Test
  @DisplayName("封禁后全部会话消失、记录标 BANNED，断言还在有效期内也拿不到当前用户；解封后能重新登录")
  void should_ban_and_revoke_all_sessions() {
    EntityExchangeResult<String> registered = register("flow.ban@example.com");
    long userId = userIdOf(registered);
    long firstSessionId = latestSessionIdOf(userId);
    EntityExchangeResult<String> second = login("flow.ban@example.com");
    long secondSessionId = latestSessionIdOf(userId);
    String firstHash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    String secondHash = SessionToken.parse(tokenOf(second)).orElseThrow().hash();

    restClient.post().uri("/admin/users/" + userId + "/ban").exchange().expectStatus().isNoContent();

    assertThat(redis.hasKey("idn:session:user:" + firstHash)).isFalse();
    assertThat(redis.hasKey("idn:session:user:" + secondHash)).isFalse();
    assertThat(redis.hasKey("idn:user-sessions:user:" + userId)).isFalse();
    assertThat(endReasonOf(firstSessionId)).isEqualTo("BANNED");
    assertThat(endReasonOf(secondSessionId)).isEqualTo("BANNED");
    assertThat(me(assertionFor(userId, firstSessionId)).getStatus().value()).isEqualTo(401);
    assertThat(login("flow.ban@example.com").getStatus().value()).isEqualTo(403);

    restClient.post().uri("/admin/users/" + userId + "/unban").exchange().expectStatus().isNoContent();
    assertThat(login("flow.ban@example.com").getStatus().value()).isEqualTo(200);
  }

  @Test
  @DisplayName("第 11 次登录挤掉最老的会话，它的记录标 REPLACED")
  void should_replace_oldest_session_over_cap() {
    EntityExchangeResult<String> registered = register("flow.cap@example.com");
    long userId = userIdOf(registered);
    long firstSessionId = latestSessionIdOf(userId);
    String firstHash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    for (int i = 0; i < 10; i++) {
      assertThat(login("flow.cap@example.com").getStatus().value()).isEqualTo(200);
    }

    assertThat(redis.opsForHash().size("idn:user-sessions:user:" + userId)).isEqualTo(10);
    assertThat(redis.hasKey("idn:session:user:" + firstHash)).isFalse();
    assertThat(endReasonOf(firstSessionId)).isEqualTo("REPLACED");
  }

  @Test
  @DisplayName("令牌只出现在注册和登录的响应里：当前用户接口和日志里都找不到")
  void should_never_echo_or_log_session_token(CapturedOutput output) {
    EntityExchangeResult<String> registered = register("flow.leak@example.com");
    long userId = userIdOf(registered);
    EntityExchangeResult<String> login = login("flow.leak@example.com");
    List<String> tokens = List.of(tokenOf(registered), tokenOf(login));
    long sessionId = latestSessionIdOf(userId);

    String me = me(assertionFor(userId, sessionId)).getResponseBody();
    String loggedOut = logout(assertionFor(userId, sessionId)).getResponseBody();

    for (String token : tokens) {
      assertThat(me).doesNotContain(token);
      assertThat(loggedOut == null ? "" : loggedOut).doesNotContain(token);
      assertThat(output.getAll()).doesNotContain(token);
    }
  }

  /// 注册，密码固定。
  ///
  /// @param email 邮箱
  /// @return 响应
  private EntityExchangeResult<String> register(String email) {
    return post("/auth/register", Map.of("email", email, "password", PASSWORD));
  }

  /// 登录，密码固定，不带客户端字段。
  ///
  /// @param email 邮箱
  /// @return 响应
  private EntityExchangeResult<String> login(String email) {
    return post("/auth/login", Map.of("email", email, "password", PASSWORD));
  }

  /// 发一个 JSON POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @return 响应
  private EntityExchangeResult<String> post(String uri, Map<String, Object> body) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new HashMap<>(body))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 登出，带或不带断言。
  ///
  /// @param headers 请求头
  /// @return 响应
  private EntityExchangeResult<String> logout(HttpHeaders headers) {
    return restClient
        .post()
        .uri("/auth/logout")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 当前用户，带或不带断言。
  ///
  /// @param headers 请求头
  /// @return 响应
  private EntityExchangeResult<String> me(HttpHeaders headers) {
    return restClient
        .get()
        .uri("/auth/me")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 用测试私钥签一条断言，模拟网关查完会话后的转发。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return `Authorization: Bearer` 头
  private static HttpHeaders assertionFor(long userId, long sessionId) {
    return TestIdentity.headers(
        CurrentUser.of(userId, sessionId, AccountType.USER, ClientType.WEB));
  }

  /// 响应里的令牌。
  ///
  /// @param result 注册或登录的响应
  /// @return 令牌
  private static String tokenOf(EntityExchangeResult<String> result) {
    return JSON.readTree(result.getResponseBody()).get("sessionToken").asString();
  }

  /// 响应里的用户 ID。
  ///
  /// @param result 注册或登录的响应
  /// @return 用户 ID
  private static long userIdOf(EntityExchangeResult<String> result) {
    return Long.parseLong(JSON.readTree(result.getResponseBody()).get("userId").asString());
  }

  /// 用户最新一条登录记录的 ID，也就是最新会话的 ID。
  ///
  /// @param userId 用户 ID
  /// @return 记录 ID
  private long latestSessionIdOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM idn_user_login_record WHERE user_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        userId);
  }

  /// 登录记录的结束原因。
  ///
  /// @param sessionId 记录 ID
  /// @return 结束原因；未结束时为 `null`
  private String endReasonOf(long sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT end_reason FROM idn_user_login_record WHERE id = ?", String.class, sessionId);
  }
}
```

`ArrayList` 没用到就删掉 import。`containsEntry("user_id", userId)` 里 `user_id` 从 JDBC 回来是 `Long`，和 `long` 自动装箱后相等。

- [ ] **Step 2: 改写 `RedisUnavailableIT`**

类 Javadoc 改成「Redis 连不上时，注册和登录都返回 503：注册在建会话时失败并整体回滚，登录在失败限制处失败，都不放行」；用例换成：

```java
  @Test
  @DisplayName("注册回滚、登录拒绝：都是 503 和 IDN-0503，库里没有那个用户")
  void should_return_503_and_roll_back_registration_when_redis_is_unreachable() {
    EntityExchangeResult<String> registered =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(registered.getStatus().value()).isEqualTo(503);
    assertThat(JSON.readTree(registered.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0503");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user WHERE email = ?", Long.class, "no-redis@example.com"))
        .isZero();

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
        .isEqualTo("IDN-0503");
  }
```

补 import：`static org.assertj.core.api.Assertions.assertThat`、`org.springframework.jdbc.core.JdbcTemplate`（加字段 `@Autowired private JdbcTemplate jdbcTemplate;`）、`org.springframework.test.web.servlet.client.EntityExchangeResult`、`tools.jackson.databind.json.JsonMapper`（加常量 `private static final JsonMapper JSON = JsonMapper.builder().build();`）。原来登录用例里关于 503 的断言写法保留风格。

- [ ] **Step 3: 跑 boot 的全部集成测试**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: PASS，`SessionFlowIT` 7 个用例、`RedisUnavailableIT` 1 个、其余不变。

- [ ] **Step 4: 格式化并提交**

Run: `./gradlew :patra-api:patra-identity:patra-identity-boot:spotlessApply`

```bash
git add patra-api/patra-identity/patra-identity-boot
git commit -m "test(identity): 会话完整流程的端到端测试，Redis 不可用时注册回滚 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 18: 两份 README、设计文档写回实测结果、全量回归

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/README.md`
- Modify: `patra-api/patra-identity/README.md`
- Modify: `docs/patra/specs/2026-10-09-identity-session-design.md`（第 15 节写回实测结果）

**Interfaces:**
- Consumes: 全部前面的任务。
- Produces: PAP-65 据以接入的契约说明；本地跑 identity 的步骤。

- [ ] **Step 1: 写会话模块的 README**

`patra-api/patra-identity/patra-identity-session/README.md`：

```markdown
# patra-identity-session

identity 与网关共用的会话存储契约：令牌格式、Redis 键、四段 Lua 和 `RedisSessionStore`。
只有这两个消费者；其他服务只认网关签的身份断言（`patra-spring-boot-starter-security`），不碰 Redis。

设计：`docs/patra/specs/2026-10-09-identity-session-design.md` 第 6、7 节。

## 令牌

`patra_user_` + 43 个 base64url 字符（32 字节 `SecureRandom`），总长 54。前缀只说明账号类型，
网关靠它决定查哪个键空间。`SessionToken.parse` 对格式不对的值返回空，调用方按匿名处理。
Redis 里只存 `SessionToken.hash()`：整个令牌 UTF-8 的 SHA-256，小写十六进制。令牌不进日志，
`toString()` 只有前缀。

## Redis 键

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:session:user:<哈希>` | HASH | 一条会话 | `min(idle_timeout_ms, expires_at − now)`，续期时重算 |
| `idn:user-sessions:user:<userId>` | HASH | `session_id → <哈希>` | 不小于该用户最新会话的绝对有效期 |

会话字段（都是字符串）：`user_id`、`session_id`、`account_type`（`user`）、`client_type`（`web`）、
`device_id`（可选，没有就没这个字段）、`created_at`、`last_active_at`、`expires_at`（epoch 毫秒）、
`idle_timeout_ms`。

## 操作

| 方法 | 脚本 | 做什么 |
|---|---|---|
| `create(NewSession)` | `session-create.lua` | 清掉索引里指向不存在会话的条目；到上限按会话 ID 从小到大挤掉多出来的；写会话和索引。返回令牌和被挤掉的 ID |
| `findAndTouch(SessionToken)` | `session-touch.lua` | 读会话；过了绝对过期返回空并删键；距上次写入满 60 秒（`RENEW_INTERVAL`）才写回 `last_active_at` 和 TTL |
| `delete(accountType, userId, sessionId)` | `session-delete.lua` | 按索引找到哈希删会话；真的删掉了返回 `true` |
| `deleteAll(accountType, userId)` | `session-delete-all.lua` | 删该用户全部会话和索引；返回真的删掉了的会话 ID |

时间由 Java 按注入的 `Clock` 传进脚本，脚本不调 `TIME`。会话 ID 的大小按「字符串长度，再字典序」比，
雪花 ID 转成 Lua 的 double 会丢精度。

## 网关怎么用（PAP-65）

```java
@Bean
RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
  return new RedisSessionStore(redis, clock);
}
```

每个请求：取 `Authorization: Bearer` 的值 → `SessionToken.parse`，空按匿名 → `findAndTouch`，
空按匿名 → `StoredSession.toCurrentUser()` 建 `CurrentUserAuthentication`，再签断言。
网关不配任何有效期：续期只照会话里的字段做。

## 错误

Redis 连不上、超时、`LOADING` / `READONLY` / `BUSY` / `MASTERDOWN` 转成
`SessionStoreUnavailableException`（`DEP_UNAVAILABLE`，503）。判定在 `TransientRedisFailures`，
identity 的登录限流也用它。其他 Redis 异常（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，原样抛出。

## 限制

- 只支持单机 Redis：会话键在脚本里由前缀拼出来，Redis Cluster 要求所有键事先声明。
- 索引里过期会话留下的条目在该用户下一次登录时清理，封禁时整键删除。

## 测试

单测 `./gradlew :patra-api:patra-identity:patra-identity-session:test`；集成测试
`…:integrationTest` 用测试 starter 的 `RedisContainerInitializer`（`redis:7.0.15`），要本机 Docker 在线。
```

- [ ] **Step 2: 改 identity 的 README**

`patra-api/patra-identity/README.md`：

- 「1. 模块」的表里加一行：`patra-identity-session` — 「与网关共用的会话存储契约（令牌、Redis 键、Lua、`RedisSessionStore`），见它自己的 README」。
- 「2. 接口」：注册、登录的成功响应改成 `{ "sessionToken", "userId", "email" }`，请求体加可选的 `clientType`（不传按 `web`）和 `deviceId`；加 `POST /auth/logout`（204，有当前用户就删会话，没有也 204）和 `GET /auth/me`（`{ "userId", "email", "accountType" }`，需要登录）。
- 「6. 错误码」：加 `IDN-0401`（`/auth/me` 没有当前用户、用户不存在或已封禁，文案 `Authentication required`）、`IDN-0409` 的乐观锁冲突（文案「用户正被其他操作修改，请重试」）；`IDN-0503` 补「会话存储暂时不可用」。
- 新加一节「8. 会话」：

```markdown
## 8. 会话

登录、注册成功后签发不透明令牌，会话写进 Redis，登录记录写进 `idn_user_login_record`（ID 就是会话 ID）。
令牌、键和脚本见 `patra-identity-session/README.md`。

策略只在这里配，网关不配：

```yaml
patra:
  identity:
    session:
      max-sessions-per-user: 10      # 超过挤掉最老的，记录标 REPLACED
      lifetime:
        user:
          web:
            idle: 30d                # 不活跃过期；网关每个请求续期，60 秒内只读不写
            absolute: 180d           # 绝对过期
```

缺 `user.web` 这一行、键不认识、`idle` 长于 `absolute`，应用启动失败。

登出删会话并把记录标成 `LOGOUT`；封禁删该用户全部会话并把记录标成 `BANNED`。会话过期不回写记录：
记录里 `ended_at` 为空而 Redis 里没有会话，就是过期了。
```

- 「7. 本地运行和测试」加：

```markdown
identity 从网关签的身份断言取当前用户，启动时必须配网关的公钥：

```bash
./gradlew :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut=/tmp/identity-assertion-private.jwk
```

标准输出的公钥 JWK Set 设进环境变量 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`（`application-dev.yml` 没有默认值），
私钥文件交给本地网关后删掉。集成测试不需要这一步：安全 starter 的测试支持会自动注入测试公钥。
```

- [ ] **Step 3: 设计文档第 15 节写回实测结果**

把 Task 4、6、9、16 跑出来的事实逐条写进 `docs/patra/specs/2026-10-09-identity-session-design.md` 第 15 节：`LettuceExceptionConverter` 实际包成什么、`touch` 返回空时 Java 拿到的是什么、雪花 ID 的位数、回滚是否连用户和凭据一起、PAP-63 的测试是否要补公钥。每条写「实测：…」，不留问句。

- [ ] **Step 4: 全量回归**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL。

Run: `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: BUILD SUCCESSFUL。

Run: `./gradlew dumpModuleGraph && git status --short patra-infra/cd/module-graph.json`
Expected: 没有改动（Task 1 已经生成过）。

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-identity/README.md patra-api/patra-identity/patra-identity-session/README.md docs/patra/specs/2026-10-09-identity-session-design.md
git commit -m "docs(accounts): 会话模块与 identity 的 README，设计文档写回实测结果 (PAP-64)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## 自查记录

- 规格覆盖：§5 模块 → Task 1、10、16；§6 令牌与存储 → Task 1、4、5、6；§7 接口 → Task 2、3、4、6；§8 领域模型 → Task 7、8、9、12；§9 流程与事务 → Task 13、14；§10 接口 → Task 13、15；§11 错误契约 → Task 2、10、11、12；§12 配置与接入 → Task 16、18；§13 令牌不进日志 → Task 1、13、17；§14 测试策略 → 各任务；§15 实测 → Task 18；§16 约束 → 已写进 Linear PAP-65。
- 评审焦点的五条各有归属的测试（Task 1、7、5、4、6）。
- 类型一致：`NewSession` 用 `createdAt` / `expiresAt`（Task 3、4、10）；`NewUserSession` 带 `accountType`（Task 8、10）；`IssuedUserSession.token` 是字符串（Task 8、10、13）；`SessionStorePort.revoke` / `revokeAll`（Task 8、14）；`UserQueryService.currentAccount()`（Task 12、15）；`LoginUserHandler` / `RegisterUserHandler` 的构造顺序（Task 13 的测试与实现一致）。
