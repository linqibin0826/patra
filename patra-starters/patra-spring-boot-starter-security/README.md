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

身份头没有签名。下游只认带着正确内部令牌的身份头，安全性依赖三件事：

- 服务不对外暴露，外部请求只能经过网关。
- 内部令牌不泄露。
- 网关转发前删掉外部请求自带的四个身份头和内部令牌头，再写入自己的。漏删的话，外部请求可以冒充任意用户。

内部令牌缺失或不对的请求按匿名处理，不会被拒绝：服务之间直连的调用（`/_internal/**`）不带令牌。

## 给网关用的部分

网关（PAP-65）声明自己的过滤器链，本 starter 的默认链随之让位。网关复用这几样：

| 类 | 用途 |
|---|---|
| `StatelessSecurityDefaults.apply(http, problemWriter)` | 无会话、关 CSRF、关登出、401 / 403 走统一写出器 |
| `CurrentUserAuthentication` | 查到会话后构造的认证对象 |
| `IdentityHeaders.IDENTITY_HEADER_NAMES`、`IdentityHeaders.GATEWAY_TOKEN` | 转发前先删掉外部请求自带的这五个头 |
| `IdentityHeaders.write(headers, user)` | 往转发给下游的请求里写身份头 |
| `SecurityProblemWriter` | 过滤器链里的失败输出 |
