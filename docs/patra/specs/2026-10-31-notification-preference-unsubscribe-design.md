# 提醒偏好与一键退订工程设计（PAP-142）

> 状态：已实施，待验收（2026-10-31，计划 `docs/patra/plans/2026-10-31-notification-preference-unsubscribe.md`）；第 13、14 节的归属与收件信息的字段 10/31 经编排会话转达用户「按建议定」；实施中的偏离见第 20 节
> 日期：2026-10-31
> Issue：PAP-142（GitHub #329），Linear Project P-PAP-13 v0.16 Notification，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1（偏好三档、默认离站逐条、有帮助只在站内、安全邮件不进偏好、一键退订）、决策 F、第 5 节 D5；设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md`（设计分支 `claude/v016-design-5692fb`）§3.5、§3.6 与附录 A 第 1～5 条；骨架 spec `docs/patra/specs/2026-10-30-notification-skeleton-design.md`（子域、底座、端口与构建约定）
> 基线：`claude/v016-notify-skeleton-64a17c` @ `9d9892b1c`（Boot 4.0.8）

## 1. 要解决的问题

v0.16 开始给「回复我的」「@ 我的」发提醒邮件（PAP-146）。发之前得先有两样东西，都归本 Issue：

1. **偏好**：每个人给这两类各选一档（关 / 离站时逐条 / 每日汇总），默认离站时逐条；设置页能读、能改，改了立即保存。
2. **退订**：每封提醒邮件都能不登录就退订。两条入口：
   - 邮箱客户端自带的「退订」按钮（RFC 8058 一键退订）：一次 POST 直接生效；
   - 邮件底部的「退订这类邮件」链接：先打开确认页，点「退订」才生效。

本 Issue 交付后端：偏好模型与读写接口、退订令牌、退订落地页的读写接口、一键退订接口，以及给 PAP-146 用的令牌与链接格式。邮件怎么发、退订头怎么拼进邮件归 PAP-146；两个页面与门户的转发归 PAP-148。

## 2. 先讲清四个概念

### 2.1 退订令牌

**是什么**：一串随机字符，放在退订链接里，代表「这是某个人的退订链接」。点链接的人不用登录，服务端凭这串字符就知道是谁。

**防什么**：退订链接要免登录（很多人在手机邮箱里点，根本没登录门户），但又不能让人随便改别人的设置。链接里如果直接写用户 ID（`?user=123`），谁都能把 123 改成 124 去退订别人。令牌是猜不出来的：32 字节随机数有 2²⁵⁶ 种可能，比彩票难中得多。

**业界怎么做**：

| 做法 | 谁在用 | 特点 |
|---|---|---|
| 随机串存库，每封邮件一个 | Discourse（`unsubscribe_keys` 表，每发一封插一行） | 能精确到「这封邮件」；表随发信量一直长 |
| 随机串存库，每人一个 | 本设计 | 表只有每人一行；同一个人所有邮件的链接里令牌相同 |
| 签名令牌（HMAC 签上用户 ID，不存库） | Mastodon（Rails 的签名 ID）、GitHub | 不用存；要一把签名密钥，密钥泄露所有人都能被伪造链接，单个人的链接没法作废 |

**选择**：每人一个随机串，存库（第 6 节）。spec 要求「每个用户一个、长期有效」；不用签名密钥，就不用往 `~/.patra/secrets/` 里再放一样东西。

### 2.2 RFC 8058 一键退订

**是什么**：一份 2017 年的互联网标准，规定邮件怎么支持「邮箱客户端里直接点退订」。发件方在邮件头里放两行：

```
List-Unsubscribe: <https://patra.linqibin.dev/unsubscribe/one-click?token=…&category=MENTION>
List-Unsubscribe-Post: List-Unsubscribe=One-Click
```

Gmail、Apple Mail 等看到这两行，会在邮件顶部显示「退订」。用户点了，**邮箱服务商的服务器**（不是用户的浏览器）向那个地址发一个 POST，请求体固定是 `List-Unsubscribe=One-Click`。

**几条硬规矩**（RFC 8058 第 3 节）：

- POST 不带 Cookie、不带登录信息，所以退订必须只凭地址里的东西（令牌 + 类别）就能完成；
- 服务端直接生效、返回 2xx，**不能重定向**（邮箱服务商不会跟着跳转）；
- 同一个地址可能被 POST 好几次（重试），结果要一样。

**为什么要做**：Gmail、Yahoo 从 2024 年起要求批量发件方支持一键退订；就算 Patra 发信量远够不上「批量」，有这个头也少一些人直接点「垃圾邮件」，保住发件信誉。

### 2.3 为什么落地页「打开不改东西」

**防什么问题**：企业邮箱的安全网关（比如医院的邮件系统）会在邮件送达前把里面的链接都打开一遍，检查是不是钓鱼网站。如果「打开退订链接」就等于退订，用户一封邮件都没看，就被网关替他退订了。所以落地页的读取接口只读不写；用户点了「退订这类邮件」按钮，才发一次真正的写请求（设计简报 §3.6，10/30 用户确认）。

邮箱客户端的一键退订是 POST，网关预取只用 GET，所以一键退订可以直接生效。

底部链接照现有四个邮件落地页（验证邮箱、找回密码、改邮箱的确认与撤销）的做法，把令牌放在地址的 `#` 片段里：浏览器不会把片段发给服务器，预取的网关只拿到一个空页面壳，连令牌都碰不到；门户页面读出片段后立即从地址栏抹掉，令牌不留在历史记录和截图里（第 7 节）。

### 2.4 幂等

**是什么**：同一个请求做一次和做十次，结果一样。比如「把『@ 我的』改成关」是幂等的，「把计数加一」不是。

**这里为什么重要**：邮箱服务商可能重试 POST；用户可能在确认页连点；一封旧邮件的退订链接过几个月再点一次。所以退订接口按「把这一类设成关」来写（而不是「切换」），已经是关的再退订一次照样返回成功、结果相同。

## 3. 范围

**做**：

- notification 的 `kernel`：提醒类别枚举、收件信息端口（调 identity）、注销清理扩展点（第 10.1 节）。
- notification 的 `preference` 子域四层全部代码、`preference` schema 与表。
- 偏好读写接口（需登录），退订落地页的读写接口与一键退订接口（免登录）。
- 给 mail 子域的公开读端口：取某人的档位与退订令牌（第 9 节）。
- identity 新增内部接口「按用户 ID 批量取收件信息」（第 13 节；原列在 PAP-146 的 To-Do 第一条，因本 Issue 先用到而提前，10/31 定）。
- notification 的注销清理底座流程（第 14 节，10/31 定）。
- notification boot 接入安全 starter、HTTP Interface 客户端、identity 代理。
- 网关的集成测试补一例：匿名 POST 退订路径被转发（证明「经网关免登录能退订」）。
- 上线手册第 7 步补本 Issue 的小节。

**不做**：

- 邮件的发送、退订头的拼装、门户地址的配置项：PAP-146（按第 7 节的格式拼）。
- 设置页、退订落地页、门户把一键退订的 POST 转发到网关：PAP-148（第 8.6 节列出它要做的转发）。
- 站内信箱：PAP-143。偏好只管邮件，「只关这一类邮件，站内提醒照常」，inbox 不依赖 preference（骨架 spec 5.3）。
- 免登录重新订阅（设计简报 §3.6：改回来要登录）。
- 换令牌（「重置退订链接」）：没有需求；需要时加一个改令牌的用例即可。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1 | 「回复我的」「@ 我的」各三档；默认离站时逐条；「有帮助」只在站内、偏好里不提供邮件选项；13 类安全邮件不进偏好、不能退订 |
| release spec 2.1 | 一键退订只关这一类邮件，站内提醒照常；落地页还能「关闭全部提醒邮件」；不用登录，链接长期有效 |
| 设计简报 附录 A | 档位 `OFF` / `INSTANT` / `DIGEST`；退订类别 `REPLY` / `MENTION` / `DIGEST` / `ALL`；逐条邮件带 `REPLY` 或 `MENTION`，汇总邮件带 `DIGEST`；`DIGEST` = 关掉此刻设为每日汇总的所有类型；令牌无效、缺失、账号已注销返回同一种「无效」 |
| 设计简报 §3.5 | 按类单独保存，前端一次只改一类 |
| 设计简报 §3.6 | 落地页打开只读，点按钮才退订；带着登录状态打开也只按令牌工作；不做免登录重新订阅 |
| release spec 第 3 节 G | 公网只开放了门户（Cloudflare Tunnel 只转发到门户 4000 端口），网关在 tailnet 内 |
| 决策 F | 通知服务不存邮箱副本，用的时候按 user_id 调 identity 现查 |
| 骨架 spec | 子域 `preference` 管偏好与退订；底座 `kernel` 放类别、注销清理扩展点；每个子域一个 schema；只存用户 ID；第一个要登录的接口接安全 starter；第一个调 identity 的地方加 HTTP Interface 分组 |
| 网关 | 第二条过滤器链对 identity 以外的路径一律放行（带令牌时照样查会话、写身份断言）；`/*/_internal/**` 对外 403 |
| 安全 starter | 下游所有路径放行，需要登录的接口由业务代码调 `CurrentUserPort.require()`；CSRF 已关 |

## 5. 规则

### 5.1 档位与默认值

| 类别 | 可选档位 | 默认 |
|---|---|---|
| 回复我的（`REPLY`） | `OFF` 关 / `INSTANT` 离站时逐条 / `DIGEST` 每日汇总 | `INSTANT` |
| @ 我的（`MENTION`） | 同上 | `INSTANT` |
| 有帮助（`HELPFUL`） | 无：永远不发邮件。读档位时当作 `OFF`；改它返回 422 | — |

没有偏好行的人一律按默认值处理；**只读不落库**，第一次改档位、第一次被取令牌时才建行（第 6.4 节）。

### 5.2 退订类别与效果

| 类别 | 出现在哪 | 退订后 |
|---|---|---|
| `REPLY` | 「回复我的」逐条邮件 | 「回复我的」设为 `OFF`，不看原来是哪一档 |
| `MENTION` | 「@ 我的」逐条邮件 | 「@ 我的」设为 `OFF`，同上 |
| `DIGEST` | 每日汇总邮件 | 此刻是 `DIGEST` 的类别都设为 `OFF`；`INSTANT` 的不动 |
| `ALL` | 只在落地页（「关闭全部提醒邮件」「同时关闭其余提醒邮件」） | 两类都设为 `OFF` |

`REPLY` 不看原来的档位：用户把「回复我的」改成每日汇总之后，再点一封旧的逐条邮件里的退订，意思仍是「别再给我发回复邮件」。

### 5.3 「已经退订过了」与「其余还开着」

落地页要决定显示 `unsub.ready` 还是 `unsub.already`、出不出「关闭全部提醒邮件」，退订成功后出不出「同时关闭其余提醒邮件」。两个判断都由后端算好返回：

- **已经退订过**（`alreadyUnsubscribed`）：`REPLY` 看「回复我的」是不是 `OFF`；`MENTION` 同理；`DIGEST` 看是不是**没有任何类别**处于 `DIGEST`（都不在汇总，就收不到汇总邮件）。
- **其余还开着**（`othersEnabled`）：假设这一类已经退订，还有没有类别不是 `OFF`。`REPLY` 看「@ 我的」；`MENTION` 看「回复我的」；`DIGEST` 看有没有类别是 `INSTANT`；`ALL` 永远是否。

读接口返回「退订之前」的这两项，写接口返回「退订之后」的 `othersEnabled`。两处定义相同：执行这一类退订之后，还有没有提醒邮件开着。

### 5.4 无效链接

下面几种都返回同一个「链接无效」（404，原因码 `UNSUBSCRIBE_LINK_INVALID`），不区分原因：

- 令牌缺失、格式不对（不是 43 个 base64url 字符）、库里没有；
- 类别缺失或不认识；读接口与一键退订接口的类别是 `ALL`（链接里不会有 `ALL`）；
- 读接口：identity 说这个账号已注销（`DELETED`），或不认识这个用户，或给的邮箱为空（没有地址可显示，也收不到提醒邮件）。

封禁（`BANNED`）与注销冷静期（`PENDING_DELETION`）的账号**链接照常有效**：退订只会让他少收邮件，没有坏处；冷静期内撤销注销后，设置还是他自己的。

写接口（落地页的确认、一键退订）**不调 identity**：只凭令牌改档位。这样 identity 挂了也能退订（一键退订的可靠性比「已注销的人也能把自己已不存在的邮件关掉」重要）；已注销用户的偏好行由注销清理删掉（第 14 节），删掉后链接自然无效。这一条有个前提：门户调这几个接口时不带用户的会话令牌，否则网关会先去 identity 查会话，identity 挂了就被挡成 503（8.6）。

## 6. 退订令牌

### 6.1 形态

- 32 字节 `SecureRandom` 随机数，base64url 不带填充，恰好 43 个字符（`[A-Za-z0-9_-]{43}`）。照 identity 的 `EmailTokenSecret`。
- base64url 只用字母、数字、`-`、`_`，放进链接不用再转义。
- 领域值对象 `UnsubscribeToken`：`generate(SecureRandom)`、`parse(String)`（去首尾空白、校验格式，不合法返回空，不去查库）、`toString()` 只输出 `UnsubscribeToken[***]`。

### 6.2 存原文，不像 identity 那样只存哈希

identity 的一次性令牌（验证邮箱、找回密码）在库里只存 SHA-256：原文只在发邮件那一刻出现一次，用完就作废，库被拖走也拿不到能用的令牌。

退订令牌不一样：**每封提醒邮件都要把它拼进链接**（PAP-146 每次发信都要读出原文）。只存哈希就拼不出链接。所以存原文。

代价：库被拖走时，拿到的人能替别人关掉提醒邮件。影响面小：令牌只能关邮件、不能开，只能看到脱敏后的邮箱和两个档位；notification 的库只在 tailnet 内。加密存储要一把密钥，又回到签名令牌的密钥管理问题，不值得。

### 6.3 为什么不用签名令牌

签名令牌（把用户 ID 用 HMAC 签名后放进链接）不用存库，但：要一把密钥放 `~/.patra/secrets/`、本机和 mini 各一份；密钥一旦泄露，所有人的链接都能被伪造；以后要作废某一个人的链接做不到（只能换密钥，所有人的旧链接一起失效）。存库的随机串没有这些问题，代价只是每人一行。

### 6.4 何时生成、何时失效

- **生成**：偏好行第一次落库时一起生成，之后不变。落库发生在两处：用户第一次改档位；mail 第一次为他取令牌（第 9 节，发第一封提醒邮件之前）。两处都是「没有就插入、有就不动」，并发插入时只有一个成功，其余读到同一行，所以每个用户始终只有一个令牌。
- **长期有效**：没有过期时间。
- **失效**：注销清理删掉偏好行时（第 14 节）。本版不做换令牌。

## 7. 链接格式（给 PAP-146 与 PAP-148）

两个地址，都在门户上：

```
确认页（邮件底部的「退订这类邮件」）：
{门户地址}/unsubscribe#token={令牌}&category={REPLY|MENTION|DIGEST}

一键退订（List-Unsubscribe 头）：
{门户地址}/unsubscribe/one-click?token={令牌}&category={REPLY|MENTION|DIGEST}
```

- 门户地址：线上 `https://patra.linqibin.dev`，本机与 e2e 是门户的本机地址。配置项由 PAP-146 加（发信时才用到，同 identity 邮件链接的做法：域名来自配置，不来自请求的 Host 头）。
- 逐条邮件：「回复我的」带 `REPLY`、「@ 我的」带 `MENTION`；多条合一的邮件只合同一类（设计简报 §3.9），所以一封邮件只有一个类别。每日汇总带 `DIGEST`。
- 令牌取自第 9 节的读端口；它是 base64url，放进地址不用再转义。
- **这两个地址是长期契约**：已经发出去的邮件里的链接要一直能用，路径和参数名以后不能改。

PAP-146 在提醒邮件里放：

| 位置 | 内容 |
|---|---|
| `List-Unsubscribe` 头 | `<` + 一键退订地址 + `>` |
| `List-Unsubscribe-Post` 头 | `List-Unsubscribe=One-Click` |
| 正文底部「退订这类邮件」 | 确认页地址 |
| 正文底部「通知设置」 | `{门户地址}/settings/notifications`（设计简报 §2） |

**为什么是两个地址、为什么都在门户**：

- 网关在 tailnet 内，Gmail 的服务器打不到 `/patra-notification/...`，一键退订只能落在门户上，由门户转发到网关（release spec 第 3 节 G；门户要做的转发见 8.6）。
- 确认页的令牌放片段，是沿用现有四个落地页（`TokenLandingPage`，设计简报 §2 的建议）：预取拿不到令牌、读到即抹掉（第 2.3 节）。
- 一键退订的 POST 由邮箱服务商的服务器发出，**片段不会随请求发给服务器**，所以令牌只能放查询串。
- 少数不支持一键退订的客户端会在浏览器里打开 `List-Unsubscribe` 的地址（`GET`）：门户对一键退订地址的 `GET` 跳到确认页（8.6），用户仍然先看到确认再退订。

## 8. 对前端的接口（PAP-148 据此开写）

前端经网关访问，路径前加 `/patra-notification`。响应都是 JSON；错误是 ProblemDetail（`code` 形如 `NOTIFICATION-0404`，`traits` 列原因码），同其他服务。

### 8.1 偏好读写（需登录）

**读**：`GET /me/notification-preferences`

```json
200
{ "reply": "INSTANT", "mention": "INSTANT" }
```

- 没改过的返回默认值 `INSTANT`。只有「回复我的」「@ 我的」两项；「有帮助」不在里面（页面上那张卡片只有说明）。
- 未登录 401。

**改一类**：`PUT /me/notification-preferences/{category}`，`category` 是 `REPLY` 或 `MENTION`

```json
请求  { "mode": "DIGEST" }
200   { "reply": "DIGEST", "mention": "INSTANT" }
```

- 立即保存，返回两类当前的档位（前端拿它覆盖本地状态）。两类分别保存、互不影响：同时改两类的两个请求不会互相覆盖（第 12 节）。
- 未登录 401；`category` 不是 `REPLY` / `MENTION`（含 `HELPFUL`）、`mode` 缺失或不认识 → 422（`NOTIFICATION-0422`，原因码 `RULE_VIOLATION`）。
- 同一档再设一次照常 200。

### 8.2 退订落地页：读（免登录，不改任何东西）

`GET /unsubscribe?token={令牌}&category={REPLY|MENTION|DIGEST}`

```json
200
{
  "maskedEmail": "c***@example.com",
  "category": "MENTION",
  "reply": "INSTANT",
  "mention": "INSTANT",
  "alreadyUnsubscribed": false,
  "othersEnabled": true
}
```

| 字段 | 含义 | 页面用途 |
|---|---|---|
| `maskedEmail` | 账号当前邮箱，本地部分只留首字符（同 identity 的写法） | 信息表「地址」 |
| `category` | 原样回显链接里的类别 | 标题、按钮文案（`退订这类邮件` / `退订每日汇总`） |
| `reply` / `mention` | 两类当前的档位 | 信息表「邮件」一行（如「@ 我的」提醒 · 离站时逐条）；汇总邮件成功页按类写说明 |
| `alreadyUnsubscribed` | 第 5.3 节 | `true` → `unsub.already` |
| `othersEnabled` | 第 5.3 节（退订这一类之后还有没有邮件开着） | `false` → 不出「关闭全部提醒邮件」 |

- 链接无效（第 5.4 节）→ 404，`traits` 含 `UNSUBSCRIBE_LINK_INVALID` → `unsub.invalid`。
- identity 不可用 → 503（`DEP_UNAVAILABLE`）。设计简报里读失败没有专门的状态，前端按通用错误处理。
- 可以被预取、可以重复打开。

### 8.3 退订落地页：写（免登录）

`POST /unsubscribe`

```json
请求  { "token": "…43 个字符…", "category": "MENTION" }
200
{
  "category": "MENTION",
  "turnedOff": ["MENTION"],
  "reply": "INSTANT",
  "mention": "OFF",
  "othersEnabled": true
}
```

- `category` 可以是 `REPLY` / `MENTION` / `DIGEST` / `ALL`：点「退订这类邮件」传链接里的类别；点「关闭全部提醒邮件」「同时关闭其余提醒邮件」传 `ALL`。
- `turnedOff`：这一次真的从开变成关的类别，按 `REPLY`、`MENTION` 的顺序；重复退订时是空数组。
- `reply` / `mention`：退订之后两类的档位。汇总邮件的成功页说明按它写：两类都变成关 → 「都改为不发邮件」；只有一类在汇总 → 只写那一类，另一类写「仍是离站时逐条」。
- `othersEnabled`：退订之后还有没有提醒邮件开着；`false` → 成功页不出「同时关闭其余提醒邮件」。`ALL` 时永远是 `false`。
- 重复调用结果相同，照常 200。
- 令牌或类别无效 → 404 `UNSUBSCRIBE_LINK_INVALID`（`unsub.error` 只用于网络和服务错误）。

### 8.4 一键退订（RFC 8058，免登录）

`POST /unsubscribe/one-click?token={令牌}&category={REPLY|MENTION|DIGEST}`

- 请求体是邮箱服务商发的 `List-Unsubscribe=One-Click`（`application/x-www-form-urlencoded` 或 `multipart/form-data`）。**后端不检查请求体**：地址里的令牌和类别足够完成退订；对请求体宽松，各家客户端的细微差异和 D5 里的 `curl` 都能用。
- 令牌与类别**只从查询串取**：表单 POST 下 Spring 的 `@RequestParam` 会把请求体里的同名参数和查询串合并（两个值拼成 `a,b`），请求体被改写或伪造时退订会失败、甚至被顶替。
- 效果同 8.3；成功返回 200，响应体同 8.3（邮箱服务商不看响应体）。**不重定向**。
- 链接无效 → 404。类别是 `ALL` 也算无效（链接里不会有）。
- 这条路径只有 POST；`GET` 返回 405（确认页由门户出，不走这里）。

### 8.5 错误码汇总

| 情况 | 状态 | `code` | `traits` |
|---|---|---|---|
| 偏好接口未登录 | 401 | `NOTIFICATION-0401` | 同其他服务 |
| 偏好的类别 / 档位不合法 | 422 | `NOTIFICATION-0422` | `RULE_VIOLATION` |
| 退订链接无效 | 404 | `NOTIFICATION-0404` | `NOT_FOUND`、`UNSUBSCRIBE_LINK_INVALID` |
| 退订读接口时 identity 不可用 | 503 | `NOTIFICATION-0503` | `DEP_UNAVAILABLE` |

链接无效用 404 而不是 identity 那样的 422：这里是「按令牌找资源，找不到」，404 更贴；前端靠 `traits` 里的 `UNSUBSCRIBE_LINK_INVALID` 判断，不靠状态码，和网关路由缺失的 404 分得开。

### 8.6 门户要做的转发（PAP-148）

| 门户收到 | 门户做 |
|---|---|
| `GET /unsubscribe#token&category`（确认页） | 照 `TokenLandingPage`：挂载后从片段读出令牌与类别、立即抹掉；经门户服务端调 8.2，按结果出 `ready` / `already` / `invalid`；页面 `robots: noindex` |
| 确认页上点按钮 | 经门户服务端调 8.3 |
| `POST /unsubscribe/one-click?token&category`（邮箱服务商的一键退订） | 原样转发到网关 `POST /patra-notification/unsubscribe/one-click?token&category`，把后端的状态码返回给对方；**不重定向、不带用户的会话令牌**（退订只认令牌，不看当前登录的是谁） |
| `GET /unsubscribe/one-click?token&category`（不支持一键退订的客户端在浏览器里打开） | 跳到 `/unsubscribe#token=…&category=…`（确认页），不改任何东西；建议加 `Referrer-Policy: no-referrer` |

门户调后端这三个接口时都**不带**用户的会话令牌：带了也不影响结果，但网关会多查一次 identity 的会话接口，identity 不可用时还会被挡成 503，退订就不再「只要 notification 在就能用」。

## 9. 给 mail 子域的公开读端口（PAP-146）

mail 发信前要知道两件事：这个人这一类选的哪一档；他的退订令牌。preference 公开一个读端口（mail → preference 的依赖边由 PAP-146 在架构测试里声明，骨架 spec 5.3）：

```java
package dev.patra.notification.domain.preference.port.read;

/// mail 发提醒邮件前取收件人的邮件订阅：两类的档位与退订令牌。
public interface EmailSubscriptionReadPort {

  /// 某人的邮件订阅。没有偏好行时按默认值建一行（带新令牌）再返回：对调用方来说每个人「本来就有」
  /// 一个令牌，第一次读到时才落库；并发调用拿到的是同一个令牌。
  EmailSubscription subscriptionOf(long userId);
}
```

```java
package dev.patra.notification.domain.preference.model.read;

/// @param unsubscribeToken 令牌原文，按第 7 节拼进链接；toString 不输出它
public record EmailSubscription(
    long userId, EmailMode reply, EmailMode mention, String unsubscribeToken) {

  /// 某一类的档位；HELPFUL 永远是 OFF。
  public EmailMode modeOf(NotificationCategory category) { … }
}
```

`EmailMode`（`OFF` / `INSTANT` / `DIGEST`）放在 `model.read`：mail 要读它，而普通子域对外只公开 `port.read`、`model.read`、`event` 三个包。

**为什么一个「读」端口会建行**：令牌要在第一封邮件发出前就有，但 notification 不知道「有新用户注册了」，没有更早的时机去建。把「没有就建」藏在读端口里，mail 只管读；实现是 `INSERT … ON CONFLICT (user_id) DO NOTHING` 再按 `user_id` 读回，幂等。每日汇总要「找出所有设为汇总的人」，那个查询由 PAP-146 按需加到这个端口。

**给 PAP-146 的一条用法**：先用 `RecipientPort` 查收件人（已注销、封禁、邮箱未验证都不发），再调 `subscriptionOf`。反过来调会给已注销的人重新建一行（带新令牌），要等第二天的注销清理再删；注销清理恰好删在「插入落空」与「读回」之间时，读回为空会报 500。两种情况都只落在已注销用户身上。

## 10. 子域与代码落点

根包 `dev.patra.notification`，分包按 `.claude/rules/tech/package-layout.md`。

### 10.1 kernel（底座）新增

| 东西 | 位置 | 谁用 |
|---|---|---|
| `NotificationCategory { REPLY, MENTION, HELPFUL }` | `domain.kernel.model.enums` | preference（按它存档位，HELPFUL 没有邮件）；inbox 的三个标签（PAP-143 / 144）；mail 按它查档位（PAP-146） |
| 收件信息端口 `RecipientPort`、读模型 `Recipient`、账号状态 `AccountState`、`IdentityUnavailableException`（503） | `domain.kernel.port.identity`、`model.read`、`model.enums`、`exception` | preference（落地页的脱敏邮箱、已注销判定）；mail（收件地址、是否已验证、账号状态、用户名与显示名，PAP-146） |
| 账号状态端口 `AccountStatePort`（调 `/summaries`） | `domain.kernel.port.identity` | 注销清理流程（第 14 节） |
| 实现 `RecipientAdapter` | `infra.kernel.identity` | 调第 13 节的 identity 接口 |
| 注销清理扩展点 `UserDataCleanupGateway` | `domain.kernel.port.gateway` | 照 interaction 的同名接口原样：`subdomain()`、`findUsersAfter(afterUserId, limit)`、`deleteAllOf(userId)`；preference 实现一份（第 14 节），inbox、mail 以后各实现一份 |

```java
/// 按用户 ID 取收件信息（identity 内部接口，第 13 节）。一次最多 100 个。
public interface RecipientPort {
  int MAX_USERS = 100;
  Map<Long, Recipient> findAll(Collection<Long> userIds);   // identity 不认识的省略
  default Optional<Recipient> find(long userId) { … }
}

/// @param email 已注销为 null
/// @param username 已注销为 null
/// @param displayName 昵称，没设时已回退成用户名；已注销为 null
public record Recipient(
    long userId,
    String email,
    boolean emailVerified,
    AccountState state,
    String username,
    String displayName) {}
```

**与骨架 spec 5.2 的偏离**：骨架写「邮箱地址、是否已验证只有 mail 用，放 mail 自己的端口，不进底座」。现在 preference 的落地页也要邮箱（脱敏显示）和账号状态，和 mail 两处共用，按「两个以上子域共用才进底座」放进 kernel。

### 10.2 preference

| 层 | 类 | 说明 |
|---|---|---|
| domain `model.read` | `EmailMode`、`EmailSubscription` | 公开给 mail（第 9 节） |
| domain `model.enums` | `UnsubscribeCategory { REPLY, MENTION, DIGEST, ALL }` | `inLinks()`：前三个会出现在链接里；`parse(String)` |
| domain `model.vo` | `UnsubscribeToken`、`MaskedEmail` | 第 6.1 节；`MaskedEmail.of("chen@example.com")` → `c***@example.com` |
| domain `model.aggregate` | `EmailPreference` | 一个用户一个：`userId`、令牌、两类档位。`createDefault`、`restore`、`modeOf(category)`、`change(category, mode)`、`unsubscribe(category)` → 关掉的类别、`isUnsubscribedFrom(category)`、`anyEmailOnAfter(category)`；第 5 节的规则都在这里 |
| domain `exception` | `PreferenceErrorTrait`、`UnsubscribeLinkInvalidException`（404）、`InvalidPreferenceRequestException`（422） | 照 interaction 的 `NotificationErrorTrait` 写法 |
| domain `port.repository` | `EmailPreferenceRepository` | `findByUserId`、`findByToken`、`findByUserIdForUpdate`、`findByTokenForUpdate`、`insertIfAbsent`、`updateModes`、`findUsersAfter`、`deleteByUserId` |
| domain `port.read` | `EmailSubscriptionReadPort` | 第 9 节 |
| app `usecase.query` | `EmailPreferenceQueryService`、`EmailPreferenceView`、`UnsubscribeLinkView` | 读本人偏好；落地页读（调 `RecipientPort`） |
| app `usecase.change` | `ChangeEmailModeCommand` / `Handler` | 改一类 |
| app `usecase.unsubscribe` | `UnsubscribeCommand` / `Handler` / `UnsubscribeResult` | 落地页的写与一键退订共用，命令里标明来源（一键退订不收 `ALL`）；命令的 `toString` 不输出令牌 |
| app `service` | `PreferenceUserDataCleanupGatewayImpl` | 注销清理扩展点 |
| infra `persistence` | `EmailPreferenceRepositoryAdapter`、`EmailSubscriptionReadAdapter` | JdbcClient 原生 SQL，同 interaction |
| infra 资源 | `db/migration/preference/V…__create_preference_schema.sql`、`V…__create_preference_tables.sql` | 第 11 节 |
| adapter `rest` | `MyEmailPreferenceController`（`/me/notification-preferences`）、`UnsubscribeController`（`/unsubscribe`） | 请求、响应 record 在 `rest.request` / `rest.response` |

令牌生成要随机数：`SecureRandom` 在 app 的两个写用例与 infra 的读适配器里各持有一个，传给 `UnsubscribeToken.generate`。

### 10.3 子域边

本 Issue 不新增跨子域的边：preference 只依赖 kernel（底座不用声明）。

## 11. 存储

`preference` schema，一张表：

```sql
CREATE SCHEMA preference;

CREATE TABLE preference.email_preference
(
    user_id            BIGINT          NOT NULL,
    reply_mode         VARCHAR(16)     NOT NULL,
    mention_mode       VARCHAR(16)     NOT NULL,
    unsubscribe_token  VARCHAR(43)     NOT NULL,
    created_at         timestamptz(6)  NOT NULL,
    updated_at         timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_email_preference_token UNIQUE (unsubscribe_token),
    CONSTRAINT ck_email_preference_reply_mode CHECK (reply_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_email_preference_mention_mode CHECK (mention_mode IN ('OFF', 'INSTANT', 'DIGEST')),
    CONSTRAINT ck_email_preference_token CHECK (unsubscribe_token ~ '^[A-Za-z0-9_-]{43}$')
);
```

- 每个用户一行，主键 `user_id`；没有行 = 默认值。只存用户 ID（R6）。
- 令牌唯一索引既保证不重复，也是按令牌查的索引。
- `created_at` / `updated_at` 由应用写入（注入的 `Clock`），不挂 `set_updated_at()` 触发器：每条写入都显式给 `updated_at`，同 interaction 的 `collection.collection`（那里的注释写了理由：触发器在「新值等于旧值」时会换成数据库时间）。骨架 spec 第 8 节「随第一张带 `updated_at` 的表建触发器」在这里不适用，`preference` schema 不建这个函数。
- 迁移脚本版本号取写脚本时的时间戳 `yyyyMMddHHmmss`；`NotificationMigrationIT` 的 schema 白名单已含 `preference`，不用改。
- 表与列加英文 `COMMENT`，同 interaction。

## 12. 并发与幂等的实现

所有写都在 app 层的一个事务里、对这一行加行锁（`SELECT … FOR UPDATE`）后在 Java 里按领域规则改，再按列写回：

| 用例 | 步骤 |
|---|---|
| 改一类 | `insertIfAbsent(默认值 + 新令牌)`（`ON CONFLICT (user_id) DO NOTHING`）→ `findByUserIdForUpdate` → `change` → `updateModes` |
| 退订（两个写接口） | 解析令牌与类别（不合法直接 404，不查库）→ `findByTokenForUpdate`（没有 → 404）→ `unsubscribe` → 有变化才 `updateModes` |
| mail 取订阅 | `INSERT … ON CONFLICT DO NOTHING` → 按 `user_id` 读回；不加锁（只读档位和令牌） |

行锁让「同时改两类」「设置页改档位的同时邮箱服务商发来一键退订」排队执行，后一个看到的是前一个提交后的值，不会互相覆盖。`insertIfAbsent` 让第一次改档位与第一次取令牌并发时只建一行、只有一个令牌。

退订按「设成关」实现（第 2.4 节），所以天然幂等：第二次 `turnedOff` 为空，最终状态相同。

## 13. identity 新增的内部接口

落地页要显示脱敏邮箱，现有 `POST /_internal/users/summaries` 不带邮箱。按 PAP-146 To-Do 第一条的字段（收件地址、是否已验证、账号状态、用户名）新增一个批量接口，本 Issue 先用、PAP-146 直接复用。另加 `displayName`：设计简报 §3.7 与附录 A 第 15 条写的是邮件里显示「显示名」，而 PAP-146 的 To-Do 写「用户名」，两个都给，PAP-146 不用回头改契约（10/31 定：identity 的 `display_name` 列允许为空，没设显示名的人邮件里要用用户名兜底）。只给通知服务用，不把邮箱加进 `/summaries`（它在 interaction 里十几处用来显示作者卡片，加进去邮箱就会流到那些地方）；identity 这边只增不改，`/summaries` 的代码与字段都不动：

```
POST /_internal/users/recipients
请求  { "userIds": [1, 2, 3] }            // 1 ~ 100 个，可重复
200   { "recipients": [
          { "userId": 1, "email": "chen@example.com", "emailVerified": true,
            "accountStatus": "ACTIVE", "username": "chen", "displayName": "陈宇" },
          { "userId": 2, "email": null, "emailVerified": false,
            "accountStatus": "DELETED", "username": null, "displayName": null }
      ] }
```

- 契约在 `patra-identity-api` 的 `UserEndpoint` 加一个方法，DTO `UserRecipientsReq`、`UserRecipientsResp`、`UserRecipientResp`；实现照 `summaries` 的分层：`UserRecipientQueryService` + `UserReadPort.findRecipients` + `UserReadAdapter`（同 `findSummaries`，走 JPA 的 `userDao.findAllById`，资料行取昵称）+ `UserEndpointImpl`。
- 规则同 `summaries`：上限按去重前的长度算，超过 100、列表为 `null`、含 `null` → 422；按去重后的请求顺序返回；不认识的 ID 省略并记 `WARN`。
- 昵称没设时回退成用户名，同 `summaries`。
- 已注销：`email`、`username`、`displayName` 为 `null`，`emailVerified` 为 `false`。identity 注销时已把邮箱和验证时间置空，但**用户名在库里保留**，所以要像 `UserSummaryReadModel.viewFor` 那样在读模型里显式置空。
- 只在 `/_internal` 下，网关对外 403；notification 经服务发现直连 `lb://patra-identity`。
- notification boot 加 `IdentityClientConfiguration`（照 interaction），`application.yml` 加 `identity` 分组：连接超时 1 秒、读超时 2 秒（按主键批量查，同 interaction）。

## 14. 注销清理

10/31 定：底座流程由本 Issue 建，PAP-143 只给 inbox 补一份 `UserDataCleanupGateway` 实现。结构照搬 interaction：

| 层 | 类 | 做什么 |
|---|---|---|
| domain `kernel.port.gateway` | `UserDataCleanupGateway` | 扩展点：`subdomain()`、`findUsersAfter(afterUserId, limit)`、`deleteAllOf(userId)` |
| domain `kernel.port.identity` | `AccountStatePort` | 批量取账号状态（≤ 100 个），identity 不可用抛 `IdentityUnavailableException` |
| infra `kernel.identity` | `AccountStateAdapter` | 调现成的 `POST /_internal/users/summaries`（没有访客，`viewerUserId` 为空），只取 `accountStatus`。不走第 13 节的收件信息接口：清理每天要扫全部有数据的用户，没必要每天把所有人的邮箱拉一遍（同 interaction） |
| app `kernel.usecase.cleanup` | `CleanupDeletedUsersCommand` / `Handler` / `CleanupResult` | 按用户 ID 升序，每轮把各子域在游标之后有数据的用户并起来取最小的 100 个，问一次 identity；只删 `DELETED` 的，每个（用户，子域）一个事务；identity 不认识的跳过并打 WARN；identity 不可用跳过这一批、计失败批次 |
| adapter `kernel.scheduler` | `DeletedUserCleanupScheduleJob` | XXL handler `deletedUserCleanupJob`，有失败批次回写失败（调度中心标红，次日重跑） |
| app `preference.service` | `PreferenceUserDataCleanupGatewayImpl` | 列有偏好行的用户；删这个人的偏好行，令牌随行作废，此后他所有旧邮件里的退订链接都是「链接无效」 |

清理跑之前，已注销用户的退订链接在**读接口**上已经是无效（第 5.4 节回查 identity）；写接口仍能改他残留的偏好行，没有坏处。

## 15. 安全

- **登录**：偏好的两个接口在 app 层调 `CurrentUserPort.require()`，未登录 401。退订的三个接口不调，免登录。网关的第二条链对 notification 路径一律放行，不用改网关配置；集成测试证明这一点（第 16 节）。
- **令牌不进日志**：`UnsubscribeToken`、`EmailSubscription`、两个命令与请求 record 都覆盖 `toString()`；日志里不打令牌。命令总线的日志拦截器成功时只打命令类名，失败时会打异常的 `getMessage()`，所以 preference 的异常文案一律是固定文本，不拼令牌、不拼请求参数。
- **猜令牌**：2²⁵⁶ 种可能，不需要限流。
- **CSRF**：安全 starter 已关（无状态、不用 Cookie）。有人诱导浏览器向退订地址发 POST，也得先有令牌。
- **令牌只出现在**：库里、邮件里、一键退订地址的查询串里（RFC 8058 要求地址自带全部信息）、门户与网关之间的请求里（tailnet 内）。确认页把它放在片段里、读到即抹掉（第 7 节）。本机用 `bootRun` 挂 OTel agent 时，agent 的 HTTP 服务端 span 会记下查询串，令牌会进本机的 trace；容器里不挂 agent，不受影响。
- **identity 的端口直连**（不是本 Issue 引入，记在这里）：compose 把 identity 的 6400 映射到了宿主机，网关的 `/*/_internal/**` 403 只管经网关的请求；tailnet 或局域网里的设备直连 6400 就能调内部接口。`/_internal/users/recipients` 是第一个返回邮箱原文的内部接口，按 ID 就能批量取到邮箱。是否收紧（不映射端口、或给内部接口加服务间认证）留给编排会话决定。

## 16. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 新用户的偏好默认值符合 2.1 | `EmailPreferenceTest`（默认两类 `INSTANT`、`HELPFUL` 为 `OFF`）；`PreferenceFlowIT`：新用户 `GET` 得两类 `INSTANT`，库里没有行 |
| 逐条退订只关这一类，汇总退订关掉所有设为汇总的类型；重复调用不报错、结果相同 | `EmailPreferenceTest` 按第 5.2 / 5.3 节逐条；`UnsubscribeFlowIT`：`REPLY` 只关回复、`DIGEST` 只关处于汇总的、`ALL` 全关；同一请求连发两次都 200、第二次 `turnedOff` 为空 |
| 打开落地页（读接口）不改偏好，确认后才生效 | `UnsubscribeFlowIT`：读接口调两次后档位与 `updated_at` 不变；`POST` 之后才变 |
| 无效令牌返回明确的错误 | `UnsubscribeTokenTest`（格式）；`UnsubscribeFlowIT`：缺令牌、格式错、库里没有、类别错、一键退订带 `ALL`、账号已注销 → 都是 404 + `UNSUBSCRIBE_LINK_INVALID` |
| 经网关免登录能退订；偏好读写接口未登录时被拒 | `UnsubscribeFlowIT`：不带身份断言的一键退订与落地页写都生效；`PreferenceFlowIT`：不带断言 `GET` / `PUT` 偏好 → 401；网关 `GatewayRoutingIT` 新增一例：不带 `Authorization` 的 `POST /patra-notification/unsubscribe/one-click?…` 被转发到下游（WireMock 收到、查询串原样、无 `Authorization`），下游的 200 原样返回、不是 401 / 重定向 |

其余：

| 测试 | 断言 |
|---|---|
| `MaskedEmailTest` | `c***@example.com` 写法；一个字符的本地部分 |
| `EmailPreferenceRepositoryAdapterIT`（infra） | 插入不覆盖已有行；按令牌、按用户读；行锁下改档位；`findUsersAfter` 游标与上限；`deleteByUserId`；约束名（令牌唯一、档位取值） |
| `EmailSubscriptionReadAdapterIT`（infra） | 第一次建行带默认值与合法令牌；第二次同一个令牌；两个线程并发首次调用拿到同一个令牌、只有一行 |
| `ChangeEmailModeHandlerTest`、`UnsubscribeHandlerTest`、`EmailPreferenceQueryServiceTest`（app） | 未登录抛未认证；`HELPFUL` / 非法档位 422；令牌格式错不查库；读接口遇已注销 / identity 不认识 → 404，identity 不可用 → 503 |
| `PreferenceUserDataCleanupGatewayImplTest` | 转调仓储 |
| `RecipientAdapterTest`（infra） | 三种失败翻成 503；状态映射；超过 100 个参数错误 |
| `PreferenceFlowIT` 另 | `PUT` 一类不影响另一类；`PUT` 后 `GET` 一致（刷新后保持） |
| `UnsubscribeFlowIT` 另 | 读接口返回脱敏邮箱与第 5.3 节两项；`GET /unsubscribe/one-click` 405；一键退订带表单体与不带体都生效 |
| identity：`UserRecipientQueryServiceTest`、读适配器 IT、`UserEndpointImplIT` 新例 | 上限与校验、顺序与去重、已注销的空字段、不认识省略 |
| `NotificationArchitectureTest` | 新类全部落在声明的子域与允许的包里；没有跨子域边 |

流程测试的底座照 interaction 的 `InteractionFlowITSupport`：真 PG（`NotificationITPostgreSQLContainerInitializer`）、真验签（`patra-tool-security` 的测试私钥）、identity 由 WireMock 经 `lb://` 扮演。

门禁：`./gradlew spotlessApply && ./gradlew check`，再显式跑 notification infra / boot、identity、网关的 `integrationTest`。

## 17. 上线步骤（并进上线手册第 7 步）

本 Issue 在合并前不需要动 mini：

- Flyway 随通知服务第一次启动建 `preference` schema 与表；
- 身份断言公钥已在 Nacos 公共配置 `patra-common.yaml`，identity 分组超时写在 `application.yml`，不改 Nacos；
- identity 只多一个内部接口，随 identity 的 CD 部署。

合并后核对（编排会话按手册做，D5 的完整验收要等 PAP-146 发出真邮件、PAP-148 的页面上线）：

1. notification 库里有 `preference.email_preference`：`\dt preference.*`。
2. 经网关、带登录令牌 `GET /patra-notification/me/notification-preferences` → `{"reply":"INSTANT","mention":"INSTANT"}`；不带令牌 → 401。
3. 带令牌 `PUT …/me/notification-preferences/MENTION`、`{"mode":"DIGEST"}` → 200，库里这个人有一行、令牌 43 位。
4. 用库里的令牌：`GET …/unsubscribe?token=…&category=DIGEST` → 200、`alreadyUnsubscribed:false`，档位不变；不带登录令牌 `curl -X POST -d 'List-Unsubscribe=One-Click' '…/unsubscribe/one-click?token=…&category=DIGEST'` → 200，「@ 我的」变成 `OFF`；再发一次仍 200。
5. 令牌改错一位 → 404，`traits` 含 `UNSUBSCRIBE_LINK_INVALID`。
6. 验完把这个测试账号的档位改回 `INSTANT`。
7. 调度中心在执行器 `patra-notification` 下登记 `deletedUserCleanupJob`：每天 06:30（`0 30 6 * * ?`，错开 interaction 的 06:00）、路由「第一个」、阻塞处理「单机串行」、超时 600 秒、不重试；手动执行一次，日志有 `注销清理完成`。

## 18. 与并行 Issue 的交接

| Issue | 交接内容 |
|---|---|
| PAP-146 | 第 7 节链接格式与邮件头；第 9 节读端口（`subscriptionOf`、`EmailSubscription.modeOf`）；第 10.1 节 `RecipientPort` 与第 13 节的 identity 接口直接用，To-Do 第一条已由本 Issue 完成；显示名在 identity 里已按用户名兜底；声明 mail → preference 的边 |
| PAP-148 | 第 8 节接口；8.6 门户转发（尤其一键退订的 POST 转发、不带会话、不重定向）；读失败没有设计状态 |
| PAP-143 | kernel 的 `NotificationCategory`（含 `HELPFUL`）、`RecipientPort`、`UserDataCleanupGateway` 已建，直接用、别另建。骨架 spec 5.2 给 inbox 规划的「按 user_id 取用户名的端口」：若只要名字可直接用 `RecipientPort`（它带用户名与显示名）；要按访客算主页可见性（`profileVisible`、头像）就另走 `summaries`，那是另一个端口。底座清理流程已建（第 14 节），只需给 inbox 写一份 `UserDataCleanupGateway` 实现；notification boot 已接安全 starter 与 identity 客户端 |
| PAP-104（identity 并行会话） | identity 的 `UserEndpoint`、`UserEndpointImpl`、`UserReadPort` 与读适配器多了一个方法，汇入时可能要合并 |

会冲突的文件（编排会话汇入时处理）：notification 各层 `build.gradle.kts`（PAP-143 也会加层间依赖）、boot 的 `application.yml`、`NotificationArchitectureTest`、`patra-infra/cd/module-graph.json`（以重跑 `./gradlew dumpModuleGraph` 为准）。

## 19. 我的决定（请重点看）

1. 令牌：每人一个、32 字节随机、存原文、不过期、不换（第 6 节）。不用签名密钥。
2. 两个退订地址都在门户：确认页 `/unsubscribe#token&category`（令牌放片段，沿用 `TokenLandingPage`）；一键退订 `/unsubscribe/one-click?token&category`（`POST` 转发到网关，`GET` 跳确认页）（第 7 节）。
3. 后端三条退订接口：落地页读（`GET /unsubscribe`）、落地页写（`POST /unsubscribe`，JSON）、一键退订（`POST /unsubscribe/one-click`，不检查请求体）（第 8 节）。
4. 偏好接口按类单独 `PUT`，返回两类当前档位；`HELPFUL` 422（第 8.1 节）。
5. `REPLY` / `MENTION` 退订不看原档位直接关；`DIGEST` 只关处于汇总的；「已退订过」「其余还开着」由后端算（第 5 节）。
6. 链接无效一律 404 + `UNSUBSCRIBE_LINK_INVALID`；读接口回查 identity，已注销算无效，封禁与冷静期照常有效；写接口不调 identity（第 5.4 节）。
7. 给 mail 的读端口在没有行时建行（第 9 节）。
8. 收件信息端口进 kernel，偏离骨架 spec 5.2（第 10.1 节）。
9. identity 的收件信息接口由本 Issue 建，字段按 PAP-146 的 To-Do 再加显示名（第 13 节，10/31 定）。
10. 注销清理：扩展点、preference 的实现与底座流程都由本 Issue 建，查账号状态走 `/summaries`（第 14 节，10/31 定）。
11. 写用例用行锁 + 按列写回，不用乐观锁重试（第 12 节）。

## 20. 实施记录（2026-10-31）

### 20.1 与本设计、计划的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | kernel 的 `NotificationCategory` 多了 `hasEmail()`（「有帮助」为假） | 改档位的用例要在碰库前拒绝 `HELPFUL`；放在类别上，规则只写一处，mail 以后也用得上 |
| 2 | identity 的读模型拆成 `UserRecipientReadModel`（原始数据）+ `UserRecipientView`（对外形式，`view()` 产出），计划里写的是一个读模型的 `forOutput()` | 照 `UserSummaryReadModel` / `UserSummaryView` 的现有成对写法；原始数据里用户名非空，对外形式里已注销为空 |
| 3 | identity 新增 `UserIdBatch`（用户 ID 列表的校验与去重），只给批量收件信息用；`UserSummaryQueryService` 里同样的一份不动 | identity 只增不改（10/31 编排会话的要求），免得和并行改 identity 的 PAP-104 撞车；以后摘要那边可以改用它 |
| 4 | infra 集成测试：preference 用本包的启动类 `PreferencePersistenceITBootstrap`（带固定时钟）；根上的 `NotificationITBootstrap` 加了一个不会被调用的 `UserEndpoint` mock | 同 interaction：根启动类扫整个 infra，新的收件信息适配器要 identity 代理，代理在 boot 里装配 |
| 5 | boot 构建脚本删掉 domain、app 两行 `testImplementation`，api 那行保留 | 骨架 spec 的交接说明：domain、app 已经经 adapter、infra 传进来；api 还没有调用方 |
| 6 | 网关集成测试的新例只断言表单体「包含」`List-Unsubscribe=One-Click` | 实测发现网关按请求参数重新拼表单体，查询串里的参数被剔掉、只留下分隔符，下游收到 `&&List-Unsubscribe=One-Click`。空段按表单规则忽略，notification 也不看这个请求体，对退订无影响；别的服务以后要收经网关转发的表单 POST 时要留意 |
| 7 | 网关这一例没有经历红灯 | 它是守护测试：现有放行规则下本来就成立，守的是「以后别给 notification 路径加登录要求」 |
| 8 | kernel 多了 `AccountStatePort` / `AccountStateAdapter`（走 `/summaries`），第 10.1 节原先写「注销清理流程也用 `RecipientPort` 取状态」 | 10/31 定：清理只要状态，不拉邮箱 |
| 9 | 一键退订的令牌与类别改为只从查询串取（8.4），不用 `@RequestParam` | 独立评审发现：表单体里夹带同名参数时两个值会被拼起来，退订失败；补了流程测试先红后绿 |
| 10 | 读接口在 identity 给的邮箱为空时也算链接无效（5.4） | 没有地址可脱敏显示；独立评审建议写明 |

### 20.2 门禁

见第 16 节的测试清单；全量结果记在给编排会话的汇报里。
