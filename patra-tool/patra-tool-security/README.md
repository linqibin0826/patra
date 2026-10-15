# patra-tool-security

## 能力

给服务提供无状态的身份校验：网关在请求上签一份身份断言（JWT），本模块用网关的公钥验签，把当前用户放进安全上下文。自动配置共四个：`SecurityAutoConfiguration` 注册取当前用户的 `CurrentUserPort`；`SecurityServletAutoConfiguration`（仅 Servlet 应用）装配无状态过滤器链、身份断言 JWT 解码、把安全异常转成 ProblemDetail 的输出，以及拒绝一切的 `AuthenticationManager`（服务自己不做账号密码认证）。

另外两个按类路径条件生效：`SecurityAuditingAutoConfiguration`（类路径有 `patra-tool-jpa` 时把当前用户接到 JPA 审计）和 `SecurityForwardingAutoConfiguration`（类路径有 `patra-tool-http-client` 时把身份断言转发给下游）。`src/testFixtures` 提供测试用的公钥注入（`TestIdentityAssertionEnvironmentPostProcessor`）。

## 配置（前缀 `patra.security`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关，关掉后本模块不注册任何 bean |
| `identity-assertion.public-keys` | String | 无（必填） | 网关的公钥，JWK Set JSON，只含公钥，可以多把；必须是带 `kid` 的 EC P-256 公钥，含私钥或缺失都会让应用启动失败 |

## 怎么覆盖

```yaml
patra:
  security:
    identity-assertion:
      public-keys: '{"keys":[{"kty":"EC","crv":"P-256","kid":"gateway-1","x":"...","y":"..."}]}'
```

关闭整个模块：`patra.security.enabled: false`。

## 出哪些默认值

无。
