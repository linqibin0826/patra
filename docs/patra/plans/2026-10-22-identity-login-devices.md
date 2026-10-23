# identity 登录设备 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本项目约定：本会话内联按计划 TDD（superpowers:executing-plans），不派实现子代理。

**Goal:** 接通「门户 → 网关 → identity」的受信 IP / UA 链路，登录记录带设备与属地，提供会话列表、踢设备、新设备邮件与登录记录清理（PAP-99）。

**Architecture:** 网关用 Tomcat `RemoteIpValve`（Boot `native` 策略）只对配置的门户地址解析 `X-Forwarded-For`；identity 在登录 / 注册时把 IP + UA 经两个领域端口（Yauaa、ip2region）解析成 `ClientOrigin` 写进登录记录；会话列表 = Redis 活会话 ⋈ 登录记录；「撤销会话 + 结束记录」收进领域服务 `SessionTerminator`。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Cloud Gateway WebMVC 5.0.3 / Tomcat 11.0.26、Redis Lua、PostgreSQL 17、Yauaa 8.2.0、ip2region 3.3.7（数据 tag `v3.18.0`）、XXL-Job、Thymeleaf、Testcontainers、GreenMail、WireMock。

**Spec:** `docs/patra/specs/2026-10-22-identity-login-devices-design.md`（下称 spec；节号指它）

## Global Constraints

- 分支 `feat/v0.12-user-center-api`，主检出，不建 worktree、不切分支、不推送、不开 PR、不动 main。
- 每次提交只 `git add` 本任务列出的路径，提交前 `git diff --cached --stat` 核对；不碰未跟踪的 `docs/patra/release-specs/v0.12-user-center.md`、`patra-portal/docs/design-briefs/v0.12/`、`docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip`。
- 提交信息：`type(scope): 中文动词起头 … (PAP-99)`，body 每行 ≤ 100 字符，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；subject 不以类名开头。
- 每个任务提交前 `./gradlew spotlessApply`；本地编译报 `bad class file` / `NoSuchFileException` 先重跑（IDEA 抢 `build/classes`）。
- 方法一律 `///` Markdown Javadoc；不用全类名；record ≤ 4 字段用 `of()`、≥ 5 字段用 `@Builder` 不再给 `of()`；集合字段 `List.copyOf`；时间来自注入的 `Clock`。
- 测试方法 snake_case `should_…`；单测 JUnit 5 + AssertJ + Mockito；IT 用 Testcontainers；Lua 不返回 `false`、不调 `TIME`。
- 设备标识正则 `^[A-Za-z0-9_-]{43}$`；UA 截断 512 码点；设备名、属地截断 64；`client_ip VARCHAR(45)`。
- 配置默认：`patra.identity.session.record-retention: 190d`、`patra.identity.new-device-mail.lookback: 180d`、`.cooldown: 24h`、`patra.identity.ip-geolocation.directory: ${user.home}/.patra/data/ip2region`；网关 `server.tomcat.remoteip.internal-proxies: ""`；mini 门户 `192.168.97.250`。
- 结束原因取值（spec 9.2）：`LOGOUT`、`BANNED`、`REPLACED`、`REVOKED`、`EMAIL_VERIFIED`、`PASSWORD_CHANGED`、`PASSWORD_RESET`、`EMAIL_REVERTED`。
- 常用命令前缀：`I=:patra-api:patra-identity`，例 `./gradlew $I:patra-identity-domain:test --tests '*DeviceIdTest'`。

## 开工与收尾

- 开工第一步：Linear 把 PAP-99 推到 BE 状态（`save_issue` 的 `state`）。
- 收尾：Task 15 推到 Review。

## Review Focus

1. **受信门户连接带多个 `X-Forwarded-For` 值或 IPv6 回环**：取最右边的非代理值，IPv6 `::1` 也能被配置为受信 → Task 1 `should_take_rightmost_untrusted_value_from_trusted_proxy`，受信配置同时写 `127.0.0.1/32,::1/128`。
2. **`getRemoteAddr()` 是非字面量或超长串**（谎报的 XFF、直连 6400）：登录不得 500，IP 当 `null` → Task 4 `should_drop_ip_that_is_not_a_literal`、Task 10 流程测试带 `X-Forwarded-For: not-an-ip`。
3. **IPv6 地址的属地与内网判定**（`fd00::1`、`::1`、`2001:4860:4860::8888`、`::ffff:192.168.1.1`）→ Task 4 `IpAddressScopeTest`、Task 6 适配器测试。
4. **会话还在 Redis、登录记录却不在**（不应发生，但列表不能 500）→ Task 13 `should_list_session_without_record_with_empty_device_fields`。
5. **常用设备在绝对过期（180 天）后被迫重新登录**：不得误报新设备 → Task 11 `should_not_mail_when_previous_session_of_device_expired_within_lookback`。

---

### Task 1: 网关只信任配置的代理地址

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/security/GatewaySecurityConfiguration.java`（`forwardedHeaderFilter()` 的 Javadoc：valve 先处理受信连接的 XFF，本过滤器剥其余）
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayTrustedProxyIT.java`
- Create: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayUntrustedProxyIT.java`
- Modify: `patra-api/patra-gateway-boot/README.md`（「入站转发头为什么在 servlet 层剥」改写为「受信代理与入站转发头」；职责一条；配置表）
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（portal `networks.default.ipv4_address: 192.168.97.250` + 注释）
- Modify: `patra-infra/nacos/dev/patra-gateway.yaml`（`server.tomcat.remoteip.internal-proxies: 192.168.97.250/32` + 注释）

**Interfaces:** Produces 出站契约：受信连接 → 下游 `X-Forwarded-For` = 客户端 IP（单值）、`Forwarded` 含 `for="<客户端 IP>`；`User-Agent` 原样。

- [ ] **Step 1: 写两个 IT。** 两个类都照 `GatewayAuthenticationIT` 的注解与 WireMock（identity）写法；`GatewayTrustedProxyIT` 加 `@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=127.0.0.1/32,::1/128")`。

`GatewayTrustedProxyIT`：
```java
@Test void should_forward_client_ip_from_trusted_proxy() {
  // POST /patra-identity/auth/login，头 X-Forwarded-For: 203.0.113.9、User-Agent: UA_CHROME
  // identity 收到：X-Forwarded-For == "203.0.113.9"；Forwarded 含 for="203.0.113.9 ；User-Agent == UA_CHROME
}
@Test void should_take_rightmost_untrusted_value_from_trusted_proxy() {
  // X-Forwarded-For: 198.51.100.7, 203.0.113.9 → 下游 X-Forwarded-For == "203.0.113.9"
}
@Test void should_fall_back_to_proxy_address_when_trusted_proxy_sends_no_client_ip() {
  // 不带 XFF → 下游 X-Forwarded-For 是 127.0.0.1 或 0:0:0:0:0:0:0:1（matches("127\\.0\\.0\\.1|0:0:0:0:0:0:0:1")）
}
@Test void should_still_strip_prefix_and_forwarded_headers_from_trusted_proxy() {
  // 带 X-Forwarded-For: 203.0.113.9、X-Forwarded-Prefix: /evil、Forwarded: for=198.51.100.1;host=evil.example
  // 下游 X-Forwarded-Prefix == "/patra-identity"；Forwarded 不含 evil、不含 198.51.100.1
}
```
`GatewayUntrustedProxyIT`（默认配置）：
```java
@Test void should_ignore_client_ip_from_untrusted_source() {
  // 同第一条请求 → 下游 X-Forwarded-For 不含 203.0.113.9、matches("127\\.0\\.0\\.1|0:0:0:0:0:0:0:1")；
  // Forwarded 不含 203.0.113.9；User-Agent 仍 == UA_CHROME
}
@Test void should_require_session_for_me_sessions() {
  // GET /patra-identity/me/sessions 与 DELETE /patra-identity/me/sessions/others 匿名 → 401 GW-0401；
  // 带 GatewayITSessions.issue 的令牌 → 到达 identity 且 Authorization 是断言（spec 15）
}
```

- [ ] **Step 2: 跑，确认受信类失败、非受信类通过。** `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests '*ProxyIT'` → `GatewayTrustedProxyIT` 前两条 FAIL（下游 XFF 是 127.0.0.1）。
- [ ] **Step 3: 配置。** `application.yml` 的 `server:` 下加 `forward-headers-strategy: native` 与 `tomcat.remoteip.internal-proxies: ""`，注释写清「默认谁都不信；Boot 默认值含 100.64/10、192.168/16，不能留」。`trusted-proxies: ".*"` 不动（注释补一句它管出站）。
- [ ] **Step 4: 跑 Step 2 的命令 → 全 PASS；再跑整个 `:patra-api:patra-gateway-boot:integrationTest` 与 `test` → PASS**（已有 `should_replace_client_supplied_forwarded_headers_with_gateway_values` 照常过）。
- [ ] **Step 5: README / Nacos / compose。** 按 spec 6.3、6.4 写；compose 注释：「网关只信任这个地址发来的 X-Forwarded-For（patra-gateway.yaml），改了要同步」。
- [ ] **Step 6: Commit** `feat(gateway): 只对受信的门户地址解析客户端 IP (PAP-99)`（gateway 四个文件 + README + 两个 infra 文件）。

### Task 2: 会话模块按用户列出活会话

**Files:**
- Create: `patra-api/patra-identity/patra-identity-session/src/main/resources/redis/session-list.lua`
- Modify: `…/patra-identity-session/src/main/java/dev/patra/identity/session/RedisSessionStore.java`
- Modify: `…/patra-identity-session/src/integrationTest/java/dev/patra/identity/session/RedisSessionStoreIT.java`
- Modify: `…/patra-identity-session/README.md`（「七段 Lua」→「八段」；操作表加 `list`）

**Interfaces:** Produces `public List<StoredSession> list(AccountType accountType, long userId)`：不续期；清悬空与已过绝对期的条目；顺序不保证。

- [ ] **Step 1: 写 IT**（沿用该类建会话的辅助方法与 `AdjustableClock`）：
  - `should_list_live_sessions_of_user`：同用户建 2 条、另一用户 1 条 → 返回 2 条，`sessionId` 集合相等。
  - `should_prune_dangling_and_expired_entries_when_listing`：一条手工 `DEL` 会话键、一条拨时钟过绝对期 → 返回剩下 1 条；索引 `HKEYS` 只剩它；过期的会话键已删。
  - `should_not_touch_sessions_when_listing`：拨时钟 120 秒后 `list` → `last_active_at` 字段与 `PTTL` 不变（用 1 小时绝对期建，同 `findAndTouch` 测试的手法）。
  - `should_return_empty_list_for_user_without_sessions`。
- [ ] **Step 2: 跑** `./gradlew $I:patra-identity-session:integrationTest --tests '*RedisSessionStoreIT'` → FAIL（无 `list`）。
- [ ] **Step 3: 实现。** 脚本按 spec 10.1 伪码，返回「会话字段数组」的数组；Java 侧 `RedisScript<List>`，逐个 `toStoredSession`。
- [ ] **Step 4: 跑 → PASS；跑模块 `test`。**
- [ ] **Step 5: Commit** `feat(identity): 会话存储加按用户列出活会话 (PAP-99)`。

### Task 3: 设备标识收紧为 43 字符 base64url

**Files:**
- Modify: `…/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/DeviceId.java`、`UserFieldViolations.java`（删 `deviceIdTooLong`）
- Modify tests: `DeviceIdTest`、`LoginClientTest`、`SessionIssuerTest`、`UserLoginRecordTest`、`RegisterUserHandlerTest`、`LoginUserHandlerTest`、`VerifyEmailHandlerTest`、`SessionStoreAdapterTest`、`UserLoginRecordRepositoryAdapterIT`、`AuthControllerIT`、boot `SessionFlowIT`（把 `mac-safari` 之类换成合规值，测试常量 `DEVICE_A = "a".repeat(43)` 之类）

**Interfaces:** Produces `DeviceId.PATTERN`（`Pattern`，`^[A-Za-z0-9_-]{43}$`）；`DeviceId.validate(String) → Optional<FieldViolation>`（只会给 `INVALID_FORMAT`）；`DeviceId.of(String) → Optional<DeviceId>`。

- [ ] **Step 1: 改 `DeviceIdTest`**：43 字符通过；42 / 44 字符、含 `+` `/` `=`、首尾空格（`" " + 43 字符`，**不再 strip**：格式由门户生成，带空格就是坏值）、含换行 → `INVALID_FORMAT`；`null` / 空白 → 空。
- [ ] **Step 2: 跑** `./gradlew $I:patra-identity-domain:test --tests '*DeviceIdTest'` → FAIL。
- [ ] **Step 3: 实现**：紧凑构造器只认匹配正则的值；删 `MAX_LENGTH`、码点与控制字符的私有方法。
- [ ] **Step 4: 修其余测试里的设备标识常量**；`SessionFlowIT` 的「去首尾空格」用例改为「格式不对 422、`errors[0].field == "deviceId"`、`code == "INVALID_FORMAT"`」。
- [ ] **Step 5: 跑** identity 各模块 `test` 与 adapter、boot 的 `integrationTest --tests '*AuthControllerIT' --tests '*SessionFlowIT'` → PASS。
- [ ] **Step 6: Commit** `feat(identity): 设备标识只收 43 字符的随机串 (PAP-99)`。

### Task 4: 请求来源与解析服务（领域）

**Files（全在 `patra-identity-domain`）：**
- Create: `model/vo/RequestOrigin.java`、`model/vo/ClientOrigin.java`、`model/vo/DeviceProfile.java`、`model/enums/DeviceClass.java`
- Create: `policy/IpAddressScope.java`
- Create: `port/useragent/UserAgentParsingPort.java`、`port/geolocation/IpGeolocationPort.java`
- Create: `service/ClientOriginResolver.java`
- Tests: 同包 `RequestOriginTest`、`ClientOriginTest`、`IpAddressScopeTest`、`ClientOriginResolverTest`

**Interfaces:** Produces
- `record RequestOrigin(String ip, String userAgent)`，`static RequestOrigin of(String ip, String userAgent)`，`static RequestOrigin unknown()`；常量 `USER_AGENT_MAX_LENGTH = 512`。
- `enum DeviceClass { DESKTOP, MOBILE, TABLET, UNKNOWN }`
- `record DeviceProfile(String name, DeviceClass deviceClass)`，`of(...)`，`static DeviceProfile unknown()`。
- `@Builder record ClientOrigin(String ip, String userAgent, String deviceName, DeviceClass deviceClass, String ipRegion, boolean privateNetwork)`；`SecurityNotice toNotice(Instant occurredAt, String notMeAdvice)`（内网时 `ipLocation = "内网"`）；常量 `PRIVATE_NETWORK_LABEL = "内网"`。
- `final class IpAddressScope`：`static boolean isPrivate(String literal)`。
- `interface UserAgentParsingPort { DeviceProfile parse(String userAgent); }`
- `interface IpGeolocationPort { Optional<String> locate(String ip); }`
- `final class ClientOriginResolver(UserAgentParsingPort, IpGeolocationPort)`：`ClientOrigin resolve(RequestOrigin origin)`。

- [ ] **Step 1: 写测试。**
  - `RequestOriginTest`：`should_drop_blank_values`；`should_truncate_user_agent_to_512_code_points`（600 个 `😀` → 512 码点）；`should_strip_control_characters_from_user_agent`（`"A\nB\u0000C"` → `"ABC"`）；`should_drop_ip_that_is_not_a_literal`（`"not-an-ip"`、`"1.2.3.4.5"`、46 字符串 → `null`）；`should_normalize_ip_literal`（`"[2001:db8::1]"` → `"2001:db8:0:0:0:0:0:1"`，`"::ffff:1.2.3.4"` → `"1.2.3.4"`；以 `InetAddress.ofLiteral(...).getHostAddress()` 为准）。
  - `IpAddressScopeTest`（参数化）：私有 `127.0.0.1`、`10.0.0.1`、`172.16.0.1`、`172.31.255.255`、`192.168.97.1`、`100.64.0.0`、`100.127.255.255`、`169.254.1.1`、`0.0.0.0`、`::1`、`::`、`fd00::1`、`fe80::1`；公网 `172.15.255.255`、`172.32.0.0`、`100.63.255.255`、`100.128.0.0`、`8.8.8.8`、`2001:4860:4860::8888`；`null` 与 `"abc"` → `false`。
  - `ClientOriginTest`：`toNotice` 公网 → `ipLocation == ipRegion`；内网 → `"内网"`；`ip == null` → `ipAddress`、`ipLocation` 都 `null`；`device == deviceName`。
  - `ClientOriginResolverTest`（两个端口 mock）：公网 IP 调 `locate` 并带上结果、`privateNetwork=false`；内网 IP 不调 `locate`（`verifyNoInteractions`）、`privateNetwork=true`；`ip == null` 不调 `locate`；UA 交给 `parse`，名字与端照搬。
- [ ] **Step 2: 跑** `./gradlew $I:patra-identity-domain:test --tests '*Origin*' --tests '*IpAddressScopeTest'` → FAIL（编译）。
- [ ] **Step 3: 实现。** `IpAddressScope` 用 `InetAddress.ofLiteral`，`isLoopbackAddress / isSiteLocalAddress / isLinkLocalAddress / isAnyLocalAddress` + 手判 `100.64.0.0/10` 与 IPv6 `fc00::/7`；解析失败 `false`。
- [ ] **Step 4: 跑 → PASS；`./gradlew $I:patra-identity-domain:check` → PASS（领域纯净）。**
- [ ] **Step 5: Commit** `feat(identity): 加请求来源的规范化与解析服务 (PAP-99)`。

### Task 5: Yauaa 解析设备名与端

**Files:**
- Modify: `gradle/libs.versions.toml`（`yauaa = "8.2.0"`、库 `yauaa = { module = "nl.basjes.parse.useragent:yauaa", version.ref = "yauaa" }`）
- Modify: `…/patra-identity-infra/build.gradle.kts`（`implementation(libs.yauaa)`，注释：日志走 Boot 的 log4j-to-slf4j）
- Create: `…/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/useragent/YauaaUserAgentParsingAdapter.java`
- Test: `…/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/useragent/YauaaUserAgentParsingAdapterTest.java`

**Interfaces:** Consumes Task 4 端口。Produces `@Component YauaaUserAgentParsingAdapter implements UserAgentParsingPort`，无参构造器里建 `UserAgentAnalyzer`（`withFields("DeviceClass","AgentName","AgentVersionMajor","OperatingSystemName")`、`withCache(10000)`、`immediateInitialization()`、`dropTests()`、`hideMatcherLoadStats()`），非静态字段。

- [ ] **Step 1: 写测试**（类内一个共享适配器实例，`@BeforeAll` 建）：

| UA（真实串，写成常量） | `name` | `deviceClass` |
|---|---|---|
| Chrome 141 / macOS（`Macintosh; Intel Mac OS X 10_15_7 … Chrome/141.0.0.0 Safari/537.36`） | `Chrome 141 · macOS` | `DESKTOP` |
| Safari 18 / iPhone（`iPhone; CPU iPhone OS 18_6 … Version/18.6 Mobile/15E148 Safari/604.1`） | `Safari 18 · iOS` | `MOBILE` |
| Chrome 141 / Android 14 Pixel 8 | `Chrome 141 · Android` | `MOBILE` |
| Edge 141 / Windows 10（`Windows NT 10.0; Win64; x64 … Edg/141.0.0.0`） | `Edge 141 · Windows` | `DESKTOP` |
| 旧式 iPad（`iPad; CPU OS 17_5 like Mac OS X …`） | `Safari 17 · iOS` | `TABLET` |
| `curl/8.7.1` | `Curl 8` | `UNKNOWN` |
| `""` 与 `null` | `null` | `UNKNOWN` |
| `${jndi:ldap://x}` | `null` | `UNKNOWN` |

  外加 `should_truncate_name_to_64_characters` 无需真实 UA：把命名规则抽成包内静态方法 `deviceName(String agent, String major, String os)` 单测（`"X".repeat(80)` → 长度 64）；`should_normalize_os_family`：`Mac OS`→`macOS`、`Windows NT`→`Windows`、`Chrome OS`→`ChromeOS`、`Cloud`/`Hacker`/`Unknown`/`??` → 省略。
- [ ] **Step 2: 跑** `./gradlew $I:patra-identity-infra:test --tests '*YauaaUserAgentParsingAdapterTest'` → FAIL。
- [ ] **Step 3: 实现**（映射按 spec 8.2；`DeviceClass == Hacker` 时 `name = null`）。实测值与表不符（spec 19.1）时以 Yauaa 实际输出为准修正测试期望，并在 spec 第 19 节记录。
- [ ] **Step 4: 跑 → PASS；记一次 `@BeforeAll` 耗时（打印到测试输出）写进 spec 19.6。**
- [ ] **Step 5: Commit** `feat(identity): 用 Yauaa 把 User-Agent 解析成设备名与端 (PAP-99)`。

### Task 6: ip2region 属地与数据文件

**Files:**
- Modify: `gradle/libs.versions.toml`（`ip2region = "3.3.7"`、库 `ip2region`）
- Create: `build-logic/src/main/kotlin/VerifiedDownload.kt`（`abstract class VerifiedDownload : DefaultTask`：`@Input url`、`@Input sha256`、`@OutputFile destination`；下载到临时文件、校验 SHA-256、原子移动；校验不对抛 `GradleException` 写明期望与实际）
- Create: `patra-api/patra-identity/patra-identity-infra/ip2region.properties`（`tag=v3.18.0`、`v4.sha256=…`、`v6.sha256=…`；校验和在本任务第一次下载时算出填入）
- Modify: `…/patra-identity-infra/build.gradle.kts`（`implementation(libs.ip2region)`；读 properties 注册 `downloadIp2RegionV4` / `downloadIp2RegionV6` 两个 `VerifiedDownload` 到 `build/ip2region/`，聚合任务 `downloadIp2RegionData`；`tasks.withType<Test>` 依赖它并设 `systemProperty("patra.identity.ip-geolocation.directory", <目录>)`）
- Modify: `…/patra-identity-boot/build.gradle.kts`（`tasks.withType<Test>` 同样依赖 infra 的 `downloadIp2RegionData` 并设同一系统属性，目录取 `project(":patra-api:patra-identity:patra-identity-infra").layout.buildDirectory.dir("ip2region")`）
- Create: `…/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/geolocation/Ip2RegionGeolocationAdapter.java`、`RegionText.java`（包内纯函数：五段原始串 → 展示文本）
- Tests: `…/infra/src/test/java/…/adapter/geolocation/RegionTextTest.java`、`Ip2RegionGeolocationAdapterTest.java`
- Create: `patra-infra/scripts/fetch-ip2region.sh`（读同一 properties；下到 `~/.patra/data/ip2region/` 临时名 → `shasum -a 256` 校验 → `mv`；用 `${VAR}` 写法）
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（identity 加 `volumes: ["${HOME}/.patra/data/ip2region:/data/ip2region:ro"]`、`environment: PATRA_IDENTITY_IP_GEOLOCATION_DIRECTORY: /data/ip2region`）
- Modify: `…/patra-identity-boot/src/main/resources/application.yml`（`patra.identity.ip-geolocation.directory` 默认值与注释）

**Interfaces:** Produces `@Component Ip2RegionGeolocationAdapter implements IpGeolocationPort`，构造器 `(@Value("${patra.identity.ip-geolocation.directory}") Path directory)`：`Searcher.verifyFromFile` 校验 `ip2region_v4.xdb`、`ip2region_v6.xdb`，`Config.custom().setCachePolicy(Config.VIndexCache).setXdbPath(...).asV4()/asV6()`，`Ip2Region.create(v4, v6)`；文件缺失或不兼容抛 `IllegalStateException`，信息含绝对路径与「运行 patra-infra/scripts/fetch-ip2region.sh」；`@PreDestroy close()`。`static String RegionText.normalize(String raw)`（可返回 `null`）。

- [ ] **Step 1: 写 `RegionTextTest`（纯函数，不读文件）：** spec 8.3 表的七行原始串 → 期望值；另加 `Reserved|Reserved|Reserved|0|0` → `null`；`""` → `null`；`中国|香港特别行政区|0|0|CN` → `中国 香港`；`中国|台湾省|台北市|0|CN` → `中国 台湾 台北`；`中国|吉林省|延边朝鲜族自治州|0|CN` → `中国 吉林 延边朝鲜族自治州`；`中国|0|0|0|CN` → `中国`；`Narnia|0|0|0|0` → `Narnia`；`X|0|0|0|ZZ`（JDK 认不出）→ `X`；四段串 → `null`；结果截断 64。
- [ ] **Step 2: 写 `Ip2RegionGeolocationAdapterTest`**（目录取系统属性，真实数据）：`113.92.157.29` → `中国 广东 深圳`；`202.96.209.133` → `中国 上海`；`8.8.8.8` → `美国`；`240e:3b7:3272:d8d0:db09:c067:8d59:539e` → `中国 广东 深圳`；`2001:4860:4860::8888` → `美国`；`"not-an-ip"` → 空（不做 DNS）；`should_fail_fast_when_data_files_are_missing`（`@TempDir` 空目录 → `IllegalStateException`，消息含 `fetch-ip2region.sh`）。
- [ ] **Step 3: 跑** `./gradlew $I:patra-identity-infra:test --tests '*RegionTextTest' --tests '*Ip2RegionGeolocationAdapterTest'` → FAIL。
- [ ] **Step 4: 实现** `VerifiedDownload`、两个下载任务、`RegionText`（`Locale.of("", iso).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)`，返回值等于 iso 本身视为认不出）、适配器（`InetAddress.ofLiteral` 后调 `search(byte[])`；受检异常 → 记 WARN 返回空）。第一次跑下载时把两个 SHA-256 填进 properties（先临时用错值看报错里的实际值，再核对 GitHub 上该 tag 的文件）。
- [ ] **Step 5: 跑 Step 3 → PASS；跑 `./gradlew $I:patra-identity-boot:integrationTest --tests '*SessionFlowIT'` → PASS（证明 boot 测试拿得到数据目录）。**
- [ ] **Step 6: 本机跑一次 `patra-infra/scripts/fetch-ip2region.sh`，确认 `~/.patra/data/ip2region/` 下两个文件、校验通过。**
- [ ] **Step 7: Commit** `feat(identity): 用 ip2region 离线库解析 IP 属地 (PAP-99)`（含 build-logic、properties、脚本、compose、yml）。

### Task 7: 登录记录带设备与属地，结束原因拆细

**Files:**
- Modify: `…/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（spec 9.1：五列、`device_id VARCHAR(43)`、`end_reason VARCHAR(32)` 与新 `CHECK`、`device_class` 的 `CHECK`、两个索引、列注释）
- Modify: `…/domain/model/enums/LoginEndReason.java`（八个取值，各一行 Javadoc）
- Modify: `…/domain/model/aggregate/UserLoginRecord.java`（字段 `userAgent`、`deviceName`、`deviceClass`、`clientIp`、`ipRegion`；`start(long userId, LoginClient client, ClientOrigin origin, Instant expiresAt)`；`restore` 改 `@Builder(builderMethodName = "restore")`，同 `User`）
- Modify: `…/infra/adapter/persistence/entity/UserLoginRecordEntity.java`、`converter/mapper/UserLoginRecordJpaMapper.java`
- Tests: `UserLoginRecordTest`、`UserLoginRecordRepositoryAdapterIT`、身份库约束 IT（`ProfileSchemaConstraintsIT` 同目录新建 `LoginRecordSchemaConstraintsIT`）

**Interfaces:** Produces `UserLoginRecord#getUserAgent()/getDeviceName()/getDeviceClass()/getClientIp()/getIpRegion()`（可空的用 `Optional` 包装与否随 `getDeviceId` 的现有风格：`deviceName`、`clientIp`、`ipRegion`、`userAgent` 直接返回可空 `String`，`deviceClass` 非空）。

- [ ] **Step 1: 测试**：`UserLoginRecordTest.should_start_with_client_origin`（五个值照搬）；`UserLoginRecordRepositoryAdapterIT.should_round_trip_device_and_origin_columns`；`LoginRecordSchemaConstraintsIT`：`device_class = 'PHONE'` 与 `end_reason = 'KICKED'` 违反 `CHECK`、`end_reason = 'PASSWORD_CHANGED'` 可写入。
- [ ] **Step 2: 跑** domain `test --tests '*UserLoginRecordTest'` 与 infra `integrationTest --tests '*LoginRecord*'` → FAIL。
- [ ] **Step 3: 实现**（`SessionIssuer` 暂时传 `ClientOrigin` 的 `unknown` 值让编译通过：`ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build()`，Task 10 换成真值）。
- [ ] **Step 4: 跑 → PASS；identity 全模块 `test` → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 登录记录加设备、IP 与属地，结束原因按来由拆细 (PAP-99)`。

### Task 8: 登录记录的三个查询

**Files:**
- Modify: `…/domain/port/repository/UserLoginRecordRepository.java`
- Modify: `…/infra/adapter/persistence/dao/UserLoginRecordDao.java`、`UserLoginRecordRepositoryAdapter.java`
- Test: `UserLoginRecordRepositoryAdapterIT`

**Interfaces:** Produces
- `List<UserLoginRecord> findAllByIds(Collection<Long> ids)`
- `boolean existsTrustedDevice(long userId, DeviceId deviceId, Instant since)`：`device_id = ? AND COALESCE(ended_at, expires_at) >= since AND (end_reason IS NULL OR end_reason <> 'REVOKED')`
- `int deleteRetiredBefore(Instant cutoff, int batchSize)`：原生 SQL `DELETE … WHERE id IN (SELECT id … WHERE COALESCE(ended_at, expires_at) < :cutoff LIMIT :batchSize)`，`@Modifying`

- [ ] **Step 1: IT**：`should_find_records_by_ids`（含不存在的 ID）；`existsTrustedDevice` 五例——同设备未结束 → 真；别的账号 → 假；结束时刻早于窗口 → 假；结束原因 `REVOKED` → 假；`PASSWORD_CHANGED` 在窗口内 → 真；`deleteRetiredBefore` 三例——已结束早于 cutoff 删、未结束且 `expires_at` 早于 cutoff 删、`expires_at` 晚于 cutoff 留；批量 2 时删 3 条要两次调用（返回 2、1）。
- [ ] **Step 2: 跑 → FAIL。Step 3: 实现。Step 4: 跑 → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 登录记录按设备查近况与按退役时刻批量删 (PAP-99)`。

### Task 9: SessionTerminator，五个处理器改用它

**Files:**
- Create: `…/domain/service/SessionTerminator.java`、test `SessionTerminatorTest`
- Modify: `VerifyEmailHandler`（`EMAIL_VERIFIED`）、`ChangePasswordHandler`（`PASSWORD_CHANGED`）、`ResetPasswordHandler`（`PASSWORD_RESET`）、`RevertEmailChangeHandler`（`EMAIL_REVERTED`）、`BanUserHandler`（`BANNED`）及各自测试
- Modify: `…/boot/config/IdentityConfiguration.java`（Bean）、`IdentityConfigurationTest`

**Interfaces:** Produces `SessionTerminator(SessionStorePort, UserLoginRecordRepository)`：`boolean terminate(long userId, long sessionId, LoginEndReason, Instant)`、`List<Long> terminateAll(long userId, LoginEndReason, Instant)`、`List<Long> terminateAllExcept(long userId, long keepSessionId, LoginEndReason, Instant)`。账号类型固定 `AccountType.USER`。

- [ ] **Step 1: `SessionTerminatorTest`**：`should_end_records_of_revoked_sessions_with_reason`；`should_skip_missing_records`；`should_keep_first_end_reason`；`should_not_touch_records_when_redis_fails`（`revokeAll` 抛 `TemporarilyUnavailableException` → `verifyNoInteractions(records)`）；`terminate` 返回 `false` 时不查记录。
- [ ] **Step 2: 跑 → FAIL。Step 3: 实现。Step 4: 跑 → PASS。**
- [ ] **Step 5: 五个处理器改用它**：处理器测试先把期望的结束原因改成新值（RED），再改构造器依赖为 `SessionTerminator`（测试里用真的 `SessionTerminator` 包着原有两个 mock），跑 → PASS。
- [ ] **Step 6: 跑 identity 全模块 `test` 与 boot `integrationTest --tests '*FlowIT'`**（流程测试里断言 `REVOKED` 的地方按新原因改）→ PASS。
- [ ] **Step 7: Commit** `refactor(identity): 撤销会话并结束记录收进 SessionTerminator (PAP-99)`。

### Task 10: 登录、注册记下设备与属地

**Files:**
- Modify: `…/domain/service/SessionIssuer.java`（`issue(long userId, LoginClient client, ClientOrigin origin)`）与测试
- Modify: `…/app/usecase/login/LoginUserCommand.java`（改 `@Builder`：`email, password, clientType, deviceId, clientIp, userAgent`；`toString` 不出 UA 全文、只出长度）、`LoginUserHandler`
- Modify: `…/app/usecase/register/RegisterUserCommand.java`（加 `userAgent`）、`RegisterUserHandler`
- Modify: `…/adapter/rest/auth/AuthController.java`（两个方法收 `HttpServletRequest` 与 `@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent`）与 `AuthControllerIT`
- Modify: `IdentityConfiguration`（`ClientOriginResolver` Bean）、`IdentityConfigurationTest`（runner 加两个端口的 mock）、`IdentityConfigRefreshIT`（同上）
- Test: boot 新建 `LoginDeviceFlowIT`（本任务先写「记录」部分）

**Interfaces:** Consumes Task 4 `ClientOriginResolver.resolve`、Task 7 `UserLoginRecord.start(...)`。Produces `LoginUserCommand.builder()…build()`。

- [ ] **Step 1: 测试**：`SessionIssuerTest.should_start_record_with_origin`；`LoginUserHandlerTest` / `RegisterUserHandlerTest`：`should_resolve_origin_outside_transaction_and_record_it`（resolver mock 返回固定 `ClientOrigin`，断言传给 `sessionIssuer.issue` 的就是它；resolver 在 `transactions.execute` 之前被调用——用 `InOrder`）；`AuthControllerIT`：命令里 `clientIp == "127.0.0.1"`、`userAgent` 与请求头一致。`LoginDeviceFlowIT`（继承 `IdentityFlowITSupport`）：
  - `should_record_device_and_region_on_register_and_login`：注册与登录都带 `X-Forwarded-For: 113.92.157.29`、`User-Agent: UA_CHROME_MAC`、`deviceId` → 记录 `device_name = 'Chrome 141 · macOS'`、`device_class = 'DESKTOP'`、`client_ip = '113.92.157.29'`、`ip_region = '中国 广东 深圳'`、`user_agent` 原文。
  - `should_leave_region_empty_for_private_addresses`：参数化 `192.168.1.10`、`100.70.1.1`、`127.0.0.1` → 登录 200，`ip_region IS NULL`、`client_ip` 是该值。
  - `should_not_fail_login_on_garbage_forwarded_for`：`X-Forwarded-For: not-an-ip` → 200，`client_ip IS NULL`（若 Spring 的 `ForwardedHeaderFilter` 先拒绝了该值，按实际的 `getRemoteAddr()` 断言「不是 not-an-ip」并在 spec 19.5 记录）。
- [ ] **Step 2: 跑 → FAIL。Step 3: 实现**（处理器先 `resolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()))`，再进事务）。
- [ ] **Step 4: 跑上述测试与 identity 全模块 `test` → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 登录与注册记下设备、IP 与属地 (PAP-99)`。

### Task 11: 新设备登录邮件

**Files:**
- Create: `…/domain/policy/NewDeviceLoginPolicy.java`（`record NewDeviceLoginPolicy(Duration lookback, Duration cooldown)`，`of()`，`Instant since(Instant now)`）及测试
- Create: `…/domain/port/throttle/NewDeviceMailThrottlePort.java`（`boolean acquire(long userId, DeviceId device, Instant now)`，`device` 可空）
- Create: `…/infra/adapter/throttle/NewDeviceMailThrottleAdapter.java`（复用 `ActionThrottleScript`：冷却键 `idn:new-device-mail:user:<userId>:<sha256(device) 前 16 位十六进制 | none>`，无窗口）及 IT（`throttle/` 同目录）
- Create: `…/domain/port/mail/NewDeviceLoginMail.java`（`of(EmailAddress to, String displayName, SecurityNotice notice)`；`TEMPLATE = "new-device-login"`；`SUBJECT = "你的 Patra 账号在新设备上登录"`；`NOT_ME_ADVICE` 取 spec 12.4 原文）
- Create: `…/infra/src/main/resources/mail/html/new-device-login.html`、`mail/text/new-device-login.txt`（正文 spec 12.4；照 `password-changed` 的结构，正文末加「建议到安全设置检查登录设备：{securityUrl}」）
- Modify: `ThymeleafMailRendererTest`（新模板两部分）
- Modify: `LoginUserHandler` 与测试；`IdentityProperties`（`NewDeviceMail(@DefaultValue("180d") Duration lookback, @DefaultValue("24h") Duration cooldown)`）；`IdentityConfiguration`（策略 Bean）；`application.yml`
- Test: `LoginDeviceFlowIT` 加邮件用例

**Interfaces:** Consumes Task 8 `existsTrustedDevice`、Task 10 的 `ClientOrigin`。

- [ ] **Step 1: 测试。** `LoginUserHandlerTest`：
  - `should_mail_when_device_not_seen`（`existsTrustedDevice` 在事务内、`sessionIssuer.issue` 之前调用——`InOrder`；`since == now − 180d`；`acquire` 真 → `emailDelivery.send(NewDeviceLoginMail)`，通知里 `device`、`ipAddress`、`ipLocation` 取自 `ClientOrigin`）
  - `should_not_mail_when_device_seen`、`should_not_mail_when_throttled`
  - `should_treat_missing_device_id_as_new`（不查 `existsTrustedDevice`，`acquire(userId, null, now)`）
  - `should_still_log_in_when_throttle_unavailable`（`acquire` 抛 `TemporarilyUnavailableException` → 登录结果照常、不发信）
  - `RegisterUserHandlerTest.should_not_check_or_mail_new_device`。
  `LoginDeviceFlowIT`（`MutableClock`；GreenMail 按收件人计数）：
  - `should_mail_on_login_from_new_device_only`：注册（设备 A）→ 同设备登录不发；设备 B 登录发 1 封，正文含 `Chrome 141 · macOS` 与 `113.92.157.29`。
  - `should_not_mail_when_previous_session_of_device_expired_within_lookback`：设备 B 登录 → 拨 180 天（会话绝对过期）→ B 再登录不发。
  - `should_mail_when_device_unseen_for_lookback`：B 登录后登出 → 拨 180 天 + 1 秒 → B 再登录发。
- [ ] **Step 2: 跑 → FAIL。Step 3: 实现**（事务内判 `seen`，事务外取限流、发信；顺序见 spec 12.1）。
- [ ] **Step 4: 跑上述测试 + `ThymeleafMailRendererTest` + 限流 IT → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 新设备登录发安全通知邮件 (PAP-99)`。

### Task 12: PAP-98 的四封通知填上设备与属地

**Files:**
- Modify: `ChangePasswordCommand`、`ResetPasswordCommand`、`ConfirmEmailChangeCommand`、`RevertEmailChangeCommand`（加 `userAgent`，`of()` 多一个参数）
- Modify: 四个处理器（注入 `ClientOriginResolver`，`origin.toNotice(now, X.NOT_ME_ADVICE)`；`ConfirmEmailChangeHandler.notice(...)` 改收 `ClientOrigin`）与测试
- Modify: `MeController`、`PasswordResetController`、`EmailChangeController` 读 `User-Agent`，及对应 adapter IT
- Modify: boot `PasswordChangeFlowIT`、`PasswordResetFlowIT`、`EmailChangeFlowIT`：各加一条「带 `X-Forwarded-For: 113.92.157.29` 与 UA → 邮件正文含 `Chrome 141 · macOS` 与 `中国 广东 深圳`」

- [ ] **Step 1: 四个处理器测试先断言通知里的 `device` / `ipAddress` / `ipLocation`；控制器 IT 断言命令带 UA。Step 2: 跑 → FAIL。Step 3: 实现。Step 4: 跑这些测试与三个流程 IT → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 账号安全的通知邮件写上设备、IP 与属地 (PAP-99)`。

### Task 13: 会话列表与踢设备

**Files:**
- Modify: `SessionStorePort`（`List<ActiveUserSession> listActive(AccountType, long userId)`）、Create `…/domain/port/session/ActiveUserSession.java`（`record (long sessionId, Instant createdAt, Instant lastActiveAt)`，`of()`）
- Modify: `SessionStoreAdapter` 与 `SessionStoreAdapterTest`
- Modify: `IdentityErrorTrait`（`CURRENT_SESSION`）；Create `…/domain/exception/CurrentSessionRevocationException.java`（`CONFLICT` + `CURRENT_SESSION`，消息「不能踢掉本机，请用退出登录」）
- Create `…/app/usecase/session/`：`SessionQueryService`、`SessionView`（`@Builder`：`id, current, deviceName, deviceClass, ipAddress, ipRegion, privateNetwork, signedInAt, lastActiveAt`）、`RevokeSessionCommand`（`of(long sessionId)`）/ `RevokeSessionHandler`、`RevokeOtherSessionsCommand`（无字段，`of()`）/ `RevokeOtherSessionsHandler`、`SessionsRevoked`（`of(int revokedSessions)`）及测试
- Create `…/adapter/rest/me/MeSessionController.java`（`@RequestMapping("/me/sessions")`）、`response/SessionResponse.java`、`response/SessionListResponse.java`、`response/SessionsRevokedResponse.java`；adapter IT `MeSessionControllerIT`
- Modify: `LoginDeviceFlowIT`（列表与踢设备用例；新设备邮件与踢设备联动的用例）

**Interfaces:** Consumes Task 2 `RedisSessionStore.list`、Task 8 `findAllByIds`、Task 9 `SessionTerminator`、`ReauthenticationGuard.requireRecent(CurrentUser)`、`IpAddressScope.isPrivate`。

- [ ] **Step 1: 测试。**
  - `SessionQueryServiceTest`：`should_put_current_first_then_latest_active`（三条，`lastActiveAt` 相同时按 `id` 倒序）；`should_list_session_without_record_with_empty_device_fields`（`deviceClass == UNKNOWN`、其余空）；`should_flag_private_network_from_stored_ip`；`should_reject_when_current_session_is_gone`（`AuthenticationRequiredException`）。
  - `RevokeSessionHandlerTest`：`should_refuse_current_session_before_reauth`（抛 `CurrentSessionRevocationException`，`verifyNoInteractions(guard)`）；`should_require_recent_reauth`；`should_terminate_with_revoked_reason`（返回 1）；`should_return_zero_when_session_already_gone`。
  - `RevokeOtherSessionsHandlerTest`：守卫先于撤销；返回撤销数。
  - `MeSessionControllerIT`（`@WebMvcTest` + `@Import(MeSessionController.class)`，按 `MeControllerIT` 写法）：GET 的 JSON 字段名与 `id` 为字符串；DELETE `/me/sessions/123` → 命令 `sessionId == 123`、体 `{"revokedSessions":1}`；DELETE `/me/sessions/others` 走另一个命令；`/me/sessions/abc` → 400。
  - `LoginDeviceFlowIT`：`should_list_sessions_with_current_first`；`should_reflect_renewal_in_last_active_at`（拨 30 秒 `RedisSessionStore.findAndTouch` 后 `lastActiveAt` 不变、拨到 61 秒后变）；`should_kick_one_device_after_reauth`（窗口外 403 `REAUTH_REQUIRED` → `POST /auth/reauth` → 踢 → `findAndTouch(被踢令牌)` 为空、记录 `end_reason = 'REVOKED'`、体 `revokedSessions: 1`；再踢 → 0；踢本机 → 409 `CURRENT_SESSION`）；`should_kick_all_other_devices`（三台 → 只剩本机）。
  - `LoginDeviceFlowIT.should_mail_again_after_device_was_kicked_but_at_most_once_a_day`：设备 B 登录（1 封）→ 再认证后踢 B → B 再登录不发（24 小时冷却内）→ 拨 24 小时、再踢 B、B 再登录发第 2 封。
- [ ] **Step 2: 跑 → FAIL。Step 3: 实现**（`MeSessionController` 用 `CurrentUserPort` 由处理器自己取，与 `MeController` 一致）。
- [ ] **Step 4: 跑全部相关测试 → PASS。**
- [ ] **Step 5: Commit** `feat(identity): 加登录设备列表与踢设备接口 (PAP-99)`。

### Task 14: 登录记录清理任务

**Files:**
- Create `…/app/usecase/session/PurgeRetiredLoginRecordsCommand.java`（`of(Duration retention)`）、`PurgeRetiredLoginRecordsHandler.java`（批 1000，照 `PurgeExpiredEmailTokensHandler`）及测试
- Create `…/adapter/scheduler/job/LoginRecordCleanupScheduleJob.java`（handler `loginRecordCleanupJob`，`@Value("${patra.identity.session.record-retention:190d}")`）
- Modify `IdentityProperties.Session`（`@DefaultValue("190d") Duration recordRetention`）与 `application.yml`

- [ ] **Step 1: `PurgeRetiredLoginRecordsHandlerTest`**：`cutoff == now − retention`；仓储返回 1000、1000、3 → 调三次、总数 2003；返回 0 → 调一次。**Step 2: FAIL。Step 3: 实现。Step 4: PASS**（集成层面的边界已由 Task 8 IT 覆盖）。
- [ ] **Step 5: Commit** `feat(identity): 加登录记录的定时清理任务 (PAP-99)`。

### Task 15: 文档、契约与全量回归

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec 20：新增「登录设备」一节；接口表、错误码表、模块表、定时任务表、配置表；Yauaa 与 ip2region 的版本、许可、数据文件获取与季度更新步骤）
- Modify: `patra-infra/docker/README.md`（门户固定地址、ip2region 数据目录与脚本、xxl-job 新任务）
- Modify: `patra-infra/cd/module-graph.json`（若 `dumpModuleGraph` 有变化）
- Modify: `docs/patra/specs/2026-10-22-identity-login-devices-design.md`（状态改「已实施」；第 19 节写回实测结论）
- Add: 本 plan 与 spec

- [ ] **Step 1: 全量回归**：`./gradlew spotlessApply && ./gradlew check`；`./gradlew $I:patra-identity-session:integrationTest $I:patra-identity-infra:integrationTest $I:patra-identity-adapter:integrationTest $I:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest` → 全 PASS，记录各模块用例数。
- [ ] **Step 2: 写 README 与 spec 回写；`./gradlew dumpModuleGraph`（若存在该任务）后 `git diff patra-infra/cd/module-graph.json`。**
- [ ] **Step 3: Commit** `docs(identity): 加登录设备的设计、计划与 README 一节 (PAP-99)`。
- [ ] **Step 4: Linear**：PAP-101、PAP-102、PAP-100 各加「来自 PAP-99 的契约」（spec 21）；PAP-99 改 To-Do 结束原因一条与 AC 2 示例（spec 22），勾选 To-Do / AC，评论附 AC 证据（spec 18.5 的测试名 + 回归输出），状态推到 Review。
- [ ] **Step 5: 在共享笔记目录写 `pap-99-login-devices-implementation-handover.md`**：产物、裁决、mini 要做的事（spec 19.7 ~ 19.9：重建 identity 库、门户固定 IP、Nacos 推送、`fetch-ip2region.sh`、xxl-job 任务）、给规划会话的 release spec 同步项（2.1.8 两句、D5 在 mini 上属地只能是「内网」）。
