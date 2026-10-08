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
