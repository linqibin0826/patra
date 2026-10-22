# identity 登录设备工程设计（PAP-99）

> **Issue**：[PAP-99](https://linear.app/papertrace/issue/PAP-99)（GitHub #265）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.8 登录设备、2.1.10 安全通知邮件、决策 I）
> **前置设计**：[identity 会话（PAP-64）](2026-10-09-identity-session-design.md)（会话 HASH、键 2 索引、Lua 约定）、[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)与[网关 WebMVC（PAP-69）](2026-10-09-gateway-webmvc-design.md)（入站转发头剥除、出站头）、[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)（`V1`、第 16 节）、[identity 邮件与邮箱验证（PAP-96）](2026-10-21-identity-email-verification-design.md)（`SecurityNotice`、`OutgoingMail`、XXL-Job 清理任务）、[identity 账号安全（PAP-98）](2026-10-22-identity-account-security-design.md)（`ReauthenticationGuard`、`rotate`、`find`、第 18 节）
> **设计简报**：`patra-portal/docs/design-briefs/v0.12/user-center.md` §3.11 卡片 2「登录设备」、§3.19 B 类安全通知、§9 附录 A
> **日期**：2026-10-22
> **状态**：已实施（2026-10-22，分支 `feat/v0.12-user-center-api`；两条待拍板按用户决定：首次登录按设备认、ip2region 离线库 + 宿主机数据目录）。实施中的偏离与实测见第 24 节

## 1. 要解决的问题

用户看不到自己在哪些设备上登录着，也踢不掉公共电脑上的登录；账号在陌生设备上登录了，用户不知道。根子在链路上：门户是 BFF，浏览器只和门户说话，门户调网关时不带浏览器的 IP 和 User-Agent，网关又（正确地）剥掉一切入站转发头，所以 identity 拿到的「客户端 IP」永远是门户容器的地址，UA 是 Node 的 `undici`。

本 Issue 做五件事：

1. **受信转发链路**（决策 I）：门户把浏览器的 IP 放进 `X-Forwarded-For`、把浏览器的 UA 放进 `User-Agent`；网关只对配置里的代理地址解析 `X-Forwarded-For`（Tomcat `RemoteIpValve`），其他来源照旧剥掉；网关再按自己认定的客户端地址写出站转发头，identity 的 `getRemoteAddr()` 从此是真实客户端 IP（第 6 节）。
2. **登录记录带上设备与属地**：登录、注册时用 Yauaa 把 UA 解析成「浏览器主版本 · 系统族」和端（桌面 / 手机 / 平板），用 ip2region 离线库把 IP 解析成属地；`idn_user_login_record` 加五列，直接改 `V1`（第 8、9 节）。
3. **会话列表与踢设备**：`GET /me/sessions`（只列活跃会话，本机置顶）、`DELETE /me/sessions/{id}`、`DELETE /me/sessions/others`（后两个过再认证）；会话模块加「按用户列出」（第 10、11 节）。
4. **新设备邮件**：设备标识在该账号 180 天内没出现过才发，同设备 24 小时最多 1 封；PAP-98 的四封安全通知同时填上设备与属地（第 12、13 节）。
5. **登录记录清理任务**：结束或过期满 190 天删除（第 14 节）。

设计过程中定下的、影响别的 Issue 的事：

- 门户是唯一受信代理，靠**固定容器地址**认定：mini 上门户容器固定为 `192.168.97.250`，网关的 `server.tomcat.remoteip.internal-proxies` 只写这一个地址；默认值是空，谁都不信（第 6.3 节）。
- 门户要带的头只有两个标准头：`X-Forwarded-For`（一个值：门户自己从连接上看到的浏览器 IP）与 `User-Agent`（浏览器的原值）。不发明 `X-Patra-*` 头（第 6.2 节；PAP-101）。
- 设备标识 Cookie 的值是 32 字节随机数的 base64url（43 个字符），identity 只认这个格式，其他一律 422（第 7 节；PAP-101）。
- 登录记录的结束原因按「谁、为什么」拆细：踢设备是 `REVOKED`，验证邮箱、改密码、重设密码、撤销改邮箱各有自己的原因（第 9.2 节；PAP-100 照此加 `DELETION_REQUESTED`）。
- 「撤销会话 + 结束登录记录」抽成领域服务 `SessionTerminator`，现有五个处理器改用它（第 9.3 节）。
- 会话列表的设备信息来自登录记录，活跃与最后活跃来自 Redis；Redis 会话不加任何设备字段（第 10.2 节）。

**完成标准**：PAP-99 的 Acceptance Criteria，逐条对应见第 18.5 节。

## 2. 范围

做：

- 网关：`server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies`；保留 servlet 层的 `ForwardedHeaderFilter`（`removeOnly`）；集成测试覆盖受信与非受信两种来源。
- 基建：mini 上门户容器固定 IP；受信地址经环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES` 写在 compose 的 gateway 服务块（第 24 节）。
- 会话模块：`session-list.lua` 与 `RedisSessionStore.list`。
- identity：
  - 领域：`DeviceId` 收紧为 43 字符 base64url；`RequestOrigin`（IP + UA 原值）与解析后的 `ClientOrigin`；`UserAgentParsingPort`、`IpGeolocationPort` 与 `ClientOriginResolver`；`LoginEndReason` 拆细；`SessionTerminator`；`NewDeviceLoginPolicy` 与 `NewDeviceMailThrottlePort`；`UserLoginRecord` 加设备与属地；`SessionStorePort.listActive`；`NewDeviceLoginMail`。
  - 应用：`LoginUserHandler`、`RegisterUserHandler` 记设备与属地，登录后判新设备发信；`RevokeSessionHandler`、`RevokeOtherSessionsHandler`；`SessionQueryService`；`PurgeRetiredLoginRecordsHandler`；PAP-98 四个处理器的 `SecurityNotice` 填真实值；五个处理器改用 `SessionTerminator`。
  - 基础设施：Yauaa 与 ip2region 适配器；登录记录的新列、查询与批量删除；新设备邮件限流适配器；`new-device-login` 两个模板。
  - 适配器：`MeSessionController`；认证与 PAP-98 的控制器把 `User-Agent` 传进命令；`LoginRecordCleanupScheduleJob`。
  - `V1`：`idn_user_login_record` 加列、改结束原因约束、加索引。
- 文档：本 spec；identity、session、gateway README；infra 的 compose 与 README 说明。
- Linear：PAP-101、PAP-102、PAP-100 各加「来自 PAP-99 的契约」。

本 Issue 不做：

- 门户的设备标识 Cookie、转发头、安全页的设备列表（PAP-101 / PAP-102）。
- 「被踢下线」与「过期」的区分提示（PAP-64 第 16 节提过 Sa-Token 式标记）：被踢的设备下一个请求就是普通的 401，门户按会话失效处理。设计简报没有要求区分。
- 公开主页展示 IP 属地（边界 A）。
- 后台查看任意用户的登录记录（边界 H）。
- identity 自己的端口 6400 在 tailnet 上能被直接访问、直连时可伪造 `X-Forwarded-For`：服务之间的机器身份是 v0.8 边界 F 的事（决策 I「为什么」一段）。直连只影响记录里的 IP，拿不到别人的会话：`/me/**` 只认网关签的断言。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 列表只列活跃会话：设备名（浏览器主版本 · 系统族，Yauaa）、端、IP 与属地（ip2region 到国家 / 省 / 市，IP 不出服务器）、登录时间、最后活跃（精度 60 秒）；本机置顶 | release spec 2.1.8 |
| 踢设备先过再认证；可踢单个或其他全部；本机只能登出；结束原因 `REVOKED` | release spec 2.1.8 |
| 新设备邮件：设备标识在该账号 180 天内没出现过才发；注册后第一次登录不发；同设备 24 小时最多 1 封；门户种长期随机设备标识 Cookie，登录时带上 | release spec 2.1.8 |
| 门户是唯一受信代理，把客户端 IP 和 UA 转给网关；网关只信任配置里的代理地址，其他来源一律剥掉；identity 登录时写入 | 决策 I |
| 登录记录在结束或过期满 190 天后清理（日志留存 ≥ 6 个月） | release spec 2.1.8 |
| 安全通知：新设备登录要发；常用设备登录、主动登出、自己踢设备不发；内容含时间、设备、IP 与属地、「不是我」；不放登录链接 | release spec 2.1.10 |
| 新设备邮件是一个实现 `OutgoingMail` 的记录 + `html` / `text` 同名模板，复用 `security-notice` 片段；「建议到安全设置……」一句写在模板正文里，`securityUrl` 照旧由渲染器提供 | PAP-96 第 18 节、PAP-98 第 9.2、18 节 |
| 踢设备先 `reauthenticationGuard.requireRecent(current)`：会话不在 401，超时 403 + `REAUTH_REQUIRED`；`revoke` / `revokeAllExcept` 已有 | PAP-98 第 7.3、18 节 |
| `ChangePasswordHandler`、`ResetPasswordHandler`、`ConfirmEmailChangeHandler`、`RevertEmailChangeHandler` 的 `SecurityNotice` 把 `null` 换成真实设备、IP、属地 | PAP-98 第 18 节 |
| 会话 HASH 已有 `device_id`、`last_active_at`（网关续期时距上次写入满 60 秒才写）；`find` 不续期 | PAP-64、PAP-98 第 6.3 节 |
| 令牌表的 `requested_ip` 是 `VARCHAR(45)`，登录记录的 IP 用同样的文本列 | PAP-96 第 18 节 |
| identity 库推倒重建，改 `V1`，不写 `ALTER` | 决策 J |
| 定时任务走 XXL-Job（执行器 `patra-identity`，端口 6401），保留期从配置读进命令 | PAP-96 `EmailTokenCleanupScheduleJob` |
| 时间来自注入的 `Clock`；Lua 不调 `TIME`、不返回 `false` | PAP-64 |
| 列表项要有：是否本机、设备名（解析不出为空）、端（桌面 / 手机 / 平板 / 未知）、IP、属地（可空；内网要能识别成「内网」）、登录时间、最后活跃；踢单个 / 其他全部回被踢的数量 | 设计简报 §9 附录 A |

## 4. 业界参照

- **受信代理**：nginx `real_ip_header X-Forwarded-For` + `set_real_ip_from <代理地址>`、Tomcat `RemoteIpValve` 的 `internalProxies`、Express `trust proxy`、ASP.NET `ForwardedHeadersOptions.KnownProxies`，都是同一个模型：**只对已知代理地址发来的连接**取 `X-Forwarded-For` 里最右边那个不是代理的地址，其他连接一律用 socket 地址。Spring Boot 的 `server.forward-headers-strategy: native` 就是打开 Tomcat 的这个 valve，它的默认 `internal-proxies` 是一串私网段（含 `100.64.0.0/10`、`192.168.0.0/16`），在 Patra 的 tailnet 里等于谁都信，必须覆盖。
- **UA 透传**：反向代理（nginx、Cloudflare、Vercel 的边缘）都把 `User-Agent` 原样转给上游，不另起头名。BFF 替浏览器调后端时设成浏览器的 UA，下游就不必区分「直连」和「经代理」：直连的人报的本来就是它自己的 UA。
- **会话列表与踢设备**：GitHub「Sessions」页（设备、地点、最后活跃、撤销，撤销前要 sudo）、Google「你的设备」、Discord「Devices」（登出所有其他设备）。列表的「本机」按会话 ID 认，不按设备标识认。
- **新设备登录提醒**：GitHub「new sign-in」邮件按设备 Cookie 判断是否认识这台设备；Google 的「新设备登录」提醒；Microsoft「unusual sign-in activity」。共同点是认设备不认 IP（IP 会变），认不得的设备才发。
- **UA 解析**：Yauaa（Apache 2.0，Java，按规则树解析，字段含 `DeviceClass`、`AgentNameVersionMajor`、`OperatingSystemName`）；Matomo DeviceDetector（PHP 生态）。Java 生态 Yauaa 是主流。
- **IP 属地**：国内的「IP 属地」展示普遍用 ip2region 或付费库（IPIP.net、纯真）；ip2region 离线、免费、Apache 2.0、中文结果到市。境外库（MaxMind GeoLite2）中文地名质量差且要注册账号下载。

## 5. 模块与改动面

| 模块 | 改动 |
|---|---|
| `patra-gateway-boot` | `application.yml`：`server.forward-headers-strategy: native`、`server.tomcat.remoteip.internal-proxies: ""`；`GatewaySecurityConfiguration` 的 `forwardedHeaderFilter()` 注释改写（仍 `removeOnly`）；集成测试 `GatewayTrustedProxyIT`；README「入站转发头」一节 |
| `patra-identity-session` | `session-list.lua`；`RedisSessionStore.list`；README |
| `patra-identity-domain` | `DeviceId` 收紧；`RequestOrigin`、`ClientOrigin`、`DeviceClass`；`UserAgentParsingPort`（`port/useragent/`）、`IpGeolocationPort`（`port/geolocation/`）；`ClientOriginResolver`；`IpAddressScope`；`LoginEndReason` 拆细；`SessionTerminator`；`UserLoginRecord` 加字段、`start` 收 `ClientOrigin`；`UserLoginRecordRepository` 加 `findAllByIds`、`existsTrustedDevice`、`deleteRetiredBefore`；`SessionStorePort.listActive` 与 `ActiveUserSession`；`NewDeviceLoginPolicy`；`NewDeviceMailThrottlePort`；`NewDeviceLoginMail`；`IdentityErrorTrait.CURRENT_SESSION` 与 `CurrentSessionRevocationException`；`SessionIssuer.issue` 收 `ClientOrigin` |
| `patra-identity-app` | `LoginUserCommand` / `RegisterUserCommand` 加 `clientIp`（登录）、`userAgent`；`LoginUserHandler` 判新设备并发信；`RegisterUserHandler` 记设备；`usecase/session/`：`RevokeSessionHandler`、`RevokeOtherSessionsHandler`、`SessionQueryService`、`PurgeRetiredLoginRecordsHandler` 及命令 / 结果；PAP-98 四个处理器的命令加 `userAgent`、通知填值；`VerifyEmailHandler`、`ChangePasswordHandler`、`ResetPasswordHandler`、`RevertEmailChangeHandler`、`BanUserHandler` 改用 `SessionTerminator` |
| `patra-identity-infra` | `YauaaUserAgentParsingAdapter`、`Ip2RegionGeolocationAdapter`；`ip2region.properties` 与 Gradle 任务 `downloadIp2RegionData`（第 8.4 节）；`NewDeviceMailThrottleAdapter`（复用 `ActionThrottleScript`）；`SessionStoreAdapter.listActive`；登录记录实体、映射器、DAO 的新列与三个查询；`new-device-login` 的 HTML 与文本模板；`V1` |
| `patra-identity-adapter` | `MeSessionController`（`/me/sessions`）；`AuthController` 的登录、注册与 `MeController`、`PasswordResetController`、`EmailChangeController` 读 `User-Agent`；`LoginRecordCleanupScheduleJob` |
| `patra-identity-boot` | `IdentityProperties` 加 `session.record-retention`、`newDeviceMail`；`IdentityConfiguration` 装配解析器、策略、`SessionTerminator`；`application.yml`；依赖 Yauaa、ip2region |
| `patra-infra` | `docker-compose.apps.yaml` 门户固定 IP、identity 挂 ip2region 数据目录；`scripts/fetch-ip2region.sh`；`nacos/dev/patra-gateway.yaml` 受信地址；README |
| 文档 | 本 spec；identity README（新增「登录设备」一节、接口表、错误码表、模块表、依赖与数据文件更新方式）；session README；gateway README |

不动：门户代码（PAP-101 / PAP-102）、`patra-tool-*`、网关的路径规则（`/patra-identity/me/**` 已是需登录）、Redis 会话的字段。

## 6. 转发链路（决策 I）

### 6.1 总览

```
浏览器 ──TCP──▶ 门户（BFF，Next.js）
                 │  X-Forwarded-For: <门户从连接上看到的浏览器 IP>（一个值）
                 │  User-Agent: <浏览器发来的 User-Agent 原值>
                 ▼
               网关（Tomcat）
                 1. RemoteIpValve：连接地址 ∈ internal-proxies（只有门户）时，
                    取 X-Forwarded-For 里最右边的非代理地址作为 remoteAddr，并消费掉该头；
                    否则什么都不做（remoteAddr = 连接地址）
                 2. ForwardedHeaderFilter（removeOnly，最高优先级）：剥掉剩下的一切
                    Forwarded / X-Forwarded-*，不解释
                 3. 鉴权、路由（不看转发头）
                 4. 出站：Gateway 的 XForwarded / Forwarded 过滤器按 remoteAddr 写
                    X-Forwarded-For: <remoteAddr>、Forwarded: for=<remoteAddr>；User-Agent 原样
                 ▼
               identity（forward-headers-strategy: framework）
                 getRemoteAddr() = 网关写的客户端地址；User-Agent 头 = 浏览器 UA
```

两条来源的结果：

| 连到网关的是 | 带的 `X-Forwarded-For` | identity 看到的 IP | UA |
|---|---|---|---|
| 门户（受信） | 浏览器 IP | 浏览器 IP | 门户转来的浏览器 UA |
| 门户（受信） | 不带（PAP-101 之前） | 门户容器地址 | 门户自己的 UA |
| 其他任何来源 | 任意伪造值 | 连接地址（伪造值被剥掉） | 它自己报的 UA |

### 6.2 门户要带的头（PAP-101 的契约）

- 门户代表浏览器请求网关时（`gatewayFetch` / `gatewayStream` 在一次浏览器请求的上下文里调用），**每个请求**都带：
  - `X-Forwarded-For`：**一个值**，门户自己认定的浏览器 IP（IPv4 点分或 IPv6 文本，不带端口、不带方括号）。
  - `User-Agent`：浏览器请求里的 `User-Agent` 原值；浏览器没带时不设（让 Node 用默认值）。
- 不只登录、注册：改密码、找回密码、改邮箱确认 / 撤销都会发安全通知，通知里要写设备和 IP。全部请求都带最简单。
- **不带**任何别的转发头（`X-Forwarded-Host` / `-Proto` / `-Port` / `-Prefix`、`Forwarded`）：网关信任门户的连接，valve 会按其中三个改写网关看到的主机与协议（第 6.3 节）。
- **门户必须丢掉浏览器带来的 `X-Forwarded-For`，只发自己从连接上取到、并校验过的 IP 字面量**（IPv4 点分或 IPv6，不带端口和方括号）。这是强制条款，不是可选项（整分支评审 I2）：Next.js 16 的 `base-server` 对入站请求做的是 `req.headers['x-forwarded-for'] ??= socket.remoteAddress`，浏览器自己带了这个头 Next 就原样保留；门户若照转，受信网关的 `RemoteIpValve`（CIDR 判定内部调 `InetAddress.getByName`）和 Gateway 的 `ForwardedRequestHeadersFilter` 会对浏览器给的任意域名做阻塞的 DNS 解析，按 IP 的限流也能靠每次换一个值绕过。做法由 PAP-101 定：在 Next 之前删掉入站的 `X-Forwarded-For`（standalone 的自定义入口），或门户前面放一层会覆盖该头的反向代理并只信任它。identity 这边仍对非 IP 字面量一律当空（`ClientIp.from` / `RequestOrigin`），兜底不出错，但不能替代这条。
- **mini 上的现实**：浏览器经 tailnet 访问 `mini:4000`，OrbStack 的端口转发让容器看到的来源是 `patra-net` 网关 `192.168.97.1`（infra 文档里 PG 已实测过同样的现象）。所以 mini 上门户能报的浏览器 IP 大概率都是 `192.168.97.1` 或 tailnet 的 `100.x`，两者都是内网，设备列表显示「内网」、邮件里属地是「内网」。真实公网 IP 与属地要等公开上线、门户前面有反向代理时才看得到；本 Issue 用单元与集成测试证明解析正确（第 18 节），mini 上只验链路通（第 19 节）。

### 6.3 网关的信任判定

用 Spring Boot 对 Tomcat `RemoteIpValve` 的标准接法，不写自研过滤器：

```yaml
server:
  forward-headers-strategy: native
  tomcat:
    remoteip:
      internal-proxies: ${PATRA_GATEWAY_TRUSTED_PROXIES:}   # 默认谁都不信；mini 的 compose 里设成门户的固定地址
```

- `internal-proxies` 用 Tomcat 11 的 CIDR 写法（含 `/` 即按 CIDR 解析，逗号分隔），mini 写 `192.168.97.250/32`。**不能留 Boot 的默认值**：默认是 `192.168.0.0/16, 172.16.0.0/12, 100.64.0.0/10, 10.0.0.0/8, …`，tailnet 里的任何机器、mini 上 OrbStack 端口转发进来的任何连接（来源 `192.168.97.1`）都会被当成代理。
- 只有连接地址落在 `internal-proxies` 里时 valve 才动：从右往左跳过代理地址，第一个不是代理的值成为 `remoteAddr`，`X-Forwarded-For` 被消费（全部消费完就删除该头）。别的连接 valve 不做任何事。
- valve 对受信连接同时按 `X-Forwarded-Proto` / `X-Forwarded-Host` / `X-Forwarded-Port` **直接改写**请求的协议、主机名、端口（`serverName` 等），之后的 `removeOnly` 过滤器只藏头、不还原这些属性，路由与出站的 `X-Forwarded-Host` 会用改写后的值。接受这一点：只有门户受信，门户是我们自己的代码、只构造 `X-Forwarded-For` 与 `User-Agent` 两个头（第 6.2 节写成契约）。不去关：Boot 对 `protocol-header` 配空串会回退成 `X-Forwarded-Proto`，要关只能配成一个不存在的头名，徒增费解的配置。`GatewayTrustedProxyIT` 的受信用例只带 `X-Forwarded-For`，另加一条用例钉住「受信连接带 `X-Forwarded-Prefix` / `Forwarded` 仍被剥掉」（这两个 valve 不处理）。
- servlet 层的 `ForwardedHeaderFilter`（`removeOnly`，PAP-65）**保留**：valve 只处理受信连接的 `X-Forwarded-For`，非受信连接带来的一切转发头、受信连接带来的其他转发头，仍由它在 Security 与路由之前剥掉。它不改 `remoteAddr`，valve 的结果留着。
- 出站：Gateway 的 `trusted-proxies: ".*"` 不变。它在 WebMVC 版里的语义是「哪些地址可以出现在出站转发头里」：收窄它会让非受信来源的请求出站时一个转发头都没有，identity 的 springdoc 地址还原就坏了。出站的 `X-Forwarded-For` / `Forwarded: for=` 用的是 valve 处理后的 `remoteAddr`，入站的同名头已被剥光，所以出站只有一个值。
- 配置是启动时读的（Tomcat valve 不支持刷新），改了要重启网关。mini 的值是 compose 里 gateway 的环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES`，和 portal 的固定地址在同一个文件（原计划写 Nacos，被 CI 的「jar 与 Nacos 不得有同名键」检查拦下，第 24 节）。

### 6.4 mini 的门户固定地址

`patra-net` 是 `192.168.97.0/24`，容器地址由 Docker 顺序分配。门户在 `docker-compose.apps.yaml` 里加：

```yaml
  portal:
    networks:
      default:
        ipv4_address: 192.168.97.250
```

- 选 `.250`：Docker 从 `.2` 往上顺序分配，二十来个容器碰不到它；`.1` 是网段网关，端口转发进来的连接都显示为它，绝不能当受信地址。
- 不按整个 `patra-net` 网段信任：tailnet 经 `tailscale-gw` 子网路由进来的连接会被 SNAT 成该容器在 `patra-net` 上的地址、端口转发进来的是 `.1`，信任网段等于信任 tailnet 上的所有人。
- 本机开发（门户在 MacBook 上跑、网关指向 mini）：MacBook 的连接到 mini 网关时是 `.1` 或 tailnet 地址，不受信，记录的是那个地址。要在本机调链路就临时在 mini 的 compose 环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES` 里加 MacBook 的地址，不写进仓库。

### 6.5 identity 怎么读

- IP：`HttpServletRequest.getRemoteAddr()`，不改（`forward-headers-strategy: framework` 已在解析网关写的 `Forwarded` / `X-Forwarded-For`；`Forwarded` 优先，两者值相同）。PAP-96 / PAP-98 的控制器已经这样取，接通后自动是真实 IP。
- UA：控制器用 `@RequestHeader(value = HttpHeaders.USER_AGENT, required = false)` 取，放进命令。
- 两者都进 `RequestOrigin`（第 8.1 节），解析在处理器里做、在事务外做。

## 7. 设备标识契约

| 项 | 约定 |
|---|---|
| 谁种 | 门户（PAP-101），浏览器端没有 JS 能读到它 |
| Cookie | 名 `patra_device`（Secure 开启时 `__Host-patra_device`，与会话 Cookie 同一个开关）；`HttpOnly`、`SameSite=Lax`、`Path=/`、`Max-Age` 400 天（Chrome 的上限）；登出、被踢、过期都**不**清它 |
| 值 | `crypto.getRandomValues` 32 字节的 base64url，无填充，恰好 43 个字符：`^[A-Za-z0-9_-]{43}$` |
| 何时种 | 登录、注册的 Server Action 发请求前：没有或格式不对就新生成并写回；格式对就原样用。不在每个页面种：只有登录、注册用它 |
| 何时带 | `POST /auth/login`、`POST /auth/register` 的请求体 `deviceId` 字段 |
| identity 校验 | `DeviceId`：`null` / 空白 = 没有；有值就必须匹配上面的正则，否则 422、字段 `deviceId`、原因码 `INVALID_FORMAT`。去掉原来的「≤ 128 字符、无控制字符」宽松规则和 `TOO_LONG` 原因码 |
| 存在哪 | 登录记录 `device_id`（`VARCHAR(128)` → `VARCHAR(43)`）、Redis 会话 `device_id`（已有） |

- 为什么收紧：门户是唯一的客户端，格式由我们定；宽松规则让任意字符串（含超长、可读字符串）进库，后台展示与「180 天内出现过」的比较都要额外防御。以后的原生 App 用同一格式。
- 门户必须先自己校验 Cookie 值再带上，不合格就换新的：不然一个被改坏的 Cookie 会让这个浏览器永远 422 登不进去。
- 没带设备标识的登录（直接调 API 的客户端、PAP-101 上线前的门户）照常成功，记录里 `device_id` 为空，新设备判定按第 12.2 节。

## 8. 设备与属地解析

### 8.1 领域模型

| 类型 | 说明 |
|---|---|
| `RequestOrigin(String ip, String userAgent)` | 适配器给的原值，两者都可空。紧凑构造器做规范化：空白当 `null`；UA 去掉控制字符后按码点截到 512（`USER_AGENT_MAX_LENGTH`）；IP 去掉方括号后用 `InetAddress.ofLiteral` 校验并取规范文本（`getHostAddress()`），不是合法字面量就当 `null`。IP 不能当可信输入：受信门户可能转来浏览器谎报的值（第 6.2 节），`RemoteIpValve` 不校验格式；tailnet 上直连 identity 时 `getRemoteAddr()` 也可以是任意串——不校验的话超长值会让登录 500（`VARCHAR(45)`） |
| `DeviceClass` | 枚举 `DESKTOP`、`MOBILE`、`TABLET`、`UNKNOWN` |
| `DeviceProfile(String name, DeviceClass deviceClass)` | `UserAgentParsingPort` 的结果；`name` 可空 |
| `ClientOrigin` | `@Builder` 记录：`ip`、`userAgent`、`deviceName`、`deviceClass`、`ipRegion`、`privateNetwork`。登录记录、安全通知、会话列表都用它 |
| `UserAgentParsingPort` | `DeviceProfile parse(String userAgent)`：`null` 进 → `(null, UNKNOWN)`；不抛异常 |
| `IpGeolocationPort` | `Optional<String> locate(String ip)`：只对公网地址调用；查不到、格式不对返回空；不抛异常 |
| `IpAddressScope` | 纯 Java：`isPrivate(String ip)` —— 回环、`10/8`、`172.16/12`、`192.168/16`、链路本地、`100.64/10`（CGNAT，tailnet 用它）、IPv6 `::1`、`fc00::/7`、`fe80::/10`、未指定地址；用 `InetAddress.ofLiteral` 解析（只认字面量，不做 DNS），解析不了按「不是内网」 |
| `ClientOriginResolver` | 领域服务，依赖两个端口：`resolve(RequestOrigin) → ClientOrigin`。内网地址不查属地、`privateNetwork = true`；公网地址查属地；IP 为空时属地为空、`privateNetwork = false` |

### 8.2 设备名与端

Yauaa 只取四个字段：`AgentName`、`AgentVersionMajor`、`OperatingSystemName`、`DeviceClass`。

- 设备名 = 「浏览器主版本」+ ` · ` +「系统族」，缺哪段就省哪段，两段都缺为 `null`：
  - 浏览器段：`AgentName` 已知时 `AgentName AgentVersionMajor`（主版本未知时只写名字），例：`Chrome 141`、`Safari 18`、`Edge 141`。
  - 系统段：`OperatingSystemName` 不是「系统族」而是细名，要归一：`Mac OS` → `macOS`、`Windows NT` / `Windows 9x` → `Windows`、`Chrome OS` → `ChromeOS`，其余原样（`iOS`、`Android`、`Linux`、`Ubuntu`……）。
  - 当作「缺」的值：`null`、空、`Unknown`、`??`、`Hacker`、`Cloud`（Yauaa 给机器人的系统名）、`Anonymized`。
  - `DeviceClass` 是 `Hacker` 时（空 UA、注入串、扫描器）整个设备名为 `null`：不把「Hacker」写进用户看的列表。
  - 结果超过 64 个字符截断（列宽）。
- 端：`Desktop` → `DESKTOP`；`Phone`、`Mobile` → `MOBILE`；`Tablet`、`eReader` → `TABLET`；其余（`Robot`、`Hacker`、`Unknown`、`Watch`、`TV` 等）→ `UNKNOWN`。
- 只解析 `User-Agent`，不用 Client Hints：冻结后的 UA 仍保留浏览器主版本，系统名也不受影响（`Mac OS X 10_15_7`、`Windows NT 10.0`），Client Hints 只在细分系统版本时才需要。
- 已知局限（Yauaa 文档）：2025 年起 iPad 上的 Safari / Firefox 默认发 Mac 桌面 UA，会被认成 `DESKTOP` · `macOS`，无法区分。
- 依据 Yauaa 自带测试用例的预期：Chrome / macOS → `Desktop`、`Chrome`、`Mac OS`；Safari / iPhone → `Phone`、`Safari`、`iOS`；Chrome / Android → `Phone`；Edge / Windows → `Desktop`、`Edge`、`Windows NT`；旧式 iPad UA → `Tablet`；`curl/…` → `Robot`、`Curl`、`Cloud`；空串 → `Hacker`；`Mozilla/5.0` → `Unknown`。单测用这八个 UA（含 Chrome 141 / macOS 15 的真实串）钉住，实测与预期不符以实测为准（第 19 节）。

### 8.3 属地

ip2region 当前数据的每条结果是五段：`国家|省份|城市|ISP|ISO 国家码`，未知段写 `0`；中国境内是中文，境外全是英文；保留地址（私网、回环、CGNAT、组播）返回 `Reserved|Reserved|Reserved|0|0`；库里没有的返回空串。样例（2026-10 的数据）：

| IP | 原始结果 | 存的 `ip_region` |
|---|---|---|
| `113.92.157.29` | `中国\|广东省\|深圳市\|电信\|CN` | `中国 广东 深圳` |
| `202.96.209.133` | `中国\|上海市\|上海市\|电信\|CN` | `中国 上海` |
| `119.29.29.29` | `中国\|北京市\|北京市\|腾讯\|CN` | `中国 北京` |
| `8.8.8.8` | `United States\|California\|0\|Google LLC\|US` | `美国` |
| `1.2.3.4` | `Australia\|Queensland\|Brisbane\|0\|AU` | `澳大利亚` |
| `240e:3b7:3272:d8d0:db09:c067:8d59:539e` | `中国\|广东省\|深圳市\|电信\|CN` | `中国 广东 深圳` |
| `2001:4860:4860::8888` | `United States\|Florida\|Miami\|Google LLC\|US` | `美国` |

规范化规则（`Ip2RegionGeolocationAdapter` 内，纯函数单测）：

1. 空串、段数不是 5、第一段是 `Reserved` 或 `0` → 空。
2. **ISO 码是 `CN`**（含港澳台，数据里国家都是「中国」）：国家 + 省 + 市，省市去掉末尾的「省」「市」「特别行政区」（`广东省` → `广东`、`深圳市` → `深圳`、`香港特别行政区` → `香港`、`台湾省` → `台湾`；自治区数据里已是短称），`0` 的段省略，省与市相同时只留一个（直辖市）。自治州、地区等不去后缀（`延边朝鲜族自治州` 原样）。用空格连接。
3. **其他 ISO 码**：只到国家（release spec「境外到国家」），国家名用 JDK 的 CLDR 数据转中文：`Locale.of("", iso).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)`（`US` → `美国`）；ISO 码是 `0` 或 JDK 认不出（返回码本身）时用数据里的英文国家名。
4. 结果超过 64 个字符截断。
5. 不存 ISP：设计简报和邮件都不展示，存了也是没用途的信息（最小必要）。

调用前先由 `IpAddressScope` 判内网（第 8.1 节），内网不查库；适配器只收合法的 IP 字面量（`InetAddress.ofLiteral` 先校验，再把字节数组交给 `search(byte[])`）——ip2region 的 `search(String)` 内部用 `InetAddress.getByName`，对非字面量会去做 DNS 解析，对空串会解析成回环。

### 8.4 依赖、许可、数据文件与更新

| 项 | Yauaa | ip2region |
|---|---|---|
| 坐标 | `nl.basjes.parse.useragent:yauaa:8.2.0`（2026-07-24） | `org.lionsoul:ip2region:3.3.7`（2026-04-04）；数据取仓库 tag `v3.18.0`（2026-09-08） |
| 许可 | Apache-2.0 | 代码 Apache-2.0 OR MIT；**数据文件没有单独的许可声明**（仓库级许可是否覆盖数据没写明） |
| 运行依赖 | `caffeine` 3.2.4（与 Boot 4.0.8 的 BOM 同版本）、`prefixmap`、`jsr305`；antlr、snakeyaml、commons 全部 shade 进自己的包，不冲突；用 Log4j2 API 打日志，Boot 的 `log4j-to-slf4j` 已带 `log4j-api`，日志进 logback | 无 |
| 体积 | jar 2.8 MB + caffeine 1 MB，进镜像的「第三方依赖」层（很少变） | jar 36 KB；数据 `ip2region_v4.xdb` 10.6 MB、`ip2region_v6.xdb` 35.6 MB，**不进镜像**（见下） |
| 内存与启动 | 只取四个字段时文档估算约 100 MiB 堆、初始化 2 ~ 5 秒（老版本数据，第 19 节实测）；`immediateInitialization()` 放在启动期，`dropTests()` 丢掉内置测试用例，`withCache(10000)` | 用默认的 `VIndexCache` 模式：每个库常驻 512 KB 向量索引，查询读文件几次；登录是低频事件，不需要把 46 MB 读进堆。`Ip2Region.create(v4, v6)` 是线程安全的服务（内部按查询借还 `Searcher`） |
| 装配 | 单例 Bean（不放静态字段），`hideMatcherLoadStats()` | 单例 Bean，`destroyMethod = "close"`；启动时 `Searcher.verifyFromFile` 校验两个文件，缺失或不兼容直接启动失败（错误信息写出文件路径与 README 的获取步骤） |

**数据文件放哪**：不进 git、不进镜像，放在宿主机的数据目录、只读挂进容器。

- 版本与校验和写在一个文件里：`patra-infra/scripts/ip2region.properties`（`tag=v3.18.0`、两个文件的 SHA-256）。放在 patra-infra：mini 只稀疏检出这个目录，获取脚本要读得到它。
- 获取脚本 `patra-infra/scripts/fetch-ip2region.sh`：按这个文件从 `raw.githubusercontent.com/lionsoul2014/ip2region/<tag>/data/` 下两个文件到 `~/.patra/data/ip2region/`，校验 SHA-256，原子替换。mini 与本机各跑一次。
- compose：identity 挂 `${HOME}/.patra/data/ip2region:/data/ip2region:ro`；配置 `patra.identity.ip-geolocation.directory`，默认 `${user.home}/.patra/data/ip2region`（本机 bootRun 直接用），容器里设成 `/data/ip2region`（`.env.common` 或 compose 的 `environment`）。
- 测试：identity-infra 的 Gradle 任务 `downloadIp2RegionData` 按同一个 properties 文件下载到 `build/ip2region/`（输入是 properties，输出目录做增量缓存，校验和不对失败），`test` / `integrationTest` 依赖它并用系统属性告诉测试目录。CI 在 GitHub 的 runner 上跑测试，取 GitHub 文件没问题；mini 上的 CD 只跑 `bootJar`，不触发下载。
- **更新**：改 properties 的 tag 与校验和 → 提交 → mini 上 `git pull` 后重跑脚本 → 重启 identity。不用发版。上游「不定期」更新，按季度看一次即可，写进 README。
- 为什么不放进 jar / 镜像：两个文件 46 MB，放进 identity 自己的模块会落在每次部署都变的应用层，mini 每次拉镜像多 46 MB（经代理拉 GHCR 大层本来就会间歇出错）；放进 git 会让公开仓库每次更新数据都永久多 46 MB，而且数据的许可没写明，不宜在公开仓库里再分发。只用 IPv4 能省 36 MB，但国内移动网络大量走 IPv6，属地会大面积为空。

Yauaa 的规则在 jar 里，升级就是改版本号，README 记一句「升级后跑 `YauaaUserAgentParsingAdapterTest`」。

## 9. 登录记录

### 9.1 `V1` 的 `idn_user_login_record`

在现有列之后加（注释同其他表的英文风格）：

| 列 | 类型 | 说明 |
|---|---|---|
| `device_id` | `VARCHAR(43)` NULL | 由 `VARCHAR(128)` 收紧，第 7 节 |
| `user_agent` | `VARCHAR(512)` NULL | 截断后的 UA 原值 |
| `device_name` | `VARCHAR(64)` NULL | 第 8.2 节；解析不出为空 |
| `device_class` | `VARCHAR(16)` NOT NULL | `DESKTOP` / `MOBILE` / `TABLET` / `UNKNOWN`，`CHECK` 约束 |
| `client_ip` | `VARCHAR(45)` NULL | 客户端 IP 文本（同令牌表 `requested_ip`）；不用基类的 `ip_address bytea`：那是审计基类列，所有表都有、从不填，语义是「谁写的这行」 |
| `ip_region` | `VARCHAR(64)` NULL | 属地文本，第 8.3 节；内网与查不到为空 |
| `end_reason` | `VARCHAR(16)` → `VARCHAR(32)` | 第 9.2 节；`CHECK` 改为新取值 |

索引：

- 去掉 `idx_idn_user_login_record_user_id`，换成 `idx_idn_user_login_record_user_device (user_id, device_id)`：新设备判定按这两列查（第 12.2 节），前缀也覆盖原来按 `user_id` 的查询。
- 加表达式索引 `idx_idn_user_login_record_retire_at ((COALESCE(ended_at, expires_at)))`：清理任务按「结束或过期的时刻」删（第 14 节）。

`ip_address`（基类列）照旧不填。表注释补一句「device/UA/IP 为登录时的值」。

### 9.2 结束原因

| 取值 | 谁写 | 现在写的 |
|---|---|---|
| `LOGOUT` | 登出 | 不变 |
| `BANNED` | 后台封禁 | 不变 |
| `REPLACED` | 超过会话上限被挤掉 | 不变 |
| `REVOKED` | 本人在设备列表里踢掉（本 Issue） | 原来还兼指验证邮箱、改密码、重设、撤销改邮箱 |
| `EMAIL_VERIFIED` | 验证邮箱撤销其他会话 | `REVOKED` |
| `PASSWORD_CHANGED` | 改密码撤销其他会话 | `REVOKED` |
| `PASSWORD_RESET` | 重设密码撤销全部会话 | `REVOKED` |
| `EMAIL_REVERTED` | 撤销改邮箱撤销全部会话 | `REVOKED` |

- PAP-100 在同一处加 `DELETION_REQUESTED`（`VARCHAR(32)` 已够）；本 Issue 不加没有调用方的取值。
- 拆细的理由：结束原因是审计信息（「这台设备为什么下线了」），一个 `REVOKED` 装五种原因就答不了；新设备判定要把「被本人踢掉的设备」和「因为改密码下线的设备」分开（第 12.2 节）。PAP-99 的 Issue To-Do 已列出这组取值。
- `LoginEndReason` 的 Javadoc 逐个写清。

### 9.3 `SessionTerminator`：撤销会话 + 结束记录

现在有五处各自写「`sessions.revokeXxx(...)` 拿到被删的会话 ID → 逐个 `records.findById(...).end(reason, now)` → `save`」（`VerifyEmailHandler`、`ChangePasswordHandler`、`ResetPasswordHandler`、`RevertEmailChangeHandler`、`BanUserHandler`），本 Issue 还要加两处。抽成领域服务：

```java
public final class SessionTerminator {
  SessionTerminator(SessionStorePort sessions, UserLoginRecordRepository records)
  boolean terminate(long userId, long sessionId, LoginEndReason reason, Instant now)
  List<Long> terminateAll(long userId, LoginEndReason reason, Instant now)
  List<Long> terminateAllExcept(long userId, long keepSessionId, LoginEndReason reason, Instant now)
}
```

- 先 Redis 后记录；不管事务（调用方在自己的事务里调，和 `SessionIssuer` 一样）。Redis 失败直接抛（503），记录不动。
- 记录不存在时跳过（记日志 INFO），已结束的保留第一次的原因（`UserLoginRecord.end` 已是这个语义）。
- 账号类型固定 `AccountType.USER`（与 `SessionIssuer` 一致）。
- `LogoutUserHandler` 不改：它有意把「结束记录」放在 Redis 删除之后的独立事务里、失败只记 WARN 照样 204（PAP-64），语义不同。
- 五个处理器的单测原来 mock `SessionStorePort` 与仓储断言循环，改用真的 `SessionTerminator` + 同样的两个 mock，断言不变、只把结束原因换成第 9.2 节的新值。

## 10. 会话存储

### 10.1 `session-list.lua`

```
KEYS[1] 索引
ARGV[1] 会话键前缀  ARGV[2] now
entries = HGETALL index；result = {}
for sid, hash in entries:
  fields = HGETALL prefix..hash
  空 → HDEL index sid；continue
  now >= expires_at → DEL prefix..hash；HDEL index sid；continue
  result[#result+1] = fields
return result            -- 没有会话时是空表
```

`RedisSessionStore.list(accountType, userId) → List<StoredSession>`：不续期、不改 `last_active_at`（看列表不是活动；本机的 `last_active_at` 由这次请求经过网关时的续期负责）。顺手清掉悬空与已过期的条目，和 `session-create.lua` 一致。

### 10.2 设备信息从哪来

列表 = Redis 里活着的会话（会话 ID、`created_at`、`last_active_at`）⋈ 登录记录（设备名、端、IP、属地；ID 相同）。

- 不往 Redis 会话里加设备字段：登录记录是登录时事实的唯一来源，Redis 只管「还活着」和「最后活跃」；网关也用不到这些字段。
- 登录时间取 Redis 的 `created_at`（与记录的 `created_at` 同一个 `now`，取 Redis 的少一次依赖）。
- Redis 有、记录没有的会话（不应该出现：记录先存、会话后建，同一个事务）：照样列出，设备字段全空、端 `UNKNOWN`，记 WARN。

### 10.3 最后活跃

会话 HASH 的 `last_active_at` 已由网关的 `session-touch.lua` 维护：距上次写入满 60 秒（`RENEW_INTERVAL`）才写回。本 Issue 不改它，只是第一次把它读出来给用户看；AC「60 秒内不重复写」由已有的 `RedisSessionStoreIT` 与网关 `should_renew_the_session_on_request_without_passing_absolute_expiry` 守，流程测试再补一条端到端断言（第 18.2 节）。

## 11. 接口

### 11.1 `GET /me/sessions`

需登录（网关已有规则）。响应 200：

```json
{
  "sessions": [
    {
      "id": "723511223344556677",
      "current": true,
      "deviceName": "Chrome 141 · macOS",
      "deviceClass": "DESKTOP",
      "ipAddress": "101.88.12.4",
      "ipRegion": "中国 上海",
      "privateNetwork": false,
      "signedInAt": "2026-10-20T01:12:00Z",
      "lastActiveAt": "2026-10-22T08:03:00Z"
    }
  ]
}
```

- 排序：本机第一；其余按 `lastActiveAt` 倒序，相同再按 `id` 倒序。
- `id` 是会话 ID（雪花，按全局约定输出成字符串）；`deviceName`、`ipAddress`、`ipRegion` 可为 `null`；`privateNetwork` 为真时门户显示「内网」；`ipRegion` 为空且不是内网时显示「属地未知」。
- 当前会话不在列表里（断言签出后 60 秒内被踢、过期）：401，和再认证守卫一样（`AuthenticationRequiredException`）。
- 处理：`SessionQueryService.list(current)`，不开事务（只读，记录按 ID 批量查一次）。

### 11.2 `DELETE /me/sessions/{id}`

1. `id` 等于当前会话 ID → 409，`traits` 含 `CURRENT_SESSION`（本机走 `POST /auth/logout`）。先于再认证判：门户在弹密码框之前就该知道这条路不对。
2. `reauthenticationGuard.requireRecent(current)`：会话不在 401，超时 403 `REAUTH_REQUIRED`。
3. 事务里 `sessionTerminator.terminate(userId, id, REVOKED, now)`。
4. 200 `{ "revokedSessions": 1 }`；会话已经不在（被别处踢掉、过期、不是这个用户的）→ 200 `{ "revokedSessions": 0 }`，幂等，不回 404：范围已经限定在本人的索引里，不存在枚举问题，门户看到 0 照样把这一行移掉。
- 不发邮件（release spec 2.1.10「自己踢设备」不发）。
- `id` 不是数字 → 400（框架的类型转换错误，走统一错误引擎）。

### 11.3 `DELETE /me/sessions/others`

1. 再认证守卫。
2. 事务里 `sessionTerminator.terminateAllExcept(userId, current.sessionId(), REVOKED, now)`。
3. 200 `{ "revokedSessions": n }`（可以是 0）。

路径 `others` 是字面量，Spring 先于 `{id}` 匹配。

### 11.4 不锁用户行、不判账号状态

踢设备只动会话和登录记录，不改 `idn_user`，所以不走「`findByIdForUpdate` + `requireActive()`」（PAP-93 第 16 节的约束针对写用户聚合的处理器）。冷静期（`PENDING_DELETION`）的用户能登录、也就能踢自己的其他设备；被封禁的用户没有会话。

## 12. 新设备邮件

### 12.1 何时判、何时发

- 只在**登录**成功时判；注册不判、不发。
- 判定在登录事务里、建会话**之前**做（之后再查会查到刚建的这条记录）；发信在事务提交之后，经 `EmailDeliveryPort` 异步发出，失败只记日志（PAP-96 第 9.1 节）。
- 限流（24 小时 1 封）在提交之后、发信之前取；Redis 失败记 WARN、不发信，登录照常成功——邮件是提醒，不能让它挡住登录。

### 12.2 「见过这台设备」的判定

`NewDeviceLoginPolicy`（`lookback` 180 天、`cooldown` 24 小时，配置可调）。登录带设备标识 `d` 时：

```
seen = 存在这个账号的登录记录：device_id = d 且 COALESCE(ended_at, expires_at) ≥ now − 180 天 且 end_reason 不是 REVOKED
seen → 不发
!seen → 取 24 小时限流（键按账号 + 设备标识的哈希）→ 放行才发
```

- **被本人踢掉的设备不算「见过」**：用户在设备列表里踢掉一台陌生设备，说明他不认这台设备；它要是再登录进来（对方知道密码），应该再提醒一次。这也是「同设备 24 小时 1 封」真正起作用的地方：没有这条规则时，发过一封后记录里就有了这个设备，后面的登录本来就不会再发。
- 其他结束原因（登出、过期、被挤掉、改密码、重设、撤销改邮箱、验证邮箱）都算「见过」：用户自己的手机因为他改了密码而下线，重新登录不该被当成陌生设备。
- **注册后第一次登录**：注册本身建了一条带设备标识的记录，同一个浏览器注册后再登录自然「见过」、不发；换一台设备的首次登录照发（2026-10-22 用户定）。
- **没带设备标识**的登录：没法认设备，一律按「没见过」处理，24 小时限流键用账号 + 固定的 `none`：直接调 API 登录的客户端每天最多提醒 1 次。
- **窗口按「这条记录最后可能在用的时刻」算，不按登录时刻**：会话的绝对有效期正好是 180 天，按 `created_at` 算的话，一台天天在用、一直登录着的设备在第 180 天被强制重新登录时，上一条记录刚好掉出窗口，每 180 天误报一次「新设备」。`COALESCE(ended_at, expires_at)` 是结束时刻或绝对过期时刻：还活着的会话一定在窗口里。代价是偏宽：30 天不活跃被 Redis 静默过期的会话，按绝对过期算会多「记得」最多 150 天——宁可少提醒一台很久以前用过的自己的设备，也不误报常用设备。
- 查询：`UserLoginRecordRepository.existsTrustedDevice(userId, deviceId, since)`，走第 9.1 节的 `(user_id, device_id)` 索引前缀再按时刻过滤（同一账号同一设备的记录很少）。
- 判定用的时刻与清理任务（第 14 节）是同一个表达式，180 天的回看小于 190 天的留存，记录不会在判定需要之前被清掉。

### 12.3 限流

`NewDeviceMailThrottlePort.acquire(long userId, DeviceId device, Instant now) → boolean`（设备可空）：

- 适配器复用 `ActionThrottleScript`：只有冷却（24 小时）、没有窗口；冷却键 `idn:new-device-mail:user:<userId>:<ActionThrottleScript.keyHash(设备标识)（SHA-256 前 32 位十六进制）| none>`。
- 键里不放设备标识原文：它相当于一个长期 Cookie 的值，Redis 里能少存就少存。
- 放行即已计数；Redis 暂时不可用抛 `TemporarilyUnavailableException`，登录处理器接住只记 WARN。

### 12.4 邮件

- 模板 `new-device-login`，主题「你的 Patra 账号在新设备上登录」，收件人当前邮箱（未验证也发：这是安全通知，不是营销）。
- 记录 `NewDeviceLoginMail(EmailAddress to, String displayName, SecurityNotice notice)`，`of()`。
- 正文：「你的账号刚刚在一台新设备上登录。」+ 安全信息块 + 「不是我」块 + 「建议到安全设置检查登录设备：{securityUrl}」。
- `NOT_ME_ADVICE`：「如果不是你，你的密码可能已经泄露：请到安全设置修改密码（其他设备会全部退出登录），并检查登录设备。」
- `SecurityNotice`：`occurredAt = now`、`device = deviceName`、`ipAddress = ip`、`ipLocation = ipRegion`；内网地址的 `ipLocation` 写「内网」。

## 13. 安全通知填真实值

`ClientOrigin` 到 `SecurityNotice` 的转换写成 `ClientOrigin.toNotice(Instant occurredAt, String notMeAdvice)`（`SecurityNotice` 是 5 个字段的 `@Builder` 记录，按代码规范不再加 `of()`），五处共用；内网地址的 `ipLocation` 填「内网」：

| 处理器 | 命令新增 | 通知 |
|---|---|---|
| `ChangePasswordHandler` | `userAgent` | 改密码的这次请求 |
| `ResetPasswordHandler` | `userAgent` | 提交重设的这次请求（可能是另一台设备） |
| `ConfirmEmailChangeHandler` | `userAgent` | 点确认链接的这次请求；`notice(...)` 静态方法改收 `ClientOrigin` |
| `RevertEmailChangeHandler` | `userAgent` | 点撤销链接的这次请求 |
| `LoginUserHandler` | `clientIp`、`userAgent` | 新设备邮件 |

这些命令已有 `clientIp`（PAP-96 / PAP-98 为限流加的），只补 `userAgent`，在处理器里组成 `RequestOrigin` 交给 `ClientOriginResolver`。

## 14. 登录记录清理任务

- XXL-Job 入口 `LoginRecordCleanupScheduleJob`，handler `loginRecordCleanupJob`，无参数；保留期 `patra.identity.session.record-retention`（默认 `190d`）放进命令。与 `EmailTokenCleanupScheduleJob` 同一套写法：项目所有定时任务都走 XXL-Job（管理端可手动触发、看日志），不用 `@Scheduled`。
- `PurgeRetiredLoginRecordsHandler`：`cutoff = now − retention`；按批删、每批一个短事务、直到一批不足 1000 行（`PurgeExpiredEmailTokensHandler` 的写法）。
- 删除条件：`COALESCE(ended_at, expires_at) < cutoff`——显式结束的按结束时刻，没结束的（还活着或被 Redis 静默过期的）按绝对过期时刻。还活着的会话的 `expires_at` 一定在未来，永远不会被删。
- DAO：`@Modifying` 原生 SQL，`DELETE … WHERE id IN (SELECT id … WHERE COALESCE(ended_at, expires_at) < :cutoff LIMIT :batchSize)`，走第 9.1 节的表达式索引。
- mini 的 xxl-job-admin 里新建任务 `loginRecordCleanupJob`，执行器 `patra-identity`，Cron `0 40 4 * * ?`（令牌清理 04:30 之后）。手工步骤，写进 README 与第 19 节。

## 15. 网关路由

`/patra-identity/me/sessions`、`/patra-identity/me/sessions/**` 落在已有的 `IDENTITY_PATHS` 需登录规则里，不改 `GatewaySecurityConfiguration`。集成测试补一条：匿名访问这两个路径 401、带会话能到达 identity 并带断言。

## 16. 错误契约

| 场景 | 状态码 | 错误码 | `traits` / `errors` |
|---|---|---|---|
| `deviceId` 格式不对（登录、注册） | 422 | `IDN-0422` | `errors[]`：`deviceId` / `INVALID_FORMAT` |
| 踢的是本机 | 409 | `IDN-0409` | `CURRENT_SESSION` |
| 踢设备时再认证超时 | 403 | `IDN-0403` | `REAUTH_REQUIRED`（PAP-98） |
| 当前会话已不在（列表、踢设备） | 401 | `IDN-0401` | — |
| 会话 ID 不是数字 | 400 | `IDN-0400` | — |
| Redis 暂时不可用（列表、踢设备） | 503 | `IDN-0503` | `DEP_UNAVAILABLE` |

新增 `IdentityErrorTrait.CURRENT_SESSION` 与 `CurrentSessionRevocationException`（`DomainException`，`CONFLICT` + `CURRENT_SESSION`）。

## 17. 配置

identity `application.yml`：

```yaml
patra:
  identity:
    session:
      record-retention: 190d      # 登录记录结束或过期满这么久后删除（《网络安全法》日志留存 ≥ 6 个月）
    new-device-mail:
      lookback: 180d              # 这么久内用过的设备标识不算新设备
      cooldown: 24h               # 同一账号同一设备两封提醒的最短间隔
    ip-geolocation:
      directory: ${user.home}/.patra/data/ip2region   # ip2region 两个 xdb 所在目录；容器里是 /data/ip2region
```

容器里的值由 compose 设环境变量 `PATRA_IDENTITY_IP_GEOLOCATION_DIRECTORY=/data/ip2region`，数据文件的来源见第 8.4 节。

网关 `application.yml`：第 6.3 节。mini：`docker-compose.apps.yaml` 的 gateway 服务块 `environment: PATRA_GATEWAY_TRUSTED_PROXIES: 192.168.97.250/32`（门户容器的固定地址，同一个文件里 portal 的 `ipv4_address`）。

## 18. 测试策略

### 18.1 单元测试（不起 Spring）

- `DeviceIdTest`：43 字符 base64url 通过；42 / 44 字符、含 `+` `/` `=`、含空白与控制字符拒绝；空白当没有。
- `RequestOriginTest`：UA 截断、控制字符去除、空白当 `null`。
- `IpAddressScopeTest`：各私网段的边界（`100.63.255.255` 不是、`100.64.0.0` 是、`172.15.255.255` 不是……）、IPv6、非法字面量。
- `ClientOriginResolverTest`：内网不调属地端口；公网调；空 IP。
- `NewDeviceLoginPolicyTest`：窗口起点。
- `SessionTerminatorTest`：先 Redis 后记录、记录缺失跳过、已结束保留原因、Redis 失败不碰记录。
- `UserLoginRecordTest`：`start` 带 `ClientOrigin`；`restore` 新字段。
- 处理器：`LoginUserHandlerTest`（见过 / 没见过 / 被踢过 / 没设备标识 / 限流拒绝 / 限流 503 只记日志 / 注册不判）、`RegisterUserHandlerTest`（记设备，不发新设备邮件）、`RevokeSessionHandlerTest`（本机 409 先于再认证、不在返回 0、结束原因 `REVOKED`）、`RevokeOtherSessionsHandlerTest`、`SessionQueryServiceTest`（排序、记录缺失、当前会话不在 401）、`PurgeRetiredLoginRecordsHandlerTest`（分批）；PAP-98 四个处理器的测试断言通知里的设备、IP、属地。
- `YauaaUserAgentParsingAdapterTest`：第 8.2 节的八个 UA。
- `Ip2RegionGeolocationAdapterTest`：第 8.3 节的样例 IP（用真实数据文件）。
- `ThymeleafMailRendererTest`：`new-device-login` 的 HTML 与文本两部分。

### 18.2 集成测试（Testcontainers：PG 17、Redis 7、GreenMail）

identity-boot 的全部 `@SpringBootTest`（现有 17 个流程 IT 也在内）都会装配 ip2region 适配器，缺数据文件启动失败：boot 的 `integrationTest` 同样依赖 `downloadIp2RegionData`，测试配置把目录指向 `build/ip2region/`。


- `RedisSessionStoreIT`：`list` 返回活着的会话、清悬空与过期条目、不改 `last_active_at` 与 TTL；空索引返回空。
- `UserLoginRecordRepositoryAdapterIT`：新列往返；`existsTrustedDevice` 的三种排除（别的账号、窗口外、`REVOKED`）；`deleteRetiredBefore` 的边界（结束满 / 不满、未结束看 `expires_at`）与分批。
- `SessionStoreAdapterIT`（若无则并入上面）：`listActive` 的转换。
- `V1` 的约束：`device_class` 与 `end_reason` 的 `CHECK`（放进现有的库约束 IT）。
- boot 流程测试 `LoginDeviceFlowIT`（`RANDOM_PORT`、真实过滤器链、固定时钟 `MutableClock`）：
  - 带 `X-Forwarded-For`、`User-Agent`、`deviceId` 注册 → 登录记录有设备名、端、IP、属地（用一个真实公网样例 IP）；
  - 内网 IP（`192.168.1.10`、`100.70.1.1`、`127.0.0.1`）属地为空、`privateNetwork` 为真，不报错；
  - 两台设备登录 → `GET /me/sessions` 两项、本机第一且 `current`；
  - 网关续期模拟：拨时钟 30 秒后 `findAndTouch` 不写、拨到 61 秒写（直接调 `RedisSessionStore`，因为 identity 测试里没有网关）→ 列表的 `lastActiveAt` 随之变化；
  - 再认证窗口外踢设备 403；再认证后踢单个 → 被踢令牌经 `RedisSessionStore.findAndTouch` 为空（网关下一请求 401 的前提）、记录 `REVOKED`、`revokedSessions: 1`；再踢同一个 → 0；踢本机 409；
  - 踢其他全部 → 只剩本机；
  - 新设备邮件：新设备登录 GreenMail 收到一封、内容含设备名与 IP；同设备 24 小时内被踢后再登录不发第二封、拨到 24 小时后再登录发；注册后同设备登录不发；记录结束后 180 天内再登录不发、结束满 180 天后发；会话绝对过期（拨 180 天）后同设备重新登录不发（常用设备不误报）；
  - 改密码、重设密码、确认 / 撤销改邮箱的通知里有设备与 IP（各在已有流程测试里加一条断言即可）。
- `LoginRecordCleanupIT`（或并入流程测试）：命令删掉满 190 天的已结束 / 已过期记录，未满的保留。

### 18.3 网关集成测试 `GatewayTrustedProxyIT`

测试进程连网关的地址是 `127.0.0.1`。两个嵌套上下文（或两个测试类）：

- 受信：`server.tomcat.remoteip.internal-proxies=127.0.0.1/32`。带 `X-Forwarded-For: 203.0.113.9`、`User-Agent: Mozilla/5.0 …` 请求 `/patra-identity/auth/login` → WireMock 收到的 `X-Forwarded-For` 恰好是 `203.0.113.9`、`Forwarded` 含 `for="203.0.113.9`、`User-Agent` 原样；`X-Forwarded-Prefix: /evil` 等其他转发头仍被剥掉、换成网关的值。
- 非受信（默认空配置）：同样的请求 → WireMock 收到的 `X-Forwarded-For` 是 `127.0.0.1`，没有 `203.0.113.9`；`User-Agent` 仍原样（UA 不需要信任：直连的人报的本来就是它自己的 UA）。
- 受信但不带 `X-Forwarded-For` → 出站是 `127.0.0.1`。
- 受信且带多个值 `198.51.100.7, 203.0.113.9` → 取最右边的非代理值 `203.0.113.9`（门户只该发一个值，这条钉住 valve 的语义）。
- 已有的 `should_replace_client_supplied_forwarded_headers_with_gateway_values` 保持通过（它是非受信场景）。

### 18.4 回归

`./gradlew spotlessApply && ./gradlew check`；identity-session、identity-infra、identity-adapter、identity-boot、gateway-boot 的 `integrationTest`。`module-graph.json` 若因新依赖变化则重新生成。

### 18.5 AC 对应

| AC | 证据 |
|---|---|
| 受信代理带头 → identity 记到真实 IP 与 UA；非受信来源同样的头被剥掉 | `GatewayTrustedProxyIT` 两组 + `LoginDeviceFlowIT` 注册 / 登录记录 |
| 设备名如「Chrome 141 · macOS」、属地如「中国 广东 深圳」、境外到国家；回环与内网属地为空不报错 | `YauaaUserAgentParsingAdapterTest`、`Ip2RegionGeolocationAdapterTest`、`ClientOriginResolverTest`、流程测试的内网用例 |
| 列表只含活跃会话、本机置顶带标记、最后活跃随请求更新（60 秒内不重复写） | `RedisSessionStoreIT.list`、`SessionQueryServiceTest`、流程测试 + 已有续期测试 |
| 踢单个后 401 且记录 `REVOKED`；踢其他全部只剩本机；未再认证 403 | 流程测试 + `RevokeSessionHandlerTest` |
| 新设备邮件四条规则（固定时钟） | 流程测试 + `LoginUserHandlerTest` |
| 清理任务删满 190 天的、留未满的 | `UserLoginRecordRepositoryAdapterIT` + `PurgeRetiredLoginRecordsHandlerTest` |
| `check` + `integrationTest` 全绿 | 第 18.4 节的命令输出 |

AC 2 的属地写法用空格分隔（「中国 广东 深圳」），与设计简报和邮件一致；Issue 里的「中国 · 广东 · 深圳」随本 spec 改（第 22 节）。

## 19. 实施时要实测的点

1. Yauaa 对第 8.2 节八个 UA 的真实字段值（尤其 `OperatingSystemName` 对 macOS 是不是 `Mac OS`、iPad 的 `DeviceClass`），据此定映射。
2. ip2region 对样例 IP 的真实返回（第 8.3 节）；`VIndexCache` 模式下 `Ip2Region.search(byte[])` 的耗时（登录路径上，预期毫秒以内）；`Locale.of("", "US").getDisplayCountry(Locale.SIMPLIFIED_CHINESE)` 在 temurin 25 JRE 镜像里确实是「美国」（CLDR 数据在 `java.base` 之外的 `jdk.localedata` 模块，JRE 镜像要带着它）。
3. Tomcat `RemoteIpValve` 在 `internal-proxies: ""` 时确实谁都不信（Boot 把空串传给 valve，valve 置空两种模式）；`GatewayTrustedProxyIT` 的非受信组即是证据。
4. 网关 `ServerRequest.remoteAddress()` 在 valve 改写后给出的是客户端地址（出站 `Forwarded: for=` 的来源）。
5. identity 的 `ForwardedHeaderFilter` 对 `Forwarded: for="203.0.113.9:12345"` 与 IPv6 `for="[2001:db8::1]:443"` 解析出的 `getRemoteAddr()` 不带端口与方括号；带的话 `RequestOrigin` 要规范化。
6. Yauaa 初始化对 identity 启动时间与堆的影响（第 8.4 节的估计），以及集成测试多个 Spring 上下文各建一个解析器的总耗时。
7. mini（随后端 PR 部署时）：门户固定 IP 生效（`docker inspect`）；网关容器里 `PATRA_GATEWAY_TRUSTED_PROXIES=192.168.97.250/32`；门户接上 PAP-101 前，登录记录的 IP 是门户的 `192.168.97.250`、UA 是 Node 的；接上后看门户报的是 `192.168.97.1` 还是 tailnet 地址；从 MacBook 直接 `curl -H 'X-Forwarded-For: 1.2.3.4' mini:9528/patra-identity/auth/login …` 记录里不是 `1.2.3.4`。
8. mini：xxl-job-admin 新建 `loginRecordCleanupJob` 并手动触发一次。
9. mini 与本机：跑 `fetch-ip2region.sh`，确认 identity 启动时校验通过；缺文件时启动失败、错误信息指向脚本。

## 20. README

- identity README：新增一节「登录设备」（链路、设备标识、列表与踢设备、新设备邮件、结束原因表、清理任务、Yauaa 与 ip2region 的版本 / 许可 / 数据文件 / 更新步骤）；接口表加三行；错误码表加 `CURRENT_SESSION`；模块表；定时任务表加一行；配置表。
- session README：操作表加 `list`。
- gateway README：「入站转发头」改写为「受信代理与入站转发头」（valve + `removeOnly` 两层、配置、mini 的地址）；职责一节「入站转发头」一条改写；配置表加 `server.tomcat.remoteip.internal-proxies`。
- infra：`docker/README.md`（或 compose 注释）说明门户固定地址与它为什么不能改。

## 21. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 每个代表浏览器的网关请求带 `X-Forwarded-For`（一个值：门户从连接上取到并校验过的浏览器 IP 字面量）与 `User-Agent`（浏览器原值）；**必须**丢掉浏览器带来的 `X-Forwarded-For`（Next 的入站 `x-forwarded-for` 是 `??=`、浏览器可控，照转会让网关对任意域名做 DNS 解析、按 IP 的限流可被绕过）；设备标识 Cookie 按第 7 节（名字、43 字符 base64url、400 天、不随登出清除、格式不对就换新），登录与注册请求体带 `deviceId`；mini 上门户容器固定 `192.168.97.250`，改 compose 时不要动 | PAP-101 |
| 安全页的设备列表：`GET /me/sessions` 的形状（第 11.1 节）；`privateNetwork` → 「内网」、`ipRegion` 空且非内网 → 「属地未知」、`deviceName` 空 → 「未知设备」、`deviceClass` 决定图标；本机行的「退出登录」走 `POST /auth/logout`；`DELETE /me/sessions/{id}` 与 `DELETE /me/sessions/others` 回 `{ revokedSessions }`（toast「已退出其他 N 台设备」用它）、0 也当成功；403 `REAUTH_REQUIRED` 走再认证弹窗后重试；409 `CURRENT_SESSION` 不该出现（本机行不显示「退出」） | PAP-102 |
| 发起注销撤销全部会话用 `sessionTerminator.terminateAll(userId, DELETION_REQUESTED, now)`，在 `LoginEndReason` 与 `V1` 的 `CHECK` 里加 `DELETION_REQUESTED`（列宽已是 32）；注销三封邮件的 `SecurityNotice` 用 `origin.toNotice(now, advice)`，命令带 `clientIp`、`userAgent`；登录记录不随注销删除（2.1.9），按 190 天规则清理 | PAP-100 |

## 22. 要同步改的文档和 Issue

- Linear PAP-99：To-Do 的结束原因一条改为第 9.2 节的取值（`DELETION_REQUESTED` 留给 PAP-100）；AC 2 的属地示例写法改为空格分隔。
- Linear PAP-101 / PAP-102 / PAP-100：第 21 节的契约。
- release spec（规划会话负责，本 Issue 不动未跟踪的文件）：2.1.8「链路前提」补「受信靠门户的固定容器地址」；2.1.8 结束原因一句补「其他撤销各有原因」。
- 设计简报不用改：字段与它的附录 A 一一对应。

## 23. 设计中由我定的点与待拍板的点

**我定的（看到不对就推翻）**：

1. 受信判定用 Tomcat `RemoteIpValve`（Boot 的 `native` 策略），不自研过滤器；默认 `internal-proxies` 为空串。
2. 门户的受信地址靠 compose 固定容器 IP（`192.168.97.250`），不信整个网段。
3. UA 用标准 `User-Agent` 头透传，不另起头名、不做信任判定。
4. 设备标识收紧为 43 字符 base64url，格式不对 422。
5. 结束原因拆细为八个取值；`REVOKED` 只表示「本人在设备列表里踢掉」。
6. 「撤销会话 + 结束记录」抽成 `SessionTerminator`，五个现有处理器改用它（`LogoutUserHandler` 除外）。
7. 列表的设备信息来自登录记录，Redis 会话不加字段；登录时间取 Redis 的 `created_at`。
8. 踢单个对不存在的会话回 200 + `revokedSessions: 0`，不回 404；踢本机 409 `CURRENT_SESSION` 且先于再认证判。
9. 踢设备不锁用户行、不判账号状态。
10. 被本人踢掉（`REVOKED`）的设备不算「见过」，再登录会再提醒；其他结束原因都算见过。
11. 没带设备标识的登录一律按新设备，24 小时限流键用 `none`。
12. 新设备邮件发给当前邮箱，不论是否已验证。
13. 属地文本用空格分隔、去「省」「市」后缀、直辖市去重、境外只到国家并转中文名；不存 ISP；内网与查不到存空；API 另给 `privateNetwork`。
16. Yauaa 8.2.0 只取四个字段、启动期初始化；不用 Client Hints。
17. ip2region 用 `VIndexCache`（不把 46 MB 读进堆），IPv4 与 IPv6 都接。
18. 「见过」的窗口按 `COALESCE(ended_at, expires_at)` 算，不按登录时刻（builder check 指出按登录时刻会让常用设备每 180 天误报一次）。
19. 受信门户带来的 `X-Forwarded-Host` / `-Proto` / `-Port` 会被 valve 采用，不额外关掉；契约规定门户只发 `X-Forwarded-For` 与 `User-Agent`。
20. `RequestOrigin` 校验 IP 字面量，不合法当空。
14. 登录记录的 IP 新加 `client_ip VARCHAR(45)`，不复用基类的 `ip_address bytea`。
15. 清理按 `COALESCE(ended_at, expires_at)` 判，XXL-Job 每天 04:40。

**待拍板（改变做出来的东西）**：

1. **「注册后第一次登录不发」怎么读**（2026-10-22 用户定 A）。A：注册时就记下了这台设备，同一个浏览器注册后再登录自然不发；换一台设备的首次登录照样是新设备、照发（GitHub 的做法）。B：注册后的第一次登录不论哪台设备都不发——要给登录记录加一列「来源」（注册 / 登录）才分得出，而且对方若在主人注册后抢先登录，这封提醒就被吞了。
2. **ip2region 数据文件放哪**（2026-10-22 用户定 A；此前评估过在线接口：国内三家地图 API 商用要企业主体 + 约 5 万元/年许可，能商用的境外免费 / 廉价接口市级不准（113.92.157.29 给成广州），用户选「暂时先用离线库」——属地走 `IpGeolocationPort`，以后换在线或商业数据只换适配器）。A：宿主机 `~/.patra/data/ip2region/` 只读挂进容器，脚本按仓库里固定的 tag 与校验和下载，更新不用发版；代价是 mini 和本机各要手工跑一次脚本，缺文件 identity 起不来。B：打进 identity 的 jar / 镜像，开箱即用；代价是镜像每次部署多拉 46 MB（应用层每次都变），数据许可没写明却在我们的产物里再分发。C：只要 IPv4（10.6 MB）打进镜像，IPv6 的属地一律为空。

## 24. 实施结果（2026-10-22）

**计划**：`docs/patra/plans/2026-10-22-identity-login-devices.md` 15 个任务全部完成（账本 `.superpowers/sdd/2026-10-22-identity-login-devices/progress.md`）。

**实测**（对应第 19 节）：

1. Yauaa 对八个 UA 的结果与第 8.2 节的预期一致：Chrome / macOS → `Chrome 141 · macOS` 桌面；iPhone → `Safari 18 · iOS` 手机；Android（Chrome 冻结后的 `Android 10; K`）→ `Chrome 141 · Android` 手机；Edge / Windows → `Edge 141 · Windows` 桌面；旧式 iPad → `Safari 17 · iOS` 平板；`curl/8.7.1` → `Curl 8` 未知；空串与 `${jndi:…}` → 名字为空、未知。
2. ip2region（tag `v3.18.0`）：`113.92.157.29` → `中国 广东 深圳`、`202.96.209.133` → `中国 上海`、`8.8.8.8` 与 `2001:4860:4860::8888` → `美国`、`240e:3b7:…` → `中国 广东 深圳`。CLDR 把 `ZZ` 译成「未知地区」，判国家码改用 `Locale.getISOCountries`。JRE 镜像里的中文国名留到 mini 验（第 6 条）。
3. `internal-proxies` 为空时谁都不信：`GatewayUntrustedProxyIT` 证明。
4. 受信时出站 `Forwarded: for=` 是客户端地址：`GatewayTrustedProxyIT` 证明。
5. identity 对 `Forwarded: for="ip:port"` 与 `for="[v6]:port"` 解析出的 `getRemoteAddr()` 不带端口与方括号：`LoginDeviceFlowIT.should_read_client_ip_from_gateway_forwarded_header`。
6. Yauaa 初始化约 1.1 秒；分析器常驻约 108 MB 堆。每个 Spring 上下文各建一个会让 identity-boot 的集成测试（512 MB 测试堆、多个缓存上下文）OOM，改为 JVM 内一个（静态懒加载持有类）。Yauaa 文档反对静态持有，顾虑是 servlet 容器热卸载，可执行 jar 没有这个场景。
7. ~ 9. mini 上做（随后端 PR 部署）：见 identity README 第 13 节与 infra README「门户的固定地址与受信转发」。

**偏离计划的裁决**（账本里带 `Ruling:`）：

- `ip2region.properties` 放 `patra-infra/scripts/`（不在 identity-infra）：mini 只稀疏检出 patra-infra。
- 受信地址改用环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES`、写在 compose：CI 的 `JarConfigOverlapChecker` 不允许 jar 与 Nacos 有同名键，而 jar 里必须有「空 = 谁都不信」的默认值。
- 新设备限流键复用 `ActionThrottleScript.keyHash`（32 位十六进制）。
- `SessionTerminator` 在领域层不打日志（领域禁 slf4j），记录缺失静默跳过。
- 来源解析放在凭据 / 字段校验通过之后、事务之前：失败的登录不做无用解析。
- `RequestOrigin` 的 IPv6 保留原写法的小写，不展开成全零形式；IPv4 映射地址还原成 IPv4。

**整分支评审（opus，2026-10-22）**：无 Critical。修了：I1 只有登录与注册的登录记录规范化了 IP，注册、找回密码、改邮箱等其余用例仍拿原始 `getRemoteAddr()` 入库与当限流键（超长值写 `requested_ip` 失败、伪造值绕过按 IP 限流）→ 适配器层统一 `ClientIp.from(request)`，`LoginDeviceFlowIT` 两条超长 XFF 用例 RED→GREEN；I2 PAP-101 的「丢掉入站 XFF」改为强制条款（第 6.2、21 节）；`ClientOriginResolver` 兜住端口的运行时异常（安全通知在提交之后才解析，抛出会让已生效的操作回 500）；`SessionStoreAdapter` 一处 Javadoc 错位。延后的 Minor 见交接笔记。
- 登录现在会发新设备提醒：流程测试的 `mailFor` 与 `EmailVerificationFlowIT.waitForMail` 改为按收件人 / 主题等邮件，不按总封数。

**回归**：`./gradlew spotlessApply && ./gradlew check` 全绿；集成测试 identity-session 28、identity-infra 142、identity-adapter 75、identity-boot 79、gateway-boot 60 全过；单元测试 identity-domain 437、identity-app 192、identity-infra 102、identity-boot 21、identity-session 25、identity-adapter 6、gateway-boot 35。`module-graph.json` 无变化。
