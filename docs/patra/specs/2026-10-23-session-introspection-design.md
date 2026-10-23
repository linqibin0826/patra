# 会话存储归 identity 独占：网关调 identity 查会话（PAP-106）

> **状态**：设计（2026-10-23，分支 `refactor/pap-106-session-introspection`，从 `feat/v0.12-user-center-api` 切出）
> **Issue**：PAP-106
> **改写**：`docs/patra/specs/2026-10-09-identity-session-design.md` 第 5 节（模块）与第 7 节（会话模块的接口）、`docs/patra/specs/2026-10-09-gateway-auth-design.md` 的查会话部分、v0.8 release spec 决策 B 的补充与决策 C

## 1. 为什么改

现在网关每个请求直接读 Redis 查会话并续期，为此 identity 与网关共用 `patra-identity-session` 模块（令牌、Redis 键、8 段 Lua、`RedisSessionStore`）。三个问题：

1. 会话的规则（TTL 公式、绝对过期、60 秒续期节流）写在两个服务都依赖的库里，改会话格式要两个服务一起部署。
2. 网关持有 Redis 读写凭据和 identity 全部写方法（实际只用 `findAndTouch` 一个）。
3. 模块落在六层之外，边界要靠文档解释。

2026-10-23 补充调研的结论（来源见 PAP-106 与记忆里的会话存储调研）：

- 和 Patra 结构最像的主流组合是 Ory Kratos（身份服务，自己管会话）+ Oathkeeper（网关）：`bearer_token` 认证器每个请求调 Kratos 的 whoami，`id_token` mutator 给下游签 JWT。Oathkeeper 开源版、Traefik ForwardAuth、Envoy ext_authz、Spring Security 的 `opaqueToken()` 默认都不缓存查询结果。
- v0.8 决策 C 否决这条路的理由是「每请求多一跳、撤销窗口等于缓存 TTL」。不缓存就没有撤销窗口；多一跳在单台 Mac mini 上是个位数毫秒。
- 否决的备选：Spring Session（绝对过期、续期节流、哈希键、Bearer 解析、保留会话 ID 换令牌、批量删除、超限淘汰仍要自补）；Redis Functions（Spring Data Redis 没有 FCALL 接口、无公开先例、引入「先加载后调用」的部署顺序）；格式放 api + 契约测试（行为规则无法只写一份）；短缓存 + Pub/Sub 主动失效（消息会丢，要墓碑兜底，没有成熟产品这么组合，当前规模不需要）。

## 2. 决定

- identity 独占会话存储：`patra-identity-session` 删除，内容并入 `patra-identity-infra`。
- 新建 `patra-identity-api`：查会话的 HTTP Interface 与 DTO，identity 的第一份服务间契约。
- identity 提供内部接口 `POST /_internal/sessions/introspect`：按令牌查会话并节流续期（即原 `findAndTouch`），返回当前用户。
- 网关去掉 Spring Data Redis 依赖、Redis 配置与 `REDIS_PASSWORD`；鉴权转换器改为经 `lb://patra-identity` 调该接口，拿到当前用户后照旧签 60 秒身份断言。
- 不缓存查询结果：登出、封禁、踢设备即时生效，与现状一致。

不做：缓存与主动失效；网关预先校验令牌格式（格式只归 identity）；identity 的其他会话行为（建会话、换令牌、列表、删除）不变。

## 3. 模块

| 模块 | 改动 |
|---|---|
| `patra-identity-session` | 删除。`settings.gradle.kts` 去掉 `includeAt`，`./gradlew dumpModuleGraph` 重新生成 `patra-infra/cd/module-graph.json` |
| `patra-identity-api`（新） | 插件 `patra.hexagonal-api`。`SessionEndpoint`（HTTP Interface）、`SessionIntrospectionReq`、`SessionIntrospectionResp`。没有测试（api 插件关闭测试，与其他服务的 api 模块一致） |
| `patra-identity-domain` | `SessionStorePort` 加 `findAndTouch(String token)`，返回 `Optional<CurrentUser>` |
| `patra-identity-app` | `usecase/session/introspect/SessionIntrospectionService`：只调端口，不经 CommandBus，没有事务（请求路径上的热调用） |
| `patra-identity-infra` | 收下会话模块的类（包 `dev.patra.identity.infra.adapter.session`）、8 段 Lua（`src/main/resources/redis/`）、单测与 `RedisSessionStoreIT`；`SessionStoreAdapter` 实现新方法；`build.gradle.kts` 去掉对会话模块的依赖 |
| `patra-identity-adapter` | `rest/internal/SessionEndpointImpl` 实现 `SessionEndpoint`；依赖 `patra-identity-api` |
| `patra-identity-boot` | `RedisSessionStore` 的 Bean 改引 infra 的类；去掉对会话模块的依赖；集成测试改引 infra 的类 |
| `patra-gateway-boot` | 去掉会话模块依赖，加 `patra-tool-http-client` 与 `patra-identity-api`；`IdentityClientConfiguration`；转换器改造；`application.yml` 去掉 `spring.data.redis`，加 `identity` 分组超时 |
| `patra-infra` | `nacos/dev/patra-gateway.yaml` 去掉 `spring.data.redis.password`；`docker-compose.apps.yaml` 的 gateway 去掉 `redis.env`；`docker/README.md`、`secrets/redis.env.example`、`CLAUDE.md` 里「redis.env 给 gateway」的描述同步 |

依赖方向：`gateway-boot → identity-api ← identity-adapter`。`identity-api` 只依赖 `patra-common`、`patra-tool-core`（插件给出）与 `spring-web` 注解，不带 Redis。

## 4. 契约

```java
@HttpExchange(url = "/_internal/sessions", accept = "application/json", contentType = "application/json")
public interface SessionEndpoint {
  @PostExchange("/introspect")
  SessionIntrospectionResp introspect(@RequestBody SessionIntrospectionReq request);
}

public record SessionIntrospectionReq(String token) { static of(String token) }

@Builder
public record SessionIntrospectionResp(
    boolean active, Long userId, Long sessionId, AccountType accountType, ClientType clientType) {
  static SessionIntrospectionResp inactive();
}
```

- 令牌放请求体，不放 `Authorization`：identity 的过滤器链会把 `Authorization: Bearer` 当身份断言验签，会话令牌放进去会被拒成 401。放请求体也让令牌不进 URL 和访问日志。
- 令牌格式不对、会话不存在、过了绝对期：`200 {"active": false}`，其余字段为空。形状参照 RFC 7662 的 `active`。
- 会话在：`200 {"active": true, "userId": "…", "sessionId": "…", "accountType": …, "clientType": …}`。`Long` 经 identity 的 MVC 转换器写成字符串，网关的 `JsonMapper` 读回 `Long`（集成测试钉住）。
- Redis 暂时不可用：identity 照现有规则回 503（`IDN-0503`，`DEP_UNAVAILABLE`）。
- 外部访问：网关拒绝名单已含 `/*/_internal/**`，`/patra-identity/_internal/sessions/introspect` 从外部打是 403（集成测试补一条）。服务之间直连信任内网，属 v0.8 边界 F。

## 5. identity 侧

- `SessionStoreAdapter.findAndTouch(token)`：`SessionToken.parse` 为空直接返回空，不碰 Redis；否则 `RedisSessionStore.findAndTouch` 再 `StoredSession.toCurrentUser()`。`SessionStoreUnavailableException` 原样穿过。
- `SessionIntrospectionService.introspect(token)`：调端口。不打令牌；日志级别以下也不出现令牌。
- `SessionEndpointImpl`：`Optional<CurrentUser>` → `SessionIntrospectionResp`。
- 安全：下游默认过滤器链对所有路径放行，网关调用时不带断言，按匿名到达；不需要改 identity 的安全配置。
- http-client 的 `LoggingInterceptor` 默认不记请求体（`log-body: false`），网关不开它。

## 6. 网关侧

- `IdentityClientConfiguration`：照 identity 的 `CatalogClientConfiguration` 写法，`RestClientFactory.createRestClient(builder, "identity", "lb://patra-identity")` + `createProxy(..., SessionEndpoint.class)`。
- `application.yml`：`patra.http-client.http-interface.groups.identity`：`base-url: lb://patra-identity`、`connect-timeout: 1s`、`read-timeout: 2s`（沿用原 Redis 命令超时 2 秒的考虑：identity 卡住时几秒内变 503）。
- `SessionTokenAuthenticationConverter(SessionEndpoint)`：取「恰好一个 `Authorization` 且以 `Bearer ` 开头」的值（规则不变），去首尾空白后非空就调 `introspect`；`active=false` 按匿名；`active=true` 建 `CurrentUserAuthentication`。
- 失败翻译（只包住一次代理调用，照 `CatalogReferenceAdapter` 的三类）：

| 情形 | 网关看到的异常 | 转成 | 输出 |
|---|---|---|---|
| identity 回 503（Redis 暂时不可用） | `RemoteCallException`，traits 含 `DEP_UNAVAILABLE` | `AuthenticationServiceException` | 503 |
| 连不上、超时 | `ResourceAccessException` | `AuthenticationServiceException` | 503 |
| 注册中心里没有 identity 实例 | `IllegalStateException`（LoadBalancer） | `AuthenticationServiceException` | 503 |
| identity 回其他错误 | `RemoteCallException` | `SessionLookupFailedException` | 500 |

  不带令牌的请求不调 identity，不受 identity 可用性影响。

## 7. 测试

- identity-infra：会话模块的单测与 `RedisSessionStoreIT` 原样搬入（只改包名）；`SessionStoreAdapterTest` 加 `findAndTouch` 三种情形（格式不对不碰存储、查不到、查到转 `CurrentUser`）。
- identity-app：`SessionIntrospectionServiceTest`。
- identity-adapter：`SessionEndpointImpl` 的 `@WebMvcTest` 切片（active / inactive 的 JSON 形状，`Long` 为字符串）。
- identity-boot：`SessionFlowIT` 加一条：登录 → introspect 为 active → 登出 → introspect 为 inactive；60 秒节流由 `RedisSessionStoreIT` 覆盖。
- gateway 单测：`SessionTokenAuthenticationConverterTest` 改为 mock `SessionEndpoint`，覆盖上表四类失败与 active / inactive。
- gateway 集成测试：去掉 `RedisContainerInitializer`；`GatewayITSessions` 改成给 WireMock 的 identity 桩 introspect（按令牌返回 active / inactive）；`GatewayRedisUnavailableIT` 改成 `GatewayIdentityUnavailableIT`（identity 回 503 problem、identity 端口关闭两种，均 503，且下游未被调用；不带令牌照常放行）；拒绝名单补 `/_internal/sessions/introspect`；ArchUnit 式检查不需要，AC「网关类路径无 Spring Data Redis」用 `./gradlew :patra-api:patra-gateway-boot:dependencies` 核对。

## 8. 上线顺序（合入 main 后）

`ConfigSourceCheckPostProcessor` 会拒绝 Nacos 配置里解析不了的 `${REDIS_PASSWORD}`，所以：

1. CD 部署新网关（compose 仍给 `redis.env`，旧的 Nacos 配置仍能解析，新代码不读它）。
2. 推送 `patra-gateway.yaml`（去掉 Redis 密码）到 mini 的 Nacos。
3. compose 去掉 gateway 的 `redis.env` 后重建网关容器。

这三步的文件改动随本分支提交，执行在合入后由用户确认再做。

## 9. 文档同步

- v0.8 release spec 决策 B 的补充、决策 C 的「网关负责」与否决理由各补一条「补充（2026-10-23，PAP-106）」。
- `2026-10-09-identity-session-design.md`、`2026-10-09-gateway-auth-design.md` 文首加「已被 PAP-106 改写」的指引行。
- identity README：模块表去掉会话模块、加 `patra-identity-api`；会话一节吸收原会话模块 README 的内容（令牌、键、操作表、错误、限制），删掉指向旧 README 的话；验证命令去掉会话模块。
- 网关 README：查会话一节改为调 identity；配置表去掉 `REDIS_PASSWORD`。
- `.claude/rules/project-info.md` 不用改：会话模块删除后没有六层之外的模块。
