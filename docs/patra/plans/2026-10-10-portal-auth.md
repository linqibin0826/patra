# 门户认证基础设施 实施计划（PAP-67）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 门户把会话令牌放进 HttpOnly Cookie，服务端调网关时换成 `Authorization` 头；提供注册、登录、登出三个 Server Action 和读当前用户的入口，会话失效时清掉 Cookie；所有网关调用收拢到一个客户端。

**Architecture:** `src/lib/gateway.ts` 的 `gatewayFetch` 是全仓库唯一读网关地址、向网关发请求的地方，没传令牌就不带 `Authorization`。`src/lib/auth/session-cookie.ts` 是唯一调用 `cookies()` 的地方，管 Cookie 的名称、属性和 `Secure` 开关。`lookupSession()` 把网关的回答归成四种结果，`GET /api/auth/me` 据此刷新或清除 Cookie，导航栏用的 `useCurrentUser()` 在浏览器里读这个接口。Server Action 只返回结果对象，不带文案、不跳转。守护测试扫描 `src/`，防止以后有代码绕开这两个模块。

**Tech Stack:** Next.js 16.3.8（App Router、Route Handler、Server Action、`proxy.ts`）、React 19.3.0、TypeScript 6.0.3 strict、TanStack Query 5.103.2、Zod 4.6.5、Vitest 4.1.11 + Testing Library、Biome 2.5.14、Playwright 1.63.0。

**Spec:** `docs/patra/specs/2026-10-10-portal-auth-design.md`

## Global Constraints

- 网关地址只在 `src/lib/gateway.ts` 读，缺失时抛 `Error("PATRA_GATEWAY_BASE_URL 未配置")`；固定 `cache: "no-store"`，默认超时 `10_000` 毫秒（spec 第 6 节）。
- `cookies()` 只在 `src/lib/auth/session-cookie.ts` 调用。Cookie 名：`Secure` 开时是 `__Host-patra_session`，关时是 `patra_session`；`HttpOnly`、`SameSite=lax`、`Path=/`、不设 `Domain`、`Max-Age=2592000`。清除时写空值，属性和写入时相同，`Max-Age=0`，不用 `cookies().delete()`（spec 第 7 节）。
- 开关 `PATRA_SESSION_COOKIE_SECURE`：不设或为 `true` 时开，为 `false` 时关，其他值抛 `Error("PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false")`；每次读写 Cookie 时读取（spec 第 7 节）。
- 只有 `/api/auth/me` 读到用户时刷新 Cookie；只有它收到 401 时和登出时清除；503、超时、网络错误一律不清（spec 第 7、10 节）。
- `/api/auth/me` 的所有响应带 `Cache-Control: private, no-store`，并声明 `export const dynamic = "force-dynamic"`（spec 第 8.2 节）。
- Server Action 的结果不带中文文案，自己也不跳转；注册、登录发给网关的请求体只有 `email` 和 `password`，不带 `Authorization`、`clientType`、`deviceId`（spec 第 9 节）。
- 令牌和密码不进日志，也不进抛出的错误消息；日志只记接口、状态码和 ProblemDetail 的 `code`（spec 第 12 节）。
- `gateway.ts`、`session-cookie.ts`、`gateway-response.ts`、`current-user.ts` 第一行是 `import "server-only";`；`actions.ts` 第一行是 `"use server";`，只导出 async 函数。
- 测试放在 `patra-portal/tests/` 下。测试服务端模块的文件第一行是 `// @vitest-environment node`；`useCurrentUser()` 的测试和只扫描源码的守护测试用默认的 jsdom。`next/headers` 一律用 `vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))` 换掉。现有的 fetcher 测试和 `tests/proxy.test.ts` 一行不改。
- 代码风格：注释用 `///`，内容是 Markdown；路径别名用 `@/`、`@tests/`，不写 `../../`；Biome 是唯一的格式权威，写完文件跑一次 `pnpm exec biome check --write <文件>`。计划里的代码已经是格式化之后的样子，正常应输出 `No fixes applied`。
- 命令一律写绝对路径：portal 用 `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal …`，git 用 `git -C /Users/linqibin/Projects/Products/patra …`，不用 `cd`。
- 合格线：`pnpm lint && pnpm typecheck && pnpm test`（spec 第 13 节）。
- 提交：`type(scope): 中文开头的主题 (PAP-67)`，主题不以英文单词开头；正文每行不超过 100 个字符；末尾是 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。提交前看 `git diff --cached --stat`，只提交本任务的路径。不推送。

## Review Focus

spec 没有逐条写到、各任务原有的用例也没覆盖，但最可能让人碰上的五种情况。每条都在负责的任务里加了用例或实测步骤：

1. 浏览器里的 Cookie 值坏了（被截断、被手改）：网关按匿名处理，`/auth/me` 回 401，门户清掉 Cookie，之后不再带着坏值去问网关 → Task 10 实测第 7 步「坏值」。
2. `/api/auth/me` 回了不是 JSON 的 200（比如门户前面的代理换成了错误页）：导航栏显示未登录，不崩 → Task 6 `回了不是 JSON 的 200（比如前面的代理换成了错误页）：anonymous`。
3. 已经登录时，在同一个浏览器里换个账号再登录：新令牌覆盖 Cookie，不先去登出旧会话 → Task 7 `已经登录时再登录：新令牌覆盖 Cookie，不去作废旧会话`。
4. PAP-68 把 Server Action 直接挂在 `<form action>` 上，收到的是 FormData：返回 `unavailable`、不请求网关，不会发出一个缺字段的请求 → Task 7 `入参是 FormData：unavailable，不请求网关`。
5. 以后上了 HTTPS、打开 `Secure` 之后，浏览器里还留着旧的 `patra_session`：门户不再读它，这些人重新登录一次 → Task 3 `Secure 开时不读不带前缀的旧 Cookie：切到 HTTPS 之后要重新登录`。

## 写计划时已实测的点（2026-10-10）

在 portal 的临时副本（`node_modules` 链接到真实目录）里按本计划的顺序把 Task 1 到 8 全部跑过一遍，计划里的代码和预期输出都来自那次运行：

- 改造前的代码跑守护测试：「网关地址」命中 17 行（9 处读取，加上 8 处报错文字），「直接 fetch」命中 10 处；`cookies()`、`Authorization` 两条规则和 11 个自检用例通过。
- `vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))` 之后，测试文件自己 `import { resetCookieJar } from "@tests/stubs/cookie-jar"` 拿到的是同一个模块实例。
- Next 的 `ResponseCookies` 序列化出来的 Set-Cookie：写入时是 `patra_session=<令牌>; Path=/; Expires=<日期>; Max-Age=2592000; HttpOnly; SameSite=lax`（`Secure` 开时名字带 `__Host-`，`HttpOnly` 前面多一个 `Secure`）；清除时是 `Max-Age=0`，不带 `Expires`。
- Playwright 1.63.0 把 `webServer.env` 合并到 `process.env` 之上，不会冲掉 `PATH` 这类变量（`playwright/lib/runner/index.js` 第 873 到 876 行）。
- 每个任务做完时的测试文件数和用例数：基线 88 / 675；Task 1 后 89 / 684，Task 2 后 90 / 699，Task 3 后 91 / 714，Task 4 后 92 / 732，Task 5 后 93 / 743，Task 6 后 94 / 750，Task 7 后 95 / 793，Task 8 后 95 / 798。中途几次和最后都跑过 `pnpm lint`、`pnpm typecheck`，全部通过。
- 仓库 pre-commit 用的 gitleaks（v8.21.2）扫过全部新文件，测试里的假令牌、假密码不会被拦。
- Task 10 的实测脚本在本机的 bash 3.2 下检查过语法，状态码和响应体的拆分、令牌遮罩都离线跑过。

## 与 spec 的出入

- spec 第 13 节说守护测试最先写。计划把它放在 Task 2 的开头：Task 1 先做出 `gatewayFetch`，Task 2 写守护测试、确认它是红的，再迁移调用点。守护测试仍然先于迁移，而每个任务结束时全量测试都是绿的。
- spec 第 5 节的文件表里没有 `src/lib/auth/gateway-response.ts`。`current-user.ts` 和 `actions.ts` 都要读响应体、按同一格式记日志，所以抽出这个小模块（`isRecord`、`readJson`、`logUnexpectedResponse`、`logRequestError`），依赖方向不变。
- `types.ts` 比 spec 第 5 节多两个类型：`CurrentUserPayload`（`/api/auth/me` 的响应体，route 和 hook 共用）、`Credentials`（`register`、`login` 的入参）。
- spec 第 16 节只改 D3 和简报 §0。release spec 第 105 行（会话「30 天不活跃过期」）和简报第 214 行（会话失效的「三种来源」）讲的是同一件事，Task 9 一起补上 180 天的上限，免得文档之间又对不上。
- spec 第 14 节第 4、5 步只写了一种 `Secure` 取值。Task 10 两种取值都走一遍「登出后清除」和「不带 Cookie」，确认 `__Host-` 开头的 Cookie 清除时也带 `Secure`；另外加上 Review Focus 第 1 条的「坏值」一步。实测结果按仓库惯例写回 spec 第 14 节。
- spec 第 14 节第 3 步是「不设开关重启门户」。Task 10 改为在启动配置里显式写 `PATRA_SESSION_COOKIE_SECURE=true`：本机 `.env.local` 里可能写着 `false`，进程环境变量优先于 `.env.local`，显式写才能保证开关是开的。「不设时默认开」由 Task 3 的单元测试覆盖。

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `patra-portal/src/lib/gateway.ts` | 统一网关客户端 `gatewayFetch` | 1 |
| `patra-portal/tests/lib/gateway.test.ts` | 网关客户端的单元测试 | 1 |
| `patra-portal/tests/lib/gateway-boundary.test.ts` | 守护测试：网关地址、直接 fetch、`cookies()`、`Authorization` 的边界 | 2 |
| `patra-portal/src/lib/portal-api/publications.ts`、`publication-search.ts`、`venues.ts`，`patra-portal/src/proxy.ts` | 10 处网关调用改走 `gatewayFetch` | 2 |
| `patra-portal/tests/stubs/cookie-jar.ts` | 测试用的 Cookie 存储，替换 `next/headers` | 3 |
| `patra-portal/src/lib/auth/session-cookie.ts` | 会话 Cookie 的名称、属性、`Secure` 开关和读写 | 3 |
| `patra-portal/tests/lib/auth/session-cookie.test.ts` | 单元测试 | 3 |
| `patra-portal/.env.example`、`patra-portal/playwright.config.ts`、`patra-infra/docker/.env.portal`，本机 `.claude/launch.json` | `Secure` 开关的取值 | 3 |
| `patra-portal/src/lib/auth/types.ts` | 浏览器和服务端共用的类型 | 4 建，7 补 |
| `patra-portal/src/lib/auth/gateway-response.ts` | 读响应体、记日志的小工具 | 4 |
| `patra-portal/src/lib/auth/current-user.ts` | `lookupSession()`、`getCurrentUser()` | 4 |
| `patra-portal/tests/lib/auth/current-user.test.ts` | 单元测试 | 4 |
| `patra-portal/src/app/api/auth/me/route.ts` | `GET /api/auth/me` | 5 |
| `patra-portal/tests/app/api/auth-me.test.ts` | 单元测试 | 5 |
| `patra-portal/src/lib/query-keys.ts` | 加 `currentUser` | 6 |
| `patra-portal/src/lib/auth/current-user-query.ts` | `useCurrentUser()` | 6 |
| `patra-portal/tests/lib/auth/current-user-query.test.tsx` | 单元测试 | 6 |
| `patra-portal/src/lib/auth/actions.ts` | `register`、`login`（7），`logout`（8） | 7、8 |
| `patra-portal/tests/lib/auth/actions.test.ts` | 单元测试 | 7、8 |
| `patra-portal/README.md`、`docs/patra/release-specs/v0.8-accounts.md`、`patra-portal/docs/design-briefs/v0.8/accounts.md` | 文档同步 | 9 |
| `docs/patra/specs/2026-10-10-portal-auth-design.md` 第 14 节、Linear PAP-67 | 实测结果、Issue | 10 |

## 执行顺序

按 Task 1 到 10 的顺序做。Task 2 用到 Task 1 的 `gatewayFetch`；Task 4 用到 Task 1、3；Task 5 用到 Task 3、4；Task 6 用到 Task 4 的类型；Task 7、8 用到 Task 1、3、4；Task 9 描述的是做完之后的目录；Task 10 要在本机把门户跑起来。

---

### Task 1: 统一网关客户端 `gatewayFetch`

**Files:**
- Create: `patra-portal/src/lib/gateway.ts`
- Test: `patra-portal/tests/lib/gateway.test.ts`

**Interfaces:**
- Consumes: 无。
- Produces: `gatewayFetch(path: string, init?: GatewayRequestInit): Promise<Response>`；`GatewayRequestInit` 是 `{ method?: "GET" | "HEAD" | "POST"; json?: unknown; token?: string; timeoutMs?: number }`。之后所有请求网关的代码都只调用它：Task 2 的 10 处调用点、Task 4 的 `lookupSession()`、Task 7 和 8 的 Server Action。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/gateway.test.ts`：

```ts
// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { gatewayFetch } from "@/lib/gateway";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// 第 n 次调用 fetch 时传入的 [url, init]
const call = (n = 0) => fetchMock.mock.calls[n] ?? [];

describe("gatewayFetch", () => {
  it("在网关地址后拼上路径，默认 GET，不缓存", async () => {
    await gatewayFetch("/patra-catalog/portal/publications/42");
    const [url, init] = call();
    expect(url).toBe("http://gw.test:9528/patra-catalog/portal/publications/42");
    expect(init).toMatchObject({ method: "GET", cache: "no-store" });
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it("超时默认 10 秒，可以按调用覆盖", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    await gatewayFetch("/a");
    await gatewayFetch("/b", { method: "HEAD", timeoutMs: 3_000 });
    expect(timeout.mock.calls).toEqual([[10_000], [3_000]]);
    expect(call(1)[1]?.method).toBe("HEAD");
  });

  it("有 json 时序列化成请求体，并带 Content-Type", async () => {
    await gatewayFetch("/patra-identity/auth/login", {
      method: "POST",
      json: { email: "reader@example.com", password: "secret-pass" },
    });
    const [, init] = call();
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe('{"email":"reader@example.com","password":"secret-pass"}');
    expect(init?.headers).toEqual({ "Content-Type": "application/json" });
  });

  it("有令牌时带 Authorization: Bearer", async () => {
    await gatewayFetch("/patra-identity/auth/me", { token: "patra_user_abc" });
    expect(call()[1]?.headers).toEqual({ Authorization: "Bearer patra_user_abc" });
  });

  it("没有令牌也没有 json 时，不带请求头和请求体", async () => {
    await gatewayFetch("/patra-catalog/portal/venues");
    const [, init] = call();
    expect(init?.headers).toEqual({});
    expect(init).not.toHaveProperty("body");
  });

  it("空字符串令牌按没有令牌处理", async () => {
    await gatewayFetch("/patra-identity/auth/me", { token: "" });
    expect(call()[1]?.headers).toEqual({});
  });

  it("原样返回网关的响应，不解读状态码", async () => {
    const res = new Response("{}", { status: 503 });
    fetchMock.mockResolvedValue(res);
    await expect(gatewayFetch("/x")).resolves.toBe(res);
  });

  it("缺网关地址时报错，不发请求", async () => {
    vi.stubEnv("PATRA_GATEWAY_BASE_URL", undefined);
    await expect(gatewayFetch("/x")).rejects.toThrow("PATRA_GATEWAY_BASE_URL 未配置");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("网络错误照常抛出", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(gatewayFetch("/x")).rejects.toThrow("fetch failed");
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/gateway.test.ts`
Expected: FAIL，`Error: Cannot find package '@/lib/gateway' imported from …/tests/lib/gateway.test.ts`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/lib/gateway.ts`：

```ts
import "server-only";

/// 默认超时：慢后端不应无限阻塞服务端渲染
const DEFAULT_TIMEOUT_MS = 10_000;

export interface GatewayRequestInit {
  /// 默认 GET
  method?: "GET" | "HEAD" | "POST";
  /// 有值时序列化成 JSON 请求体，并带 `Content-Type: application/json`
  json?: unknown;
  /// 会话令牌，有值时写成 `Authorization: Bearer <token>`
  token?: string;
  /// 超时毫秒数，默认 10_000
  timeoutMs?: number;
}

/// 统一网关客户端（server-only）：全仓库只有这里读 `PATRA_GATEWAY_BASE_URL`、向网关发请求。
/// 只负责把请求发出去：原样返回网关的响应，不解读状态码；网络错误和超时照常抛出。
/// 没传令牌就不带 `Authorization`，所以正文请求不会带上用户的令牌。
export async function gatewayFetch(path: string, init: GatewayRequestInit = {}): Promise<Response> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const headers: Record<string, string> = {};
  if (init.token) {
    headers.Authorization = `Bearer ${init.token}`;
  }
  if (init.json !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  return fetch(`${baseUrl}${path}`, {
    method: init.method ?? "GET",
    headers,
    cache: "no-store",
    signal: AbortSignal.timeout(init.timeoutMs ?? DEFAULT_TIMEOUT_MS),
    ...(init.json === undefined ? {} : { body: JSON.stringify(init.json) }),
  });
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  9 passed (9)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/gateway.ts tests/lib/gateway.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  89 passed (89)`、`Tests  684 passed (684)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/gateway.ts patra-portal/tests/lib/gateway.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 新增统一网关客户端 gatewayFetch (PAP-67)

全仓库只有它读网关地址、向网关发请求：固定 no-store 和超时（默认 10 秒），有令牌时才写
Authorization，原样返回网关的响应。现有的调用点在下一个提交里迁移过来。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 的各项检查（gitleaks、commitlint 等）都是 Passed 或 Skipped。

---

### Task 2: 守护测试，10 处网关调用改走 `gatewayFetch`

**Files:**
- Test: `patra-portal/tests/lib/gateway-boundary.test.ts`
- Modify: `patra-portal/src/lib/portal-api/publications.ts`（`fetchFeed`、`fetchPublicationDetail`）
- Modify: `patra-portal/src/lib/portal-api/publication-search.ts`（`fetchPublicationSearch`、`facetsByQueryString`）
- Modify: `patra-portal/src/lib/portal-api/venues.ts`（`fetchVenues`、`fetchVenuesPage`、`fetchVenuesFacets`、`fetchVenueDetail`、`fetchVenueSuggestions`）
- Modify: `patra-portal/src/proxy.ts`（详情页的 HEAD 检查）

**Interfaces:**
- Consumes: Task 1 的 `gatewayFetch`；现有 `tests/design-system/source-scan.ts` 的 `findAll(pattern: RegExp, files = sourceFiles()): Hit[]`（`Hit` 有 `file`、`line`、`match`）和 `format(hits: Hit[]): string[]`。
- Produces: 守护测试的四条规则，之后的任务都受它约束：只有 `src/lib/gateway.ts` 能读网关地址、直接 `fetch`、写 `Authorization`；只有 `src/lib/auth/session-cookie.ts`（Task 3）能调用 `cookies()`。浏览器请求门户自己的接口（`fetch("/api/…")`）不算直接 fetch。

- [ ] **Step 1: 写守护测试**

新建 `patra-portal/tests/lib/gateway-boundary.test.ts`：

```ts
import { findAll, format } from "@tests/design-system/source-scan";
import { describe, expect, it } from "vitest";

const GATEWAY_CLIENT = "src/lib/gateway.ts";
const SESSION_COOKIE = "src/lib/auth/session-cookie.ts";

/// 读网关地址
const GATEWAY_ADDRESS = /PATRA_GATEWAY_BASE_URL/;
/// 直接 fetch。第一个参数是以 / 开头的字符串或模板字面量时除外：那是浏览器请求门户自己的接口
const DIRECT_FETCH = /\bfetch\((?!\s*["'`]\/)/;
/// 调用 next/headers 的 cookies()
const COOKIES_CALL = /\bcookies\(\)/;
/// 手写 Authorization 头或 Bearer 前缀
const AUTHORIZATION_HEADER = /["'`](?:Authorization["'`]|Bearer\s)/;

/// src 下除 allowed 以外命中 pattern 的位置，格式是 `文件:行 片段`
const hitsOutside = (allowed: string, pattern: RegExp): string[] =>
  format(findAll(pattern).filter((hit) => hit.file !== allowed));

describe("网关调用和会话 Cookie 的边界", () => {
  it("网关地址只在统一网关客户端里读取", () => {
    expect(hitsOutside(GATEWAY_CLIENT, GATEWAY_ADDRESS)).toEqual([]);
  });

  it("只有统一网关客户端直接 fetch，浏览器请求门户自己的接口除外", () => {
    expect(hitsOutside(GATEWAY_CLIENT, DIRECT_FETCH)).toEqual([]);
  });

  it("只有会话 Cookie 模块调用 cookies()", () => {
    expect(hitsOutside(SESSION_COOKIE, COOKIES_CALL)).toEqual([]);
  });

  it("只有统一网关客户端写 Authorization 头", () => {
    expect(hitsOutside(GATEWAY_CLIENT, AUTHORIZATION_HEADER)).toEqual([]);
  });
});

describe("守护规则本身", () => {
  it.each([
    [DIRECT_FETCH, "const res = await fetch(url, {"],
    [DIRECT_FETCH, "const res = await fetch(`http://gw.test/patra-catalog/portal/venues`);"],
    [DIRECT_FETCH, "const status = await fetch("],
    [COOKIES_CALL, "const store = await cookies();"],
    [AUTHORIZATION_HEADER, 'const headers = { "Authorization": token };'],
    [AUTHORIZATION_HEADER, "const headers = { Authorization: `Bearer abc` };"],
  ])("%s 能抓到：%s", (pattern, line) => {
    expect(pattern.test(line)).toBe(true);
  });

  it.each([
    [DIRECT_FETCH, 'const res = await fetch("/api/auth/me");'],
    [DIRECT_FETCH, "const res = await fetch(`/api/venues/suggest?q=lan`);"],
    [DIRECT_FETCH, "return gatewayFetch(path);"],
    [DIRECT_FETCH, "router.prefetch(href);"],
    [COOKIES_CALL, "const value = request.cookies.get(name);"],
  ])("%s 放过：%s", (pattern, line) => {
    expect(pattern.test(line)).toBe(false);
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/gateway-boundary.test.ts`
Expected: FAIL，`Tests  2 failed | 13 passed (15)`：
- 「网关地址只在统一网关客户端里读取」列出 17 行：`publication-search.ts` 第 20、22 行，`publications.ts` 第 15、17、43、45 行，`venues.ts` 第 33、35、72、74、101、103、142、144、165、167 行，`proxy.ts` 第 28 行。
- 「只有统一网关客户端直接 fetch，浏览器请求门户自己的接口除外」列出 10 处：`publication-search.ts:33`、`:55`，`publications.ts:20`、`:48`，`venues.ts:38`、`:77`、`:106`、`:147`、`:170`，`proxy.ts:27`。
- `cookies()`、`Authorization` 两条规则和「守护规则本身」的 11 个用例通过。

- [ ] **Step 3: 10 处调用点改走 `gatewayFetch`**

每处只换掉「读地址、判空、拼 URL、`fetch`」这几行，状态码处理、报错文字和 React `cache()` 包装原样保留。三个 fetcher 文件里的 `FETCH_TIMEOUT_MS` 连同它的注释删掉（超时交给 `gatewayFetch` 的默认 10 秒），`publication-search.ts` 的 `gatewayBaseUrl()` 删掉；`proxy.ts` 保留 3 秒的 `CHECK_TIMEOUT_MS`，请求失败（包括缺地址时抛出的错误）照旧得到 `null`、直接放行。改动如下，可以存成文件后在仓库根目录用 `git apply` 应用：

```diff
diff --git a/patra-portal/src/lib/portal-api/publication-search.ts b/patra-portal/src/lib/portal-api/publication-search.ts
--- a/patra-portal/src/lib/portal-api/publication-search.ts
+++ b/patra-portal/src/lib/portal-api/publication-search.ts
@@ -1,5 +1,6 @@
 import "server-only";
 import { cache } from "react";
+import { gatewayFetch } from "@/lib/gateway";
 import {
   buildFacetsApiQuery,
   buildSearchApiQuery,
@@ -13,27 +14,14 @@ import type {
   PublicationFacets,
 } from "@/types/portal";

-/** 服务端 fetch 超时（ms）：慢后端不应无限阻塞 RSC 渲染 */
-const FETCH_TIMEOUT_MS = 10_000;
-
-function gatewayBaseUrl(): string {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  return baseUrl;
-}
-
 /**
  * 服务端检索文献（`GET /portal/publications/search`）。仅 RSC 调用（server-only）。
  * 非 2xx 直接 throw 冒泡到全局 error.tsx；响应缺关键字段时降级为空页。
  */
 export async function fetchPublicationSearch(query: PaperSearchQuery): Promise<PageResult<Paper>> {
-  const url = `${gatewayBaseUrl()}/patra-catalog/portal/publications/search?${buildSearchApiQuery(query)}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(
+    `/patra-catalog/portal/publications/search?${buildSearchApiQuery(query)}`,
+  );
   if (!res.ok) {
     throw new Error(`文献检索失败：${res.status}`);
   }
@@ -51,11 +39,9 @@ const safeNumber = (value: unknown): number => (typeof value === "number" ? valu

 /** 以 facets querystring 为缓存键：同一次渲染里信息行与筛选栏共用一次请求。 */
 const facetsByQueryString = cache(async (qs: string): Promise<PublicationFacets> => {
-  const url = `${gatewayBaseUrl()}/patra-catalog/portal/publications/search/facets${qs ? `?${qs}` : ""}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(
+    `/patra-catalog/portal/publications/search/facets${qs ? `?${qs}` : ""}`,
+  );
   if (!res.ok) {
     throw new Error(`文献 facet 加载失败：${res.status}`);
   }
diff --git a/patra-portal/src/lib/portal-api/publications.ts b/patra-portal/src/lib/portal-api/publications.ts
--- a/patra-portal/src/lib/portal-api/publications.ts
+++ b/patra-portal/src/lib/portal-api/publications.ts
@@ -1,9 +1,7 @@
 import "server-only";
+import { gatewayFetch } from "@/lib/gateway";
 import type { FeedTab, PageResult, Paper, PaperDetail } from "@/types/portal";

-/** 服务端 fetch 超时（ms）：慢后端（如 DB 锁等待）不应无限阻塞 RSC 渲染线程 */
-const FETCH_TIMEOUT_MS = 10_000;
-
 /**
  * 在服务端拉取 portal 文献流。仅 RSC / Route Handler 调用（server-only）。
  * 通过 gateway 访问 catalog，gateway 地址只在服务端可见。
@@ -12,15 +10,9 @@ const FETCH_TIMEOUT_MS = 10_000;
  * TODO(contract-validation)：API 契约稳定后可用 zod safeParse 替换以防后端字段漂移。
  */
 export async function fetchFeed(tab: FeedTab, page = 1, pageSize = 14): Promise<PageResult<Paper>> {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/publications?tab=${tab}&page=${page}&pageSize=${pageSize}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(
+    `/patra-catalog/portal/publications?tab=${tab}&page=${page}&pageSize=${pageSize}`,
+  );
   if (!res.ok) {
     throw new Error(`文献流加载失败：${res.status}`);
   }
@@ -40,15 +32,7 @@ export async function fetchPublicationDetail(id: string): Promise<PaperDetail |
   if (!/^[1-9]\d*$/.test(id)) {
     return null;
   }
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/publications/${id}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(`/patra-catalog/portal/publications/${id}`);
   // 404 不存在；400 / 422 是 BE 拒绝了 id（如超出范围），对读者同样是「没有这一页」
   if (res.status === 404 || res.status === 400 || res.status === 422) {
     return null;
diff --git a/patra-portal/src/lib/portal-api/venues.ts b/patra-portal/src/lib/portal-api/venues.ts
--- a/patra-portal/src/lib/portal-api/venues.ts
+++ b/patra-portal/src/lib/portal-api/venues.ts
@@ -1,5 +1,6 @@
 import "server-only";
 import { cache } from "react";
+import { gatewayFetch } from "@/lib/gateway";
 import {
   buildVenuesFacetsApiQuery,
   buildVenuesPageApiQuery,
@@ -16,9 +17,6 @@ import type {
   VenueSuggestion,
 } from "@/types/portal";

-/** 服务端 fetch 超时（ms）：慢后端不应无限阻塞 RSC 渲染线程 */
-const FETCH_TIMEOUT_MS = 10_000;
-
 /**
  * 在服务端拉取 portal 期刊榜（按影响因子降序，取前 pageSize 条）。
  * 仅 RSC / Route Handler 调用（server-only）。
@@ -30,15 +28,9 @@ const FETCH_TIMEOUT_MS = 10_000;
  * 响应做最小运行时校验：若 payload 缺少 `items` 数组则降级返回空列表，避免调用方 `.length` 抛错。
  */
 export async function fetchVenues(pageSize = 6): Promise<VenueBrowse[]> {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/venues?sort=impactFactor&pageSize=${pageSize}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(
+    `/patra-catalog/portal/venues?sort=impactFactor&pageSize=${pageSize}`,
+  );
   if (!res.ok) {
     throw new Error(`期刊榜加载失败：${res.status}`);
   }
@@ -69,15 +61,7 @@ interface VenueFacetsApiResponse {
  * 列表恒 200（空集也是 200），非 2xx 直接 throw 冒泡到 error.tsx。
  */
 export async function fetchVenuesPage(query: VenueBrowseQuery): Promise<VenueBrowsePage> {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/venues?${buildVenuesPageApiQuery(query)}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(`/patra-catalog/portal/venues?${buildVenuesPageApiQuery(query)}`);
   if (!res.ok) {
     throw new Error(`期刊列表加载失败：${res.status}`);
   }
@@ -98,15 +82,9 @@ export async function fetchVenuesPage(query: VenueBrowseQuery): Promise<VenueBro
  * 非 2xx 直接 throw 冒泡到 error.tsx。
  */
 export async function fetchVenuesFacets(filters: VenueBrowseFilters): Promise<VenueBrowseFacets> {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/venues/facets?${buildVenuesFacetsApiQuery(filters)}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(
+    `/patra-catalog/portal/venues/facets?${buildVenuesFacetsApiQuery(filters)}`,
+  );
   if (!res.ok) {
     throw new Error(`期刊 facet 加载失败：${res.status}`);
   }
@@ -139,15 +117,7 @@ export async function fetchVenueDetail(id: string): Promise<VenueDetail | null>
   if (!/^[1-9]\d*$/.test(id)) {
     return null;
   }
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
-  const url = `${baseUrl}/patra-catalog/portal/venues/${id}`;
-  const res = await fetch(url, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(`/patra-catalog/portal/venues/${id}`);
   if (res.status === 404 || res.status === 400 || res.status === 422) {
     return null;
   }
@@ -162,15 +132,8 @@ export async function fetchVenueDetail(id: string): Promise<VenueDetail | null>
  * 端点：`GET /portal/venues?q=&pageSize=8`，按影响因子默认排序；非 2xx throw。
  */
 export async function fetchVenueSuggestions(q: string): Promise<VenueSuggestion[]> {
-  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
-  if (!baseUrl) {
-    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
-  }
   const params = new URLSearchParams({ q, pageSize: "8" });
-  const res = await fetch(`${baseUrl}/patra-catalog/portal/venues?${params}`, {
-    cache: "no-store",
-    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
-  });
+  const res = await gatewayFetch(`/patra-catalog/portal/venues?${params}`);
   if (!res.ok) {
     throw new Error(`期刊候选加载失败：${res.status}`);
   }
diff --git a/patra-portal/src/proxy.ts b/patra-portal/src/proxy.ts
--- a/patra-portal/src/proxy.ts
+++ b/patra-portal/src/proxy.ts
@@ -1,4 +1,5 @@
 import { type NextRequest, NextResponse } from "next/server";
+import { gatewayFetch } from "@/lib/gateway";

 /// 详情页 404 状态码。详情页有 loading.tsx，服务器先回 200 把骨架流出去，等页面发现文献不存在时
 /// 状态码已改不了。这里只拦地址栏直接打开 / 刷新的文档请求：先确认存在，不存在就改写到 /missing/*，
@@ -24,10 +25,10 @@ export async function proxy(req: NextRequest) {
     return NextResponse.rewrite(new URL(`/missing/${kind}`, req.url));
   }
   // 后端出错或超时不在这里拦：放行后由页面照原路报错，不让中间层多等
-  const status = await fetch(
-    `${process.env.PATRA_GATEWAY_BASE_URL}/patra-catalog/portal/${endpoint}/${id}`,
-    { method: "HEAD", signal: AbortSignal.timeout(CHECK_TIMEOUT_MS) },
-  ).then(
+  const status = await gatewayFetch(`/patra-catalog/portal/${endpoint}/${id}`, {
+    method: "HEAD",
+    timeoutMs: CHECK_TIMEOUT_MS,
+  }).then(
     (res) => res.status,
     () => null,
   );
```

- [ ] **Step 4: 跑守护测试和受影响的现有测试，确认通过**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/gateway-boundary.test.ts tests/lib/portal-api tests/proxy.test.ts`
Expected: PASS，`Test Files  11 passed (11)`、`Tests  229 passed (229)`；现有的 fetcher 测试和 `tests/proxy.test.ts` 一行没改。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write tests/lib/gateway-boundary.test.ts src/lib/portal-api/publications.ts src/lib/portal-api/publication-search.ts src/lib/portal-api/venues.ts src/proxy.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  90 passed (90)`、`Tests  699 passed (699)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/tests/lib/gateway-boundary.test.ts patra-portal/src/lib/portal-api/publications.ts patra-portal/src/lib/portal-api/publication-search.ts patra-portal/src/lib/portal-api/venues.ts patra-portal/src/proxy.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
refactor(portal): 网关调用收拢到 gatewayFetch，加守护测试 (PAP-67)

10 处直接 fetch 网关的代码改走 gatewayFetch，删掉各自的读地址、判空和超时常量；状态码处理、
报错文字和 cache() 包装不变。守护测试扫描 src/：网关地址、直接 fetch、Authorization 只许出现在
gateway.ts，cookies() 只许出现在会话 Cookie 模块。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 5 个文件；pre-commit 全部通过。

---

### Task 3: 会话 Cookie 的读写和 `Secure` 开关

**Files:**
- Create: `patra-portal/tests/stubs/cookie-jar.ts`
- Create: `patra-portal/src/lib/auth/session-cookie.ts`
- Test: `patra-portal/tests/lib/auth/session-cookie.test.ts`
- Modify: `patra-portal/.env.example`、`patra-portal/playwright.config.ts`、`patra-infra/docker/.env.portal`
- Modify（本机，被 git 忽略，不提交）：`.claude/launch.json` 里的 `portal-dev`

**Interfaces:**
- Consumes: `next/headers` 的 `cookies()`。
- Produces: `readSessionToken(): Promise<string | null>`、`writeSessionCookie(token: string): Promise<void>`、`refreshSessionCookie(): Promise<void>`、`clearSessionCookie(): Promise<void>`。测试工具 `resetCookieJar(incoming?: Record<string, string>): CookieJar`：`incoming` 是这次请求带来的 Cookie，`jar.written` 按顺序记下每次写入的 Set-Cookie 原文；配合 `vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))` 使用，Task 4、5、7、8 的测试都靠它。

- [ ] **Step 1: 测试用的 Cookie 存储**

在 Next 的请求上下文之外调用 `cookies()` 会直接抛错，测试里换成这个存储。写入交给 Next 自己的 `ResponseCookies` 序列化，测试断言的就是浏览器收到的 Set-Cookie 原文。新建 `patra-portal/tests/stubs/cookie-jar.ts`：

```ts
import { NextResponse } from "next/server";

type SetCookieArgs = Parameters<NextResponse["cookies"]["set"]>;

/// 测试用的 Cookie 存储，替换 `next/headers` 的 `cookies()`：
/// `vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))`。
/// 读到的是 `resetCookieJar` 传入的请求 Cookie；每次写入都交给 Next 自己的 ResponseCookies
/// 序列化成一条 Set-Cookie 原文记下来，测试直接断言原文。
export interface CookieJar {
  get(name: string): { name: string; value: string } | undefined;
  set(...args: SetCookieArgs): void;
  /// 按写入顺序记下的 Set-Cookie 原文
  readonly written: string[];
}

function createCookieJar(incoming: Record<string, string>): CookieJar {
  const written: string[] = [];
  return {
    get: (name) => {
      const value = incoming[name];
      return value === undefined ? undefined : { name, value };
    },
    set: (...args) => {
      const response = new NextResponse(null);
      response.cookies.set(...args);
      written.push(...response.headers.getSetCookie());
    },
    written,
  };
}

let current = createCookieJar({});

/// 换一个新的 Cookie 存储，`incoming` 是这次请求带来的 Cookie。每个测试开头调用。
export function resetCookieJar(incoming: Record<string, string> = {}): CookieJar {
  current = createCookieJar(incoming);
  return current;
}

/// 替换 `next/headers` 的 `cookies()`。
export async function cookies(): Promise<CookieJar> {
  return current;
}
```

- [ ] **Step 2: 写失败的测试**

新建 `patra-portal/tests/lib/auth/session-cookie.test.ts`：

```ts
// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  clearSessionCookie,
  readSessionToken,
  refreshSessionCookie,
  writeSessionCookie,
} from "@/lib/auth/session-cookie";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

afterEach(() => {
  vi.unstubAllEnvs();
});

const TOKEN = "patra_user_abc";
/// 写入或刷新时的 Set-Cookie。Expires 由 Next 按 Max-Age 算出，这里只要求它存在
const WRITTEN_PLAIN =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const WRITTEN_SECURE =
  /^__Host-patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; Secure; HttpOnly; SameSite=lax$/;

describe("Cookie 的名称和属性", () => {
  it("开关不设时开启 Secure，名字带 __Host- 前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_SECURE)]);
  });

  it("开关为 true 时同样开启 Secure", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "true");
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_SECURE)]);
  });

  it("开关为 false 时不带 Secure，名字不带前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_PLAIN)]);
  });

  it.each(["", "TRUE", "yes", "0"])("开关为 %j 时报错", async (value) => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", value);
    resetCookieJar();
    await expect(writeSessionCookie(TOKEN)).rejects.toThrow(
      "PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false",
    );
  });
});

describe("readSessionToken", () => {
  it("Secure 关时读 patra_session", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    resetCookieJar({ patra_session: TOKEN, "__Host-patra_session": "other" });
    await expect(readSessionToken()).resolves.toBe(TOKEN);
  });

  it("Secure 开时读 __Host-patra_session", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    resetCookieJar({ patra_session: "other", "__Host-patra_session": TOKEN });
    await expect(readSessionToken()).resolves.toBe(TOKEN);
  });

  it("Secure 开时不读不带前缀的旧 Cookie：切到 HTTPS 之后要重新登录", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    resetCookieJar({ patra_session: TOKEN });
    await expect(readSessionToken()).resolves.toBeNull();
  });

  it("没有 Cookie 或值为空时返回 null", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    resetCookieJar();
    await expect(readSessionToken()).resolves.toBeNull();
    resetCookieJar({ patra_session: "" });
    await expect(readSessionToken()).resolves.toBeNull();
  });
});

describe("refreshSessionCookie", () => {
  it("用原值重写一次，有效期回到 30 天", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_session: TOKEN });
    await refreshSessionCookie();
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_PLAIN)]);
  });

  it("没有 Cookie 时什么也不做", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    await refreshSessionCookie();
    expect(jar.written).toEqual([]);
  });
});

describe("clearSessionCookie", () => {
  it("写入空值，属性和写入时相同，Max-Age=0", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_session: TOKEN });
    await clearSessionCookie();
    expect(jar.written).toEqual(["patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax"]);
  });

  it("Secure 开时清除也带 Secure，浏览器才会接受", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const jar = resetCookieJar({ "__Host-patra_session": TOKEN });
    await clearSessionCookie();
    expect(jar.written).toEqual([
      "__Host-patra_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=lax",
    ]);
  });
});
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/session-cookie.test.ts`
Expected: FAIL，`Error: Cannot find package '@/lib/auth/session-cookie' imported from …/tests/lib/auth/session-cookie.test.ts`，`Tests  no tests`。

- [ ] **Step 4: 写实现**

新建 `patra-portal/src/lib/auth/session-cookie.ts`：

```ts
import "server-only";
import { cookies } from "next/headers";

/// 有效期 30 天，和会话的不活跃过期时间一致
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

/// `Secure` 开关 `PATRA_SESSION_COOKIE_SECURE`：不设或为 `true` 时开，为 `false` 时关，其他值报错。
/// 每次读写 Cookie 时读取。
function secureEnabled(): boolean {
  const value = process.env.PATRA_SESSION_COOKIE_SECURE;
  if (value === undefined || value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  throw new Error("PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false");
}

/// 会话 Cookie 的名称和固定属性。`Secure` 开时名字带 `__Host-` 前缀：浏览器只接受本站经 HTTPS 写入、
/// `Path=/`、不带 `Domain` 的那一份，同一域名下的其他子站点没法种入或覆盖它。这个前缀要求必须带
/// `Secure`，所以 HTTP 环境只能用不带前缀的名字。
function sessionCookie() {
  const secure = secureEnabled();
  return {
    name: secure ? "__Host-patra_session" : "patra_session",
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  } as const;
}

/// 读会话令牌；没有 Cookie 或值为空时返回 null。
export async function readSessionToken(): Promise<string | null> {
  const value = (await cookies()).get(sessionCookie().name)?.value;
  return value ? value : null;
}

/// 注册、登录成功时写入令牌，有效期 30 天。
export async function writeSessionCookie(token: string): Promise<void> {
  (await cookies()).set({ ...sessionCookie(), value: token, maxAge: MAX_AGE_SECONDS });
}

/// 用原值重写一次，把有效期顺延回 30 天；没有 Cookie 时什么也不做。
export async function refreshSessionCookie(): Promise<void> {
  const token = await readSessionToken();
  if (token) {
    await writeSessionCookie(token);
  }
}

/// 清除：写入空值，属性和写入时相同，`Max-Age=0`。不用 `cookies().delete()`：它不带 `Secure`，
/// 浏览器会拒绝用它删除 `__Host-` 开头的 Cookie。
export async function clearSessionCookie(): Promise<void> {
  (await cookies()).set({ ...sessionCookie(), value: "", maxAge: 0 });
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: 同 Step 3。
Expected: PASS，`Tests  15 passed (15)`。

- [ ] **Step 6: 没有 HTTPS 的地方显式关掉 `Secure`**

`patra-portal/.env.example` 末尾加一段（和上一段之间空一行）：

```dotenv
# 会话 Cookie 的 Secure 开关 —— 不设或为 true 时开启：Cookie 名为 __Host-patra_session，
# 浏览器只在 HTTPS 下收发它。为 false 时关闭：Cookie 名为 patra_session，只用于没有 HTTPS 的环境
# （本机 pnpm dev、mini 上经 tailscale IP 访问的容器）。只能是 true 或 false，其他值会让读写 Cookie 时报错。
PATRA_SESSION_COOKIE_SECURE=false
```

`patra-portal/playwright.config.ts` 的 `webServer` 里，`reuseExistingServer` 后面加两行：

```ts
  webServer: {
    command: "pnpm dev",
    port: 4000,
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    // 本机走 HTTP，会话 Cookie 不能带 Secure（见 .env.example）
    env: { PATRA_SESSION_COOKIE_SECURE: "false" },
  },
```

`patra-infra/docker/.env.portal` 里，`PATRA_GATEWAY_BASE_URL=http://gateway:9528` 那一行后面空一行，加：

```dotenv
# 会话 Cookie 的 Secure 开关：mini 经 tailscale IP 用 HTTP 访问，浏览器不接受 HTTP 下写入的 Secure Cookie，
# 所以关掉。上 HTTPS 时删掉这一行：默认开启，Cookie 改名为 __Host-patra_session，已登录的人要重新登录一次。
PATRA_SESSION_COOKIE_SECURE=false
```

本机的 `/Users/linqibin/Projects/Products/patra/.claude/launch.json`（被 git 忽略，只改本机）：`portal-dev` 的 `runtimeArgs` 里，在 `"PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528",` 后面加一行 `"PATRA_SESSION_COOKIE_SECURE=false",`。

改完核对：

```bash
grep -n 'PATRA_SESSION_COOKIE_SECURE' /Users/linqibin/Projects/Products/patra/patra-portal/.env.example /Users/linqibin/Projects/Products/patra/patra-portal/playwright.config.ts /Users/linqibin/Projects/Products/patra/patra-infra/docker/.env.portal /Users/linqibin/Projects/Products/patra/.claude/launch.json
```

Expected: 4 个文件各命中 1 行，值都是 `false`。

- [ ] **Step 7: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write tests/stubs/cookie-jar.ts src/lib/auth/session-cookie.ts tests/lib/auth/session-cookie.test.ts playwright.config.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  91 passed (91)`、`Tests  714 passed (714)`。

- [ ] **Step 8: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/tests/stubs/cookie-jar.ts patra-portal/src/lib/auth/session-cookie.ts patra-portal/tests/lib/auth/session-cookie.test.ts patra-portal/.env.example patra-portal/playwright.config.ts patra-infra/docker/.env.portal
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 会话 Cookie 的读写封装和 Secure 开关 (PAP-67)

全仓库只有 session-cookie.ts 调用 cookies()。Secure 开时名字是 __Host-patra_session，关时是
patra_session；HttpOnly、SameSite=Lax、Path=/、Max-Age 30 天。清除时按相同属性写空值、
Max-Age=0，不用 delete()：它不带 Secure，浏览器不让它删 __Host- 开头的 Cookie。

开关 PATRA_SESSION_COOKIE_SECURE 默认开，没有 HTTPS 的地方显式关掉：mini 的 .env.portal、
Playwright 起的 dev server。测试用的 Cookie 存储放在 tests/stubs/cookie-jar.ts。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区是这 6 个文件，没有 `.claude/launch.json`；pre-commit 全部通过。

---

### Task 4: 服务端读当前用户

**Files:**
- Create: `patra-portal/src/lib/auth/types.ts`
- Create: `patra-portal/src/lib/auth/gateway-response.ts`
- Create: `patra-portal/src/lib/auth/current-user.ts`
- Test: `patra-portal/tests/lib/auth/current-user.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `gatewayFetch(path, { token })`；Task 3 的 `readSessionToken()`、`resetCookieJar()`。
- Produces:
  - `types.ts`：`CurrentUser { userId: string; email: string }`、`CurrentUserPayload { user: CurrentUser | null }`。Task 7 往这个文件末尾追加别的类型。
  - `gateway-response.ts`（server-only）：`isRecord(value: unknown): value is Record<string, unknown>`、`readJson(res: Response): Promise<unknown>`（不是 JSON 时返回 `undefined`）、`logUnexpectedResponse(endpoint: string, status: number, body: unknown): void`、`logRequestError(endpoint: string, error: unknown): void`。Task 7、8 复用。
  - `current-user.ts`（server-only）：`SessionLookup`（`authenticated` 带 `user`，以及 `anonymous`、`rejected`、`unavailable`）、`lookupSession(): Promise<SessionLookup>`（不抛异常、不写 Cookie）、`getCurrentUser(): Promise<CurrentUser | null>`。Task 5 用 `lookupSession()`；PAP-68 的登录页、注册页用 `getCurrentUser()`。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/auth/current-user.test.ts`：

```ts
// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getCurrentUser, lookupSession } from "@/lib/auth/current-user";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const USER = { userId: "319041872872550658", email: "reader@example.com" };
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: TOKEN });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code, title: "x" }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });

describe("lookupSession", () => {
  it("没有 Cookie 时是 anonymous，不请求网关", async () => {
    resetCookieJar();
    await expect(lookupSession()).resolves.toEqual({ status: "anonymous" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("带着令牌请求网关的 /auth/me", async () => {
    fetchMock.mockResolvedValue(json(200, { ...USER, accountType: "user" }));
    await lookupSession();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test:9528/patra-identity/auth/me");
    expect(init?.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
  });

  it("200 且字段齐全时是 authenticated，结果里没有 accountType", async () => {
    fetchMock.mockResolvedValue(json(200, { ...USER, accountType: "user" }));
    await expect(lookupSession()).resolves.toEqual({ status: "authenticated", user: USER });
  });

  it.each(["GW-0401", "IDN-0401"])("401 %s 都是 rejected", async (code) => {
    fetchMock.mockResolvedValue(problem(401, code));
    await expect(lookupSession()).resolves.toEqual({ status: "rejected" });
  });

  it.each([
    ["503 GW-0503", () => problem(503, "GW-0503")],
    ["500 GW-0500", () => problem(500, "GW-0500")],
    ["200 但缺 email", () => json(200, { userId: USER.userId })],
    ["200 但 userId 是数字", () => json(200, { userId: 1, email: USER.email })],
    ["200 但响应体不是 JSON", () => new Response("<html></html>", { status: 200 })],
  ])("%s 时是 unavailable", async (_label, response) => {
    fetchMock.mockResolvedValue(response());
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it.each([
    ["超时", new DOMException("The operation was aborted due to timeout", "TimeoutError")],
    ["网络错误", new TypeError("fetch failed")],
  ])("%s时是 unavailable", async (_label, error) => {
    fetchMock.mockRejectedValue(error);
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it("缺网关地址时是 unavailable", async () => {
    vi.stubEnv("PATRA_GATEWAY_BASE_URL", undefined);
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it("Secure 开关写错时是 unavailable，不请求网关", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("意外的响应只记接口、状态码和错误码，日志里没有令牌", async () => {
    fetchMock.mockResolvedValue(problem(503, "GW-0503"));
    await lookupSession();
    expect(console.error).toHaveBeenCalledWith("[auth] /patra-identity/auth/me 返回了意外的结果", {
      status: 503,
      code: "GW-0503",
    });
    expect(JSON.stringify(vi.mocked(console.error).mock.calls)).not.toContain(TOKEN);
  });
});

describe("getCurrentUser", () => {
  it("已登录时返回用户", async () => {
    fetchMock.mockResolvedValue(json(200, USER));
    await expect(getCurrentUser()).resolves.toEqual(USER);
  });

  it("401 时返回 null，不清 Cookie", async () => {
    fetchMock.mockResolvedValue(problem(401, "GW-0401"));
    const jar = resetCookieJar({ patra_session: TOKEN });
    await expect(getCurrentUser()).resolves.toBeNull();
    expect(jar.written).toEqual([]);
  });

  it("没有 Cookie 或网关不可用时返回 null", async () => {
    resetCookieJar();
    await expect(getCurrentUser()).resolves.toBeNull();
    resetCookieJar({ patra_session: TOKEN });
    fetchMock.mockResolvedValue(problem(503, "GW-0503"));
    await expect(getCurrentUser()).resolves.toBeNull();
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/current-user.test.ts`
Expected: FAIL，`Error: Cannot find package '@/lib/auth/current-user' imported from …/tests/lib/auth/current-user.test.ts`，`Tests  no tests`。

- [ ] **Step 3: 共用类型**

新建 `patra-portal/src/lib/auth/types.ts`：

```ts
/// 当前登录的用户。`userId` 是字符串：后端的雪花 ID 超出 JS 的安全整数范围。
export interface CurrentUser {
  userId: string;
  email: string;
}

/// `GET /api/auth/me` 的响应体，没有登录时 `user` 为 null。
export interface CurrentUserPayload {
  user: CurrentUser | null;
}
```

- [ ] **Step 4: 读响应体、记日志的小工具**

新建 `patra-portal/src/lib/auth/gateway-response.ts`：

```ts
import "server-only";

/// 是不是普通对象，用来安全地读响应体里的字段。
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/// 读响应体 JSON；不是 JSON（包括空响应体）时返回 undefined。
export async function readJson(res: Response): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    return undefined;
  }
}

/// 网关返回了意外的结果：只记接口、状态码和 ProblemDetail 的 `code`，不记响应体。
export function logUnexpectedResponse(endpoint: string, status: number, body: unknown): void {
  const code = isRecord(body) && typeof body.code === "string" ? body.code : null;
  console.error(`[auth] ${endpoint} 返回了意外的结果`, { status, code });
}

/// 请求网关时抛出了错误（超时、网络错误、缺配置）：只记错误类型和消息。
export function logRequestError(endpoint: string, error: unknown): void {
  const detail = error instanceof Error ? `${error.name}: ${error.message}` : typeof error;
  console.error(`[auth] ${endpoint} 请求失败`, detail);
}
```

- [ ] **Step 5: 读当前用户**

新建 `patra-portal/src/lib/auth/current-user.ts`：

```ts
import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import type { CurrentUser } from "@/lib/auth/types";
import { gatewayFetch } from "@/lib/gateway";

const ME_ENDPOINT = "/patra-identity/auth/me";

/// 读当前用户的结果。
export type SessionLookup =
  | { status: "authenticated"; user: CurrentUser }
  | { status: "anonymous" }
  | { status: "rejected" }
  | { status: "unavailable" };

/// `/auth/me` 的 200 响应里门户要用的字段，`accountType` 不读
const currentUserSchema = z.object({ userId: z.string().min(1), email: z.string().min(1) });

/// 带着会话令牌问网关当前用户是谁。不抛异常，也不写 Cookie。
/// - 没有 Cookie：`anonymous`，不请求网关
/// - 200 且字段齐全：`authenticated`
/// - 401（`GW-0401` 或 `IDN-0401`）：`rejected`
/// - 其他状态码、请求抛错、响应体格式不对、读 Cookie 抛错：`unavailable`
export async function lookupSession(): Promise<SessionLookup> {
  try {
    const token = await readSessionToken();
    if (!token) {
      return { status: "anonymous" };
    }
    const res = await gatewayFetch(ME_ENDPOINT, { token });
    if (res.status === 401) {
      return { status: "rejected" };
    }
    const body = await readJson(res);
    const parsed = res.status === 200 ? currentUserSchema.safeParse(body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(ME_ENDPOINT, res.status, body);
      return { status: "unavailable" };
    }
    return { status: "authenticated", user: parsed.data };
  } catch (error) {
    logRequestError(ME_ENDPOINT, error);
    return { status: "unavailable" };
  }
}

/// 服务端渲染的页面判断是否已登录时用（比如登录页「已登录就跳走」）。渲染时不能写 Cookie，
/// 所以收到 401 也只返回 null，过期的 Cookie 留给下一次 `/api/auth/me` 清除。
export async function getCurrentUser(): Promise<CurrentUser | null> {
  const session = await lookupSession();
  return session.status === "authenticated" ? session.user : null;
}
```

- [ ] **Step 6: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  18 passed (18)`。

- [ ] **Step 7: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/types.ts src/lib/auth/gateway-response.ts src/lib/auth/current-user.ts tests/lib/auth/current-user.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  92 passed (92)`、`Tests  732 passed (732)`。

- [ ] **Step 8: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/types.ts patra-portal/src/lib/auth/gateway-response.ts patra-portal/src/lib/auth/current-user.ts patra-portal/tests/lib/auth/current-user.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 服务端读当前用户 (PAP-67)

lookupSession() 带着 Cookie 里的令牌请求网关的 /auth/me，结果归成四种：authenticated、
anonymous、rejected（任何 401）、unavailable（其他状态码、超时、网络错误、响应体不对）。
它不抛异常，也不写 Cookie。getCurrentUser() 给服务端渲染的页面用，只在 authenticated 时
返回用户。日志只记接口、状态码和 ProblemDetail 的 code。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 4 个文件；pre-commit 全部通过。

---

### Task 5: `GET /api/auth/me`

**Files:**
- Create: `patra-portal/src/app/api/auth/me/route.ts`
- Test: `patra-portal/tests/app/api/auth-me.test.ts`

**Interfaces:**
- Consumes: Task 4 的 `lookupSession()`、`CurrentUserPayload`；Task 3 的 `refreshSessionCookie()`、`clearSessionCookie()`、`resetCookieJar()`。
- Produces: `GET /api/auth/me`。`authenticated` 时回 200 `{ user }` 并刷新 Cookie；`anonymous` 时回 200 `{ user: null }`；`rejected` 时回 200 `{ user: null }` 并清除 Cookie；`unavailable` 时回 503，响应体为空，Cookie 不动。所有响应带 `Cache-Control: private, no-store`。Task 6 的 `useCurrentUser()` 请求它。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/app/api/auth-me.test.ts`：

```ts
// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/auth/me/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const USER = { userId: "319041872872550658", email: "reader@example.com" };
const SIGNED_IN: Record<string, string> = { patra_session: TOKEN };
const SIGNED_OUT: Record<string, string> = {};
/// 刷新：原值重写，有效期回到 30 天
const REFRESHED =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// 每次调用都新建响应：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () =>
    Response.json(body, { status, headers: { "Content-Type": "application/json" } }),
  );

describe("GET /api/auth/me", () => {
  it("读到用户：200 + 用户，并把 Cookie 的有效期顺延回 30 天", async () => {
    respondWith(200, { ...USER, accountType: "user" });
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: USER });
    expect(jar.written).toEqual([expect.stringMatching(REFRESHED)]);
  });

  it("没有 Cookie：200 + null，不请求网关，不写 Cookie", async () => {
    const jar = resetCookieJar(SIGNED_OUT);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: null });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.written).toEqual([]);
  });

  it.each(["GW-0401", "IDN-0401"])("网关回 401 %s：200 + null，并清掉 Cookie", async (code) => {
    respondWith(401, { status: 401, code });
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: null });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("网关不可用：503、响应体为空，Cookie 不动", async () => {
    respondWith(503, { status: 503, code: "GW-0503" });
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(503);
    await expect(res.text()).resolves.toBe("");
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["已登录", 200, SIGNED_IN],
    ["没有 Cookie", 200, SIGNED_OUT],
    ["401", 401, SIGNED_IN],
    ["503", 503, SIGNED_IN],
  ] as const)("%s 时响应带 Cache-Control: private, no-store", async (_label, status, cookies) => {
    respondWith(status, status === 200 ? USER : { status });
    resetCookieJar(cookies);
    const res = await GET();
    expect(res.headers.get("cache-control")).toBe("private, no-store");
  });

  it("同一个令牌并发两次：两次都是 200，都只刷新、不清除", async () => {
    respondWith(200, USER);
    const jar = resetCookieJar(SIGNED_IN);
    const [first, second] = await Promise.all([GET(), GET()]);
    expect([first.status, second.status]).toEqual([200, 200]);
    expect(jar.written).toEqual([
      expect.stringMatching(REFRESHED),
      expect.stringMatching(REFRESHED),
    ]);
  });

  it("先 503 后 200：中间不清 Cookie，恢复后照常刷新", async () => {
    const jar = resetCookieJar(SIGNED_IN);
    respondWith(503, { status: 503, code: "GW-0503" });
    expect((await GET()).status).toBe(503);
    expect(jar.written).toEqual([]);
    respondWith(200, USER);
    expect((await GET()).status).toBe(200);
    expect(jar.written).toEqual([expect.stringMatching(REFRESHED)]);
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/app/api/auth-me.test.ts`
Expected: FAIL，`Error: Cannot find package '@/app/api/auth/me/route' imported from …/tests/app/api/auth-me.test.ts`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/app/api/auth/me/route.ts`：

```ts
import { NextResponse } from "next/server";
import { lookupSession } from "@/lib/auth/current-user";
import { clearSessionCookie, refreshSessionCookie } from "@/lib/auth/session-cookie";
import type { CurrentUserPayload } from "@/lib/auth/types";

export const dynamic = "force-dynamic";

/// 用户态数据不缓存：HTTP 层和 Next 都不缓存，不同用户之间也不共享
const NO_STORE = { "Cache-Control": "private, no-store" };

/// 200 响应，带 `NO_STORE`。
function payload(body: CurrentUserPayload): NextResponse {
  return NextResponse.json(body, { headers: NO_STORE });
}

/// 当前用户：浏览器（导航栏）→ `/api/auth/me` → 网关 `/patra-identity/auth/me`。
/// 读到用户时把 Cookie 的有效期顺延回 30 天，网关也在同一个请求里给会话续期；
/// 网关回 401 时清掉 Cookie；网关不可用时回 503，Cookie 不动。
export async function GET(): Promise<NextResponse> {
  const session = await lookupSession();
  if (session.status === "authenticated") {
    await refreshSessionCookie();
    return payload({ user: session.user });
  }
  if (session.status === "rejected") {
    await clearSessionCookie();
    return payload({ user: null });
  }
  if (session.status === "anonymous") {
    return payload({ user: null });
  }
  return new NextResponse(null, { status: 503, headers: NO_STORE });
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  11 passed (11)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/app/api/auth/me/route.ts tests/app/api/auth-me.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  93 passed (93)`、`Tests  743 passed (743)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/app/api/auth/me/route.ts patra-portal/tests/app/api/auth-me.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 新增 /api/auth/me，读到用户时刷新 Cookie、401 时清除 (PAP-67)

导航栏经它读当前用户：读到用户时把 Cookie 的有效期顺延回 30 天，网关在同一个请求里给会话续期；
网关回 401 时清掉 Cookie；网关不可用时回 503，Cookie 不动。所有响应都是 private, no-store。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 6: 导航栏用的 `useCurrentUser()`

**Files:**
- Modify: `patra-portal/src/lib/query-keys.ts`
- Create: `patra-portal/src/lib/auth/current-user-query.ts`
- Test: `patra-portal/tests/lib/auth/current-user-query.test.tsx`

**Interfaces:**
- Consumes: Task 4 的 `CurrentUser`、`CurrentUserPayload`；Task 5 的 `/api/auth/me`。
- Produces: `queryKeys.currentUser`，值是 `["current-user"]`，缓存的数据类型是 `CurrentUser | null`（PAP-68 在注册、登录成功后用 `setQueryData` 写入用户，登出后写入 null）；`CurrentUserState`（`pending`、`anonymous`，以及带 `user` 的 `authenticated`）；`useCurrentUser(): CurrentUserState`。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/auth/current-user-query.test.tsx`（Review Focus 第 2 条的用例在这里）：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const USER: CurrentUser = { userId: "319041872872550658", email: "reader@example.com" };

/// 每次调用都新建响应：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body?: unknown) =>
  vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status }));

const noRetryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

function renderCurrentUser(client: QueryClient = noRetryClient()) {
  return renderHook(() => useCurrentUser(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("useCurrentUser", () => {
  it("请求同源的 /api/auth/me，拿到用户之前是 pending", async () => {
    const fetchMock = respondWith(200, { user: USER });
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderCurrentUser();
    expect(result.current).toEqual({ status: "pending" });
    await waitFor(() => expect(result.current).toEqual({ status: "authenticated", user: USER }));
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/me");
  });

  it("没有登录：anonymous", async () => {
    vi.stubGlobal("fetch", respondWith(200, { user: null }));
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("门户回 503：anonymous", async () => {
    vi.stubGlobal("fetch", respondWith(503));
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("回了不是 JSON 的 200（比如前面的代理换成了错误页）：anonymous", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("<html></html>", { status: 200 })),
    );
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("第一次失败、重试还没回来时仍是 pending，重试成功后是 authenticated", async () => {
    let answer: (res: Response) => void = () => {};
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            answer = resolve;
          }),
      );
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderCurrentUser(
      new QueryClient({ defaultOptions: { queries: { retry: 1, retryDelay: 0 } } }),
    );
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(result.current).toEqual({ status: "pending" });
    answer(Response.json({ user: USER }));
    await waitFor(() => expect(result.current).toEqual({ status: "authenticated", user: USER }));
  });

  it.each([
    ["用户", USER, { status: "authenticated", user: USER }],
    ["null", null, { status: "anonymous" }],
  ])("页面写进 queryKeys.currentUser 的%s直接生效", (_label, cached, expected) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>(() => {})),
    );
    const client = noRetryClient();
    client.setQueryData(queryKeys.currentUser, cached);
    const { result } = renderCurrentUser(client);
    expect(result.current).toEqual(expected);
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/current-user-query.test.tsx`
Expected: FAIL，`Error: Failed to resolve import "@/lib/auth/current-user-query" from "tests/lib/auth/current-user-query.test.tsx". Does the file exist?`，`Tests  no tests`。

- [ ] **Step 3: 加查询键**

`patra-portal/src/lib/query-keys.ts` 在 `venueSuggest` 后面加 `currentUser`，整个文件变成：

```ts
/** TanStack Query key 集中管理（见 .claude/rules/state.md）。 */
export const queryKeys = {
  /** 期刊候选：全部输入的公共前缀（按前缀读取所有已缓存候选） */
  venueSuggestAll: ["venue-suggest"] as const,
  /** 期刊候选：按去空白后的输入缓存 */
  venueSuggest: (q: string) => ["venue-suggest", q] as const,
  /** 当前用户：导航栏读取；注册、登录、登出之后由页面直接写入 */
  currentUser: ["current-user"] as const,
};
```

- [ ] **Step 4: 写实现**

新建 `patra-portal/src/lib/auth/current-user-query.ts`：

```ts
import { useQuery } from "@tanstack/react-query";
import type { CurrentUser, CurrentUserPayload } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// 导航栏看到的登录状态。
export type CurrentUserState =
  | { status: "pending" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: CurrentUser };

/// 请求门户自己的 `/api/auth/me`：2xx 时取 `user`，非 2xx 时抛错。
async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const res = await fetch("/api/auth/me");
  if (!res.ok) {
    throw new Error(`当前用户加载失败：${res.status}`);
  }
  return ((await res.json()) as CurrentUserPayload).user;
}

/// 当前用户（浏览器端，经 `/api/auth/me` 中转），沿用全局的缓存和重试设置。
/// 还没有数据时（包括失败后重试期间）是 `pending`；最终出错时按未登录处理，这一次显示未登录态。
export function useCurrentUser(): CurrentUserState {
  const { data, isError } = useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: fetchCurrentUser,
  });
  if (isError) {
    return { status: "anonymous" };
  }
  if (data === undefined) {
    return { status: "pending" };
  }
  return data === null ? { status: "anonymous" } : { status: "authenticated", user: data };
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  7 passed (7)`。

- [ ] **Step 6: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/query-keys.ts src/lib/auth/current-user-query.ts tests/lib/auth/current-user-query.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  94 passed (94)`、`Tests  750 passed (750)`。

- [ ] **Step 7: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/query-keys.ts patra-portal/src/lib/auth/current-user-query.ts patra-portal/tests/lib/auth/current-user-query.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 导航栏读当前用户的 useCurrentUser (PAP-67)

请求同源的 /api/auth/me，沿用全局的缓存和重试设置：还没有数据时（包括失败后重试期间）是
pending，最终出错时按未登录处理。查询键 queryKeys.currentUser 留给登录页、注册页在成功后
直接写入。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 3 个文件；pre-commit 全部通过。

---

### Task 7: 注册、登录的 Server Action

**Files:**
- Modify: `patra-portal/src/lib/auth/types.ts`（文件末尾追加）
- Create: `patra-portal/src/lib/auth/actions.ts`
- Test: `patra-portal/tests/lib/auth/actions.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `gatewayFetch`；Task 3 的 `writeSessionCookie()`、`resetCookieJar()`；Task 4 的 `isRecord`、`readJson`、`logUnexpectedResponse`、`logRequestError`、`CurrentUser`。
- Produces: `Credentials { email: string; password: string }`、`FieldError { field: string; code: string | null }`、`RegisterResult`、`LoginResult`（spec 第 9.2 节）；`register(input: Credentials): Promise<RegisterResult>`、`login(input: Credentials): Promise<LoginResult>`。Task 8 往同一个文件里加 `logout`，并复用测试文件里的 `respondWith`、`problem`、`timeout`、`networkError`、`sent`。

- [ ] **Step 1: 补类型**

`patra-portal/src/lib/auth/types.ts` 末尾空一行，追加：

```ts
/// 注册、登录的入参。
export interface Credentials {
  email: string;
  password: string;
}

/// 422 里的一个字段错误。
export interface FieldError {
  field: string;
  /// 原因码：REQUIRED、TOO_LONG、INVALID_FORMAT、TOO_SHORT、TOO_COMMON、INVALID_CHARACTER；后端没给时为 null
  code: string | null;
}

/// `register` 的结果。不带文案，页面按 `reason` 和字段的 `code` 选文案。
export type RegisterResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "email-taken" }
  | { ok: false; reason: "unavailable" };

/// `login` 的结果。不带文案，页面按 `reason` 和字段的 `code` 选文案。
export type LoginResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "bad-credentials" }
  | { ok: false; reason: "banned" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | { ok: false; reason: "unavailable" };
```

- [ ] **Step 2: 写失败的测试**

新建 `patra-portal/tests/lib/auth/actions.test.ts`（Review Focus 第 3、4 条的用例在这里）。`afterEach` 里检查每个用例的日志都不含密码和令牌：

```ts
// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { login, register } from "@/lib/auth/actions";
import type { Credentials } from "@/lib/auth/types";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const PASSWORD = "correct-horse-battery";
const CREDENTIALS: Credentials = { email: "reader@example.com", password: PASSWORD };
const USER = { userId: "319041872872550658", email: "reader@example.com" };
const SIGNED_IN = { sessionToken: TOKEN, ...USER };
/// 写入：令牌原文，有效期 30 天
const WRITTEN =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const UNAVAILABLE = { ok: false, reason: "unavailable" };
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  const logged = JSON.stringify(vi.mocked(console.error).mock.calls);
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
  // 每个用例都检查：日志里没有密码和令牌
  expect(logged).not.toContain(PASSWORD);
  expect(logged).not.toContain(TOKEN);
});

/// 网关的响应，每次调用都新建：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body?: unknown, headers: Record<string, string> = {}) =>
  fetchMock.mockImplementation(async () =>
    body === undefined
      ? new Response(null, { status, headers })
      : Response.json(body, { status, headers }),
  );

/// ProblemDetail 响应体
const problem = (status: number, extra: Record<string, unknown> = {}) => ({
  status,
  code: `IDN-0${status}`,
  title: "x",
  ...extra,
});

const timeout = () =>
  fetchMock.mockRejectedValue(new DOMException("signal timed out", "TimeoutError"));
const networkError = () => fetchMock.mockRejectedValue(new TypeError("fetch failed"));

/// 表单直接把 Server Action 当作 action 提交时，收到的是 FormData
const asFormData = (fields: Record<string, string>) => {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    form.set(name, value);
  }
  return form;
};

/// 第一次请求网关时的 init
const sent = () => fetchMock.mock.calls[0]?.[1] ?? {};

describe("register", () => {
  it("201：写入 Cookie，返回用户", async () => {
    respondWith(201, SIGNED_IN);
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
  });

  it("只转发 email 和 password，不带 Authorization，即使已经有 Cookie", async () => {
    respondWith(201, SIGNED_IN);
    resetCookieJar({ patra_session: "patra_user_old" });
    const withExtras = { ...CREDENTIALS, clientType: "WEB", deviceId: "d-1" };
    await register(withExtras);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-identity/auth/register",
      expect.objectContaining({ method: "POST", headers: { "Content-Type": "application/json" } }),
    );
    expect(JSON.parse(String(sent().body))).toEqual(CREDENTIALS);
  });

  it("422：返回字段和原因码，其余字段（包括 rejectedValue）不带回浏览器", async () => {
    respondWith(
      422,
      problem(422, {
        errors: [
          { field: "email", code: "INVALID_FORMAT", rejectedValue: "reader@", message: "格式不对" },
          { field: "password", code: "TOO_SHORT", rejectedValue: PASSWORD, message: "太短" },
        ],
      }),
    );
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "email", code: "INVALID_FORMAT" },
        { field: "password", code: "TOO_SHORT" },
      ],
    });
    expect(jar.written).toEqual([]);
  });

  it("422：code 不是字符串时记为 null，field 不是字符串的项跳过", async () => {
    respondWith(
      422,
      problem(422, {
        errors: [
          { field: "password", code: 7 },
          { field: "email" },
          { code: "REQUIRED" },
          "x",
          null,
        ],
      }),
    );
    resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "password", code: null },
        { field: "email", code: null },
      ],
    });
  });

  it("409：email-taken，不写 Cookie", async () => {
    respondWith(409, problem(409));
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({ ok: false, reason: "email-taken" });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["201 但缺 sessionToken", () => respondWith(201, USER)],
    ["201 但 userId 不是字符串", () => respondWith(201, { ...SIGNED_IN, userId: 42 })],
    ["201 但响应体不是 JSON", () => respondWith(201)],
    ["422 但没有 errors", () => respondWith(422, problem(422))],
    [
      "422 但 errors 一项都取不到",
      () => respondWith(422, problem(422, { errors: [{ code: "X" }] })),
    ],
    ["401", () => respondWith(401, problem(401))],
    ["403", () => respondWith(403, problem(403))],
    ["429", () => respondWith(429, problem(429, { retryAfterSeconds: 60 }))],
    ["500", () => respondWith(500, problem(500))],
    ["503", () => respondWith(503, problem(503))],
    ["超时", timeout],
    ["网络错误", networkError],
  ])("%s：unavailable，不写 Cookie", async (_label, arrange) => {
    arrange();
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual(UNAVAILABLE);
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["缺 password", { email: "reader@example.com" }],
    ["email 不是字符串", { email: 42, password: PASSWORD }],
    ["不是对象", "reader@example.com"],
    ["是 FormData", asFormData({ email: "reader@example.com", password: PASSWORD })],
  ])("入参%s：unavailable，不请求网关", async (_label, input) => {
    resetCookieJar();
    await expect(register(input as unknown as Credentials)).resolves.toEqual(UNAVAILABLE);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("意外的状态码：记下接口、状态码和 code，不记响应体", async () => {
    respondWith(500, problem(500, { detail: "boom" }));
    resetCookieJar();
    await register(CREDENTIALS);
    expect(console.error).toHaveBeenCalledWith(
      "[auth] /patra-identity/auth/register 返回了意外的结果",
      { status: 500, code: "IDN-0500" },
    );
  });
});

describe("login", () => {
  it("200：写入 Cookie，返回用户", async () => {
    respondWith(200, SIGNED_IN);
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
  });

  it("只转发 email 和 password，不带 Authorization，即使已经有 Cookie", async () => {
    respondWith(200, SIGNED_IN);
    resetCookieJar({ patra_session: "patra_user_old" });
    const withExtras = { ...CREDENTIALS, clientType: "WEB", deviceId: "d-1" };
    await login(withExtras);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-identity/auth/login",
      expect.objectContaining({ method: "POST", headers: { "Content-Type": "application/json" } }),
    );
    expect(JSON.parse(String(sent().body))).toEqual(CREDENTIALS);
  });

  it("已经登录时再登录：新令牌覆盖 Cookie，不去作废旧会话", async () => {
    respondWith(200, SIGNED_IN);
    const jar = resetCookieJar({ patra_session: "patra_user_old" });
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("422：invalid-fields", async () => {
    respondWith(422, problem(422, { errors: [{ field: "password", code: "REQUIRED" }] }));
    resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "password", code: "REQUIRED" }],
    });
  });

  it.each([
    [401, "bad-credentials"],
    [403, "banned"],
  ])("%i：%s，不写 Cookie", async (status, reason) => {
    respondWith(status, problem(status));
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: false, reason });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["响应体的 retryAfterSeconds 优先", { retryAfterSeconds: 120 }, { "Retry-After": "90" }, 120],
    ["响应体为 0", { retryAfterSeconds: 0 }, {}, 0],
    ["响应体没有，取 Retry-After 头", {}, { "Retry-After": "90" }, 90],
    ["响应体是负数，取 Retry-After 头", { retryAfterSeconds: -1 }, { "Retry-After": "90" }, 90],
    ["响应体是小数，没有头", { retryAfterSeconds: 1.5 }, {}, null],
    ["Retry-After 是日期", {}, { "Retry-After": "Sat, 10 Oct 2026 08:00:00 GMT" }, null],
    ["都没有", {}, {}, null],
  ])("429（%s）：throttled", async (_label, extra, headers, expected) => {
    respondWith(429, problem(429, extra), headers);
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: expected,
    });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["200 但缺 sessionToken", () => respondWith(200, USER)],
    ["422 但没有 errors", () => respondWith(422, problem(422))],
    ["409", () => respondWith(409, problem(409))],
    ["500", () => respondWith(500, problem(500))],
    ["503", () => respondWith(503, problem(503))],
    ["超时", timeout],
    ["网络错误", networkError],
  ])("%s：unavailable，不写 Cookie", async (_label, arrange) => {
    arrange();
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual(UNAVAILABLE);
    expect(jar.written).toEqual([]);
  });

  it("入参形状不对：unavailable，不请求网关", async () => {
    resetCookieJar();
    const input = { email: "reader@example.com" } as unknown as Credentials;
    await expect(login(input)).resolves.toEqual(UNAVAILABLE);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/actions.test.ts`
Expected: FAIL，`Error: Cannot find package '@/lib/auth/actions' imported from …/tests/lib/auth/actions.test.ts`，`Tests  no tests`。

- [ ] **Step 4: 写实现**

新建 `patra-portal/src/lib/auth/actions.ts`：

```ts
"use server";

import { z } from "zod";
import {
  isRecord,
  logRequestError,
  logUnexpectedResponse,
  readJson,
} from "@/lib/auth/gateway-response";
import { writeSessionCookie } from "@/lib/auth/session-cookie";
import type {
  Credentials,
  CurrentUser,
  FieldError,
  LoginResult,
  RegisterResult,
} from "@/lib/auth/types";
import { gatewayFetch } from "@/lib/gateway";

const REGISTER_ENDPOINT = "/patra-identity/auth/register";
const LOGIN_ENDPOINT = "/patra-identity/auth/login";

/// 入参只校验形状，多余的字段被剥掉；长度和格式由后端校验，失败时回 422 并带原因码
const credentialsSchema = z.object({ email: z.string(), password: z.string() });

/// 注册、登录成功时的响应体
const signedInSchema = z.object({
  sessionToken: z.string().min(1),
  userId: z.string().min(1),
  email: z.string().min(1),
});

/// 422 响应体 `errors[]` 里的一项：`field` 必须是字符串，`code` 不是字符串时记为 null，其余字段丢掉
const fieldErrorSchema = z.object({ field: z.string(), code: z.string().nullable().catch(null) });

/// 429 响应体里的等待秒数
const retryAfterSchema = z.object({ retryAfterSeconds: z.number().int().nonnegative() });

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 网关的响应和读出的响应体
interface GatewayReply {
  res: Response;
  body: unknown;
}

/// 把 `email`、`password` 发给网关，不带令牌。入参形状不对时返回 null，不请求网关；
/// 请求抛错时记日志，返回 null。
async function submitCredentials(
  endpoint: string,
  input: Credentials,
): Promise<GatewayReply | null> {
  const credentials = credentialsSchema.safeParse(input);
  if (!credentials.success) {
    return null;
  }
  try {
    const res = await gatewayFetch(endpoint, { method: "POST", json: credentials.data });
    return { res, body: await readJson(res) };
  } catch (error) {
    logRequestError(endpoint, error);
    return null;
  }
}

/// 成功响应：响应体对就写入 Cookie、返回用户；不对就记日志、返回 null，不写 Cookie。
async function signIn(endpoint: string, reply: GatewayReply): Promise<CurrentUser | null> {
  const parsed = signedInSchema.safeParse(reply.body);
  if (!parsed.success) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return null;
  }
  await writeSessionCookie(parsed.data.sessionToken);
  return { userId: parsed.data.userId, email: parsed.data.email };
}

/// 422：取到字段错误时返回 `invalid-fields`；一项都取不到时记日志，返回 `unavailable`。
function invalidFields(endpoint: string, reply: GatewayReply) {
  const errors: unknown[] =
    isRecord(reply.body) && Array.isArray(reply.body.errors) ? reply.body.errors : [];
  const fieldErrors: FieldError[] = errors.flatMap((item) => {
    const parsed = fieldErrorSchema.safeParse(item);
    return parsed.success ? [parsed.data] : [];
  });
  if (fieldErrors.length === 0) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return UNAVAILABLE;
  }
  return { ok: false, reason: "invalid-fields", fieldErrors } as const;
}

/// 429 的等待秒数：先取响应体的 `retryAfterSeconds`，再取 `Retry-After` 头，都要是非负整数；
/// 都没有时为 null。
function retryAfterSeconds(reply: GatewayReply): number | null {
  const fromBody = retryAfterSchema.safeParse(reply.body);
  if (fromBody.success) {
    return fromBody.data.retryAfterSeconds;
  }
  const fromHeader = reply.res.headers.get("Retry-After");
  return fromHeader !== null && /^\d+$/.test(fromHeader) ? Number(fromHeader) : null;
}

/// 注册：成功（201）时写入 Cookie、返回用户。422 返回字段错误，409 是邮箱已被注册；
/// 其余状态码、超时和网络错误都是 `unavailable`。
export async function register(input: Credentials): Promise<RegisterResult> {
  const reply = await submitCredentials(REGISTER_ENDPOINT, input);
  if (!reply) {
    return UNAVAILABLE;
  }
  const { status } = reply.res;
  if (status === 201) {
    const user = await signIn(REGISTER_ENDPOINT, reply);
    return user ? { ok: true, user } : UNAVAILABLE;
  }
  if (status === 422) {
    return invalidFields(REGISTER_ENDPOINT, reply);
  }
  if (status === 409) {
    return { ok: false, reason: "email-taken" };
  }
  logUnexpectedResponse(REGISTER_ENDPOINT, status, reply.body);
  return UNAVAILABLE;
}

/// 登录：成功（200）时写入 Cookie、返回用户。422 返回字段错误，401 是邮箱或密码不对，
/// 403 是账号被封禁，429 是尝试次数过多；其余状态码、超时和网络错误都是 `unavailable`。
export async function login(input: Credentials): Promise<LoginResult> {
  const reply = await submitCredentials(LOGIN_ENDPOINT, input);
  if (!reply) {
    return UNAVAILABLE;
  }
  const { status } = reply.res;
  if (status === 200) {
    const user = await signIn(LOGIN_ENDPOINT, reply);
    return user ? { ok: true, user } : UNAVAILABLE;
  }
  if (status === 422) {
    return invalidFields(LOGIN_ENDPOINT, reply);
  }
  if (status === 401) {
    return { ok: false, reason: "bad-credentials" };
  }
  if (status === 403) {
    return { ok: false, reason: "banned" };
  }
  if (status === 429) {
    return { ok: false, reason: "throttled", retryAfterSeconds: retryAfterSeconds(reply) };
  }
  logUnexpectedResponse(LOGIN_ENDPOINT, status, reply.body);
  return UNAVAILABLE;
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: 同 Step 3。
Expected: PASS，`Tests  43 passed (43)`。

- [ ] **Step 6: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/types.ts src/lib/auth/actions.ts tests/lib/auth/actions.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  95 passed (95)`、`Tests  793 passed (793)`。

- [ ] **Step 7: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/types.ts patra-portal/src/lib/auth/actions.ts patra-portal/tests/lib/auth/actions.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 注册、登录的 Server Action (PAP-67)

入参只校验形状，只把 email、password 转发给网关，不带 Authorization。成功时写入会话 Cookie，
返回用户；422 返回字段和原因码（rejectedValue 等其余字段丢掉）；注册 409 是 email-taken；
登录 401 / 403 / 429 分别是 bad-credentials、banned、throttled（等待秒数先取响应体，再取
Retry-After）；其余情况都是 unavailable。结果不带文案，也不跳转。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 3 个文件；pre-commit 全部通过。

---

### Task 8: 登出的 Server Action

**Files:**
- Modify: `patra-portal/src/lib/auth/actions.ts`
- Modify: `patra-portal/tests/lib/auth/actions.test.ts`

**Interfaces:**
- Consumes: Task 3 的 `readSessionToken()`、`clearSessionCookie()`；Task 7 的 `actions.ts` 和测试工具。
- Produces: `logout(): Promise<void>`。有令牌时请求网关删除会话，最后总是清除 Cookie；网关的任何失败都不往外抛，只有 Cookie 开关的值写错时会抛。PAP-68 的用户菜单调用它。

- [ ] **Step 1: 写失败的测试**

`patra-portal/tests/lib/auth/actions.test.ts` 改三处。

import 加上 `logout`：

```ts
import { login, logout, register } from "@/lib/auth/actions";
```

`WRITTEN` 常量后面加 `CLEARED`：

```ts
/// 清除：空值，属性和写入时相同，Max-Age=0
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
```

文件末尾空一行，追加：

```ts
describe("logout", () => {
  it("有 Cookie：带令牌请求网关登出，超时 5 秒，然后清除 Cookie", async () => {
    respondWith(204);
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");
    const jar = resetCookieJar({ patra_session: TOKEN });
    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-identity/auth/logout",
      expect.objectContaining({ method: "POST", headers: { Authorization: `Bearer ${TOKEN}` } }),
    );
    expect(sent()).not.toHaveProperty("body");
    expect(timeoutSpy).toHaveBeenCalledWith(5_000);
    expect(jar.written).toEqual([CLEARED]);
    expect(console.error).not.toHaveBeenCalled();
  });

  it.each([
    ["网关回 503", () => respondWith(503, problem(503))],
    ["超时", timeout],
    ["网络错误", networkError],
  ])("%s：不抛错，记一条日志，照样清除 Cookie", async (_label, arrange) => {
    arrange();
    const jar = resetCookieJar({ patra_session: TOKEN });
    await expect(logout()).resolves.toBeUndefined();
    expect(jar.written).toEqual([CLEARED]);
    expect(console.error).toHaveBeenCalledTimes(1);
  });

  it("没有 Cookie：不请求网关，照样清除 Cookie", async () => {
    const jar = resetCookieJar();
    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.written).toEqual([CLEARED]);
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/actions.test.ts`
Expected: FAIL，`Tests  5 failed | 43 passed (48)`，5 个失败都是 `TypeError: logout is not a function`。

- [ ] **Step 3: 写实现**

`patra-portal/src/lib/auth/actions.ts` 改三处。

session-cookie 的 import 改成：

```ts
import {
  clearSessionCookie,
  readSessionToken,
  writeSessionCookie,
} from "@/lib/auth/session-cookie";
```

`LOGIN_ENDPOINT` 后面加：

```ts
const LOGOUT_ENDPOINT = "/patra-identity/auth/logout";

/// 登出的超时：删后端会话只是尽力而为，不必让用户等满默认的 10 秒
const LOGOUT_TIMEOUT_MS = 5_000;
```

文件末尾空一行，追加：

```ts
/// 登出：有令牌时带着它请求网关删除会话，非 204 的响应和抛出的错误只记日志、不往外抛；
/// 不论结果如何，最后都清除 Cookie，所以登出在用户看来总是成功的。
export async function logout(): Promise<void> {
  try {
    const token = await readSessionToken();
    if (token) {
      const res = await gatewayFetch(LOGOUT_ENDPOINT, {
        method: "POST",
        token,
        timeoutMs: LOGOUT_TIMEOUT_MS,
      });
      if (res.status !== 204) {
        logUnexpectedResponse(LOGOUT_ENDPOINT, res.status, await readJson(res));
      }
    }
  } catch (error) {
    logRequestError(LOGOUT_ENDPOINT, error);
  } finally {
    await clearSessionCookie();
  }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  48 passed (48)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/actions.ts tests/lib/auth/actions.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0；`Test Files  95 passed (95)`、`Tests  798 passed (798)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/actions.ts patra-portal/tests/lib/auth/actions.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 登出的 Server Action，总是清除 Cookie (PAP-67)

有令牌时带着它请求网关删除会话，超时 5 秒；非 204 的响应和抛出的错误只记日志、不往外抛。
不论结果如何，都在 finally 里清除 Cookie，所以登出在用户看来总是成功的。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 9: 文档同步

**Files:**
- Modify: `patra-portal/README.md`（快速开始；目录结构里的 `api/`、`lib/`）
- Modify: `docs/patra/release-specs/v0.8-accounts.md`（第 105 行；第 192 行，D3 的第二条）
- Modify: `patra-portal/docs/design-briefs/v0.8/accounts.md`（第 19 行，§0；第 214 行，§4.3）

**Interfaces:** 无代码接口。

- [ ] **Step 1: README**

`patra-portal/README.md` 的快速开始：

```bash
pnpm install
pnpm dev          # http://localhost:4000
```

改成：

```bash
pnpm install
cp .env.example .env.local   # 网关地址、会话 Cookie 的 Secure 开关，按需修改
pnpm dev          # http://localhost:4000
```

目录结构里 `api/` 这一行：

```
│   ├── api/                    # health、期刊联想
```

改成：

```
│   ├── api/                    # health、期刊联想、auth/me（当前用户）
```

`lib/` 下面，在 `portal-api/` 这一行前面加两行：

```
├── lib/
│   ├── gateway.ts              # 统一网关客户端：全仓库只有这里读网关地址、向网关发请求
│   ├── auth/                   # 会话 Cookie、当前用户、注册 / 登录 / 登出的 Server Action
│   ├── portal-api/             # 服务端向网关取数、解析检索参数
```

- [ ] **Step 2: release spec**

`docs/patra/release-specs/v0.8-accounts.md` 第 105 行：

```
  - 会话：登录时写入 Redis，登出、封禁、踢下线时删除；Redis 里只存令牌的哈希。每次请求续期；30 天不活跃过期。
```

改成：

```
  - 会话：登录时写入 Redis，登出、封禁、踢下线时删除；Redis 里只存令牌的哈希。每次请求续期；30 天不活跃过期，从登录算起最长 180 天。
```

第 192 行（D3 的第二条）：

```
  - 30 天内有过访问就一直保持登录，不需要重新登录
```

改成：

```
  - 30 天内有过访问就保持登录；从登录算起最长 180 天，到期要重新登录
```

- [ ] **Step 3: 设计简报**

`patra-portal/docs/design-briefs/v0.8/accounts.md` 第 19 行里的「登录状态默认保持，30 天不活跃才过期；目前也没有条款文档」改成「登录状态默认保持：30 天不活跃才过期，从登录算起最长 180 天；目前也没有条款文档」。

第 214 行里的「失效有三种来源：30 天不活跃过期、被撤销、被封禁。」改成「失效有四种来源：30 天不活跃过期、从登录算起满 180 天、被撤销、被封禁。」

- [ ] **Step 4: 核对**

```bash
grep -n '180 天' /Users/linqibin/Projects/Products/patra/docs/patra/release-specs/v0.8-accounts.md /Users/linqibin/Projects/Products/patra/patra-portal/docs/design-briefs/v0.8/accounts.md
grep -c '一直保持登录' /Users/linqibin/Projects/Products/patra/docs/patra/release-specs/v0.8-accounts.md
grep -nE 'gateway\.ts|auth/|cp \.env\.example' /Users/linqibin/Projects/Products/patra/patra-portal/README.md
```

Expected: 第一条命中 4 行（release spec 第 105、192 行，简报第 19、214 行）；第二条输出 `0`；第三条命中 README 的 4 行（`cp .env.example`、`api/` 那一行、`gateway.ts`、`auth/`），改之前一行都不命中。

- [ ] **Step 5: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/README.md docs/patra/release-specs/v0.8-accounts.md patra-portal/docs/design-briefs/v0.8/accounts.md
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
docs(portal): 同步 README、D3 和设计简报里认证相关的说法 (PAP-67)

README 的快速开始提一句复制 .env.example，目录结构加上 lib/gateway.ts、lib/auth/ 和
api/auth/me。release spec 的会话一条和 D3、设计简报的 §0 和 §4.3 补上「从登录算起最长
180 天」，让文档里的承诺和网关的实际行为一致。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 3 个文件；pre-commit 全部通过。

---

### Task 10: 本地实测，结果写回 spec，同步 Linear

**Files:**
- Modify: `docs/patra/specs/2026-10-10-portal-auth-design.md`（头部的「状态」，第 14 节）
- Modify（本机，临时）：`.claude/launch.json` 加一个 `portal-dev-secure`，用完删掉
- Linear：PAP-67 的描述

**Interfaces:** 无代码接口。本机门户指向 mini 的网关 `http://100.103.73.27:9528`；测试账号用一次性邮箱，dev 数据可以丢弃。令牌只放在 shell 变量里，输出时遮掉。

- [ ] **Step 1: 实测脚本**

把下面的脚本存成本会话 scratchpad 目录里的 `pap67-check.sh`（不进仓库）。它在 mini 的网关上注册一个一次性账号，再按 spec 第 14 节请求本机门户的 `/api/auth/me`：

```bash
#!/bin/bash
# PAP-67 实测（spec 第 14 节）：在 mini 的网关上注册一个一次性账号，再请求本机门户的 /api/auth/me。
# 用法：bash pap67-check.sh <Cookie 名>，Cookie 名是 patra_session 或 __Host-patra_session。
# 令牌和密码只放在变量里，输出时遮掉；响应体只打印门户的，网关的不打印。
set -u
NAME="$1"
GW=http://100.103.73.27:9528
PORTAL=http://localhost:4000
EMAIL="pap67-$(date +%s)@example.com"
PASSWORD="$(openssl rand -hex 16)"

RESP="$(curl -sS --noproxy '*' -w '\n%{http_code}' -X POST "${GW}/patra-identity/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")"
STATUS="${RESP##*$'\n'}"
BODY="${RESP%$'\n'*}"
if [ "${STATUS}" != 201 ]; then
  CODE="$(printf '%s' "${BODY}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))' 2>/dev/null)"
  echo "1. 注册失败：${STATUS} ${CODE}"
  exit 1
fi
TOKEN="$(printf '%s' "${BODY}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sessionToken"])')"
echo "1. 注册：${STATUS}，令牌 ${#TOKEN} 个字符，前缀 ${TOKEN:0:11}"

# 请求门户的 /api/auth/me，只打印状态行、Set-Cookie、Cache-Control 和响应体。$1 是 Cookie 头的值，可以为空
me() {
  local args=(-sS --noproxy '*' -D - -o -)
  if [ -n "$1" ]; then
    args+=(-H "Cookie: $1")
  fi
  curl "${args[@]}" "${PORTAL}/api/auth/me" | tr -d '\r' \
    | grep -iE '^HTTP/|^set-cookie:|^cache-control:|^\{' \
    | sed -E 's/(patra_session=)[^;]+/\1<令牌>/'
}

echo "2. 带 ${NAME}："
me "${NAME}=${TOKEN}"
echo "5. 不带 Cookie："
me ""
echo "7. 坏值："
me "${NAME}=patra_user_broken"
LOGOUT="$(curl -sS --noproxy '*' -o /dev/null -w '%{http_code}' -X POST \
  -H "Authorization: Bearer ${TOKEN}" "${GW}/patra-identity/auth/logout")"
echo "4. 直接调网关登出：${LOGOUT}"
echo "4. 登出后带同一个 ${NAME}："
me "${NAME}=${TOKEN}"
```

- [ ] **Step 2: `Secure` 关时实测**

用 `preview_start` 起 `portal-dev`（端口 4000，Task 3 已经给它加了 `PATRA_SESSION_COOKIE_SECURE=false`），等首页能打开后跑：

Run: `bash <scratchpad>/pap67-check.sh patra_session`（`<scratchpad>` 换成 Step 1 存脚本的目录，下同）
Expected（`…` 是每次不同的值；状态行和响应头的大小写、顺序以实际为准）：

```
1. 注册：201，令牌 54 个字符，前缀 patra_user_
2. 带 patra_session：
HTTP/1.1 200 OK
cache-control: private, no-store
set-cookie: patra_session=<令牌>; Path=/; Expires=…; Max-Age=2592000; HttpOnly; SameSite=lax
{"user":{"userId":"…","email":"pap67-…@example.com"}}
5. 不带 Cookie：
HTTP/1.1 200 OK
cache-control: private, no-store
{"user":null}
7. 坏值：
HTTP/1.1 200 OK
cache-control: private, no-store
set-cookie: patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax
{"user":null}
4. 直接调网关登出：204
4. 登出后带同一个 patra_session：
HTTP/1.1 200 OK
cache-control: private, no-store
set-cookie: patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax
{"user":null}
```

和预期不一致时停下来查原因，不要改断言凑结果。比如「坏值」拿到的不是 200 加清除，说明网关对格式不对的令牌不是按匿名处理，要回头核对 spec 第 3 节的前提。

- [ ] **Step 3: `Secure` 开时实测**

在本机 `.claude/launch.json` 里复制一份 `portal-dev`，名字改成 `portal-dev-secure`，把 `"PATRA_SESSION_COOKIE_SECURE=false"` 改成 `"PATRA_SESSION_COOKIE_SECURE=true"`（显式写 `true`：进程环境变量优先于 `.env.local`，结果不受本机配置影响）。`preview_stop` 停掉 `portal-dev`，`preview_start` 起 `portal-dev-secure`，然后跑：

Run: `bash <scratchpad>/pap67-check.sh __Host-patra_session`
Expected：和 Step 2 相同，区别只在 Cookie：第 2 步是 `set-cookie: __Host-patra_session=<令牌>; Path=/; Expires=…; Max-Age=2592000; Secure; HttpOnly; SameSite=lax`；「坏值」和登出后都是 `set-cookie: __Host-patra_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=lax`。

跑完 `preview_stop` 停掉 `portal-dev-secure`，从 `.claude/launch.json` 删掉这个配置，再 `preview_start` 起回 `portal-dev`。

- [ ] **Step 4: 匿名页面的 e2e**

`portal-dev` 在跑时，Playwright 直接复用它（它的网关已经指向 mini）。`papers-search.spec.ts` 的 PMID 直跳用例自己也要读网关地址，没设就跳过，所以命令里带上：

Run: `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test:e2e --workers=1`
Expected: `14 passed`，没有 skipped，也没有 failed。这些用例连不上后端时会自己跳过，有 skipped 就说明门户没连上 mini 的网关，先查原因再重跑。要是有文献检索的用例因为网关到 catalog 连接超时（`GW-0503`）失败，单独重跑那个 spec 文件一次：以前遇到过 tailnet 到容器网段的间歇抖动，和本 Issue 无关，结果里照实写。

- [ ] **Step 5: 结果写回 spec**

`docs/patra/specs/2026-10-10-portal-auth-design.md`：

- 头部的「状态」改成「已实施（<日期>，分支 `feat/v0.8-accounts-fe`）」，`<日期>` 写实测当天，格式 `YYYY-MM-DD`，下同。
- 第 14 节开头那段末尾补一句：「实施后本地做，结果写回本节（<日期> 实测，门户是本分支 Task 9 之后的代码，网关和 identity 是 mini 上在跑的版本）。」
- 第 1 到 6 条下面各加一行 `   - 实测：…`，写实际看到的状态码、Set-Cookie 原文（令牌写成 `<令牌>`）、e2e 的通过数。第 2 条写开关关着的结果，第 3 条写开着的结果，第 4、5 条两种取值都写。
- 第 6 条后面加第 7 条：「（Review Focus）Cookie 值是坏的：网关按匿名处理，`/auth/me` 回 401，门户回 200 `{ user: null }` 并清除 Cookie。」下面同样加一行实测。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add docs/patra/specs/2026-10-10-portal-auth-design.md
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
docs(portal): 记下认证基础设施的本地实测结果 (PAP-67)

两种 Secure 取值下 /api/auth/me 的刷新、401 清除、不带 Cookie、坏值四种情况，以及匿名页面的
e2e，结果写回 spec 第 14 节；spec 状态改为已实施。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有 spec 这一个文件；pre-commit 全部通过。

- [ ] **Step 7: 同步 Linear PAP-67**

用 ToolSearch 加载 Linear 的 `get_issue`、`save_issue`。先 `get_issue` 读 PAP-67 的当前描述，再用 `save_issue` 只改描述，不改状态：

- To-Do 第一项改成「Tech Design（`docs/patra/specs/2026-10-10-portal-auth-design.md`）：」开头，后面原文不动；To-Do 七项全部勾上。
- AC 第 3 条改成「30 天内有过访问就保持登录；从登录算起最长 180 天，到期要重新登录；会话失效后回到未登录态」。
- 勾上 AC 第 4、6、7、8 条（并发不登出、匿名请求不带令牌且行为不变、守护测试、合格线），这几条在本 Issue 里已经验证完。
- AC 第 1、2、3、5 条先不勾：要用登录页、注册页和用户菜单在浏览器里走一遍，留到 PAP-68 验收时勾。

Expected: 再 `get_issue` 读一次，描述和上面一致，状态没变。

---

## 执行后的收尾

- 全部任务做完后，按所选的执行方式做一次全分支的最终评审（用最强的模型）。评审范围从 spec 提交 `760a8fea6` 到 HEAD，把本计划的 Review Focus 原样交给评审者。
- 走 `superpowers:finishing-a-development-branch` 时选「保留分支」：PAP-68 接着在 `feat/v0.8-accounts-fe` 上做。不推送，也不开 PR。前端的 draft PR 等 PAP-68 做完、用户在本地验收之后再开，一个 PR 覆盖 PAP-67 和 PAP-68（`Closes PAP-67`、`Closes PAP-68`），流程见根 `CLAUDE.md` 的「PR 与代码评审」。
- 向用户汇报时说明两点：一是本机直接跑 `pnpm dev`、不经过 `.claude/launch.json` 时，要在自己的 `.env.local` 里加 `PATRA_SESSION_COOKIE_SECURE=false`，计划不替用户改这个文件；二是 Linear 上 AC 第 1、2、3、5 条留给 PAP-68 验收。
