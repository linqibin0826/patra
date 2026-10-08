# 签名身份断言（PAP-70）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把安全 starter 从「五个明文身份头 + 共享内部令牌」改成「网关签名的身份断言」：下游从 `Authorization: Bearer` 里的 JWT 验签得到当前用户，内部客户端替用户调用时原样转发断言。

**Architecture:** 断言的格式、签名器、验签器都在 `patra-spring-boot-starter-security` 里，网关（PAP-65）和测试支持共用同一份签名代码；下游的过滤器链仍用 `AuthenticationFilter`，只是把转换器换成验签的。http-interface starter 新增一个只给内部客户端用的拦截器 SPI，安全 starter 提供转发断言的实现。密钥用 JWK JSON，私钥只在网关，下游只配公钥。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Security 7.0.7（`spring-security-oauth2-jose`，内含 Nimbus JOSE + JWT）、JUnit 5 + AssertJ + Mockito、Testcontainers（PostgreSQL 17）、Gradle 9.5 convention plugins。

**Spec:** `docs/patra/specs/2026-10-05-security-starter-design.md`（2026-10-08 改写版；第 6、8.2、8.3、10、13、14、15、17 节是本计划的依据）。关联：`docs/patra/release-specs/v0.8-accounts.md` 决策 C、G；Linear PAP-70。

## Global Constraints

- 工作目录是 worktree `/Users/linqibin/Projects/Products/patra/.claude/worktrees/v0.8-accounts-api`，分支 `feat/v0.8-accounts-api`。下文所有命令用 `W=/Users/linqibin/Projects/Products/patra/.claude/worktrees/v0.8-accounts-api` 和 `"$W/gradlew" -p "$W" …` 执行，不要 `cd`（cd 出去会把主工作目录换成主检出）。
- 模块缩写：`SEC` = `:patra-starters:patra-spring-boot-starter-security`，`HTTPI` = `:linqibin-commons:linqibin-spring-boot-starter-http-interface`。源码根：`patra-starters/patra-spring-boot-starter-security/src`、`linqibin-commons/linqibin-spring-boot-starter-http-interface/src`。
- `check` 含 `spotlessJavaCheck`（google-java-format）：每个任务跑测试前先 `"$W/gradlew" -p "$W" <模块>:spotlessApply`，spotless 改了文件要一起提交。
- 代码规范：所有方法写 `///` 风格 JavaDoc（Markdown），中文；不用全类名；record 参数 ≤ 4 个用 `of()`、≥ 5 个用 `@Builder`；空集合用 `List.of()`。
- Spring Security 只经安全 starter 进入 classpath；linqibin-commons 的模块不依赖 `patra-*` 模块（`checkBoundary` 验证）。
- 断言格式常量（spec 6.1）：`alg` = `ES256`；`typ` = `patra-identity+jwt`；`iss` = `patra-gateway`；`aud` = `patra`；`sub` / `sid` 是十进制字符串；`account_type` / `client_type` 是枚举的字符串；有效期 60 秒；验签容差 30 秒。
- 密钥只用 JWK JSON，曲线只用 P-256，`kid` 是公钥指纹；私钥不出现在下游的任何配置、测试资源或仓库文件里；测试密钥每次在 JVM 里现生成。
- 断言无效（签名、`typ`、`iss`、`aud`、过期、`Authorization` 不是单个 Bearer）→ 401；签名对但内容不合法 → 500；没带断言 → 匿名。
- 转发只发生在 `RestClientFactory` 创建的内部客户端上；`CurrentUserRunner` 放进去的用户没有凭据，不转发。
- TDD：每个任务先写失败的测试、看它失败、再写最少的实现。RED 的预期别点名具体符号：javac 先报 import 和签名里缺的类型。
- 提交信息：中文动词开头，subject 结尾带 `(PAP-70)`，正文末尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。只提交自己的路径；提交前 `git -C "$W" diff --cached --stat` 核对。
- 公开仓库：不在文档、注释、提交信息里粘贴任何真实密钥；测试里的密钥只在内存里。

## Review Focus

1. 下游配置里误把网关的私钥 JWK 当公钥配进来：应用必须启动失败并指明配置项（任务 5 的 `should_reject_private_key`）。
2. 攻击者把断言的 `alg` 改成 `none` 或 `HS256`（用公钥当 HMAC 密钥）：验签器必须拒绝（任务 3 的 `should_reject_unsecured_and_hmac_tokens`）。
3. `sub` 是 `"1001.0"`、`"+1001"`、`"01001"` 或 20 位数字：按内容不合法返回 500，不能被解析成别的用户（任务 1 的参数化用例）。
4. 业务代码在内部调用前自己设了 `Authorization` 头：转发拦截器覆盖成断言，不能两个头并存（任务 9 的 `should_overwrite_existing_authorization_header`）。
5. 断言刚过期 20 秒仍在容差内要放行，过期 31 秒必须拒绝；时钟由 `Clock` 注入（任务 3 的 `should_apply_clock_skew_window`）。

---

### Task 1: 依赖与断言格式 `IdentityAssertionClaims`

**Files:**
- Modify: `patra-starters/patra-spring-boot-starter-security/build.gradle.kts:24-37`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaims.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/MalformedIdentityException.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaimsTest.java`

**Interfaces:**
- Consumes: `CurrentUser.of(long, long, AccountType, ClientType)`、`AccountType.fromCode(String)`、`ClientType.fromCode(String)`（`patra-common-security`，已有）。
- Produces: 常量 `IdentityAssertionClaims.TYPE / ISSUER / AUDIENCE / SESSION_ID / ACCOUNT_TYPE / CLIENT_TYPE`（String）、`LIFETIME`（Duration，60 秒）；`static CurrentUser toCurrentUser(Jwt jwt)`，内容不合法时抛 `MalformedIdentityException`。

- [ ] **Step 1: 加依赖**

把 `patra-starters/patra-spring-boot-starter-security/build.gradle.kts` 的 `dependencies` 块改成：

```kotlin
dependencies {
    // 当前用户的抽象
    api(project(":patra-api:patra-common:patra-common-security"))

    // 统一错误格式（ProblemDetailAdapter、GlobalRestExceptionHandler）
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    // Spring Security（版本由 Spring Boot BOM 管理）
    api("org.springframework.boot:spring-boot-starter-security")

    // JWT 的签名与验签（JwtDecoder、校验器，内含 Nimbus JOSE + JWT）
    api("org.springframework.security:spring-security-oauth2-jose")

    // 审计人接入：classpath 上有 starter-jpa 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))

    // 内部客户端转发断言：classpath 上有 http-interface starter 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))

    // 测试支持（testFixtures）：没有它，使用方的 @WebMvcTest 里没有安全过滤器
    "testFixturesApi"("org.springframework.boot:spring-boot-security-test")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
    // 转发拦截器的单元测试要能看到 InternalCallInterceptor
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))

    // 集成测试要测审计列和转发，需要 starter-jpa 与 http-interface starter
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))
}
```

- [ ] **Step 2: 写失败的测试**

`src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaimsTest.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

/// IdentityAssertionClaims 单元测试。
@DisplayName("IdentityAssertionClaims 单元测试")
class IdentityAssertionClaimsTest {

  @Test
  @DisplayName("格式常量是设计定下的值")
  void should_expose_format_constants() {
    assertThat(IdentityAssertionClaims.TYPE).isEqualTo("patra-identity+jwt");
    assertThat(IdentityAssertionClaims.ISSUER).isEqualTo("patra-gateway");
    assertThat(IdentityAssertionClaims.AUDIENCE).isEqualTo("patra");
    assertThat(IdentityAssertionClaims.LIFETIME.toSeconds()).isEqualTo(60);
  }

  @Test
  @DisplayName("声明齐全且合法：还原出当前用户")
  void should_build_current_user_from_claims() {
    CurrentUser user = IdentityAssertionClaims.toCurrentUser(jwt(validClaims()));

    assertThat(user).isEqualTo(CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "0", "-1", "+1001", "01001", "1001.0", "99999999999999999999"})
  @DisplayName("sub 不是规范写法的正整数：内容不合法")
  void should_reject_invalid_subject(String subject) {
    Map<String, Object> claims = validClaims();
    claims.put("sub", subject);

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("sub");
  }

  @Test
  @DisplayName("缺 sid：内容不合法")
  void should_reject_missing_session_id() {
    Map<String, Object> claims = validClaims();
    claims.remove("sid");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("sid");
  }

  @Test
  @DisplayName("账号类型不认识：内容不合法")
  void should_reject_unknown_account_type() {
    Map<String, Object> claims = validClaims();
    claims.put("account_type", "staff");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("account_type");
  }

  @Test
  @DisplayName("客户端类型不认识：内容不合法")
  void should_reject_unknown_client_type() {
    Map<String, Object> claims = validClaims();
    claims.put("client_type", "ios");

    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt(claims)))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining("client_type");
  }

  /// 合法的载荷，测试各自改坏一处。
  private static Map<String, Object> validClaims() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", "patra-gateway");
    claims.put("aud", "patra");
    claims.put("sub", "1001");
    claims.put("sid", "2001");
    claims.put("account_type", "user");
    claims.put("client_type", "web");
    return claims;
  }

  /// 不验签的 Jwt 对象：这里只测声明到当前用户的转换。
  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "ES256");
    claims.forEach(builder::claim);
    return builder.build();
  }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionClaimsTest*"`
Expected: 编译失败，报 `assertion` 包里的类型找不到。

- [ ] **Step 4: 写实现**

`src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaims.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

/// 身份断言的格式：头和载荷里的固定值、自定义声明的名字，以及从已验签的断言得到当前用户。
///
/// 签名器、验签器、测试支持都只认这里的常量，断言的格式只有这一份定义。
public final class IdentityAssertionClaims {

  /// 头里的 `typ`。显式类型，防止和别的 JWT 混用。
  public static final String TYPE = "patra-identity+jwt";

  /// 载荷里的 `iss`：只有网关签发断言。
  public static final String ISSUER = "patra-gateway";

  /// 载荷里的 `aud`：整个 Patra 是一个信任域，断言可以在服务之间转发。
  public static final String AUDIENCE = "patra";

  /// 会话 ID 的声明名。
  public static final String SESSION_ID = "sid";

  /// 账号类型的声明名。
  public static final String ACCOUNT_TYPE = "account_type";

  /// 客户端类型的声明名。
  public static final String CLIENT_TYPE = "client_type";

  /// 断言的有效期：只覆盖一次请求和它的同步调用。
  public static final Duration LIFETIME = Duration.ofSeconds(60);

  /// 规范写法的正整数：没有符号、没有前导零、只有 ASCII 数字、最多 19 位。
  private static final Pattern POSITIVE_LONG = Pattern.compile("[1-9][0-9]{0,18}");

  /// 工具类，不允许实例化。
  private IdentityAssertionClaims() {}

  /// 从已验签的断言得到当前用户。
  ///
  /// @param jwt 已验签的断言
  /// @return 当前用户
  /// @throws MalformedIdentityException `sub`、`sid` 不是正整数，或两个类型不认识时
  public static CurrentUser toCurrentUser(Jwt jwt) {
    long userId = positiveLong(jwt.getSubject(), "sub");
    long sessionId = positiveLong(jwt.getClaimAsString(SESSION_ID), SESSION_ID);
    AccountType accountType =
        AccountType.fromCode(jwt.getClaimAsString(ACCOUNT_TYPE))
            .orElseThrow(() -> new MalformedIdentityException(ACCOUNT_TYPE + " 的值不认识"));
    ClientType clientType =
        ClientType.fromCode(jwt.getClaimAsString(CLIENT_TYPE))
            .orElseThrow(() -> new MalformedIdentityException(CLIENT_TYPE + " 的值不认识"));
    return CurrentUser.of(userId, sessionId, accountType, clientType);
  }

  /// 把规范写法的正整数解析成 long。
  ///
  /// @param value 声明的值，可能为 `null`
  /// @param claim 声明名，只用于错误信息
  /// @return 解析结果
  /// @throws MalformedIdentityException 写法不规范或超出 Long 范围时
  private static long positiveLong(String value, String claim) {
    if (value == null || !POSITIVE_LONG.matcher(value).matches()) {
      throw new MalformedIdentityException(claim + " 不是正整数");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException overflow) {
      throw new MalformedIdentityException(claim + " 超出范围");
    }
  }
}
```

把 `MalformedIdentityException` 的类注释和消息前缀改成断言的说法（类名、父类、构造器签名都不变）：

```java
/// 断言签名正确、但内容不合法时抛出：`sub`、`sid` 不是正整数，或账号 / 客户端类型不认识。
///
/// 只可能是网关自己的缺陷，所以按服务端错误（500）处理，不伪装成 401。
public class MalformedIdentityException extends AuthenticationServiceException {

  /// 创建异常。
  ///
  /// @param reason 不合法的原因，只含声明名，不含声明的值
  public MalformedIdentityException(String reason) {
    super("身份断言的内容不合法: " + reason);
  }
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionClaimsTest*"`
Expected: BUILD SUCCESSFUL，7 个用例（含 7 个参数化子用例）通过。`AccountType.fromCode(null)` 返回空结果，所以缺声明的用例也走「不认识」分支。

- [ ] **Step 6: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/build.gradle.kts \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaims.java \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/MalformedIdentityException.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionClaimsTest.java
git -C "$W" commit -m "定义身份断言的格式，从已验签的断言得到当前用户 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: 签名器 `IdentityAssertionSigner`

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSigner.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSignerTest.java`

**Interfaces:**
- Consumes: 任务 1 的常量；Nimbus `ECKey`、`ECDSASigner`、`SignedJWT`。
- Produces: `new IdentityAssertionSigner(ECKey privateKey, Clock clock)`（密钥不含私钥、没有 `kid`、不是 P-256 时抛 `IllegalArgumentException`）；`String sign(CurrentUser user)` 返回紧凑序列化的断言。

- [ ] **Step 1: 写失败的测试**

`src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSignerTest.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IdentityAssertionSigner 单元测试。
@DisplayName("IdentityAssertionSigner 单元测试")
class IdentityAssertionSignerTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  @Test
  @DisplayName("签出的断言：头、载荷、签名都符合设计")
  void should_sign_assertion_with_designed_header_and_claims()
      throws JOSEException, ParseException {
    ECKey key = generateKey();
    IdentityAssertionSigner signer = new IdentityAssertionSigner(key, CLOCK);

    SignedJWT jwt = SignedJWT.parse(signer.sign(USER));

    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getType()).isEqualTo(new JOSEObjectType("patra-identity+jwt"));
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(key.getKeyID());
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo("patra-gateway");
    assertThat(claims.getAudience()).containsExactly("patra");
    assertThat(claims.getSubject()).isEqualTo("1001");
    assertThat(claims.getStringClaim("sid")).isEqualTo("2001");
    assertThat(claims.getStringClaim("account_type")).isEqualTo("user");
    assertThat(claims.getStringClaim("client_type")).isEqualTo("web");
    assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
    assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plusSeconds(60)));
    assertThat(claims.getClaims()).doesNotContainKeys("nbf", "jti");
    assertThat(jwt.verify(new ECDSAVerifier(key.toPublicJWK()))).isTrue();
  }

  @Test
  @DisplayName("只有公钥的密钥不能签名")
  void should_reject_public_only_key() throws JOSEException {
    ECKey publicOnly = generateKey().toPublicJWK();

    assertThatThrownBy(() -> new IdentityAssertionSigner(publicOnly, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("私钥");
  }

  @Test
  @DisplayName("没有 kid 的密钥不能签名")
  void should_reject_key_without_kid() throws JOSEException {
    ECKey withoutKid = new ECKeyGenerator(Curve.P_256).generate();

    assertThatThrownBy(() -> new IdentityAssertionSigner(withoutKid, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kid");
  }

  @Test
  @DisplayName("不是 P-256 的密钥不能签名")
  void should_reject_key_on_other_curve() throws JOSEException {
    ECKey p384 = new ECKeyGenerator(Curve.P_384).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(() -> new IdentityAssertionSigner(p384, CLOCK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
  }

  /// 带 kid（公钥指纹）的 P-256 密钥对。
  static ECKey generateKey() throws JOSEException {
    return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionSignerTest*"`
Expected: 编译失败，报 `IdentityAssertionSigner` 找不到。

- [ ] **Step 3: 写实现**

`src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSigner.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/// 身份断言的签名器。网关查完会话后用它签，测试支持用临时密钥构造它。
///
/// 不做成自动配置的 Bean：签名用的配置项只存在于网关自己的配置里，下游拿不到私钥。
public final class IdentityAssertionSigner {

  private final ECKey key;
  private final JWSSigner signer;
  private final Clock clock;

  /// 创建签名器。
  ///
  /// @param key 含私钥的 P-256 JWK，必须带 `kid`
  /// @param clock 时钟，决定 `iat` 和 `exp`
  /// @throws IllegalArgumentException 密钥不含私钥、没有 `kid` 或不是 P-256 时
  public IdentityAssertionSigner(ECKey key, Clock clock) {
    this.key = Objects.requireNonNull(key, "key 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
    if (!key.isPrivate()) {
      throw new IllegalArgumentException("签名密钥必须含私钥");
    }
    if (key.getKeyID() == null || key.getKeyID().isBlank()) {
      throw new IllegalArgumentException("签名密钥必须带 kid");
    }
    if (!Curve.P_256.equals(key.getCurve())) {
      throw new IllegalArgumentException("签名密钥必须是 P-256 曲线");
    }
    try {
      this.signer = new ECDSASigner(key);
    } catch (JOSEException e) {
      throw new IllegalArgumentException("签名密钥不可用", e);
    }
  }

  /// 为当前用户签一个断言。每次调用都现签，不缓存。
  ///
  /// @param user 当前用户
  /// @return 紧凑序列化的断言
  /// @throws IllegalStateException 签名失败时；密钥坏了是配置错误，不是请求错误
  public String sign(CurrentUser user) {
    Objects.requireNonNull(user, "user 不能为 null");
    Instant now = clock.instant();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(IdentityAssertionClaims.ISSUER)
            .audience(IdentityAssertionClaims.AUDIENCE)
            .subject(Long.toString(user.userId()))
            .claim(IdentityAssertionClaims.SESSION_ID, Long.toString(user.sessionId()))
            .claim(IdentityAssertionClaims.ACCOUNT_TYPE, user.accountType().getCode())
            .claim(IdentityAssertionClaims.CLIENT_TYPE, user.clientType().getCode())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(IdentityAssertionClaims.LIFETIME)))
            .build();
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType(IdentityAssertionClaims.TYPE))
            .keyID(key.getKeyID())
            .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("签身份断言失败", e);
    }
    return jwt.serialize();
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionSignerTest*"`
Expected: BUILD SUCCESSFUL，4 个用例通过。

- [ ] **Step 5: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSigner.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionSignerTest.java
git -C "$W" commit -m "加身份断言的签名器 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: 验签器 `IdentityAssertionDecoders`

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecoders.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecodersTest.java`

**Interfaces:**
- Consumes: 任务 1 的常量；任务 2 的 `IdentityAssertionSigner`（测试里用来签合法断言）。
- Produces: `static JwtDecoder forPublicKeys(JWKSet publicKeys, Clock clock)`。返回的解码器只接受 ES256、`typ` / `iss` / `aud` 正确、未过期（容差 30 秒）的断言，按头里的 `kid` 在集合里选公钥；任何一项不过 `decode` 抛 `JwtException`。

- [ ] **Step 1: 写失败的测试**

`src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecodersTest.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/// IdentityAssertionDecoders 单元测试。
@DisplayName("IdentityAssertionDecoders 单元测试")
class IdentityAssertionDecodersTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final ECKey KEY_A = generate();
  private static final ECKey KEY_B = generate();
  private static final JWKSet PUBLIC_A = new JWKSet(KEY_A.toPublicJWK());

  @Test
  @DisplayName("已知公钥签的断言：接受，声明原样可读")
  void should_accept_assertion_signed_by_known_key() {
    Jwt jwt = decoder(PUBLIC_A, NOW).decode(signer(KEY_A).sign(USER));

    assertThat(jwt.getSubject()).isEqualTo("1001");
    assertThat(jwt.getClaimAsString("sid")).isEqualTo("2001");
    assertThat(jwt.getAudience()).containsExactly("patra");
  }

  @Test
  @DisplayName("未知密钥签的断言：拒绝")
  void should_reject_assertion_signed_by_unknown_key() {
    String assertion = signer(KEY_B).sign(USER);

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("过期 20 秒在容差内放行，过期 31 秒拒绝")
  void should_apply_clock_skew_window() {
    String assertion = signer(KEY_A).sign(USER);

    assertThat(decoder(PUBLIC_A, NOW.plusSeconds(80)).decode(assertion)).isNotNull();
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW.plusSeconds(91)).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("typ 不是 patra-identity+jwt 或缺省：拒绝")
  void should_reject_wrong_or_missing_type() throws JOSEException {
    String plainType =
        signed(KEY_A, header(KEY_A).type(JOSEObjectType.JWT).build(), claims().build());
    String noType =
        signed(KEY_A, header(KEY_A).type(null).build(), claims().build());

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(plainType))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(noType))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("iss 或 aud 不对：拒绝")
  void should_reject_wrong_issuer_or_audience() throws JOSEException {
    String wrongIssuer =
        signed(KEY_A, header(KEY_A).build(), claims().issuer("someone-else").build());
    String wrongAudience =
        signed(KEY_A, header(KEY_A).build(), claims().audience("other-system").build());

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(wrongIssuer))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(wrongAudience))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("alg 为 none 或 HS256：拒绝")
  void should_reject_unsecured_and_hmac_tokens() throws JOSEException {
    String unsecured = new PlainJWT(claims().build()).serialize();
    SignedJWT hmac =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType("patra-identity+jwt"))
                .keyID(KEY_A.getKeyID())
                .build(),
            claims().build());
    hmac.sign(new MACSigner(new byte[32]));

    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(unsecured))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder(PUBLIC_A, NOW).decode(hmac.serialize()))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("集合里有两把公钥时按 kid 选钥；kid 对不上时拒绝")
  void should_select_key_by_kid() throws JOSEException {
    JWKSet both = new JWKSet(List.of(KEY_A.toPublicJWK(), KEY_B.toPublicJWK()));
    String signedByB = signer(KEY_B).sign(USER);
    String unknownKid = signed(KEY_A, header(KEY_A).keyID("nope").build(), claims().build());

    assertThat(decoder(both, NOW).decode(signedByB).getSubject()).isEqualTo("1001");
    assertThatThrownBy(() -> decoder(both, NOW).decode(unknownKid))
        .isInstanceOf(JwtException.class);
  }

  /// 固定时钟的解码器。
  private static JwtDecoder decoder(JWKSet keys, Instant now) {
    return IdentityAssertionDecoders.forPublicKeys(keys, Clock.fixed(now, ZoneOffset.UTC));
  }

  /// 在 NOW 签名的签名器。
  private static IdentityAssertionSigner signer(ECKey key) {
    return new IdentityAssertionSigner(key, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 合法的载荷，测试各自改坏一处。
  private static JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer("patra-gateway")
        .audience("patra")
        .subject("1001")
        .claim("sid", "2001")
        .claim("account_type", "user")
        .claim("client_type", "web")
        .issueTime(Date.from(NOW))
        .expirationTime(Date.from(NOW.plusSeconds(60)));
  }

  /// 合法的头，测试各自改坏一处。
  private static JWSHeader.Builder header(ECKey key) {
    return new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("patra-identity+jwt"))
        .keyID(key.getKeyID());
  }

  /// 用指定密钥签任意头和载荷。
  private static String signed(ECKey key, JWSHeader header, JWTClaimsSet claims)
      throws JOSEException {
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }

  /// 带 kid 的 P-256 密钥对。
  private static ECKey generate() {
    try {
      return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionDecodersTest*"`
Expected: 编译失败，报 `IdentityAssertionDecoders` 找不到。

- [ ] **Step 3: 写实现**

`src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecoders.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/// 身份断言的验签器工厂：下游用网关的公钥验签。
public final class IdentityAssertionDecoders {

  /// 验签时允许的时钟偏差。
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  /// 工具类，不允许实例化。
  private IdentityAssertionDecoders() {}

  /// 用一组公钥建一个解码器。
  ///
  /// 只接受 ES256 签名、`typ` 为 `patra-identity+jwt`、`iss` 为 `patra-gateway`、`aud` 含 `patra`、
  /// 未过期的断言；按头里的 `kid` 在集合里选公钥。任何一项不过，`decode` 抛 `JwtException`。
  ///
  /// @param publicKeys 公钥集合，每把都带 `kid`
  /// @param clock 判断过期用的时钟
  /// @return 解码器
  public static JwtDecoder forPublicKeys(JWKSet publicKeys, Clock clock) {
    Objects.requireNonNull(publicKeys, "publicKeys 不能为 null");
    Objects.requireNonNull(clock, "clock 不能为 null");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<SecurityContext>(publicKeys))
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            // Nimbus 自己也查 typ，默认只认 JWT；不放行自定义值就到不了下面的校验器
            .jwtProcessorCustomizer(
                processor ->
                    processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType(IdentityAssertionClaims.TYPE))))
            .build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTypeValidator(IdentityAssertionClaims.TYPE),
            new JwtIssuerValidator(IdentityAssertionClaims.ISSUER),
            new JwtAudienceValidator(IdentityAssertionClaims.AUDIENCE),
            timestamps));
    return decoder;
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionDecodersTest*"`
Expected: BUILD SUCCESSFUL，7 个用例通过。这一步同时确认了 spec 第 15 节第 6、7 条（自定义 `typ` 要换 `JWSTypeVerifier`；按 `kid` 选钥）。如果「缺省 typ」那个用例没拒绝，说明 `DefaultJOSEObjectTypeVerifier` 对 `null` 类型放行，改成 `new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(TYPE))` 之外再加 `JwtTypeValidator` 的 `setAllowEmpty(false)`（已是默认），并把结果记进 spec 第 15 节。

- [ ] **Step 5: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecoders.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionDecodersTest.java
git -C "$W" commit -m "加身份断言的验签器：只认 ES256、typ、iss、aud 和有效期 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: 认证对象带上断言，转换器从断言建立认证

**Files:**
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthentication.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverter.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthenticationTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverterTest.java`

**Interfaces:**
- Consumes: 任务 1 的 `IdentityAssertionClaims.toCurrentUser(Jwt)`；任务 3 的 `IdentityAssertionDecoders.forPublicKeys`（测试里建真实解码器）。
- Produces: `new CurrentUserAuthentication(CurrentUser principal, String assertion)`，`getCredentials()` 返回断言（原有两个构造器的凭据仍为 `null`）；`new IdentityAssertionAuthenticationConverter(JwtDecoder decoder)` 实现 `AuthenticationConverter`：没有 `Authorization` 头返回 `null`；无效时抛 `BadCredentialsException`；内容不合法时抛 `MalformedIdentityException`。旧的 `GatewayHeaderAuthenticationConverter` 和 `header` 包这一步先不删（任务 7 删）。

- [ ] **Step 1: 给认证对象的测试加两个用例**

在 `CurrentUserAuthenticationTest` 里加：

```java
  @Test
  @DisplayName("带断言构造时，凭据就是那个断言")
  void should_carry_assertion_as_credentials() {
    CurrentUserAuthentication authentication =
        new CurrentUserAuthentication(USER, "header.payload.signature");

    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isEqualTo("header.payload.signature");
  }

  @Test
  @DisplayName("toString 不输出断言")
  void should_not_print_assertion_in_to_string() {
    CurrentUserAuthentication authentication =
        new CurrentUserAuthentication(USER, "header.payload.signature");

    assertThat(authentication.toString()).doesNotContain("header.payload.signature");
  }
```

- [ ] **Step 2: 写转换器的失败测试**

`src/test/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverterTest.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/// IdentityAssertionAuthenticationConverter 单元测试：spec 第 8.2 节那张表的四行。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("IdentityAssertionAuthenticationConverter 单元测试")
class IdentityAssertionAuthenticationConverterTest {

  private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final ECKey KEY = generate();

  private final IdentityAssertionSigner signer = new IdentityAssertionSigner(KEY, CLOCK);
  private final IdentityAssertionAuthenticationConverter converter =
      new IdentityAssertionAuthenticationConverter(
          IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK));

  @Test
  @DisplayName("没有 Authorization 头：返回 null，按匿名处理")
  void should_return_null_when_no_authorization_header() {
    assertThat(converter.convert(request())).isNull();
  }

  @Test
  @DisplayName("单个 Bearer 断言且有效：已登录，凭据是原始断言")
  void should_authenticate_when_assertion_valid() {
    String assertion = signer.sign(USER);

    Authentication authentication = converter.convert(request("Bearer " + assertion));

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isEqualTo(assertion);
  }

  @Test
  @DisplayName("方案名不区分大小写，断言两边的空白被去掉")
  void should_accept_lowercase_scheme_and_surrounding_whitespace() {
    Authentication authentication =
        converter.convert(request("bearer  " + signer.sign(USER) + " "));

    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  @DisplayName("Authorization 头出现多次：401，日志记原因不记断言")
  void should_reject_multiple_authorization_headers(CapturedOutput output) {
    String assertion = signer.sign(USER);
    MockHttpServletRequest request = request("Bearer " + assertion);
    request.addHeader("Authorization", "Bearer " + assertion);

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(BadCredentialsException.class);
    assertThat(output).contains("拒绝身份断言").contains("出现多次").doesNotContain(assertion);
  }

  @Test
  @DisplayName("不是 Bearer 方案：401")
  void should_reject_non_bearer_scheme() {
    assertThatThrownBy(() -> converter.convert(request("Basic dXNlcjpwYXNz")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  @DisplayName("不是合法的 JWT：401")
  void should_reject_garbage_token() {
    assertThatThrownBy(() -> converter.convert(request("Bearer not-a-jwt")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  @DisplayName("过期的断言：401，日志记原因不记断言")
  void should_reject_expired_assertion(CapturedOutput output) throws JOSEException {
    String expired = signed(claims().expirationTime(Date.from(NOW.minusSeconds(120))).build());

    assertThatThrownBy(() -> converter.convert(request("Bearer " + expired)))
        .isInstanceOf(BadCredentialsException.class);
    assertThat(output).contains("拒绝身份断言").doesNotContain(expired);
  }

  @Test
  @DisplayName("签名正确但 sub 不是正整数：500")
  void should_fail_with_malformed_identity_when_claims_invalid() throws JOSEException {
    String malformed = signed(claims().subject("abc").build());

    assertThatThrownBy(() -> converter.convert(request("Bearer " + malformed)))
        .isInstanceOf(MalformedIdentityException.class);
  }

  /// 不带 Authorization 头的请求。
  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe");
    return request;
  }

  /// 带一个 Authorization 头的请求。
  private static MockHttpServletRequest request(String authorization) {
    MockHttpServletRequest request = request();
    request.addHeader("Authorization", authorization);
    return request;
  }

  /// 合法的载荷，测试各自改坏一处。
  private static JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer("patra-gateway")
        .audience("patra")
        .subject("1001")
        .claim("sid", "2001")
        .claim("account_type", "user")
        .claim("client_type", "web")
        .issueTime(Date.from(NOW))
        .expirationTime(Date.from(NOW.plusSeconds(60)));
  }

  /// 用测试密钥签任意载荷。
  private static String signed(JWTClaimsSet claims) throws JOSEException {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("patra-identity+jwt"))
                .keyID(KEY.getKeyID())
                .build(),
            claims);
    jwt.sign(new ECDSASigner(KEY));
    return jwt.serialize();
  }

  /// 带 kid 的 P-256 密钥对。
  private static ECKey generate() {
    try {
      return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
}
```

- [ ] **Step 3: 跑两个测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserAuthenticationTest*" --tests "*IdentityAssertionAuthenticationConverterTest*"`
Expected: 编译失败，报 `IdentityAssertionAuthenticationConverter` 找不到、`CurrentUserAuthentication` 没有两个参数的构造器。

- [ ] **Step 4: 改认证对象**

`CurrentUserAuthentication` 改成：

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
/// 下游验签后构造它，凭据是原始断言，内部客户端替用户调用时原样转发；网关查到会话后
/// 和 `CurrentUserRunner` 构造它时没有断言，凭据为 `null`。
/// 本版没有角色，权限列表为空；做 admin 时由建立认证的一方把角色传进来。
public final class CurrentUserAuthentication extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final CurrentUser principal;
  private final String assertion;

  /// 创建没有凭据、没有权限的认证对象。
  ///
  /// @param principal 当前用户
  public CurrentUserAuthentication(CurrentUser principal) {
    this(principal, null, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带原始断言的认证对象。
  ///
  /// @param principal 当前用户
  /// @param assertion 验签通过的断言，紧凑序列化
  public CurrentUserAuthentication(CurrentUser principal, String assertion) {
    this(principal, assertion, AuthorityUtils.NO_AUTHORITIES);
  }

  /// 创建带权限列表、没有凭据的认证对象。
  ///
  /// @param principal 当前用户
  /// @param authorities 权限列表
  public CurrentUserAuthentication(
      CurrentUser principal, Collection<? extends GrantedAuthority> authorities) {
    this(principal, null, authorities);
  }

  /// 创建认证对象。
  ///
  /// @param principal 当前用户
  /// @param assertion 原始断言，可以为 `null`
  /// @param authorities 权限列表
  private CurrentUserAuthentication(
      CurrentUser principal, String assertion, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = Objects.requireNonNull(principal, "principal 不能为 null");
    this.assertion = assertion;
    super.setAuthenticated(true);
  }

  /// 原始断言；没有断言时为 `null`。父类的 `toString()` 把它打成 `[PROTECTED]`。
  ///
  /// @return 紧凑序列化的断言或 `null`
  @Override
  public String getCredentials() {
    return assertion;
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

- [ ] **Step 5: 写转换器**

`src/main/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverter.java`：

```java
package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把 `Authorization: Bearer` 里的身份断言变成认证对象。
///
/// 没带断言按匿名；断言无效返回 401；签名正确但内容不合法返回 500（只可能是网关的缺陷）。
/// 断言只可能来自网关，无效说明配置错了或有人伪造，所以不当成匿名静默放过。
@Slf4j
public final class IdentityAssertionAuthenticationConverter implements AuthenticationConverter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder decoder;

  /// 创建转换器。
  ///
  /// @param decoder 用网关公钥建的解码器
  public IdentityAssertionAuthenticationConverter(JwtDecoder decoder) {
    this.decoder = Objects.requireNonNull(decoder, "decoder 不能为 null");
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；没带断言时返回 `null`
  /// @throws BadCredentialsException `Authorization` 不是单个 Bearer 断言，或断言无效时
  /// @throws MalformedIdentityException 断言签名正确但内容不合法时
  @Override
  public Authentication convert(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (values.isEmpty()) {
      return null;
    }
    if (values.size() > 1) {
      throw reject(request, "Authorization 头出现多次");
    }
    String value = values.getFirst();
    if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw reject(request, "Authorization 头不是 Bearer 方案");
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(value.substring(BEARER_PREFIX.length()).strip());
    } catch (JwtException e) {
      throw reject(request, e.getMessage());
    }
    return new CurrentUserAuthentication(
        IdentityAssertionClaims.toCurrentUser(jwt), jwt.getTokenValue());
  }

  /// 记一行警告并构造 401 对应的异常。日志里只有原因，没有断言的内容。
  ///
  /// @param request 当前请求
  /// @param reason 拒绝的原因
  /// @return 给调用方抛出的异常
  private static BadCredentialsException reject(HttpServletRequest request, String reason) {
    log.warn("拒绝身份断言: {} {}，原因: {}", request.getMethod(), request.getRequestURI(), reason);
    return new BadCredentialsException("身份断言无效");
  }
}
```

- [ ] **Step 6: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*CurrentUserAuthenticationTest*" --tests "*IdentityAssertionAuthenticationConverterTest*"`
Expected: BUILD SUCCESSFUL，认证对象 6 个、转换器 8 个用例通过。Spring 的 `JwtException` 消息只含原因（「Jwt expired at …」「Invalid signature」），不含断言，日志断言才能成立。

- [ ] **Step 7: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthentication.java \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverter.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/CurrentUserAuthenticationTest.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/IdentityAssertionAuthenticationConverterTest.java
git -C "$W" commit -m "加从身份断言建立认证的转换器，认证对象带上原始断言 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: 公钥配置项与自动配置切换

**Files:**
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/PatraSecurityProperties.java`（整个重写）
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfiguration.java:1-126`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/PatraSecurityPropertiesTest.java`（整个重写）
- Modify: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfigurationTest.java`

**Interfaces:**
- Consumes: 任务 3 的 `IdentityAssertionDecoders.forPublicKeys(JWKSet, Clock)`；任务 4 的 `IdentityAssertionAuthenticationConverter(JwtDecoder)`。
- Produces: `PatraSecurityProperties(IdentityAssertion identityAssertion)`，嵌套 `IdentityAssertion(String publicKeys)`，`publicKeySet()` 返回 `JWKSet`；常量 `IdentityAssertion.PUBLIC_KEYS_PROPERTY = "patra.security.identity-assertion.public-keys"`。容器里多一个 `JwtDecoder` Bean（名字 `identityAssertionDecoder`）。旧的 `gatewayToken` 配置项消失。

- [ ] **Step 1: 重写配置属性的测试**

`src/test/java/dev/linqibin/patra/starter/security/config/PatraSecurityPropertiesTest.java` 整个换成：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import dev.linqibin.patra.starter.security.config.PatraSecurityProperties.IdentityAssertion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraSecurityProperties 单元测试。
@DisplayName("PatraSecurityProperties 单元测试")
class PatraSecurityPropertiesTest {

  private static final String PROPERTY = "patra.security.identity-assertion.public-keys";

  @Test
  @DisplayName("合法的 JWK Set：解析出带 kid 的公钥")
  void should_parse_public_key_set() throws JOSEException {
    ECKey key = p256();

    IdentityAssertion assertion = new IdentityAssertion(publicSet(key.toPublicJWK()));

    assertThat(assertion.publicKeySet().getKeyByKeyId(key.getKeyID())).isNotNull();
    assertThat(new PatraSecurityProperties(assertion).identityAssertion()).isSameAs(assertion);
  }

  @Test
  @DisplayName("两把公钥都能解析")
  void should_accept_multiple_public_keys() throws JOSEException {
    ECKey first = p256();
    ECKey second = p256();

    IdentityAssertion assertion =
        new IdentityAssertion(publicSet(first.toPublicJWK(), second.toPublicJWK()));

    assertThat(assertion.publicKeySet().getKeys()).hasSize(2);
  }

  @Test
  @DisplayName("没配 identity-assertion 这一段：拒绝，并指明配置项")
  void should_reject_missing_section() {
    assertThatThrownBy(() -> new PatraSecurityProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "not json", "{\"kty\":\"EC\"}", "{\"keys\":[]}"})
  @DisplayName("为空、不是 JSON、不是 JWK Set、没有公钥：拒绝，并指明配置项")
  void should_reject_blank_or_unparseable_or_empty(String json) {
    assertThatThrownBy(() -> new IdentityAssertion(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY);
  }

  @Test
  @DisplayName("集合里混进了私钥：拒绝，并说明私钥只能在网关")
  void should_reject_private_key() throws JOSEException {
    ECKey key = p256();

    assertThatThrownBy(() -> new IdentityAssertion(new JWKSet(key).toString(false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY)
        .hasMessageContaining("私钥");
  }

  @Test
  @DisplayName("不是 EC P-256 的公钥：拒绝")
  void should_reject_other_key_types() throws JOSEException {
    RSAKey rsa = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
    ECKey p384 = new ECKeyGenerator(Curve.P_384).keyIDFromThumbprint(true).generate();

    assertThatThrownBy(() -> new IdentityAssertion(publicSet(rsa.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
    assertThatThrownBy(() -> new IdentityAssertion(publicSet(p384.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P-256");
  }

  @Test
  @DisplayName("公钥没有 kid：拒绝")
  void should_reject_key_without_kid() throws JOSEException {
    ECKey withoutKid = new ECKeyGenerator(Curve.P_256).generate();

    assertThatThrownBy(() -> new IdentityAssertion(publicSet(withoutKid.toPublicJWK())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kid");
  }

  /// 带 kid 的 P-256 密钥对。
  private static ECKey p256() throws JOSEException {
    return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
  }

  /// 只含公钥的 JWK Set JSON。
  private static String publicSet(JWK... keys) {
    return new JWKSet(List.of(keys)).toString(true);
  }
}
```

- [ ] **Step 2: 改自动配置的测试**

`SecurityServletAutoConfigurationTest` 做这几处改动：

把常量换成用临时公钥拼的属性：

```java
  private static final String PUBLIC_KEYS_PROPERTY =
      "patra.security.identity-assertion.public-keys=" + publicJwkSet();
```

加一个静态方法（和 import `com.nimbusds.jose.JOSEException`、`com.nimbusds.jose.jwk.Curve`、`com.nimbusds.jose.jwk.ECKey`、`com.nimbusds.jose.jwk.JWKSet`、`com.nimbusds.jose.jwk.gen.ECKeyGenerator`、`org.springframework.security.oauth2.jwt.JwtDecoder`）：

```java
  /// 本测试类用的临时公钥，JWK Set JSON。
  private static String publicJwkSet() {
    try {
      ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
      return new JWKSet(key.toPublicJWK()).toString(true);
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
```

全文把 `TOKEN_PROPERTY` 换成 `PUBLIC_KEYS_PROPERTY`。第一个用例改名和断言：

```java
  @Test
  @DisplayName("没配公钥时启动失败，错误信息指明配置项")
  void should_fail_to_start_when_public_keys_missing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .rootCause()
              .hasMessageContaining("patra.security.identity-assertion.public-keys");
        });
  }
```

第二个用例改名为 `should_register_default_chain_and_companions_when_public_keys_configured`，`@DisplayName("配了公钥时注册验签器、默认过滤器链和配套 Bean")`，并在断言里加一行 `assertThat(context).hasSingleBean(JwtDecoder.class);`。第三个用例改名为 `should_build_stateless_chain_with_assertion_filter`。「非 Web 环境」用例的 `@DisplayName` 改成「非 Web 环境下不注册 servlet 相关的 Bean，也不要求公钥」。

- [ ] **Step 3: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*PatraSecurityPropertiesTest*" --tests "*SecurityServletAutoConfigurationTest*"`
Expected: 编译失败，报 `PatraSecurityProperties.IdentityAssertion` 找不到。

- [ ] **Step 4: 重写配置属性**

`PatraSecurityProperties.java` 整个换成：

```java
package dev.linqibin.patra.starter.security.config;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 安全 starter 的配置属性，前缀 `patra.security`。
///
/// 只在 servlet 应用里绑定，因为只有验签时才用到公钥。
///
/// @param identityAssertion 身份断言相关的配置
@ConfigurationProperties(prefix = "patra.security")
public record PatraSecurityProperties(IdentityAssertion identityAssertion) {

  /// 校验：没配 `identity-assertion` 这一段时应用启动失败，错误信息指明配置项。
  public PatraSecurityProperties {
    if (identityAssertion == null) {
      throw new IllegalArgumentException(IdentityAssertion.missingMessage());
    }
  }

  /// 身份断言的配置。
  ///
  /// @param publicKeys 网关的公钥，JWK Set JSON，只含公钥，可以多把
  public record IdentityAssertion(String publicKeys) {

    /// 公钥配置项的全名，用在错误信息里。
    public static final String PUBLIC_KEYS_PROPERTY =
        "patra.security.identity-assertion.public-keys";

    /// 校验公钥集合。不合法时应用启动失败，错误信息指明配置项和原因。
    public IdentityAssertion {
      parsePublicKeys(publicKeys);
    }

    /// 解析成公钥集合。启动时已经校验过，这里不会失败。
    ///
    /// @return 公钥集合
    public JWKSet publicKeySet() {
      return parsePublicKeys(publicKeys);
    }

    /// 「没配」的错误信息。
    ///
    /// @return 错误信息
    static String missingMessage() {
      return "配置项 " + PUBLIC_KEYS_PROPERTY + " 不能为空：它是网关签身份断言的公钥（JWK Set JSON）";
    }

    /// 解析并校验：是 JWK Set、至少一把、都是不含私钥的 EC P-256 公钥、都带 kid。
    ///
    /// @param json 配置的值
    /// @return 公钥集合
    /// @throws IllegalArgumentException 任何一项不满足时
    private static JWKSet parsePublicKeys(String json) {
      if (json == null || json.isBlank()) {
        throw new IllegalArgumentException(missingMessage());
      }
      JWKSet set;
      try {
        set = JWKSet.parse(json);
      } catch (ParseException e) {
        throw new IllegalArgumentException(
            "配置项 " + PUBLIC_KEYS_PROPERTY + " 不是合法的 JWK Set JSON", e);
      }
      if (set.getKeys().isEmpty()) {
        throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 至少要有一把公钥");
      }
      for (JWK key : set.getKeys()) {
        if (key.isPrivate()) {
          throw new IllegalArgumentException(
              "配置项 " + PUBLIC_KEYS_PROPERTY + " 含有私钥：下游只能配公钥，私钥只能在网关");
        }
        if (!(key instanceof ECKey ecKey) || !Curve.P_256.equals(ecKey.getCurve())) {
          throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 只能是 EC P-256 公钥");
        }
        if (key.getKeyID() == null || key.getKeyID().isBlank()) {
          throw new IllegalArgumentException("配置项 " + PUBLIC_KEYS_PROPERTY + " 里每把公钥都要有 kid");
        }
      }
      return set;
    }
  }
}
```

- [ ] **Step 5: 改自动配置**

`SecurityServletAutoConfiguration.java`：

1. import 里删掉 `GatewayHeaderAuthenticationConverter`，加上 `dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders`、`dev.linqibin.patra.starter.security.authentication.IdentityAssertionAuthenticationConverter`、`java.time.Clock`、`org.springframework.beans.factory.ObjectProvider`、`org.springframework.security.oauth2.jwt.JwtDecoder`。
2. 类注释第一行改成「安全 starter 的 servlet 自动配置：验签器、过滤器链、错误输出。」
3. 在 `rejectingAuthenticationManager()` 之后、过滤器链之前加：

```java
  /// 用配置里的公钥建验签器。容器里有 `Clock` 就用它，没有用系统 UTC 时钟。
  ///
  /// @param properties 配置属性
  /// @param clock 容器里的时钟，可能没有
  /// @return 验签器
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder identityAssertionDecoder(
      PatraSecurityProperties properties, ObjectProvider<Clock> clock) {
    return IdentityAssertionDecoders.forPublicKeys(
        properties.identityAssertion().publicKeySet(), clock.getIfAvailable(Clock::systemUTC));
  }
```

4. 过滤器链的方法改成：

```java
  /// 下游的默认过滤器链：无会话，从 `Authorization: Bearer` 里的断言建立认证，所有路径放行。
  ///
  /// 下游不做路径级拦截，路由规则归网关。需要登录的接口由业务代码调
  /// `CurrentUserPort.require()` 来保证，这样服务之间直连的 `/_internal/**` 不受影响。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param identityAssertionDecoder 验签器
  /// @return 过滤器链
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain patraSecurityFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter, JwtDecoder identityAssertionDecoder) {
    StatelessSecurityDefaults.apply(http, problemWriter);

    // 转换器给出的已经是认证完成的对象，原样返回；不经过 ProviderManager，凭据不会被擦掉。
    // 必须用显式类型：AuthenticationFilter 的两个构造器对 lambda 有二义性。
    AuthenticationManager passThrough = authentication -> authentication;
    // 不声明成 Bean：否则 Boot 会把它再注册成全局 servlet 过滤器。
    AuthenticationFilter assertionFilter =
        new AuthenticationFilter(
            passThrough, new IdentityAssertionAuthenticationConverter(identityAssertionDecoder));
    // 默认的成功处理器会回 302 再继续执行过滤器链，换成什么都不做。
    assertionFilter.setSuccessHandler((request, response, authentication) -> {});
    // 默认的失败处理器遇到服务类异常会原样抛出，换成统一的写出器。
    assertionFilter.setFailureHandler(problemWriter);

    http.addFilterBefore(assertionFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
```

这时 `GatewayHeaderAuthenticationConverter` 没人用了，但它和 `header` 包还能编译（测试支持仍引用），任务 7 再删。

- [ ] **Step 6: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*PatraSecurityPropertiesTest*" --tests "*SecurityServletAutoConfigurationTest*"`
Expected: BUILD SUCCESSFUL，配置属性 7 个（含参数化子用例）、自动配置 8 个用例通过。

- [ ] **Step 7: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/PatraSecurityProperties.java \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfiguration.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/PatraSecurityPropertiesTest.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityServletAutoConfigurationTest.java
git -C "$W" commit -m "下游改配网关公钥，过滤器链换成验签的转换器 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: 测试支持改成签断言

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestSigningKey.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestIdentity.java`（整个重写）
- Create: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestIdentityAssertionEnvironmentPostProcessor.java`
- Delete: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessor.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/testFixtures/resources/META-INF/spring.factories`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityTest.java`（整个重写）
- Create: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityAssertionEnvironmentPostProcessorTest.java`
- Delete: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessorTest.java`

**Interfaces:**
- Consumes: 任务 1 的常量；任务 2 的 `IdentityAssertionSigner`；任务 5 的 `IdentityAssertion.PUBLIC_KEYS_PROPERTY`。
- Produces: `TestSigningKey.key()`（含私钥的 `ECKey`）、`publicJwkSet()`（String）、`signer()`、`sign(JWTClaimsSet)`（String）、`claims(CurrentUser)`（`JWTClaimsSet.Builder`，合法载荷模板）；`TestIdentity.assertion()` / `assertion(CurrentUser)`（String），`headers()` / `headers(long)` / `headers(CurrentUser)` 返回只含 `Authorization: Bearer …` 的 `HttpHeaders`；`TestIdentity.GATEWAY_TOKEN` 消失。

这一步之后 `integrationTest` 源码暂时编译不过（四个 IT 还引用 `IdentityHeaders` 和 `GATEWAY_TOKEN`），任务 7 修。单元测试不受影响。

- [ ] **Step 1: 重写 `TestIdentityTest`，新建后处理器的测试**

`src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityTest.java`：

```java
package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.text.ParseException;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/// TestIdentity 与 TestSigningKey 单元测试。
@DisplayName("TestIdentity 单元测试")
class TestIdentityTest {

  @Test
  @DisplayName("默认的请求头是一个 Bearer 断言，能用测试公钥验签并还原出默认用户")
  void should_produce_bearer_header_that_decodes_to_default_user() throws ParseException {
    HttpHeaders headers = TestIdentity.headers();

    String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
    assertThat(headers.size()).isEqualTo(1);
    assertThat(authorization).startsWith("Bearer ");
    assertThat(decode(authorization.substring("Bearer ".length())))
        .isEqualTo(CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @Test
  @DisplayName("可以只指定用户 ID")
  void should_use_given_user_id() throws ParseException {
    assertThat(decode(TestIdentity.assertion(TestIdentity.user(42L))))
        .isEqualTo(CurrentUser.of(42L, 2001L, AccountType.USER, ClientType.WEB));
  }

  @Test
  @DisplayName("可以指定整个用户")
  void should_use_given_user() throws ParseException {
    CurrentUser user = CurrentUser.of(7L, 8L, AccountType.USER, ClientType.WEB);

    assertThat(decode(TestIdentity.assertion(user))).isEqualTo(user);
  }

  @Test
  @DisplayName("用同一把测试私钥签任意载荷：签名有效，内容由测试自己决定")
  void should_sign_custom_claims_with_same_key() throws ParseException {
    String malformed =
        TestSigningKey.sign(TestSigningKey.claims(TestIdentity.user()).subject("abc").build());

    Jwt jwt = decoder().decode(malformed);
    assertThatThrownBy(() -> IdentityAssertionClaims.toCurrentUser(jwt))
        .isInstanceOf(MalformedIdentityException.class);
  }

  /// 用测试公钥验签并还原用户。
  private static CurrentUser decode(String assertion) throws ParseException {
    return IdentityAssertionClaims.toCurrentUser(decoder().decode(assertion));
  }

  /// 用 `TestSigningKey.publicJwkSet()` 建的解码器，和使用方的应用里一样。
  private static JwtDecoder decoder() throws ParseException {
    return IdentityAssertionDecoders.forPublicKeys(
        JWKSet.parse(TestSigningKey.publicJwkSet()), Clock.systemUTC());
  }
}
```

`src/test/java/dev/linqibin/patra/starter/security/test/TestIdentityAssertionEnvironmentPostProcessorTest.java`：

```java
package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/// TestIdentityAssertionEnvironmentPostProcessor 单元测试。
///
/// 这里只测它对环境做了什么；它是否真的被 Spring Boot 加载，由集成测试验证
/// （没加载的话那些测试的应用会因为缺公钥而起不来）。
@DisplayName("TestIdentityAssertionEnvironmentPostProcessor 单元测试")
class TestIdentityAssertionEnvironmentPostProcessorTest {

  private static final String PROPERTY = "patra.security.identity-assertion.public-keys";

  private final TestIdentityAssertionEnvironmentPostProcessor postProcessor =
      new TestIdentityAssertionEnvironmentPostProcessor();

  @Test
  @DisplayName("没有配置时补上测试公钥")
  void should_add_test_public_keys_when_absent() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo(TestSigningKey.publicJwkSet());
  }

  @Test
  @DisplayName("已有显式配置时不覆盖")
  void should_not_override_explicit_configuration() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("explicit", Map.of(PROPERTY, "{\"keys\":[]}")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo("{\"keys\":[]}");
  }
}
```

删掉 `src/test/java/dev/linqibin/patra/starter/security/test/TestGatewayTokenEnvironmentPostProcessorTest.java`。

- [ ] **Step 2: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*TestIdentityTest*" --tests "*TestIdentityAssertionEnvironmentPostProcessorTest*"`
Expected: 编译失败，报 `TestSigningKey`、`TestIdentityAssertionEnvironmentPostProcessor` 找不到。

- [ ] **Step 3: 写测试支持**

`src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestSigningKey.java`：

```java
package dev.linqibin.patra.starter.security.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/// 测试用的签名密钥：本 JVM 第一次用到时生成一对 P-256 密钥，之后复用。
///
/// 私钥只在测试进程的内存里，不写文件，不进仓库。
public final class TestSigningKey {

  private static final ECKey KEY = generate();

  /// 工具类，不允许实例化。
  private TestSigningKey() {}

  /// 含私钥的 JWK。
  ///
  /// @return 密钥对
  public static ECKey key() {
    return KEY;
  }

  /// 只含公钥的 JWK Set JSON，给 `patra.security.identity-assertion.public-keys` 用。
  ///
  /// @return JWK Set JSON
  public static String publicJwkSet() {
    return new JWKSet(KEY.toPublicJWK()).toString(true);
  }

  /// 用测试私钥和系统时钟构造的签名器。
  ///
  /// @return 签名器
  public static IdentityAssertionSigner signer() {
    return new IdentityAssertionSigner(KEY, Clock.systemUTC());
  }

  /// 用测试私钥签任意载荷，头和签名器一致。给需要构造过期、内容不合法等异常断言的测试用。
  ///
  /// @param claims 载荷
  /// @return 紧凑序列化的断言
  public static String sign(JWTClaimsSet claims) {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(IdentityAssertionClaims.TYPE))
                .keyID(KEY.getKeyID())
                .build(),
            claims);
    try {
      jwt.sign(new ECDSASigner(KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException("签测试断言失败", e);
    }
    return jwt.serialize();
  }

  /// 合法载荷的模板：签名器会写的每个声明都在，值取自给定用户，现在签出、60 秒后过期。
  /// 测试改坏一处再调 `sign`。
  ///
  /// @param user 用户
  /// @return 载荷构建器
  public static JWTClaimsSet.Builder claims(CurrentUser user) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(IdentityAssertionClaims.ISSUER)
        .audience(IdentityAssertionClaims.AUDIENCE)
        .subject(Long.toString(user.userId()))
        .claim(IdentityAssertionClaims.SESSION_ID, Long.toString(user.sessionId()))
        .claim(IdentityAssertionClaims.ACCOUNT_TYPE, user.accountType().getCode())
        .claim(IdentityAssertionClaims.CLIENT_TYPE, user.clientType().getCode())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(IdentityAssertionClaims.LIFETIME)));
  }

  /// 生成带 kid（公钥指纹）的 P-256 密钥对。
  ///
  /// @return 密钥对
  private static ECKey generate() {
    try {
      return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
}
```

`TestIdentity.java` 整个换成：

```java
package dev.linqibin.patra.starter.security.test;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.springframework.http.HttpHeaders;

/// 测试里构造「已登录请求」用的请求头。
///
/// 已登录的请求：把 `headers()` 返回的头加到请求上。匿名的请求：什么都不加。
/// 这种写法走的是真实的安全过滤器和真实的验签，切片测试和整应用测试用法相同。
public final class TestIdentity {

  /// 默认的用户 ID。
  public static final long USER_ID = 1001L;

  /// 默认的会话 ID。
  public static final long SESSION_ID = 2001L;

  /// 工具类，不允许实例化。
  private TestIdentity() {}

  /// 默认用户。
  ///
  /// @return 用户 ID 为 `USER_ID`、会话 ID 为 `SESSION_ID` 的前台网页端用户
  public static CurrentUser user() {
    return user(USER_ID);
  }

  /// 指定用户 ID、其余字段取默认值的用户。
  ///
  /// @param userId 用户 ID
  /// @return 用户
  public static CurrentUser user(long userId) {
    return CurrentUser.of(userId, SESSION_ID, AccountType.USER, ClientType.WEB);
  }

  /// 默认用户的断言。
  ///
  /// @return 用测试私钥签出的断言
  public static String assertion() {
    return assertion(user());
  }

  /// 指定用户的断言。
  ///
  /// @param user 用户
  /// @return 用测试私钥签出的断言
  public static String assertion(CurrentUser user) {
    return TestSigningKey.signer().sign(user);
  }

  /// 默认用户的请求头。
  ///
  /// @return 一个 `Authorization: Bearer` 头
  public static HttpHeaders headers() {
    return headers(user());
  }

  /// 指定用户 ID 的请求头。
  ///
  /// @param userId 用户 ID
  /// @return 一个 `Authorization: Bearer` 头
  public static HttpHeaders headers(long userId) {
    return headers(user(userId));
  }

  /// 指定用户的请求头。
  ///
  /// @param user 用户
  /// @return 一个 `Authorization: Bearer` 头，值是用测试私钥签出的断言
  public static HttpHeaders headers(CurrentUser user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(assertion(user));
    return headers;
  }
}
```

`src/testFixtures/java/dev/linqibin/patra/starter/security/test/TestIdentityAssertionEnvironmentPostProcessor.java`：

```java
package dev.linqibin.patra.starter.security.test;

import dev.linqibin.patra.starter.security.config.PatraSecurityProperties.IdentityAssertion;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/// 测试里自动把 `patra.security.identity-assertion.public-keys` 设成测试公钥。
///
/// 只要测试 classpath 上有本模块的 testFixtures 就生效。放在最低优先级，
/// 测试里显式配置的值会覆盖它。
public class TestIdentityAssertionEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_SOURCE_NAME = "patraSecurityTestIdentityAssertion";

  /// 往环境里追加一个只含测试公钥的属性源。
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
                Map.of(IdentityAssertion.PUBLIC_KEYS_PROPERTY, TestSigningKey.publicJwkSet())));
  }
}
```

删掉 `TestGatewayTokenEnvironmentPostProcessor.java`。`src/testFixtures/resources/META-INF/spring.factories` 改成：

```
org.springframework.boot.EnvironmentPostProcessor=\
dev.linqibin.patra.starter.security.test.TestIdentityAssertionEnvironmentPostProcessor
```

- [ ] **Step 4: 跑全部单元测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test`
Expected: BUILD SUCCESSFUL。`IdentityHeadersTest`、`GatewayHeaderAuthenticationConverterTest` 这时还在、还能过（它们测的旧类还在），任务 7 一起删。

- [ ] **Step 5: 提交**

```bash
git -C "$W" add -A patra-starters/patra-spring-boot-starter-security/src/testFixtures \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/test
git -C "$W" commit -m "测试支持改为用临时密钥签断言 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: 集成测试改成断言，删掉身份头的代码

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITAssertions.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/IdentityAssertionAuthenticationIT.java`
- Delete: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/GatewayHeaderAuthenticationIT.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/SecurityErrorResponseIT.java:5,160-175`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/StatelessSessionIT.java:5,40-55`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/slice/SecurityWebMvcSliceIT.java`
- Delete: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/header/IdentityHeaders.java`、`header/IdentityParseResult.java`、`authentication/GatewayHeaderAuthenticationConverter.java`
- Delete: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/header/IdentityHeadersTest.java`、`authentication/GatewayHeaderAuthenticationConverterTest.java`

**Interfaces:**
- Consumes: 任务 6 的 `TestIdentity.headers()`、`TestSigningKey.sign` / `claims`。
- Produces: `SecurityITAssertions.garbage()`（不是 JWT 的字符串）、`expired()`（签名正确但已过期）、`malformed()`（签名正确但 `sub` 不是正整数），都返回紧凑序列化的断言，给几个 IT 共用。

- [ ] **Step 1: 写帮助类**

`src/integrationTest/java/dev/linqibin/patra/starter/security/support/SecurityITAssertions.java`：

```java
package dev.linqibin.patra.starter.security.support;

import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.time.Instant;
import java.util.Date;

/// 集成测试里构造异常断言的帮助方法。合法的断言用 `TestIdentity`，这里只放异常的。
public final class SecurityITAssertions {

  /// 工具类，不允许实例化。
  private SecurityITAssertions() {}

  /// 不是 JWT 的字符串。
  ///
  /// @return 固定文本
  public static String garbage() {
    return "not-a-jwt";
  }

  /// 签名正确但两分钟前就过期的断言，容差 30 秒也救不回来。
  ///
  /// @return 紧凑序列化的断言
  public static String expired() {
    Instant past = Instant.now().minusSeconds(180);
    return TestSigningKey.sign(
        TestSigningKey.claims(TestIdentity.user())
            .issueTime(Date.from(past))
            .expirationTime(Date.from(past.plusSeconds(60)))
            .build());
  }

  /// 签名正确但 `sub` 不是正整数的断言。
  ///
  /// @return 紧凑序列化的断言
  public static String malformed() {
    return TestSigningKey.sign(TestSigningKey.claims(TestIdentity.user()).subject("abc").build());
  }
}
```

- [ ] **Step 2: 写新的认证集成测试，删旧的**

删掉 `GatewayHeaderAuthenticationIT.java`。新建 `src/integrationTest/java/dev/linqibin/patra/starter/security/IdentityAssertionAuthenticationIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.support.SecurityITAssertions;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 从身份断言建立认证的集成测试：spec 第 8.2 节的四种情况。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("从身份断言建立认证 集成测试")
class IdentityAssertionAuthenticationIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("有效的断言：已登录，四个字段都对，且不被重定向")
  void should_identify_user_when_assertion_valid() {
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
        .isEqualTo("1001:2001:user:web");
  }

  @Test
  @DisplayName("没有 Authorization 头：匿名")
  void should_be_anonymous_when_no_assertion() {
    restClient
        .get()
        .uri("/probe/whoami")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("不是 JWT：401，ProblemDetail 格式，带 WWW-Authenticate，日志记原因不记内容")
  void should_reject_with_401_problem_when_token_is_garbage(CapturedOutput output) {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.garbage()))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.title")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.path")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.timestamp")
        .exists();

    assertThat(output).contains("拒绝身份断言").doesNotContain("not-a-jwt");
  }

  @Test
  @DisplayName("过期的断言：401，日志里没有断言的内容")
  void should_reject_with_401_when_assertion_expired(CapturedOutput output) {
    String expired = SecurityITAssertions.expired();

    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(expired))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401");

    assertThat(output).contains("拒绝身份断言").doesNotContain(expired);
  }

  @Test
  @DisplayName("Authorization 头出现两次：401")
  void should_reject_with_401_when_authorization_header_repeated() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdentity.assertion());
              headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdentity.assertion());
            })
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401");
  }

  @Test
  @DisplayName("签名正确但内容不合法：500，ProblemDetail 格式")
  void should_reject_with_500_problem_when_claims_malformed() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.malformed()))
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
}
```

- [ ] **Step 3: 改另外三个 IT**

`SecurityErrorResponseIT`：删掉 `import dev.linqibin.patra.starter.security.header.IdentityHeaders;`，加 `import dev.linqibin.patra.starter.security.support.SecurityITAssertions;`。最后一个用例里过滤器那一路的请求头改成：

```java
            .headers(headers -> headers.setBearerAuth(SecurityITAssertions.malformed()))
```

`StatelessSessionIT`：同样换 import。第一个用例改成：

```java
  @Test
  @DisplayName("匿名、已登录、401、403、断言无效、断言内容不合法的请求都不创建会话，也不下发 Cookie")
  void should_never_create_session_or_set_cookie() {
    getWithoutCookie("/probe/whoami", headers -> {});
    getWithoutCookie("/probe/whoami", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie("/probe/me", headers -> {});
    getWithoutCookie("/probe/access-denied", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie("/probe/whoami", headers -> headers.setBearerAuth(SecurityITAssertions.garbage()));
    getWithoutCookie("/probe/whoami", headers -> headers.setBearerAuth(SecurityITAssertions.malformed()));

    assertThat(sessionCounter.createdCount()).isZero();
  }
```

`SecurityWebMvcSliceIT`：删掉 `IdentityHeaders` 的 import，加 `import dev.linqibin.patra.starter.security.support.SecurityITAssertions;`。把「身份头残缺：500」用例换成下面两个，并把「身份头不合法的请求之后」用例里的请求头改成 `headers.setBearerAuth(SecurityITAssertions.malformed())`：

```java
  @Test
  @DisplayName("断言无效：401，由安全过滤器输出")
  void should_return_401_problem_when_assertion_invalid() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.garbage()))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.instance")
        .isEqualTo("/slice/whoami");
  }

  @Test
  @DisplayName("断言内容不合法：500，由安全过滤器输出")
  void should_return_500_problem_when_assertion_malformed() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.malformed()))
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0500")
        .jsonPath("$.instance")
        .isEqualTo("/slice/whoami");
  }
```

- [ ] **Step 4: 删掉身份头方案的代码和测试**

```bash
git -C "$W" rm -q \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/header/IdentityHeaders.java \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/header/IdentityParseResult.java \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverter.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/header/IdentityHeadersTest.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/authentication/GatewayHeaderAuthenticationConverterTest.java
grep -rn "IdentityHeaders\|GATEWAY_TOKEN\|gateway-token\|X-Patra-" "$W/patra-starters" "$W/patra-api" "$W/linqibin-commons" --include='*.java' --include='*.yml' --include='*.kts' --include='*.factories' | grep -v '/build/'
```

Expected: `grep` 没有输出（README 里的旧说法任务 11 改）。

- [ ] **Step 5: 跑单元测试和集成测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test :patra-starters:patra-spring-boot-starter-security:integrationTest`
Expected: BUILD SUCCESSFUL。这是 http-interface starter 第一次出现在集成测试的 classpath 上：IT 应用如果因为 LoadBalancer 或服务发现的自动配置起不来，在 `src/integrationTest/resources/application.yml` 加 `spring.cloud.discovery.enabled: false`，并把原因记进 ledger。这一步同时确认 spec 第 15 节第 8 条（两种异常都经失败处理器到达写出器）。

- [ ] **Step 6: 提交**

```bash
git -C "$W" add -A patra-starters/patra-spring-boot-starter-security/src
git -C "$W" commit -m "集成测试改用断言，删掉身份头和内部令牌的实现 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: http-interface starter 的内部客户端拦截器 SPI

**Files:**
- Create: `linqibin-commons/linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/interceptor/InternalCallInterceptor.java`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/factory/RestClientFactory.java:38-116`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/config/HttpInterfaceAutoConfiguration.java:214-227`
- Modify: `linqibin-commons/linqibin-spring-boot-starter-http-interface/src/test/java/dev/linqibin/starter/httpinterface/factory/RestClientFactoryTest.java`

**Interfaces:**
- Consumes: 现有的 `RestClientFactory.createRestClient(RestClient.Builder, String, String)`。
- Produces: `interface InternalCallInterceptor extends ClientHttpRequestInterceptor`（无新方法，标记用）；`RestClientFactory` 的构造器变成三个参数 `(ObjectProvider<RestClientCustomizer>, ObjectProvider<InternalCallInterceptor>, HttpInterfaceProperties)`；`createRestClient` 创建的客户端带上容器里所有 `InternalCallInterceptor`。`httpInterfaceRestClientBuilder`、`httpInterfaceLoadBalancedRestClientBuilder` 不动。仓库里只有自动配置和这个测试在构造 `RestClientFactory`，各服务不受影响。

- [ ] **Step 1: 写失败的测试**

在 `RestClientFactoryTest` 里：

把 `createFactory` 改成也给一个空的拦截器提供者，`shouldApplyCustomizers` 里的 `new RestClientFactory(customizers, properties)` 改成三参数（多传一个空的 `interceptors`）：

```java
  /// 创建带有空 customizers 和空拦截器的工厂
  @SuppressWarnings("unchecked")
  private RestClientFactory createFactory(HttpInterfaceProperties properties) {
    ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
    when(customizers.orderedStream()).thenReturn(Stream.empty());
    ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
    when(interceptors.orderedStream()).thenReturn(Stream.empty());
    return new RestClientFactory(customizers, interceptors, properties);
  }
```

加一个嵌套测试类（import `dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor`、`org.springframework.test.web.client.MockRestServiceServer`，静态 import `org.springframework.test.web.client.match.MockRestRequestMatchers.header`、`...MockRestRequestMatchers.requestTo`、`org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess`）：

```java
  @Nested
  @DisplayName("InternalCallInterceptor 测试")
  class InternalCallInterceptorTests {

    @Test
    @DisplayName("容器里的 InternalCallInterceptor 被挂到创建的客户端上，请求真的经过它")
    @SuppressWarnings("unchecked")
    void shouldApplyInternalCallInterceptors() {
      // Given
      HttpInterfaceProperties properties = new HttpInterfaceProperties();
      ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
      when(customizers.orderedStream()).thenReturn(Stream.empty());
      InternalCallInterceptor marker =
          (request, body, execution) -> {
            request.getHeaders().set("X-Internal-Call", "yes");
            return execution.execute(request, body);
          };
      ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
      when(interceptors.orderedStream()).thenReturn(Stream.of(marker));
      RestClient.Builder builder = RestClient.builder();
      MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
      server
          .expect(requestTo("http://localhost:8080/ping"))
          .andExpect(header("X-Internal-Call", "yes"))
          .andRespond(withSuccess());

      // When
      RestClient restClient =
          new RestClientFactory(customizers, interceptors, properties)
              .createRestClient(builder, "self", "http://localhost:8080");
      restClient.get().uri("/ping").retrieve().toBodilessEntity();

      // Then
      server.verify();
    }
  }
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :linqibin-commons:linqibin-spring-boot-starter-http-interface:test --tests "*RestClientFactoryTest*"`
Expected: 编译失败，报 `interceptor` 包里的类型找不到。

- [ ] **Step 3: 写 SPI，改工厂和自动配置**

`src/main/java/dev/linqibin/starter/httpinterface/interceptor/InternalCallInterceptor.java`：

```java
package dev.linqibin.starter.httpinterface.interceptor;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/// 只挂到内部服务客户端上的请求拦截器。
///
/// `RestClientCustomizer` 作用于容器里每一个 RestClient.Builder，包括调外部数据源的客户端；
/// 实现本接口的 Bean 只会被 `RestClientFactory` 加到它创建的内部客户端上。安全 starter 用它
/// 转发身份断言。没有新方法，只是标记。
public interface InternalCallInterceptor extends ClientHttpRequestInterceptor {}
```

`RestClientFactory`：

1. import `dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor`。
2. 类注释里的使用示例改成三参数：`return new RestClientFactory(customizers, interceptors, properties);`，并把示例方法的参数列表加上 `ObjectProvider<InternalCallInterceptor> interceptors`。
3. 字段、构造器改成：

```java
  private final ObjectProvider<RestClientCustomizer> customizers;
  private final ObjectProvider<InternalCallInterceptor> internalCallInterceptors;
  private final HttpInterfaceProperties properties;

  /// 构造 RestClient 工厂
  ///
  /// @param customizers RestClient 自定义器提供者
  /// @param internalCallInterceptors 只挂到内部客户端上的拦截器提供者
  /// @param properties HTTP Interface 配置属性
  public RestClientFactory(
      ObjectProvider<RestClientCustomizer> customizers,
      ObjectProvider<InternalCallInterceptor> internalCallInterceptors,
      HttpInterfaceProperties properties) {
    this.customizers = customizers;
    this.internalCallInterceptors = internalCallInterceptors;
    this.properties = properties;
  }
```

4. `createRestClient` 里，应用完 `RestClientCustomizer` 之后、`build()` 之前加：

```java
    // 只给内部客户端挂的拦截器（比如转发身份断言）。
    // RestClientCustomizer 也会作用到外部数据源的客户端上，这些拦截器不会。
    internalCallInterceptors.orderedStream().forEach(clientBuilder::requestInterceptor);
```

`HttpInterfaceAutoConfiguration`：import `dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor`，`restClientFactory` 这个 Bean 方法改成：

```java
  /// 注册 RestClient 工厂
  ///
  /// 提供通用的 RestClient 创建逻辑，简化各服务的 HTTP Interface 配置。
  /// 自动应用分组级别的超时配置、错误处理器，以及只给内部客户端的拦截器。
  ///
  /// @param customizers RestClient 自定义器提供者
  /// @param internalCallInterceptors 只挂到内部客户端上的拦截器提供者
  /// @param properties HTTP Interface 配置属性
  /// @return RestClient 工厂实例
  @Bean
  @ConditionalOnMissingBean
  public RestClientFactory restClientFactory(
      ObjectProvider<RestClientCustomizer> customizers,
      ObjectProvider<InternalCallInterceptor> internalCallInterceptors,
      HttpInterfaceProperties properties) {
    log.info("注册 RestClientFactory");
    return new RestClientFactory(customizers, internalCallInterceptors, properties);
  }
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `"$W/gradlew" -p "$W" :linqibin-commons:linqibin-spring-boot-starter-http-interface:spotlessApply :linqibin-commons:linqibin-spring-boot-starter-http-interface:test :linqibin-commons:linqibin-spring-boot-starter-http-interface:checkBoundary`
Expected: BUILD SUCCESSFUL，新用例通过，原有用例不变，边界检查通过（http-interface starter 没有依赖任何 patra 模块）。

- [ ] **Step 5: 提交**

```bash
git -C "$W" add linqibin-commons/linqibin-spring-boot-starter-http-interface/src
git -C "$W" commit -m "给内部客户端加只挂在它们身上的拦截器 SPI (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: 内部客户端转发断言

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/forward/IdentityAssertionForwardingInterceptor.java`
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfiguration.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/forward/IdentityAssertionForwardingInterceptorTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfigurationTest.java`
- Test: `patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/IdentityAssertionForwardingIT.java`

**Interfaces:**
- Consumes: 任务 4 的 `CurrentUserAuthentication.getCredentials()`；任务 8 的 `InternalCallInterceptor`、`RestClientFactory`。
- Produces: `IdentityAssertionForwardingInterceptor implements InternalCallInterceptor`，当前线程的认证对象是带断言的 `CurrentUserAuthentication` 时 `setBearerAuth(断言)`，否则不动请求；自动配置 `SecurityForwardingAutoConfiguration`（classpath 上有 `InternalCallInterceptor` 时注册拦截器 Bean）。spec 第 13 节写的是放进 `SecurityCoreAutoConfiguration`，这里改成独立的自动配置类：拦截器实现了 http-interface starter 的接口，没有那个 starter 时连拦截器的类都加载不了，类级别的 `@ConditionalOnClass(name = …)` 才能挡住。任务 11 把这点写回 spec。

- [ ] **Step 1: 写拦截器的失败测试**

`src/test/java/dev/linqibin/patra/starter/security/forward/IdentityAssertionForwardingInterceptorTest.java`：

```java
package dev.linqibin.patra.starter.security.forward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.patra.starter.security.context.CurrentUserRunner;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/// IdentityAssertionForwardingInterceptor 单元测试。
@DisplayName("IdentityAssertionForwardingInterceptor 单元测试")
class IdentityAssertionForwardingInterceptorTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final String ASSERTION = "header.payload.signature";

  private final IdentityAssertionForwardingInterceptor interceptor =
      new IdentityAssertionForwardingInterceptor();
  private final SecurityContextHolderStrategy strategy =
      SecurityContextHolder.getContextHolderStrategy();

  @AfterEach
  void clearContext() {
    strategy.clearContext();
  }

  @Test
  @DisplayName("当前用户带断言：出站请求带上 Authorization: Bearer 断言")
  void should_forward_assertion_when_current_user_has_one() throws IOException {
    authenticate(new CurrentUserAuthentication(USER, ASSERTION));

    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + ASSERTION);
  }

  @Test
  @DisplayName("当前用户没有断言（比如网关构造的）：不加头")
  void should_not_add_header_for_user_without_assertion() throws IOException {
    authenticate(new CurrentUserAuthentication(USER));

    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("没有安全上下文：不加头")
  void should_not_add_header_without_security_context() throws IOException {
    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("CurrentUserRunner 放进去的用户：不加头")
  void should_not_add_header_inside_current_user_runner() {
    HttpHeaders sent =
        CurrentUserRunner.callAs(
            USER,
            () -> {
              try {
                return send(new MockClientHttpRequest(HttpMethod.GET, uri()));
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            });

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("业务代码自己设了 Authorization 头：被断言覆盖，不会两个并存")
  void should_overwrite_existing_authorization_header() throws IOException {
    authenticate(new CurrentUserAuthentication(USER, ASSERTION));
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, uri());
    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

    HttpHeaders sent = send(request);

    assertThat(sent.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + ASSERTION);
  }

  /// 把认证对象放进当前线程的安全上下文。
  private void authenticate(Authentication authentication) {
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(authentication);
    strategy.setContext(context);
  }

  /// 经拦截器发出请求，返回真正发出去的请求头。
  private HttpHeaders send(MockClientHttpRequest request) throws IOException {
    HttpHeaders sent = new HttpHeaders();
    interceptor.intercept(
        request,
        new byte[0],
        (forwarded, body) -> {
          sent.addAll(forwarded.getHeaders());
          return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });
    return sent;
  }

  private static URI uri() {
    return URI.create("http://patra-registry/_internal/provenances");
  }
}
```

- [ ] **Step 2: 写自动配置的失败测试**

`src/test/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfigurationTest.java`：

```java
package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.forward.IdentityAssertionForwardingInterceptor;
import dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// SecurityForwardingAutoConfiguration 自动配置测试。
@DisplayName("SecurityForwardingAutoConfiguration 自动配置测试")
class SecurityForwardingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SecurityCoreAutoConfiguration.class, SecurityForwardingAutoConfiguration.class));

  @Test
  @DisplayName("classpath 上有 http-interface starter：注册转发拦截器，它是一个 InternalCallInterceptor")
  void should_register_interceptor_when_http_interface_starter_present() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(IdentityAssertionForwardingInterceptor.class);
          assertThat(context).hasSingleBean(InternalCallInterceptor.class);
        });
  }

  @Test
  @DisplayName("classpath 上没有 http-interface starter：不注册，应用照常启动")
  void should_skip_when_http_interface_starter_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(InternalCallInterceptor.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.containsBean("identityAssertionForwardingInterceptor")).isFalse();
            });
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(
            "dev.linqibin.patra.starter.security.config.SecurityForwardingAutoConfiguration");
  }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionForwardingInterceptorTest*" --tests "*SecurityForwardingAutoConfigurationTest*"`
Expected: 编译失败，报 `forward` 包和 `SecurityForwardingAutoConfiguration` 找不到。

- [ ] **Step 4: 写拦截器和自动配置**

`src/main/java/dev/linqibin/patra/starter/security/forward/IdentityAssertionForwardingInterceptor.java`：

```java
package dev.linqibin.patra.starter.security.forward;

import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/// 内部客户端的拦截器：当前线程有带断言的用户时，把网关签的断言原样放进出站请求。
///
/// 只挂在 `RestClientFactory` 创建的内部客户端上，外部数据源的客户端不经过这里。
/// `CurrentUserRunner` 放进去的用户没有断言，不转发：离开请求，用户身份是数据，不是凭据。
public final class IdentityAssertionForwardingInterceptor implements InternalCallInterceptor {

  /// 有断言就写进 `Authorization: Bearer`，覆盖已有的同名头；没有就原样放行。
  ///
  /// @param request 出站请求
  /// @param body 请求体
  /// @param execution 后续的执行链
  /// @return 响应
  /// @throws IOException 后续执行失败时
  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication instanceof CurrentUserAuthentication user
        && user.getCredentials() instanceof String assertion) {
      request.getHeaders().setBearerAuth(assertion);
    }
    return execution.execute(request, body);
  }
}
```

`src/main/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfiguration.java`：

```java
package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.starter.security.forward.IdentityAssertionForwardingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 转发断言的自动配置：classpath 上有 http-interface starter 时，注册内部客户端的拦截器。
///
/// 单独一个类，条件写类名字符串：拦截器实现了 http-interface starter 的接口，没有那个 starter
/// 时连拦截器的类都加载不了，只有类级别的条件能在加载前挡住。不带 Web 条件：拦截器本身
/// 在任何环境都能装配，只是没有请求线程时拿不到断言，什么都不转发。
@AutoConfiguration(after = SecurityCoreAutoConfiguration.class)
@ConditionalOnClass(name = "dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor")
public class SecurityForwardingAutoConfiguration {

  /// 注册转发断言的拦截器。
  ///
  /// @return 拦截器
  @Bean
  @ConditionalOnMissingBean
  public IdentityAssertionForwardingInterceptor identityAssertionForwardingInterceptor() {
    return new IdentityAssertionForwardingInterceptor();
  }
}
```

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 加一行，放在 `SecurityCoreAutoConfiguration` 之后：

```
dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration
dev.linqibin.patra.starter.security.config.SecurityForwardingAutoConfiguration
dev.linqibin.patra.starter.security.config.SecurityAuditingAutoConfiguration
dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration
```

- [ ] **Step 5: 跑单元测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionForwardingInterceptorTest*" --tests "*SecurityForwardingAutoConfigurationTest*"`
Expected: BUILD SUCCESSFUL，5 + 3 个用例通过。

- [ ] **Step 6: 写集成测试**

`src/integrationTest/java/dev/linqibin/patra/starter/security/IdentityAssertionForwardingIT.java`：

```java
package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.httpinterface.factory.RestClientFactory;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;

/// 内部客户端转发断言的集成测试：spec 第 10 节。
///
/// 测试应用用 `RestClientFactory` 建一个指向自己的内部客户端，在当前线程放一个带断言的用户，
/// 再经它调自己的探针接口，看对方认出了谁。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("内部客户端转发断言 集成测试")
class IdentityAssertionForwardingIT {

  @Autowired private Environment environment;
  @Autowired private RestClientFactory factory;

  @Autowired
  @Qualifier("httpInterfaceRestClientBuilder")
  private RestClient.Builder builder;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("当前线程有带断言的用户：经内部客户端调过去，对方认出同一个用户")
  void should_forward_assertion_through_internal_client() {
    login();

    String whoami = internalClient().get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("1001:2001:user:web");
  }

  @Test
  @DisplayName("当前线程没有用户：对方是匿名")
  void should_call_anonymously_without_current_user() {
    String whoami = internalClient().get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("anonymous");
  }

  @Test
  @DisplayName("不经 RestClientFactory 建的客户端没有拦截器：对方是匿名")
  void should_not_forward_through_plain_builder() {
    login();
    RestClient plain = builder.clone().baseUrl(baseUrl()).build();

    String whoami = plain.get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("anonymous");
  }

  /// 用工厂建的、指向本应用的内部客户端。
  private RestClient internalClient() {
    return factory.createRestClient(builder, "self", baseUrl());
  }

  /// 本应用的地址，端口是随机的。
  private String baseUrl() {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port");
  }

  /// 在当前线程放一个带断言的用户，模拟验签通过后的请求线程。
  private static void login() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new CurrentUserAuthentication(TestIdentity.user(), TestIdentity.assertion()));
    SecurityContextHolder.setContext(context);
  }
}
```

- [ ] **Step 7: 跑集成测试，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:integrationTest --tests "*IdentityAssertionForwardingIT*"`
Expected: BUILD SUCCESSFUL，3 个用例通过。这一步确认 spec 第 15 节第 10 条（拦截器只在工厂创建的客户端上）。如果第一个用例拿到的是 `anonymous`，先确认 `SecurityForwardingAutoConfiguration` 被加载（启动日志里 `RestClientFactory` 注册时容器里已有拦截器），再查工厂有没有把拦截器加上。

- [ ] **Step 8: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/forward \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfiguration.java \
  patra-starters/patra-spring-boot-starter-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/forward \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/config/SecurityForwardingAutoConfigurationTest.java \
  patra-starters/patra-spring-boot-starter-security/src/integrationTest/java/dev/linqibin/patra/starter/security/IdentityAssertionForwardingIT.java
git -C "$W" commit -m "内部客户端替用户调用时原样转发断言 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: 密钥生成器与 Gradle 任务

**Files:**
- Create: `patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGenerator.java`
- Modify: `patra-starters/patra-spring-boot-starter-security/build.gradle.kts`（文件末尾加任务）
- Test: `patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGeneratorTest.java`

**Interfaces:**
- Produces: `IdentityAssertionKeyGenerator.generate()` 返回 `GeneratedKey(String privateJwk, String publicJwkSet)`；`main` 把两行打到标准输出；Gradle 任务 `generateIdentityAssertionKey`（PAP-66 的 runbook 用）。

- [ ] **Step 1: 写失败的测试**

`src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGeneratorTest.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionKeyGenerator.GeneratedKey;
import java.text.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IdentityAssertionKeyGenerator 单元测试。
@DisplayName("IdentityAssertionKeyGenerator 单元测试")
class IdentityAssertionKeyGeneratorTest {

  @Test
  @DisplayName("生成一对 P-256 密钥：私钥 JWK 含 d，公钥 JWK Set 不含 d，kid 相同且等于指纹")
  void should_generate_matching_private_jwk_and_public_jwk_set()
      throws ParseException, JOSEException {
    GeneratedKey generated = IdentityAssertionKeyGenerator.generate();

    ECKey privateKey = ECKey.parse(generated.privateJwk());
    JWKSet publicSet = JWKSet.parse(generated.publicJwkSet());
    JWK publicKey = publicSet.getKeys().getFirst();

    assertThat(privateKey.isPrivate()).isTrue();
    assertThat(privateKey.getCurve()).isEqualTo(Curve.P_256);
    assertThat(publicSet.getKeys()).hasSize(1);
    assertThat(publicKey.isPrivate()).isFalse();
    assertThat(publicKey.getKeyID())
        .isEqualTo(privateKey.getKeyID())
        .isEqualTo(privateKey.computeThumbprint().toString());
    assertThat(generated.privateJwk()).doesNotContain("\n");
    assertThat(generated.publicJwkSet()).doesNotContain("\n");
  }

  @Test
  @DisplayName("每次生成的密钥都不同")
  void should_generate_fresh_key_each_time() throws JOSEException {
    assertThat(IdentityAssertionKeyGenerator.generate().privateJwk())
        .isNotEqualTo(IdentityAssertionKeyGenerator.generate().privateJwk());
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionKeyGeneratorTest*"`
Expected: 编译失败，报 `IdentityAssertionKeyGenerator` 找不到。

- [ ] **Step 3: 写生成器和 Gradle 任务**

`src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGenerator.java`：

```java
package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

/// 生成一对身份断言的签名密钥：私钥给网关，公钥给每个下游。
///
/// 由 Gradle 任务 `generateIdentityAssertionKey` 运行，输出两行 JSON。生成的密钥不落盘，
/// 由运维把两行分别注入网关的 secret 和下游的配置（PAP-66 的 runbook）。
public final class IdentityAssertionKeyGenerator {

  /// 工具类，不允许实例化。
  private IdentityAssertionKeyGenerator() {}

  /// 命令行入口：第一行私钥 JWK，第二行公钥 JWK Set。
  ///
  /// @param args 不用
  /// @throws JOSEException 生成失败时
  public static void main(String[] args) throws JOSEException {
    GeneratedKey generated = generate();
    System.out.println("私钥（只给网关，由 secret 注入）：");
    System.out.println(generated.privateJwk());
    System.out.println("公钥（给每个下游的 patra.security.identity-assertion.public-keys）：");
    System.out.println(generated.publicJwkSet());
  }

  /// 生成一对 P-256 密钥，`kid` 是公钥的 JWK 指纹。
  ///
  /// @return 私钥 JWK 和公钥 JWK Set，都是一行 JSON
  /// @throws JOSEException 生成失败时
  public static GeneratedKey generate() throws JOSEException {
    ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    return new GeneratedKey(key.toJSONString(), new JWKSet(key.toPublicJWK()).toString(true));
  }

  /// 生成结果。
  ///
  /// @param privateJwk 含私钥的 JWK JSON，一行
  /// @param publicJwkSet 只含公钥的 JWK Set JSON，一行
  public record GeneratedKey(String privateJwk, String publicJwkSet) {}
}
```

`build.gradle.kts` 文件末尾加：

```kotlin
// 生成一对身份断言的签名密钥：私钥给网关，公钥给每个下游（PAP-66 的 runbook 调用）
tasks.register<JavaExec>("generateIdentityAssertionKey") {
    group = "patra"
    description = "生成一对身份断言的签名密钥（JWK JSON）：私钥给网关，公钥给下游"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.linqibin.patra.starter.security.assertion.IdentityAssertionKeyGenerator"
}
```

- [ ] **Step 4: 跑测试和任务，确认通过**

Run: `"$W/gradlew" -p "$W" :patra-starters:patra-spring-boot-starter-security:spotlessApply :patra-starters:patra-spring-boot-starter-security:test --tests "*IdentityAssertionKeyGeneratorTest*"`
Expected: BUILD SUCCESSFUL，2 个用例通过。

Run: `"$W/gradlew" -p "$W" -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey`
Expected: 四行输出，第二行是 `{"kty":"EC",…,"d":…}`，第四行是 `{"keys":[{"kty":"EC",…}]}` 且不含 `"d"`。输出只看，不保存。

- [ ] **Step 5: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/build.gradle.kts \
  patra-starters/patra-spring-boot-starter-security/src/main/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGenerator.java \
  patra-starters/patra-spring-boot-starter-security/src/test/java/dev/linqibin/patra/starter/security/assertion/IdentityAssertionKeyGeneratorTest.java
git -C "$W" commit -m "加生成签名密钥对的工具和 Gradle 任务 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: README、spec 写回、模块图与全量检查

**Files:**
- Modify: `patra-starters/patra-spring-boot-starter-security/README.md`（整个重写）
- Modify: `docs/patra/specs/2026-10-05-security-starter-design.md`（第 13 节一句、第 15 节第 6–10 行、状态行）
- Modify: `patra-infra/cd/module-graph.json`（由任务生成）

**Interfaces:**
- Consumes: 前面所有任务的类名与配置项名。

- [ ] **Step 1: 重写 README**

`patra-starters/patra-spring-boot-starter-security/README.md` 整个换成：

````markdown
# patra-spring-boot-starter-security

基于 Spring Security 的安全 starter。它让服务从网关签的身份断言得到「当前用户」，把 401 / 403 输出成统一的 ProblemDetail 格式，并在服务替用户调用另一个服务时把断言带过去。只做 servlet 一套。

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

只有一个配置项：网关签断言用的那对密钥里的公钥。它不是机密，随各环境的配置给出（下面的环境变量名只是示例）：

```yaml
patra:
  security:
    identity-assertion:
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}   # JWK Set JSON，一行，可含多把
```

没配、不是 JWK Set、没有公钥、混进了私钥、不是 EC P-256、缺 `kid` 时，应用启动失败，错误信息指明原因。它只在 servlet 应用里必填。

生成一对密钥：

```bash
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey
```

输出两行 JSON：含私钥的 JWK 只给网关，由 secret 注入；只含公钥的 JWK Set 给每个下游。换密钥时先把新公钥加进各下游的 `keys` 并重启，再给网关换私钥，最后删掉旧公钥。

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

执行结束（包括抛异常）后会恢复原来的身份。这样放进去的用户没有断言，内部调用不会替他转发。

## 5. 服务替用户调用另一个服务

用 http-interface starter 的 `RestClientFactory` 建的内部客户端会自动把当前请求的断言放进出站请求，对方按同一套规则验签，得到同一个用户。不用写任何代码。

- 只在同一个请求的同步调用里生效；断言 60 秒有效，覆盖一次请求绰绰有余。
- 领域事件、定时任务、消息消费里没有断言：发起人的用户 ID 作为数据写进事件或任务，消费方以系统身份执行。
- 调外部数据源的客户端（rest-client starter）不经过这里，断言不会带出去。

## 6. 测试怎么写

测试依赖里加上本模块的 testFixtures：

```kotlin
testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
```

它带来三样东西：`TestIdentity`、自动写进环境的测试公钥、`spring-boot-security-test`。测试密钥每次在 JVM 里现生成，私钥只在内存里。

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

`TestIdentity.headers(42L)` 指定用户 ID，`TestIdentity.headers(CurrentUser.of(…))` 指定全部字段。要构造过期、内容不合法等异常断言，用 `TestSigningKey.claims(user)` 改坏一处再 `TestSigningKey.sign(...)`。

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

## 7. 信任前提

- 私钥只在网关。谁拿到私钥，谁就能冒充任何用户。
- 服务不对外暴露，外部请求只能经过网关；网关转发前删掉外部请求自带的 `Authorization` 头，再写入自己签的断言。
- 没带断言的请求按匿名处理，不会被拒绝：服务之间直连的调用（`/_internal/**`）可以不带断言。断言无效（签名、`typ`、`iss`、`aud`、过期任一不对）返回 401；签名正确但内容不合法返回 500。
- 服务之间调用时调用方自己的身份不验证，见 release spec 边界 F。

## 给网关用的部分

网关（PAP-65）声明自己的过滤器链，本 starter 的默认链随之让位。网关复用这几样：

| 类 | 用途 |
|---|---|
| `StatelessSecurityDefaults.apply(http, problemWriter)` | 无会话、关 CSRF、关登出、401 / 403 走统一写出器 |
| `CurrentUserAuthentication` | 查到会话后构造的认证对象（不带凭据） |
| `IdentityAssertionSigner` | 用私钥 JWK 和 `Clock` 构造；`sign(user)` 签一个 60 秒的断言，`setBearerAuth` 写进转发的请求 |
| `SecurityProblemWriter` | 过滤器链里的失败输出 |
| `generateIdentityAssertionKey` 任务 | 生成密钥对 |
````

- [ ] **Step 2: 写回 spec**

`docs/patra/specs/2026-10-05-security-starter-design.md`：

1. 第 13 节表格里 `SecurityCoreAutoConfiguration` 那行的「classpath 上有 `RestClientFactory` 时再注册 `IdentityAssertionForwardingInterceptor`」删掉，表格加一行：

```
| `SecurityForwardingAutoConfiguration` | classpath 上有 `InternalCallInterceptor`（按类名判断）；排在核心配置之后 | `IdentityAssertionForwardingInterceptor` |
```

并在表格后补一句：「转发拦截器单独一个自动配置类：它实现了 http-interface starter 的接口，没有那个 starter 时连类都加载不了，只有类级别的条件能在加载前挡住。」

2. 第 15 节第 6–10 行的「结果」列按实际结果改成「成立（PAP-70）」或写明偏差；「对应测试」列填真实的测试类名：第 6、7 条 `IdentityAssertionDecodersTest`，第 8 条 `IdentityAssertionAuthenticationIT`，第 9 条 `StatelessSessionIT`（如果没有为它单独加断言，改成 `SecurityServletAutoConfigurationTest` 里「过滤器链只有一条」的断言，并在那个用例里加上 `assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1)`），第 10 条 `IdentityAssertionForwardingIT`。
3. 文件头的「状态」改成「首版与改版都已实现」。

- [ ] **Step 3: 重新生成模块图，跑边界检查和全量检查**

```bash
"$W/gradlew" -p "$W" dumpModuleGraph
git -C "$W" diff --stat -- patra-infra/cd/module-graph.json
"$W/gradlew" -p "$W" :linqibin-commons:linqibin-spring-boot-starter-http-interface:checkBoundary \
  :patra-starters:patra-spring-boot-starter-security:check \
  :linqibin-commons:linqibin-spring-boot-starter-http-interface:check \
  :patra-api:patra-catalog:patra-catalog-boot:check \
  :patra-api:patra-ingest:patra-ingest-boot:check
grep -rn "IdentityHeaders\|GATEWAY_TOKEN\|gateway-token\|X-Patra-" "$W/patra-starters" "$W/patra-api" "$W/linqibin-commons" "$W/docs/patra/specs/2026-10-05-security-starter-design.md" | grep -v '/build/'
```

Expected: 模块图的 diff 只在安全 starter 的依赖里多了 http-interface starter（没有 diff 也正常：`compileOnly` 可能不进图，核对一下 `dumpModuleGraph` 的口径再决定）；四个模块 `check` 全绿（catalog、ingest 是 http-interface starter 的使用方，确认工厂改构造器没波及）；`grep` 没有输出。

- [ ] **Step 4: 提交**

```bash
git -C "$W" add patra-starters/patra-spring-boot-starter-security/README.md \
  docs/patra/specs/2026-10-05-security-starter-design.md \
  patra-infra/cd/module-graph.json
git -C "$W" commit -m "改写安全 starter 的 README，把实测结果写回工程设计 (PAP-70)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

`module-graph.json` 没变化时从 `add` 里去掉它。

- [ ] **Step 5: 收尾清单**

- Linear PAP-70 的 To-Do 逐条勾掉；AC 逐条对照本计划的测试勾掉；状态推到 BE。
- 整条分支的最终评审走 executing-plans 的 Final Review；评审完再决定是否跑一次全仓库 `./gradlew check`（PR 前必跑）。
- 不推送、不开 PR：后端 PR 在本版全部 BE Issue 完成后统一开。

---

## 自查记录

- **spec 覆盖**：6.1 → 任务 1、2；6.2 → 任务 2；6.3 → 任务 3；6.4 → 任务 5（配置与校验）、任务 10（生成）；7 → 任务 4；8.2 → 任务 4、7；8.3 → 任务 5；9.2 的 `BadCredentialsException` 行 → 任务 4（走现有映射，无代码改动）；10 → 任务 8、9；13 → 任务 5、9；14.1 → 任务 6；14.2 → 任务 1–10 各自的测试，回归在任务 11；15 → 任务 3、7、9 的 Expected 和任务 11 的写回；16 → 任务 11；17 的 PAP-65 / 66 约束不在本计划内（接口已由任务 2、10 提供）。
- **Review Focus**：1 → 任务 5 `should_reject_private_key`；2 → 任务 3 `should_reject_unsecured_and_hmac_tokens`；3 → 任务 1 参数化用例；4 → 任务 9 `should_overwrite_existing_authorization_header`；5 → 任务 3 `should_apply_clock_skew_window`。
- **类型一致性**：`IdentityAssertionSigner(ECKey, Clock)` / `sign(CurrentUser)` 在任务 2、6、9（IT）一致；`IdentityAssertionDecoders.forPublicKeys(JWKSet, Clock)` 在任务 3、4、5、6 一致；`CurrentUserAuthentication(CurrentUser, String)` 在任务 4、9 一致；`RestClientFactory(ObjectProvider<RestClientCustomizer>, ObjectProvider<InternalCallInterceptor>, HttpInterfaceProperties)` 在任务 8、9（经容器注入）一致；`IdentityAssertion.PUBLIC_KEYS_PROPERTY` 在任务 5、6 一致。
- **已知的中间态**：任务 6 提交后 `integrationTest` 源码编译不过，任务 7 修复；任务 6 的验证只跑 `:test`。
