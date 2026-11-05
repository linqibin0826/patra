# 门户人机验证：工程设计

> 对应 Issue：PAP-194、PAP-195。产品规则以 [v0.20 release spec](../release-specs/v0.20-human-verification.md) 为准，接口约定见 [identity 工程设计](2026-11-05-identity-human-verification-design.md) 第 3 节。这版没有新页面，本文同时是状态和文案的依据，由用户本地验收。

## 1. 目的

- 登录、注册、找回密码接入人机验证，厂商由 identity 决定，门户不用重新部署就能切换。
- 外网访问时取到浏览器的真实 IP，否则按 IP 触发在线上没有区分能力。

**不做**：加载失败兜底、验证国内加载情况、给其他页面加 CSP。

**失败的标准**：

- 正常用户登录时加载了第三方组件，或看到了验证。
- 提示把验证问题说成账号锁定或密码错误。
- 页面加载了 TRUSTTURN 域名下的脚本。
- 用户输对的密码因为要先过验证被清空。

## 2. 真实 IP（PAP-195）

`clientOriginFromHeaders`（`src/lib/client-origin.ts`）优先取 `cf-connecting-ip`：

```text
cf-connecting-ip 是单个合法 IP  → 用它
否则                            → 走现有的 x-forwarded-for 规则
```

`cf-connecting-ip` 含逗号或不是 IP 字面量时当作没有，规范化规则和现有的 `clientIp` 一样（IPv4 映射还原、去 zone）。Cloudflare 会覆盖访客带来的这个头，所以外网伪造不了；能直连 4000 端口的 tailnet 或局域网设备可以伪造，这些都是用户自己的设备。

`proxy.ts` 和 Server Action 都经这个函数，不用分别改。头能否经隧道完整到达，上线后实测：登录设备列表显示真实 IP。

## 3. 读取当前厂商

`src/lib/auth/human-verification.ts`（`server-only`）：

```ts
export interface HumanVerificationConfig {
  provider: "turnstile" | "trustturn";
  siteKey: string;
}
export async function readHumanVerification(): Promise<HumanVerificationConfig | null>
```

- 请求 `GET /patra-identity/auth/human-verification`，不缓存。响应形状不对、`siteKey` 为空、网关不可用都返回 `null`，只记日志，不让页面出错。
- `/login`、`/register`、`/forgot-password` 三个页面在服务端读取，作为 `verification` 属性传给表单。catch 里先 `unstable_rethrow`，否则吞掉动态渲染信号，只有 `pnpm build` 才暴露。
- 设置密码对话框（第 6 节）在打开时经 Server Action 读取，因为它所在的页面很多，不值得每个页面都读。

## 4. 组件

`src/components/auth/human-verification/`：

| 文件 | 职责 |
|---|---|
| `HumanVerificationWidget.tsx` | 按 `config.provider` 选实现；`config` 为 `null` 时显示不可用提示 |
| `TurnstileWidget.tsx` | 加载官方脚本，显式渲染 |
| `TrustTurnWidget.tsx` | 只用 iframe，绝不加载脚本 |
| `use-human-verification.ts` | 表单用的状态：当前令牌、是否显示、重置 |

共同的属性：

```ts
interface WidgetProps {
  siteKey: string;
  action: "login" | "register" | "password_reset";
  resetKey: number;              // 变了就重新验证
  onToken: (token: string) => void;
  onClear: () => void;           // 令牌过期或组件重置
  onLoadError: () => void;
}
```

**TurnstileWidget**

- 脚本 `https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit`，组件挂载时才注入，整个页面只注入一次。
- `turnstile.render` 传 `sitekey`、`action`、`language: "zh-cn"`、`callback`、`expired-callback`、`error-callback`。`resetKey` 变化时调 `turnstile.reset`，卸载时调 `turnstile.remove`。
- 脚本 `onerror`、`error-callback`，或 15 秒内没渲染完，都算加载失败。

**TrustTurnWidget**

- `<iframe src="https://api.trustturn.cc/widget.html?site_key=...&action=..." sandbox="allow-scripts allow-same-origin allow-forms" title="TRUSTTURN 人机验证">`，宽 100%，最小高度 88px。
- 监听 `message`：`event.origin` 严格等于 `https://api.trustturn.cc`，`event.source` 是这个 iframe，`data.type === "trustturn:verified"`，才取 `data.detail.verificationTicket`。
- 票据 60 秒过期，文档没有过期事件：拿到票据 55 秒后调 `onClear` 并重新加载 iframe。`resetKey` 变化时同样重新加载。
- iframe 加载失败浏览器不报错，所以 15 秒没收到票据时显示加载失败的提示，但仍然监听，之后验证成功了提示就消失。交互式验证可能确实慢，这是软提示。
- `sandbox` 下组件能否工作要实测。实测结果写进本文第 9 节。

## 5. 表单状态

### 5.1 登录

```text
默认                不显示组件，不加载第三方资源，提交不带令牌
  │ 后端 VERIFICATION_REQUIRED
显示组件            保留邮箱和密码，提示“请先完成下面的人机验证，再重新提交”
                    没拿到令牌时禁用提交按钮
  │ 拿到令牌，用户点登录
提交带令牌
  ├─ 成功           跳转
  ├─ 密码错         清空密码，提示密码错误，重置组件（令牌已用掉）
  ├─ VERIFICATION_FAILED      保留密码，提示验证没通过，重置组件
  ├─ 不可用         保留密码，提示暂时不可用，重置组件
  └─ 锁定           提示锁定（现有文案），重置组件
```

组件一旦显示，在这个表单的生命周期内一直显示。不自动提交：用户可能还想改密码。

`config` 为 `null` 时被要求验证：显示不可用提示，禁用提交。

“禁用提交”沿用表单现有的写法：按钮 `aria-disabled`，在 `onSubmit` 里拦截，键盘回车也走这里。被要求验证时不移动焦点，提示条已经是 `role="alert"`。

**厂商在页面打开后被切换**：旧页面拿旧厂商的令牌提交，后端返回 `VERIFICATION_FAILED`，刷新页面后恢复。切换很少发生，接受这个代价，只在文案里提示刷新，不在响应里带最新配置。

### 5.2 注册和找回密码

组件一直显示，没拿到令牌时禁用提交按钮。任何失败响应后都重置组件。字段错误（422）令牌其实没用掉，但也统一重置，不做区分。

找回密码发出后的“重新发送”也要令牌：倒计时结束后显示组件，拿到令牌后才显示重新发送按钮。

### 5.3 Server Action

`login`、`register`、`requestPasswordReset` 的入参增加可选的 `verificationToken`。结果增加：

| `reason` | 来自 |
|---|---|
| `verification-required` | 422，`errors` 含 `verificationToken` / `VERIFICATION_REQUIRED` |
| `verification-failed` | 422，`errors` 含 `verificationToken` / `VERIFICATION_FAILED` |
| `verification-unavailable` | 503，`traits` 含 `VERIFICATION_UNAVAILABLE` |

判断放在现有的字段错误处理之前：`verificationToken` 不在 `USER_FIELDS` 里，不判断的话会被当成门户的 bug 记日志并返回 `unavailable`。

### 5.4 文案（`src/lib/auth/messages.ts`）

| 状态 | 文案 |
|---|---|
| 要求验证 | 请先完成下面的人机验证，再重新提交 |
| 验证没通过 | 人机验证没有通过，请重新验证。一直不通过的话，刷新页面再试 |
| 服务不可用 | 人机验证暂时不可用，请稍后再试 |
| 组件加载失败 | 人机验证加载失败。请刷新页面，或换一个网络再试 |
| 取不到厂商配置 | 人机验证暂时不可用，请稍后再试 |

和锁定的“尝试次数过多”完全不同，用户能分清是验证问题。

## 6. 设置密码对话框

`SetPasswordDialog`（已登录但没有密码的用户）也调 `requestPasswordReset`，后端每次都要令牌，所以对话框里也放组件。每次打开时读一次厂商配置，读取期间发送按钮禁用；读到 `null` 时显示不可用提示并禁用发送。`action` 用 `password_reset`，规则同第 5.2 节。对话框里的组件要能在 `Dialog` 关闭时正确卸载。

## 7. CSP

只给 `/login`、`/register`、`/forgot-password` 加，写在 `next.config.ts` 的 `headers()`，只限制这两项，其他资源不动：

```text
script-src 'self' 'unsafe-inline' https://challenges.cloudflare.com
frame-src  https://challenges.cloudflare.com https://api.trustturn.cc
```

- `'unsafe-inline'`：Next 的水合脚本是内联的，用 nonce 要把这些页面全部变成动态渲染并改 proxy，不值得。目的是不放行 TRUSTTURN 的脚本，不是防 XSS。
- 开发模式加 `'unsafe-eval'`，否则 webpack 的 dev 脚本跑不了。
- **隐性约束**：CSP 随文档响应下发，登录或注册成功后是客户端导航，所以这批用户之后的页面都跑在这份 CSP 下，直接打开其他页面的用户没有。目前门户没有 `<iframe>`、`next/script`、`<object>`、`new Worker`，没有页面受影响。以后在这三个页面以外加第三方脚本或同源 iframe，要同时改这里，否则只有从登录页进来的用户会坏。
- **`SetPasswordDialog` 在设置页**，不在这三个页面，没有 CSP，这个对话框的隔离靠组件本身：TRUSTTURN 只用 iframe。这里不扩大范围。

## 8. 测试

单测（Vitest + Testing Library）：

- `client-origin`：有 `cf-connecting-ip`、没有、值不合法或含逗号、IPv4 映射。
- `human-verification`：正常、响应形状不对、`siteKey` 为空、网关不可用都不抛异常。
- `actions`：三种新 `reason` 的映射；令牌进请求体；`verificationToken` 的字段错误不记 unexpected 日志。
- `LoginForm`：默认不渲染组件；被要求验证后显示并保留密码；没令牌时按钮禁用；密码错后清空并重置；验证失败保留密码；文案和锁定不同。
- `RegisterForm`、`ForgotPasswordForm`、`SetPasswordDialog`：组件显示；没令牌禁用；失败后重置；重新发送要新令牌。
- `TrustTurnWidget`：`origin` 不对或 `source` 不对的消息被忽略；iframe 带 `sandbox`；页面没有它域名下的 `script`；55 秒后清令牌。
- `TurnstileWidget`：用假的 `window.turnstile`，覆盖渲染参数、`reset`、`remove`、脚本加载失败。
- `next.config`：三个路径有 CSP，`script-src` 不含 trustturn。

门控：门户的 `pnpm lint`、`pnpm test`、`pnpm build`（`--maxWorkers=2`，负载高时测试才稳）。

本机验收：连本机 gateway 和 identity，用 Turnstile 官方测试密钥走通三个页面，桌面和手机布局都看。

**Playwright**：

- 跑 e2e 时门户连本机 gateway 和 identity（环境变量配官方测试密钥）。本机 identity 读的是 mini 的库和 Redis，所以测试账号和数据不变。不能直连 mini 的网关：mini 上是绑定公网域名的真实密钥，真实组件在无头浏览器里也不稳定。
- 真实加载 always-pass 组件并等令牌，不用 `page.route` 替身，这样 CSP 和脚本加载也被测到。依赖本机能访问 `challenges.cloudflare.com`。
- `tests/e2e/support` 加通用辅助函数：组件出现时等令牌。所有 e2e 共用一个 IP，失败累计 5 次后登录也可能被要求验证，登录辅助函数要统一处理。
- `tests/e2e/social-login/mock-gateway.mjs` 要加 `GET /patra-identity/auth/human-verification`，返回官方测试 sitekey；它的注册和找回密码接口接受令牌但不校验。

## 9. 实测记录

上线实测后补：`sandbox` 下 TRUSTTURN 组件能否工作、票据消息的 `event.source` 是不是 `widget.html` 这个窗口（如果由它内部的子 iframe 发出，现在的校验会拦掉）、它实际发出的请求和采集的内容、错误响应格式。

## 10. 留给实施者决定

- 组件的视觉细节：放在提交按钮上方，和表单同宽，Turnstile 用 `size: "flexible"`。
- `use-human-verification` 的具体形状。
- 15 秒、55 秒的常量放哪。
